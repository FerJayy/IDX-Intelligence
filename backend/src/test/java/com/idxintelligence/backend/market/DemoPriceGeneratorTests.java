package com.idxintelligence.backend.market;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DemoPriceGeneratorTests {

    private final MockMarketDataProvider provider = new MockMarketDataProvider();
    private final LocalDate friday = LocalDate.of(2026, 10, 23);

    @Test
    void barsAreValidOhlcOnWeekdaysAtIdxTickSizes() {
        for (StockQuote quote : provider.quotes()) {
            List<PriceBar> bars = DemoPriceGenerator.generate(quote, friday, 270);

            assertThat(bars).hasSize(270);
            assertThat(bars.getLast().date()).isEqualTo(friday);
            for (PriceBar bar : bars) {
                assertThat(DemoPriceGenerator.isWeekday(bar.date())).isTrue();
                assertThat(bar.high()).isGreaterThanOrEqualTo(bar.open().max(bar.close()));
                assertThat(bar.low()).isLessThanOrEqualTo(bar.open().min(bar.close()));
                assertThat(bar.low().signum()).isPositive();
                double tick = DemoPriceGenerator.tickSize(bar.close().doubleValue());
                assertThat(bar.close().doubleValue() % tick)
                        .as("%s close %s on %s", quote.ticker(), bar.close(), bar.date())
                        .isZero();
            }
        }
    }

    @Test
    void historyIsDeterministic() {
        StockQuote bbca = provider.quote("BBCA").orElseThrow();
        assertThat(DemoPriceGenerator.generate(bbca, friday, 60))
                .isEqualTo(DemoPriceGenerator.generate(bbca, friday, 60));
    }
}
