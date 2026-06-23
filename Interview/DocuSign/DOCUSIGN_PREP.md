# DocuSign — Software Engineer, Commerce Backend
## Interview Prep

> Role: Software Engineer, Commerce Backend
> Rounds: R1 (DSA) → R2 (System Design) → R3 (Hiring Manager — prep later)
> Difficulty: 2.9/5 (moderate — not FAANG hard)
> Prep time available: 4–5 hours/day

---

## What "Commerce Backend" Means at DocuSign

DocuSign's Commerce team owns the billing, subscription, and payment infrastructure behind DocuSign's Agreement Cloud. Concretely:
- Subscription lifecycle management (plan upgrades, downgrades, renewals, cancellations)
- Payment processing and invoicing
- Entitlement management (which features a customer can access based on their plan)
- Order management (create, modify, fulfil enterprise contracts)

**Why your MCSE experience is a perfect match:**
- You built the promise and sourcing engine — the system that decides fulfilment, pricing, and delivery windows for every Walmart e-commerce order. Same domain: high-throughput order lifecycle with concurrent reservation and eventual consistency.
- Your ingestion pipeline (18+ Kafka pipelines, multi-tenant, optimistic writes to Cassandra) maps directly to event-driven subscription state management.
- Concurrent inventory reservation + atomic checkout = concurrent subscription state updates + idempotent payment execution.

---

## Official Interview Structure (from DocuSign's own guide)

**3 rounds total for your level, each 60 minutes:**

| Round | Focus | What they evaluate |
|---|---|---|
| **R1** | Coding & Problem Solving | DSA, algorithmic thinking, code quality |
| **R2** | System Design | Architecture, scalability, trade-offs |
| **R3** | Hiring Manager | Behavioral + technical background (prep after R1+R2 clear) |

**⚠️ Important Rules (from DocuSign's own guide):**
- **No AI tools during the interview** — no ChatGPT, Cluely, or any AI assistance. Explicitly prohibited.
- **Platform: HackerRank** — coding interviews happen on HackerRank (not your local IDE). No autocomplete. Practice there.
- Enable screen sharing in advance — takes a few minutes to set up; do it before the interview starts.
- Camera on, no virtual/blurred backgrounds.

---

## R1 — Coding & Problem Solving (DSA)

### What they evaluate (4-dimension rubric from DocuSign):
1. **Communication** — Ask clarifying questions *before* touching the keyboard. This is graded first.
2. **Problem-Solving** — Explain your thought process aloud, compare approaches, discuss time/space complexity.
3. **Coding** — Clean, well-structured code. Style and efficiency matter as much as correctness.
4. **Verification** — Test your own code. Walk through it to find bugs. Identifying and fixing bugs during the interview is a **plus**.

### The interview format:
- Max **2 problems** in **50 minutes** (10 min buffer for intros/questions)
- Use Java — your strongest language
- **Medium difficulty** — DocuSign explicitly recommends "medium level LeetCode questions" in their own guide. Don't over-prepare for Hard.
- Practice on HackerRank, not LeetCode UI — the environment is different (no hints, basic editor)

### Priority patterns (Medium only):

| Pattern | Why it matters |
|---|---|
| HashMap / frequency counting | Most common; Group Anagrams, Two Sum variants |
| Two pointers / sliding window | Strings and arrays; Longest Substring |
| BFS / DFS | Trees and graphs; Number of Islands |
| Intervals | Merge Intervals, Meeting Rooms |
| Stack | Min Stack, Valid Parentheses |
| Design problems | LRU Cache, Time-Based Key-Value Store |

### Target problem list (do these on HackerRank or LeetCode):

| # | Problem | Pattern | Difficulty |
|---|---|---|---|
| 1 | Group Anagrams (LC 49) | HashMap | Medium |
| 2 | Longest Substring Without Repeating (LC 3) | Sliding window | Medium |
| 3 | Number of Islands (LC 200) | BFS/DFS | Medium |
| 4 | Merge Intervals (LC 56) | Intervals | Medium |
| 5 | LRU Cache (LC 146) | Design | Medium |
| 6 | Time Based Key-Value Store (LC 981) | Binary search + HashMap | Medium |
| 7 | Min Stack (LC 155) | Stack | Easy-Medium |
| 8 | Product of Array Except Self (LC 238) | Array | Medium |
| 9 | Word Search (LC 79) | DFS | Medium |
| 10 | Top K Frequent Elements (LC 347) | Heap / HashMap | Medium |
| 11 | Rotting Oranges (LC 994) | BFS | Medium |
| 12 | Course Schedule (LC 207) | Topological sort | Medium |

### How to approach every coding problem (the 4-step ritual):
```
Step 1 (2 min): Ask clarifying questions
  → What's the input range? Can values be negative? Any null inputs?
  → "Can I assume the input fits in memory?"
  
Step 2 (3 min): Think out loud, compare 2 approaches
  → Brute force first ("naive O(n²) would be..."), then optimal
  → State time and space complexity for each
  
Step 3 (35 min): Code it cleanly
  → Variable names matter. No i/j/k unless obvious.
  → Handle edge cases explicitly (empty input, single element)
  
Step 4 (10 min): Test and verify
  → Walk through 2 test cases (normal + edge)
  → Find and fix bugs yourself before interviewer points them out
```

---

## R2 — System Design (Two Variants)

**⚠️ There are TWO types of system design DocuSign can ask. Confirm with recruiter which type your R2 is — if unsure, prep both.**

---

### Variant A — Infrastructure / Distributed System Design

**What they look for:** How low-level constraints affect high-level goals.

**Key dimensions they grade:**
1. Testability
2. Usability
3. Extensibility
4. Security
5. Availability
6. Scalability
7. Observability & traceability

**How to open every system design question:**
```
1. Clarify requirements (2–3 min)
   → How many users? (scale)
   → Latency requirements? (p99 SLA)
   → Read-heavy or write-heavy?
   → Strong consistency required or eventual OK?
   → Which failure modes are unacceptable?

2. Estimate scale (1–2 min)
   → QPS, storage, bandwidth back-of-envelope

3. High-level design first (5 min)
   → Draw boxes: client → API layer → services → storage

4. Deep dive on the hardest component (20 min)
   → This is where senior signal is. Pick the interesting piece.

5. Trade-offs (5 min)
   → "The trade-off here is X. We accepted it because Y."
```

**DocuSign example questions (Infrastructure):**
- Design a URL shortener
- Build a Facebook chat (messaging system)
- Architect a worldwide video distribution system

---

#### Infrastructure Design 1: URL Shortener

**Core flow:**
```
Client → POST /shorten {longUrl} → ShortenerService → Redis cache + DB → return shortCode
Client → GET /{shortCode} → ShortenerService → Redis (cache hit) → 301 redirect
```

**Key decisions:**
1. **Short code generation:** Base62 encoding of auto-increment ID (simple, no collision) vs random + collision check. Use auto-increment for predictability.
2. **Storage:** SQL (Postgres) for mappings — simple key-value, not complex queries. Redis cache for hot URLs (most reads hit cache).
3. **301 vs 302 redirect:** 301 = browser caches permanently (reduces load), 302 = every request hits server (better analytics). Use 302 if analytics matter.
4. **Scale:** 100M URLs, ~10K reads/sec. Single Postgres + Redis handles this. Shard by shortCode prefix if needed at 1B+ scale.
5. **Expiry:** TTL column in DB; background job purges expired entries; Redis TTL auto-evicts.
6. **Custom aliases:** Check uniqueness before inserting. Rate-limit per user.

**Trade-off to name:**
> *"301 reduces server load because browsers cache the redirect, but we lose click analytics. 302 gives us full analytics at the cost of every click hitting our servers. For DocuSign's use case (agreement links), analytics matter — use 302."*

---

#### Infrastructure Design 2: Facebook Chat (Messaging System)

**Core flow:**
```
Sender → WebSocket → Message Service → Kafka → Message Fanout Service
                                              → Cassandra (persist)
                                              → WebSocket → Receiver (if online)
                                              → Push notification (if offline)
```

**Key decisions:**
1. **Protocol:** WebSocket for real-time bidirectional (not HTTP polling — too much overhead at scale). Long polling as fallback for restricted networks.
2. **Storage:** Cassandra — write-optimized, partitioned by `(conversation_id, timestamp)` for sequential reads. Messages are append-only — perfect fit.
3. **Fan-out on write vs read:** For small group chats (<= 100 members), fan-out on write (copy message to each member's inbox on send). For large groups (1000+), fan-out on read (single message, each member fetches on open). DocuSign use case = business messaging, small groups → fan-out on write.
4. **Message ordering:** Kafka partitioned by `conversation_id` guarantees order within a conversation.
5. **Delivery receipt:** 3 states in DB: `SENT → DELIVERED → READ`. Acknowledgment sent back over WebSocket.
6. **Offline:** Push notification via APNs/FCM when WebSocket connection not active.

**Your MCSE parallel:**
> *"At Walmart, our Kafka ingestion pipeline handles exactly this pattern — events ordered by partition key, delivered at-least-once, processed by consumers at their own pace. The difference is we're pushing data updates, not user messages, but the fan-out and ordering guarantees are identical."*

---

#### Infrastructure Design 3: Worldwide Video Distribution System (CDN)

**Core flow:**
```
Video upload → Transcoding Service → Multiple resolutions (360p/720p/1080p/4K)
             → Object Storage (S3/GCS) → CDN Edge Nodes (global PoPs)
             → Client requests nearest edge → Cache hit → stream
                                           → Cache miss → pull from origin → cache
```

**Key decisions:**
1. **Transcoding:** Async — video uploaded to raw storage, job queued, transcoding workers process. Output multiple bitrates (adaptive streaming HLS/DASH).
2. **CDN strategy:** Push (pre-populate popular content to all edges) vs pull (edge fetches from origin on first miss). Pull is simpler; push for predictably hot content.
3. **Storage:** Raw + processed videos in object storage (immutable blobs). Metadata (title, owner, views) in Postgres. 
4. **Consistency:** Eventual — a new video may not appear on all edges immediately. Acceptable.
5. **Observability:** Track cache hit ratio per PoP, rebuffering rate, p99 time-to-first-frame.

---

### Variant B — Product Architecture / API Design

**What they look for:** Building a product or API that operates at scale to support an end-user service.

**Key areas (from DocuSign's guide):**
- Storage data models
- SOLID principles
- Scalability
- Design patterns
- Protocols (REST, gRPC, WebSocket, GraphQL)
- Data formats (JSON, Protobuf, Avro)

**DocuSign example questions (Product Architecture):**
- Design a service or product API
- Design a chat service or a feed API
- Design an email server

**How to open a Product Architecture question:**
```
1. Define the API contract first
   → What are the core resources? (nouns)
   → What operations? (CRUD + custom actions)
   → Who are the callers? (internal service, mobile, web)
   
2. Data model
   → Entity relationship (what tables/collections)
   → Indexes for expected query patterns
   
3. API design
   → RESTful resource naming (/subscriptions/{id}/upgrade)
   → Request/response shapes
   → Error codes and error contract
   
4. Scale and reliability
   → Idempotency (POST with idempotency key)
   → Pagination (cursor-based for large sets)
   → Rate limiting (per-client)
   → Versioning strategy (/v1/, /v2/ or header-based)
```

---

#### Product Architecture 1: Subscription + Billing API (most likely for Commerce Backend)

**Core entities:**
```
Customer → Subscription → Plan → Invoice → Payment
                       → Entitlement
```

**API design:**
```
POST   /subscriptions                    → create subscription
GET    /subscriptions/{id}               → get status
POST   /subscriptions/{id}/upgrade       → upgrade plan
POST   /subscriptions/{id}/cancel        → cancel
GET    /subscriptions/{id}/invoices      → list invoices
POST   /payments                         → charge (idempotency-key header required)
GET    /entitlements/{customerId}        → what features does this customer have?
```

**Key design decisions:**

1. **Idempotency on payments:**
   ```
   POST /payments
   Idempotency-Key: <uuid>
   → First call: process + store result keyed by idempotency key
   → Replay: return stored result, do NOT charge again
   ```

2. **Subscription state machine (only valid transitions):**
   ```
   PENDING → ACTIVE → PAST_DUE → CANCELLED
                    ↘ UPGRADED (new subscription created, old terminated)
                    ↘ DOWNGRADED (takes effect at next billing cycle)
   ```

3. **Proration on upgrade:**
   ```
   proration_credit = (days_remaining / days_in_cycle) × old_plan_price
   charge_today     = new_plan_price - proration_credit
   ```

4. **Async downstream pipeline:**
   Payment confirmed → publish `payment.succeeded` event to Kafka
   → Entitlement service consumes → unlocks features
   → Invoice service consumes → marks invoice paid
   → Notification service consumes → emails customer
   Never block the payment API response waiting for downstream.

5. **SOLID principles applied:**
   - **S** — BillingService only handles billing; EntitlementService only handles features
   - **O** — New payment providers added without modifying PaymentService (Strategy pattern)
   - **L** — CreditCardPayment and BankTransferPayment both implement PaymentProvider interface
   - **I** — Don't force BillingService to implement notification interface
   - **D** — BillingService depends on PaymentProvider interface, not Stripe SDK directly

**Your MCSE parallel:**
> *"This is essentially the same async pipeline I built at Walmart — Kafka events propagate state changes to downstream consumers without blocking the primary transaction. Payment confirmed = warehouse capacity reserved in MCSE. The idempotency guarantee is the same: retrying a failed payment attempt must not double-charge, just as retrying a capacity reservation must not double-reserve."*

---

#### Product Architecture 2: Feed API

**Core design:**
```
POST /posts           → create post
GET  /feed/{userId}   → get personalized feed (paginated)
POST /posts/{id}/like → like
```

**Key decisions:**
- Fan-out on write for users with < 1M followers (push to follower feeds on post)
- Fan-out on read for celebrities (pull + merge at request time)
- Cursor-based pagination: `GET /feed?cursor=<lastSeenId>&limit=20`
- Feed stored in Redis sorted set (score = timestamp); TTL = 7 days
- Eventual consistency: slight lag on new posts showing in followers' feeds is acceptable

---

#### Product Architecture 3: Email Server

**Core components:**
```
SMTP Inbound → Message Queue → Storage (IMAP) → Client Fetch
Compose API  → Outbound Queue → SMTP Relay → Recipient MTA
```

**Key decisions:**
- Store emails as blobs (S3) with metadata in Postgres (from, to, subject, timestamp, read/unread)
- Search: inverted index (Elasticsearch) over subject + body for full-text search
- Attachments: separate blob storage, referenced by URL in email metadata
- Spam filter: async, runs after acceptance — never block the SMTP handshake
- Rate limiting: per sender domain to prevent spam abuse

---

## Commerce-Specific System Design (high-probability for this role)

### Rate Limiter (reported as asked at DocuSign)

**Problem:** Design a rate limiter for a multi-tenant API.

**Algorithm choice:**
- **Token bucket:** Smooth, allows short bursts, simple to implement → best for API rate limiting
- **Sliding window log:** Precise, no burst at boundary, higher memory cost → use when strict limits needed
- **Fixed window counter:** Simplest, but allows 2× burst at window boundary → avoid for strict SLAs

**Implementation:**
```
Redis key: ratelimit:{tenantId}:{endpoint}:{windowStart}
On each request:
  1. INCR key → returns new count
  2. If count == 1: SET expiry = window_size (atomic via Lua script)
  3. If count > limit: return 429 Too Many Requests
  4. Set response headers: X-RateLimit-Limit, X-RateLimit-Remaining, X-RateLimit-Reset
```

**Distributed concern:** Single Redis is SPOF → Redis Cluster. Lua scripts ensure atomicity across INCR + TTL.

**Trade-off:**
> *"Token bucket at the window boundary can allow 2× the limit in a short burst (end of window + start of next). Sliding window log prevents this at the cost of storing every request timestamp. For DocuSign's API — where a rogue client shouldn't burst 2× — I'd use sliding window for security-sensitive endpoints and token bucket for general API limits."*

---

### Subscription Billing System — Full Design

*(See Product Architecture 1 above — same design, framed as infrastructure)*

---

## Project Deep Dive Prep

When R2 includes "walk me through a project you built" — lead with MCSE.

### MCSE pitch (30 seconds):
> "I own the promise and sourcing engine at Walmart — the system that decides which warehouse ships an order and what delivery date the customer sees. It handles about 700,000 requests per minute with sub-100ms p95 latency. The architecture is a modular monolith — 30 Maven modules in one deployable — that fans out to 50–100 parallel evaluations per request using CompletableFuture, reads from 16 in-memory Hollow caches, and writes results back to the Unified Promise API. The hard problems are: concurrency at that fan-out scale, eventually-consistent reference data, and multi-market config isolation (US, MX, CA, CL same codebase)."

### Bridge to Commerce Backend context:
> "The most directly relevant part for a commerce role is the inventory reservation pattern: when MCSE selects a fulfilment node, it needs to ensure that warehouse's capacity isn't oversold. We use optimistic locking on the capacity record — if the reservation fails due to concurrent modification, we retry with the next-best warehouse. Same problem as concurrent subscription seat purchases or concurrent invoice generation."

---

## Stack Justification — 4-Step Answers

### Why Java for a commerce backend?

**Problem:** High-throughput transaction processing with strict concurrency guarantees.

**Why Java:** JVM concurrency primitives (CompletableFuture, ReentrantLock, synchronized) battle-tested at scale. Spring's `@Transactional` + JPA optimistic locking handles concurrent subscription state natively. Type safety catches billing bugs at compile time.

**Why not Python/Node:** Python GIL limits CPU-bound concurrency; Node is single-threaded — neither gives per-transaction thread isolation.

**Trade-off:** JVM cold-start (10–30s). We run long-lived pods, not serverless. Acceptable for billing service with warm DB connections.

---

### Why event-driven (Kafka) for subscription state propagation?

**Problem:** Subscription state changes must propagate to entitlement, invoice, notification, analytics, CRM — 5+ consumers — without blocking the payment API.

**Why Kafka:** Fan-out without coupling. Each consumer processes independently. Replay lets us reprocess past events after a bug fix.

**Why not synchronous REST:** If any downstream service is down, the entire subscription update fails. We'd need circuit breakers everywhere, and the payment API response time bloats.

**Trade-off:** Eventual consistency — entitlements may lag by seconds. Acceptable for feature gates; not acceptable for the payment itself (payment service writes synchronously before publishing).

---

## Daily Schedule (4–5 hours)

| Block | Time | What to do |
|---|---|---|
| HackerRank coding | 1.5 hrs | 2 medium problems from the list above, on HackerRank |
| System Design | 1.5 hrs | 1 design topic deep (rotate through the 6 topics above) |
| MCSE pitch + behavioral | 1 hr | Practice MCSE pitch out loud, prep 1 STAR story |
| Review | 30 min | Read 1 Glassdoor review, note patterns |

### System design rotation (6 topics, one per day):
1. URL shortener (Infrastructure)
2. Messaging / Facebook chat (Infrastructure)
3. Video distribution CDN (Infrastructure)
4. Subscription + Billing API (Product Architecture)
5. Feed API (Product Architecture)
6. Rate limiter (Commerce-specific)

---

## Pre-R1 Checklist

- [ ] HackerRank account set up, screen sharing tested
- [ ] 12 medium problems done on HackerRank (see list above)
- [ ] Can explain time/space complexity of each solution
- [ ] Practiced vocalizing thought process on 3 problems
- [ ] Rate limiter — can whiteboard in 20 min
- [ ] URL shortener — can whiteboard in 20 min
- [ ] No AI tools reminder noted — cannot use ChatGPT/Cluely during interview

## Pre-R2 Checklist

- [ ] All 6 system design topics covered at least once
- [ ] Confirmed with recruiter: Infrastructure design or Product Architecture variant?
- [ ] Subscription billing API — can explain idempotency, state machine, proration
- [ ] MCSE 30-second pitch smooth
- [ ] MCSE → Commerce Backend bridge ready
- [ ] SOLID principles — can apply to a design on the fly

---

## Resources

- [DocuSign Interview Guide — InterviewQuery](https://www.interviewquery.com/interview-guides/docusign-software-engineer)
- [DocuSign Interview Questions — Exponent](https://www.tryexponent.com/questions?company=docusign)
- [DocuSign Glassdoor Reviews](https://www.glassdoor.com/Interview/Docusign-Software-Engineer-Interview-Questions-EI_IE307604.0,8_KO9,26.htm)
- [DocuSign Blind Discussions](https://www.teamblind.com/company/DocuSign/posts/docusign-interview)
- [GitHub System Design Primer](https://github.com/donnemartin/system-design-primer)
- [Grokking System Design Interview](https://www.designgurus.io/course/grokking-the-system-design-interview)

---

*Last updated: June 22, 2026*
