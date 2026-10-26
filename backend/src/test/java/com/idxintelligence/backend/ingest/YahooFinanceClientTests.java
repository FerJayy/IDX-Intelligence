package com.idxintelligence.backend.ingest;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class YahooFinanceClientTests {

    // Trimmed real response shape. Timestamps are 09:00 WIB on 2026-10-22, 23 and 26.
    private static final String RESPONSE = """
            {"chart":{"result":[{
              "meta":{"symbol":"BBCA.JK","currency":"IDR","exchangeTimezoneName":"Asia/Jakarta"},
              "timestamp":[1792634400,1792720800,1792980000],
              "events":{"splits":{}},
              "indicators":{
                "quote":[{"open":[5125.0,5175.0,null],"high":[5275.0,5225.0,null],
                          "low":[5100.0,5000.0,null],"close":[5175.0,5024.999,null],
                          "volume":[286928500,495623900,null]}],
                "adjclose":[{"adjclose":[5175.0,5024.999,null]}]}
            }],"error":null}}
            """;

    @Test
    void parsesBarsIntoJakartaDatesAndRoundsPrices() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("http://yahoo.test/v8/finance/chart/BBCA.JK?period1=")))
                .andExpect(header("User-Agent", org.hamcrest.Matchers.containsString("Mozilla/5.0")))
                .andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));

        YahooFinanceClient client = new YahooFinanceClient(builder, "http://yahoo.test");
        List<YahooFinanceClient.DailyBar> bars =
                client.dailyBars("BBCA", LocalDate.of(2026, 10, 22), LocalDate.of(2026, 10, 26));

        server.verify();
        // The third row has no close, so it is dropped rather than stored as zero.
        assertThat(bars).hasSize(2);
        assertThat(bars.get(0).date()).isEqualTo(LocalDate.of(2026, 10, 22));
        assertThat(bars.get(1).date()).isEqualTo(LocalDate.of(2026, 10, 23));
        assertThat(bars.get(1).close()).isEqualByComparingTo("5025.00");
        assertThat(bars.get(1).adjustedClose()).isEqualByComparingTo("5024.9990");
        assertThat(bars.get(1).volume()).isEqualTo(495_623_900L);
    }
}
