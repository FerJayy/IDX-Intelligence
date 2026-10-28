package com.idxintelligence.backend.signal;

import com.idxintelligence.backend.Reason;
import com.idxintelligence.backend.broker.BrokerAnalysisService;
import com.idxintelligence.backend.broker.BrokerDailyActivity;
import com.idxintelligence.backend.broker.BrokerDataProvider;
import com.idxintelligence.backend.broker.BrokerPosition;
import com.idxintelligence.backend.broker.DailyBrokerFlow;
import com.idxintelligence.backend.broker.FlowAssessment;
import com.idxintelligence.backend.market.MarketService;
import com.idxintelligence.backend.market.StockQuote;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The short, plain-language overview at the top of a stock page: broker flow over three periods side
 * by side (so a 10-day accumulation and a balanced 3 months don't look like a contradiction) and a
 * handful of facts. Describes what happened; none of it is a forecast.
 */
@Service
public class StockSummaryService {

    /** The periods shown side by side; 10D matches the Broker Radar's default window. */
    public static final List<String> PERIODS = List.of("10D", "1M", "3M");
    static final int RADAR_DAYS = 10;
    static final int BARS_1M = 21;
    static final int BARS_3M = 63;
    static final int BARS_52W = 250;

    private final MarketService market;
    private final BrokerDataProvider brokerData;
    private final SignalDataRepository prices;
    private final JdbcClient jdbc;

    public StockSummaryService(MarketService market, BrokerDataProvider brokerData, SignalDataRepository prices,
                               JdbcClient jdbc) {
        this.market = market;
        this.brokerData = brokerData;
        this.prices = prices;
        this.jdbc = jdbc;
    }

    /** @param brokerDays days with broker data in the period; under 5 the status says too few days */
    public record FlowPeriod(String period, FlowAssessment.Status status, int brokerDays) {
    }

    /** Recent range: support/resistance = last month's low/high; the 52-week range for context. */
    public record Levels(BigDecimal last, BigDecimal monthLow, BigDecimal monthHigh, BigDecimal yearLow,
                         BigDecimal yearHigh) {
    }

    /**
     * @param facts plain-language facts; points +1 = supportive, -1 = a warning, 0 = neutral context
     */
    public record Summary(String ticker, LocalDate dataDate, boolean brokerTracked, List<FlowPeriod> flows,
                          List<Reason> facts, Levels levels) {
    }

    record InsiderTrade(LocalDate date, String action) {
    }

    public Summary summary(String ticker) {
        StockQuote quote = market.stock(ticker).quote();   // 404 for unknown tickers
        LocalDate latest = quote.tradeDate() != null ? quote.tradeDate() : LocalDate.now(MarketService.JAKARTA);
        List<Bar> bars = prices.adjustedBars(quote.ticker(), latest.minusDays(400));
        Map<LocalDate, double[]> index = prices.compositeIndex(latest.minusDays(130));
        List<BrokerDailyActivity> activity = quote.brokerTracked()
                ? brokerData.dailyActivity(quote.ticker(), latest.minusMonths(3).minusDays(5), latest)
                : List.of();
        List<InsiderTrade> insiders = jdbc.sql("""
                SELECT trade_date, action FROM insider_transaction WHERE ticker = ? AND trade_date >= ?
                """)
                .params(quote.ticker(), Date.valueOf(latest.minusDays(30)))
                .query((rs, n) -> new InsiderTrade(rs.getDate("trade_date").toLocalDate(), rs.getString("action")))
                .list();
        return compute(quote.ticker(), quote.brokerTracked(), quote.hsc(), latest, bars, index, activity, insiders);
    }

    static Summary compute(String ticker, boolean tracked, boolean hsc, LocalDate latest, List<Bar> bars,
                           Map<LocalDate, double[]> index, List<BrokerDailyActivity> activity,
                           List<InsiderTrade> insiders) {
        List<Reason> facts = new ArrayList<>();
        BigDecimal last = bars.isEmpty() ? null : price(bars.getLast().close());
        Features f = bars.isEmpty() ? null : Features.at(bars, bars.size() - 1);

        // Trend: the same "above a rising 20-day average" test the Radar and the backtest use.
        if (f != null) {
            if (f.aboveRisingMa20()) {
                facts.add(Reason.of("SUM_TREND_UP", 1, "ma20", Math.round(f.ma20())));
            } else if (f.close() < f.ma20() && !f.ma20Rising()) {
                facts.add(Reason.of("SUM_TREND_DOWN", -1, "ma20", Math.round(f.ma20())));
            } else {
                facts.add(Reason.of("SUM_TREND_SIDEWAYS", 0, "ma20", Math.round(f.ma20())));
            }
        }

        // Broker flow per period, assessed exactly like the stock page and the Radar.
        List<FlowPeriod> flows = new ArrayList<>();
        if (tracked && !activity.isEmpty()) {
            List<LocalDate> days = activity.stream().map(BrokerDailyActivity::date).distinct().sorted().toList();
            LocalDate tenDaysFrom = days.get(Math.max(0, days.size() - RADAR_DAYS));
            flows.add(flow("10D", activity, tenDaysFrom, last));
            flows.add(flow("1M", activity, latest.minusMonths(1), last));
            flows.add(flow("3M", activity, latest.minusMonths(3), last));

            List<DailyBrokerFlow> threeMonths = BrokerAnalysisService.dailyFlows(since(activity, latest.minusMonths(3)));
            List<DailyBrokerFlow> foreignKnown = threeMonths.stream().filter(d -> d.foreignNetValue() != null).toList();
            if (foreignKnown.size() >= 5) {
                BigDecimal net = foreignKnown.stream().map(DailyBrokerFlow::foreignNetValue)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                if (net.signum() != 0) {
                    facts.add(Reason.of(net.signum() > 0 ? "SUM_FOREIGN_BUYING" : "SUM_FOREIGN_SELLING",
                            net.signum(), "value", net, "days", foreignKnown.size()));
                }
            }

            // What the biggest buyers of the last month paid, against today's price.
            List<BrokerPosition> buyers = topBuyers(since(activity, latest.minusMonths(1)));
            BigDecimal cost = averageBuyPrice(buyers);
            if (cost != null && last != null) {
                BigDecimal gap = last.divide(cost, 4, RoundingMode.HALF_UP).subtract(BigDecimal.ONE);
                double g = gap.doubleValue();
                if (g > 0.10) {
                    facts.add(Reason.of("SUM_FAR_ABOVE_BUYERS_COST", -1, "gap", gap, "averagePrice", cost));
                } else if (g < -0.05) {
                    facts.add(Reason.of("SUM_BELOW_BUYERS_COST", 0, "gap", gap, "averagePrice", cost));
                } else {
                    facts.add(Reason.of("SUM_NEAR_BUYERS_COST", 0, "gap", gap, "averagePrice", cost));
                }
            }
        }

        // Against the market over 1 and 3 months (close to close, both adjusted).
        Double excess1m = excess(bars, index, BARS_1M);
        Double excess3m = excess(bars, index, BARS_3M);
        if (excess3m != null || excess1m != null) {
            double main = excess3m != null ? excess3m : excess1m;
            facts.add(Reason.of("SUM_VS_IHSG", main > 0.02 ? 1 : main < -0.02 ? -1 : 0,
                    "excess1m", excess1m, "excess3m", excess3m));
        }

        if (f != null && !Double.isNaN(f.volumeRatio())) {
            if (f.volumeRatio() >= 2) {
                facts.add(Reason.of("SUM_VOLUME_HIGH", 0, "ratio", f.volumeRatio()));
            } else if (f.volumeRatio() <= 0.5) {
                facts.add(Reason.of("SUM_VOLUME_LOW", 0, "ratio", f.volumeRatio()));
            }
        }

        Levels levels = null;
        if (!bars.isEmpty()) {
            List<Bar> month = bars.subList(Math.max(0, bars.size() - BARS_1M), bars.size());
            List<Bar> year = bars.subList(Math.max(0, bars.size() - BARS_52W), bars.size());
            double yearHigh = year.stream().mapToDouble(StockSummaryService::high).max().orElse(Double.NaN);
            double yearLow = year.stream().mapToDouble(StockSummaryService::low).min().orElse(Double.NaN);
            levels = new Levels(last,
                    price(month.stream().mapToDouble(StockSummaryService::low).min().orElse(Double.NaN)),
                    price(month.stream().mapToDouble(StockSummaryService::high).max().orElse(Double.NaN)),
                    price(yearLow), price(yearHigh));
            double close = bars.getLast().close();
            if (year.size() >= 120 && close >= yearHigh * 0.95) {
                facts.add(Reason.of("SUM_NEAR_52W_HIGH", 0, "high", price(yearHigh), "gap", close / yearHigh - 1));
            } else if (year.size() >= 120 && close <= yearLow * 1.10) {
                facts.add(Reason.of("SUM_NEAR_52W_LOW", 0, "low", price(yearLow), "gap", close / yearLow - 1));
            }
        }

        long bought = insiders.stream().filter(t -> "buy".equals(t.action())).count();
        long sold = insiders.stream().filter(t -> "sell".equals(t.action())).count();
        if (bought > 0 || sold > 0) {
            facts.add(Reason.of(bought >= sold ? "SUM_INSIDER_BUYING" : "SUM_INSIDER_SELLING", bought >= sold ? 1 : 0,
                    "bought", bought, "sold", sold));
        }

        if (hsc) facts.add(Reason.of("SUM_HSC", -1));
        return new Summary(ticker, latest, tracked, flows, facts, levels);
    }

    private static FlowPeriod flow(String period, List<BrokerDailyActivity> activity, LocalDate from, BigDecimal last) {
        List<BrokerDailyActivity> rows = since(activity, from);
        List<DailyBrokerFlow> daily = BrokerAnalysisService.dailyFlows(rows);
        FlowAssessment assessment = BrokerAnalysisService.assess(daily, topBuyers(rows), last);
        return new FlowPeriod(period, assessment.status(), daily.size());
    }

    private static List<BrokerDailyActivity> since(List<BrokerDailyActivity> rows, LocalDate from) {
        return rows.stream().filter(r -> !r.date().isBefore(from)).toList();
    }

    private static List<BrokerPosition> topBuyers(List<BrokerDailyActivity> rows) {
        return BrokerAnalysisService.positions(rows).stream()
                .filter(p -> p.netValue().signum() > 0)
                .sorted(Comparator.comparing(BrokerPosition::netValue).reversed())
                .limit(5)
                .toList();
    }

    private static BigDecimal averageBuyPrice(List<BrokerPosition> buyers) {
        long volume = buyers.stream().mapToLong(BrokerPosition::buyVolume).sum();
        if (volume == 0) return null;
        BigDecimal value = buyers.stream().map(BrokerPosition::buyValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        return value.divide(BigDecimal.valueOf(volume), 0, RoundingMode.HALF_UP);
    }

    /** Stock return minus IHSG return over the last {@code n} bars; null without enough data. */
    private static Double excess(List<Bar> bars, Map<LocalDate, double[]> index, int n) {
        // End on the latest day both series have (the IHSG import can lag a day behind the stocks).
        int endIndex = bars.size() - 1;
        while (endIndex >= 0 && endIndex >= bars.size() - 5 && !index.containsKey(bars.get(endIndex).date())) endIndex--;
        if (endIndex < n || !index.containsKey(bars.get(endIndex).date())) return null;
        Bar start = bars.get(endIndex - n);
        Bar end = bars.get(endIndex);
        double[] ihsgStart = index.get(start.date());
        double[] ihsgEnd = index.get(end.date());
        if (ihsgStart == null || ihsgEnd == null || ihsgStart[1] <= 0 || start.close() <= 0) return null;
        return (end.close() / start.close() - 1) - (ihsgEnd[1] / ihsgStart[1] - 1);
    }

    private static double high(Bar b) {
        return Double.isNaN(b.high()) ? b.close() : b.high();
    }

    private static double low(Bar b) {
        return Double.isNaN(b.low()) ? b.close() : b.low();
    }

    private static BigDecimal price(double value) {
        return Double.isNaN(value) ? null : BigDecimal.valueOf(value).setScale(0, RoundingMode.HALF_UP);
    }
}
