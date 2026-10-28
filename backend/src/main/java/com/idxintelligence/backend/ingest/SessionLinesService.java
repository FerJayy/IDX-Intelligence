package com.idxintelligence.backend.ingest;

import com.idxintelligence.backend.market.IntradayLineSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The latest session's price line per stock (Yahoo spark, 20 stocks per request, free and outside the
 * IDX Edge quota) for the mini charts on the dashboard's daily lists. Cached per stock: 5 minutes while
 * the market trades, 60 minutes otherwise (the last session's line doesn't change then).
 */
@Service
public class SessionLinesService implements IntradayLineSource {

    private static final Logger log = LoggerFactory.getLogger(SessionLinesService.class);
    static final Duration IN_SESSION = Duration.ofMinutes(5);
    static final Duration OUT_OF_SESSION = Duration.ofMinutes(60);

    private final YahooFinanceClient yahoo;
    private final Clock clock;

    private record Cached(Instant fetchedAt, List<BigDecimal> line) {
    }

    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public SessionLinesService(YahooFinanceClient yahoo, Clock clock) {
        this.yahoo = yahoo;
        this.clock = clock;
    }

    @Override
    public Map<String, List<BigDecimal>> sessionLines(List<String> tickers) {
        ZonedDateTime now = clock.instant().atZone(YahooFinanceClient.JAKARTA);
        boolean session = LivePriceService.marketHours(now) && !MarketSession.lunchBreak(now);
        Duration maxAge = session ? IN_SESSION : OUT_OF_SESSION;
        Map<String, List<BigDecimal>> result = new HashMap<>();
        List<String> missing = new ArrayList<>();
        for (String ticker : tickers.stream().distinct().toList()) {
            Cached c = cache.get(ticker);
            if (c != null && c.fetchedAt().plus(maxAge).isAfter(clock.instant())) {
                if (!c.line().isEmpty()) result.put(ticker, c.line());
            } else {
                missing.add(ticker);
            }
        }
        for (int i = 0; i < missing.size(); i += YahooFinanceClient.SPARK_BATCH) {
            List<String> batch = missing.subList(i, Math.min(i + YahooFinanceClient.SPARK_BATCH, missing.size()));
            try {
                Map<String, List<BigDecimal>> lines = yahoo.sessionLines(batch.stream().map(t -> t + ".JK").toList());
                for (String ticker : batch) {
                    List<BigDecimal> line = lines.getOrDefault(ticker + ".JK", List.of());
                    cache.put(ticker, new Cached(clock.instant(), line));
                    if (!line.isEmpty()) result.put(ticker, line);
                }
            } catch (RuntimeException e) {
                log.warn("Session lines from Yahoo failed: {}", e.getMessage());
            }
        }
        if (cache.size() > 3_000) cache.clear();
        return result;
    }
}
