package com.shop.web;

import com.shop.model.ShopUser;
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
 * Eigene Zahlungsmethoden eines Verkäufers: PayGate-Wallet (Karte) und
 * NOWPayments-Account (Krypto). Zahlungen für seine Produkte laufen dann
 * direkt auf seine Wallet / sein Konto statt auf die Site-Konfiguration.
 */
@RestController
@RequiredArgsConstructor
public class PaymentConfigController {

    public record ConfigRequest(String paygateWallet, String paygateEmail,
                                String nowpaymentsApiKey, String nowpaymentsIpnSecret,
                                String cryptoWallets, String logWebhookUrl) {}

    private final ShopUserRepo userRepo;

    @GetMapping("/api/my/payment-config")
    public Map<String, Object> get(@AuthenticationPrincipal OAuth2User principal) {
        ShopUser user = userRepo.findById(principal.<String>getAttribute("id")).orElseThrow();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("paygateWallet", nullSafe(user.getPaygateWallet()));
        result.put("paygateEmail", nullSafe(user.getPaygateEmail()));
        result.put("cryptoWallets", nullSafe(user.getCryptoWallets()));
        result.put("logWebhookUrl", nullSafe(user.getLogWebhookUrl()));
        // API-Key/Secret nie zurückgeben — nur ob sie gesetzt sind
        result.put("cryptoConnected", user.getNowpaymentsApiKey() != null && !user.getNowpaymentsApiKey().isBlank());
        result.put("paygateConnected", user.getPaygateWallet() != null && !user.getPaygateWallet().isBlank());
        return result;
    }

    @PutMapping("/api/my/payment-config")
    public Map<String, Object> update(@RequestBody ConfigRequest req, @AuthenticationPrincipal OAuth2User principal) {
        ShopUser user = userRepo.findById(principal.<String>getAttribute("id")).orElseThrow();
        if (req.paygateWallet() != null) user.setPaygateWallet(req.paygateWallet().trim());
        if (req.paygateEmail() != null) user.setPaygateEmail(req.paygateEmail().trim());
        if (req.cryptoWallets() != null) user.setCryptoWallets(req.cryptoWallets().trim());
        if (req.logWebhookUrl() != null) {
            String url = req.logWebhookUrl().trim();
            if (!url.isEmpty() && !url.startsWith("https://discord.com/api/webhooks/")
                    && !url.startsWith("https://discordapp.com/api/webhooks/")) {
                throw new IllegalArgumentException("Please paste a valid Discord webhook URL.");
            }
            user.setLogWebhookUrl(url);
        }
        // Leerer String = löschen, null = nicht anfassen (damit der Key beim Speichern anderer Felder erhalten bleibt)
        if (req.nowpaymentsApiKey() != null) {
            user.setNowpaymentsApiKey(req.nowpaymentsApiKey().isBlank() ? null : req.nowpaymentsApiKey().trim());
        }
        if (req.nowpaymentsIpnSecret() != null) {
            user.setNowpaymentsIpnSecret(req.nowpaymentsIpnSecret().isBlank() ? null : req.nowpaymentsIpnSecret().trim());
        }
        userRepo.save(user);
        return get(principal);
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
