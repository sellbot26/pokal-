package com.shop.service;

import com.shop.bot.JdaHolder;
import com.shop.config.ShopProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.time.Instant;

/** Schreibt Kauf-/Fehler-Logs in den konfigurierten Discord-Log-Channel. */
@Service
@RequiredArgsConstructor
@Slf4j
public class BotLogService {

    private final JdaHolder jda;
    private final ShopProperties props;
    private final SettingsService settings;
    private final WebhookLogService webhookLog;

    /** Kategorie eines Log-Eintrags — steuert, ob er laut Dashboard-Einstellung geschrieben wird. */
    private enum Kind { SALE, ORDER, ERROR, INFO }

    public void success(String title, String description) {
        send(title, description, new Color(0x57F287), Kind.SALE);
    }

    public void error(String title, String description) {
        send(title, description, new Color(0xED4245), Kind.ERROR);
    }

    public void info(String title, String description) {
        send(title, description, new Color(0x5865F2), Kind.INFO);
    }

    /** Log für eine neue (noch unbezahlte) Bestellung — separat abschaltbar. */
    public void order(String title, String description) {
        send(title, description, new Color(0x5865F2), Kind.ORDER);
    }

    private void send(String title, String description, Color color, Kind kind) {
        if (kind == Kind.SALE && !settings.logSales()) return;
        if (kind == Kind.ORDER && !settings.logOrders()) return;
        if (kind == Kind.ERROR && !settings.logErrors()) return;

        // Site-weiter Discord-Webhook bekommt JEDEN Log-Eintrag (unabhängig vom Bot-Channel).
        webhookLog.send(settings.siteLogWebhookUrl(), title, description, color.getRGB() & 0xFFFFFF);

        String channelId = settings.logChannelId();
        if (!jda.isReady() || channelId == null || channelId.isBlank()) return;
        try {
            TextChannel channel = jda.get().getTextChannelById(channelId);
            if (channel == null) return;
            channel.sendMessageEmbeds(new EmbedBuilder()
                    .setTitle(title)
                    .setDescription(description)
                    .setColor(color)
                    .setTimestamp(Instant.now())
                    .setFooter(props.getBrandName())
                    .build()).queue(ok -> {}, err -> log.warn("Log-Channel nicht beschreibbar: {}", err.getMessage()));
        } catch (Exception e) {
            log.warn("Logging in Discord fehlgeschlagen", e);
        }
    }
}
