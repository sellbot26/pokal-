package com.shop.web;

import com.shop.service.CryptoRateService;
import com.shop.service.GuildAccessService;
import com.shop.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class StatsApiController {

    private final StatsService statsService;
    private final CryptoRateService rateService;
    private final GuildAccessService guildAccess;

    // ===== Site-Admin: alles =====

    @GetMapping("/api/admin/stats")
    public StatsService.Stats stats() {
        return statsService.getStats();
    }

    /** Umsatzverlauf fürs Chart: range = day | week | month | year */
    @GetMapping("/api/admin/stats/series")
    public List<StatsService.SeriesPoint> series(@RequestParam(defaultValue = "month") String range) {
        return statsService.revenueSeries(range);
    }

    // ===== Tenant: nur die eigenen Server =====

    @GetMapping("/api/my/stats")
    public StatsService.Stats myStats(@AuthenticationPrincipal OAuth2User principal) {
        return statsService.getStatsFor(guildAccess.managedGuildIds(principal.getAttribute("id")));
    }

    @GetMapping("/api/my/stats/series")
    public List<StatsService.SeriesPoint> mySeries(@RequestParam(defaultValue = "month") String range,
                                                    @AuthenticationPrincipal OAuth2User principal) {
        String id = principal.getAttribute("id");
        return statsService.revenueSeriesFor(range, guildAccess.managedGuildIds(id), id);
    }

    /** Live-Krypto-Kurse (CoinGecko), für alle eingeloggten Nutzer sichtbar. */
    @GetMapping("/api/rates")
    public Map<String, Map<String, Object>> rates() {
        return rateService.getRates();
    }
}
