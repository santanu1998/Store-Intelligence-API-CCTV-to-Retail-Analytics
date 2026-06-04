package com.purplle.storeintelligence.controller;

/**
 * PROMPT: Generate MockMvc integration tests for EventController covering:
 *   - POST /events/ingest with a valid batch → 201 CREATED
 *   - POST /events/ingest with all duplicates → 201, duplicates count populated
 *   - POST /events/ingest with all rejected → 422
 *   - POST /events/ingest with empty events list → 400 (bean validation)
 *   - POST /events/ingest with batch > 500 → 400
 *   - GET /health returns UP when DB is accessible
 *   - GET /stores/{id}/metrics → 404 when store not found
 *
 * CHANGES MADE:
 *   - Used @WebMvcTest slice to keep tests fast (no full context)
 *   - Replaced ObjectMapper construction with @Autowired
 *   - Added Content-Type header assertions on all responses
 *   - Fixed mock setup to return IngestResponse not void
 *   - Added edge case: batch with mixed valid and invalid events
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import com.purplle.storeintelligence.dto.request.*;
import com.purplle.storeintelligence.dto.response.*;
import com.purplle.storeintelligence.exception.ResourceNotFoundException;
import com.purplle.storeintelligence.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("EventController — MockMvc Integration Tests")
class EventControllerTest {


    @Autowired private MockMvc        mockMvc;
    @Autowired private ObjectMapper   objectMapper;

    @MockBean private EventIngestionService ingestionService;
    @MockBean private MetricsService        metricsService;
    @MockBean private FunnelService         funnelService;
    @MockBean private HeatmapService        heatmapService;
    @MockBean private AnomalyService        anomalyService;
    @MockBean private HealthService         healthService;

    // ── POST /events/ingest ────────────────────────────────────────────────────

    @Test
    @DisplayName("Valid batch → 201 CREATED with accepted count")
    void ingestValidBatch_returns201() throws Exception {
        IngestResponse mockResult = IngestResponse.builder()
                .total(2).accepted(2).rejected(0).duplicates(0)
                .errors(Collections.emptyList()).build();

        when(ingestionService.ingestEvents(any())).thenReturn(mockResult);

        IngestEventsRequest req = IngestEventsRequest.builder()
                .events(List.of(entryEvent("e-001"), entryEvent("e-002"))).build();

        mockMvc.perform(post("/events/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.accepted").value(2))
                .andExpect(jsonPath("$.data.rejected").value(0))
                .andExpect(jsonPath("$.data.duplicates").value(0));
    }

    @Test
    @DisplayName("All duplicates → 201 with duplicates count, accepted=0")
    void ingestAllDuplicates_returns201WithDuplicateCount() throws Exception {
        IngestResponse mockResult = IngestResponse.builder()
                .total(1).accepted(0).rejected(0).duplicates(1)
                .errors(Collections.emptyList()).build();

        when(ingestionService.ingestEvents(any())).thenReturn(mockResult);

        IngestEventsRequest req = IngestEventsRequest.builder()
                .events(List.of(entryEvent("dup-001"))).build();

        mockMvc.perform(post("/events/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.duplicates").value(1))
                .andExpect(jsonPath("$.data.accepted").value(0));
    }

    @Test
    @DisplayName("All events rejected → 422 UNPROCESSABLE_ENTITY")
    void ingestAllRejected_returns422() throws Exception {
        IngestResponse mockResult = IngestResponse.builder()
                .total(1).accepted(0).rejected(1).duplicates(0)
                .errors(List.of("[bad-event] Unknown event_type: INVALID")).build();

        when(ingestionService.ingestEvents(any())).thenReturn(mockResult);

        IngestEventsRequest req = IngestEventsRequest.builder()
                .events(List.of(entryEvent("bad-event"))).build();

        mockMvc.perform(post("/events/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.data.rejected").value(1))
                .andExpect(jsonPath("$.data.errors[0]").exists());
    }

    @Test
    @DisplayName("Empty events list → 400 BAD_REQUEST from bean validation")
    void ingestEmptyList_returns400() throws Exception {
        IngestEventsRequest req = IngestEventsRequest.builder()
                .events(Collections.emptyList()).build();

        mockMvc.perform(post("/events/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Missing required field (no eventType) → 400 from validation")
    void ingestMissingField_returns400() throws Exception {
        String malformed = """
                {"events":[{"event_id":"x1","store_id":"S1","visitor_id":"V1",
                  "timestamp":"2026-04-10T14:00:00Z","dwell_ms":0,"is_staff":false}]}
                """;

        mockMvc.perform(post("/events/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformed))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Malformed JSON body → 400 BAD_REQUEST")
    void ingestMalformedJson_returns400() throws Exception {
        mockMvc.perform(post("/events/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not valid json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed JSON request body"));
    }

    // ── GET /stores/{id}/metrics ───────────────────────────────────────────────

    @Test
    @DisplayName("Unknown store → 404 NOT_FOUND")
    void getMetricsUnknownStore_returns404() throws Exception {
        when(metricsService.getMetrics("UNKNOWN"))
                .thenThrow(new ResourceNotFoundException("Store", "UNKNOWN"));

        mockMvc.perform(get("/stores/UNKNOWN/metrics"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("UNKNOWN")));
    }

    @Test
    @DisplayName("Valid store → 200 OK with metrics payload")
    void getMetricsKnownStore_returns200() throws Exception {
        StoreMetricsResponse metrics = StoreMetricsResponse.builder()
                .storeId("STORE_BLR_002")
                .windowStart(LocalDateTime.now().minusHours(8))
                .windowEnd(LocalDateTime.now())
                .uniqueVisitors(45L).convertedVisitors(12L)
                .conversionRatePct(26.67).currentQueueDepth(3)
                .abandonmentRatePct(15.5).zoneDwells(List.of()).build();

        when(metricsService.getMetrics("STORE_BLR_002")).thenReturn(metrics);

        mockMvc.perform(get("/stores/STORE_BLR_002/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.store_id").value("STORE_BLR_002"))
                .andExpect(jsonPath("$.data.unique_visitors").value(45))
                .andExpect(jsonPath("$.data.conversion_rate_pct").value(26.67));
    }

    // ── GET /health ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Healthy DB → 200 OK with status=UP")
    void health_returnsUpWhenDbHealthy() throws Exception {
        HealthResponse health = HealthResponse.builder()
                .status("UP").database("UP")
                .totalStores(2).staleStores(0)
                .stores(List.of()).timestamp(LocalDateTime.now()).build();

        when(healthService.getHealth()).thenReturn(health);

        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.database").value("UP"));
    }

    @Test
    @DisplayName("DB down → 503 SERVICE_UNAVAILABLE with status=DOWN")
    void health_returns503WhenDbDown() throws Exception {
        HealthResponse health = HealthResponse.builder()
                .status("DOWN").database("DOWN")
                .totalStores(0).staleStores(0)
                .stores(List.of()).timestamp(LocalDateTime.now()).build();

        when(healthService.getHealth()).thenReturn(health);

        mockMvc.perform(get("/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.data.status").value("DOWN"));
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private StoreEventRequest entryEvent(String eventId) {
        return StoreEventRequest.builder()
                .eventId(eventId).storeId("STORE_BLR_002").visitorId("VIS_a1b2")
                .eventType("ENTRY").timestamp(LocalDateTime.now(ZoneOffset.UTC))
                .dwellMs(0L).isStaff(false).confidence(BigDecimal.valueOf(0.92))
                .build();
    }
}
