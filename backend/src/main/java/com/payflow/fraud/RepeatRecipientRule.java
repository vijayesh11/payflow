package com.payflow.fraud;

import com.payflow.model.Severity;
import com.payflow.repository.TransferRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/**
 * Many rapid transfers from one sender to the SAME recipient. Real people rarely pay the same person
 * several times a minute; scripts draining a stolen account into one mule account do.
 * Tighter than the general velocity rule, because it targets a narrower pattern.
 */
@Component
public class RepeatRecipientRule implements FraudRule {

    private final TransferRepository transfers;
    private final FraudProperties props;

    public RepeatRecipientRule(TransferRepository transfers, FraudProperties props) {
        this.transfers = transfers;
        this.props = props;
    }

    @Override
    public Optional<RuleHit> evaluate(TransferContext ctx) {
        var r = props.repeatRecipient();
        Instant windowStart = Instant.now().minusSeconds(r.windowSeconds());
        long recent = transfers.countBySenderIdAndReceiverIdAndCreatedAtAfter(
                ctx.senderWallet().getId(), ctx.receiverWallet().getId(), windowStart);
        if (recent >= r.maxTransfers()) {
            return Optional.of(new RuleHit("REPEAT_RECIPIENT", Severity.HIGH,
                    recent + " transfers to the same recipient in last " + r.windowSeconds() + "s (limit " + r.maxTransfers() + ")"));
        }
        return Optional.empty();
    }
}
