package com.shop.payment;

import com.shop.config.ShopProperties;
import com.shop.model.Order;
import com.shop.model.ShopUser;
import com.shop.service.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/**
 * PayGate (paygate.to): Kunde zahlt per Karte / Apple Pay / Google Pay,
 * die Auszahlung geht als USDC (Polygon) direkt auf die Shop-Wallet.
 *
 * Ablauf:
 *  1. wallet.php registriert die Ziel-Wallet + Callback-URL und liefert eine Einweg-Adresse
 *  2. Der Kunde bekommt einen Checkout-Link (process-payment.php)
 *  3. Nach Zahlung ruft PayGate die Callback-URL auf — verifiziert über das unerratbare Token
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PayGateProvider implements PaymentProvider {

    private final ShopProperties props;
    private final SettingsService settings;
    private final RestClient client = RestClient.create("https://api.paygate.to");

    @Override
    public String name() {
        return "paygate";
    }

    /** Site-Wallet aus den Dashboard-Einstellungen, Fallback auf .env. */
    public String siteWallet() {
        return settings.get("paygateWallet", props.getPayment().getPaygate().getWallet());
    }

    /** Wallet des Verkäufers, falls hinterlegt — sonst die Site-Wallet. */
    public String walletFor(ShopUser merchant) {
        if (merchant != null && merchant.getPaygateWallet() != null && !merchant.getPaygateWallet().isBlank()) {
            return merchant.getPaygateWallet().trim();
        }
        return siteWallet();
    }

    public boolean isConfiguredFor(ShopUser merchant) {
        String w = walletFor(merchant);
        return w != null && !w.isBlank();
    }

    @Override
    public CreatedPayment create(Order order, String payCurrency, ShopUser merchant) {
        String email = merchant != null && merchant.getPaygateEmail() != null && !merchant.getPaygateEmail().isBlank()
                ? merchant.getPaygateEmail()
                : settings.get("paygateEmail", "");
        return build(order, walletFor(merchant), email);
    }

    /** PayGate-Checkout auf eine fest vorgegebene Auszahlungs-Wallet (z. B. für Plan-Käufe). */
    public CreatedPayment createForWallet(Order order, String wallet) {
        return build(order, wallet, settings.get("paygateEmail", ""));
    }

    private CreatedPayment build(Order order, String wallet, String email) {
        if (wallet == null || wallet.isBlank()) {
            throw new IllegalStateException("Card payments are not set up yet. "
                    + "Connect a PayGate wallet in the dashboard (Payments).");
        }
        String token = UUID.randomUUID().toString();
        String callback = props.getBaseUrl() + "/api/webhook/paygate?token=" + token + "&order_id=" + order.getId();

        Map<String, Object> resp = client.get()
                .uri(uri -> uri.path("/control/wallet.php")
                        .queryParam("address", wallet)
                        .queryParam("callback", UriUtils.encode(callback, StandardCharsets.UTF_8))
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        if (resp == null || resp.get("address_in") == null) {
            throw new IllegalStateException("PayGate hat keine Zahlungsadresse geliefert: " + resp);
        }
        // address_in kommt bereits URL-kodiert von PayGate — NICHT erneut kodieren!
        String addressIn = String.valueOf(resp.get("address_in"));

        String currency = settings.get("currency", "EUR").toUpperCase();
        // Immer die von paygate.to gehostete Checkout-Seite (pay.php) — der Kunde wählt
        // die Zahlungsart dort selbst, kein fest verdrahteter Onramp wie MoonPay.
        String checkoutUrl = "https://checkout.paygate.to/pay.php"
                + "?address=" + addressIn
                + "&amount=" + order.getTotalPrice().toPlainString()
                + "&provider=hosted"
                + "&email=" + UriUtils.encode(email, StandardCharsets.UTF_8)
                + "&currency=" + currency;

        log.info("PayGate-Checkout für Bestellung #{} erstellt (Token {})", order.getId(), token);
        // payAddress = Checkout-Link, payAmount = Betrag in Shop-Währung
        return new CreatedPayment(token, checkoutUrl, order.getTotalPrice());
    }

    @Override
    public boolean verifyWebhook(String signature, String rawBody) {
        // PayGate nutzt keinen signierten IPN-Webhook — die Bestätigung läuft über
        // den GET-Callback mit unerratbarem Token (siehe WebhookController).
        return false;
    }
}
