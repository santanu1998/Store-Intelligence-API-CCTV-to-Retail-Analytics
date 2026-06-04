package com.purplle.storeintelligence.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response for {@code GET /stores/{storeId}/metrics}.
 * All counts exclude is_staff=true events.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoreMetricsResponse {

    @JsonProperty("store_id")
    private String storeId;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    @JsonProperty("window_start")
    private LocalDateTime windowStart;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    @JsonProperty("window_end")
    private LocalDateTime windowEnd;

    // ── Visitor counts ────────────────────────────────────────────────────────

    /**
     * Distinct customer (non-staff) ENTRY events in the window.
     */
    @JsonProperty("unique_visitors")
    private long uniqueVisitors;

    /**
     * Distinct visitors correlated with a POS transaction.
     */
    @JsonProperty("converted_visitors")
    private long convertedVisitors;

    /**
     * converted_visitors / unique_visitors × 100  (0.00 when no visitors).
     */
    @JsonProperty("conversion_rate_pct")
    private double conversionRatePct;

    // ── Queue / abandonment ───────────────────────────────────────────────────

    /**
     * Current billing queue depth from the latest BILLING_QUEUE_JOIN event.
     */
    @JsonProperty("current_queue_depth")
    private int currentQueueDepth;

    /**
     * BILLING_QUEUE_ABANDON ÷ BILLING_QUEUE_JOIN × 100
     */
    @JsonProperty("abandonment_rate_pct")
    private double abandonmentRatePct;

    // ── Zone breakdown ────────────────────────────────────────────────────────

    @JsonProperty("zone_dwells")
    private List<ZoneDwellResponse> zoneDwells;
}
