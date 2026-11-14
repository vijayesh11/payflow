package com.payflow.controller;

import com.payflow.dto.Dtos.*;
import com.payflow.service.InvoiceService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Endpoints that need no login: app config and the invoice pay page for overseas clients. */
@RestController
@RequestMapping("/api/public")
public class PublicController {

    private final InvoiceService invoices;
    private final boolean demoMode;
    private final boolean otpSandbox;
    private final String demoPassword;

    public PublicController(InvoiceService invoices,
                            @Value("${app.demo.seed}") boolean demoMode,
                            @Value("${app.otp.sandbox-show-code}") boolean otpSandbox,
                            @Value("${app.demo.password}") String demoPassword) {
        this.invoices = invoices;
        this.demoMode = demoMode;
        this.otpSandbox = otpSandbox;
        this.demoPassword = demoPassword;
    }

    @GetMapping("/config")
    public PublicConfig config() {
        List<DemoAccount> accounts = !demoMode ? List.of() : List.of(
                new DemoAccount("Freelancer · Trusted", "asha@demo.payflow.dev", demoPassword, "592047", "USER"),
                new DemoAccount("Client · Building trust", "vikram@demo.payflow.dev", demoPassword, "592047", "USER"),
                new DemoAccount("New user · money on hold", "neha@demo.payflow.dev", demoPassword, null, "USER"));
        return new PublicConfig(demoMode, otpSandbox, "INR", accounts);
    }

    @GetMapping("/invoices/{token}")
    public PublicInvoiceResponse invoice(@PathVariable String token) {
        return invoices.publicView(token);
    }

    @PostMapping("/invoices/{token}/pay")
    public PublicInvoiceResponse pay(@PathVariable String token, @Valid @RequestBody PayInvoiceRequest req) {
        return invoices.pay(token, req);
    }
}
