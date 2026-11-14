package com.payflow.fraud;

import com.payflow.model.Severity;
import com.payflow.repository.TransferRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/** Too many transfers in a short window — typical of a stolen account being drained by a script. */
@Component
public class VelocityRule implements FraudRule {

    private final TransferRepository transfers;
    private final FraudProperties props;

    public VelocityRule(TransferRepository transfers, FraudProperties props) {
        this.transfers = transfers;
        this.props = props;
    }

    @Override
    public Optional<RuleHit> evaluate(TransferContext ctx) {
        var v = props.velocity();
        Instant windowStart = Instant.now().minusSeconds(v.windowSeconds());
        long recent = transfers.countBySenderIdAndCreatedAtAfter(ctx.senderWallet().getId(), windowStart);
        if (recent >= v.maxTransfers()) {
            return Optional.of(new RuleHit("VELOCITY", Severity.HIGH,
                    recent + " transfers in last " + v.windowSeconds() + "s (limit " + v.maxTransfers() + ")"));
        }
        return Optional.empty();
    }
}
