package com.idxintelligence.backend.signal;

import com.idxintelligence.backend.broker.BrokerDailyActivity;
import com.idxintelligence.backend.broker.FlowAssessment;
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
import static org.assertj.core.api.Assertions.within;

class RadarTrackRecordTests {

    private static final LocalDate START = LocalDate.of(2026, 6, 1);
    private static final int DAYS = 60;
    private static final int FIRST_BROKER_DAY = 30;

    private static BrokerDailyActivity row(LocalDate day, String code, long buy, long sell, double price) {
        return new BrokerDailyActivity(day, code, "Broker " + code, buy, BigDecimal.valueOf(buy * price), sell,
                BigDecimal.valueOf(sell * price), BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /** AK buys 600 net against six small sellers (accumulation), or the mirror image. */
    private static List<BrokerDailyActivity> day(LocalDate day, boolean accumulation, double price) {
        List<BrokerDailyActivity> rows = new ArrayList<>();
        rows.add(row(day, "AK", accumulation ? 600 : 0, accumulation ? 0 : 600, price));
        for (String code : List.of("YP", "PD", "CC", "XL", "NI", "OD")) {
            rows.add(row(day, code, accumulation ? 0 : 100, accumulation ? 100 : 0, price));
        }
        return rows;
    }

    /** Consecutive days (calendar days stand in for trading days); open = close = price. */
    private static List<Bar> bars(double start, double step) {
        List<Bar> bars = new ArrayList<>();
        for (int i = 0; i < DAYS; i++) {
            double p = start + step * i;
            bars.add(new Bar(START.plusDays(i), p, p * 1.01, p * 0.99, p, 50_000_000));
        }
        return bars;
    }

    private record Data(Map<String, String> names, Map<String, List<BrokerDailyActivity>> activity,
                        Map<String, List<Bar>> bars, Map<LocalDate, double[]> index) {
        /** Everything after {@code cutoff} removed, as if today were {@code cutoff}. */
        Data until(LocalDate cutoff) {
            Map<String, List<BrokerDailyActivity>> a = new HashMap<>();
            activity.forEach((t, rows) -> a.put(t, rows.stream().filter(r -> !r.date().isAfter(cutoff)).toList()));
            Map<String, List<Bar>> b = new HashMap<>();
            bars.forEach((t, rows) -> b.put(t, rows.stream().filter(r -> !r.date().isAfter(cutoff)).toList()));
            return new Data(names, a, b, index);
        }
    }

    private static Data data() {
        Map<String, String> names = new LinkedHashMap<>();
        Map<String, List<BrokerDailyActivity>> activity = new HashMap<>();
        Map<String, List<Bar>> bars = new HashMap<>();
        // UPUP is accumulated and rises; DOWN is distributed and falls; MIXD flips every few days.
        names.put("UPUP", "Rising");
        names.put("DOWN", "Falling");
        names.put("MIXD", "Mixed");
        bars.put("UPUP", bars(1000, 10));
        bars.put("DOWN", bars(2000, -10));
        bars.put("MIXD", bars(1500, 0));
        for (var e : names.keySet()) activity.put(e, new ArrayList<>());
        for (int i = FIRST_BROKER_DAY; i < DAYS; i++) {
            LocalDate d = START.plusDays(i);
            activity.get("UPUP").addAll(day(d, true, bars.get("UPUP").get(i).close()));
            activity.get("DOWN").addAll(day(d, false, bars.get("DOWN").get(i).close()));
            activity.get("MIXD").addAll(day(d, (i / 3) % 2 == 0, 1500));
        }
        Map<LocalDate, double[]> index = new HashMap<>();
        for (int i = 0; i < DAYS; i++) index.put(START.plusDays(i), new double[]{7000, 7000});   // flat IHSG
        return new Data(names, activity, bars, index);
    }

    private static List<RadarTrackRecordService.Observation> observations(Data d) {
        return RadarTrackRecordService.observations(5, d.names(), d.activity(), Set.of(), d.bars(), d.index());
    }

    @Test
    void scoresOnlyUseDataKnownOnTheDay() {
        Data full = data();
        LocalDate cutoff = START.plusDays(45);
        List<RadarTrackRecordService.Observation> withFuture = observations(full);
        List<RadarTrackRecordService.Observation> withoutFuture = observations(full.until(cutoff));

        // Every day that both runs scored (the cut run lacks a next-day entry for its last day) must match.
        assertThat(withoutFuture).isNotEmpty();
        for (var past : withoutFuture) {
            var same = withFuture.stream()
                    .filter(o -> o.date().equals(past.date()) && o.ticker().equals(past.ticker())).findFirst().orElseThrow();
            assertThat(same.score()).as(past.ticker() + " " + past.date()).isEqualTo(past.score());
            assertThat(same.status()).isEqualTo(past.status());
        }
    }

    @Test
    void entersAtTheNextOpenAndSubtractsCostsAndTheIndex() {
        Data d = data();
        var obs = observations(d).stream()
                .filter(o -> o.ticker().equals("UPUP") && o.date().equals(START.plusDays(40))).findFirst().orElseThrow();
        // Signal on day 40 (close 1,400): buy day 41's open (1,410), sell day 41's close (1,410) for +1d.
        assertThat(obs.net()[0]).isCloseTo(1410.0 / 1410 - 1 - 0.004, within(1e-9));
        // +5d sells day 45's close (1,450).
        assertThat(obs.net()[2]).isCloseTo(1450.0 / 1410 - 1 - 0.004, within(1e-9));
        assertThat(obs.excess()[2]).isCloseTo(obs.net()[2], within(1e-9));   // IHSG flat
        assertThat(obs.status()).isEqualTo(FlowAssessment.Status.ACCUMULATION);
    }

    @Test
    void groupsAndSampleSize() {
        Data d = data();
        RadarTrackRecordService.TrackRecord record = RadarTrackRecordService.compute(5, d.names(), d.activity(), Set.of(),
                d.bars(), d.index());

        // Broker days 30..59 with a 5-day window from day 34; the last day has no next-day entry.
        assertThat(record.signalDays()).isEqualTo(25);
        assertThat(record.stocks()).isEqualTo(3);
        var all = record.groups().stream().filter(g -> g.group().equals("ALL")).findFirst().orElseThrow();
        assertThat(all.horizons().getFirst().trades()).isEqualTo(75);
        var accumulation = record.groups().stream().filter(g -> g.group().equals("ACCUMULATION")).findFirst().orElseThrow();
        var distribution = record.groups().stream().filter(g -> g.group().equals("DISTRIBUTION")).findFirst().orElseThrow();
        // In this made-up data accumulation precedes rises, so its average must beat distribution's.
        assertThat(accumulation.horizons().get(2).averageReturn()).isGreaterThan(distribution.horizons().get(2).averageReturn());
        var top = record.groups().stream().filter(g -> g.group().equals("TOP_PICKS")).findFirst().orElseThrow();
        assertThat(top.horizons().getFirst().trades()).isLessThanOrEqualTo(25 * RadarTrackRecordService.TOP_PICKS);
    }
}
