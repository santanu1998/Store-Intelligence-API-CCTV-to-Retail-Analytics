package com.purplle.storeintelligence.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

/**
 * The nested {@code metadata} object inside a store event.
 * All fields are optional — null is valid.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventMetadataRequest {

    /**
     * Queue depth at the billing counter; populated for BILLING_QUEUE_JOIN.
     */
    @JsonProperty("queue_depth")
    private Integer queueDepth;

    /**
     * Product/zone label from store_layout.json, e.g. "MOISTURISER".
     */
    @JsonProperty("sku_zone")
    private String skuZone;

    /**
     * Ordinal position of this event in the visitor's session.
     */
    @JsonProperty("session_seq")
    private Integer sessionSeq;
}
