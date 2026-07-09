package com.shop.config;

import com.shop.auth.DiscordOAuth2UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

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
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepo)
                        .csrfTokenRequestHandler(csrfHandler)
                        // Webhooks werden per HMAC-Signatur verifiziert, nicht per CSRF-Token
                        .ignoringRequestMatchers("/api/webhook/**"))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/index.html", "/css/**", "/js/**", "/img/**", "/favicon.ico",
                                "/uploads/**", "/login/**", "/oauth2/**", "/error",
                                "/api/webhook/**", "/api/settings", "/api/plans").permitAll()
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
                        new AntPathRequestMatcher("/api/**")));
        return http.build();
    }
}
