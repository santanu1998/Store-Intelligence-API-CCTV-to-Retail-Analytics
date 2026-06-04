package com.purplle.storeintelligence.service.impl;

import com.purplle.storeintelligence.config.AppProperties;
import com.purplle.storeintelligence.dto.response.*;
import com.purplle.storeintelligence.entity.PosTransaction;
import com.purplle.storeintelligence.exception.ResourceNotFoundException;
import com.purplle.storeintelligence.repository.*;
import com.purplle.storeintelligence.service.FunnelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

/**
 * Implements the 4-stage conversion funnel:
 * <pre>
 *  Entry → Zone Visit → Billing Queue → Purchase
 * </pre>
 *
 * <p>Unit of measurement is the unique visitor (by visitor_id).
 * REENTRY events do not inflate any stage count because all queries use
 * {@code COUNT(DISTINCT visitor_id)}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FunnelServiceImpl implements FunnelService {

    private final StoreEventRepository eventRepo;
    private final PosTransactionRepository posRepo;
    private final StoreRepository storeRepo;
    private final AppProperties props;

    @Override
    @Transactional(readOnly = true)
    public FunnelResponse getFunnel(String storeId) {
        LocalDateTime start = LocalDate.now(ZoneOffset.UTC).atStartOfDay();
        LocalDateTime end = LocalDateTime.now(ZoneOffset.UTC);
        return getFunnel(storeId, start, end);
    }

    @Override
    @Transactional(readOnly = true)
    public FunnelResponse getFunnel(String storeId, LocalDateTime start, LocalDateTime end) {
        if (!storeRepo.existsByStoreId(storeId)) {
            throw new ResourceNotFoundException("Store", storeId);
        }

        // Stage 1 – Entry
        long entryCount = eventRepo.countUniqueCustomerEntries(storeId, start, end);

        // Stage 2 – Zone Visit (at least one ZONE_ENTER)
        long zoneVisitCount = eventRepo.countUniqueZoneVisitors(storeId, start, end);
        // A visitor who entered must have zone visits ≤ total entries (cap for data quality)
        zoneVisitCount = Math.min(zoneVisitCount, entryCount);

        // Stage 3 – Billing Queue
        long billingCount = eventRepo.countUniqueBillingQueueVisitors(storeId, start, end);
        billingCount = Math.min(billingCount, entryCount);

        // Stage 4 – Purchase (POS correlation, same algorithm as MetricsService)
        long purchaseCount = computeConvertedVisitors(storeId, start, end);
        purchaseCount = Math.min(purchaseCount, billingCount > 0 ? billingCount : entryCount);

        // Build stages with drop-off %
        List<FunnelStageResponse> stages = List.of(
                stage("ENTRY", entryCount, 0, 0),
                stage("ZONE_VISIT", zoneVisitCount, entryCount, 1),
                stage("BILLING_QUEUE", billingCount, zoneVisitCount, 2),
                stage("PURCHASE", purchaseCount, billingCount, 3)
        );

        double overallRate = entryCount > 0
                ? round2((double) purchaseCount / entryCount * 100) : 0.0;

        log.debug("Funnel [{}]: entry={} zone={} billing={} purchase={} rate={}%",
                storeId, entryCount, zoneVisitCount, billingCount, purchaseCount, overallRate);

        return FunnelResponse.builder()
                .storeId(storeId)
                .windowStart(start)
                .windowEnd(end)
                .stages(stages)
                .overallConversionRatePct(overallRate)
                .build();
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private FunnelStageResponse stage(String name, long count, long prevCount, int stageIdx) {
        double dropOff = (stageIdx == 0 || prevCount == 0) ? 0.0
                : round2((double) (prevCount - count) / prevCount * 100);
        return FunnelStageResponse.builder()
                .stage(name).count(count).dropOffPct(dropOff).build();
    }

    private long computeConvertedVisitors(String storeId, LocalDateTime start, LocalDateTime end) {
        List<PosTransaction> transactions = posRepo.findByStoreAndTimeBetween(storeId, start, end);
        if (transactions.isEmpty()) return 0L;

        int minutes = props.getConversion().getBillingWindowMinutes();
        Set<String> converted = new HashSet<>();
        for (PosTransaction tx : transactions) {
            converted.addAll(
                    eventRepo.findBillingZoneVisitors(storeId,
                            tx.getTransactionTimestamp().minusMinutes(minutes),
                            tx.getTransactionTimestamp()));
        }
        return converted.size();
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
