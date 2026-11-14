package com.payflow.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "milestones", indexes = @Index(name = "idx_milestone_status_auto", columnList = "status, auto_release_at"))
public class Milestone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false)
    private EscrowContract contract;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MilestoneStatus status = MilestoneStatus.PENDING_FUNDING;

    @Column(length = 1000)
    private String deliveryNote;

    private Instant fundedAt;
    private Instant submittedAt;

    @Column(name = "auto_release_at")
    private Instant autoReleaseAt;

    private Instant closedAt;

    @OneToMany(mappedBy = "milestone", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<EscrowEvent> events = new ArrayList<>();

    @Version
    private Long version;   // approve / auto-release / dispute can't race each other

    protected Milestone() {}

    public Milestone(EscrowContract contract, int position, String title, BigDecimal amount) {
        this.contract = contract;
        this.position = position;
        this.title = title;
        this.amount = amount;
    }

    public void addEvent(String actor, String type, String note) {
        events.add(new EscrowEvent(this, actor, type, note));
    }

    public void markFunded() { status = MilestoneStatus.FUNDED; fundedAt = Instant.now(); }

    public void markSubmitted(String note, Instant autoRelease) {
        status = MilestoneStatus.SUBMITTED;
        deliveryNote = note;
        submittedAt = Instant.now();
        autoReleaseAt = autoRelease;
    }

    public void markDisputed() { status = MilestoneStatus.DISPUTED; autoReleaseAt = null; }
    public void markReleased() { status = MilestoneStatus.RELEASED; closedAt = Instant.now(); autoReleaseAt = null; }
    public void markRefunded() { status = MilestoneStatus.REFUNDED; closedAt = Instant.now(); autoReleaseAt = null; }

    public Long getId() { return id; }
    public EscrowContract getContract() { return contract; }
    public int getPosition() { return position; }
    public String getTitle() { return title; }
    public BigDecimal getAmount() { return amount; }
    public MilestoneStatus getStatus() { return status; }
    public String getDeliveryNote() { return deliveryNote; }
    public Instant getFundedAt() { return fundedAt; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getAutoReleaseAt() { return autoReleaseAt; }
    public Instant getClosedAt() { return closedAt; }
    public List<EscrowEvent> getEvents() { return events; }
}
