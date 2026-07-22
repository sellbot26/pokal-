package com.shop.web;

import com.shop.model.ShopUser;
import com.shop.model.StorefrontConfig;
import com.shop.repo.ShopUserRepo;
import com.shop.service.GuildAccessService;
import com.shop.service.PlanService;
import com.shop.service.StorefrontService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Storefront-Konfiguration pro Server (Dashboard, nur ab Pro) plus der öffentliche
 * Payload für die Web-Seite /s/{guildId} (ohne Login).
 */
@RestController
@RequiredArgsConstructor
public class StorefrontApiController {

    public record StorefrontRequest(Boolean enabled, String slug, String displayName, String tagline,
                                    String coverUrl, String avatarUrl, String accentColor, String accent2Color,
                                    String template, String theme, String font, String bannerText, String footerText,
                                    String discordInvite, String website, String twitter, String youtube,
                                    String tiktok, String telegram, String instagram,
                                    String aboutText, String faqJson, String customTabsJson, String designJson,
                                    Boolean showReviews, Boolean showAbout, Boolean showFaq,
                                    Boolean showStats, Boolean showPayments) {}

    private static final java.util.Set<String> TEMPLATES = java.util.Set.of("classic", "minimal", "neon", "luxe");
    private static final java.util.Set<String> THEMES = java.util.Set.of("dark", "light");
    private static final java.util.Set<String> FONTS = java.util.Set.of("grotesk", "inter", "serif", "mono");

    private final StorefrontService storefront;
    private final GuildAccessService guildAccess;
    private final ShopUserRepo userRepo;
    private final PlanService planService;
    private final com.fasterxml.jackson.databind.ObjectMapper mapper;

    // ===================== Öffentlich (keine Auth) =====================

    @GetMapping("/api/storefront/{key}")
    public ResponseEntity<Map<String, Object>> publicView(@PathVariable String key,
                                                          @RequestParam(required = false) Boolean preview,
                                                          @AuthenticationPrincipal OAuth2User principal) {
        // Vorschau (Dashboard-iframe): auch unveröffentlichte Storefront, aber nur für Besitzer/Admin.
        // key kann Slug ODER Guild-ID sein → echte Guild-ID über die Config auflösen.
        boolean includeUnpublished = false;
        if (Boolean.TRUE.equals(preview) && principal != null) {
            var cfg = storefront.resolve(key);
            String uid = principal.getAttribute("id");
            includeUnpublished = cfg != null
                    && (guildAccess.isSiteAdmin(uid) || guildAccess.manages(uid, cfg.getGuildId()));
        }
        Map<String, Object> payload = storefront.publicPayload(key, includeUnpublished);
        if (payload == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(payload);
    }

    // ===================== Tenant (eigener Server, ab Pro) =====================

    @GetMapping("/api/my/storefront/{guildId}")
    public Map<String, Object> myGet(@PathVariable String guildId, @AuthenticationPrincipal OAuth2User principal) {
        assertManages(principal, guildId);
        return storefront.configDto(storefront.configFor(guildId));
    }

    @PutMapping("/api/my/storefront/{guildId}")
    public Map<String, Object> myUpdate(@PathVariable String guildId, @RequestBody StorefrontRequest req,
                                        @AuthenticationPrincipal OAuth2User principal) {
        String uid = principal.getAttribute("id");
        assertManages(principal, guildId);
        ShopUser user = userRepo.findById(uid).orElseThrow();
        if (!guildAccess.isSiteAdmin(uid) && !planService.isAtLeast(user, "PRO")) {
            throw new IllegalStateException("Your own storefront is a Pro feature. Upgrade your plan to enable it.");
        }
        return storefront.configDto(save(guildId, req));
    }

    // ===================== Site-Admin =====================

    @GetMapping("/api/admin/storefront/{guildId}")
    public Map<String, Object> adminGet(@PathVariable String guildId) {
        return storefront.configDto(storefront.configFor(guildId));
    }

    @PutMapping("/api/admin/storefront/{guildId}")
    public Map<String, Object> adminUpdate(@PathVariable String guildId, @RequestBody StorefrontRequest req,
                                           @RequestParam(required = false) Boolean verified) {
        StorefrontConfig c = save(guildId, req);
        if (verified != null) { c.setVerified(verified); storefront.save(c); } // Verifiziert-Häkchen nur für Admin
        return storefront.configDto(c);
    }

    // ===================== Helfer =====================

    private void assertManages(OAuth2User principal, String guildId) {
        if (!guildAccess.manages(principal.getAttribute("id"), guildId)) {
            throw new SecurityException("You don't manage this server.");
        }
    }

    private StorefrontConfig save(String guildId, StorefrontRequest req) {
        StorefrontConfig c = storefront.configFor(guildId);
        c.setGuildId(guildId);
        if (req.slug() != null) c.setSlug(storefront.validateSlug(req.slug(), guildId)); // wirft bei belegt/reserviert
        if (req.enabled() != null) c.setEnabled(req.enabled());
        c.setDisplayName(trim(req.displayName()));
        c.setTagline(trim(req.tagline()));
        c.setCoverUrl(trim(req.coverUrl()));
        c.setAvatarUrl(trim(req.avatarUrl()));
        c.setAccentColor(trim(req.accentColor()));
        c.setAccent2Color(trim(req.accent2Color()));
        if (req.template() != null) c.setTemplate(oneOf(req.template(), TEMPLATES, "classic"));
        if (req.theme() != null) c.setTheme(oneOf(req.theme(), THEMES, "dark"));
        if (req.font() != null) c.setFont(oneOf(req.font(), FONTS, "grotesk"));
        c.setBannerText(trim(req.bannerText()));
        c.setFooterText(trim(req.footerText()));
        c.setDiscordInvite(trim(req.discordInvite()));
        c.setWebsite(trim(req.website()));
        c.setTwitter(trim(req.twitter()));
        c.setYoutube(trim(req.youtube()));
        c.setTiktok(trim(req.tiktok()));
        c.setTelegram(trim(req.telegram()));
        c.setInstagram(trim(req.instagram()));
        c.setAboutText(trim(req.aboutText()));
        c.setFaqJson(validJsonArray(req.faqJson()));
        c.setCustomTabsJson(validJsonArray(req.customTabsJson()));
        if (req.designJson() != null) c.setDesignJson(validJsonObject(req.designJson()));
        if (req.showReviews() != null) c.setShowReviews(req.showReviews());
        if (req.showAbout() != null) c.setShowAbout(req.showAbout());
        if (req.showFaq() != null) c.setShowFaq(req.showFaq());
        if (req.showStats() != null) c.setShowStats(req.showStats());
        if (req.showPayments() != null) c.setShowPayments(req.showPayments());
        return storefront.save(c);
    }

    private static String oneOf(String value, java.util.Set<String> allowed, String fallback) {
        String v = value == null ? "" : value.trim().toLowerCase();
        return allowed.contains(v) ? v : fallback;
    }

    /** Nimmt nur ein parsebares JSON-Objekt an (Design-Einstellungen, max. 20 kB) — sonst leer. */
    private String validJsonObject(String raw) {
        if (raw == null || raw.isBlank()) return "";
        if (raw.length() > 20_000)
            throw new IllegalArgumentException("Design settings are too large (max 20 kB — shorten your custom CSS).");
        try {
            var node = mapper.readTree(raw);
            return node.isObject() ? mapper.writeValueAsString(node) : "";
        } catch (Exception e) {
            return "";
        }
    }

    /** Nimmt nur ein parsebares JSON-Array an (FAQ / eigene Tabs) — sonst leer. */
    private String validJsonArray(String raw) {
        if (raw == null || raw.isBlank()) return "";
        try {
            var node = mapper.readTree(raw);
            return node.isArray() ? mapper.writeValueAsString(node) : "";
        } catch (Exception e) {
            return "";
        }
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
