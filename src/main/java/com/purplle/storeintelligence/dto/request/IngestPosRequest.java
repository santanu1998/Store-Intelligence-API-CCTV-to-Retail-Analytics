package com.purplle.storeintelligence.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;

import java.util.List;

/**
 * Request body for {@code POST /pos/ingest}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngestPosRequest {

    @NotNull
    @NotEmpty(message = "transactions list must not be empty")
    @Size(max = 1000, message = "Maximum 1000 transactions per batch")
    @Valid
    private List<PosTransactionRequest> transactions;
}
