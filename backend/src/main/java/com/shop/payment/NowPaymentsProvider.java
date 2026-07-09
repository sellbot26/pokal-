package com.shop.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.config.ShopProperties;
import com.shop.model.Order;
import com.shop.model.ShopUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * NOWPayments-Anbindung (https://documenter.getpostman.com/view/7907941/S1a32n38).
 * Webhook-Signatur: HMAC-SHA512 über das alphabetisch sortierte JSON, Key = IPN-Secret.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NowPaymentsProvider implements PaymentProvider {

    private final ShopProperties props;
    private final ObjectMapper mapper;
    private final RestClient client = RestClient.create("https://api.nowpayments.io");

    @Override
    public String name() {
        return "nowpayments";
    }

    /** API-Key des Verkäufers, falls hinterlegt — sonst der Site-Key aus der .env. */
    private String resolveApiKey(ShopUser merchant) {
        if (merchant != null && merchant.getNowpaymentsApiKey() != null && !merchant.getNowpaymentsApiKey().isBlank()) {
            return merchant.getNowpaymentsApiKey().trim();
        }
        return props.getPayment().getNowpayments().getApiKey();
    }

    @Override
    public CreatedPayment create(Order order, String payCurrency, ShopUser merchant) {
        String apiKey = resolveApiKey(merchant);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Crypto payments are not set up for this seller yet. "
                    + "Connect a NOWPayments account in the dashboard (Payments).");
        }
        Map<String, Object> body = Map.of(
                "price_amount", order.getTotalPrice(),
                "price_currency", "eur",
                "pay_currency", payCurrency,
                "order_id", String.valueOf(order.getId()),
                "order_description", order.getProductName() + " x" + order.getQuantity(),
                "ipn_callback_url", props.getBaseUrl() + "/api/webhook/payment"
        );
        Map<String, Object> resp = client.post()
                .uri("/v1/payment")
                .header("x-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        if (resp == null || resp.get("pay_address") == null) {
            throw new IllegalStateException("NOWPayments hat keine Zahlungsadresse geliefert: " + resp);
        }
        return new CreatedPayment(
                String.valueOf(resp.get("payment_id")),
                String.valueOf(resp.get("pay_address")),
                new BigDecimal(String.valueOf(resp.get("pay_amount")))
        );
    }

    @Override
    public boolean verifyWebhook(String signature, String rawBody) {
        return verifyWithSecret(signature, rawBody, props.getPayment().getNowpayments().getIpnSecret());
    }

    /** Verifikation mit einem beliebigen IPN-Secret — für Verkäufer mit eigenem NOWPayments-Account. */
    public boolean verifyWithSecret(String signature, String rawBody, String secret) {
        if (signature == null || secret == null || secret.isBlank()) return false;
        try {
            Object parsed = mapper.readValue(rawBody, Object.class);
            String sortedJson = mapper.writeValueAsString(sortRecursively(parsed));
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            String expected = HexFormat.of().formatHex(mac.doFinal(sortedJson.getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    signature.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("Webhook-Signaturprüfung fehlgeschlagen", e);
            return false;
        }
    }

    private Object sortRecursively(Object value) {
        if (value instanceof Map<?, ?> map) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            map.forEach((k, v) -> sorted.put(String.valueOf(k), sortRecursively(v)));
            return sorted;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::sortRecursively).toList();
        }
        return value;
    }
}
