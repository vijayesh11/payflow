package com.payflow.service;

import com.payflow.dto.Dtos.*;
import com.payflow.exception.ApiException;
import com.payflow.model.*;
import com.payflow.repository.InvoiceRepository;
import com.payflow.repository.UserRepository;
import com.payflow.repository.WalletRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.YearMonth;
import java.util.HexFormat;
import java.util.List;

/**
 * International invoices for freelancers: the client pays in USD/EUR/GBP/AED/SGD from a public link,
 * the rate is locked for a few minutes, and the freelancer gets INR with every deduction itemised.
 */
@Service
public class InvoiceService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final InvoiceRepository invoices;
    private final UserRepository users;
    private final WalletRepository wallets;
    private final FxService fx;
    private final AuditService audit;

    public InvoiceService(InvoiceRepository invoices, UserRepository users, WalletRepository wallets, FxService fx,
                          AuditService audit) {
        this.invoices = invoices;
        this.users = users;
        this.wallets = wallets;
        this.fx = fx;
        this.audit = audit;
    }

    @Transactional
    public InvoiceResponse create(Long userId, CreateInvoiceRequest req) {
        User u = users.findById(userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        if (u.getStatus() == UserStatus.FROZEN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Your account is frozen. See the Resolution Center.");
        }
        byte[] b = new byte[12];
        RANDOM.nextBytes(b);
        Invoice inv = invoices.save(new Invoice("inv_" + HexFormat.of().formatHex(b), u, req.clientName().trim(),
                req.clientEmail() == null || req.clientEmail().isBlank() ? null : req.clientEmail().trim(),
                req.description().trim(), req.currency(), req.amount()));
        audit.log(u.getEmail(), "INVOICE_CREATED", req.currency() + " " + req.amount() + " to " + req.clientName().trim());
        return InvoiceResponse.from(inv);
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponse> mine(Long userId) {
        return invoices.findByFreelancerIdOrderByCreatedAtDesc(userId).stream().map(InvoiceResponse::from).toList();
    }

    @Transactional
    public InvoiceResponse cancel(Long userId, Long id) {
        Invoice inv = invoices.findByIdAndFreelancerId(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Invoice not found"));
        if (inv.getStatus() != InvoiceStatus.OPEN) throw new ApiException(HttpStatus.CONFLICT, "Only open invoices can be cancelled");
        inv.cancel();
        return InvoiceResponse.from(inv);
    }

    /** Public view; (re)locks the rate whenever the previous lock has expired. */
    @Transactional
    public PublicInvoiceResponse publicView(String token) {
        Invoice inv = byToken(token);
        FxQuote quote = null;
        if (inv.getStatus() == InvoiceStatus.OPEN) {
            if (inv.getQuoteExpiresAt() == null || inv.getQuoteExpiresAt().isBefore(Instant.now())) {
                inv.lockQuote(fx.midRate(inv.getCurrency()), fx.newLockExpiry());
            }
            quote = fx.quote(inv.getCurrency(), inv.getAmount(), inv.getQuotedRate(), inv.getQuoteExpiresAt());
        }
        return new PublicInvoiceResponse(inv.getToken(), inv.getFreelancer().getFullName(), inv.getClientName(),
                inv.getDescription(), inv.getCurrency(), inv.getAmount(), inv.getStatus().name(), quote,
                inv.getPaidAt(), inv.getRate(), inv.getNetInr());
    }

    /** The overseas client pays by card (simulated): settles at the locked rate, credits the freelancer in INR. */
    @Transactional
    public PublicInvoiceResponse pay(String token, PayInvoiceRequest req) {
        Invoice inv = byToken(token);
        if (inv.getStatus() != InvoiceStatus.OPEN) throw new ApiException(HttpStatus.CONFLICT, "This invoice is no longer open");
        if (inv.getQuoteExpiresAt() == null || inv.getQuoteExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.CONFLICT, "The locked exchange rate expired. Refresh the page to get a new rate.");
        }
        String pan = req.cardNumber().replace(" ", "");
        if (!CardService.luhnValid(pan)) throw new ApiException(HttpStatus.BAD_REQUEST, "Card number is not valid");
        if (YearMonth.of(req.expYear(), req.expMonth()).isBefore(YearMonth.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Card has expired");
        }
        FxQuote q = fx.quote(inv.getCurrency(), inv.getAmount(), inv.getQuotedRate(), inv.getQuoteExpiresAt());
        Wallet w = wallets.findByIdForUpdate(wallets.findIdByUserId(inv.getFreelancer().getId()).orElseThrow()).orElseThrow();
        w.credit(q.netInr());
        inv.markPaid(q.midMarketRate(), q.grossInr(), q.feeInr(), q.netInr(), q.typicalNetInr(), req.payerName().trim(),
                pan.substring(pan.length() - 4));
        audit.log(inv.getFreelancer().getEmail(), "INVOICE_PAID", inv.getCurrency() + " " + inv.getAmount() + " from "
                + req.payerName().trim() + " → ₹" + q.netInr());
        return new PublicInvoiceResponse(inv.getToken(), inv.getFreelancer().getFullName(), inv.getClientName(),
                inv.getDescription(), inv.getCurrency(), inv.getAmount(), inv.getStatus().name(), q, inv.getPaidAt(),
                inv.getRate(), inv.getNetInr());
    }

    public FxQuote preview(String currency, java.math.BigDecimal amount) {
        return fx.quote(currency, amount, fx.midRate(currency), null);
    }

    private Invoice byToken(String token) {
        return invoices.findByToken(token).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Invoice not found"));
    }
}
