# Notification Service — HLD + LLD Combined Round

---

## 0.  Identity

| | |
|---|---|
| **Problem** | Notification Service — multi-channel (email, SMS, push) event-driven delivery |
| **Format** | HLD+LLD combined (Salesforce SMTS) |
| **Time budget** | 35 min LLD → 45 min HLD → 10 min buffer |
| **Frequency rank** | ⭐ **#1 overall** — confirmed as BOTH the #1 HLD question (5+ mentions) AND the #1 LLD question (4+ mentions). Roundz SMTS report confirms it asked in the exact HLD→LLD combined format in the same 90-min round. |
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

```
NotificationRequest
  - eventId:    String        ← idempotency key
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

**Relationships:** `NotificationService` owns one `ChannelRouter` and one `UserPreferenceService`. `ChannelRouter` holds a `Map<ChannelType, NotificationSender>` — classic Strategy registry, not an if/else chain.

**ASCII Class Diagram — interfaces before implementations, always:**

```
                    NotificationService
                    - router: ChannelRouter
                    - preferenceService: UserPreferenceService
                    - repository: NotificationRepository
                    + notify(NotificationRequest): void
                             │ uses
              ┌──────────────┴───────────────┐
              ▼                               ▼
    <<interface>>                    <<interface>>
    ChannelRouter                    UserPreferenceService
    + route(ChannelType):            + getPreferences(userId):
        NotificationSender               UserPreferences
              ▲                               ▲
              │ implements                    │ implements
    DefaultChannelRouter              RedisUserPreferenceService
    - senders: Map<ChannelType,
                    NotificationSender>
              │ routes to
              ▼
    <<interface>>
    NotificationSender
    + getChannel(): ChannelType
    + send(NotificationRequest): void
              ▲
              │ implements
    ┌─────────┼─────────────┐
    │         │             │
EmailSender  SmsSender   PushSender
```

**Key invariant (same rule as the HLD dual-zoom map in Section 1):** every box that fans out to multiple behaviors is an interface first — `ChannelRouter` and `NotificationSender` are both `<<interface>>` before any concrete class touches them.

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

| Decision | Pattern | Why |
|---|---|---|
| One class per channel implementing `NotificationSender` | **Strategy** | Behavior (how to send) is runtime-swappable based on `ChannelType`. Interviewer probe: "add Slack tomorrow" → one new class, zero existing code touched. |
| `ChannelRouter` holds a `Map<ChannelType, NotificationSender>` built via constructor injection | **Registry / DI** | Avoids an if/else chain that violates OCP — new channel means editing the router otherwise |
| `NotificationRepository` interface, not a direct DB call | **Repository** | `NotificationService` doesn't know or care if storage is Postgres or Cassandra — SRP: persistence logic isolated |
| Failures in one channel are caught per-channel, not propagated | **Fail-isolation** | SMS provider outage must not block the email send in the same fan-out — SRP again, one channel's failure is one channel's problem |

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

```
[Upstream Services] ──▶ [Kafka: notification-requests] ──▶ [Fan-out Service]
                          (partitioned by userId)                 │
                                                                   ├─▶ [Redis: UserPreference cache, 5m TTL]
                                                                   │
                              ┌────────────────────────────────────┼────────────────────────┐
                              ▼                                    ▼                          ▼
                     [SQS: email-queue]                  [SQS: sms-queue]           [SQS: push-queue]
                              │                                    │                          │
                              ▼                                    ▼                          ▼
                     [Email Worker Pool]                 [SMS Worker Pool]          [Push Worker Pool]
                     → SendGrid (sharded)                → Twilio                   → FCM/APNs
                              │                                    │                          │
                              └──────────────┬─────────────────────┴──────────────────────────┘
                                             ▼
                                  [Postgres: NotificationHistory]
                                  (30-day hot partitions; older → S3/Parquet)
```

**Data flow:** upstream event → Kafka (durable, partitioned by `userId` for per-user ordering) → Fan-out Service checks preferences (Redis cache) → enqueues to per-channel SQS (decouples our latency from provider latency) → workers call the provider with an idempotency check → status written to Postgres.

**Breaking point:** a single SendGrid account tops out at low-thousands emails/sec; our 21K/sec peak email share **exceeds one account's realistic ceiling** — observable symptom: 429 rate-limit responses from the provider, queue backlog growing. This is why the design shards across multiple SendGrid subusers with a second ESP (SES) as failover, not a single account.

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

### 3.9 Salesforce Multi-Tenancy Angle

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
