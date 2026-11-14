package com.payflow.fraud;

import com.payflow.model.Severity;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Unusually large single transfers: alert above one limit, block above a higher one. */
@Component
public class ThresholdRule implements FraudRule {

    private final FraudProperties props;

    public ThresholdRule(FraudProperties props) {
        this.props = props;
    }

    @Override
    public Optional<RuleHit> evaluate(TransferContext ctx) {
        var t = props.threshold();
        if (ctx.amount().compareTo(t.blockAmount()) > 0) {
            return Optional.of(new RuleHit("THRESHOLD", Severity.HIGH,
                    "amount " + ctx.amount() + " exceeds block limit " + t.blockAmount()));
        }
        if (ctx.amount().compareTo(t.alertAmount()) > 0) {
            return Optional.of(new RuleHit("THRESHOLD", Severity.MEDIUM,
                    "amount " + ctx.amount() + " exceeds alert limit " + t.alertAmount()));
        }
        return Optional.empty();
    }
}
