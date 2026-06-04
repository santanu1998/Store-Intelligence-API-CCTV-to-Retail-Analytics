package com.purplle.storeintelligence.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

/**
 * Response for {@code GET /stores/{storeId}/anomalies}.
 * Returns ALL currently active anomalies ordered by severity (CRITICAL → WARN → INFO).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnomalyResponse {

    @JsonProperty("store_id")
    private String storeId;

    @JsonProperty("anomaly_count")
    private int anomalyCount;

    /**
     * Active anomalies, sorted CRITICAL → WARN → INFO.
     */
    private List<AnomalyItem> anomalies;
}
