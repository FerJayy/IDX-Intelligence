package com.idxintelligence.backend.signal;

import com.idxintelligence.backend.Reason;
import com.idxintelligence.backend.broker.BrokerDailyActivity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ShortlistTests {

    private static final LocalDate START = LocalDate.of(2026, 6, 1);
    private static final int DAYS = 300;   // enough for 200-day averages and a 52-week range

    /** AK (a foreign broker here) buys 600 net against six small sellers, or the mirror image. */
    private static List<BrokerDailyActivity> day(LocalDate d, boolean accumulation, double price) {
        List<BrokerDailyActivity> rows = new ArrayList<>();
        BigDecimal p = BigDecimal.valueOf(price);
        long ak = 600;
        rows.add(new BrokerDailyActivity(d, "AK", "AK", accumulation ? ak : 0, p.multiply(BigDecimal.valueOf(accumulation ? ak : 0)),
                accumulation ? 0 : ak, p.multiply(BigDecimal.valueOf(accumulation ? 0 : ak)),
                accumulation ? p.multiply(BigDecimal.valueOf(ak)) : BigDecimal.ZERO,
                accumulation ? BigDecimal.ZERO : p.multiply(BigDecimal.valueOf(ak))));
        for (String code : List.of("YP", "PD", "CC", "XL", "NI", "OD")) {
            long buy = accumulation ? 0 : 100;
            long sell = accumulation ? 100 : 0;
            rows.add(new BrokerDailyActivity(d, code, code, buy, p.multiply(BigDecimal.valueOf(buy)), sell,
                    p.multiply(BigDecimal.valueOf(sell)), BigDecimal.ZERO, BigDecimal.ZERO));
        }
        return rows;
    }

    @Test
    void anAccumulatedRisingLiquidStockPassesEveryCheckAndADistributedFallingOneDoesNot() {
        Map<String, String> names = new LinkedHashMap<>();
        names.put("GOOD", "Good");
        names.put("WEAK", "Weak");
        Map<String, List<Bar>> bars = new HashMap<>();
        Map<String, List<BrokerDailyActivity>> activity = new HashMap<>();
        Map<LocalDate, double[]> index = new HashMap<>();
        List<Bar> good = new ArrayList<>();
        List<Bar> weak = new ArrayList<>();
        List<BrokerDailyActivity> goodRows = new ArrayList<>();
        List<BrokerDailyActivity> weakRows = new ArrayList<>();
        for (int i = 0; i < DAYS; i++) {
            LocalDate d = START.plusDays(i);
            double up = 1000 + 2 * i;     // slow steady rise: above a rising MA20, never "chasing"
            double down = 2000 - 3 * i;
            good.add(new Bar(d, up, up * 1.01, up * 0.99, up, 50_000_000));
            weak.add(new Bar(d, down, down * 1.01, down * 0.99, down, 50_000_000));
            index.put(d, new double[]{7000, 7000});
            if (i >= DAYS - 40) {
                goodRows.addAll(day(d, true, up));
                weakRows.addAll(day(d, false, down));
            }
        }
        bars.put("GOOD", good);
        bars.put("WEAK", weak);
        activity.put("GOOD", goodRows);
        activity.put("WEAK", weakRows);

        ShortlistService.Shortlist list = ShortlistService.compute(names, Set.of(), activity, bars, index);

        ShortlistService.Candidate first = list.candidates().getFirst();
        assertThat(first.ticker()).isEqualTo("GOOD");
        assertThat(first.passed()).isEqualTo(ShortlistService.CHECKS);
        assertThat(first.checks()).extracting(Reason::code)
                .containsExactly("SL_LONG_TREND", "SL_NEAR_HIGH", "SL_STRENGTH", "SL_BROKERS", "SL_FOREIGN",
                        "SL_NOT_STRETCHED", "SL_LIQUID", "SL_SAFE");
        ShortlistService.Candidate second = list.candidates().get(1);
        assertThat(second.ticker()).isEqualTo("WEAK");
        // Downtrend, far from its high, lagging IHSG, brokers and foreigners selling: those fail
        // (below the buyers' cost isn't "stretched"; it is still liquid and not HSC).
        assertThat(second.checks()).filteredOn(c -> c.points() == 0).extracting(Reason::code)
                .containsExactly("SL_LONG_TREND", "SL_NEAR_HIGH", "SL_STRENGTH", "SL_BROKERS", "SL_FOREIGN");
        assertThat(list.record().groups()).extracting(RadarTrackRecordService.Group::group)
                .containsExactly("TOP_PICKS", "ALL_CHECKS", "ALMOST_ALL", "ALL");
        assertThat(list.record().signalDays()).isPositive();
    }

    @Test
    void anHscStockFailsTheSafetyCheck() {
        List<Bar> bars = new ArrayList<>();
        List<BrokerDailyActivity> rows = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            LocalDate d = START.plusDays(i);
            bars.add(new Bar(d, 1000, 1010, 990, 1000, 50_000_000));
            if (i >= 25) rows.addAll(day(d, true, 1000));
        }
        List<Reason> checks = ShortlistService.checks(rows, bars, Map.of(), START.plusDays(39), true);
        assertThat(checks).filteredOn(c -> c.code().equals("SL_SAFE")).singleElement()
                .extracting(Reason::points).isEqualTo(0);
    }
}
