package com.payflow.controller;

import com.payflow.dto.Dtos.*;
import com.payflow.security.AuthUser;
import com.payflow.service.AdminService;
import com.payflow.service.AppealService;
import com.payflow.service.EscrowService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Everything under /api/admin requires ROLE_ADMIN (enforced in SecurityConfig). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService admin;
    private final AppealService appeals;
    private final EscrowService escrow;

    public AdminController(AdminService admin, AppealService appeals, EscrowService escrow) {
        this.admin = admin;
        this.appeals = appeals;
        this.escrow = escrow;
    }

    // ---- appeals & escrow disputes ----

    @GetMapping("/appeals")
    public List<AppealResponse> appeals(@RequestParam(defaultValue = "true") boolean open) {
        return appeals.forAdmin(open);
    }

    @PostMapping("/appeals/{id}/decide")
    public AppealResponse decide(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                 @Valid @RequestBody DecideAppealRequest req) {
        return appeals.decide(user.email(), id, req);
    }

    @GetMapping("/disputes")
    public List<DisputeResponse> disputes() {
        return escrow.openDisputes();
    }

    @PostMapping("/disputes/{milestoneId}/resolve")
    public ContractResponse resolveDispute(@AuthenticationPrincipal AuthUser user, @PathVariable Long milestoneId,
                                           @Valid @RequestBody ResolveDisputeRequest req) {
        return escrow.resolveDispute(user.email(), milestoneId, req);
    }

    @GetMapping("/stats")
    public StatsResponse stats() {
        return admin.stats();
    }

    @GetMapping("/timeline")
    public List<TimelinePoint> timeline() {
        return admin.timeline();
    }

    @GetMapping("/alerts")
    public List<AlertResponse> alerts(@RequestParam(defaultValue = "false") boolean open) {
        return admin.alerts(open);
    }

    @PostMapping("/alerts/{id}/resolve")
    public AlertResponse resolve(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return admin.resolve(user.email(), id);
    }

    @GetMapping("/transfers")
    public List<TransferResponse> transfers(@RequestParam(defaultValue = "100") int limit) {
        return admin.recentTransfers(limit);
    }

    @GetMapping("/users")
    public List<AdminUserResponse> users() {
        return admin.users();
    }

    @PostMapping("/users/{id}/freeze")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void freeze(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, @Valid @RequestBody FreezeRequest req) {
        admin.setFrozen(user.email(), id, true, req.reason());
    }

    @PostMapping("/users/{id}/unfreeze")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfreeze(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        admin.setFrozen(user.email(), id, false, null);
    }

    @PostMapping("/users/{id}/unlock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlock(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        admin.unlock(user.email(), id);
    }

    @GetMapping("/audit")
    public List<AuditResponse> audit() {
        return admin.auditLog();
    }
}
