package com.payflow.fraud;

import java.util.Optional;

/**
 * One pluggable fraud check. To add a rule, create a new @Component implementing this interface —
 * the engine picks it up automatically (Strategy pattern + Spring dependency injection).
 */
public interface FraudRule {
    Optional<RuleHit> evaluate(TransferContext ctx);
}
