package com.shop.web;

import com.shop.model.Order;
import com.shop.model.Payment;
import com.shop.model.Product;
import com.shop.payment.PaymentService;
import com.shop.repo.OrderRepo;
import com.shop.repo.PaymentRepo;
import com.shop.repo.ProductRepo;
import com.shop.service.OrderService;
import com.shop.service.QrService;
import com.shop.service.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Öffentliche Checkout-API — für Website-Käufe ohne Discord-Login.
 * Erstellt Bestellungen und Zahlungen für öffentlich verfügbare Produkte.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class CheckoutApiController {

    public record CheckoutRequest(long productId, int quantity, String discountCode, String currency) {}
    public record CheckoutResponse(long orderId, String provider, String currency, String amount, String address, String qr, String note) {}

    private final ProductRepo productRepo;
    private final OrderRepo orderRepo;
    private final PaymentRepo paymentRepo;
    private final OrderService orderService;
    private final PaymentService paymentService;
    private final QrService qrService;
    private final SettingsService settings;
    private final com.shop.repo.ShopUserRepo userRepo;

    /**
     * Erstellt eine neue Bestellung + Zahlung für ein öffentlich verfügbares Produkt.
     * Rückgabe: Order-ID + Zahlungsdetails (Link/Adresse/QR).
     */
    @PostMapping("/api/checkout/create-order")
    public CheckoutResponse createOrder(@RequestBody CheckoutRequest req) {
        Product product = productRepo.findById(req.productId)
                .filter(Product::isActive)
                .orElseThrow(() -> new IllegalArgumentException("Product not found."));

        // Zufälliger Guest-Username (oder könnte später Discord-integriert werden)
        String guestId = "guest-" + UUID.randomUUID().toString().substring(0, 8);
        String guestName = "Guest";

        Order order = orderService.createOrder(guestId, guestName, product.getId(), req.quantity, req.discountCode);
        Payment payment = paymentService.createPayment(order, req.currency);

        String qr = null;
        // QR-Code nur für Krypto-Adressen, nicht für Karte oder PayPal
        if (payment.getStatus() == Payment.Status.WAITING && qrEligible(payment.getProvider())) {
            qr = qrService.dataUrl(payment.getPayAddress());
        }

        log.info("Checkout: Order #{} created via public API (Product: {}, Currency: {})", order.getId(), product.getId(), req.currency);
        return new CheckoutResponse(order.getId(), payment.getProvider(), payment.getPayCurrency(),
                payment.getPayAmount().toPlainString(), payment.getPayAddress(), qr, payment.getPayNote());
    }

    /**
     * Gibt die aktuellen Zahlungsdetails einer Bestellung zurück.
     */
    @GetMapping("/api/checkout/order/{orderId}/payment")
    public Map<String, Object> getPayment(@PathVariable long orderId) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found."));
        Payment payment = paymentRepo.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("No payment for this order."));

        Map<String, Object> result = new HashMap<>();
        result.put("orderId", order.getId());
        result.put("status", payment.getStatus());
        result.put("provider", payment.getProvider());
        result.put("currency", payment.getPayCurrency());
        result.put("amount", payment.getPayAmount());
        result.put("address", payment.getPayAddress());
        result.put("note", payment.getPayNote());
        result.put("txHash", payment.getTxHash());

        if (payment.getStatus() == Payment.Status.WAITING && qrEligible(payment.getProvider())) {
            result.put("qr", qrService.dataUrl(payment.getPayAddress()));
        }
        return result;
    }

    /** QR-Code nur für Krypto-Adressen — nicht für Checkout-Links (Karte) oder PayPal-Adressen. */
    private boolean qrEligible(String provider) {
        return !"paygate".equals(provider) && !"stripe".equals(provider) && !"paypalff".equals(provider);
    }

    /**
     * Gibt die tatsächlich konfigurierten Zahlungsmethoden für die Website zurück.
     * Nur Methoden mit hinterlegter Site-Konfiguration werden angeboten, damit der
     * Käufer keine Buttons sieht, die beim Checkout fehlschlagen würden.
     */
    @GetMapping("/api/checkout/payment-methods")
    public Map<String, Object> paymentMethods() {
        boolean card = isSet("paygateWallet");
        boolean paypal = isSet(com.shop.payment.PayPalFriendsProvider.SITE_KEY);
        List<String> coins = com.shop.payment.CryptoWallets.SYMBOLS.stream()
                .filter(symbol -> isSet("wallet" + symbol))
                .toList();
        return Map.of("card", card, "paypal", paypal, "coins", coins);
    }

    /**
     * Zahlungsmethoden für EIN Produkt — bestimmt über dessen Verkäufer (eigene Wallets/Konten,
     * Site-Konfiguration als Fallback). Exakt dieselben Methoden wie im Discord-Checkout des
     * Verkäufers; genutzt vom Storefront-Checkout.
     */
    @GetMapping("/api/checkout/product/{productId}/payment-methods")
    public Map<String, Object> productPaymentMethods(@PathVariable long productId) {
        Product product = productRepo.findById(productId)
                .filter(Product::isActive)
                .orElseThrow(() -> new IllegalArgumentException("Product not found."));
        var merchant = product.getOwnerId() == null ? null
                : userRepo.findById(product.getOwnerId()).orElse(null);
        return paymentService.availableMethodsFor(merchant);
    }

    private boolean isSet(String key) {
        return !settings.get(key, "").isBlank();
    }
}
