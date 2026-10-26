package com.idxintelligence.backend.market;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * Hard-coded demo data. Tickers are real IDX codes, but every number here is
 * invented and must never be shown without the demo label.
 */
@Component
public class MockMarketDataProvider implements MarketDataProvider {

    private static final List<StockQuote> QUOTES = List.of(
            quote("BBCA", "Bank Central Asia", "Financials", 9775, 9850, 85_400_000L),
            quote("BBRI", "Bank Rakyat Indonesia", "Financials", 4120, 4060, 152_300_000L),
            quote("BMRI", "Bank Mandiri", "Financials", 6200, 6275, 98_700_000L),
            quote("TLKM", "Telkom Indonesia", "Infrastructures", 2870, 2900, 120_500_000L),
            quote("ASII", "Astra International", "Industrials", 5100, 5075, 45_200_000L),
            quote("GOTO", "GoTo Gojek Tokopedia", "Technology", 70, 74, 2_850_000_000L),
            quote("ANTM", "Aneka Tambang", "Basic Materials", 1580, 1655, 210_000_000L),
            quote("ADRO", "Alamtri Resources Indonesia", "Energy", 2450, 2410, 76_300_000L),
            quote("PGAS", "Perusahaan Gas Negara", "Energy", 1600, 1625, 60_000_000L),
            quote("UNVR", "Unilever Indonesia", "Consumer Non-Cyclicals", 1890, 1890, 18_600_000L),
            quote("ICBP", "Indofood CBP Sukses Makmur", "Consumer Non-Cyclicals", 11200, 11075, 9_800_000L)
    );

    // A little over one year of weekdays, enough for the longest PriceRange.
    private static final int HISTORY_TRADING_DAYS = 270;

    private static final IndexSnapshot COMPOSITE = index("COMPOSITE", "IDX Composite (IHSG)",
            new BigDecimal("7250.12"), new BigDecimal("7286.45"));

    @Override
    public String sourceName() {
        return "DEMO";
    }

    @Override
    public boolean isDemo() {
        return true;
    }

    @Override
    public IndexSnapshot compositeIndex() {
        return COMPOSITE;
    }

    @Override
    public List<StockQuote> quotes() {
        return QUOTES;
    }

    @Override
    public List<PriceBar> dailyBars(String ticker, LocalDate from, LocalDate to) {
        return quote(ticker)
                .map(q -> DemoPriceGenerator.generate(q, latestTradingDate(), HISTORY_TRADING_DAYS))
                .orElse(List.of())
                .stream()
                .filter(bar -> !bar.date().isBefore(from) && !bar.date().isAfter(to))
                .toList();
    }

    /** Demo quotes are treated as the most recent weekday in Jakarta. */
    @Override
    public LocalDate latestTradingDate() {
        LocalDate date = LocalDate.now(MarketService.JAKARTA);
        while (!DemoPriceGenerator.isWeekday(date)) {
            date = date.minusDays(1);
        }
        return date;
    }

    private static StockQuote quote(String ticker, String name, String sector,
                                    long previousClose, long lastPrice, long volume) {
        BigDecimal prev = BigDecimal.valueOf(previousClose);
        BigDecimal last = BigDecimal.valueOf(lastPrice);
        BigDecimal change = last.subtract(prev);
        return new StockQuote(ticker, name, sector, last, prev, change,
                percent(change, prev), volume, last.multiply(BigDecimal.valueOf(volume)));
    }

    private static IndexSnapshot index(String code, String name, BigDecimal previousClose, BigDecimal value) {
        BigDecimal change = value.subtract(previousClose);
        return new IndexSnapshot(code, name, value, previousClose, change, percent(change, previousClose));
    }

    private static BigDecimal percent(BigDecimal change, BigDecimal base) {
        return change.multiply(BigDecimal.valueOf(100)).divide(base, 2, RoundingMode.HALF_UP);
    }
}
