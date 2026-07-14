package com.shop.web;

import com.shop.model.Order;
import com.shop.model.Payment;
import com.shop.model.Product;
import com.shop.payment.PaymentService;
import com.shop.repo.OrderRepo;
import com.shop.repo.PaymentRepo;
import com.shop.repo.ProductRepo;
import com.shop.service.GuildAccessService;
import com.shop.service.OrderService;
import com.shop.service.QrService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequiredArgsConstructor
public class OrderApiController {

    private static final DateTimeFormatter CSV_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final OrderRepo orderRepo;
    private final PaymentRepo paymentRepo;
    private final ProductRepo productRepo;
    private final com.shop.repo.LicenseKeyRepo keyRepo;
    private final OrderService orderService;
    private final PaymentService paymentService;
    private final QrService qrService;
    private final GuildAccessService guildAccess;

    // ===== Kunden =====

    @GetMapping("/api/my/orders")
    public List<Map<String, Object>> myOrders(@AuthenticationPrincipal OAuth2User principal) {
        String userId = principal.getAttribute("id");
        return orderRepo.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(o -> {
                    Map<String, Object> m = orderWithPayment(o);
                    // Gelieferte Keys auch im Dashboard anzeigen — falls die Discord-DM nicht ankam
                    if (o.getStatus() == Order.Status.DELIVERED) {
                        List<String> keys = keyRepo.findByOrderId(o.getId()).stream()
                                .map(com.shop.model.LicenseKey::getKeyValue)
                                .toList();
                        if (!keys.isEmpty()) m.put("deliveredKeys", keys);
                    }
                    return m;
                })
                .toList();
    }

    @GetMapping("/api/my/orders/{id}/payment")
    public Map<String, Object> myPayment(@PathVariable long id, @AuthenticationPrincipal OAuth2User principal) {
        String userId = principal.getAttribute("id");
        Order order = orderRepo.findById(id)
                .filter(o -> o.getUserId().equals(userId))
                .orElseThrow(() -> new IllegalArgumentException("Bestellung nicht gefunden."));
        Payment payment = paymentRepo.findByOrderId(order.getId())
                .orElseThrow(() -> new IllegalArgumentException("Keine Zahlung zu dieser Bestellung."));
        Map<String, Object> result = new HashMap<>();
        result.put("orderId", order.getId());
        result.put("status", payment.getStatus());
        result.put("provider", payment.getProvider());
        result.put("currency", payment.getPayCurrency());
        result.put("amount", payment.getPayAmount());
        result.put("address", payment.getPayAddress());
        result.put("note", payment.getPayNote());
        result.put("txHash", payment.getTxHash());
        // Bei PayGate ist die "Adresse" ein Checkout-Link, bei PayPal eine E-Mail — QR nur für Krypto
        if (payment.getStatus() == Payment.Status.WAITING
                && !"paygate".equals(payment.getProvider())
                && !"paypalff".equals(payment.getProvider())) {
            result.put("qr", qrService.dataUrl(payment.getPayAddress()));
        }
        return result;
    }

    // ===== Admin =====

    @GetMapping("/api/admin/orders")
    public List<Map<String, Object>> allOrders(@RequestParam(required = false) String status) {
        List<Order> orders = (status == null || status.isBlank())
                ? orderRepo.findAllByOrderByCreatedAtDesc()
                : orderRepo.findByStatusOrderByCreatedAtDesc(Order.Status.valueOf(status));
        return orders.stream().map(this::orderWithPayment).toList();
    }

    @PutMapping("/api/admin/orders/{id}/status")
    public Order updateStatus(@PathVariable long id, @RequestBody Map<String, String> body) {
        return orderService.updateStatus(id, Order.Status.valueOf(body.get("status")));
    }

    /** Nur im Mock-Modus: Zahlung als bestätigt simulieren (löst die Lieferung aus). */
    @PostMapping("/api/admin/orders/{id}/simulate-payment")
    public Map<String, String> simulate(@PathVariable long id) {
        paymentService.simulatePayment(id);
        return Map.of("status", "ok");
    }

    // ===== Tenant: Bestellungen für die eigenen Produkte (nicht die eigenen Käufe!) =====

    @GetMapping("/api/my/shop-orders")
    public List<Map<String, Object>> myShopOrders(@RequestParam(required = false) String status,
                                                    @AuthenticationPrincipal OAuth2User principal) {
        String myId = principal.getAttribute("id");
        List<Order> orders = (status == null || status.isBlank())
                ? orderRepo.findAllByOrderByCreatedAtDesc()
                : orderRepo.findByStatusOrderByCreatedAtDesc(Order.Status.valueOf(status));
        // Pro-Account-Isolation: nur Bestellungen für die EIGENEN Produkte
        return orders.stream()
                .filter(o -> {
                    Product p = productRepo.findById(o.getProductId()).orElse(null);
                    return p != null && myId.equals(p.getOwnerId());
                })
                .map(this::orderWithPayment)
                .toList();
    }

    @PutMapping("/api/my/shop-orders/{id}/status")
    public Order myUpdateStatus(@PathVariable long id, @RequestBody Map<String, String> body,
                                 @AuthenticationPrincipal OAuth2User principal) {
        requireOwnOrder(id, principal.getAttribute("id"));
        return orderService.updateStatus(id, Order.Status.valueOf(body.get("status")));
    }

    @PostMapping("/api/my/shop-orders/{id}/simulate-payment")
    public Map<String, String> mySimulate(@PathVariable long id, @AuthenticationPrincipal OAuth2User principal) {
        requireOwnOrder(id, principal.getAttribute("id"));
        paymentService.simulatePayment(id);
        return Map.of("status", "ok");
    }

    /** Direct-Krypto: Verkäufer bestätigt den Zahlungseingang auf seiner Wallet → Lieferung läuft. */
    @PostMapping("/api/my/shop-orders/{id}/confirm-payment")
    public Map<String, String> myConfirm(@PathVariable long id, @AuthenticationPrincipal OAuth2User principal) {
        requireOwnOrder(id, principal.getAttribute("id"));
        paymentService.confirmDirectPayment(id);
        return Map.of("status", "confirmed");
    }

    @PostMapping("/api/admin/orders/{id}/confirm-payment")
    public Map<String, String> adminConfirm(@PathVariable long id) {
        paymentService.confirmDirectPayment(id);
        return Map.of("status", "confirmed");
    }

    private void requireOwnOrder(long orderId, String tenantId) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found."));
        Product product = productRepo.findById(order.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found."));
        if (tenantId == null || !tenantId.equals(product.getOwnerId())) {
            throw new SecurityException("This order belongs to another account.");
        }
    }

    @GetMapping("/api/admin/export/orders.csv")
    public ResponseEntity<String> exportCsv() {
        StringBuilder sb = new StringBuilder("id;datum;kunde;discord_id;produkt;menge;einzelpreis;rabatt_code;rabatt_prozent;gesamt_eur;status;bezahlt_am;waehrung;krypto_betrag;adresse;tx_hash\n");
        for (Order o : orderRepo.findAllByOrderByCreatedAtDesc()) {
            Payment p = paymentRepo.findByOrderId(o.getId()).orElse(null);
            sb.append(o.getId()).append(';')
                    .append(CSV_DATE.format(o.getCreatedAt())).append(';')
                    .append(csv(o.getUsername())).append(';')
                    .append(o.getUserId()).append(';')
                    .append(csv(o.getProductName())).append(';')
                    .append(o.getQuantity()).append(';')
                    .append(o.getUnitPrice()).append(';')
                    .append(csv(o.getDiscountCode())).append(';')
                    .append(o.getDiscountPercent()).append(';')
                    .append(o.getTotalPrice()).append(';')
                    .append(o.getStatus()).append(';')
                    .append(o.getPaidAt() != null ? CSV_DATE.format(o.getPaidAt()) : "").append(';')
                    .append(p != null ? csv(p.getPayCurrency()) : "").append(';')
                    .append(p != null && p.getPayAmount() != null ? p.getPayAmount().toPlainString() : "").append(';')
                    .append(p != null ? csv(p.getPayAddress()) : "").append(';')
                    .append(p != null ? csv(p.getTxHash()) : "")
                    .append('\n');
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=orders.csv")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(sb.toString());
    }

    private Map<String, Object> orderWithPayment(Order o) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", o.getId());
        m.put("userId", o.getUserId());
        m.put("username", o.getUsername());
        m.put("productName", o.getProductName());
        m.put("quantity", o.getQuantity());
        m.put("unitPrice", o.getUnitPrice());
        m.put("discountCode", o.getDiscountCode());
        m.put("discountPercent", o.getDiscountPercent());
        m.put("totalPrice", o.getTotalPrice());
        m.put("status", o.getStatus());
        m.put("createdAt", o.getCreatedAt());
        m.put("paidAt", o.getPaidAt());
        paymentRepo.findByOrderId(o.getId()).ifPresent(p -> {
            m.put("payCurrency", p.getPayCurrency());
            m.put("payAmount", p.getPayAmount());
            m.put("payAddress", p.getPayAddress());
            m.put("txHash", p.getTxHash());
            m.put("paymentStatus", p.getStatus());
            m.put("paymentProvider", p.getProvider());
        });
        return m;
    }

    private String csv(String value) {
        if (value == null) return "";
        return value.replace(';', ',').replace('\n', ' ').replace('\r', ' ');
    }
}
