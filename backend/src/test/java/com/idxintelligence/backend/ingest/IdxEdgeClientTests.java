package com.idxintelligence.backend.ingest;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class IdxEdgeClientTests {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 23);

    // Trimmed real response.
    private static final String RESPONSE = """
            {"stock_code":"BBCA","brokers":[
              {"broker_code":"YU","broker_name":"CGS INTERNATIONAL SEKURITAS INDONESIA",
               "bval":876941307500,"bvol":171477200,"bfrq":9509,"sfrq":2998,
               "sval":167894102500,"svol":33095600,"nval":709047205000,"nvol":138381600}],
             "broker_levels":[],"broker_start_date":"2026-10-23","broker_end_date":"2026-10-23","flow":"all"}
            """;

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final IdxEdgeClient client = new IdxEdgeClient(builder, "http://edge.test", "test-key");

    @Test
    void readsAllBrokersAndRemainingQuota() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("x-ratelimit-remaining", "997");
        server.expect(requestTo("http://edge.test/api/broker-summary/BBCA?start_date=2026-10-23&end_date=2026-10-23&all_data=true"))
                .andExpect(header("X-API-Key", "test-key"))
                .andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON).headers(headers));

        IdxEdgeClient.BrokerDay day = client.brokerSummary("BBCA", DAY, IdxEdgeClient.Flow.ALL);

        server.verify();
        assertThat(day.remainingQuota()).isEqualTo(997);
        IdxEdgeClient.BrokerRow yu = day.brokers().getFirst();
        assertThat(yu.brokerCode()).isEqualTo("YU");
        assertThat(yu.buyVolume()).isEqualTo(171_477_200L);
        assertThat(yu.buyValue()).isEqualByComparingTo("876941307500");
        assertThat(yu.sellFrequency()).isEqualTo(2998);
    }

    @Test
    void foreignFlowAddsTheFlowParameter() {
        server.expect(requestTo("http://edge.test/api/broker-summary/BBCA?start_date=2026-10-23&end_date=2026-10-23&all_data=true&flow=F"))
                .andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));

        client.brokerSummary("BBCA", DAY, IdxEdgeClient.Flow.F);

        server.verify();
    }

    @Test
    void http429MeansQuotaExhausted() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("http://edge.test/api/broker-summary/BBCA")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.brokerSummary("BBCA", DAY, IdxEdgeClient.Flow.ALL))
                .isInstanceOf(IdxEdgeClient.QuotaExhaustedException.class);
    }

    @Test
    void readsAListingPage() {
        server.expect(requestTo("http://edge.test/api/market-cap?page=2&per_page=50"))
                .andExpect(header("X-API-Key", "test-key"))
                .andRespond(withSuccess("""
                        {"date":"2026-10-26","total":963,"page":2,"per_page":50,"total_pages":20,
                         "data":[{"code":"BBCA","name":"Bank Central Asia Tbk.","close":4940.0,
                                  "listed_shares":122042299500.0,"market_cap":602888959530000.0,"turnover_ratio":0.0029}]}
                        """, MediaType.APPLICATION_JSON));

        IdxEdgeClient.ListingPage page = client.listingPage(2);

        assertThat(page.totalPages()).isEqualTo(20);
        assertThat(page.stocks().getFirst().code()).isEqualTo("BBCA");
        assertThat(page.stocks().getFirst().listedShares()).isEqualByComparingTo("122042299500");
    }

    @Test
    void notConfiguredWithoutKey() {
        assertThat(new IdxEdgeClient(RestClient.builder(), "http://edge.test", " ").isConfigured()).isFalse();
    }
}
