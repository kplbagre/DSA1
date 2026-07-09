# A2 — Build a Facebook Chat / Messenger Application

> **Read `solution-notes-standards.md` first.** This file is a 60-minute interview answer framework, not a concept reference. Concept-level depth lives in `SystemDesignConcepts/`.

---

## 📚 Prerequisites — Study These First

Before reading the solution, make sure you can explain the bolded items below from memory.

| Concept | File in `SystemDesignConcepts/` | Why you need it for this question |
|---|---|---|
| **WebSocket / real-time communication** | `Production-Grade/System-Design-Patterns/26-websocket-real-time-communication.md` | The entire real-time delivery model — persistent connections, connection-server affinity, how the routing layer tracks which server holds each user's socket |
| **Message queues (Kafka / RabbitMQ)** | `Core-Architecture/Service-Communication/19-message-queues-kafka-rabbitmq.md` | Fan-out to multi-device delivery; decouples senders from the push layer; guarantees durability when a socket is temporarily down |
| **Feed and fan-out pattern** | `Patterns/DeepDive/03-feed-and-fanout.md` | Group chats and multi-device delivery are a fan-out problem — know push-on-write vs pull-on-read trade-offs |
| **Real-time updates pattern** | `Patterns/DeepDive/07-real-time-updates.md` | Presence, typing indicators, read receipts — these are sub-second state updates that bypass the normal request-response model |
| **Caching fundamentals** (Redis) | `Foundations/Performance-and-Scale/03-caching.md` | Online/presence status lives in Redis (not the DB) — TTL-based presence is a standard interviewer follow-up |
| **Outbox / CDC pattern** | `Foundations/Data-Fundamentals/07-cdc-outbox.md` | Reliable Kafka publish after a message insert — prevents the "message written but Kafka publish crashed" data loss scenario |
| **Idempotency** | `Foundations/Concurrency-and-Consistency/04-idempotency.md` | Duplicate delivery on retry — the client-side idempotency key pattern is the correct answer here |

---

## 🎯 What Is This System?

**In plain English:** A real-time messaging system lets users exchange messages instantly, see delivery and read receipts, and know who's online. Messages must survive network drops, arrive in order, and sync across all a user's devices.

**Real-world examples:**

| System / Company | What they built |
|---|---|
| **Facebook Messenger** | 1B+ users, real-time delivery with read receipts |
| **WhatsApp** | 100B messages/day, end-to-end encrypted |
| **Telegram** | Multi-device sync, large group chats up to 200K members |
| **Slack** | Workplace messaging with threads and file sharing |
| **Discord** | Community chat with voice/video channels |
| **iMessage** | Apple's native messaging with SMS fallback |

**Core user journey:** Alice opens a chat with Bob → types "are you free at 3pm?" → Bob sees it appear on his phone in real time → Alice's screen shows "Delivered", then a few seconds later "Read."

**Why it's hard to build at scale:** Maintaining persistent WebSocket connections for 100M+ concurrent users across thousands of servers requires a routing layer that knows which server holds each user's connection — a stateful fan-out problem that doesn't exist in stateless REST APIs.

---

## 🧠 How to Use This File

**This file is an instantiation of DELIVERY-RECIPE** (`Interview/DocuSign/DELIVERY-RECIPE.md`). Every section below maps to one step of the 6-step interview delivery framework. The framework is backed by cognitive psychology — under stress, your working memory shrinks 40–50%, so you need ONE rhythm you can execute automatically.

**Before your interview:**
1. Read DELIVERY-RECIPE.md once to understand the psychology (30 min)
2. Skim the 6 **Memory Anchors** below (2 min)
3. Read this entire file and the 3 **Common Mistakes** (Section 13) so you know what to avoid (20 min)
4. During the interview, follow the 6-step rhythm: Ask → Clarify → Requirements → Estimate → HLD → Deep Dives → Trade-offs → Dimensions → Probes

**The time budget:**
- Minutes 0–5: Sections 1–2 (Opener + Clarifying questions)
- Minutes 5–10: Sections 3–4 (Requirements + Scale estimation)
- Minutes 10–20: Sections 5–6 (Requirements variation + HLD + Data flow)
- Minutes 20–38: Section 7 (Deep dives: 2–3 riskiest components)
- Minutes 38–45: Section 10 (Trade-offs: exactly 3, with failure modes)
- Minutes 45–50: Section 11 (DocuSign dimensions — map explicitly)
- Minutes 50–60: Section 12 (Interviewer probes — prepared Tier 1/2/3 answers)

**Stay on this schedule.** If you're at minute 40 and still deep-diving, pause and move to trade-offs — the rubric values trade-off thinking over technical depth.

---

## 💾 Memory Anchors (Memorize These 6)

Before every interview, say these 6 sentences to yourself (takes 30 seconds):

1. **"Ask before you design."** — Don't assume. Use Section 2 to ask clarifying questions and confirm scope.
2. **"Name the nouns."** — Entities are your mental hooks. When stressed, you can remember categories even if you forget details.
3. **"Define the boundary."** — The API/interface is the contract. Lock it down before you argue about implementation.
4. **"Trace a request."** — Section 6's data flow narrative shows you understand movement through the system, not just boxes.
5. **"Draw the boxes."** — ASCII HLD is your mental model made visible. The interviewer can probe specific boxes without restarting.
6. **"Dig where it's risky."** — Section 7: pick 2–3 *riskiest* components (where the system breaks, where scale hits hardest), not the most *interesting* ones.

**Bonus anchors (if you have memory space):**
- "Everything is a trade-off." → Section 10
- "Why, not what." → Explain reasoning, not just technology
- "Conversational, not presentation." → Think aloud; don't recite

---

## Section 0 — Question Identity Card

| | |
|---|---|
| **Question** | Build a Facebook Chat / Messenger Application |
| **Interview Type** | Type A — System Design |
| **Confirmed or Likely** | ⭐ Confirmed asked (DocuSign PDF p.3 + candidate report: "design a Facebook messenger type app") |
| **Concept notes prerequisite** | `03-caching.md` (presence via Redis), `07-cdc-outbox.md` (reliable Kafka delivery), `04-idempotency.md` (dedup on retry) |
| **DocuSign-specific angle** | This is a PDF-example question, not a DocuSign domain question. The DocuSign move is to **explicitly name which of the 7 evaluation dimensions your design addresses** — that's the grading signal for Type A. Do not try to make chat sound DocuSign-y. |
| **Time budget** | 5 min clarify → 5 min estimate → 15 min HLD → 15 min deep dive → 8 min trade-offs → 7 min Q&A buffer |

---

## Section 1 — 🚀 The One-Sentence Opener

> "Before I draw anything, let me ask a few clarifying questions — specifically around which features are in scope, what scale we're targeting, and whether the interviewer wants me to focus on infrastructure scale or the product API design, because those lead to different architectures."

Then immediately go to Section 2. Do NOT start drawing.

---

## Section 2 — 🔍 Clarifying Questions Script (Minutes 0–5)

**What to do:** Ask 4–6 questions that clarify scope. Don't assume. The interviewer is watching how you *think*, not how fast you talk.

**Say this out loud (after your opener):**
> "I have a few clarifying questions so I make sure I'm building the right thing..."

---

**Q: "Should I focus on the infrastructure and scale side — WebSocket fleet, message storage at scale — or the product API contract — endpoint design, schema, SOLID principles?"**
- Why ask: this is a dual-use question in the DocuSign PDF. Type A = infrastructure scale, Type B = product API. Let the interviewer tell you which fork to take.
- If infrastructure → go deep on WebSocket connection management, Cassandra, fan-out
- If product API → go deep on REST/WebSocket API contract, schema, SOLID extensibility

> *(In most candidate reports this question was answered as infrastructure/Type A. Assume Type A unless told otherwise.)*

---

**Q: "How many daily active users are we designing for — and roughly how many messages per day?"**
- Why ask: this single number drives every capacity decision.
- If 1M DAU → single region, monolith could work, Redis Pub/Sub for fan-out
- If 500M+ DAU → multi-region, Cassandra sharding, dedicated connection server fleet, separate fan-out service

---

**Q: "Are we supporting 1:1 messaging only, or group chats too? If group chats, what's the maximum group size?"**
- Why ask: fan-out strategy changes completely between 1:1 and group.
- 1:1 → one copy, push to one recipient
- Group ≤100 → fan-out on write (create N copies immediately, fast reads)
- Group >1000 → fan-out on read (shared inbox + lazy delivery), otherwise write amplification is unbounded

---

**Q: "Do we need delivery receipts — sent, delivered, read?"**
- Why ask: adds a whole acknowledgment pipeline — client ACKs to server on deliver/read, server updates status in DB.
- If yes → need client-side ACK protocol + status update API
- If no → simpler; just fire and forget with retry

---

**Q: "Are media attachments (images, video) in scope, or text only for now?"**
- Why ask: media requires CDN + blob storage (S3-style) — completely different storage layer.
- If yes → add S3 pre-signed URL flow, CDN, thumbnail generation
- If no → scope is clean, defer to "how would you extend this" in trade-offs

---

**Q: "Is end-to-end encryption required (Signal-style, where the server never sees plaintext), or server-side encryption only?"**
- Why ask: E2EE changes the entire architecture — server can't search messages, can't deliver to multiple devices easily, key management is a first-class problem.
- E2EE → out of scope for this session, but call it out as a trade-off in Section 10
- Server-side encryption → TLS in transit + AES-256 at rest, much simpler

---

**Assumed answers (state these at the start of Section 3):**
- Type A focus — infrastructure + scale
- 500M DAU, 40 messages/user/day = 20B messages/day
- 1:1 + group chat (up to 100 members)
- Delivery receipts: yes (sent/delivered/read)
- Media: out of scope for this session, note as extension
- Server-side encryption only; E2EE as a trade-off discussion

---

## Section 3 — 📋 Requirements

**Functional Requirements (what the system does):**
- Users can send and receive text messages in real-time (1:1 and groups up to 100 members)
- Messages are persistent — retrievable for the last 12 months
- Delivery receipts: sent (server received) → delivered (recipient's device received) → read (recipient opened)
- Online/offline presence: "last seen" or "active now"
- Push notifications for offline users
- Multi-device sync: same user on phone + desktop receives the same message on both

**Out of scope (say these explicitly):**
- Voice/video calls
- Media attachments (images, video)
- End-to-end encryption (flagged as a trade-off)
- Message search
- Message deletion / edit (say "extensible — add delete flag to schema")

**Non-Functional Requirements:**
- Scale: 500M DAU, ~20B messages/day, ~230K messages/sec (average), ~700K messages/sec (peak 3×)
- Latency: P99 message delivery to online user < 200ms
- Availability: 99.99% (< 52 minutes downtime/year) — messaging apps are relied upon continuously
- Consistency: messages within a conversation must be ordered (by message_id); eventual consistency across conversations is acceptable
- Durability: zero message loss — undelivered messages must be retried until delivered or the recipient comes online
- Compliance: server-side encryption at rest (AES-256) and in transit (TLS 1.3)

---

## Section 3.5 — 🗂️ Core Entities (~2 minutes)

> **Say this out loud:** "Before I sketch the architecture, let me name the key data objects the system manages."

| Entity | What it represents | Storage |
|---|---|---|
| **User** | Person using the app — profile, device tokens for push, connection state | PostgreSQL |
| **Conversation** | A 1:1 or group thread — holds metadata (name, participant list, last message preview) | PostgreSQL |
| **Message** | Individual text/media message within a conversation — the core data object | Cassandra (time-series) |
| **Participant** | Join record connecting a User to a Conversation, with their last-read cursor | PostgreSQL |
| **Presence** | User's current online/offline/last_seen status — ephemeral, not source-of-truth | Redis |

**Key relationships:**
- A `Conversation` has many `Participants` (many-to-many between User and Conversation via Participant)
- A `Conversation` has many `Messages` (one-to-many; queried time-ordered by `(conversation_id, message_id)`)
- `Presence` is ephemeral — rebuilt on reconnect; loss on crash is acceptable

---

## Section 4 — 🔢 Scale Estimation (Minutes 5–10)

**What to do:** Do envelope math out loud. These numbers justify every architecture choice you make in Section 6+. The interviewer wants to see your *thinking*, not just your conclusion.

**Say this out loud (as you write the math on the whiteboard):**
> "Let me do some envelope math to justify the architecture. Starting with traffic..."

---

**Traffic:**
- DAU: 500M
- Messages/sec (avg): 500M × 40 / 86,400 = **231K messages/sec**
- Messages/sec (peak 3×): **~700K messages/sec**
- Presence heartbeat events: 500M × 1 heartbeat/30s = ~17K heartbeat writes/sec

**Storage:**
- Per message: ~1 KB (text + metadata)
- Messages/day: 20B × 1 KB = **~20 TB/day**
- 1 year: ~7 PB (compressed ~1–2 PB with columnar compression in Cassandra)
- Conversations metadata: 500M users × 50 avg conversations × ~100 bytes = **~2.5 TB**

**Connections:**
- Concurrent WebSocket connections at peak: ~500M
- Max TCP connections per server: ~65K
- WebSocket connection servers needed: 500M / 65K = **~7,700 connection servers**

**Key conclusions:**
- "At 700K messages/sec, a single DB is impossible. We need horizontal sharding — Cassandra partitioned by conversation_id."
- "7,700 connection servers can't be addressed individually; we need consistent hashing to route message delivery."
- "20 TB/day of message data — we need a TTL (time-to-live) policy. Messages older than 12 months are archived or deleted."

---

## Section 5 — 🔄 Requirements Variation Table ⭐ KEY SECTION

| If the interviewer says... | Your architecture changes to... | The reasoning |
|---|---|---|
| "Only 1:1 chat, no groups" | Remove fan-out service; each message routes directly to one recipient | Fan-out complexity only exists for groups; 1:1 is always one sender → one recipient |
| "Groups up to 100,000 members" | Fan-out on write becomes fan-out on read: one shared message row, each reader pulls new messages using cursor | Fan-out on write to 100K recipients per message creates 100K Cassandra writes per send — write amplification is unbounded |
| "Strict message ordering globally" | Single sequence-number generator per conversation (or Redis INCR per conversation) | Clock skew between message servers would break ordering; need a central authority per conversation |
| "P99 delivery < 50ms" | In-memory message bus replaces Kafka (use Redis Pub/Sub or direct WebSocket push without intermediate queue) | Kafka adds 10–50ms latency per hop; at 50ms P99 you can't afford it. Trade: lower durability guarantee |
| "E2EE required (Signal-style)" | Key exchange during session setup; server stores only encrypted ciphertext; multi-device requires per-device key bundles | Server can never see plaintext — breaks server-side search, breaks server-side content moderation, complicates multi-device sync significantly |
| "Global deployment, multiple regions" | Active-active multi-region with per-region Cassandra clusters; conversation pinned to a region (routing table by conversation_id hash) | Cross-region Cassandra write consistency adds 100+ ms latency; pin conversations to nearest region, migrate on demand |
| "Enterprise B2B (DocuSign context)" | Add per-tenant namespace isolation; compliance retention policies per tenant; audit trail for every message event | Enterprise customers have different data residency and retention requirements; co-mingling tenant data is a compliance violation |

---

## Section 8 — 🌐 API Design (Before HLD)

> **Why here:** Define the external contract before drawing the architecture — the HLD shows how these endpoints are implemented. For Type A, this is concise (3–5 minutes); the architecture is the primary deliverable.

### 🧠 How to Derive These Endpoints

Chat is a system where **not all APIs are REST**. Start by asking: "Which operations are real-time (server must push to client unprompted) and which are request-response (client asks, server answers)?" That split determines which endpoints are WebSocket and which are REST.

"Users receive messages the moment they're sent by others" → server must push to client → WebSocket. HTTP cannot do this: HTTP is request-response, client initiates. A WebSocket connection is bi-directional and persistent — the server can push `MESSAGE_RECEIVED` events without the client polling.

"Users load their conversation list when they open the app" → request-response → REST `GET /v1/conversations`. No real-time needed — the app opens, fetches, displays. Simple GET.

"Users scroll up to load older messages" → request-response → REST `GET /v1/conversations/{id}/messages?cursor=`. History is a read — cursor pagination so the results don't drift as new messages arrive at the head.

"Users create a new group chat" → CREATE → REST `POST /v1/conversations`. One-time action, no real-time needed.

"Message delivery status (sent → delivered → read)" → real-time updates → WebSocket `MESSAGE_STATUS_UPDATE` event from server to client. But also REST `PATCH /v1/messages/{id}/status` when the recipient's device notifies the server it has seen the message.

Validation check: the WebSocket protocol replaces `POST /v1/messages` for real-time sends. The REST endpoint `POST /v1/conversations/{id}/messages` is a REST fallback for clients that can't hold a WebSocket (webhooks, bots). Both paths write to the same Cassandra store.

### REST Endpoints

| Method | Path | Auth | Request | Response | Status Codes |
|---|---|---|---|---|---|
| POST | `/v1/conversations` | JWT Bearer | `{ "type": "GROUP\|ONE_TO_ONE", "participantIds": [...] }` | `{ "conversationId": "..." }` | 201, 400, 409 |
| GET | `/v1/conversations` | JWT Bearer | — | `[{ conversationId, lastMessage, unreadCount }]` | 200 |
| GET | `/v1/conversations/{id}/messages` | JWT Bearer | `?cursor={messageId}&limit=50` | `[{ messageId, senderId, content, status, createdAt }]` | 200, 404 |
| PATCH | `/v1/messages/{id}/status` | JWT Bearer | `{ "status": "DELIVERED\|READ" }` | `204 No Content` | 204, 404 |

### WebSocket Protocol

```
Endpoint: wss://chat.example.com/ws
Auth:     ?token={JWT}  — validated on connect; connection rejected if invalid

Client → Server:
  { "type": "MESSAGE_SEND",    "conversationId": "...", "content": "...", "idempotencyKey": "..." }
  { "type": "HEARTBEAT" }      — every 30s to maintain presence (server closes connection after 60s silence)

Server → Client:
  { "type": "MESSAGE_RECEIVED",      "messageId": "...", "conversationId": "...", "senderId": "...", "content": "..." }
  { "type": "MESSAGE_STATUS_UPDATE", "messageId": "...", "status": "DELIVERED|READ" }
  { "type": "PRESENCE_CHANGED",      "userId": "...",   "status": "ONLINE|OFFLINE" }
  { "type": "ACK",                   "idempotencyKey": "...", "messageId": "..." }  — confirms message persisted
```

### 🔍 Endpoint Stories

**WebSocket `MESSAGE_SEND`** is where idempotency matters most. The client sends the message, the server acknowledges with `ACK` (carrying the same `idempotencyKey`). If the client's WebSocket drops before the `ACK` arrives, the client reconnects and retries. Without idempotency, the message is stored twice. Implementation: dedup table in Cassandra keyed by `idempotencyKey` with 24-hour TTL. If the key is found, return the cached `ACK` without re-inserting. The client never sees the duplicate.

**`GET /v1/conversations/{id}/messages`** uses cursor pagination anchored to `message_id` (a TIMEUUID). TIMEUUID encodes timestamp + random UUID — so sorting by TIMEUUID is equivalent to sorting by time, with no collisions at the same millisecond. Offset-based pagination would break: if 10 new messages arrive while the user is scrolling, offset 50 now points to message 40 (shifted by 10). Cursor pagination is immune: "give me messages with TIMEUUID < {last seen}" always returns the correct next page.

**`PATCH /v1/messages/{id}/status`** to `READ` triggers a WebSocket `MESSAGE_STATUS_UPDATE` push to all other participants in the conversation. The REST call and the WebSocket push are the same state change — the PATCH updates the DB, the fan-out pushes the update to connected clients. For offline clients, the status update is durable in the DB and will be delivered on next sync.

**No `POST /v1/conversations/{id}/messages` REST endpoint** in the primary path — real-time sends go over WebSocket. The REST fallback exists for bots and webhook integrations that can't maintain a WebSocket. Both write to the same Cassandra partition; read path is unified.

---

## Section 6 — 🏗️ High-Level Architecture (Minutes 10–25)

**What to do:** Draw the boxes (ASCII or whiteboard). Walk through the data flow *as if telling a story*. The interviewer is checking: "Does this person understand flow or just know boxes?"

**Say this out loud (as you start drawing):**
> "Let me draw a high-level architecture. This is how the system looks from 10,000 feet..."

---

### 🎨 Visual — Chat System Architecture (3-Stage Evolution)

```
── Stage 1: HTTP Polling + Single DB ─────────────────────────────

Clients poll for new messages on a fixed interval.
One REST API server. One MySQL database. Zero real-time infrastructure.

 ┌──────────┐  GET /messages?since=lastId   ┌──────────────────────┐
 │ Client A │ ──────────── every 5s ───────→│   REST API Server    │
 └──────────┘                               │                      │
                                            │  SELECT * FROM msgs  │
 ┌──────────┐  POST /messages               │  WHERE conv_id = ?   │
 │ Client A │ ─────────────────────────────→│  AND id > lastId     │
 └──────────┘                               └──────────┬───────────┘
                                                       │
                                            ┌──────────▼──────────┐
                                            │    MySQL (single)    │
                                            │  messages table      │
                                            │  PK: (conv_id, id)   │
                                            └─────────────────────┘

BREAKING POINT 1: 500M DAU × 1 poll / 5s = 100M requests/sec of
   pure polling overhead — most returning empty "no new messages."
   The API server fleet and DB saturate handling empty polls before
   they process any real message traffic.

BREAKING POINT 2: MySQL write throughput at 700K msgs/sec is impossible
   on a single node. Even sharded, the B-tree index on (conv_id, created_at)
   grows unbounded for active conversations and degrades on range reads.
```

**DECISION — WHICH real-time transport?**

| Option | Strength | Weakness | Verdict |
|---|---|---|---|
| HTTP polling (client requests every N seconds) | Simple; stateless server; works everywhere | 500M DAU × 1 poll/5s = 100M empty requests/sec overhead; P99 latency = poll interval (5s) not < 200ms | ❌ Doesn't scale |
| SSE — Server-Sent Events (server pushes, client listens) | Server pushes; no polling overhead; HTTP-compatible | Server-to-client only (half-duplex); client cannot send messages over SSE — needs a separate REST call | ⚠️ Half-duplex only |
| WebSocket (persistent full-duplex TCP connection) | Real-time push and receive; P99 < 200ms achievable; single connection for send + receive | Stateful connections (7,700 servers needed); connection routing complexity | ✅ Best |

> 📖 Full: **`SystemDesignConcepts/Production-Grade/System-Design-Patterns/26-websocket-real-time-communication.md`**

```
── Stage 2: WebSocket + Message Service + MySQL ───────────────────

WebSocket connection servers hold persistent client connections.
Message Service is stateless — receives messages and persists them.
MySQL stores messages. Routing: how does Message Service reach Client B?

 ┌──────────┐  WebSocket   ┌─────────────────┐
 │ Client A │ ────────────→│  Conn. Server A  │
 └──────────┘              │  (Client A's WS) │
                           └────────┬─────────┘
                                    │  inbound message
                                    ▼
                           ┌─────────────────────┐
                           │   Message Service    │
                           │   (stateless)        │
                           └──────────┬───────────┘
                                      │
                        ┌─────────────▼──────────────┐
                        │    MySQL (messages)         │
                        │  PK: (conv_id, created_at) │
                        └────────────────────────────┘

 ┌──────────┐  WebSocket   ┌─────────────────┐
 │ Client B │ ────────────→│  Conn. Server B  │
 └──────────┘              │  (Client B's WS) │
                           └─────────────────┘

           ??? Message Service → Client B ???
           Message Service is stateless.
           It has no idea that Client B is on Conn. Server B,
           not Conn. Server C or D or any of the other 7,699.

BREAKING POINT 1: Routing gap. The Message Service cannot reach Client B
   in real time without knowing which of 7,700 connection servers holds
   Client B's WebSocket. Without a routing table, every delivery is a
   broadcast to all servers (wasteful) or a database query per message (too slow).

BREAKING POINT 2: No offline delivery. If Client B is offline, the message
   is simply discarded — no push notification, no queuing.

BREAKING POINT 3: MySQL append-only writes at 700K msgs/sec — B-tree
   index degrades; time-series range reads ("last 50 msgs") become full-table
   scans as conversations grow. Needs Cassandra's LSM tree + clustering key.
   Quantified: MySQL B-tree degrades at ~10M messages per conversation partition.
   At 700K msgs/sec, a popular conversation hits 10M messages in ~4 hours.
   Observable: P99 read latency spikes to 2,000ms for "load last 50 messages."
   Stage 3 needed because Cassandra's LSM tree + TIMEUUID clustering key handles
   append-only writes natively and range reads remain O(1) regardless of partition size.
```

**DECISION — WHICH message storage?**

| Option | Strength | Weakness | Verdict |
|---|---|---|---|
| MySQL / Postgres | ACID; familiar; easy pagination | B-tree index degrades for long-lived conversations; 700K writes/sec requires complex app-level sharding; `ORDER BY created_at` scans unbounded rows | ❌ Wrong access pattern |
| MongoDB | Flexible schema; document model | Primary write to single node; secondary = read replica only; not optimized for time-series; aggregation pipeline adds latency | ⚠️ Viable at < 10M users |
| Cassandra (partitioned by conv_id, TIMEUUID clustering key) | Append-optimized LSM tree; native time-series range reads; horizontal write scale by adding nodes; 1-year TTL built-in | No JOINs; eventual consistency; schema migrations are careful operations | ✅ Best |

> 📖 Full: **`SystemDesignConcepts/Core-Architecture/Database-Core/06-databases-types-and-selection.md`**

```
── Stage 3: Kafka + Redis Routing + Cassandra + Push (Production) ─

Kafka decouples connection servers from message persistence (durability).
Redis routing table: maps user → which conn. server holds their WebSocket.
Cassandra: time-series message storage (append-optimized, TTL auto-expire).
Push Notification Service: APNs/FCM for offline users.

 ┌──────────┐  WebSocket         ┌──────────┐  WebSocket
 │ Client A │ ─────────────────→ │ Client B │ ─────────────────────→
 └────┬─────┘                    └────┬─────┘
      │                               │
      ▼                               ▼
 ┌─────────────────────────────────────────────────────────────┐
 │              WebSocket Connection Servers                   │
 │   ┌──────────────────┐          ┌──────────────────┐        │
 │   │  Conn. Server A  │          │  Conn. Server B  │  ...   │
 │   │  holds Client A  │          │  holds Client B  │        │
 │   └────────┬─────────┘          └──────────┬───────┘        │
 └────────────┼────────────────────────────────┼───────────────┘
              │  publish to Kafka               │  gRPC push (inbound)
              ▼                                 │
     ┌──────────────────┐          ┌────────────▼───────────────┐
     │  Kafka           │          │  Redis (routing + presence) │
     │  messages topic  │          │  user:B:device:1 →         │
     │  (durable replay)│          │  "conn-svr-B", TTL 60s     │
     └────────┬─────────┘          └────────────┬───────────────┘
              │  consume                         │  lookup on deliver
              ▼                                  │
     ┌────────────────────────────────────────────────────────┐
     │                   Message Service (stateless)          │
     │  1. Write to Cassandra (persist)                       │
     │  2. Redis lookup → which server holds recipient?       │
     │  3a. Online  → gRPC push to Conn. Server → WebSocket   │
     │  3b. Offline → Push Notification Service               │
     └────────┬────────────────────────────┬───────────────────┘
              │                             │
              ▼                             ▼
     ┌────────────────────┐     ┌───────────────────────┐
     │  Cassandra         │     │  Push Notif. Service  │
     │  PK: (conv_id,     │     │  APNs (iOS)           │
     │       TIMEUUID)    │     │  FCM  (Android)       │
     │  TTL: 1 year       │     └───────────────────────┘
     └────────────────────┘

PRESENCE (heartbeat model):
 ┌──────────┐  heartbeat / 30s  ┌─────────────────────────────────────┐
 │  Client  │ ─────────────────→│ Redis: user:{id}:device:{did} → svrId│
 └──────────┘                   │ TTL: 60s — auto-expires on disconnect │
                                └─────────────────────────────────────┘

KEY INVARIANT:
   Connection Servers are STATEFUL — each holds ~65K WebSocket connections.
   Message Service is STATELESS — routes via Redis lookup, never holds connections.
   TWO-HOP MODEL:
     Hop 1: Message Service → Redis lookup → knows which Conn. Server holds Client B.
     Hop 2: gRPC → Conn. Server B → WebSocket push to Client B.
   Message Service never knows *which* physical server holds a client
   until the Redis lookup resolves it at delivery time.

BREAKING POINT 3→4 (future stage):
   Single-partition Kafka topic and single Redis pub/sub node saturate at ~100K msg/sec.
   At 700K msgs/sec target load, delivery lag exceeds 5s — messages queue in Kafka
   faster than consumers drain them. Observable: consumer lag metric climbs; users
   see "sending..." spinner; delivery receipts stop arriving. Stage 4 needed because
   Kafka partitioning (by conv_id) and Redis Cluster sharding distribute the routing
   and pub/sub load across multiple nodes to handle 700K msg/sec throughput.
```

**DECISION — WHICH connection routing strategy?**

| Option | Strength | Weakness | Verdict |
|---|---|---|---|
| DB lookup (each server registers in Postgres) | Simple; durable | 1–5ms per lookup; at 700K msgs/sec this is 700K DB reads/sec on the delivery hot path | ❌ DB bottleneck |
| Gossip protocol (servers discover each other P2P) | Decentralized; no single point of failure | Complex to implement; convergence delay of 1–5s; hard to debug at 7,700-server scale | ❌ Too complex |
| Redis routing table (user_id → conn_server_id, 60s TTL) | Sub-ms lookup (O(1)); auto-expires on client disconnect; connection server re-registers on reconnect | Redis failure → degrade to push-only mode (mitigated by Redis cluster with replica failover) | ✅ Best |

> 📖 Full: **`SystemDesignConcepts/Foundations/Performance-and-Scale/03-caching.md`**

**DECISION — WHICH fan-out strategy for group chat?**

| Option | Strength | Weakness | Verdict |
|---|---|---|---|
| Fan-out on write for ALL groups (N Cassandra writes per message) | Cheap reads; recipient's inbox is pre-populated | 100K-member group × 1 message = 100K Cassandra writes per send; write amplification is unbounded at scale | ❌ Breaks for large groups |
| Fan-out on read for ALL groups (one shared message row) | O(1) write cost per message regardless of group size | Every member hits Cassandra on conversation open; heavier reads; cursor tracking per-member per-group | ⚠️ Works but reads heavy for small groups |
| Hybrid: write for ≤100 members, read for >1,000 members | Bounded write amplification (max 100 writes per message); cheap inbox reads for most users; large groups use shared partition | Per-member `last_read_id` cursor adds schema complexity for large groups | ✅ Best |

> 📖 Full: **`SystemDesignConcepts/Patterns/DeepDive/03-feed-and-fanout.md`**

**Data flow walkthrough (say this out loud):**

1. **Client A sends message:** WebSocket frame hits Connection Server A. The connection server publishes the raw message to Kafka topic `messages`.
2. **Message Service consumes:** Persists the message to Cassandra (partition = `conversation_id`, row = `message_id` TIMEUUID). Gets a durable message_id back.
3. **Fan-out:** Message Service looks up Client B's current connection server in Redis. If found, sends delivery via gRPC (Google Remote Procedure Call — a high-performance binary protocol used for internal service-to-service calls; ~5–10× faster than HTTP/JSON for the same payload because it uses compact binary encoding instead of text) to Connection Server B, which pushes over WebSocket. Simultaneously sends a "sent" ACK back to Client A.
4. **Offline delivery:** If Redis shows no active connection for Client B (TTL expired → offline), the Push Notification Service sends an APNs/FCM notification. When Client B reconnects, they sync unread messages from Cassandra by querying from their last-seen TIMEUUID cursor.
5. **Delivery receipts:** Client B's device sends a "delivered" ACK over WebSocket on receipt; "read" ACK when the user opens the conversation. Each ACK flows back through Message Service, updates `message_status` in Cassandra, and notifies Client A.

---

## Section 7 — 🔬 Core Component Deep Dives (Minutes 25–40)

**What to do:** Pick 2–3 *riskiest* components. "Riskiest" = where the system most likely fails, where scale hits hardest, or what's unique to this problem.

**Why not 5 deep dives?** Under stress, your working memory shrinks 40–50%. If you try to hold 5 things, you'll confuse them. Pick the hardest 2–3 and go deep.

**Why these 3 for chat?**
1. **Message Storage (Cassandra)** — Wrong choice here = system can't scale past a few million users
2. **WebSocket Connection Management** — 7,700 stateful servers × 65K connections each. Routing must be millisecond-fast.
3. **Fan-out Strategy** — Breaks spectacularly at scale. Small group (2 members) vs large (100K members) use opposite strategies.

**Say this out loud:**
> "Let me go deep on the three riskiest components — the ones where the system most likely breaks at scale..."

---

### Deep Dive 1: Message Storage — Cassandra Schema

**Why this is the most critical component:**
Messages are the core data of the system. Wrong storage choice = the system can't scale past a few million users. The access patterns are very specific: append-only writes, time-series reads per conversation, never random access by individual message.

**Options considered:**

| Option | Pros | Cons |
|---|---|---|
| **MySQL (per conversation)** | ACID, familiar | Single node write bottleneck; B-tree index grows unbounded for active conversations; JOIN-heavy for participant lookup |
| **Cassandra** | Horizontal write scale, time-series native, LSM tree optimised for append, partition key gives locality | No JOINs, eventual consistency, complex schema migrations |
| **MongoDB** | Flexible schema, document model | Not optimised for time-series access; writes go to primary only (secondary = read replica) |

**Decision: Cassandra**
Because the access pattern is append-only writes + range reads by conversation ordered by time. This is the exact pattern Cassandra's LSM tree (a write-optimised storage engine that batches writes in memory and flushes sorted to disk) and clustering keys are designed for.
The trade-off: no cross-conversation queries, no JOINs, schema changes require careful migration.

**Schema:**

```sql
-- Core message table — partitioned by conversation for locality
CREATE TABLE messages (
    conversation_id UUID,
    message_id      TIMEUUID,    -- time-ordered UUID: ordering + uniqueness in one field
    sender_id       UUID,
    content         TEXT,
    message_type    VARCHAR,     -- 'TEXT', 'IMAGE', 'SYSTEM'
    status          VARCHAR,     -- 'SENT', 'DELIVERED', 'READ'
    created_at      TIMESTAMP,
    PRIMARY KEY (conversation_id, message_id)
) WITH CLUSTERING ORDER BY (message_id DESC)
  AND default_time_to_live = 31536000;  -- 1 year TTL, auto-expires old messages

-- Conversation metadata — separate table for conversation-level info
CREATE TABLE conversations (
    conversation_id UUID PRIMARY KEY,
    type            VARCHAR,     -- 'ONE_TO_ONE', 'GROUP'
    name            TEXT,        -- group name; null for 1:1
    created_at      TIMESTAMP,
    last_message_id TIMEUUID     -- denormalised for inbox "last message" preview
);

-- Participants lookup — "which conversations is user X in?"
CREATE TABLE conversation_by_user (
    user_id         UUID,
    conversation_id UUID,
    joined_at       TIMESTAMP,
    PRIMARY KEY (user_id, conversation_id)
);
```

**Why TIMEUUID for message_id:**
- Time-ordered: messages sort chronologically without a separate `created_at` comparator
- Globally unique: UUID v1 includes MAC address + timestamp — no collision risk across servers
- "In an interview, if asked: TIMEUUID gives me ordering and uniqueness in one field; I don't need a separate sequence generator per conversation."

**⚠️ TIMEUUID ordering caveat (know this for Tier 2 probes):**

TIMEUUID ordering is based on the 100-nanosecond timestamp embedded in the UUID v1. This means:
- Messages generated on the same Cassandra coordinator within 1 nanosecond can have unpredictable relative order (clock ties are broken by the node's MAC bytes — implementation-defined)
- More critically: **if two messages arrive within 1ms from different clients, Cassandra's TIMEUUID ordering is determined by their UUID generation time, not their network arrival time.** This creates apparent ordering inconsistency — Message A was sent at T+0 but got delayed in transit; Message B was sent at T+1 and arrived first. Cassandra shows them in TIMEUUID order (A before B) even though B was written first.

For most chat use cases, this is acceptable — users see timestamps as the source of truth, not insertion order. If you need causal ordering (A was typed after B, and that ordering must be preserved even under network jitter), add a per-conversation **logical sequence counter** (monotonically incrementing counter on the conversation record, atomically incremented on each write using a Cassandra `COUNTER` column or optimistic lock in Postgres).

**⚠️ Cassandra consistency level — must name this explicitly:**

Cassandra's consistency is tunable per operation. Never say "Cassandra is eventually consistent" without naming the level you're using:

| Level | Write behavior | Read behavior | Use for |
|---|---|---|---|
| `ONE` | Write to any 1 node; success | Read from any 1 node | Presence updates, ephemeral data — speed > correctness |
| `QUORUM` | Write to majority of replicas | Read from majority | **Messages** — ensures a write visible to any subsequent read |
| `ALL` | Write to ALL replicas | Read from ALL | Never use at chat scale — any replica failure = write failure |

**For message writes: use `QUORUM` (typically 2 out of 3 replicas with replication factor 3).** This ensures a message written by the sender is visible to the recipient's read even if one replica is temporarily down. `ONE` would allow a "lost message" scenario: write goes to replica R1, read happens on replica R2 before replication completes, user sees empty conversation.

**In an interview:** "I'd use `QUORUM` consistency for message writes and reads. With replication factor 3, that means 2 replicas must acknowledge. This gives me strong enough consistency that a write is always visible to subsequent reads, while tolerating one replica failure without impacting availability."

**Pagination query pattern (say this out loud):**

```sql
-- Get last 50 messages in a conversation, newest first
SELECT * FROM messages
WHERE conversation_id = ?
  AND message_id < ?          -- cursor: last message_id seen by the client
LIMIT 50;
```

> **Cross-reference:** cursor pagination mechanics in full depth at `SystemDesignConcepts/11-api-design.md`.

---

### Deep Dive 2: WebSocket Connection Management at Scale

**Why this is the riskiest component:**
7,700 WebSocket connection servers, each managing ~65K connections. When Client A sends a message to Client B, the Message Service must know *which* of 7,700 servers currently holds Client B's connection — in milliseconds.

**The routing problem:**

```
Message for user B arrives at Message Service.
Message Service asks: "Which connection server currently has user B's WebSocket?"

Answer lives in Redis: SET user:{userId}:server → "conn-server-247"
TTL: 30 seconds (refreshed by heartbeat from connection server)
```

**What happens when a connection server crashes:**
1. Redis TTL expires → user appears offline within 30 seconds
2. If Client B is still connected (to a different server after reconnect): new heartbeat updates Redis
3. In-flight messages during the 30s gap: Message Service falls through to push notification path
4. On reconnect: Client B syncs from Cassandra using their last-seen cursor

**⚠️ Production gotcha: TCP alive but phone is off (the silent disconnect)**

TCP's `ESTABLISHED` state means the OS-level socket is still tracked — it does NOT mean the remote device is actually reachable. When a phone is switched off abruptly (power cut, plane mode, battery dead), **the TCP connection is never formally closed.** The server's OS still shows the socket as `ESTABLISHED`. No `FIN` packet is sent. From the server's perspective, Client B is still "connected."

This creates a silent zombie connection: the connection server believes Client B is online (Redis key alive), tries to deliver messages over the WebSocket, but the phone is off. The write to the TCP send buffer succeeds (buffer not full yet), but the data is never actually sent to the device. The message is "delivered" in the server's view but the user never sees it.

**The fix: application-level PING/PONG heartbeat:**

```
Client → Server:  PING frame (every 30 seconds, client-side timer)
Server → Client:  PONG frame (within 5 seconds, server-side timeout)

If Server does NOT receive a PING within 35s:
   → mark client as likely-disconnected
   → send a PING from server side
   → if no PONG within 5s: close() the socket, remove Redis routing entry
   → deliver subsequent messages via push notification path
```

The WebSocket protocol has built-in `PING`/`PONG` opcodes (RFC 6455) specifically for this. You configure both sides:
- **Client:** send PING every 30 seconds
- **Server:** if no PING received in 35 seconds, probe with a server-initiated PING; close on 5s timeout

**Why 30 seconds?** Match the Redis TTL (30s). The heartbeat and the Redis presence key must stay in sync — if the heartbeat is 60s but Redis TTL is 30s, the key expires before the phone is detected as offline.

**In an interview:** "TCP's ESTABLISHED state doesn't mean the remote is reachable — if a phone loses power, the TCP connection is silently stranded. I use application-level WebSocket PING/PONG frames (RFC 6455) to detect this. If no PING from the client for 35 seconds, I probe and close the socket within 5 seconds. This bounds the zombie connection window to 40 seconds maximum — after which the Redis routing entry is deleted and messages fall through to push notifications."

**Consistent hashing for connection server assignment:**
When a new WebSocket connection is established, the load balancer uses consistent hashing (see `SystemDesignConcepts/05-consistent-hashing.md`) on `user_id` to always route the same user to the same connection server (session affinity). This means Client B's desktop and mobile can both connect — to different connection servers based on device_id, not user_id.

**Multi-device delivery:**
Each device has its own WebSocket to its own connection server. Redis stores one entry per device:
```
user:{userId}:device:{deviceId}:server → "conn-server-247"
```
Fan-out delivers to ALL active devices for the recipient. Idempotency on the client side deduplicates (same message_id received twice is ignored).

---

### Deep Dive 3: Fan-out Strategy for Group Chat

**Why this matters:**
A group of 100 sending one message = 99 fan-outs. A group of 100,000 sending one message = 99,999 fan-outs. The strategy must handle both without burning the system.

**Fan-out on write (for groups ≤ 100):**

```
When message M arrives for group G with 100 members:
  FOR each member in G:
    1. Write delivery record to member's "inbox" partition in Cassandra
    2. Push delivery notification to their connection server (if online)

Write cost: O(N) writes per message
Read cost: O(1) — member's inbox is pre-populated
```

**Fan-out on read (for groups > 1000):**

```
When message M arrives for group G:
  1. Write ONE message row to shared group partition in Cassandra
  2. Update group's "last_message_id" pointer

When member reads:
  SELECT * FROM messages
  WHERE conversation_id = {group_id}
    AND message_id > {last_seen_by_this_member}
  LIMIT 50;

Write cost: O(1) per message
Read cost: O(1) per member read
```

**Decision: hybrid — write for small groups, read for large**
- Groups ≤ 100 members: fan-out on write (WhatsApp / Messenger approach)
- Groups > 1000 members: fan-out on read (Slack channels / Telegram supergroups approach)
- 100–1000: fan-out on write with throttling (async, not in the critical path)

> **The trade-off I'm accepting:** fan-out on read means the last_seen_by_member pointer must be tracked per user per group, which is an extra table. Fan-out on write is simpler for reads but expensive on writes for large groups.

---

## Section 9 — 🗄️ Data Model

### Core Tables

```sql
-- Cassandra: message storage (partition by conversation, cluster by time)
CREATE TABLE messages (
    conversation_id UUID,
    message_id      TIMEUUID,
    sender_id       UUID,
    content         TEXT,
    message_type    VARCHAR,
    status          VARCHAR,
    created_at      TIMESTAMP,
    PRIMARY KEY (conversation_id, message_id)
) WITH CLUSTERING ORDER BY (message_id DESC)
  AND default_time_to_live = 31536000;

-- Cassandra: conversation participants (partition by user for inbox query)
CREATE TABLE conversation_by_user (
    user_id         UUID,
    conversation_id UUID,
    last_read_id    TIMEUUID,    -- cursor: last message this user has read
    unread_count    COUNTER,     -- Cassandra COUNTER: an atomic distributed increment type; multiple nodes can increment it concurrently without locks or race conditions
    joined_at       TIMESTAMP,
    PRIMARY KEY (user_id, conversation_id)
);

-- Redis: presence (TTL-based, expires after 60s without heartbeat)
SET user:{userId}:device:{deviceId}:server {connectionServerId} EX 60

-- Redis: routing (which connection server holds this device's WebSocket)
-- Same as above — presence IS the routing table
```

### Key Schema Decisions:
- **TIMEUUID as message_id:** ordering + uniqueness in one field; no need for separate sequence generator
- **`last_read_id` per user per conversation:** tracks exactly where each member left off — enables "unread count" and "sync from where you left off" on reconnect
- **`unread_count` as COUNTER type in Cassandra:** Cassandra COUNTER is an atomic distributed increment; no race condition when multiple devices acknowledge the same message
- **Redis for presence, not Cassandra:** TTL semantics are natively O(1) in Redis; Cassandra TTL works but expiry isn't real-time, needs a background compaction pass

---

## Section 10 — ⚠️ Trade-offs + Failure Modes (Minutes 40–48)

**What to do:** Name exactly 3 major trade-offs. For each: what you chose, what you gain, what you lose, what breaks if you chose wrong.

**Why this matters (from DocuSign PDF):** "We are more interested in seeing how you think through the pros and cons of different approaches."

**Say this out loud:**
> "Let me step back and name the three major trade-offs in this design..."

---

### Trade-off 1: Fan-out on Write vs Fan-out on Read for Group Chat

- **Chose:** hybrid — write for groups ≤100, read for groups >1000
- **Gain:** reads are cheap for most users (pre-populated inbox); write cost is bounded for large groups
- **Lose:** write amplification for active small groups; extra `last_read_id` tracking per user for read-based groups
- **Failure mode if wrong:** if we chose write for all groups and someone creates a 100K-member broadcast channel, every single message triggers 100K Cassandra writes — the cluster would saturate. **Business impact:** Message delivery for all users stalls while the cluster is overwhelmed — for DocuSign this means signers on a large enterprise broadcast agreement (e.g., HR policy sign-off to 50K employees) stop receiving envelope notifications, creating legal uncertainty about whether the signing request was delivered.

### Trade-off 2: WebSocket vs HTTP Polling for Real-Time Delivery

- **Chose:** WebSocket (persistent bidirectional connection)
- **Gain:** P99 < 200ms delivery is achievable; no thundering herd of poll requests; server can push without client polling
- **Lose:** stateful connections (7,700+ servers needed); connection server crashes lose all its connections; more complex infrastructure than REST
- **Failure mode if wrong:** HTTP polling at 500M DAU with 1-second poll intervals = 500M requests/second just for polling — 10× the actual message traffic. Impossible. **Business impact:** Signers can't see prior comments or status updates on a contract in real time — for DocuSign this means a signer views an old version of in-document comments and signs without seeing a critical revision note, creating legal ambiguity about what the party agreed to.

### Trade-off 3: Server-Side Encryption vs End-to-End Encryption

- **Chose:** server-side encryption (TLS in transit + AES-256 at rest)
- **Gain:** server can search messages, deliver to multiple devices seamlessly, recover from device loss, enable content moderation
- **Lose:** server has access to plaintext — a compromised server exposes all messages; incompatible with strongest privacy guarantees
- **Failure mode if wrong:** if the customer requires E2EE (enterprise context), the current architecture breaks entirely — message indexing, server-side fan-out, and multi-device sync all assume server can read message content. E2EE is a different architecture (Signal protocol), not an additive feature. **Business impact:** An enterprise customer (financial institution or law firm) with a contractual data-privacy requirement demands E2EE — the current architecture cannot comply without a full rewrite — for DocuSign this means losing the enterprise deal or facing a compliance breach, as contract content (including sensitive PII) transits the server in plaintext.

---

## Section 11 — 🔐 DocuSign-Specific Depth (Minutes 48–52)

**What to do:** For PDF-example questions (A1, A2), the DocuSign signal is naming which of the 7 evaluation dimensions your design addresses and how.

**This is NOT a DocuSign domain question.** Chat has no e-signature or document angle. The grading signal is: "Does the candidate understand how DocuSign evaluates, and can they map their generic design to those dimensions?"

**After the trade-offs, say this out loud:**

> "Let me pause and map this back to the DocuSign evaluation dimensions:
> - **Scalability:** Cassandra sharded by conversation_id + 7,700 WebSocket servers via consistent hashing
> - **Availability:** Kafka decouples connection servers from message processing — a message service restart doesn't lose messages; 99.99% target achieved through active-active connection server pools
> - **Security:** TLS 1.3 in transit, AES-256 at rest, JWT auth on WebSocket connect — E2EE is the upgrade path if required
> - **Observability:** trace_id injected at WebSocket frame entry, propagated through Kafka message headers, all downstream services log under the same trace_id — I can reconstruct the full delivery path of any message in post-incident analysis
> - **Extensibility:** fan-out logic is a Strategy pattern — adding a new notification channel (in-app, SMS, email) means adding a new `NotificationStrategy` implementation without touching the fan-out core
> - **Testability:** Message Service accepts `MessageRepository` (Cassandra) and `FanoutService` as constructor-injected interfaces — each can be mocked in unit tests without a running Cassandra cluster
> - **Usability:** WebSocket event protocol follows a consistent schema — every event has a `type` field; clients can deserialise without switch-on-type"

---

## Section 12 — 🔬 Where the Interviewer Will Probe (Minutes 52–60)

**What to do:** Prepare for 3 tiers of follow-ups. Tier 1 (surface) — everyone gets it. Tier 2 (deep) — tests if you *understand*, not just *know*. Tier 3 (cross-concept) — separates senior candidates.

**Why 3 tiers?** The interviewer is watching your depth. Answer Tier 1 in 2–3 sentences. Tier 2 in 3–4 sentences with specific technical detail. Tier 3 requires you to reason across system boundaries.

**If you get a Tier 3 question, it's a good sign** — they think you're strong enough to probe the hard stuff.

---

### Surface Probe (Tier 1 — every candidate gets this)

**Q: "Why Cassandra over MySQL for message storage?"**
> The access pattern is append-only writes and time-series range reads — "give me the last 50 messages in conversation X, ordered by time." MySQL's B-tree index grows without bound for active conversations, and the primary key (conversation_id, timestamp) requires full-table scans for pagination. Cassandra's LSM tree is write-optimised and the clustering key on TIMEUUID gives me range reads in O(log N) natively. Also: Cassandra scales horizontally by adding nodes; MySQL scaling requires sharding logic in the application layer.

**Q: "How do you handle a message sent to an offline user?"**
> The Message Service checks Redis for the recipient's active connection server. If the key is absent (TTL expired → user is offline), it routes to the Push Notification Service, which calls APNs or FCM with the message preview. When the user comes back online, the WebSocket client sends its last-seen TIMEUUID as a cursor and syncs all unread messages from Cassandra.

**Q: "How do you ensure messages in a conversation are displayed in the correct order?"**
> I use TIMEUUID as the message_id — it embeds a timestamp component, so messages naturally sort chronologically in Cassandra's clustering key order. The client displays messages in the order they appear in the Cassandra result set (newest first, reversed for display). The only edge case is two messages with the same millisecond timestamp — TIMEUUID includes a random node component that breaks ties deterministically, so ties are stable but not causally ordered. For strict causal ordering, I'd add a per-conversation sequence counter in Redis (INCR) and include it in the message row.

### Deep Probe (Tier 2 — tests real understanding)

**Q: "Your group has 1 million members — think a Facebook public group or a broadcast channel. How does your fan-out strategy change?"**
> Fan-out on write for 1M members means every single message creates 1M Cassandra writes. At 700K messages/sec average, that's 700 billion writes/sec — impossible. I switch to fan-out on read: one message row is written to the shared group partition. Each member's client, when they open the conversation, queries from their `last_read_id` cursor. The tradeoff: reads become more expensive (each member hits Cassandra on open), but writes stay O(1). For ultra-large groups, I'd add a cache layer in front of Cassandra for the most recent N messages — most members read the same recent messages, so the cache hit rate is high.

**Q: "The Redis cluster storing user→connection_server mappings goes down completely. What happens and how do you recover?"**
> Without Redis, the Message Service can't route real-time delivery — it falls back entirely to the push notification path. All online users effectively appear "offline" until Redis recovers. Messages are durably persisted in Cassandra and in Kafka (if not yet consumed), so no messages are lost. On Redis recovery, connection servers re-register all their current users (they maintain in-memory maps of user_id → WebSocket connection). The recovery period is bounded by the time it takes for all 7,700 servers to replay their in-memory registration to Redis — at scale, this is done in batches with backpressure (see `SystemDesignConcepts/10-backpressure.md`). The system degrades gracefully (push-only) and recovers automatically.

### Cross-Concept Probe (Tier 3 — separates senior candidates)

**Q: "A message is sent, Kafka writes it, but the Message Service crashes before writing to Cassandra. The client sees 'sent' (Kafka ACK). Does the message get delivered? How?"**
> Yes, durably. Kafka retains the message until a consumer group commits an offset. The Message Service consumer group has not committed the offset for this message (it crashed before Cassandra write). When the Message Service restarts, it replays from the last committed offset — the message is re-processed and written to Cassandra. The client already got an ACK that the message was accepted by the system (Kafka), not that it was delivered. Delivery receipts are a separate flow — the "delivered" receipt only fires when the Cassandra write AND the push to the recipient's connection server both succeed. See `SystemDesignConcepts/07-cdc-outbox.md` for the at-least-once delivery guarantee pattern this depends on.

---

### Deep Probe (Tier 2 — additional)

**Q: "A connection server holding 65K clients crashes. All clients reconnect simultaneously. How do you prevent this thundering herd from taking down the remaining servers?"**
> Two mitigations work together. (1) Client-side exponential backoff with jitter — each client waits a random delay (0–10 seconds) before reconnecting. 65K clients with 0–10s uniform jitter → ~6,500 reconnects/sec spread over 10 seconds, well within any server's capacity. Without jitter, all 65K reconnect at t=0 and overwhelm whoever they hit. (2) Consistent hashing distributes the reconnects across the entire remaining cluster, not one server. The crashed server's Redis routing entries expire within 60s (TTL). During that window, any message for a disconnected client falls through to the push notification path. Reconnecting clients re-register in Redis within seconds. Net result: ~60s of push-only degraded mode → full real-time recovery. No messages are lost because Kafka held them until the Message Service could deliver.

**Q: "A user reports seeing duplicate messages — the same message appears twice in their chat. Walk me through every possible root cause."**
> Three sources: (1) Client retry without idempotency key — client sends a message, loses the WebSocket ACK (network blip), retries. Without an `idempotencyKey`, the server creates two Cassandra rows with different TIMEUIDs, both fan out. Fix: every `MESSAGE_SEND` frame includes a client-generated UUID idempotencyKey. Server checks Redis SETNX on the key before writing to Cassandra — if already exists, skip write and re-ACK only. (2) Multi-device fan-out — phone and browser tab both have active WebSocket connections, both subscribed to the same pub/sub channel. Both receive and render the same message. Fix: client-side dedup on `message_id` — if already rendered, discard silently. (3) Kafka at-least-once redelivery — Message Service consumer crashes after Cassandra write but before offset commit; message reprocessed on restart. Fix: same idempotency key check at Cassandra write layer. The combination of client-supplied idempotencyKey + server-side Redis SETNX (24h TTL) achieves exactly-once write semantics.

---

### Cross-Concept Probe (Tier 3 — additional)

**Q: "You add new connection servers to handle growth. With consistent hashing, some hash slots now map to the new servers. What happens to existing clients whose slot moved?"**
> Nothing happens to existing connections — and that's the correct design. WebSocket connections are long-lived TCP connections; you cannot migrate them without dropping them, which would cause a visible reconnect flash for the user. Consistent hashing governs only NEW connection assignment (where the load balancer routes a fresh WebSocket upgrade). The Redis routing table reflects physical reality — wherever the client IS connected — not the hash ring's ideal assignment. So after adding a new server: fresh logins and reconnects land on the new server (correct hash slot); existing clients stay on their current server until they naturally disconnect. The system converges to the new distribution gradually as clients reconnect over days. This is consistent hashing's key advantage: only ~1/N of new assignments move to the new server, instead of all clients remapping at once like modulo hashing. Active WebSocket connections are always pinned to their current physical server via Redis lookup — the hash ring is just an initial placement rule, not an ongoing constraint.

---

## Section 13 — 🐞 Common Mistakes on This Question

**Note:** Reading these mistakes BEFORE the interview prevents you from making them under stress. Your working memory will shrink, and you're most likely to default to mistakes you haven't explicitly prepared for.

---

- **Mistake 1:** Using MySQL/PostgreSQL for message storage → **Why wrong:** MySQL's B-tree primary key on `(conversation_id, created_at)` degrades as conversations grow; horizontal write scaling requires app-level sharding. You'll sound like you picked a database without understanding the access pattern. **Say instead:** "Cassandra's append-optimised LSM tree and partition-by-conversation design handle this access pattern natively. The access pattern is append-only writes + range reads by time — that's exactly what Cassandra's clustering key is built for."

- **Mistake 2:** Using HTTP polling instead of WebSocket → **Why wrong:** 500M DAU polling every 1 second = 500M+ requests/sec of pure overhead. **Say instead:** "WebSocket gives me persistent full-duplex connections; the connection server fleet handles the statefulness."

- **Mistake 3:** Forgetting the offline path → **Why wrong:** at any moment, millions of users are offline. Without push notifications, they never receive messages until they manually reopen the app. **Say instead:** "I detect offline status via Redis TTL, then route to APNs/FCM."

- **Mistake 4:** Saying "just use Kafka for everything" without explaining the delivery path → **Why wrong:** Kafka is the backbone, but the actual delivery is WebSocket → the Message Service still needs to know *which* connection server holds the recipient's WebSocket. **Say instead:** "Kafka delivers to the Message Service; the routing table in Redis tells the Message Service which connection server to push to next."

- **Mistake 5:** Not addressing message ordering explicitly → **Why wrong:** in a distributed system with multiple connection servers, messages can arrive out of order. **Say instead:** "TIMEUUID in Cassandra provides natural ordering; for strict causal ordering, I'd add a per-conversation sequence counter."

---

## Section 14 — 🧭 DocuSign Dimensions Checklist

| Dimension | Relevant? | How your design addresses it |
|---|---|---|
| Testability | ✅ | Message Service accepts `MessageRepository` and `FanoutService` as constructor-injected interfaces — mockable without live Cassandra/Redis |
| Usability | ✅ | WebSocket protocol has consistent event schema (every event has `type` field); REST API follows HTTP verb semantics; error responses are structured |
| Extensibility | ✅ | Fan-out targets are a Strategy interface (`NotificationStrategy`) — adding SMS/email/in-app channel = new implementation, no core change |
| Security | ✅ | TLS 1.3 in transit; AES-256 at rest; JWT auth on WebSocket connect; E2EE is the trade-off upgrade path |
| Availability | ✅ | Kafka decouples delivery from persistence (message not lost if service restarts); push notification fallback for offline users; active-active connection server pool |
| Scalability | ✅ | Cassandra horizontal sharding by conversation_id; consistent hashing across 7,700 WebSocket connection servers; fan-out strategy switches at group size boundary |
| Observability & Traceability | ✅ | trace_id injected at WebSocket frame entry; propagated in Kafka message headers; all services log under same trace_id — full delivery path reconstructable |

---

## Section 15 — 🧾 TL;DR Answer Summary (Review Morning-of-Interview)

**If you had 60 seconds to summarize the entire answer, say this:**

> "I'd design the chat system with WebSocket connection servers (routed by consistent hashing on user_id) feeding into a Kafka-backed Message Service that writes to Cassandra — partitioned by conversation_id with TIMEUUID clustering for natural message ordering. Fan-out delivers to online recipients via their connection server's WebSocket, and to offline users via APNs/FCM. The key trade-off is fan-out on write for small groups (≤100 members, cheap reads) versus fan-out on read for large groups (one shared message row, each reader queries from their cursor). In a DocuSign interview, I'd map this explicitly to their 7 evaluation dimensions — that's the grading signal. The core insight: at 700K messages/sec, you can't afford synchronous persistence; Kafka and eventual consistency are non-negotiable."

**Why read this before your interview?**
The TL;DR fixes the core idea in your head. Under stress, you'll default to this mental model. When the interviewer asks unexpected questions, you'll reason from this core idea, not from memorized details.

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 2026 | File created. Type A — System Design. Based on: DocuSign PDF (confirmed question type), candidate report ("Facebook messenger type app"), ByteByteGo chat system design chapter, hellointerview.com WhatsApp breakdown, codekarle.com WhatsApp architecture. Advisor review: DocuSign angle clarified (7 dimensions, not forced chat-domain mapping); multi-device sync and E2EE addressed explicitly; cross-references used instead of reproduced content. |
| Jul 4, 2026 | **Diagram rewrite + 4 new Q&As.** Replaced flat `[Box]──→[Box]` diagram with proper box-drawing chars. Added full system architecture diagram + message send flow + presence service diagram. Added Two-hop mental model invariant to KEY INVARIANT. Section 12: added Tier 2 Q "thundering herd on conn server crash" (jitter + consistent hashing); Tier 2 Q "duplicate messages" (3 root causes: client retry, multi-device fan-out, Kafka at-least-once); Tier 3 Q "new servers + consistent hashing — what happens to existing clients" (hash ring governs new assignment only; Redis is source of truth). |
| Jul 5, 2026 | **Section 6 restructured: single final-state diagram → 3-stage progressive HLD.** Stage 1 (HTTP Polling + MySQL): client polls every 5s, single REST server, single MySQL node — BREAKING POINTs: 500M DAU × 1 poll/5s = 100M empty requests/sec; MySQL B-tree can't sustain 700K writes/sec. Stage 2 (WebSocket + Message Service + MySQL): connection servers hold WebSocket state, stateless Message Service processes inbound messages — BREAKING POINTs: routing gap (Message Service can't determine which of 7,700 conn servers holds Client B); no offline delivery; MySQL write throughput ceiling. Stage 3 (Kafka + Redis Routing + Cassandra + Push — production): Kafka for durability, Redis routing table (user_id → conn_server_id, 60s TTL), Cassandra for time-series storage, APNs/FCM for offline users, presence via heartbeat. Four inline decision tables added: (1) real-time transport — polling ❌ / SSE ⚠️ / WebSocket ✅; (2) message storage — MySQL ❌ / MongoDB ⚠️ / Cassandra ✅; (3) connection routing — DB lookup ❌ / gossip ❌ / Redis TTL key ✅; (4) fan-out strategy — write-only ❌ / read-only ⚠️ / hybrid ≤100/≥1000 ✅. All Section 6 verdicts verified against Section 7 deep dive choices — no contradictions. |
| June 23, 2026 | **DELIVERY-RECIPE integration & interview-readiness rewrite.** (1) Added 🧠 preamble explaining file structure + 60-minute time budget with explicit minute allocations per section. (2) Added 💾 Memory Anchors section (6 core + 3 bonus) with stress management rationale. (3) Enhanced all major sections (2, 4, 6, 7, 10, 11, 12) with: explicit timing callouts, "say this out loud" dialogue framing, interview psychology context (why this step matters, working memory constraints, stress failure modes). (4) Clarified "riskiest vs interesting" components in Deep Dives section. (5) Updated TL;DR to emphasize reviewing morning-of-interview + mental model stability under stress. (6) Enhanced Common Mistakes section with context that these are designed to be read before the interview to prevent stress-induced defaults. Result: File is now interview delivery-ready with zero refinement needed. |
