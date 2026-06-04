package com.purplle.storeintelligence.controller;

import com.purplle.storeintelligence.constants.ApiRoutes;
import com.purplle.storeintelligence.dto.response.*;
import com.purplle.storeintelligence.entity.Store;
import com.purplle.storeintelligence.repository.StoreRepository;
import com.purplle.storeintelligence.service.*;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.time.*;
import java.util.List;

/**
 * Store analytics endpoints.
 *
 * <p>All GET endpoints default to "today" (UTC midnight → now).
 * Optionally accepts {@code ?start=} and {@code ?end=} in ISO-8601 UTC format
 * to query custom time windows.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Store Analytics", description = "Real-time store metrics, funnel, heatmap, and anomaly detection")
public class StoreController {

    private final MetricsService metricsService;
    private final FunnelService funnelService;
    private final HeatmapService heatmapService;
    private final AnomalyService anomalyService;
    private final StoreRepository storeRepo;

    // ── GET /stores ────────────────────────────────────────────────────────────

    @GetMapping(ApiRoutes.STORES_BASE)
    @Operation(summary = "List all registered stores")
    public ResponseEntity<ApiResponse<List<Store>>> listStores() {
        return ResponseEntity.ok(ApiResponse.success(storeRepo.findAll()));
    }

    // ── GET /stores/{storeId}/metrics ─────────────────────────────────────────

    @GetMapping(ApiRoutes.STORES_METRICS)
    @Operation(
            summary = "Get real-time store metrics",
            description = "Returns: unique_visitors, conversion_rate_pct, converted_visitors, "
                    + "current_queue_depth, abandonment_rate_pct, zone_dwells. "
                    + "Defaults to today (UTC midnight → now). Staff events are excluded. "
                    + "Results are always live — not cached."
    )
    public ResponseEntity<ApiResponse<StoreMetricsResponse>> getMetrics(
            @PathVariable String storeId,
            @Parameter(description = "Window start in ISO-8601 UTC, e.g. 2026-04-10T09:00:00")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @Parameter(description = "Window end in ISO-8601 UTC (defaults to now)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {

        log.info("GET metrics: storeId={} start={} end={}", storeId, start, end);

        StoreMetricsResponse result = (start != null && end != null)
                ? metricsService.getMetrics(storeId, start, end)
                : metricsService.getMetrics(storeId);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ── GET /stores/{storeId}/funnel ───────────────────────────────────────────

    @GetMapping(ApiRoutes.STORES_FUNNEL)
    @Operation(
            summary = "Get conversion funnel",
            description = "Entry → Zone Visit → Billing Queue → Purchase. "
                    + "Session is the unit; REENTRY does not double-count. "
                    + "Includes drop-off percentage at each stage."
    )
    public ResponseEntity<ApiResponse<FunnelResponse>> getFunnel(
            @PathVariable String storeId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {

        log.info("GET funnel: storeId={}", storeId);
        FunnelResponse result = (start != null && end != null)
                ? funnelService.getFunnel(storeId, start, end)
                : funnelService.getFunnel(storeId);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ── GET /stores/{storeId}/heatmap ──────────────────────────────────────────

    @GetMapping(ApiRoutes.STORES_HEATMAP)
    @Operation(
            summary = "Get zone heatmap",
            description = "Returns zone visit frequency and avg dwell time, normalised 0–100. "
                    + "data_confidence=false when total sessions < 20."
    )
    public ResponseEntity<ApiResponse<HeatmapResponse>> getHeatmap(
            @PathVariable String storeId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {

        log.info("GET heatmap: storeId={}", storeId);
        HeatmapResponse result = (start != null && end != null)
                ? heatmapService.getHeatmap(storeId, start, end)
                : heatmapService.getHeatmap(storeId);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ── GET /stores/{storeId}/anomalies ───────────────────────────────────────

    @GetMapping(ApiRoutes.STORES_ANOMALIES)
    @Operation(
            summary = "Get active anomalies",
            description = "Detects: BILLING_QUEUE_SPIKE (CRITICAL), CONVERSION_DROP (WARN), "
                    + "DEAD_ZONE (INFO), HIGH_ABANDONMENT (WARN), STALE_FEED (WARN). "
                    + "Sorted by severity. Each anomaly includes a suggested_action."
    )
    public ResponseEntity<ApiResponse<AnomalyResponse>> getAnomalies(
            @PathVariable String storeId) {

        log.info("GET anomalies: storeId={}", storeId);
        AnomalyResponse result = anomalyService.getAnomalies(storeId);

        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
