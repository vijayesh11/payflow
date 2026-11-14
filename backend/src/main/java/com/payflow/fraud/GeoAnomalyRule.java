package com.payflow.fraud;

import com.payflow.model.Severity;
import com.payflow.model.TransferStatus;
import com.payflow.repository.TransferRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * "Impossible travel": the sender's last successful transfer came from a different country only minutes ago.
 * Geolocation is simulated via the X-Country request header (a real system would derive it from the IP).
 */
@Component
public class GeoAnomalyRule implements FraudRule {

    private final TransferRepository transfers;
    private final FraudProperties props;

    public GeoAnomalyRule(TransferRepository transfers, FraudProperties props) {
        this.transfers = transfers;
        this.props = props;
    }

    @Override
    public Optional<RuleHit> evaluate(TransferContext ctx) {
        if (ctx.country() == null) return Optional.empty();
        return transfers.findFirstBySenderIdAndStatusOrderByCreatedAtDesc(ctx.senderWallet().getId(), TransferStatus.COMPLETED)
                .filter(last -> last.getCountry() != null && !last.getCountry().equalsIgnoreCase(ctx.country()))
                .filter(last -> Duration.between(last.getCreatedAt(), Instant.now()).toMinutes() < props.geo().windowMinutes())
                .map(last -> new RuleHit("GEO_ANOMALY", Severity.HIGH,
                        "country changed " + last.getCountry() + " -> " + ctx.country().toUpperCase()
                                + " within " + props.geo().windowMinutes() + " min"));
    }
}
