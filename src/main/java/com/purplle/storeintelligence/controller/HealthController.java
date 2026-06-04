package com.purplle.storeintelligence.controller;

import com.purplle.storeintelligence.constants.AppConstants;
import com.purplle.storeintelligence.dto.response.*;
import com.purplle.storeintelligence.service.HealthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/**
 * Service health endpoint — what an on-call engineer checks first.
 *
 * <p>Always responds with a body, even when the database is unavailable.
 * HTTP 503 is returned when status = "DOWN" or "DEGRADED".
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Health", description = "Service health and feed staleness check")
public class HealthController {

    private final HealthService healthService;

    @GetMapping("/health")
    @Operation(
            summary = "Service health check",
            description = "Returns service status (UP/DOWN/DEGRADED), database status, "
                    + "and per-store last-event timestamps. "
                    + "STALE_FEED warning when no events received in > 10 minutes."
    )
    public ResponseEntity<ApiResponse<HealthResponse>> health() {
        HealthResponse result = healthService.getHealth();

        HttpStatus status = switch (result.getStatus()) {
            case AppConstants.HEALTH_DOWN, AppConstants.HEALTH_DEGRADED -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.OK;
        };

        return ResponseEntity.status(status)
                .body(ApiResponse.success("Health check complete", result));
    }
}
