package com.shop.bot;

import com.shop.service.SettingsService;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.time.Instant;

/** Einheitliches Embed-Design mit Shop-Branding (Farbe, Name, Footer) aus den Einstellungen. */
@Component
@RequiredArgsConstructor
public class EmbedFactory {

    private final SettingsService settings;

    public Color color() {
        return settings.brandColor();
    }

    public EmbedBuilder base() {
        return new EmbedBuilder()
                .setColor(color())
                .setFooter(settings.brandName())
                .setTimestamp(Instant.now());
    }

    public MessageEmbed error(String message) {
        return new EmbedBuilder()
                .setColor(new Color(0xED4245))
                .setDescription("❌ " + message)
                .setFooter(settings.brandName())
                .build();
    }

    public MessageEmbed success(String message) {
        return new EmbedBuilder()
                .setColor(new Color(0x57F287))
                .setDescription("✅ " + message)
                .setFooter(settings.brandName())
                .build();
    }
}
