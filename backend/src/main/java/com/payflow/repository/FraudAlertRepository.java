package com.payflow.repository;

import com.payflow.model.FraudAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface FraudAlertRepository extends JpaRepository<FraudAlert, Long> {

    List<FraudAlert> findTop200ByOrderByCreatedAtDesc();

    List<FraudAlert> findByResolvedFalseOrderByCreatedAtDesc();

    long countByResolvedFalse();

    List<FraudAlert> findByTransferId(Long transferId);

    long countByUserIdAndResolvedFalseAndSeverity(Long userId, com.payflow.model.Severity severity);

    boolean existsByUserIdAndRuleAndCreatedAtAfter(Long userId, String rule, Instant after);

    @Query("select a.rule, count(a) from FraudAlert a group by a.rule")
    List<Object[]> countByRule();
}
