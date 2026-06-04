package com.purplle.storeintelligence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A single POS (Point of Sale) transaction record.
 *
 * <p>Source: {@code pos_transactions.csv} / {@code Brigade_Bangalore_10_April_26.csv}
 * Schema: store_id, transaction_id, timestamp, basket_value_inr (and extended fields).
 *
 * <p>Conversion correlation: A visitor who had billing-zone activity in the
 * 5-minute window before {@code transactionTimestamp} counts as "converted" for
 * that session (see {@code MetricsService}).
 */
@Entity
@Table(
        name = "pos_transactions",
        indexes = {
                @Index(name = "idx_pos_store_time", columnList = "store_id, transaction_timestamp")
        }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class PosTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", length = 60, nullable = false)
    private String storeId;

    /**
     * Unique transaction / order identifier.
     */
    @Column(name = "transaction_id", length = 100, nullable = false, unique = true)
    private String transactionId;

    /**
     * Combined order_date + order_time in UTC.
     */
    @Column(name = "transaction_timestamp", nullable = false)
    private LocalDateTime transactionTimestamp;

    /**
     * Total basket value in INR. Maps to {@code total_amount} in the CSV.
     */
    @Column(name = "basket_value_inr", precision = 12, scale = 2)
    private BigDecimal basketValueInr;

    @Column(name = "order_date")
    private LocalDate orderDate;

    @Column(name = "invoice_number", length = 100)
    private String invoiceNumber;

    @Column(name = "invoice_type", length = 50)
    private String invoiceType;

    @Column(name = "customer_number", length = 50)
    private String customerNumber;

    @Column(name = "salesperson_id", length = 50)
    private String salespersonId;

    @CreatedDate
    @Column(name = "ingested_at", nullable = false, updatable = false)
    private LocalDateTime ingestedAt;
}
