package com.payflow.service;

import com.payflow.dto.Dtos.*;
import com.payflow.exception.ApiException;
import com.payflow.model.*;
import com.payflow.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Every automated decision can be appealed to a human, who must answer within an SLA and must
 * give a reason that the user sees. Approving an appeal acts immediately: the hold is released,
 * the blocked payment goes through, or the account is unfrozen.
 */
@Service
public class AppealService {

    private final AppealRepository appeals;
    private final UserRepository users;
    private final HoldRepository holds;
    private final TransferRepository transfers;
    private final WalletRepository wallets;
    private final HoldService holdService;
    private final WalletService walletService;
    private final TrustService trust;
    private final AuditService audit;
    private final int slaHours;

    public AppealService(AppealRepository appeals, UserRepository users, HoldRepository holds,
                         TransferRepository transfers, WalletRepository wallets, HoldService holdService,
                         WalletService walletService, TrustService trust, AuditService audit,
                         @Value("${app.appeals.sla-hours}") int slaHours) {
        this.appeals = appeals;
        this.users = users;
        this.holds = holds;
        this.transfers = transfers;
        this.wallets = wallets;
        this.holdService = holdService;
        this.walletService = walletService;
        this.trust = trust;
        this.audit = audit;
        this.slaHours = slaHours;
    }

    @Transactional
    public AppealResponse create(Long userId, AppealRequest req) {
        User user = users.findById(userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        Long target = req.targetId();
        switch (req.type()) {
            case HOLD -> {
                Hold h = holds.findById(target == null ? -1 : target)
                        .filter(x -> x.getWallet().getUser().getId().equals(userId))
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Hold not found"));
                if (h.getStatus() != HoldStatus.HELD) throw new ApiException(HttpStatus.CONFLICT, "This money has already been released");
            }
            case BLOCKED_TRANSFER -> {
                Transfer t = transfers.findById(target == null ? -1 : target)
                        .filter(x -> x.getSender().getUser().getId().equals(userId))
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Payment not found"));
                if (t.getStatus() != TransferStatus.BLOCKED) throw new ApiException(HttpStatus.CONFLICT, "This payment is not blocked");
            }
            case ACCOUNT_FREEZE -> {
                if (user.getStatus() != UserStatus.FROZEN) throw new ApiException(HttpStatus.CONFLICT, "Your account is not frozen");
                target = null;
            }
        }
        boolean duplicate = target == null
                ? appeals.existsByUserIdAndTypeAndStatus(userId, req.type(), AppealStatus.OPEN)
                : appeals.existsByUserIdAndTypeAndTargetIdAndStatus(userId, req.type(), target, AppealStatus.OPEN);
        if (duplicate) throw new ApiException(HttpStatus.CONFLICT, "You already have an open appeal for this — we'll get back to you soon.");

        Appeal a = appeals.save(new Appeal(user, req.type(), target, req.message().trim(),
                Instant.now().plus(slaHours, ChronoUnit.HOURS)));
        audit.log(user.getEmail(), "APPEAL_SUBMITTED", req.type().name().toLowerCase().replace('_', ' ')
                + (target == null ? "" : " #" + target));
        return toResponse(a, false);
    }

    @Transactional(readOnly = true)
    public List<AppealResponse> mine(Long userId) {
        return appeals.findByUserIdOrderByCreatedAtDesc(userId).stream().map(a -> toResponse(a, false)).toList();
    }

    @Transactional(readOnly = true)
    public List<AppealResponse> forAdmin(boolean openOnly) {
        List<Appeal> list = openOnly ? appeals.findByStatusOrderByDueAtAsc(AppealStatus.OPEN) : appeals.findTop200ByOrderByCreatedAtDesc();
        return list.stream().map(a -> toResponse(a, true)).toList();
    }

    @Transactional
    public AppealResponse decide(String adminEmail, Long appealId, DecideAppealRequest req) {
        Appeal a = appeals.findById(appealId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Appeal not found"));
        if (a.getStatus() != AppealStatus.OPEN) throw new ApiException(HttpStatus.CONFLICT, "This appeal has already been decided");
        if (req.approve()) {
            switch (a.getType()) {
                case HOLD -> holdService.release(a.getTargetId(), "appeal approved by " + adminEmail);
                case BLOCKED_TRANSFER -> walletService.releaseBlockedTransfer(a.getTargetId(),
                        "Released after review: " + req.note().trim());
                case ACCOUNT_FREEZE -> a.getUser().unfreeze();
            }
        }
        a.decide(req.approve(), req.note().trim(), adminEmail);
        audit.log(adminEmail, req.approve() ? "APPEAL_APPROVED" : "APPEAL_REJECTED",
                "appeal #" + a.getId() + " (" + a.getType().name().toLowerCase().replace('_', ' ') + ") from " + a.getUser().getEmail());
        return toResponse(a, true);
    }

    private AppealResponse toResponse(Appeal a, boolean forAdmin) {
        boolean overdue = a.getStatus() == AppealStatus.OPEN && a.getDueAt().isBefore(Instant.now());
        return new AppealResponse(a.getId(), a.getType().name(), a.getTargetId(), a.getMessage(), a.getStatus().name(),
                a.getDecisionNote(), forAdmin ? a.getDecidedBy() : (a.getDecidedBy() == null ? null : "PayFlow review team"),
                a.getDueAt(), a.getDecidedAt(), a.getCreatedAt(), overdue, a.getUser().getEmail(), a.getUser().getFullName(),
                context(a), forAdmin ? trust.score(a.getUser()).score() : null);
    }

    /** One-line summary of what is being appealed, for both the user and the reviewer. */
    private String context(Appeal a) {
        return switch (a.getType()) {
            case HOLD -> holds.findById(a.getTargetId())
                    .map(h -> "₹" + h.getAmount() + " on hold (" + h.getStatus().name().toLowerCase() + ") — " + h.getExplanation())
                    .orElse("Hold #" + a.getTargetId());
            case BLOCKED_TRANSFER -> transfers.findById(a.getTargetId())
                    .map(t -> "₹" + t.getAmount() + " to " + t.getReceiver().getUser().getEmail() + " (" + t.getStatus().name().toLowerCase()
                            + ") — " + (t.getFraudReason() == null ? "" : t.getFraudReason()))
                    .orElse("Payment #" + a.getTargetId());
            case ACCOUNT_FREEZE -> "Account freeze — reason given: "
                    + (a.getUser().getFreezeReason() == null ? "(already unfrozen)" : a.getUser().getFreezeReason());
        };
    }

    // ---- Resolution Center: everything about money that's held, blocked or under review, in one place ----

    @Transactional(readOnly = true)
    public ResolutionResponse resolution(Long userId) {
        User user = users.findById(userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        Long walletId = wallets.findIdByUserId(userId).orElseThrow();
        List<HoldResponse> holdList = holds.findTop50ByWalletIdOrderByCreatedAtDesc(walletId).stream()
                .map(h -> HoldService.toResponse(h, appeals.existsByUserIdAndTypeAndTargetIdAndStatus(userId, AppealType.HOLD, h.getId(), AppealStatus.OPEN)))
                .toList();
        List<TransferResponse> blocked = transfers.findTop20BySenderIdAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
                        walletId, TransferStatus.BLOCKED, Instant.now().minus(30, ChronoUnit.DAYS)).stream()
                .map(t -> TransferResponse.from(t, false)).toList();
        return new ResolutionResponse(trust.score(user), holdList, blocked, mine(userId), user.getStatus().name(),
                user.getFreezeReason(), user.getFrozenAt());
    }
}
