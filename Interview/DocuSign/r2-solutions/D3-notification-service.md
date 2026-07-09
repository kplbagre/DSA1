# D3 — Design a Real-Time Notification Service

> **Read `solution-notes-standards.md` FIRST.** This file is a 60-minute interview answer framework.
> Its structure mirrors DELIVERY-RECIPE: clarify (5 min) → estimate (5 min) → HLD (15 min) → deep dives (15 min) → trade-offs (8 min) → Q&A (7 min).
> **Say this out loud** before your interview — don't just read it.

---

## 📚 Prerequisites — Study These First

Before reading the solution, make sure you can explain the bolded items below from memory.

| Concept | File in `SystemDesignConcepts/` | Why you need it for this question |
|---|---|---|
| **Outbox / CDC pattern** | `Foundations/Data-Fundamentals/07-cdc-outbox.md` | The triggering microservice must atomically write its DB record AND publish the event to Kafka — the outbox pattern prevents the "payment succeeded but event never published" failure mode |
| **Idempotency** | `Foundations/Concurrency-and-Consistency/04-idempotency.md` | At-least-once Kafka delivery means the notification worker may consume the same event twice — idempotency key (idempotency_key + channel) in the sent_notifications table prevents duplicate emails |
| **Message queues (Kafka + SQS)** | `Core-Architecture/Service-Communication/19-message-queues-kafka-rabbitmq.md` | Kafka as the durable event bus; SQS as the per-channel delivery queue — know why two queue layers, when to use dead-letter queues, and partition key design |
| **Push notifications fan-out** | `Core-Architecture/Service-Communication/46-push-notifications-fanout.md` | APNs and FCM have completely different token formats, payload sizes, and failure semantics — know the token lifecycle and how to handle token expiry |
| **Feed and fan-out** | `Patterns/DeepDive/03-feed-and-fanout.md` | One event → multiple channels (email + SMS + push) is a fan-out — know when to use pull-on-read vs push-on-write for channel delivery |
| **Retry / exponential backoff** | `Core-Architecture/Resilience-and-Fault-Tolerance/35-retry-exponential-backoff-patterns.md` | Provider failure (SendGrid down) triggers retry with backoff + jitter — know the formula and how the dead-letter queue captures exhausted retries |
| **Caching fundamentals** | `Foundations/Performance-and-Scale/03-caching.md` | User channel preferences are read on every notification — cache with short TTL to avoid DB lookup per delivery, invalidate on preference update |

---

## 🎯 What Is This System?

**In plain English:** A notification service is a standalone system that receives events from other microservices ("payment failed", "document signed", "new comment") and delivers messages to users across multiple channels — email, SMS, and push notification — based on each user's preferences, exactly once, with retry on failure.

**Real-world examples:**

| System / Company | What they built |
|---|---|
| **Amazon SNS** | AWS's fan-out pub/sub to email, SMS, HTTP, SQS, Lambda |
| **Twilio** | Programmable SMS, voice, and email APIs (used by Airbnb, Uber, Lyft) |
| **SendGrid** | Transactional and marketing email at scale (3B emails/day) |
| **Firebase Cloud Messaging (FCM)** | Google's push notification service for Android and iOS |
| **OneSignal** | Multi-channel push, email, SMS, in-app — with segmentation |
| **Knock.fyi / Courier** | Notification infrastructure platforms for developer teams |

**Core user journey:** Billing service publishes a `payment_failed` event to Kafka → Notification Service consumes it → looks up user's channel preferences (email: on, SMS: on, push: off) → sends email via SendGrid and SMS via Twilio, exactly once, with exponential-backoff retry if either channel fails.

**Why it's hard to build at scale:** Fan-out across channels with different latency and reliability guarantees; deduplication (the same Kafka event must not trigger 3 emails if the consumer retries after a crash); channel routing by user preference must be evaluated per-event; and a SendGrid outage must not block SMS delivery.

---

## Section 0 — Question Identity Card

| | |
|---|---|
| **Question** | **Design a Real-Time Notification Service** (multi-channel: email, SMS, push notifications) |
| **Interview Type** | **Type A — System Design** (Infrastructure: fan-out, delivery guarantees, retry logic, channel coordination) |
| **Confirmed or Likely** | 🔶 Likely (DesignGurus + InterviewQuery both list as standalone; distinct from billing/Kafka fanout) |
| **Concept notes prerequisite** | `07-cdc-outbox.md` (outbox pattern for reliable event publishing), `04-idempotency.md` (exactly-once delivery semantics) |
| **DocuSign-specific angle** | Notifications are critical for e-signature workflows: "Document ready for your signature," "You've been asked to sign," "Document fully signed," "Your signature is contested" — must be reliable (no lost notifications), multi-channel (email for formal, SMS for urgent, push for mobile), and respect user preferences (opt-in/out per channel). |
| **Time budget** | 5 min clarify → 5 min estimate → 15 min HLD → 15 min deep dive → 8 min trade-offs → 7 min Q&A buffer |

---

## Section 1 — 🚀 The One-Sentence Opener

> "Before I design, let me clarify the scope — especially around channels (which ones?), delivery guarantees (at-least-once or exactly-once?), and latency SLOs (how fast do notifications need to arrive?)..."

Then immediately pivot to Section 2 (clarifying questions).

---

## Section 2 — 🔍 Clarifying Questions Script (Minutes 0–5)

**Q: "Which notification channels do we support — email, SMS, push, or all three?"**
- Why ask: Each channel has different infrastructure (email via SendGrid/SES, SMS via Twilio, push via Firebase/Apple APNs). Supporting all three is 3× the complexity.
- If email only → simple service; single provider
- If all three → fan-out service; must coordinate across three parallel paths

**Q: "What's the delivery guarantee — at-least-once (user may see duplicate notifications) or exactly-once (user sees it once)?"**
- Why ask: At-least-once is easier (simple retry logic); exactly-once requires idempotency keys stored in a database.
- If at-least-once → acceptable for most use cases (push, SMS); user can ignore duplicates
- If exactly-once → need idempotency table; adds latency + complexity

**Q: "What's the latency SLO — do users need to see the notification within 1 second, 1 minute, or is background delivery OK?"**
- Why ask: Real-time (< 1s) requires in-memory queues + synchronous delivery; background (minutes) allows batching + cheaper infrastructure.
- If real-time → synchronous delivery path; SQS FIFO or Kafka with low lag
- If background → batch delivery (every 5 minutes); cheaper

**Q: "Do we need to respect user preferences — can users opt out of SMS but stay opted in for email?"**
- Why ask: Per-channel opt-in/out requires a preferences table; adds a lookup on every notification.
- If yes → preferences table required
- If no → simpler (all-or-nothing opt-in)

**Q: "What's the scale — how many users, how many notifications per user per day?"**
- Why ask: Drives throughput, sharding strategy, and channel selection (SMS at 1M notifs/day is cheap; at 1B/day becomes expensive).

**Q: "Are notifications event-driven (triggered by user actions) or batch (scheduled digest emails)?"**
- Why ask: Event-driven = synchronous + Kafka; batch = scheduled jobs + cron.
- If event-driven → Kafka → fan-out service
- If batch → cron job + batch processor

---

## Section 3 — 📋 Requirements (Functional + Non-Functional)

**Functional Requirements:**
- System should send notifications to users via email (primary), SMS (secondary), and push notifications (tertiary)
- Notifications are triggered by events from upstream services (document signed, payment received, etc.)
- System should respect user preferences (opt-in/out per channel, do-not-disturb hours)
- System should provide a notification history (users can view past notifications)
- Out of scope: In-app messaging, webhook delivery, SMS/email template customization (assume templates are pre-defined)

**Non-Functional Requirements:**
- Scale: 10M users, 100 notifications per user per day = 1B notifications/day (~11.6K notifs/sec baseline, ~35K peak)
- Latency: P99 notification delivery < 30 seconds (email can be slower than push)
- Availability: 99.9% SLO (9 hours downtime/year)
- Delivery guarantee: **At-least-once** (acceptable to resend notifications; consumer idempotency handles duplicates)
- Durability: Notifications are queued durably (SQS/Kafka) — no loss even if service restarts
- Multi-channel coordination: Fan-out to all configured channels in parallel; fail one channel without blocking others
- Rate limiting: Max 100 notifications per user per hour (avoid spam)
- Compliance: GDPR (user can request deletion of notification history), CAN-SPAM (the US law requiring commercial emails to include an unsubscribe link and honest sender address; violations risk fines and email provider blacklisting)

---

## Section 3.5 — 🗂️ Core Entities (~2 minutes)

> **Say this out loud:** "Before I sketch the architecture, let me name the key data objects the system manages."

| Entity | What it represents | Storage |
|---|---|---|
| **NotificationRequest** | An incoming request to send a notification — event type, recipient, payload, source service | Kafka / outbox (ephemeral) |
| **NotificationHistory** | Permanent record of every sent notification — channel, status, timestamp, retry count | PostgreSQL |
| **UserPreference** | User's opt-in/out settings per channel and do-not-disturb hours | PostgreSQL |
| **Outbox** | DB-side event queue for guaranteed-delivery pattern — written in the same transaction as the triggering event | PostgreSQL (outbox pattern) |
| **IdempotencyKey** | Deduplication guard — prevents sending the same notification twice on retry | Redis (short TTL) / PostgreSQL |

**Key relationships:**
- An upstream event creates a `NotificationRequest` → fan-out to each channel produces one `NotificationHistory` row per channel
- `UserPreference` is checked before dispatch — if user opted out of email, skip the email channel
- `Outbox` ensures the event is not lost if the service crashes between receiving the request and publishing to Kafka (dual-write problem solved)
- `IdempotencyKey` is indexed on `(user_id, event_id, channel)` — the same event_id is never delivered twice to the same user on the same channel

---

## Section 4 — 🔢 Scale Estimation (Minutes 5–10)

**Traffic:**
- DAU: 10M users
- Notifications/day: 100 per user = 1B notifications/day
- Notifications/sec: 1B ÷ 86,400 = 11.6K notifs/sec baseline
- Peak (3×): 35K notifs/sec
- Per-channel breakdown (assume): 60% email, 30% SMS, 10% push
  - Email: 21K notifs/sec baseline
  - SMS: 10.5K notifs/sec baseline
  - Push: 3.5K notifs/sec baseline

**Provider limits (typical):**
- SendGrid: up to 100K emails/sec (plenty)
- Twilio: up to 10K SMS/sec (need to shard at 10K+ or use multiple accounts)
- Firebase Cloud Messaging: up to 30K push/sec (plenty)

**Storage:**
- Per notification record: ~500 bytes (user_id, channel, message, status, timestamp)
- Notifications/year: 1B × 365 = 365B notifications = 182 GB/year
- At 5 years: 910 GB (fits in one Postgres instance; archive after 1 year to S3)

**Bandwidth:**
- Inbound (event ingestion): 35K notifs/sec × 2 KB = 70 MB/sec
- Outbound (to email/SMS providers): 35K notifs/sec × 1 KB = 35 MB/sec

**Key conclusions:**
- At 35K notifs/sec, **single Kafka broker is sufficient** (typical throughput ~100K msgs/sec); add 3 brokers for HA
- At 21K emails/sec, **SendGrid is fine** (capacity is 100K/sec)
- At 10.5K SMS/sec, **single Twilio account is at the limit**; might need shard by region
- At 182 GB/year, **single Postgres instance handles easily**; archive after 1 year to cold storage

---

## Section 5 — 🔄 Requirements Variation Table ⭐ Key Differentiator

| Requirement | Small scale (1K notifs/day) | Large scale (1B notifs/day) | Impact on design |
|---|---|---|---|
| **Throughput** | 0.01 notifs/sec | 35K notifs/sec peak | Single-thread processing → Kafka sharding; single DB → read replicas |
| **Channels** | Email only (simplest) | All three (email + SMS + push) | Direct API call per channel → fan-out service; webhook fanout |
| **Delivery guarantee** | At-least-once OK | Exactly-once required (legal/payments) | Simple retry → idempotency table + deduplication |
| **Latency SLO** | 1 hour acceptable | P99 < 30s required (time-sensitive events) | Batch processing → real-time streaming (Kafka) |
| **User preferences** | All-or-nothing opt-in | Per-channel + per-notification-type opt-in | Simple flag → preferences table + lookup on every notification |
| **Rate limiting** | Global limit | Per-user per-hour limit | Static threshold → token bucket + Redis |

---

## Section 8 — 🌐 API Design (Before HLD)

> **Why here:** Define the external contract before drawing the architecture — the HLD shows how these endpoints are implemented. For Type A, this is concise (3–5 minutes); the architecture is the primary deliverable.

### 🧠 How to Derive These Endpoints

Notification service has two kinds of API surface: **inbound** (other services trigger notifications) and **outbound management** (users control their preferences and view notification history). Most of the "inbound" path is event-driven — Kafka, not REST.

"Document Service tells Notification Service: user X just signed a document" → this is a Kafka event, not a REST call. Document Service publishes `document.signed` to Kafka; Notification Service consumes it. No REST endpoint exists for this path. But some callers can't use Kafka — they're simple services or external integrators. For them: `POST /v1/notifications` as a REST fallback, protected by API key (internal only).

"User views their notification history in-app" → `GET /v1/notifications/{user_id}`. This is the inbox. Cursor pagination by `created_at DESC, id DESC` — millions of historical notifications, offset would be O(N).

"User opts out of SMS notifications" → `PUT /v1/users/{user_id}/preferences`. Full-replace PUT (not PATCH) because preferences are a small, flat config object — easier to reason about. The quiet hours field is a business constraint: if `quiet_hours: {start: "22:00", end: "08:00"}` and a notification would fire at 11 PM, it's held until 8 AM.

Validation check: the fan-out to FCM/APNs/Twilio is internal — no REST endpoint. Delivery status (SENT, FAILED, BOUNCED) is updated via webhooks from the providers (Twilio calls your webhook URL when SMS is delivered). No REST endpoint needed from the user's perspective.

### Core Endpoints

| Method | Path | Auth | Request | Response | Status Codes |
|---|---|---|---|---|---|
| POST | `/v1/notifications` | API Key (internal) | `{user_id, event_type, content, channels: ["email", "sms"]}` | `{notification_id, status}` | 201, 400, 429 |
| GET | `/v1/notifications/{user_id}` | JWT Bearer | `?cursor=&limit=20` | `{notifications: [{id, type, content, sent_at, read_at}], next_cursor}` | 200, 403, 404 |
| PUT | `/v1/users/{user_id}/preferences` | JWT Bearer | `{email_enabled, sms_enabled, push_enabled, quiet_hours: {start, end, tz}}` | `{user_id, preferences}` | 200, 400 |

**Primary inbound path (Kafka — not REST):**

```
Topic: notification-requests
Key: user_id (for partition locality)
Value:
{
  "event_type": "document.signed",
  "user_id": "...",
  "payload": { "document_id": "...", "signer_name": "..." },
  "channels": ["email", "push"],
  "priority": "high"
}
```

### 🔍 Endpoint Stories

**`POST /v1/notifications`** is the REST fallback for callers that can't produce Kafka events. It's internal-only: protected by API key or mTLS (mutual TLS — both caller and server present certificates; stronger than API keys because identity is cryptographically proven). Rate limited at 100 notifications/hour per `user_id` — excess is silently dropped (returning `201 Created` for the request even if the actual notification is suppressed). The caller doesn't need to know about user-level rate limits — that's the notification service's responsibility.

**`GET /v1/notifications/{user_id}`** is the inbox endpoint. The `read_at` field is null until the user opens the notification — the client patches it by calling `PATCH /v1/notifications/{id}/read` (a simple endpoint not listed in the main table because it's just a timestamp write). The probe: "What if a user has 10 million historical notifications?" Cursor pagination handles it. Secondary index on `(user_id, created_at DESC)` makes the cursor query O(log N + page_size). Partition the table by `user_id` range at 1M notifications per partition.

**`PUT /v1/users/{user_id}/preferences`** carries `quiet_hours.tz` — the user's timezone, not UTC offset. Why timezone name (`"Asia/Kolkata"`) instead of offset (`+05:30`)? Timezones handle DST transitions automatically; fixed offsets break twice a year in DST-observing regions. The Fan-out Service converts `quiet_hours.start` + timezone to UTC before comparing to `NOW()`. DocuSign ships globally — timezone-aware quiet hours matter.

**The Kafka topic `notification-requests`** is the real primary API for well-behaved services. Partitioned by `user_id`, so all notifications for a given user land on the same consumer — ordering is preserved per user. High-priority events (`"priority": "high"`) bypass quiet hours; low-priority ones wait. Priority is a field in the event payload, not a separate Kafka topic — simpler to manage one topic with filtered consumer logic than two topics with separate consumer groups.

---

## Section 6 — 🏗️ High-Level Architecture (Minutes 10–25)

### 🎨 Visual — Notification Service Architecture (3-Stage Evolution)

```
── Stage 1: Direct Sync HTTP ─────────────────────────────────────

Upstream services call the Notification Service synchronously.
The Notification Service calls SendGrid + Twilio in sequence.
One incoming request → one thread → caller blocked until all providers respond.

 ┌────────────────────────────────────────────────────────────┐
 │                   Upstream Services                        │
 │   Document Service   │  Payment Service  │  Order Service  │
 └──────────────────────────┬─────────────────────────────────┘
                            │  POST /v1/notify
                            ▼
                   ┌─────────────────────┐
                   │   Notification Svc  │
                   │  1. INSERT into     │
                   │     notification_   │
                   │     history         │
                   │  2. call SendGrid   │←── 50ms (email)
                   │  3. call Twilio     │←── 200ms (SMS, sequential)
                   └─────────────────────┘
                         │           │
                         ▼           ▼
                    SendGrid      Twilio
                     (email)       (SMS)

BREAKING POINT 1: If Twilio is down, the Notification Service HTTP
   response blocks for the full 30s timeout, then returns 500.
   The caller's workflow is interrupted. Email was sent but SMS was
   never retried — notification is partially delivered with no audit.

BREAKING POINT 2: Sequential channel calls: email 50ms + SMS 200ms
   = 250ms blocked per notification. At 35K/sec, thread pool
   saturates. New inbound requests queue behind slow providers.

BREAKING POINT 3: No retry logic. Any transient network failure
   (Twilio 503, SendGrid timeout) = notification silently lost.
```

**DECISION — WHICH event transport from upstream?**

| Option | Strength | Weakness | Verdict |
|---|---|---|---|
| Sync HTTP (direct call, wait for providers) | Simple; caller gets immediate ack | Caller blocks on provider failure; no retry; no buffering; thread pool saturates at 35K/sec | ❌ Brittle at scale |
| Async to single shared queue | Decoupled; durable; decouples caller | One queue: SMS backlog at Twilio's 10K/sec limit delays emails; channels can't scale independently | ⚠️ Step forward, but bottleneck |
| Kafka (partitioned by user_id) | High-throughput; ordering guarantee per user; fan-out service separates concerns | Extra infra; ~100ms additional latency | ✅ Best |

> 📖 Full: **`SystemDesignConcepts/Core-Architecture/Service-Communication/19-message-queues-kafka-rabbitmq.md`**

```
── Stage 2: Kafka + Per-Channel SQS + Retry ──────────────────────

Upstream services publish events to Kafka. Fan-out Service consumes
events, checks user preferences, and routes to per-channel SQS queues.
Per-channel delivery workers call providers with exponential backoff retry.

 ┌──────────────────────────────────────────────────────┐
 │                  Upstream Services                   │
 └──────────────────────┬───────────────────────────────┘
                        │  POST /v1/notify
                        ▼
               ┌──────────────────────┐
               │   Notification Svc   │
               │  1. INSERT notif_    │
               │     history          │
               │  2. publish to Kafka │ ← direct publish (no outbox yet)
               └───────────┬──────────┘
                           │
                           ▼
               ┌───────────────────────┐
               │   Kafka               │
               │   notifications.events│
               │   partitioned by      │
               │   user_id             │
               └──────────┬────────────┘
                          │  consume
                          ▼
               ┌──────────────────────┐
               │    Fan-out Service   │
               │  check preferences   │
               │  check rate limit    │
               └──┬──────────┬─────┬──┘
                  │          │     │
                  ▼          ▼     ▼
           ┌──────────┐ ┌────────┐ ┌────────┐
           │ email-   │ │ sms-   │ │ push-  │
           │ queue    │ │ queue  │ │ queue  │
           │ (SQS)    │ │ (SQS)  │ │ (SQS)  │
           └────┬─────┘ └───┬────┘ └───┬────┘
                │           │          │
                ▼           ▼          ▼
          SendGrid       Twilio     Firebase
         (+ retry)      (+ retry)   (+ retry)

BREAKING POINT 1: Dual-write problem. If Notification Service crashes
   after INSERT into notification_history but before publishing to Kafka,
   the row exists in the DB but no event reaches the Fan-out Service.
   The notification is silently lost — DB shows "created," no email sent.

BREAKING POINT 2: No idempotency. If the SQS consumer crashes mid-delivery
   and SQS re-delivers the same message, the delivery worker calls SendGrid
   again. User receives a duplicate email. At 35K/sec with any consumer
   restart, this happens thousands of times per day.
```

**DECISION — WHICH fan-out queue strategy?**

| Option | Strength | Weakness | Verdict |
|---|---|---|---|
| Single shared queue (all channels together) | Simple; one queue to manage | SMS backlog (Twilio 10K/sec limit) delays emails behind it; channels can't scale independently | ❌ Bottleneck |
| In-memory queue (in-process, no broker) | Zero latency; no extra infra | Lost on service restart; no durability at 35K/sec | ❌ Not durable |
| Per-channel SQS queues (email-queue, sms-queue, push-queue) | Each channel scales independently; SMS failure doesn't delay emails; easy per-channel debugging | 3 queues to manage (minor ops overhead) | ✅ Best |

> 📖 Full: **`SystemDesignConcepts/Core-Architecture/Service-Communication/19-message-queues-kafka-rabbitmq.md`**

**DECISION — WHICH retry pattern for provider calls?**

| Option | Strength | Weakness | Verdict |
|---|---|---|---|
| No retry | Simplest code | Any transient failure = notification permanently lost; unacceptable at 1B notifs/day | ❌ Unacceptable |
| Fixed-interval retry (retry every N seconds) | Simple | Thundering herd: all failed requests retry simultaneously when provider recovers, re-failing it | ⚠️ Risky at scale |
| Exponential backoff + jitter (1s, 2s, 4s … 64s + random offset per retry) | Spreads retry load over time; handles transients; DLQ catches permanent failures after 7 retries | Slightly more complex; max 127s delay before DLQ | ✅ Best |

> 📖 Full: **`SystemDesignConcepts/Core-Architecture/Resilience-and-Fault-Tolerance/35-retry-exponential-backoff-patterns.md`**

```
── Stage 3: Outbox + Redis + DLQ (Production) ────────────────────

Fixes both Stage 2 breaking points:
- Outbox pattern: INSERT notification_history + INSERT outbox in one
  DB transaction → Kafka publish is atomic, never dual-write lost.
- Redis idempotency keys: consumer restart → key already set → skip.
- DLQ: permanent failures captured for manual ops review.

 ┌────────────────────────────────────────────────────────┐
 │                   Upstream Services                    │
 └─────────────────────────┬──────────────────────────────┘
                           │  POST /v1/notify
                           ▼
              ┌────────────────────────────┐
              │      Notification Svc      │
              │  @Transactional {          │
              │    INSERT notification_    │
              │    history                 │
              │    INSERT outbox (PENDING) │ ← atomic pair
              │  }                         │
              └─────────────┬──────────────┘
                            │
                            ▼
              ┌─────────────────────────────┐
              │      Outbox Processor       │
              │  polls outbox every 100ms   │
              │  → publish to Kafka         │
              │  → mark outbox SENT         │
              └─────────────┬───────────────┘
                            │
                            ▼
              ┌─────────────────────────────┐
              │  Kafka  notifications.events│
              │  (partitioned by user_id)   │
              └──────────┬──────────────────┘
                         │  consume
                         ▼
              ┌──────────────────────────────┐
              │       Fan-out Service        │
              │◀────▶ Redis                  │
              │  rate limit (INCR+TTL 1hr)   │
              │  prefs cache (5-min TTL)     │
              └──┬──────────────┬───────┬────┘
                 │              │       │
                 ▼              ▼       ▼
          ┌──────────┐  ┌────────┐  ┌────────┐
          │ email-   │  │ sms-   │  │ push-  │
          │ queue    │  │ queue  │  │ queue  │
          │ (SQS)    │  │ (SQS)  │  │ (SQS)  │
          └────┬─────┘  └───┬────┘  └───┬────┘
               │            │           │
               ▼            ▼           ▼
          ┌─────────────────────────────────────┐
          │         Delivery Workers            │
          │  ◀────▶ Redis idempotency check     │
          │  key: notif:{event_id}:{uid}:{ch}   │
          │  24h TTL; hit = skip; miss = send   │
          └────┬───────────┬──────────┬──────────┘
               │           │          │
               ▼           ▼          ▼
          SendGrid      Twilio     Firebase
         (+ backoff)  (+ backoff)  (+ backoff)
               │           │          │
               └─────┬─────┘──────────┘
                     │  after 7 retries
                     ▼
              ┌──────────────────┐
              │       DLQ        │
              │  manual ops      │
              │  review + retry  │
              └──────────────────┘

KEY INVARIANT:
   Outbox: notification_history INSERT + outbox INSERT are in ONE
   DB transaction. Kafka publish happens async (100ms lag). If Kafka
   is down, outbox row stays PENDING and processor retries forever —
   no notification is ever silently lost.

   Kafka partitions by user_id: all notifications for a user arrive
   in order. "Document rejected" never precedes "Document ready to
   sign" — prevents confusing race conditions at multi-signer workflows.

   Redis idempotency key (event_id + user_id + channel, 24h TTL):
   consumer restart → same SQS message redelivered → key already
   exists → skip delivery → no duplicate email to user.
```

**DECISION — WHICH reliable event publish strategy?**

| Option | Strength | Weakness | Verdict |
|---|---|---|---|
| Direct Kafka publish (no outbox) | Fewer moving parts | Dual-write risk: crash between DB write and Kafka publish = notification silently lost; no recovery path | ❌ Dual-write gap |
| Event sourcing (all state is events, no DB row) | True single source of truth; no dual-write | Overkill; slow read queries (must replay events); large storage; complex for this problem | ❌ Overkill |
| Outbox pattern (atomic DB write, async Kafka publish via processor) | Atomic: both DB rows committed together; processor retries forever; no dual-write | 100ms publish lag; outbox processor is one more component to operate | ✅ Best |

> 📖 Full: **`SystemDesignConcepts/Foundations/Data-Fundamentals/07-cdc-outbox.md`**

**DECISION — WHICH idempotency storage?**

| Option | Strength | Weakness | Verdict |
|---|---|---|---|
| No idempotency check | Simple | Consumer restart → same SQS message redelivered → duplicate notification to user | ❌ No dedup |
| Postgres idempotency_keys table | Durable; survives Redis restart; exact record | 1–5ms lookup + extra write per delivery; at 35K/sec this is a hot write path | ⚠️ Viable for legal/payment exactly-once flows |
| Redis TTL key (event_id + user_id + channel, 24h TTL) | Sub-ms lookup; auto-expires; handles 35K/sec easily; EBS-backed Redis survives restarts | Warm-up period after cold Redis start (mitigated by Postgres fallback on miss) | ✅ Best |

> 📖 Full: **`SystemDesignConcepts/Foundations/Concurrency-and-Consistency/04-idempotency.md`**

**Data flow walkthrough (say this out loud):**

**Flow 1 — Event ingestion (Stage 3: outbox pattern):**
1. Document Service finishes signing, calls `POST /v1/notify`
2. Notification Service opens a DB transaction:
   - INSERT into `notification_history` (user sees this in notification history API)
   - INSERT into `outbox` table (payload = event JSON, status = PENDING)
   - Both committed atomically — if either INSERT fails, neither row is written
3. Outbox Processor (background job, every 100ms) polls `outbox WHERE status = 'PENDING'`
4. For each PENDING row: publish to Kafka `notifications.events` (keyed by user_id for ordering)
5. If Kafka confirms → UPDATE `outbox.status = SENT`
6. If Kafka is down → outbox row stays PENDING → processor retries next cycle; no notification lost

**Flow 2 — Kafka to fan-out:**
1. Fan-out Service consumes from `notifications.events` partition (keyed by user_id → ordered per user)
2. Check `user_preferences` (Redis cache, 5-min TTL; fallback to Postgres on miss)
3. Check rate limit: Redis INCR on `notif:ratelimit:{user_id}`, expire 1 hour — if > 100, drop silently
4. Check quiet hours (user's timezone-converted do-not-disturb window)
5. Enqueue to `email-queue`, `sms-queue`, `push-queue` — whichever channels user has enabled

**Flow 3 — Channel delivery with dedup + retry:**
1. Delivery worker pops message from SQS queue (visibility timeout: 30s)
2. Check Redis idempotency key `notif:{event_id}:{user_id}:{channel}`:
   - Key exists → already delivered; return immediately (no duplicate send)
   - Key absent → proceed
3. Call provider (SendGrid / Twilio / Firebase)
4. If success → SET Redis idempotency key (24h TTL); UPDATE `notification_status`
5. If transient failure (5xx, timeout, 429) → throw exception → SQS re-enqueues with exponential backoff
6. After 7 retries (max 127s total) → SQS moves message to DLQ for manual ops review

---

## Section 7 — 🔬 Core Component Deep Dives (Minutes 25–40)

### Deep Dive 1: Outbox Pattern for Reliable Event Publishing

**Why this is the most critical component:**
Without the outbox, notifications can be lost silently. The Notification Service inserts into `notification_history` and publishes to Kafka, but one can fail without the other. Result: DB thinks the notification was sent (user sees it in history), but Kafka never got the event (so no email is actually sent). The outbox pattern moves the "publish to Kafka" operation **into the same database transaction** as the insert, guaranteeing atomicity.

**Options considered:**

| Option | Pros | Cons |
|---|---|---|
| **Option A: Direct Kafka publish** (synchronous, no outbox) | Simple; fewer moving parts | Dual-write problem: if Kafka fails, notification is in DB but not published. If DB fails after Kafka commit, Kafka has the event but DB doesn't. |
| **Option B: Outbox pattern** (async processor) | Atomic DB + Kafka publish; retries are automatic (processor retries); at-least-once delivery | Slightly more complex; requires a background processor (one more service to operate); 100ms lag before Kafka publish |
| **Option C: Event sourcing** (all state is events) | True single source of truth; easy audit trail; no dual-write | Overkill for this problem; slow queries (must replay events); large storage overhead |

**Decision: Option B (Outbox pattern).**

Because it balances atomicity (DB write + Kafka publish are transactional) with operational simplicity (no event sourcing complexity). At-least-once delivery is acceptable; the idempotency layer handles duplicates.

**Implementation sketch:**

```java
public class NotificationService {
    private final JdbcTemplate jdbc;
    private final KafkaTemplate kafkaTemplate;

    /**
     * Create a notification and publish event (outbox pattern).
     * Both the notification insert and the outbox insert happen in ONE transaction.
     */
    @Transactional
    public void createNotification(String userId, NotificationEvent event) {
        // Step 1: Insert notification_history (user can query this via API)
        jdbc.update(
            "INSERT INTO notification_history (id, user_id, event_type, message, created_at, status) " +
            "VALUES (?, ?, ?, ?, ?, ?)",
            UUID.randomUUID(), userId, event.getType(), event.getMessage(),
            Instant.now(), "CREATED"
        );

        // Step 2: Insert outbox entry (marker for async processor)
        // This is IN THE SAME TRANSACTION as Step 1.
        // If the transaction commits, BOTH rows are in the database.
        // If it rolls back, NEITHER row exists. No dual-write inconsistency.
        jdbc.update(
            "INSERT INTO outbox (aggregate_id, event_type, payload, status, created_at) " +
            "VALUES (?, ?, ?, ?, ?)",
            userId, event.getType(), event.toJson(), "PENDING", Instant.now()
        );
        // Transaction auto-commits here.

        // Step 3: Return to caller immediately.
        // The outbox processor will publish to Kafka asynchronously.
    }
}

public class OutboxProcessor {
    private final JdbcTemplate jdbc;
    private final KafkaTemplate kafkaTemplate;

    /**
     * Background job: poll outbox table, publish to Kafka, mark as SENT.
     * Runs continuously (e.g., every 100ms or triggered by events).
     * If Kafka is down, processor retries indefinitely.
     * The notification is durably stored (doesn't get lost).
     */
    @Scheduled(fixedRate = 100)  // every 100ms
    public void processPendingEvents() {
        // Poll for unsent outbox entries
        List<OutboxEntry> pending = jdbc.query(
            "SELECT * FROM outbox WHERE status = 'PENDING' ORDER BY created_at ASC LIMIT 100",
            (rs, rowNum) -> new OutboxEntry(
                rs.getLong("id"),
                rs.getString("aggregate_id"),
                rs.getString("event_type"),
                rs.getString("payload")
            )
        );

        for (OutboxEntry entry : pending) {
            try {
                // Publish to Kafka
                kafkaTemplate.send(
                    "notifications.events",
                    entry.getAggregateId(),  // key = user_id (for partitioning)
                    entry.getPayload()       // value = event JSON
                ).get(5, TimeUnit.SECONDS);  // wait for Kafka to confirm

                // Mark as SENT only after Kafka confirms
                jdbc.update(
                    "UPDATE outbox SET status = 'SENT', sent_at = ? WHERE id = ?",
                    Instant.now(), entry.getId()
                );
            } catch (Exception e) {
                // Kafka failed or timed out
                // DO NOT mark as SENT. Retry next cycle.
                // The outbox entry will stay PENDING forever (until Kafka recovers).
                logger.warn("Failed to publish outbox entry {}", entry.getId(), e);
                // Could add a retry counter here; after N retries, move to FAILED.
            }
        }
    }
}
```

**Why this deep dive matters:**
- The outbox pattern is the industry-standard solution to the dual-write problem
- At 35K notifs/sec, you must have a durable mechanism to avoid losing notifications
- The 100ms outbox processing delay is acceptable (notifications still arrive within seconds)

---

### Deep Dive 2: Multi-Channel Fan-Out with Rate Limiting

**Why this is the most critical component:**
Notifications go to multiple channels (email, SMS, push) in parallel. Each channel has different SLOs, limits, and failure modes. Email is cheap but slow (50ms); SMS is expensive and has per-account limits (Twilio: ~10K SMS/sec); push is fast but optional. At 35K notifs/sec, you need to **shard** the fan-out (partition by user_id) and **rate-limit** per user (prevent spam: max 100 notifs/hour) and per **channel** (respect SMS provider limits).

**Options considered:**

| Option | Pros | Cons |
|---|---|---|
| **Option A: Fan-out inline** (synchronous) | Simple; no queue; user gets immediate feedback | Blocks on slowest channel (email); if SMS fails, whole request fails; no retry mechanism |
| **Option B: Fan-out async to SQS** (one queue for all channels) | Reliable; retry built-in; decoupled | All channels share one queue; SMS backlog blocks emails; hard to debug which channel failed |
| **Option C: Fan-out async to SQS (per-channel queues)** | Each channel scales independently; failures are isolated; easy to debug | Slightly more complex (3 SQS queues) |

**Decision: Option C (per-channel SQS queues).**

Because SMS is the constraint (10K SMS/sec limit), and we don't want SMS backlog to affect email delivery. Each channel's SQS queue is consumed by its own consumer fleet, so they scale independently.

**Implementation sketch:**

```java
public class FanOutService {
    private final AmazonSQS sqs;
    private final RedisTemplate redis;
    private final UserPreferencesService preferences;

    /**
     * Fan out one incoming notification to multiple channels.
     * Called by the Kafka consumer listening to notifications.events.
     */
    public void fanOutNotification(String userId, NotificationEvent event) {
        // Step 1: Check user preferences
        UserPreferences prefs = preferences.get(userId);
        boolean emailEnabled = prefs.isEmailEnabled();
        boolean smsEnabled = prefs.isSmsEnabled();
        boolean pushEnabled = prefs.isPushEnabled();

        // Step 2: Rate limiting (per user, per hour)
        String rateLimitKey = "notif:ratelimit:" + userId;
        Long currentCount = redis.opsForValue().increment(rateLimitKey);
        if (currentCount == 1) {
            // First notification of this hour; set TTL
            redis.expire(rateLimitKey, Duration.ofHours(1));
        }
        if (currentCount > 100) {
            // User has exceeded quota; skip all channels
            logger.info("User {} exceeded notification quota. Dropping notification.", userId);
            return;
        }

        // Step 3: Enqueue to per-channel SQS queues
        if (emailEnabled) {
            sendToQueue(
                "notification-service-email-queue",
                new EmailMessage(userId, event)
            );
        }
        if (smsEnabled) {
            sendToQueue(
                "notification-service-sms-queue",
                new SmsMessage(userId, event)
            );
        }
        if (pushEnabled) {
            sendToQueue(
                "notification-service-push-queue",
                new PushMessage(userId, event)
            );
        }
    }

    private void sendToQueue(String queueName, Object message) {
        try {
            sqs.sendMessage(
                queueName,
                ObjectMapper.writeValueAsString(message)
            );
        } catch (Exception e) {
            // Log but don't throw; if SQS is down, other channels still work
            logger.error("Failed to enqueue message to {}", queueName, e);
        }
    }
}

/**
 * Email delivery consumer (runs in a separate fleet).
 */
public class EmailDeliveryConsumer {
    private final SendGrid sendGrid;
    private final JdbcTemplate jdbc;

    @SqsListener("notification-service-email-queue")
    public void deliverEmail(EmailMessage message) throws Exception {
        String userId = message.getUserId();

        // Step 1: Idempotency check
        String idempotencyKey = "email:" + userId + ":" + message.getEventId();
        Optional<String> cached = redis.opsForValue().get(idempotencyKey);
        if (cached.isPresent()) {
            // Already delivered; skip
            logger.info("Email already sent (idempotent). Skipping user {}.", userId);
            return;
        }

        // Step 2: Get user's email
        String email = userService.getEmail(userId);

        // Step 3: Send via SendGrid
        SendGrid.Request request = new SendGrid.Request();
        request.setMethod(SendGrid.Method.POST);
        request.setEndpoint("mail/send");
        request.setBody(buildEmailBody(email, message.getContent()));

        try {
            SendGrid.Response response = sendGrid.api(request);
            if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
                // Success
                redis.opsForValue().set(idempotencyKey, "sent", Duration.ofHours(24));
                updateNotificationStatus(userId, message.getEventId(), "SENT");
            } else if (response.getStatusCode() >= 500) {
                // Server error; retry
                throw new Exception("SendGrid returned " + response.getStatusCode());
            } else {
                // Client error (4xx); don't retry
                logger.error("SendGrid returned {}. Dropping message.", response.getStatusCode());
                updateNotificationStatus(userId, message.getEventId(), "FAILED");
            }
        } catch (Exception e) {
            // Retry: SQS will re-queue after visibility timeout
            logger.error("Failed to send email to user {}. Will retry.", userId, e);
            throw e;  // rethrow to trigger SQS retry
        }
    }
}
```

**Why this deep dive matters:**
- Fan-out to multiple channels requires handling different SLOs (email slow, SMS fast)
- Per-channel queues allow independent scaling (don't let SMS backlog block emails)
- Rate limiting prevents spam and respects user preferences
- Idempotency ensures duplicates don't result in duplicate emails

---

### Deep Dive 3: Retry Logic with Exponential Backoff + Dead-Letter Queue

**Why this is the most critical component:**
At 35K notifs/sec, you're making 35K API calls per second to SendGrid, Twilio, Firebase. Network failures are inevitable (1-2% of calls may fail due to timeouts, rate limits, or provider transients). Without retry logic, 350-700 notifications are silently lost every second. With smart retry logic (exponential backoff), transient failures recover automatically, and only permanent failures go to the DLQ.

**Options considered:**

| Option | Pros | Cons |
|---|---|---|
| **Option A: No retry** (send once, fail silently) | Simplest | Loses notifications on transient failures (unacceptable) |
| **Option B: Fixed retry** (retry N times at fixed interval) | Simple | Thundering herd: when all failed requests retry at the exact same fixed interval, they simultaneously hammer the provider the moment it starts recovering — often causing it to fail again in a cycle |
| **Option C: Exponential backoff + jitter** (1s, 2s, 4s, 8s, 16s, 32s, 64s, + random jitter) | Smooth retry pattern; spreads load over time; recovers from transients | Slightly more complex |
| **Option D: Adaptive retry** (watch provider response codes, adjust backoff dynamically) | Optimal for provider health | Overkill; too complex for this problem |

**Decision: Option C (Exponential backoff + jitter).**

Because it handles the vast majority of transient failures without overwhelming the provider. After 7 retries (max 127s + jitter), we give up and move to the DLQ.

**Implementation sketch:**

```java
public class RetryableEmailDelivery {
    private static final int[] BACKOFF_MILLIS = {1000, 2000, 4000, 8000, 16000, 32000, 64000};
    private static final Random random = new Random();

    public void sendWithRetry(String userId, String email, String content) throws Exception {
        int retryCount = 0;
        Exception lastException = null;

        while (retryCount <= BACKOFF_MILLIS.length) {
            try {
                // Attempt to send
                sendViaProvider(email, content);
                return;  // success
            } catch (Exception e) {
                lastException = e;

                if (isTransientError(e)) {
                    // Transient (network error, 5xx, rate limit) → retry
                    if (retryCount < BACKOFF_MILLIS.length) {
                        long backoffMs = BACKOFF_MILLIS[retryCount];
                        long jitterMs = random.nextLong(backoffMs / 2);  // ±50% jitter
                        long sleepMs = backoffMs + jitterMs;

                        logger.info("Transient error for user {}. Retrying in {}ms (attempt {})",
                            userId, sleepMs, retryCount + 1);
                        Thread.sleep(sleepMs);
                        retryCount++;
                    } else {
                        // Max retries exceeded; give up
                        logger.error("Max retries exceeded for user {}. Moving to DLQ.", userId);
                        sendToDLQ(userId, email, content, lastException);
                        return;
                    }
                } else {
                    // Permanent error (4xx, invalid email) → don't retry
                    logger.error("Permanent error for user {}. Not retrying.", userId, e);
                    updateNotificationStatus(userId, "FAILED");
                    return;
                }
            }
        }
    }

    private boolean isTransientError(Exception e) {
        // 5xx = transient
        // Connection timeout = transient
        // Rate limit (429) = transient
        // 4xx (except 429) = permanent
        if (e instanceof HttpClientErrorException) {
            HttpClientErrorException httpError = (HttpClientErrorException) e;
            return httpError.getStatusCode().value() == 429;  // rate limit
        }
        if (e instanceof SocketTimeoutException || e instanceof ConnectTimeoutException) {
            return true;
        }
        if (e instanceof HttpServerErrorException) {
            return true;
        }
        return false;
    }

    private void sendToDLQ(String userId, String email, String content, Exception error) {
        // Enqueue to dead-letter queue for manual inspection
        deadLetterQueue.send(new DLQMessage(userId, email, content, error.toString()));
    }
}
```

**Why this deep dive matters:**
- Retry logic must distinguish transient (retry) from permanent (fail) errors
- Exponential backoff prevents thundering herd (all failed requests retrying simultaneously)
- Jitter spreads retries over time (smooth load on provider)
- DLQ catches permanent failures for manual inspection (ops can investigate + manually retry if appropriate)

---

## Section 9 — 🗄️ Data Model

### Core Tables

```sql
CREATE TABLE notification_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    event_type VARCHAR(50),  -- "document.signed", "payment.received"
    message TEXT,  -- user-facing message
    channels VARCHAR(20)[] DEFAULT ARRAY[]::VARCHAR[],  -- which channels received it
    read_at TIMESTAMP,  -- NULL until user opens notification
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_user_created (user_id, created_at DESC)
);

CREATE TABLE user_preferences (
    user_id UUID PRIMARY KEY,
    email_enabled BOOLEAN DEFAULT TRUE,
    sms_enabled BOOLEAN DEFAULT FALSE,
    push_enabled BOOLEAN DEFAULT TRUE,
    
    quiet_hours_start TIME,  -- e.g., "22:00"
    quiet_hours_end TIME,    -- e.g., "08:00"
    quiet_hours_tz VARCHAR(40),  -- user's timezone (for quiet hour calc)
    
    unsubscribed_at TIMESTAMP,  -- opt-out timestamp (GDPR: can request this)
    
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE notification_status (
    notification_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    event_type VARCHAR(50),
    
    email_status VARCHAR(20),  -- 'PENDING', 'SENT', 'FAILED', 'SKIPPED'
    sms_status VARCHAR(20),
    push_status VARCHAR(20),
    
    email_sent_at TIMESTAMP,
    sms_sent_at TIMESTAMP,
    push_sent_at TIMESTAMP,
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_user_status (user_id, created_at DESC)
);

-- Outbox table for reliable Kafka publishing (dual-write prevention)
CREATE TABLE outbox (
    id BIGSERIAL PRIMARY KEY,
    aggregate_id UUID NOT NULL,  -- user_id
    event_type VARCHAR(50),
    payload TEXT,  -- JSON event
    status VARCHAR(20) CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP,
    
    INDEX idx_pending (status, created_at)
);

-- Idempotency keys for exactly-once delivery semantics
CREATE TABLE idempotency_keys (
    key VARCHAR(128) PRIMARY KEY,  -- event_id + user_id
    response TEXT,  -- stored response
    status VARCHAR(20),  -- 'IN_PROGRESS', 'SUCCESS', 'FAILED'
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE,
    
    INDEX idx_expires (expires_at)
);
```

### Key Schema Decisions

- **notification_history.channels**: Array of channel names (PostgreSQL ARRAY type) for quick filtering ("show me all email notifications")
- **notification_status**: Separate table to track per-channel delivery status (email might FAIL, but SMS succeeds)
- **outbox table**: For the outbox pattern; processor polls this table and publishes to Kafka
- **idempotency_keys**: Fast lookup to avoid duplicate deliveries; expired keys are cleaned up daily
- **quiet_hours_tz**: User's timezone; server converts "quiet_hours = 22:00 to 08:00 in Asia/Kolkata" to UTC for consistent server-side logic

---

## Section 10 — ⚠️ Trade-Offs + Failure Modes (Minutes 45–52)

### Trade-off 1: Delivery Guarantee (At-Least-Once vs Exactly-Once)

**Chose:** At-least-once (with idempotency on the consumer side = effectively exactly-once from the user's perspective).

**Gain:** Simpler to implement (outbox pattern is standard); no coordination between services needed; consumer idempotency is isolated.

**Lose:** Outbox processor may publish the same event twice to Kafka if it crashes after Kafka confirms but before marking the outbox entry as SENT. Consumer gets the event twice, but idempotency deduplication handles it.

**Failure mode if wrong:** If you try to guarantee exactly-once end-to-end (transactional outbox + Kafka + idempotency), you add 40% latency and 2-3× complexity. At-least-once + idempotency is simpler and sufficient. **Business impact:** The 40% latency overhead means signing request notifications arrive ~700ms later at median — for DocuSign this means a signer who clicks the envelope link from the web UI before the email is processed sees a "no pending signatures" state and abandons the workflow, creating support tickets and signing delays that the 40% complexity tax was supposed to prevent.

---

### Trade-off 2: Per-Channel Queues vs Single Fan-Out Queue

**Chose:** Per-channel SQS queues (email-queue, sms-queue, push-queue).

**Gain:** Email consumer can process 21K emails/sec without being blocked by SMS backlog; SMS queue can have its own scaling policy (respect Twilio limits).

**Lose:** Slightly more complexity (manage 3 queues instead of 1); more operational overhead.

**Failure mode if wrong:** If you use a single queue and SMS provider is slow, emails get delayed behind SMS messages. Users see a 5-minute delay in receiving emails, which feels broken. **Business impact:** For DocuSign: a signer receives a signing request, expects the email confirmation, but it's stuck behind a slow SMS backlog — the signer refreshes their inbox, believes the signing failed, clicks "Sign Now" again from the web portal, and DocuSign now has a duplicate envelope event to reconcile; the sender panics that the document was sent twice and calls support.

---

### Trade-off 3: Synchronous Preferences Lookup vs Cached Preferences

**Chose:** Redis cache (5-minute TTL) + fallback to DB.

**Gain:** At 35K notifs/sec, 35K preference lookups/sec would hit the DB hard. Redis cache at 100 microseconds per lookup is feasible.

**Lose:** Stale preferences (up to 5 minutes); if user disables SMS at 12:00 PM, they might get 2-3 SMS messages until cache expires.

**Failure mode if wrong:** If you do synchronous DB lookup per notification, DB becomes the bottleneck (typically 10-20 notifs/sec max for a single DB). You'll need read replicas and caching anyway. **Business impact:** At DocuSign's notification volume (1M+ notifications/day), synchronous DB preference lookups saturate the database at ~1K notifications/minute — during a bulk-send event (a customer sends 50K envelopes at once), the notification queue backs up for hours, signers receive signing request emails an hour late, the customer's campaign deadline is missed, and they file an SLA complaint.

---

## Section 11 — 🔐 DocuSign-Specific Depth (Minutes 52–57)

**Why this question is on the DocuSign R2 interview:**

Notifications are critical to DocuSign's product. When a document is ready for signature, DocuSign must notify the signer — via email (formal), SMS (urgent), or push (on-mobile users). If a notification is lost, the signer never signs, the customer's workflow breaks, and customer support gets a ticket. Non-repudiation also requires notifications: "We notified you on 3 June at 2 PM" must be provable (audit trail).

**DocuSign-specific angles your answer must address:**

1. **Multi-signer coordination**: When a document has 5 signers, notifications must arrive in order. Signer 1 gets notified → signs → Signer 2 gets notified (not before). Kafka partitioning by user_id + ordered processing ensures this.

2. **Audit trail**: Every notification is logged (when sent, to which channel, success/failure). For legal compliance, DocuSign keeps these logs forever. GDPR allows GDPR right-to-deletion, but audit logs of **sent** notifications are exempt (they're evidence of compliance).

3. **Quiet hours + business context**: DocuSign customers are global enterprises. A notification sent at 3 AM to a Tokyo office worker is rude. Quiet hours must respect the user's timezone.

4. **Compliance (CAN-SPAM, GDPR)**: Every email notification must have an unsubscribe link (CAN-SPAM requirement). User must be able to opt out per channel without opting out entirely.

5. **High availability**: Notifications are on the critical path. If the notification service is down, customers can't sign documents. 99.9% SLO is non-negotiable.

6. **SLA-based queue isolation — from Docusign Engineering Blog (mid-2025):**

The existing design uses per-channel queues (email / SMS / push). Docusign's own engineering blog reveals they also isolate by **SLA class** — a completely different dimension of isolation:

| Queue Class | SLA Target | What goes in this queue |
|---|---|---|
| **Live Queue** | P95 < 15 minutes | User-facing real-time requests — "You have a document to sign" |
| **Bulk Queue** | Flexible SLA | Batch jobs — nightly reminder digests, bulk send of 50K envelopes |
| **Workflow Queue** | Per-workflow SLA | Orchestrated multi-step workflows — sequential signing chains, conditional routing |

**Why this matters — head-of-line blocking.** If a bulk import of 50K envelopes generates 50K notifications and they enter the same queue as a live signing request, the live request waits behind 50,000 messages. A signer sees a 10-minute delay receiving their email.

**Fix: Two-tier priority model.** Inbound events carry `"priority": "high"` (live signing) vs `"priority": "bulk"` (batch). Fan-out Service routes to the SLA-appropriate queue. Live queue consumers are a larger, always-warm fleet. Bulk queue consumers auto-scale.

Your per-channel queues remain — they handle the *channel* dimension. SLA queues handle the *priority* dimension. Full topology for production: `email-high`, `email-bulk`, `sms-high`, `push-high`. For MVP: at minimum separate `high` and `bulk` before channel split.

7. **CAP theorem position for notifications:**

Notifications are **AP** (availability over consistency). Staleness is acceptable — a signer receiving their email 30 seconds later than the exact moment of status change is not legally material. Contrast with signed document records (CP). State this explicitly: "For this service I choose AP — a duplicate notification is recoverable (user ignores it); a notification that never arrives is not."

**Your answer should include:**

> "Notifications are event-driven via Kafka. When a document is ready for the next signer, the Document Service publishes an event. The Notification Service consumes it, checks the signer's preferences (cached in Redis), respects quiet hours (converted to the user's timezone), and fans out to the user's enabled channels (email, SMS, push) via separate SQS queues. Each channel retries with exponential backoff on transient failures. Idempotency keys prevent duplicate emails (if the Kafka consumer restarts). The entire flow is audited: every notification delivery attempt is logged to notification_status for compliance. If a notification fails permanently, it goes to a dead-letter queue for manual inspection by support."

> "For multi-signer workflows, Kafka partitions notifications by signer_id, ensuring all notifications for signer John are processed in order. If signer #2 rejects the document, a new notification is sent back to signer #1 (in sequence), and the audit trail shows the full signing attempt history."

---

## Section 12 — 🔬 Where the Interviewer Will Probe (Minutes 57–60)

### Tier 1 — Surface Probe (Do you understand async systems?)

**Q: "What happens if a user opts out of SMS after we've already enqueued the notification to the SMS queue but before we've sent it?"**

> The SMS queue consumer checks user preferences again (Redis cache + DB fallback) before actually calling Twilio. If the user unsubscribed, the consumer skips delivery and marks the notification as 'SKIPPED' instead of 'SENT'. This is safe because we don't modify the queue — we just skip processing for that user.

### Tier 2 — Deep Probe (Do you understand failure modes?)

**Q: "What if Twilio's SMS API is down for 1 hour? Will users get spammed with notifications when it comes back online, or do we have backpressure?"**

> Good question. The SMS queue is durable (SQS), so notifications accumulate while Twilio is down. When Twilio comes back online, the SMS consumer starts processing the backlog. With exponential backoff, the first message will retry immediately, the second after 1s, etc. This spreads the load over ~2 minutes instead of a thundering herd. Also, **the SQS queue visibility timeout is set to 30s**, so if Twilio is still slow, messages go back to the queue and other SMS consumer instances can pick them up. This prevents a single slow instance from blocking the entire queue.

### Tier 3 — Cross-Concept Probe (Can you reason across concepts?)

**Q: "How does your notification service interact with the audit system? If a customer claims they never received a signing request, how do you prove them wrong?"**

> The notification_history table is immutable (append-only, indexed by user_id + created_at). When the Document Service triggers a signature request, the Notification Service creates a notification_history entry (proof: "we sent a notification at 2026-06-24 14:30 UTC"). The notification_status table tracks delivery: we have proof that the email was sent successfully (email_status = 'SENT', email_sent_at = '2026-06-24 14:30 UTC'). We also log the provider's response (SendGrid returned 200 OK). If the customer claims they didn't receive the email, we check if the email was marked spam by their mail provider (out of our control), or we resend it. The audit trail proves we did our part.

---

### New Deep Probes (Tier 2 — added Jul 4, 2026)

**Q: "You said 'push notifications via Firebase Cloud Messaging.' But DocuSign has millions of iOS users. FCM is Android-first. How does push delivery differ between iOS and Android, and does your design handle both?"**
> This is a real operational distinction:
>
> - **FCM** (Firebase Cloud Messaging — Google's push notification service for Android and Chrome; you send a message with the device's registration token to the FCM API and it delivers to the device): works natively for Android. For iOS, Firebase wraps Apple's APNs (Apple Push Notification service — Apple's delivery channel for push notifications to iPhones and iPads; apps must register for push with Apple to receive a device token; APNs requires the server to hold an Apple-issued signing certificate or authentication key to authenticate pushes) behind the scenes — you can send through Firebase and it routes to APNs.
>
> - **Two approaches:**
>   - **(A) Firebase for both** — simpler; one SDK to manage; Firebase handles the iOS/APNs routing. Trade-off: Firebase is an intermediary layer; if Firebase goes down, both platforms are affected; FCM → APNs can add 100–200ms latency for iOS.
>   - **(B) Native APNs for iOS, FCM for Android** — you talk directly to Apple's APNs HTTP/2 API for iOS and FCM's API for Android. Lower latency for iOS; no intermediary. Trade-off: you manage two device token formats, two authentication flows (APNs uses JWT-based auth with an Apple-issued `.p8` key).
>
> **My design:** Store the `device_platform` ("ios" or "android") and `device_token` per user device. The Push Delivery Service routes iOS tokens directly to APNs, Android tokens directly to FCM. This eliminates Firebase as a single point of failure and reduces iOS push latency.
>
> **Critical operational detail:** APNs returns error code `BadDeviceToken` when the token is stale (user reinstalled the app, new token issued). Your Push Delivery Service must delete stale tokens immediately on receiving this error — if you keep sending to a stale token, APNs may throttle or blacklist your service.

---

**Q: "How do you handle stale push notification device tokens? A user reinstalls the app, gets a new token — the old token is dead. You keep sending to the dead token. What happens?"**
> APNs (Apple) returns HTTP 410 Gone with `BadDeviceToken` in the response body. FCM (Google) returns `registration_not_found` in the response JSON.
>
> **What happens if you ignore it:** APNs quietly discards the push. No delivery, no error visible to the user. After repeated pushes to a stale token, APNs marks your service as "noisy" and may rate-limit your push throughput for legitimate tokens from that app.
>
> **Correct handling in the Push Delivery Service:**
> ```java
> // After calling APNs or FCM:
> if (response.error == "BadDeviceToken" || response.error == "registration_not_found") {
>     // Delete this stale token from our DB
>     deviceTokenRepository.delete(userId, deviceToken, platform);
>     // Don't retry — the token is permanently dead
> }
> ```
> The device token table (`user_device_tokens`) is updated immediately. Next push to this user skips this device. When the user opens the app post-reinstall, the app registers a new token → fresh push registration.
>
> **In an interview:** "Stale token handling is a common operational gap. I'd check the provider's response code on every push: BadDeviceToken / registration_not_found → immediately delete from DB, no retry. This keeps the device token registry clean and prevents APNs rate-limiting."

---

### New Cross-Concept Probe (Tier 3 — added Jul 4, 2026)

**Q: "Your outbox processor polls the outbox table every 100ms. At 35K notifs/sec, that's 3,500 outbox rows per 100ms batch. How does polling at this rate affect Postgres performance, and what would you do if it becomes a bottleneck?"**
> At 3,500 rows per 100ms batch with a `SELECT WHERE status = 'PENDING' ORDER BY created_at ASC LIMIT 3500`, the index on `(status, created_at)` makes this ~1ms. Postgres handles this fine at our scale. But as you scale to 350K notifs/sec (10× growth), three things break:
>
> 1. **Write throughput**: 350K outbox INSERTs/sec on a single Postgres table. A single Postgres can sustain ~50–100K TPS on fast SSDs. You're at 3.5× the limit.
>
> 2. **Polling contention**: If you run 10 outbox processor instances each polling every 100ms, they all SELECT the same PENDING rows simultaneously. Multiple processors try to claim the same row. Fix: use `SELECT ... FOR UPDATE SKIP LOCKED` (a Postgres clause that locks the selected rows for update and skips rows already locked by another session — like checkout lanes at a store; each processor grabs different rows without stepping on each other).
>
> 3. **Index bloat**: The `(status, created_at)` index grows as PENDING rows age. After rows are marked SENT, they remain in the index until vacuumed.
>
> **At 10× scale:** Switch from polling to CDC (Change Data Capture — a technique where a separate process reads the Postgres write-ahead log to see every INSERT the moment it's written, rather than polling; like listening to the DB journal in real time). Debezium (a CDC tool that reads the Postgres WAL and publishes every database change to Kafka — no polling, no SELECT overhead; sub-10ms latency from INSERT to Kafka publish) reads every outbox INSERT from the WAL and publishes to Kafka immediately. No polling overhead, no SELECT contention, sub-millisecond latency from outbox write to Kafka publish.
>
> **In an interview:** "Polling outbox works at our scale (35K/sec). For 10× growth, I'd switch to CDC via Debezium — reads the Postgres WAL, zero polling overhead. The outbox table becomes just a durable write; Debezium handles the Kafka publish."

---

**Q: "Your design marks a notification as 'SENT' when SendGrid / Twilio returns 200 OK. But 200 OK from SendGrid only means they accepted the email — not that the recipient actually received it. How do you track actual delivery?"**

> This is a common gap. SendGrid/Twilio's API returning 200 means "we queued your message." Whether the recipient's inbox accepted it is a separate event that arrives minutes later.
>
> **Delivery receipts via webhooks (the production pattern):**
>
> Both SendGrid and Twilio support webhook callbacks (HTTP POST to a URL you configure) when a message's delivery status changes:
>
> | Provider | Webhook event | When fired |
> |---|---|---|
> | SendGrid | `delivered` | Recipient mail server accepted the message |
> | SendGrid | `bounce` / `blocked` | Recipient mail server rejected; permanent or transient |
> | SendGrid | `open` | Recipient opened the email (pixel tracking) |
> | Twilio | `delivered` | SMS delivered to carrier + handset |
> | Twilio | `failed` | Carrier rejected the SMS permanently |
>
> **Implementation:**
> 1. Register a webhook URL in SendGrid/Twilio dashboard: `POST https://notify.docusign.com/webhooks/sendgrid`
> 2. SendGrid/Twilio call this URL with a payload containing `message_id` (which you included when calling the API) and the delivery event
> 3. Webhook handler updates `notification_status` table: `UPDATE notification_status SET delivery_status='DELIVERED', delivered_at=NOW() WHERE provider_message_id = ?`
>
> **Why this matters for DocuSign's audit trail:** "We notified you on June 3 at 2 PM" is provable when a lawyer asks. But without delivery receipts, you can only prove "we sent it" not "it was delivered." With SendGrid delivery webhooks, you can prove "the recipient's mail server accepted the message at 2:03 PM" — a much stronger legal statement.
>
> **In an interview:** "SendGrid's 200 OK means they queued it. For proof of delivery, I'd register a SendGrid webhook on the `delivered` event. When SendGrid's servers receive an acceptance from the recipient's mail server, they POST to our webhook URL with the message_id. We update notification_status to DELIVERED. Now the audit trail distinguishes SENT (we sent it to SendGrid) from DELIVERED (recipient's server accepted it) from OPENED (recipient opened it)."

---

**Q: "You store quiet hours in the user's timezone (e.g., 'Asia/Kolkata', 22:00–08:00). During the US spring-forward DST transition (clocks jump from 2:00 AM to 3:00 AM), what happens to notifications scheduled for US users with quiet hours ending at 8:00 AM?"**

> DST transitions are a real edge case that fails silently. The spring-forward scenario:
> - US Eastern time clocks jump from **1:59 AM** to **3:00 AM** (the 2:00–2:59 AM hour doesn't exist)
> - A user has quiet hours ending at `08:00 America/New_York`
> - The Fan-out Service is processing at what it thinks is **2:30 AM** local time
> - After spring-forward, that 2:30 AM slot **never happened** — the next real moment is 3:00 AM
>
> **Three failure modes to explain:**
>
> **Mode 1 — Incorrect quiet-hours gate:** If you stored the quiet hours end time as a UTC offset (e.g., `+05:30`) instead of a timezone name (`Asia/Kolkata`), the offset doesn't update when DST changes. A user in New York stored as `America/New_York` correctly handles DST; a user stored as `-05:00` (EST fixed offset) gets a wrong gate for the 6 months they're on EDT (-04:00).
>
> **Fix 1:** Store timezone names, never fixed UTC offsets. Use `java.time.ZoneId` (not `ZoneOffset`) when comparing quiet hours to `NOW()`:
> ```java
> ZonedDateTime now = ZonedDateTime.now(ZoneId.of(user.getTimezone()));
> // ZoneId handles DST transitions automatically
> int hourNow = now.getHour();
> boolean inQuietHours = hourNow >= quietStart || hourNow < quietEnd;
> ```
>
> **Mode 2 — Notification scheduled for the skipped hour (2:00–2:59 AM spring-forward):** A cron job that should fire at 2:30 AM fires at either 1:59 AM (before spring) or 3:00 AM (after spring) depending on scheduler behavior. Notifications scheduled for "quiet hours end" could fire at the wrong moment.
>
> **Fix 2:** Use a timezone-aware scheduler (Spring's `@Scheduled` with a `ZoneId`-aware cron expression) or use a library like Quartz Scheduler that handles DST-safe trigger computation. Avoid computing absolute UTC timestamps for future recurring events — always recompute "next fire time" from the timezone-aware current time at each tick.
>
> **Mode 3 — Fall-back double-firing:** During fall-back (clocks roll back from 2:00 AM to 1:00 AM), the 1:00–1:59 AM hour occurs twice. A notification scheduled for 1:30 AM local time could fire twice if the scheduler doesn't handle repeated intervals.
>
> **In an interview:** "Quiet hours must use timezone names, never fixed UTC offsets — DST changes the offset twice a year. I'd use `java.time.ZoneId` for all timezone-aware comparisons. For scheduled notifications, I'd rely on a DST-aware scheduler (like Quartz) rather than computing fixed UTC timestamps, because UTC-correct means real-clock-incorrect when DST transitions happen."

---

## Section 13 — 🐞 Common Mistakes on This Question

- **Mistake 1:** "I'll have one queue for all notification channels." → **Why it's wrong:** SMS is the bottleneck (Twilio max ~10K SMS/sec). If SMS queue gets congested, emails get delayed. Users see 5-minute email latency, which is unacceptable. → **What to say instead:** "Per-channel SQS queues. Each channel has independent scaling. Email consumer fleet can have 10 instances, SMS can have 5, push can have 3. They don't interfere."

- **Mistake 2:** "I'll call the user preference service for every notification (synchronous lookup)." → **Why it's wrong:** At 35K notifs/sec, you're making 35K preference lookups/sec to the database. Most databases can't sustain more than 1K concurrent queries. You'll need caching anyway. → **What to say instead:** "Redis cache for user preferences, 5-minute TTL. Fallback to database on cache miss. This handles 35K lookups/sec with sub-100ms latency."

- **Mistake 3:** "If the outbox processor crashes, notifications are lost." → **Why it's wrong:** Outbox entries are durable in the DB. The processor is stateless and restartable. When it restarts, it will re-process all PENDING outbox entries. Nothing is lost. → **What to say instead:** "Outbox entries are append-only in the database. The processor is stateless and restartable. If it crashes, the next instance reads the same PENDING entries and processes them. At-least-once delivery is guaranteed."

---

## Section 14 — 🧭 DocuSign Dimensions Checklist

| Dimension | Relevant? | How your design addresses it |
|---|---|---|
| **Testability** | ✅ | Each component is independently testable: verify email delivery with mocked SendGrid client; test retry logic by injecting a simulated Twilio 503; test idempotency by publishing the same event_id twice and asserting exactly one SQS message. Outbox processor is a pure function (input: list of outbox rows → assert Kafka publish + processed_at update). |
| **Usability** | ✅ | Users manage preferences via PUT /users/{id}/notifications/preferences — email/SMS/push per event type. Quiet hours stored in UTC + user_timezone (ZoneId, not ZoneOffset). For DocuSign: a signer in Tokyo sets quiet hours 11 PM–7 AM JST — the system calculates the UTC window correctly even across DST transitions, ensuring signing requests don't arrive as 3 AM SMS messages. |
| **Extensibility** | ✅ | New channel (Slack, WhatsApp) = new SQS queue + new consumer class implementing DeliveryHandler interface; fan-out core unchanged. For DocuSign: adding in-app notifications for the DocuSign mobile app = new push-queue + APNs/FCM consumer, zero changes to the outbox, Kafka, or email/SMS consumers. |
| **Security** | ✅ | Service-to-service auth (HMAC-signed API key) on POST /v1/notifications. User preferences are tenant-isolated (tenant_id scoped). Notifications contain no contract content — only metadata + a signed pre-signed URL to the document — for DocuSign: if a notification SMS is intercepted, the attacker sees only "A document is awaiting your signature" + an expiring URL, not the contract contents. |
| **Availability** | ✅ | Outbox pattern makes event publishing atomic with the DB transaction — no dual-write gap (notification is never lost even if the service crashes between DB write and Kafka publish). At 35K notifs/sec (Section 4), per-channel SQS queues mean an SMS provider outage (Twilio down) does not delay email delivery — queues are independent. 99.9% SLO. |
| **Scalability** | ✅ | Kafka 50 partitions by user_id → 50 parallel fan-out consumers. At 1B notifs/day (Section 4 = 35K notifs/sec), SQS email-queue auto-scales consumer fleet to 21K emails/sec (SendGrid's max). Notification history archived to S3 after 1 year — active table stays < 10GB. For DocuSign's bulk-send feature (one customer sends 50K envelopes at once), the Kafka partition design ensures load is spread across 50 consumers, not serialized. |
| **Observability & Traceability** | ✅ | Every notification carries notification_id + trace_id from event creation through Kafka → fan-out → SQS → delivery attempt. Log at each stage: queued, preferences_checked, delivered, failed. Alert: delivery_latency_p99 > 30s (SLA breach). For DocuSign's legal proof of notification: "We notified signer@example.com on June 24 at 2:14 PM UTC" is a query on notification_status (event_id, channel, sent_at, provider_response) — the legal audit trail required for non-repudiation defense. |

---

## Section 15 — 🧾 TL;DR Answer Summary

> "A real-time notification service at scale (35K notifs/sec) requires three critical pieces: (1) **reliable event publishing** via the outbox pattern (atomic DB insert + Kafka publish in one transaction); (2) **independent channel delivery** with per-channel SQS queues (email/SMS/push scale independently, no queue bottleneck); (3) **smart retry logic** with exponential backoff + jitter (handles transient failures without thundering herd, moves permanent failures to DLQ). The system respects user preferences (cached in Redis), enforces quiet hours (timezone-aware), and maintains full audit trails (notification_history + notification_status). Trade-offs: at-least-once delivery (acceptable with idempotency), per-channel queues (slightly more ops overhead but required for scale), and cached preferences (5-minute stale window acceptable). At 35K notifs/sec, the Kafka → fan-out → SQS → delivery flow completes in <5 seconds end-to-end, meeting DocuSign's requirement that signers are notified promptly when a document is ready to sign."

---

---

## 🔌 LLD Drill-Down — Class Structure for the Notification Delivery Layer

> **Trigger:** Interviewer says "Walk me through the class structure for the delivery layer" or "How would you model the channel handlers?" — this is the expected LLD follow-up for D3.
>
> **What they're testing:** Observer / Strategy pattern for extensible channel delivery, and whether you understand WHY synchronous observer calls fail at 35K notifs/sec.

---

### 🧠 Mental Model Before You Draw

The fan-out service receives one notification event and must dispatch to N channels (email, SMS, push). Each channel is independent — SMS failing must not block email. Each channel handler is pluggable — adding Slack = one new class.

This is **Observer meets Strategy**: Observer for the "fire to all interested channels" pattern; Strategy for each channel's delivery algorithm.

**But here's the critical interview insight:** A naive Observer calls each handler synchronously inline. At 35K notifs/sec, a Twilio 500 response blocks the entire thread for the timeout duration. The correct answer is: the fan-out service publishes to per-channel SQS queues (async). The Observer interface is still the design — but execution is decoupled.

---

### 🏗️ Class Structure

```
┌──────────────────────────────────────────────────────────────┐
│                    NotificationFanoutService                 │
│  - handlers: Map<DeliveryChannel, DeliveryHandler>           │
│  - preferenceService: UserPreferenceService                  │
│  - queueClient: SqsClient                                    │
│  + fanout(NotificationEvent event): void                     │
└──────────────────────────────────────────────────────────────┘
         │ dispatches to
         ▼
┌──────────────────────────┐     ┌──────────────────────────────┐
│  <<interface>>           │     │  UserPreferenceService        │
│  DeliveryHandler         │     │  + getPreferences(userId)     │
│  + deliver(event): void  │     │  (Redis cache, 5-min TTL)     │
└──────────┬───────────────┘     └──────────────────────────────┘
           │ implements
  ┌────────┴──────────────────────────┐
  │                                   │
EmailDeliveryHandler    SmsDeliveryHandler    PushDeliveryHandler
(enqueues to           (enqueues to           (enqueues to
 email-queue)           sms-queue)             push-queue)

DeliveryChannel (enum): EMAIL, SMS, PUSH

NotificationEvent
  - eventId:    String          ← idempotency key
  - userId:     String
  - type:       NotificationType  (SIGNING_REQUEST, COMPLETED, REMINDER)
  - payload:    Map<String, Object>
  - createdAt:  Instant

NotificationStatus (enum): PENDING, DELIVERED, FAILED, SKIPPED
```

---

### 🔌 Key Interfaces

```java
/**
 * Contract for every delivery channel.
 * Adding Slack = one new class, zero changes to FanoutService.
 * Open-Closed Principle + Separation of Concerns.
 */
public interface DeliveryHandler {

    DeliveryChannel getChannel();

    // Enqueues to the channel-specific SQS queue.
    // Does NOT call SendGrid/Twilio directly — the SQS worker does that.
    // This makes deliver() fast and non-blocking (~1ms per call).
    void deliver(NotificationEvent event);
}
```

```java
public enum DeliveryChannel {
    EMAIL,
    SMS,
    PUSH
}
```

---

### 🖊️ Critical Classes — Write These in the Interview

**NotificationFanoutService — the core class:**

```java
public class NotificationFanoutService {

    private final Map<DeliveryChannel, DeliveryHandler> handlers;
    private final UserPreferenceService preferenceService;

    public NotificationFanoutService(
            List<DeliveryHandler> handlers,
            UserPreferenceService preferenceService) {
        this.handlers = handlers.stream()
            .collect(Collectors.toMap(DeliveryHandler::getChannel, h -> h));
        this.preferenceService = preferenceService;
    }

    /**
     * Fan-out one event to all channels the user has opted in to.
     * Each DeliveryHandler enqueues to its SQS queue — non-blocking.
     * If one channel fails to enqueue, others are NOT affected (try-catch per channel).
     */
    public void fanout(NotificationEvent event) {
        UserPreferences prefs = preferenceService.getPreferences(event.getUserId());
        for (DeliveryChannel channel : prefs.getEnabledChannels()) {
            DeliveryHandler handler = handlers.get(channel);
            if (handler != null) {
                try {
                    handler.deliver(event);
                } catch (Exception e) {
                    // Log and continue — one channel failure must not block others
                    // In production: publish to a dead-letter topic for alerting
                }
            }
        }
    }
}
```

**EmailDeliveryHandler — one concrete channel (SMS and Push follow same pattern):**

```java
/**
 * Enqueues the notification to the email-specific SQS queue.
 * The SQS worker (separate service) calls SendGrid.
 * Why not call SendGrid directly here?
 * → At 35K notifs/sec, a 2s SendGrid timeout blocks this thread for 2 seconds.
 *   With 10 threads, we'd be stuck after 5 concurrent failures.
 *   Enqueuing to SQS is <5ms and never blocks on provider availability.
 */
public class EmailDeliveryHandler implements DeliveryHandler {

    private final SqsClient sqsClient;
    private final String emailQueueUrl;

    @Override
    public DeliveryChannel getChannel() {
        return DeliveryChannel.EMAIL;
    }

    @Override
    public void deliver(NotificationEvent event) {
        String messageBody = serialize(event);
        sqsClient.sendMessage(emailQueueUrl, messageBody);
    }

    private String serialize(NotificationEvent event) {
        // JSON serialization — in production use Jackson ObjectMapper
        return "{\"eventId\":\"" + event.getEventId() + "\",\"userId\":\"" + event.getUserId() + "\"}";
    }
}
```

**The SQS worker that actually calls SendGrid (separate from the fan-out service):**

```java
/**
 * Polls email-queue and calls SendGrid.
 * Separate from the fan-out path — failures here trigger SQS retry, not fan-out retry.
 * Idempotency: checks event_id in Redis before calling SendGrid.
 */
public class EmailWorker {

    private final SendGridClient sendGridClient;
    private final RedisClient redisClient;
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    public void processMessage(SqsMessage message) {
        NotificationEvent event = deserialize(message.getBody());

        // Idempotency check — at-least-once delivery, but exactly-once SendGrid call
        String idempotencyKey = "notif:sent:" + event.getEventId() + ":email";
        if (redisClient.exists(idempotencyKey)) {
            return;   // already delivered — SQS duplicate, skip
        }

        sendGridClient.send(buildEmail(event));
        redisClient.setex(idempotencyKey, IDEMPOTENCY_TTL, "1");
    }
}
```

---

### 🔁 Concurrency — The Critical Point

At 35K notifs/sec the fan-out service is multi-threaded. Two threads can fan-out the same event simultaneously (SQS redelivery, retry). The idempotency key in the worker (Redis `SETNX`) prevents double-delivery:

```
Thread A: deliver event_id=X → SQS → worker: SETNX "notif:sent:X:email" → SET (new) → call SendGrid
Thread B: deliver event_id=X → SQS → worker: SETNX "notif:sent:X:email" → EXISTS → skip
```

`NotificationFanoutService.fanout()` itself does NOT need synchronization — each `deliver()` call is a stateless SQS enqueue. State lives in Redis + the outbox table, not in shared Java heap.

---

### 🔬 LLD Interview Probes — D3 Specific

**Q: "Your fanout() method catches exceptions per channel. What happens if the SQS enqueue itself fails?"**
> The outbox is the safety net. The outbox processor is polling the `notification_outbox` table for `status = PENDING` rows. Even if SQS enqueue fails in `fanout()`, the outbox row stays PENDING. The processor retries the next poll cycle (every 100ms). The `DeliveryHandler.deliver()` failure is transient — the outbox guarantees eventual delivery.

**Q: "How do you add a new Slack channel tomorrow?"**
> One new class: `SlackDeliveryHandler implements DeliveryHandler`. Override `getChannel()` to return `DeliveryChannel.SLACK` (add the enum value). Override `deliver()` to enqueue to a new `slack-queue`. Register it with `NotificationFanoutService` via constructor injection. Zero changes to `fanout()`, zero changes to existing handlers. This is the Open-Closed Principle — the fan-out service is closed to modification, open to extension.

**Q: "Why not call SendGrid directly in EmailDeliveryHandler.deliver()?"**
> At 35K notifs/sec with 10 fanout threads, if SendGrid is slow (2s timeout), 10 concurrent slow calls exhaust the thread pool in 200ms. Every subsequent fanout() call blocks waiting for a thread. One provider's latency spike takes down the entire notification system. SQS enqueue is <5ms and always fast — it decouples our latency from the provider's latency.

**Q: "What if UserPreferenceService is slow — user preferences lookup takes 500ms?"**
> That's why preferences are cached in Redis with a 5-minute TTL. The cache miss path (cold start or TTL expiry) hits Postgres and populates Redis — one slow call per user every 5 minutes. At 35K notifs/sec, 99.9% of lookups are Redis hits (<1ms). The 0.1% cache misses are ~35 Postgres queries/sec — well within single-instance Postgres capacity.

**Q: "Walk me through exactly-once delivery — same event, two SQS deliveries. What happens?"**
> SQS guarantees at-least-once delivery, so a retry or network hiccup can redeliver. The worker checks `SETNX "notif:sent:{event_id}:{channel}"` before calling the provider. First delivery: SETNX returns true → proceed → call SendGrid → set key with 24h TTL. Second delivery: SETNX returns false (key exists) → skip. The 24h TTL covers the SQS visibility timeout window (max 12h) with margin. Result: at-least-once at the SQS layer, exactly-once at the SendGrid call layer.

**Q: "DocuSign sends a bulk envelope to 50,000 signers. All 50,000 notification events arrive in 2 seconds. What breaks?"**
> Nothing in the current design — this is exactly what Kafka partitioning + per-channel SQS auto-scaling handles. 50,000 events → 50 Kafka partitions absorb the burst → fan-out consumers process in parallel → 50,000 SQS enqueues in <10 seconds → SQS email-queue auto-scales consumer fleet. The only limit is SendGrid's throughput cap (21K emails/sec at peak). If 50K emails in 2 seconds exceeds that, SQS provides natural buffering — emails are delivered within minutes, not rejected.

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 24, 2026 | **D3-notification-service.md created.** Full 15-section solution framework for Type A System Design. Covers: outbox pattern for reliable event publishing, per-channel fan-out (email/SMS/push), exponential backoff retry logic, idempotency for exactly-once delivery, rate limiting, and DocuSign-specific depth (multi-signer coordination, audit trails, quiet hours). Scale: 1B notifs/day, 35K notifs/sec peak. Prerequisites: `07-cdc-outbox.md`, `04-idempotency.md`. |
| Jul 4, 2026 | **4 new Q&As added to Section 12.** (1) **APNs vs FCM distinction** — FCM wraps APNs for iOS (adds latency), or use native APNs direct (lower latency, but two auth flows); store `device_platform` per device and route directly; (2) **Stale device token handling** — APNs returns HTTP 410 `BadDeviceToken`, FCM returns `registration_not_found`; correct response: immediately delete from device token table, no retry; failure to handle this causes APNs rate-limiting of your entire service; (3) **Outbox polling at scale** — 3,500 rows/batch at 100ms is fine today; at 10× scale, switch from polling to CDC via Debezium (reads Postgres WAL, publishes to Kafka, zero SELECT overhead, sub-ms latency); `SELECT FOR UPDATE SKIP LOCKED` prevents multi-processor row contention for the intermediate scale. |
| Jul 5, 2026 | **Section 6 restructured: single final-state diagram → 3-stage progressive HLD.** Stage 1 (Direct Sync HTTP): upstream calls Notification Service synchronously; Service calls SendGrid + Twilio in sequence — BREAKING POINTs: provider down = caller blocks + request fails; sequential calls saturate thread pool at 35K/sec; no retry = lost notifications. Stage 2 (Kafka + Per-Channel SQS + Retry): Kafka for ordered event ingestion, Fan-out Service routes to email-queue/sms-queue/push-queue, delivery workers with exponential backoff retry — BREAKING POINTs: dual-write gap (crash between DB write and Kafka publish = silent loss); no idempotency (consumer restart = duplicate emails). Stage 3 (Outbox + Redis + DLQ — production): outbox pattern makes DB write + Kafka publish atomic; Redis handles rate limiting (INCR+TTL) + preferences cache (5-min TTL) + idempotency keys (event_id+user_id+channel, 24h TTL); DLQ captures permanent failures. Four inline decision tables added: (1) event transport — sync HTTP ❌ / single queue ⚠️ / Kafka ✅; (2) fan-out queue strategy — single queue ❌ / in-memory ❌ / per-channel SQS ✅; (3) retry pattern — none ❌ / fixed-interval ⚠️ / exp backoff+jitter ✅; (4) idempotency storage — none ❌ / Postgres table ⚠️ / Redis TTL ✅. All Section 6 verdicts verified against Section 7 deep dive choices — no contradictions. |
| Jul 9, 2026 | **Section 11 additions.** (1) SLA-based queue isolation from Docusign Engineering Blog mid-2025: Live Queue (P95 <15 min), Bulk Queue (flexible SLA), Workflow Queue (per-workflow SLA); explains head-of-line blocking and two-tier priority model; cross-references existing per-channel queues. (2) CAP theorem position for notifications explicitly stated: AP (staleness not legally material; duplicate recoverable; contrast with signed records which are CP). |
| Jul 5, 2026 | **Section 10 business impact + Section 14 DocuSign dimensions pass.** Section 10: added **Business impact:** to all 3 trade-offs — 40% latency overhead causing signer abandonment before notification delivery completes (throughput cost), email delay triggering duplicate "Sign Now" click creating duplicate envelope event (at-least-once delivery), bulk-send 50K envelopes backing up notification queue for hours and missing marketing campaign deadline (queue depth). Section 14: rewrote all 7 dimension cells — ZoneId DST ambiguity in quiet-hours scheduler causing midnight notification surge (Usability), `delivery_log` audit trail as legal proof of notification for non-repudiation disputes (Observability), bulk-send Kafka partition design from Section 4 (35K notifs/sec, 20 partitions) enabling Scalability RCA. |
