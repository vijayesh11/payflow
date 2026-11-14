package com.payflow.repository;

import com.payflow.model.Appeal;
import com.payflow.model.AppealStatus;
import com.payflow.model.AppealType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppealRepository extends JpaRepository<Appeal, Long> {
    List<Appeal> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<Appeal> findByStatusOrderByDueAtAsc(AppealStatus status);
    List<Appeal> findTop200ByOrderByCreatedAtDesc();
    boolean existsByUserIdAndTypeAndTargetIdAndStatus(Long userId, AppealType type, Long targetId, AppealStatus status);
    boolean existsByUserIdAndTypeAndStatus(Long userId, AppealType type, AppealStatus status);
    long countByStatus(AppealStatus status);
}
