package com.payflow.controller;

import com.payflow.dto.Dtos.*;
import com.payflow.security.AuthUser;
import com.payflow.service.EscrowService;
import com.payflow.service.PinService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/escrow")
public class EscrowController {

    private final EscrowService escrow;
    private final PinService pins;

    public EscrowController(EscrowService escrow, PinService pins) {
        this.escrow = escrow;
        this.pins = pins;
    }

    @GetMapping
    public List<ContractResponse> mine(@AuthenticationPrincipal AuthUser user) {
        return escrow.mine(user.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContractResponse create(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody CreateContractRequest req) {
        return escrow.create(user.id(), req);
    }

    /** Funding moves money, so it needs the UPI PIN — exactly like a payment. */
    @PostMapping("/milestones/{id}/fund")
    public ContractResponse fund(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, @Valid @RequestBody PinRequest req) {
        pins.verify(user.id(), req.pin());
        return escrow.fund(user.id(), id);
    }

    @PostMapping("/milestones/{id}/submit")
    public ContractResponse submit(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, @Valid @RequestBody NoteRequest req) {
        return escrow.submit(user.id(), id, req.note());
    }

    @PostMapping("/milestones/{id}/approve")
    public ContractResponse approve(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return escrow.approve(user.id(), id);
    }

    @PostMapping("/milestones/{id}/dispute")
    public ContractResponse dispute(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, @Valid @RequestBody NoteRequest req) {
        return escrow.dispute(user.id(), id, req.note());
    }

    @PostMapping("/milestones/{id}/evidence")
    public ContractResponse evidence(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, @Valid @RequestBody NoteRequest req) {
        return escrow.addEvidence(user.id(), id, req.note());
    }

    @PostMapping("/milestones/{id}/refund")
    public ContractResponse refund(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, @Valid @RequestBody NoteRequest req) {
        return escrow.refundByFreelancer(user.id(), id, req.note());
    }
}
