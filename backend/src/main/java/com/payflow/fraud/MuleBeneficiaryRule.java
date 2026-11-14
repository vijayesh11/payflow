package com.payflow.fraud;

import com.payflow.model.Severity;
import com.payflow.model.TransferStatus;
import com.payflow.repository.TransferRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Beneficiary risk ("money mule" cut-off). If other rules have already blocked several transfers INTO an
 * account recently, that account is probably collecting stolen money, so block every new transfer to it,
 * even ones that look harmless on their own. This is what catches "low and slow" fraud, which stays under
 * every per-sender limit but still has to send the money somewhere.
 */
@Component
public class MuleBeneficiaryRule implements FraudRule {

    private final TransferRepository transfers;
    private final FraudProperties props;

    public MuleBeneficiaryRule(TransferRepository transfers, FraudProperties props) {
        this.transfers = transfers;
        this.props = props;
    }

    @Override
    public Optional<RuleHit> evaluate(TransferContext ctx) {
        var b = props.beneficiary();
        Instant windowStart = Instant.now().minus(b.windowHours(), ChronoUnit.HOURS);
        long blockedIncoming = transfers.countByReceiverIdAndStatusAndCreatedAtAfter(
                ctx.receiverWallet().getId(), TransferStatus.BLOCKED, windowStart);
        if (blockedIncoming >= b.blockedThreshold()) {
            return Optional.of(new RuleHit("MULE_BENEFICIARY", Severity.HIGH,
                    "recipient received " + blockedIncoming + " blocked transfers in last " + b.windowHours() + "h"));
        }
        return Optional.empty();
    }
}
