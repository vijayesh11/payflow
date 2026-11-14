package com.payflow.controller;

import com.payflow.dto.Dtos.*;
import com.payflow.repository.AuditLogRepository;
import com.payflow.security.AuthUser;
import com.payflow.service.AuthService;
import com.payflow.service.PinService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** The signed-in user's own profile and security settings. */
@RestController
@RequestMapping("/api/me")
public class ProfileController {

    private final AuthService auth;
    private final AuditLogRepository auditLogs;
    private final PinService pins;

    public ProfileController(AuthService auth, AuditLogRepository auditLogs, PinService pins) {
        this.auth = auth;
        this.auditLogs = auditLogs;
        this.pins = pins;
    }

    /** Sends an OTP needed to set or reset the UPI PIN. */
    @PostMapping("/pin/otp")
    public OtpChallengeResponse pinOtp(@AuthenticationPrincipal AuthUser user) {
        return pins.requestOtp(user.id());
    }

    @PostMapping("/pin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setPin(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody SetPinRequest req) {
        pins.setPin(user.id(), req);
    }

    @PostMapping("/two-factor")
    public ProfileResponse twoFactor(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody TwoFactorRequest req) {
        return auth.setTwoFactor(user.id(), req);
    }

    @GetMapping
    public ProfileResponse me(@AuthenticationPrincipal AuthUser user) {
        return auth.profile(user.id());
    }

    /** Recent security events on this account (logins, failed attempts, password and card changes). */
    @GetMapping("/activity")
    public List<AuditResponse> activity(@AuthenticationPrincipal AuthUser user) {
        return auditLogs.findTop30ByActorEmailOrderByCreatedAtDesc(user.email()).stream()
                .map(AuditResponse::from).toList();
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody ChangePasswordRequest req) {
        auth.changePassword(user.id(), req);
    }

    /** Sliding session: the app calls this while the user is active to get a fresh 15-minute token. */
    @PostMapping("/refresh-token")
    public AuthResponse refresh(@AuthenticationPrincipal AuthUser user) {
        return auth.refresh(user.id());
    }
}
