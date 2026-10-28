package com.idxintelligence.backend.market;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** The latest session's price line per stock, for the mini charts next to daily lists. */
public interface IntradayLineSource {

    /**
     * @return per ticker: the previous close followed by the session's prices (5-minute steps), so the
     *         line's start-to-end direction equals the day's change; tickers without data are left out
     */
    Map<String, List<BigDecimal>> sessionLines(List<String> tickers);
}
