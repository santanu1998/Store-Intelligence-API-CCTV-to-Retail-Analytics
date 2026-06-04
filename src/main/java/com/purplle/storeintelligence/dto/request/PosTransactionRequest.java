package com.purplle.storeintelligence.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A single POS transaction as received via JSON ingest.
 * Also used internally when parsing the Brigade Bangalore CSV.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PosTransactionRequest {

    @NotBlank(message = "store_id is required")
    @JsonProperty("store_id")
    private String storeId;

    @NotBlank(message = "transaction_id is required")
    @JsonProperty("transaction_id")
    private String transactionId;

    @NotNull(message = "timestamp is required (ISO-8601 UTC)")
    @JsonProperty("timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private LocalDateTime timestamp;

    @DecimalMin(value = "0.0", message = "basket_value_inr must be >= 0")
    @JsonProperty("basket_value_inr")
    private BigDecimal basketValueInr;

    @JsonProperty("invoice_number")
    private String invoiceNumber;

    @JsonProperty("invoice_type")
    private String invoiceType;

    @JsonProperty("customer_number")
    private String customerNumber;

    @JsonProperty("salesperson_id")
    private String salespersonId;
}
