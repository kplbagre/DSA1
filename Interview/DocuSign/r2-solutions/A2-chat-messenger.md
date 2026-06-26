# A2 — Build a Facebook Chat / Messenger Application

> **Read `solution-notes-standards.md` first.** This file is a 60-minute interview answer framework, not a concept reference. Concept-level depth lives in `SystemDesignConcepts/`.

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

## Section 6 — 🏗️ High-Level Architecture (Minutes 10–25)

**What to do:** Draw the boxes (ASCII or whiteboard). Walk through the data flow *as if telling a story*. The interviewer is checking: "Does this person understand flow or just know boxes?"

**Say this out loud (as you start drawing):**
> "Let me draw a high-level architecture. This is how the system looks from 10,000 feet..."

---

### ASCII Architecture Diagram

```
[Client A]─────WebSocket────→[Connection Server A]
[Client B]─────WebSocket────→[Connection Server B]
                                      ↑
                             [Consistent Hashing]
                             (user → conn server routing)
                                      ↑
                             [Redis: user_id → server_id]

─────────────── Message Send Flow ───────────────

Client A sends message →
[Connection Server A]
          ↓
    [Kafka: messages topic]   ←── reliable async backbone
          ↓
  [Message Service]           ←── consumes, fans out
    /           \
[Cassandra]   [Fan-out logic]
(message store) ─→ look up recipients' connection servers in Redis
                ─→ push to [Connection Server B] via internal gRPC
                        ↓
                    Client B receives via WebSocket

─────── Offline path (Client B not connected) ──────

If Redis shows Client B has no active connection:
[Message Service] → [Push Notification Service]
                            ↓
                    [APNs] or [FCM]
                            ↓
                    Client B's device (push alert)
                    Client B reconnects → sync from Cassandra

─────── Presence Service ──────────────

[Client heartbeat every 30s] → [Presence Service]
                                    ↓
                               [Redis: user_id → last_seen TTL]
```

**Data flow walkthrough (say this out loud):**

1. **Client A sends message:** WebSocket frame hits Connection Server A. The connection server writes the raw message to a Kafka topic (`messages`).
2. **Message Service consumes:** Persists the message to Cassandra (partition = `conversation_id`, row = `message_id` TIMEUUID). Gets a durable message_id back.
3. **Fan-out:** Message Service looks up Client B's current connection server in Redis. If found, sends delivery via gRPC (Google Remote Procedure Call — a high-performance binary protocol used for internal service-to-service calls; ~5-10× faster than HTTP/JSON for the same payload because it uses compact binary encoding instead of text) to Connection Server B, which pushes over WebSocket. Atomically, sends a "sent" ACK back to Client A.
4. **Offline delivery:** If Redis shows Client B has no active connection, the Push Notification Service sends an APNs/FCM notification. When Client B comes online, they sync unread messages from Cassandra by querying the last-seen cursor.
5. **Delivery receipts:** Client B's device sends a "delivered" ACK over WebSocket on receipt; "read" ACK when the user opens the conversation. Each ACK flows back through Message Service, updates the `message_status` in Cassandra, and notifies Client A.

**Each box justified:**
- **Connection Servers:** maintain stateful WebSocket connections; horizontal pool behind consistent hashing
- **Kafka:** decouples connection servers from message processing; provides durability and replay if the message service is momentarily down
- **Message Service:** sole writer to Cassandra; handles fan-out logic and push notification routing
- **Cassandra:** write-heavy, append-only access pattern (insert, never update in place for core messages), time-series queries ("last 50 messages for conversation X")
- **Redis (routing):** maps `user_id → connection_server_id` — O(1) lookup for delivery routing
- **Redis (presence):** maps `user_id → last_seen_epoch` with TTL; heartbeat from client keeps it alive
- **Push Notification Service:** delegates to APNs/FCM (APNs = Apple Push Notification service for iPhones; FCM = Firebase Cloud Messaging for Android devices — both are external gateways that deliver alerts to a device even when the app is closed) for offline users; see `07-cdc-outbox.md` for at-least-once delivery pattern

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

## Section 8 — 🌐 API Design

### Core Endpoints

| Method | Path | Auth | Request body | Response | Status codes |
|---|---|---|---|---|---|
| POST | `/v1/conversations` | JWT Bearer | `{ "type": "GROUP"/"ONE_TO_ONE", "participantIds": [...] }` | `{ "conversationId": "..." }` | 201, 400, 409 |
| GET | `/v1/conversations` | JWT Bearer | — | `[ { "conversationId", "lastMessage", "unreadCount" } ]` | 200 |
| POST | `/v1/conversations/{conversationId}/messages` | JWT Bearer | `{ "content": "...", "messageType": "TEXT", "idempotencyKey": "..." }` | `{ "messageId": "..." }` | 201, 400, 409 |
| GET | `/v1/conversations/{conversationId}/messages` | JWT Bearer | `?cursor={messageId}&limit=50` | `[ { "messageId", "senderId", "content", "status", "createdAt" } ]` | 200, 404 |
| PATCH | `/v1/messages/{messageId}/status` | JWT Bearer | `{ "status": "DELIVERED"/"READ" }` | 204 | 204, 404 |

### WebSocket Protocol

```
Endpoint: wss://chat.example.com/ws
Auth:     ?token={JWT} (validated on connect; connection rejected if invalid)

Client → Server events:
  { "type": "MESSAGE_SEND", "conversationId": "...", "content": "...", "idempotencyKey": "..." }
  { "type": "HEARTBEAT" }  — sent every 30s to maintain presence

Server → Client events:
  { "type": "MESSAGE_RECEIVED", "messageId": "...", "conversationId": "...", "senderId": "...", "content": "..." }
  { "type": "MESSAGE_STATUS_UPDATE", "messageId": "...", "status": "DELIVERED"/"READ" }
  { "type": "PRESENCE_CHANGED", "userId": "...", "status": "ONLINE"/"OFFLINE" }
  { "type": "ACK", "idempotencyKey": "...", "messageId": "..." }  — confirms message was persisted
```

### Key Design Decisions:
- **Idempotency:** `idempotencyKey` in message send prevents duplicates on WebSocket reconnect + retry — see `SystemDesignConcepts/04-idempotency.md` for the dedup table pattern
- **Cursor pagination:** GET messages uses cursor (TIMEUUID `message_id`), not offset — consistent despite concurrent writes; no page drift
- **Status codes:** 409 Conflict on duplicate message_id (idempotent — return the existing message)
- **WebSocket over SSE:** full-duplex needed (client sends + server pushes); SSE is server-push only

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
- **Failure mode if wrong:** if we chose write for all groups and someone creates a 100K-member broadcast channel, every single message triggers 100K Cassandra writes — the cluster would saturate

### Trade-off 2: WebSocket vs HTTP Polling for Real-Time Delivery

- **Chose:** WebSocket (persistent bidirectional connection)
- **Gain:** P99 < 200ms delivery is achievable; no thundering herd of poll requests; server can push without client polling
- **Lose:** stateful connections (7,700+ servers needed); connection server crashes lose all its connections; more complex infrastructure than REST
- **Failure mode if wrong:** HTTP polling at 500M DAU with 1-second poll intervals = 500M requests/second just for polling — 10× the actual message traffic. Impossible.

### Trade-off 3: Server-Side Encryption vs End-to-End Encryption

- **Chose:** server-side encryption (TLS in transit + AES-256 at rest)
- **Gain:** server can search messages, deliver to multiple devices seamlessly, recover from device loss, enable content moderation
- **Lose:** server has access to plaintext — a compromised server exposes all messages; incompatible with strongest privacy guarantees
- **Failure mode if wrong:** if the customer requires E2EE (enterprise context), the current architecture breaks entirely — message indexing, server-side fan-out, and multi-device sync all assume server can read message content. E2EE is a different architecture (Signal protocol), not an additive feature.

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
| June 23, 2026 | **DELIVERY-RECIPE integration & interview-readiness rewrite.** (1) Added 🧠 preamble explaining file structure + 60-minute time budget with explicit minute allocations per section. (2) Added 💾 Memory Anchors section (6 core + 3 bonus) with stress management rationale. (3) Enhanced all major sections (2, 4, 6, 7, 10, 11, 12) with: explicit timing callouts, "say this out loud" dialogue framing, interview psychology context (why this step matters, working memory constraints, stress failure modes). (4) Clarified "riskiest vs interesting" components in Deep Dives section. (5) Updated TL;DR to emphasize reviewing morning-of-interview + mental model stability under stress. (6) Enhanced Common Mistakes section with context that these are designed to be read before the interview to prevent stress-induced defaults. Result: File is now interview delivery-ready with zero refinement needed. |
