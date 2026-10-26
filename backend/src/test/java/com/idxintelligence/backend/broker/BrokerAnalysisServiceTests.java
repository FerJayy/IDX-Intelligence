package com.idxintelligence.backend.broker;

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
                row(DAY, "AK", BrokerType.FOREIGN, 700, 100),
                row(DAY, "YP", BrokerType.DOMESTIC, 100, 250),
                row(DAY, "PD", BrokerType.DOMESTIC, 100, 250),
                row(DAY, "CC", BrokerType.DOMESTIC, 100, 200),
                row(DAY, "XL", BrokerType.DOMESTIC, 0, 200));

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
        rows.add(row(DAY, "AK", BrokerType.FOREIGN, 700, 0));
        for (String code : List.of("YP", "PD", "CC", "XL", "NI", "OD", "LG")) {
            rows.add(row(DAY, code, BrokerType.DOMESTIC, 0, 100));
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
            rows.add(row(date, "AK", BrokerType.FOREIGN, 700, 0));
            for (String code : List.of("YP", "PD", "CC", "XL", "NI", "OD", "LG")) {
                rows.add(row(date, code, BrokerType.DOMESTIC, 0, 100));
            }
        }
        List<DailyBrokerFlow> daily = BrokerAnalysisService.dailyFlows(rows);
        List<BrokerPosition> positions = BrokerAnalysisService.positions(rows);

        FlowAssessment assessment = BrokerAnalysisService.assess(daily, positions.subList(0, 1), new BigDecimal("110"));

        assertThat(assessment.status()).isEqualTo(FlowAssessment.Status.ACCUMULATION);
        assertThat(assessment.reasons()).hasSize(4);
    }

    @Test
    void mirroredFlowIsDistribution() {
        List<BrokerDailyActivity> rows = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            LocalDate date = DAY.minusDays(i);
            rows.add(row(date, "AK", BrokerType.FOREIGN, 0, 700));
            for (String code : List.of("YP", "PD", "CC", "XL", "NI", "OD", "LG")) {
                rows.add(row(date, code, BrokerType.DOMESTIC, 100, 0));
            }
        }
        List<DailyBrokerFlow> daily = BrokerAnalysisService.dailyFlows(rows);

        FlowAssessment assessment = BrokerAnalysisService.assess(daily, List.of(), new BigDecimal("110"));

        assertThat(assessment.status()).isEqualTo(FlowAssessment.Status.DISTRIBUTION);
    }

    @Test
    void noDataIsNeutral() {
        FlowAssessment assessment = BrokerAnalysisService.assess(List.of(), List.of(), BigDecimal.ONE);
        assertThat(assessment.status()).isEqualTo(FlowAssessment.Status.NEUTRAL);
    }

    @Test
    void compactUsesIndonesianShortScale() {
        assertThat(BrokerAnalysisService.compact(new BigDecimal("1905020470112"))).isEqualTo("1,9 T");
        assertThat(BrokerAnalysisService.compact(new BigDecimal("806012737441"))).isEqualTo("806 M");
        assertThat(BrokerAnalysisService.compact(new BigDecimal("12500000"))).isEqualTo("12,5 jt");
        assertThat(BrokerAnalysisService.compact(new BigDecimal("950"))).isEqualTo("950");
    }

    private static BrokerDailyActivity row(LocalDate date, String code, BrokerType type, long buy, long sell) {
        return new BrokerDailyActivity(date, code, type,
                buy, BigDecimal.valueOf(buy * 100), sell, BigDecimal.valueOf(sell * 100));
    }
}
