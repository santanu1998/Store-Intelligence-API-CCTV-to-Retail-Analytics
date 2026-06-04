package com.purplle.storeintelligence.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

/**
 * Heatmap data point for a single zone.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZoneHeatmapResponse {

    @JsonProperty("zone_id")
    private String zoneId;

    /**
     * Distinct non-staff visitors who entered this zone.
     */
    @JsonProperty("visit_count")
    private long visitCount;

    /**
     * Average dwell time in milliseconds (from ZONE_DWELL events).
     */
    @JsonProperty("avg_dwell_ms")
    private long avgDwellMs;

    /**
     * Normalised visit score 0–100.
     * 100 = busiest zone in the store; 0 = no visits.
     */
    @JsonProperty("score")
    private double score;
}
