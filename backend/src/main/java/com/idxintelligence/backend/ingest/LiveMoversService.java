package com.idxintelligence.backend.ingest;

import com.idxintelligence.backend.market.MarketDataProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Today's top gainers, losers and most traded stocks while today's end-of-day prices aren't imported
 * yet (from the session start until the evening import). Two provider requests per refresh, shared by
 * every visitor, refreshed at most every 10 minutes in the session, with its own small daily budget.
 */
@Service
public class LiveMoversService {

    private static final Logger log = LoggerFactory.getLogger(LiveMoversService.class);
    static final LocalTime FROM = LocalTime.of(9, 0);
    static final LocalTime SESSION_END = LocalTime.of(16, 15);
    static final Duration REFRESH_IN_SESSION = Duration.ofMinutes(10);
    static final Duration REFRESH_AFTER_CLOSE = Duration.ofMinutes(60);
    /** Same bar as the end-of-day movers (MarketService.MIN_MOVER_VALUE): below it a stock is tagged "thin". */
    static final BigDecimal THIN_VALUE = new BigDecimal("1000000000");

    private final IdxEdgeClient client;
    private final JdbcClient jdbc;
    private final MarketDataProvider marketData;
    private final Clock clock;
    private final int dailyBudget;
    private LocalDate budgetDay;
    private int usedToday;
    private LiveMovers cached;
    private Instant cachedAt;

    public LiveMoversService(IdxEdgeClient client, JdbcClient jdbc, MarketDataProvider marketData, Clock clock,
                             @Value("${live.movers-daily-budget:80}") int dailyBudget) {
        this.client = client;
        this.jdbc = jdbc;
        this.marketData = marketData;
        this.clock = clock;
        this.dailyBudget = dailyBudget;
    }

    /** @param thin less than Rp1 miliar traded today: the move may not mean much */
    public record LiveMover(String ticker, String name, boolean hsc, BigDecimal lastPrice, BigDecimal change,
                            BigDecimal changePercent, BigDecimal value, boolean thin) {
    }

    /**
     * @param available false outside the window (weekends, before 09:00, or once today's close is imported):
     *                  the end-of-day overview applies then
     */
    /** @param sparklines per ticker: the last 20 closes, oldest first, ending with today's live price */
    public record LiveMovers(boolean available, OffsetDateTime fetchedAt, String marketStatus, String marketLabel,
                             List<LiveMover> gainers, List<LiveMover> losers, List<LiveMover> mostActive,
                             Map<String, List<BigDecimal>> sparklines) {
        static final LiveMovers NONE = new LiveMovers(false, null, null, null, List.of(), List.of(), List.of(), Map.of());
    }

    public synchronized LiveMovers movers() {
        ZonedDateTime now = clock.instant().atZone(YahooFinanceClient.JAKARTA);
        if (!client.isConfigured() || !inWindow(now)) return LiveMovers.NONE;

        Duration refresh = now.toLocalTime().isBefore(SESSION_END) ? REFRESH_IN_SESSION : REFRESH_AFTER_CLOSE;
        boolean fresh = cached != null && cachedAt.plus(refresh).isAfter(clock.instant())
                && cachedAt.atZone(YahooFinanceClient.JAKARTA).toLocalDate().equals(now.toLocalDate());
        if (fresh) return cached;
        if (!takeFromBudget(now, 2)) return cached != null ? cached : LiveMovers.NONE;
        try {
            IdxEdgeClient.MoversPage byChange = client.movers("chg");
            IdxEdgeClient.MoversPage byValue = client.movers("value");
            cached = build(byChange, byValue, now.toOffsetDateTime());
            cachedAt = clock.instant();
            return cached;
        } catch (RuntimeException e) {
            log.warn("Live movers failed: {}", e.getMessage());
            return cached != null ? cached : LiveMovers.NONE;
        }
    }

    /** Weekdays from 09:00 until today's end-of-day prices are in the database. */
    private boolean inWindow(ZonedDateTime now) {
        DayOfWeek day = now.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY || now.toLocalTime().isBefore(FROM)) return false;
        LocalDate stored = jdbc.sql("SELECT max(trade_date) FROM daily_price").query(LocalDate.class).optional().orElse(null);
        return stored == null || stored.isBefore(now.toLocalDate());
    }

    private LiveMovers build(IdxEdgeClient.MoversPage byChange, IdxEdgeClient.MoversPage byValue, OffsetDateTime at) {
        Map<String, String> names = new HashMap<>();
        Set<String> hsc = new HashSet<>();
        jdbc.sql("SELECT s.ticker, s.name, (h.ticker IS NOT NULL) AS hsc FROM stock s LEFT JOIN hsc_stock h ON h.ticker = s.ticker")
                .query((rs, n) -> {
                    names.put(rs.getString("ticker"), rs.getString("name"));
                    if (rs.getBoolean("hsc")) hsc.add(rs.getString("ticker"));
                    return null;
                })
                .list();
        IdxEdgeClient.MarketState market = byChange.market() != null ? byChange.market() : byValue.market();
        List<LiveMover> gainers = convert(byChange.gainers(), names, hsc);
        List<LiveMover> losers = convert(byChange.losers(), names, hsc);
        List<LiveMover> active = convert(byValue.top(), names, hsc);
        return new LiveMovers(true, at, market == null ? null : market.status(), market == null ? null : market.label(),
                gainers, losers, active, sparklines(Stream.of(gainers, losers, active).flatMap(List::stream).toList()));
    }

    /** The stored closes before today, plus today's live price, so the line ends where the list says. */
    private Map<String, List<BigDecimal>> sparklines(List<LiveMover> movers) {
        List<String> tickers = movers.stream().map(LiveMover::ticker).distinct().toList();
        Map<String, List<BigDecimal>> closes = tickers.isEmpty() ? Map.of() : marketData.recentCloses(tickers, SPARKLINE_DAYS - 1);
        Map<String, List<BigDecimal>> result = new HashMap<>();
        for (LiveMover m : movers) {
            if (result.containsKey(m.ticker())) continue;
            List<BigDecimal> line = new ArrayList<>(closes.getOrDefault(m.ticker(), List.of()));
            if (m.lastPrice() != null) line.add(m.lastPrice());
            result.put(m.ticker(), line);
        }
        return result;
    }

    static final int SPARKLINE_DAYS = 20;

    /** Only listed four-letter stocks we know (the provider's list can include rights and warrants). */
    private static List<LiveMover> convert(List<IdxEdgeClient.Mover> movers, Map<String, String> names, Set<String> hsc) {
        if (movers == null) return List.of();
        return movers.stream()
                .filter(m -> m.code() != null && names.containsKey(m.code()))
                .map(m -> new LiveMover(m.code(), names.get(m.code()), hsc.contains(m.code()), m.lastPrice(), m.change(),
                        m.changePercent(), m.value(), m.value() == null || m.value().compareTo(THIN_VALUE) < 0))
                .toList();
    }

    private boolean takeFromBudget(ZonedDateTime now, int requests) {
        if (!now.toLocalDate().equals(budgetDay)) {
            budgetDay = now.toLocalDate();
            usedToday = 0;
        }
        if (usedToday + requests > dailyBudget) return false;
        usedToday += requests;
        return true;
    }
}
