package com.shop.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "license_keys")
@Getter
@Setter
@NoArgsConstructor
public class LicenseKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false, columnDefinition = "text")
    private String keyValue;

    private boolean used;
    private Long orderId;
    private Instant createdAt = Instant.now();

    public LicenseKey(Long productId, String keyValue) {
        this.productId = productId;
        this.keyValue = keyValue;
    }
}
