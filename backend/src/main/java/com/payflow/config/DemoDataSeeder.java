package com.payflow.config;

import com.payflow.dto.Dtos.FxQuote;
import com.payflow.model.*;
import com.payflow.repository.*;
import com.payflow.service.FxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * DEMO_MODE=true: creates three ready-to-use accounts with realistic history so anyone opening the
 * deployed link can explore every feature without setting anything up:
 *   Asha   — freelancer, "Trusted", escrow work in progress, international invoices
 *   Vikram — client, "Building", funds Asha's milestones
 *   Neha   — brand-new user: money on hold and a blocked payment she can appeal
 * Runs once (skips if the accounts exist). Never enable on a real production system.
 */
@Component
@Order(2)
@ConditionalOnProperty(name = "app.demo.seed", havingValue = "true")
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final UserRepository users;
    private final WalletRepository wallets;
    private final CardRepository cards;
    private final TopUpRepository topUps;
    private final TransferRepository transfers;
    private final FraudAlertRepository alerts;
    private final HoldRepository holds;
    private final EscrowContractRepository contracts;
    private final InvoiceRepository invoices;
    private final FxService fx;
    private final PasswordEncoder encoder;
    private final String password;

    public DemoDataSeeder(UserRepository users, WalletRepository wallets, CardRepository cards, TopUpRepository topUps,
                          TransferRepository transfers, FraudAlertRepository alerts, HoldRepository holds,
                          EscrowContractRepository contracts, InvoiceRepository invoices, FxService fx,
                          PasswordEncoder encoder, @Value("${app.demo.password}") String password) {
        this.users = users;
        this.wallets = wallets;
        this.cards = cards;
        this.topUps = topUps;
        this.transfers = transfers;
        this.alerts = alerts;
        this.holds = holds;
        this.contracts = contracts;
        this.invoices = invoices;
        this.fx = fx;
        this.encoder = encoder;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (users.existsByEmail("asha@demo.payflow.dev")) return;

        User asha = user("asha@demo.payflow.dev", "Asha Rao", 45, true);
        User vikram = user("vikram@demo.payflow.dev", "Vikram Shah", 20, true);
        User neha = user("neha@demo.payflow.dev", "Neha Gupta", 0, false);
        Wallet wa = wallet(asha), wv = wallet(vikram), wn = wallet(neha);

        Card ca = card(asha, "Visa", "4242"), cv = card(vikram, "Mastercard", "4444"), cn = card(neha, "Visa", "1881");
        topUp(wa, ca, "25000"); topUp(wv, cv, "120000"); topUp(wn, cn, "1000");

        // everyday payments
        pay(wv, wa, "1200", "Logo tweaks");
        pay(wv, wa, "850", "Stock photos");
        pay(wv, wa, "2000", "Hosting for March");
        pay(wv, wa, "1500", "Copywriting");
        pay(wa, wv, "640", "Lunch split");

        // Neha (new account) receives ₹3,000 -> 60% held, with an explanation
        Transfer toNeha = pay(wv, wn, "3000", "Freelance logo — advance");
        BigDecimal held = new BigDecimal("1800.00");
        wn.debit(held);          // undo the full credit from pay(), then hold the 60%
        wn.hold(held);
        toNeha.setHeldAmount(held);
        holds.save(new Hold(wn, toNeha, held, "NEW_ACCOUNT",
                "60% of this payment is held because your account is still building trust (Trust Score 27/100, New). "
                        + "The other 40% is available right now.",
                "Releases automatically in 3 days, or instantly once your Trust Score reaches 70. "
                        + "Quick wins: Set your UPI PIN in Security; Grows automatically over your first 30 days.",
                Instant.now().plus(3, ChronoUnit.DAYS)));

        // Neha's blocked attempt (location jump) -> she can appeal it from the Resolution Center
        Transfer blocked = transfers.save(new Transfer(wn, wa, new BigDecimal("500.00"), TransferStatus.BLOCKED,
                "demo-" + UUID.randomUUID(), "AE", "GEO_ANOMALY: country changed IN -> AE within 30 min", "Book order"));
        alerts.save(new FraudAlert(blocked, neha, "GEO_ANOMALY", Severity.HIGH, "country changed IN -> AE within 30 min"));

        // Escrow: Vikram hires Asha
        EscrowContract c = new EscrowContract("Website redesign — Shah Traders",
                "Responsive redesign of the company website: wireframes, visual design, build and launch.", vikram, asha);
        Milestone m1 = new Milestone(c, 1, "Wireframes", new BigDecimal("15000.00"));
        Milestone m2 = new Milestone(c, 2, "Visual design", new BigDecimal("20000.00"));
        Milestone m3 = new Milestone(c, 3, "Development & launch", new BigDecimal("35000.00"));
        for (Milestone m : new Milestone[]{m1, m2, m3}) { m.addEvent(vikram.getEmail(), "CREATED", null); c.addMilestone(m); }
        wv.debit(m1.getAmount()); m1.markFunded(); m1.addEvent(vikram.getEmail(), "FUNDED", "₹15000.00 moved into escrow");
        m1.markSubmitted("Wireframes for all 8 pages: figma.com/file/demo-wireframes", Instant.now());
        m1.addEvent(asha.getEmail(), "SUBMITTED", "Wireframes for all 8 pages: figma.com/file/demo-wireframes");
        wa.credit(m1.getAmount()); m1.markReleased();
        m1.addEvent(vikram.getEmail(), "APPROVED", "Client approved the delivery — ₹15000.00 paid to Asha Rao");
        wv.debit(m2.getAmount()); m2.markFunded(); m2.addEvent(vikram.getEmail(), "FUNDED", "₹20000.00 moved into escrow");
        m2.markSubmitted("Final visual designs, desktop + mobile: figma.com/file/demo-visuals", Instant.now().plus(7, ChronoUnit.DAYS));
        m2.addEvent(asha.getEmail(), "SUBMITTED", "Final visual designs, desktop + mobile: figma.com/file/demo-visuals");
        contracts.save(c);

        // International invoices for Asha: one paid (with full FX breakdown), one open with a public pay link
        Invoice paid = new Invoice("inv_demo_paid_" + UUID.randomUUID().toString().substring(0, 8), asha, "Nordic Apps AB",
                "billing@nordicapps.example", "App onboarding illustrations (6)", "EUR", new BigDecimal("300.00"));
        FxQuote q = fx.quote("EUR", paid.getAmount(), fx.midRate("EUR"), null);
        paid.markPaid(q.midMarketRate(), q.grossInr(), q.feeInr(), q.netInr(), q.typicalNetInr(), "Nordic Apps AB", "0005");
        wa.credit(q.netInr());
        invoices.save(paid);
        invoices.save(new Invoice("inv_demo_acme", asha, "Acme Studio (USA)", "ap@acmestudio.example",
                "Landing page illustrations — 3 hero images", "USD", new BigDecimal("450.00")));

        log.info("Demo data created: asha / vikram / neha @demo.payflow.dev (password from DEMO_PASSWORD)");
    }

    private User user(String email, String name, int ageDays, boolean withPin) {
        User u = new User(email, encoder.encode(password), name, Role.USER);
        u.backdateCreatedAt(Instant.now().minus(ageDays, ChronoUnit.DAYS));
        if (withPin) u.setPin(encoder.encode("592047"));
        return users.save(u);
    }

    private Wallet wallet(User u) {
        return wallets.save(new Wallet(u));
    }

    private Card card(User u, String brand, String last4) {
        return cards.save(new Card(u, "tok_demo_" + UUID.randomUUID().toString().substring(0, 8), brand, last4, 12, 2030, u.getFullName()));
    }

    private void topUp(Wallet w, Card c, String amount) {
        BigDecimal a = new BigDecimal(amount);
        w.credit(a);
        topUps.save(new TopUp(w, a, c.getBrand(), c.getLast4()));
    }

    private Transfer pay(Wallet from, Wallet to, String amount, String note) {
        BigDecimal a = new BigDecimal(amount);
        from.debit(a);
        to.credit(a);
        return transfers.save(new Transfer(from, to, a, TransferStatus.COMPLETED, "demo-" + UUID.randomUUID(), "IN", null, note));
    }
}
