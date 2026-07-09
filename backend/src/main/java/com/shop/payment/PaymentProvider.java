package com.shop.payment;

import com.shop.model.Order;
import com.shop.model.ShopUser;

/**
 * Abstraktion über den Zahlungsanbieter. Implementierungen: NOWPayments (echt), PayGate (Karte), Mock (Tests).
 * Zahlungen werden pro Bestellung zum Verkäufer (merchant) geroutet — hat der Verkäufer eigene
 * Zugangsdaten/Wallets hinterlegt, werden diese genutzt, sonst die Site-Konfiguration.
 */
public interface PaymentProvider {

    /** Eindeutiger Name, wird gegen shop.payment.provider gematcht. */
    String name();

    /**
     * Erstellt eine Zahlung beim Anbieter. Der Anbieter generiert pro Bestellung
     * eine frische Zahlungsadresse (keine Wiederverwendung).
     *
     * @param payCurrency API-Code der Währung, z. B. "btc" oder "usdttrc20"
     * @param merchant    Verkäufer des Produkts (null = Site-Betreiber/Plattform-Produkt)
     */
    CreatedPayment create(Order order, String payCurrency, ShopUser merchant);

    /** Verifiziert die Signatur eines eingehenden Webhooks (gegen gefälschte "bezahlt"-Meldungen). */
    boolean verifyWebhook(String signature, String rawBody);
}
