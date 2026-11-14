package com.payflow.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Append-only security audit trail (PCI-DSS 10.2): who did what, when, from where.
 * Never contains passwords, card numbers or tokens.
 */
@Entity
@Table(name = "audit_logs", indexes = @Index(name = "idx_audit_actor", columnList = "actor_email, created_at"))
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_email", nullable = false, length = 255)
    private String actorEmail;

    @Column(nullable = false, length = 40)
    private String action;

    @Column(length = 500)
    private String details;

    @Column(length = 64)
    private String ipAddress;

    @Column(length = 255)
    private String userAgent;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected AuditLog() {}

    public AuditLog(String actorEmail, String action, String details, String ipAddress, String userAgent) {
        this.actorEmail = actorEmail;
        this.action = action;
        this.details = details;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
    }

    public Long getId() { return id; }
    public String getActorEmail() { return actorEmail; }
    public String getAction() { return action; }
    public String getDetails() { return details; }
    public String getIpAddress() { return ipAddress; }
    public String getUserAgent() { return userAgent; }
    public Instant getCreatedAt() { return createdAt; }
}
