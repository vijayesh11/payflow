package com.payflow.fraud;

import com.payflow.model.Severity;
import java.util.List;
import java.util.stream.Collectors;

public record FraudDecision(List<RuleHit> hits) {

    /** Policy: any HIGH-severity hit blocks the transfer; lower severities only raise alerts. */
    public boolean blocked() {
        return hits.stream().anyMatch(h -> h.severity() == Severity.HIGH);
    }

    public String reason() {
        return hits.stream().map(h -> h.rule() + ": " + h.details()).collect(Collectors.joining(" | "));
    }
}
