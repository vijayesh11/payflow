package com.payflow.repository;

import com.payflow.model.Hold;
import com.payflow.model.HoldStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface HoldRepository extends JpaRepository<Hold, Long> {
    List<Hold> findByStatusAndReleaseAtBefore(HoldStatus status, Instant before);
    List<Hold> findByStatus(HoldStatus status);
    List<Hold> findTop50ByWalletIdOrderByCreatedAtDesc(Long walletId);
    Optional<Hold> findByTransferId(Long transferId);
}
