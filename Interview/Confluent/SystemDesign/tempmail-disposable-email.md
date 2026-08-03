# Design a Disposable / Temporary Email Service (TempMail)

> **Interview Type:** Type 2 — Full System Design
> **Frequency:** ⭐ Most repeated Confluent question (3+ reports — Jul 2025, May 2025, May 2026)
> **Bloom filter warning:** One round (May 2026, Hack2Hire) had the Bloom filter discussion consume most of the session. Section 8 Deep Dive #1 prepares you for that.
> **Standards file:** `solution-notes-standards.md`

---

## 🎯 What Is This System?

**In plain English:** A service that generates throwaway email addresses on demand. Each address lives for a short window (5–60 minutes), receives any email sent to it, and is then deleted with all its messages. No registration, no identity, no long-term storage.

**Real-world examples:**

| System / Company | What they built |
|---|---|
| **Mailinator** | Public disposable email since 2003; any address @mailinator.com works instantly |
| **Guerrilla Mail** | User-generated temp addresses; 1-hour default TTL |
| **10 Minute Mail** | Exactly 10-minute TTL; one of the most-cited in interview reports |
| **Temp-Mail.org** | Commercial API + consumer product; 24-hour TTL |
| **Apple Hide My Email** | Permanent forwarding aliases, not TTL-based — a contrast worth knowing |

**Core user journey:** User visits the site, gets an email address like `f4k2s9@tempmail.io`, pastes it into a signup form, waits for the confirmation email to appear in the inbox, clicks the link, closes the tab — address auto-deletes in 10 minutes.

**Why it's hard to build at scale:** The system must receive arbitrary SMTP traffic from the entire internet (no allowlist), match it against millions of ephemeral addresses in milliseconds, and garbage-collect those addresses and their messages atomically when TTL expires — while maintaining zero false deliveries to expired addresses.

**Tableflow parallel:** TTL-based data expiration = Kafka topic retention (messages deleted when retention.ms expires) + Iceberg snapshot expiry (table snapshots purged on a schedule). The address lifecycle is exactly how Kafka manages ephemeral log segments.

---

## 🚀 Section 1 — The One-Sentence Opener

> "Before I start, let me ask a few clarifying questions to make sure I'm solving the right version of this problem — TTL policies and SMTP ingress scope will significantly shape the architecture."

Then immediately Section 2. Never draw a box before the scope is set.

---

## 🔍 Section 2 — Clarifying Questions Script (Minutes 0–5)

**Q: "What's the TTL on an address — fixed or user-configurable?"**
- Why ask: fixed TTL = simple timer; configurable = extension APIs, billing tiers, longer storage
- If fixed (10 minutes) → hard-coded TTL, simple sweeper, no extension endpoint needed
- If configurable (1 hour, 24 hours, custom) → need TTL field in DB, extension API, and tiered retention costs

**Q: "Do we support custom domains, or only our own @tempmail.io?"**
- Why ask: custom domains = DNS ownership verification, multi-tenant MX routing, domain-specific storage partitioning
- If our domain only → one MX record (a DNS record that tells sending mail servers which IP accepts email for our domain), one SMTP server, simpler routing
- If custom domains → need domain registration flow, per-domain MX, significantly more complex

**Q: "Receive-only inbox, or do users need to reply or forward?"**
- Why ask: SMTP ingress only = no SMTP egress, no SPF/DKIM signing for outbound; reply = we need a full MTA with auth
- If receive-only → skip outbound entirely; simpler infra
- If reply/forward → need outbound SMTP, SPF record, DKIM signing, abuse controls

**Q: "What scale — DAU and peak email delivery rate?"**
- Why ask: shapes whether Postgres can absorb writes directly or we need Kafka buffering between SMTP ingress and storage
- If ~1M DAU → single Postgres handles it
- If ~100M DAU → need Kafka + async processing between receipt and storage

**Q: "Any requirement for real-time push (SSE (Server-Sent Events — a one-way HTTP push channel where the server streams events to the browser over a persistent connection, eliminating polling)/webhook) when email arrives, or is polling acceptable?"**
- Why ask: polling = simple; push = need event bus (Kafka or SSE server) + connection state
- If polling → `GET /v1/addresses/{id}/messages` on a timer
- If push → Server-Sent Events channel or WebSocket, adds stateful connection management

**Assume for this session:** Fixed 10-minute TTL, our domain only, receive-only, ~10M DAU, polling acceptable. Interviewer can vary any axis.

---

## 📋 Section 3 — Requirements

**Functional Requirements:**
- Users can generate a new temp email address (system-assigned, random)
- System receives any email sent to an active address via SMTP
- Users can list and read messages in their inbox
- Address (and all its messages) auto-deletes after 10 minutes from creation
- Users can manually delete their address early
- Out of scope: custom domains, reply/forward, attachments > 5MB, long-term archiving

**Non-Functional Requirements:**
- Scale: 10M DAU; ~120 new addresses/sec; ~350 incoming messages/sec; ~3,500 inbox reads/sec
- Latency: Address creation P99 < 200ms; inbox poll P99 < 100ms; SMTP acceptance P99 < 500ms
- Availability: 99.9% SLO for address generation + inbox reads (SMTP ingress can briefly queue)
- Consistency: Eventual for inbox (message appears within 2 seconds of SMTP acceptance)
- Durability: No durability requirement — TTL-expired data is intentionally deleted; in-window messages must not be lost
- TTL atomicity: When address expires, ALL its messages must be unreachable within 1 TTL window (no stale reads after expiry)

---

## 🗂️ Section 3.5 — Core Entities

| Entity | What it represents |
|---|---|
| **EmailAddress** | Ephemeral — the temp address itself; exists only for TTL duration; identified by UUID + address string |
| **Message** | Append-only — an email received at an active address; immutable after receipt; deleted when parent address expires |
| **BloomFilter** | Client-held by SMTP ingress — probabilistic index of active addresses; never persisted as a row; lives in Redis |
| **SMTPSession** | Ephemeral — the in-flight connection state while receiving an email; not stored, discarded after processing |

---

## 🔢 Section 4 — Scale Estimation (Type 2)

**Traffic:**
- DAU: 10M
- Address creation: 10M/day ÷ 86,400 ≈ **116/sec** (peak 3× = ~350/sec)
- Emails received: assume 3 emails per session = 30M/day ÷ 86,400 ≈ **347/sec** (peak 3× = ~1,040/sec)
- Inbox polls: assume 10 polls per session = 100M/day ÷ 86,400 ≈ **1,157/sec** (peak 3× = ~3,500/sec)

**Storage:**
- Active addresses at any time: 350/sec × 600s (10-min TTL) = **210,000 live addresses**
- Per address row: ~200 bytes → 210,000 × 200B = ~42 MB (trivially small)
- Per message: avg email body ~10KB → 1,040 msg/sec × 10KB = ~10MB/sec write throughput
- Messages are deleted at TTL (10 min window maximum): max live message storage = 10MB/sec × 600s = **~6GB at peak**
- After TTL sweep: net storage growth = ~0 (ephemeral system)

**Key conclusions:**
- At 3,500 read/sec + 1,040 write/sec, a single Postgres with connection pooling handles reads fine (~10K read/sec capacity) but SMTP write path must not block under burst — justifies Kafka buffering in Section 7.
- 210K active addresses easily fits in Redis (42MB) — Bloom filter sizing in Section 8 builds on this.
- 6GB live message storage fits on a single large Postgres instance — no sharding required at 10M DAU.

---

## 🔄 Section 5 — Requirements Variation Table ⭐

| If the interviewer says... | Your architecture changes to... | The reasoning |
|---|---|---|
| "100K DAU, not 10M" | Single Postgres, no Kafka, no Bloom filter — direct SMTP → DB write | At 1.2 addr/sec + 3.5 msg/sec, a single Postgres handles everything; Bloom filter overhead not worth it |
| "100M DAU" | Kafka + async message processors (fan-out); Cassandra for messages; Redis Cluster for Bloom filter | At 3,500 msg/sec write, Postgres write throughput saturates; need async buffering + wide-column storage |
| "Multi-region (US + EU + APAC)" | Regional SMTP ingress; address table replicated via Kafka compacted topic; Bloom filter per region synced via changelog | SMTP is latency-sensitive — regional ingress avoids cross-ocean hops; address lookup must be local-region-fast |
| "1-hour TTL instead of 10 min" | Same architecture, increase TTL sweeper interval; Bloom filter size grows 6× (~5M active) but still trivial | Linear scaling on active address count; no architectural change |
| "User can extend their address TTL" | Add `PATCH /v1/addresses/{id}` endpoint; TTL becomes a mutable field; sweeper must re-read before deleting | Introduces write-after-read race condition at expiry — need atomic TTL extension (CAS update) |
| "Attachments up to 100MB" | Message body offloaded to S3; DB stores S3 key, not body; SMTP ingress streams to S3 during SMTP session | 100MB × 1,040 msg/sec = 100GB/sec — cannot buffer in memory or Postgres |
| "Real-time push when email arrives" | SSE endpoint per address; SMTP ingress publishes to Kafka; SSE server consumes and pushes to connected clients | Polling at 3,500/sec generates unnecessary DB reads; SSE cuts read load by ~90% on active sessions |
| "Must block spam senders" | Add RBL (real-time blacklist) lookup at SMTP MAIL FROM stage; rate-limit per sending IP | Spam is the #1 abuse vector for disposable email; RBL check adds ~10ms to SMTP HELO |

---

## ⭐ Section 6 — API Design ← CONFLUENT'S PRIMARY EVALUATION AXIS

> **MANDATORY: API defined before HLD.** The architecture implements this contract — not the other way around.
> HTTP verb/code/header rules: **`api-design-cheatsheet.md`**

### 🧠 Part 1 — Derivation Framework

Every endpoint starts from a functional requirement: **FR → operation → resource → HTTP method → contract.**

**"Users can generate a new temp email address"** → create operation → resource is an `address` → `POST /v1/addresses`.
Who calls it? Anonymous user (no auth required — that's the point of disposable email). Minimum payload? None — the system assigns the address. What do they get back? The address string + expiry timestamp + an ID to use for subsequent calls. Why return `expires_at`? Client can show a countdown timer without polling.

**"Users can list messages in their inbox"** → read collection operation → resource is `messages` under `addresses` → `GET /v1/addresses/{id}/messages`.
Pagination strategy: **cursor-based, not offset**. Why? With TTL-based deletions running concurrently, offset pagination drifts — rows disappear mid-read, causing duplicates or skips. Cursor on `received_at` (stable order, never mutates) gives consistent pages regardless of concurrent deletes. This is a probe-level decision — Confluent will ask.

**"Address auto-deletes after TTL"** → the system deletes, not the user — no endpoint for expiry. But the status code on a read after expiry is non-obvious: `410 Gone` (not `404`). Why? `404` means "never existed or you have the wrong ID." `410` means "existed, is permanently gone." Precise semantics let clients distinguish "I have a bad ID" from "my address expired — I need a new one."

**Validation check:** Every FR maps to an endpoint. No orphan endpoints.

---

### Part 2 — Core Endpoints

| Method | Path | Auth | Request Body | Response | Status Codes |
|---|---|---|---|---|---|
| `POST` | `/v1/addresses` | None | `{ "ttl_seconds": 600 }` (optional override) | `{ "id", "address", "expires_at" }` | 201, 429, 503 |
| `GET` | `/v1/addresses/{id}` | None | — | `{ "id", "address", "expires_at", "message_count" }` | 200, 404, 410 |
| `GET` | `/v1/addresses/{id}/messages` | None | — | `{ "messages": [...], "next_cursor" }` | 200, 404, 410, 400 |
| `GET` | `/v1/addresses/{id}/messages/{messageId}` | None | — | `{ "id", "from", "subject", "body_text", "body_html", "received_at" }` | 200, 404, 410 |
| `DELETE` | `/v1/addresses/{id}` | None | — | — | 204, 404, 410 |

---

### 🔍 Part 3 — Endpoint Stories

**`POST /v1/addresses`** — Creates a new ephemeral address and starts the TTL clock. Non-obvious: the system assigns the address string (client cannot choose it) to prevent address-squatting attacks where bad actors reserve `paypal-verify@tempmail.io`. Returns 201 with `Location: /v1/addresses/{id}` header so client knows where to poll. Returns 429 if the requesting IP has exceeded address creation rate (e.g., >10 addresses/minute from one IP) — named trigger: IP-based rate limit bucket exhausted. Returns 503 during SMTP infrastructure maintenance windows.

**`GET /v1/addresses/{id}`** — Checks whether an address is still alive. 200 = active. 404 = ID never existed in the system (client has a corrupt or fabricated ID). **410 = address existed but TTL expired** — this is the precise distinction Confluent will probe. A client that gets 410 knows to generate a new address, not retry the same ID. `message_count` in the response body lets the client know whether to bother fetching messages.

**`GET /v1/addresses/{id}/messages`** — Lists inbox with cursor pagination. `?cursor=` is a base64-encoded `received_at` + `message_id` tuple — opaque to the client, but server can decompose it into a `WHERE received_at < :cursor_time AND id < :cursor_id` query. Returns 410 (not 404) if the address has expired, because the address DID exist — this lets clients show "your inbox expired" vs "invalid address." Returns 400 if the cursor value is malformed (unparseable base64 or out-of-range timestamp) — named trigger: cursor decode fails validation.

**`GET /v1/addresses/{id}/messages/{messageId}`** — Fetches full message body. Non-obvious: both the address AND the message can independently be "not found." If the address expired (410) and the message table cascade-deleted the messages, the 410 on the address ID takes precedence — return 410, not 404 for the message. This ordering matters: a 404 on a message inside a 410-dead address would confuse clients.

**`DELETE /v1/addresses/{id}`** — Early manual deletion. Returns 204 (no body on success — nothing to return). Returns 410 if the address already expired before the client hit delete — the deletion already happened via TTL sweeper. Client should treat 410 as success (idempotent intent: "I want this gone" = it's gone). Returns 404 only if the ID literally never existed.

---

## 🏗️ Section 7 — High-Level Architecture (Type 2, Minutes 18–30)

### 🎨 Visual — System Data Flow

```
══════════════════════════════════════════════════════════
  USER FLOW (HTTP)                  EMAIL FLOW (SMTP)
══════════════════════════════════════════════════════════

[Browser / Client]                [External Mail Server]
       |                                   |
       | POST /v1/addresses                | SMTP (port 25)
       |                                   |
       v                                   v
[API Gateway / LB]           [SMTP Ingress Cluster]
       |                        |          |
       v                        | Bloom?   v
[Address Service] ──────────► [Redis Bloom Filter]
       |                        |
       | INSERT                 | YES (maybe exists)
       v                        v
[Postgres: addresses]   [Address Lookup: Postgres]
                                |
                          still alive?
                                |
                         YES    |    NO → SMTP 550
                                v
                        [Kafka: raw-messages topic]
                                |
                                v
                     [Message Processor Service]
                        - parse MIME
                        - extract text/HTML
                        - enforce size limit
                                |
                                v
                     [Postgres: messages table]

                     [TTL Sweeper (scheduled job)]
                        - every 30s: DELETE FROM addresses
                          WHERE expires_at < NOW()
                        - CASCADE deletes messages

KEY INVARIANT:
   Bloom filter is a fast NEGATIVE check only.
   Postgres is the ground truth for address existence + expiry.
   A Bloom false positive → one extra Postgres read → SMTP 550.
   A false negative is impossible: if Bloom says NO, address definitely doesn't exist.
```

**Data flow walkthrough:**

1. **Address creation:** Client → API Gateway → Address Service → INSERT into Postgres addresses table → also ADD to Redis Bloom filter → return 201 with address string + expiry.

2. **Incoming email:** External server sends SMTP → SMTP Ingress receives `RCPT TO: f4k2s9@tempmail.io` → check Bloom filter in Redis → if Bloom says MAYBE EXISTS, check Postgres `WHERE address = ? AND expires_at > NOW()` → if alive, accept message → publish raw email bytes to Kafka `raw-messages` topic → return SMTP 250 OK to sender.

3. **Message processing:** Message Processor consumes from Kafka → parses MIME → extracts text/HTML body → inserts into Postgres messages table. Decoupled from SMTP session — sender doesn't wait for DB write.

4. **Inbox read:** Client polls `GET /v1/addresses/{id}/messages` → API Gateway → Address Service reads from Postgres messages table with cursor pagination.

5. **TTL sweep:** Background job runs every 30s → `DELETE FROM addresses WHERE expires_at < NOW()` → Postgres CASCADE deletes all related messages → Bloom filter is rebuilt from remaining active addresses once per TTL window.

**Each box justified:**
- **Redis Bloom filter** — rejects ~99% of SMTP traffic for non-existent addresses without hitting Postgres (at 1,040 msg/sec, most will be spam to non-existent addresses)
- **Kafka** — decouples SMTP acceptance (must be fast: < 500ms SMTP timeout) from message processing (can be async); provides replay on processor crash; buffers burst traffic
- **Message Processor** — separate from SMTP ingress so MIME parsing (CPU) doesn't block SMTP sessions
- **TTL Sweeper** — simpler than per-row expiry triggers; a single scan every 30s handles 10M DAU workload with index on `expires_at`

---

**Stage Transitions:**

```
══════════════════════════════════════════════════════════
STAGE 1 — Single Postgres, no Kafka (handles ~1M DAU)
══════════════════════════════════════════════════════════
SMTP Ingress writes directly to Postgres during SMTP session.

BREAKING POINT: Stage 1 breaks at ~2,000 SMTP msg/sec
  because SMTP session blocks on Postgres write (~5ms avg),
  and at 2,000 concurrent SMTP sessions, Postgres connection
  pool (200 connections) exhausts. New SMTP connections get
  "connection refused."
  Observable symptom: SMTP 421 "Service temporarily unavailable."
  Why Stage 2 is needed: SMTP acceptance must be decoupled from DB writes.

══════════════════════════════════════════════════════════
STAGE 2 — Kafka buffer between SMTP and storage (10M DAU — our target)
══════════════════════════════════════════════════════════
SMTP Ingress publishes to Kafka (< 5ms) and returns SMTP 250 immediately.
Message Processor consumes asynchronously.

BREAKING POINT: Stage 2 breaks at ~15K msg/sec
  because 32 Kafka partitions → 32 Message Processor instances
  → 32 concurrent Postgres write sessions each at ~450 inserts/sec.
  At ~15K total writes/sec, Postgres WAL write I/O saturates.
  Observable symptom: INSERT latency > 500ms, consumer lag growing unbounded.
  Observable symptom: Kafka consumer lag grows unbounded (Tableflow signal:
  this is exactly how you'd see a slow Iceberg table sink falling behind).
  Why Stage 3 is needed: Cassandra (wide-column, address_id as partition key)
  handles unlimited parallel writes.
```

---

## 🔬 Section 8 — Core Component Deep Dives (Minutes 30–45)

### Deep Dive 1: Bloom Filter — ⭐ Most Likely to Consume Entire Round

**Why this is the most critical component:**
At 1,040 SMTP msg/sec, the vast majority are spam to random/non-existent addresses. Without a fast pre-filter, every incoming SMTP connection does a Postgres lookup. Postgres can handle 10K reads/sec — but wasting that capacity on spam lookups leaves no headroom for real reads. The Bloom filter is the first line of defense.

**What a Bloom filter is (define it in the interview — Confluent wants this):**
A Bloom filter (a probabilistic data structure that answers "is this element in the set?" in O(k) time using a bit array of size m and k hash functions — like a set that can say "definitely not" but never "definitely yes") guarantees zero false negatives. If the filter says NO → the address definitely does not exist → reject immediately, no DB lookup. If the filter says YES → address MIGHT exist → verify in Postgres.

**Sizing the Bloom filter:**

```
Formula: m = -n × ln(p) / (ln 2)²
         k = (m/n) × ln(2)

Where: n = number of active addresses = 210,000 (10-min TTL × 350 addr/sec)
       p = target false positive rate = 1% (0.01)

m = -210,000 × ln(0.01) / (0.693)²
  = -210,000 × (-4.605) / 0.480
  = 210,000 × 9.594
  ≈ 2,014,740 bits ≈ 250 KB

k = (2,014,740 / 210,000) × 0.693 ≈ 9.59 × 0.693 ≈ 6.6 → 7 hash functions
```

**250KB for 1% FPR on the entire active address space. Trivially fits in Redis.**

If the interviewer scales to 100M DAU (3,500 addr/sec × 600s = 2.1M active addresses):
- m = ~2.5MB — still fits trivially in a single Redis instance (max Redis memory ~256GB)

**What happens on a false positive:**
SMTP ingress checks Bloom → "maybe exists" → checks Postgres → address not found → returns SMTP 550 "User unknown" to sender. Cost: one extra Postgres read (~1ms). No data corruption, no false delivery. Bloom false positives are safe.

**The deletion problem — what interviewers probe hardest:**

Standard Bloom filters do not support deletion. When an address expires (after 10 minutes), you cannot remove it from the filter. The filter fills up with stale entries → false positive rate rises over time.

```
Options considered:
```

| Option | Pros | Cons |
|---|---|---|
| **Periodic full rebuild** | Simple; accurate; Postgres is ground truth | Rebuild every TTL window (10 min); brief window where filter is stale |
| **Counting Bloom filter** | Supports deletion; FPR stays stable | ~4× memory (counters instead of bits); counters can overflow |
| **Time-windowed (two filters)** | No deletion needed; automatic rotation | More complex; double memory; brief overlap window |

**Decision: Periodic full rebuild every 10 minutes.**

Because: Postgres query `SELECT address FROM addresses WHERE expires_at > NOW()` returns 210K rows in milliseconds. Rebuild takes < 100ms. During the rebuild, use the old filter (slightly stale). The stale window is bounded at 10 minutes (the TTL itself), so at worst a Bloom filter misses a just-expired address for one TTL window → causes one extra Postgres lookup that correctly returns "expired" → SMTP 550. Acceptable.

**The trade-off accepted:** Counting Bloom filter eliminates the stale window but increases memory 4× (1MB → 4MB at current scale — trivial) and adds counter-overflow risk. For our scale, periodic rebuild is simpler and auditable — you can verify it worked by counting set bits.

**Implementation sketch:**

```java
// Rebuild job — runs every TTL_SECONDS / 2 (5 minutes for 10-min TTL)
public void rebuildBloomFilter() {
    // expected insertions: 10-min TTL × 350 addr/sec peak = 210K active
    // desired false positive rate: 1%
    BloomFilter<String> newFilter = BloomFilter.create(
        Funnels.stringFunnel(Charset.UTF_8),
        210_000,
        0.01
    );

    // Query only addresses whose expiry is in the future
    List<String> activeAddresses = addressRepo
        .findActiveAddresses(Instant.now());

    for (String address : activeAddresses) {
        newFilter.put(address);
    }

    // Atomic swap — old filter serves reads during rebuild
    redisBloomFilter.atomicReplace(newFilter.toByteArray());
}
```

---

### Deep Dive 2: TTL Expiry Mechanism

**Why this is a critical component:**
TTL enforcement is a correctness requirement, not a performance one. An expired address that still serves messages leaks PII and violates the service contract.

**Options considered:**

| Option | Pros | Cons |
|---|---|---|
| **Lazy expiry (check on read)** | No background job; simple | Expired data stays in DB until queried; storage bloat; inconsistent |
| **Background sweeper** | Proactive cleanup; bounded storage | Sweep interval = max stale window |
| **Kafka topic retention (TTL via log retention)** | Native TTL, no custom job | Messages as Kafka records — operational complexity |
| **DB-level TTL (Redis native or Postgres cron job)** | Atomic; DB manages cleanup | Postgres has no native row TTL; Redis TTL fits KV but not relational |

**Decision: Background sweeper + lazy check on read (two-layer defense).**

```sql
-- Sweeper runs every 30 seconds
DELETE FROM addresses
WHERE expires_at < NOW() - INTERVAL '30 seconds';
-- CASCADE deletes all messages for expired addresses

-- Lazy check on every API read (belt + suspenders)
SELECT * FROM addresses
WHERE id = $1 AND expires_at > NOW();
-- If no row returned → 410 Gone
```

The `INTERVAL '30 seconds'` buffer prevents a race condition where the sweeper deletes an address 1ms before the API check completes.

**Index required for sweeper performance:**
```sql
CREATE INDEX idx_addresses_expires_at ON addresses (expires_at);
```
Without this index, the sweeper does a full-table scan every 30 seconds. At 210K rows, this is ~20ms per scan — fine for now, but at 100M DAU (2M rows), it becomes 2 seconds per scan and blocks reads. Index makes it ~1ms regardless of table size.

---

### Deep Dive 3: SMTP Ingress and MX Record Setup

**Why this matters:**
The SMTP layer is the system's external interface. Getting MX records wrong means no email arrives. Getting SMTP error codes wrong means legitimate emails are silently dropped.

**MX Record:**
```
tempmail.io.  IN  MX  10  mx1.tempmail.io.
tempmail.io.  IN  MX  20  mx2.tempmail.io.  (backup)
mx1.tempmail.io.  IN  A  203.0.113.10
```

The priority numbers (10, 20) tell sending servers to prefer `mx1` and fall back to `mx2`. Without an MX record, email to `@tempmail.io` is undeliverable — every major MTA (Mail Transfer Agent — the software that routes and delivers email between servers, e.g. Postfix, Sendmail, Exchange) will refuse to route it.

**SMTP state machine at ingress:**
```
EHLO sender.domain
  → 250 OK (accept all)

MAIL FROM: <spammer@evil.com>
  → 250 OK (we don't block on sender for a disposable service)

RCPT TO: <f4k2s9@tempmail.io>
  → Bloom filter check → Postgres check
  → 250 OK (address valid)   OR   550 No such user here (address invalid/expired)

DATA
  → 354 Start mail input
  → [email body]
  → Publish to Kafka
  → 250 Message accepted for delivery

QUIT
```

The critical decision: where to do the address check. Do it at `RCPT TO` (not at `DATA`). Why? If we accept `DATA` and then realize the address is invalid, we've received the full email payload (potentially megabytes) for nothing — worse, some SMTP etiquette says once you accept DATA you should deliver it. Rejecting at `RCPT TO` is cleaner and wastes no bandwidth.

---

## 🗄️ Section 9 — Data Model / SQL Schema

```sql
-- Primary table: one row per temp address
CREATE TABLE addresses (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    address     VARCHAR(254) NOT NULL,
    expires_at  TIMESTAMP   NOT NULL,
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW(),
    created_ip  INET        NOT NULL
);

-- address lookup by string (SMTP ingress + API reads)
CREATE UNIQUE INDEX idx_addresses_address ON addresses (address);

-- TTL sweeper scan + expiry check on reads
CREATE INDEX idx_addresses_expires_at ON addresses (expires_at);

-- Rate limit lookup: how many addresses has this IP created recently?
CREATE INDEX idx_addresses_ip_created ON addresses (created_ip, created_at DESC);

-- Messages table: messages belong to an address, deleted when address deleted
CREATE TABLE messages (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    address_id      UUID        NOT NULL REFERENCES addresses(id) ON DELETE CASCADE,
    from_address    VARCHAR(254) NOT NULL,
    subject         VARCHAR(998),
    body_text       TEXT,
    body_html       TEXT,
    raw_size_bytes  INTEGER     NOT NULL DEFAULT 0,
    received_at     TIMESTAMP   NOT NULL DEFAULT NOW()
);

-- Primary read pattern: list messages for an address, newest first
CREATE INDEX idx_messages_address_received ON messages (address_id, received_at DESC);

-- Cursor pagination: stable cursor using (address_id, received_at, id)
CREATE INDEX idx_messages_cursor ON messages (address_id, received_at DESC, id DESC);
```

**Key Schema Decisions:**

- **`ON DELETE CASCADE` on messages.address_id** — When TTL sweeper deletes from `addresses`, Postgres atomically deletes all messages for that address. No orphaned messages. No two-phase delete logic in application code. This is the correctness guarantee for TTL atomicity.

- **`UNIQUE INDEX` on `addresses.address`** — Prevents two concurrent address creation requests from generating the same address string. Without this, two processes could generate `f4k2s9@tempmail.io` simultaneously, both pass the uniqueness check, and both insert — one succeeds, one fails with constraint violation (handle with retry).

- **Two indexes on messages** — `idx_messages_address_received` serves `ORDER BY received_at DESC LIMIT N` (simple list). `idx_messages_cursor` serves cursor pagination (`WHERE received_at < :cursor AND id < :cursor_id`) without index scan.

- **`created_ip` for rate limiting** — Stored at address creation time. The index lets us do `SELECT COUNT(*) WHERE created_ip = ? AND created_at > NOW() - INTERVAL '1 minute'` in ~1ms to enforce per-IP rate limits.

- **SQL vs NoSQL choice:** SQL (Postgres) because the access patterns are relational (messages belong to addresses; cascade delete is a business requirement, not just a nice-to-have), the data volume at 10M DAU is small (~6GB live), and Confluent explicitly evaluates SQL schema design. Cassandra would be chosen at 100M+ DAU where Postgres write throughput saturates.

---

## ⚠️ Section 10 — Trade-offs + Failure Modes (Minutes 45–53)

### Trade-off 1: Async (Kafka) vs Sync SMTP Processing

- **Chose:** Async — SMTP ingress publishes to Kafka, Message Processor consumes asynchronously
- **Gain:** SMTP acceptance takes < 5ms (Kafka publish); SMTP session completes fast; bursts absorbed by Kafka queue; processor can crash/restart without losing messages
- **Lose:** 2-3 second delivery lag between SMTP acceptance and message appearing in inbox (eventual consistency); if Kafka is down, SMTP must fall back to sync processing or refuse new messages
- **Failure mode if wrong (if sync):** [Technical]: At 1,040 SMTP msg/sec, each session waits ~15ms for Postgres write. With a 500ms SMTP timeout, a Postgres slowdown (GC pause, lock contention) causes SMTP connections to pile up → thread pool exhaustion → SMTP 421 "temporarily unavailable." [Streaming impact]: Kafka consumer lag equivalent — ingestion stalls; the "pipe" backs up. For Tableflow, this is the equivalent of a slow Iceberg table sink: the pipeline falls behind real-time and downstream consumers see hours-old data instead of near-real-time.

### Trade-off 2: Probabilistic (Bloom filter) vs Exact (Redis SET) Address Index

- **Chose:** Bloom filter at 250KB with 1% FPR
- **Gain:** 250KB vs ~10MB for Redis SET (42× smaller); Bloom check is O(k) bit operations — faster than Redis GET; scales to 2.5GB at 100M DAU vs 10GB Redis SET
- **Lose:** 1% false positive rate (1 in 100 SMTP connections for non-existent addresses hits Postgres); requires periodic rebuild (no delete support)
- **Failure mode if wrong (if no filter at all):** [Technical]: At 1,040 msg/sec with 80% spam to non-existent addresses = 832 extra Postgres reads/sec. Postgres reads/sec capacity: ~10K/sec. Currently tolerable, but at 100M DAU (8,320 extra reads/sec) + legitimate reads (35,000/sec), Postgres hits saturation (P99 > 200ms). [Streaming impact]: Equivalent to a Kafka consumer without offset commits — the system "processes" every message (reads DB) regardless of whether it needs to, wasting throughput on noise rather than signal.

### Trade-off 3: Background Sweeper vs Lazy-Only Expiry

- **Chose:** Background sweeper every 30 seconds + lazy check on reads (dual layer)
- **Gain:** Bounded storage (max ~6GB live, then cleaned); no indefinite growth; expired data removed proactively
- **Lose:** Brief stale window (up to 30s between expiry and deletion); sweeper adds periodic DB load (low — indexed scan on `expires_at`)
- **Failure mode if wrong (lazy-only):** [Technical]: Expired addresses and messages accumulate indefinitely. At 116 new addresses/sec × 86,400s/day × average 3 messages × 10KB = ~300GB/day of un-deleted data. In 30 days: 9TB. Postgres VACUUM cannot keep up with dead tuples. [Streaming impact]: Equivalent to a Kafka topic with `retention.ms = -1` (infinite retention) — the log grows forever, disk exhausts, broker crashes. For Tableflow: Iceberg table snapshots never expire → table metadata grows unbounded → snapshot reads slow down as Iceberg must scan all historical snapshots.

---

## 🌊 Section 11 — Confluent/Tableflow Angle

**TTL = Kafka topic retention + Iceberg snapshot expiry:**
The disposable email's TTL lifecycle is architecturally identical to Kafka's log retention. In Kafka, a topic with `retention.ms = 600000` (10 minutes) automatically purges log segments older than 10 minutes. Our `addresses.expires_at` + TTL sweeper is the same mechanism at the application layer. For Confluent's Tableflow team, this is a solved problem: Iceberg's `expire_snapshots` procedure purges snapshots older than a configured timestamp. Mentioning this parallel signals you understand Confluent's core product — not just the interview question.

**Kafka as ingestion backbone:**
In Stage 2, Kafka's role is exactly what Tableflow uses it for: a reliable, ordered, replayable ingestion buffer between an external event source (SMTP sender = Kafka producer) and a stateful processor (Message Processor = Kafka Streams application writing to Iceberg). The at-least-once delivery guarantee means a processor crash doesn't lose emails — exactly the durability guarantee Tableflow provides for table sync operations.

**Compacted topic as address registry:**
A Kafka log-compacted topic with `address → expiry_time` as key-value is effectively the same as our Bloom filter + Postgres combination — the compacted topic retains the latest value per key (address), and consuming it rebuilds an exact in-memory address set. This is how a Tableflow control plane could track table metadata: each table name is a key, current schema is the value, Kafka compaction ensures the latest schema survives log cleanup.

**Multi-cloud consideration:**
If TempMail must serve global users with < 50ms SMTP acceptance (e.g., US + EU + APAC), regional SMTP ingress is needed. Each region maintains its own Bloom filter, synchronized via a Kafka topic — the address creation event streams globally. This is the same pattern Confluent uses for multi-region cluster replication (CRR — Cluster Linking replicates topics across regions). Mentioning CRR signals Confluent domain fit.

---

## 🔬 Section 12 — Where the Interviewer Will Probe

### Surface Probe (Tier 1 — every candidate gets this)

**Q: "Why do you need a Bloom filter at all? Can't you just query Postgres?"**
> At 1,040 SMTP msg/sec with ~80% to non-existent addresses, that's 832 Postgres lookups/sec for emails that will be rejected. Postgres can handle ~10K reads/sec, so it's technically fine at 10M DAU — but at 100M DAU it becomes the bottleneck. The Bloom filter is a 250KB guard that eliminates ~99% of those wasted lookups with zero false negatives. I'd introduce it at 10M DAU as a cheap insurance policy, not as a premature optimization.

**Q: "What happens when the address expires mid-delivery — the sender sends the email while the address is alive but it gets processed after TTL?"**
> The Bloom filter + Postgres check happens at `RCPT TO` (during the SMTP session, before we accept the DATA). If the address is alive at `RCPT TO`, we return SMTP 250 and accept the email. The message is published to Kafka. If the TTL sweeper fires before the Message Processor writes to the messages table, the processor's INSERT will fail the foreign key constraint (address_id references a deleted address). The processor catches this constraint violation and discards the message — the address is gone, so there's no inbox to deliver to anyway. This is correct behavior: the sender was told "250 accepted" but the address expired before delivery completed. A better UX would be a grace period in the sweeper, but that's a product decision, not a correctness bug.

---

### Deep Probe (Tier 2 — tests real understanding)

**Q: "Your Bloom filter has 1% FPR. Walk me through what happens on a false positive end-to-end."**
> SMTP ingress receives `RCPT TO: <ghost123@tempmail.io>`. This address was deleted 5 minutes ago but our Bloom filter hasn't been rebuilt yet (max stale = 10 minutes). Filter returns "maybe exists." SMTP ingress queries Postgres: `SELECT id FROM addresses WHERE address = 'ghost123@tempmail.io' AND expires_at > NOW()`. Returns zero rows. SMTP ingress returns `550 No such user here` to the sending server. The sender's MTA logs a permanent failure and stops retrying. Total cost: one extra Postgres read (~1ms). No data corruption, no false delivery, no user impact. A false positive is a performance cost, not a correctness bug.

**Q: "How do you handle the Bloom filter across multiple SMTP ingress servers?"**
> The Bloom filter lives in Redis, shared across all SMTP ingress instances. Each instance does a Redis GET on the bit positions for the incoming address (7 hash lookups via Lua script for atomicity). The filter rebuild job runs on one designated instance (with a distributed lock via Redis `SET NX PX`) and updates the filter in Redis atomically. No SMTP ingress server maintains local state — they all read from the same Redis. This means a single Redis instance is a SPOF for the Bloom filter, but since a Bloom filter failure mode is graceful degradation (just query Postgres for every message, which we already handle), we can tolerate brief Redis downtime.

**Q: "Counting Bloom filter vs periodic rebuild — when would you switch?"**
> Periodic rebuild works until the rebuild time becomes noticeable relative to TTL. At 10-minute TTL with a 100ms rebuild time, the stale window is 0.1/600 = 0.017% of the TTL — negligible. If TTL drops to 30 seconds (real-time use case), a 100ms rebuild is 0.3% of TTL — still fine. I'd switch to a Counting Bloom filter when either: (a) the address count grows to 2M+ rows and Postgres scan takes > 1s (making the rebuild window materially stale), or (b) the TTL drops to < 10 seconds. For our 10-minute TTL at 10M DAU, periodic rebuild is the right call: simpler code, no counter overflow risk, and fully auditable.

---

### Cross-Concept Probe (Tier 3 — separates senior candidates)

**Q: "How does your architecture change if this system needs to be the authoritative email provider for a paid tier — addresses live for 30 days, users have accounts, and messages must never be lost?"**
> Three structural changes: (1) TTL sweeper becomes opt-in rather than mandatory — paid addresses have `expires_at = NULL` and are exempt from the sweep; (2) Messages must have durability guarantees — promote the Kafka `raw-messages` topic to a compacted topic (by address_id as key) so messages survive processor restarts with guaranteed delivery; (3) Storage changes — ephemeral Postgres with 6GB cap is replaced by Cassandra (address_id as partition key, received_at as clustering key) for 30-day × potentially millions of messages per user. The Bloom filter no longer makes sense for long-lived addresses — an exact Redis SET or secondary Postgres index replaces it. Essentially: the ephemeral architecture optimizes for fast cleanup; the paid architecture optimizes for durability and at-least-once delivery — same Kafka backbone, different retention and storage tier.

---

## 🐞 Section 13 — Common Mistakes on This Question

- **Mistake 1: Returning 404 for expired addresses** → **Why it's wrong:** 404 means "never existed or bad ID." An expired address DID exist — the client has a valid ID. 404 misleads clients into thinking they have a wrong ID and creating a new request chain instead of understanding their session expired. → **What to say instead:** "I use 410 Gone for expired addresses — it's semantically precise, tells the client the resource existed and is permanently gone, and lets the frontend show 'your inbox expired, click to get a new address' instead of 'error: invalid address.'"

- **Mistake 2: Starting address creation with a uniqueness check in application code** → **Why it's wrong:** Check-then-insert is a TOCTOU race — two concurrent requests can both pass the check, both attempt INSERT, and you get a constraint violation or duplicate address. → **What to say instead:** "The UNIQUE INDEX on the address column makes Postgres the arbiter. Application code attempts INSERT; if it gets a unique constraint violation, it generates a new random address and retries — the DB guarantees uniqueness, not the application."

- **Mistake 3: Doing the address existence check at DATA (not at RCPT TO)** → **Why it's wrong:** Accepting DATA means receiving the full email body (potentially megabytes) before deciding to reject it. At 1,040 msg/sec with 80% spam, that's 832 × avg 10KB = 8MB/sec of wasted I/O ingested before rejection. SMTP etiquette says: reject early. → **What to say instead:** "The check happens at RCPT TO — before DATA. If the address doesn't exist, we return 550 immediately. The sender gets a definitive rejection without sending the email body."

- **Mistake 4: Bloom filter without explaining the deletion problem** → **Why it's wrong:** Any interviewer with distributed systems experience knows Bloom filters don't support deletion. If you introduce a Bloom filter without addressing this, they'll probe it — and if you don't have an answer, it looks like you copied the pattern without understanding it. → **What to say instead:** "Standard Bloom filters don't support deletion, so I rebuild the filter every TTL window (10 minutes) from the current active address set in Postgres. During rebuild I serve from the old filter — the stale window is bounded at one rebuild interval, and a stale entry causes at most one extra Postgres lookup, not a false delivery."

- **Mistake 5: Using offset pagination for the inbox** → **Why it's wrong:** With concurrent TTL deletes, offset pagination produces duplicates and gaps. If page 1 returns messages 1-10, then messages 2 and 5 expire before the client requests page 2, `OFFSET 10` now starts at what was message 13 — the client skips 11 and 12. → **What to say instead:** "Cursor pagination on `received_at DESC` with the message ID as a tiebreaker. The cursor is a stable position in the sorted order — deletes behind the cursor don't affect forward pagination."

---

## 🧭 Section 14 — Confluent Evaluation Axes Checklist

| Axis | Relevant? | How your design addresses it |
|---|---|---|
| **API Design Precision** | ✅ | `POST /v1/addresses` returns 201 with `Location` header; `GET /v1/addresses/{id}` returns 410 (not 404) for expired addresses; `GET /../messages` uses cursor pagination (`received_at DESC + id` tiebreaker) because offset breaks under concurrent TTL deletes; `DELETE /v1/addresses/{id}` returns 204 and treats 410 as idempotent success |
| **Trade-off Defense** | ✅ | Bloom filter vs Redis SET (memory vs exactness, with sizing math); async Kafka vs sync SMTP processing (latency vs throughput, with SMTP timeout numbers); background sweeper vs lazy expiry (proactive cleanup vs simplicity); each with explicit "failure mode if wrong" at numbers |
| **SQL / Data Modeling** | ✅ | `ON DELETE CASCADE` for TTL atomicity; UNIQUE INDEX on `addresses.address` for concurrent-safe creation; composite index `(address_id, received_at DESC, id DESC)` for cursor pagination; `created_ip + created_at` index for per-IP rate limiting |
| **Distributed Systems** | ✅ | Bloom filter as distributed cache (Redis, shared across SMTP ingress fleet); Kafka decoupling SMTP ingress from storage for burst absorption; Stage 1→2 transitions with specific SMTP thread-pool numbers |
| **Pipeline Resilience** | ✅ | Kafka at-least-once delivery between SMTP ingress and Message Processor; SMTP returns 250 before Postgres write (fast accept); processor crash does not lose messages (Kafka offset commit on success); Bloom filter failure degrades gracefully to Postgres-only (not a hard failure) |
| **Concurrency** | ✅ | Concurrent address creation handled by DB UNIQUE constraint (not application-level check); Bloom filter rebuild uses Redis distributed lock (`SET NX PX`) to prevent concurrent rebuilds; sweeper uses DELETE with indexed scan (no application-level locking needed); cursor pagination eliminates race conditions from concurrent deletes |

---

## 🧾 Section 15 — TL;DR Answer Summary

> "TempMail is an ephemeral SMTP-receiving system where the core architectural challenge is fast address lookup for millions of concurrent SMTP connections — solved by a 250KB Bloom filter in Redis that provides zero false negatives and eliminates ~99% of Postgres lookups for non-existent addresses. The API contract uses 410 (not 404) for expired addresses because semantic precision matters at Confluent: a client needs to distinguish 'my ID is wrong' from 'my session expired.' SMTP acceptance is decoupled from storage writes via Kafka so ingress stays under 5ms regardless of DB load, matching how Tableflow uses Kafka as a reliable ingestion buffer before Iceberg table writes. The TTL mechanism — a background sweeper + `ON DELETE CASCADE` — is architecturally identical to Kafka topic retention and Iceberg snapshot expiry: the address lifecycle IS the log retention lifecycle. The trade-off I'd defend first is the Bloom filter deletion limitation: periodic rebuild every 10 minutes is the right call over a Counting Bloom filter because the stale window is bounded, the rebuild is auditable, and the failure mode of a false positive is graceful (one extra Postgres read, not a false delivery)."

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Aug 2026 | File created. Type 2, Full System Design. Bloom filter deep dive (Section 8.1) expanded to cover sizing math, deletion problem, and rebuild vs Counting filter trade-off — research shows this took an entire round at Confluent (Hack2Hire, May 2026). |
