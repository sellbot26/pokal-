package com.shop.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

/** Von einem Admin erzeugter Lizenzcode, der einen Dashboard-Plan freischaltet — Alternative zum Bezahlen. */
@Entity
@Table(name = "plan_licenses")
@Getter
@Setter
@NoArgsConstructor
public class PlanLicense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, length = 20)
    private String tier;

    /** Laufzeit in Tagen, die der Plan beim Einlösen freischaltet (0 = unbegrenzt). */
    @ColumnDefault("0")
    private int days;

    /** Menschlich lesbare Laufzeit, z. B. "3 months" / "1 year" — nur fürs Dashboard. */
    private String durationLabel;

    private boolean redeemed = false;
    private String redeemedBy;
    private Instant redeemedAt;
    private Instant createdAt = Instant.now();
}
