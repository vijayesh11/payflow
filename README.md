# PayFlow — Digital Wallet & P2P Payment Platform

![CI](https://github.com/YOUR_GITHUB_USERNAME/payflow/actions/workflows/ci.yml/badge.svg)

**Live demo:** _add your deployed link here_

PayFlow is a full-stack digital wallet and P2P payments platform built around one idea:
**every risk decision should be explained, proportionate and appealable.**

Big payment platforms are often criticised for opaque account limitations (funds held for up to 180 days
with little explanation), blanket holds on new sellers' money (up to 21 days), currency-conversion
markups hidden inside the exchange rate (often 3–4%), and disputes that feel one-sided. PayFlow is my take
on how a wallet could handle each of these better, while still stopping fraud. It blocks 92.5% of
simulated fraud at a 2% false-positive rate.

> Sandbox only: no real money, cards or banks are involved.

## What makes PayFlow different

| Common pain point | PayFlow's answer |
|---|---|
| "My account was limited and nobody can tell me why" | **Explainable decisions**: every block, hold and freeze shows the exact rule, in plain language, with what to do next. Frozen users can still sign in to see the reason and **appeal**. |
| No way to challenge an automated decision | **Appeals with a 48h SLA**: a human must decide and **give a reason the customer sees**. Approval acts instantly (releases the hold, executes the blocked payment, or unfreezes the account). |
| New users' money held for weeks | **Progressive release with a visible Trust Score (0–100)**: only part of a large payment is held (60% / 30% / 0% by tier), with an exact release time. Holds release early the moment the user becomes Trusted. Every point of the score is shown, with how to earn it. |
| Hidden FX markup on international payments | **Honest FX invoices**: freelancers bill overseas clients in USD/EUR/GBP/AED/SGD. The mid-market rate is **locked for 10 minutes**, there's one visible 0.5% fee, and every receipt shows how much more you kept than with a typical 3.5% markup + 4.4% fee. |
| Disputes decided behind closed doors | **Milestone escrow** for freelance work: the client funds a milestone (UPI PIN), the freelancer delivers, and the client approves or it **auto-releases**. Disputes run on a **shared evidence timeline** both parties see, and the reviewer's reasoning goes on the record. |

## Features

**Authentication & authorisation**
- **2-step verification:** after the password, a 6-digit OTP is required to sign in (can be turned off in Security)
- **UPI PIN:** a 6-digit PIN authorises every payment and escrow funding; it's set or reset only after an OTP, weak PINs are rejected, and 3 wrong attempts lock it
- **Card OTP (3-D Secure style):** adding money from a card needs a "bank" OTP, and nothing is charged until it's confirmed
- OTPs are 6 random digits, stored hashed, valid for 5 minutes, single use, 3 guesses each, and rate-limited per user
- Delivery is pluggable: **sandbox** (code shown in-app, for demos) or **real email** via SMTP
- JWT sessions (15 minutes, refreshed only while the user is active), plus idle sign-out after 15 minutes

**Customer app**
- Overview with available and held balance, Trust Score, monthly totals, a 14-day chart and "send again" contacts
- Send money in 3 steps (details → review → UPI PIN), with an optional note; blocked payments come with a plain-language explanation and an **Appeal** button
- **Resolution Center**: Trust Score breakdown, money on hold (with progress bars), blocked payments with explanations, and appeals
- **Escrow**: create contracts, fund, deliver, approve, dispute, add evidence, view the timeline
- **Get paid from abroad**: international invoices with a live FX quote, shareable public payment link and per-payment breakdown
- Activity feed (payments, top-ups, escrow, international payments) with filters, search, details and **CSV statement download**
- **Cards**: tokenized linking (the full number and CVV are never stored)
- **Security**: change password, UPI PIN, 2-step verification, previous sign-in, and recent account activity (device, IP, time)

**Admin / risk console**
- Live risk overview: 24h volume, block rate, appeals within or past SLA, disputes, money on hold, money in escrow, a transfers-per-hour chart and alerts by rule
- Fraud alert queue with severity filter and "mark reviewed"
- **Appeals & disputes** queue sorted by SLA deadline (overdue highlighted); every decision needs a written reason
- Searchable transaction monitor with rule-hit details
- User management with Trust Scores: **freeze (with a reason the user sees) / unfreeze**, and **unlock** sign-in and PIN locks
- **Audit log** of security events, exportable to CSV

**Payments engine**
- **ACID transfers**: debit, credit and ledger record commit together or not at all
- Row-level locking with deadlock-free lock ordering
- **Idempotency keys**, so a retried or double-clicked payment is never charged twice
- **Fraud rule engine** with 5 real-time rules plus a Spring `@Scheduled` batch scan, every hit **explained in plain language**

## Try it instantly (demo mode)

Start with `DEMO_MODE=true` (the default in `render.yaml`). The sign-in page then lists three ready accounts
(password `Demo@Pay2026`, UPI PIN `592047`), and OTPs appear on screen:

| Account | Shows |
|---|---|
| **Asha**: freelancer, *Trusted* | escrow work in progress, a paid EUR invoice with full FX breakdown, an open USD invoice with a public pay link |
| **Vikram**: client, *Building* | funded milestones; approve or dispute Asha's delivered design |
| **Neha**: new user, *New* | 60% of a payment on hold with an explanation, plus a blocked payment she can appeal |

Sign in as the admin (`admin@payflow.dev`) to review Neha's appeal and see it take effect immediately.

## Security controls (aligned with PCI-DSS v4.0 principles)

| Area | What PayFlow does |
|---|---|
| 3: Protect stored account data | Card number reduced to brand + last 4 digits + random token; **full PAN and CVV never stored or logged** (request `toString()` methods mask them) |
| 3.4: Mask PAN when displayed | Cards shown as `•••• 4242` everywhere |
| 7: Restrict access by need to know | Role-based access: `/api/admin/**` is ADMIN-only; users only see their own data |
| 8.3.4: Lock out repeated failed logins | 5 failed attempts lock the account for 30 minutes; admin can unlock |
| 8.3.6: Password strength | At least 12 characters, with letters and numbers; BCrypt-hashed |
| 8.4: Multi-factor authentication | OTP after password at sign-in; OTP before setting or resetting the UPI PIN; OTP before card top-ups |
| 8.2.8: Idle session timeout | Signed out after 15 min of inactivity; JWTs live 15 min and are refreshed only while the user is active |
| 10.2: Audit logs | Sign-ins, OTP and PIN failures, lockouts, card and password changes, appeals, admin decisions, each with IP and device |
| 6: Secure software | Security headers, input validation, rate limiting, no stack traces in responses |
| Payment authorisation (UPI-style, beyond PCI) | UPI PIN (BCrypt-hashed) on every transfer; 3 wrong attempts lock it for 24 h |

Idempotency keys are a payments-reliability pattern rather than a PCI-DSS requirement. They sit
alongside these controls.

## Tech stack

| Layer | Tech |
|---|---|
| Backend | Java 17, Spring Boot 3, Spring Security, Spring Data JPA, Spring Scheduler, Spring Mail |
| Database | PostgreSQL 16 (H2 for tests) |
| Auth | JWT (HS256, 15-min sliding sessions), BCrypt, OTP (SHA-256 hashed), UPI PIN |
| Frontend | React 18 + Vite, hand-built SVG charts (no chart library) |
| DevOps | Docker, Docker Compose, Nginx, GitHub Actions, GitHub Container Registry, Render Blueprint |

## How a transfer works (`WalletService.transfer`)

1. **UPI PIN check** (its own transaction, so wrong attempts are counted even when the request fails).
2. Look up only the wallet **ids**, so no stale copy is cached before locking.
3. Lock both wallet rows (`SELECT … FOR UPDATE`), **lower id first**, so opposite-direction transfers can't deadlock.
4. **Idempotency check:** a repeated `Idempotency-Key` returns the original result. If the same key comes with a different payload, the request is rejected (422).
5. **Fraud check:** any HIGH-severity hit blocks the payment. It's still saved as `BLOCKED`, with plain-language explanations, and can be appealed.
6. **Progressive release:** part of a large payment to a not-yet-trusted account goes to `heldBalance`, as a `Hold` with a reason and a release time.
7. Debit, credit (available + held) and save the transfer in **one transaction**.

## Fraud rules (thresholds in `application.yml` → `fraud:`)

| Rule | Type | Catches | Default |
|---|---|---|---|
| `VELOCITY` | real-time, HIGH | scripted draining of a stolen account | ≥ 5 transfers / 60 s |
| `REPEAT_RECIPIENT` | real-time, HIGH | a script draining an account into one mule | ≥ 3 transfers to the same person / 60 s |
| `MULE_BENEFICIARY` | real-time, HIGH | money flowing to an account already receiving blocked transfers (catches "low-and-slow" fraud) | ≥ 3 blocked incoming / 24 h |
| `GEO_ANOMALY` | real-time, HIGH | "impossible travel": the country changes minutes after the last payment | 30 min window |
| `THRESHOLD` | real-time, MEDIUM / HIGH | unusually large payments (MEDIUM also holds 50% for review) | alert > 5,000, block > 10,000 |
| `HIGH_OUTFLOW` | scheduled batch, MEDIUM | many medium payments that add up | > 8,000 / 60 min |

Geolocation is **simulated** through a "sending from" selector, sent as the `X-Country` header. A
production system would derive it from the client IP.

## Trust Score (fully visible to the user)

| Factor | Points |
|---|---|
| Account created | +10 |
| UPI PIN set | +15 |
| 2-step verification on | +10 |
| Card linked | +10 |
| Account age | up to +15 (over 30 days) |
| Payments received | +2 each, up to +20 |
| Escrow milestones delivered | +5 each, up to +20 |
| Open HIGH fraud alerts / blocked attempts / frozen | penalties |

**New** (< 40) holds 60% of incoming payments of ₹2,000+, **Building** (40–69) holds 30%, and **Trusted**
(70+) holds nothing. Holds release on schedule, or instantly once the user reaches Trusted.

## Results

Measured with `simulator/simulate.py` (80 labelled fraudulent and 99 legitimate transfers):

| Metric | Engine OFF | Engine ON |
|---|---|---|
| Successful fraudulent transfers | 80/80 | 6/80 |
| Fraudulent attempts blocked | 0% | **92.5%** |
| Fraudulent value stopped | 0% | **99.3%** |
| False positives (legit blocked) | 0% | 2.0% |

## Run locally

```bash
docker compose up --build
```
- App: http://localhost:3000
- Admin: `admin@payflow.dev` / `PayFlow@Admin2026` (override with `ADMIN_EMAIL` / `ADMIN_PASSWORD`)
- OTPs: by default the code appears in a labelled **"Sandbox inbox"** box on screen. For real emails, set
  `OTP_DELIVERY=email`, `OTP_SANDBOX_SHOW_CODE=false` and the `SPRING_MAIL_*` SMTP settings (see `docs/DEPLOYMENT.md`)
- Sandbox test cards: `4242 4242 4242 4242` (Visa), `5555 5555 5555 4444` (Mastercard), any future expiry, any CVV
- Amounts are in **₹ (INR)**

To start with demo data: `DEMO_MODE=true docker compose up --build`
(PowerShell: `$env:DEMO_MODE="true"; docker compose up --build`).
After pulling schema changes, reset the local database with `docker compose down -v`.

Run the tests with `cd backend && mvn test`. They cover:
- **Payments:** idempotent replay, key reuse, insufficient funds
- **Fraud and access control:** every fraud rule, admin-only access
- **Sign-in and cards:** the password policy, login lockout, card masking and Luhn validation
- **OTP:** wrong code, single use, top-ups credited only after the OTP
- **UPI PIN:** required for payments, wrong-PIN lockout, OTP reset, weak PINs rejected
- **Progressive holds:** sized by Trust Score
- **Appeals:** a blocked payment appealed and approved by an admin
- **Escrow:** fund with PIN, deliver, approve; dispute, evidence and admin refund
- **International invoices:** settled at the locked rate, and can't be paid twice
- **Frozen accounts:** can sign in, see the reason and appeal

### Fraud simulation

```bash
python simulator/simulate.py                                             # engine ON
FRAUD_ENABLED=false docker compose up -d backend && python simulator/simulate.py   # baseline
```

## API

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/api/auth/register` | — | `{email, password, fullName}` |
| POST | `/api/auth/login` | — | returns `{otpRequired, challenge}` when 2-step is on; 423 when locked |
| POST | `/api/auth/login/verify` | — | `{challengeId, code}` → session |
| GET | `/api/me`, `/api/me/activity`, `/api/me/trust` | USER | profile, own security events, Trust Score |
| POST | `/api/me/password`, `/api/me/two-factor`, `/api/me/refresh-token` | USER | account settings, sliding session |
| POST | `/api/me/pin/otp`, `/api/me/pin` | USER | set or reset the UPI PIN: get an OTP, then `{challengeId, code, pin}` |
| GET/POST/DELETE | `/api/cards` | USER | link `{cardNumber, expMonth, expYear, cvv, holderName}`; responses are masked |
| GET | `/api/wallet`, `/api/wallet/summary`, `/api/wallet/topups` | USER | balance (available + held), dashboard numbers, top-ups |
| POST | `/api/wallet/deposit` → `/deposit/confirm` | USER | `{cardId, amount}` → 202 OTP challenge → `{challengeId, code}` credits the wallet |
| POST | `/api/transfers` | USER | headers `Idempotency-Key` (required), `X-Country`; body `{recipientEmail, amount, note, pin}` → 201 / 200 replay / 403 blocked (with `explanations`) / 409 funds / 422 key reuse |
| GET | `/api/transfers` | USER | history |
| GET | `/api/resolution`, `/api/appeals` | USER | Resolution Center data |
| POST | `/api/appeals` | USER | `{type: HOLD/BLOCKED_TRANSFER/ACCOUNT_FREEZE, targetId, message}` |
| GET/POST | `/api/escrow`, `/api/escrow/milestones/{id}/fund\|submit\|approve\|dispute\|evidence\|refund` | USER | fund needs `{pin}`; others `{note}` |
| GET/POST | `/api/invoices`, `/api/invoices/{id}/cancel`, `/api/fx/quote?currency&amount` | USER | international invoices |
| GET/POST | `/api/public/invoices/{token}`, `/api/public/invoices/{token}/pay` | — | public pay page (rate-limited) |
| GET | `/api/public/config` | — | demo mode / sandbox flags |
| GET | `/api/admin/stats`, `/timeline`, `/alerts`, `/transfers`, `/users`, `/audit`, `/appeals`, `/disputes` | ADMIN | console data |
| POST | `/api/admin/appeals/{id}/decide`, `/api/admin/disputes/{id}/resolve` | ADMIN | `{approve, note}` / `{outcome: RELEASE/REFUND, note}` |
| POST | `/api/admin/alerts/{id}/resolve`, `/users/{id}/freeze` `{reason}`, `/unfreeze`, `/unlock` | ADMIN | actions (audited) |

## Production readiness

- One-click **Render Blueprint** (`render.yaml`): API (Docker), static web app with SPA rewrites and security headers, generated JWT secret
- Every configuration value comes from environment variables; nothing is hard-coded
- **Rate limiting** per IP on sign-in, sign-up, OTP verification and public payment (`RATE_LIMIT_PER_MINUTE`, default 60)
- Global error handler: clients never see stack traces; optimistic-lock conflicts return a friendly 409
- Health check at `/actuator/health`; the mail health indicator is disabled so a slow email provider can't mark the app as down
- Schedulers for fraud scans, hold release and escrow auto-release, all safe against races (row locks + `@Version`)
- A React error boundary keeps one broken page from blanking the app
- Integration tests cover every flow; CI runs them on every push and deploys only on green

## CI/CD

`.github/workflows/ci.yml` runs on every push. It builds the backend and runs its tests, then builds
the frontend. On `main`, it publishes Docker images to GHCR and then triggers a Render redeploy
through `RENDER_DEPLOY_HOOK_URL`. See [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md).

## Known limitations

- Card charges and FX rates are simulated (a production system would call a processor and a live mid-market rate API).
- Geolocation is simulated, and fraud rules are static thresholds (next step: per-user baselines or ML scoring).
- OTPs go by email, not SMS; SMS would need a paid provider (Twilio, MSG91).
- No refresh-token rotation; JWT logout is client-side.
- Escrow evidence is text and links only (no file uploads yet).
- `ddl-auto: update` instead of Flyway migrations; in-memory rate limiting suits a single instance (use Redis for several).
