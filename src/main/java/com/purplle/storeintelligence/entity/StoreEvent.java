package com.purplle.storeintelligence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Persisted representation of one event emitted by the detection pipeline.
 *
 * <p><strong>Idempotency:</strong> {@code event_id} (UUID v4) has a UNIQUE constraint.
 * Duplicate ingestion of the same event_id is silently skipped — never rejected.
 *
 * <p><strong>Timezone:</strong> {@code eventTimestamp} is always UTC.
 *
 * <p>Schema reference: Purplle Store Intelligence Challenge PDF §4 — Event Schema.
 */
@Entity
@Table(
        name = "store_events",
        indexes = {
                @Index(name = "idx_store_time", columnList = "store_id, event_timestamp"),
                @Index(name = "idx_visitor", columnList = "store_id, visitor_id"),
                @Index(name = "idx_event_type", columnList = "store_id, event_type"),
                @Index(name = "idx_zone", columnList = "store_id, zone_id"),
                @Index(name = "idx_is_staff", columnList = "store_id, is_staff")
        }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class StoreEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * UUID v4 — globally unique per event; used for idempotent ingestion.
     */
    @Column(name = "event_id", length = 36, nullable = false, unique = true)
    private String eventId;

    @Column(name = "store_id", length = 60, nullable = false)
    private String storeId;

    /**
     * e.g. "CAM_ENTRY_01", "CAM_3"
     */
    @Column(name = "camera_id", length = 100)
    private String cameraId;

    /**
     * Per-visit visitor token assigned by the Re-ID system, e.g. "VIS_c8a2f1".
     */
    @Column(name = "visitor_id", length = 100, nullable = false)
    private String visitorId;

    /**
     * One of: ENTRY, EXIT, ZONE_ENTER, ZONE_EXIT, ZONE_DWELL, BILLING_QUEUE_JOIN,
     * BILLING_QUEUE_ABANDON, REENTRY.
     */
    @Column(name = "event_type", length = 50, nullable = false)
    private String eventType;

    /**
     * UTC timestamp derived from clip start time + frame offset.
     */
    @Column(name = "event_timestamp", nullable = false)
    private LocalDateTime eventTimestamp;

    /**
     * Zone name from store_layout.json; null for ENTRY / EXIT events.
     */
    @Column(name = "zone_id", length = 100)
    private String zoneId;

    /**
     * Dwell duration in milliseconds; 0 for instantaneous events.
     */
    @Column(name = "dwell_ms", nullable = false)
    @Builder.Default
    private Long dwellMs = 0L;

    /**
     * True when the model classifies this person as store staff.
     */
    @Column(name = "is_staff", nullable = false)
    @Builder.Default
    private Boolean isStaff = false;

    /**
     * Detection confidence score 0.00–1.00 from the CV model.
     */
    @Column(name = "confidence", precision = 5, scale = 4)
    private BigDecimal confidence;

    /**
     * Current billing queue depth; populated only for BILLING_QUEUE_JOIN.
     */
    @Column(name = "queue_depth")
    private Integer queueDepth;

    /**
     * Zone product label from store_layout; e.g. "MOISTURISER".
     */
    @Column(name = "sku_zone", length = 100)
    private String skuZone;

    /**
     * Ordinal position of this event in the visitor's session.
     */
    @Column(name = "session_seq")
    private Integer sessionSeq;

    @CreatedDate
    @Column(name = "ingested_at", nullable = false, updatable = false)
    private LocalDateTime ingestedAt;
}
