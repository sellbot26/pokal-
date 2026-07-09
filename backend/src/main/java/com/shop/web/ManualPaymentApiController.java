package com.shop.web;

import com.shop.model.ManualPayment;
import com.shop.repo.ManualPaymentRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ManualPaymentApiController {

    public record PaymentRequest(String customer, BigDecimal amount, String method,
                                 String status, String date, String note) {}

    private static final DateTimeFormatter CSV_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final ManualPaymentRepo paymentRepo;

    // ===================== Site-Admin (sieht alle manuellen Zahlungen) =====================

    @GetMapping("/api/admin/payments")
    public List<ManualPayment> list() {
        return paymentRepo.findAllByOrderByPaymentDateDesc();
    }

    @PostMapping("/api/admin/payments")
    public ManualPayment create(@RequestBody PaymentRequest req, @AuthenticationPrincipal OAuth2User principal) {
        ManualPayment payment = new ManualPayment();
        payment.setOwnerId(principal.getAttribute("id"));
        apply(payment, req);
        return paymentRepo.save(payment);
    }

    @PutMapping("/api/admin/payments/{id}")
    public ManualPayment update(@PathVariable long id, @RequestBody PaymentRequest req) {
        ManualPayment payment = paymentRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Zahlung nicht gefunden."));
        apply(payment, req);
        return paymentRepo.save(payment);
    }

    @DeleteMapping("/api/admin/payments/{id}")
    public Map<String, String> delete(@PathVariable long id) {
        if (!paymentRepo.existsById(id)) throw new IllegalArgumentException("Zahlung nicht gefunden.");
        paymentRepo.deleteById(id);
        return Map.of("status", "gelöscht");
    }

    @GetMapping("/api/admin/export/payments.csv")
    public ResponseEntity<String> exportCsv() {
        StringBuilder sb = new StringBuilder("id;datum;kunde;betrag;methode;status;notiz\n");
        for (ManualPayment p : paymentRepo.findAllByOrderByPaymentDateDesc()) {
            sb.append(p.getId()).append(';')
                    .append(CSV_DATE.format(p.getPaymentDate())).append(';')
                    .append(csv(p.getCustomer())).append(';')
                    .append(p.getAmount()).append(';')
                    .append(p.getMethod()).append(';')
                    .append(p.getStatus()).append(';')
                    .append(csv(p.getNote()))
                    .append('\n');
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=payments.csv")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(sb.toString());
    }

    // ===================== Tenant (eigenes Zahlungs-Buch) =====================

    @GetMapping("/api/my/payments")
    public List<ManualPayment> myList(@AuthenticationPrincipal OAuth2User principal) {
        return paymentRepo.findByOwnerIdOrderByPaymentDateDesc(principal.getAttribute("id"));
    }

    @PostMapping("/api/my/payments")
    public ManualPayment myCreate(@RequestBody PaymentRequest req, @AuthenticationPrincipal OAuth2User principal) {
        ManualPayment payment = new ManualPayment();
        payment.setOwnerId(principal.getAttribute("id"));
        apply(payment, req);
        return paymentRepo.save(payment);
    }

    @PutMapping("/api/my/payments/{id}")
    public ManualPayment myUpdate(@PathVariable long id, @RequestBody PaymentRequest req,
                                   @AuthenticationPrincipal OAuth2User principal) {
        ManualPayment payment = requireOwn(id, principal.getAttribute("id"));
        apply(payment, req);
        return paymentRepo.save(payment);
    }

    @DeleteMapping("/api/my/payments/{id}")
    public Map<String, String> myDelete(@PathVariable long id, @AuthenticationPrincipal OAuth2User principal) {
        requireOwn(id, principal.getAttribute("id"));
        paymentRepo.deleteById(id);
        return Map.of("status", "gelöscht");
    }

    private ManualPayment requireOwn(long id, String ownerId) {
        ManualPayment payment = paymentRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found."));
        if (!ownerId.equals(payment.getOwnerId())) {
            throw new SecurityException("This payment belongs to another user.");
        }
        return payment;
    }

    private void apply(ManualPayment payment, PaymentRequest req) {
        // Serverseitige Validierung — dem Client wird nicht vertraut
        if (req.customer() == null || req.customer().isBlank())
            throw new IllegalArgumentException("Kunde/Empfänger fehlt.");
        if (req.amount() == null || req.amount().signum() <= 0)
            throw new IllegalArgumentException("Betrag muss größer als 0 sein.");
        payment.setCustomer(req.customer().trim());
        payment.setAmount(req.amount().setScale(2, java.math.RoundingMode.HALF_UP));
        if (req.method() != null) payment.setMethod(ManualPayment.Method.valueOf(req.method()));
        if (req.status() != null) payment.setStatus(ManualPayment.Status.valueOf(req.status()));
        if (req.date() != null && !req.date().isBlank()) {
            try {
                payment.setPaymentDate(Instant.parse(req.date()));
            } catch (Exception e) {
                throw new IllegalArgumentException("Ungültiges Datum.");
            }
        }
        payment.setNote(req.note());
    }

    private String csv(String value) {
        if (value == null) return "";
        return value.replace(';', ',').replace('\n', ' ').replace('\r', ' ');
    }
}
