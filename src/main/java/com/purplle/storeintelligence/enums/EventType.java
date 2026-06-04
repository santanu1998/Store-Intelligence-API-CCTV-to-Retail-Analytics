package com.purplle.storeintelligence.enums;

/**
 * All event types emitted by the CCTV detection pipeline.
 * Schema reference: Purplle Store Intelligence Challenge PDF §4 — Event Type Catalogue.
 */
public enum EventType {

    /**
     * Visitor crosses entry threshold inbound → starts a new session.
     */
    ENTRY,

    /**
     * Visitor crosses entry threshold outbound → closes the session.
     */
    EXIT,

    /**
     * Visitor enters a named zone (zone names from store_layout).
     */
    ZONE_ENTER,

    /**
     * Visitor leaves a named zone.
     */
    ZONE_EXIT,

    /**
     * Visitor has been in a zone continuously for 30+ seconds.
     * Emitted every 30 seconds of continued dwell.
     */
    ZONE_DWELL,

    /**
     * Visitor enters the billing zone while queue_depth &gt; 0.
     */
    BILLING_QUEUE_JOIN,

    /**
     * Visitor leaves the billing zone before a POS transaction follows.
     */
    BILLING_QUEUE_ABANDON,

    /**
     * Same visitor_id detected after a prior EXIT (re-ID system).
     */
    REENTRY
}
