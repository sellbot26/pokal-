package com.shop.web;

import com.shop.bot.JdaHolder;
import com.shop.model.TicketConfig;
import com.shop.service.GuildAccessService;
import com.shop.service.TicketService;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ticket-System-Konfiguration pro Server: lesen/speichern, Panel in einen Channel posten,
 * plus Rollen- und Kategorie-Listen für die Dashboard-Dropdowns.
 */
@RestController
@RequiredArgsConstructor
public class TicketApiController {

    public record TicketConfigRequest(String panelTitle, String panelDescription, String panelColor,
                                      String panelImageUrl, String panelThumbnailUrl,
                                      String buttonLabel, String buttonEmoji,
                                      String categoryId, String supportRoleIds, Integer maxOpenPerUser,
                                      Boolean mentionSupport, String namePrefix,
                                      String welcomeTitle, String welcomeMessage,
                                      Boolean transcriptEnabled, String transcriptChannelId,
                                      Boolean transcriptDmUser) {}

    public record PanelRequest(String channelId) {}

    private final TicketService tickets;
    private final GuildAccessService guildAccess;
    private final JdaHolder jdaHolder;

    // ===================== Site-Admin =====================

    @GetMapping("/api/admin/tickets/{guildId}")
    public Map<String, Object> get(@PathVariable String guildId) {
        return toDto(tickets.configFor(guildId));
    }

    @PutMapping("/api/admin/tickets/{guildId}")
    public Map<String, Object> update(@PathVariable String guildId, @RequestBody TicketConfigRequest req) {
        return toDto(saveConfig(guildId, req));
    }

    @PostMapping("/api/admin/tickets/{guildId}/panel")
    public Map<String, String> postPanel(@PathVariable String guildId, @RequestBody PanelRequest req) {
        return sendPanel(guildId, req);
    }

    /** Rollen + Kategorien eines Servers — für die Auswahl-Dropdowns im Dashboard. */
    @GetMapping("/api/admin/tickets/{guildId}/meta")
    public Map<String, Object> meta(@PathVariable String guildId) {
        return guildMeta(guildId);
    }

    // ===================== Tenant (nur eigene Server) =====================

    @GetMapping("/api/my/tickets/{guildId}")
    public Map<String, Object> myGet(@PathVariable String guildId, @AuthenticationPrincipal OAuth2User principal) {
        assertManages(principal, guildId);
        return toDto(tickets.configFor(guildId));
    }

    @PutMapping("/api/my/tickets/{guildId}")
    public Map<String, Object> myUpdate(@PathVariable String guildId, @RequestBody TicketConfigRequest req,
                                        @AuthenticationPrincipal OAuth2User principal) {
        assertManages(principal, guildId);
        return toDto(saveConfig(guildId, req));
    }

    @PostMapping("/api/my/tickets/{guildId}/panel")
    public Map<String, String> myPostPanel(@PathVariable String guildId, @RequestBody PanelRequest req,
                                           @AuthenticationPrincipal OAuth2User principal) {
        assertManages(principal, guildId);
        return sendPanel(guildId, req);
    }

    @GetMapping("/api/my/tickets/{guildId}/meta")
    public Map<String, Object> myMeta(@PathVariable String guildId, @AuthenticationPrincipal OAuth2User principal) {
        assertManages(principal, guildId);
        return guildMeta(guildId);
    }

    // ===================== Gemeinsame Helfer =====================

    private void assertManages(OAuth2User principal, String guildId) {
        if (!guildAccess.manages(principal.getAttribute("id"), guildId)) {
            throw new SecurityException("You don't manage this server.");
        }
    }

    private TicketConfig saveConfig(String guildId, TicketConfigRequest req) {
        TicketConfig c = tickets.configFor(guildId);
        c.setGuildId(guildId);
        c.setPanelTitle(trim(req.panelTitle()));
        c.setPanelDescription(trim(req.panelDescription()));
        c.setPanelColor(trim(req.panelColor()));
        c.setPanelImageUrl(trim(req.panelImageUrl()));
        c.setPanelThumbnailUrl(trim(req.panelThumbnailUrl()));
        c.setButtonLabel(trim(req.buttonLabel()));
        c.setButtonEmoji(trim(req.buttonEmoji()));
        c.setCategoryId(trim(req.categoryId()));
        c.setSupportRoleIds(trim(req.supportRoleIds()));
        if (req.maxOpenPerUser() != null) c.setMaxOpenPerUser(Math.max(0, Math.min(25, req.maxOpenPerUser())));
        if (req.mentionSupport() != null) c.setMentionSupport(req.mentionSupport());
        c.setNamePrefix(trim(req.namePrefix()));
        c.setWelcomeTitle(trim(req.welcomeTitle()));
        c.setWelcomeMessage(trim(req.welcomeMessage()));
        if (req.transcriptEnabled() != null) c.setTranscriptEnabled(req.transcriptEnabled());
        c.setTranscriptChannelId(trim(req.transcriptChannelId()));
        if (req.transcriptDmUser() != null) c.setTranscriptDmUser(req.transcriptDmUser());
        return tickets.save(c);
    }

    private Map<String, String> sendPanel(String guildId, PanelRequest req) {
        if (req.channelId() == null || req.channelId().isBlank())
            throw new IllegalArgumentException("Please select a channel.");
        JDA jda = jdaHolder.get();
        if (jda == null) throw new IllegalStateException("Bot is not connected.");
        GuildMessageChannel channel = jda.getChannelById(GuildMessageChannel.class, req.channelId().trim());
        if (channel == null) throw new IllegalArgumentException("Channel not found.");
        if (!channel.getGuild().getId().equals(guildId))
            throw new IllegalArgumentException("Channel doesn't belong to this server.");
        if (!channel.canTalk()) throw new IllegalStateException("The bot can't write in this channel.");

        TicketConfig cfg = tickets.configFor(guildId);
        channel.sendMessageEmbeds(tickets.panelEmbed(cfg))
                .setActionRow(tickets.panelButton(cfg))
                .queue();
        return Map.of("status", "sent", "channel", channel.getName());
    }

    /** Rollen (ohne @everyone/Bot-Rollen) + Kategorien des Servers. */
    private Map<String, Object> guildMeta(String guildId) {
        JDA jda = jdaHolder.get();
        if (jda == null) return Map.of("roles", List.of(), "categories", List.of());
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) return Map.of("roles", List.of(), "categories", List.of());

        List<Map<String, String>> roles = new ArrayList<>();
        guild.getRoles().forEach(r -> {
            if (r.isPublicRole() || r.isManaged()) return;
            Map<String, String> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("name", r.getName());
            roles.add(m);
        });
        List<Map<String, String>> categories = new ArrayList<>();
        guild.getCategories().forEach(c -> {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("name", c.getName());
            categories.add(m);
        });
        return Map.of("roles", roles, "categories", categories);
    }

    private Map<String, Object> toDto(TicketConfig c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("guildId", c.getGuildId());
        m.put("panelTitle", nz(c.getPanelTitle()));
        m.put("panelDescription", nz(c.getPanelDescription()));
        m.put("panelColor", nz(c.getPanelColor()));
        m.put("panelImageUrl", nz(c.getPanelImageUrl()));
        m.put("panelThumbnailUrl", nz(c.getPanelThumbnailUrl()));
        m.put("buttonLabel", nz(c.getButtonLabel()));
        m.put("buttonEmoji", nz(c.getButtonEmoji()));
        m.put("categoryId", nz(c.getCategoryId()));
        m.put("supportRoleIds", nz(c.getSupportRoleIds()));
        m.put("maxOpenPerUser", c.getMaxOpenPerUser());
        m.put("mentionSupport", c.isMentionSupport());
        m.put("namePrefix", nz(c.getNamePrefix()));
        m.put("welcomeTitle", nz(c.getWelcomeTitle()));
        m.put("welcomeMessage", nz(c.getWelcomeMessage()));
        m.put("transcriptEnabled", c.isTranscriptEnabled());
        m.put("transcriptChannelId", nz(c.getTranscriptChannelId()));
        m.put("transcriptDmUser", c.isTranscriptDmUser());
        return m;
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
