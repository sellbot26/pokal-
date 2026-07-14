package com.shop.payment;

import java.math.BigDecimal;

/**
 * Ergebnis der Zahlungserstellung beim Provider.
 *
 * @param note optionale Pflicht-Notiz, die der Käufer bei der Zahlung angeben muss
 *             (nur PayPal Friends & Family — damit die Zahlung "persönlich" wirkt und
 *             eindeutig zugeordnet werden kann). Für andere Anbieter {@code null}.
 */
public record CreatedPayment(String providerPaymentId, String payAddress, BigDecimal payAmount, String note) {

    public CreatedPayment(String providerPaymentId, String payAddress, BigDecimal payAmount) {
        this(providerPaymentId, payAddress, payAmount, null);
    }
}
