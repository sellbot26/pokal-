package com.shop.web;

import com.shop.payment.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * IPN-Webhook des Zahlungsanbieters. Öffentlich erreichbar, aber jede Meldung
 * wird per HMAC-Signatur verifiziert — gefälschte "bezahlt"-Meldungen werden abgelehnt.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final PaymentService paymentService;

    @PostMapping("/api/webhook/payment")
    public ResponseEntity<String> paymentWebhook(
            @RequestHeader(value = "x-nowpayments-sig", required = false) String signature,
            @RequestBody String rawBody) {
        try {
            paymentService.handleWebhook(signature, rawBody);
            return ResponseEntity.ok("ok");
        } catch (SecurityException e) {
            log.warn("Webhook mit ungültiger Signatur abgelehnt");
            return ResponseEntity.status(401).body("invalid signature");
        } catch (Exception e) {
            log.error("Webhook-Verarbeitung fehlgeschlagen", e);
            return ResponseEntity.status(400).body("error");
        }
    }

    /** Stripe-Webhook. Verifikation über die HMAC-Signatur im Stripe-Signature-Header. */
    @PostMapping("/api/webhook/stripe")
    public ResponseEntity<String> stripeWebhook(
            @RequestHeader(value = "Stripe-Signature", required = false) String signature,
            @RequestBody String rawBody) {
        try {
            paymentService.handleStripeWebhook(signature, rawBody);
            return ResponseEntity.ok("ok");
        } catch (SecurityException e) {
            log.warn("Stripe-Webhook mit ungültiger Signatur abgelehnt");
            return ResponseEntity.status(401).body("invalid signature");
        } catch (Exception e) {
            log.error("Stripe-Webhook-Verarbeitung fehlgeschlagen", e);
            return ResponseEntity.status(400).body("error");
        }
    }

    /**
     * PayGate-Callback (GET). Verifikation über das unerratbare UUID-Token,
     * das nur der Server und PayGate kennen.
     */
    @GetMapping("/api/webhook/paygate")
    public ResponseEntity<String> paygateCallback(
            @RequestParam String token,
            @RequestParam(value = "txid_in", required = false) String txid,
            @RequestParam(value = "value_coin", required = false) String valueCoin) {
        try {
            paymentService.confirmPayGate(token, txid, valueCoin);
            return ResponseEntity.ok("ok");
        } catch (SecurityException e) {
            log.warn("PayGate-Callback mit ungültigem Token abgelehnt");
            return ResponseEntity.status(401).body("invalid token");
        } catch (Exception e) {
            log.error("PayGate-Callback fehlgeschlagen", e);
            return ResponseEntity.status(400).body("error");
        }
    }
}
