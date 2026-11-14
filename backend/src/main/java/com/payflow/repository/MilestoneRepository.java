package com.payflow.repository;

import com.payflow.model.Milestone;
import com.payflow.model.MilestoneStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MilestoneRepository extends JpaRepository<Milestone, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Milestone m where m.id = :id")
    Optional<Milestone> findByIdForUpdate(@Param("id") Long id);

    List<Milestone> findByStatusAndAutoReleaseAtBefore(MilestoneStatus status, Instant before);

    List<Milestone> findByStatusOrderBySubmittedAtAsc(MilestoneStatus status);

    long countByStatus(MilestoneStatus status);

    @Query("select count(m) from Milestone m where m.contract.freelancer.id = :userId and m.status = :status")
    long countForFreelancer(@Param("userId") Long userId, @Param("status") MilestoneStatus status);
}
