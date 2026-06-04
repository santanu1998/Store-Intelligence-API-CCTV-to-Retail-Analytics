package com.purplle.storeintelligence.service.impl;

import com.purplle.storeintelligence.config.AppProperties;
import com.purplle.storeintelligence.dto.response.*;
import com.purplle.storeintelligence.entity.PosTransaction;
import com.purplle.storeintelligence.exception.ResourceNotFoundException;
import com.purplle.storeintelligence.repository.*;
import com.purplle.storeintelligence.service.MetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class MetricsServiceImpl implements MetricsService {

    private final StoreEventRepository eventRepo;
    private final PosTransactionRepository posRepo;
    private final StoreRepository storeRepo;
    private final AppProperties props;

    // ── Public API ─────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public StoreMetricsResponse getMetrics(String storeId) {
        LocalDateTime start = LocalDate.now(ZoneOffset.UTC).atStartOfDay();
        LocalDateTime end = LocalDateTime.now(ZoneOffset.UTC);
        return getMetrics(storeId, start, end);
    }

    @Override
    @Transactional(readOnly = true)
    public StoreMetricsResponse getMetrics(String storeId, LocalDateTime start, LocalDateTime end) {
        validateStore(storeId);

        // ── Unique customers ───────────────────────────────────────────────────
        long uniqueVisitors = eventRepo.countUniqueCustomerEntries(storeId, start, end);

        // ── Conversion (POS correlation) ───────────────────────────────────────
        long convertedVisitors = computeConvertedVisitors(storeId, start, end);
        double conversionRate = uniqueVisitors > 0
                ? round2((double) convertedVisitors / uniqueVisitors * 100) : 0.0;

        // ── Queue depth ────────────────────────────────────────────────────────
        int queueDepth = eventRepo.findCurrentQueueDepth(storeId).orElse(0);

        // ── Abandonment rate ───────────────────────────────────────────────────
        long joins = eventRepo.countByStoreAndTypeAndTimeBetween(storeId, "BILLING_QUEUE_JOIN", start, end);
        long abandons = eventRepo.countByStoreAndTypeAndTimeBetween(storeId, "BILLING_QUEUE_ABANDON", start, end);
        double abandonRate = joins > 0 ? round2((double) abandons / joins * 100) : 0.0;

        // ── Zone dwells ────────────────────────────────────────────────────────
        List<ZoneDwellResponse> zoneDwells = buildZoneDwells(storeId, start, end);

        log.debug("Metrics [{}]: visitors={} converted={} convRate={}% queueDepth={} abandonRate={}%",
                storeId, uniqueVisitors, convertedVisitors, conversionRate, queueDepth, abandonRate);

        return StoreMetricsResponse.builder()
                .storeId(storeId)
                .windowStart(start)
                .windowEnd(end)
                .uniqueVisitors(uniqueVisitors)
                .convertedVisitors(convertedVisitors)
                .conversionRatePct(conversionRate)
                .currentQueueDepth(queueDepth)
                .abandonmentRatePct(abandonRate)
                .zoneDwells(zoneDwells)
                .build();
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    /**
     * Correlates POS transactions with visitors who had billing-zone activity
     * in the N-minute window before each transaction.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Load all POS transactions in the window.</li>
     *   <li>For each transaction T, query visitors in billing zone in [T-5min, T].</li>
     *   <li>Union all visitor IDs → distinct converted count.</li>
     * </ol>
     */
    private long computeConvertedVisitors(String storeId, LocalDateTime windowStart, LocalDateTime windowEnd) {
        List<PosTransaction> transactions = posRepo.findByStoreAndTimeBetween(storeId, windowStart, windowEnd);
        if (transactions.isEmpty()) return 0L;

        int billingMinutes = props.getConversion().getBillingWindowMinutes();
        Set<String> converted = new HashSet<>();

        for (PosTransaction tx : transactions) {
            LocalDateTime txTime = tx.getTransactionTimestamp();
            LocalDateTime corrStart = txTime.minusMinutes(billingMinutes);
            List<String> visitors = eventRepo.findBillingZoneVisitors(storeId, corrStart, txTime);
            converted.addAll(visitors);
        }

        return converted.size();
    }

    private List<ZoneDwellResponse> buildZoneDwells(String storeId, LocalDateTime start, LocalDateTime end) {
        List<Object[]> rows = eventRepo.findZoneDwellSummary(storeId, start, end);
        List<ZoneDwellResponse> result = new ArrayList<>();

        for (Object[] row : rows) {
            String zone = (String) row[0];
            double avgDwellMs = row[1] == null ? 0 : ((Number) row[1]).doubleValue();
            long visitors = row[2] == null ? 0 : ((Number) row[2]).longValue();

            result.add(ZoneDwellResponse.builder()
                    .zoneId(zone)
                    .avgDwellMs((long) avgDwellMs)
                    .avgDwellSeconds(round2(avgDwellMs / 1000.0))
                    .visitorCount(visitors)
                    .build());
        }

        return result;
    }

    private void validateStore(String storeId) {
        if (!storeRepo.existsByStoreId(storeId)) {
            throw new ResourceNotFoundException("Store", storeId);
        }
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
