package com.shop.service;

import com.shop.model.Order;
import com.shop.model.Payment;
import com.shop.payment.PaymentService;
import com.shop.repo.OrderRepo;
import com.shop.repo.PaymentRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Erkennt Direct-Krypto-Zahlungen AUTOMATISCH — ohne API-Key, ohne Anmeldung.
 * Fragt für offene BTC-/LTC-Zahlungen kostenlose, öffentliche Blockchain-Explorer ab
 * (blockstream.info / litecoinspace.org, Blockstream-kompatible API) und bestätigt die
 * Bestellung, sobald eine passende Zahlung an die Wallet eingegangen ist. Andere Coins
 * werden weiterhin per Ein-Klick vom Verkäufer bestätigt.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CryptoWatchService {

    /** payCurrency-Label -> Explorer-Basis-URL (Blockstream-kompatibles API). */
    private static final Map<String, String> EXPLORERS = Map.of(
            "BTC", "https://blockstream.info/api",
            "LTC", "https://litecoinspace.org/api");

    private final PaymentRepo paymentRepo;
    private final OrderRepo orderRepo;
    private final PaymentService paymentService;
    private final RestClient client = RestClient.create();

    @Scheduled(fixedDelay = 90_000, initialDelay = 30_000)
    public void checkPending() {
        List<Payment> waiting;
        try {
            waiting = paymentRepo.findAll().stream()
                    .filter(p -> p.getStatus() == Payment.Status.WAITING)
                    .filter(p -> "direct".equals(p.getProvider()))
                    .filter(p -> EXPLORERS.containsKey(p.getPayCurrency()))
                    .toList();
        } catch (Exception e) {
            return;
        }
        for (Payment p : waiting) {
            try {
                if (isPaid(p)) {
                    log.info("Auto-detected {} payment for order #{}", p.getPayCurrency(), p.getOrderId());
                    paymentService.confirmDirectPayment(p.getOrderId());
                }
            } catch (Exception e) {
                log.debug("Crypto-Check für Order #{} fehlgeschlagen: {}", p.getOrderId(), e.getMessage());
            }
        }
    }

    /** Prüft, ob an die Zahladresse ein passender Betrag NACH der Bestellung eingegangen ist. */
    private boolean isPaid(Payment payment) {
        String base = EXPLORERS.get(payment.getPayCurrency());
        String address = payment.getPayAddress();
        if (address == null || address.isBlank() || payment.getPayAmount() == null) return false;

        // Enges Fenster (±0,1 %): Beträge werden pro offener Zahlung um +0,02 € versetzt,
        // damit jede eingehende Zahlung eindeutig EINEM Käufer zugeordnet werden kann.
        long expectedSats = payment.getPayAmount().movePointRight(8).longValue();
        long minSats = (long) (expectedSats * 0.999);
        long maxSats = (long) (expectedSats * 1.001) + 1;
        long createdAt = orderRepo.findById(payment.getOrderId())
                .map(Order::getCreatedAt).map(java.time.Instant::getEpochSecond).orElse(0L) - 900; // 15 Min Puffer

        List<Map<String, Object>> txs = client.get()
                .uri(base + "/address/{addr}/txs", address)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        if (txs == null) return false;

        for (Map<String, Object> tx : txs) {
            Map<?, ?> status = (Map<?, ?>) tx.get("status");
            boolean confirmed = status != null && Boolean.TRUE.equals(status.get("confirmed"));
            if (!confirmed) continue; // nur bestätigte Transaktionen
            Object blockTime = status.get("block_time");
            long t = blockTime instanceof Number n ? n.longValue() : 0L;
            if (t < createdAt) continue; // ältere Zahlungen ignorieren

            long toUs = 0L;
            Object vout = tx.get("vout");
            if (vout instanceof List<?> outs) {
                for (Object o : outs) {
                    if (o instanceof Map<?, ?> out && address.equals(out.get("scriptpubkey_address"))) {
                        Object v = out.get("value");
                        if (v instanceof Number vn) toUs += vn.longValue();
                    }
                }
            }
            if (toUs >= minSats && toUs <= maxSats) return true;
        }
        return false;
    }
}
