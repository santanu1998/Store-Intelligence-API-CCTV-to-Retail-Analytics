package com.purplle.storeintelligence.service;

import com.purplle.storeintelligence.dto.response.AnomalyResponse;

/**
 * Detects active operational anomalies for {@code GET /stores/{storeId}/anomalies}.
 *
 * <p>Anomaly types (from challenge spec):
 * <ul>
 *   <li>BILLING_QUEUE_SPIKE — current queue depth &gt; threshold → CRITICAL</li>
 *   <li>CONVERSION_DROP — today's rate &lt; 7-day avg × factor → WARN</li>
 *   <li>DEAD_ZONE — no ZONE_ENTER in 30 min → INFO</li>
 *   <li>HIGH_ABANDONMENT — abandonment rate &gt; 30% → WARN</li>
 *   <li>STALE_FEED — no events from store in 10 min → WARN</li>
 * </ul>
 */
public interface AnomalyService {

    AnomalyResponse getAnomalies(String storeId);
}
