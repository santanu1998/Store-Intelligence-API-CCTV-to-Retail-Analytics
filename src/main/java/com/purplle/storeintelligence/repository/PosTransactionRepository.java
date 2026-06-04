package com.purplle.storeintelligence.repository;

import com.purplle.storeintelligence.entity.PosTransaction;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PosTransactionRepository extends JpaRepository<PosTransaction, Long> {

    boolean existsByTransactionId(String transactionId);

    /**
     * All POS transactions for a store within a time window (for conversion calc).
     */
    @Query("""
            SELECT p FROM PosTransaction p
            WHERE p.storeId              = :storeId
              AND p.transactionTimestamp BETWEEN :start AND :end
            ORDER BY p.transactionTimestamp ASC
            """)
    List<PosTransaction> findByStoreAndTimeBetween(@Param("storeId") String storeId,
                                                   @Param("start") LocalDateTime start,
                                                   @Param("end") LocalDateTime end);

    /**
     * Count transactions for a store in a time window (for 7-day avg conversion).
     */
    @Query(value = """
            SELECT DATE(transaction_timestamp) AS day,
                   COUNT(*) AS tx_count
            FROM pos_transactions
            WHERE store_id              = :storeId
              AND transaction_timestamp >= :since
            GROUP BY DATE(transaction_timestamp)
            ORDER BY day
            """, nativeQuery = true)
    List<Object[]> findDailyTransactionCounts(@Param("storeId") String storeId,
                                              @Param("since") LocalDateTime since);
}
