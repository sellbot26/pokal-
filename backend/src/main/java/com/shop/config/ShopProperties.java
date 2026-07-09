package com.shop.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "shop")
public class ShopProperties {

    private String brandName;
    private String brandColor;
    private String baseUrl;
    private String uploadDir;
    private int orderExpiryMinutes = 60;
    /** true = jeder eingeloggte Discord-Nutzer bekommt Admin-Rechte im Dashboard. */
    private boolean openDashboard = false;
    private Discord discord = new Discord();
    private Payment payment = new Payment();

    @Getter
    @Setter
    public static class Discord {
        private String token;
        private String guildId;
        private String adminRoleId;
        private String adminIds;
        private String logChannelId;
        private String ticketCategoryId;
        private String supportRoleId;

        public List<String> adminIdList() {
            if (adminIds == null || adminIds.isBlank()) return List.of();
            return Arrays.stream(adminIds.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
    }

    @Getter
    @Setter
    public static class Payment {
        private String provider = "mock";
        private NowPayments nowpayments = new NowPayments();
        private PayGate paygate = new PayGate();
        private Stripe stripe = new Stripe();

        @Getter
        @Setter
        public static class NowPayments {
            private String apiKey;
            private String ipnSecret;
        }

        @Getter
        @Setter
        public static class PayGate {
            /** Eigene USDC-Wallet (Polygon), auf die PayGate auszahlt. */
            private String wallet;
            /** Checkout-Anbieter auf der PayGate-Seite (moonpay, wert, guardarian, …). */
            private String checkoutProvider = "moonpay";
        }

        @Getter
        @Setter
        public static class Stripe {
            /** Stripe Secret Key (sk_live_… / sk_test_…). */
            private String secretKey;
            /** Signing Secret des Stripe-Webhooks (whsec_…). */
            private String webhookSecret;
        }
    }
}
