package com.payflow.fraud;

import com.payflow.model.FraudAlert;
import com.payflow.model.Severity;
import com.payflow.model.TransferStatus;
import com.payflow.model.Wallet;
import com.payflow.repository.FraudAlertRepository;
import com.payflow.repository.TransferRepository;
import com.payflow.repository.WalletRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Second line of defence. Real-time rules see one transfer at a time; this periodic job looks at
 * aggregate behaviour ("low and slow" draining that stays under every per-transfer limit).
 */
@Component
public class FraudScanScheduler {

    private static final Logger log = LoggerFactory.getLogger(FraudScanScheduler.class);
    static final String RULE = "HIGH_OUTFLOW";

    private final TransferRepository transfers;
    private final WalletRepository wallets;
    private final FraudAlertRepository alerts;
    private final FraudProperties props;

    public FraudScanScheduler(TransferRepository transfers, WalletRepository wallets,
                              FraudAlertRepository alerts, FraudProperties props) {
        this.transfers = transfers;
        this.wallets = wallets;
        this.alerts = alerts;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "${fraud.batch.interval-ms}")
    @Transactional
    public void scan() {
        if (!props.enabled()) return;
        var batch = props.batch();
        Instant windowStart = Instant.now().minus(batch.windowMinutes(), ChronoUnit.MINUTES);

        int raised = 0;
        for (Object[] row : transfers.findHighOutflow(TransferStatus.COMPLETED, windowStart, batch.maxOutflow())) {
            Long walletId = (Long) row[0];
            BigDecimal total = (BigDecimal) row[1];
            Wallet wallet = wallets.findById(walletId).orElseThrow();
            Long userId = wallet.getUser().getId();
            if (alerts.existsByUserIdAndRuleAndCreatedAtAfter(userId, RULE, windowStart)) continue; // de-duplicate
            alerts.save(new FraudAlert(null, wallet.getUser(), RULE, Severity.MEDIUM,
                    "sent " + total + " in last " + batch.windowMinutes() + " min (limit " + batch.maxOutflow() + ")"));
            raised++;
        }
        if (raised > 0) log.info("Fraud scan raised {} HIGH_OUTFLOW alert(s)", raised);
    }
}
