package com.payflow.controller;

import com.payflow.dto.Dtos.*;
import com.payflow.security.AuthUser;
import com.payflow.service.AppealService;
import com.payflow.service.TrustService;
import com.payflow.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Customer-facing "Resolution Center": trust score, holds, blocked payments, appeals. */
@RestController
@RequestMapping("/api")
public class ResolutionController {

    private final AppealService appeals;
    private final TrustService trust;
    private final UserRepository users;

    public ResolutionController(AppealService appeals, TrustService trust, UserRepository users) {
        this.appeals = appeals;
        this.trust = trust;
        this.users = users;
    }

    @GetMapping("/resolution")
    public ResolutionResponse resolution(@AuthenticationPrincipal AuthUser user) {
        return appeals.resolution(user.id());
    }

    @GetMapping("/me/trust")
    public TrustScoreResponse trust(@AuthenticationPrincipal AuthUser user) {
        return trust.score(users.findById(user.id()).orElseThrow());
    }

    @GetMapping("/appeals")
    public List<AppealResponse> myAppeals(@AuthenticationPrincipal AuthUser user) {
        return appeals.mine(user.id());
    }

    @PostMapping("/appeals")
    @ResponseStatus(HttpStatus.CREATED)
    public AppealResponse appeal(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody AppealRequest req) {
        return appeals.create(user.id(), req);
    }
}
