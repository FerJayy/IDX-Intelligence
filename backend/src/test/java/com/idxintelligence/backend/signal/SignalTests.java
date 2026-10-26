package com.idxintelligence.backend.signal;

import com.idxintelligence.backend.Reason;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SignalTests {

    private static final LocalDate START = LocalDate.of(2026, 1, 1);

    /** 30 quiet days at 1,000 with 10 M shares a day, then a breakout day: close 1,060 on 25 M shares, near the high. */
    private static List<Bar> breakoutSeries() {
        List<Bar> bars = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            double close = 1000 + (i % 2 == 0 ? 5 : -5);
            bars.add(new Bar(START.plusDays(i), close, close + 10, close - 10, close, 10_000_000));
        }
        bars.add(new Bar(START.plusDays(30), 1005, 1065, 1000, 1060, 25_000_000));
        return bars;
    }

    @Test
    void featuresDescribeTheBreakoutDay() {
        List<Bar> bars = breakoutSeries();
        Features f = Features.at(bars, bars.size() - 1);

        assertThat(f.breakout()).isTrue();
        assertThat(f.priorHigh20()).isEqualTo(1015);
        assertThat(f.volumeRatio()).isCloseTo(2.5, within(1e-9));
        assertThat(f.closeLocation()).isCloseTo(60.0 / 65, within(1e-9));
        assertThat(f.return1()).isCloseTo(1060.0 / 995 - 1, within(1e-9));
    }

    @Test
    void featuresNeverLookAtLaterBars() {
        List<Bar> bars = breakoutSeries();
        Features before = Features.at(bars, 29);
        bars.add(new Bar(START.plusDays(31), 2000, 3000, 100, 2500, 999_000_000)); // wild future bar

        assertThat(Features.at(bars, 29)).isEqualTo(before);
    }

    @Test
    void notEnoughHistoryGivesNoFeatures() {
        assertThat(Features.at(breakoutSeries(), 10)).isNull();
    }

    @Test
    void breakoutWithVolumeScoresHighWithReasons() {
        List<Bar> bars = breakoutSeries();
        MomentumScorer.Score score = MomentumScorer.score(Features.at(bars, bars.size() - 1), null);

        // breakout 25 + volume >= 2x 25 + close near high 15 + up today 10 + above rising 20-day average 15
        // (the breakout day itself lifts the average above its level 5 days earlier)
        assertThat(score.points()).isEqualTo(90);
        assertThat(score.reasons()).extracting(Reason::code)
                .containsExactly("BREAKOUT_20D", "VOLUME_SPIKE", "CLOSE_NEAR_HIGH", "UP_TODAY", "ABOVE_RISING_MA20");
        assertThat(score.reasons().get(1).values().get("ratio")).isEqualTo(2.5);
        assertThat(score.listed()).isTrue();
    }

    @Test
    void brokerFlowAddsOrSubtractsPoints() {
        List<Bar> bars = breakoutSeries();
        Features f = Features.at(bars, bars.size() - 1);

        int buying = MomentumScorer.score(f, new BrokerSignal(3, 0.08, BigDecimal.valueOf(5_000_000_000L), "YP", null, null)).points();
        int selling = MomentumScorer.score(f, new BrokerSignal(3, -0.08, BigDecimal.valueOf(-5_000_000_000L), "YP", null, null)).points();

        assertThat(buying).isEqualTo(110);
        assertThat(selling).isEqualTo(70);
    }

    @Test
    void illiquidStocksAreExcludedEvenWithAHighScore() {
        List<Bar> bars = new ArrayList<>();
        for (Bar b : breakoutSeries()) {
            bars.add(new Bar(b.date(), b.open(), b.high(), b.low(), b.close(), b.volume() / 1000)); // ~Rp10 jt/day
        }
        MomentumScorer.Score score = MomentumScorer.score(Features.at(bars, bars.size() - 1), null);

        assertThat(score.points()).isEqualTo(90);
        assertThat(score.excludedBecause().code()).isEqualTo("LOW_LIQUIDITY");
        assertThat(score.listed()).isFalse();
    }

    @Test
    void backtestBuysNextOpenAndSubtractsCostsAndIhsg() {
        List<Bar> bars = breakoutSeries();
        // Day after the breakout: open 1,000, close 1,050 (+5%). Then four flat days at 1,050.
        bars.add(new Bar(START.plusDays(31), 1000, 1060, 995, 1050, 10_000_000));
        for (int i = 32; i < 36; i++) {
            bars.add(new Bar(START.plusDays(i), 1050, 1055, 1045, 1050, 10_000_000));
        }
        Map<LocalDate, double[]> ihsg = new HashMap<>();
        for (Bar b : bars) {
            ihsg.put(b.date(), new double[]{7000, 7000}); // flat market
        }
        ihsg.put(START.plusDays(31), new double[]{7000, 7070}); // IHSG +1% on the entry day

        BacktestService.Result result = BacktestService.run(Map.of("TEST", bars), ihsg, bars.getLast().date());

        BacktestService.HorizonStats nextDay = result.horizons().stream()
                .filter(h -> h.horizonDays() == 1 && h.period() == BacktestService.Period.ALL).findFirst().orElseThrow();
        assertThat(nextDay.picks()).isEqualTo(1); // only the breakout day scores >= 50
        // +5% - 0,4% costs - IHSG's +1%
        assertThat(nextDay.avgExcess()).isCloseTo(0.05 - BacktestService.ROUND_TRIP_COST - 0.01, within(1e-9));

        BacktestService.HorizonStats fiveDays = result.horizons().stream()
                .filter(h -> h.horizonDays() == 5 && h.period() == BacktestService.Period.ALL).findFirst().orElseThrow();
        // Entry at 1,000 open, exit at 1,050 close 5 days later; IHSG from 7.000 open to 7.000 close.
        assertThat(fiveDays.avgExcess()).isCloseTo(0.05 - BacktestService.ROUND_TRIP_COST, within(1e-9));
    }

    @Test
    void nextWeekdaySkipsTheWeekend() {
        assertThat(WatchlistService.nextWeekday(LocalDate.of(2026, 10, 23))).isEqualTo(LocalDate.of(2026, 10, 26));
        assertThat(WatchlistService.nextWeekday(LocalDate.of(2026, 10, 26))).isEqualTo(LocalDate.of(2026, 10, 27));
    }
}
