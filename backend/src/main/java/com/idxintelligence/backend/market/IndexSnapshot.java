package com.idxintelligence.backend.market;

import java.math.BigDecimal;

public record IndexSnapshot(
        String code,
        String name,
        BigDecimal value,
        BigDecimal previousClose,
        BigDecimal change,
        BigDecimal changePercent
) {
}
