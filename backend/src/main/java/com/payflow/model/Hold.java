package com.payflow.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Part of an incoming payment that is held and released progressively.
 * Unlike an opaque "up to 21 days" hold, every hold carries a plain-language reason,
 * an exact release time and what the user can do to release it sooner.
 */
@Entity
@Table(name = "holds", indexes = @Index(name = "idx_hold_status_release", columnList = "status, release_at"))
public class Hold {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_id")
    private Transfer transfer;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 30)
    private String reasonCode;          // NEW_ACCOUNT | RISK_REVIEW

    @Column(nullable = false, length = 500)
    private String explanation;

    @Column(nullable = false, length = 400)
    private String howToRelease;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private HoldStatus status = HoldStatus.HELD;

    @Column(name = "release_at", nullable = false)
    private Instant releaseAt;

    private Instant releasedAt;

    @Column(length = 120)
    private String releasedBy;          // "schedule", "trust", "appeal by admin@..."

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Version
    private Long version;               // guards against double release (scheduler vs appeal)

    protected Hold() {}

    public Hold(Wallet wallet, Transfer transfer, BigDecimal amount, String reasonCode, String explanation,
                String howToRelease, Instant releaseAt) {
        this.wallet = wallet;
        this.transfer = transfer;
        this.amount = amount;
        this.reasonCode = reasonCode;
        this.explanation = explanation;
        this.howToRelease = howToRelease;
        this.releaseAt = releaseAt;
    }

    public void markReleased(String by) {
        this.status = HoldStatus.RELEASED;
        this.releasedAt = Instant.now();
        this.releasedBy = by;
    }

    public Long getId() { return id; }
    public Wallet getWallet() { return wallet; }
    public Transfer getTransfer() { return transfer; }
    public BigDecimal getAmount() { return amount; }
    public String getReasonCode() { return reasonCode; }
    public String getExplanation() { return explanation; }
    public String getHowToRelease() { return howToRelease; }
    public HoldStatus getStatus() { return status; }
    public Instant getReleaseAt() { return releaseAt; }
    public Instant getReleasedAt() { return releasedAt; }
    public String getReleasedBy() { return releasedBy; }
    public Instant getCreatedAt() { return createdAt; }
}
