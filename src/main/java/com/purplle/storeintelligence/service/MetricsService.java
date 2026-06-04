package com.purplle.storeintelligence.service;

import com.purplle.storeintelligence.dto.response.StoreMetricsResponse;

import java.time.LocalDateTime;

/**
 * Computes real-time store metrics for {@code GET /stores/{storeId}/metrics}.
 *
 * <p>All counts exclude {@code is_staff = true} events.
 * Results are always computed live from the database — never served from cache.
 */
public interface MetricsService {

    /**
     * Returns today's metrics for the given store (UTC midnight → now).
     *
     * @param storeId the store identifier
     * @return live metrics snapshot
     */
    StoreMetricsResponse getMetrics(String storeId);

    /**
     * Returns metrics for a custom time window.
     *
     * @param storeId store identifier
     * @param start   window start (UTC)
     * @param end     window end (UTC)
     * @return live metrics snapshot
     */
    StoreMetricsResponse getMetrics(String storeId, LocalDateTime start, LocalDateTime end);
}
