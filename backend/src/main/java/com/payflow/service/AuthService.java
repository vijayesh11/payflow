package com.payflow.service;

import com.payflow.dto.Dtos.*;
import com.payflow.exception.ApiException;
import com.payflow.model.OtpPurpose;
import com.payflow.model.Role;
import com.payflow.model.User;
import com.payflow.model.UserStatus;
import com.payflow.model.Wallet;
import com.payflow.repository.UserRepository;
import com.payflow.repository.WalletRepository;
import com.payflow.otp.OtpService;
import com.payflow.otp.OtpService.VerifiedOtp;
import com.payflow.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Service
public class AuthService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private final UserRepository users;
    private final WalletRepository wallets;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AuditService audit;
    private final OtpService otp;
    private final int maxFailedLogins;
    private final int lockMinutes;

    public AuthService(UserRepository users, WalletRepository wallets, PasswordEncoder encoder, JwtService jwt,
                       AuditService audit, OtpService otp,
                       @Value("${app.security.max-failed-logins}") int maxFailedLogins,
                       @Value("${app.security.lock-minutes}") int lockMinutes) {
        this.users = users;
        this.wallets = wallets;
        this.encoder = encoder;
        this.jwt = jwt;
        this.audit = audit;
        this.otp = otp;
        this.maxFailedLogins = maxFailedLogins;
        this.lockMinutes = lockMinutes;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase();
        if (users.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email already registered");
        }
        // Self-registration always gets USER; ADMIN is only seeded server-side
        User user = users.save(new User(email, encoder.encode(req.password()), req.fullName().trim(), Role.USER));
        wallets.save(new Wallet(user));   // user + wallet created atomically in one transaction
        user.recordSuccessfulLogin();
        audit.log(email, "REGISTER", "account created");
        return toResponse(user);
    }

    /**
     * noRollbackFor: a failed login throws, but the failed-attempt counter and the audit entry
     * must still be saved — otherwise lockout could never trigger.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public LoginResponse login(LoginRequest req) {
        String email = req.email().trim().toLowerCase();
        User user = users.findByEmail(email).orElse(null);
        if (user == null) {
            audit.log(email, "LOGIN_FAILED", "unknown account");
            throw invalidCredentials();
        }
        if (user.isLocked()) {
            audit.log(email, "LOGIN_BLOCKED", "account locked");
            throw new ApiException(HttpStatus.LOCKED, "Too many failed attempts. Account locked until "
                    + TIME.format(user.getLockedUntil()) + ".");
        }
        if (!encoder.matches(req.password(), user.getPasswordHash())) {
            boolean nowLocked = user.recordFailedLogin(maxFailedLogins, lockMinutes);
            audit.log(email, "LOGIN_FAILED", "wrong password");
            if (nowLocked) {
                audit.log(email, "ACCOUNT_LOCKED", "locked for " + lockMinutes + " min after "
                        + maxFailedLogins + " failed attempts");
                throw new ApiException(HttpStatus.LOCKED, "Too many failed attempts. Account locked for "
                        + lockMinutes + " minutes.");
            }
            throw invalidCredentials();
        }
        // Frozen accounts can still sign in (read-only) to see the reason and appeal; money movement is blocked elsewhere.
        if (user.isTwoFactorEnabled()) {
            // password was right, but the session is only issued after the OTP step
            OtpChallengeResponse challenge = otp.issue(user, OtpPurpose.LOGIN, null, null,
                    "Someone (hopefully you) is signing in to PayFlow.");
            return new LoginResponse(true, null, challenge);
        }
        user.recordSuccessfulLogin();
        audit.log(email, "LOGIN_SUCCESS", null);
        return new LoginResponse(false, toResponse(user), null);
    }

    /** Step 2 of sign-in when 2-step verification is on. */
    @Transactional
    public AuthResponse verifyLogin(VerifyOtpRequest req) {
        VerifiedOtp v = otp.verify(req.challengeId(), null, OtpPurpose.LOGIN, req.code());
        User user = users.findById(v.userId()).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Unknown user"));
        if (user.isLocked()) {
            throw new ApiException(HttpStatus.LOCKED, "This account is locked. Please try again later.");
        }
        user.recordSuccessfulLogin();
        audit.log(user.getEmail(), "LOGIN_SUCCESS", "2-step verified");
        return toResponse(user);
    }

    @Transactional
    public ProfileResponse setTwoFactor(Long userId, TwoFactorRequest req) {
        User user = users.findById(userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        if (!encoder.matches(req.password(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Password is incorrect");
        }
        user.setTwoFactorEnabled(req.enabled());
        audit.log(user.getEmail(), req.enabled() ? "TWO_FACTOR_ON" : "TWO_FACTOR_OFF", null);
        return ProfileResponse.from(user);
    }

    /** Issues a fresh short-lived token while the user is active (sliding session). */
    @Transactional(readOnly = true)
    public AuthResponse refresh(Long userId) {
        User user = users.findById(userId).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Unknown user"));
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public ProfileResponse profile(Long userId) {
        return ProfileResponse.from(users.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found")));
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest req) {
        User user = users.findById(userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        if (!encoder.matches(req.currentPassword(), user.getPasswordHash())) {
            audit.log(user.getEmail(), "PASSWORD_CHANGE_FAILED", "wrong current password");
            throw new ApiException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        if (encoder.matches(req.newPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "New password must be different from the current one");
        }
        user.changePassword(encoder.encode(req.newPassword()));
        audit.log(user.getEmail(), "PASSWORD_CHANGED", null);
    }

    private ApiException invalidCredentials() {
        // same message for unknown email and wrong password, so attackers can't enumerate accounts
        return new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }

    private AuthResponse toResponse(User u) {
        return new AuthResponse(jwt.generateToken(u), u.getEmail(), u.getFullName(), u.getRole().name(),
                u.getPreviousLoginAt());
    }
}
