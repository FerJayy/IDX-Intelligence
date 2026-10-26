package com.idxintelligence.backend.broker;

import com.idxintelligence.backend.IndonesianFormat;
import com.idxintelligence.backend.market.MarketDataProvider;
import com.idxintelligence.backend.market.MarketService;
import com.idxintelligence.backend.market.PriceRange;
import com.idxintelligence.backend.market.StockQuote;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Turns raw broker summary rows into per-broker totals, daily flow metrics and a
 * rule-based assessment. Works the same on demo and real data.
 */
@Service
public class BrokerAnalysisService {

    static final int TOP_N = 5;
    /** Fewer days than this and the assessment says so instead of guessing. */
    static final int MIN_DAYS_FOR_ASSESSMENT = 5;

    // Assessment thresholds. Heuristic starting points; to be validated by backtesting.
    static final BigDecimal CONCENTRATION_THRESHOLD = new BigDecimal("0.05");
    static final double CONCENTRATED_DAYS_HIGH = 0.60;
    static final double CONCENTRATED_DAYS_LOW = 0.40;

    private static final Locale INDONESIA = Locale.forLanguageTag("id-ID");

    private final BrokerDataProvider brokerData;
    private final MarketDataProvider marketData;
    private final MarketService marketService;

    public BrokerAnalysisService(BrokerDataProvider brokerData, MarketDataProvider marketData,
                                 MarketService marketService) {
        this.brokerData = brokerData;
        this.marketData = marketData;
        this.marketService = marketService;
    }

    public BrokerSummary summary(String ticker, PriceRange range) {
        StockQuote quote = marketService.stock(ticker).quote(); // 404 for unknown tickers
        LocalDate to = marketData.latestTradingDate();
        LocalDate from = range.startFrom(to);
        List<BrokerDailyActivity> rows = brokerData.dailyActivity(quote.ticker(), from, to);
        int tradingDays = marketData.dailyBars(quote.ticker(), from, to).size();

        List<BrokerPosition> positions = positions(rows);
        List<BrokerPosition> topBuyers = positions.stream()
                .filter(p -> p.netValue().signum() > 0)
                .sorted(Comparator.comparing(BrokerPosition::netValue).reversed())
                .limit(TOP_N)
                .toList();
        List<BrokerPosition> topSellers = positions.stream()
                .filter(p -> p.netValue().signum() < 0)
                .sorted(Comparator.comparing(BrokerPosition::netValue))
                .limit(TOP_N)
                .toList();
        List<DailyBrokerFlow> daily = dailyFlows(rows);

        return new BrokerSummary(
                brokerData.isDemo(),
                brokerData.sourceName(),
                brokerData.isDemo() ? MarketService.DEMO_NOTICE : null,
                OffsetDateTime.now(MarketService.JAKARTA),
                quote.ticker(),
                range.code(),
                from,
                to,
                tradingDays,
                daily.size(),
                quote.lastPrice(),
                assess(daily, topBuyers, quote.lastPrice()),
                topBuyers,
                topSellers,
                daily);
    }

    public static List<BrokerPosition> positions(List<BrokerDailyActivity> rows) {
        Map<String, List<BrokerDailyActivity>> byBroker = rows.stream()
                .collect(Collectors.groupingBy(BrokerDailyActivity::brokerCode, LinkedHashMap::new, Collectors.toList()));

        List<BrokerPosition> positions = new ArrayList<>();
        byBroker.forEach((code, brokerRows) -> {
            long buyVolume = brokerRows.stream().mapToLong(BrokerDailyActivity::buyVolume).sum();
            long sellVolume = brokerRows.stream().mapToLong(BrokerDailyActivity::sellVolume).sum();
            BigDecimal buyValue = sum(brokerRows.stream().map(BrokerDailyActivity::buyValue).toList());
            BigDecimal sellValue = sum(brokerRows.stream().map(BrokerDailyActivity::sellValue).toList());
            String name = brokerRows.stream().map(BrokerDailyActivity::brokerName)
                    .filter(Objects::nonNull).findFirst().orElse(null);
            positions.add(new BrokerPosition(code, name,
                    buyVolume, buyValue, averagePrice(buyValue, buyVolume),
                    sellVolume, sellValue, averagePrice(sellValue, sellVolume),
                    buyVolume - sellVolume, buyValue.subtract(sellValue),
                    sumIfAllKnown(brokerRows.stream().map(BrokerDailyActivity::foreignNetValue).toList())));
        });
        return positions;
    }

    public static List<DailyBrokerFlow> dailyFlows(List<BrokerDailyActivity> rows) {
        Map<LocalDate, List<BrokerDailyActivity>> byDate = rows.stream()
                .collect(Collectors.groupingBy(BrokerDailyActivity::date, TreeMap::new, Collectors.toList()));

        List<DailyBrokerFlow> flows = new ArrayList<>();
        BigDecimal cumulativeForeign = null;
        for (Map.Entry<LocalDate, List<BrokerDailyActivity>> day : byDate.entrySet()) {
            List<BrokerDailyActivity> dayRows = day.getValue();
            long totalVolume = dayRows.stream().mapToLong(BrokerDailyActivity::buyVolume).sum();
            List<Long> nets = dayRows.stream().map(BrokerDailyActivity::netVolume).sorted().toList();

            long top5Buy = nets.reversed().stream().filter(v -> v > 0).limit(TOP_N).mapToLong(Long::longValue).sum();
            long top5Sell = -nets.stream().filter(v -> v < 0).limit(TOP_N).mapToLong(Long::longValue).sum();
            BigDecimal concentration = totalVolume == 0 ? BigDecimal.ZERO
                    : BigDecimal.valueOf(top5Buy - top5Sell).divide(BigDecimal.valueOf(totalVolume), 4, RoundingMode.HALF_UP);

            BigDecimal foreignNet = sumIfAllKnown(dayRows.stream().map(BrokerDailyActivity::foreignNetValue).toList());
            if (foreignNet != null) {
                cumulativeForeign = cumulativeForeign == null ? foreignNet : cumulativeForeign.add(foreignNet);
            }

            flows.add(new DailyBrokerFlow(day.getKey(), totalVolume, top5Buy, top5Sell, concentration,
                    (int) nets.stream().filter(v -> v > 0).count(),
                    (int) nets.stream().filter(v -> v < 0).count(),
                    foreignNet, foreignNet == null ? null : cumulativeForeign));
        }
        return flows;
    }

    static FlowAssessment assess(List<DailyBrokerFlow> daily, List<BrokerPosition> topBuyers, BigDecimal lastPrice) {
        if (daily.size() < MIN_DAYS_FOR_ASSESSMENT) {
            return new FlowAssessment(FlowAssessment.Status.NEUTRAL, List.of(daily.isEmpty()
                    ? "No broker data for this period yet."
                    : "Only " + daily.size() + " trading day" + (daily.size() == 1 ? "" : "s")
                      + " of broker data so far; an assessment needs at least " + MIN_DAYS_FOR_ASSESSMENT + "."));
        }
        NumberFormat percent = NumberFormat.getPercentInstance(INDONESIA);
        percent.setMaximumFractionDigits(1);
        List<String> reasons = new ArrayList<>();
        int score = 0;

        // 1. Is net buying concentrated in a few brokers while selling is spread out (or the reverse)?
        BigDecimal avgConcentration = sum(daily.stream().map(DailyBrokerFlow::concentration).toList())
                .divide(BigDecimal.valueOf(daily.size()), 4, RoundingMode.HALF_UP);
        int concentrationBias = avgConcentration.compareTo(CONCENTRATION_THRESHOLD) > 0 ? 1
                : avgConcentration.compareTo(CONCENTRATION_THRESHOLD.negate()) < 0 ? -1 : 0;
        score += concentrationBias;
        reasons.add(switch (concentrationBias) {
            case 1 -> "Top 5 net buyers took in more than top 5 net sellers let go: average "
                    + signed(percent, avgConcentration) + " of daily volume (accumulation above +5%).";
            case -1 -> "Top 5 net sellers let go more than top 5 net buyers took in: average "
                    + signed(percent, avgConcentration) + " of daily volume (distribution below -5%).";
            default -> "Top 5 net buyers and sellers were roughly balanced: average "
                    + signed(percent, avgConcentration) + " of daily volume.";
        });

        // 2. On how many days were fewer brokers buying than selling (buying in fewer hands)?
        double concentratedShare = daily.stream().filter(d -> d.buyerCount() < d.sellerCount()).count()
                / (double) daily.size();
        int breadthBias = concentratedShare > CONCENTRATED_DAYS_HIGH ? 1
                : concentratedShare < CONCENTRATED_DAYS_LOW ? -1 : 0;
        score += breadthBias;
        reasons.add("Fewer brokers were net buying than net selling on " + percent.format(concentratedShare)
                + " of days" + switch (breadthBias) {
                    case 1 -> " — buying concentrated in fewer hands.";
                    case -1 -> " — selling concentrated in fewer hands.";
                    default -> ".";
                });

        // 3. Did foreign investors net buy or sell? Only days where foreign flow is known count.
        List<DailyBrokerFlow> foreignDays = daily.stream().filter(d -> d.foreignNetValue() != null).toList();
        if (foreignDays.isEmpty()) {
            reasons.add("Foreign investor flow is not available for this period yet.");
        } else {
            BigDecimal foreignNet = sum(foreignDays.stream().map(DailyBrokerFlow::foreignNetValue).toList());
            score += foreignNet.signum();
            String coverage = foreignDays.size() == daily.size() ? ""
                    : " (" + foreignDays.size() + " of " + daily.size() + " days with foreign data)";
            reasons.add("Foreign investors net " + (foreignNet.signum() >= 0 ? "bought" : "sold")
                    + " Rp" + IndonesianFormat.compact(foreignNet.abs()) + " over the period" + coverage + ".");
        }

        // Context only, not scored: where the main buyers' average cost sits versus today's price.
        BigDecimal buyValue = sum(topBuyers.stream().map(BrokerPosition::buyValue).toList());
        long buyVolume = topBuyers.stream().mapToLong(BrokerPosition::buyVolume).sum();
        BigDecimal buyersAverage = averagePrice(buyValue, buyVolume);
        if (buyersAverage != null && lastPrice != null) {
            BigDecimal gap = lastPrice.subtract(buyersAverage).divide(buyersAverage, 4, RoundingMode.HALF_UP);
            reasons.add("Top 5 buyers' average buy price is Rp" + NumberFormat.getIntegerInstance(INDONESIA).format(buyersAverage)
                    + "; the last price is " + percent.format(gap.abs()) + (gap.signum() >= 0 ? " above" : " below") + " it.");
        }

        FlowAssessment.Status status = score >= 2 ? FlowAssessment.Status.ACCUMULATION
                : score <= -2 ? FlowAssessment.Status.DISTRIBUTION
                : FlowAssessment.Status.NEUTRAL;
        return new FlowAssessment(status, reasons);
    }

    private static BigDecimal averagePrice(BigDecimal value, long volume) {
        return volume == 0 ? null : value.divide(BigDecimal.valueOf(volume), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Sum, or null if any value is missing (a partial sum would look like a real total). */
    private static BigDecimal sumIfAllKnown(List<BigDecimal> values) {
        return values.stream().anyMatch(Objects::isNull) ? null : sum(values);
    }

    private static String signed(NumberFormat percent, BigDecimal fraction) {
        return (fraction.signum() > 0 ? "+" : "") + percent.format(fraction);
    }
}
