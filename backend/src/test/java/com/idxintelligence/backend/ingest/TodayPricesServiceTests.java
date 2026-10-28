package com.idxintelligence.backend.ingest;

import com.idxintelligence.backend.EmbeddedPostgresTest;
import com.idxintelligence.backend.market.MarketService;
import com.idxintelligence.backend.market.StockQuote;
import com.idxintelligence.backend.market.TodayPriceSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class TodayPricesServiceTests extends EmbeddedPostgresTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);
    private static final ZonedDateTime AFTER_CLOSE = TODAY.atTime(16, 40).atZone(YahooFinanceClient.JAKARTA);

    @Autowired
    private JdbcClient jdbc;

    private final YahooFinanceClient yahoo = mock(YahooFinanceClient.class);

    @BeforeEach
    void setUp() {
        jdbc.sql("DELETE FROM daily_price WHERE trade_date >= ?").param(Date.valueOf(TODAY)).update();
        long at = AFTER_CLOSE.withHour(16).withMinute(0).toEpochSecond();
        when(yahoo.spark(anyList())).thenAnswer(call -> {
            List<String> symbols = call.getArgument(0);
            return symbols.contains("BBCA.JK")
                    ? Map.of("BBCA.JK", new YahooFinanceClient.SparkQuote(new BigDecimal("5275"), new BigDecimal("4900"), at),
                             // A stock that didn't trade today: its last price is from an earlier day.
                             "BBRI.JK", new YahooFinanceClient.SparkQuote(new BigDecimal("2900"), new BigDecimal("2900"), at - 86_400))
                    : Map.of();
        });
    }

    @Test
    void afterTheCloseGivesTodaysPricesUntilTheImport() {
        TodayPricesService service = new TodayPricesService(yahoo, jdbc, Clock.fixed(AFTER_CLOSE.toInstant(), YahooFinanceClient.JAKARTA));

        TodayPriceSource.TodayPrices today = service.todayPrices().orElseThrow();
        assertThat(today.prices()).containsOnlyKeys("BBCA");
        assertThat(today.prices().get("BBCA").price()).isEqualByComparingTo("5275");
        verify(yahoo, atLeastOnce()).spark(anyList());
    }

    @Test
    void nothingBeforeNineOrOnceTodayIsImported() {
        TodayPricesService early = new TodayPricesService(yahoo, jdbc,
                Clock.fixed(AFTER_CLOSE.withHour(8).toInstant(), YahooFinanceClient.JAKARTA));
        assertThat(early.todayPrices()).isEmpty();

        jdbc.sql("""
                INSERT INTO daily_price (ticker, trade_date, close, volume, source)
                SELECT ticker, ?, 100, 1, 'TEST' FROM stock WHERE active AND price_missing_since IS NULL
                """).param(Date.valueOf(TODAY)).update();
        TodayPricesService after = new TodayPricesService(yahoo, jdbc, Clock.fixed(AFTER_CLOSE.toInstant(), YahooFinanceClient.JAKARTA));
        assertThat(after.todayPrices()).isEmpty();
        verify(yahoo, never()).spark(anyList());
        jdbc.sql("DELETE FROM daily_price WHERE trade_date = ?").param(Date.valueOf(TODAY)).update();
    }

    @Test
    void onlyStocksWithoutTodaysStoredBarAreReplaced() {
        StockQuote stale = StockQuote.of("BBCA", "Bank Central Asia", null, TODAY.minusDays(1), new BigDecimal("4900"),
                new BigDecimal("4630"), 1000L, null, true, false);
        StockQuote current = StockQuote.of("TLKM", "Telkom", null, TODAY, new BigDecimal("2070"),
                new BigDecimal("2140"), 1000L, null, true, false);
        TodayPriceSource.TodayPrices today = new TodayPriceSource.TodayPrices(AFTER_CLOSE.toOffsetDateTime(), Map.of(
                "BBCA", new TodayPriceSource.TodayPrice(new BigDecimal("5275"), new BigDecimal("4900")),
                "TLKM", new TodayPriceSource.TodayPrice(new BigDecimal("9999"), new BigDecimal("2140"))));

        List<StockQuote> result = MarketService.withTodayPrices(List.of(stale, current), today);

        assertThat(result.get(0).lastPrice()).isEqualByComparingTo("5275");
        assertThat(result.get(0).changePercent()).isEqualByComparingTo("7.65");
        assertThat(result.get(0).tradeDate()).isEqualTo(TODAY);
        assertThat(result.get(0).value()).isNull();          // unknown until the evening import
        assertThat(result.get(1)).isSameAs(current);          // already has today's stored bar
    }
}
