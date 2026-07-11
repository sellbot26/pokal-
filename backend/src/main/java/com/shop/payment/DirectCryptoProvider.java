package com.shop.payment;

import com.shop.model.Order;
import com.shop.model.Payment;
import com.shop.model.ShopUser;
import com.shop.repo.PaymentRepo;
import com.shop.service.CryptoRateService;
import com.shop.service.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Direkte Krypto-Zahlung OHNE externen Anbieter: Der Käufer überweist den per
 * CoinGecko-Kurs berechneten Betrag direkt an die Wallet des Verkäufers
 * (bzw. die Site-Wallet als Fallback). BTC/LTC werden automatisch auf der
 * Blockchain erkannt, andere Coins bestätigt der Verkäufer per Klick im Dashboard.
 * Kein API-Key, keine Registrierung nötig.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DirectCryptoProvider implements PaymentProvider {

    /** API-Code -> Anzeige-Symbol + Settings-Key. */
    private static final Map<String, String> CODE_TO_SYMBOL = Map.ofEntries(
            Map.entry("btc", "BTC"), Map.entry("eth", "ETH"), Map.entry("ltc", "LTC"),
            Map.entry("usdttrc20", "USDT"), Map.entry("sol", "SOL"), Map.entry("usdc", "USDC"),
            Map.entry("doge", "DOGE"), Map.entry("xrp", "XRP"), Map.entry("bch", "BCH"),
            Map.entry("trx", "TRX"));

    /** Aufschlag pro bereits offener Zahlung an dieselbe Adresse — macht jeden Betrag eindeutig. */
    private static final BigDecimal UNIQUE_STEP = new BigDecimal("0.02");

    private final SettingsService settings;
    private final CryptoRateService rates;
    private final PaymentRepo paymentRepo;

    @Override
    public String name() {
        return "direct";
    }

    @Override
    public CreatedPayment create(Order order, String payCurrency, ShopUser merchant) {
        String symbol = CODE_TO_SYMBOL.get(payCurrency == null ? "" : payCurrency.toLowerCase(Locale.ROOT));
        if (symbol == null) throw new IllegalArgumentException("Unsupported crypto currency: " + payCurrency);

        String wallet = resolveWallet(symbol, merchant);
        if (wallet == null || wallet.isBlank()) {
            throw new IllegalStateException(symbol + " payments are not set up for this seller yet. "
                    + "Add your " + symbol + " wallet in the dashboard (Settings → Payments).");
        }
        wallet = wallet.trim();

        // Eindeutiger Betrag: pro bereits WARTENDER Zahlung an dieselbe Wallet +0,02 (Fiat).
        // So lässt sich jede eingehende Zahlung eindeutig einem Käufer zuordnen.
        long open = paymentRepo.countByStatusAndProviderAndPayAddress(
                Payment.Status.WAITING, "direct", wallet);
        BigDecimal fiat = order.getTotalPrice().add(UNIQUE_STEP.multiply(BigDecimal.valueOf(open)));

        BigDecimal amount = convert(fiat, symbol);
        log.info("Direct-Crypto-Zahlung für Bestellung #{}: {} {} an {} (+{} offene Zahlungen)",
                order.getId(), amount, symbol, wallet, open);
        return new CreatedPayment("DIRECT-" + UUID.randomUUID(), wallet, amount);
    }

    /** Wallet des Verkäufers (Zeilenformat "BTC: bc1…"), sonst Site-Wallet aus den Settings. */
    private String resolveWallet(String symbol, ShopUser merchant) {
        if (merchant != null) {
            String own = CryptoWallets.parse(merchant.getCryptoWallets()).get(symbol);
            if (own != null && !own.isBlank()) return own;
        }
        return settings.get("wallet" + symbol, null);
    }

    /** Rechnet den Shop-Preis (EUR/USD) per Live-Kurs in den Coin-Betrag um. */
    private BigDecimal convert(BigDecimal fiatTotal, String symbol) {
        Map<String, Object> coin = rates.getRates().get(symbol);
        if (coin == null) throw new IllegalStateException("Live exchange rate unavailable — please try again in a minute.");
        String fiatKey = "USD".equalsIgnoreCase(settings.get("currency", "EUR")) ? "usd" : "eur";
        Object rate = coin.get(fiatKey);
        if (rate == null) throw new IllegalStateException("Live exchange rate unavailable — please try again in a minute.");
        BigDecimal price = new BigDecimal(rate.toString());
        int scale = switch (symbol) {
            case "USDT", "USDC" -> 2;
            case "DOGE", "XRP", "TRX" -> 4;
            default -> 8;
        };
        return fiatTotal.divide(price, scale, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    @Override
    public boolean verifyWebhook(String signature, String rawBody) {
        // Keine Webhooks — Bestätigung per Blockchain-Watcher (BTC/LTC) oder manuell im Dashboard.
        return false;
    }
}
