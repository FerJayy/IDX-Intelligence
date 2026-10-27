package com.idxintelligence.backend.signal;

import com.idxintelligence.backend.Reason;
import com.idxintelligence.backend.broker.BrokerDailyActivity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RadarServiceTests {

    private static final LocalDate START = LocalDate.of(2026, 10, 12);

    /** Price 1,000 throughout; each share counted at that price. foreign = whole flow counts as foreign. */
    private static BrokerDailyActivity row(LocalDate day, String code, long buy, long sell, boolean foreign) {
        BigDecimal buyValue = BigDecimal.valueOf(buy * 1000);
        BigDecimal sellValue = BigDecimal.valueOf(sell * 1000);
        return new BrokerDailyActivity(day, code, "Broker " + code, buy, buyValue, sell, sellValue,
                foreign ? buyValue : BigDecimal.ZERO, foreign ? sellValue : BigDecimal.ZERO);
    }

    /** One day where AK (foreign) buys 600 net and selling is spread over 6 small brokers. */
    private static List<BrokerDailyActivity> accumulationDay(LocalDate day) {
        List<BrokerDailyActivity> rows = new ArrayList<>();
        rows.add(row(day, "AK", 600, 0, true));
        for (String code : List.of("YP", "PD", "CC", "XL", "NI", "OD")) rows.add(row(day, code, 0, 100, false));
        return rows;
    }

    /** The mirror image: AK sells 600, six small brokers buy 100 each. */
    private static List<BrokerDailyActivity> distributionDay(LocalDate day) {
        List<BrokerDailyActivity> rows = new ArrayList<>();
        rows.add(row(day, "AK", 0, 600, true));
        for (String code : List.of("YP", "PD", "CC", "XL", "NI", "OD")) rows.add(row(day, code, 100, 0, false));
        return rows;
    }

    /** 30 flat bars at 1,000 with plenty of liquidity. */
    private static List<Bar> flatBars(LocalDate end) {
        List<Bar> bars = new ArrayList<>();
        for (int i = 29; i >= 0; i--) {
            bars.add(new Bar(end.minusDays(i), 1000, 1005, 995, 1000, 50_000_000));
        }
        return bars;
    }

    @Test
    void persistentConcentratedForeignBuyingScoresHighWithReasons() {
        List<BrokerDailyActivity> rows = new ArrayList<>();
        rows.addAll(distributionDay(START));                  // older day breaks the streak
        for (int i = 1; i <= 4; i++) rows.addAll(accumulationDay(START.plusDays(i)));
        LocalDate last = START.plusDays(4);

        RadarService.RadarRow radar = RadarService.row("TEST", "Test", 10, rows, flatBars(last), last);

        assertThat(radar.accumulationStreak()).isEqualTo(4);
        assertThat(radar.streakBroker().code()).isEqualTo("AK");
        assertThat(radar.streakBroker().days()).isEqualTo(4);
        assertThat(radar.reasons()).extracting(Reason::code)
                .contains("RADAR_ACCUMULATION_STREAK", "RADAR_CONCENTRATED_BUYING", "RADAR_FOREIGN_BUYING",
                        "RADAR_BROKER_STREAK", "RADAR_NEAR_BUYERS_COST");
        assertThat(radar.exitGap()).isEqualByComparingTo("0");   // price equals the buyers' average cost
        assertThat(radar.excludedBecause()).isNull();
        assertThat(radar.score()).isPositive();
    }

    @Test
    void distributionStreakIsNegative() {
        List<BrokerDailyActivity> rows = new ArrayList<>();
        for (int i = 0; i < 3; i++) rows.addAll(distributionDay(START.plusDays(i)));
        LocalDate last = START.plusDays(2);

        RadarService.RadarRow radar = RadarService.row("TEST", "Test", 10, rows, flatBars(last), last);

        assertThat(radar.accumulationStreak()).isEqualTo(-3);
        assertThat(radar.score()).isNegative();
        assertThat(radar.reasons()).extracting(Reason::code).contains("RADAR_DISTRIBUTION_STREAK", "RADAR_FOREIGN_SELLING");
    }

    @Test
    void windowOnlyUsesTheLatestBrokerDays() {
        List<BrokerDailyActivity> rows = new ArrayList<>();
        for (int i = 0; i < 10; i++) rows.addAll(distributionDay(START.plusDays(i)));
        for (int i = 10; i < 15; i++) rows.addAll(accumulationDay(START.plusDays(i)));
        LocalDate last = START.plusDays(14);

        RadarService.RadarRow five = RadarService.row("TEST", "Test", 5, rows, flatBars(last), last);
        RadarService.RadarRow twenty = RadarService.row("TEST", "Test", 20, rows, flatBars(last), last);

        assertThat(five.brokerDays()).isEqualTo(5);
        assertThat(twenty.brokerDays()).isEqualTo(15);
        assertThat(five.avgConcentration()).isPositive();
        assertThat(five.accumulationStreak()).isEqualTo(5);
    }

    @Test
    void hscStocksAreListedSeparatelyWhateverTheirFlow() {
        List<BrokerDailyActivity> rows = new ArrayList<>();
        for (int i = 0; i < 4; i++) rows.addAll(accumulationDay(START.plusDays(i)));
        LocalDate last = START.plusDays(3);

        RadarService.RadarRow radar = RadarService.row("BREN", "Barito Renewables", true, 10, rows, flatBars(last), last);

        assertThat(radar.excludedBecause().code()).isEqualTo("HSC");
        assertThat(radar.score()).isPositive(); // the flow is still shown
    }

    @Test
    void illiquidStocksAreFlaggedAndStaleDataIsNoted() {
        List<BrokerDailyActivity> rows = new ArrayList<>(accumulationDay(START));
        List<Bar> thin = new ArrayList<>();
        for (Bar b : flatBars(START)) thin.add(new Bar(b.date(), b.open(), b.high(), b.low(), b.close(), 1000));

        RadarService.RadarRow radar = RadarService.row("TEST", "Test", 10, rows, thin, START.plusDays(3));

        assertThat(radar.excludedBecause().code()).isEqualTo("LOW_LIQUIDITY");
        assertThat(radar.reasons()).extracting(Reason::code).contains("RADAR_BROKER_DATA_BEHIND");
    }
}
