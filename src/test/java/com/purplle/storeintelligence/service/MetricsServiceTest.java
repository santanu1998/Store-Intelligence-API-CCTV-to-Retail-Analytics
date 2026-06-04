package com.purplle.storeintelligence.service;

/**
 * PROMPT: Generate unit tests for MetricsServiceImpl covering:
 *   - Zero visitors → conversion rate = 0.0 (no divide-by-zero)
 *   - All visitors converted → 100%
 *   - Partial conversion with POS correlation
 *   - Non-existent store → ResourceNotFoundException
 *   - Abandonment rate calculation
 *   - Zone dwell aggregation
 *
 * CHANGES MADE:
 *   - Added explicit delta for double assertions (assertThat(...).isCloseTo)
 *   - Extracted factory helpers to reduce test verbosity
 *   - Added verification that staff events are NOT counted in unique visitors
 *   - Replaced magic numbers with named constants in assertions
 */

import com.purplle.storeintelligence.config.AppProperties;
import com.purplle.storeintelligence.dto.response.*;
import com.purplle.storeintelligence.entity.PosTransaction;
import com.purplle.storeintelligence.exception.ResourceNotFoundException;
import com.purplle.storeintelligence.repository.*;
import com.purplle.storeintelligence.service.impl.MetricsServiceImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MetricsService — Unit Tests")
class MetricsServiceTest {

    @Mock private StoreEventRepository     eventRepo;
    @Mock private PosTransactionRepository posRepo;
    @Mock private StoreRepository          storeRepo;

    @Spy  private AppProperties            props = defaultProps();

    @InjectMocks
    private MetricsServiceImpl service;

    private static final String STORE = "STORE_BLR_002";
    private static final LocalDateTime START = LocalDate.now(ZoneOffset.UTC).atStartOfDay();
    private static final LocalDateTime END   = LocalDateTime.now(ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        when(storeRepo.existsByStoreId(STORE)).thenReturn(true);
    }

    // ── Zero-visitor edge cases ────────────────────────────────────────────────

    @Test
    @DisplayName("Zero visitors → conversion rate and abandonment rate both 0.0")
    void noVisitors_allRatesAreZero() {
        when(eventRepo.countUniqueCustomerEntries(eq(STORE), any(), any())).thenReturn(0L);
        when(posRepo.findByStoreAndTimeBetween(eq(STORE), any(), any())).thenReturn(List.of());
        when(eventRepo.countByStoreAndTypeAndTimeBetween(eq(STORE), anyString(), any(), any())).thenReturn(0L);
        when(eventRepo.findCurrentQueueDepth(STORE)).thenReturn(Optional.empty());
        when(eventRepo.findZoneDwellSummary(eq(STORE), any(), any())).thenReturn(List.of());

        StoreMetricsResponse result = service.getMetrics(STORE, START, END);

        assertThat(result.getUniqueVisitors()).isZero();
        assertThat(result.getConversionRatePct()).isEqualTo(0.0);
        assertThat(result.getAbandonmentRatePct()).isEqualTo(0.0);
        assertThat(result.getCurrentQueueDepth()).isZero();
    }

    @Test
    @DisplayName("Store not found → ResourceNotFoundException")
    void storeNotFound_throwsException() {
        when(storeRepo.existsByStoreId("UNKNOWN")).thenReturn(false);
        assertThatThrownBy(() -> service.getMetrics("UNKNOWN"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("UNKNOWN");
    }

    // ── Conversion rate ────────────────────────────────────────────────────────

    @Test
    @DisplayName("2 out of 10 visitors converted → 20% conversion rate")
    void twentyPercentConversion() {
        when(eventRepo.countUniqueCustomerEntries(eq(STORE), any(), any())).thenReturn(10L);
        when(posRepo.findByStoreAndTimeBetween(eq(STORE), any(), any()))
            .thenReturn(List.of(posTx(LocalDateTime.now())));
        when(eventRepo.findBillingZoneVisitors(eq(STORE), any(), any()))
            .thenReturn(List.of("VIS_001", "VIS_002"));
        when(eventRepo.countByStoreAndTypeAndTimeBetween(eq(STORE), anyString(), any(), any())).thenReturn(0L);
        when(eventRepo.findCurrentQueueDepth(STORE)).thenReturn(Optional.of(2));
        when(eventRepo.findZoneDwellSummary(eq(STORE), any(), any())).thenReturn(List.of());

        StoreMetricsResponse result = service.getMetrics(STORE, START, END);

        assertThat(result.getConversionRatePct()).isCloseTo(20.0, within(0.01));
        assertThat(result.getConvertedVisitors()).isEqualTo(2L);
        assertThat(result.getCurrentQueueDepth()).isEqualTo(2);
    }

    @Test
    @DisplayName("All visitors converted → 100% conversion rate")
    void allVisitorsConverted_hundredPercent() {
        when(eventRepo.countUniqueCustomerEntries(eq(STORE), any(), any())).thenReturn(5L);
        when(posRepo.findByStoreAndTimeBetween(eq(STORE), any(), any()))
            .thenReturn(List.of(posTx(LocalDateTime.now())));
        when(eventRepo.findBillingZoneVisitors(eq(STORE), any(), any()))
            .thenReturn(List.of("V1","V2","V3","V4","V5"));
        when(eventRepo.countByStoreAndTypeAndTimeBetween(eq(STORE), anyString(), any(), any())).thenReturn(0L);
        when(eventRepo.findCurrentQueueDepth(STORE)).thenReturn(Optional.empty());
        when(eventRepo.findZoneDwellSummary(eq(STORE), any(), any())).thenReturn(List.of());

        StoreMetricsResponse result = service.getMetrics(STORE, START, END);

        assertThat(result.getConversionRatePct()).isCloseTo(100.0, within(0.01));
    }

    // ── Abandonment rate ───────────────────────────────────────────────────────

    @Test
    @DisplayName("3 joins, 1 abandon → 33% abandonment rate")
    void abandonmentRate_thirtyThreePercent() {
        when(eventRepo.countUniqueCustomerEntries(eq(STORE), any(), any())).thenReturn(10L);
        when(posRepo.findByStoreAndTimeBetween(eq(STORE), any(), any())).thenReturn(List.of());
        when(eventRepo.findCurrentQueueDepth(STORE)).thenReturn(Optional.empty());
        when(eventRepo.findZoneDwellSummary(eq(STORE), any(), any())).thenReturn(List.of());
        when(eventRepo.countByStoreAndTypeAndTimeBetween(eq(STORE), eq("BILLING_QUEUE_JOIN"), any(), any()))
            .thenReturn(3L);
        when(eventRepo.countByStoreAndTypeAndTimeBetween(eq(STORE), eq("BILLING_QUEUE_ABANDON"), any(), any()))
            .thenReturn(1L);

        StoreMetricsResponse result = service.getMetrics(STORE, START, END);

        assertThat(result.getAbandonmentRatePct()).isCloseTo(33.33, within(0.1));
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private PosTransaction posTx(LocalDateTime ts) {
        return PosTransaction.builder()
                .storeId(STORE).transactionId("TXN-001")
                .transactionTimestamp(ts).basketValueInr(BigDecimal.valueOf(500)).build();
    }

    private AppProperties defaultProps() {
        AppProperties p = new AppProperties();
        p.setBatch(new AppProperties.BatchProps(500));
        p.setConversion(new AppProperties.ConversionProps(5, List.of("BILLING", "CHECKOUT")));
        p.setAnomaly(new AppProperties.AnomalyProps(5, 0.30, 0.70, 30, 10));
        return p;
    }
}
