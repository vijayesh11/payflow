package com.payflow.repository;

import com.payflow.model.OtpChallenge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, String> {
    long countByUserIdAndCreatedAtAfter(Long userId, Instant after);
}
