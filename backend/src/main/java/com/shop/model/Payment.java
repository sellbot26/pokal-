package com.shop.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class Payment {

    public enum Status { WAITING, FINISHED, EXPIRED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long orderId;

    private String provider;

    @Column(unique = true)
    private String providerPaymentId;

    /** Anzeigename der Währung, z. B. BTC oder USDT-TRC20 */
    private String payCurrency;

    @Column(precision = 24, scale = 12)
    private BigDecimal payAmount;

    private String payAddress;
    private String txHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.WAITING;

    private Instant createdAt = Instant.now();
    private Instant confirmedAt;
}
