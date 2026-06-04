package com.purplle.storeintelligence.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response for {@code GET /health}.
 * What an on-call engineer checks first — must always respond, even when DB is down.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HealthResponse {

    /**
     * UP | DOWN | DEGRADED
     */
    private String status;

    /**
     * Database connectivity status: UP | DOWN
     */
    private String database;

    @JsonProperty("total_stores")
    private int totalStores;

    @JsonProperty("stale_stores")
    private int staleStores;

    /**
     * Per-store last-event timestamps and stale flags.
     */
    private List<StoreHealthStatus> stores;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private LocalDateTime timestamp;
}
