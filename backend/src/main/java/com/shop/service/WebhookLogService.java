package com.shop.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Schickt Verkaufs-/Ereignis-Logs als Embed an eine Discord-Webhook-URL. */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookLogService {

    private final RestClient client = RestClient.create();
    private final SettingsService settings;

    /** Best effort — ein kaputter Webhook darf niemals eine Lieferung blockieren. */
    @Async
    public void send(String webhookUrl, String title, String description, int color) {
        if (webhookUrl == null || webhookUrl.isBlank()) return;
        if (!webhookUrl.startsWith("https://discord.com/api/webhooks/")
                && !webhookUrl.startsWith("https://discordapp.com/api/webhooks/")) {
            return;
        }
        try {
            Map<String, Object> body = Map.of(
                    "username", settings.brandName(),
                    "embeds", List.of(Map.of(
                            "title", title,
                            "description", description,
                            "color", color,
                            "timestamp", Instant.now().toString(),
                            "footer", Map.of("text", settings.brandName())
                    ))
            );
            client.post().uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Webhook log failed: {}", e.getMessage());
        }
    }

    public void success(String webhookUrl, String title, String description) {
        send(webhookUrl, title, description, 0x57F287);
    }
}
