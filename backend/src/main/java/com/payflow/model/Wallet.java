package com.payflow.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "wallets")
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    // BigDecimal, never double: floating point cannot represent money exactly
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    // Money received but not yet available (progressive release). Not spendable until released.
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal heldBalance = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    private String currency = "INR";

    // Optimistic-lock version: a second safety net on top of the row lock used during transfers
    @Version
    private Long version;

    protected Wallet() {}

    public Wallet(User user) {
        this.user = user;
    }

    public void debit(BigDecimal amount) {
        if (balance.compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient funds");
        }
        balance = balance.subtract(amount);
    }

    public void credit(BigDecimal amount) {
        balance = balance.add(amount);
    }

    public void hold(BigDecimal amount) {
        heldBalance = heldBalance.add(amount);
    }

    /** Moves held money into the spendable balance. */
    public void releaseHeld(BigDecimal amount) {
        if (heldBalance.compareTo(amount) < 0) {
            throw new IllegalStateException("Held balance too low");
        }
        heldBalance = heldBalance.subtract(amount);
        balance = balance.add(amount);
    }

    public BigDecimal getHeldBalance() { return heldBalance; }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public BigDecimal getBalance() { return balance; }
    public String getCurrency() { return currency; }
}
