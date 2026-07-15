package com.shop.service;

import com.shop.bot.JdaHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Changelog als EINE Quelle für das Dashboard-Modal ("What's New") und die automatischen
 * Update-Ankündigungen in Discord.
 *
 * Neues Release? → Oben in {@link #ENTRIES} einen Eintrag ergänzen (Discord-Markdown:
 * **fett**, `code`). Nach dem Deploy postet der Bot das Embed automatisch in alle
 * konfigurierten Update-Channels (Setting "updateChannelId", kommagetrennt) — genau einmal
 * pro Version (Marker "changelogPostedVersion" in den Settings).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChangelogService {

    public record Entry(String version, String title, List<String> items) {}

    /** Neuester Eintrag zuerst. */
    public static final List<Entry> ENTRIES = List.of(
            new Entry("2026-07-16", "🎫 Ticket System, Auto-Role & Updates", List.of(
                    "New **Tickets** section: build your own ticket panel embed (title, text, color, images, button) with live preview",
                    "Per server: ticket category, support roles, **ticket limit per user**, channel prefix & support ping",
                    "**Transcripts**: closed tickets are saved as a text file to a channel — optionally DM'd to the user",
                    "Auto-role now supports `serverId:roleId` pairs — one role per server",
                    "Welcome & setup message (incl. Terms of Service) when the bot joins a new server",
                    "Slash commands now register **instantly** on new servers",
                    "\"Powered by Pokal\" footer on all product embeds",
                    "**What's New** popup in the dashboard + automatic update announcements in Discord (set your update channel in Settings → General)"
            )),
            new Entry("2026-07-12", "💸 PayPal & Reviews", List.of(
                    "**PayPal Friends & Family** with automatic payment detection via IPN — no API key needed",
                    "New delivery type: **Serial/Account** (email:pass pools)",
                    "**DM broadcast** — message all your customers at once",
                    "Post-purchase **review DMs** with star rating + per-seller review settings"
            )),
            new Entry("2026-07-11", "🪙 Payments Rework", List.of(
                    "**10 coins** with direct wallet payments — unique amount per order, no API keys",
                    "One **Payments** tab for all your payment methods",
                    "Maintenance mode + auto-role on server join"
            ))
    );

    private final JdaHolder jda;
    private final SettingsService settings;

    public List<Entry> all() {
        return ENTRIES;
    }

    public Entry latest() {
        return ENTRIES.get(0);
    }

    /**
     * Nach dem Bot-Start aufgerufen: postet den neuesten Eintrag in alle Update-Channels,
     * falls diese Version noch nicht angekündigt wurde.
     */
    public void postIfNew() {
        String posted = settings.get("changelogPostedVersion", "");
        if (latest().version().equals(posted)) return;
        int sent = postLatest();
        // Nur als "angekündigt" markieren, wenn es mindestens einen Channel erreicht hat —
        // sonst beim nächsten Start erneut versuchen (z. B. Channel erst später konfiguriert).
        if (sent > 0) settings.setInternal("changelogPostedVersion", latest().version());
    }

    /** Postet den neuesten Eintrag in alle konfigurierten Update-Channels. Liefert die Anzahl erfolgreicher Posts. */
    public int postLatest() {
        String channelIds = settings.get("updateChannelId", null);
        if (channelIds == null || channelIds.isBlank() || !jda.isReady()) return 0;

        MessageEmbed embed = updateEmbed(latest());
        int sent = 0;
        for (String id : channelIds.split("[,\\s]+")) {
            if (id.isBlank()) continue;
            try {
                GuildMessageChannel channel = jda.get().getChannelById(GuildMessageChannel.class, id.trim());
                if (channel == null || !channel.canTalk()) {
                    log.warn("Update-Channel {} nicht gefunden oder nicht beschreibbar.", id.trim());
                    continue;
                }
                channel.sendMessageEmbeds(embed).queue(
                        ok -> {},
                        err -> log.warn("Update-Post in {} fehlgeschlagen: {}", id, err.getMessage()));
                sent++;
            } catch (Exception e) {
                log.warn("Update-Post in {} fehlgeschlagen: {}", id, e.getMessage());
            }
        }
        if (sent > 0) log.info("Update-Ankündigung '{}' in {} Channel(s) gepostet.", latest().version(), sent);
        return sent;
    }

    private MessageEmbed updateEmbed(Entry entry) {
        StringBuilder desc = new StringBuilder();
        for (String item : entry.items()) desc.append("• ").append(item).append("\n");
        return new EmbedBuilder()
                .setColor(settings.brandColor())
                .setTitle("📢 " + settings.brandName() + " Update — " + entry.title())
                .setDescription(desc.toString())
                .setFooter("Powered by " + settings.brandName() + " • " + entry.version())
                .setTimestamp(Instant.now())
                .build();
    }
}
