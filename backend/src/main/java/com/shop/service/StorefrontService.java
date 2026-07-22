package com.shop.service;

import com.shop.model.Product;
import com.shop.model.Review;
import com.shop.model.StorefrontConfig;
import com.shop.repo.ProductRepo;
import com.shop.repo.ReviewRepo;
import com.shop.repo.StorefrontConfigRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Öffentliche Storefront pro Server: Konfiguration (Dashboard) plus der zusammengesetzte
 * öffentliche Payload (Config + Produkte + Reviews + Verkaufszahl) für die Web-Seite /s/{guildId}.
 */
@Service
@RequiredArgsConstructor
public class StorefrontService {

    /** Max. Reviews, die auf der Storefront gezeigt werden. */
    private static final int REVIEW_LIMIT = 30;

    private final StorefrontConfigRepo repo;
    private final ProductRepo productRepo;
    private final ReviewRepo reviewRepo;
    private final StatsService stats;
    private final SettingsService settings;
    private final com.shop.payment.PaymentService paymentService;
    private final com.shop.repo.ShopUserRepo userRepo;

    /** Pfad-Segmente, die NICHT als Storefront-Slug erlaubt sind (kollidieren mit echten Routen). */
    public static final Set<String> RESERVED_SLUGS = Set.of(
            "s", "api", "css", "js", "img", "uploads", "login", "oauth2", "logout", "error",
            "index", "dashboard", "shop", "terms", "privacy", "refund", "cookies", "storefront",
            "favicon", "admin", "assets", "static", "webhook", "app");

    /** Konfiguration für einen Server — mit Defaults, falls noch nichts gespeichert ist. */
    public StorefrontConfig configFor(String guildId) {
        return repo.findById(guildId).orElseGet(() -> new StorefrontConfig(guildId));
    }

    /** Config über Slug ODER Guild-ID finden (Slug hat Vorrang). */
    public StorefrontConfig resolve(String key) {
        if (key == null || key.isBlank()) return null;
        return repo.findBySlug(key).orElseGet(() -> repo.findById(key).orElse(null));
    }

    /**
     * Normalisiert eine Wunsch-Adresse zu einem gültigen Slug (klein, a-z0-9-, 3–32 Zeichen) und
     * prüft, dass er nicht reserviert oder von einem ANDEREN Server belegt ist.
     * @return normalisierter Slug oder null (= kein Slug)
     */
    public String validateSlug(String raw, String ownGuildId) {
        if (raw == null || raw.isBlank()) return null;
        String slug = raw.trim().toLowerCase()
                .replaceAll("\\s+", "-")
                .replaceAll("[^a-z0-9-]", "")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "");
        if (slug.isEmpty()) return null;
        if (slug.length() < 3 || slug.length() > 32)
            throw new IllegalArgumentException("The address must be 3–32 characters (letters, numbers, hyphens).");
        if (RESERVED_SLUGS.contains(slug))
            throw new IllegalArgumentException("\"" + slug + "\" is reserved — please pick another address.");
        repo.findBySlug(slug).ifPresent(other -> {
            if (!other.getGuildId().equals(ownGuildId))
                throw new IllegalArgumentException("The address \"" + slug + "\" is already taken.");
        });
        return slug;
    }

    @Transactional
    public StorefrontConfig save(StorefrontConfig config) {
        config.setUpdatedAt(Instant.now());
        return repo.save(config);
    }

    public boolean isEnabled(String guildId) {
        return repo.findById(guildId).map(StorefrontConfig::isEnabled).orElse(false);
    }

    // ===================== Öffentlicher Payload =====================

    /** Alles, was die öffentliche Storefront-Seite braucht — oder null, wenn nicht aktiviert. */
    public Map<String, Object> publicPayload(String guildId) {
        return publicPayload(guildId, false);
    }

    /**
     * Wie {@link #publicPayload(String)}, aber mit {@code includeUnpublished=true} wird auch eine
     * noch nicht veröffentlichte Storefront geliefert — für die Dashboard-Vorschau des Besitzers.
     */
    public Map<String, Object> publicPayload(String key, boolean includeUnpublished) {
        StorefrontConfig cfg = resolve(key);
        if (cfg == null) return null;
        if (!cfg.isEnabled() && !includeUnpublished) return null;
        String guildId = cfg.getGuildId();

        // Produkte (aktiv, ohne interne Plan-Produkte)
        List<Product> products = productRepo.findByGuildIdAndActiveTrueOrderByCategoryAscNameAsc(guildId).stream()
                .filter(p -> !PlanService.PLATFORM_CATEGORY.equals(p.getCategory()))
                .toList();

        List<Map<String, Object>> productDtos = products.stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("name", p.getName());
            m.put("description", nz(p.getDescription()));
            m.put("price", p.getPrice());
            m.put("category", nz(p.getCategory()));
            m.put("imageUrl", nz(p.getImageUrl()));
            m.put("inStock", p.getStock() != 0); // -1 = unbegrenzt, >0 = vorrätig, 0 = ausverkauft
            return m;
        }).toList();

        // Reviews + Schnitt
        List<Review> reviews = reviewRepo.findByGuildIdOrderByCreatedAtDesc(guildId);
        double avg = reviews.isEmpty() ? 0
                : Math.round(reviews.stream().mapToInt(Review::getStars).average().orElse(0) * 10) / 10.0;
        List<Map<String, Object>> reviewDtos = reviews.stream().limit(REVIEW_LIMIT).map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("username", nz(r.getUsername()));
            m.put("stars", r.getStars());
            m.put("text", nz(r.getText()));
            m.put("productName", nz(r.getProductName()));
            m.put("createdAt", r.getCreatedAt() == null ? null : r.getCreatedAt().toString());
            return m;
        }).toList();

        long salesDelivered = stats.getStatsFor(Set.of(guildId)).ordersTotal();

        // Zahlungsarten des Verkäufers (Owner des ersten Produkts; Fallback: Site-Konfiguration) —
        // dieselben Methoden wie im Discord-Checkout, für die Badges und den Checkout auf der Seite.
        var merchant = products.stream()
                .map(Product::getOwnerId)
                .filter(id -> id != null && !id.isBlank())
                .findFirst()
                .flatMap(userRepo::findById)
                .orElse(null);
        Map<String, Object> payments = paymentService.availableMethodsFor(merchant);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("config", configDto(cfg));
        out.put("products", productDtos);
        out.put("reviews", reviewDtos);
        out.put("rating", avg);
        out.put("reviewCount", reviews.size());
        out.put("productCount", productDtos.size());
        out.put("salesDelivered", salesDelivered);
        out.put("payments", payments);
        out.put("currency", settings.get("currency", "EUR"));
        out.put("brandName", settings.brandName());
        return out;
    }

    public Map<String, Object> configDto(StorefrontConfig c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("guildId", c.getGuildId());
        m.put("slug", nz(c.getSlug()));
        m.put("enabled", c.isEnabled());
        m.put("displayName", nz(c.getDisplayName()));
        m.put("tagline", nz(c.getTagline()));
        m.put("coverUrl", nz(c.getCoverUrl()));
        m.put("avatarUrl", nz(c.getAvatarUrl()));
        m.put("accentColor", nz(c.getAccentColor()));
        m.put("accent2Color", nz(c.getAccent2Color()));
        m.put("verified", c.isVerified());
        m.put("template", c.getTemplate() == null || c.getTemplate().isBlank() ? "classic" : c.getTemplate());
        m.put("theme", c.getTheme() == null || c.getTheme().isBlank() ? "dark" : c.getTheme());
        m.put("font", c.getFont() == null || c.getFont().isBlank() ? "grotesk" : c.getFont());
        m.put("bannerText", nz(c.getBannerText()));
        m.put("footerText", nz(c.getFooterText()));
        m.put("discordInvite", nz(c.getDiscordInvite()));
        m.put("website", nz(c.getWebsite()));
        m.put("twitter", nz(c.getTwitter()));
        m.put("youtube", nz(c.getYoutube()));
        m.put("tiktok", nz(c.getTiktok()));
        m.put("telegram", nz(c.getTelegram()));
        m.put("instagram", nz(c.getInstagram()));
        m.put("aboutText", nz(c.getAboutText()));
        m.put("faqJson", nz(c.getFaqJson()));
        m.put("customTabsJson", nz(c.getCustomTabsJson()));
        m.put("designJson", nz(c.getDesignJson()));
        m.put("showReviews", c.isShowReviews());
        m.put("showAbout", c.isShowAbout());
        m.put("showFaq", c.isShowFaq());
        m.put("showStats", c.isShowStats());
        m.put("showPayments", c.isShowPayments());
        return m;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
