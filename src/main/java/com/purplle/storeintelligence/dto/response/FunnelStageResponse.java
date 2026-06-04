package com.purplle.storeintelligence.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

/**
 * One stage in the store conversion funnel.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FunnelStageResponse {

    /**
     * Stage label: ENTRY | ZONE_VISIT | BILLING_QUEUE | PURCHASE
     */
    private String stage;

    /**
     * Unique (non-staff) visitor count at this stage.
     */
    private long count;

    /**
     * Percentage of visitors from the PREVIOUS stage who dropped off here.
     */
    @JsonProperty("drop_off_pct")
    private double dropOffPct;
}
