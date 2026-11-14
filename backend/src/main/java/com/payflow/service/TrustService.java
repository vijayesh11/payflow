package com.payflow.service;

import com.payflow.dto.Dtos.TrustFactor;
import com.payflow.dto.Dtos.TrustScoreResponse;
import com.payflow.model.*;
import com.payflow.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * A transparent Trust Score (0-100). PayPal-style systems keep this kind of scoring secret, so users
 * can't tell why their money is held. Here every point is listed, with what to do to earn it.
 * The score decides how much of a large incoming payment is released immediately.
 */
@Service
public class TrustService {

    public static final int BUILDING = 40;
    public static final int TRUSTED = 70;

    private final WalletRepository wallets;
    private final CardRepository cards;
    private final TransferRepository transfers;
    private final MilestoneRepository milestones;
    private final FraudAlertRepository alerts;

    public TrustService(WalletRepository wallets, CardRepository cards, TransferRepository transfers,
                        MilestoneRepository milestones, FraudAlertRepository alerts) {
        this.wallets = wallets;
        this.cards = cards;
        this.transfers = transfers;
        this.milestones = milestones;
        this.alerts = alerts;
    }

    @Transactional(readOnly = true)
    public TrustScoreResponse score(User u) {
        List<TrustFactor> f = new ArrayList<>();
        Long walletId = wallets.findIdByUserId(u.getId()).orElse(-1L);

        f.add(new TrustFactor("Account created", 10, 10, true, null));
        f.add(new TrustFactor("UPI PIN set", u.hasPin() ? 15 : 0, 15, u.hasPin(), "Set your UPI PIN in Security"));
        f.add(new TrustFactor("2-step verification on", u.isTwoFactorEnabled() ? 10 : 0, 10, u.isTwoFactorEnabled(),
                "Turn on 2-step verification in Security"));
        boolean hasCard = cards.countByUserId(u.getId()) > 0;
        f.add(new TrustFactor("Card linked", hasCard ? 10 : 0, 10, hasCard, "Link a debit or credit card"));

        long days = Duration.between(u.getCreatedAt(), Instant.now()).toDays();
        int agePts = (int) Math.min(15, days / 2);
        f.add(new TrustFactor("Account age (" + days + (days == 1 ? " day)" : " days)"), agePts, 15, agePts == 15,
                "Grows automatically over your first 30 days"));

        long received = transfers.countByReceiverIdAndStatus(walletId, TransferStatus.COMPLETED);
        int recvPts = (int) Math.min(20, received * 2);
        f.add(new TrustFactor("Payments received (" + received + ")", recvPts, 20, recvPts == 20,
                "Each payment you receive adds 2 points"));

        long delivered = milestones.countForFreelancer(u.getId(), MilestoneStatus.RELEASED);
        int escrowPts = (int) Math.min(20, delivered * 5);
        f.add(new TrustFactor("Escrow milestones delivered (" + delivered + ")", escrowPts, 20, escrowPts == 20,
                "Each escrow milestone you complete adds 5 points"));

        // penalties
        long openHigh = alerts.countByUserIdAndResolvedFalseAndSeverity(u.getId(), Severity.HIGH);
        if (openHigh > 0) {
            f.add(new TrustFactor("Open fraud alerts (" + openHigh + ")", (int) -Math.min(30, openHigh * 10), 0, false,
                    "Resolved after review, or by appealing the blocked payment"));
        }
        long blocked = transfers.countBySenderIdAndStatusAndCreatedAtAfter(walletId, TransferStatus.BLOCKED,
                Instant.now().minus(30, ChronoUnit.DAYS));
        if (blocked > 0) {
            f.add(new TrustFactor("Blocked payment attempts, last 30 days (" + blocked + ")", (int) -Math.min(15, blocked * 3), 0,
                    false, "Drops off after 30 days"));
        }
        if (u.getStatus() == UserStatus.FROZEN) {
            f.add(new TrustFactor("Account frozen", -40, 0, false, "Appeal the freeze from the Resolution Center"));
        }

        int score = Math.max(0, Math.min(100, f.stream().mapToInt(TrustFactor::points).sum()));
        String tier = score >= TRUSTED ? "Trusted" : score >= BUILDING ? "Building" : "New";
        int holdPercent = score >= TRUSTED ? 0 : score >= BUILDING ? 30 : 60;
        String next = score >= TRUSTED ? null : score >= BUILDING ? "Trusted" : "Building";
        int toNext = score >= TRUSTED ? 0 : (score >= BUILDING ? TRUSTED : BUILDING) - score;
        return new TrustScoreResponse(score, tier, holdPercent, next, toNext, f);
    }
}
