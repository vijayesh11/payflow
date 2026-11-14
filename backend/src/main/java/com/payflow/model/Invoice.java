package com.payflow.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * An invoice a freelancer sends to an overseas client in a foreign currency.
 * The client pays from a public link; the exchange rate is quoted and locked up front and the full
 * breakdown (mid-market rate, our fee, what a typical provider would have deducted) is stored.
 */
@Entity
@Table(name = "invoices")
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String token;               // unguessable public link id

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "freelancer_id", nullable = false)
    private User freelancer;

    @Column(nullable = false, length = 120)
    private String clientName;

    @Column(length = 255)
    private String clientEmail;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private InvoiceStatus status = InvoiceStatus.OPEN;

    // locked quote shown to the payer
    @Column(precision = 19, scale = 6)
    private BigDecimal quotedRate;
    private Instant quoteExpiresAt;

    // settlement breakdown (set when paid)
    @Column(precision = 19, scale = 6)
    private BigDecimal rate;
    @Column(precision = 19, scale = 2)
    private BigDecimal grossInr;
    @Column(precision = 19, scale = 2)
    private BigDecimal feeInr;
    @Column(precision = 19, scale = 2)
    private BigDecimal netInr;
    @Column(precision = 19, scale = 2)
    private BigDecimal typicalNetInr;

    @Column(length = 120)
    private String payerName;
    @Column(length = 4)
    private String payerCardLast4;
    private Instant paidAt;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Version
    private Long version;

    protected Invoice() {}

    public Invoice(String token, User freelancer, String clientName, String clientEmail, String description,
                   String currency, BigDecimal amount) {
        this.token = token;
        this.freelancer = freelancer;
        this.clientName = clientName;
        this.clientEmail = clientEmail;
        this.description = description;
        this.currency = currency;
        this.amount = amount;
    }

    public void lockQuote(BigDecimal rate, Instant expiresAt) {
        this.quotedRate = rate;
        this.quoteExpiresAt = expiresAt;
    }

    public void markPaid(BigDecimal rate, BigDecimal gross, BigDecimal fee, BigDecimal net, BigDecimal typicalNet,
                         String payerName, String cardLast4) {
        this.status = InvoiceStatus.PAID;
        this.rate = rate;
        this.grossInr = gross;
        this.feeInr = fee;
        this.netInr = net;
        this.typicalNetInr = typicalNet;
        this.payerName = payerName;
        this.payerCardLast4 = cardLast4;
        this.paidAt = Instant.now();
    }

    public void cancel() { this.status = InvoiceStatus.CANCELLED; }

    public Long getId() { return id; }
    public String getToken() { return token; }
    public User getFreelancer() { return freelancer; }
    public String getClientName() { return clientName; }
    public String getClientEmail() { return clientEmail; }
    public String getDescription() { return description; }
    public String getCurrency() { return currency; }
    public BigDecimal getAmount() { return amount; }
    public InvoiceStatus getStatus() { return status; }
    public BigDecimal getQuotedRate() { return quotedRate; }
    public Instant getQuoteExpiresAt() { return quoteExpiresAt; }
    public BigDecimal getRate() { return rate; }
    public BigDecimal getGrossInr() { return grossInr; }
    public BigDecimal getFeeInr() { return feeInr; }
    public BigDecimal getNetInr() { return netInr; }
    public BigDecimal getTypicalNetInr() { return typicalNetInr; }
    public String getPayerName() { return payerName; }
    public String getPayerCardLast4() { return payerCardLast4; }
    public Instant getPaidAt() { return paidAt; }
    public Instant getCreatedAt() { return createdAt; }
}
