package com.shop.payment;

import com.shop.config.ShopProperties;
import com.shop.model.Order;
import com.shop.model.ShopUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Simulierter Zahlungsanbieter für Entwicklung/Tests ohne echten API-Key.
 * Zahlungen können im Admin-Dashboard oder per Webhook mit dem Header
 * "x-nowpayments-sig: &lt;IPN-Secret oder 'mock'&gt;" bestätigt werden.
 */
@Component
@RequiredArgsConstructor
public class MockPaymentProvider implements PaymentProvider {

    private final ShopProperties props;

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
        String secret = props.getPayment().getNowpayments().getIpnSecret();
        String expected = (secret == null || secret.isBlank()) ? "mock" : secret;
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }
}
