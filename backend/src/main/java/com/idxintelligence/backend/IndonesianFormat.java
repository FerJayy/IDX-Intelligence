package com.idxintelligence.backend;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

/** Number formatting for human-readable reasons, matching the frontend's id-ID style. */
public final class IndonesianFormat {

    private static final Locale INDONESIA = Locale.forLanguageTag("id-ID");

    private IndonesianFormat() {
    }

    /** Whole rupiah / shares: 9.850 */
    public static String integer(Number value) {
        return NumberFormat.getIntegerInstance(INDONESIA).format(value);
    }

    /** One decimal: 2,4 */
    public static String decimal(double value) {
        NumberFormat number = NumberFormat.getNumberInstance(INDONESIA);
        number.setMinimumFractionDigits(1);
        number.setMaximumFractionDigits(1);
        return number.format(value);
    }

    /** Fraction as percent with up to one decimal: 0.061 -> 6,1% */
    public static String percent(double fraction) {
        NumberFormat percent = NumberFormat.getPercentInstance(INDONESIA);
        percent.setMaximumFractionDigits(1);
        return percent.format(fraction);
    }

    /** Signed percent: +6,1% / -2,3% */
    public static String signedPercent(double fraction) {
        return (fraction > 0 ? "+" : "") + percent(fraction);
    }

    /** Indonesian short scale: 1,9 T (triliun), 806 M (miliar), 12,5 jt (juta), 950 rb (ribu). */
    public static String compact(BigDecimal value) {
        NumberFormat number = NumberFormat.getNumberInstance(INDONESIA);
        number.setMaximumFractionDigits(1);
        BigDecimal abs = value.abs();
        String[][] scales = {{"1000000000000", " T"}, {"1000000000", " M"}, {"1000000", " jt"}, {"1000", " rb"}};
        for (String[] scale : scales) {
            BigDecimal unit = new BigDecimal(scale[0]);
            if (abs.compareTo(unit) >= 0) {
                return number.format(value.divide(unit, 1, RoundingMode.HALF_UP)) + scale[1];
            }
        }
        return number.format(value);
    }
}
