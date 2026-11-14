package com.payflow.repository;

import com.payflow.model.Transfer;
import com.payflow.model.TransferStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TransferRepository extends JpaRepository<Transfer, Long> {

    Optional<Transfer> findBySenderIdAndIdempotencyKey(Long senderWalletId, String idempotencyKey);

    long countBySenderIdAndCreatedAtAfter(Long senderWalletId, Instant after);

    long countBySenderIdAndReceiverIdAndCreatedAtAfter(Long senderWalletId, Long receiverWalletId, Instant after);

    long countByReceiverIdAndStatusAndCreatedAtAfter(Long receiverWalletId, TransferStatus status, Instant after);

    Optional<Transfer> findFirstBySenderIdAndStatusOrderByCreatedAtDesc(Long senderWalletId, TransferStatus status);

    long countByStatus(TransferStatus status);

    long countByReceiverIdAndStatus(Long receiverWalletId, TransferStatus status);

    long countBySenderIdAndStatusAndCreatedAtAfter(Long senderWalletId, TransferStatus status, Instant after);

    List<Transfer> findTop20BySenderIdAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(Long senderWalletId, TransferStatus status, Instant after);

    @Query("select t from Transfer t where t.sender.id = :walletId or t.receiver.id = :walletId order by t.createdAt desc")
    List<Transfer> findHistory(@Param("walletId") Long walletId, Pageable pageable);

    @Query("select t from Transfer t where (t.sender.id = :walletId or t.receiver.id = :walletId) and t.createdAt > :after")
    List<Transfer> findWalletTransfersSince(@Param("walletId") Long walletId, @Param("after") Instant after);

    List<Transfer> findByCreatedAtAfter(Instant after);

    @Query("select t from Transfer t order by t.createdAt desc")
    List<Transfer> findRecent(Pageable pageable);

    /** Senders whose completed outflow inside the window exceeds the limit. Returns [walletId, total]. */
    @Query("""
           select t.sender.id, sum(t.amount) from Transfer t
           where t.status = :status and t.createdAt > :after
           group by t.sender.id
           having sum(t.amount) > :limit
           """)
    List<Object[]> findHighOutflow(@Param("status") TransferStatus status,
                                   @Param("after") Instant after,
                                   @Param("limit") BigDecimal limit);
}
