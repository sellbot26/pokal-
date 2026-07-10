package com.shop.service;

import com.shop.config.ShopProperties;
import com.shop.model.ShopSetting;
import com.shop.repo.ShopSettingRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shop-Einstellungen aus der Datenbank, über das Dashboard editierbar.
 * Fallback auf die .env-Werte (ShopProperties), solange nichts gespeichert wurde.
 */
@Service
@RequiredArgsConstructor
public class SettingsService {

    /** Whitelist — nur diese Keys dürfen über die API gesetzt werden. */
    public static final Set<String> ALLOWED_KEYS = Set.of(
            "shopName", "brandColor", "logoUrl", "bannerUrl", "description",
            "currency", "discordInvite", "supportServer", "footerText", "socialLinks",
            "maintenance", "monthlyGoal", "productOrder",
            "walletBTC", "walletETH", "walletLTC", "walletSOL", "walletUSDT", "walletUSDC",
            "paygateWallet", "paygateCheckoutProvider", "paygateEmail",
            // Discord-Channels + Logging, direkt über das Dashboard steuerbar
            "logChannelId", "reviewChannelId", "logSales", "logErrors", "logOrders", "siteLogWebhookUrl",
            // Stripe (Karte) — Site-weit
            "stripeSecretKey", "stripeWebhookSecret"
    );

    /** Teilmenge, die ohne Admin-Rechte sichtbar ist (Branding für Login-Seite etc.). */
    public static final Set<String> PUBLIC_KEYS = Set.of(
            "shopName", "brandColor", "logoUrl", "bannerUrl", "description",
            "currency", "discordInvite", "footerText", "socialLinks", "maintenance"
    );

    private final ShopSettingRepo repo;
    private final ShopProperties props;

    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private volatile boolean loaded = false;

    private void ensureLoaded() {
        if (loaded) return;
        synchronized (this) {
            if (loaded) return;
            repo.findAll().forEach(s -> {
                if (s.getValue() != null) cache.put(s.getKey(), s.getValue());
            });
            loaded = true;
        }
    }

    public String get(String key, String fallback) {
        ensureLoaded();
        String value = cache.get(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    @Transactional
    public void set(String key, String value) {
        if (!ALLOWED_KEYS.contains(key)) throw new IllegalArgumentException("Unbekannte Einstellung: " + key);
        setInternal(key, value);
    }

    /**
     * Serverseitiger Write ohne Whitelist-Prüfung — NUR für interne Zustände wie den
     * Plan-Tier, der niemals direkt über die Settings-API gesetzt werden darf.
     */
    @Transactional
    public void setInternal(String key, String value) {
        ensureLoaded();
        repo.save(new ShopSetting(key, value));
        if (value == null) cache.remove(key); else cache.put(key, value);
    }

    /** Alle Einstellungen inkl. Defaults — fürs Admin-Dashboard. */
    public Map<String, String> all() {
        ensureLoaded();
        Map<String, String> result = new LinkedHashMap<>();
        result.put("shopName", brandName());
        result.put("brandColor", get("brandColor", props.getBrandColor()));
        result.put("currency", get("currency", "EUR"));
        result.put("maintenance", get("maintenance", "false"));
        result.put("monthlyGoal", get("monthlyGoal", "1000"));
        for (String key : ALLOWED_KEYS) result.putIfAbsent(key, get(key, ""));
        return result;
    }

    /** Öffentliche Teilmenge — für Login-/Shopseite ohne Login. */
    public Map<String, String> publicSettings() {
        Map<String, String> all = all();
        Map<String, String> result = new LinkedHashMap<>();
        PUBLIC_KEYS.forEach(key -> result.put(key, all.getOrDefault(key, "")));
        return result;
    }

    // ===== Bequeme Zugriffe für Bot & Services =====

    public String brandName() {
        return get("shopName", props.getBrandName());
    }

    public Color brandColor() {
        try {
            return Color.decode(get("brandColor", props.getBrandColor()));
        } catch (Exception e) {
            return new Color(0xE3A63A);
        }
    }

    public String currencySymbol() {
        return "USD".equalsIgnoreCase(get("currency", "EUR")) ? "$" : "€";
    }

    public boolean isMaintenance() {
        return Boolean.parseBoolean(get("maintenance", "false"));
    }

    /** Discord-Log-Channel — Dashboard-Wert, sonst .env-Fallback. */
    public String logChannelId() {
        return get("logChannelId", props.getDiscord().getLogChannelId());
    }

    /** Öffentlicher Bewertungs-Channel — hier postet der Bot neue /review-Bewertungen. */
    public String reviewChannelId() {
        return get("reviewChannelId", null);
    }

    /** Verkaufs-Logs in den Log-Channel schreiben (Standard: an). */
    public boolean logSales() {
        return Boolean.parseBoolean(get("logSales", "true"));
    }

    /** Fehler-/System-Logs in den Log-Channel schreiben (Standard: an). */
    public boolean logErrors() {
        return Boolean.parseBoolean(get("logErrors", "true"));
    }

    /** Logs für neue (noch unbezahlte) Bestellungen schreiben (Standard: an). */
    public boolean logOrders() {
        return Boolean.parseBoolean(get("logOrders", "true"));
    }

    /** Site-weiter Discord-Webhook, an den ALLE Logs zusätzlich geschickt werden. */
    public String siteLogWebhookUrl() {
        return get("siteLogWebhookUrl", null);
    }

    /** Stripe Secret Key (sk_...) — Karte über Stripe, wenn gesetzt. */
    public String stripeSecretKey() {
        return get("stripeSecretKey", props.getPayment().getStripe().getSecretKey());
    }

    /** Stripe Webhook Signing Secret (whsec_...). */
    public String stripeWebhookSecret() {
        return get("stripeWebhookSecret", props.getPayment().getStripe().getWebhookSecret());
    }
}
