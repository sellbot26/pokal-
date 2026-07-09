# Pokal — Release auf Railway mit Domain `pokal.shop`

Der Code ist deploy-ready gemacht:
- **Port**: liest jetzt Railways dynamisches `$PORT` (`server.port=${PORT:8080}`)
- **Datenbank**: nutzt automatisch Railways Postgres (`PGHOST/PGPORT/PGDATABASE/PGUSER/PGPASSWORD`), sonst `DB_*`, sonst lokale Defaults
- **HTTPS hinter Proxy**: `forward-headers-strategy=framework` (OAuth-Redirects werden korrekt `https://pokal.shop`)
- **Build**: `backend/Dockerfile` (Multi-Stage, baut die Fat-Jar) + `backend/railway.json` (Healthcheck `/api/plans`)

Ich kann nicht in deine Railway-/IONOS-/Discord-Accounts. Mach die Schritte unten — dauert ~15 Min.

---

## 1) Code zu GitHub pushen
Railway deployt am einfachsten aus einem GitHub-Repo.
```bash
cd "C:/Users/xrsel/Desktop/bot golf"
git init
git add .
git commit -m "Pokal shop – release"
# Repo auf github.com anlegen, dann:
git branch -M main
git remote add origin https://github.com/<dein-user>/pokal.git
git push -u origin main
```
> `.env` ist in `.gitignore` — deine Secrets landen **nicht** auf GitHub. Gut so; die kommen als Railway-Variablen rein.

## 2) Railway-Projekt + Postgres
1. https://railway.app → **New Project → Deploy from GitHub repo** → dein Repo wählen.
2. Beim App-Service: **Settings → Root Directory = `backend`** (wichtig — der Dockerfile liegt dort).
3. Im Projekt **+ New → Database → PostgreSQL** hinzufügen.

## 3) Environment-Variablen am App-Service (Variables)
**Datenbank (Referenzen auf den Postgres-Service):**
```
PGHOST=${{Postgres.PGHOST}}
PGPORT=${{Postgres.PGPORT}}
PGDATABASE=${{Postgres.PGDATABASE}}
PGUSER=${{Postgres.PGUSER}}
PGPASSWORD=${{Postgres.PGPASSWORD}}
```
**App / Domain:**
```
BASE_URL=https://pokal.shop
BRAND_NAME=Pokal
OPEN_DASHBOARD=false
```
**Discord (aus deiner lokalen `.env` übernehmen):**
```
DISCORD_BOT_TOKEN=...
DISCORD_CLIENT_ID=...
DISCORD_CLIENT_SECRET=...
DISCORD_GUILD_ID=...          (optional)
ADMIN_IDS=1508539971160637515  (du bleibst Owner)
```
**Zahlungen:**
```
PAYMENT_PROVIDER=nowpayments   # für ECHTE Zahlungen (nicht "mock"!)
PAYGATE_WALLET=0x9824D446002d2AfBFac1D9B10dBB275EF46330fe
NOWPAYMENTS_API_KEY=...         (falls Krypto)
NOWPAYMENTS_IPN_SECRET=...
STRIPE_SECRET_KEY=sk_live_...   (falls Stripe)
STRIPE_WEBHOOK_SECRET=whsec_...
```
> **Wichtig:** `SPRING_PROFILES_ACTIVE` NICHT auf `local` setzen — sonst nimmt die App die lokale H2-Datei statt Postgres. Default-Profil = Postgres = korrekt.

## 4) Custom Domain `pokal.shop` in Railway
1. App-Service → **Settings → Networking → Custom Domain** → `pokal.shop` eintragen (und optional `www.pokal.shop`).
2. Railway zeigt dir jetzt einen **CNAME-Zielwert** (z. B. `abcd1234.up.railway.app`). **Diesen Wert** brauchst du für IONOS.

## 5) DNS bei IONOS (pokal.shop → Railway)
In deinem Link (IONOS → Domains → pokal.shop → DNS):

**Empfohlen (www als Hauptadresse, apex leitet weiter):**
| Typ | Host/Name | Wert |
|-----|-----------|------|
| CNAME | `www` | `<der-railway-wert>.up.railway.app` |
| (IONOS) Weiterleitung | `pokal.shop` | → `https://www.pokal.shop` (HTTP-301) |

Falls du `www` als Haupt-URL nimmst: setze oben `BASE_URL=https://www.pokal.shop`.

**Oder apex direkt (nur wenn IONOS „CNAME am @" bzw. eine A-Record-Option von Railway anbietet):**
- Trägt Railway für die Root-Domain einen **A-Record** (feste IP) an? Dann in IONOS `A @ → <IP>`.
- Bietet IONOS CNAME-Flattening am `@`? Dann `CNAME @ → <railway-wert>`.

> DNS-Änderungen brauchen 5 Min – paar Stunden. Railway stellt das TLS-Zertifikat automatisch aus, sobald die DNS-Auflösung passt.

## 6) Discord OAuth-Redirect freischalten
Discord Developer Portal → deine App → **OAuth2 → Redirects** → hinzufügen:
```
https://pokal.shop/login/oauth2/code/discord
```
(bzw. `https://www.pokal.shop/...`, je nachdem was deine Haupt-URL ist). Sonst schlägt der Login mit „redirect_uri mismatch" fehl.

## 7) Payment-Webhooks auf die neue Domain zeigen
- **NOWPayments**: IPN-URL = `https://pokal.shop/api/webhook/payment`
- **Stripe**: Webhook-Endpoint = `https://pokal.shop/api/webhook/stripe` (Event `checkout.session.completed`) → das Signing-Secret als `STRIPE_WEBHOOK_SECRET` eintragen
- **PayGate**: Callback läuft automatisch über `BASE_URL` — nichts zu tun

---

## Vor dem echten Release checken
- [ ] `PAYMENT_PROVIDER` steht auf einem echten Anbieter (nicht `mock`), sonst laufen Krypto-Käufe simuliert
- [ ] Login über `https://pokal.shop` funktioniert (Discord-Redirect gesetzt)
- [ ] Testkauf über Karte öffnet die echte PayGate-Seite
- [ ] **Uploads sind flüchtig**: Produktbilder liegen unter `/app/uploads` und gehen bei jedem Redeploy verloren. Für dauerhafte Bilder in Railway ein **Volume** an `/app/uploads` mounten (Service → Settings → Volumes) — oder externe Bild-URLs nutzen.
- [ ] Optional: eine 2. Instanz/Region ist nicht nötig; der Discord-Bot darf nur **einmal** laufen (ein Token = eine Verbindung).

## Lokal weiterentwickeln bleibt unverändert
`start.bat` nutzt weiter das `local`-Profil mit H2 — die Änderungen betreffen nur den Produktivbetrieb.
