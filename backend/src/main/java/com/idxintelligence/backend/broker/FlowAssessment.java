package com.idxintelligence.backend.broker;

import com.idxintelligence.backend.Reason;

import java.util.List;

/**
 * Rule-based reading of broker flow over a period, with the reasons behind it.
 * Describes what happened; it is not a forecast and is not yet backtested.
 */
public record FlowAssessment(Status status, List<Reason> reasons) {

    public enum Status {
        ACCUMULATION,
        NEUTRAL,
        DISTRIBUTION
    }
}
