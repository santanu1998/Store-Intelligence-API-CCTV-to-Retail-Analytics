package com.purplle.storeintelligence.constants;

/**
 * All REST endpoint path constants.
 * Matches the spec endpoints in the Purplle Store Intelligence Challenge PDF §4.
 */
public final class ApiRoutes {

    private ApiRoutes() {
    }

    private static final String API = "/api/v1";

    // ── Event Ingestion ────────────────────────────────────────────────────────
    public static final String EVENTS_INGEST = "/events/ingest";
    public static final String POS_INGEST = "/pos/ingest";
    public static final String POS_IMPORT_CSV = "/pos/import-csv";

    // ── Store Analytics ────────────────────────────────────────────────────────
    public static final String STORES_BASE = "/stores";
    public static final String STORES_METRICS = "/stores/{storeId}/metrics";
    public static final String STORES_FUNNEL = "/stores/{storeId}/funnel";
    public static final String STORES_HEATMAP = "/stores/{storeId}/heatmap";
    public static final String STORES_ANOMALIES = "/stores/{storeId}/anomalies";

    // ── Health ─────────────────────────────────────────────────────────────────
    public static final String HEALTH = "/health";
}
