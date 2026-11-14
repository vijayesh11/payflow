package com.payflow.controller;

import com.payflow.dto.Dtos.*;
import com.payflow.security.AuthUser;
import com.payflow.service.CardService;
import com.payflow.service.PinService;
import com.payflow.service.WalletService;
import com.payflow.service.WalletService.TransferResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class WalletController {

    private final WalletService walletService;
    private final CardService cardService;
    private final PinService pinService;

    public WalletController(WalletService walletService, CardService cardService, PinService pinService) {
        this.walletService = walletService;
        this.cardService = cardService;
        this.pinService = pinService;
    }

    @GetMapping("/wallet")
    public WalletResponse wallet(@AuthenticationPrincipal AuthUser user) {
        return walletService.getWallet(user.id());
    }

    /** Step 1: returns an OTP challenge; nothing is charged yet. */
    @PostMapping("/wallet/deposit")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public OtpChallengeResponse deposit(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody DepositRequest req) {
        return walletService.startDeposit(user.id(), req);
    }

    /** Step 2: confirm with the OTP; the wallet is credited. */
    @PostMapping("/wallet/deposit/confirm")
    public WalletResponse confirmDeposit(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody ConfirmDepositRequest req) {
        return walletService.confirmDeposit(user.id(), req);
    }

    @GetMapping("/wallet/topups")
    public List<TopUpResponse> topUps(@AuthenticationPrincipal AuthUser user) {
        return walletService.topUps(user.id());
    }

    @GetMapping("/wallet/summary")
    public SummaryResponse summary(@AuthenticationPrincipal AuthUser user) {
        return walletService.summary(user.id());
    }

    /**
     * Clients must send a unique Idempotency-Key per logical payment (e.g. a UUID generated when the user
     * opens the payment form). Retrying with the same key returns the original result instead of paying twice.
     */
    @PostMapping("/transfers")
    public ResponseEntity<TransferResponse> transfer(@AuthenticationPrincipal AuthUser user,
                                                     @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                     @RequestHeader(value = "X-Country", required = false) String country,
                                                     @Valid @RequestBody TransferRequest req) {
        pinService.verify(user.id(), req.pin());   // UPI PIN authorises the payment (own transaction: failures are counted)
        TransferResult result = walletService.transfer(user.id(), req, idempotencyKey, country);
        HttpStatus status = result.blocked() ? HttpStatus.FORBIDDEN
                : result.response().replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(result.response());
    }

    @GetMapping("/transfers")
    public List<TransferResponse> history(@AuthenticationPrincipal AuthUser user,
                                          @RequestParam(defaultValue = "50") int limit) {
        return walletService.history(user.id(), limit);
    }

    // ---- linked cards ----

    @GetMapping("/cards")
    public List<CardResponse> cards(@AuthenticationPrincipal AuthUser user) {
        return cardService.list(user.id());
    }

    @PostMapping("/cards")
    @ResponseStatus(HttpStatus.CREATED)
    public CardResponse linkCard(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody CardRequest req) {
        return cardService.link(user.id(), req);
    }

    @DeleteMapping("/cards/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeCard(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        cardService.remove(user.id(), id);
    }
}
