package com.idxintelligence.backend.ingest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

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
