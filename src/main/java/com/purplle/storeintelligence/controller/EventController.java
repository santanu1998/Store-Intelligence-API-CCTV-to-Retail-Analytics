package com.purplle.storeintelligence.controller;

import com.purplle.storeintelligence.constants.ApiRoutes;
import com.purplle.storeintelligence.dto.request.*;
import com.purplle.storeintelligence.dto.response.*;
import com.purplle.storeintelligence.service.EventIngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Handles all ingest endpoints.
 *
 * <p>{@code POST /events/ingest} — receives structured events from the CCTV detection pipeline.
 * Idempotent by event_id; partial-success on validation failures.
 *
 * <p>{@code POST /pos/ingest} — JSON batch of POS transactions.
 *
 * <p>{@code POST /pos/import-csv} — CSV upload (Brigade Bangalore format).
 */
@RestController
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Event Ingestion", description = "Ingest CCTV detection events and POS transactions")
public class EventController {

    private final EventIngestionService ingestionService;

    // ── POST /events/ingest ────────────────────────────────────────────────────

    @PostMapping(ApiRoutes.EVENTS_INGEST)
    @Operation(
            summary = "Ingest a batch of detection events",
            description = "Accepts 1–500 CCTV detection events per call. "
                    + "Idempotent: duplicate event_ids are silently skipped. "
                    + "Partial success: valid events are stored even when others fail. "
                    + "Structured errors are returned for each rejected event."
    )
    public ResponseEntity<ApiResponse<IngestResponse>> ingestEvents(
            @Valid @RequestBody IngestEventsRequest request) {

        log.info("Event ingest batch: size={}", request.getEvents().size());
        IngestResponse result = ingestionService.ingestEvents(request);

        HttpStatus status = result.getRejected() == result.getTotal()
                ? HttpStatus.UNPROCESSABLE_ENTITY
                : HttpStatus.CREATED;

        return ResponseEntity.status(status)
                .body(ApiResponse.success("Events ingested", result));
    }

    // ── POST /pos/ingest ───────────────────────────────────────────────────────

    @PostMapping(ApiRoutes.POS_INGEST)
    @Operation(
            summary = "Ingest POS transactions (JSON)",
            description = "Batch ingest of POS transaction records in JSON format. "
                    + "Idempotent by transaction_id."
    )
    public ResponseEntity<ApiResponse<IngestResponse>> ingestPos(
            @Valid @RequestBody IngestPosRequest request) {

        log.info("POS JSON ingest batch: size={}", request.getTransactions().size());
        IngestResponse result = ingestionService.ingestPosTransactions(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("POS transactions ingested", result));
    }

    // ── POST /pos/import-csv ───────────────────────────────────────────────────

    @PostMapping(value = ApiRoutes.POS_IMPORT_CSV, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Import POS transactions from CSV",
            description = "Parses a Brigade Bangalore–style CSV file and ingests all rows. "
                    + "Required columns: order_id, order_date (dd-MM-yyyy), order_time (HH:mm:ss), "
                    + "store_id (or use the storeId query param), total_amount. "
                    + "Duplicate order_ids are silently skipped."
    )
    public ResponseEntity<ApiResponse<IngestResponse>> importPosCsv(
            @Parameter(description = "Store ID override (used when store_id column is missing)")
            @RequestParam(required = false) String storeId,
            @RequestPart("file") MultipartFile file) throws IOException {

        log.info("POS CSV import: filename={} size={} bytes storeId={}",
                file.getOriginalFilename(), file.getSize(), storeId);

        IngestResponse result = ingestionService.importPosCsv(storeId, file);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("CSV imported", result));
    }
}
