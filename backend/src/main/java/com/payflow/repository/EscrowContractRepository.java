package com.payflow.repository;

import com.payflow.model.EscrowContract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EscrowContractRepository extends JpaRepository<EscrowContract, Long> {
    @Query("select c from EscrowContract c where c.client.id = :userId or c.freelancer.id = :userId order by c.createdAt desc")
    List<EscrowContract> findForUser(@Param("userId") Long userId);
}
