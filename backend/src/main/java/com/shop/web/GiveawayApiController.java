package com.shop.web;

import com.shop.model.Giveaway;
import com.shop.service.GiveawayService;
import com.shop.service.GuildAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gewinnspiel-Verwaltung pro Server: laufende/beendete auflisten, neues erstellen & posten,
 * sofort beenden, neu auslosen, abbrechen. Auth wie beim Ticket-System über GuildAccessService.
 */
@RestController
@RequiredArgsConstructor
public class GiveawayApiController {

    public record CreateRequest(String channelId, String prize, String description, String color,
                                String imageUrl, Integer winnersCount, Integer durationMinutes,
                                String winnerRoleId, String dmMessage) {}

    private final GiveawayService giveaways;
    private final GuildAccessService guildAccess;

    // ===================== Site-Admin =====================

    @GetMapping("/api/admin/giveaways/{guildId}")
    public List<Map<String, Object>> adminList(@PathVariable String guildId) {
        return list(guildId);
    }

    @PostMapping("/api/admin/giveaways/{guildId}")
    public Map<String, Object> adminCreate(@PathVariable String guildId, @RequestBody CreateRequest req) {
        return create(guildId, null, req);
    }

    @PostMapping("/api/admin/giveaways/{guildId}/{id}/end")
    public Map<String, Object> adminEnd(@PathVariable String guildId, @PathVariable long id) {
        return toDto(giveaways.endNow(assertOwned(id, guildId).getId()));
    }

    @PostMapping("/api/admin/giveaways/{guildId}/{id}/reroll")
    public Map<String, Object> adminReroll(@PathVariable String guildId, @PathVariable long id) {
        return toDto(giveaways.reroll(assertOwned(id, guildId).getId()));
    }

    @DeleteMapping("/api/admin/giveaways/{guildId}/{id}")
    public Map<String, Object> adminCancel(@PathVariable String guildId, @PathVariable long id) {
        return toDto(giveaways.cancel(assertOwned(id, guildId).getId()));
    }

    // ===================== Tenant (nur eigene Server) =====================

    @GetMapping("/api/my/giveaways/{guildId}")
    public List<Map<String, Object>> myList(@PathVariable String guildId, @AuthenticationPrincipal OAuth2User principal) {
        assertManages(principal, guildId);
        return list(guildId);
    }

    @PostMapping("/api/my/giveaways/{guildId}")
    public Map<String, Object> myCreate(@PathVariable String guildId, @RequestBody CreateRequest req,
                                        @AuthenticationPrincipal OAuth2User principal) {
        assertManages(principal, guildId);
        return create(guildId, principal.getAttribute("id"), req);
    }

    @PostMapping("/api/my/giveaways/{guildId}/{id}/end")
    public Map<String, Object> myEnd(@PathVariable String guildId, @PathVariable long id,
                                     @AuthenticationPrincipal OAuth2User principal) {
        assertManages(principal, guildId);
        assertOwned(id, guildId);
        return toDto(giveaways.endNow(id));
    }

    @PostMapping("/api/my/giveaways/{guildId}/{id}/reroll")
    public Map<String, Object> myReroll(@PathVariable String guildId, @PathVariable long id,
                                        @AuthenticationPrincipal OAuth2User principal) {
        assertManages(principal, guildId);
        assertOwned(id, guildId);
        return toDto(giveaways.reroll(id));
    }

    @DeleteMapping("/api/my/giveaways/{guildId}/{id}")
    public Map<String, Object> myCancel(@PathVariable String guildId, @PathVariable long id,
                                        @AuthenticationPrincipal OAuth2User principal) {
        assertManages(principal, guildId);
        assertOwned(id, guildId);
        return toDto(giveaways.cancel(id));
    }

    // ===================== Gemeinsame Helfer =====================

    private void assertManages(OAuth2User principal, String guildId) {
        if (!guildAccess.manages(principal.getAttribute("id"), guildId)) {
            throw new SecurityException("You don't manage this server.");
        }
    }

    /** Stellt sicher, dass das Gewinnspiel existiert UND zu diesem Server gehört. */
    private Giveaway assertOwned(long id, String guildId) {
        Giveaway g = giveaways.get(id);
        if (g == null || !guildId.equals(g.getGuildId())) {
            throw new IllegalArgumentException("Giveaway not found for this server.");
        }
        return g;
    }

    private List<Map<String, Object>> list(String guildId) {
        return giveaways.listForGuild(guildId).stream().map(this::toDto).toList();
    }

    private Map<String, Object> create(String guildId, String hostId, CreateRequest req) {
        if (req.prize() == null || req.prize().isBlank())
            throw new IllegalArgumentException("Please enter a prize.");
        int minutes = req.durationMinutes() == null ? 0 : req.durationMinutes();
        if (minutes < 1) throw new IllegalArgumentException("Duration must be at least 1 minute.");

        Giveaway g = new Giveaway();
        g.setGuildId(guildId);
        g.setChannelId(trim(req.channelId()));
        g.setPrize(trim(req.prize()));
        g.setDescription(trim(req.description()));
        g.setColor(trim(req.color()));
        g.setImageUrl(trim(req.imageUrl()));
        g.setWinnersCount(req.winnersCount() == null ? 1 : Math.max(1, Math.min(50, req.winnersCount())));
        g.setEndsAt(Instant.now().plusSeconds(Math.min(minutes, 60 * 24 * 30) * 60L)); // max 30 Tage
        g.setWinnerRoleId(trim(req.winnerRoleId()));
        g.setDmMessage(trim(req.dmMessage()));
        g.setHostId(hostId);
        return toDto(giveaways.createAndPost(g));
    }

    private Map<String, Object> toDto(Giveaway g) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", g.getId());
        m.put("guildId", g.getGuildId());
        m.put("channelId", nz(g.getChannelId()));
        m.put("prize", nz(g.getPrize()));
        m.put("description", nz(g.getDescription()));
        m.put("color", nz(g.getColor()));
        m.put("imageUrl", nz(g.getImageUrl()));
        m.put("winnersCount", g.getWinnersCount());
        m.put("endsAt", g.getEndsAt() == null ? null : g.getEndsAt().toString());
        m.put("winnerRoleId", nz(g.getWinnerRoleId()));
        m.put("dmMessage", nz(g.getDmMessage()));
        m.put("status", g.getStatus().name());
        m.put("entrantCount", idCount(g.getEntrants()));
        m.put("winnerIds", nz(g.getWinnerIds()));
        m.put("createdAt", g.getCreatedAt() == null ? null : g.getCreatedAt().toString());
        return m;
    }

    private static int idCount(String raw) {
        if (raw == null || raw.isBlank()) return 0;
        int n = 0;
        for (String p : raw.split("[,\\s]+")) if (!p.isBlank()) n++;
        return n;
    }

    private static String trim(String s) { return s == null ? "" : s.trim(); }
    private static String nz(String s) { return s == null ? "" : s; }
}
