package com.payflow.otp;

import com.payflow.dto.Dtos.OtpChallengeResponse;
import com.payflow.exception.ApiException;
import com.payflow.model.OtpChallenge;
import com.payflow.model.OtpPurpose;
import com.payflow.model.User;
import com.payflow.repository.OtpChallengeRepository;
import com.payflow.service.AuditService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Issues and checks one-time passwords.
 *  - 6 random digits from SecureRandom, stored only as SHA-256(challengeId + code)
 *  - expire after a few minutes, single use, limited wrong guesses per code
 *  - limited number of codes per user per 15 minutes (stops SMS/email bombing and brute force)
 */
@Service
public class OtpService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OtpChallengeRepository challenges;
    private final OtpSender sender;
    private final AuditService audit;
    private final int expiryMinutes;
    private final int maxAttempts;
    private final int maxPer15Min;
    private final boolean showCode;

    public OtpService(OtpChallengeRepository challenges, OtpSender sender, AuditService audit,
                      @Value("${app.otp.expiry-minutes}") int expiryMinutes,
                      @Value("${app.otp.max-attempts}") int maxAttempts,
                      @Value("${app.otp.max-per-15-min}") int maxPer15Min,
                      @Value("${app.otp.sandbox-show-code}") boolean showCode) {
        this.challenges = challenges;
        this.sender = sender;
        this.audit = audit;
        this.expiryMinutes = expiryMinutes;
        this.maxAttempts = maxAttempts;
        this.maxPer15Min = maxPer15Min;
        this.showCode = showCode;
    }

    /** What a successful verification proves, as plain values (safe to use outside this transaction). */
    public record VerifiedOtp(Long userId, OtpPurpose purpose, Long cardId, BigDecimal amount) {}

    @Transactional
    public OtpChallengeResponse issue(User user, OtpPurpose purpose, Long cardId, BigDecimal amount, String context) {
        Instant windowStart = Instant.now().minus(15, ChronoUnit.MINUTES);
        if (challenges.countByUserIdAndCreatedAtAfter(user.getId(), windowStart) >= maxPer15Min) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many codes requested. Please wait a few minutes and try again.");
        }
        String id = UUID.randomUUID().toString();
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        Instant expiresAt = Instant.now().plus(expiryMinutes, ChronoUnit.MINUTES);
        challenges.save(new OtpChallenge(id, user, purpose, hash(id, code), expiresAt, cardId, amount));
        sender.send(user, purpose, code, context);
        audit.log(user.getEmail(), "OTP_SENT", purpose.name().toLowerCase().replace('_', ' ') + " via " + sender.channel());
        return new OtpChallengeResponse(id, purpose.name(), sender.channel(), maskEmail(user.getEmail()),
                expiresAt, 6, showCode ? code : null);
    }

    /**
     * Runs in its OWN transaction so a wrong guess is counted even though we then throw — otherwise the
     * attempt limit could never kick in. Returns plain values because the entity is detached afterwards.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = ApiException.class)
    public VerifiedOtp verify(String challengeId, Long expectedUserId, OtpPurpose purpose, String code) {
        OtpChallenge c = challenges.findById(challengeId == null ? "" : challengeId)
                .filter(x -> x.getPurpose() == purpose)
                .filter(x -> expectedUserId == null || x.getUser().getId().equals(expectedUserId))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "This code is invalid. Please request a new one."));
        if (c.isConsumed() || c.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This code has expired. Please request a new one.");
        }
        if (c.getAttempts() >= maxAttempts) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Too many wrong attempts. Please request a new code.");
        }
        boolean ok = code != null && MessageDigest.isEqual(
                hash(c.getId(), code).getBytes(StandardCharsets.UTF_8), c.getCodeHash().getBytes(StandardCharsets.UTF_8));
        if (!ok) {
            c.recordAttempt();
            int left = maxAttempts - c.getAttempts();
            audit.log(c.getUser().getEmail(), "OTP_FAILED", purpose.name().toLowerCase().replace('_', ' '));
            throw new ApiException(HttpStatus.BAD_REQUEST, left > 0
                    ? "Incorrect code. " + left + (left == 1 ? " attempt" : " attempts") + " left."
                    : "Too many wrong attempts. Please request a new code.");
        }
        c.consume();
        return new VerifiedOtp(c.getUser().getId(), c.getPurpose(), c.getCardId(), c.getAmount());
    }

    static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return "***" + email.substring(Math.max(at, 0));
        return email.charAt(0) + "*****" + email.substring(at);
    }

    private static String hash(String salt, String code) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest((salt + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
