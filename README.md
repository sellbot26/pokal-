# 🛒 Discord Shop-Bot + Web-Dashboard (Crypto + PayGate)

Digitaler Shop als Discord-Bot (Java / JDA) mit Premium-Web-Dashboard (Dark Mode), gemeinsamer PostgreSQL-Datenbank, Krypto-Zahlungen über NOWPayments (BTC, ETH, LTC, USDT-TRC20, SOL) und **Kartenzahlung über PayGate** (paygate.to → Auszahlung als USDC auf deine Wallet).

## Dashboard-Bereiche

- **Übersicht** — Umsatz-Statistiken, Umsatzverlauf-Chart (Tag/Woche/Monat/Jahr), Monatsziel, letzte Bestellungen, Top-Produkte
- **Crypto & Wallets** — Live-Kurse (CoinGecko), EUR/USD→Crypto-Umrechner, Shop-Wallets verwalten
- **Produkte / Stock** — Produkt-CRUD, Lager-Übersicht mit Warnungen (ausverkauft / niedrig), Key-Pool, Schnell-Anpassung
- **Bestellungen / Zahlungen / Kunden / Gutscheine** — Verwaltung inkl. CSV-Export
- **✨ Embed-Editor** — Discord-Embeds mit Live-Vorschau bauen (Titel, Felder, Bilder, Author, Footer, Link-Buttons), speichern/laden/löschen und direkt über den Bot in einen Channel senden
- **Einstellungen** — Shop-Editor ohne Code: Name, Logo, Farbe, Währung (€/$), Beschreibung, Footer, Discord-Invite, Social Links, Wartungsmodus, Monatsziel, PayGate-Wallet — alles gilt sofort ohne Neustart

## Features

**Discord-Bot**
| Command | Beschreibung |
|---|---|
| `/shop` | Kategorien als Embed mit Select-Menü |
| `/product <name>` | Produktdetails (Bild, Preis, Beschreibung, Lager) |
| `/buy <produkt> [menge] [rabattcode]` | Kaufprozess: Zusammenfassung → Krypto-Auswahl → Adresse + QR-Code |
| `/orders` | Eigene Bestellhistorie |
| `/ticket` | Support-Ticket-Channel öffnen |
| `/admin product add/edit/remove` | Produktverwaltung |
| `/admin stock <produkt> <menge>` | Lagerbestand setzen |
| `/admin keys add <produkt> <keys>` | Lizenzkeys einpflegen |
| `/admin discount create <code> <prozent>` | Rabattcodes |
| `/admin stats` | Umsatz, Bestellungen, Top-Produkte |
| `/admin orders pending` | Offene Zahlungen |

Nach Zahlungsbestätigung (Webhook) liefert der Bot automatisch: **Rolle**, **Lizenzkey**, **Text** oder **Datei-Link** per DM. Alle Käufe/Fehler werden in den Log-Channel geschrieben.

**Web-Dashboard** (`http://localhost:8080`)
- Login **nur** über Discord OAuth2, Admin-Rechte über Discord-Rolle
- Admin: Umsatz-Übersicht, Produkt-CRUD mit Bild-Upload, Bestellverwaltung mit Blockexplorer-Links, Kunden sperren/entsperren, Rabattcodes, Live-Kurse (CoinGecko), CSV-Export
- Kunde: eigene Bestellungen mit Status, „Jetzt bezahlen"-Button mit QR-Code
- Dark-Theme mit Light-Mode-Toggle, responsive

## Schnellstart

### 1. Discord-App einrichten
1. https://discord.com/developers/applications → **New Application**
2. **Bot** → Token kopieren → `DISCORD_BOT_TOKEN`
3. **OAuth2 → General**: Client-ID & Secret kopieren → `DISCORD_CLIENT_ID` / `DISCORD_CLIENT_SECRET`
4. **OAuth2 → Redirects** hinzufügen: `http://localhost:8080/login/oauth2/code/discord`
   (bei öffentlichem Betrieb zusätzlich `https://deine-domain.de/login/oauth2/code/discord`)
5. Bot einladen: OAuth2 URL-Generator → Scopes `bot` + `applications.commands`, Bot-Permissions: *Manage Roles, Manage Channels, Send Messages, Embed Links, Attach Files*

### 2. Konfiguration
```bash
cp .env.example .env
# .env ausfüllen: Token, Client-ID/Secret, Guild-ID, Admin-Rolle etc.
```

### 3. Starten
```bash
docker compose up -d --build
```
Fertig: Bot ist online, Dashboard läuft auf `http://localhost:8080`.

## Zahlungsanbieter

### Mock-Modus (Standard, zum Testen)
`PAYMENT_PROVIDER=mock` — es wird eine Fake-Adresse generiert. Zahlung bestätigen:
- im Dashboard: Bestellungen → 💰-Button, **oder**
- per Webhook: `POST /api/webhook/payment` mit Header `x-nowpayments-sig: mock` und Body `{"payment_id":"MOCK-…","payment_status":"finished"}`

### NOWPayments (echt)
1. Account auf https://nowpayments.io → API-Key erstellen → `NOWPAYMENTS_API_KEY`
2. Einstellungen → IPN-Secret erstellen → `NOWPAYMENTS_IPN_SECRET`
3. `PAYMENT_PROVIDER=nowpayments` setzen
4. `BASE_URL` muss **öffentlich erreichbar** sein (Webhooks!), z. B. `https://shop.deine-domain.de`

### PayGate — Kartenzahlung (paygate.to)
1. USDC-Wallet (Polygon) im Dashboard unter **Einstellungen → PayGate** eintragen (oder `PAYGATE_WALLET` in der `.env`)
2. Sobald eine Wallet gesetzt ist, erscheint **„💳 Karte / Apple Pay"** als Zahlungsmethode im Bot-Checkout
3. Der Kunde bekommt einen Checkout-Link; nach der Zahlung ruft PayGate den Callback auf und die Lieferung läuft automatisch
4. Auch hier gilt: `BASE_URL` muss öffentlich erreichbar sein, sonst kommt der Callback nicht an
5. Verifikation läuft über ein unerratbares UUID-Token in der Callback-URL — es wird pro Bestellung neu erzeugt

Pro Bestellung wird eine frische Zahlungsadresse generiert (keine Wiederverwendung). Webhooks werden per HMAC-SHA512-Signatur verifiziert — gefälschte „bezahlt"-Meldungen werden abgelehnt.

## Sicherheit
- Alle Secrets ausschließlich über `.env` (nie im Code / Git — `.env` ist in `.gitignore`)
- Preise/Beträge werden **nur serverseitig** berechnet und validiert
- Webhook-Signaturprüfung (HMAC-SHA512, constant-time compare)
- Rate-Limiting auf allen API-Routen (120 Req/min pro IP)
- CSRF-Schutz (Cookie + Header) für das Dashboard
- Unbezahlte Bestellungen verfallen automatisch (Standard: 60 min), Lager wird zurückgebucht
- Noch offen (bewusst nicht im ersten Wurf): 2FA für Admin-Aktionen — bis dahin Admin-Rolle in Discord eng vergeben

## Entwicklung ohne Docker
Voraussetzungen: Java 21, Maven, laufendes PostgreSQL.
```bash
cd backend
# DB-Zugang + Secrets als Umgebungsvariablen setzen (siehe .env.example)
mvn spring-boot:run
```

## Projektstruktur
```
├── docker-compose.yml       # App + PostgreSQL
├── .env.example             # Vorlage für alle Secrets/Einstellungen
└── backend/
    ├── Dockerfile           # Multi-Stage-Build (Maven → JRE)
    └── src/main/
        ├── java/com/shop/
        │   ├── bot/         # JDA-Bot: Commands, Embeds, Ticket-System
        │   ├── auth/        # Discord OAuth2 Login
        │   ├── payment/     # PaymentProvider-Abstraktion, NOWPayments, Mock
        │   ├── service/     # Bestellungen, Lieferung, Statistiken, Kurse, QR
        │   ├── web/         # REST-API fürs Dashboard, Webhook, CSV-Export
        │   ├── model/       # Entities (Products, Orders, Payments, …)
        │   └── repo/        # Spring Data Repositories
        └── resources/static # Dashboard (HTML/CSS/JS, kein Framework)
```
