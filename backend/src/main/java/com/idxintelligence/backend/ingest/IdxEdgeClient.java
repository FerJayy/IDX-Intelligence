package com.idxintelligence.backend.ingest;

import com.fasterxml.jackson.annotation.JsonAlias;
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

    /** Real-time quote from IDX's running trade (today's aggregate so far). Costs one request. */
    public LiveQuote livePrice(String ticker) {
        LiveQuote body = http.get()
                .uri("/api/price/{code}", ticker)
                .header("X-API-Key", apiKey)
                .retrieve()
                .onStatus(status -> status.value() == 429, (request, resp) -> {
                    throw new QuotaExhaustedException();
                })
                .onStatus(HttpStatusCode::isError, (request, resp) -> {
                    throw new IllegalStateException("IDX Edge returned HTTP " + resp.getStatusCode().value());
                })
                .body(LiveQuote.class);
        if (body == null) {
            throw new IllegalStateException("IDX Edge returned an empty quote for " + ticker);
        }
        return body;
    }

    /**
     * @param lot          lots traded today (1 lot = 100 shares)
     * @param dataTime     time of the data, "HH:mm:ss" WIB
     * @param marketState  e.g. "open"
     * @param marketLabel  e.g. "Sesi 2 berlangsung (13:30-15:50 WIB)"
     * @param noTradeToday true when the stock hasn't traded today (price is then the last close)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LiveQuote(
            @JsonAlias("code") String code,
            @JsonAlias("last_price") BigDecimal lastPrice,
            @JsonAlias("change") BigDecimal change,
            @JsonAlias("change_pct") BigDecimal changePercent,
            @JsonAlias("lot") BigDecimal lot,
            @JsonAlias("value") BigDecimal value,
            @JsonAlias("freq") Long frequency,
            @JsonAlias("vwap") BigDecimal vwap,
            @JsonAlias("data_ts") String dataTime,
            @JsonAlias("market_state") String marketState,
            @JsonAlias("market_label") String marketLabel,
            @JsonAlias("no_trade_today") Boolean noTradeToday) {
    }

    /** IDX Edge's market-wide screener result for the latest trading day. Costs one request. */
    public Screener screener() {
        Screener body = http.get()
                .uri("/api/screener/latest")
                .header("X-API-Key", apiKey)
                .retrieve()
                .onStatus(status -> status.value() == 429, (request, resp) -> {
                    throw new QuotaExhaustedException();
                })
                .onStatus(HttpStatusCode::isError, (request, resp) -> {
                    throw new IllegalStateException("IDX Edge returned HTTP " + resp.getStatusCode().value());
                })
                .body(Screener.class);
        if (body == null) {
            throw new IllegalStateException("IDX Edge returned an empty screener");
        }
        return body;
    }

    /** @param date the provider's heading, e.g. "📊 **SCREENER V5.2** — Senin, 26 Oktober 2026" */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Screener(@JsonProperty("date") String date, @JsonProperty("rows") List<ScreenerRow> rows) {
    }

    /** One pick. Win rate, potential and drawdown are the provider's own claims, in percent. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ScreenerRow(
            @JsonProperty("stock_code") String code,
            @JsonProperty("stock_name") String name,
            @JsonProperty("bucket") String bucket,
            @JsonProperty("summary") String summary,
            @JsonProperty("note") String note,
            @JsonProperty("wr_event") BigDecimal winRate,
            @JsonProperty("potential") BigDecimal potential,
            @JsonProperty("drawdown") BigDecimal drawdown) {
    }

    /** One page (max 50) of the full listing, largest market cap first. Costs one request. */
    public ListingPage listingPage(int page) {
        ResponseEntity<ListingPage> response = http.get()
                .uri(uri -> uri.path("/api/market-cap").queryParam("page", page).queryParam("per_page", 50).build())
                .header("X-API-Key", apiKey)
                .retrieve()
                .onStatus(status -> status.value() == 429, (request, resp) -> {
                    throw new QuotaExhaustedException();
                })
                .onStatus(HttpStatusCode::isError, (request, resp) -> {
                    throw new IllegalStateException("IDX Edge returned HTTP " + resp.getStatusCode().value());
                })
                .toEntity(ListingPage.class);
        ListingPage body = response.getBody();
        if (body == null || body.stocks() == null) {
            throw new IllegalStateException("IDX Edge returned an empty listing page " + page);
        }
        return body;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ListingPage(
            @JsonProperty("total") int total,
            @JsonProperty("page") int page,
            @JsonProperty("total_pages") int totalPages,
            @JsonProperty("data") List<ListedStock> stocks) {
    }

    /** @param listedShares and marketCap may be null when the provider doesn't know them */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ListedStock(
            @JsonProperty("code") String code,
            @JsonProperty("name") String name,
            @JsonProperty("listed_shares") BigDecimal listedShares,
            @JsonProperty("market_cap") BigDecimal marketCap) {
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
