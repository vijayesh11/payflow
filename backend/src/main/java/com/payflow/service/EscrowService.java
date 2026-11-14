package com.payflow.service;

import com.payflow.dto.Dtos.*;
import com.payflow.exception.ApiException;
import com.payflow.model.*;
import com.payflow.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Milestone escrow for freelance work:
 *  - the client funds a milestone (UPI PIN): money leaves their wallet and is held by PayFlow, not the freelancer
 *  - the freelancer submits the delivery: a review window starts
 *  - the client approves -> paid out; silence until the window ends -> paid out automatically
 *  - the client disputes -> both sides add evidence to a shared timeline -> an admin releases or refunds, with a reason
 * Every money movement uses the same wallet row locks as transfers, so it stays consistent under concurrency.
 */
@Service
public class EscrowService {

    private final EscrowContractRepository contracts;
    private final MilestoneRepository milestones;
    private final UserRepository users;
    private final WalletRepository wallets;
    private final AuditService audit;
    private final int reviewMinutes;

    public EscrowService(EscrowContractRepository contracts, MilestoneRepository milestones, UserRepository users,
                         WalletRepository wallets, AuditService audit,
                         @Value("${app.escrow.review-minutes}") int reviewMinutes) {
        this.contracts = contracts;
        this.milestones = milestones;
        this.users = users;
        this.wallets = wallets;
        this.audit = audit;
        this.reviewMinutes = reviewMinutes;
    }

    @Transactional
    public ContractResponse create(Long clientId, CreateContractRequest req) {
        User client = active(clientId);
        User freelancer = users.findByEmail(req.freelancerEmail().trim().toLowerCase())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No PayFlow account with that email"));
        if (freelancer.getId().equals(clientId)) throw new ApiException(HttpStatus.BAD_REQUEST, "You can't hire yourself");
        EscrowContract c = new EscrowContract(req.title().trim(), req.description() == null ? null : req.description().trim(),
                client, freelancer);
        int pos = 1;
        for (MilestoneInput in : req.milestones()) {
            Milestone m = new Milestone(c, pos++, in.title().trim(), in.amount());
            m.addEvent(client.getEmail(), "CREATED", null);
            c.addMilestone(m);
        }
        c = contracts.save(c);
        audit.log(client.getEmail(), "ESCROW_CREATED", "\"" + c.getTitle() + "\" with " + freelancer.getEmail());
        return toResponse(c, clientId);
    }

    @Transactional(readOnly = true)
    public List<ContractResponse> mine(Long userId) {
        return contracts.findForUser(userId).stream().map(c -> toResponse(c, userId)).toList();
    }

    /** Client pays the milestone into escrow. The UPI PIN is checked by the controller first. */
    @Transactional
    public ContractResponse fund(Long userId, Long milestoneId) {
        User client = active(userId);
        Milestone m = lock(milestoneId);
        requireRole(m, userId, true);
        expect(m, MilestoneStatus.PENDING_FUNDING, "This milestone is already funded");
        Wallet w = wallets.findByIdForUpdate(walletId(userId)).orElseThrow();
        if (w.getBalance().compareTo(m.getAmount()) < 0) throw new ApiException(HttpStatus.CONFLICT, "Insufficient funds to fund this milestone");
        w.debit(m.getAmount());
        m.markFunded();
        m.addEvent(client.getEmail(), "FUNDED", "₹" + m.getAmount() + " moved into escrow");
        audit.log(client.getEmail(), "ESCROW_FUNDED", m.getAmount() + " for \"" + m.getTitle() + "\"");
        return toResponse(m.getContract(), userId);
    }

    @Transactional
    public ContractResponse submit(Long userId, Long milestoneId, String note) {
        User freelancer = active(userId);
        Milestone m = lock(milestoneId);
        requireRole(m, userId, false);
        expect(m, MilestoneStatus.FUNDED, "Only funded milestones can be submitted");
        m.markSubmitted(note.trim(), Instant.now().plus(reviewMinutes, ChronoUnit.MINUTES));
        m.addEvent(freelancer.getEmail(), "SUBMITTED", note.trim());
        return toResponse(m.getContract(), userId);
    }

    @Transactional
    public ContractResponse approve(Long userId, Long milestoneId) {
        Milestone m = lock(milestoneId);
        requireRole(m, userId, true);
        expect(m, MilestoneStatus.SUBMITTED, "Only submitted work can be approved");
        payFreelancer(m, m.getContract().getClient().getEmail(), "APPROVED", "Client approved the delivery");
        return toResponse(m.getContract(), userId);
    }

    @Transactional
    public ContractResponse dispute(Long userId, Long milestoneId, String reason) {
        Milestone m = lock(milestoneId);
        requireRole(m, userId, true);
        expect(m, MilestoneStatus.SUBMITTED, "You can only dispute work during its review window");
        m.markDisputed();
        m.addEvent(m.getContract().getClient().getEmail(), "DISPUTED", reason.trim());
        audit.log(m.getContract().getClient().getEmail(), "ESCROW_DISPUTED", "\"" + m.getTitle() + "\"");
        return toResponse(m.getContract(), userId);
    }

    /** Either party adds evidence while a dispute is open; both see the same timeline. */
    @Transactional
    public ContractResponse addEvidence(Long userId, Long milestoneId, String note) {
        Milestone m = lock(milestoneId);
        EscrowContract c = m.getContract();
        boolean party = c.getClient().getId().equals(userId) || c.getFreelancer().getId().equals(userId);
        if (!party) throw new ApiException(HttpStatus.NOT_FOUND, "Milestone not found");
        expect(m, MilestoneStatus.DISPUTED, "Evidence can only be added to an open dispute");
        m.addEvent(users.findById(userId).orElseThrow().getEmail(), "EVIDENCE", note.trim());
        return toResponse(c, userId);
    }

    /** Freelancer can't deliver: give the client their money back. */
    @Transactional
    public ContractResponse refundByFreelancer(Long userId, Long milestoneId, String note) {
        Milestone m = lock(milestoneId);
        requireRole(m, userId, false);
        expect(m, MilestoneStatus.FUNDED, "Only funded, undelivered milestones can be refunded");
        refundClient(m, m.getContract().getFreelancer().getEmail(), "REFUNDED", note == null ? "Freelancer cancelled" : note.trim());
        return toResponse(m.getContract(), userId);
    }

    @Transactional(readOnly = true)
    public List<DisputeResponse> openDisputes() {
        return milestones.findByStatusOrderBySubmittedAtAsc(MilestoneStatus.DISPUTED).stream().map(m -> {
            EscrowContract c = m.getContract();
            return new DisputeResponse(c.getId(), c.getTitle(), c.getClient().getEmail(), c.getClient().getFullName(),
                    c.getFreelancer().getEmail(), c.getFreelancer().getFullName(), MilestoneResponse.from(m));
        }).toList();
    }

    @Transactional
    public ContractResponse resolveDispute(String adminEmail, Long milestoneId, ResolveDisputeRequest req) {
        Milestone m = lock(milestoneId);
        expect(m, MilestoneStatus.DISPUTED, "This milestone is not in dispute");
        String note = "Decision: " + req.note().trim();
        if ("RELEASE".equals(req.outcome())) payFreelancer(m, adminEmail, "RESOLVED_RELEASE", note);
        else refundClient(m, adminEmail, "RESOLVED_REFUND", note);
        audit.log(adminEmail, "DISPUTE_RESOLVED", req.outcome().toLowerCase() + " — \"" + m.getTitle() + "\"");
        return toResponse(m.getContract(), null);
    }

    /** Called by the scheduler: the review window ended without approval or dispute. */
    @Transactional
    public boolean autoRelease(Long milestoneId) {
        Milestone m = lock(milestoneId);
        if (m.getStatus() != MilestoneStatus.SUBMITTED || m.getAutoReleaseAt() == null
                || m.getAutoReleaseAt().isAfter(Instant.now())) return false;
        payFreelancer(m, "PayFlow", "AUTO_RELEASED", "Review window ended without a dispute");
        return true;
    }

    @Transactional(readOnly = true)
    public List<Long> dueForAutoRelease() {
        return milestones.findByStatusAndAutoReleaseAtBefore(MilestoneStatus.SUBMITTED, Instant.now())
                .stream().map(Milestone::getId).toList();
    }

    // ---------- helpers ----------

    private void payFreelancer(Milestone m, String actor, String event, String note) {
        User f = m.getContract().getFreelancer();
        Wallet w = wallets.findByIdForUpdate(walletId(f.getId())).orElseThrow();
        w.credit(m.getAmount());
        m.markReleased();
        m.addEvent(actor, event, note + " — ₹" + m.getAmount() + " paid to " + f.getFullName());
        audit.log(f.getEmail(), "ESCROW_RELEASED", m.getAmount() + " for \"" + m.getTitle() + "\"");
    }

    private void refundClient(Milestone m, String actor, String event, String note) {
        User c = m.getContract().getClient();
        Wallet w = wallets.findByIdForUpdate(walletId(c.getId())).orElseThrow();
        w.credit(m.getAmount());
        m.markRefunded();
        m.addEvent(actor, event, note + " — ₹" + m.getAmount() + " returned to " + c.getFullName());
        audit.log(c.getEmail(), "ESCROW_REFUNDED", m.getAmount() + " for \"" + m.getTitle() + "\"");
    }

    private Milestone lock(Long id) {
        return milestones.findByIdForUpdate(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Milestone not found"));
    }

    private void requireRole(Milestone m, Long userId, boolean client) {
        Long expected = client ? m.getContract().getClient().getId() : m.getContract().getFreelancer().getId();
        if (!expected.equals(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, client ? "Only the client can do this" : "Only the freelancer can do this");
        }
    }

    private static void expect(Milestone m, MilestoneStatus s, String message) {
        if (m.getStatus() != s) throw new ApiException(HttpStatus.CONFLICT, message);
    }

    private User active(Long userId) {
        User u = users.findById(userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        if (u.getStatus() == UserStatus.FROZEN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Your account is frozen, so money can't be moved. See the Resolution Center.");
        }
        return u;
    }

    private Long walletId(Long userId) {
        return wallets.findIdByUserId(userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Wallet not found"));
    }

    private ContractResponse toResponse(EscrowContract c, Long viewerId) {
        BigDecimal total = BigDecimal.ZERO, released = BigDecimal.ZERO, inEscrow = BigDecimal.ZERO;
        for (Milestone m : c.getMilestones()) {
            total = total.add(m.getAmount());
            switch (m.getStatus()) {
                case RELEASED -> released = released.add(m.getAmount());
                case FUNDED, SUBMITTED, DISPUTED -> inEscrow = inEscrow.add(m.getAmount());
                default -> { }
            }
        }
        String role = viewerId == null ? "ADMIN" : c.getClient().getId().equals(viewerId) ? "CLIENT" : "FREELANCER";
        return new ContractResponse(c.getId(), c.getTitle(), c.getDescription(), c.getClient().getEmail(),
                c.getClient().getFullName(), c.getFreelancer().getEmail(), c.getFreelancer().getFullName(), role,
                c.getMilestones().stream().map(MilestoneResponse::from).toList(), total, released, inEscrow, c.getCreatedAt());
    }
}
