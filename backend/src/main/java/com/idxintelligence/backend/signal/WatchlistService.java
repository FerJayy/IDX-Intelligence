package com.idxintelligence.backend.signal;

import com.idxintelligence.backend.Reason;
import com.idxintelligence.backend.broker.BrokerAnalysisService;
import com.idxintelligence.backend.broker.BrokerDailyActivity;
import com.idxintelligence.backend.broker.BrokerDataProvider;
import com.idxintelligence.backend.broker.BrokerPosition;
import com.idxintelligence.backend.broker.DailyBrokerFlow;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Tonight's watchlist: every tracked stock scored on its latest close, strongest first.
 * Same scoring as the backtest, plus recent broker flow where available.
 */
@Service
public class WatchlistService {

    static final int MAX_CANDIDATES = 10;
    /** Broker days averaged for the live broker signal. */
    static final int BROKER_DAYS = 3;

    private final SignalDataRepository data;
    private final BrokerDataProvider brokerData;
    private final BacktestService backtest;

    public WatchlistService(SignalDataRepository data, BrokerDataProvider brokerData, BacktestService backtest) {
        this.data = data;
        this.brokerData = brokerData;
        this.backtest = backtest;
    }

    public Watchlist watchlist() {
        Map<String, SignalDataRepository.StockInfo> stocks = data.stocks();
        // ~90 calendar days covers the 25 trading days the features need, with room for holidays.
        Map<String, List<Bar>> bars = data.adjustedBars(LocalDate.now(BacktestService.JAKARTA).minusDays(90));
        LocalDate marketDate = bars.values().stream().filter(b -> !b.isEmpty())
                .map(b -> b.getLast().date()).max(Comparator.naturalOrder()).orElse(null);

        List<Candidate> listed = new ArrayList<>();
        List<Skipped> excluded = new ArrayList<>();
        List<String> noData = new ArrayList<>();
        int withBrokerData = 0;
        for (SignalDataRepository.StockInfo stock : stocks.values()) {
            List<Bar> series = bars.getOrDefault(stock.ticker(), List.of());
            if (series.isEmpty() || !series.getLast().date().equals(marketDate)) {
                noData.add(stock.ticker()); // no bar on the latest trading day (e.g. suspended)
                continue;
            }
            Features f = Features.at(series, series.size() - 1);
            if (f == null) {
                noData.add(stock.ticker());
                continue;
            }
            BrokerSignal broker = brokerSignal(stock.ticker(), marketDate);
            if (broker != null) withBrokerData++;
            MomentumScorer.Score score = MomentumScorer.score(f, broker);

            if (score.excludedBecause() != null) {
                if (score.points() >= MomentumScorer.MIN_SCORE) {
                    excluded.add(new Skipped(stock.ticker(), stock.name(), score.points(), score.excludedBecause()));
                }
            } else if (score.listed()) {
                listed.add(candidate(stock, f, broker, score));
            }
        }
        listed.sort(Comparator.comparingInt(Candidate::score).reversed());

        return new Watchlist(
                OffsetDateTime.now(BacktestService.JAKARTA),
                marketDate,
                marketDate == null ? null : nextWeekday(marketDate),
                stocks.size(),
                withBrokerData,
                MomentumScorer.MIN_SCORE,
                listed.stream().limit(MAX_CANDIDATES).toList(),
                excluded,
                noData,
                backtestHeadline());
    }

    /** Last {@link #BROKER_DAYS} broker days ending on the market date, or null if the latest day isn't imported. */
    private BrokerSignal brokerSignal(String ticker, LocalDate marketDate) {
        List<BrokerDailyActivity> rows = brokerData.dailyActivity(ticker, marketDate.minusDays(14), marketDate);
        List<DailyBrokerFlow> flows = BrokerAnalysisService.dailyFlows(rows);
        if (flows.size() < BROKER_DAYS || !flows.getLast().date().equals(marketDate)) return null;

        List<DailyBrokerFlow> recent = flows.subList(flows.size() - BROKER_DAYS, flows.size());
        LocalDate since = recent.getFirst().date();
        double avgConcentration = recent.stream().mapToDouble(d -> d.concentration().doubleValue()).average().orElse(0);
        BigDecimal foreign = recent.stream().anyMatch(d -> d.foreignNetValue() == null) ? null
                : recent.stream().map(DailyBrokerFlow::foreignNetValue).reduce(BigDecimal.ZERO, BigDecimal::add);

        BrokerPosition topBuyer = BrokerAnalysisService.positions(
                        rows.stream().filter(r -> !r.date().isBefore(since)).toList())
                .stream()
                .filter(p -> p.netValue().signum() > 0)
                .max(Comparator.comparing(BrokerPosition::netValue))
                .orElse(null);
        return new BrokerSignal(BROKER_DAYS, avgConcentration, foreign,
                topBuyer == null ? null : topBuyer.brokerCode(),
                topBuyer == null ? null : topBuyer.brokerName(),
                topBuyer == null ? null : topBuyer.buyAveragePrice());
    }

    private static Candidate candidate(SignalDataRepository.StockInfo stock, Features f, BrokerSignal broker,
                                       MomentumScorer.Score score) {
        return new Candidate(stock.ticker(), stock.name(), stock.sector(), score.points(),
                score.reasons(),
                price(f.close()), price(f.high()), price(f.low()), price(f.priorHigh20()), price(f.ma20()),
                Double.isNaN(f.volumeRatio()) ? null : round(f.volumeRatio(), 2),
                round(f.return1(), 4),
                broker == null ? null : broker.topBuyerCode(),
                broker == null ? null : broker.topBuyerName(),
                broker == null ? null : broker.topBuyerAvgPrice());
    }

    private BacktestHeadline backtestHeadline() {
        BacktestService.Result result = backtest.result();
        BacktestService.HorizonStats one = find(result, 1);
        BacktestService.HorizonStats five = find(result, 5);
        return new BacktestHeadline(result.from(), result.to(), BacktestService.Period.LAST_3_YEARS, one, five);
    }

    private static BacktestService.HorizonStats find(BacktestService.Result result, int days) {
        return result.horizons().stream()
                .filter(h -> h.horizonDays() == days && h.period() == BacktestService.Period.LAST_3_YEARS)
                .findFirst().orElse(null);
    }

    /** Holidays aren't known, so this is the next weekday; the UI calls it "next trading day". */
    static LocalDate nextWeekday(LocalDate date) {
        LocalDate next = date.plusDays(1);
        while (next.getDayOfWeek() == DayOfWeek.SATURDAY || next.getDayOfWeek() == DayOfWeek.SUNDAY) {
            next = next.plusDays(1);
        }
        return next;
    }

    private static BigDecimal price(double value) {
        return Double.isNaN(value) || Double.isInfinite(value) ? null : BigDecimal.valueOf(value).setScale(0, RoundingMode.HALF_UP);
    }

    private static Double round(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * @param close          latest close (adjusted for splits/dividends; on the latest day this equals the traded price)
     * @param priorHigh20    highest high of the 20 days before today: the breakout level
     * @param return1        today's change as a fraction
     * @param topBuyerAvgPrice average buy price of the largest net buyer over the last broker days
     */
    public record Candidate(String ticker, String name, String sector, int score, List<Reason> reasons,
                            BigDecimal close, BigDecimal dayHigh, BigDecimal dayLow, BigDecimal priorHigh20,
                            BigDecimal ma20, Double volumeRatio, Double return1,
                            String topBuyerCode, String topBuyerName, BigDecimal topBuyerAvgPrice) {
    }

    public record Skipped(String ticker, String name, int score, Reason reason) {
    }

    public record BacktestHeadline(LocalDate from, LocalDate to, BacktestService.Period period,
                                   BacktestService.HorizonStats nextDay, BacktestService.HorizonStats fiveDays) {
    }

    /**
     * @param basedOn        trading day whose close the scores use
     * @param forDay         next weekday (the next trading day unless it's a holiday)
     * @param withBrokerData stocks whose score includes broker flow
     * @param noData         tickers without a bar on the latest day or without enough history
     */
    public record Watchlist(OffsetDateTime generatedAt, LocalDate basedOn, LocalDate forDay, int scanned,
                            int withBrokerData, int minScore, List<Candidate> candidates, List<Skipped> excluded,
                            List<String> noData, BacktestHeadline backtest) {
    }
}
