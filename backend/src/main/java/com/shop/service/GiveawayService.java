package com.shop.service;

import com.shop.bot.JdaHolder;
import com.shop.model.Giveaway;
import com.shop.repo.GiveawayRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Gewinnspiel-System: Dashboard erstellt & postet ein Embed mit "Enter"-Button, Teilnehmer
 * werden per Button gesammelt, nach Ablauf zieht ein Scheduler automatisch die Gewinner
 * (mit optionaler Rollen-Vergabe und DM). End-now / Reroll / Cancel laufen ebenfalls hierüber.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GiveawayService {

    private final GiveawayRepo repo;
    private final JdaHolder jdaHolder;
    private final SettingsService settings;

    public List<Giveaway> listForGuild(String guildId) {
        return repo.findByGuildIdOrderByCreatedAtDesc(guildId);
    }

    public Giveaway get(long id) {
        return repo.findById(id).orElse(null);
    }

    // ===================== Erstellen & posten =====================

    /**
     * Speichert das Gewinnspiel (um eine ID für den Button zu bekommen), postet dann das
     * Embed samt "Enter"-Button in den Channel und merkt sich die Nachricht-ID.
     */
    @Transactional
    public Giveaway createAndPost(Giveaway g) {
        JDA jda = jdaHolder.get();
        if (jda == null) throw new IllegalStateException("Bot is not connected.");
        GuildMessageChannel channel = jda.getChannelById(GuildMessageChannel.class, safe(g.getChannelId()));
        if (channel == null) throw new IllegalArgumentException("Channel not found.");
        if (!channel.getGuild().getId().equals(g.getGuildId()))
            throw new IllegalArgumentException("Channel doesn't belong to this server.");
        if (!channel.canTalk()) throw new IllegalStateException("The bot can't write in this channel.");
        if (g.getEndsAt() == null || g.getEndsAt().isBefore(Instant.now().plusSeconds(30)))
            throw new IllegalArgumentException("The end time must be at least a minute from now.");

        g.setStatus(Giveaway.Status.RUNNING);
        g.setEntrants("");
        g.setWinnerIds("");
        Giveaway saved = repo.save(g); // ID für die Button-Custom-ID

        channel.sendMessageEmbeds(runningEmbed(saved))
                .setActionRow(enterButton(saved))
                .queue(msg -> {
                    saved.setMessageId(msg.getId());
                    repo.save(saved);
                }, err -> log.warn("Giveaway-Post fehlgeschlagen: {}", err.getMessage()));
        return saved;
    }

    // ===================== Teilnehmen =====================

    public enum EntryResult { OK, ALREADY_IN, NOT_RUNNING, NOT_FOUND }

    /** Fügt einen Nutzer zur Teilnehmerliste hinzu. */
    @Transactional
    public EntryResult enter(long giveawayId, String userId) {
        Giveaway g = repo.findById(giveawayId).orElse(null);
        if (g == null) return EntryResult.NOT_FOUND;
        if (g.getStatus() != Giveaway.Status.RUNNING || g.getEndsAt().isBefore(Instant.now()))
            return EntryResult.NOT_RUNNING;
        List<String> ids = idList(g.getEntrants());
        if (ids.contains(userId)) return EntryResult.ALREADY_IN;
        ids.add(userId);
        g.setEntrants(String.join(",", ids));
        repo.save(g);
        return EntryResult.OK;
    }

    /** Aktuelle Teilnehmerzahl (für die Button-Antwort). */
    public int entrantCount(long giveawayId) {
        Giveaway g = repo.findById(giveawayId).orElse(null);
        return g == null ? 0 : idList(g.getEntrants()).size();
    }

    // ===================== Auslosen =====================

    /** Auslosen aller abgelaufenen, noch laufenden Gewinnspiele — alle 30 s. */
    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void sweepExpired() {
        List<Giveaway> due = repo.findByStatusAndEndsAtBefore(Giveaway.Status.RUNNING, Instant.now());
        for (Giveaway g : due) {
            try {
                drawAndAnnounce(g, false);
            } catch (Exception e) {
                log.warn("Auslosen von Giveaway {} fehlgeschlagen: {}", g.getId(), e.getMessage());
                g.setStatus(Giveaway.Status.ENDED); // nicht endlos erneut versuchen
                g.setEndedAt(Instant.now());
                repo.save(g);
            }
        }
    }

    /** Sofort beenden (Dashboard-Button). */
    @Transactional
    public Giveaway endNow(long id) {
        Giveaway g = mustFind(id);
        if (g.getStatus() != Giveaway.Status.RUNNING)
            throw new IllegalStateException("This giveaway isn't running.");
        drawAndAnnounce(g, false);
        return g;
    }

    /** Neu auslosen (Dashboard-Button) — zieht frisch aus allen Teilnehmern. */
    @Transactional
    public Giveaway reroll(long id) {
        Giveaway g = mustFind(id);
        if (g.getStatus() != Giveaway.Status.ENDED)
            throw new IllegalStateException("You can only reroll a finished giveaway.");
        drawAndAnnounce(g, true);
        return g;
    }

    /** Abbrechen — beendet ohne Gewinner und markiert das Embed als abgesagt. */
    @Transactional
    public Giveaway cancel(long id) {
        Giveaway g = mustFind(id);
        if (g.getStatus() == Giveaway.Status.ENDED)
            throw new IllegalStateException("This giveaway has already ended.");
        g.setStatus(Giveaway.Status.CANCELLED);
        g.setEndedAt(Instant.now());
        repo.save(g);
        editOriginal(g, cancelledEmbed(g), false);
        return g;
    }

    /** Zieht Gewinner, kündigt sie an, vergibt Rolle/DM und markiert das Embed als beendet. */
    private void drawAndAnnounce(Giveaway g, boolean reroll) {
        List<String> entrants = idList(g.getEntrants());
        List<String> winners = pickWinners(entrants, g.getWinnersCount());

        g.setWinnerIds(String.join(",", winners));
        g.setStatus(Giveaway.Status.ENDED);
        g.setEndedAt(Instant.now());
        repo.save(g);

        JDA jda = jdaHolder.get();
        if (jda == null) return;
        GuildMessageChannel channel = jda.getChannelById(GuildMessageChannel.class, safe(g.getChannelId()));

        // Original-Embed auf "beendet" aktualisieren, Button entfernen
        editOriginal(g, endedEmbed(g, winners), false);

        if (channel != null && channel.canTalk()) {
            String text;
            if (winners.isEmpty()) {
                text = "🎉 The giveaway for **" + safe(g.getPrize()) + "** ended — but there were no valid entries.";
            } else {
                String mentions = mentionList(winners);
                text = (reroll ? "🔁 **Reroll!** New " : "🎉 ") + winnerWord(winners.size())
                        + ": " + mentions + " — you won **" + safe(g.getPrize()) + "**!";
            }
            channel.sendMessage(text).queue(ok -> {}, err -> {});
        }

        // Rolle vergeben + DM verschicken (best effort)
        if (!winners.isEmpty()) {
            Guild guild = jda.getGuildById(g.getGuildId());
            for (String winnerId : winners) {
                if (guild != null && notBlank(g.getWinnerRoleId())) {
                    var role = guild.getRoleById(g.getWinnerRoleId().trim());
                    if (role != null) {
                        guild.addRoleToMember(net.dv8tion.jda.api.entities.UserSnowflake.fromId(winnerId), role)
                                .reason("Giveaway winner").queue(ok -> {}, err -> {});
                    }
                }
                if (notBlank(g.getDmMessage())) {
                    MessageEmbed dm = new EmbedBuilder()
                            .setColor(color(g))
                            .setTitle("🎉 You won a giveaway!")
                            .setDescription("**Prize:** " + safe(g.getPrize()) + "\n\n" + g.getDmMessage())
                            .setFooter(settings.brandName())
                            .setTimestamp(Instant.now())
                            .build();
                    jda.retrieveUserById(winnerId).queue(user ->
                            user.openPrivateChannel()
                                    .flatMap(dmChannel -> dmChannel.sendMessageEmbeds(dm))
                                    .queue(ok -> {}, err -> {}), err -> {});
                }
            }
        }
    }

    private static List<String> pickWinners(List<String> entrants, int count) {
        List<String> pool = new ArrayList<>(entrants);
        Collections.shuffle(pool);
        return pool.stream().limit(Math.max(1, count)).toList();
    }

    // ===================== Embeds =====================

    public MessageEmbed runningEmbed(Giveaway g) {
        long endEpoch = g.getEndsAt().getEpochSecond();
        EmbedBuilder eb = new EmbedBuilder()
                .setColor(color(g))
                .setTitle("🎉 GIVEAWAY: " + safe(g.getPrize()))
                .setDescription((notBlank(g.getDescription()) ? g.getDescription() + "\n\n" : "")
                        + "Click **Enter** below to join!\n\n"
                        + "🏆 **Winners:** " + Math.max(1, g.getWinnersCount()) + "\n"
                        + "⏰ **Ends:** <t:" + endEpoch + ":R> (<t:" + endEpoch + ":f>)")
                .setFooter(settings.brandName())
                .setTimestamp(g.getEndsAt());
        if (notBlank(g.getImageUrl())) eb.setImage(g.getImageUrl().trim());
        return eb.build();
    }

    private MessageEmbed endedEmbed(Giveaway g, List<String> winners) {
        EmbedBuilder eb = new EmbedBuilder()
                .setColor(color(g))
                .setTitle("🎉 GIVEAWAY ENDED: " + safe(g.getPrize()))
                .setDescription(winners.isEmpty()
                        ? "No valid entries — nobody won."
                        : "🏆 **" + winnerWord(winners.size()) + ":** " + mentionList(winners))
                .setFooter(settings.brandName())
                .setTimestamp(Instant.now());
        if (notBlank(g.getImageUrl())) eb.setImage(g.getImageUrl().trim());
        return eb.build();
    }

    private MessageEmbed cancelledEmbed(Giveaway g) {
        return new EmbedBuilder()
                .setColor(Color.GRAY)
                .setTitle("🚫 GIVEAWAY CANCELLED: " + safe(g.getPrize()))
                .setDescription("This giveaway was cancelled.")
                .setFooter(settings.brandName())
                .build();
    }

    public Button enterButton(Giveaway g) {
        return Button.of(ButtonStyle.SUCCESS, "gw:enter:" + g.getId(), "🎉 Enter");
    }

    /** Aktualisiert das ursprünglich gepostete Embed; optional bleibt der Button erhalten. */
    private void editOriginal(Giveaway g, MessageEmbed embed, boolean keepButton) {
        JDA jda = jdaHolder.get();
        if (jda == null || isBlank(g.getMessageId())) return;
        GuildMessageChannel channel = jda.getChannelById(GuildMessageChannel.class, safe(g.getChannelId()));
        if (channel == null) return;
        var action = channel.editMessageEmbedsById(g.getMessageId(), embed);
        if (keepButton) action = action.setActionRow(enterButton(g));
        else action = action.setComponents(); // Button entfernen
        action.queue(ok -> {}, err -> {});
    }

    // ===================== Helfer =====================

    private Giveaway mustFind(long id) {
        return repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Giveaway not found."));
    }

    public Color color(Giveaway g) {
        try {
            return Color.decode(g.getColor());
        } catch (Exception e) {
            return settings.brandColor();
        }
    }

    /** IDs aus kommagetrenntem String, dedupliziert und in Reihenfolge. */
    private static List<String> idList(String raw) {
        List<String> out = new ArrayList<>(new LinkedHashSet<>());
        if (raw == null || raw.isBlank()) return new ArrayList<>();
        for (String part : raw.split("[,\\s]+")) {
            if (!part.isBlank() && !out.contains(part.trim())) out.add(part.trim());
        }
        return out;
    }

    private static String mentionList(List<String> ids) {
        List<String> m = new ArrayList<>();
        for (String id : ids) m.add("<@" + id + ">");
        return String.join(", ", m);
    }

    private static String winnerWord(int n) {
        return n == 1 ? "Winner" : "Winners";
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }
    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
    private static String safe(String s) { return s == null ? "" : s.trim(); }
}
