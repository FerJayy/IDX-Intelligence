package com.idxintelligence.backend;

import com.idxintelligence.backend.broker.BrokerAnalysisService;
import com.idxintelligence.backend.broker.BrokerSummary;
import com.idxintelligence.backend.market.IndexSnapshot;
import com.idxintelligence.backend.market.MarketDataProvider;
import com.idxintelligence.backend.market.PriceRange;
import com.idxintelligence.backend.market.StockQuote;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The database-backed providers (the default outside tests) against a real PostgreSQL. */
@SpringBootTest
class DatabaseProvidersTests extends EmbeddedPostgresTest {

    private static final LocalDate THU = LocalDate.of(2026, 10, 22);
    private static final LocalDate FRI = LocalDate.of(2026, 10, 23);

    @Autowired
    private MarketDataProvider market;

    @Autowired
    private BrokerAnalysisService brokerAnalysis;

    @Autowired
    private com.idxintelligence.backend.market.MarketService marketService;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbc.sql("DELETE FROM broker_daily_activity").update();
        jdbc.sql("DELETE FROM broker_import_day").update();
        jdbc.sql("DELETE FROM daily_price").update();
        jdbc.sql("DELETE FROM index_daily").update();
        price("BBCA", THU, 5000, 100);
        price("BBCA", FRI, 5100, 200);
        price("TLKM", THU, 3000, 100); // no Friday bar: treated as stale
        jdbc.sql("INSERT INTO index_daily (code, trade_date, close, source) VALUES ('COMPOSITE', ?, 7000, 'TEST'), ('COMPOSITE', ?, 7070, 'TEST')")
                .params(Date.valueOf(THU), Date.valueOf(FRI)).update();
    }

    @Test
    void quotesComeFromTheLatestTwoBars() {
        assertThat(market.isDemo()).isFalse();
        assertThat(market.latestTradingDate()).isEqualTo(FRI);

        StockQuote bbca = market.quote("BBCA").orElseThrow();
        assertThat(bbca.tradeDate()).isEqualTo(FRI);
        assertThat(bbca.lastPrice()).isEqualByComparingTo("5100");
        assertThat(bbca.changePercent()).isEqualByComparingTo("2.00");
        assertThat(bbca.value()).isEqualByComparingTo("1020000");
    }

    @Test
    void staleStockKeepsItsPriceButHasNoDailyChange() {
        StockQuote tlkm = market.quote("TLKM").orElseThrow();
        assertThat(tlkm.tradeDate()).isEqualTo(THU);
        assertThat(tlkm.lastPrice()).isEqualByComparingTo("3000");
        assertThat(tlkm.change()).isNull();
        assertThat(tlkm.volume()).isNull();
    }

    @Test
    void stockWithoutAnyPricesHasOnlyItsName() {
        StockQuote unvr = market.quote("UNVR").orElseThrow();
        assertThat(unvr.name()).isEqualTo("Unilever Indonesia");
        assertThat(unvr.lastPrice()).isNull();
    }

    @Test
    void thinlyTradedStocksAreNotTopMovers() {
        // UNVR jumps 50% on Rp15.000 of trading: real for the breadth count, but not a top gainer.
        price("UNVR", THU, 100, 100);
        price("UNVR", FRI, 150, 100);
        // BBCA trades Rp5,1 miliar on Friday: liquid enough to count.
        jdbc.sql("UPDATE daily_price SET volume = 1000000 WHERE ticker = 'BBCA' AND trade_date = ?").param(Date.valueOf(FRI)).update();

        var overview = marketService.overview();

        assertThat(overview.topGainers()).extracting(StockQuote::ticker).doesNotContain("UNVR").contains("BBCA");
        assertThat(overview.advancers()).isEqualTo(2);
    }

    @Test
    void hscStocksAreFlaggedFilterableAndTheListCanBeReplaced() {
        // BREN is in the seeded watchlist and on the HSC list from migration V9.
        assertThat(market.quote("BREN").orElseThrow().hsc()).isTrue();
        assertThat(market.quote("BBCA").orElseThrow().hsc()).isFalse();
        assertThat(marketService.list(null, null, null, "only", "ticker", true, 1, 200).stocks())
                .extracting(StockQuote::ticker).contains("BREN").doesNotContain("BBCA");

        var admin = new com.idxintelligence.backend.ingest.AdminHscController(
                new com.idxintelligence.backend.ingest.AdminAuth("token"), jdbc, jdbcTemplate);
        try {
            var result = admin.replace("token", new com.idxintelligence.backend.ingest.AdminHscController.HscList(
                    LocalDate.of(2026, 11, 2), "test", List.of("bbca", "ZZZZ")));
            assertThat(result.stocks()).isEqualTo(2);
            assertThat(market.quote("BBCA").orElseThrow().hsc()).isTrue();
            assertThat(market.quote("BREN").orElseThrow().hsc()).isFalse(); // removed by the new list
        } finally {
            // Put the seeded list back for other tests sharing this database.
            jdbc.sql("DELETE FROM hsc_stock").update();
            jdbc.sql("INSERT INTO hsc_stock (ticker, as_of, source) VALUES ('BREN', DATE '2026-10-12', 'test reset')").update();
        }
    }

    @Test
    void compositeIndexComparesLatestTwoDays() {
        IndexSnapshot ihsg = market.compositeIndex();
        assertThat(ihsg.tradeDate()).isEqualTo(FRI);
        assertThat(ihsg.changePercent()).isEqualByComparingTo("1.00");
    }

    @Test
    void brokerSummaryReportsCoverage() {
        jdbc.sql("INSERT INTO broker (code, name) VALUES ('YP', 'MIRAE ASSET SEKURITAS INDONESIA') ON CONFLICT DO NOTHING").update();
        jdbc.sql("INSERT INTO broker (code, name) VALUES ('AK', 'UBS SEKURITAS INDONESIA') ON CONFLICT DO NOTHING").update();
        jdbc.sql("""
                INSERT INTO broker_daily_activity (ticker, trade_date, broker_code, buy_volume, buy_value, sell_volume, sell_value,
                    foreign_buy_volume, foreign_buy_value, foreign_sell_volume, foreign_sell_value, source)
                VALUES ('BBCA', ?, 'YP', 200, 1020, 0, 0, 0, 0, 0, 0, 'TEST'),
                       ('BBCA', ?, 'AK', 0, 0, 200, 1020, 0, 0, 200, 1020, 'TEST')
                """).params(Date.valueOf(FRI), Date.valueOf(FRI)).update();

        BrokerSummary summary = brokerAnalysis.summary("BBCA", PriceRange.ONE_MONTH);

        assertThat(summary.demo()).isFalse();
        assertThat(summary.tradingDays()).isEqualTo(2);
        assertThat(summary.brokerDays()).isEqualTo(1);
        assertThat(summary.topBuyers().getFirst().brokerName()).isEqualTo("MIRAE ASSET SEKURITAS INDONESIA");
        assertThat(summary.daily().getFirst().foreignNetValue()).isEqualByComparingTo("-1020");
        assertThat(summary.assessment().reasons().getFirst().code()).isEqualTo("TOO_FEW_BROKER_DAYS");
    }

    private void price(String ticker, LocalDate date, long close, long volume) {
        jdbc.sql("INSERT INTO daily_price (ticker, trade_date, close, volume, source) VALUES (?, ?, ?, ?, 'TEST')")
                .params(ticker, Date.valueOf(date), close, volume).update();
    }
}
