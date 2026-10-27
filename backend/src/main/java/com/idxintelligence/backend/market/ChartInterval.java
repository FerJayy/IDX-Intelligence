package com.idxintelligence.backend.market;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.Period;
import java.util.Arrays;
import java.util.List;

/** Candle size for charts, with the look-back ranges that make sense for it. */
public enum ChartInterval {
    M15("15m", true, List.of(Range.D5, Range.M1), Range.D5),
    H1("1h", true, List.of(Range.M1, Range.M3, Range.M6, Range.Y1), Range.M1),
    H4("4h", true, List.of(Range.M3, Range.M6, Range.Y1, Range.Y2), Range.M6),
    D1("1d", false, List.of(Range.M1, Range.M3, Range.M6, Range.Y1, Range.Y5), Range.M6),
    W1("1w", false, List.of(Range.Y1, Range.Y5, Range.MAX), Range.Y5),
    MO1("1mo", false, List.of(Range.Y5, Range.MAX), Range.MAX);

    private final String code;
    private final boolean intraday;
    private final List<Range> ranges;
    private final Range defaultRange;

    ChartInterval(String code, boolean intraday, List<Range> ranges, Range defaultRange) {
        this.code = code;
        this.intraday = intraday;
        this.ranges = ranges;
        this.defaultRange = defaultRange;
    }

    public String code() {
        return code;
    }

    public boolean intraday() {
        return intraday;
    }

    public List<Range> ranges() {
        return ranges;
    }

    /** The requested range if it suits this interval, otherwise the interval's default. */
    public Range range(String code) {
        return ranges.stream().filter(r -> r.code().equalsIgnoreCase(code)).findFirst().orElse(defaultRange);
    }

    /**
     * Long views are split/dividend adjusted so corporate actions don't look like crashes; up to a
     * year stays as traded, matching broker average prices.
     */
    public boolean adjusted(Range range) {
        return this == W1 || this == MO1 || range == Range.Y2 || range == Range.Y5 || range == Range.MAX;
    }

    public static ChartInterval fromCode(String code) {
        return Arrays.stream(values())
                .filter(i -> i.code.equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unknown interval '" + code + "'. Use 15m, 1h, 4h, 1d, 1w or 1mo."));
    }

    public enum Range {
        D5("5D", Period.ofDays(5)), M1("1M", Period.ofMonths(1)), M3("3M", Period.ofMonths(3)),
        M6("6M", Period.ofMonths(6)), Y1("1Y", Period.ofYears(1)), Y2("2Y", Period.ofYears(2)),
        Y5("5Y", Period.ofYears(5)), MAX("MAX", Period.ofYears(30));

        private final String code;
        private final Period period;

        Range(String code, Period period) {
            this.code = code;
            this.period = period;
        }

        public String code() {
            return code;
        }

        public LocalDate startFrom(LocalDate end) {
            return end.minus(period).plusDays(1);
        }
    }
}
