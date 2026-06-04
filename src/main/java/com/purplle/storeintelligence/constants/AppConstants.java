package com.purplle.storeintelligence.constants;

/**
 * Central registry for all application-wide string/numeric literals.
 * Never use raw strings in service or controller code — add them here.
 */
public final class AppConstants {

    private AppConstants() {
    }

    // ── Anomaly Type Identifiers ───────────────────────────────────────────────
    public static final String ANOMALY_QUEUE_SPIKE = "BILLING_QUEUE_SPIKE";
    public static final String ANOMALY_CONVERSION_DROP = "CONVERSION_DROP";
    public static final String ANOMALY_DEAD_ZONE = "DEAD_ZONE";
    public static final String ANOMALY_HIGH_ABANDONMENT = "HIGH_ABANDONMENT";
    public static final String ANOMALY_STALE_FEED = "STALE_FEED";

    // ── Suggested Actions ──────────────────────────────────────────────────────
    public static final String ACTION_OPEN_COUNTER = "Open an additional billing counter immediately";
    public static final String ACTION_INVESTIGATE_DROP = "Review pricing, promotions, or recent staff changes";
    public static final String ACTION_CHECK_ZONE = "Check if zone display is obstructed or camera is offline";
    public static final String ACTION_REDUCE_WAIT = "Deploy additional staff to billing queue";
    public static final String ACTION_CHECK_FEED = "Verify CCTV pipeline connectivity for this store";

    // ── Header Names ───────────────────────────────────────────────────────────
    public static final String HEADER_TRACE_ID = "X-Trace-Id";
    public static final String HEADER_STORE_ID = "X-Store-Id";

    // ── Defaults ──────────────────────────────────────────────────────────────
    public static final String DEFAULT_TIMEZONE = "UTC";
    public static final int MAX_BATCH_SIZE = 500;
    public static final int HEATMAP_MIN_SESSIONS_FOR_CONFIDENCE = 20;

    // ── Health Status ─────────────────────────────────────────────────────────
    public static final String HEALTH_UP = "UP";
    public static final String HEALTH_DOWN = "DOWN";
    public static final String HEALTH_DEGRADED = "DEGRADED";

    // ── Date/Time ─────────────────────────────────────────────────────────────
    public static final String ISO_UTC_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'";
    public static final String POS_DATE_FORMAT = "dd-MM-yyyy";
    public static final String POS_TIME_FORMAT = "HH:mm:ss";
}
