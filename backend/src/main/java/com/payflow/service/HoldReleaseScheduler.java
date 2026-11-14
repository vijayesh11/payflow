package com.payflow.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Releases holds when they're due, or as soon as the recipient becomes "Trusted". */
@Component
public class HoldReleaseScheduler {

    private static final Logger log = LoggerFactory.getLogger(HoldReleaseScheduler.class);

    private final HoldService holdService;

    public HoldReleaseScheduler(HoldService holdService) {
        this.holdService = holdService;
    }

    @Scheduled(fixedDelayString = "${app.holds.check-interval-ms}")
    public void run() {
        int released = 0;
        for (Long id : holdService.dueHoldIds()) {
            try { if (holdService.release(id, "scheduled release")) released++; }
            catch (RuntimeException e) { log.warn("Could not release hold {}: {}", id, e.getMessage()); }
        }
        for (Long id : holdService.trustedHoldIds()) {
            try { if (holdService.release(id, "Trust Score reached Trusted")) released++; }
            catch (RuntimeException e) { log.warn("Could not release hold {}: {}", id, e.getMessage()); }
        }
        if (released > 0) log.info("Released {} hold(s)", released);
    }

}
