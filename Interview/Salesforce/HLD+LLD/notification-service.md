# Notification Service — HLD + LLD Combined Round

---

## 0.  Identity

| | |
|---|---|
| **Problem** | Notification Service — multi-channel (email, SMS, push) event-driven delivery |
| **Format** | HLD+LLD combined (Salesforce SMTS) |
| **Time budget** | 35 min LLD → 45 min HLD → 10 min buffer |
| **Frequency rank** | Confirmed real and recently asked (CodingKaro, Jan 2025: "Design a Notification Service" covering HLD+LLD+DB+patterns in one round) plus two adjacent hits (Push Notification System at APNS/FCM scale, Aug 2025; a notification sub-question inside a Zomato HLD, Feb 2025). A deep Aug 2026 re-research pass (see `research-findings-2026-08.md`) found this sits in a tied cluster with Rate Limiter, Job Scheduler, and Booking systems rather than being uniquely #1 — the original Roundz-sourced "#1 overall" claim could not be re-verified (Roundz is now paywalled). Still fully worth knowing — just not to the exclusion of the other clusters. |
| **Salesforce-specific angle** | Maps directly to **Platform Events** (Salesforce's Kafka-backed event bus). Multi-tenancy is the natural extension: 150K orgs sharing dispatch infrastructure. |

---

## 1.  Dual-Layer Map

> Build this in your head before writing either half. Every HLD box owns exactly one interface + N strategy implementations.

| HLD Box | LLD Class(es) | Interface Contract |
|---|---|---|
| **API Layer** | `NotificationController`, `NotificationRequest` (value object) | Entry point — validates and hands off to orchestrator |
| **Notification Service** (orchestrator) | `NotificationService` | Coordinates preference lookup → routing → dispatch |
| **Channel Router** | `ChannelRouter` (interface) + `DefaultChannelRouter` | `route(ChannelType): NotificationSender` — Strategy selection |
| **Dispatch Workers** | `NotificationSender` (interface) + `EmailSender`, `SmsSender`, `PushSender` | `send(NotificationRequest): void` — one impl per channel |
| **Notification Store** | `NotificationRepository` (interface) + `NotificationStatus` (enum) | Repository pattern — persists delivery history |
| **Preference Store** | `UserPreferenceService` (interface) + Redis-backed impl | `getPreferences(userId): UserPreferences` — cached lookup |

The zoom direction is always: **HLD box → `<<interface>>` → implementations.** Say this out loud when the interviewer asks "how would you implement the Channel Router?" — draw only that box's interface + impls, nothing else.

---

## 2.  LLD Half (target: 35 min)

### 2.1  Problem Statement

Design a system that sends notifications to users across multiple channels (email, SMS, push) when triggered by upstream events, respecting per-user, per-channel preferences and quiet hours.

### 2.2  Requirements

**Functional:**
- Send notifications via email (primary), SMS, push
- Respect per-channel opt-in/out + do-not-disturb hours
- Provide notification history per user
- Out of scope: in-app messaging, custom templates

**Non-Functional:**
- Extensible — adding a new channel = one new class, zero changes elsewhere
- Thread-safe — the same event can be dispatched by two threads (retry/redelivery)
- At-least-once delivery with idempotent sends (no duplicate SMS charges)

### 2.3  Class Design

#### 2.3.1  Deriving the classes (say this out loud, minutes 2-6)

Don't recite a class list from memory — derive it in front of them. Read each requirement, pull the noun, then justify why that noun deserves its own class instead of being a field on an existing one. **The justification is the scored part, not the class name.**

| # | Requirement (verbatim from 2.2) | Noun / verb extracted | Becomes | Why it earns its own type (and what breaks if you inline it) |
|---|---|---|---|---|
| 1 | "Send notifications via email, SMS, push" | noun: *notification* | **`NotificationRequest`** (data holder) | The thing being sent needs identity so it can be retried, deduped, and audited. If it were just loose method params (`send(userId, text, channel)`), you'd have no single object to attach an idempotency key to — and idempotency is a stated NFR. |
| 2 | same line — "via email, SMS, push" | verb: *send*, varying by channel | **`NotificationSender`** (interface) | This is the **variation point**. Three channels = three send algorithms. Inlining it as a field (`request.channelType` + `if/else` in one sender class) means every new channel edits that class — an OCP violation, and the `if/else` grows unbounded. Interface first, one impl per channel. |
| 3 | same line — "email, SMS, push" as a closed set | the *set* of channels | **`ChannelType`** (enum) | Closed, known-at-compile-time set with no behavior of its own → enum, not a class. If channels became user-definable at runtime (plugin model), this would flip to a class/registry — flag that as the trigger for change. |
| 4 | "Respect per-channel opt-in/out + do-not-disturb hours" | noun: *preferences* | **`UserPreferences`** (data holder) | Belongs to the **user**, not the **event**. One user has one preference set read across thousands of requests. Bolting `enabledChannels` onto `NotificationRequest` would duplicate the same preference data onto every request object and force a re-fetch per event — different lifetime, different owner, different class. |
| 5 | same line — the *act* of deciding who's opted in | verb: *respect / check* | **`UserPreferenceService`** (interface) | The lookup is a swappable capability (Redis-cached today, direct DB tomorrow, gRPC to a Preferences team service later). Interface keeps `NotificationService` from knowing where preferences live — DIP. |
| 6 | "Provide notification history per user" | verb: *provide* over persisted state | **`NotificationRepository`** (interface) | "History" implies a query surface over stored records, distinct from the in-flight request. Separate class because persistence is a different reason to change than dispatch logic — SRP. |
| 7 | "Adding a new channel = one new class" (NFR) | the *selection* of a sender | **`ChannelRouter`** (interface) | Careful with the justification here — the OCP guarantee comes from the injected `List<NotificationSender>` collapsed into a map, **not** from this class existing. Inline that map into the orchestrator and Slack is still zero edits. The router earns its place for two other reasons: it's the home for selection logic that grows later (channel fallback, per-tenant overrides, provider sharding), and it maps 1:1 onto the Fan-out Service when you zoom out to HLD — which matters in a combined round. |
| 8 | "At-least-once with idempotent sends" (NFR) | state of a send attempt | **`NotificationStatus`** (enum) | Terminal, behavior-free value → enum. See 2.5 for why this is *not* a State pattern. |

**The one-liner to say after the table:** *"So: one data holder for the event, one for preferences, and three interfaces — routing, preference lookup, and sending — because those are my three variation points."*

#### 2.3.2  Entity fields

```
NotificationRequest
  - eventId:    String        <- idempotency key
  - userId:     String
  - content:    String
  - channels:   List<ChannelType>
  - createdAt:  Instant

ChannelType (enum): EMAIL, SMS, PUSH

NotificationStatus (enum): PENDING, SENT, FAILED, SKIPPED

UserPreferences
  - userId:          String
  - enabledChannels:  Set<ChannelType>
  - quietHoursStart:  LocalTime
  - quietHoursEnd:    LocalTime
```

#### 2.3.3  Relationships — with the composition-vs-aggregation call made explicit

Saying "HAS-A" is only half an answer; the interviewer's follow-up is *"composition or aggregation?"* The distinction is **lifecycle ownership**, not syntax:

- **Composition** (UML filled diamond) — the whole *creates and owns* the part. Part dies with the whole. Part is never shared.
- **Aggregation** (UML hollow diamond) — the whole *holds a reference to* a part built elsewhere. Part outlives the whole and may be shared by several wholes.
- **Rule of thumb to say out loud:** *"If I `new` it inside the constructor, it's composition. If it arrives through the constructor, it's aggregation."*

| Relationship | Type | Composition or aggregation — and why that one |
|---|---|---|
| `EmailSender` — `NotificationSender` | **IS-A** (implements) | Neither — this is realization, not ownership. Justification is Liskov: the router invokes `.send()` through the interface and must never need to know the concrete type. |
| `NotificationService` — `ChannelRouter` | **HAS-A** → **aggregation** | Honest answer: **aggregation**, because the router is constructor-injected, not `new`-ed inside the service. It's tempting to call this composition since the service is useless without a router, but *"can't function without it"* describes a **required dependency**, not ownership — those are different things. It'd only be composition if `NotificationService` did `this.router = new DefaultChannelRouter(...)` internally, which would also destroy testability (no way to inject a mock router). |
| `DefaultChannelRouter` — `Map<ChannelType, NotificationSender>` | **HAS-A** → **composition of the map, aggregation of the senders** | Two different answers in one field, and saying so scores points. The `Map` object itself is created and owned by the router (composition — nobody else references that map instance). The `NotificationSender` values inside it are injected singletons shared with other components (aggregation — `EmailSender` survives the router being replaced). |
| `NotificationService` — `NotificationRepository` | **USES** (dependency) | Not HAS-A at all, even though it's a field. It's a **collaborator**, not a part: the service calls it and forgets it, holds no meaningful state on it, and swapping Postgres for Cassandra changes nothing but DI wiring. Use USES when the relationship is "calls methods on," HAS-A when it's "is structurally made of." |
| `NotificationRequest` — `List<ChannelType>` | **HAS-A** → **composition** | The list is created with the request and dies with it; no other object holds that list instance. Enum *values* are shared JVM-wide, but the collection holding them is exclusively the request's. |
| `UserPreferences` — `UserPreferenceService` | no direct relationship | Deliberate: `UserPreferences` is a dumb data holder with no back-reference to the service that loaded it. Adding one would create a cycle and drag persistence concerns into a value object. |

#### 2.3.4  ASCII class diagram — interfaces before implementations, always

```
                    NotificationService
                    - router: ChannelRouter
                    - preferenceService: UserPreferenceService
                    - repository: NotificationRepository
                    + notify(NotificationRequest): void
                             | uses
              +--------------+---------------+
              v                              v
    <<interface>>                    <<interface>>
    ChannelRouter                    UserPreferenceService
    + route(ChannelType):            + getPreferences(userId):
        NotificationSender               UserPreferences
              ^                              ^
              | implements                   | implements
    DefaultChannelRouter              RedisUserPreferenceService
    - senders: Map<ChannelType,
                    NotificationSender>
              | routes to
              v
    <<interface>>
    NotificationSender
    + getChannel(): ChannelType
    + send(NotificationRequest): void
              ^
              | implements
    +---------+-------------+
    |         |             |
EmailSender  SmsSender   PushSender
```

**Key invariant (same rule as the dual-zoom map in Section 1):** every box that fans out to multiple behaviors is an interface first — `ChannelRouter`, `UserPreferenceService`, and `NotificationSender` are all `<<interface>>` before any concrete class touches them.

#### 2.3.5  Follow-ups they will ask after this section — and your answers

| Their question | Your answer (one breath) |
|---|---|
| "Composition or aggregation between the service and the router?" | "Aggregation — it's injected, not constructed internally. Composition would mean the service owns its lifecycle, which would also kill mock-injection in tests." |
| "Why isn't `ChannelType` a class with a `send()` method on it?" | "That's the enum-with-behavior shortcut. It works for 3 fixed channels, but each channel then needs its own dependencies — SendGrid client, Twilio client — injected into an enum constant, which Java makes ugly and untestable. Separate sender classes keep dependency injection clean." |
| "Could `UserPreferences` just be fields on `NotificationRequest`?" | "Different lifetime and different owner — preferences are per-user and long-lived, requests are per-event and ephemeral. Merging them means re-fetching and re-serializing preference data on every single event." |
| "Why is `NotificationRepository` an interface if you only have one database?" | "Two reasons: it's the seam for testing without a DB, and Section 3 already plans a 30-day Postgres hot window with S3 archival — that second store lands behind this same interface with no change to the service." |
| "You have both a router and a service — isn't that over-engineering?" | "I'd push back on that one. Note *where* the OCP guarantee actually comes from — it's the injected `List<NotificationSender>` collected into a map, not the router class itself. I could inline that map into `NotificationService` and adding Slack would still be zero edits. So the router isn't carrying the extensibility claim. What it carries is: (a) a home for selection logic that *will* grow — channel fallback, per-tenant overrides, provider-shard picking — none of which belong in an orchestrator, and (b) a clean 1:1 mapping when we zoom out, since this class becomes the Fan-out Service in the HLD. It's four lines. The cost of keeping it is near zero; the cost of threading routing logic back out of the orchestrator later isn't." |
| "What if a notification needs to go to two users?" | "Today `NotificationRequest` is single-user by design so `userId` can be the Kafka partition key. Multi-recipient would be a separate fan-out step upstream that explodes one broadcast into N single-user requests — I'd keep this class single-user." |

### 2.4  Key Interfaces

```java
/**
 * Contract for every delivery channel.
 * Adding Slack = one new class implementing this. Zero changes to
 * NotificationService or ChannelRouter. Open-Closed Principle.
 */
public interface NotificationSender {
    ChannelType getChannel();
    void send(NotificationRequest request);
}
```

```java
/**
 * Strategy selector — decouples "which channel" from "how to send".
 */
public interface ChannelRouter {
    NotificationSender route(ChannelType type);
}
```

```java
public interface NotificationRepository {
    void save(NotificationRequest request, NotificationStatus status);
    List<NotificationRequest> findByUserId(String userId);
}
```

### 2.5  Design Decisions

**The question you must be ready for: "Isn't multi-channel fan-out just Observer?"**

Yes, partially — and the honest answer names both patterns instead of picking one and hoping the interviewer doesn't probe. One `NotificationRequest` firing to N channels is Observer-shaped ("multiple listeners reacting to one event" — the exact trigger phrase for Observer in the pattern-map). But *how* each channel actually sends is Strategy-shaped (swappable algorithm per `ChannelType`). This is **Observer meets Strategy**: the fan-out loop in `NotificationService.notify()` is the Observer half (iterate and notify all interested channels); `NotificationSender` is the Strategy half (each channel's send algorithm is interchangeable). Say this out loud — it pre-empts the follow-up instead of getting caught by it.

**Why not pure Observer (a `List<NotificationSender>` iterated with no router)?** Because Observer alone doesn't give you *selective* dispatch — you'd iterate every registered sender and each one would need its own internal check for "is this channel enabled for this user," duplicating that logic N times. The router centralizes the lookup: filter enabled channels once in `NotificationService`, then route only to those. Rejected because it pushes filtering logic into every sender implementation — violates DRY.

**Why not State pattern for `NotificationStatus`?** State pattern earns its cost when *transitions* carry behavior (e.g., a subscription's `CANCELLED` state rejects new charges). Here, `PENDING → SENT/FAILED/SKIPPED` is a one-shot terminal write with no transition logic attached — a plain enum is correct. Introducing a State class per status would be over-engineering for a value that's written once and never transitions again.

| Decision | Pattern Chosen | Alternative Considered | Why Rejected |
|---|---|---|---|
| One class per channel implementing `NotificationSender` | **Strategy** | Single class with an if/else on `ChannelType` | If/else means editing one growing class every time a channel is added — violates OCP; the class becomes a merge-conflict magnet as channels grow |
| `ChannelRouter` holds a `Map<ChannelType, NotificationSender>` via constructor injection | **Registry / DI** | The strongest alternative isn't a `switch` — it's **the same injected map inlined into `NotificationService`** | Be honest that the inlined map is *also* OCP-clean; the injected-list-to-map trick is what buys extensibility, not the extra class. The router is kept for cohesion (selection logic has somewhere to grow: fallback chains, per-tenant overrides, provider sharding) and for the clean LLD-class-to-HLD-service mapping in a combined round — not because the orchestrator would otherwise need editing. |
| `NotificationRepository` interface, not a direct DB call | **Repository** | Active Record — `NotificationRequest.save()` calls JDBC directly | Active Record works for CRUD-simple entities, but couples the domain object to a specific storage engine. Section 3's HLD half already flags a future move from Postgres (30-day hot window) to Cassandra/S3 archival — Active Record would mean rewriting `NotificationRequest` itself for that migration; Repository isolates the blast radius to one class |
| Failures in one channel are caught per-channel inside the fan-out loop, not propagated | **Fail-isolation (try/catch per iteration)** | Let exceptions propagate and fail the whole `notify()` call | Propagating means one dead SMS provider takes down email and push for that user too — unacceptable per NFR ("fail one channel without blocking others" is stated explicitly in Section 2.2) |

### 2.6  Visual — Object Interaction

```
Client → NotificationController.create(request)
             │
             ▼
       NotificationService.notify(request)
             │
             ├─▶ UserPreferenceService.getPreferences(userId)   [cache-first]
             │        └─▶ filters request.channels to enabled channels only
             │
             ├─▶ for each enabled channel:
             │        ChannelRouter.route(channel) ─▶ NotificationSender
             │        NotificationSender.send(request)
             │             │
             │             ├─ success ─▶ NotificationRepository.save(SENT)
             │             └─ failure ─▶ NotificationRepository.save(FAILED)  [isolated — doesn't stop other channels]
             │
             └─▶ return NotificationResponse{id, status}
```

### 2.7  Coding Skeleton

**Write in this order (interview-tested):** enum → interface → impl → factory/router → orchestrator.

```java
// 1. Enum first — no magic strings
public enum ChannelType { EMAIL, SMS, PUSH }

// 2. Interface before any implementation
public interface NotificationSender {
    ChannelType getChannel();
    void send(NotificationRequest request);
}

// 3. One implementation (write ONE live, mention the others follow the same shape)
public class EmailSender implements NotificationSender {
    @Override
    public ChannelType getChannel() { return ChannelType.EMAIL; }

    @Override
    public void send(NotificationRequest request) {
        // calls email provider client — omitted for brevity
    }
}

// 4. Router — Strategy registry, built once via DI
public class DefaultChannelRouter implements ChannelRouter {
    private final Map<ChannelType, NotificationSender> senders;

    public DefaultChannelRouter(List<NotificationSender> senderList) {
        this.senders = senderList.stream()
            .collect(Collectors.toMap(NotificationSender::getChannel, s -> s));
    }

    @Override
    public NotificationSender route(ChannelType type) {
        return senders.get(type);
    }
}

// 5. Orchestrator — the class you narrate live
public class NotificationService {
    private final ChannelRouter router;
    private final UserPreferenceService preferenceService;
    private final NotificationRepository repository;

    public void notify(NotificationRequest request) {
        UserPreferences prefs = preferenceService.getPreferences(request.getUserId());
        for (ChannelType channel : request.getChannels()) {
            if (!prefs.getEnabledChannels().contains(channel)) continue;
            try {
                router.route(channel).send(request);
                repository.save(request, NotificationStatus.SENT);
            } catch (Exception e) {
                repository.save(request, NotificationStatus.FAILED);
                // one channel's failure never stops the loop
            }
        }
    }
}
```

### 2.8  Concurrency — Making It Thread-Safe

**Shared fields:** `senders` map inside `DefaultChannelRouter` is built once at startup and never mutated — read-only after construction, so no lock needed (immutability > locking).

**What DOES need protection:** the idempotency check. If the same `eventId` is redelivered by two threads (retry/at-least-once redelivery), both could call `send()` concurrently.

> **Say this explicitly:** *"I'll guard against double-send with a `ConcurrentHashMap<String, Boolean>` idempotency cache keyed on `eventId:channel`, using `putIfAbsent` — that's an atomic check-and-set, so only one thread wins the race and proceeds to call the provider."*

```java
private final ConcurrentHashMap<String, Boolean> idempotencyCache = new ConcurrentHashMap<>();

public void send(NotificationRequest request, ChannelType channel) {
    String key = request.getEventId() + ":" + channel;
    if (idempotencyCache.putIfAbsent(key, true) != null) {
        return; // already sent — atomic check, no race window
    }
    router.route(channel).send(request);
}
```

### 2.9  "What Would You Do Differently?"

> *"In an interview I'd say: at real scale I wouldn't call the provider synchronously inside `send()` — I'd enqueue to a per-channel queue (SQS/Kafka) and let a separate worker pool call SendGrid/Twilio. That decouples my thread pool's latency from a slow third-party provider's latency — one SendGrid timeout shouldn't exhaust my dispatch threads."*

### 2.10  Interview Q&As

**Q: "How do you add a new channel like Slack?"**
> One new class `SlackSender implements NotificationSender`. Register it in the constructor list passed to `DefaultChannelRouter`. Zero changes to `NotificationService` or the router logic itself — Open-Closed Principle.

**Q: "What if `UserPreferenceService` is slow?"**
> Cache preferences in Redis with a short TTL (5 min). Cold miss hits the DB once per user per TTL window; hot path is sub-millisecond.

**Q: "Two threads dispatch the same event — what happens?"**
> `ConcurrentHashMap.putIfAbsent` on the idempotency key guarantees only one thread's `send()` call reaches the provider — atomic check-and-set, no explicit lock needed.

**Q: "Why Repository pattern instead of calling the DB directly from `NotificationService`?"**
> Single Responsibility — `NotificationService` orchestrates business logic; it shouldn't know whether history is stored in Postgres or Cassandra. Swapping storage engines touches one class, not the orchestrator.

**Q: "Why not an if/else chain instead of the router map?"**
> An if/else chain means editing `NotificationService` every time a channel is added — violates OCP. The map-based router isolates that lookup to one class that's built once and never touched again.

### 2.11  TL;DR — 30-Second Pitch

> "I'd model this with a `NotificationSender` Strategy interface — one implementation per channel — behind a `ChannelRouter` that's just a `Map<ChannelType, NotificationSender>` built via DI. The orchestrator, `NotificationService`, pulls user preferences, filters channels, and dispatches through the router, catching failures per-channel so one bad provider doesn't block the others. Idempotency is a `ConcurrentHashMap.putIfAbsent` keyed on `eventId:channel` to survive at-least-once redelivery. Adding a channel is one new class — zero changes anywhere else."

### 2.12  Patterns Used

- **Strategy** — `NotificationSender` + per-channel implementations
- **Repository** — `NotificationRepository` abstracts storage
- **Registry / Dependency Injection** — `ChannelRouter`'s internal map built once at startup

---

## 3.  HLD Half (target: 45 min)

### 3.1 Clarifying Questions (0–3 min)

| Question | Architectural Fork |
|---|---|
| "Scale — how many users, how many notifications per day?" | 10K/day → single service fine. 1B/day → need Kafka + sharded dispatch + multiple provider accounts. |
| "Is exactly-once delivery required, or is at-least-once acceptable?" | At-least-once → simple retry + idempotency cache. Exactly-once (e.g., billing alerts) → durable idempotency table + outbox pattern. |
| "Which channels — email only, or email + SMS + push?" | Email only → single dispatcher. Multi-channel → router + fan-out + per-channel provider limits become the bottleneck. |
| "Single-tenant or does this run across many Salesforce orgs?" | Single-tenant → no isolation needed. Multi-tenant (150K orgs) → per-org rate limits + `orgId` partition key on every table and queue. |

### 3.2 Requirements

**Functional (5):**
- Send notifications via email, SMS, push
- Respect per-user, per-channel preferences + quiet hours
- Provide notification history per user
- Trigger from upstream service events (e.g., record updated)
- Fan out to all configured channels in parallel

**Non-Functional (4):**
- Scale: 10M users, 100 notifs/user/day → **11.6K/sec baseline, ~35K/sec peak**
- Latency: P99 delivery < 30s
- Availability: 99.9% SLO
- Delivery guarantee: at-least-once with idempotent consumer-side dedup

### 3.3 Core Entities

| Entity | Nature |
|---|---|
| **NotificationRequest** | ephemeral — consumed off Kafka, not persisted long-term |
| **NotificationHistory** | append-only — permanent delivery record |
| **UserPreference** | transactional — small, relational, cache-friendly |
| **Outbox** | transactional (outbox pattern) — written in same DB transaction as the triggering event |
| **IdempotencyKey** | ephemeral — 24h TTL, auto-evicted |

### 3.4 Scale Estimation

- **Throughput:** 10M users × 100 notifs/day = 1B/day → 11.6K/sec baseline, **35K/sec peak** (3×)
- **Storage:** 500 bytes/record × 1B/day = 500 GB/day → **182 TB/year** (kills the "one Postgres table forever" answer — keep a 30-day hot window, archive the rest to S3/Parquet)
- **Provider ceiling:** a single SendGrid account realistically sustains low-thousands emails/sec — at 21K emails/sec peak (60% of 35K), **email is the hardest constraint**, not the easy one. Requires a sharded subuser pool + a second ESP for failover.

### 3.5 Architecture Diagram

Don't draw the final architecture immediately — draw the naive version, break it with a number, then fix it. The evolution *is* the answer; the final box diagram is just where you stop.

#### Stage 1 — Naive: synchronous inline dispatch

```
 +---------------------------------------------------+
 |               Upstream Services                   |
 |  Record Svc  |  Approval Svc  |  Case Svc         |
 +-------------------------+-------------------------+
                           | POST /v1/notify  (blocking)
                           v
              +--------------------------+
              |   Notification Service   |
              |  1. INSERT history row   |
              |  2. call SendGrid  -----------> 50ms  (email)
              |  3. call Twilio    -----------> 200ms (SMS, sequential)
              |  4. call FCM       -----------> 30ms  (push)
              +--------------------------+
```

**BREAKING POINT 1 — thread exhaustion (the quantified one).** Sequential provider calls cost 50 + 200 + 30 = **280ms of blocked thread time per notification**. A 200-thread pool therefore sustains 200 / 0.28s = **~714 notifications/sec**. We need **35,000/sec peak** — we are short by a factor of ~49. The pool saturates and inbound requests queue behind provider latency.

**BREAKING POINT 2 — partial delivery with no recovery.** If Twilio times out at 30s, email already went out but SMS never retries. The user is partially notified and there's no record of what still owes delivery.

**BREAKING POINT 3 — dual-write loss.** The history row is INSERTed and *then* providers are called. Crash in between = DB says "notification created," user got nothing.

**DECISION — how do upstream events reach us?**

| Option | Strength | Weakness | Verdict |
|---|---|---|---|
| Synchronous HTTP (Stage 1) | Simplest; caller gets an immediate ack | Caller blocks on provider health; ~714/sec ceiling; no retry or buffering | Rejected — breaks at 2% of required load |
| Async to one shared queue | Durable; decouples caller from providers | One queue for all channels: an SMS backlog delays unrelated emails; channels can't scale independently | Better, but a known bottleneck |
| **Kafka partitioned by `userId`** | 35K/sec is routine; per-user ordering; consumer groups scale horizontally | Extra infra; adds ~100ms | **Chosen** |

#### Stage 2 — Kafka ingest + per-channel queues + fail isolation

```
 +---------------------------------------------------+
 |               Upstream Services                   |
 +-------------------------+-------------------------+
                           | publish (or POST fallback)
                           v
            +-------------------------------+
            |   Kafka: notification-events  |
            |   partitioned by userId       |
            |   (per-user ordering)         |
            +---------------+---------------+
                            | consume (consumer group)
                            v
            +-------------------------------+        +----------------------------+
            |      Fan-out Service          |<------>| Redis: preference cache    |
            |  - load prefs (cache-first)   |        | 5 min TTL, ~95% hit rate   |
            |  - filter disabled channels   |        +----------------------------+
            |  - drop if in quiet hours     |
            |  - idempotency check (SETNX)  |
            +--+---------------+---------+--+
               |               |         |
               v               v         v
        +-----------+  +-----------+  +-----------+
        | SQS       |  | SQS       |  | SQS       |
        | email-q   |  | sms-q     |  | push-q    |
        +-----+-----+  +-----+-----+  +-----+-----+
              |              |              |
              v              v              v
        +-----------+  +-----------+  +-----------+
        | Email     |  | SMS       |  | Push      |
        | Workers   |  | Workers   |  | Workers   |
        +-----+-----+  +-----+-----+  +-----+-----+
              |              |              |
              v              v              v
        SendGrid pool     Twilio          FCM / APNs
        (sharded subusers)
              |              |              |
              +--------------+--------------+
                             v
              +--------------------------------+
              | Postgres: notification_history |
              | 30-day hot partitions          |
              |   older -> S3 / Parquet        |
              +--------------------------------+
```

**What each hop buys us:**
- **Kafka** absorbs the 3x peak burst; `userId` partitioning keeps one user's notifications ordered without global ordering cost.
- **Redis preference cache** keeps the hot path off Postgres — at 35K/sec, an uncached preference read per event would itself be 35K read QPS against the primary.
- **Per-channel SQS** is the fail-isolation boundary: enqueue is <5ms and never blocks on provider health, so a dead SMS provider cannot consume threads that email needs.
- **Per-channel worker pools** scale independently — email needs ~21K/sec of capacity, push needs far less.

**BREAKING POINT (Stage 2, and the one worth naming aloud) — the email provider ceiling.** At 35K/sec with ~60% email share, we need **~21,000 emails/sec**. A single SendGrid account realistically sustains low-thousands/sec, so we're over one account's ceiling by roughly an order of magnitude. Symptom: HTTP 429s from the provider and a growing `email-q` backlog with rising queue age. Mitigation already in the diagram: a **sharded pool of SendGrid subusers** plus **SES as a failover ESP**, with the worker pool round-robining across credentials and circuit-breaking a subuser that starts 429-ing.

**Remaining known gap (say it before they find it):** Stage 2 still publishes to Kafka *after* the DB write, so Breaking Point 3 (dual-write) survives. The fix is the **outbox pattern** — write the event to an `outbox` table inside the same transaction as the business write, and let a poller publish to Kafka. That's Section 3.9's data model, and I'd add it before going to production.

**Data flow in one sentence:** upstream event -> Kafka (partitioned by `userId`) -> Fan-out Service filters by cached preferences and dedups -> per-channel SQS -> channel workers call providers with retry/DLQ -> terminal status written to Postgres.

### 3.6 Deep Dive: Multi-Channel Fan-Out with Fail Isolation

**Why this is the riskiest component:** it's where SRP and fault isolation either hold or collapse under load — a naive synchronous fan-out lets one slow channel take down all channels.

**Options considered:**

| Option | Pros | Cons |
|---|---|---|
| Call providers synchronously inline in fan-out | Simple | One slow provider (e.g., SendGrid timeout) blocks the thread handling all 3 channels for that user |
| Enqueue per-channel to SQS, separate worker pools per channel | Provider latency fully decoupled; one channel's outage doesn't touch others | Extra hop (~5–50ms), more moving infra pieces |

**Decision:** per-channel SQS queues. At 35K notifs/sec, a 2s SendGrid timeout with inline calls would exhaust a 10-thread pool in 200ms — enqueue is <5ms and never blocks on provider health.

**Implementation sketch:** `fanout()` (LLD Section 2.7) loops channels and calls `handler.deliver()`, which just does `sqsClient.sendMessage(queueUrl, payload)`. The actual `sendGridClient.send(...)` call lives in a separate `EmailWorker` polling the queue — failures there trigger SQS's own retry/DLQ, not fan-out retry.

### 3.7 Trade-offs

**Trade-off 1: At-least-once vs Exactly-once delivery**
- **Chose:** at-least-once + idempotency cache (Redis `SETNX`, 24h TTL)
- **Gain:** simpler, cheaper, no distributed transaction needed
- **Lose:** requires every consumer to be idempotent
- **Failure mode if wrong:** Redis restart loses idempotency keys → user gets duplicate SMS → for a per-message-billed provider like Twilio, that's real dollars per duplicate at 35K/sec scale, and the user sees a spammy double-notification.

**Trade-off 2: Per-channel SQS queues vs single fan-out queue**
- **Chose:** per-channel
- **Gain:** SMS backlog never delays email; independent auto-scaling per channel
- **Lose:** more infra to operate (3 queues + 3 worker pools instead of 1)
- **Failure mode if wrong:** single shared queue means an SMS provider outage backs up the whole queue — email delivery (which was healthy) gets delayed behind stuck SMS messages, breaching the P99 < 30s SLO for a channel that had nothing wrong with it.

**Trade-off 3: 30-day Postgres hot window vs full history in one store**
- **Chose:** 30-day hot partitions in Postgres, archive older to S3/Parquet
- **Gain:** Postgres stays fast for the in-app inbox (the 99% use case)
- **Lose:** "show me a notification from 8 months ago" becomes an analytics query, not a live lookup
- **Failure mode if wrong:** keeping 182 TB/year in one Postgres instance means the primary can't vacuum effectively, write latency degrades across the board — including the hot-path 30-day inbox reads that matter for daily active users.

### 3.8 API Design

| Method | Path | Auth | Request | Response | Status Codes |
|---|---|---|---|---|---|
| POST | `/v1/notifications` | API Key (internal) | `{userId, eventType, content, channels}` | `{notificationId, status}` | 201, 400, 429 |
| GET | `/v1/notifications/{userId}` | JWT | `?cursor=&limit=20` | `{notifications[], nextCursor}` | 200, 403 |
| PUT | `/v1/users/{userId}/preferences` | JWT | `{enabledChannels, quietHours}` | `{userId, preferences}` | 200, 400 |

Primary inbound path is **Kafka, not REST** — `POST /v1/notifications` is only the fallback for callers that can't produce events.

### 3.9 Data Model

```sql
-- Append-only delivery record. This is the "notification history" FR.
CREATE TABLE notification_history (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id       UUID NOT NULL,              -- multi-tenancy: partition key
    user_id      UUID NOT NULL,
    event_id     VARCHAR(128) NOT NULL,      -- idempotency key from producer
    event_type   VARCHAR(50),                -- "record.updated", "approval.requested"
    content      TEXT,
    created_at   TIMESTAMPTZ DEFAULT now(),

    UNIQUE (org_id, event_id),               -- DB-level idempotency backstop
    INDEX idx_user_recent (org_id, user_id, created_at DESC)
) PARTITION BY RANGE (created_at);           -- 30-day hot partitions, older -> S3

-- Per-channel outcome. Separate from history because one event has N outcomes:
-- email can FAIL while SMS succeeds, and each has its own timestamp/retry count.
CREATE TABLE notification_delivery (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    notification_id   UUID NOT NULL REFERENCES notification_history(id),
    org_id            UUID NOT NULL,
    channel           VARCHAR(10) NOT NULL,  -- EMAIL | SMS | PUSH
    status            VARCHAR(10) NOT NULL,  -- PENDING | SENT | FAILED | SKIPPED
    attempt_count     SMALLINT DEFAULT 0,
    last_error        TEXT,
    provider_msg_id   VARCHAR(128),          -- for provider-side reconciliation
    updated_at        TIMESTAMPTZ DEFAULT now(),

    UNIQUE (notification_id, channel),       -- one row per channel per event
    INDEX idx_retry (status, updated_at) WHERE status = 'FAILED'
);

-- Small, relational, cache-backed. Read ~35K/sec, written rarely.
CREATE TABLE user_preferences (
    org_id             UUID NOT NULL,
    user_id            UUID NOT NULL,
    email_enabled      BOOLEAN DEFAULT TRUE,
    sms_enabled        BOOLEAN DEFAULT FALSE,
    push_enabled       BOOLEAN DEFAULT TRUE,
    quiet_hours_start  TIME,
    quiet_hours_end    TIME,
    quiet_hours_tz     VARCHAR(40),          -- IANA tz; quiet hours are local, not UTC
    updated_at         TIMESTAMPTZ DEFAULT now(),

    PRIMARY KEY (org_id, user_id)
);

-- Outbox: fixes the dual-write gap called out in Stage 2.
-- Written in the SAME transaction as the business change; poller publishes to Kafka.
CREATE TABLE outbox (
    id           BIGSERIAL PRIMARY KEY,
    org_id       UUID NOT NULL,
    aggregate_id UUID NOT NULL,              -- user_id
    payload      JSONB NOT NULL,
    status       VARCHAR(10) DEFAULT 'PENDING' CHECK (status IN ('PENDING','SENT','FAILED')),
    created_at   TIMESTAMPTZ DEFAULT now(),

    INDEX idx_pending (status, created_at) WHERE status = 'PENDING'
);
```

**Schema decisions worth saying out loud:**

| Decision | Why | What breaks otherwise |
|---|---|---|
| `notification_delivery` split from `notification_history` | One event fans out to N channels, each with its own status, retry count, and provider message ID | Cramming `email_status`/`sms_status`/`push_status` as columns on one row means adding Slack is a schema migration — the DB mirrors the same OCP violation the LLD avoided |
| `UNIQUE (org_id, event_id)` | Idempotency backstop below Redis | Redis restart loses dedup keys; without this, a replayed Kafka event bills a duplicate SMS |
| `PARTITION BY RANGE (created_at)` | 182 TB/year won't live in one hot table; drop/archive whole partitions cheaply | Unpartitioned, autovacuum can't keep up and the hot-path inbox query degrades for everyone |
| `quiet_hours_tz` stored, not pre-converted to UTC | "No SMS after 10pm" means 10pm *where the user is*; DST shifts the UTC offset twice a year | Storing a fixed UTC offset silently sends 9pm messages after a DST change |
| `org_id` on every table + leading every PK/index | Salesforce multi-tenancy — see 3.10 | Without it, no way to shard or rate-limit per tenant, and cross-tenant leakage is one missing WHERE clause away |
| `INDEX ... WHERE status = 'FAILED'` (partial index) | The retry sweeper only ever scans failures, which are <1% of rows | A full index on `status` wastes space and write throughput on the 99% SENT rows |

### 3.10 Salesforce Multi-Tenancy Angle

> *"Since this runs on Salesforce's shared infrastructure across 150K orgs, I'd add `orgId` as a partition key on the Kafka topic, the NotificationHistory table, and a per-org token bucket on the dispatch queue — so one org blasting 50,000 notifications doesn't starve dispatch capacity for every other org sharing the same worker pool."*

This also maps directly to **Platform Events** — Salesforce's real Kafka-backed event bus with at-least-once delivery. Saying this signals product-domain awareness, not just generic distributed-systems knowledge.

---

## 4.  Navigation Pivots — THIS Problem

**Opening Protocol (first 2 minutes — use verbatim, per `format.md` Section 2):**

> "Before I start — should I do LLD first or HLD first, or do you have a preference?"
> *(If no preference:)* "I'll start with LLD — class design, interfaces, concurrency. Then zoom out to the distributed system. I'll flag the transition explicitly so you can redirect me if you want more depth anywhere."

| Interviewer Says | What They Want | Your Move |
|---|---|---|
| "How would you implement the Channel Router?" | LLD zoom-in on that one HLD box | Draw `ChannelRouter` interface + `DefaultChannelRouter`'s map only — don't redraw the whole system |
| "How does this scale to 10M users?" | HLD zoom-out | "`NotificationSender` and its impls → this becomes the Dispatch Workers service; the router logic moves into the Fan-out Service" — say the promotion explicitly |
| "What if we add Slack tomorrow?" | Extensibility (OCP) at both levels | LLD: "one new class, zero existing code changes." HLD: "one new SQS queue + worker pool, one new routing entry" |
| "Two threads process the same event — what happens?" | LLD concurrency | `ConcurrentHashMap.putIfAbsent` atomic check — named field, named strategy |
| "What breaks first at scale?" | HLD bottleneck + threshold | Name SendGrid's realistic per-account ceiling (~low thousands/sec) vs our 21K/sec peak email share |
| "How does this relate to Salesforce's own products?" | Domain awareness | Platform Events — Kafka-backed bus, at-least-once delivery, same design philosophy |

---

## 5.  TL;DR — Dual-Level Pitch

At the class level, this is a Strategy pattern: `NotificationSender` per channel, selected by a `ChannelRouter` map, orchestrated by `NotificationService` with per-channel failure isolation and a `ConcurrentHashMap` idempotency guard. At the system level, those classes become services: the router and senders promote to a Fan-out Service that reads events off Kafka (partitioned by `userId`) and enqueues to per-channel SQS queues so one slow provider (SendGrid's realistic ceiling is the actual bottleneck at 35K/sec peak) never blocks another channel. The key trade-off is at-least-once delivery with idempotent consumers instead of exactly-once — cheaper and simpler, protected by a 24h-TTL dedup key. On Salesforce's shared infrastructure, I'd partition every queue and table by `orgId` so one tenant's notification spike can't starve the other 150K orgs.

---

##  Changelog

| Date | Change |
|---|---|
| Aug 2026 | File created — first problem in `Interview/Salesforce/HLD+LLD/`. Follows `solution-notes-standards.md` combined-round format. Adapted architecture, scale numbers, and deep-dive content from `Interview/DocuSign/r2-solutions/D3-notification-service.md`; LLD class structure follows the dual-zoom worked example in `format.md`. |
