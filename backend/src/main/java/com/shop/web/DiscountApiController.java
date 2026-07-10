package com.shop.web;

import com.shop.model.DiscountCode;
import com.shop.repo.DiscountCodeRepo;
import com.shop.service.GuildAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class DiscountApiController {

    public record DiscountRequest(String code, Integer percent, Integer maxUses, Integer validDays, String guildId) {}

    private final DiscountCodeRepo discountRepo;
    private final GuildAccessService guildAccess;
    private final com.shop.service.PlanService planService;
    private final com.shop.repo.ShopUserRepo userRepo;

    // ===================== Site-Admin (alle Server) =====================

    @GetMapping("/api/admin/discounts")
    public List<DiscountCode> list() {
        return discountRepo.findAll();
    }

    @PostMapping("/api/admin/discounts")
    public DiscountCode create(@RequestBody DiscountRequest req, @AuthenticationPrincipal OAuth2User principal) {
        if (req.guildId() == null || req.guildId().isBlank()) throw new IllegalArgumentException("Bitte einen Server auswählen.");
        return createDiscount(req, principal.getAttribute("id"));
    }

    @PutMapping("/api/admin/discounts/{id}/toggle")
    public DiscountCode toggle(@PathVariable long id) {
        return toggleDiscount(id, null, true);
    }

    @DeleteMapping("/api/admin/discounts/{id}")
    public Map<String, String> delete(@PathVariable long id) {
        return deleteDiscount(id, null, true);
    }

    // ===================== Tenant (nur eigene Server) =====================

    @GetMapping("/api/my/discounts")
    public List<DiscountCode> myList(@AuthenticationPrincipal OAuth2User principal) {
        String myId = principal.getAttribute("id");
        // Pro-Account-Isolation: nur selbst angelegte Codes
        return discountRepo.findAll().stream().filter(d -> myId.equals(d.getOwnerId())).toList();
    }

    @PostMapping("/api/my/discounts")
    public DiscountCode myCreate(@RequestBody DiscountRequest req, @AuthenticationPrincipal OAuth2User principal) {
        String id = principal.getAttribute("id");
        if (req.guildId() == null || req.guildId().isBlank()) throw new IllegalArgumentException("Please select a server.");
        if (!guildAccess.manages(id, req.guildId())) throw new SecurityException("You don't manage this server.");
        var actor = userRepo.findById(id).orElseThrow();
        if (!planService.isAtLeast(actor, "PRO")) {
            throw new IllegalStateException("Coupons are a Pro feature. Upgrade your plan to create discount codes.");
        }
        return createDiscount(req, id);
    }

    @PutMapping("/api/my/discounts/{id}/toggle")
    public DiscountCode myToggle(@PathVariable long id, @AuthenticationPrincipal OAuth2User principal) {
        return toggleDiscount(id, principal.getAttribute("id"), false);
    }

    @DeleteMapping("/api/my/discounts/{id}")
    public Map<String, String> myDelete(@PathVariable long id, @AuthenticationPrincipal OAuth2User principal) {
        return deleteDiscount(id, principal.getAttribute("id"), false);
    }

    // ===================== Gemeinsame Helfer =====================

    private DiscountCode createDiscount(DiscountRequest req, String ownerId) {
        if (req.code() == null || req.code().isBlank()) throw new IllegalArgumentException("Code fehlt.");
        if (req.percent() == null || req.percent() < 1 || req.percent() > 100)
            throw new IllegalArgumentException("Prozent muss zwischen 1 und 100 liegen.");
        String code = req.code().trim().toUpperCase();
        if (discountRepo.findByGuildIdAndCodeIgnoreCase(req.guildId(), code).isPresent())
            throw new IllegalArgumentException("Code existiert auf diesem Server bereits.");
        DiscountCode dc = new DiscountCode();
        dc.setGuildId(req.guildId());
        dc.setOwnerId(ownerId);
        dc.setCode(code);
        dc.setPercent(req.percent());
        if (req.maxUses() != null) dc.setMaxUses(Math.max(0, req.maxUses()));
        if (req.validDays() != null && req.validDays() > 0)
            dc.setExpiresAt(Instant.now().plus(req.validDays(), ChronoUnit.DAYS));
        return discountRepo.save(dc);
    }

    private DiscountCode toggleDiscount(long id, String tenantId, boolean siteAdmin) {
        DiscountCode dc = discountRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Code nicht gefunden."));
        requireAccess(dc, tenantId, siteAdmin);
        dc.setActive(!dc.isActive());
        return discountRepo.save(dc);
    }

    private Map<String, String> deleteDiscount(long id, String tenantId, boolean siteAdmin) {
        DiscountCode dc = discountRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Code nicht gefunden."));
        requireAccess(dc, tenantId, siteAdmin);
        discountRepo.deleteById(id);
        return Map.of("status", "gelöscht");
    }

    private void requireAccess(DiscountCode dc, String tenantId, boolean siteAdmin) {
        if (siteAdmin) return;
        // Pro-Account-Isolation: nur der Ersteller darf seinen eigenen Code ändern
        if (tenantId == null || !tenantId.equals(dc.getOwnerId())) {
            throw new SecurityException("This coupon belongs to another account.");
        }
    }
}
