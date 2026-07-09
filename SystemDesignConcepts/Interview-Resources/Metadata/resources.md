# Resources — Curated Study List

> **Research date:** June 2026. Cross-referenced across 5 searches: YouTube recommendations, GitHub star counts, Reddit/Blind community picks, ByteByteGo vs HelloInterview comparison, and real interview experience posts from LeetCode Discuss.
>
> **Selection criteria:** Only resources that appeared in MULTIPLE sources or had 10K+ GitHub stars / strong community consensus. Not random blogs.

---

## 🎯 The Short Answer — Start Here

If you only have time for 3 things before an interview:

| Priority | Resource | What it gives you | Cost |
|---|---|---|---|
| 1 | **Arpit Bhayani (YouTube — Asli Engineering)** | Concept-level depth — caching, DB internals, consistent hashing, distributed locking. Most technically rigorous free content available. | FREE |
| 2 | **ashishps1/awesome-low-level-design** (GitHub) | 20+ LLD problems from easy to hard, OOP, design patterns, concurrency — interview-ready format. | FREE |
| 3 | **hellointerview.com — "System Design in a Hurry"** | Structured, interview-aligned system design guide built by FAANG hiring managers. Free section covers all core concepts. | FREE (partial) |

---

## 🔹 LLD Resources (Ranked)

### 1. ashishps1/awesome-low-level-design ⭐ PRIMARY
- **Link:** https://github.com/ashishps1/awesome-low-level-design
- **Stars:** 36.9K+ (community-validated)
- **What it covers:**
  - OOP fundamentals (encapsulation, inheritance, polymorphism, abstraction)
  - SOLID principles (with examples)
  - All major design patterns (Singleton, Factory, Strategy, Observer, Command, Builder, etc.)
  - 20+ interview problems (Easy → Hard): Parking Lot, Elevator, BookMyShow, Splitwise, Ride Sharing, Food Delivery
  - Concurrency handling in LLD
- **Why it's #1:** Appears in almost every "best LLD resources" list. Free alternative to paid courses. Java implementations included.
- **How to use:** Read the SOLID section first → pick 3-4 problems matching the frequency table → code them yourself

### 2. Concept and Coding with Shreyansh (YouTube)
- **Search:** "Concept and Coding Shreyansh LLD"
- **What it covers:** LLD walkthroughs with live coding, design pattern explanations, SOLID principles
- **Best for:** Seeing how an interviewer thinks through a problem in real time

### 3. sudoCode (YouTube)
- **Search:** "sudoCode LLD system design"
- **What it covers:** System design + LLD, clean explanations of design patterns with real use cases
- **Best for:** Quick concept reinforcement, 10-20 min videos

### 4. Tech Dummies Narendra L (YouTube)
- **Search:** "Tech Dummies Narendra L LLD"
- **Best for:** Both HLD + LLD balance if you want one channel for both

---

## 🔹 System Design Concepts Resources (Ranked)

### 1. Arpit Bhayani — Asli Engineering (YouTube) ⭐ PRIMARY
- **Link:** https://www.youtube.com/c/ArpitBhayani
- **Website:** https://arpitbhayani.me
- **What it covers:**
  - Database internals (how MySQL/Postgres actually work)
  - Caching (LRU, LFU, eviction, Redis internals)
  - Consistent hashing (with implementation)
  - Distributed locking, rate limiting
  - System Design Masterclass (paid, but free videos cover 80% of interview content)
- **Why it's #1:** 160K+ subscribers. Technically rigorous — explains WHY behind every concept, not just what. Engineers on Blind consistently recommend this for senior-level prep.
- **Best videos to start:** Search "Arpit Bhayani consistent hashing", "Arpit Bhayani rate limiting", "Arpit Bhayani distributed locking"

### 2. hellointerview.com — "System Design in a Hurry" ⭐ PRIMARY (FREE)
- **Link:** https://www.hellointerview.com/learn/system-design/in-a-hurry/introduction
- **What it covers:** Complete structured guide — caching, rate limiting, databases, messaging, sharding — interview-aligned
- **Why it's #2:** Built by FAANG hiring managers. Written specifically for interview performance, not academic depth. Free section is comprehensive.
- **Best for:** Rate Limiter, Caching strategy walkthroughs with trade-offs

### 3. System Design Primer (GitHub)
- **Link:** https://github.com/donnemartin/system-design-primer
- **Stars:** 270K+ (most starred system design repo on GitHub)
- **What it covers:** CAP theorem, scalability, latency, caching, load balancing, databases, sharding, consistent hashing, communication patterns
- **Why it's here:** The OG reference. Not interview-specific but covers fundamentals thoroughly.
- **How to use:** Reference only — don't read end-to-end. Look up specific concepts when needed.

### 4. ashishps1/awesome-system-design-resources (GitHub)
- **Link:** https://github.com/ashishps1/awesome-system-design-resources
- **Stars:** 36.9K+
- **What it covers:** Curated list of free articles, videos, and resources for every system design concept
- **Best for:** Finding the best article/video on any specific topic (consistent hashing → best article, Bloom filter → best video, etc.)

### 5. ByteByteGo (YouTube — FREE videos)
- **Link:** https://www.youtube.com/@ByteByteGo
- **What it covers:** Visual explainers for system design concepts — API gateway, CDN, caching, rate limiting, database indexing
- **Why use the free YouTube:** Their paid course is strong but the YouTube channel alone covers most interview topics with excellent visuals
- **Best for:** Quick 5-10 min visual explanations of any concept before going deeper with Arpit

---

## 🔹 Priority Study Order (Based on Interview Frequency)

From research — topics ranked by how often they appear in actual SDE2/SDE3 interviews:

### LLD Problems (do in this order)
| # | Problem | Pattern to learn | Difficulty |
|---|---|---|---|
| 1 | **Parking Lot** | Factory, Strategy | Easy — warm up |
| 2 | **BookMyShow / Movie Ticket** | Observer, State machine | Medium |
| 3 | **Splitwise** | Strategy, Observer | Medium |
| 4 | **Elevator System** | Command, Strategy | Medium |
| 5 | **LRU Cache** | Doubly LL + HashMap | Medium — technical |
| 6 | **Rate Limiter** | Strategy (token bucket vs sliding window) | Hard — technical |
| 7 | **Meeting Room Reservation** | Concurrency + State | Hard |
| 8 | **Ride Sharing (Uber-lite)** | Observer, Strategy, Factory | Hard |

**Concurrency is mandatory for ALL of the above.** After solving each, ask yourself: "How do I make this thread-safe?"

### System Design Concepts (do in this order)
| # | Concept | Resource to use |
|---|---|---|
| 1 | **Optimistic + Pessimistic Locking** | Arpit Bhayani |
| 2 | **Rate Limiting** (token bucket, sliding window) | hellointerview.com + Arpit |
| 3 | **Caching** (LRU, TTL, eviction, Redis) | hellointerview.com + ByteByteGo |
| 4 | **Idempotency** | Arpit Bhayani |
| 5 | **Consistent Hashing** | Arpit Bhayani |
| 6 | **Distributed Locking** (Redis SETNX, Redlock) | hellointerview.com |
| 7 | **CDC + Outbox Pattern** | Arpit Bhayani |
| 8 | **Kafka / Stream Processing basics** | ByteByteGo YouTube |
| 9 | **Bloom Filter** | ashishps1 resources |
| 10 | **Backpressure** | Arpit Bhayani |

---

## 🔹 Resources for DocuSign R2 Gap Concepts (Added June 2026)

> These three concepts were not in the original resources list — surfaced from DocuSign R2 research. Resources curated specifically for interview depth, not textbook depth.

### API Design (for `11-api-design.md`)

| Resource | What it covers | Link |
|---|---|---|
| **hellointerview.com — API Design** ⭐ | REST contract design, versioning, pagination, idempotency keys — interview-aligned | https://www.hellointerview.com/learn/system-design/core-concepts/api-design |
| **DocuSign Engineering Blog — Pagination** | Real-world cursor pagination decisions at DocuSign (primary source for C3 question) | https://www.docusign.com/blog/developers/the-trenches-api-pagination |
| **Arpit Bhayani — API Versioning** | Search "Arpit Bhayani API versioning" — covers breaking vs non-breaking changes | YouTube / arpitbhayani.me |

**Key concepts this needs to cover:** HTTP verbs + idempotency, cursor vs offset pagination, API versioning strategies (`/v1/` in path vs `Accept-Version` header), request/response schema design, status code semantics (200 vs 201 vs 204 vs 409 vs 422).

---

### Relational Data Modeling (for `12-data-modeling.md`)

| Resource | What it covers | Link |
|---|---|---|
| **hellointerview.com — Data Models** ⭐ | Storage data model selection (SQL vs NoSQL), schema design for interview | https://www.hellointerview.com/learn/system-design/deep-dives/sql |
| **Arpit Bhayani — Database Internals** | How indexes work, why normalisation matters, B-tree indexing | YouTube: "Arpit Bhayani database internals" |
| **System Design Primer — Database section** | SQL vs NoSQL trade-offs, sharding, replication — reference only | https://github.com/donnemartin/system-design-primer#database |

**Key concepts this needs to cover:** 1NF/2NF/3NF normalisation, foreign keys + cascade rules, indexing strategy (single-column vs composite), validation at DB layer (CHECK constraints, NOT NULL), when to denormalise for read performance, choosing SQL vs NoSQL for interview questions.

---

### Idempotency (for `04-idempotency.md`)

| Resource | What it covers | Link |
|---|---|---|
| **Arpit Bhayani — Idempotency** ⭐ | Server-side deduplication mechanics, DB table design, Kafka consumer deduplication | YouTube: "Arpit Bhayani idempotency" |
| **Stripe Engineering Blog — Idempotent Requests** | Real-world idempotency implementation from Stripe's payment infrastructure | Search: "Stripe idempotent requests" |

**Key concepts this needs to cover:** Idempotency-Key header pattern, idempotency table schema, concurrent duplicate handling (unique constraint race), Kafka consumer deduplication with processed_events table, transaction scope.

---

### Security + PKI Fundamentals (for `13-security-pki.md`)

| Resource | What it covers | Link |
|---|---|---|
| **ByteByteGo — "How HTTPS works"** ⭐ | Asymmetric crypto, TLS handshake, certificate chain — best free visual | YouTube: search "ByteByteGo HTTPS TLS explained" |
| **Arpit Bhayani — JWT Deep Dive** ⭐ | JWT structure, RS256 (asymmetric) signing, verification — same concepts as DocuSign PKI | YouTube: search "Arpit Bhayani JWT" |
| **DocuSign — Digital Signatures FAQ** | Primary source: how DocuSign's own product uses PKI | https://www.docusign.com/products/electronic-signature/learn/digital-signature-faq |
| **hellointerview.com — Security section** | Authentication, authorisation, encryption at rest vs in transit | https://www.hellointerview.com/learn/system-design/core-concepts/security |

**Key concepts this needs to cover:** Symmetric vs asymmetric encryption, how digital signatures work (hash → sign with private key → verify with public key), SHA-256 and why we hash first, certificate authority chain, non-repudiation, audit trail as append-only log, multi-party signing order (sequential vs parallel).

---

### Distributed Locking (for `06-distributed-locking.md`)

| Resource | What it covers | Link |
|---|---|---|
| **hellointerview.com — Distributed Locking** ⭐ | Redis SETNX, Redlock algorithm, fencing tokens, when to use vs optimistic locking | https://www.hellointerview.com/learn/system-design/deep-dives/redis |
| **Arpit Bhayani — Distributed Locking** | Deep dive on Redis-based locking, deadlock prevention, implementation patterns | YouTube: "Arpit Bhayani distributed locking" |

**Key concepts this needs to cover:** SETNX lock acquisition, TTL to prevent deadlock, fencing token to prevent stale-lock problem, Redlock for multi-node Redis, safe lock release (only owner releases), distributed lock vs optimistic locking — when to choose each.

---

### CDC + Outbox Pattern (for `07-cdc-outbox.md`)

| Resource | What it covers | Link |
|---|---|---|
| **Arpit Bhayani — CDC and Outbox Pattern** ⭐ | Dual-write problem, outbox table design, Debezium CDC, at-least-once delivery | YouTube: "Arpit Bhayani outbox pattern" or "Arpit Bhayani CDC" |
| **ByteByteGo — Transactional Outbox** | Visual walkthrough of outbox pattern with polling vs CDC approaches | YouTube: search "ByteByteGo outbox pattern" |

**Key concepts this needs to cover:** Dual-write problem (DB + Kafka as two operations = race condition), outbox table as part of the same DB transaction, outbox processor (polling worker), CDC via Debezium reading the DB WAL/binlog, at-least-once delivery guarantee, idempotent consumer as the consumer-side contract.

---

### Optimistic + Pessimistic Locking (for `01-optimistic-pessimistic-locking.md`)

| Resource | What it covers | Link |
|---|---|---|
| **Arpit Bhayani — Optimistic and Pessimistic Locking** ⭐ | DB-layer concurrency control, row-level locking internals, when each strategy breaks | YouTube: "Arpit Bhayani optimistic locking" |
| **hellointerview.com — SQL Concurrency** | How locking fits system design decisions — interview-aligned with trade-off analysis | https://www.hellointerview.com/learn/system-design/deep-dives/sql |

**Key concepts this needs to cover:** @Version + Hibernate OptimisticLockException, SELECT FOR UPDATE (@Lock PESSIMISTIC_WRITE), retry loop with @Retryable, deadlock definition + consistent lock-order prevention, when to choose which (contention frequency + retry cost), interaction between retry and side effects (idempotency requirement).

---

### Consistent Hashing (for `05-consistent-hashing.md`)

| Resource | What it covers | Link |
|---|---|---|
| **Arpit Bhayani — Consistent Hashing** ⭐ | Ring structure, virtual nodes, node addition/removal, why modular hashing fails — most technically rigorous free explanation | YouTube: "Arpit Bhayani consistent hashing" |
| **ashishps1 — Consistent Hashing article** | Visual walkthrough of the ring + virtual nodes, interview-aligned with trade-offs | https://github.com/ashishps1/awesome-system-design-resources |
| **ByteByteGo — Consistent Hashing** | Visual explainer in 8 minutes — good for quick concept reinforcement after reading the note | YouTube: "ByteByteGo consistent hashing" |

**Key concepts this needs to cover:** Why modular hashing fails (N→N+1 causes mass key migration), hash ring construction, clockwise walk to server, virtual nodes for even distribution, node add/remove (only K/N keys move), real-world use (Redis Cluster, Cassandra, CDN edge routing).

---

### Bloom Filter (for `08-bloom-filter.md`)

| Resource | What it covers | Link |
|---|---|---|
| **ashishps1 — Bloom Filter** ⭐ | Bit array mechanics, k hash functions, false positive rate formula, use cases — community-validated best article | https://github.com/ashishps1/awesome-system-design-resources |
| **ByteByteGo — Bloom Filter** | Visual explainer — bit array animation, insertion/lookup steps | YouTube: "ByteByteGo bloom filter" |

**Key concepts this needs to cover:** Bit array + k hash functions, insert = set k bits, lookup = check k bits (all set → maybe yes, any unset → definitely no), false positive (never false negative), space efficiency (10 bits/element at 1% FP rate), use cases (URL shortener dedup, Cassandra SSTable lookup, rate limiting, DB query optimization), why you can't delete (use counting Bloom filter), sizing trade-offs.

---

### Sharded Counters (for `09-sharded-counters.md`)

| Resource | What it covers | Link |
|---|---|---|
| **Arpit Bhayani — Sharded Counters** ⭐ | Hot-write problem on a single counter row, shard table design, periodic aggregation, trade-off between exact vs approximate counts | YouTube: "Arpit Bhayani sharded counters" |
| **ByteByteGo — Counting at Scale** | Visual walkthrough of the hot-row bottleneck and how sharding splits write load | YouTube: search "ByteByteGo counting at scale" |
| **ashishps1/awesome-system-design-resources** | Curated article on distributed counter patterns including Redis INCR and shard aggregation | https://github.com/ashishps1/awesome-system-design-resources |

**Key concepts this needs to cover:** Hot-write problem (single counter row becomes lock bottleneck), sharded counter table (N rows per logical counter), write routing (hash to shard), read aggregation (SUM across shards → eventual consistency), Redis INCR as an alternative to DB sharding, approximate counting (HyperLogLog) vs exact counting, use cases (YouTube views, product ratings, likes, leaderboards).

---

### Backpressure (for `10-backpressure.md`)

| Resource | What it covers | Link |
|---|---|---|
| **Arpit Bhayani — Backpressure** ⭐ | Producer-consumer mismatch, bounded queues, explicit signalling, load shedding strategies — most technically rigorous free explanation | YouTube: "Arpit Bhayani backpressure" |
| **ByteByteGo — Circuit Breaker Pattern** | Visual walkthrough of circuit breaker states (closed/open/half-open) and how it implements backpressure at service boundaries | YouTube: search "ByteByteGo circuit breaker" |
| **ashishps1/awesome-system-design-resources** | Curated articles on circuit breaker + load shedding patterns | https://github.com/ashishps1/awesome-system-design-resources |

**Key concepts this needs to cover:** Producer-consumer throughput mismatch (root cause), bounded queue (fixed-size buffer as first defence), queue-full strategies (block/drop/error), explicit backpressure signal (HTTP 429, Kafka consumer lag), circuit breaker pattern (closed/open/half-open states, Resilience4j), load shedding (deliberate request dropping by priority), reactive streams backpressure (Project Reactor `request(N)` pull model), Kafka producer acks and consumer lag monitoring.

---

### Document & Blob Storage (for `14-document-blob-storage.md`)

| Resource | What it covers | Link |
|---|---|---|
| **ByteByteGo — "Object Storage vs Block Storage vs File Storage"** ⭐ | Three-way comparison, when to use each, S3 internals, CDN integration — best free visual | YouTube: search "ByteByteGo object storage block storage file storage" |
| **hellointerview.com — Storage Systems** ⭐ | Object storage architecture, metadata DB pattern, document versioning, pre-signed URLs — interview-aligned | https://www.hellointerview.com/learn/system-design/deep-dives/s3 |
| **ashishps1/awesome-system-design-resources** | Curated articles on storage system design — S3 architecture, blob storage trade-offs | https://github.com/ashishps1/awesome-system-design-resources |

**Key concepts this needs to cover:** Object storage vs block storage vs file storage (three-way decision table), how S3-style object storage works (bucket + key + opaque blob, HTTP PUT/GET, eventual consistency), metadata DB alongside blob (Postgres table: doc_id, s3_key, owner_id, version, content_type, size, status), document versioning strategies (immutable new key per version vs S3 native versioning with version pointer), pre-signed URLs (time-limited access without exposing credentials), compliance (GDPR data residency — region-locked buckets, encryption at rest + transit), soft delete pattern (mark deleted in metadata DB, retain blob for audit trail), DocuSign-specific context (signed document immutability, audit trail, legal hold).

---

### System Qualities — The 7 DocuSign Evaluation Dimensions (for `15-system-qualities.md`)

| Resource | What it covers | Link |
|---|---|---|
| **Google SRE Book — "Service Level Objectives"** ⭐ | The canonical SLI/SLO/SLA definitions, error budget concept, and why you set SLA below SLO | https://sre.google/sre-book/service-level-objectives/ |
| **ByteByteGo — "What is Observability"** ⭐ | Logs vs metrics vs traces (three pillars), distributed tracing, correlation IDs — best free visual | YouTube: search "ByteByteGo observability logs metrics traces" |
| **hellointerview.com — System Design Fundamentals** | Availability patterns (active-passive vs active-active), scalability bottlenecks, security layers — interview-aligned | https://www.hellointerview.com/learn/system-design/in-a-hurry/introduction |
| **OpenTelemetry.io** | The standard for distributed tracing — trace_id, span_id, context propagation across services | https://opentelemetry.io/docs/concepts/ |

**Key concepts this needs to cover (all 7 DocuSign dimensions):**
- **Testability:** constructor injection, interfaces as contracts, test doubles (mock/stub/fake), testable in isolation without Spring context
- **Usability:** HTTP verb semantics, standard error body format, consistent naming, self-documenting endpoints
- **Extensibility:** Open-Closed Principle, Strategy/Plugin pattern, API versioning for backward compatibility
- **Security:** defense in depth (authn → authz → encryption), JWT validation, AES-256 at rest, TLS 1.3 in transit
- **Availability:** SLI/SLO/SLA definitions, 99.9% = 8.7 hrs/year, active-active vs active-passive, circuit breaker for cascading failure
- **Scalability:** identify bottleneck first (read vs write path), read replicas + cache for read-heavy, sharding + async queue for write-heavy
- **Observability & Traceability:** three pillars (logs/metrics/traces), MDC (Mapped Diagnostic Context), trace_id correlation, Micrometer for metrics

---

## 🔹 What NOT to Use

| Resource | Why skip |
|---|---|
| Random Medium blogs | Quality varies wildly, often wrong trade-offs |
| GeeksforGeeks LLD pages | Surface-level, no depth on patterns |
| Grokking System Design (Educative) | Good but expensive; free alternatives cover the same content |
| InterviewBit LLD pages | Too generic, not enough depth for senior interviews |

---

---

## 🔹 Resources for Week 2-3 System Architecture Concepts (27-33, Added June 2026)

### Authentication & Authorization (for `27-auth-authz-fundamentals.md`)

| Resource | What it covers | Link |
|---|---|---|
| **OWASP — Authentication Cheat Sheet** ⭐ | Password hashing, session management, MFA, OAuth 2.0 flow | https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html |
| **Auth0 Blog — OAuth 2.0 Explained** ⭐ | Authorization code flow, refresh tokens, scopes — interview-friendly | Search: "Auth0 OAuth 2.0 explained" |
| **JWT.io — Introduction to JWT** ⭐ | JWT structure (header.payload.signature), RS256 asymmetric signing, token claims | https://jwt.io/introduction |
| **hellointerview.com — Security** | RBAC vs ABAC, permission tables, audit logs | https://www.hellointerview.com/learn/system-design/core-concepts/security |

**Key concepts:** Authentication (who are you?), Authorization (what can you do?), JWT tokens, OAuth 2.0 flow, RBAC (role-based), ABAC (attribute-based), password hashing (bcrypt, Argon2), session tokens vs JWTs.

---

### CDN — Content Delivery Network (for `28-cdn-edge-caching.md`)

| Resource | What it covers | Link |
|---|---|---|
| **ByteByteGo — "CDN Explained"** ⭐ | Edge locations, cache hits/misses, geographic distribution, TTL | YouTube: search "ByteByteGo CDN explained" |
| **Cloudflare Blog — How CDNs Work** ⭐ | Real-world CDN architecture, cache invalidation, DDoS protection | Search: "Cloudflare CDN how it works" |
| **hellointerview.com — CDN** | Cache strategies, origin fallback, cache header directives (max-age, s-maxage) | https://www.hellointerview.com/learn/system-design/core-concepts/cdn |

**Key concepts:** Edge caching, geographic distribution, cache hit rate, TTL (time-to-live), cache invalidation (TTL vs purge), origin server, DDoS mitigation, bandwidth savings.

---

### Database Replication (for `29-db-replication-failover.md`)

| Resource | What it covers | Link |
|---|---|---|
| **Arpit Bhayani — Database Replication** ⭐ | Master-slave, WAL (write-ahead log), sync vs async, RPO/RTO | YouTube: "Arpit Bhayani database replication" |
| **MongoDB Replication Guide** ⭐ | Replica sets, primary/secondary, heartbeat, failover election | https://docs.mongodb.com/manual/replication/ |
| **hellointerview.com — Databases** | Replication strategies, consistency guarantees, failover scenarios | https://www.hellointerview.com/learn/system-design/deep-dives/databases |

**Key concepts:** Master-slave replication, WAL (write-ahead log), sync vs async lag, RPO (recovery point objective), RTO (recovery time objective), failover, split-brain prevention, multi-region replication.

---

### Distributed Tracing (for `30-distributed-tracing-spans.md`)

| Resource | What it covers | Link |
|---|---|---|
| **OpenTelemetry — Tracing Spec** ⭐ | Trace ID, span ID, parent span, context propagation, baggage | https://opentelemetry.io/docs/concepts/signals/traces/ |
| **Jaeger Documentation** ⭐ | Trace collection, sampling strategies (const, probabilistic, adaptive), storage backends | https://www.jaegertracing.io/docs/ |
| **Arpit Bhayani — Distributed Tracing** | Real-world trace analysis, latency breakdown, root cause detection | YouTube: search "Arpit Bhayani distributed tracing" |

**Key concepts:** Trace ID (request correlation), span (operation), parent-child span relationships, sampling (1-10%), context propagation across services, latency waterfall visualization.

---

### CQRS — Command Query Responsibility Segregation (for `31-cqrs-read-write-separation.md`)

| Resource | What it covers | Link |
|---|---|---|
| **Martin Fowler — CQRS** ⭐ | Separation of read and write models, eventual consistency, event sourcing synergy | https://martinfowler.com/bliki/CQRS.html |
| **Axon Framework Guide** ⭐ | CQRS implementation, event handlers, read model projections | https://docs.axoniq.io/ |
| **hellointerview.com — CQRS** | When to use CQRS, read/write scaling tradeoffs, eventual consistency guarantees | Search for CQRS section |

**Key concepts:** Command (write) vs Query (read) separation, write DB vs read DB, event projections, eventual consistency, scaling reads independently from writes.

---

### Elasticsearch (for `32-elasticsearch-inverted-index.md`)

| Resource | What it covers | Link |
|---|---|---|
| **Elasticsearch Official Guide** ⭐ | Inverted index, shards/replicas, mapping, query DSL, aggregations | https://www.elastic.co/guide/en/elasticsearch/reference/current/ |
| **ByteByteGo — "Elasticsearch Explained"** ⭐ | Full-text search, inverted index mechanics, sharding strategy, replication | YouTube: search "ByteByteGo elasticsearch" |
| **ELK Stack Tutorial** ⭐ | Logstash → Elasticsearch → Kibana pipeline, log ingestion, visualization | Search: "ELK stack tutorial" |

**Key concepts:** Inverted index (word → document mapping), sharding (horizontal scale), replication (durability), full-text search, aggregations, index mapping, analyzer plugins.

---

### gRPC — Protocol Buffers & HTTP/2 (for `33-grpc-protocol-buffers.md`)

| Resource | What it covers | Link |
|---|---|---|
| **gRPC Official Guide** ⭐ | Protocol buffers, service definition, client/server code generation | https://grpc.io/docs/what-is-grpc/ |
| **ByteByteGo — "gRPC vs REST"** ⭐ | Binary serialization, HTTP/2 multiplexing, streaming, latency comparison | YouTube: search "ByteByteGo gRPC vs REST" |
| **Protobuf Documentation** ⭐ | Message definition syntax, code generation, backward compatibility, field numbering | https://developers.google.com/protocol-buffers |

**Key concepts:** Protocol buffers (schema-driven, binary, smaller payload), HTTP/2 (multiplexing, server push), streaming (client-stream, server-stream, bidirectional), code generation (Java, Python, Go), backward compatibility.

---

### API Gateway (for `24-api-gateway-pattern.md`)

| Resource | What it covers | Link |
|---|---|---|
| **ByteByteGo — "API Gateway Explained"** ⭐ | Request routing, authentication at edge, rate limiting, load balancing — visual walkthrough | YouTube: search "ByteByteGo API gateway" |
| **hellointerview.com — API Gateway** ⭐ | Gateway as single entry point, request translation, protocol conversion (REST ↔ gRPC) — interview-aligned | https://www.hellointerview.com/learn/system-design/core-concepts/api-gateway |
| **Netflix Engineering Blog — Edge Services** | Real-world API gateway patterns from Netflix (authentication, routing, circuit breaker at edge) | Search: "Netflix edge services" |

**Key concepts:** API Gateway sits BETWEEN clients and services (distinct from load balancer which sits BETWEEN LB and services), request routing by path/method, protocol translation (REST to gRPC backend), rate limiting at gateway, request/response transformation, authentication/authorization layer, SSL termination.

---

### Monitoring & Observability Fundamentals (for `25-monitoring-observability-fundamentals.md`)

| Resource | What it covers | Link |
|---|---|---|
| **Google SRE Book — "Monitoring Distributed Systems"** ⭐ | SLI/SLO/SLA, metrics vs alerts, burn rate, error budget | https://sre.google/sre-book/monitoring-distributed-systems/ |
| **ByteByteGo — "Observability: Logs, Metrics, Traces"** ⭐ | Three pillars with clear separation (what to measure, how to aggregate, how to correlate) — best visual | YouTube: search "ByteByteGo observability logs metrics traces" |
| **OpenTelemetry Documentation** | Standard for instrumentation, context propagation, collector patterns, exporter setup | https://opentelemetry.io/docs/concepts/ |
| **ELK Stack / Prometheus + Grafana** | Real-world stacks — Elasticsearch for logs, Prometheus for metrics, Grafana for visualization | Search: "Prometheus Grafana setup tutorial" |

**Key concepts:** Logs (what happened), Metrics (how much/often), Traces (how did request flow?), correlation IDs, MDC (Mapped Diagnostic Context), SLI/SLO definitions, RED method (Rate, Errors, Duration) vs USE method (Utilization, Saturation, Errors), distributed tracing with trace_id propagation, alert rules (firing + recovery).

---

### WebSocket — Real-Time Communication (for `26-websocket-real-time-communication.md`)

| Resource | What it covers | Link |
|---|---|---|
| **MDN Web Docs — WebSocket API** ⭐ | Protocol fundamentals, upgrade handshake, frame format, client-server lifecycle | https://developer.mozilla.org/en-US/docs/Web/API/WebSocket |
| **ByteByteGo — "WebSocket vs Polling vs Server-Sent Events"** ⭐ | Three-way comparison with trade-off table — best free visual | YouTube: search "ByteByteGo websocket polling server-sent events" |
| **High Scalability Blog — WebSocket Scaling** | Scaling WebSocket to millions of connections (sticky sessions, connection pooling, Kafka for broadcasts) | Search: "High Scalability WebSocket millions" |

**Key concepts:** WebSocket sits AT THE APPLICATION layer (distinct from HTTP), persistent connection upgrade via HTTP upgrade header, bidirectional messaging, heartbeat/ping-pong for connection health, message broadcasting (pub-sub over WebSocket), scaling challenge (sticky sessions + message broker like Kafka for fan-out), reconnection logic, graceful degradation (fallback to polling if WebSocket unavailable).

---

---

## 🔹 Resources for Gap-Closure Notes 50-51 (Added Jul 2026)

### Database Indexing (for `50-database-indexing.md`)

| Resource | What it covers | Link |
|---|---|---|
| **Arpit Bhayani — Database Internals** ⭐ | B-tree structure, why indexes speed up queries, composite index mechanics, EXPLAIN plans — most rigorous free content on DB index internals | YouTube: search "Arpit Bhayani database internals" or "Arpit Bhayani indexing" |
| **ByteByteGo — "How Database Indexes Work"** ⭐ | Visual walkthrough of B-tree leaf nodes, clustered vs non-clustered, covering index, EXPLAIN output — best free visual | YouTube: search "ByteByteGo database indexes" |
| **Use The Index, Luke (use-the-index-luke.com)** | The definitive free reference on SQL indexing — composite index column order, selectivity, covering indexes with worked examples across Postgres/MySQL/Oracle | https://use-the-index-luke.com |

**Key concepts this needs to cover:** B-tree structure (sorted leaf nodes, O(log n) lookups), composite index leftmost-prefix rule (index on (A,B,C) helps WHERE A=? and WHERE A=? AND B=? but NOT WHERE B=? alone), covering index (INCLUDE columns so no heap fetch needed), selectivity (high cardinality = good candidate for indexing), EXPLAIN ANALYZE (Seq Scan vs Index Scan vs Index Only Scan), clustered vs non-clustered (heap fetch cost), partial indexes (WHERE clause narrows index to active rows), when NOT to index (high write rate, low selectivity columns like boolean flags).

---

### Geospatial Indexing (for `51-geospatial-indexing.md`)

| Resource | What it covers | Link |
|---|---|---|
| **Uber Engineering — H3: Uber's Hexagonal Hierarchical Spatial Index** ⭐ | Primary source — why hexagons, resolution levels, real production usage for surge pricing and ETA computation | Search: "Uber Engineering H3 hexagonal spatial index" or eng.uber.com |
| **Redis GEO documentation** ⭐ | Exact API contract for GEOADD, GEODIST, GEOSEARCH (BYRADIUS vs BYBOX, ASC/DESC, COUNT, WITHCOORD, WITHCOORD options) | https://redis.io/docs/latest/commands/geosearch |
| **hellointerview.com — Proximity Service** | Full system design walkthrough of "find nearby drivers/restaurants" — combining Redis GEO with geohash DB, failure modes at scale, data freshness trade-offs | Search: "hellointerview proximity service system design" |

**Key concepts this needs to cover:** Geohash (shared prefix = nearby, precision levels 5-char ≈ 5km vs 7-char ≈ 153m, boundary problem + 9-cell fix using Moore neighbours), quad tree (recursive 4-quadrant split, adaptive depth for non-uniform density, in-memory structure), H3 (hexagonal grid, resolution 0-15, equidistant neighbours = k-ring(1) gives 7 cells not 9, why hexagons beat squares for circular proximity), Redis GEO commands (GEOADD O(log N), GEOSEARCH O(N+log M), member = ID only + separate DB for metadata), R-tree mention (PostGIS uses it for polygon containment, not the go-to for circular proximity queries).

---

### Pub/Sub Pattern (added to `19-message-queues-kafka-rabbitmq.md`)

The existing Kafka/RabbitMQ resources below cover pub/sub routing as part of the broader message broker story. No separate resources needed — the pub/sub section added to note 19 is self-contained. For deeper reading, the existing note 19 Further Reading resources (Arpit Bhayani Kafka Internals, ByteByteGo Kafka Explained) cover the fan-out semantics.

---

### Webhooks (for `53-webhooks.md`)

| Resource | What it covers | Link |
|---|---|---|
| **Stripe Webhooks — Best Practices** ⭐ | HMAC-SHA256 signing, idempotency key pattern, replay prevention with timestamp headers, retry with exponential backoff, event ordering caveats, endpoint registration — the industry-canonical webhook reference | https://stripe.com/docs/webhooks/best-practices |
| **DocuSign Connect Developer Guide** ⭐ | DocuSign's webhook system (Connect): envelope lifecycle events (Sent, Delivered, Signed, Declined), signature verification with HMAC, retry behavior, event deduplication — directly relevant to DocuSign interview context | https://developers.docusign.com/platform/webhooks |
| **GitHub Webhooks Documentation** | Comprehensive real-world webhook implementation: event catalog, delivery semantics, HMAC-SHA256 signing with `X-Hub-Signature-256`, redeliver API, ping event — useful for understanding the provider-side contract | https://docs.github.com/en/webhooks |

**Key concepts this needs to cover:** Webhook vs polling trade-off (push vs pull, event-driven vs polling interval), HMAC-SHA256 signature verification (provider signs body with shared secret → consumer recomputes and constant-time compares), idempotency key on receiver side (event_id as deduplication key — `ON CONFLICT DO NOTHING`), replay attack prevention (timestamp header + 5-minute window rejection), return 200 immediately then process async (never do slow work before ACK — causes provider timeout + retry), endpoint registration and secret rotation (per-subscriber secrets, rotation without downtime), exponential backoff on the provider retry side (5s → 25s → 125s), dead letter queue for permanently failed deliveries, fan-out webhooks (one internal event → deliver to N subscribers).

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 2026 | File created. Research from 5 web searches cross-referenced. |
| June 25, 2026 | Added resources for concepts 24 (API Gateway), 25 (Monitoring & Observability), 26 (WebSocket). Resources include foundational videos (ByteByteGo), interview-aligned guides (hellointerview.com), and specification docs (OpenTelemetry, MDN). |
| Jul 1, 2026 | Added resources for concepts 50 (Database Indexing) and 51 (Geospatial Indexing). Gap-closure notes — resources include Arpit Bhayani DB internals, Use The Index Luke reference, Uber Engineering H3 blog, Redis GEO docs, and hellointerview Proximity Service. |
| Jul 9, 2026 | Added resources for Pub/Sub Pattern (note: points to existing note 19 resources) and Webhooks (note 53). Webhook resources: Stripe Best Practices, DocuSign Connect Developer Guide, GitHub Webhooks docs. |
