package com.shop.web;

import com.shop.model.Order;
import com.shop.model.Product;
import com.shop.model.ShopUser;
import com.shop.repo.OrderRepo;
import com.shop.repo.ProductRepo;
import com.shop.repo.ShopUserRepo;
import com.shop.service.DeliveryService;
import com.shop.service.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * DM-Broadcast: schickt allen Kunden eine Discord-DM (Ankündigungen, Restock, Rabatte).
 * Verkäufer erreichen nur ihre eigenen Käufer, der Site-Admin alle Nutzer. Gast-Käufe
 * (ohne Discord-Login) und gesperrte Nutzer werden übersprungen.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class BroadcastApiController {

    public record BroadcastRequest(String title, String message) {}

    private final OrderRepo orderRepo;
    private final ProductRepo productRepo;
    private final ShopUserRepo userRepo;
    private final DeliveryService deliveryService;
    private final SettingsService settings;

    @PostMapping("/api/my/broadcast")
    public Map<String, Object> broadcastToMyCustomers(@RequestBody BroadcastRequest req,
                                                      @AuthenticationPrincipal OAuth2User principal) {
        String myId = principal.getAttribute("id");
        Set<String> recipients = new LinkedHashSet<>();
        for (Order o : orderRepo.findAll()) {
            Product p = productRepo.findById(o.getProductId()).orElse(null);
            if (p != null && myId.equals(p.getOwnerId())) recipients.add(o.getUserId());
        }
        return send(req, recipients);
    }

    @PostMapping("/api/admin/broadcast")
    public Map<String, Object> broadcastToAll(@RequestBody BroadcastRequest req) {
        Set<String> recipients = new LinkedHashSet<>();
        userRepo.findAll().forEach(u -> recipients.add(u.getId()));
        return send(req, recipients);
    }

    private Map<String, Object> send(BroadcastRequest req, Set<String> recipients) {
        String message = req.message() == null ? "" : req.message().trim();
        if (message.isEmpty()) throw new IllegalArgumentException("Please enter a message to send.");
        if (message.length() > 3800) throw new IllegalArgumentException("Message is too long (max 3800 characters).");

        String title = req.title() != null && !req.title().isBlank()
                ? req.title().trim() : "📢 " + settings.brandName();
        MessageEmbed embed = new EmbedBuilder()
                .setTitle(title)
                .setDescription(message)
                .setColor(settings.brandColor())
                .setFooter(settings.brandName())
                .setTimestamp(Instant.now())
                .build();

        int sent = 0;
        for (String userId : recipients) {
            // Gast-Bestellungen haben keine echte Discord-ID → nicht anschreibbar
            if (userId == null || userId.startsWith("guest-")) continue;
            ShopUser user = userRepo.findById(userId).orElse(null);
            if (user != null && user.isBanned()) continue;
            deliveryService.sendDm(userId, embed);
            sent++;
        }
        log.info("Broadcast an {} Empfänger verschickt (Titel: {})", sent, title);
        return Map.of("recipients", sent);
    }
}
