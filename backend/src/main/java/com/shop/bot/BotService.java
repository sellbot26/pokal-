package com.shop.bot;

import com.shop.config.ShopProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/** Startet den Bot nach dem App-Start und registriert alle Slash-Commands. */
@Component
@RequiredArgsConstructor
@Slf4j
public class BotService {

    private final ShopProperties props;
    private final JdaHolder holder;
    private final ShopCommands shopCommands;
    private final AdminCommands adminCommands;
    private final MemberJoinListener memberJoinListener;
    private final GuildJoinListener guildJoinListener;

    @EventListener(ApplicationReadyEvent.class)
    public void start() throws InterruptedException {
        String token = props.getDiscord().getToken();
        if (token == null || token.isBlank()) {
            log.warn("Kein DISCORD_BOT_TOKEN gesetzt — der Bot wird nicht gestartet (Dashboard läuft trotzdem).");
            return;
        }
        // Für Auto-Role bei Server-Join wird das privilegierte "Server Members Intent" gebraucht
        // (Discord Developer Portal → Bot → Privileged Gateway Intents). Ist es nicht freigeschaltet,
        // startet der Bot trotzdem — nur ohne Auto-Role.
        JDA jda;
        try {
            jda = JDABuilder.createDefault(token)
                    .enableIntents(net.dv8tion.jda.api.requests.GatewayIntent.GUILD_MEMBERS)
                    .setActivity(Activity.watching(props.getBrandName()))
                    .addEventListeners(shopCommands, adminCommands, memberJoinListener, guildJoinListener)
                    .build()
                    .awaitReady();
        } catch (Exception e) {
            log.warn("Bot-Start mit GUILD_MEMBERS-Intent fehlgeschlagen ({}). Auto-Role ist deaktiviert — "
                    + "aktiviere das 'Server Members Intent' im Discord Developer Portal. Starte ohne Intent…",
                    e.getMessage());
            // Auto-Role läuft trotzdem — als Fallback bei jeder Bot-Interaktion (ohne Intent).
            jda = JDABuilder.createDefault(token)
                    .setActivity(Activity.watching(props.getBrandName()))
                    .addEventListeners(shopCommands, adminCommands, memberJoinListener, guildJoinListener)
                    .build()
                    .awaitReady();
        }
        holder.set(jda);
        registerCommands(jda);
        log.info("Discord-Bot gestartet als {}", jda.getSelfUser().getName());
    }

    private void registerCommands(JDA jda) {
        List<CommandData> commands = buildAllCommands();
        var guilds = jda.getGuilds();
        if (guilds.isEmpty()) {
            jda.updateCommands().addCommands(commands).queue();
            return;
        }
        // In JEDEM Server frisch registrieren (sofort verfügbar + immer aktuelle Options-Definitionen).
        // Verhindert "null option"-Crashes durch veraltete Command-Definitionen in einzelnen Servern.
        for (Guild g : guilds) {
            g.updateCommands().addCommands(commands).queue(
                    ok -> log.info("Slash-Commands in '{}' registriert", g.getName()),
                    err -> log.warn("Command-Registrierung in '{}' fehlgeschlagen: {}", g.getName(), err.getMessage()));
        }
        // Veraltete GLOBALE Commands entfernen, damit keine alten Definitionen doppelt existieren.
        jda.updateCommands().queue();
    }

    /** Komplette Command-Liste — auch vom {@link GuildJoinListener} für neue Server genutzt. */
    static List<CommandData> buildAllCommands() {
        return List.of(
                Commands.slash("shop", "Shows the shop with all categories"),

                Commands.slash("product", "Shows details for a product")
                        .addOptions(productOption()),

                Commands.slash("buy", "Buy a product with crypto or card")
                        .addOptions(productOption(),
                                new OptionData(OptionType.INTEGER, "quantity", "Quantity (default: 1)", false)
                                        .setRequiredRange(1, 100),
                                new OptionData(OptionType.STRING, "discount_code", "Discount code (optional)", false)),

                Commands.slash("orders", "Shows your order history"),

                Commands.slash("review", "Leave a review for a product you bought")
                        .addOptions(productOption(),
                                new OptionData(OptionType.INTEGER, "stars", "Rating (1-5)", true)
                                        .setRequiredRange(1, 5),
                                new OptionData(OptionType.STRING, "text", "Your review (optional)", false)),

                Commands.slash("ticket", "Opens a support ticket"),

                Commands.slash("admin", "Shop management (staff only)")
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
                        .addSubcommandGroups(
                                new SubcommandGroupData("product", "Product management")
                                        .addSubcommands(
                                                new SubcommandData("add", "Create a product").addOptions(
                                                        new OptionData(OptionType.STRING, "name", "Product name", true),
                                                        new OptionData(OptionType.NUMBER, "price", "Price", true).setMinValue(0.01),
                                                        new OptionData(OptionType.STRING, "delivery_type", "How should it be delivered?", true)
                                                                .addChoice("Assign role", "ROLE")
                                                                .addChoice("License key from pool", "KEY")
                                                                .addChoice("Text via DM", "TEXT")
                                                                .addChoice("File/download link", "FILE"),
                                                        new OptionData(OptionType.STRING, "category", "Category", false),
                                                        new OptionData(OptionType.STRING, "description", "Description", false),
                                                        new OptionData(OptionType.INTEGER, "stock", "Stock (-1 = unlimited)", false),
                                                        new OptionData(OptionType.STRING, "delivery_data", "Role ID / text / file URL", false),
                                                        new OptionData(OptionType.STRING, "image_url", "Image URL", false)),
                                                new SubcommandData("edit", "Edit a product").addOptions(
                                                        productOption(),
                                                        new OptionData(OptionType.NUMBER, "price", "New price", false).setMinValue(0.01),
                                                        new OptionData(OptionType.STRING, "category", "New category", false),
                                                        new OptionData(OptionType.STRING, "description", "New description", false),
                                                        new OptionData(OptionType.STRING, "delivery_data", "New delivery data", false),
                                                        new OptionData(OptionType.STRING, "image_url", "New image URL", false)),
                                                new SubcommandData("remove", "Remove a product from the shop")
                                                        .addOptions(productOption())),
                                new SubcommandGroupData("discount", "Discount codes")
                                        .addSubcommands(
                                                new SubcommandData("create", "Create a discount code").addOptions(
                                                        new OptionData(OptionType.STRING, "code", "The code", true),
                                                        new OptionData(OptionType.INTEGER, "percent", "Discount in %", true)
                                                                .setRequiredRange(1, 100),
                                                        new OptionData(OptionType.INTEGER, "max_uses", "Maximum redemptions (0 = unlimited)", false),
                                                        new OptionData(OptionType.INTEGER, "valid_days", "Validity in days", false))),
                                new SubcommandGroupData("orders", "Orders")
                                        .addSubcommands(
                                                new SubcommandData("pending", "Show pending/unconfirmed payments")),
                                new SubcommandGroupData("keys", "License key pool")
                                        .addSubcommands(
                                                new SubcommandData("add", "Add keys (comma-separated)").addOptions(
                                                        productOption(),
                                                        new OptionData(OptionType.STRING, "keys", "Keys, comma-separated", true))))
                        .addSubcommands(
                                new SubcommandData("stock", "Set stock level").addOptions(
                                        productOption(),
                                        new OptionData(OptionType.INTEGER, "quantity", "New stock level (-1 = unlimited)", true)),
                                new SubcommandData("stats", "Revenue, orders, top products"))
        );
    }

    private static OptionData productOption() {
        return new OptionData(OptionType.STRING, "product", "Product name", true, true);
    }
}
