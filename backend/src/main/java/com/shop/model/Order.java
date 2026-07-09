package com.shop.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
public class Order {

    public enum Status { PENDING, PAID, DELIVERED, CANCELLED, EXPIRED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String userId;
    private String username;

    @Column(nullable = false)
    private Long productId;
    /** Snapshot, damit die Historie stabil bleibt, auch wenn das Produkt geändert wird */
    private String productName;

    private int quantity = 1;

    @Column(precision = 12, scale = 2)
    private BigDecimal unitPrice;

    private String discountCode;
    private int discountPercent;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    private Instant createdAt = Instant.now();
    private Instant paidAt;
}
