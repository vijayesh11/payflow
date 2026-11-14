package com.payflow.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * A linked payment card. PCI-DSS 3.x: the full card number (PAN) and CVV are NEVER stored.
 * We keep only a random token (what a real system would get back from a card vault / payment
 * processor), the brand, the last 4 digits and the expiry — enough to show "Visa •••• 4242".
 */
@Entity
@Table(name = "cards")
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true, length = 40)
    private String token;

    @Column(nullable = false, length = 20)
    private String brand;

    @Column(nullable = false, length = 4)
    private String last4;

    @Column(nullable = false)
    private int expMonth;

    @Column(nullable = false)
    private int expYear;

    @Column(nullable = false, length = 100)
    private String holderName;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected Card() {}

    public Card(User user, String token, String brand, String last4, int expMonth, int expYear, String holderName) {
        this.user = user;
        this.token = token;
        this.brand = brand;
        this.last4 = last4;
        this.expMonth = expMonth;
        this.expYear = expYear;
        this.holderName = holderName;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getToken() { return token; }
    public String getBrand() { return brand; }
    public String getLast4() { return last4; }
    public int getExpMonth() { return expMonth; }
    public int getExpYear() { return expYear; }
    public String getHolderName() { return holderName; }
    public Instant getCreatedAt() { return createdAt; }
}
