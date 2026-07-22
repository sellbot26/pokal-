package com.shop.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS auf MVC-Ebene für die Chat-API.
 *
 * Die native SilentLink-/SecureChat-App läuft auf dem Handy in einer WebView unter
 * fremdem Origin (z. B. {@code https://localhost}) und ruft {@code /api/chat/**}
 * cross-origin auf. Der {@code X-Chat-Token}-Header löst einen Preflight (OPTIONS)
 * aus. Ohne passende CORS-Regel weist Spring-MVC den Preflight mit
 * "Invalid CORS request" (403) ab → in der App: "Failed to fetch".
 *
 * Die Chat-Endpunkte authentifizieren sich per Token-Header (nicht per Cookie),
 * daher sind keine Credentials nötig und wir können alle Origins erlauben.
 * Ergänzt die Security-seitige CORS-Config in {@link SecurityConfig}.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/chat/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
