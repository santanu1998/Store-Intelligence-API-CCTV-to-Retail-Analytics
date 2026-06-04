package com.purplle.storeintelligence.service.impl;

import com.purplle.storeintelligence.constants.AppConstants;
import com.purplle.storeintelligence.dto.response.*;
import com.purplle.storeintelligence.exception.ResourceNotFoundException;
import com.purplle.storeintelligence.repository.*;
import com.purplle.storeintelligence.service.HeatmapService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds a zone heatmap from ZONE_ENTER (visit frequency) and ZONE_DWELL
 * (average dwell time) events.
 *
 * <p>Normalisation: score = (zone_visit_count / max_zone_visit_count) × 100.
 * The busiest zone always scores 100; an unvisited zone scores 0.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HeatmapServiceImpl implements HeatmapService {

    private final StoreEventRepository eventRepo;
    private final StoreRepository storeRepo;

    @Override
    @Transactional(readOnly = true)
    public HeatmapResponse getHeatmap(String storeId) {
        LocalDateTime start = LocalDate.now(ZoneOffset.UTC).atStartOfDay();
        LocalDateTime end = LocalDateTime.now(ZoneOffset.UTC);
        return getHeatmap(storeId, start, end);
    }

    @Override
    @Transactional(readOnly = true)
    public HeatmapResponse getHeatmap(String storeId, LocalDateTime start, LocalDateTime end) {
        if (!storeRepo.existsByStoreId(storeId)) {
            throw new ResourceNotFoundException("Store", storeId);
        }

        List<Object[]> rows = eventRepo.findZoneHeatmapData(storeId, start, end);

        // Parse raw results
        List<ZoneHeatmapResponse> zones = new ArrayList<>();
        for (Object[] row : rows) {
            String zone = (String) row[0];
            long visitCount = row[1] == null ? 0L : ((Number) row[1]).longValue();
            double avgDwell = row[2] == null ? 0.0 : ((Number) row[2]).doubleValue();

            if (zone == null) continue;

            zones.add(ZoneHeatmapResponse.builder()
                    .zoneId(zone)
                    .visitCount(visitCount)
                    .avgDwellMs((long) avgDwell)
                    .score(0.0) // set after normalisation
                    .build());
        }

        // Normalise scores
        long maxVisits = zones.stream()
                .mapToLong(ZoneHeatmapResponse::getVisitCount).max().orElse(1L);
        if (maxVisits == 0) maxVisits = 1;

        final long finalMax = maxVisits;
        zones = zones.stream()
                .map(z -> {
                    double score = Math.round((double) z.getVisitCount() / finalMax * 100.0 * 100) / 100.0;
                    return ZoneHeatmapResponse.builder()
                            .zoneId(z.getZoneId())
                            .visitCount(z.getVisitCount())
                            .avgDwellMs(z.getAvgDwellMs())
                            .score(score)
                            .build();
                })
                .sorted(Comparator.comparingDouble(ZoneHeatmapResponse::getScore).reversed())
                .collect(Collectors.toList());

        long totalSessions = eventRepo.countUniqueCustomerEntries(storeId, start, end);
        boolean dataConfidence = totalSessions >= AppConstants.HEATMAP_MIN_SESSIONS_FOR_CONFIDENCE;

        log.debug("Heatmap [{}]: {} zones, totalSessions={}, dataConfidence={}",
                storeId, zones.size(), totalSessions, dataConfidence);

        return HeatmapResponse.builder()
                .storeId(storeId)
                .windowStart(start)
                .windowEnd(end)
                .dataConfidence(dataConfidence)
                .totalSessions(totalSessions)
                .zones(zones)
                .build();
    }
}
