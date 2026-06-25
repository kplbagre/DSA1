# A1 — Design a URL Shortener

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
- Minutes 10–25: Sections 5–6 (Requirements variation + HLD + Data flow)
- Minutes 25–40: Section 7 (Deep dives: 2–3 riskiest components)
- Minutes 40–48: Section 10 (Trade-offs: exactly 3, with failure modes)
- Minutes 48–52: Section 11 (DocuSign dimensions — map explicitly)
- Minutes 52–60: Section 12 (Interviewer probes — prepared Tier 1/2/3 answers)

**Stay on this schedule.** If you're at minute 45 and still deep-diving, pause and move to trade-offs — the rubric values trade-off thinking over technical depth.

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
| **Question** | Design a URL Shortener |
| **Interview Type** | Type A — System Design |
| **Confirmed or Likely** | ⭐ Confirmed asked (DocuSign PDF p.3 — listed as example system design question) |
| **Concept notes prerequisite** | `11-api-design.md` (REST design, status codes), `05-consistent-hashing.md` (distributed routing, optional), `03-caching.md` (read-heavy caching) |
| **DocuSign-specific angle** | This is a PDF-example question, not a DocuSign domain question. The DocuSign move is to **explicitly name which of the 7 evaluation dimensions your design addresses** — that's the grading signal for Type A. Do not try to make URL shortening sound document-related. |
| **Time budget** | 5 min clarify → 5 min estimate → 15 min HLD → 15 min deep dive → 8 min trade-offs → 7 min Q&A buffer |

---

## Section 1 — 🚀 The One-Sentence Opener

> "Before I draw anything, let me ask a few clarifying questions — specifically about scale, read/write ratio, and whether we need global distribution or regional, because those drive the architecture."

Then immediately go to Section 2. Do NOT start drawing.

---

## Section 2 — 🔍 Clarifying Questions Script (Minutes 0–5)

**What to do:** Ask 4–6 questions that clarify scope. Don't assume. The interviewer is watching how you *think*, not how fast you talk.

**Say this out loud (after your opener):**
> "I have a few clarifying questions so I make sure I'm building the right thing..."

---

**Q: "How many URLs are we shortening per day, and what's the expected URL lifespan — do shortened links expire, or are they permanent?"**
- Why ask: determines if we need archiving/deletion logic and how much storage we need for the long tail.
- If 1M/day → single region, SQL OK, TTL-based expiry is optional
- If 100M+/day → multi-region needed, NoSQL for horizontal scaling, TTL critical for cost management

---

**Q: "What's the read-to-write ratio — how many times is a shortened URL clicked vs created?"**
- Why ask: this determines caching strategy. URL shorteners are typically 100:1 read:write.
- If mostly writes → focus on write optimization, fast collision detection
- If mostly reads (likely) → cache aggressively, use CDN for redirects

---

**Q: "Do we need global distribution — can users in different regions get different shortened URLs, or do we need a single global namespace?"**
- Why ask: single namespace requires a central ID generator (bottleneck); per-region allows sharding but complicates cross-region sync.
- Global namespace → Redis INCR bottleneck, or UUID-based (no collision risk but longer codes)
- Regional sharding → per-region generators, eventual consistency on metadata

---

**Q: "Are custom short codes in scope — like bit.ly/mycompany, or just auto-generated 6-character codes?"**
- Why ask: custom codes require a reservation system and uniqueness enforcement across users.
- If yes → add user namespace isolation, allow reservations
- If no → simpler; just auto-generate and return

---

**Q: "Should we track analytics — which links are most popular, geographic distribution of clicks?"**
- Why ask: analytics requires counters or event logging, which adds write complexity.
- If yes → separate analytics pipeline (Kafka → aggregation service)
- If no → simpler; just store redirect target

---

**Assumed answers (state these at the start of Section 3):**
- Type A focus — infrastructure + scale
- 1M URLs shortened/day, permanent lifespan (no auto-expiry)
- 100:1 read:write ratio (very read-heavy)
- Global namespace (single cluster, not regional sharding)
- Auto-generated codes only (no custom codes)
- No analytics tracking (defer as extension)

---

## Section 3 — 📋 Requirements

**Functional Requirements (what the system does):**
- Users can shorten a long URL and receive a 6–8 character short code (e.g., `bit.ly/abc123`)
- Users can redirect to the original URL by accessing the short code (HTTP 301/302 redirect)
- Short codes are globally unique — no collisions
- (Implicit) Short codes are not guessable — random or distributed generation, not sequential

**Out of scope (say these explicitly):**
- Custom short codes (user-specified vanity URLs)
- URL expiration / TTL (all shortened URLs are permanent)
- Analytics / click tracking
- QR code generation
- Batch shortening API
- User accounts / authentication (assume unauthenticated public API)

**Non-Functional Requirements:**
- Scale: 1M URLs shortened/day = ~11 shortening requests/sec (avg), ~33 requests/sec (peak 3×)
- Scale: 100:1 read:write = ~1,100 redirects/sec (avg), ~3,300 redirects/sec (peak 3×)
- Latency: P99 shorten request < 100ms; P99 redirect < 50ms
- Availability: 99.99% (< 52 minutes downtime/year)
- Consistency: short code uniqueness is strict (no duplicates permitted); redirect target is eventually consistent (acceptable)
- Durability: shortened URL mappings are durable — must survive server restarts

---

## Section 4 — 🔢 Scale Estimation (Minutes 5–10)

**What to do:** Do envelope math out loud. These numbers justify every architecture choice you make in Section 6+. The interviewer wants to see your *thinking*, not just your conclusion.

**Say this out loud (as you write the math on the whiteboard):**
> "Let me do some envelope math to justify the architecture. Starting with traffic..."

---

**Traffic:**
- DAU (estimated): 1M URLs created per day ÷ 10 (average user creates ~10 shortens) = ~100K DAU
- Shorten rate (avg): 1M URLs/day ÷ 86,400 sec = **~11 requests/sec**
- Shorten rate (peak 3×): **~33 requests/sec**
- Redirect rate (avg): 11 req/sec × 100 (read:write ratio) = **~1,100 requests/sec**
- Redirect rate (peak 3×): **~3,300 requests/sec**

**Storage:**
- Per shortened URL entry: ~500 bytes (short code 6 chars + original URL ~200 chars + metadata + timestamp)
- 1 year: 1M/day × 365 × 500 bytes = **~182.5 GB/year** (fits on single high-capacity DB node with headroom)
- 5 years: ~912 GB (still fits on a single node, but requires sharding for write scalability)

**Key conclusions:**
- "At 33 writes/sec peak, a single Postgres instance can handle this comfortably (max ~1K writes/sec). But we're read-heavy (3,300 reads/sec peak) — single instance bottlenecks on read throughput. We need caching in front."
- "At 182.5 GB/year, we can fit 5 years of data on a single high-capacity node. Archiving is optional at this scale."
- "3,300 reads/sec at P99 < 50ms requires cache hit rate > 95%. This suggests Redis in front of the DB."

---

## Section 5 — 🔄 Requirements Variation Table ⭐ KEY SECTION

| If the interviewer says... | Your architecture changes to... | The reasoning |
|---|---|---|
| "1B URLs/day instead of 1M" | Write sharding: hash(original_url) mod 10 → route to shard 0-9; each shard generates short codes independently | At 1B/day = ~11,500 writes/sec, a single Postgres instance maxes out around 5K writes/sec. Sharding distributes the load. |
| "Custom short codes (vanity URLs)" | Add user namespace: `bit.ly/{username}/{custom_code}`; enforce uniqueness per user OR globally depending on requirement | Custom codes require reservation/collision checking — adds a lookup before each shorten. Global uniqueness adds a distributed lock (Redis SET NX). |
| "URL expiration (TTL)" | Add TTL column; background job scans for expired entries daily; soft-delete (mark deleted, don't remove immediately) or hard-delete | Hard-delete while serving reads is risky (concurrent delete/redirect race). Soft-delete + asynchronous cleanup is safer. |
| "Analytics required" | Separate analytics pipeline: each redirect increments a counter service (Redis, HyperLogLog for cardinality, or Kafka → aggregation) | Analytics writes are 100:1 ratio to shorten writes. Storing clicks in the main DB would 100× write load. Use eventual consistency (Kafka) instead. |
| "Batch shorten API" | Same architecture; add bulk endpoint POST /shorten/batch { urls: [...] } returning array of short codes | Batch allows parallelization client-side, but server-side is still O(N) per URL. No fundamental change. |
| "Global multi-region deployment" | Per-region DB + async replication (or CRDT for global state); redirect always hits local region; shorten replicates to replicas (eventual consistency) | Cross-region latency is 50-150ms per hop. Local lookup hits < 50ms P99; replication async to tolerate latency. |

---

## Section 6 — 🏗️ High-Level Architecture (Minutes 10–25)

**What to do:** Draw the boxes (ASCII or whiteboard). Walk through the data flow *as if telling a story*. The interviewer is checking: "Does this person understand flow or just know boxes?"

**Say this out loud (as you start drawing):**
> "Let me draw a high-level architecture. This is how the system looks from 10,000 feet..."

---

### ASCII Architecture Diagram

```
[Client: shorten]  ──POST /shorten──→  [API Server 1]
                                             ↓
                                      [Request ID]
                                        (UUID v4)
                                             ↓
                         [Encode: base62(ID) → short code]
                                             ↓
                      [Write to Postgres] + [Cache in Redis]
                      [Return short code]
                                             ↑
                                      [Success response]

─────────────────────────────────────────────────────────

[Client: redirect] ──GET /{shortcode}──→  [API Server 2]
                                             ↓
                                    [Check Redis cache]
                                      (hit: ~1-5ms)
                                             ↓
                    [If miss: query Postgres] (~10-20ms)
                                             ↓
                                    [Populate Redis]
                                             ↓
                         [HTTP 301: Location: {original_url}]
                                             ↑
                                      [Client redirects]

─────────────────────────────────────────────────────────

[Optional Analytics]
                     [Redirect event] → [Kafka topic]
                                            ↓
                              [Aggregation service]
                                 (counts/heatmaps)
```

**Data flow walkthrough (say this out loud):**

1. **Shorten request:** Client POST /shorten with { "long_url": "..." }. API server generates a UUID v4. Encodes it as base62 (6 chars covers ~68B permutations — no collision risk). Writes mapping (short_code → original_url) to Postgres. Populates Redis cache with 1-hour TTL. Returns the short_code.

2. **Redirect request:** Client GET /{shortcode}. API server checks Redis first (likely hit, ~1-5ms). On miss, queries Postgres (10-20ms). Populates Redis. Returns HTTP 301 Location header pointing to original_url. Client's browser follows the redirect automatically.

3. **Analytics (optional):** On redirect, emit event to Kafka. Aggregation service consumes, updates counters in Redis or timeseries DB. This is fully decoupled from the critical path (redirect latency).

**Each box justified:**
- **API Servers:** stateless; scale horizontally behind load balancer. No session affinity needed.
- **Postgres:** source of truth; write-optimized with single-row insert per shorten. Handles ~33 writes/sec peak easily.
- **Redis:** caching layer; 1-hour TTL balances memory cost (182 GB/year ÷ 8760 hours ÷ 0.05 hit-fraction ≈ few GB) against hit rate (>95%).
- **Kafka (optional):** decouples analytics from critical path; provides replay if aggregation service is down.

---

## Section 7 — 🔬 Core Component Deep Dives (Minutes 25–40)

**What to do:** Pick 2–3 *riskiest* components. "Riskiest" = where the system most likely fails, where scale hits hardest, or what's unique to this problem.

**Why not 5 deep dives?** Under stress, your working memory shrinks 40–50%. If you try to hold 5 things, you'll confuse them. Pick the hardest 2–3 and go deep.

**Why these 3 for URL shortening?**
1. **Short code generation & collision detection** — Wrong choice = system returns duplicate codes = two users share the same URL = data corruption
2. **Caching strategy for reads** — 3,300 redirects/sec, P99 < 50ms, can't hit DB every time = must cache aggressively
3. **Database schema & indexing** — Wrong primary key = slow lookups; wrong indexes = slow redirects

**Say this out loud:**
> "Let me go deep on the three riskiest components — the ones where the system most likely breaks at scale..."

---

### Deep Dive 1: Short Code Generation — Collision Detection & Base62 Encoding

**Why this is the most critical component:**
The entire system depends on short codes being globally unique. A collision = two different original URLs map to the same short code = data corruption. The method must be deterministic and collision-free.

**Options considered:**

| Option | Pros | Cons |
|---|---|---|
| **Sequential counter (Redis INCR)** | Deterministic, no collision risk, short codes are sequential (1, 2, 3...) | Bottleneck: single Redis instance, max ~500K increments/sec. At 33 shorten/sec it's fine, but doesn't scale. Also, sequential codes are guessable (security risk). |
| **UUID v4 → base62 encode** | No collision risk (2^122 space is effectively infinite). Stateless (no central counter). Codes are random (not guessable). | Slightly longer codes (UUID 36 bytes → base62 ~22 chars; could compress to ~13 chars with URL-safe base64). Requires conversion logic. |
| **Zookeeper/Snowflake-style distributed ID** | Scalable, unique, not guessable. Handles multi-region. | Complex; requires coordination service. Overkill for 33 writes/sec. |

**Decision: UUID v4 → base62 encoding**
Because at this scale, a single Redis INCR is fast enough, but UUID is simpler (no external state), stateless, and naturally random. The slight code length increase (6 chars UUID vs 8 chars base62) is acceptable.

**In an interview, if asked:** "UUID v4 gives me collision-free generation without a central state. I encode it as base62 to create pronounceable, URL-safe short codes. At 33 writes/sec, Redis INCR is overkill, and UUID scales to any future load without changes."

**Implementation sketch:**

```java
public String generateShortCode() {
    // Generate UUID v4
    UUID uuid = UUID.randomUUID();
    
    // Encode as base62 (0-9, a-z, A-Z)
    long mostSigBits = uuid.getMostSignificantBits();
    long leastSigBits = uuid.getLeastSignificantBits();
    
    // Combine bits into single number (simplified for 6-char code)
    long id = Math.abs((mostSigBits ^ leastSigBits));
    
    // Base62 encoding
    return encodeBase62(id, 6);  // 6-char code
}

private String encodeBase62(long num, int length) {
    String base62Chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    StringBuilder sb = new StringBuilder();
    while (num > 0) {
        sb.append(base62Chars.charAt((int)(num % 62)));
        num /= 62;
    }
    // Pad to desired length
    while (sb.length() < length) {
        sb.append("0");
    }
    return sb.reverse().toString();
}
```

**Why base62 instead of base64?**
Base62 (0-9, a-z, A-Z) avoids special characters (+, /, =) that require URL encoding. Base64 includes special chars; a URL like `bit.ly/abc+123` requires encoding the `+` as `%2B`. Base62 is URL-safe natively.

---

### Deep Dive 2: Caching Strategy — Redis TTL & Hit Rate

**Why this is the riskiest component:**
3,300 redirects/sec peak, P99 < 50ms latency. A single Postgres query (10-20ms) leaves ~30ms budget for network + API overhead. If cache miss rate is > 10%, we hit DB > 300/sec. At that rate, Postgres connection pool (typical max ~20 conns) saturates, and latency degrades to 100-200ms+.

**Caching strategy options:**

| Option | Pros | Cons |
|---|---|---|
| **No cache** | Simplest, no staleness. | DB can't handle 3,300 reads/sec. Latency > 100ms. |
| **Cache-aside (lazy loading)** | Only cache hot URLs. Memory-efficient. | Writes latency spike on cache miss. Need TTL + invalidation policy. |
| **Read-through cache** | Hides latency. Client always hits cache layer. | Requires cache-aware data access layer. More code. |
| **Cache warming (pre-load top URLs)** | Reduces cold misses. | Need background job to track "top" URLs. Doesn't help new URLs. |

**Decision: Cache-aside with 1-hour TTL**
Because most URLs are written once, read many times. 1 hour covers bursts of consecutive reads from the same URL. After 1 hour, the URL is likely inactive (no recent reads), so evicting it saves memory. New URLs will be cached on first access.

**Query pattern:**

```sql
-- Redirect lookup (cache-aside)
1. Check Redis: GET short_code
2. If hit → return original_url (1-5ms)
3. If miss → 
     Query Postgres: SELECT original_url FROM urls WHERE short_code = ?
     Populate Redis: SET short_code original_url EX 3600  (1-hour TTL)
     Return original_url (10-20ms + network RTT)
```

**Memory math:**
- Assume 20% of URLs get 80% of reads (Zipf distribution)
- Per year: 1M URLs/day × 365 = 365M total URLs
- 20% = 73M URLs
- 73M × 500 bytes = 36.5 GB (if all are cached)
- In practice, only active URLs in the 1-hour window are cached = few GB

---

### Deep Dive 3: Database Schema & Indexing

**Why this is the most critical component:**
Wrong schema = slow lookups on redirects. At 3,300 reads/sec, a full table scan on 365M rows is impossible. Indexing strategy determines latency.

**Schema:**

```sql
CREATE TABLE urls (
    short_code          VARCHAR(8) PRIMARY KEY,  -- 6-8 char base62 code
    original_url        TEXT NOT NULL,           -- up to 2KB
    created_at          TIMESTAMP DEFAULT NOW(),
    expiry_at           TIMESTAMP,               -- nullable; NULL = never expire
    INDEX idx_created   (created_at)             -- for archiving queries
);
```

**Key schema decisions:**
- **Primary key = short_code:** Direct lookup by short_code is the critical path. O(1) hash or B-tree lookup. Alternative (auto-increment ID) would require secondary index on short_code, adding latency.
- **original_url as TEXT:** URLs can be 2KB+. VARCHAR(255) is insufficient.
- **No user_id:** Assume public API (no authentication). If users were tracked, add user_id + index (user_id, created_at) for "my shortened URLs" queries.
- **expiry_at nullable:** Some URLs never expire; others have TTL. Nullable allows both patterns without schema complexity.

**Indexing strategy:**
```sql
-- Primary index (auto-created)
PRIMARY KEY (short_code)

-- Optional: for "list URLs created on date X" (admin queries, not critical path)
CREATE INDEX idx_created ON urls(created_at DESC);

-- If analytics required: track click counts
ALTER TABLE urls ADD COLUMN click_count INTEGER DEFAULT 0;
-- But don't UPDATE click_count on every redirect (write bottleneck)
-- Instead, use Redis counter + async sync to DB
```

---

## Section 8 — 🌐 API Design

### Core Endpoints

| Method | Path | Auth | Request body | Response | Status codes |
|---|---|---|---|---|---|
| POST | `/v1/shorten` | None | `{ "long_url": "..." }` | `{ "short_code": "abc123" }` | 201, 400, 409 |
| GET | `/{short_code}` | None | — | HTTP 301 to original_url | 301, 404 |
| GET | `/api/v1/info/{short_code}` | Optional | — | `{ "short_code": "...", "original_url": "...", "created_at": "...", "clicks": ... }` | 200, 404 |

### WebSocket Protocol
None (not needed for this question).

### Key Design Decisions:
- **Idempotency:** POST /shorten is idempotent by input. If same long_url is submitted twice, return same short_code (check cache first, then DB query). Use idempotency-key header if needed, or make GET short_code deterministic from original_url hash.
- **Versioning:** /v1/ in path (vs Accept-Version header) for simplicity; easier to debug.
- **Pagination:** Not applicable (no list endpoints in MVP).
- **Error format:** Standard HTTP status codes. 409 Conflict if custom short_code is already taken. 400 Bad Request if long_url is malformed.

---

## Section 9 — 🗄️ Data Model

### Core Tables

```sql
CREATE TABLE urls (
    short_code          VARCHAR(8) PRIMARY KEY,
    original_url        TEXT NOT NULL,
    created_at          TIMESTAMP DEFAULT NOW(),
    expiry_at           TIMESTAMP,
    created_by_ip       VARCHAR(45),         -- optional: IPv4 or IPv6 for abuse tracking
    INDEX idx_created   (created_at)
);
```

### Key Schema Decisions:
- **VARCHAR(8) for short_code:** Base62-encoded UUID is max ~13 chars, but we use first 6-8. Future-proof for 68B unique codes.
- **TEXT for original_url:** URLs can exceed VARCHAR(255).
- **created_at:** Timestamps for accounting, archiving, rate-limiting per IP.
- **No user_id:** Simplifies anonymity. If required later, add user_id + index.

---

## Section 10 — ⚠️ Trade-offs + Failure Modes (Minutes 40–48)

**What to do:** Name exactly 3 major trade-offs. For each: what you chose, what you gain, what you lose, what breaks if you chose wrong.

**Why this matters (from DocuSign PDF):** "We are more interested in seeing how you think through the pros and cons of different approaches."

**Say this out loud:**
> "Let me step back and name the three major trade-offs in this design..."

---

### Trade-off 1: UUID-Based vs Central Counter (Redis INCR) for ID Generation

- **Chose:** UUID v4 (stateless)
- **Gain:** No central bottleneck; scales infinitely; each API server generates IDs independently
- **Lose:** Slightly longer encoding (UUID 36 bytes → base62 ~13 chars vs INCR 8 bytes → ~5 chars). Tiny memory cost per URL.
- **Failure mode if wrong:** If we chose Redis INCR and traffic spiked to 1K shorten/sec, Redis INCR becomes the bottleneck. Single-threaded Redis max ~500K ops/sec, but network RTT adds latency. Shorten response time spikes to 50-100ms. With UUID, we're unaffected by traffic spikes.

### Trade-off 2: Cache-Aside (1-hour TTL) vs Cache-Warming vs No Cache

- **Chose:** Cache-aside with 1-hour TTL
- **Gain:** Simple to implement. Memory-efficient (only active URLs cached). Cache invalidation is automatic (TTL expiry).
- **Lose:** Cold misses on URLs accessed after cache expires. New URLs have first-access latency spike (10-20ms vs 1-5ms for cached).
- **Failure mode if wrong:** If we chose no cache, 3,300 reads/sec hits Postgres directly. Postgres max ~5K reads/sec at P99 < 10ms. At 3,300 reads/sec, CPU utilization is ~66%, and any spike causes latency to exceed 50ms SLA. With cache-aside, only cache misses hit DB (estimated 5% = 165 reads/sec), well within Postgres capacity.

### Trade-off 3: Single Global Namespace vs Per-User Namespaces

- **Chose:** Single global namespace (short codes are globally unique)
- **Gain:** Simpler API. No user registration required. Codes are short (6 chars instead of `/username/abc123`). 
- **Lose:** Users can't customize codes; can't "reserve" a code. No per-user quotas (anyone can create unlimited shortened URLs).
- **Failure mode if wrong:** If we chose per-user namespaces and didn't implement quotas, a single user could create millions of URLs and exhaust storage. Global namespace forces us to implement rate-limiting by IP or API key to prevent abuse, but that's a separate concern.

---

## Section 11 — 🔐 DocuSign-Specific Depth (Minutes 48–52)

**What to do:** For PDF-example questions (A1, A2), the DocuSign signal is naming which of the 7 evaluation dimensions your design addresses and how.

**This is NOT a DocuSign domain question.** URL shortening has no e-signature or document angle. The grading signal is: "Does the candidate understand how DocuSign evaluates, and can they map their generic design to those dimensions?"

**After the trade-offs, say this out loud:**

> "Let me pause and map this back to the DocuSign evaluation dimensions:
> - **Scalability:** Base62-encoded UUID generation scales infinitely without central bottleneck; Redis cache absorbs 3,300 reads/sec peak at P99 < 50ms
> - **Availability:** Postgres replication (master-slave) handles read replicas; cache misses gracefully degrade (slower but still functional). 99.99% target achieved via active-active API server pool
> - **Security:** Original URLs are encrypted at rest (Postgres full-disk encryption); in transit (HTTPS). Short codes are random (not sequential), preventing enumeration attacks
> - **Observability:** Request ID (UUID) injected at API entry; logged in Postgres for audit trail. Cache hit/miss rates monitored; redirect latency tracked per percentile
> - **Extensibility:** Analytics pipeline (Kafka) is decoupled from core; adding click tracking means adding a consumer, not rewriting the shorten/redirect logic
> - **Testability:** URL lookup (Postgres query) is a pure function; easily mocked. Cache (Redis) is optional in tests; fake in-memory cache replaces it
> - **Usability:** API is stateless and simple: POST /shorten returns short_code; GET /{short_code} returns 301 redirect. No authentication needed; error responses are HTTP standard (404, 409, 400)"

---

## Section 12 — 🔬 Where the Interviewer Will Probe (Minutes 52–60)

**What to do:** Prepare for 3 tiers of follow-ups. Tier 1 (surface) — everyone gets it. Tier 2 (deep) — tests if you *understand*, not just *know*. Tier 3 (cross-concept) — separates senior candidates.

**Why 3 tiers?** The interviewer is watching your depth. Answer Tier 1 in 2–3 sentences. Tier 2 in 3–4 sentences with specific technical detail. Tier 3 requires you to reason across system boundaries.

**If you get a Tier 3 question, it's a good sign** — they think you're strong enough to probe the hard stuff.

---

### Surface Probe (Tier 1 — every candidate gets this)

**Q: "Why base62 encoding instead of base36 or base64?"**
> Base62 includes digits (0-9) and both upper/lowercase letters (A-Z, a-z), so it uses all alphanumeric characters. This gives the highest encoding density — 6 chars in base62 covers ~68B permutations vs base36 (36 chars) covering ~2B. Base64 includes special chars (+, /) that require URL encoding; base62 is URL-safe natively. In an interview: "I pick base62 to maximize code density and avoid special-character encoding overhead."

**Q: "What happens if two users try to shorten the same long URL — do they get the same short code?"**
> Yes, they should. This saves storage and reduces collisions. The API should check: does this long_url already have a short_code? If yes, return it (idempotent). If no, generate a new one. This is a simple SELECT before INSERT pattern, with a unique constraint on original_url to prevent race conditions. In an interview: "I'd add a unique index on original_url to enforce single-mapping per URL. If two requests race, the DB's unique constraint prevents duplicates."

**Q: "How do you handle URL expiration — do you delete rows, or just mark them deleted?"**
> Mark deleted (soft delete) with an expiry_at timestamp. Don't delete rows while they're being queried (race condition between DELETE and SELECT). Instead, set expiry_at, add a filter WHERE expiry_at IS NULL to queries, and run a background cleanup job nightly. In an interview: "I use soft deletes to avoid race conditions. A cron job deletes expired rows at off-peak hours."

### Deep Probe (Tier 2 — tests real understanding)

**Q: "Your cache (Redis) has a 1-hour TTL. But what if a long_url is updated or deleted — how does the cache invalidate?"**
> In my design, URLs aren't updated or deleted (immutable once created). If the business needs to support deletion, I'd add a cache invalidation event: when DELETE is called, emit a message to invalidate the short_code from Redis immediately. Alternatively, use a versioned approach — if deletion is requested, mark as deleted in the DB, but old cached mappings still work until TTL expires (eventual consistency). In an interview: "If URLs were mutable, I'd use event-driven cache invalidation (Kafka) to broadcast deletions immediately. Since they're immutable, TTL-based expiry is sufficient."

**Q: "At 3,300 reads/sec peak, what's the expected cache hit rate, and how do you ensure it doesn't degrade over time?"**
> Assuming 20% of URLs receive 80% of traffic (Zipf), the cache hit rate should be ~90-95% with 1-hour TTL. Over time, as URLs age and become inactive, the hit rate on new URLs drops. I'd monitor cache hit rate continuously; if it drops below 85%, I'd increase TTL to 2 hours or add cache warming (background job that preloads top URLs hourly). In an interview: "I'd track cache hit rate as a metric. If it degrades, I can adjust TTL or add warming. A/B testing different TTLs helps optimize memory vs hit rate."

### Cross-Concept Probe (Tier 3 — separates senior candidates)

**Q: "Your system uses a single Postgres instance. What happens if it fails, and how does Redis cache help?"**
> Postgres failure: reads (redirects) are unaffected because 90% of redirects hit Redis (no DB query). New shortens fail (POST /shorten can't write to DB), so users can't create new URLs, but existing ones remain accessible. If Postgres is down for 1 hour, all cache entries are still valid (1-hour TTL). After 1 hour, if Postgres is still down, the next redirect to an uncached URL will fail with 500 error. To improve: I'd add Postgres replication (master-slave); promote a replica on master failure. See `SystemDesignConcepts/03-caching.md` for cache failure patterns. In an interview: "Redis acts as a reliability buffer — even if the DB fails, cached redirects work. I'd add Postgres replication and automatic failover to handle DB failures gracefully."

---

## Section 13 — 🐞 Common Mistakes on This Question

**Note:** Reading these mistakes BEFORE the interview prevents you from making them under stress. Your working memory will shrink, and you're most likely to default to mistakes you haven't explicitly prepared for.

---

- **Mistake 1:** Using a sequential counter (auto-increment) for short codes → **Why wrong:** Sequential codes are guessable. An attacker can enumerate all shortened URLs by incrementing a counter. **Say instead:** "I use a random UUID v4 + base62 encoding so codes are unpredictable. An attacker can't enumerate or guess other users' URLs."

- **Mistake 2:** Not thinking about collision detection → **Why wrong:** If two UUIDs collide (astronomically rare but possible), the system corrupts data (two URLs sharing a code). You sound like you didn't reason about the probabilistic aspect. **Say instead:** "UUID v4 collision probability is 2^-122 for 2^122 permutations. Practically impossible. If I wanted zero-collision guarantees, I'd use a central counter (Redis INCR), trading scalability for certainty."

- **Mistake 3:** Ignoring the read-heavy nature of the problem (100:1 read:write) → **Why wrong:** Without caching, the DB hits 3,300 reads/sec and bottlenecks. You'll sound like you didn't calculate scale. **Say instead:** "Redirects are 100:1 more frequent than shortens. I prioritize read latency via caching (Redis) and read replicas."

- **Mistake 4:** Proposing a "cache warming" strategy without explaining when/how → **Why wrong:** Cache warming adds complexity (background jobs, staleness) without clear benefit at this scale. **Say instead:** "At this scale, cache-aside (lazy loading) with 1-hour TTL is sufficient. Cache warming is an optimization I'd consider if hit rate dropped below 85%."

- **Mistake 5:** Not addressing what breaks if the database fails → **Why wrong:** You sound like you didn't think about availability. **Say instead:** "Redis cache provides a reliability buffer. New shortens fail, but existing cached redirects work. I'd add Postgres replication and automatic failover for full HA."

---

## Section 14 — 🧭 DocuSign Dimensions Checklist

| Dimension | Relevant? | How your design addresses it |
|---|---|---|
| Testability | ✅ | URL lookup (Postgres query) is a pure function; easily mocked. Cache (Redis) is optional in tests; replace with in-memory cache. |
| Usability | ✅ | API is stateless and simple: POST /shorten, GET /{code}. No authentication required. Error responses follow HTTP standards (201, 301, 404, 409). |
| Extensibility | ✅ | Analytics pipeline (Kafka) is decoupled; adding click tracking = adding a consumer, not rewriting core logic. |
| Security | ✅ | Random short codes prevent enumeration. HTTPS in transit. Postgres encryption at rest. Rate limiting by IP (future enhancement). |
| Availability | ✅ | Postgres replication (master-slave read replicas); Redis cache provides graceful degradation if DB is slow/down. 99.99% target via HA setup. |
| Scalability | ✅ | Stateless API servers scale horizontally. UUID generation is distributed (no bottleneck). Redis cache absorbs read traffic. Postgres can handle 33 writes/sec peak easily. |
| Observability & Traceability | ✅ | Request ID (UUID) injected at API entry; logged for audit trail. Cache hit/miss rates monitored. Redirect latency tracked per percentile (P50, P99). |

---

## Section 15 — 🧾 TL;DR Answer Summary (Review Morning-of-Interview)

**If you had 60 seconds to summarize the entire answer, say this:**

> "I'd design a URL shortener with stateless API servers generating UUID v4 short codes (base62-encoded, 6-8 chars). A Redis cache with 1-hour TTL absorbs redirects (3,300 reads/sec peak); the DB stores the mapping. Shortens are write-optimized (simple INSERT); redirects are read-optimized (cache + read replicas). The key trade-off is stateless generation (UUID) vs centralized counter (Redis INCR) — I chose UUID to avoid bottlenecks. The core insight: at this scale (1M shortens/day, 100:1 read:write ratio), caching is non-negotiable; the DB can handle writes, but reads require cache or we exceed 50ms SLA. In a DocuSign interview, I map this to all 7 evaluation dimensions — each component (API, cache, DB, replicas) addresses one or more."

**Why read this before your interview?**
The TL;DR fixes the core idea in your head. Under stress, you'll default to this mental model. When the interviewer asks unexpected questions, you'll reason from this core idea (caching + stateless generation), not from memorized details.

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 23, 2026 | **File created.** Type A — System Design. Based on: DocuSign PDF (confirmed question type), System Design Primer, ByteByteGo URL Shortener chapter. Fully integrated with DELIVERY-RECIPE framework: 🧠 preamble explaining structure + 60-minute time budget, 💾 Memory Anchors (6 core + 3 bonus), explicit timing callouts in all major sections (2, 4, 6, 7, 10, 11, 12), "say this out loud" dialogue framing, interview psychology context (working memory constraints, stress failure modes). Deep dives cover riskiest components: UUID vs INCR trade-off, cache-aside strategy, schema design. Pre-write checklist enforced: Section 0 Identity Card filled, Section 5 variation table covers 6 axes, Section 10 trade-offs include failure modes, Section 12 has all 3 probe tiers. Common Mistakes section (5 entries) emphasizes collision risk, cache necessity, failure modes. Result: Interview delivery-ready, zero refinement needed. |
