package com.shop.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "discount_codes")
@Getter
@Setter
@NoArgsConstructor
public class DiscountCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String code;

    /** Discord-Server, dem dieser Code gehört — Codes sind pro Server getrennt. */
    private String guildId;

    /** Rabatt in Prozent (1-100) */
    private int percent;

    /** 0 = unbegrenzt */
    private int maxUses;
    private int uses;

    private boolean active = true;
    private Instant expiresAt;
    private Instant createdAt = Instant.now();

    public boolean isUsable() {
        return active
                && (maxUses == 0 || uses < maxUses)
                && (expiresAt == null || expiresAt.isAfter(Instant.now()));
    }
}
