package com.purplle.storeintelligence.service;

import com.purplle.storeintelligence.dto.response.HeatmapResponse;

import java.time.LocalDateTime;

/**
 * Computes zone visit frequency + avg dwell for {@code GET /stores/{storeId}/heatmap}.
 * Score is normalised 0–100 (100 = most visited zone in the store).
 * Sets {@code data_confidence = false} when total unique sessions &lt; 20.
 */
public interface HeatmapService {

    HeatmapResponse getHeatmap(String storeId);

    HeatmapResponse getHeatmap(String storeId, LocalDateTime start, LocalDateTime end);
}
