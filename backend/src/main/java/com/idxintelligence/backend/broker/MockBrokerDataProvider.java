package com.idxintelligence.backend.broker;

import com.idxintelligence.backend.market.MarketDataProvider;
import com.idxintelligence.backend.market.PriceBar;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/** Demo broker summaries derived from the demo price history. Never real broker data. */
@Component
public class MockBrokerDataProvider implements BrokerDataProvider {

    private final MarketDataProvider marketData;

    public MockBrokerDataProvider(MarketDataProvider marketData) {
        this.marketData = marketData;
    }

    @Override
    public String sourceName() {
        return "DEMO";
    }

    @Override
    public boolean isDemo() {
        return true;
    }

    @Override
    public List<BrokerDailyActivity> dailyActivity(String ticker, LocalDate from, LocalDate to) {
        // Generate from the full demo history so a given day looks the same in every range.
        LocalDate latest = marketData.latestTradingDate();
        List<PriceBar> bars = marketData.dailyBars(ticker, latest.minusYears(2), latest);
        return DemoBrokerGenerator.generate(ticker.toUpperCase(), bars).stream()
                .filter(row -> !row.date().isBefore(from) && !row.date().isAfter(to))
                .toList();
    }
}
