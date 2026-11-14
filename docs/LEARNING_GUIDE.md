# PayFlow — Learning Guide & Interview Prep

This file is for you, not for recruiters. Read it alongside the code until you can explain every
part without notes, because a PayPal interviewer will pick one line from your resume and keep asking.

---

## 1. The concepts, in plain words

**Digital wallet / P2P.** Each user has one wallet with a balance. P2P (peer-to-peer) means users send
money directly to each other, like PayPal, Google Pay or PhonePe.

**ACID** is the guarantee a database transaction gives you:
- **Atomic:** all steps happen or none do. If the debit succeeds but the credit crashes, the debit is undone.
- **Consistent:** rules always hold, e.g. a balance never goes negative.
- **Isolated:** two transfers running at the same time don't see each other's half-finished work.
- **Durable:** once committed, the change survives a crash.

In the code this is `@Transactional` on `WalletService.transfer` plus PostgreSQL.

**Race condition / lost update.** Alice has ₹100 and two ₹80 transfers run at the same moment. Both read
100, both pass the check, and both subtract. Without locking she ends at ₹20 after sending ₹160.
`findByIdForUpdate` (`SELECT … FOR UPDATE`) makes the second transfer wait until the first commits.

**Deadlock.** Transfer 1 locks A and waits for B, while transfer 2 locks B and waits for A. Both wait forever.
The fix is to always lock in the same order (lower id first).

**Idempotency.** An operation is idempotent if doing it twice has the same effect as doing it once. Suppose the
phone sends "pay ₹500", the server pays, and the response is lost in a bad network. The app retries.
Without protection, the user pays twice. With protection, the client sends the same `Idempotency-Key`
on the retry, the server sees it already handled that key, and it returns the original result.
Stripe, PayPal and Adyen APIs all work this way.

**JWT (JSON Web Token).** After login, the server gives the client a signed token containing the user id,
role and expiry. The client sends it as `Authorization: Bearer <token>` on every request. The server checks
the signature using its secret key, so it needs no session storage (it is *stateless*). A JWT's contents are
encoded, not encrypted, so never put secrets in one.

**Authentication vs authorization.** Authentication answers *who are you* (login, JWT). Authorization answers
*what are you allowed to do* (`/api/admin/**` requires ADMIN).

**BCrypt.** Passwords are stored as slow, salted hashes, so even a leaked database doesn't reveal them.

**Fraud rule engine.** A list of independent checks (the Strategy pattern). Each returns "no problem" or a hit
with a severity, and the engine combines them into allow, alert or block. Thresholds live in config, so risk
teams can tune them without a code deploy.

**Spring Scheduler.** `@Scheduled` runs a method every N ms. Real-time rules look at one transfer at a time. The
scheduled scan looks at totals over an hour and catches fraud that is split into many small payments.

**Docker / Compose.** Docker packages each service with everything it needs to run. Compose starts the database,
backend and frontend together with one command.

**CI/CD.** Continuous Integration means every push automatically builds and tests the code. Continuous Delivery
means a passing build on `main` automatically produces deployable artifacts (here, Docker images on GHCR).

---

## 2. Suggested order to study / rebuild it

Don't just upload this. Go through it in this order, run it at each step, and change something small each time
so you really understand it.

1. **Entities + repositories** (`model/`, `repository/`). Draw the 4 tables on paper.
2. **Auth** (`AuthService`, `JwtService`, `JwtAuthFilter`, `SecurityConfig`). Register, log in, then paste the
   token into https://jwt.io to see what's inside.
3. **Wallet + transfer** (`WalletService`). Read `transfer()` line by line; it's the heart of the project.
   Try it: send the same `Idempotency-Key` twice with Postman or curl and watch the second response say `replayed: true`.
4. **Fraud engine** (`fraud/`). Change `max-transfers` to 3 and see the effect.
5. **Admin API + React dashboard.**
6. **Docker + CI.** Push to GitHub and watch the Actions tab go green.
7. **Run the simulator** and note your real numbers.
8. **Add one feature yourself** (ideas below), so part of the project is unmistakably yours.

Own-feature ideas: a per-user daily transfer limit rule; a "new recipient + large amount" rule; refunds or
reversals; transaction PDF statements; rate limiting on `/api/auth/login`; Flyway migrations; Swagger/OpenAPI docs;
a Testcontainers test against real PostgreSQL that fires 50 concurrent transfers and asserts total money is conserved.

---

## 3. Your resume bullets: what to fix

Be careful here, because interviewers check these claims.

1. **"a core PCI-DSS design pattern"**: this is inaccurate. PCI-DSS is a security standard for protecting
   **cardholder data** (card numbers, encryption, access control, logging). Idempotency keys are not part of it;
   they're an API reliability pattern. A payments interviewer will likely catch this. Say instead:
   *"…idempotency keys on transfer endpoints to prevent duplicate charges on client retries, the pattern used by Stripe and PayPal APIs."*

2. **"reducing simulated fraudulent transactions by 92%"**: only use a number the simulator actually printed,
   and know how it was measured. With the default scenario mix, expect detection well below 100%, because the
   low-and-slow scenario is built to evade the real-time rules. That's a good talking point, not a weakness.
   Example honest phrasing: *"…blocked X% of simulated fraudulent attempts (Y% of fraudulent value) at a Z% false-positive rate across N labelled transfers."*
   If you're asked "92% compared to what?", the answer is "versus the same traffic with the engine disabled."

3. **"deployed via CI/CD pipeline"**: as built, the pipeline tests the code and publishes Docker images. That's
   accurate if you say *"CI/CD pipeline (GitHub Actions) that tests every push and publishes Docker images."*
   If you want to say "deployed", actually deploy it (Render, Railway or Fly.io have free or cheap tiers) and put the live link on your resume.

4. **"Architected"** is fine for a solo project, as long as you can defend each design choice in section 4.

Suggested rewrite:

> **PayFlow: Digital Wallet & P2P Payment Simulation Platform** | Java, Spring Boot, PostgreSQL, React.js, JWT, Docker, GitHub Actions
> - Built a full-stack digital wallet with ACID fund transfers using row-level locking and deadlock-free lock ordering, JWT authentication, role-based access (user/admin), and idempotency keys that prevent duplicate charges on client retries.
> - Designed a configurable fraud rule engine (velocity, geolocation-anomaly and amount-threshold rules plus a Spring Scheduler batch scan) that blocked **X%** of simulated fraudulent transfers at a **Z%** false-positive rate; shipped with a React monitoring dashboard, Docker Compose and a GitHub Actions CI/CD pipeline.

---

## 4. Questions you should be ready for (with short answers)

**Why BigDecimal, not double?** Doubles can't represent 0.1 exactly, so `0.1 + 0.2 != 0.3`. Money must be exact.

**What happens if two requests with the same idempotency key arrive at the same time?** Both try to lock the
sender's wallet row. The second waits. When the first commits, the second runs its idempotency lookup, finds
the committed transfer, and replays it. The unique DB constraint `(sender_wallet_id, idempotency_key)` is a backstop.

**Why check idempotency after taking the lock, not before?** Before the lock, both racing requests would see
"no existing transfer" and both proceed.

**Why load only the wallet id before locking?** Hibernate caches entities per transaction. If the wallet were
loaded unlocked first, the later `FOR UPDATE` query would return the cached, possibly stale object.

**Pessimistic vs optimistic locking?** Pessimistic means lock the row and make others wait; it's good when
conflicts are likely, as with hot wallets. Optimistic (`@Version`) means no lock, and the save fails if someone else
changed the row, so you retry; it's good when conflicts are rare. This project uses pessimistic locking for transfers
and keeps `@Version` as a safety net.

**Why save BLOCKED transfers instead of throwing an error?** It creates an audit trail, lets blocked attempts
count toward velocity, and lets the admin see them. Throwing would roll everything back, including the alert.

**What's a false positive here, and why does it matter?** A real customer gets blocked. In payments that means lost
revenue and angry users, so detection rate alone is meaningless without the false-positive rate.

**How would you scale this?** Make the API stateless (JWT already makes this possible) and run several instances behind a
load balancer. Move fraud checks to an async pipeline (Kafka) for non-blocking rules, cache velocity counters in Redis,
add read replicas for dashboards, and use a double-entry ledger table instead of mutable balances.

**What's a double-entry ledger?** Every transfer writes two immutable entries (−100 from A, +100 to B), and balances
are derived by summing them. Nothing is ever updated in place, so history can't be lost, and the books always sum to zero.

**JWT downsides?** It can't be revoked before expiry (you need short expiry plus refresh tokens or a denylist), and the
payload is readable by anyone.

**How did you get 92%?** I ran a labelled simulation (`simulator/simulate.py`) of legitimate traffic plus four
fraud patterns: scripted draining, foreign takeover, a large cash-out, and low-and-slow, all cashing out to two
mule accounts. With the engine off, every fraudulent transfer succeeds. With it on, only the first few transfers
of the first victims get through, before the repeat-recipient rule fires and the mule accounts get flagged. After
that, the mule rule blocks everything flowing to them, including low-and-slow fraud that no single-transfer rule
catches. The cost is about a 2% false-positive rate, from legitimate users on a VPN.

**What are the limits of that number?** It's synthetic data, and I designed the scenarios myself. A fraud ring that
used a fresh mule for every victim would get further. Real systems add device fingerprinting, per-user baselines
and ML scoring on top of rules like these.

**Your resume mentions PCI-DSS. Explain.** First a one-line correction: idempotency keys are a payments
*reliability* pattern, not a PCI-DSS requirement. Then the real PCI work in the project:
- **Req. 3, stored card data:** card numbers are validated (Luhn) and immediately reduced to brand, last 4 digits
  and a random token. The full number and CVV are never saved, and even the request DTO's `toString()` masks them,
  so they can't leak into logs.
- **Req. 8.3.4, lockout:** 5 wrong passwords lock the account for 30 minutes. The counter is saved even though the
  login throws an error (`@Transactional(noRollbackFor = ApiException.class)`); without that, lockout would never trigger.
- **Req. 8.3.6, passwords:** at least 12 characters with letters and numbers, hashed with BCrypt.
- **Req. 8.2.8, idle timeout:** 15-minute JWTs, refreshed only while the user is active, plus a client-side idle sign-out.
- **Req. 10.2, audit trail:** sign-ins, failures, lockouts, card and password changes and admin actions, each with IP and device.
- **Req. 7, least privilege:** role-based access, and users can only touch their own wallet and cards.
Finish with: "In a real system, the card number would go straight from the browser to a PCI-certified processor
(Stripe, Braintree), so it never touches our servers at all. I simulated that tokenization step."

**How do your OTPs work, and why are they secure?** Each OTP is 6 digits from `SecureRandom`, and only
`SHA-256(challengeId + code)` is stored. It expires in 5 minutes, works once, and allows 3 wrong guesses.
Users can request at most 8 codes per 15 minutes, which prevents OTP flooding and brute force. Wrong
guesses are counted in a separate transaction (`REQUIRES_NEW`), so throwing an error doesn't roll the
counter back. The client never sends a user id, only a random `challengeId`. For card top-ups, the
amount and card are saved **inside the challenge**, so a valid code can't be reused to approve a
different amount.

**Why OTP for login but PIN for payments?** That's how UPI separates them. The OTP proves you hold the
device or email (possession), and the PIN proves you know a secret (knowledge). Together that's two
factors. Typing a PIN for every payment is fast, while an OTP for every payment would be slow and costly to send.

**How is the UPI PIN stored? Could you just use SHA-256?** It's hashed with BCrypt, like a password. A
6-digit PIN has only a million possibilities, so a fast hash like SHA-256 could be cracked instantly if
the database leaked. BCrypt is deliberately slow, and the 3-attempt lock stops online guessing.

**What if someone steals the session token?** They still can't move money without the PIN. They can't
reset the PIN without an OTP sent to the user's email. And the token dies after 15 minutes of inactivity.

**Why is a stateless JWT a problem for freezing an account?** A frozen user's token stays valid until it expires.
I handle it two ways: tokens only live 15 minutes, and every money-moving action re-checks the account status in
the database. A production system would add a token denylist or session versioning.

**Is your geolocation real?** No, it's simulated with an `X-Country` header. In production you'd use IP geolocation
plus device fingerprinting. Say this upfront; honesty scores points.

---

## 5. The PayFlow story: what to say in the interview

**One-line pitch:** "PayFlow is a wallet where every risk decision is explained, proportionate and appealable.
It stops 92.5% of simulated fraud, and it treats honest users fairly when they get flagged."

**Why I built it this way:** Payment platforms face a real trade-off. Fraud controls protect everyone, but the
same controls frustrate honest users when they're opaque: long account limitations with little explanation,
blanket holds on new sellers, FX markups hidden in the rate, and disputes nobody can follow. I kept strong
controls, and changed *how they treat the user*:
- **Explain** every decision in plain language
- **Hold proportionately**: only part of the money, sized by trust
- **Let people appeal** to a human who must give a reason
- **Make pricing visible**

Say it this way in the interview: *"These are improvements I'd explore"*. Don't criticise PayPal. Show that you
understand why holds and limitations exist (chargebacks, fraud losses, regulation).

### Questions on the new features

**How is the Trust Score calculated, and why show it to users?** It's a sum of visible factors: PIN set,
2-step on, card linked, account age, payments received and escrow work delivered, minus penalties for open
fraud alerts and blocked attempts. Showing it turns a frustrating "why is my money held?" into a to-do list
("set your PIN, +15"). That reduces support tickets and nudges users towards *more* secure behaviour.
Trade-off: a visible score is easier to game, so real systems combine it with hidden signals (device, network,
behaviour). The visible part is about *fairness and explanation*, not the whole risk model.

**How does progressive release work technically?** The wallet has two balances: `balance` (spendable) and
`heldBalance`. When a large payment arrives for a non-Trusted user, the transfer transaction credits part of
it to `balance` and part to `heldBalance`, and creates a `Hold` row with the reason and `releaseAt`, all in
the same transaction. A scheduler releases due holds. Each release locks the wallet row, and `Hold` has
`@Version` (optimistic locking), so the scheduler and an appeal approval can't both release the same hold.

**What happens when an appeal on a blocked payment is approved?** `releaseBlockedTransfer` locks both wallets
(in id order, like any transfer), re-checks the sender's balance, moves the money, flips the transfer to
COMPLETED with a review note, and resolves its fraud alerts, all in one transaction. If the sender no longer
has the funds, nothing changes and the admin sees a clear error.

**Why can frozen users still sign in?** Locking someone out of an account *and* refusing to say why is the most
frustrating experience possible. Frozen users get read-only access: they see the reason and can appeal. Every
money-moving path checks the status, so a freeze still stops all money movement.

**How does escrow stay consistent?** The milestone is a state machine (PENDING_FUNDING → FUNDED → SUBMITTED →
RELEASED / DISPUTED → RELEASED or REFUNDED). Every transition takes a `SELECT … FOR UPDATE` lock on the
milestone row and checks the expected state, so "approve" and "auto-release" can't both pay out. Funding moves
money out of the client's wallet into no one's wallet; the platform holds it. That's why there's an
"in escrow" total in the admin console.

**Why does funding a milestone need the UPI PIN but approving doesn't?** Funding *moves your money out*, so it
needs strong authorisation. Approving releases money that's already committed to the person you hired; the
risk is low, and requiring a PIN there would add friction without adding safety.

**How is the FX quote honest?** It converts at the mid-market rate and shows one fee (0.5%). The rate is
*locked* on the invoice for 10 minutes when the payer opens it, so the payer and the freelancer see exactly
what will happen. If the lock expires, payment is refused until the page is refreshed. The "typical provider"
comparison uses commonly published rates (3.5% markup + 4.4% fee) and is labelled as illustrative. Be upfront
that rates in the project are **simulated**; in production you'd call a live rate API.

**What would you do next?**
- **Risk:** per-user behavioural baselines or an ML risk score, device fingerprinting.
- **Infrastructure:** Redis for rate limits and velocity counters, Flyway migrations, and moving rule
  evaluation to an event stream (Kafka) for the non-blocking checks.
- **Escrow:** file evidence uploads.
