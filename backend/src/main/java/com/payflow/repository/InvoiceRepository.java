package com.payflow.repository;

import com.payflow.model.Invoice;
import com.payflow.model.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    Optional<Invoice> findByToken(String token);
    List<Invoice> findByFreelancerIdOrderByCreatedAtDesc(Long freelancerId);
    Optional<Invoice> findByIdAndFreelancerId(Long id, Long freelancerId);
    long countByFreelancerIdAndStatus(Long freelancerId, InvoiceStatus status);
}
