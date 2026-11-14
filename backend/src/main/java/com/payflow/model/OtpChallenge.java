package com.payflow.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * One OTP sent to a user. Only a salted SHA-256 hash of the code is stored, it expires after a few
 * minutes, allows a limited number of guesses, and can be used once.
 */
@Entity
@Table(name = "otp_challenges", indexes = @Index(name = "idx_otp_user_created", columnList = "user_id, created_at"))
public class OtpChallenge {

    @Id
    @Column(length = 36)
    private String id;                 // random UUID: unguessable handle the client sends back with the code

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OtpPurpose purpose;

    @Column(nullable = false, length = 64)
    private String codeHash;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(nullable = false)
    private boolean consumed = false;

    // context for CARD_TOPUP: what exactly the user is approving
    private Long cardId;

    @Column(precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected OtpChallenge() {}

    public OtpChallenge(String id, User user, OtpPurpose purpose, String codeHash, Instant expiresAt,
                        Long cardId, BigDecimal amount) {
        this.id = id;
        this.user = user;
        this.purpose = purpose;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
        this.cardId = cardId;
        this.amount = amount;
    }

    public void recordAttempt() { attempts++; }
    public void consume() { consumed = true; }

    public String getId() { return id; }
    public User getUser() { return user; }
    public OtpPurpose getPurpose() { return purpose; }
    public String getCodeHash() { return codeHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public int getAttempts() { return attempts; }
    public boolean isConsumed() { return consumed; }
    public Long getCardId() { return cardId; }
    public BigDecimal getAmount() { return amount; }
    public Instant getCreatedAt() { return createdAt; }
}
