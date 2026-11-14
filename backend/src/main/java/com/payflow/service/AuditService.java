package com.payflow.service;

import com.payflow.model.AuditLog;
import com.payflow.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Writes the security audit trail. Callers must never pass secrets (passwords, card numbers, tokens). */
@Service
public class AuditService {

    private final AuditLogRepository repo;

    public AuditService(AuditLogRepository repo) {
        this.repo = repo;
    }

    public void log(String actorEmail, String action, String details) {
        String ip = null;
        String agent = null;
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest req = attrs.getRequest();
            String forwarded = req.getHeader("X-Forwarded-For");      // set by nginx / cloud load balancers
            ip = forwarded != null ? forwarded.split(",")[0].trim() : req.getRemoteAddr();
            agent = req.getHeader("User-Agent");
            if (agent != null && agent.length() > 255) agent = agent.substring(0, 255);
        }
        repo.save(new AuditLog(actorEmail == null ? "anonymous" : actorEmail, action, details, ip, agent));
    }
}
