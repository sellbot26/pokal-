package com.shop.web;

import com.shop.service.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class SettingsApiController {

    private final SettingsService settings;

    /** Öffentliches Branding (Shop-Name, Farbe, Logo, …) — auch ohne Login abrufbar. */
    @GetMapping("/api/settings")
    public Map<String, String> publicSettings() {
        return settings.publicSettings();
    }

    @GetMapping("/api/admin/settings")
    public Map<String, String> all() {
        // Read-only Diagnose-Infos zusätzlich zu den editierbaren Settings — werden beim
        // Speichern (PUT) ignoriert, da nur bekannte Keys aus dem Request übernommen werden.
        Map<String, String> result = new LinkedHashMap<>(settings.all());
        // Stripe-Secrets NIE an den Client zurückgeben — nur ob konfiguriert
        boolean stripeConfigured = settings.stripeSecretKey() != null && !settings.stripeSecretKey().isBlank();
        result.remove("stripeSecretKey");
        result.remove("stripeWebhookSecret");
        result.put("stripeConfigured", String.valueOf(stripeConfigured));
        return result;
    }

    @PutMapping("/api/admin/settings")
    public Map<String, String> update(@RequestBody Map<String, String> body) {
        body.forEach((key, value) -> {
            if (!SettingsService.ALLOWED_KEYS.contains(key))
                throw new IllegalArgumentException("Unbekannte Einstellung: " + key);
        });
        body.forEach((key, value) -> settings.set(key, value == null ? "" : value.trim()));
        return settings.all();
    }
}
