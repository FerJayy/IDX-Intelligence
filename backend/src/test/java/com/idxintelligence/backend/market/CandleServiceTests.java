package com.idxintelligence.backend.market;

import com.idxintelligence.backend.ingest.YahooFinanceClient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CandleServiceTests {

    private static long wib(int day, int hour) {
        return LocalDateTime.of(2026, 10, day, hour, 0).atZone(MarketService.JAKARTA).toEpochSecond();
    }

    private static Candle candle(long time, int open, int high, int low, int close, long volume) {
        return new Candle(time, BigDecimal.valueOf(open), BigDecimal.valueOf(high), BigDecimal.valueOf(low),
                BigDecimal.valueOf(close), volume);
    }

    @Test
    void fourHourCandlesFollowTheMorningAndAfternoonSessions() {
        List<Candle> hourly = List.of(
                candle(wib(26, 9), 100, 105, 99, 104, 10),
                candle(wib(26, 10), 104, 108, 103, 107, 20),
                candle(wib(26, 11), 107, 107, 101, 102, 30),
                candle(wib(26, 13), 102, 103, 100, 101, 40),   // afternoon session
                candle(wib(26, 14), 101, 110, 101, 109, 50),
                candle(wib(27, 9), 109, 111, 108, 110, 60));   // next day

        List<Candle> fourHour = CandleService.fourHour(hourly);

        assertThat(fourHour).hasSize(3);
        assertThat(fourHour.get(0)).isEqualTo(candle(wib(26, 9), 100, 108, 99, 102, 60));
        assertThat(fourHour.get(1)).isEqualTo(candle(wib(26, 13), 102, 110, 100, 109, 90));
        assertThat(fourHour.get(2).time()).isEqualTo(wib(27, 9));
    }

    @Test
    void mergeKeepsMissingVolumeMissing() {
        Candle a = new Candle(1, BigDecimal.TEN, null, null, BigDecimal.TEN, null);
        Candle b = new Candle(2, BigDecimal.ONE, null, null, BigDecimal.ONE, null);

        Candle merged = CandleService.merge(1, List.of(a, b));

        assertThat(merged.volume()).isNull();
        assertThat(merged.high()).isEqualByComparingTo("10");
        assertThat(merged.low()).isEqualByComparingTo("1");
    }

    @Test
    void weeklyCandlesUseAdjustedPricesAndTheFirstTradingDay() {
        MarketDataProvider provider = mock(MarketDataProvider.class);
        LocalDate thu = LocalDate.of(2026, 10, 22);
        LocalDate fri = LocalDate.of(2026, 10, 23);
        LocalDate mon = LocalDate.of(2026, 10, 26);
        when(provider.latestTradingDate()).thenReturn(mon);
        when(provider.sourceName()).thenReturn("TEST");
        // adjusted_close is half the close: e.g. a later 2:1 split.
        when(provider.dailyBars(eq("BBCA"), any(), eq(mon))).thenReturn(List.of(
                bar(thu, 100, 50), bar(fri, 110, 55), bar(mon, 120, 60)));
        when(provider.quote("BBCA")).thenReturn(java.util.Optional.of(
                StockQuote.of("BBCA", "Bank Central Asia", null, mon, BigDecimal.ONE, BigDecimal.ONE, 1L, null, true)));
        CandleService service = new CandleService(provider, new MarketService(provider), mock(YahooFinanceClient.class),
                Clock.systemUTC());

        CandleSeries weekly = service.stock("BBCA", ChartInterval.W1, "5Y");

        assertThat(weekly.adjusted()).isTrue();
        assertThat(weekly.candles()).hasSize(2);
        assertThat(weekly.candles().get(0).time()).isEqualTo(thu.atStartOfDay(ZoneOffset.UTC).toEpochSecond());
        assertThat(weekly.candles().get(0).close()).isEqualByComparingTo("55");
        assertThat(weekly.candles().get(0).volume()).isEqualTo(2000);
        assertThat(weekly.ranges()).containsExactly("1Y", "5Y", "MAX");
    }

    @Test
    void intradayIsFetchedOnceAndThenServedFromCache() {
        MarketDataProvider provider = mock(MarketDataProvider.class);
        when(provider.isDemo()).thenReturn(false);
        when(provider.latestTradingDate()).thenReturn(LocalDate.of(2026, 10, 27));
        YahooFinanceClient yahoo = mock(YahooFinanceClient.class);
        when(yahoo.intradayBars(eq("^JKSE"), eq("60m"), any())).thenReturn(List.of(
                new YahooFinanceClient.IntradayBar(wib(27, 9), BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.TEN, 5L)));
        CandleService service = new CandleService(provider, new MarketService(provider), yahoo, Clock.systemUTC());

        CandleSeries first = service.index("composite", ChartInterval.H1, "1M");
        service.index("COMPOSITE", ChartInterval.H1, "1M");

        assertThat(first.intraday()).isTrue();
        assertThat(first.candles()).hasSize(1);
        verify(yahoo, times(1)).intradayBars(anyString(), anyString(), any());
    }

    @Test
    void unsuitableRangeFallsBackToTheIntervalDefault() {
        assertThat(ChartInterval.M15.range("1Y")).isEqualTo(ChartInterval.Range.D5);
        assertThat(ChartInterval.D1.adjusted(ChartInterval.Range.Y1)).isFalse();
        assertThat(ChartInterval.D1.adjusted(ChartInterval.Range.Y5)).isTrue();
    }

    private static PriceBar bar(LocalDate date, int close, int adjustedClose) {
        BigDecimal c = BigDecimal.valueOf(close);
        return new PriceBar(date, c, c, c, c, 1000L, BigDecimal.valueOf(adjustedClose));
    }
}
