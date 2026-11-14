package com.payflow.repository;

import com.payflow.model.Card;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CardRepository extends JpaRepository<Card, Long> {
    List<Card> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<Card> findByIdAndUserId(Long id, Long userId);
    long countByUserId(Long userId);
}
