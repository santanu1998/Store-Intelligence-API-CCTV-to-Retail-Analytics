package com.purplle.storeintelligence.service.impl;

import com.purplle.storeintelligence.config.AppProperties;
import com.purplle.storeintelligence.constants.AppConstants;
import com.purplle.storeintelligence.dto.request.*;
import com.purplle.storeintelligence.dto.response.IngestResponse;
import com.purplle.storeintelligence.entity.*;
import com.purplle.storeintelligence.enums.EventType;
import com.purplle.storeintelligence.repository.*;
import com.purplle.storeintelligence.service.EventIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventIngestionServiceImpl implements EventIngestionService {

    private final StoreEventRepository eventRepository;
    private final PosTransactionRepository posRepository;
    private final StoreRepository storeRepository;
    private final AppProperties props;

    // ── Event Ingestion ────────────────────────────────────────────────────────

    @Override
    @Transactional
    public IngestResponse ingestEvents(IngestEventsRequest request) {
        List<StoreEventRequest> events = request.getEvents();
        int total = events.size(), accepted = 0, rejected = 0, duplicates = 0;
        List<String> errors = new ArrayList<>();

        for (StoreEventRequest req : events) {
            try {
                // 1. Idempotency check
                if (eventRepository.existsByEventId(req.getEventId())) {
                    log.debug("Duplicate event skipped: {}", req.getEventId());
                    duplicates++;
                    continue;
                }

                // 2. Business-rule validation
                validateEvent(req);

                // 3. Auto-register store if new
                ensureStoreExists(req.getStoreId());

                // 4. Persist
                eventRepository.save(mapToEntity(req));
                accepted++;

            } catch (IllegalArgumentException e) {
                rejected++;
                errors.add("[" + req.getEventId() + "] " + e.getMessage());
                log.warn("Event rejected: {} — {}", req.getEventId(), e.getMessage());
            }
        }

        log.info("Ingest batch: total={} accepted={} rejected={} duplicates={}",
                total, accepted, rejected, duplicates);

        return IngestResponse.builder()
                .total(total).accepted(accepted)
                .rejected(rejected).duplicates(duplicates)
                .errors(errors).build();
    }

    // ── POS Transaction Ingestion ──────────────────────────────────────────────

    @Override
    @Transactional
    public IngestResponse ingestPosTransactions(IngestPosRequest request) {
        int total = request.getTransactions().size(), accepted = 0, rejected = 0, duplicates = 0;
        List<String> errors = new ArrayList<>();

        for (PosTransactionRequest req : request.getTransactions()) {
            try {
                if (posRepository.existsByTransactionId(req.getTransactionId())) {
                    duplicates++;
                    continue;
                }
                ensureStoreExists(req.getStoreId());
                posRepository.save(mapPosToEntity(req));
                accepted++;
            } catch (Exception e) {
                rejected++;
                errors.add("[" + req.getTransactionId() + "] " + e.getMessage());
            }
        }

        log.info("POS ingest: total={} accepted={} rejected={} duplicates={}",
                total, accepted, rejected, duplicates);

        return IngestResponse.builder()
                .total(total).accepted(accepted)
                .rejected(rejected).duplicates(duplicates)
                .errors(errors).build();
    }

    // ── CSV Import (Brigade Bangalore format) ──────────────────────────────────

    @Override
    @Transactional
    public IngestResponse importPosCsv(String defaultStoreId, MultipartFile file) throws IOException {
        int total = 0, accepted = 0, rejected = 0, duplicates = 0;
        List<String> errors = new ArrayList<>();

        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern(AppConstants.POS_DATE_FORMAT);
        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern(AppConstants.POS_TIME_FORMAT);

        try (Reader reader = new InputStreamReader(file.getInputStream());
             CSVParser csv = CSVFormat.DEFAULT
                     .builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreHeaderCase(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {

            for (CSVRecord row : csv) {
                total++;
                try {
                    String txnId = row.get("order_id");
                    String storeId = safeGet(row, "store_id", defaultStoreId);
                    String dateStr = row.get("order_date");
                    String timeStr = row.get("order_time");

                    if (posRepository.existsByTransactionId(txnId)) {
                        duplicates++;
                        continue;
                    }

                    LocalDate date = LocalDate.parse(dateStr, dateFmt);
                    LocalTime time = LocalTime.parse(timeStr, timeFmt);
                    LocalDateTime ts = LocalDateTime.of(date, time);

                    String totalAmtStr = safeGet(row, "total_amount", "0");
                    BigDecimal amount = totalAmtStr.isBlank()
                            ? BigDecimal.ZERO
                            : new BigDecimal(totalAmtStr.replace(",", ""));

                    ensureStoreExists(storeId);

                    PosTransaction tx = PosTransaction.builder()
                            .storeId(storeId)
                            .transactionId(txnId)
                            .transactionTimestamp(ts)
                            .basketValueInr(amount)
                            .orderDate(date)
                            .invoiceNumber(safeGet(row, "invoice_number", null))
                            .invoiceType(safeGet(row, "invoice_type", null))
                            .customerNumber(safeGet(row, "customer_number", null))
                            .salespersonId(safeGet(row, "salesperson_id", null))
                            .build();

                    posRepository.save(tx);
                    accepted++;

                } catch (Exception e) {
                    rejected++;
                    errors.add("Row " + total + ": " + e.getMessage());
                    log.warn("CSV row {} rejected: {}", total, e.getMessage());
                }
            }
        }

        log.info("CSV import: total={} accepted={} rejected={} duplicates={}",
                total, accepted, rejected, duplicates);

        return IngestResponse.builder()
                .total(total).accepted(accepted)
                .rejected(rejected).duplicates(duplicates)
                .errors(errors).build();
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private void validateEvent(StoreEventRequest req) {
        // Validate event_type is a known enum value
        try {
            EventType.valueOf(req.getEventType().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unknown event_type: '" + req.getEventType() + "'. Valid types: "
                            + Arrays.toString(EventType.values()));
        }

        // end_time sanity: dwell events must have dwell_ms > 0
        if ("ZONE_DWELL".equalsIgnoreCase(req.getEventType()) && req.getDwellMs() <= 0) {
            throw new IllegalArgumentException("ZONE_DWELL events must have dwell_ms > 0");
        }

        // BILLING_QUEUE_JOIN should have queue_depth in metadata
        if ("BILLING_QUEUE_JOIN".equalsIgnoreCase(req.getEventType())
                && (req.getMetadata() == null || req.getMetadata().getQueueDepth() == null)) {
            log.warn("BILLING_QUEUE_JOIN event {} missing queue_depth in metadata", req.getEventId());
        }
    }

    /**
     * Registers a store row if one does not yet exist.
     * Handles the race condition where two threads try to create the same store simultaneously.
     */
    private void ensureStoreExists(String storeId) {
        if (!storeRepository.existsByStoreId(storeId)) {
            try {
                storeRepository.saveAndFlush(
                        Store.builder().storeId(storeId).build());
            } catch (DataIntegrityViolationException ex) {
                // Concurrent thread already created it — safe to ignore
                log.debug("Store {} created by concurrent thread, skipping", storeId);
            }
        }
    }

    private StoreEvent mapToEntity(StoreEventRequest req) {
        Integer queueDepth = null;
        String skuZone = null;
        Integer sessionSeq = null;

        if (req.getMetadata() != null) {
            queueDepth = req.getMetadata().getQueueDepth();
            skuZone = req.getMetadata().getSkuZone();
            sessionSeq = req.getMetadata().getSessionSeq();
        }

        return StoreEvent.builder()
                .eventId(req.getEventId())
                .storeId(req.getStoreId())
                .cameraId(req.getCameraId())
                .visitorId(req.getVisitorId())
                .eventType(req.getEventType().toUpperCase())
                .eventTimestamp(req.getTimestamp())
                .zoneId(req.getZoneId())
                .dwellMs(req.getDwellMs() != null ? req.getDwellMs() : 0L)
                .isStaff(Boolean.TRUE.equals(req.getIsStaff()))
                .confidence(req.getConfidence())
                .queueDepth(queueDepth)
                .skuZone(skuZone)
                .sessionSeq(sessionSeq)
                .build();
    }

    private PosTransaction mapPosToEntity(PosTransactionRequest req) {
        return PosTransaction.builder()
                .storeId(req.getStoreId())
                .transactionId(req.getTransactionId())
                .transactionTimestamp(req.getTimestamp())
                .basketValueInr(req.getBasketValueInr())
                .invoiceNumber(req.getInvoiceNumber())
                .invoiceType(req.getInvoiceType())
                .customerNumber(req.getCustomerNumber())
                .salespersonId(req.getSalespersonId())
                .build();
    }

    private String safeGet(CSVRecord row, String col, String defaultVal) {
        try {
            String val = row.get(col);
            return (val == null || val.isBlank()) ? defaultVal : val;
        } catch (IllegalArgumentException e) {
            return defaultVal;
        }
    }
}
