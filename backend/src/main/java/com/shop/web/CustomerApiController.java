package com.shop.web;

import com.shop.model.Order;
import com.shop.model.Product;
import com.shop.model.ShopUser;
import com.shop.repo.OrderRepo;
import com.shop.repo.ProductRepo;
import com.shop.repo.ShopUserRepo;
import com.shop.service.GuildAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequiredArgsConstructor
public class CustomerApiController {

    private final ShopUserRepo userRepo;
    private final OrderRepo orderRepo;
    private final ProductRepo productRepo;
    private final GuildAccessService guildAccess;

    // ===================== Site-Admin (alle Kunden, alle Bestellungen) =====================

    @GetMapping("/api/admin/customers")
    public List<Map<String, Object>> customers() {
        return userRepo.findAll().stream()
                .map(u -> customerRow(u, orderRepo.findByUserIdOrderByCreatedAtDesc(u.getId())))
                .toList();
    }

    @PutMapping("/api/admin/customers/{id}/ban")
    public ShopUser setBanned(@PathVariable String id, @RequestBody Map<String, Boolean> body) {
        ShopUser user = userRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Kunde nicht gefunden."));
        user.setBanned(Boolean.TRUE.equals(body.get("banned")));
        return userRepo.save(user);
    }

    @GetMapping("/api/admin/customers/{id}/orders")
    public List<Order> customerOrders(@PathVariable String id) {
        return orderRepo.findByUserIdOrderByCreatedAtDesc(id);
    }

    // ===================== Tenant: nur Käufer der eigenen Produkte =====================

    /** Kunden, die bei diesem Nutzer (auf seinen eigenen Servern) gekauft haben — Umsatz/Anzahl nur dafür. */
    @GetMapping("/api/my/shop-customers")
    public List<Map<String, Object>> myCustomers(@AuthenticationPrincipal OAuth2User principal) {
        Set<String> myGuilds = guildAccess.managedGuildIds(principal.getAttribute("id"));
        Map<String, List<Order>> ordersByBuyer = new HashMap<>();
        for (Order o : orderRepo.findAll()) {
            Product p = productRepo.findById(o.getProductId()).orElse(null);
            if (p == null || !myGuilds.contains(p.getGuildId())) continue;
            ordersByBuyer.computeIfAbsent(o.getUserId(), k -> new java.util.ArrayList<>()).add(o);
        }
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        ordersByBuyer.forEach((userId, orders) -> {
            ShopUser u = userRepo.findById(userId).orElse(null);
            if (u == null) return;
            result.add(customerRow(u, orders));
        });
        result.sort((a, b) -> ((Comparable) b.get("totalSpent")).compareTo(a.get("totalSpent")));
        return result;
    }

    private Map<String, Object> customerRow(ShopUser u, List<Order> orders) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", u.getId());
        m.put("username", u.getUsername());
        m.put("avatar", u.getAvatar());
        m.put("banned", u.isBanned());
        m.put("createdAt", u.getCreatedAt());
        m.put("lastLogin", u.getLastLogin());
        m.put("orderCount", orders.size());
        m.put("totalSpent", orders.stream()
                .filter(o -> o.getStatus() == Order.Status.PAID || o.getStatus() == Order.Status.DELIVERED)
                .map(Order::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        return m;
    }
}
