package com.payflow.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;   // BCrypt hash, never the raw password

    @Column(nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.USER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status = UserStatus.ACTIVE;

    // --- account lockout (PCI-DSS 8.3.4: lock after repeated failed logins) ---
    @Column(nullable = false)
    private int failedLoginAttempts = 0;

    private Instant lockedUntil;

    // --- UPI PIN: hashed like a password; 3 wrong attempts lock it ---
    private String pinHash;

    @Column(nullable = false)
    private int pinFailedAttempts = 0;

    private Instant pinLockedUntil;
    private Instant pinSetAt;

    // --- 2-step verification: OTP after password at sign-in ---
    @Column(nullable = false)
    private boolean twoFactorEnabled = true;

    @Column(length = 300)
    private String freezeReason;      // shown to the user, so a freeze is never a black box
    private Instant frozenAt;

    private Instant lastLoginAt;
    private Instant previousLoginAt;     // shown to the user as "last login" so they can spot misuse
    private Instant passwordChangedAt;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected User() {}

    public User(String email, String passwordHash, String fullName, Role role) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
        this.passwordChangedAt = Instant.now();
    }

    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(Instant.now());
    }

    /** @return true if this failure caused the account to lock */
    public boolean recordFailedLogin(int maxAttempts, int lockMinutes) {
        failedLoginAttempts++;
        if (failedLoginAttempts >= maxAttempts) {
            lockedUntil = Instant.now().plus(lockMinutes, ChronoUnit.MINUTES);
            failedLoginAttempts = 0;
            return true;
        }
        return false;
    }

    public void recordSuccessfulLogin() {
        failedLoginAttempts = 0;
        lockedUntil = null;
        previousLoginAt = lastLoginAt;
        lastLoginAt = Instant.now();
    }

    public void changePassword(String newHash) {
        this.passwordHash = newHash;
        this.passwordChangedAt = Instant.now();
    }

    public void unlock() {
        lockedUntil = null;
        failedLoginAttempts = 0;
        pinLockedUntil = null;
        pinFailedAttempts = 0;
    }

    public boolean hasPin() { return pinHash != null; }

    public boolean isPinLocked() {
        return pinLockedUntil != null && pinLockedUntil.isAfter(Instant.now());
    }

    public void setPin(String hash) {
        this.pinHash = hash;
        this.pinSetAt = Instant.now();
        this.pinFailedAttempts = 0;
        this.pinLockedUntil = null;
    }

    /** @return true if this failure locked the PIN */
    public boolean recordFailedPin(int maxAttempts, int lockMinutes) {
        pinFailedAttempts++;
        if (pinFailedAttempts >= maxAttempts) {
            pinLockedUntil = Instant.now().plus(lockMinutes, ChronoUnit.MINUTES);
            pinFailedAttempts = 0;
            return true;
        }
        return false;
    }

    public void recordCorrectPin() { pinFailedAttempts = 0; }

    public void setTwoFactorEnabled(boolean enabled) { this.twoFactorEnabled = enabled; }

    public void setStatus(UserStatus status) { this.status = status; }

    public void freeze(String reason) {
        this.status = UserStatus.FROZEN;
        this.freezeReason = reason;
        this.frozenAt = Instant.now();
    }

    public void unfreeze() {
        this.status = UserStatus.ACTIVE;
        this.freezeReason = null;
        this.frozenAt = null;
    }

    /** Only used by the demo-data seeder to create accounts with some history. */
    public void backdateCreatedAt(Instant when) { this.createdAt = when; }

    public String getFreezeReason() { return freezeReason; }
    public Instant getFrozenAt() { return frozenAt; }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getFullName() { return fullName; }
    public Role getRole() { return role; }
    public UserStatus getStatus() { return status; }
    public int getFailedLoginAttempts() { return failedLoginAttempts; }
    public Instant getLockedUntil() { return lockedUntil; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public String getPinHash() { return pinHash; }
    public int getPinFailedAttempts() { return pinFailedAttempts; }
    public Instant getPinLockedUntil() { return pinLockedUntil; }
    public Instant getPinSetAt() { return pinSetAt; }
    public boolean isTwoFactorEnabled() { return twoFactorEnabled; }
    public Instant getPreviousLoginAt() { return previousLoginAt; }
    public Instant getPasswordChangedAt() { return passwordChangedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
