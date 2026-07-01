# Idempotency

> **Standard followed:** `SystemDesignConcepts/notes-standards.md`

---

## 🎯 Why This Matters

In distributed systems, every network call can fail in a way that leaves you uncertain: did the server process it or not? The client times out — but the server may have already completed the operation. Without idempotency, retrying creates duplicates: two payments charged, two orders placed, two documents sent. Idempotency is the design pattern that makes retries safe. DocuSign's prep PDF explicitly covers billing idempotency keys, and every senior backend engineer working on payments, messaging, or APIs is expected to understand and implement this.

**Which round:** R2 System Design — surfaces any time you design a payment, order, notification, or API write path.
**Why senior engineers own this:** It's the difference between "we retry on failure" (every team does this) and "we retry SAFELY" (many teams don't). Idempotent designs eliminate an entire category of production incidents: duplicate charges, double-sends, inventory over-decrements.

---

## 📖 What is Idempotency?

**Full form:** Idempotent Operation / Idempotency Pattern

**Simple analogy:** Pressing an elevator button multiple times goes to the same floor once, not multiple times. Calling the same function with the same inputs produces the same result, no matter how many times you call it.

**Core principle:** An operation is **idempotent** if applying it multiple times produces the same result as applying it once. In distributed systems, when a network call might fail or timeout, idempotency ensures retries are safe — no duplicate charges, no duplicate emails, no inventory over-decrements. The server remembers each unique request (via a unique key) and returns the cached response if it's seen before.

**Why it matters in system design:** Network failures are inevitable. Without idempotency, every retry risks creating duplicates. With idempotency, the system tolerates retries safely, eliminating an entire class of production bugs.

---

## 📖 Terminology Table

| Term | Plain-English Meaning | Example |
|------|----------------------|---------|
| **Idempotent Operation** | applying the same operation N times produces the same result as applying it once | `PUT /orders/123 {status: "paid"}` — calling 5 times, result same as calling once |
| **Idempotency Key** | unique client-generated ID sent with every request; server uses it to detect duplicates | `Idempotency-Key: uuid-a3f2...` header; Stripe uses this for payment requests |
| **Idempotency Table** | server-side store mapping idempotency key → (response, timestamp); duplicate lookup before processing | `SELECT response FROM idempotency_keys WHERE key='uuid-123'` → return cached response |
| **At-Least-Once Delivery** | guarantee that a message is delivered at minimum once; duplicates are possible; requires idempotent consumer | Kafka default delivery: consumer may receive same event twice on rebalance |
| **Client-Generated Key (UUID)** | client generates the idempotency key before sending; ensures uniqueness even if request is never received | `UUID.randomUUID().toString()` — generated client-side before each payment attempt |
| **Safe HTTP Methods** | GET, HEAD, OPTIONS — idempotent and read-only by definition; retrying them is always safe | `GET /orders/123` — can call 100 times; result and state unchanged |
| **Non-Safe Methods** | POST — not idempotent by default; each call may create a new resource unless you add idempotency key | `POST /payments` without idempotency key → two calls = two charges |
| **Deduplication Window** | how long an idempotency key is remembered; duplicate requests outside this window treated as new | 24-hour window: if client retries after 25 hours → payment processed again |

---

## 🎨 Visual — System Topology: Idempotency in Architecture

```
CLIENT                              SERVER
(Browser / Mobile App)           (Payment Service)
    │                                │
    │ POST /payments                 │
    │ Idempotency-Key: uuid-123  ──▶ │
    │ { amount: 5000 }               │
    │                                ▼
    │                         ┌──────────────────┐
    │                         │ Check Idempotency │
    │                         │ Table             │
    │                         │ Key = uuid-123?   │
    │                         └────────┬─────────┘
    │                                  │
    │                    ┌─────────────┴──────────────┐
    │                    │ (if not found)             │
    │                    ▼                            │
    │             ┌──────────────────┐               │
    │             │ Process Payment   │               │
    │             │ Charge card ✅    │               │
    │             └────────┬──────────┘               │
    │                      │                         │
    │                      ▼                         │
    │             ┌──────────────────────┐          │
    │             │ Store in Idempotency  │          │
    │             │ Table                 │          │
    │             │ (key, response, time) │          │
    │             └────────┬──────────────┘          │
    │                      │                         │
    │ ◀──────────────────  │ 200 OK: payment_id      │
    │  (network drops)     │                         │
    │                      │                         │
    │ Retry (same key)     │                         │
    │ Idempotency-Key: uuid-123 ──▶                 │
    │                         (cached lookup)        │
    │                         Found! Return stored   │
    │                         response immediately   │
    │ ◀──────────────────────── 200 OK (same)       │
    │                                                │
    │ Result: No duplicate payment ✅               │

KEY INVARIANT:
   Each unique operation (Idempotency-Key) can be processed only once
   Retries return cached response without re-processing
   Prevents duplicates on network retries
```

---

## 🎨 Visual — Idempotency Key Flow (Component Detail)

Think of an elevator button.

You're on the ground floor. You press "3". The elevator starts moving. You press "3" again. And again. And again. The elevator does not go to floor 3 four times. The first press triggered the action. Every subsequent press was recognised as a duplicate and ignored — the elevator is already going to floor 3.

That's idempotency: **applying the same operation multiple times produces the same result as applying it once.**

Now apply this to a payment system. A user clicks "Pay ₹5000" on Razorpay. The payment goes through on the server, but the network drops before the success response reaches the user's browser. The user sees a spinner and clicks "Pay" again. Without idempotency, they get charged twice. With idempotency, the second request is recognised as a duplicate of the first and returns the already-processed result — no second charge.

**The two ingredients you need:**

**Ingredient 1 — A unique operation fingerprint (the Idempotency Key):**
The client generates a UUID (a random unique identifier) before making the request and attaches it to the header: `Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000`. This UUID is the "elevator floor number" — it uniquely identifies THIS specific intended operation. The same UUID means "this is a retry of the same operation." A different UUID means "this is a new operation."

**Ingredient 2 — A memory of what happened (the Idempotency Table):**
The server maintains a table that maps every idempotency key it has seen to the response it returned. On every incoming request: check the table first. If the key is already there, return the stored response. If not, process the operation, store the key + response, return the result.

**The four cases you must handle:**

- **Case 1 — First request, key not seen:** Process normally. Store key + response.
- **Case 2 — Retry, processing already complete:** Key found, return stored response. Don't process again.
- **Case 3 — Retry, processing still in flight:** Key found but no response yet (concurrent duplicate). Make the second request wait or return `409 Conflict`.
- **Case 4 — Different request, same key (client bug):** If the request body is different but the key is the same, return `422 Unprocessable Entity` — don't silently use the wrong key.

**The key insight is:** Idempotency moves the burden of retry safety from "hope the server knows what it already did" to an explicit, queryable record. The server doesn't need to understand what the request does — it just needs to check: "Have I seen this key? Yes → replay. No → process."

---

## 🎨 Visual — Idempotency Key Flow

```
  FIRST REQUEST (no duplicate)
  ─────────────────────────────────────────────────────────────────

  Client                           Server
  ──────                           ──────
  Generate UUID: abc-123
  POST /payments
  Idempotency-Key: abc-123  ──▶   Check idempotency_keys table
  { amount: 5000 }                   WHERE key = 'abc-123'
                                     → NOT FOUND

                                  Process payment ✅
                                  INSERT idempotency_keys
                                     (key='abc-123', status='SUCCESS',
                                      response='{"payment_id":"pay_001"}')

                             ◀──  200 OK: {"payment_id": "pay_001"}

  CLIENT GETS RESPONSE ✅


  RETRY (network dropped on first attempt — client didn't get the 200)
  ─────────────────────────────────────────────────────────────────

  Client                           Server
  ──────                           ──────
  POST /payments (retry)
  Idempotency-Key: abc-123  ──▶   Check idempotency_keys table
  { amount: 5000 }                   WHERE key = 'abc-123'
                                     → FOUND, status='SUCCESS'

                                  DO NOT process payment again ✅
                             ◀──  200 OK: {"payment_id": "pay_001"}
                                  (same response as first time)

  CLIENT GETS SAME RESULT ✅  No duplicate payment.


  CONCURRENT DUPLICATE (two retries arrive before either completes)
  ─────────────────────────────────────────────────────────────────

  Request #1:  arrives, inserts key with status='IN_PROGRESS'
  Request #2:  arrives, finds key with status='IN_PROGRESS'
               → returns 409 Conflict or waits for REQUEST #1 to complete

  KEY INVARIANT:
     The unique constraint on idempotency_keys.key is the safety net.
     Only one request can INSERT for a given key — the second gets a DB constraint violation.
     The loser reads the winner's stored response and returns it.
```

---

## ⚙️ How It Actually Works

### The Idempotency Table

```java
// The idempotency_keys table — one record per unique client operation
// CREATE TABLE idempotency_keys (
//     key         VARCHAR(64) PRIMARY KEY,  -- the UUID from the client
//     request_hash VARCHAR(64),             -- hash of request body (to detect Case 4)
//     status      ENUM('IN_PROGRESS', 'SUCCESS', 'FAILED'),
//     response    TEXT,                     -- stored JSON response
//     created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
//     expires_at  TIMESTAMP NOT NULL        -- clean up old keys after 24h
// );
// INDEX: expires_at (for scheduled cleanup job)

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyRecord {

    @Id
    @Column(name = "key")
    private String key;

    @Column(name = "request_hash")
    private String requestHash;

    @Enumerated(EnumType.STRING)
    private IdempotencyStatus status;

    @Column(name = "response", columnDefinition = "TEXT")
    private String response;

    @Column(name = "expires_at")
    private Instant expiresAt;
}
```

### The Service-Layer Check

**Steps:**
1. **Extract** `Idempotency-Key` from request header. If missing, reject with `400 Bad Request`.
2. **Hash** the request body (SHA-256) to detect Case 4 (same key, different body).
3. **Check** the idempotency table: does this key exist?
4. **If exists and SUCCESS:** return stored response immediately — do NOT process.
5. **If exists and IN_PROGRESS:** return `409 Conflict` — concurrent duplicate.
6. **If exists and body hash mismatch:** return `422 Unprocessable Entity` — client bug.
7. **If not exists:** insert with `IN_PROGRESS`, process the operation, update to `SUCCESS` + store response, return result. Wrap in a transaction.

```java
@Transactional
public PaymentResponse createPayment(CreatePaymentRequest req, String idempotencyKey) {
    // Step 1: key is required
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
        throw new BadRequestException("Idempotency-Key header is required");
    }

    // Step 2: hash request body for Case 4 detection
    String requestHash = sha256(req.toString());

    // Step 3: check table
    Optional<IdempotencyRecord> existing = idempotencyRepo.findById(idempotencyKey);

    if (existing.isPresent()) {
        IdempotencyRecord record = existing.get();

        // Step 6: same key, different body — client bug
        if (!record.getRequestHash().equals(requestHash)) {
            throw new UnprocessableEntityException(
                "Idempotency key reused with different request body"
            );
        }

        // Step 5: concurrent duplicate in flight
        if (record.getStatus() == IdempotencyStatus.IN_PROGRESS) {
            throw new ConflictException("Request with this key is still being processed");
        }

        // Step 4: already completed — replay stored response
        return deserialize(record.getResponse(), PaymentResponse.class);
    }

    // Step 7: new request — insert IN_PROGRESS, process, store result
    idempotencyRepo.save(new IdempotencyRecord(
        idempotencyKey,
        requestHash,
        IdempotencyStatus.IN_PROGRESS,
        null,
        Instant.now().plus(Duration.ofHours(24))
    ));

    PaymentResponse response = paymentService.charge(req);

    // Update to SUCCESS with stored response
    idempotencyRepo.updateSuccess(idempotencyKey, serialize(response));

    return response;
}
```

### Handling the Race Condition (Two Concurrent Requests, Same Key)

The table has a **PRIMARY KEY on `key`** — which is a unique constraint. If two requests simultaneously try to INSERT the same key, exactly one will succeed; the other gets a DB unique constraint violation.

```java
// Catch the unique constraint violation from the concurrent duplicate
try {
    idempotencyRepo.save(new IdempotencyRecord(idempotencyKey, ...));
} catch (DataIntegrityViolationException e) {
    // Another request already started — read what they stored
    IdempotencyRecord existing = idempotencyRepo.findById(idempotencyKey)
        .orElseThrow(); // must exist now
    if (existing.getStatus() == IdempotencyStatus.SUCCESS) {
        return deserialize(existing.getResponse(), PaymentResponse.class);
    }
    throw new ConflictException("Duplicate request in flight");
}
```

### Idempotency in Kafka Consumer (Message Deduplication)

Kafka delivers messages **at-least-once** — in failure scenarios, the same message can be delivered twice. Without idempotency on the consumer, you process the same event twice.

```java
// Pattern: store processed message IDs in a "processed_events" table
// CREATE TABLE processed_events (
//     message_id VARCHAR(64) PRIMARY KEY,
//     processed_at TIMESTAMP
// );

@KafkaListener(topics = "payment.succeeded")
@Transactional
public void onPaymentSucceeded(PaymentSucceededEvent event) {
    // Idempotency check — have we processed this message already?
    boolean alreadyProcessed = processedEventRepo.existsById(event.getMessageId());
    if (alreadyProcessed) {
        log.info("Duplicate message ignored: {}", event.getMessageId());
        return;
    }

    // Process the event
    emailService.sendPaymentConfirmation(event.getUserId(), event.getAmount());

    // Mark as processed — inside the same transaction as the above work
    processedEventRepo.save(new ProcessedEvent(event.getMessageId(), Instant.now()));
}
```

**The critical detail:** The `processedEventRepo.save()` must be in the SAME database transaction as the actual work (`emailService.sendPaymentConfirmation(...)`). If they're separate and the app crashes between them, the work is done but the event isn't marked processed — it will be processed again on the next delivery.

---

## 🏢 Real World — Where Companies Use This

- **Stripe** — Every write endpoint (create charge, create customer, create subscription) accepts `Idempotency-Key`. Their docs: "Stripe's API supports idempotent requests to allow you to retry safely." SDKs auto-generate and attach UUIDs. A failed payment retry returns the exact same charge object, not a second charge.
- **Razorpay** — Payment capture API uses idempotency keys. DocuSign's prep PDF explicitly calls out billing idempotency as a required design for the enterprise tier — same pattern as Razorpay.
- **PhonePe / UPI** — Every UPI payment has a transaction reference ID (the idempotency key). The NPCI network may retry failed transactions; the bank's idempotency check prevents double debit.
- **DocuSign** — Envelope creation API accepts a client-supplied `clientUserId` and checks for duplicate envelope creation. The envelope ID is deterministic for the same document + same recipients on the same day — a retry returns the existing envelope.
- **Uber** — Trip creation is idempotent per `request_id`. If the app retries after a network drop, the same trip request returns the existing trip rather than creating a second booking.
- **Amazon SQS** — Message deduplication ID on FIFO queues — within a 5-minute deduplication window, messages with the same ID are treated as duplicates and not delivered twice to consumers.

---

## 🧭 When to Use vs When NOT to Use

| Make this operation idempotent | Idempotency not needed |
|---|---|
| Any POST that creates a resource (payment, order, document, user) | GET, DELETE, PUT — already idempotent by HTTP semantics |
| Kafka consumer processing irreversible side effects (charges, emails) | Internal read operations |
| Any operation that charges money or moves inventory | Operations that are naturally safe to repeat (increment a view counter — being off by 1 is acceptable) |
| APIs exposed to external clients who will implement retry logic | Short-lived operations where the retry window is smaller than TTL and duplicates are genuinely harmless |

**The common mistake:** Treating idempotency as an API-layer concern only. The same issue exists in Kafka consumers, SQS workers, and any async processor that handles at-least-once delivery. Every place that receives a message and performs a side effect needs idempotency protection.

---

## ⚠️ Trade-offs

| | |
|---|---|
| **You gain** | Safe retries — clients can retry aggressively without risk of duplicates. Cleaner incident response — when something fails, retry without manual investigation of "did it go through?" |
| **You lose** | Storage cost for the idempotency table (typically small). DB lookup on every write path (one extra SELECT before processing). Complexity of the IN_PROGRESS state for concurrent duplicates. |
| **Failure mode** | Idempotency key expires before the client's retry window. Client retries after 25 hours; the 24-hour TTL key is gone; the server processes it as a new request and creates a duplicate. Always match TTL to the client's maximum retry window, with a safety margin. |

---

## 🔬 Interview Q&As

### Tier 1 — Surface Questions

### Q: "What is idempotency and why does it matter in distributed systems?"
> An operation is idempotent if applying it once and applying it N times produces the same result. It matters because in distributed systems, the "did my request go through?" question has no reliable answer — the server might have processed it before the network dropped. Without idempotency, retrying creates duplicates: two charges, two orders, two emails. With idempotency — via an Idempotency-Key header and a server-side record of what was processed — retries are safe. The client can retry confidently, and the server recognises the duplicate and replays the stored result.

### Q: "Walk me through how you'd implement idempotency for a payment API."
> Client generates a UUID before the request and sends it in `Idempotency-Key` header. Server checks an `idempotency_keys` table — primary key on the UUID. If found: return stored response, don't charge again. If not found: insert with `IN_PROGRESS`, process the charge, update to `SUCCESS` with the stored response. The insert-then-process step must be wrapped in a transaction. The unique constraint on the key column handles concurrent duplicates — only one INSERT succeeds, the other catches the violation and reads the stored result. Keys expire after 24 hours to bound storage.

### Q: "How is idempotency different from exactly-once delivery?"
> Exactly-once delivery is a guarantee made by the messaging infrastructure — the broker ensures each message is delivered exactly once. Idempotency is a design property of the consumer — it can handle the same message arriving twice without causing duplicates. In practice, truly exactly-once delivery is very hard to guarantee (Kafka provides it only within a transaction across its own components). The industry standard is at-least-once delivery + idempotent consumers — the infrastructure might deliver twice, but the consumer is designed to handle that gracefully. Idempotency is the consumer's half of that contract.

---

### Tier 2 — Cross / Probe Questions

### Q: "Your idempotency check and the business operation are in the same transaction. The transaction rolls back. What happens to the idempotency record?"
> If the idempotency INSERT and the business operation are in the same DB transaction, and the transaction rolls back — both roll back together. The idempotency record is gone. The next retry will be treated as a new request and re-processed. This is usually the RIGHT behaviour — the operation didn't complete, so the retry should re-attempt it. The dangerous case is the opposite: if the business operation commits (e.g., money moved) but the idempotency record INSERT fails — then the idempotency protection is gone and a retry will double-charge. Always keep both in the same transaction.

### Q: "What if the client generates the same idempotency key for a completely different payment amount? (Bug in the client)"
> This is the "same key, different body" case. Hash the request body (SHA-256) and store it alongside the idempotency key. On incoming requests, if the key exists but the request hash doesn't match, return `422 Unprocessable Entity` with a clear error: "Idempotency key reused with a different request body." This is a client bug — the key is supposed to uniquely identify one specific intended operation. Silently processing the different body would corrupt data. Flagging it loudly helps the client team find and fix the bug.

### Q: "Idempotency works for the HTTP layer. What about Kafka consumers — how do you ensure they're idempotent?"
> Kafka delivers at-least-once — the same message can arrive multiple times on rebalance or node failure. Idempotency on the consumer: store processed message IDs (the Kafka `offset` or a business-level event ID) in a `processed_events` table with a primary key. On each message, check if the ID is already there — if yes, skip. If no, process AND insert the ID in the same DB transaction. The atomicity is critical: if the app crashes after processing but before recording the ID, the message will be re-delivered and re-processed. Transactional outbox or commit-then-mark is the pattern. Alternatively, Kafka transactions can atomically commit the consumer offset and the DB write — but that requires Kafka + DB transactional coordination which most teams avoid.

---

## 🧾 TL;DR — One Interviewer-Ready Line

> *"Idempotency makes retries safe — the client attaches a UUID as an Idempotency-Key, the server checks a keyed table before processing, and if the key is already there it replays the stored response instead of re-executing. The same pattern applies to Kafka consumers with a processed_events table — always inside the same transaction as the actual work."*

---

## 🔗 Related Concepts

- **`11-api-design.md`** — The `Idempotency-Key` header pattern introduced briefly there is fully detailed here. API design is where the client-facing contract lives; this note is the server-side implementation.
- **`07-cdc-outbox.md`** — The transactional outbox pattern solves a related problem: ensuring the DB write and the Kafka publish are atomic. Idempotency on the Kafka consumer is the other half of that guarantee.
- **`02-rate-limiting.md`** — Rate limiting and idempotency interact: a retry after a `429 Too Many Requests` must use the same idempotency key — otherwise the client creates a new operation when the previous one may have gone through.
- **`04-idempotency_advanced.md`** — For advanced patterns: saga pattern (multi-step operations with compensations), batch idempotency (partition tracking and retry of failed partitions), deterministic request ID generation (deriving keys from inputs, not random).

---

## 📚 Further Reading (Optional — after the note)

| Resource | What it adds | Time |
|---|---|---|
| **Stripe Engineering Blog — Idempotency** | Real-world implementation decisions from Stripe's payment infrastructure. Search: "Stripe idempotent requests" | ~10 min |
| **Arpit Bhayani — Idempotency** (YouTube) | Server-side deduplication mechanics, DB table design. Search: "Arpit Bhayani idempotency" | ~20 min |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 2026 | File created. DocuSign R2 prep — prep PDF explicitly covers billing idempotency. Covers HTTP Idempotency-Key pattern, idempotency table design, concurrent duplicate handling, Kafka consumer deduplication. |
