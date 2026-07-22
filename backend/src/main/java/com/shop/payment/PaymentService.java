package com.shop.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.config.ShopProperties;
import com.shop.model.Order;
import com.shop.model.Payment;
import com.shop.model.ShopUser;
import com.shop.repo.OrderRepo;
import com.shop.repo.PaymentRepo;
import com.shop.repo.ProductRepo;
import com.shop.repo.ShopUserRepo;
import com.shop.service.BotLogService;
import com.shop.service.DeliveryService;
import com.shop.service.SettingsService;
import com.shop.service.WebhookLogService;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class PaymentService {

    /** Anzeigename -> API-Code beim Provider */
    public static final Map<String, String> CURRENCIES = new LinkedHashMap<>() {{
        put("BTC", "btc");
        put("LTC", "ltc");
        put("ETH", "eth");
        put("SOL", "sol");
        put("USDT-TRC20", "usdttrc20");
        put("USDC", "usdc");
        put("DOGE", "doge");
        put("XRP", "xrp");
        put("BCH", "bch");
        put("TRX", "trx");
    }};

    /** Pseudo-Währung für Kartenzahlung über PayGate. */
    public static final String CARD = "KARTE";

    /** Pseudo-Währung für Kartenzahlung über Stripe Checkout. */
    public static final String STRIPE = "STRIPE";

    /** Pseudo-Währung für PayPal "Friends & Family" (manuelle Bestätigung durch den Verkäufer). */
    public static final String PAYPAL = "PAYPAL";

    /**
     * Feste Auszahlungs-Wallet (USDC · Polygon) für ALLE Plan-Käufe. Plan-Käufe laufen
     * ausschließlich über PayGate (Karte) auf genau diese Adresse — kein Krypto, keine
     * Verkäufer-Wallet, unabhängig vom Test-/Mock-Modus.
     */
    public static final String PLAN_PAYOUT_WALLET = "0x9824D446002d2AfBFac1D9B10dBB275EF46330fe";

    private final Map<String, PaymentProvider> providers;
    private final PaymentRepo paymentRepo;
    private final OrderRepo orderRepo;
    private final ProductRepo productRepo;
    private final ShopUserRepo userRepo;
    private final DeliveryService deliveryService;
    private final BotLogService botLog;
    private final WebhookLogService webhookLog;
    private final ShopProperties props;
    private final SettingsService settings;
    private final ObjectMapper mapper;
    private final com.shop.service.PlanService planService;
    private final com.shop.repo.ReviewRepo reviewRepo;

    /** Für die IPN-Rückvalidierung an PayPal (kein DI nötig). */
    private final org.springframework.web.client.RestClient http =
            org.springframework.web.client.RestClient.create();

    public PaymentService(List<PaymentProvider> providerList, PaymentRepo paymentRepo, OrderRepo orderRepo,
                          ProductRepo productRepo, ShopUserRepo userRepo,
                          DeliveryService deliveryService, BotLogService botLog, WebhookLogService webhookLog,
                          ShopProperties props, SettingsService settings, ObjectMapper mapper,
                          com.shop.service.PlanService planService, com.shop.repo.ReviewRepo reviewRepo) {
        this.providers = providerList.stream()
                .collect(Collectors.toMap(PaymentProvider::name, Function.identity()));
        this.reviewRepo = reviewRepo;
        this.paymentRepo = paymentRepo;
        this.orderRepo = orderRepo;
        this.productRepo = productRepo;
        this.userRepo = userRepo;
        this.deliveryService = deliveryService;
        this.botLog = botLog;
        this.webhookLog = webhookLog;
        this.props = props;
        this.settings = settings;
        this.mapper = mapper;
        this.planService = planService;
    }

    /**
     * Zahlungsmethoden, die für diesen Verkäufer tatsächlich konfiguriert sind — dieselbe Logik
     * wie im Discord-Checkout: Karte (PayGate), PayPal F&F und alle Coins mit hinterlegter Wallet
     * (eigene Wallet des Verkäufers oder Site-Fallback). Für Web-Checkout und Storefront.
     */
    public Map<String, Object> availableMethodsFor(ShopUser merchant) {
        PaymentProvider payGate = providers.get("paygate");
        PaymentProvider payPal = providers.get("paypalff");
        PaymentProvider direct = providers.get("direct");
        boolean card = payGate instanceof PayGateProvider pg && pg.isConfiguredFor(merchant);
        boolean paypal = payPal instanceof PayPalFriendsProvider pp && pp.isConfiguredFor(merchant);
        List<String> coins = direct instanceof DirectCryptoProvider dc
                ? CryptoWallets.SYMBOLS.stream().filter(s -> dc.isCoinConfiguredFor(s, merchant)).toList()
                : List.of();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("card", card);
        out.put("paypal", paypal);
        out.put("coins", coins);
        return out;
    }

    /** Verkäufer des bestellten Produkts — bestimmt, auf wessen Wallet/Konto die Zahlung läuft. */
    public ShopUser merchantFor(Order order) {
        return productRepo.findById(order.getProductId())
                .map(p -> p.getOwnerId() == null ? null : userRepo.findById(p.getOwnerId()).orElse(null))
                .orElse(null);
    }

    @Transactional
    public Payment createPayment(Order order, String currencyLabel) {
        boolean card = CARD.equals(currencyLabel);
        boolean stripe = STRIPE.equals(currencyLabel);
        boolean paypal = PAYPAL.equals(currencyLabel);
        String apiCode = stripe ? "card" : card ? "usdc" : paypal ? "paypal" : CURRENCIES.get(currencyLabel);
        if (apiCode == null) throw new IllegalArgumentException("Unsupported currency: " + currencyLabel);

        var existing = paymentRepo.findByOrderId(order.getId());
        if (existing.isPresent() && existing.get().getStatus() == Payment.Status.WAITING) {
            return existing.get();
        }

        // Provider-Wahl:
        //  - Stripe / Karte brauchen eine echte Checkout-URL → IMMER der echte Provider.
        //  - PayPal F&F: Käufer zahlt direkt an die PayPal-Adresse, manuelle Bestätigung.
        //  - Krypto: DIRECT (Adresse + Kurs, auto-erkannt bzw. Ein-Klick-Bestätigung —
        //    funktioniert ohne jeden API-Key). Mock nur bei PAYMENT_PROVIDER=mock (lokales Testen).
        PaymentProvider chosen;
        if (stripe) chosen = requireProvider("stripe");
        else if (card) chosen = requireProvider("paygate");
        else if (paypal) chosen = requireProvider("paypalff");
        else chosen = cryptoProvider();
        CreatedPayment created = chosen.create(order, apiCode, merchantFor(order));
        Payment payment = new Payment();
        payment.setOrderId(order.getId());
        payment.setProvider(chosen.name());
        payment.setProviderPaymentId(created.providerPaymentId());
        payment.setPayCurrency(currencyLabel);
        payment.setPayAmount(created.payAmount());
        payment.setPayAddress(created.payAddress());
        payment.setPayNote(created.note()); // Pflicht-Notiz (nur PayPal F&F, sonst null)
        return paymentRepo.save(payment);
    }

    private PaymentProvider requireProvider(String name) {
        PaymentProvider p = providers.get(name);
        if (p == null) throw new IllegalStateException("Payment provider not available: " + name);
        return p;
    }

    /** Krypto-Routing: mock (nur explizit gesetzt) > direct — kein externer Anbieter nötig. */
    private PaymentProvider cryptoProvider() {
        if ("mock".equals(props.getPayment().getProvider())) return requireProvider("mock");
        return requireProvider("direct");
    }

    /**
     * Manuelle Zahlungsbestätigung durch den Verkäufer/Owner im Dashboard: markiert die
     * offene Zahlung als bezahlt und löst die Lieferung aus. Funktioniert auch, wenn der
     * Käufer noch keine Zahlungsart gewählt hatte (dann wird eine manuelle Zahlung angelegt).
     */
    @Transactional
    public void confirmDirectPayment(long orderId) {
        Payment payment = paymentRepo.findByOrderId(orderId).orElse(null);
        if (payment == null) {
            Order order = orderRepo.findById(orderId)
                    .orElseThrow(() -> new IllegalArgumentException("Order #" + orderId + " not found."));
            if (order.getStatus() != Order.Status.PENDING) {
                throw new IllegalStateException("Order #" + orderId + " is already " + order.getStatus() + ".");
            }
            payment = new Payment();
            payment.setOrderId(orderId);
            payment.setProvider("manual");
            payment.setProviderPaymentId("MANUAL-" + orderId);
            payment = paymentRepo.save(payment);
        }
        if (payment.getStatus() != Payment.Status.WAITING) {
            throw new IllegalStateException("This payment is already " + payment.getStatus() + ".");
        }
        markFinished(payment, null);
    }

    /**
     * Automatische Bestätigung einer PayPal-F&F-Zahlung durch den {@link com.shop.service.PayPalWatchService}:
     * Die Transaction Search API hat eine passende, eingegangene Zahlung gefunden → Lieferung auslösen.
     * Idempotent (doppelte Erkennung schadet nicht).
     */
    @Transactional
    public void confirmPayPalPayment(long orderId, String txId) {
        Payment payment = paymentRepo.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("No payment for order #" + orderId));
        if (!"paypalff".equals(payment.getProvider())) {
            throw new IllegalStateException("Order #" + orderId + " is not a PayPal payment.");
        }
        if (payment.getStatus() != Payment.Status.WAITING) return; // schon bestätigt/geschlossen
        log.info("PayPal payment for order #{} auto-confirmed (tx {})", orderId, txId);
        markFinished(payment, txId);
    }

    /**
     * Verarbeitet eine PayPal IPN (Instant Payment Notification). Der Rohtext wird zuerst gegen
     * PayPal zurückvalidiert ({@code cmd=_notify-validate} → "VERIFIED"), erst dann verarbeitet —
     * so werden gefälschte "bezahlt"-Meldungen abgewehrt (kein API-Key nötig). Zuordnung über
     * Empfänger-Adresse + exakten (eindeutigen) Betrag + Währung. Passt alles, wird die Bestellung
     * automatisch ausgeliefert.
     */
    @Transactional
    public void handlePayPalIpn(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) return;
        Map<String, String> ipn = parseForm(rawBody);
        boolean sandbox = "1".equals(ipn.get("test_ipn"));

        if (!verifyIpn(rawBody, sandbox)) {
            botLog.error("🚨 IPN Rejected", "A PayPal IPN failed validation (not VERIFIED) and was ignored.");
            throw new SecurityException("PayPal IPN could not be verified");
        }

        String status = ipn.getOrDefault("payment_status", "");
        if (!"Completed".equalsIgnoreCase(status)) {
            log.info("PayPal IPN ignored: payment_status={}", status);
            return;
        }

        String receiver = firstNonBlank(ipn.get("receiver_email"), ipn.get("business"));
        String currency = ipn.get("mc_currency");
        String grossStr = ipn.get("mc_gross");
        String txnId = ipn.get("txn_id");
        if (receiver == null || grossStr == null) return;

        java.math.BigDecimal gross;
        try { gross = new java.math.BigDecimal(grossStr.trim()); }
        catch (NumberFormatException e) { return; }

        String shopCurrency = settings.get("currency", "EUR");
        String rcv = receiver.trim();

        // Passende offene PayPal-Zahlung: gleiche Empfänger-Adresse + exakter (eindeutiger) Betrag + Währung.
        Payment match = paymentRepo.findAll().stream()
                .filter(p -> p.getStatus() == Payment.Status.WAITING)
                .filter(p -> "paypalff".equals(p.getProvider()))
                .filter(p -> p.getPayAddress() != null && rcv.equalsIgnoreCase(p.getPayAddress().trim()))
                .filter(p -> currency == null || currency.equalsIgnoreCase(shopCurrency))
                .filter(p -> p.getPayAmount() != null && p.getPayAmount().compareTo(gross) == 0)
                .findFirst().orElse(null);

        if (match == null) {
            log.info("PayPal IPN without matching order: {} {} to {} (txn {})", gross, currency, rcv, txnId);
            return;
        }
        log.info("PayPal IPN matched order #{} (txn {})", match.getOrderId(), txnId);
        confirmPayPalPayment(match.getOrderId(), txnId);
    }

    /** Rückvalidierung an PayPal: sendet den Rohtext mit vorangestelltem _notify-validate zurück. */
    private boolean verifyIpn(String rawBody, boolean sandbox) {
        String url = sandbox
                ? "https://ipnpb.sandbox.paypal.com/cgi-bin/webscr"
                : "https://ipnpb.paypal.com/cgi-bin/webscr";
        try {
            String resp = http.post()
                    .uri(url)
                    .header("User-Agent", "Pokal-IPN/1.0")
                    .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                    .body("cmd=_notify-validate&" + rawBody)
                    .retrieve()
                    .body(String.class);
            return "VERIFIED".equals(resp == null ? "" : resp.trim());
        } catch (Exception e) {
            log.warn("PayPal IPN validation call failed: {}", e.getMessage());
            return false;
        }
    }

    /** application/x-www-form-urlencoded → Map (URL-dekodiert). */
    private Map<String, String> parseForm(String body) {
        Map<String, String> map = new java.util.HashMap<>();
        for (String pair : body.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) continue;
            String key = urlDecode(pair.substring(0, eq));
            String value = urlDecode(pair.substring(eq + 1));
            map.put(key, value);
        }
        return map;
    }

    private String urlDecode(String s) {
        try { return java.net.URLDecoder.decode(s, java.nio.charset.StandardCharsets.UTF_8); }
        catch (Exception e) { return s; }
    }

    private String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }

    /**
     * Zahlung für einen Plan-Kauf: IMMER PayGate (Karte) auf die feste {@link #PLAN_PAYOUT_WALLET}.
     * Läuft auch im Mock-Modus über den echten PayGate-Checkout — kein Krypto-Fallback.
     */
    @Transactional
    public Payment createPlanPayment(Order order) {
        var existing = paymentRepo.findByOrderId(order.getId());
        if (existing.isPresent() && existing.get().getStatus() == Payment.Status.WAITING) {
            return existing.get();
        }
        PayGateProvider payGate = (PayGateProvider) requireProvider("paygate");
        CreatedPayment created = payGate.createForWallet(order, PLAN_PAYOUT_WALLET);
        Payment payment = new Payment();
        payment.setOrderId(order.getId());
        payment.setProvider("paygate");
        payment.setProviderPaymentId(created.providerPaymentId());
        payment.setPayCurrency(CARD);
        payment.setPayAmount(created.payAmount());
        payment.setPayAddress(created.payAddress());
        return paymentRepo.save(payment);
    }

    /**
     * Verarbeitet den Stripe-Webhook (checkout.session.completed). Verifikation über die
     * HMAC-Signatur im {@code Stripe-Signature}-Header gegen das Webhook-Signing-Secret.
     */
    @Transactional
    public void handleStripeWebhook(String signature, String rawBody) throws Exception {
        StripeProvider stripe = (StripeProvider) requireProvider("stripe");
        if (!stripe.verifyWebhook(signature, rawBody)) {
            botLog.error("🚨 Webhook Rejected", "Invalid Stripe signature on an incoming payment webhook.");
            throw new SecurityException("Invalid Stripe signature");
        }
        JsonNode node = mapper.readTree(rawBody);
        String type = node.path("type").asText();
        JsonNode object = node.path("data").path("object");
        String sessionId = object.path("id").asText();

        switch (type) {
            case "checkout.session.completed", "checkout.session.async_payment_succeeded" -> {
                Payment payment = paymentRepo.findByProviderPaymentId(sessionId)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown Stripe session: " + sessionId));
                String txRef = object.hasNonNull("payment_intent") ? object.get("payment_intent").asText() : sessionId;
                markFinished(payment, txRef);
            }
            case "checkout.session.expired" -> paymentRepo.findByProviderPaymentId(sessionId)
                    .ifPresent(p -> markClosed(p, Payment.Status.EXPIRED, Order.Status.EXPIRED));
            case "checkout.session.async_payment_failed" -> paymentRepo.findByProviderPaymentId(sessionId)
                    .ifPresent(p -> markClosed(p, Payment.Status.FAILED, Order.Status.CANCELLED));
            default -> log.info("Stripe event '{}' for session {} — no action", type, sessionId);
        }
    }

    /**
     * Bestätigung aus dem PayGate-Callback. Verifikation über das unerratbare
     * Token (UUID), das nur PayGate über die Callback-URL kennt.
     */
    @Transactional
    public void confirmPayGate(String token, String txHash, String valueCoin) {
        Payment payment = paymentRepo.findByProviderPaymentId(token)
                .orElseThrow(() -> new SecurityException("Unknown PayGate token"));
        if (!"paygate".equals(payment.getProvider())) {
            throw new SecurityException("Token does not belong to a PayGate payment");
        }
        if (valueCoin != null && !valueCoin.isBlank()) {
            log.info("PayGate payment {} received: {} USDC", token, valueCoin);
        }
        markFinished(payment, txHash);
    }

    /** Bestätigt eine Zahlung manuell — nur im Mock-Modus erlaubt (zum Testen). */
    @Transactional
    public void simulatePayment(long orderId) {
        if (!"mock".equals(props.getPayment().getProvider())) {
            throw new IllegalStateException("Simulation is only possible in mock mode.");
        }
        Payment payment = paymentRepo.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("No payment for order #" + orderId));
        markFinished(payment, "0xMOCKTX" + orderId);
    }

    private void markFinished(Payment payment, String txHash) {
        if (payment.getStatus() == Payment.Status.FINISHED) return; // idempotent bei doppelten Webhooks

        payment.setStatus(Payment.Status.FINISHED);
        payment.setConfirmedAt(Instant.now());
        if (txHash != null && !txHash.isBlank()) payment.setTxHash(txHash);
        paymentRepo.save(payment);

        Order order = orderRepo.findById(payment.getOrderId()).orElseThrow();
        order.setStatus(Order.Status.PAID);
        order.setPaidAt(Instant.now());
        orderRepo.save(order);

        String deliveryMessage = deliveryService.deliver(order);
        order.setStatus(Order.Status.DELIVERED);
        orderRepo.save(order);

        // Eigener DM-Titel/Text des Verkäufers (falls gesetzt), sonst Standard
        ShopUser seller = merchantFor(order);
        String title = seller != null && seller.getDeliveryTitle() != null && !seller.getDeliveryTitle().isBlank()
                ? seller.getDeliveryTitle()
                : "🎉 Your order is ready!";
        String intro = seller != null && seller.getDeliveryMessage() != null && !seller.getDeliveryMessage().isBlank()
                ? seller.getDeliveryMessage() + "\n\n"
                : "";
        // Eigenes Branding (Business): Farbe + Footer des Verkäufers, sonst Site-Standard
        Color dmColor = brandColor();
        String dmFooter = settings.brandName();
        if (seller != null && planService.isAtLeast(seller, "BUSINESS")) {
            if (seller.getBrandColor() != null && !seller.getBrandColor().isBlank()) {
                try { dmColor = Color.decode(seller.getBrandColor()); } catch (Exception ignored) {}
            }
            if (seller.getBrandFooter() != null && !seller.getBrandFooter().isBlank()) {
                dmFooter = seller.getBrandFooter();
            }
        }
        deliveryService.sendDm(order.getUserId(), new EmbedBuilder()
                .setTitle(title)
                .setDescription(intro + "**" + order.getProductName() + "** x" + order.getQuantity() + "\n\n" + deliveryMessage)
                .setColor(dmColor)
                .setFooter(dmFooter)
                .setTimestamp(Instant.now())
                .build());

        String saleSummary = "**Order:** #" + order.getId()
                + "\n**Buyer:** <@" + order.getUserId() + "> (" + order.getUsername() + ")"
                + "\n**Product:** " + order.getProductName() + " x" + order.getQuantity()
                + "\n**Amount:** " + order.getTotalPrice() + " " + settings.currencySymbol()
                + " (" + payment.getPayAmount() + " " + payment.getPayCurrency() + ")"
                + (payment.getTxHash() != null ? "\n**Tx:** `" + payment.getTxHash() + "`" : "");
        botLog.success("💰 Sale Completed", saleSummary);
        // Verkaufs-Log zusätzlich an den Discord-Webhook des Verkäufers (falls konfiguriert)
        ShopUser merchant = merchantFor(order);
        if (merchant != null) {
            webhookLog.success(merchant.getLogWebhookUrl(), "💰 Sale Completed", saleSummary);
        }

        // Nach dem Kauf zum Bewerten einladen (Sterne-Buttons per DM)
        maybePromptReview(order, seller);
    }

    /**
     * Schickt dem Käufer nach der Lieferung die Bewertungs-DM — aber nur, wenn der Verkäufer das
     * Review-Feature hat (Pro/Owner), es kein Plattform-/Plan-Produkt ist, der Käufer ein echter
     * Discord-Nutzer ist und dieses Produkt noch nicht bewertet hat.
     */
    private void maybePromptReview(Order order, ShopUser seller) {
        try {
            String buyerId = order.getUserId();
            if (buyerId == null || buyerId.startsWith("guest-")) return;
            var product = productRepo.findById(order.getProductId()).orElse(null);
            if (product == null) return;
            if (com.shop.service.PlanService.PLATFORM_CATEGORY.equals(product.getCategory())) return;
            boolean eligible = seller != null
                    && (props.getDiscord().adminIdList().contains(seller.getId())
                        || planService.isAtLeast(seller, "PRO"));
            if (!eligible) return;
            if (!seller.isReviewPromptEnabled()) return; // Verkäufer hat den Post-Kauf-Prompt deaktiviert
            if (reviewRepo.findByUserIdAndProductId(buyerId, order.getProductId()).isPresent()) return;
            deliveryService.sendReviewPrompt(buyerId, order.getId(), order.getProductName());
        } catch (Exception e) {
            log.warn("Review prompt for order #{} failed: {}", order.getId(), e.getMessage());
        }
    }

    private void markClosed(Payment payment, Payment.Status paymentStatus, Order.Status orderStatus) {
        if (payment.getStatus() != Payment.Status.WAITING) return;
        payment.setStatus(paymentStatus);
        paymentRepo.save(payment);
        orderRepo.findById(payment.getOrderId()).ifPresent(order -> {
            if (order.getStatus() == Order.Status.PENDING) {
                order.setStatus(orderStatus);
                orderRepo.save(order);
            }
        });
        botLog.info("ℹ️ Payment Closed",
                "Payment for order #" + payment.getOrderId() + " is " + paymentStatus + ".");
    }

    public Color brandColor() {
        return settings.brandColor();
    }
}
