package com.purplle.storeintelligence.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

/**
 * Result of a {@code POST /events/ingest} call.
 * Supports partial success: valid events are stored even if some fail validation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngestResponse {

    /**
     * Total events in the batch.
     */
    private int total;

    /**
     * Events that passed validation and were persisted.
     */
    private int accepted;

    /**
     * Events that failed schema or business-rule validation.
     */
    private int rejected;

    /**
     * Events whose event_id was already present (idempotent skip).
     */
    private int duplicates;

    /**
     * Human-readable descriptions of each rejected event.
     */
    @JsonProperty("errors")
    private List<String> errors;
}
