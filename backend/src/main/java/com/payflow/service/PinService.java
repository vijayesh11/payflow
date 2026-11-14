package com.payflow.service;

import com.payflow.dto.Dtos.OtpChallengeResponse;
import com.payflow.dto.Dtos.SetPinRequest;
import com.payflow.exception.ApiException;
import com.payflow.model.OtpPurpose;
import com.payflow.model.User;
import com.payflow.otp.OtpService;
import com.payflow.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/**
 * UPI-style transaction PIN: required to authorise every payment.
 *  - set / reset only after an OTP (proves the person controls the account's email)
 *  - stored as a BCrypt hash, never in plain text, never logged
 *  - 3 wrong attempts lock it (24 h by default, like UPI); OTP reset or an admin unlock clears it
 */
@Service
public class PinService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("d MMM, HH:mm 'UTC'").withZone(ZoneOffset.UTC);
    private static final Set<String> WEAK = Set.of("1234", "4321", "1111", "0000", "1212", "123456", "654321",
            "111111", "000000", "121212", "112233", "123123");

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final OtpService otp;
    private final AuditService audit;
    private final int maxAttempts;
    private final int lockMinutes;

    public PinService(UserRepository users, PasswordEncoder encoder, OtpService otp, AuditService audit,
                      @Value("${app.security.pin-max-attempts}") int maxAttempts,
                      @Value("${app.security.pin-lock-minutes}") int lockMinutes) {
        this.users = users;
        this.encoder = encoder;
        this.otp = otp;
        this.audit = audit;
        this.maxAttempts = maxAttempts;
        this.lockMinutes = lockMinutes;
    }

    @Transactional
    public OtpChallengeResponse requestOtp(Long userId) {
        User user = load(userId);
        return otp.issue(user, OtpPurpose.SET_PIN, null, null,
                "You asked to " + (user.hasPin() ? "reset" : "set") + " your PayFlow UPI PIN.");
    }

    @Transactional
    public void setPin(Long userId, SetPinRequest req) {
        // validate the new PIN BEFORE using up the OTP
        if (WEAK.contains(req.pin()) || req.pin().chars().distinct().count() == 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "That PIN is too easy to guess. Avoid repeated or sequential digits.");
        }
        otp.verify(req.challengeId(), userId, OtpPurpose.SET_PIN, req.code());
        User user = load(userId);
        boolean reset = user.hasPin();
        user.setPin(encoder.encode(req.pin()));
        audit.log(user.getEmail(), reset ? "PIN_RESET" : "PIN_SET", null);
    }

    /**
     * Called before every transfer. Own transaction so a failed attempt is saved even though we throw.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = ApiException.class)
    public void verify(Long userId, String pin) {
        User user = load(userId);
        if (!user.hasPin()) {
            throw new ApiException(HttpStatus.PRECONDITION_REQUIRED, "Set your UPI PIN in Security before sending money.");
        }
        if (user.isPinLocked()) {
            throw new ApiException(HttpStatus.LOCKED, "UPI PIN locked after too many wrong attempts. Reset it from Security, or try again after "
                    + TIME.format(user.getPinLockedUntil()) + ".");
        }
        if (pin == null || !encoder.matches(pin, user.getPinHash())) {
            boolean locked = user.recordFailedPin(maxAttempts, lockMinutes);
            audit.log(user.getEmail(), locked ? "PIN_LOCKED" : "PIN_FAILED", null);
            if (locked) {
                throw new ApiException(HttpStatus.LOCKED, "Incorrect UPI PIN. Your PIN is now locked — reset it from Security.");
            }
            int left = maxAttempts - user.getPinFailedAttempts();
            throw new ApiException(HttpStatus.FORBIDDEN, "Incorrect UPI PIN. " + left + (left == 1 ? " attempt" : " attempts") + " left.");
        }
        user.recordCorrectPin();
    }

    private User load(Long userId) {
        return users.findById(userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
