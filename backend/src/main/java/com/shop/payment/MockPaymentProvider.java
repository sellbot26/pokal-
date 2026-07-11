package com.shop.payment;

import com.shop.model.Order;
import com.shop.model.ShopUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Simulierter Zahlungsanbieter für Entwicklung/Tests ohne echten API-Key.
 * Zahlungen werden im Dashboard über den Simulate-Button bestätigt.
 */
@Component
@RequiredArgsConstructor
public class MockPaymentProvider implements PaymentProvider {

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public CreatedPayment create(Order order, String payCurrency, ShopUser merchant) {
        String id = "MOCK-" + UUID.randomUUID();
        String address = "mock_" + payCurrency + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        // Im Mock-Modus wird der EUR-Betrag 1:1 als Krypto-Betrag angezeigt
        return new CreatedPayment(id, address, order.getTotalPrice());
    }

    @Override
    public boolean verifyWebhook(String signature, String rawBody) {
        if (signature == null) return false;
        return MessageDigest.isEqual(
                "mock".getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }
}
