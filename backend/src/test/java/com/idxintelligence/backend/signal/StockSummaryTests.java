package com.idxintelligence.backend.signal;

import com.idxintelligence.backend.Reason;
import com.idxintelligence.backend.broker.BrokerDailyActivity;
import com.idxintelligence.backend.broker.FlowAssessment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StockSummaryTests {

    private static final LocalDate END = LocalDate.of(2026, 10, 27);
    private static final int DAYS = 90;

    private static BrokerDailyActivity row(LocalDate day, String code, long buy, long sell, boolean foreign) {
        BigDecimal b = BigDecimal.valueOf(buy * 1000);
        BigDecimal s = BigDecimal.valueOf(sell * 1000);
        return new BrokerDailyActivity(day, code, "Broker " + code, buy, b, sell, s,
                foreign ? b : BigDecimal.ZERO, foreign ? s : BigDecimal.ZERO);
    }

    /** AK (foreign) buys 600 against six small sellers, or the mirror image. */
    private static List<BrokerDailyActivity> day(LocalDate day, boolean accumulation) {
        List<BrokerDailyActivity> rows = new ArrayList<>();
        rows.add(row(day, "AK", accumulation ? 600 : 0, accumulation ? 0 : 600, true));
        for (String code : List.of("YP", "PD", "CC", "XL", "NI", "OD")) {
            rows.add(row(day, code, accumulation ? 0 : 100, accumulation ? 100 : 0, false));
        }
        return rows;
    }

    @Test
    void recentAccumulationAfterOlderSellingShowsDifferentStatusesPerPeriod() {
        // Like KLBF: two months of selling, then the last 15 days of buying.
        List<BrokerDailyActivity> activity = new ArrayList<>();
        List<Bar> bars = new ArrayList<>();
        Map<LocalDate, double[]> index = new HashMap<>();
        for (int i = DAYS - 1; i >= 0; i--) {
            LocalDate d = END.minusDays(i);
            activity.addAll(day(d, i < 15));
            bars.add(new Bar(d, 1000, 1010, 990, 1000, 50_000_000));
            index.put(d, new double[]{7000, 7000});
        }

        StockSummaryService.Summary summary = StockSummaryService.compute("TEST", true, false, END, bars, index,
                activity, List.of());

        assertThat(summary.flows()).extracting(StockSummaryService.FlowPeriod::period).containsExactly("10D", "1M", "3M");
        assertThat(summary.flows().get(0).status()).isEqualTo(FlowAssessment.Status.ACCUMULATION);
        assertThat(summary.flows().get(0).brokerDays()).isEqualTo(10);
        assertThat(summary.flows().get(2).status()).isNotEqualTo(FlowAssessment.Status.ACCUMULATION);
        // Foreign: 15 days bought, the rest sold -> net selling over 3 months.
        assertThat(summary.facts()).extracting(Reason::code).contains("SUM_FOREIGN_SELLING", "SUM_VS_IHSG");
        assertThat(summary.levels().monthLow()).isEqualByComparingTo("990");
        assertThat(summary.levels().monthHigh()).isEqualByComparingTo("1010");
    }

    @Test
    void untrackedHscStockHasNoFlowsButAWarning() {
        List<Bar> bars = new ArrayList<>();
        for (int i = 29; i >= 0; i--) bars.add(new Bar(END.minusDays(i), 500, 505, 495, 500, 1_000_000));

        StockSummaryService.Summary summary = StockSummaryService.compute("HSCX", false, true, END, bars, Map.of(),
                List.of(), List.of(new StockSummaryService.InsiderTrade(END.minusDays(3), "buy")));

        assertThat(summary.flows()).isEmpty();
        assertThat(summary.facts()).extracting(Reason::code).contains("SUM_HSC", "SUM_INSIDER_BUYING");
        assertThat(summary.facts()).filteredOn(r -> r.code().equals("SUM_HSC")).singleElement()
                .extracting(Reason::points).isEqualTo(-1);
    }
}
