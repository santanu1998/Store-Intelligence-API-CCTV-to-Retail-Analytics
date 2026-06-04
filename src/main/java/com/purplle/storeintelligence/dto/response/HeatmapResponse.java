package com.purplle.storeintelligence.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response for {@code GET /stores/{storeId}/heatmap}.
 * Score is normalised 0–100 (100 = most visited zone).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HeatmapResponse {

    @JsonProperty("store_id")
    private String storeId;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    @JsonProperty("window_start")
    private LocalDateTime windowStart;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    @JsonProperty("window_end")
    private LocalDateTime windowEnd;

    /**
     * True when session count >= 20 (the confidence threshold from the spec).
     * False signals low-data warning to the dashboard consumer.
     */
    @JsonProperty("data_confidence")
    private boolean dataConfidence;

    @JsonProperty("total_sessions")
    private long totalSessions;

    /**
     * Zone heatmap entries, sorted by score descending.
     */
    private List<ZoneHeatmapResponse> zones;
}
