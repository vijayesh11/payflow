package com.payflow.repository;

import com.payflow.model.TopUp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface TopUpRepository extends JpaRepository<TopUp, Long> {
    List<TopUp> findTop100ByWalletIdOrderByCreatedAtDesc(Long walletId);
    List<TopUp> findByWalletIdAndCreatedAtAfter(Long walletId, Instant after);
}
