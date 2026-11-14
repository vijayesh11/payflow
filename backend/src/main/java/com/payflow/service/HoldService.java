package com.payflow.service;

import com.payflow.dto.Dtos.HoldResponse;
import com.payflow.dto.Dtos.TrustFactor;
import com.payflow.dto.Dtos.TrustScoreResponse;
import com.payflow.fraud.FraudDecision;
import com.payflow.model.*;
import com.payflow.repository.HoldRepository;
import com.payflow.repository.WalletRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Collectors;

/**
 * Progressive release instead of a blanket "up to 21 days" hold:
 *  - only part of a payment is held, sized by the recipient's Trust Score (60% / 30% / 0%),
 *  - or 50% when the payment itself raised a medium-risk alert,
 *  - each hold says exactly why, when it releases, and how to release it sooner,
 *  - holds release on schedule, instantly when the user reaches "Trusted", or on an approved appeal.
 */
@Service
public class HoldService {

    public record HoldPlan(BigDecimal amount, String reasonCode, String explanation, String howToRelease, Instant releaseAt) {}

    private final HoldRepository holds;
    private final WalletRepository wallets;
    private final TrustService trust;
    private final AuditService audit;
    private final BigDecimal minAmount;
    private final int releaseMinutes;

    public HoldService(HoldRepository holds, WalletRepository wallets, TrustService trust, AuditService audit,
                       @Value("${app.holds.min-amount}") BigDecimal minAmount,
                       @Value("${app.holds.release-minutes}") int releaseMinutes) {
        this.holds = holds;
        this.wallets = wallets;
        this.trust = trust;
        this.audit = audit;
        this.minAmount = minAmount;
        this.releaseMinutes = releaseMinutes;
    }

    /** Decide how much of an incoming payment to hold (null = release everything now). */
    public HoldPlan plan(User recipient, BigDecimal amount, FraudDecision decision) {
        Instant releaseAt = Instant.now().plus(releaseMinutes, ChronoUnit.MINUTES);
        String when = releaseMinutes >= 120 ? (releaseMinutes / 60) + " hours" : releaseMinutes + " minutes";

        boolean mediumRisk = decision.hits().stream().anyMatch(h -> h.severity() == Severity.MEDIUM);
        if (mediumRisk) {
            String why = decision.hits().stream().map(h -> h.details()).collect(Collectors.joining("; "));
            return new HoldPlan(pct(amount, 50), "RISK_REVIEW",
                    "Half of this payment is on hold because it triggered a routine review (" + why + "). "
                            + "This is a precaution, not an accusation.",
                    "It releases automatically in " + when + ". Need it sooner? Request an early release and tell us what the payment is for.",
                    releaseAt);
        }
        if (amount.compareTo(minAmount) < 0) return null;

        TrustScoreResponse t = trust.score(recipient);
        if (t.holdPercent() == 0) return null;
        String quickWins = t.factors().stream().filter(f -> !f.achieved() && f.hint() != null && f.maxPoints() > 0)
                .limit(2).map(TrustFactor::hint).collect(Collectors.joining("; "));
        return new HoldPlan(pct(amount, t.holdPercent()), "NEW_ACCOUNT",
                t.holdPercent() + "% of this payment is held because your account is still building trust "
                        + "(Trust Score " + t.score() + "/100, " + t.tier() + "). The other " + (100 - t.holdPercent())
                        + "% is available right now.",
                "Releases automatically in " + when + ", or instantly once your Trust Score reaches " + TrustService.TRUSTED + "."
                        + (quickWins.isEmpty() ? "" : " Quick wins: " + quickWins + "."),
                releaseAt);
    }

    public Hold create(Wallet wallet, Transfer transfer, HoldPlan p) {
        return holds.save(new Hold(wallet, transfer, p.amount(), p.reasonCode(), p.explanation(), p.howToRelease(), p.releaseAt()));
    }

    /** Releases one hold into the spendable balance. Returns false if it was already released. */
    @Transactional
    public boolean release(Long holdId, String by) {
        Hold h = holds.findById(holdId).orElse(null);
        if (h == null || h.getStatus() != HoldStatus.HELD) return false;
        Wallet w = wallets.findByIdForUpdate(h.getWallet().getId()).orElseThrow();
        w.releaseHeld(h.getAmount());
        h.markReleased(by);
        audit.log(w.getUser().getEmail(), "HOLD_RELEASED", h.getAmount() + " released (" + by + ")");
        return true;
    }

    @Transactional(readOnly = true)
    public java.util.List<Long> dueHoldIds() {
        return holds.findByStatusAndReleaseAtBefore(HoldStatus.HELD, Instant.now()).stream().map(Hold::getId).toList();
    }

    /** Holds whose owner has since become Trusted: released early (the score is computed once per user). */
    @Transactional(readOnly = true)
    public java.util.List<Long> trustedHoldIds() {
        java.util.Map<Long, Boolean> trustedByUser = new java.util.HashMap<>();
        return holds.findByStatus(HoldStatus.HELD).stream()
                .filter(h -> trustedByUser.computeIfAbsent(h.getWallet().getUser().getId(),
                        id -> trust.score(h.getWallet().getUser()).score() >= TrustService.TRUSTED))
                .map(Hold::getId).toList();
    }

    public static HoldResponse toResponse(Hold h, boolean appealOpen) {
        Transfer t = h.getTransfer();
        return new HoldResponse(h.getId(), h.getAmount(), h.getReasonCode(), h.getExplanation(), h.getHowToRelease(),
                h.getStatus().name(), h.getReleaseAt(), h.getReleasedAt(), h.getReleasedBy(), h.getCreatedAt(),
                t == null ? null : t.getId(), t == null ? null : t.getSender().getUser().getFullName(), appealOpen);
    }

    private static BigDecimal pct(BigDecimal amount, int percent) {
        return amount.multiply(BigDecimal.valueOf(percent)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
