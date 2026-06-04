package com.purplle.storeintelligence.service;

import com.purplle.storeintelligence.dto.response.HealthResponse;

/**
 * Reports the overall health of the Store Intelligence service.
 *
 * <p>Always responds — even when the database is down (returns HTTP 503 with body).
 * Used by on-call engineers to verify system and feed status.
 */
public interface HealthService {

    /**
     * Checks database connectivity and per-store event-feed staleness.
     *
     * @return health snapshot including per-store last-event timestamps and STALE_FEED flags
     */
    HealthResponse getHealth();
}
