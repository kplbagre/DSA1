# Docusign System Design — New Research (Jul 9, 2026)

> **Source:** Two deep research rounds (70+ web searches), Glassdoor, Blind/TeamBlind, LeetCode Discuss, InterviewQuery, Exponent, 1point3acres, DesignGurus, JoinTaro, Docusign Engineering Blog (4-part series published mid-2025), official Docusign prep guide PDF.
>
> **Purpose:** Captures intelligence gathered on Jul 9, 2026 — additive to `system-design-questions.md` and `r2-solutions/`. Read this first before reviewing existing solution files.

---

## 1. Official PDF — Key Confirmed Facts

From the actual Docusign preparation guide PDF received by Kapil:

**Round structure (full loop):** 3–5 technical discussions depending on level. Each 60 min.

**Three rounds:**
1. Coding & Problem Solving
2. System Design (HLD)
3. Hiring Manager

**Design round — exact wording from PDF:**
> *"Your 60-minute interview will be a deeply technical discussion on system or product architectural design, depending on the role."*

**Two flavors (both HLD, not LLD):**

| Flavor | PDF Evaluation Areas | Example Questions from PDF |
|---|---|---|
| System Design | Testability, Usability, Extensibility, Security, Availability, Scalability, Observability & Traceability | URL Shortener, Facebook Chat, Worldwide Video CDN |
| Product Architecture | Storage data models, SOLID principles, Scalability, Design patterns, Protocols, Data formats | Service/product API, Chat/Feed API, Email server |

**PDF's own preparation tips (direct quotes):**
- *"Start with requirements — ask: How many users? What features? What latency? How handle errors? Sync vs Async?"*
- *"Focus on trade-offs — avoid fixating on only the most optimal solution. We are more interested in seeing how you think through pros and cons."*

**Confirmed: It is HLD only.** LLD (class diagrams, design patterns as standalone round) only confirmed in India loop. Not in US Software Engineer loop.

---

## 2. New Questions Not in Existing Files

These were found in Jul 9 research and are NOT in `system-design-questions.md`:

| Question | Source | Tier |
|---|---|---|
| **Design a multi-level parking system** (backend tracking floors, rows, spaces) | Glassdoor/InterviewQuery Oct 2025 | 🔶 Likely |
| **Design a pub/sub architecture** (came up unexpectedly in what was billed as a DSA round — candidate failed by not pivoting to pub/sub) | Blind late 2025 | 🔶 Likely |
| **Design a document routing/workflow as a DAG** (cycle detection, conditional branching, parallel vs sequential routing) | Multiple aggregators | 🔶 Likely |
| **Design an email server** | PDF p.3 directly | ⭐ Confirmed (PDF) |

> **Warning from Blind 2025 report:** One candidate's DSA round shifted into system design unexpectedly. The interviewer expected a pub/sub-style architecture for what was described as a map/data problem. Candidate stuck with map-based solution, interviewer kept pushing scalability questions → no offer. **Be ready for system design thinking even in rounds labeled "coding."**

---

## 3. Docusign Engineering Blog — Real Internal Architecture

Docusign published a **4-part series** on their actual architecture (mid-2025). This is what they value and what may appear as design prompts.

**Key architectural facts from their own blog:**

### Three Decoupled Layers
```
Application Layer → Storage Layer → AI Platform Layer
(strictly decoupled — each can scale independently)
```

### CPU/GPU Separation
- **CPU microservices:** Rasterization + OCR (pre-processing)
- **GPU:** Only activated after CPU artifacts are cached in ephemeral blob storage with TTL
- CPU and GPU never share compute pools

### Queue Isolation by SLA Class
This is a key pattern they explicitly use and may test:
```
Live Queue      → P95 < 15 minutes  (user-facing, high priority)
Bulk Queue      → Flexible SLA      (batch jobs, imports)
Workflow Queue  → Per-workflow SLA  (orchestrated pipelines)
```
**Why it matters:** Head-of-line blocking — without queue isolation, a large bulk job starves live user requests.

### Model Routing (AI Platform)
- Fine-tuned small models → high-volume structured field extraction (dates, parties, governing law)
- Frontier LLMs → complex clause analysis and summarization
- Semantic compression before inference: 100,000+ tokens → few thousand (accuracy + cost win)

### Scale Numbers
- **99.9%+ platform availability** over 24 months
- **3 geographically disparate active-active sites** — entire sites can go offline for maintenance without impacting live transactions
- **50+ data points extracted per agreement**
- **~50x per-document cost reduction** after AI pipeline optimization
- Millions of documents/day

> **Interview application:** If asked "Design DocuSign's agreement processing system" — mention: 3-layer decoupled architecture, SLA-based queue isolation, CPU/GPU separation, at-least-once delivery with idempotency keys.

---

## 4. Detailed Interviewer Drill-Down Questions (Per Topic)

These are the follow-up questions asked INSIDE a design round — from actual candidate reports.

### Rate Limiter (most confirmed non-obvious question)
1. "What is the client identifier?" → IP rejected → push to JWT + KYC
2. "How do you identify a client in a multi-tenant enterprise SaaS context?"
3. "What data structure stores the request counts?" → deque of timestamps per client ID + threshold
4. "How do you handle JWT expiry — what happens to in-flight requests?"
5. "What happens when a distributed cache node holding rate limit state goes down?"

### Facebook Messenger / Chat
1. Requirements: how many users, features, latency, error states, sync vs async?
2. "How do you handle message ordering across distributed servers?"
3. "What's your consistency model for message delivery?"
4. "How do you scale the presence/online-status service?"

### Event Ingestion / Webhook System
1. "How do you handle exactly-once vs at-least-once delivery?" → **Expected: at-least-once + idempotency keys. Saying "exactly-once" without caveats = red flag**
2. "A consumer endpoint returns 200 OK but silently drops payloads — how do you debug this?"
3. "How do you partition Kafka topics for ordered delivery of sequential envelope events?"
4. "What happens when a downstream consumer is rate-limited?"
5. "How do you handle a Dead Letter Queue?"

### Agreement Lifecycle / Schema Design
1. "How do you handle retries and reversals without losing history?" → **Expected: append-only, never overwrite state**
2. "What's the grain of your event table?"
3. "How do you ensure point-in-time accuracy for compliance queries?"
4. "Can you prove in court what happened, when, and who authorized it?"

### Embedded Signing API
1. "What happens if the user closes the browser before the returnUrl fires?"
2. "How do you prevent URL spoofing of the returnUrl event parameter?"
3. **Expected pattern — "Fast Path + Safe Path":**
   - `returnUrl` = fast UI confirmation (don't trust alone)
   - `Envelopes:get` REST call = safe backend confirmation
   - Connect webhook = async audit record
   - Candidates who stop at returnUrl fail

---

## 5. CAP Theorem — What Docusign Expects You to Say

This is explicitly evaluated. Confirmed from multiple candidate reports and Docusign domain analysis.

### The Right Position

| System Component | CAP Choice | Why |
|---|---|---|
| Signed document records | **CP** (consistency over availability) | Legal binding — correctness required even during partition |
| Envelope state transitions | **CP** | Non-repudiation requires correctness |
| Audit trail | **CP** | Court-admissible records — no eventual consistency |
| Notification delivery | **AP** (availability over consistency) | Staleness not legally material |
| Activity feeds | **AP** | User experience degrades gracefully |
| Webhook delivery confirmations | **AP** | Retries handle eventual delivery |

### Key Phrase That Impresses Interviewers
> *"Partition tolerance is not optional in distributed systems — the network chooses for you at failure time. What you choose is CP vs AP."*

### ACID vs BASE
- **ACID:** All write paths involving legal commitments (envelope creation, signature capture, void/decline)
- **BASE:** Read replicas, analytics, webhook delivery confirmations

---

## 6. SQL vs NoSQL — What Docusign Expects

| Data Type | Technology | Reason |
|---|---|---|
| Core envelope/signer/audit tables | **RDBMS** (PostgreSQL/MySQL) | ACID, joins, foreign keys, point-in-time accuracy |
| High-volume event streams | **Kafka** or time-series store | Throughput, ordering guarantees, replay |
| Binary PDF documents | **Object storage** (S3-equivalent) | Blob storage, not a DB question |
| Hot metadata / session state | **Redis** | O(1) cache, TTL support |
| Agreement search / indexing | **Elasticsearch** | Full-text search on document content |

---

## 7. "Design DocuSign Itself" — Core Schema

If asked to design the DocuSign platform, this is the minimum schema they expect:

```sql
envelopes:
  envelope_id    UUID PRIMARY KEY
  status         ENUM('draft','sent','delivered','completed','voided','declined')
  email_subject  VARCHAR
  created_at     TIMESTAMP
  sender_id      UUID FK → users

documents:
  document_id    UUID PRIMARY KEY
  envelope_id    UUID FK → envelopes
  file_reference VARCHAR  -- S3 object key, NOT the binary
  name           VARCHAR
  file_extension VARCHAR

recipients:
  recipient_id   UUID PRIMARY KEY
  envelope_id    UUID FK → envelopes
  email          VARCHAR
  name           VARCHAR
  routing_order  INT       -- sequential routing: 1, 2, 3...
  status         ENUM('pending','sent','delivered','completed','declined')

signing_tabs:
  tab_id         UUID PRIMARY KEY
  recipient_id   UUID FK → recipients
  document_id    UUID FK → documents
  page_number    INT
  x_position     FLOAT
  y_position     FLOAT

audit_logs:  ← IMMUTABLE, append-only, never UPDATE or DELETE
  log_id         UUID PRIMARY KEY
  event_type     VARCHAR   -- 'envelope_created','signed','viewed','voided'
  envelope_id    UUID
  actor_id       UUID
  timestamp      TIMESTAMP
  metadata       JSONB
```

**Key architecture on top of schema:**
1. Binary PDFs → S3 (file_reference is the pointer)
2. Signing workflow → DAG-based state machine (routing_order drives sequential; null = parallel)
3. Webhook/Connect → Kafka partitioned by envelope_id (preserves ordering per envelope)
4. Audit trail → append-only, cryptographically sealed, 256-bit encryption at rest

---

## 8. Winning Answer Structure (From Candidates Who Got Offers)

### Opening Statement (Say This Out Loud at the Start)
> *"I'll cover functional requirements, non-functional requirements, capacity estimation, DB schema, API design, then HLD. Redirect me if you want me to focus somewhere specific."*

### Time Budget for 60-Minute Round
```
0–10 min   → Requirements (functional + non-functional)
10–15 min  → Capacity estimation (QPS, storage, read/write ratio)
15–30 min  → High-level architecture (components, data flow diagram)
30–45 min  → Deep dive (interviewer picks — go wherever they steer)
45–55 min  → Trade-off discussion ("Why X over Y")
55–60 min  → Your questions for them
```

### Trade-Off Framing That Works at Docusign
- CAP: "For signed records — CP. For notifications — AP is fine since staleness isn't legally material."
- DB: "Core tables stay RDBMS for ACID. Event streams go to Kafka. Blobs to S3. No single DB serves all."
- Delivery: "Exactly-once is a myth — at-least-once with idempotency keys is the correct architecture."
- Scalability: "Profile and measure first. Then: pagination, caching hot paths, background jobs for heavy processing."

---

## 9. Confirmed Failure Patterns (New from 2025 Reports)

| What Killed the Candidate | Why |
|---|---|
| Not pivoting to pub/sub when interviewer hinted | Interviewer expected event-driven; candidate stuck with map-based solution |
| Saying "exactly-once delivery" without caveat | Shows lack of distributed systems understanding |
| IP-only rate limiter | Not sufficient — interviewer pushes to JWT + KYC |
| Mutable state schema | Challenged on auditability and compliance |
| Only designing happy path | Explicit rejection feedback in multiple reports |
| Jumping to architecture before requirements | Interviewer redirects you — counts against you |
| Generic FAANG answer without compliance awareness | Passes coding, fails design at senior level |
| Weak basic coding visible at VP level (P4) | VP can veto entire loop |

---

## 10. Docusign's Non-Negotiables in ANY Design

Mention these regardless of the question asked — they appear in every evaluation:

1. **Append-only audit log** — every system that touches data must produce one
2. **Webhooks over polling** — Docusign's product is webhook-first; candidates who default to polling get probed
3. **Multi-tenancy** — enterprise SaaS; data isolation between orgs is non-negotiable
4. **Idempotency keys** — enterprise clients retry; all write operations must be idempotent
5. **Compliance framing** — ESIGN, UETA, eIDAS — at minimum acknowledge these exist
6. **At-least-once + idempotency > exactly-once** — state this explicitly in any event/webhook design

---

## 11. LLD Patterns They Test (India Loop / Product Architecture Flavor)

If your round turns out to be Product Architecture flavor:

| Pattern | Docusign Context | When to Use |
|---|---|---|
| **Strategy** | Signer role types (signer/approver/editor/carbon copy) — each has different behavior | When you see if/else chains based on type |
| **Observer** | Webhook/Connect event model — envelope events fan out to subscribers | Event notification systems |
| **Factory** | Envelope component creation (documents, tabs, recipients) | Object creation with type-based logic |
| **Builder** | API client / complex config objects | Many optional parameters in constructor |
| **State Machine** | Envelope lifecycle: `Draft → Sent → Delivered → Completed / Voided / Declined` | Anything with state transitions |

**Envelope state machine (draw this if asked):**
```
Draft ──────────────────────────────────────────→ Voided
  │                                                   ↑
  └─→ Sent ──→ Delivered ──→ Completed               │
                    │                                  │
                    └──→ Declined ─────────────────────┘
```

---

## 12. Recent Dated Reports (2025–2026)

| Date | Location | What Happened |
|---|---|---|
| Oct 2025 | San Francisco | 7 rounds, varied formats, "some peculiar questions" — Principal level |
| Mar 2025 | Seattle | Recruiter → LC mediums → system design → HM. "System design seemed relevant to the business, with plenty of back-and-forth" |
| Late 2025 | US (remote) | DSA round shifted to system design unexpectedly. Interviewer expected pub/sub. Candidate disagreed with map-based solution. No offer |
| Oct 2024 | US | Slow scheduling, interviewers changed frequently, sometimes day-of |
| Jan 2025 | US | Two technical rounds back-to-back same day |

---

## 13. Cross-Reference to Existing Files

| Topic | Existing Coverage |
|---|---|
| URL Shortener | `r2-solutions/A1-url-shortener.md` — full 60-min framework |
| Facebook Chat | `r2-solutions/A2-chat-messenger.md` |
| Video CDN | `r2-solutions/A3-video-distribution.md` |
| Rate Limiter | `r2-solutions/C1-rate-limiter.md` — update with JWT/KYC drill-down from Section 4 above |
| Expense Report | `r2-solutions/C2-expense-report.md` |
| Pagination API | `r2-solutions/C3-pagination-api.md` |
| Digital Signature | `r2-solutions/D1-digital-signature.md` |
| Document Storage | `r2-solutions/D2-document-storage.md` |
| Notification Service | `r2-solutions/D3-notification-service.md` |

> **Gap identified:** No solution file exists yet for:
> - Email server (PDF-confirmed question)
> - Pub/sub / event pipeline (Docusign engineering blog confirms they use this)
> - Document routing DAG / workflow orchestration

---

> 🔄 **Changelog**
>
> | Date | Change |
> |---|---|
> | Jul 9, 2026 | Created — New research from 70+ web searches + official Docusign PDF analysis. Additive to existing system-design-questions.md. |
