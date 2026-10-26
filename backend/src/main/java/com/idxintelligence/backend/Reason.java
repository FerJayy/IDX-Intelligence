package com.idxintelligence.backend;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A language-neutral explanation: a stable code plus the numbers behind it. The frontend turns it
 * into an English or Bahasa Indonesia sentence, so wording and number formatting live in one place.
 *
 * @param values numbers/strings the sentence needs (fractions as 0.061, rupiah as plain numbers)
 * @param points score contribution, or null when the reason isn't scored
 */
public record Reason(String code, Map<String, Object> values, Integer points) {

    /** {@code Reason.of("VOLUME_SPIKE", 15, "ratio", 2.4)}: points, then alternating keys and values. */
    public static Reason of(String code, Integer points, Object... keysAndValues) {
        if (keysAndValues.length % 2 != 0) {
            throw new IllegalArgumentException("keysAndValues must come in pairs");
        }
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            values.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return new Reason(code, values, points);
    }

    /** An unscored reason. */
    public static Reason info(String code, Object... keysAndValues) {
        return of(code, null, keysAndValues);
    }
}
