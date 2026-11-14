package com.payflow.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Pays out submitted milestones whose review window ended with no approval and no dispute. */
@Component
public class EscrowScheduler {

    private static final Logger log = LoggerFactory.getLogger(EscrowScheduler.class);
    private final EscrowService escrow;

    public EscrowScheduler(EscrowService escrow) {
        this.escrow = escrow;
    }

    @Scheduled(fixedDelayString = "${app.holds.check-interval-ms}")
    public void run() {
        for (Long id : escrow.dueForAutoRelease()) {
            try {
                if (escrow.autoRelease(id)) log.info("Auto-released milestone {}", id);
            } catch (RuntimeException e) {
                log.warn("Could not auto-release milestone {}: {}", id, e.getMessage());
            }
        }
    }
}
