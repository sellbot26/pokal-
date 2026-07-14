package com.shop.payment;

import com.shop.model.Order;
import com.shop.model.Payment;
import com.shop.model.ShopUser;
import com.shop.repo.PaymentRepo;
import com.shop.service.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * PayPal "Friends & Family": Der Käufer sendet den Betrag direkt als Freunde-&-Familie-Zahlung
 * an die PayPal-Adresse des Verkäufers (bzw. die Site-Adresse als Fallback) UND gibt dabei ein
 * vorgegebenes Notiz-Wort an. Der Zahlungseingang wird über PayPal IPN AUTOMATISCH erkannt
 * (siehe {@code /api/webhook/paypal}) und die Bestellung ausgeliefert. Ohne eingerichtetes IPN
 * bestätigt der Verkäufer den Eingang per Klick in den Orders (Fallback).
 *
 * <p>payAddress = PayPal-Adresse (E-Mail), payAmount = eindeutiger Betrag in Shop-Währung
 * (+0,01 pro offener Zahlung an dieselbe Adresse), note = Pflicht-Notiz für die Zahlung.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PayPalFriendsProvider implements PaymentProvider {

    /** Site-weiter Settings-Key für die Fallback-PayPal-Adresse (Plattform-Produkte / Verkäufer ohne eigene). */
    public static final String SITE_KEY = "paypalFfEmail";

    /** Aufschlag pro bereits offener Zahlung an dieselbe Adresse — macht jeden Betrag eindeutig. */
    private static final BigDecimal UNIQUE_STEP = new BigDecimal("0.01");

    /**
     * Harmlose Alltags-Wörter für die Pflicht-Notiz. Eine echte Notiz lässt die F&F-Zahlung
     * "persönlich" wirken (kein Shop-Bezug) und senkt so das Risiko, dass PayPal das Konto sperrt.
     */
    private static final List<String> NOTE_WORDS = List.of(
            "Fahrrad", "Auto", "Geschenk", "Buch", "Kaffee", "Pizza", "Konzert", "Urlaub",
            "Kamera", "Gitarre", "Schuhe", "Blumen", "Kuchen", "Reise", "Spiel", "Jacke");

    private final SettingsService settings;
    private final PaymentRepo paymentRepo;

    @Override
    public String name() {
        return "paypalff";
    }

    /** PayPal-Adresse des Verkäufers, falls hinterlegt — sonst die Site-Adresse aus den Settings. */
    public String addressFor(ShopUser merchant) {
        if (merchant != null && merchant.getPaypalFfEmail() != null && !merchant.getPaypalFfEmail().isBlank()) {
            return merchant.getPaypalFfEmail().trim();
        }
        return settings.get(SITE_KEY, null);
    }

    public boolean isConfiguredFor(ShopUser merchant) {
        String address = addressFor(merchant);
        return address != null && !address.isBlank();
    }

    @Override
    public CreatedPayment create(Order order, String payCurrency, ShopUser merchant) {
        String address = addressFor(merchant);
        if (address == null || address.isBlank()) {
            throw new IllegalStateException("PayPal Friends & Family is not set up for this seller yet. "
                    + "Add your PayPal address in the dashboard (Settings → Payments).");
        }
        address = address.trim();

        // Eindeutiger Betrag: pro bereits WARTENDER Zahlung an dieselbe Adresse +0,01.
        long open = paymentRepo.countByStatusAndProviderAndPayAddress(Payment.Status.WAITING, name(), address);
        BigDecimal amount = order.getTotalPrice()
                .add(UNIQUE_STEP.multiply(BigDecimal.valueOf(open)))
                .setScale(2, RoundingMode.HALF_UP);

        String note = NOTE_WORDS.get(ThreadLocalRandom.current().nextInt(NOTE_WORDS.size()));

        log.info("PayPal-F&F-Zahlung für Bestellung #{}: {} an {} (Notiz \"{}\", +{} offene)",
                order.getId(), amount.toPlainString(), address, note, open);
        return new CreatedPayment("PPFF-" + UUID.randomUUID(), address, amount, note);
    }

    @Override
    public boolean verifyWebhook(String signature, String rawBody) {
        // IPN-Validierung läuft über den Rückruf an PayPal (_notify-validate), siehe PaymentService.
        return false;
    }
}
