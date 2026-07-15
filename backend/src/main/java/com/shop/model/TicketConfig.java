package com.shop.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Ticket-System-Konfiguration pro Discord-Server (Guild) — über das Dashboard editierbar:
 * Panel-Embed (Titel, Text, Farbe, Bild, Button), Kategorie, Support-Rollen,
 * Ticket-Limit pro Nutzer, Transcript-Einstellungen und Willkommens-Nachricht im Ticket.
 */
@Entity
@Table(name = "ticket_configs")
@Getter
@Setter
@NoArgsConstructor
public class TicketConfig {

    /** Discord-Guild-ID — eine Konfiguration pro Server. */
    @Id
    private String guildId;

    // ===== Panel-Embed (wird mit "Open Ticket"-Button in einen Channel gepostet) =====

    private String panelTitle;
    @Column(columnDefinition = "text")
    private String panelDescription;
    /** #RRGGBB — leer = Marken-Farbe. */
    private String panelColor;
    private String panelImageUrl;
    private String panelThumbnailUrl;
    private String buttonLabel;
    /** Unicode-Emoji oder Custom-Emoji (<:name:id>). */
    private String buttonEmoji;

    // ===== Verhalten =====

    /** Kategorie, unter der Ticket-Channels angelegt werden (leer = oben im Server). */
    private String categoryId;
    /** Support-Rollen-IDs, kommagetrennt — sehen alle Tickets. */
    private String supportRoleIds;
    /** Max. gleichzeitig offene Tickets pro Nutzer (0 = unbegrenzt). */
    private int maxOpenPerUser = 1;
    /** Support-Rollen beim Öffnen eines Tickets anpingen. */
    private boolean mentionSupport = false;
    /** Prefix für Ticket-Channel-Namen (z. B. "ticket" → #ticket-username). */
    private String namePrefix;

    // ===== Nachricht IM neu erstellten Ticket =====

    private String welcomeTitle;
    @Column(columnDefinition = "text")
    private String welcomeMessage;

    // ===== Transcript =====

    /** Beim Schließen ein Transcript (Textdatei) erzeugen. */
    private boolean transcriptEnabled = false;
    /** Channel, in den das Transcript gepostet wird. */
    private String transcriptChannelId;
    /** Transcript zusätzlich dem Ticket-Ersteller per DM schicken. */
    private boolean transcriptDmUser = false;

    private Instant updatedAt = Instant.now();

    public TicketConfig(String guildId) {
        this.guildId = guildId;
    }
}
