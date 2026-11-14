package com.payflow.dto;

import com.payflow.fraud.RiskExplainer;
import com.payflow.model.*;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** All request/response shapes in one place. Entities are never returned directly from controllers. */
public final class Dtos {
    private Dtos() {}

    /** PCI-DSS 8.3.6: at least 12 characters, containing both letters and numbers. */
    public static final String PASSWORD_REGEX = "^(?=.*[A-Za-z])(?=.*\\d).{12,72}$";
    public static final String PASSWORD_MESSAGE = "must be 12-72 characters and contain letters and numbers";

    // ---------- auth & profile ----------

    public record RegisterRequest(
            @NotBlank @Email String email,
            @NotBlank @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MESSAGE) String password,
            @NotBlank @Size(max = 100) String fullName) {
        @Override public String toString() { return "RegisterRequest[email=" + email + ", password=***]"; }
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
        @Override public String toString() { return "LoginRequest[email=" + email + ", password=***]"; }
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MESSAGE) String newPassword) {
        @Override public String toString() { return "ChangePasswordRequest[***]"; }
    }

    public record AuthResponse(String token, String email, String fullName, String role, Instant previousLoginAt) {}

    /** Login either finishes (auth) or asks for an OTP (challenge) when 2-step verification is on. */
    public record LoginResponse(boolean otpRequired, AuthResponse auth, OtpChallengeResponse challenge) {}

    /**
     * Returned whenever an OTP is sent. sandboxCode is only filled when app.otp.sandbox-show-code=true
     * (demo mode with no SMS/email provider) — in production it is always null.
     */
    public record OtpChallengeResponse(String challengeId, String purpose, String channel, String destination,
                                       Instant expiresAt, int codeLength, String sandboxCode) {}

    public record VerifyOtpRequest(@NotBlank String challengeId,
                                   @NotBlank @Pattern(regexp = "^\\d{6}$", message = "must be 6 digits") String code) {
        @Override public String toString() { return "VerifyOtpRequest[" + challengeId + ", code=***]"; }
    }

    public record SetPinRequest(@NotBlank String challengeId,
                                @NotBlank @Pattern(regexp = "^\\d{6}$", message = "must be 6 digits") String code,
                                @NotBlank @Pattern(regexp = "^(\\d{4}|\\d{6})$", message = "must be 4 or 6 digits") String pin) {
        @Override public String toString() { return "SetPinRequest[" + challengeId + ", code=***, pin=***]"; }
    }

    public record TwoFactorRequest(boolean enabled, @NotBlank String password) {
        @Override public String toString() { return "TwoFactorRequest[enabled=" + enabled + ", password=***]"; }
    }

    public record ProfileResponse(Long id, String email, String fullName, String role, String status,
                                  Instant memberSince, Instant lastLoginAt, Instant previousLoginAt,
                                  Instant passwordChangedAt, boolean hasPin, boolean pinLocked,
                                  Instant pinSetAt, boolean twoFactorEnabled, String freezeReason, Instant frozenAt) {
        public static ProfileResponse from(User u) {
            return new ProfileResponse(u.getId(), u.getEmail(), u.getFullName(), u.getRole().name(),
                    u.getStatus().name(), u.getCreatedAt(), u.getLastLoginAt(), u.getPreviousLoginAt(),
                    u.getPasswordChangedAt(), u.hasPin(), u.isPinLocked(), u.getPinSetAt(), u.isTwoFactorEnabled(),
                    u.getFreezeReason(), u.getFrozenAt());
        }
    }

    // ---------- cards ----------

    public record CardRequest(
            @NotBlank @Pattern(regexp = "^[0-9 ]{13,23}$", message = "must be 13-19 digits") String cardNumber,
            @Min(1) @Max(12) int expMonth,
            @Min(2000) @Max(2100) int expYear,
            @NotBlank @Pattern(regexp = "^[0-9]{3,4}$", message = "must be 3 or 4 digits") String cvv,
            @NotBlank @Size(max = 100) String holderName) {
        /** Never let a card number or CVV reach a log line, even by accident. */
        @Override public String toString() { return "CardRequest[number=****, cvv=***, holder=" + holderName + "]"; }
    }

    public record CardResponse(Long id, String brand, String last4, String masked, int expMonth, int expYear,
                               String holderName, Instant createdAt) {
        public static CardResponse from(Card c) {
            return new CardResponse(c.getId(), c.getBrand(), c.getLast4(), "•••• •••• •••• " + c.getLast4(),
                    c.getExpMonth(), c.getExpYear(), c.getHolderName(), c.getCreatedAt());
        }
    }

    // ---------- wallet ----------

    /** Step 2 of adding money: the OTP "from the bank" (3-D Secure style). */
    public record ConfirmDepositRequest(@NotBlank String challengeId,
                                        @NotBlank @Pattern(regexp = "^\\d{6}$", message = "must be 6 digits") String code) {
        @Override public String toString() { return "ConfirmDepositRequest[" + challengeId + ", code=***]"; }
    }

    public record DepositRequest(
            @NotNull Long cardId,
            @NotNull @DecimalMin("1.00") @DecimalMax("50000.00") @Digits(integer = 17, fraction = 2)
            BigDecimal amount) {}

    public record TransferRequest(
            @NotBlank @Email String recipientEmail,
            @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
            @Size(max = 140) String note,
            @NotBlank @Pattern(regexp = "^(\\d{4}|\\d{6})$", message = "must be 4 or 6 digits") String pin) {
        @Override public String toString() {
            return "TransferRequest[to=" + recipientEmail + ", amount=" + amount + ", pin=***]";
        }
    }

    public record WalletResponse(Long walletId, String email, String fullName, BigDecimal balance,
                                 BigDecimal heldBalance, String currency) {}

    public record TopUpResponse(Long id, BigDecimal amount, String cardBrand, String cardLast4, Instant createdAt) {
        public static TopUpResponse from(TopUp t) {
            return new TopUpResponse(t.getId(), t.getAmount(), t.getCardBrand(), t.getCardLast4(), t.getCreatedAt());
        }
    }

    /** A risk decision in plain language: what happened, why, and what the user can do. */
    public record Explanation(String rule, String title, String detail, String advice) {}

    public record TransferResponse(Long id, String status, BigDecimal amount, String senderEmail, String senderName,
                                   String recipientEmail, String recipientName, String note, String country,
                                   String fraudReason, List<Explanation> explanations, BigDecimal heldAmount,
                                   String reviewNote, Instant createdAt, boolean replayed) {
        public static TransferResponse from(Transfer t, boolean replayed) {
            User s = t.getSender().getUser();
            User r = t.getReceiver().getUser();
            return new TransferResponse(t.getId(), t.getStatus().name(), t.getAmount(),
                    s.getEmail(), s.getFullName(), r.getEmail(), r.getFullName(), t.getNote(),
                    t.getCountry(), t.getFraudReason(), RiskExplainer.explain(t.getFraudReason()),
                    t.getHeldAmount(), t.getReviewNote(), t.getCreatedAt(), replayed);
        }
    }

    public record DayPoint(LocalDate date, BigDecimal sent, BigDecimal received, BigDecimal added) {}

    public record Contact(String email, String fullName, long transfers, BigDecimal total) {}

    public record SummaryResponse(BigDecimal balance, BigDecimal heldBalance, BigDecimal sentThisMonth, BigDecimal receivedThisMonth,
                                  BigDecimal addedThisMonth, long blockedThisMonth, List<DayPoint> last14Days,
                                  List<Contact> recentContacts) {}

    // ---------- admin ----------

    public record AlertResponse(Long id, Long transferId, String userEmail, String rule, String severity,
                                String details, boolean resolved, Instant createdAt) {
        public static AlertResponse from(FraudAlert a) {
            return new AlertResponse(a.getId(), a.getTransfer() == null ? null : a.getTransfer().getId(),
                    a.getUser().getEmail(), a.getRule(), a.getSeverity().name(), a.getDetails(),
                    a.isResolved(), a.getCreatedAt());
        }
    }

    public record StatsResponse(long totalTransfers, long completed, long blocked, long openAlerts,
                                long totalUsers, long frozenUsers, BigDecimal volume24h,
                                Map<String, Long> alertsByRule, long openAppeals, long overdueAppeals,
                                long openDisputes, BigDecimal heldTotal, BigDecimal inEscrow) {}

    public record TimelinePoint(Instant hour, long completed, long blocked) {}

    public record AdminUserResponse(Long id, String email, String fullName, String role, String status,
                                    boolean locked, Instant lockedUntil, boolean pinLocked, boolean hasPin,
                                    boolean twoFactorEnabled, BigDecimal balance, BigDecimal heldBalance, long cards,
                                    int trustScore, String trustTier, String freezeReason,
                                    Instant createdAt, Instant lastLoginAt) {}

    public record FreezeRequest(@NotBlank @Size(min = 5, max = 300) String reason) {}

    public record AuditResponse(Long id, String actorEmail, String action, String details, String ipAddress,
                                String userAgent, Instant createdAt) {
        public static AuditResponse from(AuditLog a) {
            return new AuditResponse(a.getId(), a.getActorEmail(), a.getAction(), a.getDetails(),
                    a.getIpAddress(), a.getUserAgent(), a.getCreatedAt());
        }
    }

    // ---------- trust, holds, appeals ----------

    public record TrustFactor(String label, int points, int maxPoints, boolean achieved, String hint) {}

    public record TrustScoreResponse(int score, String tier, int holdPercent, String nextTier, int pointsToNext,
                                     List<TrustFactor> factors) {}

    public record HoldResponse(Long id, BigDecimal amount, String reasonCode, String explanation, String howToRelease,
                               String status, Instant releaseAt, Instant releasedAt, String releasedBy,
                               Instant createdAt, Long transferId, String fromName, boolean appealOpen) {}

    public record AppealRequest(@NotNull AppealType type, Long targetId,
                                @NotBlank @Size(min = 10, max = 1000) String message) {}

    public record AppealResponse(Long id, String type, Long targetId, String message, String status,
                                 String decisionNote, String decidedBy, Instant dueAt, Instant decidedAt,
                                 Instant createdAt, boolean overdue, String userEmail, String userName,
                                 String context, Integer userTrustScore) {}

    public record DecideAppealRequest(boolean approve, @NotBlank @Size(min = 5, max = 500) String note) {}

    public record ResolutionResponse(TrustScoreResponse trust, List<HoldResponse> holds,
                                     List<TransferResponse> blocked, List<AppealResponse> appeals,
                                     String accountStatus, String freezeReason, Instant frozenAt) {}

    // ---------- escrow ----------

    public record MilestoneInput(@NotBlank @Size(max = 120) String title,
                                 @NotNull @DecimalMin("1.00") @DecimalMax("1000000.00") @Digits(integer = 17, fraction = 2) BigDecimal amount) {}

    public record CreateContractRequest(@NotBlank @Email String freelancerEmail,
                                        @NotBlank @Size(max = 120) String title,
                                        @Size(max = 1000) String description,
                                        @NotEmpty @Size(max = 10) List<@jakarta.validation.Valid MilestoneInput> milestones) {}

    public record PinRequest(@NotBlank @Pattern(regexp = "^(\\d{4}|\\d{6})$", message = "must be 4 or 6 digits") String pin) {
        @Override public String toString() { return "PinRequest[pin=***]"; }
    }

    public record NoteRequest(@NotBlank @Size(min = 3, max = 1000) String note) {}

    public record ResolveDisputeRequest(@NotBlank @Pattern(regexp = "^(RELEASE|REFUND)$") String outcome,
                                        @NotBlank @Size(min = 5, max = 500) String note) {}

    public record EventResponse(String actor, String type, String note, Instant createdAt) {
        public static EventResponse from(EscrowEvent e) {
            return new EventResponse(e.getActor(), e.getType(), e.getNote(), e.getCreatedAt());
        }
    }

    public record MilestoneResponse(Long id, int position, String title, BigDecimal amount, String status,
                                    String deliveryNote, Instant fundedAt, Instant submittedAt,
                                    Instant autoReleaseAt, Instant closedAt, List<EventResponse> events) {
        public static MilestoneResponse from(Milestone m) {
            return new MilestoneResponse(m.getId(), m.getPosition(), m.getTitle(), m.getAmount(), m.getStatus().name(),
                    m.getDeliveryNote(), m.getFundedAt(), m.getSubmittedAt(), m.getAutoReleaseAt(), m.getClosedAt(),
                    m.getEvents().stream().map(EventResponse::from).toList());
        }
    }

    public record ContractResponse(Long id, String title, String description, String clientEmail, String clientName,
                                   String freelancerEmail, String freelancerName, String role,
                                   List<MilestoneResponse> milestones, BigDecimal total, BigDecimal released,
                                   BigDecimal inEscrow, Instant createdAt) {}

    public record DisputeResponse(Long contractId, String contractTitle, String clientEmail, String clientName,
                                  String freelancerEmail, String freelancerName, MilestoneResponse milestone) {}

    // ---------- FX invoices ----------

    public record CreateInvoiceRequest(@NotBlank @Size(max = 120) String clientName,
                                       @Email @Size(max = 255) String clientEmail,
                                       @NotBlank @Size(max = 500) String description,
                                       @NotBlank @Pattern(regexp = "^(USD|EUR|GBP|AED|SGD)$", message = "unsupported currency") String currency,
                                       @NotNull @DecimalMin("1.00") @DecimalMax("100000.00") @Digits(integer = 17, fraction = 2) BigDecimal amount) {}

    /** Everything the payer and payee see before money moves: no fee hidden inside the rate. */
    public record FxQuote(String currency, BigDecimal amount, BigDecimal midMarketRate, BigDecimal grossInr,
                          BigDecimal feePercent, BigDecimal feeInr, BigDecimal netInr,
                          BigDecimal typicalMarkupPercent, BigDecimal typicalFeePercent, BigDecimal typicalNetInr,
                          BigDecimal savingsInr, Instant lockedUntil, String rateSource) {}

    public record InvoiceResponse(Long id, String token, String clientName, String clientEmail, String description,
                                  String currency, BigDecimal amount, String status, Instant createdAt, Instant paidAt,
                                  String payerName, String payerCardLast4, BigDecimal rate, BigDecimal grossInr,
                                  BigDecimal feeInr, BigDecimal netInr, BigDecimal typicalNetInr) {
        public static InvoiceResponse from(Invoice i) {
            return new InvoiceResponse(i.getId(), i.getToken(), i.getClientName(), i.getClientEmail(), i.getDescription(),
                    i.getCurrency(), i.getAmount(), i.getStatus().name(), i.getCreatedAt(), i.getPaidAt(),
                    i.getPayerName(), i.getPayerCardLast4(), i.getRate(), i.getGrossInr(), i.getFeeInr(),
                    i.getNetInr(), i.getTypicalNetInr());
        }
    }

    /** What the public pay page shows: no freelancer email or internal ids. */
    public record PublicInvoiceResponse(String token, String freelancerName, String clientName, String description,
                                        String currency, BigDecimal amount, String status, FxQuote quote,
                                        Instant paidAt, BigDecimal rate, BigDecimal netInr) {}

    public record PayInvoiceRequest(@NotBlank @Size(max = 120) String payerName,
                                    @NotBlank @Pattern(regexp = "^[0-9 ]{13,23}$", message = "must be 13-19 digits") String cardNumber,
                                    @Min(1) @Max(12) int expMonth,
                                    @Min(2000) @Max(2100) int expYear,
                                    @NotBlank @Pattern(regexp = "^[0-9]{3,4}$", message = "must be 3 or 4 digits") String cvv) {
        @Override public String toString() { return "PayInvoiceRequest[payer=" + payerName + ", card=****, cvv=***]"; }
    }

    // ---------- public config ----------

    public record DemoAccount(String label, String email, String password, String pin, String role) {}

    public record PublicConfig(boolean demoMode, boolean otpSandbox, String currency, List<DemoAccount> demoAccounts) {}
}
