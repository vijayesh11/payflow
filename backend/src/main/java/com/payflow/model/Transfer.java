package com.payflow.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
    name = "transfers",
    // The database itself guarantees one transfer per (sender, idempotency key),
    // even if two identical requests race past the application-level check.
    uniqueConstraints = @UniqueConstraint(
        name = "uk_transfer_sender_idempotency", columnNames = {"sender_wallet_id", "idempotency_key"}),
    indexes = @Index(name = "idx_transfer_sender_created", columnList = "sender_wallet_id, created_at")
)
public class Transfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_wallet_id", nullable = false)
    private Wallet sender;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_wallet_id", nullable = false)
    private Wallet receiver;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransferStatus status;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(length = 2)
    private String country;      // simulated geolocation (ISO country code)

    @Column(length = 500)
    private String fraudReason;

    @Column(length = 140)
    private String note;

    @Column(precision = 19, scale = 2)
    private BigDecimal heldAmount;   // part of a completed payment held for progressive release

    @Column(length = 300)
    private String reviewNote;       // set when a blocked payment is released after human review         // optional message from the sender, e.g. "Dinner split"

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Transfer() {}

    public Transfer(Wallet sender, Wallet receiver, BigDecimal amount, TransferStatus status,
                    String idempotencyKey, String country, String fraudReason, String note) {
        this.sender = sender;
        this.receiver = receiver;
        this.amount = amount;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.country = country;
        this.fraudReason = fraudReason;
        this.note = note;
    }

    public Long getId() { return id; }
    public Wallet getSender() { return sender; }
    public Wallet getReceiver() { return receiver; }
    public BigDecimal getAmount() { return amount; }
    public TransferStatus getStatus() { return status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getCountry() { return country; }
    public String getFraudReason() { return fraudReason; }
    public String getNote() { return note; }
    public BigDecimal getHeldAmount() { return heldAmount; }
    public String getReviewNote() { return reviewNote; }

    public void setHeldAmount(BigDecimal heldAmount) { this.heldAmount = heldAmount; }

    /** A blocked payment approved on appeal: it now completes, with a note explaining why. */
    public void completeAfterReview(String note) {
        this.status = TransferStatus.COMPLETED;
        this.reviewNote = note;
    }
    public Instant getCreatedAt() { return createdAt; }
}
