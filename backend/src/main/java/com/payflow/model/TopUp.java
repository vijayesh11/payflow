package com.payflow.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/** Money added to a wallet from a linked card. Card brand/last4 are copied so history survives card removal. */
@Entity
@Table(name = "top_ups")
public class TopUp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 20)
    private String cardBrand;

    @Column(nullable = false, length = 4)
    private String cardLast4;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected TopUp() {}

    public TopUp(Wallet wallet, BigDecimal amount, String cardBrand, String cardLast4) {
        this.wallet = wallet;
        this.amount = amount;
        this.cardBrand = cardBrand;
        this.cardLast4 = cardLast4;
    }

    public Long getId() { return id; }
    public Wallet getWallet() { return wallet; }
    public BigDecimal getAmount() { return amount; }
    public String getCardBrand() { return cardBrand; }
    public String getCardLast4() { return cardLast4; }
    public Instant getCreatedAt() { return createdAt; }
}
