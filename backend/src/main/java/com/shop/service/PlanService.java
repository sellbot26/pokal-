package com.shop.service;

import com.shop.model.PlanLicense;
import com.shop.model.Product;
import com.shop.model.ShopUser;
import com.shop.repo.PlanLicenseRepo;
import com.shop.repo.ProductRepo;
import com.shop.repo.SavedEmbedRepo;
import com.shop.repo.ShopUserRepo;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dashboard-Plan-Tiers (FREE / PRO / ULTIMATE) — PRO NUTZER, nicht global.
 * Jeder eingeloggte Discord-Nutzer startet auf FREE und kann per Kauf (Krypto/PayGate,
 * über die normale Order/Payment-Pipeline) oder per Lizenzcode auf PRO/ULTIMATE upgraden.
 * Der Site-Betreiber (ADMIN_IDS) ist von allen Limits ausgenommen.
 */
@Service
@RequiredArgsConstructor
public class PlanService {

    /** Kategorie, unter der die internen Plan-Produkte laufen — aus /shop, /buy, /product ausgeblendet. */
    public static final String PLATFORM_CATEGORY = "__platform__";

    /** Abrechnungszeitraum eines Plan-Kaufs — einmalige Freischaltung für den gewählten Zeitraum. */
    public enum Cycle {
        MONTHLY("Monthly"), YEARLY("Yearly");
        public final String label;
        Cycle(String label) { this.label = label; }
        public static Cycle from(String raw) {
            return raw != null && raw.equalsIgnoreCase("yearly") ? YEARLY : MONTHLY;
        }
    }

    public record Tier(String id, String name, BigDecimal monthlyPrice, BigDecimal yearlyPrice,
                       int productLimit, int embedLimit, String tagline, boolean popular, List<String> features) {
        /** Rückwärtskompatibler Preis-Zugriff (= Monatspreis) für Aufrufer/Serialisierung. */
        public BigDecimal price() { return monthlyPrice; }
        public BigDecimal priceFor(Cycle cycle) { return cycle == Cycle.YEARLY ? yearlyPrice : monthlyPrice; }
        public boolean isPaid() { return monthlyPrice.signum() > 0; }
    }

    private static final Map<String, Tier> TIERS = new LinkedHashMap<>() {{
        put("FREE", new Tier("FREE", "Free", BigDecimal.ZERO, BigDecimal.ZERO, 5, 3,
                "Forever free · no card required", false, List.of(
                "Up to 5 products (unlimited variants)", "All payment providers",
                "Full analytics + audit log", "Editable delivery message")));
        put("PRO", new Tier("PRO", "Pro", new BigDecimal("9.99"), new BigDecimal("99.00"), 50, 20,
                "€9.99/mo · or €99/yr (save ~17%)", true, List.of(
                "Up to 50 products", "Custom embed colors",
                "Vouch / review system", "Auto-role on purchase", "Removable branding footer")));
        put("BUSINESS", new Tier("BUSINESS", "Business", new BigDecimal("24.99"), new BigDecimal("249.00"),
                Integer.MAX_VALUE, Integer.MAX_VALUE,
                "€24.99/mo · or €249/yr (save ~17%)", false, List.of(
                "Everything in Pro", "Unlimited products & embeds",
                "Team members with role rights", "Priority support")));
    }};

    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // ohne 0/O/1/I
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ProductRepo productRepo;
    private final SavedEmbedRepo embedRepo;
    private final PlanLicenseRepo licenseRepo;
    private final ShopUserRepo userRepo;

    @PostConstruct
    public void seedPlanProducts() {
        for (Tier tier : TIERS.values()) {
            if (!tier.isPaid()) continue; // FREE braucht kein Kaufprodukt
            for (Cycle cycle : Cycle.values()) {
                String name = planProductName(tier.id(), cycle);
                if (productRepo.findByNameIgnoreCase(name).isPresent()) continue;
                Product p = new Product();
                p.setName(name);
                p.setDescription("Unlocks the " + tier.name() + " dashboard plan (" + cycle.label.toLowerCase() + ") for your own account.");
                p.setPrice(tier.priceFor(cycle));
                p.setCategory(PLATFORM_CATEGORY);
                p.setStock(-1);
                p.setDeliveryType(Product.DeliveryType.PLAN_UNLOCK);
                p.setDeliveryData(tier.id());
                p.setActive(true);
                productRepo.save(p);
            }
        }
    }

    public List<Tier> tiers() {
        return List.copyOf(TIERS.values());
    }

    public Tier tier(String id) {
        Tier t = TIERS.get(id);
        if (t == null) throw new IllegalArgumentException("Unknown plan: " + id);
        return t;
    }

    /** Aktueller Plan eines Nutzers — FREE, falls noch nie gesetzt, unbekannt oder abgelaufen. */
    public Tier tierFor(ShopUser user) {
        String id = user == null || user.getPlanTier() == null ? "FREE" : user.getPlanTier();
        if (!TIERS.containsKey(id)) return TIERS.get("FREE");
        // Abgelaufener bezahlter Plan zählt als FREE
        if (!"FREE".equals(id) && user.getPlanExpiresAt() != null && user.getPlanExpiresAt().isBefore(Instant.now())) {
            return TIERS.get("FREE");
        }
        return TIERS.get(id);
    }

    private static final List<String> TIER_RANK = List.of("FREE", "PRO", "BUSINESS");

    /** true, wenn der Nutzer (nach Ablaufprüfung) mindestens diesen Tier hat. Für Feature-Gating. */
    public boolean isAtLeast(ShopUser user, String tierId) {
        return TIER_RANK.indexOf(tierFor(user).id()) >= Math.max(0, TIER_RANK.indexOf(tierId));
    }

    /** Rückwärtskompatibel: unbegrenzte Freischaltung (z. B. Owner/Legacy). */
    @Transactional
    public void unlockForUser(ShopUser user, String tierId) {
        unlockForUser(user, tierId, 0);
    }

    /**
     * Owner-Override: setzt den Plan eines Nutzers direkt — darf auch DOWNGRADEN
     * (z. B. Plan wegnehmen = FREE). days &le; 0 = unbegrenzt (kein Ablauf).
     */
    @Transactional
    public void adminSetPlan(ShopUser user, String tierId, int days) {
        if (!TIERS.containsKey(tierId)) throw new IllegalArgumentException("Unknown plan: " + tierId);
        user.setPlanTier(tierId);
        user.setPlanExpiresAt("FREE".equals(tierId) || days <= 0
                ? null
                : Instant.now().plus(days, java.time.temporal.ChronoUnit.DAYS));
        userRepo.save(user);
    }

    /**
     * Schaltet den Plan für einen Nutzer frei (Kauf oder Lizenz) — downgraded nie.
     * @param days Laufzeit in Tagen (0 = unbegrenzt). Gleicher Tier mit Restlaufzeit → wird verlängert.
     */
    @Transactional
    public void unlockForUser(ShopUser user, String tierId, int days) {
        if (!TIERS.containsKey(tierId)) return;
        Tier target = tier(tierId);
        Tier current = tierFor(user);
        if (target.productLimit() < current.productLimit()) return; // nie downgraden
        Instant now = Instant.now();
        boolean sameTierActive = tierId.equals(current.id())
                && user.getPlanExpiresAt() != null && user.getPlanExpiresAt().isAfter(now);
        Instant base = sameTierActive ? user.getPlanExpiresAt() : now;
        user.setPlanTier(tierId);
        user.setPlanExpiresAt(days > 0 ? base.plus(days, java.time.temporal.ChronoUnit.DAYS) : null);
        userRepo.save(user);
    }

    public String planProductName(String tierId, Cycle cycle) {
        return "Pokal " + tier(tierId).name() + " Plan (" + cycle.label + ")";
    }

    /** Aktive, eigene (nicht-Plattform) Produkte über die angegebenen Server hinweg. */
    public long activeProductCount(Set<String> guildIds) {
        return productRepo.findAll().stream()
                .filter(Product::isActive)
                .filter(p -> !PLATFORM_CATEGORY.equals(p.getCategory()))
                .filter(p -> guildIds.contains(p.getGuildId()))
                .count();
    }

    /** Aktive Produkte, die genau diesem Nutzer gehören (pro-Account-Isolation). */
    public long activeProductCountForOwner(String ownerId) {
        if (ownerId == null) return 0;
        return productRepo.findAll().stream()
                .filter(Product::isActive)
                .filter(p -> !PLATFORM_CATEGORY.equals(p.getCategory()))
                .filter(p -> ownerId.equals(p.getOwnerId()))
                .count();
    }

    public void assertCanAddProduct(ShopUser actor, boolean siteAdmin) {
        if (siteAdmin) return; // Site-Betreiber ist von allen Limits ausgenommen
        Tier t = tierFor(actor);
        if (activeProductCountForOwner(actor.getId()) >= t.productLimit()) {
            throw new IllegalStateException("Your " + t.name() + " plan allows up to " + t.productLimit()
                    + " active products. Upgrade your plan to add more.");
        }
    }

    public void assertCanAddEmbed(ShopUser actor, boolean siteAdmin) {
        if (siteAdmin) return;
        Tier t = tierFor(actor);
        if (embedRepo.countByOwnerId(actor.getId()) >= t.embedLimit()) {
            throw new IllegalStateException("Your " + t.name() + " plan allows up to " + t.embedLimit()
                    + " saved embeds. Upgrade your plan to add more.");
        }
    }

    // ===== License codes (Alternative zum Bezahlen: verschenken, Reseller, Support) =====

    @Transactional
    public PlanLicense generateLicense(String tierId, int days, String durationLabel) {
        Tier tier = tier(tierId); // wirft IllegalArgumentException bei unbekanntem Tier
        if (!tier.isPaid()) throw new IllegalArgumentException("The Free plan doesn't need a license.");

        PlanLicense license = new PlanLicense();
        license.setCode(generateUniqueCode(tier.id()));
        license.setTier(tier.id());
        license.setDays(Math.max(0, days));
        license.setDurationLabel(durationLabel);
        return licenseRepo.save(license);
    }

    private String generateUniqueCode(String tierId) {
        String code;
        do {
            StringBuilder sb = new StringBuilder("POKAL-" + tierId + "-");
            for (int i = 0; i < 8; i++) sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
            code = sb.toString();
        } while (licenseRepo.findByCodeIgnoreCase(code).isPresent());
        return code;
    }

    public List<PlanLicense> listLicenses() {
        return licenseRepo.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public void revokeLicense(long id) {
        PlanLicense license = licenseRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("License not found."));
        if (license.isRedeemed()) throw new IllegalStateException("Cannot revoke an already redeemed license.");
        licenseRepo.delete(license);
    }

    /** Löst einen Lizenzcode für den angegebenen Nutzer ein (schaltet dessen eigenen Plan frei). */
    @Transactional
    public Tier redeemLicense(String code, ShopUser redeemer) {
        PlanLicense license = licenseRepo.findByCodeIgnoreCase(code == null ? "" : code.trim())
                .orElseThrow(() -> new IllegalArgumentException("Invalid license code."));
        if (license.isRedeemed()) throw new IllegalStateException("This license has already been redeemed.");
        license.setRedeemed(true);
        license.setRedeemedBy(redeemer.getUsername() + " (" + redeemer.getId() + ")");
        license.setRedeemedAt(Instant.now());
        licenseRepo.save(license);
        unlockForUser(redeemer, license.getTier(), license.getDays());
        return tierFor(redeemer);
    }
}
