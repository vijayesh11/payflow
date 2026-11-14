package com.payflow.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** A project between a client (pays) and a freelancer (delivers), split into milestones. */
@Entity
@Table(name = "escrow_contracts")
public class EscrowContract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(length = 1000)
    private String description;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private User client;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "freelancer_id", nullable = false)
    private User freelancer;

    @OneToMany(mappedBy = "contract", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<Milestone> milestones = new ArrayList<>();

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected EscrowContract() {}

    public EscrowContract(String title, String description, User client, User freelancer) {
        this.title = title;
        this.description = description;
        this.client = client;
        this.freelancer = freelancer;
    }

    public void addMilestone(Milestone m) {
        milestones.add(m);
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public User getClient() { return client; }
    public User getFreelancer() { return freelancer; }
    public List<Milestone> getMilestones() { return milestones; }
    public Instant getCreatedAt() { return createdAt; }
}
