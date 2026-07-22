package com.shop.config;

import com.shop.auth.DiscordOAuth2UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RegexRequestMatcher;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final DiscordOAuth2UserService discordUserService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // CSRF-Token als Cookie, damit das JS-Frontend es als Header mitschicken kann
        CookieCsrfTokenRepository csrfRepo = CookieCsrfTokenRepository.withHttpOnlyFalse();
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        csrfHandler.setCsrfRequestAttributeName(null);

        http
                // CORS für /api/chat/** wird auf MVC-Ebene in WebConfig geregelt (native App/WebView).
                // Der Preflight (OPTIONS) ist per permitAll + CSRF-safe durchgelassen und landet dort.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepo)
                        .csrfTokenRequestHandler(csrfHandler)
                        // Webhooks (HMAC-verifiziert) + öffentlicher Gast-Checkout + SecureChat-Client
                        // (eigenes Token-Auth, kein Browser) brauchen kein CSRF-Token
                        .ignoringRequestMatchers("/api/webhook/**", "/api/checkout/**", "/api/chat/**"))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/index.html", "/shop.html",
                                "/terms.html", "/privacy.html", "/refund.html", "/cookies.html",
                                "/css/**", "/js/**", "/img/**", "/favicon.ico",
                                "/uploads/**", "/login/**", "/oauth2/**", "/error",
                                "/api/webhook/**", "/api/settings", "/api/plans",
                                // Öffentliche Verkäufer-Storefront (/s/{key}) ohne Login
                                "/storefront.html", "/s/**", "/api/storefront/**",
                                // Öffentlicher Web-Shop (Gast-Checkout ohne Discord-Login)
                                "/api/products", "/api/checkout/**",
                                // SecureChat-Desktop-App: eigenes Username/Passwort-Login mit Token
                                "/api/chat/**").permitAll()
                        // Vanity-Storefront-URLs: nur einzelne Kleinbuchstaben-Segmente (pokal.shop/deinname).
                        // Punkt/Mehr-Segment-Pfade (dashboard.html, /api/**) matchen NICHT → bleiben geschützt.
                        .requestMatchers(RegexRequestMatcher.regexMatcher(HttpMethod.GET, "/[a-z0-9-]+")).permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .oauth2Login(oauth -> oauth
                        .userInfoEndpoint(u -> u.userService(discordUserService))
                        .defaultSuccessUrl("/dashboard.html", true)
                        .failureUrl("/?error=login"))
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/")
                        .deleteCookies("JSESSIONID"))
                // API-Aufrufe ohne Session bekommen 401 statt Redirect zur Login-Seite
                .exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
                        (request, response, e) -> response.sendError(401, "Nicht eingeloggt"),
                        new AntPathRequestMatcher("/api/**")))
                // Standard ist X-Frame-Options: DENY — das blockiert sogar die eigene Storefront-Preview
                // im Dashboard-iframe. sameOrigin erlaubt nur die eigene Domain, fremde Seiten weiterhin nicht.
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
        return http.build();
    }
}
