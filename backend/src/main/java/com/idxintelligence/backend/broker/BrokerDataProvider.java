package com.idxintelligence.backend.broker;

import java.time.LocalDate;
import java.util.List;

/**
 * Source of broker summary data. Kept separate from MarketDataProvider because
 * broker data may come from a different provider than prices.
 */
public interface BrokerDataProvider {

    String sourceName();

    boolean isDemo();

    /** Broker summary rows for {@code ticker} between {@code from} and {@code to} inclusive. */
    List<BrokerDailyActivity> dailyActivity(String ticker, LocalDate from, LocalDate to);
}
