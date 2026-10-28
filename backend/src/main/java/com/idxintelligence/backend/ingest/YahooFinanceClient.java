package com.idxintelligence.backend.ingest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads daily bars from Yahoo Finance's chart endpoint. This endpoint is unofficial and
 * undocumented: it can change or rate-limit without notice, and Yahoo's terms forbid
 * redistribution. Used for a small private app; keep request volume low.
 */
@Component
public class YahooFinanceClient {

    static final ZoneId JAKARTA = ZoneId.of("Asia/Jakarta");

    private final RestClient http;

    public YahooFinanceClient(RestClient.Builder builder,
                              @Value("${yahoo.base-url:https://query1.finance.yahoo.com}") String baseUrl) {
        this.http = builder
                .baseUrl(baseUrl)
                // Yahoo answers 429 to requests without a browser-style User-Agent.
                .defaultHeader("User-Agent", "Mozilla/5.0 (compatible; IDX-Intelligence/0.1; personal research)")
                .build();
    }

    /** Daily bars for an IDX ticker (without the .JK suffix) from {@code from} to {@code to} inclusive. */
    public List<DailyBar> dailyBars(String ticker, LocalDate from, LocalDate to) {
        return dailyBarsForSymbol(ticker + ".JK", from, to);
    }

    /** Daily bars for any Yahoo symbol, e.g. {@code ^JKSE} for the IDX Composite. */
    public List<DailyBar> dailyBarsForSymbol(String symbol, LocalDate from, LocalDate to) {
        long period1 = from.atStartOfDay(JAKARTA).toEpochSecond();
        long period2 = to.plusDays(1).atStartOfDay(JAKARTA).toEpochSecond();

        ChartResponse response = http.get()
                .uri("/v8/finance/chart/{symbol}?period1={p1}&period2={p2}&interval=1d&events=div,splits",
                        symbol, period1, period2)
                .retrieve()
                .body(ChartResponse.class);

        if (response == null || response.chart() == null || response.chart().result() == null
                || response.chart().result().isEmpty()) {
            throw new IllegalStateException("Yahoo returned no data for " + symbol);
        }
        return toBars(response.chart().result().getFirst());
    }

    /**
     * Intraday candles ({@code interval} = "15m" or "60m") since {@code from}. Times are the candle
     * start as epoch seconds; the latest candle may still be forming. Yahoo keeps ~60 days of 15m
     * and ~2 years of 60m history.
     */
    public List<IntradayBar> intradayBars(String symbol, String interval, Instant from) {
        ChartResponse response = http.get()
                .uri("/v8/finance/chart/{symbol}?period1={p1}&period2={p2}&interval={interval}",
                        symbol, from.getEpochSecond(), Instant.now().getEpochSecond(), interval)
                .retrieve()
                .body(ChartResponse.class);
        if (response == null || response.chart() == null || response.chart().result() == null
                || response.chart().result().isEmpty()) {
            throw new IllegalStateException("Yahoo returned no intraday data for " + symbol);
        }
        Result result = response.chart().result().getFirst();
        List<Long> timestamps = result.timestamp();
        if (timestamps == null || result.indicators() == null || result.indicators().quote() == null) {
            return List.of();
        }
        Quote quote = result.indicators().quote().getFirst();
        List<IntradayBar> bars = new ArrayList<>(timestamps.size());
        for (int i = 0; i < timestamps.size(); i++) {
            BigDecimal close = at(quote.close(), i);
            if (close == null) continue;
            bars.add(new IntradayBar(timestamps.get(i), price(at(quote.open(), i)), price(at(quote.high(), i)),
                    price(at(quote.low(), i)), price(close), at(quote.volume(), i)));
        }
        return bars;
    }

    /** Most symbols the spark endpoint answers in one request (more returns HTTP 400). */
    public static final int SPARK_BATCH = 20;

    /**
     * Today's last price and previous close for up to {@link #SPARK_BATCH} symbols in one request (Yahoo's
     * "spark" endpoint; intraday prices are ~10 minutes delayed). Symbols without data are left out.
     */
    public Map<String, SparkQuote> spark(List<String> symbols) {
        Map<String, SparkResult> body = http.get()
                .uri("/v8/finance/spark?symbols={symbols}&range=1d&interval=1d", String.join(",", symbols))
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, SparkResult>>() {
                });
        Map<String, SparkQuote> result = new HashMap<>();
        if (body == null) return result;
        body.forEach((symbol, r) -> {
            if (r == null || r.close() == null || r.close().isEmpty() || r.timestamp() == null || r.timestamp().isEmpty()) return;
            BigDecimal price = r.close().getLast();
            BigDecimal previous = r.chartPreviousClose() != null ? r.chartPreviousClose() : r.previousClose();
            if (price == null || previous == null) return;
            result.put(symbol, new SparkQuote(price(price), price(previous), r.timestamp().getLast()));
        });
        return result;
    }

    /**
     * The latest session's 5-minute prices for up to {@link #SPARK_BATCH} symbols in one request, each
     * preceded by the previous close. Symbols without data are left out.
     */
    public Map<String, List<BigDecimal>> sessionLines(List<String> symbols) {
        Map<String, SparkResult> body = http.get()
                .uri("/v8/finance/spark?symbols={symbols}&range=1d&interval=5m", String.join(",", symbols))
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, SparkResult>>() {
                });
        Map<String, List<BigDecimal>> result = new HashMap<>();
        if (body == null) return result;
        body.forEach((symbol, r) -> {
            if (r == null || r.close() == null) return;
            BigDecimal previous = r.chartPreviousClose() != null ? r.chartPreviousClose() : r.previousClose();
            List<BigDecimal> line = new ArrayList<>();
            if (previous != null) line.add(price(previous));
            r.close().stream().filter(java.util.Objects::nonNull).map(YahooFinanceClient::price).forEach(line::add);
            if (line.size() >= 2) result.put(symbol, line);
        });
        return result;
    }

    /** @param time epoch seconds (UTC) of the last price */
    public record SparkQuote(BigDecimal price, BigDecimal previousClose, long time) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SparkResult(List<Long> timestamp, List<BigDecimal> close, BigDecimal chartPreviousClose, BigDecimal previousClose) {
    }

    /** One intraday candle; {@code time} is epoch seconds (UTC) of the candle start. */
    public record IntradayBar(long time, BigDecimal open, BigDecimal high, BigDecimal low,
                              BigDecimal close, Long volume) {
    }

    private static List<DailyBar> toBars(Result result) {
        List<Long> timestamps = result.timestamp();
        if (timestamps == null || result.indicators() == null || result.indicators().quote() == null) {
            return List.of(); // no trading days in the range
        }
        Quote quote = result.indicators().quote().getFirst();
        List<BigDecimal> adjusted = result.indicators().adjclose() == null || result.indicators().adjclose().isEmpty()
                ? null : result.indicators().adjclose().getFirst().adjclose();

        List<DailyBar> bars = new ArrayList<>(timestamps.size());
        for (int i = 0; i < timestamps.size(); i++) {
            BigDecimal close = at(quote.close(), i);
            if (close == null) continue; // a bar without a close is not a usable bar
            bars.add(new DailyBar(
                    Instant.ofEpochSecond(timestamps.get(i)).atZone(JAKARTA).toLocalDate(),
                    price(at(quote.open(), i)),
                    price(at(quote.high(), i)),
                    price(at(quote.low(), i)),
                    price(close),
                    adjusted == null ? null : scale(at(adjusted, i), 4),
                    at(quote.volume(), i)));
        }
        return bars;
    }

    private static <T> T at(List<T> values, int i) {
        return values == null || i >= values.size() ? null : values.get(i);
    }

    // Yahoo sends floats such as 4979.999; IDX prices are whole rupiah.
    private static BigDecimal price(BigDecimal value) {
        return scale(value, 2);
    }

    private static BigDecimal scale(BigDecimal value, int scale) {
        return value == null ? null : value.setScale(scale, RoundingMode.HALF_UP);
    }

    /** One day as Yahoo reports it. Any field except date and close may be null (missing). */
    public record DailyBar(LocalDate date, BigDecimal open, BigDecimal high, BigDecimal low,
                           BigDecimal close, BigDecimal adjustedClose, Long volume) {
    }

    // Subset of Yahoo's chart JSON that we read.
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChartResponse(Chart chart) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Chart(List<Result> result) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Result(List<Long> timestamp, Indicators indicators) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Indicators(List<Quote> quote, List<AdjClose> adjclose) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Quote(List<BigDecimal> open, List<BigDecimal> high, List<BigDecimal> low,
                 List<BigDecimal> close, List<Long> volume) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AdjClose(List<BigDecimal> adjclose) {
    }
}
