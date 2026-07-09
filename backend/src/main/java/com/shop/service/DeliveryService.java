package com.shop.service;

import com.shop.bot.JdaHolder;
import com.shop.config.ShopProperties;
import com.shop.model.LicenseKey;
import com.shop.model.Order;
import com.shop.model.Product;
import com.shop.model.ShopUser;
import com.shop.repo.LicenseKeyRepo;
import com.shop.repo.ProductRepo;
import com.shop.repo.ShopUserRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.UserSnowflake;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryService {

    private final JdaHolder jda;
    private final ProductRepo productRepo;
    private final LicenseKeyRepo keyRepo;
    private final ShopProperties props;
    private final PlanService planService;
    private final ShopUserRepo userRepo;

    /**
     * Liefert das Produkt aus (Rolle, Key, Text oder Datei-Link) und gibt den Text zurück,
     * der dem Käufer per DM geschickt wird.
     */
    @Transactional
    public String deliver(Order order) {
        Product product = productRepo.findById(order.getProductId()).orElse(null);
        if (product == null) {
            return "⚠️ This product no longer exists. Please open a ticket (/ticket).";
        }
        String message = switch (product.getDeliveryType()) {
            case ROLE -> deliverRole(order, product);
            case KEY -> deliverKeys(order, product);
            case TEXT -> product.getDeliveryData() != null ? product.getDeliveryData()
                    : "Thank you for your purchase!";
            case FILE -> "📁 Your download: " + product.getDeliveryData();
            case PLAN_UNLOCK -> deliverPlanUnlock(order, product);
        };
        // "How to use"-Anleitung wird immer mitgeliefert, egal welche Lieferart
        if (product.getInstructions() != null && !product.getInstructions().isBlank()) {
            message += "\n\n📖 **How to use:**\n" + product.getInstructions();
        }
        return message;
    }

    private String deliverRole(Order order, Product product) {
        try {
            // Rolle gehört zum Server des Produkts, nicht zwingend zum primär konfigurierten Server
            String guildId = product.getGuildId() != null ? product.getGuildId() : props.getDiscord().getGuildId();
            Guild guild = jda.get().getGuildById(guildId);
            Role role = guild.getRoleById(product.getDeliveryData().trim());
            guild.addRoleToMember(UserSnowflake.fromId(order.getUserId()), role).queue();
            return "🎖️ You've been given the **" + role.getName() + "** role.";
        } catch (Exception e) {
            log.error("Role assignment failed for order #{}", order.getId(), e);
            return "⚠️ The role could not be assigned automatically. Please open a ticket (/ticket).";
        }
    }

    private String deliverKeys(Order order, Product product) {
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < order.getQuantity(); i++) {
            var key = keyRepo.findFirstByProductIdAndUsedFalse(product.getId());
            if (key.isEmpty()) break;
            LicenseKey k = key.get();
            k.setUsed(true);
            k.setOrderId(order.getId());
            keyRepo.save(k);
            keys.add(k.getKeyValue());
        }
        if (keys.isEmpty()) {
            return "⚠️ No keys left in stock. Please open a ticket (/ticket) — we'll take care of it right away.";
        }
        StringBuilder sb = new StringBuilder();
        // Eigene Liefer-Nachricht des Verkäufers zuerst — der Key kommt trotzdem immer mit
        if (product.getDeliveryData() != null && !product.getDeliveryData().isBlank()) {
            sb.append(product.getDeliveryData()).append("\n\n");
        }
        sb.append("🔑 Your key").append(keys.size() > 1 ? "s" : "").append(":\n");
        keys.forEach(k -> sb.append("`").append(k).append("`\n"));
        if (keys.size() < order.getQuantity()) {
            sb.append("\n⚠️ Only ").append(keys.size()).append(" of ").append(order.getQuantity())
              .append(" keys were available. Please open a ticket for the rest.");
        }
        return sb.toString();
    }

    private String deliverPlanUnlock(Order order, Product product) {
        String tierId = product.getDeliveryData();
        ShopUser buyer = userRepo.findById(order.getUserId()).orElse(null);
        if (buyer == null) {
            log.error("Plan purchase for unknown ShopUser {}", order.getUserId());
            return "⚠️ Could not upgrade your plan — please open a ticket (/ticket).";
        }
        // Laufzeit aus dem Produktnamen ableiten: "… (Yearly)" = 365 Tage, sonst 30 Tage
        int days = product.getName() != null && product.getName().toLowerCase().contains("year") ? 365 : 30;
        planService.unlockForUser(buyer, tierId, days);
        return "🚀 Your dashboard has been upgraded to **" + planService.tier(tierId).name()
                + "** for " + (days >= 365 ? "1 year" : "1 month")
                + ". Refresh the dashboard to see your new limits.";
    }

    /** Schickt einem Nutzer eine DM (best effort — DMs können deaktiviert sein). */
    public void sendDm(String userId, MessageEmbed embed) {
        if (!jda.isReady()) return;
        jda.get().retrieveUserById(userId)
                .flatMap(User::openPrivateChannel)
                .flatMap(ch -> ch.sendMessageEmbeds(embed))
                .queue(ok -> {}, err -> log.warn("DM to {} failed: {}", userId, err.getMessage()));
    }
}
