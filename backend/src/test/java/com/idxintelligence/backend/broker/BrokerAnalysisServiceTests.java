package com.idxintelligence.backend.broker;

import com.idxintelligence.backend.Reason;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BrokerAnalysisServiceTests {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 23);

    @Test
    void dailyFlowMeasuresConcentrationAndForeignNet() {
        // 1,000 shares traded. AK (foreign) buys 600 net; selling is spread over 4 brokers.
        List<BrokerDailyActivity> rows = List.of(
                row(DAY, "AK", true, 700, 100),
                row(DAY, "YP", false, 100, 250),
                row(DAY, "PD", false, 100, 250),
                row(DAY, "CC", false, 100, 200),
                row(DAY, "XL", false, 0, 200));

        DailyBrokerFlow flow = BrokerAnalysisService.dailyFlows(rows).getFirst();

        assertThat(flow.totalVolume()).isEqualTo(1000);
        assertThat(flow.top5NetBuyVolume()).isEqualTo(600);
        assertThat(flow.top5NetSellVolume()).isEqualTo(600);
        assertThat(flow.concentration()).isEqualByComparingTo("0");
        assertThat(flow.buyerCount()).isEqualTo(1);
        assertThat(flow.sellerCount()).isEqualTo(4);
        // Values use price 100: AK net 600 shares * 100.
        assertThat(flow.foreignNetValue()).isEqualByComparingTo("60000");
    }

    @Test
    void topFiveOnlyCountsTheFiveLargestNetSellers() {
        // One buyer takes 700; seven brokers sell 100 each. Top 5 sellers release only 500.
        List<BrokerDailyActivity> rows = new ArrayList<>();
        rows.add(row(DAY, "AK", true, 700, 0));
        for (String code : List.of("YP", "PD", "CC", "XL", "NI", "OD", "LG")) {
            rows.add(row(DAY, code, false, 0, 100));
        }

        DailyBrokerFlow flow = BrokerAnalysisService.dailyFlows(rows).getFirst();

        assertThat(flow.top5NetSellVolume()).isEqualTo(500);
        assertThat(flow.concentration()).isEqualByComparingTo("0.2857"); // (700 - 500) / 700
    }

    @Test
    void concentratedForeignBuyingIsAccumulation() {
        List<BrokerDailyActivity> rows = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            LocalDate date = DAY.minusDays(i);
            rows.add(row(date, "AK", true, 700, 0));
            for (String code : List.of("YP", "PD", "CC", "XL", "NI", "OD", "LG")) {
                rows.add(row(date, code, false, 0, 100));
            }
        }
        List<DailyBrokerFlow> daily = BrokerAnalysisService.dailyFlows(rows);
        List<BrokerPosition> positions = BrokerAnalysisService.positions(rows);

        FlowAssessment assessment = BrokerAnalysisService.assess(daily, positions.subList(0, 1), new BigDecimal("110"));

        assertThat(assessment.status()).isEqualTo(FlowAssessment.Status.ACCUMULATION);
        assertThat(assessment.reasons()).extracting(Reason::code).containsExactly(
                "CONCENTRATION_ACCUMULATION", "BREADTH_BUYING_CONCENTRATED", "FOREIGN_NET_BUY", "TOP_BUYERS_AVG_PRICE");
    }

    @Test
    void mirroredFlowIsDistribution() {
        List<BrokerDailyActivity> rows = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            LocalDate date = DAY.minusDays(i);
            rows.add(row(date, "AK", true, 0, 700));
            for (String code : List.of("YP", "PD", "CC", "XL", "NI", "OD", "LG")) {
                rows.add(row(date, code, false, 100, 0));
            }
        }
        List<DailyBrokerFlow> daily = BrokerAnalysisService.dailyFlows(rows);

        FlowAssessment assessment = BrokerAnalysisService.assess(daily, List.of(), new BigDecimal("110"));

        assertThat(assessment.status()).isEqualTo(FlowAssessment.Status.DISTRIBUTION);
    }

    @Test
    void tooFewDaysIsNeutralAndSaysSo() {
        List<BrokerDailyActivity> rows = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            rows.add(row(DAY.minusDays(i), "AK", true, 700, 0));
            rows.add(row(DAY.minusDays(i), "YP", false, 0, 700));
        }
        FlowAssessment assessment = BrokerAnalysisService.assess(
                BrokerAnalysisService.dailyFlows(rows), List.of(), BigDecimal.ONE);

        assertThat(assessment.status()).isEqualTo(FlowAssessment.Status.NEUTRAL);
        assertThat(assessment.reasons()).singleElement().satisfies(r -> {
            assertThat(r.code()).isEqualTo("TOO_FEW_BROKER_DAYS");
            assertThat(r.values()).containsEntry("days", 3).containsEntry("minDays", 5);
        });
    }

    @Test
    void missingForeignFlowStaysMissingInsteadOfZero() {
        LocalDate other = DAY.minusDays(1);
        List<BrokerDailyActivity> rows = List.of(
                row(DAY, "AK", true, 700, 0),
                row(DAY, "YP", false, 0, 700),
                new BrokerDailyActivity(other, "AK", null, 700, BigDecimal.valueOf(70000), 0, BigDecimal.ZERO, null, null),
                new BrokerDailyActivity(other, "YP", null, 0, BigDecimal.ZERO, 700, BigDecimal.valueOf(70000), null, null));

        List<DailyBrokerFlow> daily = BrokerAnalysisService.dailyFlows(rows);
        List<BrokerPosition> positions = BrokerAnalysisService.positions(rows);

        assertThat(daily.get(0).foreignNetValue()).isNull();          // the earlier day has no foreign data
        assertThat(daily.get(0).cumulativeForeignNetValue()).isNull();
        assertThat(daily.get(1).foreignNetValue()).isEqualByComparingTo("70000");
        assertThat(positions.getFirst().foreignNetValue()).isNull();  // not known for every day
        assertThat(positions.getFirst().brokerName()).isEqualTo("Broker AK");
    }

    @Test
    void noDataIsNeutral() {
        FlowAssessment assessment = BrokerAnalysisService.assess(List.of(), List.of(), BigDecimal.ONE);
        assertThat(assessment.status()).isEqualTo(FlowAssessment.Status.NEUTRAL);
    }

    /** Values use price 100. A "foreign" broker's whole flow counts as foreign investors'. */
    private static BrokerDailyActivity row(LocalDate date, String code, boolean foreign, long buy, long sell) {
        BigDecimal buyValue = BigDecimal.valueOf(buy * 100);
        BigDecimal sellValue = BigDecimal.valueOf(sell * 100);
        return new BrokerDailyActivity(date, code, "Broker " + code, buy, buyValue, sell, sellValue,
                foreign ? buyValue : BigDecimal.ZERO, foreign ? sellValue : BigDecimal.ZERO);
    }
}
