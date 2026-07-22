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
 * Öffentliche Storefront-Seite pro Discord-Server (Guild) — ein gebrandeter Web-Shop
 * unter /s/{guildId}. Über das Dashboard konfigurierbar (Cover, Avatar, Bio, Farbe,
 * Social-Links, Tabs). Nur für Verkäufer ab dem Pro-Plan.
 */
@Entity
@Table(name = "storefront_configs")
@Getter
@Setter
@NoArgsConstructor
public class StorefrontConfig {

    /** Discord-Guild-ID — eine Storefront pro Server. */
    @Id
    private String guildId;

    /** Wunsch-Adresse: pokal.shop/{slug} (klein, a-z0-9-). Leer = nur /s/{guildId}. */
    @Column(unique = true)
    private String slug;

    /** Storefront öffentlich sichtbar schalten. */
    @Column(columnDefinition = "boolean default false")
    private boolean enabled = false;

    // ===== Identität =====

    private String displayName;
    private String tagline;
    /** Cover-/Banner-Bild (Upload-URL). */
    private String coverUrl;
    /** Profil-/Avatar-Bild (Upload-URL) — leer = Shop-Logo. */
    private String avatarUrl;
    /** #RRGGBB — leer = Marken-Farbe. */
    private String accentColor;
    /** Zweite Verlaufsfarbe (#RRGGBB) — leer = Standard. */
    private String accent2Color;
    /** Verifiziert-Häkchen — nur vom Site-Admin setzbar. */
    @Column(columnDefinition = "boolean default false")
    private boolean verified = false;

    // ===== Design =====

    /** Design-Template: classic | minimal | neon | luxe. */
    @Column(columnDefinition = "varchar(24) default 'classic'")
    private String template = "classic";
    /** Farbschema: dark | light. */
    @Column(columnDefinition = "varchar(12) default 'dark'")
    private String theme = "dark";
    /** Überschriften-Font: grotesk | inter | serif | mono. */
    @Column(columnDefinition = "varchar(12) default 'grotesk'")
    private String font = "grotesk";
    /** Ankündigungs-Banner oben auf der Seite (leer = kein Banner). */
    private String bannerText;
    /** Eigener Footer-Zusatztext (leer = nur "Powered by"). */
    private String footerText;
    /**
     * Erweiterte Design-Einstellungen als JSON-Objekt (pageWidth, radius, buttonStyle, coverHeight,
     * avatarShape, heroAlign, ctaText, gridCols, show*-Flags, bgColor/panelColor/textColor,
     * bgImageUrl/bgOverlay, chips[], customCss) — flexibel erweiterbar ohne neue Spalten.
     */
    @Column(columnDefinition = "text")
    private String designJson;

    // ===== Links =====

    private String discordInvite;
    private String website;
    private String twitter;
    private String youtube;
    private String tiktok;
    private String telegram;
    private String instagram;

    // ===== Tabs & Inhalte =====

    @Column(columnDefinition = "text")
    private String aboutText;
    /** FAQ-Einträge als JSON-Array [{q,a},…]. */
    @Column(columnDefinition = "text")
    private String faqJson;
    /** Eigene Zusatz-Tabs als JSON-Array [{title,content},…]. */
    @Column(columnDefinition = "text")
    private String customTabsJson;
    @Column(columnDefinition = "boolean default true")
    private boolean showReviews = true;
    @Column(columnDefinition = "boolean default true")
    private boolean showAbout = true;
    @Column(columnDefinition = "boolean default true")
    private boolean showFaq = true;
    /** Statistik-Kacheln (Produkte/Sales/Rating) anzeigen. */
    @Column(columnDefinition = "boolean default true")
    private boolean showStats = true;
    /** Zahlungsarten-Badges anzeigen (automatisch aus den konfigurierten Methoden). */
    @Column(columnDefinition = "boolean default true")
    private boolean showPayments = true;

    private Instant updatedAt = Instant.now();

    public StorefrontConfig(String guildId) {
        this.guildId = guildId;
    }
}
