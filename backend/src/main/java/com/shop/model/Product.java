package com.shop.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
public class Product {

    public enum DeliveryType {
        /** Discord-Rolle wird zugewiesen; deliveryData = Rollen-ID */
        ROLE,
        /** Lizenzkey aus dem Key-Pool; Keys werden separat eingepflegt */
        KEY,
        /** Account/Serial aus dem Pool (z. B. "email:pass"); eine Zeile pro Verkauf */
        SERIAL,
        /** deliveryData wird als Text per DM geschickt */
        TEXT,
        /** deliveryData = Datei-URL/Download-Link, wird per DM geschickt */
        FILE,
        /** Internes Platform-Produkt: schaltet einen Dashboard-Plan frei (deliveryData = Tier-Name) */
        PLAN_UNLOCK
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    /** Discord-Server, dem dieses Produkt gehört — Produkte sind pro Server getrennt. */
    private String guildId;

    /** Discord-User-ID des Verkäufers — bestimmt, auf wessen Wallet/Konto Zahlungen laufen. */
    private String ownerId;

    @Column(columnDefinition = "text")
    private String description;

    /** "How to use"-Anleitung — wird nach dem Kauf zusammen mit der Lieferung per DM geschickt. */
    @Column(columnDefinition = "text")
    private String instructions;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    private String category;
    private String imageUrl;

    /** -1 = unbegrenzt */
    private int stock = -1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryType deliveryType = DeliveryType.TEXT;

    @Column(columnDefinition = "text")
    private String deliveryData;

    private boolean active = true;
    private Instant createdAt = Instant.now();
}
