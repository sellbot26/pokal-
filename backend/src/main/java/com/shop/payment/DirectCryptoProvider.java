package com.shop.payment;

import com.shop.model.Order;
import com.shop.model.ShopUser;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Direkte Krypto-Zahlung OHNE externen Anbieter: Der Käufer überweist den per
 * CoinGecko-Kurs berechneten Betrag direkt an die Wallet des Verkäufers
 * (bzw. die Site-Wallet als Fallback). Bestätigung erfolgt manuell durch den
 * Verkäufer im Dashboard ("Mark as paid") — dann läuft die Lieferung automatisch.
 * Kein API-Key, keine Registrierung nötig.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DirectCryptoProvider implements PaymentProvider {

    /** API-Code (NOWPayments-Konvention) -> Anzeige-Symbol + Settings-Key. */
    private static final Map<String, String> CODE_TO_SYMBOL = Map.of(
            "btc", "BTC", "eth", "ETH", "ltc", "LTC", "usdttrc20", "USDT", "sol", "SOL");

    private static final Pattern WALLET_LINE =
            Pattern.compile("^\\s*(BTC|ETH|LTC|SOL|USDT)[^\\w]+(\\S+)\\s*$", Pattern.CASE_INSENSITIVE);

    private final SettingsService settings;
    private final CryptoRateService rates;

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
                    + "Add your " + symbol + " wallet in the dashboard (Settings → Payment Methods).");
        }

        BigDecimal amount = convert(order.getTotalPrice(), symbol);
        log.info("Direct-Crypto-Zahlung für Bestellung #{}: {} {} an {}", order.getId(), amount, symbol, wallet);
        return new CreatedPayment("DIRECT-" + UUID.randomUUID(), wallet.trim(), amount);
    }

    /** Wallet des Verkäufers (Zeilenformat "BTC: bc1…"), sonst Site-Wallet aus den Settings. */
    private String resolveWallet(String symbol, ShopUser merchant) {
        if (merchant != null && merchant.getCryptoWallets() != null) {
            for (String line : merchant.getCryptoWallets().split("\\R")) {
                Matcher m = WALLET_LINE.matcher(line);
                if (m.matches() && m.group(1).equalsIgnoreCase(symbol)) return m.group(2);
            }
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
        int scale = "USDT".equals(symbol) ? 2 : 8;
        return fiatTotal.divide(price, scale, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    @Override
    public boolean verifyWebhook(String signature, String rawBody) {
        // Keine Webhooks — Bestätigung erfolgt manuell durch den Verkäufer im Dashboard.
        return false;
    }
}
