package com.shop.payment;

import com.shop.config.ShopProperties;
import com.shop.model.Order;
import com.shop.model.ShopUser;
import com.shop.service.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/**
 * Stripe Checkout (Karte / Apple Pay / Google Pay). Nutzt die Stripe-REST-API direkt
 * (kein SDK-Dependency): erstellt eine Checkout-Session, der Kunde zahlt auf der von
 * Stripe gehosteten Seite, Bestätigung kommt per signiertem Webhook.
 *
 * Aktiv, sobald ein Secret Key hinterlegt ist (Dashboard → Settings oder STRIPE_SECRET_KEY).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StripeProvider implements PaymentProvider {

    private final ShopProperties props;
    private final SettingsService settings;
    private final RestClient client = RestClient.create("https://api.stripe.com");

    @Override
    public String name() {
        return "stripe";
    }

    /** Ist Stripe einsatzbereit (Secret Key vorhanden)? */
    public boolean isConfigured() {
        String key = settings.stripeSecretKey();
        return key != null && !key.isBlank();
    }

    @Override
    public CreatedPayment create(Order order, String payCurrency, ShopUser merchant) {
        String secretKey = settings.stripeSecretKey();
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("Stripe is not set up yet. Add your Stripe secret key in Settings → Payments.");
        }
        String currency = settings.get("currency", "EUR").toLowerCase();
        // Stripe erwartet den Betrag in der kleinsten Einheit (Cent)
        long amountMinor = order.getTotalPrice()
                .multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact();
        String base = props.getBaseUrl();

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("mode", "payment");
        form.add("success_url", base + "/dashboard.html?paid=" + order.getId());
        form.add("cancel_url", base + "/dashboard.html?cancelled=" + order.getId());
        form.add("client_reference_id", String.valueOf(order.getId()));
        form.add("metadata[order_id]", String.valueOf(order.getId()));
        form.add("line_items[0][quantity]", "1");
        form.add("line_items[0][price_data][currency]", currency);
        form.add("line_items[0][price_data][unit_amount]", String.valueOf(amountMinor));
        form.add("line_items[0][price_data][product_data][name]",
                order.getProductName() + " x" + order.getQuantity());

        Map<String, Object> resp = client.post()
                .uri("/v1/checkout/sessions")
                .header("Authorization", "Bearer " + secretKey.trim())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        if (resp == null || resp.get("url") == null || resp.get("id") == null) {
            throw new IllegalStateException("Stripe returned no checkout URL: " + resp);
        }
        log.info("Stripe-Checkout für Bestellung #{} erstellt (Session {})", order.getId(), resp.get("id"));
        // providerPaymentId = Session-ID, payAddress = Checkout-Link, payAmount = Betrag in Shop-Währung
        return new CreatedPayment(String.valueOf(resp.get("id")), String.valueOf(resp.get("url")), order.getTotalPrice());
    }

    /**
     * Verifiziert die {@code Stripe-Signature}: HMAC-SHA256 über "{timestamp}.{payload}"
     * mit dem Webhook-Signing-Secret.
     */
    @Override
    public boolean verifyWebhook(String signatureHeader, String rawBody) {
        String secret = settings.stripeWebhookSecret();
        if (signatureHeader == null || secret == null || secret.isBlank()) return false;
        try {
            String timestamp = null, v1 = null;
            for (String part : signatureHeader.split(",")) {
                String[] kv = part.split("=", 2);
                if (kv.length != 2) continue;
                if (kv[0].trim().equals("t")) timestamp = kv[1].trim();
                else if (kv[0].trim().equals("v1")) v1 = kv[1].trim();
            }
            if (timestamp == null || v1 == null) return false;
            String signedPayload = timestamp + "." + rawBody;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.trim().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = HexFormat.of().formatHex(mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    v1.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("Stripe-Signaturprüfung fehlgeschlagen", e);
            return false;
        }
    }
}
