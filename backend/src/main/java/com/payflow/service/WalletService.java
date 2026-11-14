package com.payflow.service;

import com.payflow.dto.Dtos.*;
import com.payflow.exception.ApiException;
import com.payflow.fraud.FraudDecision;
import com.payflow.fraud.FraudEngine;
import com.payflow.fraud.TransferContext;
import com.payflow.model.*;
import com.payflow.otp.OtpService;
import com.payflow.otp.OtpService.VerifiedOtp;
import com.payflow.repository.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
public class WalletService {

    private final WalletRepository wallets;
    private final UserRepository users;
    private final TransferRepository transfers;
    private final FraudAlertRepository alerts;
    private final CardRepository cards;
    private final TopUpRepository topUps;
    private final FraudEngine fraudEngine;
    private final AuditService audit;
    private final OtpService otp;
    private final HoldService holdService;

    public WalletService(WalletRepository wallets, UserRepository users, TransferRepository transfers,
                         FraudAlertRepository alerts, CardRepository cards, TopUpRepository topUps,
                         FraudEngine fraudEngine, AuditService audit, OtpService otp, HoldService holdService) {
        this.wallets = wallets;
        this.users = users;
        this.transfers = transfers;
        this.alerts = alerts;
        this.cards = cards;
        this.topUps = topUps;
        this.fraudEngine = fraudEngine;
        this.audit = audit;
        this.otp = otp;
        this.holdService = holdService;
    }

    @Transactional(readOnly = true)
    public WalletResponse getWallet(Long userId) {
        return toResponse(walletOf(userId));
    }

    /**
     * Add money from a linked card, step 1: the "bank" sends an OTP to approve the charge (3-D Secure style).
     * Nothing is charged until the OTP is confirmed.
     */
    @Transactional
    public OtpChallengeResponse startDeposit(Long userId, DepositRequest req) {
        User user = requireActive(userId);
        Card card = cards.findByIdAndUserId(req.cardId(), userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Card not found"));
        return otp.issue(user, OtpPurpose.CARD_TOPUP, card.getId(), req.amount(),
                "Approve a payment of INR " + req.amount() + " to PayFlow Wallet using your " + card.getBrand()
                        + " card ending " + card.getLast4() + ".");
    }

    /** Step 2: OTP confirmed -> charge the card (simulated) and credit the wallet, using the amount that was approved. */
    @Transactional
    public WalletResponse confirmDeposit(Long userId, ConfirmDepositRequest req) {
        VerifiedOtp v = otp.verify(req.challengeId(), userId, OtpPurpose.CARD_TOPUP, req.code());
        User user = requireActive(userId);
        Card card = cards.findByIdAndUserId(v.cardId(), userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Card not found"));
        Wallet w = wallets.findByIdForUpdate(walletIdOf(userId)).orElseThrow();
        w.credit(v.amount());
        topUps.save(new TopUp(w, v.amount(), card.getBrand(), card.getLast4()));
        audit.log(user.getEmail(), "MONEY_ADDED", v.amount() + " from " + card.getBrand() + " ending " + card.getLast4());
        return toResponse(w);
    }

    /**
     * The core of the project. Everything inside runs in ONE database transaction:
     * either the debit, the credit and the transfer record are all saved, or none of them are (ACID).
     */
    @Transactional
    public TransferResult transfer(Long userId, TransferRequest req, String idempotencyKey, String country) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Idempotency-Key header must be 1-100 characters");
        }
        requireActive(userId);
        // Resolve ids only. Loading the Wallet entities now would cache an unlocked (possibly stale)
        // balance in the persistence context, and the locking query below would return that cached copy.
        Long senderId = walletIdOf(userId);
        User recipient = users.findByEmail(req.recipientEmail().trim().toLowerCase())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No PayFlow account with that email"));
        if (recipient.getStatus() == UserStatus.FROZEN) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "This recipient cannot receive payments");
        }
        Long receiverId = walletIdOf(recipient.getId());
        if (senderId.equals(receiverId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot transfer to yourself");
        }

        // 1. Lock both wallet rows, ALWAYS in ascending id order. If A->B and B->A run at the same time,
        //    both try to lock the lower id first, so they queue instead of deadlocking.
        Wallet first = wallets.findByIdForUpdate(Math.min(senderId, receiverId)).orElseThrow();
        Wallet second = wallets.findByIdForUpdate(Math.max(senderId, receiverId)).orElseThrow();
        Wallet sender = first.getId().equals(senderId) ? first : second;
        Wallet receiver = sender == first ? second : first;

        // 2. Idempotency check, done AFTER taking the sender lock: a duplicate request that was waiting on the
        //    lock now sees the committed original and replays it instead of charging twice.
        Optional<Transfer> existing = transfers.findBySenderIdAndIdempotencyKey(sender.getId(), idempotencyKey);
        if (existing.isPresent()) {
            Transfer t = existing.get();
            boolean samePayload = t.getAmount().compareTo(req.amount()) == 0
                    && t.getReceiver().getId().equals(receiver.getId());
            if (!samePayload) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Idempotency-Key was already used for a different transfer");
            }
            return new TransferResult(TransferResponse.from(t, true));
        }

        // 3. Fraud check (runs under the sender lock, so velocity counts can't be raced)
        String cc = country == null || country.isBlank() ? null : country.trim().toUpperCase();
        String note = req.note() == null || req.note().isBlank() ? null : req.note().trim();
        FraudDecision decision = fraudEngine.evaluate(new TransferContext(sender, receiver, req.amount(), cc));

        if (decision.blocked()) {
            // Record the blocked attempt (no money moves) so it's auditable and counts toward velocity
            Transfer blocked = transfers.save(new Transfer(sender, receiver, req.amount(), TransferStatus.BLOCKED,
                    idempotencyKey, cc, decision.reason(), note));
            saveAlerts(decision, blocked, sender.getUser());
            return new TransferResult(TransferResponse.from(blocked, false));
        }

        // 4. Move the money
        if (sender.getBalance().compareTo(req.amount()) < 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Insufficient funds");  // rolls back; nothing saved
        }
        sender.debit(req.amount());
        // 5. Progressive release: part of a large payment to a not-yet-trusted account is held,
        //    with a plain-language reason and an exact release time (instead of an opaque blanket hold).
        HoldService.HoldPlan plan = holdService.plan(receiver.getUser(), req.amount(), decision);
        BigDecimal held = plan == null ? BigDecimal.ZERO : plan.amount();
        receiver.credit(req.amount().subtract(held));
        if (held.signum() > 0) receiver.hold(held);
        Transfer done = new Transfer(sender, receiver, req.amount(), TransferStatus.COMPLETED,
                idempotencyKey, cc, decision.hits().isEmpty() ? null : decision.reason(), note);
        if (held.signum() > 0) done.setHeldAmount(held);
        done = transfers.save(done);
        if (plan != null) holdService.create(receiver, done, plan);
        saveAlerts(decision, done, sender.getUser());   // MEDIUM hits: allowed but flagged for review
        return new TransferResult(TransferResponse.from(done, false));
    }

    /**
     * An admin approved the appeal on a blocked payment: execute it now, atomically, with a review note.
     * Fails cleanly (nothing changes) if the sender no longer has the funds.
     */
    @Transactional
    public TransferResponse releaseBlockedTransfer(Long transferId, String reviewNote) {
        Transfer t = transfers.findById(transferId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Payment not found"));
        if (t.getStatus() != TransferStatus.BLOCKED) {
            throw new ApiException(HttpStatus.CONFLICT, "This payment is no longer blocked");
        }
        Long senderId = t.getSender().getId();
        Long receiverId = t.getReceiver().getId();
        Wallet first = wallets.findByIdForUpdate(Math.min(senderId, receiverId)).orElseThrow();
        Wallet second = wallets.findByIdForUpdate(Math.max(senderId, receiverId)).orElseThrow();
        Wallet sender = first.getId().equals(senderId) ? first : second;
        Wallet receiver = sender == first ? second : first;
        if (sender.getBalance().compareTo(t.getAmount()) < 0) {
            throw new ApiException(HttpStatus.CONFLICT, "The sender no longer has enough balance to complete this payment");
        }
        sender.debit(t.getAmount());
        receiver.credit(t.getAmount());
        t.completeAfterReview(reviewNote);
        alerts.findByTransferId(t.getId()).forEach(FraudAlert::resolve);
        audit.log(sender.getUser().getEmail(), "PAYMENT_RELEASED", t.getAmount() + " to " + receiver.getUser().getEmail() + " after review");
        return TransferResponse.from(t, false);
    }

    @Transactional(readOnly = true)
    public List<TransferResponse> history(Long userId, int limit) {
        Long walletId = walletIdOf(userId);
        return transfers.findHistory(walletId, PageRequest.of(0, Math.max(1, Math.min(limit, 500)))).stream()
                .map(t -> TransferResponse.from(t, false)).toList();
    }

    @Transactional(readOnly = true)
    public List<TopUpResponse> topUps(Long userId) {
        return topUps.findTop100ByWalletIdOrderByCreatedAtDesc(walletIdOf(userId)).stream()
                .map(TopUpResponse::from).toList();
    }

    /** Numbers for the customer overview: this month's totals, a 14-day chart and frequent contacts. */
    @Transactional(readOnly = true)
    public SummaryResponse summary(Long userId) {
        Wallet wallet = walletOf(userId);
        Long walletId = wallet.getId();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant monthStart = today.withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant chartStart = today.minusDays(13).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant since = monthStart.isBefore(chartStart) ? monthStart : chartStart;

        List<Transfer> recent = transfers.findWalletTransfersSince(walletId, since);
        List<TopUp> recentTopUps = topUps.findByWalletIdAndCreatedAtAfter(walletId, since);

        BigDecimal sentMonth = BigDecimal.ZERO, receivedMonth = BigDecimal.ZERO, addedMonth = BigDecimal.ZERO;
        long blockedMonth = 0;
        Map<LocalDate, BigDecimal[]> days = new TreeMap<>();
        for (int i = 13; i >= 0; i--) {
            days.put(today.minusDays(i), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
        }

        for (Transfer t : recent) {
            boolean outgoing = t.getSender().getId().equals(walletId);
            boolean inMonth = !t.getCreatedAt().isBefore(monthStart);
            if (t.getStatus() == TransferStatus.BLOCKED) {
                if (outgoing && inMonth) blockedMonth++;
                continue;
            }
            if (inMonth) {
                if (outgoing) sentMonth = sentMonth.add(t.getAmount());
                else receivedMonth = receivedMonth.add(t.getAmount());
            }
            BigDecimal[] d = days.get(LocalDate.ofInstant(t.getCreatedAt(), ZoneOffset.UTC));
            if (d != null) d[outgoing ? 0 : 1] = d[outgoing ? 0 : 1].add(t.getAmount());
        }
        for (TopUp t : recentTopUps) {
            if (!t.getCreatedAt().isBefore(monthStart)) addedMonth = addedMonth.add(t.getAmount());
            BigDecimal[] d = days.get(LocalDate.ofInstant(t.getCreatedAt(), ZoneOffset.UTC));
            if (d != null) d[2] = d[2].add(t.getAmount());
        }

        List<DayPoint> points = days.entrySet().stream()
                .map(e -> new DayPoint(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2])).toList();

        // Frequent contacts: people this user paid most often recently (for one-tap "send again")
        Map<String, Contact> contacts = new LinkedHashMap<>();
        for (Transfer t : transfers.findHistory(walletId, PageRequest.of(0, 200))) {
            if (!t.getSender().getId().equals(walletId) || t.getStatus() != TransferStatus.COMPLETED) continue;
            User r = t.getReceiver().getUser();
            Contact c = contacts.get(r.getEmail());
            contacts.put(r.getEmail(), c == null
                    ? new Contact(r.getEmail(), r.getFullName(), 1, t.getAmount())
                    : new Contact(r.getEmail(), r.getFullName(), c.transfers() + 1, c.total().add(t.getAmount())));
        }
        List<Contact> top = contacts.values().stream()
                .sorted(Comparator.comparingLong(Contact::transfers).reversed()).limit(6).toList();

        return new SummaryResponse(wallet.getBalance(), wallet.getHeldBalance(), sentMonth, receivedMonth, addedMonth, blockedMonth, points, top);
    }

    private void saveAlerts(FraudDecision decision, Transfer transfer, User user) {
        decision.hits().forEach(h ->
                alerts.save(new FraudAlert(transfer, user, h.rule(), h.severity(), h.details())));
    }

    private User requireActive(Long userId) {
        User u = users.findById(userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        if (u.getStatus() == UserStatus.FROZEN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Your account is frozen, so money can't be moved. See the Resolution Center for the reason and to appeal.");
        }
        return u;
    }

    private Long walletIdOf(Long userId) {
        return wallets.findIdByUserId(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Wallet not found"));
    }

    private Wallet walletOf(Long userId) {
        return wallets.findByUserId(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Wallet not found"));
    }

    private static WalletResponse toResponse(Wallet w) {
        return new WalletResponse(w.getId(), w.getUser().getEmail(), w.getUser().getFullName(),
                w.getBalance(), w.getHeldBalance(), w.getCurrency());
    }

    public record TransferResult(TransferResponse response) {
        public boolean blocked() { return "BLOCKED".equals(response.status()); }
    }
}
