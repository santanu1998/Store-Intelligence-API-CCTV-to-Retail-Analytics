package com.purplle.storeintelligence.repository;

import com.purplle.storeintelligence.entity.StoreEvent;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface StoreEventRepository extends JpaRepository<StoreEvent, Long> {

    // ── Idempotency check ──────────────────────────────────────────────────────

    boolean existsByEventId(String eventId);

    // ── Unique-visitor counts (non-staff only) ─────────────────────────────────

    /**
     * Count distinct customer (non-staff) ENTRY events for a store in a time window.
     */
    @Query("""
            SELECT COUNT(DISTINCT e.visitorId)
            FROM StoreEvent e
            WHERE e.storeId        = :storeId
              AND e.eventType      = 'ENTRY'
              AND e.isStaff        = false
              AND e.eventTimestamp BETWEEN :start AND :end
            """)
    long countUniqueCustomerEntries(@Param("storeId") String storeId,
                                    @Param("start") LocalDateTime start,
                                    @Param("end") LocalDateTime end);

    /**
     * Count distinct non-staff visitors who had at least one ZONE_ENTER event.
     */
    @Query("""
            SELECT COUNT(DISTINCT e.visitorId)
            FROM StoreEvent e
            WHERE e.storeId        = :storeId
              AND e.eventType      = 'ZONE_ENTER'
              AND e.isStaff        = false
              AND e.eventTimestamp BETWEEN :start AND :end
            """)
    long countUniqueZoneVisitors(@Param("storeId") String storeId,
                                 @Param("start") LocalDateTime start,
                                 @Param("end") LocalDateTime end);

    /**
     * Count distinct non-staff visitors who joined billing queue.
     */
    @Query("""
            SELECT COUNT(DISTINCT e.visitorId)
            FROM StoreEvent e
            WHERE e.storeId        = :storeId
              AND e.eventType      = 'BILLING_QUEUE_JOIN'
              AND e.isStaff        = false
              AND e.eventTimestamp BETWEEN :start AND :end
            """)
    long countUniqueBillingQueueVisitors(@Param("storeId") String storeId,
                                         @Param("start") LocalDateTime start,
                                         @Param("end") LocalDateTime end);

    // ── Conversion correlation ─────────────────────────────────────────────────

    /**
     * Returns visitor IDs who had billing-zone activity (BILLING_QUEUE_JOIN or
     * ZONE_ENTER in a billing-like zone) within a specific time window.
     * Used to correlate with POS transactions.
     */
    @Query("""
            SELECT DISTINCT e.visitorId
            FROM StoreEvent e
            WHERE e.storeId        = :storeId
              AND e.isStaff        = false
              AND (
                   e.eventType     = 'BILLING_QUEUE_JOIN'
                   OR (e.eventType = 'ZONE_ENTER'
                       AND (UPPER(COALESCE(e.zoneId,'')) LIKE '%BILLING%'
                         OR UPPER(COALESCE(e.zoneId,'')) LIKE '%CHECKOUT%'
                         OR UPPER(COALESCE(e.zoneId,'')) LIKE '%CASHIER%'
                         OR UPPER(COALESCE(e.zoneId,'')) LIKE '%CASH%'
                         OR UPPER(COALESCE(e.zoneId,'')) LIKE '%COUNTER%'))
                  )
              AND e.eventTimestamp BETWEEN :windowStart AND :windowEnd
            """)
    List<String> findBillingZoneVisitors(@Param("storeId") String storeId,
                                         @Param("windowStart") LocalDateTime windowStart,
                                         @Param("windowEnd") LocalDateTime windowEnd);

    // ── Zone dwell analytics ───────────────────────────────────────────────────

    /**
     * Returns [zone_id, avg_dwell_ms, visitor_count] aggregated for ZONE_DWELL events.
     */
    @Query("""
            SELECT e.zoneId, AVG(e.dwellMs), COUNT(DISTINCT e.visitorId)
            FROM StoreEvent e
            WHERE e.storeId        = :storeId
              AND e.eventType      = 'ZONE_DWELL'
              AND e.isStaff        = false
              AND e.zoneId         IS NOT NULL
              AND e.eventTimestamp BETWEEN :start AND :end
            GROUP BY e.zoneId
            ORDER BY AVG(e.dwellMs) DESC
            """)
    List<Object[]> findZoneDwellSummary(@Param("storeId") String storeId,
                                        @Param("start") LocalDateTime start,
                                        @Param("end") LocalDateTime end);

    // ── Heatmap data ───────────────────────────────────────────────────────────

    /**
     * Returns [zone_id, unique_visitor_count, avg_dwell_ms] for ZONE_ENTER events
     * (visit count) and ZONE_DWELL events (dwell time), merged by zone_id.
     */
    @Query("""
            SELECT e.zoneId,
                   COUNT(DISTINCT CASE WHEN e.eventType = 'ZONE_ENTER' THEN e.visitorId END),
                   AVG(CASE WHEN e.eventType = 'ZONE_DWELL' THEN e.dwellMs ELSE NULL END)
            FROM StoreEvent e
            WHERE e.storeId        = :storeId
              AND e.isStaff        = false
              AND e.zoneId         IS NOT NULL
              AND e.eventType      IN ('ZONE_ENTER', 'ZONE_DWELL')
              AND e.eventTimestamp BETWEEN :start AND :end
            GROUP BY e.zoneId
            ORDER BY COUNT(DISTINCT CASE WHEN e.eventType = 'ZONE_ENTER' THEN e.visitorId END) DESC
            """)
    List<Object[]> findZoneHeatmapData(@Param("storeId") String storeId,
                                       @Param("start") LocalDateTime start,
                                       @Param("end") LocalDateTime end);

    // ── Abandonment rate ───────────────────────────────────────────────────────

    /**
     * Count raw (not distinct) billing queue joins (for abandonment ratio).
     */
    @Query("""
            SELECT COUNT(e)
            FROM StoreEvent e
            WHERE e.storeId        = :storeId
              AND e.eventType      = :eventType
              AND e.isStaff        = false
              AND e.eventTimestamp BETWEEN :start AND :end
            """)
    long countByStoreAndTypeAndTimeBetween(@Param("storeId") String storeId,
                                           @Param("eventType") String eventType,
                                           @Param("start") LocalDateTime start,
                                           @Param("end") LocalDateTime end);

    // ── Queue depth ────────────────────────────────────────────────────────────

    /**
     * Latest billing queue depth from the most recent BILLING_QUEUE_JOIN event.
     */
    @Query("""
            SELECT e.queueDepth
            FROM StoreEvent e
            WHERE e.storeId   = :storeId
              AND e.eventType = 'BILLING_QUEUE_JOIN'
              AND e.queueDepth IS NOT NULL
            ORDER BY e.eventTimestamp DESC
            LIMIT 1
            """)
    Optional<Integer> findCurrentQueueDepth(@Param("storeId") String storeId);

    // ── Dead-zone detection ────────────────────────────────────────────────────

    /**
     * All distinct zone IDs that had at least one ZONE_ENTER event for the store.
     */
    @Query("""
            SELECT DISTINCT e.zoneId
            FROM StoreEvent e
            WHERE e.storeId   = :storeId
              AND e.eventType = 'ZONE_ENTER'
              AND e.zoneId    IS NOT NULL
            """)
    List<String> findAllKnownZones(@Param("storeId") String storeId);

    /**
     * Zone IDs that had a ZONE_ENTER event after the given cutoff time.
     */
    @Query("""
            SELECT DISTINCT e.zoneId
            FROM StoreEvent e
            WHERE e.storeId        = :storeId
              AND e.eventType      = 'ZONE_ENTER'
              AND e.zoneId         IS NOT NULL
              AND e.eventTimestamp >= :cutoff
            """)
    List<String> findActiveZonesSince(@Param("storeId") String storeId,
                                      @Param("cutoff") LocalDateTime cutoff);

    // ── Health / stale-feed ────────────────────────────────────────────────────

    /**
     * Timestamp of the most recent event for each store.
     */
    @Query("""
            SELECT e.storeId, MAX(e.eventTimestamp)
            FROM StoreEvent e
            GROUP BY e.storeId
            """)
    List<Object[]> findLastEventPerStore();

    @Query("""
            SELECT MAX(e.eventTimestamp)
            FROM StoreEvent e
            WHERE e.storeId = :storeId
            """)
    Optional<LocalDateTime> findLastEventTimestamp(@Param("storeId") String storeId);

    // ── 7-day conversion history (for anomaly: CONVERSION_DROP) ───────────────

    /**
     * Returns [date, unique_customer_entry_count] for the past N days.
     * Used to compute rolling average conversion rate for anomaly detection.
     */
    @Query(value = """
            SELECT DATE(event_timestamp) AS day,
                   COUNT(DISTINCT CASE WHEN event_type = 'ENTRY' AND is_staff = 0 THEN visitor_id END) AS entries
            FROM store_events
            WHERE store_id        = :storeId
              AND event_timestamp >= :since
            GROUP BY DATE(event_timestamp)
            ORDER BY day
            """, nativeQuery = true)
    List<Object[]> findDailyEntryCounts(@Param("storeId") String storeId,
                                        @Param("since") LocalDateTime since);
}
