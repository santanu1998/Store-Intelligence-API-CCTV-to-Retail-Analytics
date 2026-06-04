package com.purplle.storeintelligence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Represents a physical retail store.
 * Rows are created automatically the first time an event or POS record
 * referencing a new store_id is ingested — no manual registration required.
 */
@Entity
@Table(name = "stores")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class Store {

    /**
     * e.g. "STORE_BLR_002" or "ST1008" — matches camera / POS data.
     */
    @Id
    @Column(name = "store_id", length = 60, nullable = false)
    private String storeId;

    @Column(name = "store_name")
    private String storeName;

    @Column(name = "city", length = 100)
    private String city;

    @CreatedDate
    @Column(name = "registered_at", nullable = false, updatable = false)
    private LocalDateTime registeredAt;
}
