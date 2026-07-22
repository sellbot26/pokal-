package com.shop.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Ein Gewinnspiel pro Discord-Server — über das Dashboard erstellt und gestartet.
 * Der Bot postet ein Embed mit "Enter"-Button; Teilnehmer werden in {@link #entrants}
 * gesammelt (kommagetrennte User-IDs). Nach Ablauf zieht ein Scheduler die Gewinner,
 * weist optional eine Rolle zu und schickt optional eine DM.
 */
@Entity
@Table(name = "giveaways")
@Getter
@Setter
@NoArgsConstructor
public class Giveaway {

    public enum Status { RUNNING, ENDED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Discord-Guild-ID, in der das Gewinnspiel läuft. */
    private String guildId;
    /** Channel, in den das Gewinnspiel-Embed gepostet wurde. */
    private String channelId;
    /** Nachricht-ID des geposteten Embeds (zum Bearbeiten beim Auslosen). */
    private String messageId;

    // ===== Inhalt =====

    /** Der Preis — steht groß im Titel (z. B. "Discord Nitro 1 Monat"). */
    private String prize;
    @Column(columnDefinition = "text")
    private String description;
    /** #RRGGBB — leer = Marken-Farbe. */
    private String color;
    private String imageUrl;

    /** Anzahl Gewinner. */
    private int winnersCount = 1;
    /** Zeitpunkt, zu dem automatisch gezogen wird. */
    private Instant endsAt;

    // ===== Preis-Zustellung (optional) =====

    /** Rolle, die den Gewinnern beim Auslosen zugewiesen wird (leer = keine). */
    private String winnerRoleId;
    /** DM-Text an jeden Gewinner (leer = keine DM). */
    @Column(columnDefinition = "text")
    private String dmMessage;

    // ===== Zustand =====

    /** Wer das Gewinnspiel erstellt hat (Discord-User-ID). */
    private String hostId;
    /** Teilnehmer — kommagetrennte User-IDs, per Button gesammelt. */
    @Column(columnDefinition = "text")
    private String entrants;
    /** Gewinner nach dem Auslosen — kommagetrennte User-IDs. */
    @Column(columnDefinition = "text")
    private String winnerIds;

    @Enumerated(EnumType.STRING)
    private Status status = Status.RUNNING;

    private Instant createdAt = Instant.now();
    private Instant endedAt;
}
