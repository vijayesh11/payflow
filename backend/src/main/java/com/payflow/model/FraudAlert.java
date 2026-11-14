package com.payflow.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "fraud_alerts")
public class FraudAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_id")
    private Transfer transfer;          // null for alerts raised by the scheduled batch scan

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "rule_name", nullable = false, length = 50)
    private String rule;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

    @Column(nullable = false, length = 500)
    private String details;

    @Column(nullable = false)
    private boolean resolved = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected FraudAlert() {}

    public FraudAlert(Transfer transfer, User user, String rule, Severity severity, String details) {
        this.transfer = transfer;
        this.user = user;
        this.rule = rule;
        this.severity = severity;
        this.details = details;
    }

    public void resolve() { this.resolved = true; }

    public Long getId() { return id; }
    public Transfer getTransfer() { return transfer; }
    public User getUser() { return user; }
    public String getRule() { return rule; }
    public Severity getSeverity() { return severity; }
    public String getDetails() { return details; }
    public boolean isResolved() { return resolved; }
    public Instant getCreatedAt() { return createdAt; }
}
