package com.purplle.storeintelligence.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.purplle.storeintelligence.enums.AnomalySeverity;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A single detected anomaly with severity and a suggested remediation action.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnomalyItem {

    /**
     * e.g. BILLING_QUEUE_SPIKE, CONVERSION_DROP, DEAD_ZONE, STALE_FEED
     */
    private String type;

    private AnomalySeverity severity;

    /**
     * Human-readable description of the anomaly condition.
     */
    private String description;

    /**
     * Actionable suggestion for the store manager / on-call engineer.
     */
    @JsonProperty("suggested_action")
    private String suggestedAction;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    @JsonProperty("detected_at")
    private LocalDateTime detectedAt;

    /**
     * Optional additional context (e.g. the affected zone ID).
     */
    private String context;
}
