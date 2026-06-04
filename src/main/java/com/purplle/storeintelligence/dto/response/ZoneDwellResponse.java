package com.purplle.storeintelligence.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZoneDwellResponse {

    @JsonProperty("zone_id")
    private String zoneId;

    @JsonProperty("avg_dwell_ms")
    private long avgDwellMs;

    @JsonProperty("avg_dwell_seconds")
    private double avgDwellSeconds;

    @JsonProperty("visitor_count")
    private long visitorCount;
}
