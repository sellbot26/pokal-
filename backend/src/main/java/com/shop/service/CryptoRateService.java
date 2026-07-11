package com.shop.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

/** Live-Kurse über CoinGecko, 60 Sekunden gecacht. */
@Service
@Slf4j
public class CryptoRateService {

    private static final Map<String, String> COINGECKO_IDS = new LinkedHashMap<>() {{
        put("BTC", "bitcoin");
        put("ETH", "ethereum");
        put("LTC", "litecoin");
        put("USDT", "tether");
        put("SOL", "solana");
        put("USDC", "usd-coin");
        put("DOGE", "dogecoin");
        put("XRP", "ripple");
        put("BCH", "bitcoin-cash");
        put("TRX", "tron");
    }};

    private final RestClient client = RestClient.create("https://api.coingecko.com");
    private volatile Map<String, Map<String, Object>> cache = Map.of();
    private volatile long cachedAt;

    public Map<String, Map<String, Object>> getRates() {
        if (System.currentTimeMillis() - cachedAt < 60_000 && !cache.isEmpty()) {
            return cache;
        }
        try {
            String ids = String.join(",", COINGECKO_IDS.values());
            Map<String, Map<String, Object>> raw = client.get()
                    .uri("/api/v3/simple/price?ids={ids}&vs_currencies=eur,usd&include_24hr_change=true", ids)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            Map<String, Map<String, Object>> bySymbol = new LinkedHashMap<>();
            COINGECKO_IDS.forEach((symbol, id) -> {
                if (raw != null && raw.containsKey(id)) bySymbol.put(symbol, raw.get(id));
            });
            cache = bySymbol;
            cachedAt = System.currentTimeMillis();
        } catch (Exception e) {
            log.warn("CoinGecko nicht erreichbar: {}", e.getMessage());
        }
        return cache;
    }
}
