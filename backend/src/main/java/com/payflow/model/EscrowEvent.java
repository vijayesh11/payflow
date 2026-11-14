package com.payflow.model;

import jakarta.persistence.*;
import java.time.Instant;

/** One entry in a milestone's timeline: visible to both parties, so disputes are decided on shared evidence. */
@Entity
@Table(name = "escrow_events")
public class EscrowEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "milestone_id", nullable = false)
    private Milestone milestone;

    @Column(nullable = false, length = 255)
    private String actor;

    @Column(nullable = false, length = 30)
    private String type;     // CREATED, FUNDED, SUBMITTED, APPROVED, AUTO_RELEASED, DISPUTED, EVIDENCE, RESOLVED, REFUNDED

    @Column(length = 1000)
    private String note;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected EscrowEvent() {}

    public EscrowEvent(Milestone milestone, String actor, String type, String note) {
        this.milestone = milestone;
        this.actor = actor;
        this.type = type;
        this.note = note;
    }

    public Long getId() { return id; }
    public String getActor() { return actor; }
    public String getType() { return type; }
    public String getNote() { return note; }
    public Instant getCreatedAt() { return createdAt; }
}
