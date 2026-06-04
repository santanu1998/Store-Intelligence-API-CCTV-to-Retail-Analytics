package com.purplle.storeintelligence.config;

import lombok.*;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Strongly-typed wrapper for all {@code app.*} properties defined in application.yml.
 * Inject this bean wherever configurable thresholds are needed.
 */
@ConfigurationProperties(prefix = "app")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppProperties {

    private BatchProps batch = new BatchProps();
    private ConversionProps conversion = new ConversionProps();
    private AnomalyProps anomaly = new AnomalyProps();
    private StoreProps store = new StoreProps();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchProps {
        private int maxIngestSize = 500;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConversionProps {
        private int billingWindowMinutes = 5;
        private List<String> billingZoneKeywords =
                List.of("BILLING", "CHECKOUT", "CASHIER", "CASH", "COUNTER");
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnomalyProps {
        private int queueSpikeThreshold = 5;
        private double abandonmentWarnThreshold = 0.30;
        private double conversionDropFactor = 0.70;
        private int deadZoneMinutes = 30;
        private int staleFeedMinutes = 10;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StoreProps {
        private String defaultId = "STORE_BLR_002";
    }
}
