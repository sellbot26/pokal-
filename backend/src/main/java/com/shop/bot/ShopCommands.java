package com.shop.bot;

import com.shop.config.ShopProperties;
import com.shop.model.Order;
import com.shop.model.Payment;
import com.shop.model.Product;
import com.shop.payment.PayGateProvider;
import com.shop.payment.PaymentService;
import com.shop.model.Review;
import com.shop.model.ShopUser;
import com.shop.repo.OrderRepo;
import com.shop.repo.ProductRepo;
import com.shop.repo.ReviewRepo;
import com.shop.repo.ShopUserRepo;
import com.shop.service.BotLogService;
import com.shop.service.OrderService;
import com.shop.service.PlanService;
import com.shop.service.QrService;
import com.shop.service.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.utils.FileUpload;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ShopCommands extends ListenerAdapter {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
            .withZone(ZoneId.systemDefault());

    private final ProductRepo productRepo;
    private final OrderRepo orderRepo;
    private final ShopUserRepo userRepo;
    private final ReviewRepo reviewRepo;
    private final OrderService orderService;
    private final PaymentService paymentService;
    private final PayGateProvider payGate;
    private final com.shop.payment.PayPalFriendsProvider payPal;
    private final QrService qrService;
    private final EmbedFactory embeds;
    private final BotLogService botLog;
    private final ShopProperties props;
    private final SettingsService settings;
    private final PlanService planService;
    private final MemberJoinListener autoRole;
    private final com.shop.service.TicketService ticketService;

    /** true = Aktion blockiert, Antwort wurde bereits gesendet. */
    private boolean maintenanceBlocked(net.dv8tion.jda.api.interactions.callbacks.IReplyCallback event) {
        if (!settings.isMaintenance()) return false;
        event.replyEmbeds(embeds.error("🔧 This shop is currently in maintenance mode. Please try again later."))
                .setEphemeral(true).queue();
        return true;
    }

    // ===================== Slash-Commands =====================

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        // Auto-Role-Fallback: greift auch ohne "Server Members Intent" bei jeder Interaktion
        autoRole.ensureAutoRole(event.getMember());
        try {
            switch (event.getName()) {
                case "shop" -> handleShop(event);
                case "product" -> handleProduct(event);
                case "buy" -> handleBuy(event);
                case "orders" -> handleOrders(event);
                case "review" -> handleReview(event);
                case "ticket" -> handleTicket(event);
                default -> { /* /admin is handled in AdminCommands */ }
            }
        } catch (Exception e) {
            log.error("Error in /{}", event.getName(), e);
            botLog.error("⚠️ Bot Error", "Command `/" + event.getName() + "`: " + e.getMessage());
            if (!event.isAcknowledged()) {
                event.replyEmbeds(embeds.error("Something went wrong. Please try again later."))
                        .setEphemeral(true).queue();
            }
        }
    }

    private void handleShop(SlashCommandInteractionEvent event) {
        if (maintenanceBlocked(event)) return;
        String guildId = event.getGuild() != null ? event.getGuild().getId() : null;
        Map<String, Long> categories = new LinkedHashMap<>();
        for (Product p : productsForGuild(guildId)) {
            String cat = p.getCategory() == null || p.getCategory().isBlank() ? "Other" : p.getCategory();
            categories.merge(cat, 1L, Long::sum);
        }
        if (categories.isEmpty()) {
            event.replyEmbeds(embeds.error("This shop is currently empty.")).setEphemeral(true).queue();
            return;
        }
        EmbedBuilder eb = embeds.base()
                .setTitle("🛍️ " + settings.brandName())
                .setDescription("Choose a category below to see the products.");
        categories.forEach((cat, count) -> eb.addField(cat, count + " product" + (count > 1 ? "s" : ""), true));

        StringSelectMenu.Builder menu = StringSelectMenu.create("shop:cat")
                .setPlaceholder("Choose a category…");
        categories.keySet().forEach(cat -> menu.addOption(cat, cat));

        event.replyEmbeds(eb.build()).addActionRow(menu.build()).setEphemeral(true).queue();
    }

    private void handleProduct(SlashCommandInteractionEvent event) {
        String name = event.getOption("product").getAsString();
        Product product = findGuildProduct(event.getGuild(), name);
        if (product == null) {
            event.replyEmbeds(embeds.error("Product **" + name + "** not found.")).setEphemeral(true).queue();
            return;
        }
        event.replyEmbeds(productEmbed(product))
                .addActionRow(Button.success("buy:start:" + product.getId(), "🛒 Buy Now"))
                .setEphemeral(true).queue();
    }

    private void handleBuy(SlashCommandInteractionEvent event) {
        if (maintenanceBlocked(event)) return;
        String name = event.getOption("product").getAsString();
        int quantity = event.getOption("quantity") != null ? event.getOption("quantity").getAsInt() : 1;
        String discount = event.getOption("discount_code") != null ? event.getOption("discount_code").getAsString() : null;

        Product product = findGuildProduct(event.getGuild(), name);
        if (product == null) {
            event.replyEmbeds(embeds.error("Product **" + name + "** not found.")).setEphemeral(true).queue();
            return;
        }
        if (product.getStock() != -1 && product.getStock() < quantity) {
            event.replyEmbeds(embeds.error("Not enough stock (available: " + product.getStock() + ")."))
                    .setEphemeral(true).queue();
            return;
        }
        showCheckout(event, product, quantity, discount);
    }

    /** Warenkorb-Zusammenfassung + Auswahl der Zahlungsmethode. */
    private void showCheckout(net.dv8tion.jda.api.interactions.callbacks.IReplyCallback event,
                              Product product, int quantity, String discount) {
        String cur = settings.currencySymbol();
        EmbedBuilder eb = embeds.base()
                .setTitle("🧾 Order Summary")
                .addField("Product", product.getName(), true)
                .addField("Quantity", String.valueOf(quantity), true)
                .addField("Unit Price", product.getPrice().toPlainString() + " " + cur, true)
                .addField("Total", product.getPrice().multiply(java.math.BigDecimal.valueOf(quantity)).toPlainString()
                        + " " + cur + (discount != null ? " *(discount code will be verified at checkout)*" : ""), false)
                .setDescription("Choose a payment method below. For crypto you'll get an address, amount and QR code.");
        if (isValidImageUrl(product.getImageUrl())) {
            eb.setThumbnail(product.getImageUrl());
        }

        String encodedCode = discount == null ? ""
                : Base64.getUrlEncoder().withoutPadding().encodeToString(discount.getBytes(StandardCharsets.UTF_8));
        StringSelectMenu.Builder menu = StringSelectMenu
                .create("buy:cur:" + product.getId() + ":" + quantity + ":" + encodedCode)
                .setPlaceholder("Choose a payment method…");
        PaymentService.CURRENCIES.keySet().forEach(c -> menu.addOption(c, c, "Crypto payment"));
        // Zahlungsmethoden-Optionen basierend auf Konfiguration des Verkäufers
        var merchant = product.getOwnerId() == null ? null : userRepo.findById(product.getOwnerId()).orElse(null);
        if (payGate.isConfiguredFor(merchant)) {
            menu.addOption("💳 Card / Apple Pay", PaymentService.CARD, "Card payment via PayGate");
        }
        if (payPal.isConfiguredFor(merchant)) {
            menu.addOption("💸 PayPal (Friends & Family)", PaymentService.PAYPAL, "Send directly via PayPal F&F");
        }

        event.replyEmbeds(eb.build()).addActionRow(menu.build()).setEphemeral(true).queue();
    }

    private void handleOrders(SlashCommandInteractionEvent event) {
        List<Order> orders = orderRepo.findByUserIdOrderByCreatedAtDesc(event.getUser().getId());
        if (orders.isEmpty()) {
            event.replyEmbeds(embeds.error("You don't have any orders yet.")).setEphemeral(true).queue();
            return;
        }
        StringBuilder sb = new StringBuilder();
        orders.stream().limit(10).forEach(o -> sb.append("**#").append(o.getId()).append("** • ")
                .append(o.getProductName()).append(" x").append(o.getQuantity())
                .append(" • ").append(o.getTotalPrice().toPlainString()).append(" ").append(settings.currencySymbol()).append(" • ")
                .append(statusLabel(o.getStatus())).append(" • ")
                .append(DATE.format(o.getCreatedAt())).append("\n"));
        event.replyEmbeds(embeds.base().setTitle("📦 Your Orders").setDescription(sb.toString()).build())
                .setEphemeral(true).queue();
    }

    /** /review — Bewertung abgeben, nur für verifizierte Käufer, wird öffentlich im Channel gepostet. */
    private void handleReview(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) {
            event.replyEmbeds(embeds.error("Reviews can only be posted on a server.")).setEphemeral(true).queue();
            return;
        }
        if (event.getOption("product") == null || event.getOption("stars") == null) {
            event.replyEmbeds(embeds.error("Please pick a product and a star rating (1-5). "
                    + "If the options are missing, the bot's commands are still syncing — try again in a moment."))
                    .setEphemeral(true).queue();
            return;
        }
        String name = event.getOption("product").getAsString();
        int stars = event.getOption("stars").getAsInt();
        String text = event.getOption("text") != null ? event.getOption("text").getAsString() : null;

        Product product = findGuildProduct(event.getGuild(), name);
        if (product == null) {
            event.replyEmbeds(embeds.error("Product **" + name + "** not found.")).setEphemeral(true).queue();
            return;
        }
        // Vouch-/Review-System ist ein Pro-Feature des Verkäufers.
        if (product.getOwnerId() != null && !props.getDiscord().adminIdList().contains(product.getOwnerId())) {
            ShopUser seller = userRepo.findById(product.getOwnerId()).orElse(null);
            if (seller == null || !planService.isAtLeast(seller, "PRO")) {
                event.replyEmbeds(embeds.error("The review system is a Pro feature — this shop hasn't unlocked it yet."))
                        .setEphemeral(true).queue();
                return;
            }
        }
        boolean verifiedBuyer = orderRepo.findByUserIdOrderByCreatedAtDesc(event.getUser().getId()).stream()
                .anyMatch(o -> product.getId().equals(o.getProductId())
                        && (o.getStatus() == Order.Status.DELIVERED || o.getStatus() == Order.Status.PAID));
        if (!verifiedBuyer) {
            event.replyEmbeds(embeds.error("You can only review products you've actually purchased."))
                    .setEphemeral(true).queue();
            return;
        }
        if (reviewRepo.findByUserIdAndProductId(event.getUser().getId(), product.getId()).isPresent()) {
            event.replyEmbeds(embeds.error("You've already reviewed this product.")).setEphemeral(true).queue();
            return;
        }

        Review review = new Review();
        review.setGuildId(event.getGuild().getId());
        review.setProductId(product.getId());
        review.setProductName(product.getName());
        review.setUserId(event.getUser().getId());
        review.setUsername(event.getUser().getName());
        review.setStars(stars);
        review.setText(text);
        reviewRepo.save(review);

        String starLine = "⭐".repeat(stars) + "▫".repeat(5 - stars);
        EmbedBuilder eb = embeds.base()
                .setTitle("Review — " + product.getName())
                .setDescription("**" + starLine + "**  (" + stars + "/5)"
                        + (text != null && !text.isBlank() ? "\n\n" + text : ""))
                .setAuthor(event.getUser().getName() + " · verified buyer", null, event.getUser().getEffectiveAvatarUrl());
        if (isValidImageUrl(product.getImageUrl())) eb.setThumbnail(product.getImageUrl());

        // Wenn im Dashboard ein Bewertungs-Channel gesetzt ist, dorthin posten (zentrale Social-Proof-Wall),
        // sonst öffentlich in den aktuellen Channel.
        String reviewChannelId = resolveReviewChannelId(product);
        var reviewChannel = reviewChannelId == null || reviewChannelId.isBlank()
                ? null : event.getGuild().getChannelById(
                        net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel.class, reviewChannelId.trim());
        if (reviewChannel != null) {
            reviewChannel.sendMessageEmbeds(eb.build()).queue();
            event.replyEmbeds(embeds.base()
                    .setTitle("Thanks for your review!")
                    .setDescription("Your " + stars + "★ review was posted in " + reviewChannel.getAsMention() + ".")
                    .build()).setEphemeral(true).queue();
        } else {
            event.replyEmbeds(eb.build()).queue(); // öffentlich — Social Proof im Channel
        }
    }

    private void handleTicket(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            event.replyEmbeds(embeds.error("Tickets can only be opened on the server.")).setEphemeral(true).queue();
            return;
        }
        event.deferReply(true).queue();
        ticketService.open(guild, event.getUser(),
                channelMention -> event.getHook().sendMessage("🎫 Your ticket has been created: " + channelMention).queue(),
                error -> event.getHook().sendMessageEmbeds(embeds.error(error)).queue());
    }

    // ===================== Select-Menüs =====================

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        autoRole.ensureAutoRole(event.getMember());
        String id = event.getComponentId();
        try {
            if (id.equals("shop:cat")) {
                showCategory(event);
            } else if (id.equals("shop:prod")) {
                showProductDetail(event);
            } else if (id.startsWith("buy:cur:")) {
                startPayment(event);
            }
        } catch (Exception e) {
            log.error("Error in select {}", id, e);
            if (!event.isAcknowledged()) {
                event.replyEmbeds(embeds.error("Something went wrong.")).setEphemeral(true).queue();
            } else {
                event.getHook().sendMessageEmbeds(embeds.error("Something went wrong.")).queue();
            }
        }
    }

    private void showCategory(StringSelectInteractionEvent event) {
        String category = event.getValues().get(0);
        String guildId = event.getGuild() != null ? event.getGuild().getId() : null;
        List<Product> products = category.equals("Other")
                ? productsForGuild(guildId).stream()
                    .filter(p -> p.getCategory() == null || p.getCategory().isBlank()).toList()
                : productsForGuild(guildId).stream()
                    .filter(p -> category.equals(p.getCategory())).toList();
        if (products.isEmpty()) {
            event.editMessageEmbeds(embeds.error("No products in this category.")).setComponents().queue();
            return;
        }
        StringBuilder sb = new StringBuilder();
        StringSelectMenu.Builder menu = StringSelectMenu.create("shop:prod").setPlaceholder("View product…");
        products.stream().limit(25).forEach(p -> {
            sb.append("**").append(p.getName()).append("** — ").append(p.getPrice().toPlainString())
                    .append(" ").append(settings.currencySymbol()).append(" — Stock: ").append(stockText(p)).append("\n");
            menu.addOption(p.getName(), String.valueOf(p.getId()),
                    p.getPrice().toPlainString() + " " + settings.currencySymbol());
        });
        event.editMessageEmbeds(embeds.base().setTitle("📂 " + category).setDescription(sb.toString()).build())
                .setActionRow(menu.build()).queue();
    }

    private void showProductDetail(StringSelectInteractionEvent event) {
        long productId = Long.parseLong(event.getValues().get(0));
        Product product = productRepo.findById(productId).filter(Product::isActive).orElse(null);
        if (product == null) {
            event.editMessageEmbeds(embeds.error("Product no longer available.")).setComponents().queue();
            return;
        }
        event.editMessageEmbeds(productEmbed(product))
                .setActionRow(Button.success("buy:start:" + product.getId(), "🛒 Buy Now"))
                .queue();
    }

    /** Währung gewählt -> Bestellung + Zahlung anlegen, Adresse + QR-Code liefern. */
    private void startPayment(StringSelectInteractionEvent event) {
        String[] parts = event.getComponentId().split(":");
        long productId = Long.parseLong(parts[2]);
        int quantity = Integer.parseInt(parts[3]);
        String discount = parts.length > 4 && !parts[4].isEmpty()
                ? new String(Base64.getUrlDecoder().decode(parts[4]), StandardCharsets.UTF_8)
                : null;
        String currency = event.getValues().get(0);

        if (maintenanceBlocked(event)) return;
        event.deferReply(true).queue();
        try {
            Order order = orderService.createOrder(event.getUser().getId(), event.getUser().getName(),
                    productId, quantity, discount);
            Payment payment = paymentService.createPayment(order, currency);

            boolean cardLike = PaymentService.CARD.equals(currency);
            boolean paypal = PaymentService.PAYPAL.equals(currency);
            if (cardLike) {
                // Karte: Checkout-Link (keine Krypto-Adresse + QR)
                MessageEmbed embed = cardPaymentEmbed(order);
                Button payButton = Button.link(payment.getPayAddress(), "💳 Pay Now");
                event.getHook().sendMessageEmbeds(embed).setActionRow(payButton).queue();
                event.getUser().openPrivateChannel()
                        .flatMap(ch -> ch.sendMessageEmbeds(embed).setActionRow(payButton))
                        .queue(ok -> {}, err -> {});
            } else if (paypal) {
                // PayPal F&F: PayPal-Adresse + Betrag (kein QR), manuelle Bestätigung durch den Verkäufer
                MessageEmbed embed = paypalPaymentEmbed(order, payment);
                event.getHook().sendMessageEmbeds(embed).queue();
                event.getUser().openPrivateChannel()
                        .flatMap(ch -> ch.sendMessageEmbeds(embed))
                        .queue(ok -> {}, err -> {});
            } else {
                MessageEmbed embed = paymentEmbed(order, payment);
                byte[] qr = qrService.png(payment.getPayAddress());

                event.getHook().sendMessageEmbeds(embed)
                        .addFiles(FileUpload.fromData(qr, "qr.png"))
                        .queue();

                // Kopie per DM (best effort)
                event.getUser().openPrivateChannel()
                        .flatMap(ch -> ch.sendMessageEmbeds(embed).addFiles(FileUpload.fromData(qrService.png(payment.getPayAddress()), "qr.png")))
                        .queue(ok -> {}, err -> {});
            }

            botLog.info("🛒 New Order",
                    "**#" + order.getId() + "** — " + order.getProductName() + " x" + order.getQuantity()
                            + " for " + order.getTotalPrice() + " " + settings.currencySymbol() + " (" + currency + ")"
                            + "\nBuyer: <@" + order.getUserId() + ">");
        } catch (IllegalArgumentException | IllegalStateException e) {
            event.getHook().sendMessageEmbeds(embeds.error(e.getMessage())).queue();
        } catch (Exception e) {
            log.error("Payment could not be created", e);
            botLog.error("⚠️ Payment Error", e.getMessage() == null ? e.toString() : e.getMessage());
            event.getHook().sendMessageEmbeds(
                    embeds.error("Payment could not be created. Please try again later.")).queue();
        }
    }

    // ===================== Buttons =====================

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        autoRole.ensureAutoRole(event.getMember());
        String id = event.getComponentId();
        try {
            if (id.startsWith("buy:start:")) {
                if (maintenanceBlocked(event)) return;
                long productId = Long.parseLong(id.substring("buy:start:".length()));
                Product product = productRepo.findById(productId).filter(Product::isActive).orElse(null);
                if (product == null) {
                    event.replyEmbeds(embeds.error("Product no longer available.")).setEphemeral(true).queue();
                    return;
                }
                showCheckout(event, product, 1, null);
            } else if (id.equals("ticket:open")) {
                // Panel-Button: gleicher Flow wie /ticket
                if (event.getGuild() == null) {
                    event.replyEmbeds(embeds.error("Tickets can only be opened on the server.")).setEphemeral(true).queue();
                    return;
                }
                event.deferReply(true).queue();
                ticketService.open(event.getGuild(), event.getUser(),
                        mention -> event.getHook().sendMessage("🎫 Your ticket has been created: " + mention).queue(),
                        error -> event.getHook().sendMessageEmbeds(embeds.error(error)).queue());
            } else if (id.equals("ticket:close")) {
                var channel = event.getChannel().asTextChannel();
                // Ticket-Channels am Topic-Marker erkennen; Alt-Tickets (Name-Prefix) weiter unterstützen
                boolean isTicket = (channel.getTopic() != null && channel.getTopic().contains("uid:"))
                        || channel.getName().startsWith("ticket-");
                if (isTicket) {
                    event.reply("🔒 Closing ticket…").setEphemeral(true).queue();
                    ticketService.close(channel, event.getUser());
                }
            } else if (id.startsWith("rate:")) {
                String[] parts = id.split(":");
                handleRateButton(event, Long.parseLong(parts[1]), Integer.parseInt(parts[2]));
            }
        } catch (Exception e) {
            log.error("Error in button {}", id, e);
            botLog.error("⚠️ Bot Error", "Button `" + id + "`: " + e.getMessage());
            if (!event.isAcknowledged()) {
                event.replyEmbeds(embeds.error("Something went wrong. Please try again later."))
                        .setEphemeral(true).queue();
            }
        }
    }

    /** Sterne-Button aus der Post-Kauf-DM geklickt → Kommentar-Modal öffnen. */
    private void handleRateButton(ButtonInteractionEvent event, long orderId, int stars) {
        Order order = orderRepo.findById(orderId).orElse(null);
        if (order == null || !event.getUser().getId().equals(order.getUserId())) {
            event.reply("This rating link isn't for you.").setEphemeral(true).queue();
            return;
        }
        if (reviewRepo.findByUserIdAndProductId(order.getUserId(), order.getProductId()).isPresent()) {
            event.reply("You've already reviewed this product — thank you! 💛").setEphemeral(true).queue();
            return;
        }
        TextInput comment = TextInput.create("comment", "Your review (optional)", TextInputStyle.PARAGRAPH)
                .setRequired(false)
                .setMaxLength(1000)
                .setPlaceholder("What did you think of your purchase?")
                .build();
        Modal modal = Modal.create("ratefb:" + orderId + ":" + stars, stars + "★ — leave a review")
                .addActionRow(comment)
                .build();
        event.replyModal(modal).queue();
    }

    /** Kommentar-Modal abgeschickt → Bewertung speichern + (falls konfiguriert) in den Review-Channel posten. */
    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        String id = event.getModalId();
        if (!id.startsWith("ratefb:")) return;
        try {
            String[] parts = id.split(":");
            long orderId = Long.parseLong(parts[1]);
            int stars = Math.max(1, Math.min(5, Integer.parseInt(parts[2])));
            Order order = orderRepo.findById(orderId).orElse(null);
            if (order == null || !event.getUser().getId().equals(order.getUserId())) {
                event.reply("Could not save your review.").setEphemeral(true).queue();
                return;
            }
            Product product = productRepo.findById(order.getProductId()).orElse(null);
            if (product == null) {
                event.reply("This product no longer exists.").setEphemeral(true).queue();
                return;
            }
            if (reviewRepo.findByUserIdAndProductId(order.getUserId(), product.getId()).isPresent()) {
                event.reply("You've already reviewed this product — thank you! 💛").setEphemeral(true).queue();
                return;
            }
            String text = event.getValue("comment") != null ? event.getValue("comment").getAsString() : null;
            if (text != null && text.isBlank()) text = null;

            Review review = new Review();
            review.setGuildId(product.getGuildId());
            review.setProductId(product.getId());
            review.setProductName(product.getName());
            review.setUserId(event.getUser().getId());
            review.setUsername(event.getUser().getName());
            review.setStars(stars);
            review.setText(text);
            reviewRepo.save(review);

            postReviewToChannel(event, product, stars, text);

            event.replyEmbeds(embeds.base()
                    .setTitle("Thanks for your review! ⭐")
                    .setDescription("Your **" + stars + "★** rating for **" + product.getName() + "** was saved.")
                    .build()).setEphemeral(true).queue();
        } catch (Exception e) {
            log.error("Review modal {} failed", id, e);
            if (!event.isAcknowledged()) {
                event.reply("Something went wrong saving your review.").setEphemeral(true).queue();
            }
        }
    }

    /** Review-Channel des Verkäufers (falls gesetzt), sonst der Site-weite Fallback. */
    private String resolveReviewChannelId(Product product) {
        if (product != null && product.getOwnerId() != null) {
            ShopUser owner = userRepo.findById(product.getOwnerId()).orElse(null);
            if (owner != null && owner.getReviewChannelId() != null && !owner.getReviewChannelId().isBlank()) {
                return owner.getReviewChannelId().trim();
            }
        }
        return settings.reviewChannelId();
    }

    /** Postet die Bewertung in den konfigurierten Review-Channel des zugehörigen Servers. */
    private void postReviewToChannel(ModalInteractionEvent event, Product product, int stars, String text) {
        String reviewChannelId = resolveReviewChannelId(product);
        if (reviewChannelId == null || reviewChannelId.isBlank() || product.getGuildId() == null) return;
        Guild guild = event.getJDA().getGuildById(product.getGuildId());
        if (guild == null) return;
        var channel = guild.getChannelById(
                net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel.class, reviewChannelId.trim());
        if (channel == null) return;
        String starLine = "⭐".repeat(stars) + "▫".repeat(5 - stars);
        EmbedBuilder eb = embeds.base()
                .setTitle("Review — " + product.getName())
                .setDescription("**" + starLine + "**  (" + stars + "/5)"
                        + (text != null && !text.isBlank() ? "\n\n" + text : ""))
                .setAuthor(event.getUser().getName() + " · verified buyer", null, event.getUser().getEffectiveAvatarUrl());
        if (isValidImageUrl(product.getImageUrl())) eb.setThumbnail(product.getImageUrl());
        channel.sendMessageEmbeds(eb.build()).queue();
    }

    // ===================== Autocomplete =====================

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        if (!event.getFocusedOption().getName().equals("product")) return;
        if (event.getGuild() == null) {
            event.replyChoices(List.of()).queue(ok -> {}, err -> {});
            return;
        }
        String query = event.getFocusedOption().getValue();
        List<Command.Choice> choices = productRepo
                .findTop25ByGuildIdAndActiveTrueAndNameContainingIgnoreCase(event.getGuild().getId(), query)
                .stream()
                .filter(p -> !PlanService.PLATFORM_CATEGORY.equals(p.getCategory()))
                .limit(25)
                .map(p -> new Command.Choice(p.getName(), p.getName()))
                .toList();
        event.replyChoices(choices).queue(ok -> {}, err -> {});
    }

    // ===================== Helfer =====================

    /** Alle aktiven, kaufbaren Produkte des angegebenen Discord-Servers (ohne interne Plan-Produkte). */
    private List<Product> productsForGuild(String guildId) {
        if (guildId == null) return List.of();
        return productRepo.findByGuildIdAndActiveTrueOrderByCategoryAscNameAsc(guildId).stream()
                .filter(p -> !PlanService.PLATFORM_CATEGORY.equals(p.getCategory()))
                .toList();
    }

    /** Produkt per Name innerhalb des aktuellen Servers finden (Discord-Server-getrennter Katalog). */
    private Product findGuildProduct(Guild guild, String name) {
        if (guild == null) return null;
        return productRepo.findByGuildIdAndNameIgnoreCase(guild.getId(), name)
                .filter(Product::isActive)
                .filter(p -> !PlanService.PLATFORM_CATEGORY.equals(p.getCategory()))
                .orElse(null);
    }

    /** JDA lehnt Embed-Bilder ab, die keine absolute http(s)/attachment-URL sind (z. B. alte relative Upload-Pfade). */
    private boolean isValidImageUrl(String url) {
        if (url == null || url.isBlank()) return false;
        String lower = url.trim().toLowerCase();
        return lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("attachment://");
    }

    private String stockText(Product p) {
        return p.getStock() == -1 ? "∞" : String.valueOf(p.getStock());
    }

    private String statusLabel(Order.Status status) {
        return switch (status) {
            case PENDING -> "🕐 Pending";
            case PAID -> "💰 Paid";
            case DELIVERED -> "✅ Delivered";
            case CANCELLED -> "❌ Cancelled";
            case EXPIRED -> "⌛ Expired";
        };
    }

    private MessageEmbed productEmbed(Product p) {
        EmbedBuilder eb = embeds.base()
                .setTitle("🛍️ " + p.getName())
                .addField("Price", p.getPrice().toPlainString() + " " + settings.currencySymbol(), true)
                .addField("Category", p.getCategory() == null || p.getCategory().isBlank() ? "Other" : p.getCategory(), true)
                .addField("Stock", stockText(p), true);
        if (p.getDescription() != null && !p.getDescription().isBlank()) {
            eb.setDescription(p.getDescription());
        }
        if (isValidImageUrl(p.getImageUrl())) {
            eb.setImage(p.getImageUrl());
        }
        return eb.build();
    }

    private MessageEmbed paymentEmbed(Order order, Payment payment) {
        return embeds.base()
                .setTitle("💸 Payment for Order #" + order.getId())
                .setDescription("Send **exactly** the following amount to the address below.\n"
                        + "Delivery is automatic once the blockchain confirms it.")
                .addField("Product", order.getProductName() + " x" + order.getQuantity(), true)
                .addField("Price", order.getTotalPrice().toPlainString() + " " + settings.currencySymbol()
                        + (order.getDiscountPercent() > 0 ? " (-" + order.getDiscountPercent() + "%)" : ""), true)
                .addField("Currency", payment.getPayCurrency(), true)
                .addField("Amount", "`" + payment.getPayAmount().stripTrailingZeros().toPlainString() + " "
                        + payment.getPayCurrency() + "`", false)
                .addField("Address", "`" + payment.getPayAddress() + "`", false)
                .addField("⏱ Valid for", props.getOrderExpiryMinutes() + " minutes", true)
                .setImage("attachment://qr.png")
                .build();
    }

    private MessageEmbed paypalPaymentEmbed(Order order, Payment payment) {
        EmbedBuilder eb = embeds.base()
                .setTitle("💸 PayPal Payment for Order #" + order.getId())
                .setDescription("Send **exactly** the amount below as **PayPal Friends & Family** to the address.\n"
                        + "⚠️ Choose **Friends & Family**, not Goods & Services.\n"
                        + "Delivery is automatic once your payment arrives.")
                .addField("Product", order.getProductName() + " x" + order.getQuantity(), true)
                .addField("Amount", "`" + payment.getPayAmount().toPlainString() + " " + settings.currencySymbol() + "`", true)
                .addField("PayPal address", "`" + payment.getPayAddress() + "`", false);
        if (payment.getPayNote() != null && !payment.getPayNote().isBlank()) {
            eb.addField("⚠️ Note (required!)", "Write **" + payment.getPayNote()
                    + "** in the payment message — otherwise it can't be matched.", false);
        }
        return eb.build();
    }

    private MessageEmbed cardPaymentEmbed(Order order) {
        return embeds.base()
                .setTitle("💳 Card Payment for Order #" + order.getId())
                .setDescription("Click **Pay Now** below and complete the payment.\n"
                        + "Delivery is automatic once the payment succeeds.")
                .addField("Product", order.getProductName() + " x" + order.getQuantity(), true)
                .addField("Amount", order.getTotalPrice().toPlainString() + " " + settings.currencySymbol()
                        + (order.getDiscountPercent() > 0 ? " (-" + order.getDiscountPercent() + "%)" : ""), true)
                .addField("⏱ Valid for", props.getOrderExpiryMinutes() + " minutes", true)
                .build();
    }
}
