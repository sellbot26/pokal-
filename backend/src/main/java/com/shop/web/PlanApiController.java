package com.shop.web;

import com.shop.model.Order;
import com.shop.model.Payment;
import com.shop.model.PlanLicense;
import com.shop.model.ShopUser;
import com.shop.payment.PaymentService;
import com.shop.repo.ProductRepo;
import com.shop.repo.SavedEmbedRepo;
import com.shop.repo.ShopUserRepo;
import com.shop.service.GuildAccessService;
import com.shop.service.OrderService;
import com.shop.service.PlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Dashboard-Plan-Tiers und Kauf — PRO NUTZER. Jeder eingeloggte Discord-Nutzer hat
 * seinen eigenen Plan (Standard: Free) und kann per Kauf (dieselbe Order/Payment-Pipeline
 * wie ein normaler Shop-Kauf) oder per Lizenzcode upgraden. Der Site-Betreiber ist
 * von allen Limits ausgenommen.
 */
@RestController
@RequiredArgsConstructor
public class PlanApiController {

    public record PurchaseRequest(String tier, String cycle, String currency) {}
    public record LicenseRequest(String tier, String cycle, Integer count) {}
    public record RedeemRequest(String code) {}

    private final PlanService planService;
    private final ProductRepo productRepo;
    private final OrderService orderService;
    private final PaymentService paymentService;
    private final SavedEmbedRepo embedRepo;
    private final ShopUserRepo userRepo;
    private final GuildAccessService guildAccess;

    /** Öffentliche Preisliste — für die Landingpage, kein Login nötig. */
    @GetMapping("/api/plans")
    public List<PlanService.Tier> plans() {
        return planService.tiers();
    }

    /** Aktueller Plan + Verbrauch des eingeloggten Nutzers. */
    @GetMapping("/api/my/plan")
    public Map<String, Object> myPlan(@AuthenticationPrincipal OAuth2User principal) {
        String id = principal.getAttribute("id");
        ShopUser user = userRepo.findById(id).orElseThrow();
        boolean siteAdmin = guildAccess.isSiteAdmin(id);
        PlanService.Tier tier = planService.tierFor(user);
        long productsUsed = siteAdmin
                ? productRepo.findAll().stream().filter(p -> !PlanService.PLATFORM_CATEGORY.equals(p.getCategory())).count()
                : planService.activeProductCountForOwner(id);
        long embedsUsed = siteAdmin ? embedRepo.count() : embedRepo.countByOwnerId(id);
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("tier", tier);
        result.put("productsUsed", productsUsed);
        result.put("embedsUsed", embedsUsed);
        result.put("isSiteAdmin", siteAdmin);
        if (user.getPlanExpiresAt() != null && !"FREE".equals(tier.id())) {
            result.put("expiresAt", user.getPlanExpiresAt().toString());
        }
        return result;
    }

    /** Startet den Kauf eines Plans für den eingeloggten Nutzer — liefert eine orderId, die wie eine normale Bestellung bezahlt wird. */
    @PostMapping("/api/my/plan/purchase")
    public Map<String, Object> purchase(@RequestBody PurchaseRequest req, @AuthenticationPrincipal OAuth2User principal) {
        PlanService.Tier tier = planService.tier(req.tier());
        if (!tier.isPaid()) throw new IllegalArgumentException("The Free plan doesn't need a purchase.");

        PlanService.Cycle cycle = PlanService.Cycle.from(req.cycle());
        var product = productRepo.findByNameIgnoreCase(planService.planProductName(tier.id(), cycle))
                .orElseThrow(() -> new IllegalStateException("Plan product not seeded yet — restart the server."));

        String userId = principal.getAttribute("id");
        String username = principal.getAttribute("username");
        Order order = orderService.createOrder(userId, username, product.getId(), 1, null);

        // Plan-Käufe laufen IMMER über PayGate (Karte) auf die feste Plattform-Wallet — kein Krypto.
        Payment payment = paymentService.createPlanPayment(order);

        return Map.of("orderId", order.getId(), "provider", payment.getProvider());
    }

    /** Löst einen Lizenzcode ein — schaltet den Plan des eingeloggten Nutzers frei. */
    @PostMapping("/api/my/plan/redeem")
    public Map<String, Object> redeem(@RequestBody RedeemRequest req, @AuthenticationPrincipal OAuth2User principal) {
        String id = principal.getAttribute("id");
        ShopUser user = userRepo.findById(id).orElseThrow();
        PlanService.Tier tier = planService.redeemLicense(req.code(), user);
        return Map.of("tier", tier);
    }

    // ===== Lizenzcodes generieren/verwalten: Site-Admin only (verschenken, Reseller, Support) =====

    @PostMapping("/api/admin/plan/licenses")
    public PlanLicense generateLicense(@RequestBody LicenseRequest req) {
        int count = req.count() == null ? 1 : Math.max(1, Math.min(12, req.count()));
        boolean yearly = "yearly".equalsIgnoreCase(req.cycle());
        int days = yearly ? count * 365 : count * 30;
        String label = count + " " + (yearly ? "year" : "month") + (count > 1 ? "s" : "");
        return planService.generateLicense(req.tier(), days, label);
    }

    @GetMapping("/api/admin/plan/licenses")
    public List<PlanLicense> licenses() {
        return planService.listLicenses();
    }

    @DeleteMapping("/api/admin/plan/licenses/{id}")
    public Map<String, String> revokeLicense(@PathVariable long id) {
        planService.revokeLicense(id);
        return Map.of("status", "revoked");
    }
}
