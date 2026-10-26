package com.idxintelligence.backend.market;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.Period;
import java.util.Arrays;

public enum PriceRange {
    ONE_MONTH("1M", Period.ofMonths(1)),
    THREE_MONTHS("3M", Period.ofMonths(3)),
    SIX_MONTHS("6M", Period.ofMonths(6)),
    ONE_YEAR("1Y", Period.ofYears(1));

    private final String code;
    private final Period period;

    PriceRange(String code, Period period) {
        this.code = code;
        this.period = period;
    }

    public String code() {
        return code;
    }

    /** First date (inclusive) covered by this range when it ends on {@code end}. */
    public LocalDate startFrom(LocalDate end) {
        return end.minus(period).plusDays(1);
    }

    public static PriceRange fromCode(String code) {
        return Arrays.stream(values())
                .filter(r -> r.code.equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unknown range '" + code + "'. Use 1M, 3M, 6M or 1Y."));
    }
}
