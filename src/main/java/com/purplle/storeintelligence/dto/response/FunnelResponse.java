package com.purplle.storeintelligence.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response for {@code GET /stores/{storeId}/funnel}.
 *
 * <p>Funnel: Entry → Zone Visit → Billing Queue → Purchase.
 * Session is the unit — REENTRY events do NOT double-count a visitor.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FunnelResponse {

    @JsonProperty("store_id")
    private String storeId;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    @JsonProperty("window_start")
    private LocalDateTime windowStart;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    @JsonProperty("window_end")
    private LocalDateTime windowEnd;

    /**
     * Ordered funnel stages from top to bottom.
     */
    private List<FunnelStageResponse> stages;

    /**
     * Entry-to-purchase overall conversion percentage.
     */
    @JsonProperty("overall_conversion_rate_pct")
    private double overallConversionRatePct;
}
