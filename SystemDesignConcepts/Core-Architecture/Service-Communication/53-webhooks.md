# Webhooks — HTTP Push Callbacks

---

## 📖 What is a Webhook?

**Full form:** Webhook (also called "HTTP Callback" or "Reverse API")

**Simple analogy:** Traditional API = you call a restaurant every 5 minutes to ask "is my order ready?" Webhook = you leave your phone number at the counter; the restaurant calls YOU the moment your food is ready. Same outcome — you learn when the event happened — but the direction of the call is reversed, and you stop wasting 99% of your calls on "not yet."

**Core principle:** Instead of your system polling another service for state changes, the other service sends an HTTP POST to your registered endpoint whenever an event occurs. The external system is the HTTP *client*; your service is the HTTP *server*. You react to events instead of checking for them.

**Two roles:**
- **Provider** (e.g., DocuSign, Stripe, GitHub): the service that generates events and sends the HTTP POST to subscriber endpoints
- **Consumer** (your service): registers an HTTPS endpoint URL with the provider, validates incoming events, and processes them

**Why it matters at scale:** 1 million e-signature envelopes waiting for completion → polling at 5-minute intervals = 1M × 288 = 288 million outbound requests per day, most returning "still pending." Webhooks replace all of this with exactly one request per event, delivered the moment the event happens.

---

## 🎯 Why This Matters

Webhooks appear in almost every DocuSign interview — because **DocuSign Connect** (DocuSign's built-in webhook delivery system that pushes envelope lifecycle events to registered consumer endpoints) IS the mechanism by which your system learns about every envelope state change. Every event — Sent, Delivered, Viewed, Signed, Declined, Voided — arrives as a webhook. If you are designing any system that reacts to DocuSign events, you are building a webhook receiver.

Beyond DocuSign:
- **Stripe:** `payment_intent.succeeded` and `charge.failed` → fulfill orders or trigger payment recovery flows
- **GitHub:** Push events, pull_request events → trigger CI/CD pipelines automatically
- **Shopify:** `orders/create` → inventory decrement, fulfillment kickoff

The two interview traps: (1) processing synchronously inside the HTTP handler — the provider times out, retries, and you process the same event twice; (2) not verifying the HMAC signature — anyone on the internet can POST to your endpoint and inject fake events.

---

## 🧠 Mental Model

**The "Reverse API" frame:**

In a normal REST API, you are the client and the external service is the server — you initiate every request:

```
You ──── GET /envelopes/123/status ────▶ DocuSign API
You ◀─── 200 { status: "pending" } ────
(repeat every 5 minutes × 1M envelopes = 288M wasted requests/day)
```

In a webhook, the roles are flipped — DocuSign is the client, you are the server:

```
DocuSign ──── POST /webhooks/docusign ────▶ You
             { envelopeId, status: Signed }
You ◀──────── 200 OK ────────────────────
(exactly 1 request, delivered the instant it happens)
```

**Three questions every webhook receiver must answer:**

| Question | Problem | Solution |
|---|---|---|
| "Is this request genuine?" | Anyone on the internet can POST to your endpoint | HMAC-SHA256 signature verification |
| "Have I already processed this?" | Provider retries → same event arrives multiple times | Idempotency key (event_id) stored in DB |
| "Is this a replay of an old event?" | Attacker replays a valid captured request hours later | Timestamp header + 5-minute window check |

**Why return 200 before processing:** The provider has a short HTTP timeout (often 5 seconds). If your endpoint spends 10 seconds updating the database and sending emails before returning, the provider sees a timeout, marks the delivery as failed, and retries — now you process the same event twice. The correct pattern: validate → persist event ID → return 200 immediately → enqueue for async processing.

---

## 🎨 Visual — Full System Topology: Webhooks in Architecture

```
POLLING MODEL (before webhooks, 1M active envelopes):

Your Backend ──── GET /envelopes/{id}/status ────▶ DocuSign API
             ◀─── 200 "status: pending" ──────────
(every 5 minutes × 1M envelopes = 288M requests/day, ~99% wasted)


WEBHOOK MODEL (event-driven push):

DocuSign ──── POST /webhooks/docusign ──────────▶ Your Backend
              X-DocuSign-Signature-1: abc...
              X-DocuSign-Timestamp: 2026-07-09T10:00:00Z
              { envelopeId, status: Completed, ... }
         ◀─── 200 OK ────────────────────────────
(exactly 1 request per event, zero polling, zero wasted calls)


Full Architecture — Webhooks in a Microservices Stack:

External System         Your API Layer              Internal Async Processing
(DocuSign)              (Webhook Controller)        (Queue + Event Processor)

DocuSign                ┌────────────────────────┐  ┌─────────────────────────┐
Event fires:            │ /webhooks/docusign      │  │ Internal Queue          │
EnvelopeSigned          │                        │  │ (Redis / Kafka / SQS)   │
      │                 │ ① verify HMAC          │  │         │               │
      └── HTTPS POST──▶ │ ② idempotency check    ├─▶│         ▼               │
         Sig header      │ ③ timestamp check      │  │ EnvelopeProcessor       │
         Timestamp       │ ④ persist event_id     │  │ - upsert DB             │
         JSON payload    │ ⑤ return 200 OK        │  │ - notify signers        │
                         │ ⑥ enqueue async        │  │ - trigger billing       │
                         └────────────────────────┘  └─────────────────────────┘

KEY INVARIANT:
   The external system calls your endpoint — you do not poll
   Your HTTP handler does 6 fast steps and returns 200; business logic is async
   Same event arriving multiple times → same DB outcome (idempotent receiver)
```

---

## 🎨 Visual — Component Detail: Webhook Delivery Lifecycle

```
PROVIDER SIDE                               CONSUMER (YOUR) SIDE

Event fires                                 Your /webhooks/docusign endpoint:
(Envelope Signed in DocuSign)
      │
      ▼
Outbound event queue                        Receive HTTPS POST
(provider buffers before sending)                 │
      │                                     ① Verify HMAC signature
      ▼                                           │ mismatch ──▶ 401 (drop)
HTTPS POST to registered URL ───────────▶         │ match ──▶ continue
Headers:                                    ② Check idempotency (event_id in DB)
  X-Signature: HMAC(secret, body)                 │ seen ──▶ 200 (skip)
  X-Timestamp: 2026-07-09T10:00:00Z              │ new ──▶ continue
  X-Event-Id:  evt_abc123                   ③ Validate timestamp
Body: { envelopeId, status, ... }                 │ >5 min old ──▶ 400 (reject)
                                                  │ fresh ──▶ continue
Response?                                   ④ Persist event_id (unique constraint)
  ├── 200 OK ──────────────── ✓             ⑤ Return 200 OK immediately
  ├── 5xx / timeout                         ⑥ Enqueue event for async processing
  │     retry after 5s                            │
  ├── 5xx again                                    ▼
  │     retry after 25s                      Internal Queue
  ├── 5xx again                             → EnvelopeProcessor (async)
  │     retry after 125s                       → DB upsert (idempotent)
  └── N failures → DLQ + ops alert             → send notifications
                                               → update audit log

KEY INVARIANTS:
   Provider delivers at-least-once; your idempotency key achieves exactly-once effect
   Return 200 before any slow processing — never block the HTTP response on DB writes
   Idempotency key = event_id OR composite (envelopeId + eventType); unique in DB
```

---

## ⚙️ How It Actually Works

### What is HMAC-SHA256, and why does it fit here?

**HMAC-SHA256** (Hash-based Message Authentication Code with SHA-256 — a cryptographic signature computed by applying the SHA-256 hash function to the message body combined with a shared secret key) is the standard authentication mechanism for webhooks. The provider and consumer share a secret at registration time. When the provider sends a webhook, it computes `HMAC-SHA256(secret, rawBody)` and includes the result in a request header. The consumer recomputes the same HMAC over the raw body and compares — if they match, the request is authentic and unmodified.

In an interview: *"I use HMAC-SHA256 with a per-subscriber shared secret. The provider signs the raw request body; I recompute the HMAC on receipt and compare using `MessageDigest.isEqual()` for constant-time comparison. String.equals() short-circuits on the first mismatched byte, leaking timing information that lets an attacker brute-force the signature byte by byte."*

See: **`SystemDesignConcepts/Patterns/tools-glossary.md` — HMAC** for the full plain-English explanation.

---

**Receiving Webhooks — Verification, Deduplication, Async Processing**

**Steps in plain English:**
1. **Verify HMAC signature first** — recompute HMAC over raw body, compare to header in constant time; reject 401 on mismatch.
2. **Extract the idempotency key** (event ID from header or payload body) — this is the deduplication handle.
3. **Check idempotency** — query DB for this event ID; if found, return 200 and skip all processing.
4. **Validate timestamp** — reject events older than 5 minutes (replay attack prevention).
5. **Persist the event ID** atomically (DB unique constraint) before returning — prevents race condition on concurrent duplicate deliveries.
6. **Return 200 immediately**, then enqueue for async processing — never do slow work before responding.

```java
// Webhook controller — receives and validates DocuSign Connect events
@RestController
@RequestMapping("/webhooks")
public class WebhookController {

    @Autowired
    private WebhookEventRepository eventRepository;

    @Autowired
    private WebhookEventQueue eventQueue;

    @Value("${docusign.webhook.secret}")
    private String webhookSecret;

    @PostMapping("/docusign")
    public ResponseEntity<Void> handleDocuSignEvent(
            @RequestHeader("X-DocuSign-Signature-1") String receivedSignature,
            @RequestHeader("X-DocuSign-Timestamp") String timestampHeader,
            @RequestBody String rawBody) {

        // Step 1 — verify HMAC; reject unauthenticated requests at the door
        if (!isValidSignature(rawBody, receivedSignature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Step 2 — parse event and extract idempotency key
        DocuSignEvent event = parseEvent(rawBody);
        String eventId = event.getEnvelopeId() + ":" + event.getStatus();

        // Step 3 — idempotency check (already processed this event?)
        if (eventRepository.existsByEventId(eventId)) {
            // Already seen: acknowledge without re-processing
            return ResponseEntity.ok().build();
        }

        // Step 4 — replay attack check: reject events older than 5 minutes
        Instant eventTime = Instant.parse(timestampHeader);
        if (eventTime.isBefore(Instant.now().minus(Duration.ofMinutes(5)))) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        // Step 5 — persist event ID atomically before returning (unique constraint prevents races)
        eventRepository.insertOrIgnore(eventId);

        // Step 6 — return 200 BEFORE processing; never make provider wait on business logic
        eventQueue.enqueue(event);
        return ResponseEntity.ok().build();
    }

    private boolean isValidSignature(String payload, String receivedSignature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(
                webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"
            );
            mac.init(keySpec);
            byte[] expectedBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expected = Base64.getEncoder().encodeToString(expectedBytes);
            // Constant-time comparison — never use String.equals() here:
            // equals() returns early on first mismatch, leaking timing info to attackers
            return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                receivedSignature.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            return false;
        }
    }

    private DocuSignEvent parseEvent(String rawBody) {
        try {
            return new ObjectMapper().readValue(rawBody, DocuSignEvent.class);
        } catch (Exception e) {
            throw new RuntimeException("Invalid webhook payload", e);
        }
    }
}
```

---

**Sending Webhooks — Provider Side (when YOUR service is the webhook sender)**

When you build a SaaS platform and offer webhooks to your customers, you become the provider. You must sign outgoing payloads, handle timeouts, retry on failure, and fan out to multiple subscribers.

**Steps in plain English:**
1. **Serialize the event to a JSON string** — you must sign the exact bytes you send (not a re-serialized version).
2. **Compute HMAC-SHA256** over the raw body using this subscriber's unique secret.
3. **Include the signature, timestamp, and event ID** in request headers.
4. **POST to the subscriber's registered endpoint** with a short connection timeout.
5. **On non-2xx or timeout, schedule a retry** with exponential backoff; after N failures, move to DLQ and alert the customer.

```java
// Webhook sender — delivers events to registered subscriber endpoints
@Service
public class WebhookSender {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private RetryQueue retryQueue;

    public void deliver(WebhookSubscription subscription, WebhookEvent event) {
        // Step 1 — serialize to JSON string; sign the exact bytes we send
        String payload = serialize(event);

        // Step 2 — compute HMAC-SHA256 with this subscriber's unique secret
        String signature = computeHmac(payload, subscription.getSecret());
        String timestamp = Instant.now().toString();

        // Step 3 — include signature, timestamp, event ID in headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Webhook-Signature", signature);
        headers.set("X-Webhook-Timestamp", timestamp);
        headers.set("X-Webhook-Event-Id", event.getId());

        HttpEntity<String> request = new HttpEntity<>(payload, headers);

        try {
            // Step 4 — POST with short timeout; subscriber must return 200 quickly
            ResponseEntity<Void> response = restTemplate.postForEntity(
                subscription.getEndpointUrl(), request, Void.class
            );
            if (!response.getStatusCode().is2xxSuccessful()) {
                // Step 5 — non-2xx: schedule retry with exponential backoff
                retryQueue.schedule(subscription, event, 1);
            }
        } catch (ResourceAccessException e) {
            // Timeout or connection failure: schedule retry
            retryQueue.schedule(subscription, event, 1);
        }
    }

    private String computeHmac(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"
            );
            mac.init(keySpec);
            byte[] hmacBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hmacBytes);
        } catch (Exception e) {
            throw new RuntimeException("HMAC computation failed", e);
        }
    }

    private String serialize(Object event) {
        try {
            return new ObjectMapper().writeValueAsString(event);
        } catch (Exception e) {
            throw new RuntimeException("Serialization failed", e);
        }
    }
}
```

---

**Endpoint Registration and Secret Rotation**

**What is endpoint registration, and why does it fit here?** Before any provider can send webhooks to your service, you register your endpoint URL and receive a unique shared secret. This secret is generated once at registration, returned to you exactly once (store it in a secrets manager — it cannot be retrieved again), and used for all future HMAC verification. **Secret rotation** replaces the old secret with a new one — during rotation, accept both old and new signatures briefly to avoid dropping in-flight events.

**Steps in plain English:**
1. **Generate a cryptographically random secret** per subscriber — never reuse secrets across subscribers.
2. **Store the endpoint URL + secret** in the subscriptions table.
3. **Return the secret to the subscriber exactly once** — store only a hash in your DB if security demands it.

```java
// Endpoint registration service — subscriber registers their URL; receives their unique secret
@Service
public class WebhookRegistrationService {

    @Autowired
    private WebhookSubscriptionRepository subscriptionRepository;

    public WebhookRegistration register(RegisterWebhookRequest request) {
        // Step 1 — generate a cryptographically random 32-byte (256-bit) secret
        byte[] secretBytes = new byte[32];
        new SecureRandom().nextBytes(secretBytes);
        String plainSecret = Base64.getEncoder().encodeToString(secretBytes);

        // Step 2 — store subscription: endpoint URL + secret + allowed event types
        WebhookSubscription subscription = WebhookSubscription.builder()
            .subscriberId(request.getSubscriberId())
            .endpointUrl(request.getEndpointUrl())
            .secret(plainSecret)
            .eventTypes(request.getEventTypes())
            .active(true)
            .createdAt(Instant.now())
            .build();
        subscriptionRepository.save(subscription);

        // Step 3 — return plain secret exactly once; it will never be retrievable again
        return WebhookRegistration.builder()
            .subscriptionId(subscription.getId())
            .secret(plainSecret)
            .build();
    }
}
```

---

## 🏢 Real World — Where Companies Use This

- **DocuSign Connect:** The primary interview context. Connect (DocuSign's webhook delivery system) pushes envelope lifecycle events — Sent, Delivered, Viewed, Signed, Declined, Voided, Purged — to your registered endpoint. Your envelope management service must handle these reliably: HMAC verification + idempotency is mandatory because Connect retries on non-2xx. Interview scenario: "When an envelope is signed by all parties, how does your system find out?" → DocuSign Connect fires a webhook → your controller verifies + enqueues → async processor updates envelope status and notifies the account.

- **Stripe:** Payment events (`payment_intent.succeeded`, `payment_intent.payment_failed`, `charge.dispute.created`) arrive as webhooks. Stripe signs with HMAC-SHA256 using `whsec_` prefixed secrets and puts both timestamp and signature in `Stripe-Signature` header. On `payment_intent.succeeded`, fulfill the order. On `charge.dispute.created`, flag the transaction for fraud review.

- **GitHub:** Push events, pull_request events, check_suite events → your CI/CD pipeline triggers on every push. GitHub signs with `X-Hub-Signature-256`. You can also call the redeliver API to replay any specific delivery.

- **Shopify:** `orders/create`, `products/update`, `inventory_levels/update` → your inventory management and search indexing pipelines. Signs with `X-Shopify-Hmac-Sha256` header.

---

## 🧭 When to Use vs When NOT to Use

| Use webhooks when | Do NOT use webhooks when |
|---|---|
| Reacting to events from an external system you don't control (DocuSign, Stripe, GitHub) | You control both producer and consumer — use internal message queue (Kafka / SQS) instead |
| You want event-driven, zero-polling architecture with external partners | Strict event ordering is required — providers don't guarantee delivery order |
| Events are infrequent but must trigger actions quickly (envelope signed → trigger workflow) | You need synchronous request-response — webhooks are one-way fire-and-forget |
| Cross-company or cross-domain integration at organizational boundaries | Events are extremely high frequency (millions/sec) — use a streaming platform instead |
| | You need bidirectional communication — use WebSocket or gRPC streaming |

**The common mistake:** Using webhooks for internal service-to-service communication. For that, use Kafka (event sourcing, fan-out) or direct calls (gRPC/REST). Webhooks are specifically for the integration boundary between systems that don't share infrastructure.

---

## ⚠️ Trade-offs

| | |
|---|---|
| **You gain** | Zero polling cost — events arrive instantly, not on a timer. Provider fan-out — one provider event reaches all subscribers without your involvement. Event-driven decoupling — provider has no knowledge of what you do with events. Works across organizational and network boundaries. |
| **You lose** | Your endpoint must be publicly reachable over HTTPS — local development requires tunneling tools (ngrok). Events are not strictly ordered — provider retries can cause out-of-order arrival if a retry of event #1 arrives after event #2's first delivery. You now have an inbound HTTP attack surface that must be guarded with HMAC. |
| **Failure modes** | **Retry storm:** your endpoint is slow → provider times out → retries → endpoint gets hammered with duplicates. Mitigation: return 200 before processing, use an internal queue to buffer. **Event loss:** provider exhausts retries while your service is down; events land in provider's DLQ — you need a reconciliation job or manual replay API for the missed window. **Duplicate delivery:** any retry delivers a duplicate — idempotency on event_id is mandatory, not optional. |

---

## 🔬 Interview Q&As

### Q: "How do you verify that a webhook payload is authentic?"

> Compute HMAC-SHA256 over the raw request body using the shared secret provided at webhook registration. Compare the result to the `X-Signature` header from the provider — using **constant-time comparison** (`MessageDigest.isEqual()`), never with `String.equals()`. `equals()` short-circuits on the first mismatched byte, creating a timing oracle: an attacker can measure response time to determine how many bytes of their forged signature match, then brute-force the rest. If signatures don't match, return 401 and discard the request. ⭐ **Tier 1 — security fundamentals**

### Q: "What happens if your webhook endpoint is down for 2 hours?"

> The provider retries with exponential backoff (e.g., 5s → 25s → 125s → ...). Most providers retry for 24–72 hours. When your endpoint comes back, it receives all accumulated retries — potentially hundreds of the same events. This is why idempotency is mandatory: your DB uses a unique constraint on event_id with `INSERT ... ON CONFLICT DO NOTHING`, so every duplicate returns 200 without re-processing. For events beyond the provider's retry window, implement a reconciliation job: query the provider's API for the missed time range and replay any missed events manually. ⭐ **Tier 1 — reliability**

### Q: "Your webhook endpoint takes 10 seconds to process. The provider times out after 5 seconds. What breaks, and how do you fix it?"

> The provider sees a 5-second timeout (no response), marks the delivery failed, and retries. Your endpoint is still mid-processing — it eventually completes. On the retry, your endpoint processes the same event again. Without idempotency you corrupt state; with idempotency you get duplicate-safe behavior but wasted work. The root fix: **return 200 before processing**. The HTTP handler does only: verify HMAC → check idempotency → validate timestamp → persist event_id → enqueue → return 200. All business logic (DB writes, downstream calls, emails) runs in the async queue processor after the HTTP response has already been sent. ⭐ **Tier 2 — async pattern + timeout footgun**

### Q: "DocuSign fires EnvelopeSigned at 10:00 AM. Your endpoint processes it successfully. At 10:30 AM, DocuSign sends the same event again (your 200 was delayed and DocuSign didn't receive it). What happens?"

> Second delivery hits your endpoint. Step 1: HMAC verifies ✓. Step 2: idempotency check — you query `webhook_events` for `(envelopeId=X, status=Signed)`. This row was inserted at 10:00 AM; it exists. Return 200 and skip all processing. The async processor never sees the duplicate. One subtlety: the insert at step 5 must be atomic — use a DB unique constraint on `event_id` and `INSERT ... ON CONFLICT DO NOTHING`. If two concurrent deliveries of the same event arrive simultaneously, only one insert succeeds; the other sees the constraint violation and returns 200 safely. ⭐ **Tier 2 — idempotency + concurrent delivery race**

### Q: "How do you prevent replay attacks on your webhook endpoint?"

> A replay attack: attacker captures a valid authenticated webhook (HMAC verifies ✓ — body and signature are unchanged) and replays it hours later to trigger the action again. Fix: provider includes a timestamp in the request (Stripe uses `Stripe-Signature: t=1720000000,v1=abc...`; DocuSign uses `X-DocuSign-Timestamp`). On receipt, check: `now - timestamp > 5 minutes → reject 400`. This window is the tradeoff — short enough to block most replay attacks, long enough to handle clock skew and delivery delays. The attacker must replay within 5 minutes of the original request, which is usually impractical for the attack scenarios you're defending against. ⭐ **Tier 2 — security / replay attack**

### Q: "You are building a SaaS platform and need to send webhooks to 500 enterprise customers. Design the delivery system."

> Three components: (1) **Registration service** — each customer registers their endpoint URL; you generate a unique 32-byte random secret per customer, store it, return it once. (2) **Fan-out queue** — when an internal event fires, create one delivery job per active subscription (fan-out). Use Kafka or SQS as the delivery queue; this decouples event creation from delivery and lets the delivery workers scale independently. (3) **Delivery workers** — consume from the queue; compute HMAC; POST to endpoint with 5-second timeout; write attempt to `webhook_delivery_log` (pending → success/failed). On non-2xx: schedule exponential backoff retry (5s, 25s, 125s). After N failures: move to DLQ, mark subscription as failing, and send a customer alert. Expose a delivery log UI and a manual replay API so customers can inspect and re-trigger missed events. ⭐ **Tier 2 — system design (provider side)**

---

## 🧾 TL;DR

> "A webhook is an HTTP POST the external system sends to your registered endpoint when an event fires — the reverse of polling. Three mandatory receiver properties: (1) HMAC-SHA256 signature verification in constant time (authenticity), (2) idempotency key on event_id with unique DB constraint (duplicate delivery), (3) return 200 immediately then process async (provider timeout prevention). DocuSign Connect delivers all envelope lifecycle events as webhooks. Build receivers that handle at-least-once delivery safely — same event, same outcome, every time."

---

## 🔗 Related Concepts

- **`04-idempotency.md`** — idempotency key pattern is mandatory for webhook receivers; the same event may arrive multiple times (at-least-once delivery)
- **`19-message-queues-kafka-rabbitmq.md`** — after receiving a webhook, enqueue for async processing; Kafka/SQS decouple the HTTP handler from business logic; see also the Pub/Sub Pattern section for fan-out to multiple subscribers
- **`35-retry-exponential-backoff-patterns.md`** — webhook providers use exponential backoff for retries; build the same pattern into your sender side
- **`13-security-pki.md`** — HMAC-SHA256 is a MAC (Message Authentication Code); PKI fundamentals cover the cryptographic primitives underneath
- **`26-websocket-real-time-communication.md`** — complementary push mechanism: WebSocket for bidirectional real-time browser connections vs webhook for server-to-server async event delivery

---

## 📚 Further Reading (Optional — after the note)

| Resource | What it adds | Link |
|---|---|---|
| **Stripe Webhooks — Best Practices** ⭐ | Industry-canonical guide: HMAC signing, idempotency pattern, event ordering caveats, endpoint registration and rotation, testing with Stripe CLI | stripe.com/docs/webhooks/best-practices |
| **DocuSign Connect Developer Guide** ⭐ | DocuSign-specific webhook system: envelope event types, HMAC signature verification setup, retry behavior, event filtering — directly relevant to DocuSign interview | developers.docusign.com/platform/webhooks |
| **GitHub Webhooks Documentation** | Provider-perspective reference: event catalog, delivery semantics, `X-Hub-Signature-256` header, redeliver API, ping event for endpoint validation | docs.github.com/en/webhooks |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| July 9, 2026 | Created as Concept 53. Full coverage: HMAC-SHA256 verification (receiver side, constant-time comparison explained), idempotency key pattern with race condition handling, replay attack prevention with timestamp window, async processing pattern, sender side with exponential backoff and DLQ, endpoint registration and secret rotation. DocuSign Connect context throughout. 6 Q&As (4 Tier 2 including concurrent duplicate delivery, async timeout footgun, replay attack, and full sender system design). |
