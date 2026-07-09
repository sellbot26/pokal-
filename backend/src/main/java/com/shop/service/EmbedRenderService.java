package com.shop.service;

import com.fasterxml.jackson.databind.JsonNode;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Wandelt das JSON aus dem Dashboard-Embed-Editor in ein Discord-Embed um.
 * Alle Discord-Limits (Titel 256, Beschreibung 4096, 25 Felder, …) werden hart begrenzt.
 */
@Service
public class EmbedRenderService {

    public MessageEmbed render(JsonNode node) {
        EmbedBuilder eb = new EmbedBuilder();

        String title = text(node, "title", 256);
        if (title != null) eb.setTitle(title, url(node, "url"));

        String description = text(node, "description", 4096);
        if (description != null) eb.setDescription(description);

        String color = text(node, "color", 16);
        if (color != null) {
            try {
                eb.setColor(Color.decode(color));
            } catch (Exception ignored) {
            }
        }

        String thumbnail = url(node, "thumbnail");
        if (thumbnail != null) eb.setThumbnail(thumbnail);

        String image = url(node, "image");
        if (image != null) eb.setImage(image);

        String footer = text(node, "footer", 2048);
        if (footer != null) eb.setFooter(footer, url(node, "footerIcon"));

        String author = text(node, "author", 256);
        if (author != null) eb.setAuthor(author, url(node, "authorUrl"), url(node, "authorIcon"));

        if (node.path("timestamp").asBoolean(false)) eb.setTimestamp(Instant.now());

        JsonNode fields = node.path("fields");
        if (fields.isArray()) {
            int count = 0;
            for (JsonNode field : fields) {
                if (++count > 25) break;
                String name = text(field, "name", 256);
                String value = text(field, "value", 1024);
                if (name == null || value == null) continue;
                eb.addField(name, value, field.path("inline").asBoolean(false));
            }
        }

        if (eb.isEmpty()) throw new IllegalArgumentException("Embed ist leer — mindestens Titel oder Beschreibung angeben.");
        return eb.build();
    }

    /**
     * Buttons unter dem Embed (max. 25 = 5 Reihen à 5).
     * Zwei Typen: Link-Button (url) oder Kauf-Button (productId) — der Kauf-Button
     * nutzt die bestehende buy:start-Interaktion des Bots und startet den Checkout.
     */
    public List<Button> buttons(JsonNode node) {
        List<Button> result = new ArrayList<>();
        JsonNode buttons = node.path("buttons");
        if (!buttons.isArray()) return result;
        for (JsonNode btn : buttons) {
            if (result.size() >= 25) break;
            String label = text(btn, "label", 80);
            if (label == null) continue;

            Button button;
            long productId = btn.path("productId").asLong(0);
            if (productId > 0) {
                button = Button.of(buttonStyle(text(btn, "style", 16)), "buy:start:" + productId, label);
            } else {
                String url = url(btn, "url");
                if (url == null) continue;
                button = Button.link(url, label);
            }

            String emoji = text(btn, "emoji", 32);
            if (emoji != null) {
                try {
                    button = button.withEmoji(Emoji.fromFormatted(emoji));
                } catch (Exception ignored) {
                }
            }
            result.add(button);
        }
        return result;
    }

    private net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle buttonStyle(String style) {
        return switch (style == null ? "success" : style) {
            case "primary" -> net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle.PRIMARY;
            case "secondary" -> net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle.SECONDARY;
            case "danger" -> net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle.DANGER;
            default -> net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle.SUCCESS;
        };
    }

    public String content(JsonNode node) {
        return text(node, "content", 2000);
    }

    private String text(JsonNode node, String key, int maxLength) {
        String value = node.path(key).asText(null);
        if (value == null || value.isBlank()) return null;
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    private String url(JsonNode node, String key) {
        String value = text(node, key, 2000);
        if (value == null) return null;
        return value.startsWith("http://") || value.startsWith("https://") ? value : null;
    }
}
