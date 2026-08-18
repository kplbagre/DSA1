# Payment System — JPMC Round 3 (LLD + HLD)

> **JPMC context:** Round 3, done on HackerRank Code Pair. This is JPMC's home turf —
> they will push HARD on **idempotency, consistency, and money-never-lost** guarantees.
> The reported arc is the full sweep: *requirements → DB schema → estimations → HLD →
> optimizations → idempotency*. Multiple SuperDay reports.
>
> **Why this problem is different from the others:** Parking Lot and Movie Ticket are
> about a *hot resource race* (one seat, one spot). Payment is about **money correctness** —
> a double-charge or a lost credit is a career-ending bug at a bank. Every design decision
> here is filtered through one question: *"can this cause money to be created, destroyed,
> or moved twice?"* If yes, it's wrong.

---

## Index

| § | Section |
|---|---------|
| §1 | 🎯 Problem Statement |
| §2 | ❓ Clarifying Questions |
| §3a | 🏗️ LLD — Construction Guide (7 Moves) |
| §3b | 🏗️ LLD — Complete Class Diagram |
| §4 | 🧭 Design Decisions |
| §5 | 🔌 Key Interfaces |
| §6 | ⚙️ Code — Three Methods |
| §7 | 🔁 Concurrency |
| §8 | 🧨 Java Depth Probes |
| §9 | 🌐 HLD — 3-Phase Construction Guide |
| §10 | 🏛️ HLD Decisions |
| §11 | 📡 API Design |
| §12 | 🛤️ Happy + Unhappy Paths |
| §13 | ⚠️ Fault Tolerance |
| §14 | 📐 Q&A — Tier-2 JPMC Probes |
| §15 | 🧾 TL;DR |
| §16 | 🔄 Changelog |

---

## §1 — 🎯 Problem Statement

Design a payment system that lets a payer send money to a payee (or a merchant), where:

- A **payment request** moves money from a source (card / wallet / bank account) to a destination
- An **external payment gateway** (Stripe / a card network) actually moves the funds — we orchestrate, we don't hold the money-movement rails
- Every money movement is recorded in a **double-entry ledger** (a bookkeeping method where every transaction writes two rows — one debit, one credit — that must sum to zero; this is how banks guarantee money is never created or destroyed)
- The system is **idempotent** — the same payment request retried (network glitch, user double-click) must never charge twice
- The system is **consistent** — an interrupted payment must resolve to exactly one of *fully done* or *fully not done*, never a half-state

**The one-line framing to say out loud in the interview:**
> *"A payment system is a state machine over money, guarded by idempotency on the way in
> and a double-entry ledger on the way out. My whole design protects those two invariants."*

---

## §2 — ❓ Clarifying Questions

**Scope / MVP**

1. Is this payer-to-merchant (like a checkout) or peer-to-peer (like Venmo/UPI)?
   *(P2P needs both sides to be internal accounts; checkout has an external merchant payout)*
2. Payment instruments — cards only, or also wallet balance and bank transfer (ACH/UPI)?
3. Do we own the money-movement rails, or do we orchestrate an external gateway (Stripe)?
   *(this decides whether we build a ledger + gateway integration, or a full banking core)*

**Actors**

4. Who are the actors — payer, payee/merchant, our system, external gateway, reconciliation/finance?

**Scale**

5. Peak transactions per second (TPS)? Average payment amount? Read:write ratio on
   payment-status lookups?

**Consistency / Correctness (JPMC will camp here)**

6. Is any double-charge ever acceptable? *(Answer is always no — this frames idempotency as mandatory.)*
7. Is eventual consistency acceptable for the *payee's* view of the balance, or must both
   sides see the money move atomically?
8. Do we need strict ordering of a single account's transactions?

**External Dependencies**

9. What's the gateway's timeout SLA? Does it support idempotency keys itself?
   *(Stripe does — we piggyback on it)*
10. Does the gateway send async webhooks for final settlement, or is the sync response final?

**Edge Cases**

11. What happens on a partial refund? Chargeback? Reversal?
12. What if the gateway charges the card but our DB write fails right after? (the classic
    "money moved but we don't know it" case)

**Non-Functional**

13. Latency budget for the payment API? Regulatory/audit requirements (immutable ledger, retention)?

---

## §3a — 🏗️ LLD — Construction Guide (7 Moves)

> Rebuild this on a whiteboard in ~10 min. Stop at move 7 (~75% visible).
> The star of this LLD is the **state machine + the double-entry ledger** — spend your
> words there, not on getters/setters.

---

### Move 1 — List Every Domain Noun

Before the board, say: *"Let me separate the nouns the problem gives me directly from the ones that financial constraints force me to invent."*

**From the statement directly:** Payment, Account, User, PaymentGateway (external), Refund, Webhook

**Derived from constraints:**
- *"financial regulation requires a permanent, immutable audit trail of every movement of money"* → **LedgerEntry** (append-only rows — never updated or deleted; `Account.balance` is a cache/projection of these, but the LedgerEntry rows are the source of truth)
- *"every payment moves money from one account to another — it never creates or destroys money"* → **double-entry constraint**: two `LedgerEntry` rows per `Payment` (one DEBIT, one CREDIT), always equal in amount and always written in the same transaction
- *"network retries on a payment API must never double-charge"* → **IdempotencyRecord** (client-provided key → stored result; a second call with the same key returns the first result without re-processing the payment)
- *"mixing USD and INR amounts silently is a financial bug"* → **Money** value object (amount + currency together, immutable; prevents cross-currency arithmetic at the type level — not a standalone entity, but it must be its own class)

*Filter rule:* keep nouns that carry state or invariants.
`Money` → a value object (amount + currency), not an entity with identity. Keep it — it
prevents the classic "adding USD to INR" bug.
`Transaction` and `Payment` sound like duplicates — clarify: `Payment` is the user-facing
request; `LedgerEntry` rows are the accounting truth. Drop the vague "Transaction" noun.

**Your board at the end of Move 1:**

```
From statement:  Payment · Account · User · PaymentGateway (external) · Refund · Webhook
Derived:         LedgerEntry (append-only audit row — the source of truth),
                 IdempotencyRecord (key → stored result; blocks double-charge on retry),
                 Money (value object: amount + currency; prevents cross-currency bugs)
```

---

### Move 2 — Classify: Enums → Value Objects → Entities → Interfaces → Services

```
Board after Move 2:

  ENUMS:         PaymentStatus   EntryType (DEBIT / CREDIT)   Currency
  VALUE OBJECTS: Money (amount + currency — immutable)
  ENTITIES:      Payment   Account   LedgerEntry   IdempotencyRecord
  INTERFACES:    PaymentGateway
  SERVICES:      PaymentService   LedgerService
```

*Say aloud:* the `Money` value object is deliberate — it makes currency mismatches a
compile-time / constructor-time error, not a silent arithmetic bug.

---

### Move 3 — Draw the Enums + the Money Value Object

```
Board after Move 3:

  ┌────────────────────────┐  ┌──────────────────┐  ┌───────────────────┐
  │  PaymentStatus         │  │  EntryType       │  │  Money  (VO)      │
  │  ────────────────────  │  │  ─────────────── │  │  ───────────────  │
  │  INITIATED             │  │  DEBIT           │  │  amount: BigDecimal│
  │  PENDING   (at gateway)│  │  CREDIT          │  │  currency: Currency│
  │  SUCCESS               │  └──────────────────┘  │  (immutable)      │
  │  FAILED                │                        │  + add(Money)     │
  │  REVERSED (refunded)   │                        │  + subtract(Money)│
  └────────────────────────┘                        └───────────────────┘
```

> **Why `BigDecimal`, never `double`, for money.** `0.1 + 0.2 != 0.3` in floating point.
> `double` loses cents at scale. `BigDecimal` is exact decimal arithmetic — the only
> acceptable money type in Java. Saying this unprompted is a strong SDE-3 signal at a bank.

---

### Move 4 — Draw Account and LedgerEntry (the accounting core)

```
Board after Move 4:

  ┌──────────────────────────┐   ┌────────────────────────────────────────┐
  │  Account                 │   │  LedgerEntry   (immutable, append-only) │
  │  ──────────────────────  │   │  ──────────────────────────────────────  │
  │  accountId: String       │   │  entryId: String                        │
  │  balance: Money  ← HOT   │   │  paymentId: String   // groups the pair │
  │  version: long  //@Version│   │  accountId: String                      │
  └──────────────────────────┘   │  type: EntryType   // DEBIT or CREDIT   │
                                 │  amount: Money                          │
                                 │  createdAt: Instant                     │
                                 └──────────────────────────────────────────┘
```

*Say aloud:* `LedgerEntry` is **append-only and immutable** — you never UPDATE or DELETE
a ledger row. A correction is a new compensating entry. This is how auditors trust the
system; the ledger is the source of truth, `Account.balance` is a cached projection of it.

---

### Move 5 — Name the Two Hot Invariants (this is the whole problem)

```
Board after Move 5 (annotations added):

  INVARIANT 1 — DOUBLE-ENTRY BALANCE
    Every Payment writes EXACTLY TWO LedgerEntry rows:
        DEBIT  payer_account   $100
        CREDIT payee_account   $100
    The two amounts MUST be equal and opposite. Sum of all entries = 0, always.
    → Money is never created or destroyed, only moved.

  INVARIANT 2 — IDEMPOTENCY
    The same client idempotency-key hitting /payments twice
    produces ONE Payment and ONE pair of ledger entries.
    The second call returns the FIRST call's result — no new side effect.
    → A retry (network glitch, double-click) never double-charges.

  HOT RESOURCE: Account.balance — protected by @Version optimistic lock.
```

*This is the SDE-3 signal.* Stating the two invariants before writing any method tells
the interviewer you understand that a payment system is *defined* by these two guarantees.

---

### Move 6 — Draw Payment and IdempotencyRecord

```
Board after Move 6:

  ┌──────────────────────────────────────────┐  ┌─────────────────────────────┐
  │  Payment                                 │  │  IdempotencyRecord          │
  │  ──────────────────────────────────────  │  │  ─────────────────────────  │
  │  paymentId: String                       │  │  idempotencyKey: String «PK»│
  │  payerAccountId: String                  │  │  paymentId: String          │
  │  payeeAccountId: String                  │  │  responseBody: String       │
  │  amount: Money                           │  │  createdAt: Instant         │
  │  status: PaymentStatus                   │  │  (TTL 24h)                  │
  │  idempotencyKey: String                  │  └─────────────────────────────┘
  │  gatewayRef: String   // Stripe charge id│
  │  + transition(newStatus): void           │
  └──────────────────────────────────────────┘
```

The `idempotencyKey` is a **unique index** in the DB — the database itself becomes the
enforcer. Two concurrent inserts with the same key → the second gets a unique-constraint
violation → we catch it and return the first payment's result.

---

### Move 7 — Add PaymentGateway Interface + Services (~75% — stop here)

```
Board after Move 7:

  «interface»
  PaymentGateway   «external — Stripe / card network»
  ─────────────────────────────────────────────────────
  + charge(request: ChargeRequest, idemKey: String): ChargeResult
  + refund(gatewayRef: String, amount: Money): RefundResult

  LedgerService
  ─────────────────────────────────────────────────────
  + recordTransfer(payment: Payment): void   // writes the DEBIT+CREDIT pair atomically

  PaymentService
  ─────────────────────────────────────────────────────
  gateway: PaymentGateway
  ledgerService: LedgerService
  + pay(request: PaymentRequest, idemKey: String): Payment
  – checkIdempotency(idemKey): Optional<Payment>
```

*Explain the two seams:*
- `PaymentGateway` is an interface → the actual money rail (Stripe today, a card network
  tomorrow) is swappable and mockable. We orchestrate; we don't hold funds.
- `LedgerService.recordTransfer()` writes the DEBIT + CREDIT **in one DB transaction** —
  it is impossible to persist half of a transfer.

---

## §3b — 🏗️ LLD — Complete Class Diagram

```
  ┌────────────────────────┐  ┌──────────────────┐  ┌───────────────────┐
  │  PaymentStatus         │  │  EntryType       │  │  Money  (VO)      │
  │  ────────────────────  │  │  ─────────────── │  │  ───────────────  │
  │  INITIATED             │  │  DEBIT           │  │  amount:BigDecimal│
  │  PENDING               │  │  CREDIT          │  │  currency:Currency│
  │  SUCCESS               │  └──────────────────┘  │  + add / subtract │
  │  FAILED                │                        └───────────────────┘
  │  REVERSED              │
  └───────┬────────────────┘
          │ status
          ▼
  ┌──────────────────────────────────────────┐
  │  Payment                                 │
  │  ──────────────────────────────────────  │
  │  paymentId: String                       │      writes 2 rows
  │  payerAccountId: String                  │──────────────────────┐
  │  payeeAccountId: String                  │                      │
  │  amount: Money                           │                      ▼
  │  status: PaymentStatus                   │   ┌────────────────────────────────────┐
  │  idempotencyKey: String «unique index»   │   │  LedgerEntry (immutable, append) │
  │  gatewayRef: String                      │   │  ────────────────────────────────  │
  │  + transition(newStatus): void           │   │  entryId: String                   │
  └──────────────────────────────────────────┘   │  paymentId: String                 │
                                                  │  accountId: String                 │
  ┌─────────────────────────────┐                 │  type: EntryType (DEBIT|CREDIT)    │
  │  IdempotencyRecord          │                 │  amount: Money                     │
  │  ─────────────────────────  │                 │  createdAt: Instant                │
  │  idempotencyKey: String «PK»│                 └──────────────┬─────────────────────┘
  │  paymentId: String          │                     0..* entries│ project into
  │  responseBody: String       │                 ┌───────────────▼──────────────────┐
  │  createdAt: Instant (TTL24h)│                 │  Account                          │
  └─────────────────────────────┘                 │  ────────────────────────────────  │
                                                  │  accountId: String                 │
                                                  │  balance: Money  ← HOT             │
                                                  │  version: long   // @Version       │
                                                  └────────────────────────────────────┘

  «interface»
  PaymentGateway   «external — Stripe»
  ──────────────────────────────────────────────
  + charge(req: ChargeRequest, idemKey: String): ChargeResult
  + refund(gatewayRef: String, amount: Money): RefundResult

  LedgerService                              PaymentService
  ────────────────────────────────          ─────────────────────────────────────
  + recordTransfer(payment): void            gateway: PaymentGateway
       (DEBIT + CREDIT in one tx)            ledgerService: LedgerService
                                             + pay(req, idemKey): Payment
                                             – checkIdempotency(idemKey): Optional<Payment>
```

---

## §4 — 🧭 Design Decisions

| Decision | Why | Rejected alternative |
|---|---|---|
| **Idempotency key as a DB unique index** | The database is the single enforcer — even with concurrent duplicate requests across pods, only one row survives; the loser catches the constraint violation and returns the winner's result. No distributed coordination needed. | In-memory dedup cache — lost on pod restart; not shared across pods; a duplicate slips through during a deploy |
| **Double-entry ledger (DEBIT + CREDIT pair)** | Money is provably conserved — sum of all entries is always zero. Auditable, reversible (a refund is a new opposite pair), industry-standard at banks. | Single "balance update" per account — no audit trail; a bug can create or destroy money with no way to detect it |
| **Immutable, append-only `LedgerEntry`** | You can always replay history to reconstruct any balance. Corrections are compensating entries, never edits — regulators require this. | Mutable balance-only rows — an UPDATE overwrites history; impossible to audit "what did the balance look like on March 3rd" |
| **`Account.balance` is a cached projection, not the truth** | Fast reads without summing the whole ledger. Guarded by `@Version` optimistic lock so concurrent debits can't race. Ledger remains the source of truth for reconciliation. | Balance as source of truth — you lose the "recompute from history" safety net that catches drift |
| **`@Version` optimistic lock on Account** | Concurrent debits on the same account are rare (one user isn't usually paying twice at once); optimistic + retry is cheaper than holding a row lock. | Pessimistic `SELECT FOR UPDATE` — holds a DB connection/lock for the whole payment including the slow gateway call; kills throughput |
| **`Money` as an immutable value object with `BigDecimal`** | Exact decimal math; currency mismatch caught at the boundary; no accidental floating-point cent loss. | `double amount` — floating-point rounding silently loses money at scale; the #1 rookie bank bug |

---

## §5 — 🔌 Key Interfaces

```java
public interface PaymentGateway {

    ChargeResult charge(ChargeRequest request, String idempotencyKey);

    RefundResult refund(String gatewayRef, Money amount);
}
```

```java
// Immutable value object — the ONLY money type in the system.
public final class Money {

    private final BigDecimal amount;
    private final Currency currency;

    public Money(BigDecimal amount, Currency currency) {
        this.amount = Objects.requireNonNull(amount);
        this.currency = Objects.requireNonNull(currency);
    }

    public Money add(Money other) {
        // guard: never mix currencies in one arithmetic operation
        requireSameCurrency(other);
        return new Money(this.amount.add(other.amount), this.currency);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        return new Money(this.amount.subtract(other.amount), this.currency);
    }

    private void requireSameCurrency(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new CurrencyMismatchException(this.currency, other.currency);
        }
    }
}
```

---

## §6 — ⚙️ Code — Three Methods

### Method 1 — `PaymentService.pay()` — the idempotent orchestrator

**Steps in plain English:**

1. **Idempotency check first** — if this key was seen before, return the stored result and stop. No new work, no new charge.
2. **Reserve the key** — insert an `IdempotencyRecord` + a `Payment` in state INITIATED, in one DB transaction. The unique index on the key makes concurrent duplicates fail here safely.
3. **Call the external gateway** to actually move funds, passing the SAME idempotency key so the gateway also dedups.
4. **On gateway success** — record the double-entry ledger transfer and mark the payment SUCCESS, in one DB transaction.
5. **On gateway failure** — mark the payment FAILED. No ledger entries written. Money never moved.

```java
public class PaymentService {

    private final PaymentGateway gateway;
    private final LedgerService ledgerService;

    public Payment pay(PaymentRequest request, String idempotencyKey) {
        // Step 1 — idempotency short-circuit: a retry returns the original result
        Optional<Payment> existing = checkIdempotency(idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        // Step 2 — reserve the key + create INITIATED payment atomically.
        // The DB unique index on idempotencyKey rejects concurrent duplicates here.
        Payment payment = createInitiatedPayment(request, idempotencyKey);

        // Step 3 — move real money; pass the SAME key so the gateway dedups too
        ChargeResult result = gateway.charge(toChargeRequest(request), idempotencyKey);

        if (result.isSuccess()) {
            // Step 4 — write DEBIT+CREDIT and flip to SUCCESS in one transaction
            payment.setGatewayRef(result.getGatewayRef());
            ledgerService.recordTransfer(payment);
            payment.transition(PaymentStatus.SUCCESS);
        } else {
            // Step 5 — no ledger entries; money never moved
            payment.transition(PaymentStatus.FAILED);
        }
        return payment;
    }
}
```

> **The ordering is the whole game.** Idempotency check is step 1 so a retry never reaches
> the gateway. The gateway gets the same key so even if OUR check races, the gateway itself
> refuses to charge twice. That's **defense in depth** — two independent dedup layers.

---

### Method 2 — `LedgerService.recordTransfer()` — the atomic double-entry write

**Steps in plain English:**

1. **Open one DB transaction** — both rows commit together or neither does.
2. **Write the DEBIT** row against the payer for the amount.
3. **Write the CREDIT** row against the payee for the same amount.
4. **Update both cached balances** under their `@Version` optimistic locks.
5. **Commit** — the invariant (debit == credit) can never be persisted half-done.

```java
public class LedgerService {

    // Step 1 — @Transactional: all-or-nothing; no half-transfer can exist
    @Transactional
    public void recordTransfer(Payment payment) {
        Money amount = payment.getAmount();

        // Step 2 — money leaves the payer
        LedgerEntry debit = new LedgerEntry(
            payment.getPaymentId(),
            payment.getPayerAccountId(),
            EntryType.DEBIT,
            amount
        );

        // Step 3 — the SAME amount arrives at the payee (conservation of money)
        LedgerEntry credit = new LedgerEntry(
            payment.getPaymentId(),
            payment.getPayeeAccountId(),
            EntryType.CREDIT,
            amount
        );

        ledgerRepository.saveAll(List.of(debit, credit));

        // Step 4 — update cached projections under optimistic lock (throws on conflict)
        accountRepository.debit(payment.getPayerAccountId(), amount);
        accountRepository.credit(payment.getPayeeAccountId(), amount);
        // Step 5 — commit happens automatically at method exit
    }
}
```

> **Why write the ledger AND update the balance?** The ledger is the immutable truth;
> `Account.balance` is a fast cache so reads don't sum millions of rows. If they ever
> drift, a nightly reconciliation job recomputes balance from the ledger and alerts.

---

### Method 3 — `checkIdempotency()` — the dedup lookup

**Steps in plain English:**

1. **Look up the idempotency record** by key.
2. **If found**, load and return the original payment — the caller gets the exact same result as the first call.
3. **If not found**, return empty so `pay()` proceeds to do the real work.

```java
private Optional<Payment> checkIdempotency(String idempotencyKey) {
    // Step 1 — look up prior record by the client-supplied key
    Optional<IdempotencyRecord> record =
        idempotencyRepository.findByKey(idempotencyKey);

    // Step 2 — replay the original outcome; no new side effect
    if (record.isPresent()) {
        return paymentRepository.findById(record.get().getPaymentId());
    }

    // Step 3 — first time we've seen this key; caller proceeds
    return Optional.empty();
}
```

> **What if the first call is still in-flight (PENDING) when the retry arrives?** Return the
> in-flight PENDING payment as-is. The client polls status; it does NOT trigger a second
> charge. This is why `Payment` carries its own status — a retry observes progress, never restarts it.

---

## §7 — 🔁 Concurrency

### Race 1 — duplicate request (the idempotency race)

```
Client double-clicks / network retries → two identical requests, same idemKey

Pod A                              Pod B
─────────────────────────          ─────────────────────────
checkIdempotency(K) → empty        checkIdempotency(K) → empty   ← BOTH see empty!
INSERT IdempotencyRecord(K) ─────┐ INSERT IdempotencyRecord(K)
   unique index → OK             │    unique index → CONSTRAINT VIOLATION
   proceed to charge             │    catch → re-read → return Pod A's payment
                                 │
        The DB unique index is the referee. Exactly one INSERT wins.
        The loser does NOT charge — it returns the winner's result.
```

**Fix:** the DB `UNIQUE` constraint on `idempotency_key` is the arbiter. No app-level lock,
no distributed lock — the database serializes the two inserts for free. Loser catches
`DataIntegrityViolationException`, re-reads, returns the existing payment.

### Race 2 — concurrent debits on the same account (the balance race)

```
Two payments debit account A ($100 balance) at the same time, each for $80.

Pod A                              Pod B
────────────────────────           ────────────────────────
read balance=100, version=5        read balance=100, version=5
compute 100-80=20                  compute 100-80=20
UPDATE ... WHERE version=5 ──────┐  UPDATE ... WHERE version=5
   1 row updated, version→6      │     0 rows updated (version already 6!)
   commit                        │     → OptimisticLockException → retry
                                 │        re-read balance=20 → 20-80 = -20
                                 │        → reject: INSUFFICIENT_FUNDS
        Without @Version: both would write balance=20 → $160 spent from $100. Money created.
```

**Fix:** `@Version` optimistic lock. The second UPDATE matches zero rows (version moved),
JPA throws `OptimisticLockException`, the service re-reads and re-evaluates — now correctly
seeing only $20 left and rejecting the second $80 debit. **This prevents overdraft / money
creation.**

### Why optimistic over pessimistic here

A payment holds a slow external gateway call (~seconds). A pessimistic `SELECT FOR UPDATE`
would hold the account row locked for that entire duration — every other payment on that
account blocks, and a connection is pinned. Optimistic locking holds nothing during the
gateway call; it only detects a conflict at the final balance write, which is rare per-account.

### Cross-cutting: exactly-once at the gateway

Even with our idempotency layer, we pass the same `idempotencyKey` to the gateway's
`charge()`. Stripe (and card networks) dedup on it. So if OUR record write and the gateway
call straddle a crash, replaying the call is safe — the gateway returns the original charge,
not a new one. **Two independent dedup layers = money charged exactly once.**

---

## §8 — 🧨 Java Depth Probes

| Question | Answer |
|---|---|
| "Why `BigDecimal` not `double` for money?" | Floating point can't represent 0.1 exactly — `0.1 + 0.2 == 0.30000000000000004`. At millions of transactions, cents vanish. `BigDecimal` is exact base-10 arithmetic. Non-negotiable for money. |
| "Optimistic vs pessimistic locking on the balance — which and why?" | Optimistic (`@Version`). A payment holds a multi-second gateway call; pessimistic `SELECT FOR UPDATE` would lock the row for that whole time and pin a DB connection. Same-account concurrency is rare, so a version-conflict retry is cheaper than serializing every payment. |
| "How does the DB unique index give you idempotency for free?" | Concurrent duplicate inserts with the same key: the DB serializes them and rejects all but one with a unique-constraint violation. The loser catches it and returns the winner's result. No app-level or distributed lock needed. |
| "What if the gateway charges the card but your DB write fails right after?" | The gateway holds the same idempotency key. On recovery we replay `charge()` with that key — the gateway returns the *existing* charge (not a new one), and we then complete the ledger write. Combined with a reconciliation job comparing our ledger to the gateway's settlement report, no charge is lost or duplicated. |
| "Why append-only ledger instead of updating a balance column?" | Auditability and recoverability. The ledger is immutable truth; balance is a cached projection. You can always replay entries to recompute any balance at any point in time — regulators require this; a mutable balance overwrites history. |
| "Would you use `synchronized` here?" | No — `synchronized` is single-JVM only. Payments run across many pods. Correctness must live in the shared DB: unique index (idempotency) + `@Version` (balance). App-level locks would give false confidence and still allow cross-pod double-charges. |
| "SERIALIZABLE isolation — would that solve it?" | It would prevent the balance race, but at a huge throughput cost (heavy locking / abort-retry storms). We get the same correctness cheaper with a targeted `@Version` check on just the account row. Use the narrowest tool that guarantees the invariant. |

---

## §9 — 🌐 HLD — How to Build This Diagram in the Interview — 3 Phases

### Phase 1 — Numbers First (≈2 min)

```
Scale assumption: mid-size payment processor

  Payments/day    50M payments/day
  Avg TPS         50M / 86,400s ≈ 580 writes/sec
  Peak TPS        3× average (sale events, salary day) ≈ 1,740 writes/sec
  Status reads    each payment polled ~5× (payer app, merchant, webhook, retries)
                  50M × 5 / 86,400 ≈ 2,900 reads/sec  (peak ~8,700/sec)
  Ledger rows     2 entries per payment × 50M = 100M rows/day
  Storage         100M rows × ~200 bytes = 20 GB/day → 7.3 TB/year → SHARDING NEEDED
  Idempotency     50M keys/day, 24h TTL → ~50M keys hot at a time → Redis fits (~5 GB)

HOT PATH:      status reads (~8,700/sec peak) — read-heavy, cacheable
CRITICAL PATH: the write (charge + ledger) — must be exactly-once and ACID

Two forces on the architecture:
  (1) Read volume (8,700/sec)  → Redis cache for payment status
  (2) Ledger growth (7.3 TB/yr) → shard ledger by account_id; archive cold rows
```

---

### Phase 2 — Skeleton: Simplest System That Could Work (≈3 min)

```
── Skeleton: Simplest System That Could Work ──────────────────────

   ┌────────────────────────────────────────────────┐
   │  Client   Payer App · Merchant Backend         │
   └─────────────────────┬──────────────────────────┘
                         │ HTTPS  (X-Idempotency-Key)
   ┌─────────────────────▼──────────────────────────┐
   │  API Gateway  (auth · routing)                 │
   └──────┬──────────────────────────────┬──────────┘
          │                              │
   ┌──────▼────────────────┐   ┌─────────▼──────────────────────────┐
   │  PaymentQueryService  │   │  PaymentService                    │
   │  GET /payments/{id}   │   │       └──▶ PaymentGateway ──▶ Stripe│
   │                       │   │  LedgerService (DEBIT + CREDIT)    │
   └──────┬────────────────┘   │  NotificationService ──▶ Email     │
          │                    └─────────────────────┬──────────────┘
          │                                          │
   ┌──────▼──────────────────────────────────────────▼────────────┐
   │  MySQL  (payments · ledger_entries · accounts · idem_keys)   │
   │  ACID — the unique index on idem_key lives here              │
   └───────────────────────────────────────────────────────────────┘

BREAKING POINT — walk this skeleton against the Phase 1 numbers:
  (a) PaymentQueryService → MySQL at ~8,700 status reads/sec: same payment
      rows read over and over; read load competes with ACID payment writes
      for the connection pool.
  (b) idem_keys table grows by 50M rows/day forever; the unique-index lookup
      on every payment slows as the table bloats — and 24h-old keys are dead weight.
  (c) ledger_entries grows 7.3 TB/year in one MySQL instance — a single node
      can't hold it; queries and backups degrade.
  (d) NotificationService (email) is synchronous on the payment path — a slow
      email provider makes the payment API slow, hurting the money-critical write.

══════════════════════════════════════════════════════════════════
```

---

### Phase 3 — Upgrade It: One Fix per Pain Point (≈5 min)

*"This works in dev. Now let me address each breaking point."*

**BREAKING POINT (a) → Redis cache for payment status (read path)**

Cache `payment:{id}:status` in Redis with a short TTL. Status reads (~8,700/sec)
hit Redis first (cache-aside); only a miss falls through to MySQL. This isolates
the heavy read traffic from the money-critical write path — they no longer fight
for the same connection pool.

**BREAKING POINT (b) → Move idempotency keys to Redis with native TTL**

Store the idempotency key → payment mapping in Redis with `SET key {paymentId} NX EX 86400`.
`NX` gives the same "exactly one winner" guarantee as the DB unique index, and `EX 86400`
auto-expires the key after 24h — no bloat, no cleanup job. (Keep a DB unique index too as
the durable backstop; Redis is the fast first check.)

**BREAKING POINT (c) → Shard the ledger by account_id + archive cold rows**

Partition `ledger_entries` by `account_id` (hash shard) so no single node holds 7.3 TB.
An account's full history stays co-located on one shard (fast per-account statements).
Move rows older than N months to cold storage (S3 / an archive DB); reconciliation still
reads them, but they leave the hot path.

**BREAKING POINT (d) → Kafka for async notifications + reconciliation**

After the payment ACID commit, emit `payment.completed` to Kafka. NotificationService
(email/SMS receipt) and ReconciliationService (compare our ledger vs the gateway's
settlement report) consume asynchronously. The payment API returns as soon as money moved
and the ledger committed — it never waits on email. At-least-once delivery; consumers
dedup on `paymentId`.

---

```
── Production: All 4 Upgrades Applied ────────────────────────────

   ┌────────────────────────────────────────────────┐
   │  Client   Payer App · Merchant Backend         │
   └─────────────────────┬──────────────────────────┘
                         │ HTTPS  (X-Idempotency-Key)
   ┌─────────────────────▼────────────────────────────────────────┐
   │  API Gateway  (JWT · rate-limit · TLS · routing)            │
   └──────┬────────────────────────────────────────┬─────────────┘
          │                                        │
   ┌──────▼────────────────┐   ┌────────────────────▼────────────────────┐
   │  PaymentQueryService  │   │  PaymentService                         │
   │  GET /payments/{id}   │   │  1. check idem  (Redis SET NX EX)      │
   │  (read-heavy)         │   │  2. charge ──▶ Stripe (same idemKey)   │
   │                       │   │  3. LedgerService: DEBIT+CREDIT (ACID)  │
   │                       │   │     @Version balance update             │
   └──────┬────────────────┘   └────────────────────┬────────────────────┘
          │ GET payment:{id}:status                  │ SET idem:{key} NX EX 86400
          │ (cache-aside)                            │ + ACID write to shard
          ▼                                          ▼
   ┌──────────────────────────────────────────────────────────────────────┐
   │  Redis                                                               │
   │  payment:{id}:status → status enum  · EX 300   ← PaymentQuerySvc   │
   │  idem:{key}          → paymentId    · EX 86400 ← PaymentSvc        │
   └──────────────────────────┬───────────────────────────────────────────┘
                              │ cache miss / ACID write
   ┌──────────────────────────▼───────────────────────────────────────────┐
   │  MySQL  (ACID · sharded by account_id)                              │
   │  payments · idem_keys (durable backstop)      ← PaymentSvc         │
   │  ledger_entries (immutable, append-only)      ← LedgerSvc          │
   │  accounts (balance projection · @Version)     ← LedgerSvc          │
   │      cold rows (>N months) ──▶ S3 archive                          │
   └──────────────────────────┬───────────────────────────────────────────┘
                              │ emit payment.completed
   ┌──────────────────────────▼───────────────────────────────────────────┐
   │  Kafka  (topic: payment-events, key = paymentId)                    │
   │  ├──▶ NotificationService    email / SMS receipt · retry via DLQ   │
   │  └──▶ ReconciliationService  our ledger vs gateway settlement report│
   └──────────────────────────────────────────────────────────────────────┘

KEY INVARIANT: Money moves exactly once — two dedup layers (Redis SET NX +
  our idem key passed to the gateway) block double-charge; the DEBIT+CREDIT
  ledger pair commits in one ACID transaction so a transfer is never half-done;
  @Version on the balance prevents overdraft under concurrent debits; the
  ledger is immutable truth that ReconciliationService checks against the
  gateway's own settlement report to catch any drift.
══════════════════════════════════════════════════════════════════
```

---

## §10 — 🏛️ HLD Decisions

| Component | Why chosen | Rejected + why |
|---|---|---|
| **Redis for idempotency (`SET NX EX`)** | Atomic "exactly one winner" + native 24h auto-expiry — no bloat, no cleanup job. Fast first-check before the DB. | Only a DB table — grows 50M rows/day; needs a purge job; lookup slows as it bloats. (We keep the DB unique index as a durable backstop, not the fast path.) |
| **Redis cache for payment status** | ~8,700 reads/sec of the same rows — cache-aside isolates read traffic from the money-critical write path so they don't fight for the connection pool. | Read replicas only — still a full DB round-trip per read; replica lag can show a stale-but-DB-backed status; Redis is faster and cheaper for hot keys |
| **MySQL (ACID) for payments + ledger** | Money requires ACID transactions and a unique index; the DEBIT+CREDIT pair must commit atomically. Strong consistency is mandatory. | NoSQL / eventual consistency — unacceptable for money; you cannot have a payment that is "eventually" not double-charged |
| **Shard ledger by `account_id`** | 7.3 TB/year can't live on one node; hash-sharding keeps each account's history co-located for fast statements and spreads write load. | Single instance — runs out of disk and IOPS; backups and per-account queries degrade |
| **Kafka for notifications + reconciliation** | Email/SMS and settlement-reconciliation are off the critical path; async fan-out means the payment API returns the instant money moved. | Synchronous calls — a slow email provider slows the money write; reconciliation batch would block payments |
| **Double-entry ledger** | Provable conservation of money; auditable; reversible. The reconciliation consumer compares it to the gateway's settlement report to catch drift. | Balance-only updates — no audit trail; a bug silently creates/destroys money with no detection |

---

## §11 — 📡 API Design

### POST /v1/payments — create a payment (write; the endpoint JPMC will grill)

```
POST /v1/payments
X-Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000   ← MANDATORY
Authorization: Bearer {jwt}
Content-Type: application/json

{
  "payerAccountId": "acct-payer-123",
  "payeeAccountId": "acct-merchant-999",
  "amount": { "value": "150.00", "currency": "USD" },
  "instrument": "CARD"
}

201 Created                          ← first call: money moved, ledger written
{
  "paymentId": "pay-7f3a9b",
  "status": "SUCCESS",
  "amount": { "value": "150.00", "currency": "USD" },
  "createdAt": "2026-08-17T09:15:00Z"
}

200 OK                               ← retry with SAME key: original result replayed
{
  "paymentId": "pay-7f3a9b",
  "status": "SUCCESS",   // identical body; NO second charge
  ...
}

402 Payment Required                 ← gateway declined / insufficient funds
409 Conflict                         ← same key, DIFFERENT body (client bug — key reuse)
```

> **The two behaviors to say out loud:** (1) same key + same body → replay the first result,
> no new charge. (2) same key + *different* body → `409`, because reusing a key for a
> different payment is a client error we must refuse, not silently accept.

---

### GET /v1/payments/{paymentId} — poll status (read; hits Redis first)

```
GET /v1/payments/pay-7f3a9b
Authorization: Bearer {jwt}

200 OK
{
  "paymentId": "pay-7f3a9b",
  "status": "PENDING",     // INITIATED | PENDING | SUCCESS | FAILED | REVERSED
  "amount": { "value": "150.00", "currency": "USD" }
}
```

---

### POST /v1/payments/{paymentId}/refund — reverse a payment (write)

```
POST /v1/payments/pay-7f3a9b/refund
X-Idempotency-Key: {uuid}
Authorization: Bearer {jwt}

{ "amount": { "value": "150.00", "currency": "USD" } }

201 Created
{ "refundId": "rfnd-22c1", "status": "REVERSED" }
```

> A refund is **not** a DELETE — it writes a *new* compensating DEBIT+CREDIT pair (opposite
> direction) and a new ledger record. History is never mutated; the original payment stays.

---

## §12 — 🛤️ Happy + Unhappy Paths

### Happy path — a payment that goes through

```
1. Payer app → POST /payments with X-Idempotency-Key K.

2. PaymentService:
   a. SET idem:K {new-paymentId} NX EX 86400 → OK  (first time; we own this key).
   b. INSERT payment (status INITIATED) in MySQL.

3. gateway.charge(request, K) → Stripe moves the funds → returns gatewayRef, SUCCESS.

4. Single MySQL ACID transaction:
   a. INSERT DEBIT  ledger_entry  (payer  -$150).
   b. INSERT CREDIT ledger_entry  (payee  +$150).
   c. UPDATE payer.balance  (WHERE version = N)  → version N+1.
   d. UPDATE payee.balance  (WHERE version = M)  → version M+1.
   e. UPDATE payment.status = SUCCESS.
   COMMIT.

5. Write payment:{id}:status = SUCCESS to Redis (EX 300).

6. Emit payment.completed to Kafka →
      NotificationService → email receipt (~seconds).
      ReconciliationService → will match this against the nightly gateway report.

7. Return 201 with status SUCCESS.
```

---

### Unhappy path 1 — duplicate request (idempotency)

```
Network retry: two POSTs, same key K, same body.

Pod A: SET idem:K {pA} NX EX 86400 → OK   → proceeds, charges once, returns 201.
Pod B: SET idem:K {pB} NX EX 86400 → nil  → key exists → read idem:K → paymentId pA
       → return pA's stored result as 200. NO second charge.
```

---

### Unhappy path 2 — gateway charged the card, then our DB write crashed

```
gateway.charge(request, K) → SUCCESS at Stripe  (card actually charged).
[POD CRASHES before the ledger transaction commits.]

State: money moved at the gateway, but we have no SUCCESS ledger entry.

Recovery:
  a. Payment row is stuck in INITIATED/PENDING. A recovery job (or the client's
     retry) calls gateway.charge(request, K) again with the SAME key K.
  b. Stripe returns the EXISTING charge (idempotent) — it does NOT charge again.
  c. We now write the DEBIT+CREDIT ledger transaction and flip to SUCCESS.
  d. ReconciliationService independently confirms our ledger matches Stripe's
     settlement report. Any unmatched charge is flagged for manual review.

Outcome: charged exactly once; ledger eventually consistent with the gateway.
```

---

### Unhappy path 3 — insufficient funds / concurrent overdraft

```
Balance $100. Two concurrent debits of $80 each.

Payment 1: UPDATE accounts SET balance=20 WHERE id=A AND version=5 → 1 row → commit.
Payment 2: UPDATE accounts SET balance=20 WHERE id=A AND version=5 → 0 rows
           → OptimisticLockException → re-read balance=20
           → 20 - 80 < 0 → reject → payment FAILED, 402, no ledger entries.

Money never went negative. No funds created.
```

---

### Unhappy path 4 — gateway timeout (unknown outcome)

```
gateway.charge(request, K) → no response within 30s (timeout).

We do NOT know if the charge succeeded. So:
  a. Leave payment in PENDING (never guess SUCCESS or FAILED).
  b. A status-poll job later calls gateway.getCharge(K):
       - charge exists & succeeded → complete ledger, flip SUCCESS.
       - charge does not exist      → flip FAILED (safe: no money moved).
  c. Client sees PENDING meanwhile and polls GET /payments/{id}; it never retries
     the charge blindly (the idem key protects it even if it does).
```

---

## §13 — ⚠️ Fault Tolerance

| External call | Timeout | Retry policy | Fallback |
|---|---|---|---|
| **PaymentGateway (Stripe)** | 30s | Retry with the SAME idempotency key (safe — gateway dedups) | Leave payment PENDING; async status-poll job resolves via `getCharge(key)`; never guess the outcome |
| **Redis (idempotency / status)** | 50ms | 1 immediate retry | Fall back to the DB unique index for idempotency (durable backstop); status read falls through to MySQL |
| **MySQL (ledger write)** | 5s | 1 retry with a new connection | Fail-fast; return 503; do NOT mark SUCCESS — money must not be recorded on an uncertain write; circuit breaker after 5 consecutive failures |
| **Kafka (emit event)** | 5s | 3× producer retry; DLQ on failure | Payment is already SUCCESS and committed in MySQL — do NOT roll back; an outbox/relay re-emits the event so notification/reconciliation are never permanently lost |

> **The banking rule for the table above:** on *any* uncertainty about whether money moved,
> the system stays PENDING and lets an idempotent replay + reconciliation resolve it. It
> never optimistically records SUCCESS.

---

## §14 — 📐 Q&A — Tier-2 JPMC Probes

**Q: A client sends the same idempotency key but with a *different* amount. What do you do?**

> Reject with `409 Conflict`. The idempotency record stores a hash of the original request
> body. On a repeat key, I compare the incoming body's hash to the stored one — if they
> differ, the client is misusing the key (reusing it for a different payment), which is a
> bug I must refuse, not silently process. Same key + same body → replay the original result.

**Q: Your `Account.balance` cache and the ledger disagree. How do you detect and fix it?**

> The ledger is the source of truth; balance is a projection. A reconciliation job
> periodically recomputes `SUM(credits) - SUM(debits)` per account from the ledger and
> compares it to the cached balance. On mismatch, it corrects the balance from the ledger
> and alerts — because a mismatch means a bug, not just drift. Because the ledger is
> immutable and append-only, the recomputation is always authoritative.

**Q: How do you guarantee exactly-once when your service AND the gateway can both crash?**

> Two independent dedup layers plus reconciliation. (1) Our idempotency key (Redis `NX` +
> DB unique index) stops us from starting a second charge. (2) We pass that same key to the
> gateway, which dedups on its side — so even if our record and the gateway call straddle a
> crash, replaying returns the existing charge. (3) ReconciliationService compares our ledger
> to the gateway's settlement report nightly, flagging any charge we have that the gateway
> doesn't (or vice versa) for manual review. Belt, suspenders, and an auditor.

**Q: Why not just use SERIALIZABLE isolation and skip the `@Version` and idempotency machinery?**

> SERIALIZABLE would prevent the balance race, but at a severe throughput cost — heavy
> range locks and frequent abort-retries at ~1,740 write TPS. And it does nothing for
> cross-service idempotency (the gateway double-charge) or for auditability. I prefer the
> narrowest tool per invariant: `@Version` for the balance race, a unique index for
> idempotency, a double-entry ledger for auditability. Each is cheap and targeted.

---

## §15 — 🧾 TL;DR

**The one sentence:** *A payment system is a state machine over money, guarded by
**idempotency** on the way in and a **double-entry ledger** on the way out.*

**Entities:** `Payment (state machine) · Account (balance, @Version) · LedgerEntry
(immutable DEBIT/CREDIT pair) · IdempotencyRecord · Money (BigDecimal VO)`

**Two invariants that define the whole design:**
1. **Idempotency** — same key ⇒ one payment, one charge. Enforced by Redis `SET NX` +
   DB unique index + the same key passed to the gateway (two dedup layers).
2. **Double-entry** — every payment writes a DEBIT+CREDIT pair in ONE ACID transaction;
   sum of all entries = 0. Money is moved, never created or destroyed.

**Concurrency:**
- Duplicate-request race → DB unique index is the referee (loser returns winner's result).
- Concurrent-debit race → `@Version` optimistic lock prevents overdraft / money creation.
- `synchronized` is useless here — correctness must live in the shared DB (multi-pod).

**HLD shape:**
- `PaymentService` → Redis idem check → gateway charge (same key) → ledger ACID write
- `PaymentQueryService` → Redis `payment:{id}:status` cache (8,700 reads/sec off the write path)
- MySQL ACID, ledger **sharded by account_id**, cold rows archived to S3
- Kafka fan-out → NotificationService (receipt) + ReconciliationService (ledger vs gateway report)

**SDE-3 signals to surface proactively:**
- `BigDecimal`, never `double`, for money.
- On any uncertainty (gateway timeout), stay PENDING — never guess SUCCESS.
- Ledger is immutable truth; balance is a cached projection; reconciliation catches drift.
- The architecture is driven by *correctness* (exactly-once, ACID) first, scale second.

---

## §16 — 🔄 Changelog

| Date | Change |
|------|--------|
| Aug 2026 | File created. Full 16-section solution for Payment System — Tier-1 JPMC Round 3 problem (⭐⭐⭐), done on HackerRank Code Pair. LLD: Money value object (BigDecimal), Payment state machine, immutable double-entry LedgerEntry, IdempotencyRecord, PaymentGateway interface, PaymentService + LedgerService. Concurrency: DB unique index for the duplicate-request race + @Version optimistic lock for the concurrent-debit race; explicit why-not-synchronized. HLD: 3-phase Confluent construction guide with Confluent single-column diagrams; two dedup layers + reconciliation for exactly-once; ledger sharded by account_id. Emphasis throughout on money-correctness invariants (idempotency + double-entry) as the framing device, per JPMC banking-domain focus. |
