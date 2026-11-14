package com.payflow.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-IP fixed-window rate limit on the endpoints attackers hammer: sign-in, sign-up, OTP verification
 * and public invoice payment. In-memory, which is right for a single instance; with several instances
 * you'd move the counters to Redis.
 * Not a @Component on purpose (same reason as JwtAuthFilter): it's added only to the security chain.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private record Window(long minute, int count) {}

    private final int limitPerMinute;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(int limitPerMinute) {
        this.limitPerMinute = limitPerMinute;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        String p = req.getRequestURI();
        return !"POST".equals(req.getMethod())
                || !(p.startsWith("/api/auth/") || (p.startsWith("/api/public/invoices/") && p.endsWith("/pay")));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String forwarded = req.getHeader("X-Forwarded-For");
        String ip = forwarded != null ? forwarded.split(",")[0].trim() : req.getRemoteAddr();
        long minute = System.currentTimeMillis() / 60_000;
        Window w = windows.compute(ip, (k, old) -> old == null || old.minute() != minute
                ? new Window(minute, 1) : new Window(minute, old.count() + 1));
        if (windows.size() > 10_000) windows.entrySet().removeIf(e -> e.getValue().minute() < minute);
        if (w.count() > limitPerMinute) {
            res.setStatus(429);
            res.setHeader("Retry-After", "60");
            res.setContentType("application/json");
            res.getWriter().write("{\"status\":429,\"error\":\"Too many attempts. Please wait a minute and try again.\"}");
            return;
        }
        chain.doFilter(req, res);
    }
}
