package com.payflow.service;

import com.payflow.dto.Dtos.*;
import com.payflow.exception.ApiException;
import com.payflow.model.*;
import com.payflow.repository.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AdminService {

    private final TransferRepository transfers;
    private final FraudAlertRepository alerts;
    private final UserRepository users;
    private final WalletRepository wallets;
    private final CardRepository cards;
    private final AuditLogRepository auditLogs;
    private final AuditService audit;
    private final TrustService trust;
    private final AppealRepository appeals;
    private final HoldRepository holds;
    private final MilestoneRepository milestones;

    public AdminService(TransferRepository transfers, FraudAlertRepository alerts, UserRepository users,
                        WalletRepository wallets, CardRepository cards, AuditLogRepository auditLogs,
                        AuditService audit, TrustService trust, AppealRepository appeals, HoldRepository holds,
                        MilestoneRepository milestones) {
        this.transfers = transfers;
        this.alerts = alerts;
        this.users = users;
        this.wallets = wallets;
        this.cards = cards;
        this.auditLogs = auditLogs;
        this.audit = audit;
        this.trust = trust;
        this.appeals = appeals;
        this.holds = holds;
        this.milestones = milestones;
    }

    @Transactional(readOnly = true)
    public List<AlertResponse> alerts(boolean openOnly) {
        List<FraudAlert> list = openOnly ? alerts.findByResolvedFalseOrderByCreatedAtDesc()
                                         : alerts.findTop200ByOrderByCreatedAtDesc();
        return list.stream().map(AlertResponse::from).toList();
    }

    @Transactional
    public AlertResponse resolve(String adminEmail, Long alertId) {
        FraudAlert a = alerts.findById(alertId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Alert not found"));
        a.resolve();
        audit.log(adminEmail, "ALERT_RESOLVED", "alert #" + a.getId() + " (" + a.getRule() + ") for " + a.getUser().getEmail());
        return AlertResponse.from(a);
    }

    @Transactional(readOnly = true)
    public List<TransferResponse> recentTransfers(int limit) {
        return transfers.findRecent(PageRequest.of(0, Math.max(1, Math.min(limit, 500)))).stream()
                .map(t -> TransferResponse.from(t, false)).toList();
    }

    @Transactional(readOnly = true)
    public StatsResponse stats() {
        Map<String, Long> byRule = new TreeMap<>();
        for (Object[] row : alerts.countByRule()) {
            byRule.put((String) row[0], (Long) row[1]);
        }
        Instant dayAgo = Instant.now().minus(24, ChronoUnit.HOURS);
        BigDecimal volume = transfers.findByCreatedAtAfter(dayAgo).stream()
                .filter(t -> t.getStatus() == TransferStatus.COMPLETED)
                .map(Transfer::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<User> all = users.findAll();
        long frozen = all.stream().filter(u -> u.getStatus() == UserStatus.FROZEN).count();
        long overdue = appeals.findByStatusOrderByDueAtAsc(AppealStatus.OPEN).stream()
                .filter(a -> a.getDueAt().isBefore(Instant.now())).count();
        BigDecimal held = holds.findByStatus(HoldStatus.HELD).stream().map(Hold::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal escrow = milestones.findAll().stream()
                .filter(m -> m.getStatus() == MilestoneStatus.FUNDED || m.getStatus() == MilestoneStatus.SUBMITTED
                        || m.getStatus() == MilestoneStatus.DISPUTED)
                .map(Milestone::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new StatsResponse(transfers.count(), transfers.countByStatus(TransferStatus.COMPLETED),
                transfers.countByStatus(TransferStatus.BLOCKED), alerts.countByResolvedFalse(),
                all.size(), frozen, volume, byRule, appeals.countByStatus(AppealStatus.OPEN), overdue,
                milestones.countByStatus(MilestoneStatus.DISPUTED), held, escrow);
    }

    /** Completed vs blocked transfers per hour for the last 24 hours (admin overview chart). */
    @Transactional(readOnly = true)
    public List<TimelinePoint> timeline() {
        Instant end = Instant.now().truncatedTo(ChronoUnit.HOURS);
        Instant start = end.minus(23, ChronoUnit.HOURS);
        Map<Instant, long[]> buckets = new TreeMap<>();
        for (int i = 0; i < 24; i++) buckets.put(start.plus(i, ChronoUnit.HOURS), new long[2]);
        for (Transfer t : transfers.findByCreatedAtAfter(start)) {
            long[] b = buckets.get(t.getCreatedAt().truncatedTo(ChronoUnit.HOURS));
            if (b != null) b[t.getStatus() == TransferStatus.COMPLETED ? 0 : 1]++;
        }
        return buckets.entrySet().stream()
                .map(e -> new TimelinePoint(e.getKey(), e.getValue()[0], e.getValue()[1])).toList();
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> users() {
        Map<Long, Wallet> walletByUser = wallets.findAll().stream()
                .collect(Collectors.toMap(w -> w.getUser().getId(), Function.identity()));
        return users.findAllByOrderByCreatedAtDesc().stream().map(u -> {
            Wallet w = walletByUser.get(u.getId());
            TrustScoreResponse t = trust.score(u);
            return new AdminUserResponse(u.getId(), u.getEmail(), u.getFullName(), u.getRole().name(),
                    u.getStatus().name(), u.isLocked(), u.getLockedUntil(), u.isPinLocked(), u.hasPin(),
                    u.isTwoFactorEnabled(),
                    w == null ? BigDecimal.ZERO : w.getBalance(), w == null ? BigDecimal.ZERO : w.getHeldBalance(),
                    cards.countByUserId(u.getId()), t.score(), t.tier(), u.getFreezeReason(),
                    u.getCreatedAt(), u.getLastLoginAt());
        }).toList();
    }

    @Transactional
    /** A freeze must carry a reason the customer will see — no black-box limitations. */
    public void setFrozen(String adminEmail, Long userId, boolean frozen, String reason) {
        User u = users.findById(userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        if (u.getRole() == Role.ADMIN) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Admin accounts cannot be frozen");
        }
        if (frozen) {
            if (reason == null || reason.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "A reason is required");
            u.freeze(reason.trim());
        } else {
            u.unfreeze();
        }
        audit.log(adminEmail, frozen ? "USER_FROZEN" : "USER_UNFROZEN", u.getEmail() + (frozen ? " — " + reason.trim() : ""));
    }

    @Transactional
    public void unlock(String adminEmail, Long userId) {
        User u = users.findById(userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        u.unlock();
        audit.log(adminEmail, "USER_UNLOCKED", u.getEmail());
    }

    @Transactional(readOnly = true)
    public List<AuditResponse> auditLog() {
        return auditLogs.findTop300ByOrderByCreatedAtDesc().stream().map(AuditResponse::from).toList();
    }
}
