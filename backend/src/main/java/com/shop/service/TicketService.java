package com.shop.service;

import com.shop.model.TicketConfig;
import com.shop.repo.TicketConfigRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.FileUpload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Consumer;

/**
 * Ticket-System: Konfiguration pro Server (Dashboard), Ticket öffnen (Slash-Command
 * oder Panel-Button) mit Limit pro Nutzer, Schließen mit optionalem Transcript.
 *
 * Offene Tickets werden über einen Marker im Channel-Topic erkannt ("uid:<userId>") —
 * das übersteht Bot-Neustarts ohne eigene Ticket-Tabelle.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TicketService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
            .withZone(ZoneId.systemDefault());
    /** Max. Nachrichten, die ins Transcript geladen werden. */
    private static final int TRANSCRIPT_LIMIT = 500;

    private final TicketConfigRepo repo;
    private final SettingsService settings;
    private final com.shop.config.ShopProperties props;

    // ===================== Konfiguration =====================

    /** Konfiguration für einen Server — mit sinnvollen Defaults, falls nichts gespeichert ist. */
    public TicketConfig configFor(String guildId) {
        TicketConfig c = repo.findById(guildId).orElseGet(() -> new TicketConfig(guildId));
        // Legacy-Fallback: .env-Werte greifen, solange im Dashboard nichts gesetzt ist
        if (isBlank(c.getCategoryId())) c.setCategoryId(props.getDiscord().getTicketCategoryId());
        if (isBlank(c.getSupportRoleIds())) c.setSupportRoleIds(props.getDiscord().getSupportRoleId());
        return c;
    }

    @Transactional
    public TicketConfig save(TicketConfig config) {
        config.setUpdatedAt(Instant.now());
        return repo.save(config);
    }

    // ===================== Ticket öffnen =====================

    /** Prüft das Offen-Limit des Nutzers. Liefert eine Fehlermeldung oder null, wenn er öffnen darf. */
    public String openLimitError(Guild guild, User user) {
        TicketConfig cfg = configFor(guild.getId());
        int limit = cfg.getMaxOpenPerUser();
        if (limit > 0) {
            long open = countOpenTickets(guild, user.getId());
            if (open >= limit) {
                return limit == 1
                        ? "You already have an open ticket — please use that one."
                        : "You already have " + open + " open tickets (limit: " + limit + ").";
            }
        }
        return null;
    }

    public void open(Guild guild, User user, Consumer<String> onSuccess, Consumer<String> onError) {
        open(guild, user, null, null, onSuccess, onError);
    }

    /**
     * Öffnet ein Ticket für den Nutzer. Antworten laufen über die Callbacks, damit
     * Slash-Command und Panel-Button denselben Code nutzen können.
     * Reason/Details kommen aus dem Ticket-Modal und landen im Welcome-Embed.
     */
    public void open(Guild guild, User user, String reason, String details,
                     Consumer<String> onSuccess, Consumer<String> onError) {
        TicketConfig cfg = configFor(guild.getId());

        String limitError = openLimitError(guild, user);
        if (limitError != null) {
            onError.accept(limitError);
            return;
        }

        String prefix = isBlank(cfg.getNamePrefix()) ? "ticket" : cfg.getNamePrefix().toLowerCase()
                .replaceAll("[^a-z0-9-]", "");
        if (prefix.isBlank()) prefix = "ticket";
        String cleaned = prefix + "-" + user.getName().toLowerCase().replaceAll("[^a-z0-9]", "");
        String channelName = cleaned.substring(0, Math.min(90, cleaned.length()));

        // Marker im Topic: daran werden offene Tickets + Ersteller erkannt (übersteht Neustarts)
        String topic = "🎫 Ticket • uid:" + user.getId() + " • opened " + TS.format(Instant.now());
        if (!isBlank(reason)) {
            String shortReason = reason.trim().replaceAll("\\s+", " ");
            if (shortReason.length() > 60) shortReason = shortReason.substring(0, 60) + "…";
            topic += " • " + shortReason;
        }
        var action = guild.createTextChannel(channelName).setTopic(topic);

        if (!isBlank(cfg.getCategoryId())) {
            Category category = guild.getCategoryById(cfg.getCategoryId().trim());
            if (category != null) action = action.setParent(category);
        }
        action = action
                .addPermissionOverride(guild.getPublicRole(), null, EnumSet.of(Permission.VIEW_CHANNEL))
                .addMemberPermissionOverride(user.getIdLong(),
                        EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY), null)
                .addMemberPermissionOverride(guild.getSelfMember().getIdLong(),
                        EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY,
                                Permission.MANAGE_CHANNEL), null);

        StringBuilder mention = new StringBuilder(user.getAsMention());
        for (String roleId : splitIds(cfg.getSupportRoleIds())) {
            try {
                action = action.addRolePermissionOverride(Long.parseLong(roleId),
                        EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY), null);
                if (cfg.isMentionSupport()) mention.append(" <@&").append(roleId).append(">");
            } catch (NumberFormatException ignored) {
            }
        }

        String title = isBlank(cfg.getWelcomeTitle()) ? "🎫 Support Ticket" : cfg.getWelcomeTitle();
        String message = isBlank(cfg.getWelcomeMessage())
                ? "Describe your issue — the team will get back to you as soon as possible."
                : cfg.getWelcomeMessage();
        EmbedBuilder welcomeBuilder = new EmbedBuilder()
                .setColor(panelColor(cfg))
                .setTitle(title)
                .setDescription(message
                        .replace("{user}", user.getAsMention())
                        .replace("{server}", guild.getName()))
                .setFooter(settings.brandName())
                .setTimestamp(Instant.now());
        if (!isBlank(reason)) {
            welcomeBuilder.addField("📌 Reason", reason.trim(), false);
        }
        if (!isBlank(details)) {
            String text = details.trim();
            if (text.length() > 1000) text = text.substring(0, 1000) + "…";
            welcomeBuilder.addField("📝 Message", text, false);
        }
        MessageEmbed welcome = welcomeBuilder.build();

        final String mentions = mention.toString();
        action.queue(channel -> {
            channel.sendMessage(mentions)
                    .addEmbeds(welcome)
                    .addActionRow(Button.danger("ticket:close", "Close Ticket"))
                    .queue();
            onSuccess.accept(channel.getAsMention());
        }, err -> onError.accept("Ticket could not be created: " + err.getMessage()));
    }

    /** Offene Tickets eines Nutzers — erkannt am uid-Marker im Channel-Topic. */
    public long countOpenTickets(Guild guild, String userId) {
        String marker = "uid:" + userId;
        return guild.getTextChannels().stream()
                .filter(c -> c.getTopic() != null && c.getTopic().contains(marker))
                .count();
    }

    // ===================== Ticket schließen =====================

    /**
     * Schließt einen Ticket-Channel: optional Transcript erzeugen (Transcript-Channel + DM),
     * danach den Channel löschen.
     */
    public void close(TextChannel channel, User closedBy) {
        TicketConfig cfg = configFor(channel.getGuild().getId());
        if (!cfg.isTranscriptEnabled()) {
            channel.delete().reason("Ticket closed by " + closedBy.getName())
                    .queueAfter(2, java.util.concurrent.TimeUnit.SECONDS);
            return;
        }
        channel.getIterableHistory().takeAsync(TRANSCRIPT_LIMIT).thenAccept(messages -> {
            try {
                sendTranscript(channel, closedBy, cfg, messages);
            } catch (Exception e) {
                log.warn("Transcript für #{} fehlgeschlagen: {}", channel.getName(), e.getMessage());
            }
            channel.delete().reason("Ticket closed by " + closedBy.getName())
                    .queueAfter(2, java.util.concurrent.TimeUnit.SECONDS);
        }).exceptionally(err -> {
            log.warn("Ticket-History für #{} nicht ladbar: {}", channel.getName(), err.getMessage());
            channel.delete().reason("Ticket closed by " + closedBy.getName())
                    .queueAfter(2, java.util.concurrent.TimeUnit.SECONDS);
            return null;
        });
    }

    private void sendTranscript(TextChannel channel, User closedBy, TicketConfig cfg, List<Message> history) {
        List<Message> messages = new ArrayList<>(history);
        Collections.reverse(messages); // älteste zuerst

        StringBuilder sb = new StringBuilder();
        sb.append("Transcript of #").append(channel.getName())
                .append(" (").append(channel.getGuild().getName()).append(")\n")
                .append("Closed by ").append(closedBy.getName())
                .append(" at ").append(TS.format(Instant.now())).append("\n")
                .append("=".repeat(60)).append("\n\n");
        for (Message m : messages) {
            sb.append("[").append(TS.format(m.getTimeCreated().toInstant())).append("] ")
                    .append(m.getAuthor().getName()).append(": ")
                    .append(m.getContentDisplay());
            for (MessageEmbed e : m.getEmbeds()) {
                sb.append("\n    [embed] ").append(e.getTitle() == null ? "" : e.getTitle());
                if (e.getDescription() != null) sb.append(" — ").append(e.getDescription());
            }
            for (Message.Attachment a : m.getAttachments()) {
                sb.append("\n    [attachment] ").append(a.getUrl());
            }
            sb.append("\n");
        }
        byte[] file = sb.toString().getBytes(StandardCharsets.UTF_8);
        String fileName = "transcript-" + channel.getName() + ".txt";

        String openerId = openerIdFromTopic(channel);
        MessageEmbed info = new EmbedBuilder()
                .setColor(panelColor(cfg))
                .setTitle("📄 Ticket Transcript")
                .setDescription("**Channel:** #" + channel.getName()
                        + "\n**Opened by:** " + (openerId == null ? "unknown" : "<@" + openerId + ">")
                        + "\n**Closed by:** " + closedBy.getAsMention()
                        + "\n**Messages:** " + messages.size())
                .setFooter(settings.brandName())
                .setTimestamp(Instant.now())
                .build();

        // 1) In den konfigurierten Transcript-Channel
        if (!isBlank(cfg.getTranscriptChannelId())) {
            GuildMessageChannel target = channel.getGuild()
                    .getChannelById(GuildMessageChannel.class, cfg.getTranscriptChannelId().trim());
            if (target != null && target.canTalk()) {
                target.sendMessageEmbeds(info)
                        .addFiles(FileUpload.fromData(file, fileName))
                        .queue(ok -> {}, err -> log.warn("Transcript-Channel nicht beschreibbar: {}", err.getMessage()));
            } else {
                log.warn("Transcript-Channel {} in '{}' nicht gefunden/beschreibbar.",
                        cfg.getTranscriptChannelId(), channel.getGuild().getName());
            }
        }
        // 2) Optional dem Ticket-Ersteller per DM (best effort)
        if (cfg.isTranscriptDmUser() && openerId != null) {
            channel.getJDA().retrieveUserById(openerId).queue(user ->
                    user.openPrivateChannel()
                            .flatMap(dm -> dm.sendMessageEmbeds(info).addFiles(FileUpload.fromData(file, fileName)))
                            .queue(ok -> {}, err -> {}), err -> {});
        }
    }

    /** Ersteller-ID aus dem Topic-Marker ("uid:<id>") lesen. */
    private String openerIdFromTopic(TextChannel channel) {
        String topic = channel.getTopic();
        if (topic == null) return null;
        var m = java.util.regex.Pattern.compile("uid:(\\d+)").matcher(topic);
        return m.find() ? m.group(1) : null;
    }

    // ===================== Panel =====================

    /** Baut das Ticket-Panel-Embed (für Channel-Post und Dashboard-Vorschau identisch). */
    public MessageEmbed panelEmbed(TicketConfig cfg) {
        EmbedBuilder eb = new EmbedBuilder()
                .setColor(panelColor(cfg))
                .setTitle(isBlank(cfg.getPanelTitle()) ? "🎫 Support" : cfg.getPanelTitle())
                .setDescription(isBlank(cfg.getPanelDescription())
                        ? "Need help? Click the button below to open a private ticket with our team."
                        : cfg.getPanelDescription())
                .setFooter(settings.brandName());
        if (!isBlank(cfg.getPanelImageUrl())) eb.setImage(cfg.getPanelImageUrl().trim());
        if (!isBlank(cfg.getPanelThumbnailUrl())) eb.setThumbnail(cfg.getPanelThumbnailUrl().trim());
        return eb.build();
    }

    /** Der "Open Ticket"-Button unter dem Panel. */
    public Button panelButton(TicketConfig cfg) {
        Button button = Button.primary("ticket:open",
                isBlank(cfg.getButtonLabel()) ? "🎫 Open Ticket" : cfg.getButtonLabel());
        if (!isBlank(cfg.getButtonEmoji())) {
            try {
                button = button.withEmoji(Emoji.fromFormatted(cfg.getButtonEmoji().trim()));
            } catch (Exception ignored) { // ungültiges Emoji → Button ohne Emoji
            }
        }
        return button;
    }

    public Color panelColor(TicketConfig cfg) {
        try {
            return Color.decode(cfg.getPanelColor());
        } catch (Exception e) {
            return settings.brandColor();
        }
    }

    // ===================== Helfer =====================

    public static List<String> splitIds(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        List<String> result = new ArrayList<>();
        for (String part : raw.split("[,\\s]+")) {
            if (!part.isBlank()) result.add(part.trim());
        }
        return result;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
