package com.shop.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.bot.JdaHolder;
import com.shop.model.SavedEmbed;
import com.shop.model.ShopUser;
import com.shop.repo.SavedEmbedRepo;
import com.shop.repo.ShopUserRepo;
import com.shop.service.EmbedRenderService;
import com.shop.service.GuildAccessService;
import com.shop.service.PlanService;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Embed-Editor: speichern, laden, löschen und über den Bot in einen Channel senden. */
@RestController
@RequiredArgsConstructor
public class EmbedApiController {

    public record EmbedRequest(String name, JsonNode data) {}
    public record SendRequest(String channelId) {}

    private final SavedEmbedRepo embedRepo;
    private final EmbedRenderService renderer;
    private final JdaHolder jdaHolder;
    private final ObjectMapper mapper;
    private final PlanService planService;
    private final ShopUserRepo userRepo;
    private final GuildAccessService guildAccess;
    private final com.shop.service.SettingsService settings;

    // ===================== Site-Admin (sieht/verwaltet alle Embeds) =====================

    @GetMapping("/api/admin/embeds")
    public List<Map<String, Object>> list() throws Exception {
        return toDtoList(embedRepo.findAllByOrderByNameAsc());
    }

    @PostMapping("/api/admin/embeds")
    public Map<String, Object> create(@RequestBody EmbedRequest req) throws Exception {
        return saveEmbed(req, null, null, true);
    }

    @PutMapping("/api/admin/embeds/{id}")
    public Map<String, Object> update(@PathVariable long id, @RequestBody EmbedRequest req) throws Exception {
        return updateEmbed(id, req, null, true);
    }

    @DeleteMapping("/api/admin/embeds/{id}")
    public Map<String, String> delete(@PathVariable long id) {
        return deleteEmbed(id, null, true);
    }

    @PostMapping("/api/admin/embeds/{id}/send")
    public Map<String, String> send(@PathVariable long id, @RequestBody SendRequest req) throws Exception {
        return sendEmbed(id, req, null, true);
    }

    /** Alle Text-Channels, in denen der Bot schreiben kann — für die Channel-Auswahl. */
    @GetMapping("/api/admin/channels")
    public List<Map<String, String>> channels() {
        return channelsFor(null);
    }

    /** Custom-Emojis der Server, auf denen der Bot ist — für den Emoji-Picker im Embed-Editor. */
    @GetMapping("/api/admin/emojis")
    public List<Map<String, Object>> emojis() {
        JDA jda = jdaHolder.get();
        if (jda == null) return List.of();
        return jda.getEmojis().stream()
                .sorted(java.util.Comparator.comparing(net.dv8tion.jda.api.entities.emoji.RichCustomEmoji::getName))
                .map(e -> {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", e.getId());
                    m.put("name", e.getName());
                    m.put("animated", e.isAnimated());
                    m.put("imageUrl", e.getImageUrl());
                    m.put("formatted", e.getAsMention());
                    return m;
                }).toList();
    }

    /** Server, auf denen der Bot ist — für die Server-Auswahl im Dashboard, plus Invite-Link. */
    @GetMapping("/api/admin/guilds")
    public Map<String, Object> guilds() {
        JDA jda = jdaHolder.get();
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        List<Map<String, String>> list = new ArrayList<>();
        String inviteUrl = "";
        if (jda != null) {
            for (var guild : jda.getGuilds()) {
                list.add(guildDto(guild));
            }
            inviteUrl = "https://discord.com/oauth2/authorize?client_id=" + jda.getSelfUser().getApplicationId()
                    + "&scope=bot%20applications.commands&permissions=268553232";
        }
        result.put("guilds", list);
        result.put("inviteUrl", inviteUrl);
        result.put("botName", jda == null ? "" : jda.getSelfUser().getName());
        result.put("botOnline", jda != null);
        return result;
    }

    // ===================== Tenant (nur eigene Embeds, eigene Server) =====================

    @GetMapping("/api/my/embeds")
    public List<Map<String, Object>> myList(@AuthenticationPrincipal OAuth2User principal) throws Exception {
        String id = principal.getAttribute("id");
        return toDtoList(embedRepo.findByOwnerIdOrderByNameAsc(id));
    }

    @PostMapping("/api/my/embeds")
    public Map<String, Object> myCreate(@RequestBody EmbedRequest req, @AuthenticationPrincipal OAuth2User principal) throws Exception {
        return saveEmbed(req, principal.getAttribute("id"), principal, false);
    }

    @PutMapping("/api/my/embeds/{id}")
    public Map<String, Object> myUpdate(@PathVariable long id, @RequestBody EmbedRequest req,
                                         @AuthenticationPrincipal OAuth2User principal) throws Exception {
        return updateEmbed(id, req, principal.getAttribute("id"), false);
    }

    @DeleteMapping("/api/my/embeds/{id}")
    public Map<String, String> myDelete(@PathVariable long id, @AuthenticationPrincipal OAuth2User principal) {
        return deleteEmbed(id, principal.getAttribute("id"), false);
    }

    @PostMapping("/api/my/embeds/{id}/send")
    public Map<String, String> mySend(@PathVariable long id, @RequestBody SendRequest req,
                                       @AuthenticationPrincipal OAuth2User principal) throws Exception {
        return sendEmbed(id, req, principal.getAttribute("id"), false);
    }

    @GetMapping("/api/my/channels")
    public List<Map<String, String>> myChannels(@AuthenticationPrincipal OAuth2User principal) {
        return channelsFor(principal.getAttribute("id"));
    }

    /** Server, die dieser Nutzer selbst auf Discord verwaltet (Owner/Administrator) — sein eigener Tenant-Bereich. */
    @GetMapping("/api/my/managed-guilds")
    public List<Map<String, String>> myManagedGuilds(@AuthenticationPrincipal OAuth2User principal) {
        JDA jda = jdaHolder.get();
        if (jda == null) return List.of();
        Set<String> guildIds = guildAccess.managedGuildIds(principal.getAttribute("id"));
        List<Map<String, String>> result = new ArrayList<>();
        for (String gid : guildIds) {
            Guild g = jda.getGuildById(gid);
            if (g != null) result.add(guildDto(g));
        }
        return result;
    }

    /** Custom-Emojis der eigenen Server — für den Emoji-Picker der Tenants. */
    @GetMapping("/api/my/emojis")
    public List<Map<String, Object>> myEmojis(@AuthenticationPrincipal OAuth2User principal) {
        JDA jda = jdaHolder.get();
        if (jda == null) return List.of();
        Set<String> guildIds = guildAccess.managedGuildIds(principal.getAttribute("id"));
        return jda.getGuilds().stream()
                .filter(g -> guildIds.contains(g.getId()))
                .flatMap(g -> g.getEmojis().stream())
                .sorted(java.util.Comparator.comparing(net.dv8tion.jda.api.entities.emoji.RichCustomEmoji::getName))
                .<Map<String, Object>>map(e -> {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", e.getId());
                    m.put("name", e.getName());
                    m.put("animated", e.isAnimated());
                    m.put("imageUrl", e.getImageUrl());
                    m.put("formatted", e.getAsMention());
                    return m;
                }).toList();
    }

    /** Gleiche Struktur wie /api/admin/guilds, aber nur die eigenen Server — für die Bot-&-Servers-Sektion der Tenants. */
    @GetMapping("/api/my/guilds")
    public Map<String, Object> myGuilds(@AuthenticationPrincipal OAuth2User principal) {
        JDA jda = jdaHolder.get();
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("guilds", myManagedGuilds(principal));
        result.put("inviteUrl", jda == null ? "" :
                "https://discord.com/oauth2/authorize?client_id=" + jda.getSelfUser().getApplicationId()
                        + "&scope=bot%20applications.commands&permissions=268553232");
        result.put("botName", jda == null ? "" : jda.getSelfUser().getName());
        result.put("botOnline", jda != null);
        return result;
    }

    // ===================== Gemeinsame Helfer =====================

    private List<Map<String, Object>> toDtoList(List<SavedEmbed> embeds) throws Exception {
        List<Map<String, Object>> result = new ArrayList<>();
        for (SavedEmbed e : embeds) {
            result.add(Map.of(
                    "id", e.getId(),
                    "name", e.getName(),
                    "updatedAt", e.getUpdatedAt(),
                    "data", mapper.readTree(e.getJson())
            ));
        }
        return result;
    }

    private Map<String, Object> saveEmbed(EmbedRequest req, String ownerId, OAuth2User principal, boolean siteAdmin) throws Exception {
        if (req.name() == null || req.name().isBlank()) throw new IllegalArgumentException("Name fehlt.");
        if (req.data() == null) throw new IllegalArgumentException("Embed-Daten fehlen.");
        renderer.render(req.data()); // validiert, dass das Embed baubar ist

        var existing = siteAdmin
                ? embedRepo.findByNameIgnoreCase(req.name().trim())
                : embedRepo.findByOwnerIdAndNameIgnoreCase(ownerId, req.name().trim());

        if (existing.isEmpty() && !siteAdmin) {
            ShopUser actor = userRepo.findById(ownerId).orElseThrow();
            planService.assertCanAddEmbed(actor, false);
        }
        SavedEmbed embed = existing.orElseGet(SavedEmbed::new);
        embed.setName(req.name().trim());
        if (!siteAdmin) embed.setOwnerId(ownerId);
        embed.setJson(mapper.writeValueAsString(req.data()));
        embed.setUpdatedAt(Instant.now());
        embed = embedRepo.save(embed);
        return Map.of("id", embed.getId(), "name", embed.getName());
    }

    private Map<String, Object> updateEmbed(long id, EmbedRequest req, String ownerId, boolean siteAdmin) throws Exception {
        SavedEmbed embed = embedRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Embed nicht gefunden."));
        if (!siteAdmin && !ownerId.equals(embed.getOwnerId())) {
            throw new SecurityException("You don't have access to this embed.");
        }
        if (req.name() != null && !req.name().isBlank()) embed.setName(req.name().trim());
        if (req.data() != null) {
            renderer.render(req.data());
            embed.setJson(mapper.writeValueAsString(req.data()));
        }
        embed.setUpdatedAt(Instant.now());
        embed = embedRepo.save(embed);
        return Map.of("id", embed.getId(), "name", embed.getName());
    }

    private Map<String, String> deleteEmbed(long id, String ownerId, boolean siteAdmin) {
        SavedEmbed embed = embedRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Embed nicht gefunden."));
        if (!siteAdmin && !ownerId.equals(embed.getOwnerId())) {
            throw new SecurityException("You don't have access to this embed.");
        }
        embedRepo.deleteById(id);
        return Map.of("status", "gelöscht");
    }

    private Map<String, String> sendEmbed(long id, SendRequest req, String ownerId, boolean siteAdmin) throws Exception {
        SavedEmbed saved = embedRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Embed nicht gefunden."));
        if (!siteAdmin && !ownerId.equals(saved.getOwnerId())) {
            throw new SecurityException("You don't have access to this embed.");
        }
        if (req.channelId() == null || req.channelId().isBlank())
            throw new IllegalArgumentException("Channel fehlt.");

        JDA jda = jdaHolder.get();
        if (jda == null) throw new IllegalStateException("Bot ist nicht verbunden.");
        TextChannel channel = jda.getTextChannelById(req.channelId().trim());
        if (channel == null) throw new IllegalArgumentException("Channel nicht gefunden.");
        if (!channel.canTalk()) throw new IllegalStateException("Bot darf in diesem Channel nicht schreiben.");
        if (!siteAdmin && !guildAccess.manages(ownerId, channel.getGuild().getId())) {
            throw new SecurityException("You don't manage this server.");
        }

        JsonNode data = mapper.readTree(saved.getJson());
        // Plan-Gating: eigene Embed-Farben + entfernbarer Branding-Footer sind Pro-Features.
        // Free-Verkäufer bekommen die Marken-Farbe erzwungen und behalten den Pokal-Footer.
        if (!siteAdmin && ownerId != null) {
            ShopUser actor = userRepo.findById(ownerId).orElse(null);
            if (actor != null && !planService.isAtLeast(actor, "PRO")
                    && data instanceof com.fasterxml.jackson.databind.node.ObjectNode obj) {
                obj.put("color", settings.get("brandColor", "#e3a63a"));
                obj.put("footer", settings.brandName());
            }
        }
        MessageEmbed embed = renderer.render(data);
        MessageCreateAction action = channel.sendMessageEmbeds(embed);
        String content = renderer.content(data);
        if (content != null) action = action.setContent(content);
        List<Button> buttons = renderer.buttons(data);
        if (!buttons.isEmpty()) {
            // Discord erlaubt 5 Buttons pro Reihe, max. 5 Reihen
            List<net.dv8tion.jda.api.interactions.components.ActionRow> rows = new ArrayList<>();
            for (int i = 0; i < buttons.size() && rows.size() < 5; i += 5) {
                rows.add(net.dv8tion.jda.api.interactions.components.ActionRow.of(
                        buttons.subList(i, Math.min(i + 5, buttons.size()))));
            }
            action = action.setComponents(rows);
        }
        action.queue();
        return Map.of("status", "gesendet", "channel", channel.getName());
    }

    private List<Map<String, String>> channelsFor(String tenantId) {
        JDA jda = jdaHolder.get();
        if (jda == null) return List.of();
        Set<String> allowedGuilds = tenantId == null ? null : guildAccess.managedGuildIds(tenantId);
        List<Map<String, String>> result = new ArrayList<>();
        jda.getGuilds().forEach(guild -> {
            if (allowedGuilds != null && !allowedGuilds.contains(guild.getId())) return;
            guild.getTextChannels().forEach(channel -> {
                if (channel.canTalk()) {
                    result.add(Map.of(
                            "id", channel.getId(),
                            "name", channel.getName(),
                            "guild", guild.getName(),
                            "guildId", guild.getId()
                    ));
                }
            });
        });
        return result;
    }

    private Map<String, String> guildDto(Guild guild) {
        Map<String, String> g = new java.util.LinkedHashMap<>();
        g.put("id", guild.getId());
        g.put("name", guild.getName());
        g.put("icon", guild.getIconUrl() == null ? "" : guild.getIconUrl());
        g.put("members", String.valueOf(guild.getMemberCount()));
        return g;
    }
}
