package com.shop.payment;

import java.math.BigDecimal;

/** Ergebnis der Zahlungserstellung beim Provider. */
public record CreatedPayment(String providerPaymentId, String payAddress, BigDecimal payAmount) {
}
