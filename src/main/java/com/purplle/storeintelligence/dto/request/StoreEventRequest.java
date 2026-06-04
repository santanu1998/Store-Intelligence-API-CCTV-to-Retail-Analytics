package com.purplle.storeintelligence.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One event as received in the ingest payload.
 * Mirrors the required output schema from the Purplle challenge PDF §4.
 *
 * <pre>
 * {
 *   "event_id":   "uuid-v4",
 *   "store_id":   "STORE_BLR_002",
 *   "camera_id":  "CAM_ENTRY_01",
 *   "visitor_id": "VIS_c8a2f1",
 *   "event_type": "ZONE_DWELL",
 *   "timestamp":  "2026-03-03T14:22:10Z",
 *   "zone_id":    "SKINCARE",
 *   "dwell_ms":   8400,
 *   "is_staff":   false,
 *   "confidence": 0.91,
 *   "metadata":   { "queue_depth": null, "sku_zone": "MOISTURISER", "session_seq": 5 }
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoreEventRequest {

    @NotBlank(message = "event_id is required and must be a UUID v4")
    @JsonProperty("event_id")
    private String eventId;

    @NotBlank(message = "store_id is required")
    @JsonProperty("store_id")
    private String storeId;

    @JsonProperty("camera_id")
    private String cameraId;

    @NotBlank(message = "visitor_id is required")
    @JsonProperty("visitor_id")
    private String visitorId;

    @NotBlank(message = "event_type is required")
    @JsonProperty("event_type")
    private String eventType;

    @NotNull(message = "timestamp is required (ISO-8601 UTC)")
    @JsonProperty("timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private LocalDateTime timestamp;

    /**
     * null for ENTRY / EXIT events.
     */
    @JsonProperty("zone_id")
    private String zoneId;

    @Min(value = 0, message = "dwell_ms must be >= 0")
    @JsonProperty("dwell_ms")
    @Builder.Default
    private Long dwellMs = 0L;

    @JsonProperty("is_staff")
    @Builder.Default
    private Boolean isStaff = false;

    @DecimalMin(value = "0.0", message = "confidence must be between 0 and 1")
    @DecimalMax(value = "1.0", message = "confidence must be between 0 and 1")
    @JsonProperty("confidence")
    private BigDecimal confidence;

    @Valid
    @JsonProperty("metadata")
    private EventMetadataRequest metadata;
}
