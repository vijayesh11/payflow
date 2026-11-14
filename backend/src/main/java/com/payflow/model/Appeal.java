package com.payflow.model;

import jakarta.persistence.*;
import java.time.Instant;

/** A user's request for a human to review an automated decision, with a response deadline (SLA). */
@Entity
@Table(name = "appeals")
public class Appeal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AppealType type;

    private Long targetId;               // hold id or transfer id; null for ACCOUNT_FREEZE

    @Column(nullable = false, length = 1000)
    private String message;              // the user's side of the story / evidence

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AppealStatus status = AppealStatus.OPEN;

    @Column(length = 500)
    private String decisionNote;         // always sent back to the user

    @Column(length = 255)
    private String decidedBy;

    @Column(nullable = false)
    private Instant dueAt;

    private Instant decidedAt;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected Appeal() {}

    public Appeal(User user, AppealType type, Long targetId, String message, Instant dueAt) {
        this.user = user;
        this.type = type;
        this.targetId = targetId;
        this.message = message;
        this.dueAt = dueAt;
    }

    public void decide(boolean approved, String note, String by) {
        this.status = approved ? AppealStatus.APPROVED : AppealStatus.REJECTED;
        this.decisionNote = note;
        this.decidedBy = by;
        this.decidedAt = Instant.now();
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public AppealType getType() { return type; }
    public Long getTargetId() { return targetId; }
    public String getMessage() { return message; }
    public AppealStatus getStatus() { return status; }
    public String getDecisionNote() { return decisionNote; }
    public String getDecidedBy() { return decidedBy; }
    public Instant getDueAt() { return dueAt; }
    public Instant getDecidedAt() { return decidedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
