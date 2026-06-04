package com.purplle.storeintelligence.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;

import java.util.List;

/**
 * Request body for {@code POST /events/ingest}.
 * Accepts batches of up to 500 events per the Purplle challenge spec.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngestEventsRequest {

    @NotNull(message = "events list must not be null")
    @NotEmpty(message = "events list must contain at least one event")
    @Size(max = 500, message = "Maximum 500 events per batch")
    @Valid
    private List<StoreEventRequest> events;
}
