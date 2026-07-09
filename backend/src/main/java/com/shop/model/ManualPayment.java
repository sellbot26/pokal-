package com.shop.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** Manuell erfasste Zahlung (außerhalb des Krypto-Flows, z. B. PayPal/Überweisung). */
@Entity
@Table(name = "manual_payments")
@Getter
@Setter
@NoArgsConstructor
public class ManualPayment {

    public enum Method { KREDITKARTE, PAYPAL, UEBERWEISUNG, KRYPTO, SONSTIGES }

    public enum Status { PAID, PENDING, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Discord-User-ID des Erstellers — jeder Nutzer führt sein eigenes Zahlungs-Buch. */
    private String ownerId;

    @Column(nullable = false)
    private String customer;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Method method = Method.SONSTIGES;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PAID;

    @Column(columnDefinition = "text")
    private String note;

    @Column(nullable = false)
    private Instant paymentDate = Instant.now();

    private Instant createdAt = Instant.now();
}
