package com.purplle.storeintelligence.service.impl;

import com.purplle.storeintelligence.config.AppProperties;
import com.purplle.storeintelligence.constants.AppConstants;
import com.purplle.storeintelligence.dto.response.*;
import com.purplle.storeintelligence.entity.PosTransaction;
import com.purplle.storeintelligence.enums.AnomalySeverity;
import com.purplle.storeintelligence.exception.ResourceNotFoundException;
import com.purplle.storeintelligence.repository.*;
import com.purplle.storeintelligence.service.AnomalyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Detects active anomalies in real time.
 *
 * <p>Anomaly detection rules:
 * <ul>
 *   <li>{@code BILLING_QUEUE_SPIKE} — latest queue_depth &gt; threshold (CRITICAL)</li>
 *   <li>{@code CONVERSION_DROP} — today's rate &lt; 7-day rolling avg × factor (WARN)</li>
 *   <li>{@code DEAD_ZONE} — zone with no ZONE_ENTER in past 30 min (INFO)</li>
 *   <li>{@code HIGH_ABANDONMENT} — abandonment rate &gt; 30% (WARN)</li>
 *   <li>{@code STALE_FEED} — no events from store in past 10 min (WARN)</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnomalyServiceImpl implements AnomalyService {

    private final StoreEventRepository eventRepo;
    private final PosTransactionRepository posRepo;
    private final StoreRepository storeRepo;
    private final AppProperties props;

    @Override
    @Transactional(readOnly = true)
    public AnomalyResponse getAnomalies(String storeId) {
        if (!storeRepo.existsByStoreId(storeId)) {
            throw new ResourceNotFoundException("Store", storeId);
        }

        List<AnomalyItem> anomalies = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime todayStart = LocalDate.now(ZoneOffset.UTC).atStartOfDay();

        anomalies.addAll(checkQueueSpike(storeId, now));
        anomalies.addAll(checkConversionDrop(storeId, todayStart, now));
        anomalies.addAll(checkDeadZones(storeId, now));
        anomalies.addAll(checkHighAbandonment(storeId, todayStart, now));
        anomalies.addAll(checkStaleFeed(storeId, now));

        // Sort: CRITICAL → WARN → INFO
        anomalies.sort(Comparator.comparingInt(a -> -severityOrder(a.getSeverity())));

        log.info("Anomalies [{}]: {} active", storeId, anomalies.size());

        return AnomalyResponse.builder()
                .storeId(storeId)
                .anomalyCount(anomalies.size())
                .anomalies(anomalies)
                .build();
    }

    // ── Individual anomaly checks ──────────────────────────────────────────────

    private List<AnomalyItem> checkQueueSpike(String storeId, LocalDateTime now) {
        int threshold = props.getAnomaly().getQueueSpikeThreshold();
        int current = eventRepo.findCurrentQueueDepth(storeId).orElse(0);

        if (current > threshold) {
            return List.of(AnomalyItem.builder()
                    .type(AppConstants.ANOMALY_QUEUE_SPIKE)
                    .severity(AnomalySeverity.CRITICAL)
                    .description("Billing queue depth is " + current + " (threshold: " + threshold + ")")
                    .suggestedAction(AppConstants.ACTION_OPEN_COUNTER)
                    .detectedAt(now)
                    .context("queue_depth=" + current)
                    .build());
        }
        return Collections.emptyList();
    }

    private List<AnomalyItem> checkConversionDrop(String storeId,
                                                  LocalDateTime todayStart,
                                                  LocalDateTime now) {
        // Today's conversion rate
        long todayVisitors = eventRepo.countUniqueCustomerEntries(storeId, todayStart, now);
        long todayConverted = computeConverted(storeId, todayStart, now);
        if (todayVisitors == 0) return Collections.emptyList();

        double todayRate = (double) todayConverted / todayVisitors;

        // 7-day rolling average
        LocalDateTime sevenDaysAgo = now.minusDays(7);
        double sevenDayAvg = computeSevenDayAvgConversion(storeId, sevenDaysAgo, todayStart);
        if (sevenDayAvg <= 0) return Collections.emptyList(); // not enough history

        double factor = props.getAnomaly().getConversionDropFactor();
        if (todayRate < sevenDayAvg * factor) {
            String desc = String.format(
                    "Today's conversion rate %.1f%% is below 7-day average %.1f%% (threshold: %.0f%%)",
                    todayRate * 100, sevenDayAvg * 100, factor * 100);

            return List.of(AnomalyItem.builder()
                    .type(AppConstants.ANOMALY_CONVERSION_DROP)
                    .severity(AnomalySeverity.WARN)
                    .description(desc)
                    .suggestedAction(AppConstants.ACTION_INVESTIGATE_DROP)
                    .detectedAt(now)
                    .context(String.format("today_rate=%.2f%% seven_day_avg=%.2f%%",
                            todayRate * 100, sevenDayAvg * 100))
                    .build());
        }
        return Collections.emptyList();
    }

    private List<AnomalyItem> checkDeadZones(String storeId, LocalDateTime now) {
        int deadMinutes = props.getAnomaly().getDeadZoneMinutes();
        LocalDateTime cutoff = now.minusMinutes(deadMinutes);

        List<String> allZones = eventRepo.findAllKnownZones(storeId);
        List<String> activeZones = eventRepo.findActiveZonesSince(storeId, cutoff);
        Set<String> activeSet = new HashSet<>(activeZones);

        return allZones.stream()
                .filter(z -> !activeSet.contains(z))
                .map(z -> AnomalyItem.builder()
                        .type(AppConstants.ANOMALY_DEAD_ZONE)
                        .severity(AnomalySeverity.INFO)
                        .description("Zone '" + z + "' has had no visitor activity in the last "
                                + deadMinutes + " minutes")
                        .suggestedAction(AppConstants.ACTION_CHECK_ZONE)
                        .detectedAt(now)
                        .context("zone=" + z)
                        .build())
                .collect(Collectors.toList());
    }

    private List<AnomalyItem> checkHighAbandonment(String storeId,
                                                   LocalDateTime start,
                                                   LocalDateTime end) {
        long joins = eventRepo.countByStoreAndTypeAndTimeBetween(storeId, "BILLING_QUEUE_JOIN", start, end);
        long abandons = eventRepo.countByStoreAndTypeAndTimeBetween(storeId, "BILLING_QUEUE_ABANDON", start, end);
        if (joins == 0) return Collections.emptyList();

        double rate = (double) abandons / joins;
        double threshold = props.getAnomaly().getAbandonmentWarnThreshold();

        if (rate > threshold) {
            return List.of(AnomalyItem.builder()
                    .type(AppConstants.ANOMALY_HIGH_ABANDONMENT)
                    .severity(AnomalySeverity.WARN)
                    .description(String.format("Billing queue abandonment rate is %.1f%% (threshold: %.0f%%)",
                            rate * 100, threshold * 100))
                    .suggestedAction(AppConstants.ACTION_REDUCE_WAIT)
                    .detectedAt(end)
                    .context(String.format("joins=%d abandons=%d", joins, abandons))
                    .build());
        }
        return Collections.emptyList();
    }

    private List<AnomalyItem> checkStaleFeed(String storeId, LocalDateTime now) {
        int staleMinutes = props.getAnomaly().getStaleFeedMinutes();
        Optional<LocalDateTime> lastEvent = eventRepo.findLastEventTimestamp(storeId);

        boolean isStale = lastEvent.isEmpty()
                || Duration.between(lastEvent.get(), now).toMinutes() >= staleMinutes;

        if (isStale) {
            String detail = lastEvent.map(t -> "last event at " + t)
                    .orElse("no events ever received");
            return List.of(AnomalyItem.builder()
                    .type(AppConstants.ANOMALY_STALE_FEED)
                    .severity(AnomalySeverity.WARN)
                    .description("No events received from store " + storeId + " in the last "
                            + staleMinutes + " minutes (" + detail + ")")
                    .suggestedAction(AppConstants.ACTION_CHECK_FEED)
                    .detectedAt(now)
                    .context(detail)
                    .build());
        }
        return Collections.emptyList();
    }

    // ── Conversion helpers ─────────────────────────────────────────────────────

    private long computeConverted(String storeId, LocalDateTime start, LocalDateTime end) {
        List<PosTransaction> transactions = posRepo.findByStoreAndTimeBetween(storeId, start, end);
        int minutes = props.getConversion().getBillingWindowMinutes();
        Set<String> converted = new HashSet<>();
        for (PosTransaction tx : transactions) {
            converted.addAll(eventRepo.findBillingZoneVisitors(storeId,
                    tx.getTransactionTimestamp().minusMinutes(minutes),
                    tx.getTransactionTimestamp()));
        }
        return converted.size();
    }

    private double computeSevenDayAvgConversion(String storeId,
                                                LocalDateTime sevenDaysAgo,
                                                LocalDateTime todayStart) {
        List<Object[]> dailyEntries = eventRepo.findDailyEntryCounts(storeId, sevenDaysAgo);
        List<Object[]> dailyTxs = posRepo.findDailyTransactionCounts(storeId, sevenDaysAgo);

        // Build maps: date → count
        Map<String, Long> entryByDay = new LinkedHashMap<>();
        for (Object[] row : dailyEntries) {
            entryByDay.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        }
        Map<String, Long> txByDay = new LinkedHashMap<>();
        for (Object[] row : dailyTxs) {
            txByDay.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        }

        if (entryByDay.isEmpty()) return 0.0;

        double totalRate = 0.0;
        int days = 0;
        for (Map.Entry<String, Long> e : entryByDay.entrySet()) {
            if (e.getValue() == 0) continue;
            long txCount = txByDay.getOrDefault(e.getKey(), 0L);
            totalRate += (double) txCount / e.getValue();
            days++;
        }
        return days > 0 ? totalRate / days : 0.0;
    }

    private static int severityOrder(AnomalySeverity s) {
        return switch (s) {
            case CRITICAL -> 3;
            case WARN -> 2;
            case INFO -> 1;
        };
    }
}
