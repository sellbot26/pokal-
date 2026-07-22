package com.shop.web;

import com.shop.service.StorefrontService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Hübsche URLs für die öffentliche Storefront (Forward — die URL im Browser bleibt erhalten):
 *   /s/{key}   — key = Slug ODER Guild-ID (immer verfügbar)
 *   /{slug}    — Wunsch-Adresse pokal.shop/deinname (nur einzelnes Segment, klein, ohne Punkt)
 * Die Seite liest den Schlüssel selbst aus dem Pfad und holt die Daten über /api/storefront/{key}.
 */
@Controller
@RequiredArgsConstructor
public class StorefrontPageController {

    private final StorefrontService storefront;

    @GetMapping("/s/{key}")
    public String viaPrefix(@PathVariable String key) {
        return "forward:/storefront.html";
    }

    /**
     * Bare Vanity-URL. Das Regex {@code [a-z0-9-]+} schließt alle .html/.ico-Dateien (Punkt) und
     * Mehr-Segment-Pfade aus; exakt gemappte Routen (z. B. /error) gewinnen ohnehin gegen dieses
     * Pattern. Reservierte Wörter und unbekannte Slugs leiten zurück auf die Startseite.
     */
    @GetMapping("/{slug:[a-z0-9-]+}")
    public String viaSlug(@PathVariable String slug) {
        if (StorefrontService.RESERVED_SLUGS.contains(slug)) return "redirect:/";
        if (storefront.resolve(slug) == null) return "redirect:/";
        return "forward:/storefront.html";
    }
}
