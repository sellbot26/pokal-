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
        put("ETH", "eth");
        put("LTC", "ltc");
        put("USDT-TRC20", "usdttrc20");
        put("SOL", "sol");
    }};

    /** Pseudo-Währung für Kartenzahlung über PayGate. */
    public static final String CARD = "KARTE";

    /** Pseudo-Währung für Kartenzahlung über Stripe Checkout. */
    public static final String STRIPE = "STRIPE";

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

    public PaymentService(List<PaymentProvider> providerList, PaymentRepo paymentRepo, OrderRepo orderRepo,
                          ProductRepo productRepo, ShopUserRepo userRepo,
                          DeliveryService deliveryService, BotLogService botLog, WebhookLogService webhookLog,
                          ShopProperties props, SettingsService settings, ObjectMapper mapper,
                          com.shop.service.PlanService planService) {
        this.providers = providerList.stream()
                .collect(Collectors.toMap(PaymentProvider::name, Function.identity()));
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

    /** Verkäufer des bestellten Produkts — bestimmt, auf wessen Wallet/Konto die Zahlung läuft. */
    public ShopUser merchantFor(Order order) {
        return productRepo.findById(order.getProductId())
                .map(p -> p.getOwnerId() == null ? null : userRepo.findById(p.getOwnerId()).orElse(null))
                .orElse(null);
    }

    private PaymentProvider provider() {
        PaymentProvider p = providers.get(props.getPayment().getProvider());
        if (p == null) throw new IllegalStateException("Unknown payment provider: " + props.getPayment().getProvider());
        return p;
    }

    @Transactional
    public Payment createPayment(Order order, String currencyLabel) {
        boolean card = CARD.equals(currencyLabel);
        boolean stripe = STRIPE.equals(currencyLabel);
        String apiCode = stripe ? "card" : card ? "usdc" : CURRENCIES.get(currencyLabel);
        if (apiCode == null) throw new IllegalArgumentException("Unsupported currency: " + currencyLabel);

        var existing = paymentRepo.findByOrderId(order.getId());
        if (existing.isPresent() && existing.get().getStatus() == Payment.Status.WAITING) {
            return existing.get();
        }

        // Provider-Wahl:
        //  - Stripe / Karte brauchen eine echte Checkout-URL → IMMER der echte Provider.
        //  - Krypto: eigener NOWPayments-Account des Verkäufers > Site-NOWPayments >
        //    DIRECT (Adresse + Kurs, manuelle Bestätigung — funktioniert ohne jeden API-Key).
        //    Mock nur, wenn explizit PAYMENT_PROVIDER=mock gesetzt ist (lokales Testen).
        PaymentProvider chosen;
        if (stripe) chosen = requireProvider("stripe");
        else if (card) chosen = requireProvider("paygate");
        else chosen = cryptoProvider(merchantFor(order));
        CreatedPayment created = chosen.create(order, apiCode, merchantFor(order));
        Payment payment = new Payment();
        payment.setOrderId(order.getId());
        payment.setProvider(chosen.name());
        payment.setProviderPaymentId(created.providerPaymentId());
        payment.setPayCurrency(currencyLabel);
        payment.setPayAmount(created.payAmount());
        payment.setPayAddress(created.payAddress());
        return paymentRepo.save(payment);
    }

    private PaymentProvider requireProvider(String name) {
        PaymentProvider p = providers.get(name);
        if (p == null) throw new IllegalStateException("Payment provider not available: " + name);
        return p;
    }

    /** Krypto-Routing: Verkäufer-NOWPayments > Site-NOWPayments > mock (nur explizit) > direct. */
    private PaymentProvider cryptoProvider(ShopUser merchant) {
        if (merchant != null && merchant.getNowpaymentsApiKey() != null && !merchant.getNowpaymentsApiKey().isBlank()) {
            return requireProvider("nowpayments");
        }
        String configured = props.getPayment().getProvider();
        if ("nowpayments".equals(configured)) {
            String siteKey = props.getPayment().getNowpayments().getApiKey();
            if (siteKey != null && !siteKey.isBlank()) return requireProvider("nowpayments");
        }
        if ("mock".equals(configured)) return requireProvider("mock");
        return requireProvider("direct");
    }

    /**
     * Manuelle Zahlungsbestätigung durch den Verkäufer — nur für DIRECT-Zahlungen
     * (Käufer hat direkt an die Wallet überwiesen, kein automatischer Webhook).
     * Löst die normale Lieferung aus.
     */
    @Transactional
    public void confirmDirectPayment(long orderId) {
        Payment payment = paymentRepo.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("No payment for order #" + orderId));
        if (!"direct".equals(payment.getProvider())) {
            throw new IllegalStateException("Only direct wallet payments can be confirmed manually.");
        }
        if (payment.getStatus() != Payment.Status.WAITING) {
            throw new IllegalStateException("This payment is already " + payment.getStatus() + ".");
        }
        markFinished(payment, null);
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

    /** Verarbeitet den IPN-Webhook des Zahlungsanbieters. */
    @Transactional
    public void handleWebhook(String signature, String rawBody) throws Exception {
        JsonNode node = mapper.readTree(rawBody);
        String paymentId = node.path("payment_id").asText();
        String status = node.path("payment_status").asText();
        String txHash = node.hasNonNull("payin_hash") ? node.get("payin_hash").asText() : null;

        Payment payment = paymentRepo.findByProviderPaymentId(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown payment: " + paymentId));

        // Signatur gegen das Site-Secret prüfen — oder gegen das IPN-Secret des Verkäufers,
        // falls die Zahlung über dessen eigenen NOWPayments-Account lief.
        boolean valid = provider().verifyWebhook(signature, rawBody);
        if (!valid) {
            ShopUser merchant = orderRepo.findById(payment.getOrderId()).map(this::merchantFor).orElse(null);
            if (merchant != null && providers.get("nowpayments") instanceof NowPaymentsProvider np) {
                valid = np.verifyWithSecret(signature, rawBody, merchant.getNowpaymentsIpnSecret());
            }
        }
        if (!valid) {
            botLog.error("🚨 Webhook Rejected", "Invalid signature on an incoming payment webhook.");
            throw new SecurityException("Invalid webhook signature");
        }

        switch (status) {
            case "finished", "confirmed" -> markFinished(payment, txHash);
            case "expired" -> markClosed(payment, Payment.Status.EXPIRED, Order.Status.EXPIRED);
            case "failed", "refunded" -> markClosed(payment, Payment.Status.FAILED, Order.Status.CANCELLED);
            case "partially_paid" -> botLog.error("⚠️ Partial Payment",
                    "Order #" + payment.getOrderId() + " was only partially paid (payment " + paymentId + ").");
            default -> log.info("Webhook status '{}' for payment {} — no action", status, paymentId);
        }
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
