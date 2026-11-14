package com.payflow.security;

import com.payflow.model.Role;

/** The logged-in caller, rebuilt from the JWT on every request (no server-side session). */
public record AuthUser(Long id, String email, Role role) {}
