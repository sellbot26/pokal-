package com.shop.payment;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Unterstützte Coins + Parsen/Serialisieren der pro-Verkäufer-Wallets.
 * Gespeichert wird weiterhin zeilenweise ("BTC: bc1…") im Feld cryptoWallets,
 * das Dashboard zeigt aber eigene Felder pro Coin.
 */
public final class CryptoWallets {

    /** Alle unterstützten Coin-Symbole in Anzeige-Reihenfolge. */
    public static final List<String> SYMBOLS =
            List.of("BTC", "LTC", "ETH", "SOL", "USDT", "USDC", "DOGE", "XRP", "BCH", "TRX");

    private static final Pattern WALLET_LINE = Pattern.compile(
            "^\\s*(BTC|ETH|LTC|SOL|USDT|USDC|DOGE|XRP|BCH|TRX)[^\\w]+(\\S+)\\s*$",
            Pattern.CASE_INSENSITIVE);

    private CryptoWallets() {}

    /** Zeilenformat → Map SYMBOL → Adresse (nur bekannte Symbole). */
    public static Map<String, String> parse(String lines) {
        Map<String, String> result = new LinkedHashMap<>();
        if (lines == null || lines.isBlank()) return result;
        for (String line : lines.split("\\R")) {
            Matcher m = WALLET_LINE.matcher(line);
            if (m.matches()) result.put(m.group(1).toUpperCase(Locale.ROOT), m.group(2));
        }
        return result;
    }

    /** Map SYMBOL → Adresse → Zeilenformat (leere Adressen werden übersprungen). */
    public static String serialize(Map<String, String> wallets) {
        if (wallets == null) return "";
        StringBuilder sb = new StringBuilder();
        for (String symbol : SYMBOLS) {
            String address = wallets.get(symbol);
            if (address == null || address.isBlank()) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(symbol).append(": ").append(address.trim());
        }
        return sb.toString();
    }
}
