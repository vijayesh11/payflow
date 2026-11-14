package com.payflow.controller;

import com.payflow.dto.Dtos.*;
import com.payflow.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
        return auth.register(req);
    }

    /** Returns a session directly, or an OTP challenge when 2-step verification is on. */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        return auth.login(req);
    }

    @PostMapping("/login/verify")
    public AuthResponse verify(@Valid @RequestBody VerifyOtpRequest req) {
        return auth.verifyLogin(req);
    }
}
