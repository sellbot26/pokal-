package com.shop.bot;

import com.shop.config.ShopProperties;
import com.shop.model.*;
import com.shop.repo.*;
import com.shop.service.SettingsService;
import com.shop.service.StatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminCommands extends ListenerAdapter {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM. HH:mm")
            .withZone(ZoneId.systemDefault());

    private final ProductRepo productRepo;
    private final OrderRepo orderRepo;
    private final PaymentRepo paymentRepo;
    private final DiscountCodeRepo discountRepo;
    private final LicenseKeyRepo keyRepo;
    private final StatsService statsService;
    private final EmbedFactory embeds;
    private final ShopProperties props;
    private final SettingsService settings;

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("admin")) return;

        if (!isAdmin(event.getMember())) {
            event.replyEmbeds(embeds.error("You don't have permission for that.")).setEphemeral(true).queue();
            return;
        }
        if (event.getGuild() == null) {
            event.replyEmbeds(embeds.error("This command must be used in a server.")).setEphemeral(true).queue();
            return;
        }
        try {
            String group = event.getSubcommandGroup();
            String sub = event.getSubcommandName();
            if ("product".equals(group)) {
                switch (sub) {
                    case "add" -> productAdd(event);
                    case "edit" -> productEdit(event);
                    case "remove" -> productRemove(event);
                }
            } else if ("discount".equals(group)) {
                discountCreate(event);
            } else if ("orders".equals(group)) {
                ordersPending(event);
            } else if ("keys".equals(group)) {
                keysAdd(event);
            } else if ("stock".equals(sub)) {
                stock(event);
            } else if ("stats".equals(sub)) {
                stats(event);
            }
        } catch (Exception e) {
            log.error("Admin command failed", e);
            if (!event.isAcknowledged()) {
                event.replyEmbeds(embeds.error(e.getMessage() == null ? "Unknown error." : e.getMessage()))
                        .setEphemeral(true).queue();
            }
        }
    }

    private boolean isAdmin(Member member) {
        if (member == null) return false;
        if (member.hasPermission(Permission.ADMINISTRATOR)) return true;
        if (props.getDiscord().adminIdList().contains(member.getId())) return true;
        String adminRole = props.getDiscord().getAdminRoleId();
        return adminRole != null && !adminRole.isBlank()
                && member.getRoles().stream().anyMatch(r -> r.getId().equals(adminRole.trim()));
    }

    // ===================== Produkte (pro Discord-Server getrennt) =====================

    private void productAdd(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();
        String name = event.getOption("name").getAsString().trim();
        if (productRepo.findByGuildIdAndNameIgnoreCase(guildId, name).isPresent()) {
            event.replyEmbeds(embeds.error("A product with that name already exists on this server.")).setEphemeral(true).queue();
            return;
        }
        Product p = new Product();
        p.setGuildId(guildId);
        // Verkäufer merken — Zahlungen für dieses Produkt laufen auf dessen Wallet/Konto
        p.setOwnerId(event.getUser().getId());
        p.setName(name);
        p.setPrice(BigDecimal.valueOf(event.getOption("price").getAsDouble()).setScale(2, java.math.RoundingMode.HALF_UP));
        p.setDeliveryType(Product.DeliveryType.valueOf(event.getOption("delivery_type").getAsString()));
        if (event.getOption("category") != null) p.setCategory(event.getOption("category").getAsString());
        if (event.getOption("description") != null) p.setDescription(event.getOption("description").getAsString());
        if (event.getOption("stock") != null) p.setStock(event.getOption("stock").getAsInt());
        if (event.getOption("delivery_data") != null) p.setDeliveryData(event.getOption("delivery_data").getAsString());
        if (event.getOption("image_url") != null) p.setImageUrl(event.getOption("image_url").getAsString());
        productRepo.save(p);
        event.replyEmbeds(embeds.success("Product **" + p.getName() + "** created (" + p.getPrice() + " "
                + settings.currencySymbol() + ", " + p.getDeliveryType() + ").")).setEphemeral(true).queue();
    }

    private void productEdit(SlashCommandInteractionEvent event) {
        Product p = findProduct(event);
        if (p == null) return;
        if (event.getOption("price") != null)
            p.setPrice(BigDecimal.valueOf(event.getOption("price").getAsDouble()).setScale(2, java.math.RoundingMode.HALF_UP));
        if (event.getOption("category") != null) p.setCategory(event.getOption("category").getAsString());
        if (event.getOption("description") != null) p.setDescription(event.getOption("description").getAsString());
        if (event.getOption("delivery_data") != null) p.setDeliveryData(event.getOption("delivery_data").getAsString());
        if (event.getOption("image_url") != null) p.setImageUrl(event.getOption("image_url").getAsString());
        productRepo.save(p);
        event.replyEmbeds(embeds.success("Product **" + p.getName() + "** updated.")).setEphemeral(true).queue();
    }

    private void productRemove(SlashCommandInteractionEvent event) {
        Product p = findProduct(event);
        if (p == null) return;
        p.setActive(false);
        productRepo.save(p);
        event.replyEmbeds(embeds.success("Product **" + p.getName() + "** was removed from the shop."))
                .setEphemeral(true).queue();
    }

    private void stock(SlashCommandInteractionEvent event) {
        Product p = findProduct(event);
        if (p == null) return;
        int amount = event.getOption("quantity").getAsInt();
        p.setStock(Math.max(-1, amount));
        productRepo.save(p);
        event.replyEmbeds(embeds.success("Stock for **" + p.getName() + "** set to "
                + (p.getStock() == -1 ? "∞" : p.getStock()) + ".")).setEphemeral(true).queue();
    }

    private void keysAdd(SlashCommandInteractionEvent event) {
        Product p = findProduct(event);
        if (p == null) return;
        List<String> keys = Arrays.stream(event.getOption("keys").getAsString().split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
        keys.forEach(k -> keyRepo.save(new LicenseKey(p.getId(), k)));
        if (p.getDeliveryType() == Product.DeliveryType.KEY) {
            p.setStock((int) keyRepo.countByProductIdAndUsedFalse(p.getId()));
            productRepo.save(p);
        }
        event.replyEmbeds(embeds.success(keys.size() + " key(s) added to **" + p.getName() + "**. "
                + "Available: " + keyRepo.countByProductIdAndUsedFalse(p.getId()))).setEphemeral(true).queue();
    }

    /** Produkt im aktuellen Discord-Server suchen (Produktkatalog ist pro Server getrennt). */
    private Product findProduct(SlashCommandInteractionEvent event) {
        String name = event.getOption("product").getAsString();
        Product p = productRepo.findByGuildIdAndNameIgnoreCase(event.getGuild().getId(), name).orElse(null);
        if (p == null) {
            event.replyEmbeds(embeds.error("Product **" + name + "** not found.")).setEphemeral(true).queue();
        }
        return p;
    }

    // ===================== Rabatte =====================

    private void discountCreate(SlashCommandInteractionEvent event) {
        String code = event.getOption("code").getAsString().trim().toUpperCase();
        String guildId = event.getGuild().getId();
        if (discountRepo.findByGuildIdAndCodeIgnoreCase(guildId, code).isPresent()) {
            event.replyEmbeds(embeds.error("Code **" + code + "** already exists.")).setEphemeral(true).queue();
            return;
        }
        DiscountCode dc = new DiscountCode();
        dc.setGuildId(guildId);
        dc.setCode(code);
        dc.setPercent(event.getOption("percent").getAsInt());
        if (event.getOption("max_uses") != null) dc.setMaxUses(Math.max(0, event.getOption("max_uses").getAsInt()));
        if (event.getOption("valid_days") != null) {
            dc.setExpiresAt(Instant.now().plus(event.getOption("valid_days").getAsInt(), ChronoUnit.DAYS));
        }
        discountRepo.save(dc);
        event.replyEmbeds(embeds.success("Discount code **" + dc.getCode() + "** (" + dc.getPercent() + "%) created"
                + (dc.getMaxUses() > 0 ? ", max " + dc.getMaxUses() + " redemptions" : "")
                + (dc.getExpiresAt() != null ? ", valid until " + DATE.format(dc.getExpiresAt()) : "") + "."))
                .setEphemeral(true).queue();
    }

    // ===================== Statistiken & Bestellungen =====================

    private void stats(SlashCommandInteractionEvent event) {
        StatsService.Stats s = statsService.getStats();
        String cur = settings.currencySymbol();
        EmbedBuilder eb = embeds.base()
                .setTitle("📊 Shop Statistics")
                .addField("Total Revenue", s.revenueTotal().toPlainString() + " " + cur, true)
                .addField("Revenue Today", s.revenueToday().toPlainString() + " " + cur, true)
                .addField("Revenue this Month", s.revenueMonth().toPlainString() + " " + cur, true)
                .addField("Total Orders", String.valueOf(s.ordersTotal()), true)
                .addField("Orders Today", String.valueOf(s.ordersToday()), true)
                .addField("Pending Payments", String.valueOf(s.ordersPending()), true)
                .addField("Active Customers (30 days)", String.valueOf(s.activeCustomers()), true);
        if (!s.topProducts().isEmpty()) {
            StringBuilder top = new StringBuilder();
            int rank = 1;
            for (StatsService.TopProduct tp : s.topProducts()) {
                top.append(rank++).append(". **").append(tp.name()).append("** — ")
                        .append(tp.quantity()).append("x — ").append(tp.revenue().toPlainString()).append(" ").append(cur).append("\n");
            }
            eb.addField("Top Products", top.toString(), false);
        }
        event.replyEmbeds(eb.build()).setEphemeral(true).queue();
    }

    private void ordersPending(SlashCommandInteractionEvent event) {
        List<Order> pending = orderRepo.findByStatusOrderByCreatedAtDesc(Order.Status.PENDING);
        if (pending.isEmpty()) {
            event.replyEmbeds(embeds.success("No pending payments. 🎉")).setEphemeral(true).queue();
            return;
        }
        StringBuilder sb = new StringBuilder();
        pending.stream().limit(15).forEach(o -> {
            sb.append("**#").append(o.getId()).append("** • ").append(o.getProductName())
                    .append(" x").append(o.getQuantity()).append(" • ").append(o.getTotalPrice()).append(" ")
                    .append(settings.currencySymbol()).append(" • <@")
                    .append(o.getUserId()).append("> • ").append(DATE.format(o.getCreatedAt()));
            paymentRepo.findByOrderId(o.getId()).ifPresent(p ->
                    sb.append(" • ").append(p.getPayCurrency()));
            sb.append("\n");
        });
        event.replyEmbeds(embeds.base()
                .setTitle("🕐 Pending Payments (" + pending.size() + ")")
                .setDescription(sb.toString())
                .build()).setEphemeral(true).queue();
    }
}
