package com.payflow.controller;

import com.payflow.dto.Dtos.*;
import com.payflow.security.AuthUser;
import com.payflow.service.InvoiceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api")
@Validated
public class InvoiceController {

    private final InvoiceService invoices;

    public InvoiceController(InvoiceService invoices) {
        this.invoices = invoices;
    }

    @GetMapping("/invoices")
    public List<InvoiceResponse> mine(@AuthenticationPrincipal AuthUser user) {
        return invoices.mine(user.id());
    }

    @PostMapping("/invoices")
    @ResponseStatus(HttpStatus.CREATED)
    public InvoiceResponse create(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody CreateInvoiceRequest req) {
        return invoices.create(user.id(), req);
    }

    @PostMapping("/invoices/{id}/cancel")
    public InvoiceResponse cancel(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return invoices.cancel(user.id(), id);
    }

    /** Live preview while creating an invoice (no rate lock). */
    @GetMapping("/fx/quote")
    public FxQuote quote(@RequestParam String currency,
                         @RequestParam @DecimalMin("1.00") @DecimalMax("100000.00") BigDecimal amount) {
        return invoices.preview(currency, amount);
    }
}
