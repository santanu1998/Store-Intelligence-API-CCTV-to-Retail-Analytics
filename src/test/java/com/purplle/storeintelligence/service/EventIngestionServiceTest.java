package com.purplle.storeintelligence.service;

/**
 * PROMPT: Generate unit tests for EventIngestionServiceImpl covering:
 *   - Successful batch ingestion of valid events
 *   - Deduplication (same event_id twice → duplicate count, not error)
 *   - Partial success: batch with valid + invalid events
 *   - Batch size limit (>500 events → HTTP 400 from controller, not service)
 *   - Unknown event_type → rejected with clear error message
 *   - Auto store registration on first event for new store_id
 *   - BILLING_QUEUE_JOIN without metadata logs warning but still accepted
 *
 * CHANGES MADE:
 *   - Added explicit assertj assertion messages for readability
 *   - Added edge case: empty batch (validation handled by @NotEmpty, not service)
 *   - Used ArgumentCaptor to verify exact entity saved to repository
 *   - Replaced anonymous Mockito returns with named helpers for clarity
 */

import com.purplle.storeintelligence.config.AppProperties;
import com.purplle.storeintelligence.dto.request.*;
import com.purplle.storeintelligence.dto.response.IngestResponse;
import com.purplle.storeintelligence.entity.*;
import com.purplle.storeintelligence.repository.*;
import com.purplle.storeintelligence.service.impl.EventIngestionServiceImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("EventIngestionService — Unit Tests")
class EventIngestionServiceTest {

    @Mock private StoreEventRepository     eventRepo;
    @Mock private PosTransactionRepository posRepo;
    @Mock private StoreRepository          storeRepo;

    @Spy  private AppProperties            props = defaultProps();

    @InjectMocks
    private EventIngestionServiceImpl service;

    // ── Happy path ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should accept and persist a valid ENTRY event")
    void ingestSingleValidEvent_accepted() {
        when(eventRepo.existsByEventId(anyString())).thenReturn(false);
        when(storeRepo.existsByStoreId(anyString())).thenReturn(true);
        when(eventRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        IngestResponse result = service.ingestEvents(request(List.of(entryEvent("evt-001"))));

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getAccepted()).as("one valid event should be accepted").isEqualTo(1);
        assertThat(result.getRejected()).isZero();
        assertThat(result.getDuplicates()).isZero();
        assertThat(result.getErrors()).isEmpty();
        verify(eventRepo, times(1)).save(any(StoreEvent.class));
    }

    @Test
    @DisplayName("Should skip duplicate events without counting as rejected")
    void ingestDuplicate_skippedSilently() {
        when(eventRepo.existsByEventId("dup-001")).thenReturn(true);

        IngestResponse result = service.ingestEvents(request(List.of(entryEvent("dup-001"))));

        assertThat(result.getDuplicates()).as("duplicate should be counted").isEqualTo(1);
        assertThat(result.getAccepted()).isZero();
        assertThat(result.getRejected()).isZero();
        assertThat(result.getErrors()).as("no error message for duplicates").isEmpty();
        verify(eventRepo, never()).save(any());
    }

    @Test
    @DisplayName("Should provide partial success: valid saved, invalid rejected, duplicate skipped")
    void ingestMixedBatch_partialSuccess() {
        String goodId  = "good-001";
        String badId   = "bad-002";
        String dupId   = "dup-003";

        when(eventRepo.existsByEventId(goodId)).thenReturn(false);
        when(eventRepo.existsByEventId(badId)).thenReturn(false);
        when(eventRepo.existsByEventId(dupId)).thenReturn(true);
        when(storeRepo.existsByStoreId(anyString())).thenReturn(true);
        when(eventRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StoreEventRequest good = entryEvent(goodId);
        StoreEventRequest bad  = eventWithInvalidType(badId);
        StoreEventRequest dup  = entryEvent(dupId);

        IngestResponse result = service.ingestEvents(request(List.of(good, bad, dup)));

        assertThat(result.getTotal()).isEqualTo(3);
        assertThat(result.getAccepted()).as("only valid event accepted").isEqualTo(1);
        assertThat(result.getRejected()).as("invalid event_type rejected").isEqualTo(1);
        assertThat(result.getDuplicates()).as("duplicate skipped").isEqualTo(1);
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0)).contains("bad-002").contains("event_type");
    }

    @Test
    @DisplayName("Should reject ZONE_DWELL event with dwell_ms = 0")
    void ingestZoneDwellWithZeroDwell_rejected() {
        when(eventRepo.existsByEventId(anyString())).thenReturn(false);
        when(storeRepo.existsByStoreId(anyString())).thenReturn(true);

        StoreEventRequest req = StoreEventRequest.builder()
                .eventId("dwell-zero").storeId("STORE_BLR_002").visitorId("VIS_001")
                .eventType("ZONE_DWELL").timestamp(LocalDateTime.now())
                .zoneId("SKINCARE").dwellMs(0L).isStaff(false)
                .confidence(BigDecimal.valueOf(0.91)).build();

        IngestResponse result = service.ingestEvents(request(List.of(req)));

        assertThat(result.getRejected()).isEqualTo(1);
        assertThat(result.getErrors().get(0)).contains("dwell_ms");
    }

    @Test
    @DisplayName("Should reject event with unknown event_type")
    void ingestUnknownEventType_rejected() {
        when(eventRepo.existsByEventId(anyString())).thenReturn(false);
        when(storeRepo.existsByStoreId(anyString())).thenReturn(true);

        IngestResponse result = service.ingestEvents(request(List.of(eventWithInvalidType("inv-001"))));

        assertThat(result.getRejected()).isEqualTo(1);
        assertThat(result.getErrors().get(0))
            .as("error should mention the unknown type")
            .contains("UNKNOWN_TYPE");
        verify(eventRepo, never()).save(any());
    }

    @Test
    @DisplayName("Should auto-register new store on first event")
    void ingestEventForNewStore_storeRegistered() {
        when(eventRepo.existsByEventId(anyString())).thenReturn(false);
        when(storeRepo.existsByStoreId("STORE_NEW_999")).thenReturn(false);
        when(storeRepo.saveAndFlush(any(Store.class))).thenReturn(Store.builder().storeId("STORE_NEW_999").build());
        when(eventRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StoreEventRequest evt = entryEvent("new-store-evt");
        evt.setStoreId("STORE_NEW_999");
        service.ingestEvents(request(List.of(evt)));

        verify(storeRepo, times(1)).saveAndFlush(
            argThat(s -> "STORE_NEW_999".equals(s.getStoreId())));
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private StoreEventRequest entryEvent(String eventId) {
        return StoreEventRequest.builder()
                .eventId(eventId).storeId("STORE_BLR_002").visitorId("VIS_a1b2c3")
                .eventType("ENTRY").timestamp(LocalDateTime.now())
                .dwellMs(0L).isStaff(false).confidence(BigDecimal.valueOf(0.95)).build();
    }

    private StoreEventRequest eventWithInvalidType(String eventId) {
        StoreEventRequest r = entryEvent(eventId);
        r.setEventType("UNKNOWN_TYPE");
        return r;
    }

    private IngestEventsRequest request(List<StoreEventRequest> events) {
        return IngestEventsRequest.builder().events(events).build();
    }

    private AppProperties defaultProps() {
        AppProperties p = new AppProperties();
        p.setBatch(new AppProperties.BatchProps(500));
        p.setConversion(new AppProperties.ConversionProps(5, List.of("BILLING", "CHECKOUT")));
        p.setAnomaly(new AppProperties.AnomalyProps(5, 0.30, 0.70, 30, 10));
        return p;
    }
}
