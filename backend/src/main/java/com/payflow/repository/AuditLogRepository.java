package com.payflow.repository;

import com.payflow.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findTop300ByOrderByCreatedAtDesc();
    List<AuditLog> findTop30ByActorEmailOrderByCreatedAtDesc(String actorEmail);
}
