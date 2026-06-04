package com.purplle.storeintelligence.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Health status snapshot for one store's event feed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoreHealthStatus {

    @JsonProperty("store_id")
    private String storeId;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    @JsonProperty("last_event_at")
    private LocalDateTime lastEventAt;

    /**
     * True when no events have been received for > 10 minutes.
     * Signals STALE_FEED to an on-call engineer.
     */
    @JsonProperty("is_stale")
    private boolean isStale;

    /**
     * Lag in seconds between now and last_event_at.
     */
    @JsonProperty("lag_seconds")
    private long lagSeconds;
}
