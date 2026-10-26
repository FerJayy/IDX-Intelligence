package com.idxintelligence.backend.broker;

import com.idxintelligence.backend.market.MockMarketDataProvider;
import com.idxintelligence.backend.market.PriceBar;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class DemoBrokerGeneratorTests {

    private final MockMarketDataProvider market = new MockMarketDataProvider();

    @Test
    void everyDayBalancesBuysAndSellsAgainstBarVolume() {
        LocalDate end = market.latestTradingDate();
        for (String ticker : List.of("BBCA", "GOTO", "UNVR")) {
            List<PriceBar> bars = market.dailyBars(ticker, end.minusMonths(6), end);
            Map<LocalDate, PriceBar> barByDate = bars.stream()
                    .collect(Collectors.toMap(PriceBar::date, b -> b));
            Map<LocalDate, List<BrokerDailyActivity>> rowsByDate = DemoBrokerGenerator.generate(ticker, bars).stream()
                    .collect(Collectors.groupingBy(BrokerDailyActivity::date));

            assertThat(rowsByDate.keySet()).isEqualTo(barByDate.keySet());
            rowsByDate.forEach((date, rows) -> {
                PriceBar bar = barByDate.get(date);
                assertThat(rows.stream().mapToLong(BrokerDailyActivity::buyVolume).sum()).isEqualTo(bar.volume());
                assertThat(rows.stream().mapToLong(BrokerDailyActivity::sellVolume).sum()).isEqualTo(bar.volume());
                for (BrokerDailyActivity row : rows) {
                    assertThat(row.buyVolume() % 100).isZero();
                    assertThat(row.sellVolume() % 100).isZero();
                    assertAveragePriceWithinBar(row.buyValue(), row.buyVolume(), bar);
                    assertAveragePriceWithinBar(row.sellValue(), row.sellVolume(), bar);
                }
            });
        }
    }

    private static void assertAveragePriceWithinBar(BigDecimal value, long volume, PriceBar bar) {
        if (volume == 0) return;
        double average = value.doubleValue() / volume;
        assertThat(average).isBetween(bar.low().doubleValue() - 0.01, bar.high().doubleValue() + 0.01);
    }
}
