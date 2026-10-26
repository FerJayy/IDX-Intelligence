package com.idxintelligence.backend.ingest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Client for IDX Edge PRO (stock.arjum.com) broker summaries. Every request costs one unit
 * of the daily quota (1,000/day on the free tier, reset at 00:00 WIB).
 */
@Component
public class IdxEdgeClient {

    public enum Flow {
        /** All investors, regular market. */
        ALL,
        /** Foreign investors only. */
        F
    }

    private final RestClient http;
    private final String apiKey;

    public IdxEdgeClient(RestClient.Builder builder,
                         @Value("${idx-edge.base-url:https://stock.arjum.com}") String baseUrl,
                         @Value("${IDX_EDGE_API_KEY:}") String apiKey) {
        this.apiKey = apiKey.trim();
        this.http = builder
                .baseUrl(baseUrl)
                .defaultHeader("User-Agent", "IDX-Intelligence/0.1 (personal research)")
                .build();
    }

    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    /** Every broker's activity in {@code ticker} on one trading day. */
    public BrokerDay brokerSummary(String ticker, LocalDate date, Flow flow) {
        ResponseEntity<SummaryResponse> response = http.get()
                .uri(uri -> {
                    uri.path("/api/broker-summary/{code}")
                            .queryParam("start_date", date)
                            .queryParam("end_date", date)
                            .queryParam("all_data", true);
                    if (flow == Flow.F) uri.queryParam("flow", "F");
                    return uri.build(ticker);
                })
                .header("X-API-Key", apiKey)
                .retrieve()
                .onStatus(status -> status.value() == 429, (request, resp) -> {
                    throw new QuotaExhaustedException();
                })
                .onStatus(HttpStatusCode::isError, (request, resp) -> {
                    throw new IllegalStateException("IDX Edge returned HTTP " + resp.getStatusCode().value());
                })
                .toEntity(SummaryResponse.class);

        SummaryResponse body = response.getBody();
        List<BrokerRow> brokers = body == null || body.brokers() == null ? List.of() : body.brokers();
        return new BrokerDay(brokers, parseRemaining(response.getHeaders().getFirst("x-ratelimit-remaining")));
    }

    private static Integer parseRemaining(String header) {
        try {
            return header == null ? null : Integer.valueOf(header.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** @param remainingQuota requests left today according to the provider, or null if not reported */
    public record BrokerDay(List<BrokerRow> brokers, Integer remainingQuota) {
    }

    /** One broker's activity. Volumes in shares, values in IDR, frequency = number of trades. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BrokerRow(
            @JsonProperty("broker_code") String brokerCode,
            @JsonProperty("broker_name") String brokerName,
            @JsonProperty("bvol") long buyVolume,
            @JsonProperty("bval") BigDecimal buyValue,
            @JsonProperty("bfrq") Integer buyFrequency,
            @JsonProperty("svol") long sellVolume,
            @JsonProperty("sval") BigDecimal sellValue,
            @JsonProperty("sfrq") Integer sellFrequency) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SummaryResponse(List<BrokerRow> brokers) {
    }

    public static class QuotaExhaustedException extends RuntimeException {
        public QuotaExhaustedException() {
            super("IDX Edge daily quota exhausted (HTTP 429)");
        }
    }
}
