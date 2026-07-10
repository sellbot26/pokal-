package com.shop.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "shop_users")
@Getter
@Setter
@NoArgsConstructor
public class ShopUser {

    /** Discord-User-ID */
    @Id
    private String id;

    private String username;
    private String avatar;
    private boolean banned;
    private Instant createdAt = Instant.now();
    private Instant lastLogin;

    /** Eigener Dashboard-Plan dieses Nutzers (FREE/PRO/ULTIMATE) — jeder Nutzer startet auf FREE. */
    private String planTier = "FREE";

    /** Ablauf des bezahlten Plans. null = unbegrenzt (Legacy/Owner). Abgelaufen → zählt als FREE. */
    private Instant planExpiresAt;

    // ===== Eigene Zahlungsmethoden dieses Verkäufers (Fallback: Site-Konfiguration) =====

    /** PayGate-Auszahlungs-Wallet (USDC · Polygon) für Kartenzahlungen. */
    private String paygateWallet;
    private String paygateEmail;
    private String paygateCheckoutProvider;

    /** Eigener NOWPayments-Account für Krypto-Zahlungen (BTC/ETH/LTC/SOL/USDT). */
    private String nowpaymentsApiKey;
    private String nowpaymentsIpnSecret;

    /** Eigene Krypto-Wallet-Adressen (frei, eine pro Zeile, z. B. "BTC: bc1..."). */
    @jakarta.persistence.Column(columnDefinition = "text")
    private String cryptoWallets;

    /** Discord-Webhook für Verkaufs-Logs dieses Verkäufers. */
    private String logWebhookUrl;

    /** Eigener Titel + Nachricht für die Liefer-DM (leer = Standard). */
    private String deliveryTitle;
    @jakarta.persistence.Column(columnDefinition = "text")
    private String deliveryMessage;

    /** Eigenes Branding (Business): Akzentfarbe (#RRGGBB) + Footer für die eigenen Liefer-DMs. */
    private String brandColor;
    private String brandFooter;

    public ShopUser(String id, String username, String avatar) {
        this.id = id;
        this.username = username;
        this.avatar = avatar;
    }
}
