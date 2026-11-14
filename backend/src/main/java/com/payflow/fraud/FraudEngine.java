package com.payflow.fraud;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class FraudEngine {

    private final List<FraudRule> rules;
    private final FraudProperties props;

    public FraudEngine(List<FraudRule> rules, FraudProperties props) {
        this.rules = rules;
        this.props = props;
    }

    public FraudDecision evaluate(TransferContext ctx) {
        if (!props.enabled()) {
            return new FraudDecision(List.of());   // baseline mode, used to measure the engine's effect
        }
        List<RuleHit> hits = rules.stream()
                .map(r -> r.evaluate(ctx))
                .flatMap(Optional::stream)
                .toList();
        return new FraudDecision(hits);
    }
}
