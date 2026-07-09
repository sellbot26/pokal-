package com.shop.service;

import com.shop.config.ShopProperties;
import com.shop.model.*;
import com.shop.repo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    public static final List<Order.Status> REVENUE_STATUSES = List.of(Order.Status.PAID, Order.Status.DELIVERED);

    private final OrderRepo orderRepo;
    private final ProductRepo productRepo;
    private final DiscountCodeRepo discountRepo;
    private final ShopUserRepo userRepo;
    private final PaymentRepo paymentRepo;
    private final ShopProperties props;
    private final BotLogService botLog;

    @Transactional
    public Order createOrder(String userId, String username, long productId, int quantity, String discountCode) {
        ShopUser user = userRepo.findById(userId)
                .orElseGet(() -> userRepo.save(new ShopUser(userId, username, null)));
        if (user.isBanned()) throw new IllegalStateException("Du bist für den Shop gesperrt.");

        Product product = productRepo.findById(productId)
                .filter(Product::isActive)
                .orElseThrow(() -> new IllegalArgumentException("Produkt nicht gefunden."));

        if (quantity < 1 || quantity > 100) throw new IllegalArgumentException("Ungültige Menge.");
        if (product.getStock() != -1 && product.getStock() < quantity)
            throw new IllegalStateException("Nicht genug Lagerbestand (verfügbar: " + product.getStock() + ").");

        int percent = 0;
        String appliedCode = null;
        if (discountCode != null && !discountCode.isBlank()) {
            // Rabattcodes gehören zum Server des Produkts — derselbe Code-Text kann auf
            // verschiedenen Servern unterschiedliche Codes sein.
            DiscountCode dc = discountRepo.findByGuildIdAndCodeIgnoreCase(product.getGuildId(), discountCode.trim())
                    .filter(DiscountCode::isUsable)
                    .orElseThrow(() -> new IllegalArgumentException("Rabattcode ungültig oder abgelaufen."));
            percent = dc.getPercent();
            appliedCode = dc.getCode();
            dc.setUses(dc.getUses() + 1);
            discountRepo.save(dc);
        }

        // Preis wird ausschließlich serverseitig berechnet
        BigDecimal total = product.getPrice()
                .multiply(BigDecimal.valueOf(quantity))
                .multiply(BigDecimal.valueOf(100L - percent))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        if (product.getStock() != -1) {
            product.setStock(product.getStock() - quantity);
            productRepo.save(product);
        }

        Order order = new Order();
        order.setUserId(userId);
        order.setUsername(username);
        order.setProductId(product.getId());
        order.setProductName(product.getName());
        order.setQuantity(quantity);
        order.setUnitPrice(product.getPrice());
        order.setDiscountCode(appliedCode);
        order.setDiscountPercent(percent);
        order.setTotalPrice(total);
        Order saved = orderRepo.save(order);
        botLog.order("🧾 New Order", "**Order:** #" + saved.getId()
                + "\n**Buyer:** <@" + userId + "> (" + username + ")"
                + "\n**Product:** " + saved.getProductName() + " x" + saved.getQuantity()
                + "\n**Total:** " + saved.getTotalPrice()
                + (percent > 0 ? " (-" + percent + "%)" : ""));
        return saved;
    }

    @Transactional
    public Order updateStatus(long orderId, Order.Status newStatus) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Bestellung nicht gefunden."));
        if (order.getStatus() == Order.Status.PENDING
                && (newStatus == Order.Status.CANCELLED || newStatus == Order.Status.EXPIRED)) {
            restoreStock(order);
        }
        if (newStatus == Order.Status.PAID && order.getPaidAt() == null) {
            order.setPaidAt(Instant.now());
        }
        order.setStatus(newStatus);
        return orderRepo.save(order);
    }

    private void restoreStock(Order order) {
        productRepo.findById(order.getProductId()).ifPresent(p -> {
            if (p.getStock() != -1) {
                p.setStock(p.getStock() + order.getQuantity());
                productRepo.save(p);
            }
        });
    }

    /** Unbezahlte Bestellungen nach Ablaufzeit automatisch verfallen lassen und Lager zurückbuchen. */
    @Scheduled(fixedDelay = 300_000)
    @Transactional
    public void expirePendingOrders() {
        Instant cutoff = Instant.now().minus(props.getOrderExpiryMinutes(), ChronoUnit.MINUTES);
        for (Order order : orderRepo.findByStatusAndCreatedAtBefore(Order.Status.PENDING, cutoff)) {
            order.setStatus(Order.Status.EXPIRED);
            restoreStock(order);
            orderRepo.save(order);
            paymentRepo.findByOrderId(order.getId()).ifPresent(p -> {
                if (p.getStatus() == Payment.Status.WAITING) {
                    p.setStatus(Payment.Status.EXPIRED);
                    paymentRepo.save(p);
                }
            });
            log.info("Bestellung #{} abgelaufen", order.getId());
        }
    }
}
