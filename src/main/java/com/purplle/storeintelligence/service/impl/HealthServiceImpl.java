package com.purplle.storeintelligence.service.impl;

import com.purplle.storeintelligence.config.AppProperties;
import com.purplle.storeintelligence.constants.AppConstants;
import com.purplle.storeintelligence.dto.response.*;
import com.purplle.storeintelligence.repository.*;
import com.purplle.storeintelligence.service.HealthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Provides a real-time health snapshot of the Store Intelligence service.
 *
 * <p>Always returns a response body — even when the database is unavailable.
 * HTTP status 503 is used when the DB is down; body still contains structured info.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HealthServiceImpl implements HealthService {

    private final StoreEventRepository eventRepo;
    private final StoreRepository storeRepo;
    private final AppProperties props;

    @Override
    @Transactional(readOnly = true)
    public HealthResponse getHealth() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        int staleMinutes = props.getAnomaly().getStaleFeedMinutes();

        // ── Database check ──────────────────────────────────────────────────────
        boolean dbUp = false;
        List<StoreHealthStatus> storeStatuses = new ArrayList<>();

        try {
            // If this query succeeds, DB is reachable
            List<Object[]> lastEvents = eventRepo.findLastEventPerStore();
            dbUp = true;

            // Build per-store health
            Map<String, LocalDateTime> lastEventMap = new LinkedHashMap<>();
            for (Object[] row : lastEvents) {
                String storeId = (String) row[0];
                LocalDateTime lastTs = row[1] == null ? null : (LocalDateTime) row[1];
                lastEventMap.put(storeId, lastTs);
            }

            // Include stores that have a registration but no events yet
            storeRepo.findAll().forEach(s -> lastEventMap.putIfAbsent(s.getStoreId(), null));

            for (Map.Entry<String, LocalDateTime> entry : lastEventMap.entrySet()) {
                String storeId = entry.getKey();
                LocalDateTime lastEvent = entry.getValue();
                boolean isStale = isStale(lastEvent, now, staleMinutes);
                long lagSeconds = lagSeconds(lastEvent, now);

                storeStatuses.add(StoreHealthStatus.builder()
                        .storeId(storeId)
                        .lastEventAt(lastEvent)
                        .isStale(isStale)
                        .lagSeconds(lagSeconds)
                        .build());
            }

        } catch (DataAccessException dae) {
            log.error("Database health check failed: {}", dae.getMessage());
        }

        // ── Overall status ──────────────────────────────────────────────────────
        long staleCount = storeStatuses.stream().filter(StoreHealthStatus::isStale).count();

        String overallStatus = !dbUp
                ? AppConstants.HEALTH_DOWN
                : staleCount > 0
                  ? AppConstants.HEALTH_DEGRADED
                  : AppConstants.HEALTH_UP;

        return HealthResponse.builder()
                .status(overallStatus)
                .database(dbUp ? AppConstants.HEALTH_UP : AppConstants.HEALTH_DOWN)
                .totalStores(storeStatuses.size())
                .staleStores((int) staleCount)
                .stores(storeStatuses)
                .timestamp(now)
                .build();
    }

    private boolean isStale(LocalDateTime lastEvent, LocalDateTime now, int staleMinutes) {
        if (lastEvent == null) return true;
        return Duration.between(lastEvent, now).toMinutes() >= staleMinutes;
    }

    private long lagSeconds(LocalDateTime lastEvent, LocalDateTime now) {
        if (lastEvent == null) return -1L;
        return Duration.between(lastEvent, now).getSeconds();
    }
}
