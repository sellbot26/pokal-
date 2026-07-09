package com.shop.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Im Embed-Editor gespeichertes Embed (Rohdaten als JSON vom Dashboard). */
@Entity
@Table(name = "saved_embeds")
@Getter
@Setter
@NoArgsConstructor
public class SavedEmbed {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    /** Discord-User-ID des Erstellers — Embeds sind pro Nutzer getrennt (außer Site-Admin sieht alle). */
    private String ownerId;

    @Column(nullable = false, columnDefinition = "text")
    private String json;

    private Instant updatedAt = Instant.now();
}
