package com.shop.web;

import com.shop.model.LicenseKey;
import com.shop.model.Product;
import com.shop.model.ShopUser;
import com.shop.repo.LicenseKeyRepo;
import com.shop.repo.ProductRepo;
import com.shop.repo.ShopUserRepo;
import com.shop.service.GuildAccessService;
import com.shop.service.PlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequiredArgsConstructor
public class ProductApiController {

    public record ProductRequest(String name, String description, BigDecimal price, String category,
                                 String imageUrl, Integer stock, String deliveryType, String deliveryData,
                                 Boolean active, String guildId, String instructions) {}

    private final ProductRepo productRepo;
    private final LicenseKeyRepo keyRepo;
    private final PlanService planService;
    private final ShopUserRepo userRepo;
    private final GuildAccessService guildAccess;

    /** Produkte sind pro Discord-Server getrennt — guildId filtert, ohne Angabe kommt alles (Admin-Überblick). */
    @GetMapping("/api/products")
    public List<Product> list(Authentication auth, @RequestParam(required = false) String guildId) {
        // auth ist null für nicht eingeloggte Gäste (öffentlicher Web-Shop) — dann kein Admin.
        boolean admin = auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        List<Product> products = admin ? productRepo.findAll() : productRepo.findByActiveTrueOrderByCategoryAscNameAsc();
        return products.stream()
                // Interne Plan-Produkte gehören nicht in die normale Produktliste/den Discord-Shop
                .filter(p -> !PlanService.PLATFORM_CATEGORY.equals(p.getCategory()))
                .filter(p -> guildId == null || guildId.isBlank() || guildId.equals(p.getGuildId()))
                .toList();
    }

    // ===================== Site-Admin (alle Server, keine Limits) =====================

    @PostMapping("/api/admin/products")
    public Product create(@RequestBody ProductRequest req, @AuthenticationPrincipal OAuth2User principal) {
        if (req.guildId() == null || req.guildId().isBlank()) throw new IllegalArgumentException("Bitte einen Server auswählen.");
        return createProduct(req, principal.getAttribute("id"), true);
    }

    @PutMapping("/api/admin/products/{id}")
    public Product update(@PathVariable long id, @RequestBody ProductRequest req) {
        return updateProduct(id, req, null, true);
    }

    @DeleteMapping("/api/admin/products/{id}")
    public Map<String, String> delete(@PathVariable long id) {
        return deactivateProduct(id, null, true);
    }

    @PostMapping("/api/admin/products/{id}/keys")
    public Map<String, Object> addKeys(@PathVariable long id, @RequestBody Map<String, String> body) {
        return addKeysTo(id, body, null, true);
    }

    @GetMapping("/api/admin/products/{id}/keys")
    public Map<String, Object> keyCount(@PathVariable long id) {
        return Map.of("available", keyRepo.countByProductIdAndUsedFalse(id));
    }

    /** Lager-Übersicht fürs Stock-Dashboard: Bestand + verfügbare Keys pro Produkt, optional nach Server gefiltert. */
    @GetMapping("/api/admin/stock")
    public List<Map<String, Object>> stockOverview(@RequestParam(required = false) String guildId) {
        return stockRows(null, guildId, true);
    }

    /** Schnelle Lager-Anpassung aus der Stock-Ansicht. */
    @PutMapping("/api/admin/products/{id}/stock")
    public Map<String, Object> setStock(@PathVariable long id, @RequestBody Map<String, Integer> body) {
        return setStockOn(id, body, null, true);
    }

    // ===================== Tenant (nur eigene Server, eigene Plan-Limits) =====================

    /** Eigene Produkte (aktiv + inaktiv) für die "My Shop"-Verwaltungsansicht — nur die Server, die der Nutzer selbst verwaltet. */
    @GetMapping("/api/my/products")
    public List<Product> myProducts(@RequestParam(required = false) String guildId, @AuthenticationPrincipal OAuth2User principal) {
        String myId = principal.getAttribute("id");
        // Pro-Account-Isolation: nur Produkte, die DIESER Nutzer selbst angelegt hat
        return productRepo.findAll().stream()
                .filter(p -> !PlanService.PLATFORM_CATEGORY.equals(p.getCategory()))
                .filter(p -> myId.equals(p.getOwnerId()))
                .filter(p -> guildId == null || guildId.isBlank() || guildId.equals(p.getGuildId()))
                .toList();
    }

    @PostMapping("/api/my/products")
    public Product myCreate(@RequestBody ProductRequest req, @AuthenticationPrincipal OAuth2User principal) {
        String id = principal.getAttribute("id");
        if (req.guildId() == null || req.guildId().isBlank()) throw new IllegalArgumentException("Please select a server.");
        if (!guildAccess.manages(id, req.guildId())) throw new SecurityException("You don't manage this server.");
        return createProduct(req, id, false);
    }

    @PutMapping("/api/my/products/{id}")
    public Product myUpdate(@PathVariable long id, @RequestBody ProductRequest req, @AuthenticationPrincipal OAuth2User principal) {
        return updateProduct(id, req, principal.getAttribute("id"), false);
    }

    @DeleteMapping("/api/my/products/{id}")
    public Map<String, String> myDelete(@PathVariable long id, @AuthenticationPrincipal OAuth2User principal) {
        return deactivateProduct(id, principal.getAttribute("id"), false);
    }

    @PostMapping("/api/my/products/{id}/keys")
    public Map<String, Object> myAddKeys(@PathVariable long id, @RequestBody Map<String, String> body,
                                          @AuthenticationPrincipal OAuth2User principal) {
        return addKeysTo(id, body, principal.getAttribute("id"), false);
    }

    @GetMapping("/api/my/stock")
    public List<Map<String, Object>> myStock(@RequestParam(required = false) String guildId, @AuthenticationPrincipal OAuth2User principal) {
        return stockRows(principal.getAttribute("id"), guildId, false);
    }

    @PutMapping("/api/my/products/{id}/stock")
    public Map<String, Object> mySetStock(@PathVariable long id, @RequestBody Map<String, Integer> body,
                                           @AuthenticationPrincipal OAuth2User principal) {
        return setStockOn(id, body, principal.getAttribute("id"), false);
    }

    // ===================== Gemeinsame Helfer =====================

    private Product createProduct(ProductRequest req, String creatorId, boolean siteAdmin) {
        if (req.name() == null || req.name().isBlank()) throw new IllegalArgumentException("Name fehlt.");
        if (req.price() == null || req.price().signum() <= 0) throw new IllegalArgumentException("Preis muss > 0 sein.");
        if (productRepo.findByGuildIdAndNameIgnoreCase(req.guildId(), req.name().trim()).isPresent())
            throw new IllegalArgumentException("Produktname existiert auf diesem Server bereits.");
        if (!siteAdmin) {
            ShopUser actor = userRepo.findById(creatorId).orElseThrow();
            planService.assertCanAddProduct(actor, false);
            assertCanUseDeliveryType(actor, req.deliveryType());
        }
        Product p = new Product();
        p.setGuildId(req.guildId());
        // Verkäufer merken — Zahlungen für dieses Produkt laufen auf dessen Wallet/Konto
        p.setOwnerId(creatorId);
        apply(p, req);
        return productRepo.save(p);
    }

    private Product updateProduct(long id, ProductRequest req, String tenantId, boolean siteAdmin) {
        Product p = productRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Produkt nicht gefunden."));
        requireAccess(p, tenantId, siteAdmin);
        String targetGuild = req.guildId() != null && !req.guildId().isBlank() ? req.guildId() : p.getGuildId();
        if (!siteAdmin && req.guildId() != null && !req.guildId().isBlank() && !guildAccess.manages(tenantId, req.guildId())) {
            throw new SecurityException("You don't manage this server.");
        }
        if (req.name() != null && !req.name().isBlank()
                && productRepo.findByGuildIdAndNameIgnoreCase(targetGuild, req.name().trim())
                        .filter(o -> !o.getId().equals(id)).isPresent()) {
            throw new IllegalArgumentException("Produktname existiert auf diesem Server bereits.");
        }
        if (!siteAdmin && tenantId != null) {
            assertCanUseDeliveryType(userRepo.findById(tenantId).orElseThrow(), req.deliveryType());
        }
        if (req.guildId() != null && !req.guildId().isBlank()) p.setGuildId(req.guildId());
        apply(p, req);
        return productRepo.save(p);
    }

    private Map<String, String> deactivateProduct(long id, String tenantId, boolean siteAdmin) {
        Product p = productRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Produkt nicht gefunden."));
        requireAccess(p, tenantId, siteAdmin);
        p.setActive(false);
        productRepo.save(p);
        return Map.of("status", "deaktiviert");
    }

    private Map<String, Object> addKeysTo(long id, Map<String, String> body, String tenantId, boolean siteAdmin) {
        Product p = productRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Produkt nicht gefunden."));
        requireAccess(p, tenantId, siteAdmin);
        String raw = body.getOrDefault("keys", "");
        List<String> keys = raw.lines()
                .flatMap(line -> java.util.Arrays.stream(line.split(",")))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
        keys.forEach(k -> keyRepo.save(new LicenseKey(p.getId(), k)));
        long available = keyRepo.countByProductIdAndUsedFalse(p.getId());
        // Pool-basierte Lieferarten (KEY/SERIAL) haben ihren Bestand = Anzahl freier Pool-Einträge
        if (p.getDeliveryType() == Product.DeliveryType.KEY || p.getDeliveryType() == Product.DeliveryType.SERIAL) {
            p.setStock((int) available);
            productRepo.save(p);
        }
        return Map.of("added", keys.size(), "available", available);
    }

    private List<Map<String, Object>> stockRows(String tenantId, String guildId, boolean siteAdmin) {
        return productRepo.findAll().stream()
                .filter(p -> !PlanService.PLATFORM_CATEGORY.equals(p.getCategory()))
                // Site-Admin sieht alles, Tenant nur seine eigenen Produkte
                .filter(p -> siteAdmin || (tenantId != null && tenantId.equals(p.getOwnerId())))
                .filter(p -> guildId == null || guildId.isBlank() || guildId.equals(p.getGuildId()))
                .<Map<String, Object>>map(p -> {
                    java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
                    row.put("id", p.getId());
                    row.put("name", p.getName());
                    row.put("category", p.getCategory());
                    row.put("guildId", p.getGuildId());
                    row.put("stock", p.getStock());
                    row.put("deliveryType", p.getDeliveryType());
                    row.put("active", p.isActive());
                    // Keys können für jedes Produkt bevorratet werden — geliefert werden sie bei Typ KEY
                    row.put("unusedKeys", keyRepo.countByProductIdAndUsedFalse(p.getId()));
                    return row;
                }).toList();
    }

    private Map<String, Object> setStockOn(long id, Map<String, Integer> body, String tenantId, boolean siteAdmin) {
        Product p = productRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Produkt nicht gefunden."));
        requireAccess(p, tenantId, siteAdmin);
        Integer stock = body.get("stock");
        if (stock == null) throw new IllegalArgumentException("Lagerbestand fehlt.");
        p.setStock(Math.max(-1, stock));
        productRepo.save(p);
        return Map.of("id", p.getId(), "stock", p.getStock());
    }

    /** Auto-Rollen-Lieferung (ROLE) ist ein Pro-Feature — Free-Verkäufer dürfen sie nicht nutzen. */
    private void assertCanUseDeliveryType(ShopUser actor, String deliveryType) {
        if ("ROLE".equals(deliveryType) && !planService.isAtLeast(actor, "PRO")) {
            throw new IllegalStateException("Auto-role on purchase is a Pro feature. Upgrade your plan to assign Discord roles automatically.");
        }
    }

    private void requireAccess(Product p, String tenantId, boolean siteAdmin) {
        if (siteAdmin) return;
        // Pro-Account-Isolation: nur der Ersteller darf sein eigenes Produkt bearbeiten
        if (tenantId == null || !tenantId.equals(p.getOwnerId())) {
            throw new SecurityException("This product belongs to another account.");
        }
    }

    private void apply(Product p, ProductRequest req) {
        if (req.name() != null && !req.name().isBlank()) p.setName(req.name().trim());
        if (req.description() != null) p.setDescription(req.description());
        if (req.price() != null) {
            if (req.price().signum() <= 0) throw new IllegalArgumentException("Preis muss > 0 sein.");
            p.setPrice(req.price().setScale(2, java.math.RoundingMode.HALF_UP));
        }
        if (req.category() != null) p.setCategory(req.category().isBlank() ? null : req.category().trim());
        if (req.imageUrl() != null) p.setImageUrl(req.imageUrl().isBlank() ? null : req.imageUrl().trim());
        if (req.stock() != null) p.setStock(Math.max(-1, req.stock()));
        if (req.deliveryType() != null) p.setDeliveryType(Product.DeliveryType.valueOf(req.deliveryType()));
        if (req.deliveryData() != null) p.setDeliveryData(req.deliveryData());
        if (req.instructions() != null) p.setInstructions(req.instructions().isBlank() ? null : req.instructions());
        if (req.active() != null) p.setActive(req.active());
    }
}
