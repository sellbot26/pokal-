package com.shop.web;

import com.shop.config.ShopProperties;
import com.shop.model.ShopUser;
import com.shop.payment.CryptoWallets;
import com.shop.repo.ShopUserRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Eigene Zahlungsmethoden eines Verkäufers: eine Wallet-Adresse pro Coin
 * (Zahlungen laufen direkt auf seine Wallets), PayGate-Wallet für Karte
 * und ein Discord-Webhook für Verkaufs-Logs.
 */
@RestController
@RequiredArgsConstructor
public class PaymentConfigController {

    public record ConfigRequest(String paygateWallet, String paygateEmail, String paypalFfEmail,
                                Map<String, String> wallets, String logWebhookUrl) {}

    /** Einfache E-Mail-Validierung für die PayPal-F&F-Adresse. */
    private static final java.util.regex.Pattern EMAIL =
            java.util.regex.Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final ShopUserRepo userRepo;
    private final ShopProperties props;

    @GetMapping("/api/my/payment-config")
    public Map<String, Object> get(@AuthenticationPrincipal OAuth2User principal) {
        ShopUser user = userRepo.findById(principal.<String>getAttribute("id")).orElseThrow();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("paygateWallet", nullSafe(user.getPaygateWallet()));
        result.put("paygateEmail", nullSafe(user.getPaygateEmail()));
        result.put("paypalFfEmail", nullSafe(user.getPaypalFfEmail()));
        result.put("logWebhookUrl", nullSafe(user.getLogWebhookUrl()));
        Map<String, String> wallets = new LinkedHashMap<>();
        Map<String, String> stored = CryptoWallets.parse(user.getCryptoWallets());
        for (String symbol : CryptoWallets.SYMBOLS) wallets.put(symbol, stored.getOrDefault(symbol, ""));
        result.put("wallets", wallets);
        result.put("paygateConnected", user.getPaygateWallet() != null && !user.getPaygateWallet().isBlank());
        result.put("paypalConnected", user.getPaypalFfEmail() != null && !user.getPaypalFfEmail().isBlank());
        // IPN-URL, die der Verkäufer in seinen PayPal-Einstellungen hinterlegt (shared endpoint)
        result.put("ipnUrl", props.getBaseUrl() + "/api/webhook/paypal");
        result.put("cryptoConnected", !stored.isEmpty());
        return result;
    }

    @PutMapping("/api/my/payment-config")
    public Map<String, Object> update(@RequestBody ConfigRequest req, @AuthenticationPrincipal OAuth2User principal) {
        ShopUser user = userRepo.findById(principal.<String>getAttribute("id")).orElseThrow();
        if (req.paygateWallet() != null) user.setPaygateWallet(req.paygateWallet().trim());
        if (req.paygateEmail() != null) user.setPaygateEmail(req.paygateEmail().trim());
        if (req.paypalFfEmail() != null) {
            String email = req.paypalFfEmail().trim();
            if (!email.isEmpty() && !EMAIL.matcher(email).matches()) {
                throw new IllegalArgumentException("Please enter a valid PayPal email address.");
            }
            user.setPaypalFfEmail(email);
        }
        if (req.wallets() != null) user.setCryptoWallets(CryptoWallets.serialize(req.wallets()));
        if (req.logWebhookUrl() != null) {
            String url = req.logWebhookUrl().trim();
            if (!url.isEmpty() && !url.startsWith("https://discord.com/api/webhooks/")
                    && !url.startsWith("https://discordapp.com/api/webhooks/")) {
                throw new IllegalArgumentException("Please paste a valid Discord webhook URL.");
            }
            user.setLogWebhookUrl(url);
        }
        userRepo.save(user);
        return get(principal);
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
