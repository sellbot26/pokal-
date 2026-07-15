package com.shop.bot;

import com.shop.config.ShopProperties;
import com.shop.service.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Wird der Bot auf einen neuen Server eingeladen:
 *  1) Slash-Commands sofort dort registrieren (sonst erst beim nächsten Neustart verfügbar).
 *  2) Willkommens-Embed mit Setup-Tutorial, benötigten Berechtigungen und Terms of Service posten —
 *     in den System-Channel, sonst in den ersten beschreibbaren Text-Channel.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GuildJoinListener extends ListenerAdapter {

    private final ShopProperties props;
    private final SettingsService settings;
    private final EmbedFactory embeds;

    @Override
    public void onGuildJoin(GuildJoinEvent event) {
        Guild guild = event.getGuild();
        log.info("Bot wurde auf Server '{}' ({}) eingeladen", guild.getName(), guild.getId());

        guild.updateCommands().addCommands(BotService.buildAllCommands()).queue(
                ok -> log.info("Slash-Commands in '{}' registriert (Join)", guild.getName()),
                err -> log.warn("Command-Registrierung in '{}' fehlgeschlagen: {}", guild.getName(), err.getMessage()));

        TextChannel channel = findWelcomeChannel(guild);
        if (channel == null) {
            log.warn("Kein beschreibbarer Channel in '{}' — Willkommens-Nachricht übersprungen.", guild.getName());
            return;
        }
        channel.sendMessageEmbeds(welcomeEmbed()).queue(
                ok -> log.info("Willkommens-Nachricht in '{}' #{} gepostet", guild.getName(), channel.getName()),
                err -> log.warn("Willkommens-Nachricht in '{}' fehlgeschlagen: {}", guild.getName(), err.getMessage()));
    }

    private TextChannel findWelcomeChannel(Guild guild) {
        TextChannel system = guild.getSystemChannel();
        if (system != null && system.canTalk()) return system;
        return guild.getTextChannels().stream()
                .filter(TextChannel::canTalk)
                .findFirst().orElse(null);
    }

    private MessageEmbed welcomeEmbed() {
        String brand = settings.brandName();
        String dashboard = props.getBaseUrl() == null || props.getBaseUrl().isBlank()
                ? "the web dashboard" : props.getBaseUrl();

        return embeds.base()
                .setTitle("👋 Thanks for adding " + brand + "!")
                .setDescription(brand + " turns your server into a full shop — sell products, "
                        + "accept crypto & card payments, and deliver roles, keys, or files automatically.\n"
                        + "Follow the steps below to get started in a few minutes.")
                .addField("🚀 Quick Setup",
                        "**1.** Give the bot the permissions listed below (or simply **Administrator**).\n"
                        + "**2.** Log in to the dashboard: " + dashboard + "\n"
                        + "**3.** Create your first product with `/admin product add` — or in the dashboard.\n"
                        + "**4.** Set up your payment methods: dashboard → **Settings → Payments**.\n"
                        + "**5.** Run `/shop` in the channel where customers should browse and buy.",
                        false)
                .addField("🔑 Required Permissions",
                        "The easiest option is **Administrator**. If you prefer minimal permissions:\n"
                        + "• **Manage Roles** — deliver role products & auto-role on join\n"
                        + "• **Manage Channels** — create support ticket channels\n"
                        + "• **Send Messages**, **Embed Links**, **Attach Files**, **Read Message History**\n"
                        + "⚠️ Drag the bot's role **above** every role it should assign "
                        + "(Server Settings → Roles).",
                        false)
                .addField("📜 Terms of Service",
                        "By keeping this bot on your server you agree to the following:\n"
                        + "• **No illegal content.** Selling goods or services that violate any law or "
                        + "Discord's Terms of Service is strictly prohibited and will result in removal.\n"
                        + "• **No liability.** The bot is provided **\"as is\"**, without any warranty. "
                        + "The operators are not liable for losses, damages, or disputes between buyers and sellers.\n"
                        + "• **Seller responsibility.** Sellers are solely responsible for their products, "
                        + "taxes, refunds, and legal compliance.\n"
                        + "• **Abuse** (fraud, scams, chargeback abuse) leads to a permanent ban.\n"
                        + "If you do not agree, please remove the bot from your server.",
                        false)
                .addField("❓ Need help?",
                        "Open a support ticket with `/ticket` or reach out via the dashboard.",
                        false)
                .setTimestamp(Instant.now())
                .build();
    }
}
