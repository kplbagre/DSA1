# D1 — Design a Digital Signature System

> **Read `solution-notes-standards.md` FIRST.** This file is a 60-minute interview answer framework.
> Its structure mirrors DELIVERY-RECIPE: clarify (5 min) → estimate (5 min) → HLD (15 min) → deep dives (15 min) → trade-offs (8 min) → Q&A (7 min).
> **Say this out loud** before your interview — don't just read it. Practice until the memory anchors feel natural.

---

## 🎯 What Is This System?

**In plain English:** A digital signature system lets one or more parties sign a legal document electronically using public-key cryptography. The system proves that a specific person signed a specific document at a specific time — and that the document has not been altered since — creating a tamper-evident, legally admissible audit trail.

**Real-world examples:**

| System / Company | What they built |
|---|---|
| **DocuSign** | The company you're interviewing at — the world's #1 e-signature platform, 1B+ envelopes signed |
| **Adobe Acrobat Sign** | E-signatures integrated into the Adobe document workflow |
| **HelloSign (Dropbox Sign)** | Developer-friendly e-signature API and UX |
| **PandaDoc** | Document automation + signing for sales contracts |
| **eSignLive (OneSpan)** | High-assurance signing for banks and regulated industries |
| **DocHub** | Lightweight PDF signing and form filling |

**Core user journey:** Sender uploads a contract PDF, assigns signature fields to 3 recipients in a specific order → each recipient receives an email link → opens the document → signs using their private key → after all parties sign, the sealed document and an audit certificate are available to all parties permanently.

**Why it's hard to build at scale:** Non-repudiation is a legal requirement — a signer must never be able to claim they didn't sign; the audit trail must be cryptographically tamper-evident, not just a DB record; multi-party signing order is a distributed workflow where any party can decline or time out; and the sealed document must be legally equivalent to a wet signature in 60+ countries.

---

## Section 0 — Question Identity Card

| | |
|---|---|
| **Question** | **Design a Digital Signature System** (scale to millions of users) |
| **Interview Type** | **Mixed A+B** — System Design (PKI infrastructure) + Product Architecture (API/data model) |
| **Confirmed or Likely** | 🔶 Likely (DesignGurus DocuSign guide, multiple Blind 2024-25, DocuSign's core product) |
| **Concept notes prerequisite** | `13-security-pki.md` (PKI, asymmetric crypto, certificates, non-repudiation) |
| **DocuSign-specific angle** | **This IS DocuSign's core product.** Your answer must cover: PKI/cert management, audit trail (non-repudiation), multi-party signing order, webhook on completion, B2B SaaS multi-tenant considerations, legal compliance (SOC 2, GDPR). The interviewer will probe deeply on how certificates are managed at scale and how you guarantee audit integrity. |
| **Time budget** | 5 min clarify → 5 min estimate → 15 min HLD → 15 min deep dive → 8 min trade-offs → 7 min Q&A buffer |

---

## Section 1 — 🚀 The One-Sentence Opener

> "Before I design, let me ask a few clarifying questions to make sure I understand the scope — especially around multi-party signing, audit requirements, and key management at scale..."

Then pivot immediately to Section 2 (clarifying questions). Do NOT start drawing until you've clarified scope.

---

## Section 2 — 🔍 Clarifying Questions Script (Minutes 0–5)

**Q: "How many signers can a single document have, and what's the signing order — sequential (A signs, then B, then C) or parallel (all can sign simultaneously)?"**
- Why ask: This determines whether you need a state machine for signing order or a simple "signed by" set.
- If sequential → workflow engine with role-based approval (expensive but common in enterprises)
- If parallel → simpler; just track who has signed and who hasn't

**Q: "Who generates and manages the signing keys — the user (BYOK — Bring Your Own Key: the user generates their own RSA key pair offline and uploads only the public key; DocuSign never sees the private key), or DocuSign (we generate, hold, and sign on behalf of the user)?"**
- Why ask: Key management at scale is massive. Self-signed certs = each user's RSA key pair. Hosted = DocuSign manages a CA.
- If BYOK → complexity jumps; you must validate certificate chains at signing time
- If hosted → you control the lifecycle, easier, but higher security responsibility

**Q: "What's the audit trail requirement — do we need immutable proof that John signed on June 24, 2026 at 3:14 PM in California, for legal compliance?"**
- Why ask: This drives non-repudiation design. Non-repudiation means "John can't later claim he didn't sign."
- If yes (legal requirement) → append-only audit log with timestamps, IP, user agent, certificate serial number, signature value — all immutable
- If no (just "who signed") → simple signed_by list is enough

**Q: "Do we need to notify external systems (webhooks) when a document is fully signed, and is order important?"**
- Why ask: Webhook fanout vs direct HTTP response. Order (exactly-once delivery) vs best-effort.
- If yes + order matters → needs idempotent webhook delivery with retries
- If yes + order doesn't matter → can use fan-out queue

**Q: "What about audit trail durability — if a user claims they were hacked and didn't sign, can we prove they did from the audit log?"**
- Why ask: Legal admissibility. Ties to timestamp accuracy, certificate validity, and whether we store the actual signature bytes.
- If yes → store signature bytes, cert serial, timestamp, IP, user agent; use tamper-evident storage (HMAC, signed records)
- If no → lighter schema

**Q: "Scale: how many documents/day and how many concurrent signers?"**
- Why ask: Drives whether you need sharding, replication, or a single-region setup.

---

## Section 3 — 📋 Requirements (Functional + Non-Functional)

**Functional Requirements:**
- Users should be able to **create a document** and specify who must sign it
- Users should be able to **sign a document** with a digital signature (cryptographically binding their identity to the doc)
- Users should be able to **view the signature status** (who has signed, who hasn't, full audit trail with timestamps)
- System should **support multi-party signing** (sequential or parallel, configurable)
- System should **send webhooks** when a document is fully signed
- System should **store audit trail** showing exact proof of who signed what at what time (non-repudiation)
- System should **verify signatures** are valid (using PKI certificate chain)
- Out of scope: PDF rendering, document OCR, embedded signing in the document itself (assume signing happens via API)

**Non-Functional Requirements:**
- Scale: 1M users, 50K documents/day (~0.58 docs/sec), 1M signature events/day (~11.6 sig/sec), peak 3× = 35 sig/sec
- Latency: P99 signing operation < 500ms (users waiting for response); P99 audit query < 100ms
- Availability: 99.9% SLO (9 hours downtime/year)
- Consistency: **Strong consistency required** — once a document is signed, that fact cannot be contradicted; signatures are append-only (no deletes or edits)
- Durability: Audit trail is immutable forever (legal requirement for 7+ years)
- Multi-tenant: DocuSign serves enterprise customers; strict data isolation required (customer A can't see customer B's docs)
- Legal compliance: SOC 2, GDPR, e-signature compliance (e.g., ESIGN Act — the US Electronic Signatures in Global and National Commerce Act; the federal law that gives electronic signatures the same legal standing as handwritten "wet ink" signatures in commercial transactions)

---

## Section 3.5 — 🗂️ Core Entities (~2 minutes)

> **Say this out loud:** "Before I sketch the architecture, let me name the key data objects the system manages."

| Entity | What it represents | Storage |
|---|---|---|
| **Document** | The file requiring signatures — metadata (title, owner, tenant), status, S3 reference | PostgreSQL |
| **SigningSession** | One complete signing workflow for a document — tracks all signers, order, overall status | PostgreSQL |
| **Signer** | A participant in a signing session — their role, signing status, timestamp when they signed | PostgreSQL |
| **UserCertificate** | User's RSA public key + certificate chain — used to verify their signature is genuine | PostgreSQL |
| **AuditEvent** | Cryptographically tamper-proof record of every action — signed, viewed, rejected, webhook sent | PostgreSQL (append-only) |

**Key relationships:**
- A `Document` has one active `SigningSession` (one-to-one; could have future re-sign sessions)
- A `SigningSession` has many `Signers` — sequential or parallel depending on config (one-to-many)
- Each `Signer` action creates an `AuditEvent` — this is the legal proof of non-repudiation
- `UserCertificate` is looked up during signature verification to confirm the key belonged to that user at signing time

---

## Section 8 — 🌐 API Design (Minutes 8–13) ⭐ Mixed A+B — State Contract Early

> **Why here:** D1 is Mixed A+B. The API contract makes the signing workflow concrete before you explain the PKI internals. Without the contract, "sequential signing with audit" is abstract; with it, the interviewer can follow each step.

### 🧠 How to Derive These Endpoints

Signing is a workflow with named state transitions. The FR "documents must be signed by one or more parties in a specified order" gives you the state machine: DRAFT → PENDING_SIGNATURE → FULLY_SIGNED (or REJECTED). Each state transition is an action.

"Create a document for signing" → CREATE → `POST /v1/documents`. The body must capture signing order at creation time — `signing_order: "sequential" | "parallel"` and `signer_user_ids: [...]`. Why at creation? Because once the workflow starts, reordering signers invalidates any signatures already applied.

"A designated signer submits their cryptographic signature" → state transition → `POST /v1/documents/{id}/sign`. Not `PATCH /v1/documents/{id}` with `{action: "sign"}` — action sub-resources are cleaner for permission checks. The body carries `signature_bytes` (base64-encoded RSA/ECDSA signature) and `certificate_serial` so the server can look up the user's public key and verify. Idempotency-Key header is mandatory: if the client's POST succeeds but the response is lost (network timeout), a retry must not double-sign.

"A signer can reject the document" → state transition → `POST /v1/documents/{id}/reject`. Parallel to `/sign`. The `reason` field feeds into the audit log and is surfaced to the document creator. In sequential order, rejection resets the workflow to the previous signer — the response includes `reset_to_signer` so the creator knows who needs to re-sign.

"Check signing status" → READ → `GET /v1/documents/{id}`. Returns the full signer list with individual statuses — who signed, who is pending, who rejected.

"Audit trail for legal proof" → `GET /v1/documents/{id}/audit`. Every action (view, sign, reject, webhook sent) is an immutable append-only event. The audit endpoint is the legal chain of custody.

Validation check: each FR maps to an endpoint. The "webhook notification on signing completion" FR has no endpoint — webhooks are outbound from the system, not inbound REST calls. Correct to have no endpoint here.

### Core Endpoints

| Method | Path | Auth | Request | Response | Status Codes |
|---|---|---|---|---|---|
| POST | `/v1/documents` | JWT Bearer | `{title, signer_user_ids, signing_order}` | `{document_id, status, created_at}` | 201, 400, 409 |
| POST | `/v1/documents/{id}/sign` | JWT Bearer | `{signature_bytes (base64), certificate_serial}` | `{document_id, status, next_signer, signed_at}` | 200, 400, 403, 409 |
| POST | `/v1/documents/{id}/reject` | JWT Bearer | `{reason}` | `{document_id, status, reset_to_signer}` | 200, 400, 403 |
| GET | `/v1/documents/{id}` | JWT Bearer | — | `{document_id, status, signers: [{user_id, order, status, signed_at}]}` | 200, 403, 404 |
| GET | `/v1/documents/{id}/audit` | JWT Bearer | — | `{audit_events: [{action, timestamp, certificate_serial, signature_hash, client_ip}]}` | 200, 403, 404 |

### 🔍 Endpoint Stories

**`POST /v1/documents`** is where the signing workflow is configured, not started. The document is created in `DRAFT` status. Including `signer_user_ids` and `signing_order` at creation time (not in a separate "start workflow" call) keeps the API simple: one POST creates and configures. The `409 Conflict` case: the document already exists (if caller retries with the same idempotency key). `400 Bad Request` if `signing_order = "sequential"` but `signer_user_ids` is empty.

**`POST /v1/documents/{id}/sign`** carries the cryptographic payload. `signature_bytes` is the RSA/ECDSA signature of the document hash, base64-encoded. The server verifies it using the public key from `UserCertificate` matching `certificate_serial`. Two interesting status codes: `403 Forbidden` means it's not your turn (sequential order, the previous signer hasn't signed yet) — you're an authorized user but not authorized for this action right now. `409 Conflict` means you already signed — the idempotency check. The `Idempotency-Key` header is mandatory on this endpoint: if the client POSTs, the server signs the document, but the response is lost in transit, the retry must return the same `{document_id, status}` without signing twice. Implementation: cache response in Redis for 24 hours keyed by `Idempotency-Key`.

**`POST /v1/documents/{id}/reject`** is the failure path. In sequential signing, if signer #2 rejects, the workflow can reset to signer #1 for revision — the `reset_to_signer` field in the response tells the creator who to contact. The probe: "What if rejection happens in parallel signing when two signers have already signed?" This is a business rule question, not a technical one — document the assumption ("rejection invalidates all previous signatures and resets to DRAFT") and move on.

**`GET /v1/documents/{id}/audit`** carries `signature_hash` in each audit event — the hash of the document bytes at the moment of signing. If the document is later modified (even by the system), the hash won't match the stored hash. This is the non-repudiation proof: a signer cannot later claim "that's not the document I signed" because the hash of what they signed is in the immutable audit log. Add cursor-based pagination: large documents with many signers over 7 years can accumulate thousands of audit events.

---

## Section 4 — 🔢 Scale Estimation (Minutes 5–10)

**Traffic:**
- DAU: 1M users (global, includes enterprise teams + individuals)
- Documents/day: 50K documents = 0.58 docs/sec baseline
- Signature events/day: 1M events (avg 20 signers per doc) = 11.6 sig/sec baseline
- Peak (3×): 35 sig/sec
- Read (audit queries): 5:1 ratio = 175 reads/sec peak

**Storage:**
- Per document: metadata ~1 KB, per signature ~500 bytes
- Signatures: 1M events/day × 500 bytes = 500 MB/day = 182 GB/year
- Audit log (metadata + events): 1 GB/day = 365 GB/year
- Total year 1: ~550 GB; at 10 years = 5.5 TB (fits comfortably on a single large Postgres instance, but needs sharding at year 5)

**Bandwidth:**
- Inbound (sign request): 35 sig/sec × 5 KB = 175 KB/sec
- Outbound (audit read): 175 reads/sec × 2 KB = 350 KB/sec
- Webhooks (outbound): 11.6 events/sec × 1 KB = 11 KB/sec

**Key conclusions:**
- At 35 sig/sec, a **single Postgres instance handles easily** (typical write capacity ~1K/sec), but **replication for HA is mandatory** (signatures can't be lost)
- At 5.5 TB/year, **sharding by customer_id will be needed** within 18 months to avoid slow queries on the audit log
- At 11.6 audit queries/sec, **caching the "who signed" status** (Redis) helps, but the immutable audit log must hit disk for legal compliance
- **Strong consistency required** → no eventual consistency for signing state, but webhooks can be async

---

## Section 5 — 🔄 Requirements Variation Table ⭐ Key Differentiator

| Requirement | Small scale (1K users) | Large scale (10M users) | Impact on design |
|---|---|---|---|
| **Documents/day** | 500 | 500K | Single region → multi-region sharding; single Postgres → Postgres + read replicas + customer sharding |
| **Signing order** | Parallel (simple) | Sequential (complex) | Simple set of signers → state machine workflow; approval rejection → rollback audit |
| **Key management** | DocuSign-hosted keys | BYOK (Bring Your Own Key) | Simple RSA key store → CA infrastructure; certificate validation; PKI chain verification at sign time |
| **Audit trail durability** | 1 year retention | Legal: 7+ years | In-memory + periodic backup → replicated, sharded, archived to cold storage (S3 Glacier) |
| **Multi-tenant isolation** | Single database | Separate DB per customer or row-level security | Simple queries → RBAC filters on every query + encryption at rest per tenant |
| **Compliance scope** | Internal only | ESIGN Act + GDPR + SOC 2 | Local timezone → UTC + per-region audit logs; local deletion → immutable forever |

---

## Section 6 — 🏗️ High-Level Architecture (Minutes 10–25)

### Stage 1 — Monolith + Direct DB (Baseline)

> Start here. Works at low volume. Two breaking points: (1) synchronous webhook couples external system availability to signing success; (2) every certificate lookup hits Postgres at 35 sig/sec — no caching, no isolation.

```
── Stage 1: Monolith ─────────────────────────────────────────────────

 ┌────────────┐  POST /v1/documents/{id}/sign
 │   Client   │──────────────────────────────────────────────────────▶
 └────────────┘
       ▲  200 OK         ┌────────────────────────────────────────────┐
       └─────────────────│              API Server                    │
                         │  1. JWT auth + tenant isolation check      │
                         │  2. SELECT cert FROM user_certificates     │  ← full DB hit
                         │  3. Verify RSA signature (~50-100ms)       │
                         │  4. UPDATE signing_sessions SET signed     │
                         │  5. INSERT INTO audit_signature_events     │
                         │  6. POST webhook to customer URL (sync)    │  ← blocking
                         └──────────────────┬─────────────────────────┘
                                            │
                         ┌──────────────────▼─────────────────────────┐
                         │                PostgreSQL                   │
                         │  documents           (status, metadata)     │
                         │  signing_sessions    (who signed, order)    │
                         │  audit_signature_events (append-only log)   │
                         │  user_certificates   (certs + revocation)   │
                         └────────────────────────────────────────────┘

BREAKING POINT 1:
   Step 6 — synchronous webhook: if the customer's server is slow or down,
   the signing HTTP response is blocked or times out. A webhook failure
   fails the signing — external system availability controls signing availability.

BREAKING POINT 2:
   Step 2 — cert lookup on every sign request hits the DB directly.
   At 35 sig/sec, 35 concurrent reads against the same cert table.
   No caching. Every request pays 10-20ms DB round-trip for cert data
   that almost never changes.
```

**WHICH key management model?**

| Option | Strength | Weakness | Verdict |
|---|---|---|---|
| DocuSign-hosted CA + HSM | Seamless UX — one-click signing; DocuSign manages cert lifecycle; enterprise adoption | DocuSign holds private keys — auditors may question non-repudiation; HSM compromise exposes all keys | ✅ Best for MVP — enterprise UX wins; non-repudiation via audit trail + 2FA context |
| BYOK (user generates + holds private key) | User owns their signing identity; cleaner cryptographic non-repudiation | Users lose keys → can't sign; key rotation is a UX nightmare; support ticket flood | ❌ Operational nightmare at 1M users |
| Hybrid (DocuSign CA + user holds private key in device TPM) | User owns key; DocuSign manages cert lifecycle | Device-dependent; tricky on mobile; requires TPM/SE support | ⚠️ Right direction for future; complex for MVP |

> 📖 Full: `SystemDesignConcepts/Production-Grade/System-Design-Patterns/13-security-pki.md`

---

### Stage 2 — Service Split + Redis + Async Webhook (Production)

> **Why we evolve:** Stage 1 has two breaking points — sync webhook couples signing to external systems, and uncached cert lookups hit the DB on every sign. Fix: (1) decouple webhook delivery via Kafka; (2) cache certificates in Redis. This also separates services for independent scaling and compliance isolation.

```
── Stage 2: Production ───────────────────────────────────────────────

 ┌────────────┐  POST /v1/documents/{id}/sign
 │   Client   │──────────────────────────────▶
 └────────────┘
       ▲  200 OK  ┌────────────────────────────────────────────────┐
       └──────────│                 API Gateway                   │
                  │  JWT auth + rate limit + tenant isolation     │
                  └─────────────────┬──────────────────────────────┘
                                    │
                  ┌─────────────────▼──────────────────────────────┐
                  │            Signature Service                   │
                  │  1. GET cert:{serial} from Redis (sub-ms)     │
                  │     cache miss → SELECT from Postgres → cache │
                  │     Redis CRL set SISMEMBER check (< 1ms)     │
                  │  2. Verify RSA signature SHA256withRSA (~50ms)│
                  │  3. UPDATE signing_sessions SET signed         │
                  │  4. INSERT audit_signature_events (immutable) │
                  │  5. Publish "doc.signed" to Kafka             │  ← returns immediately
                  └──────┬─────────────────────────┬──────────────┘
                         │ writes                   │ Kafka event
          ┌──────────────▼────────────────┐  ┌─────▼─────────────────────────┐
          │          PostgreSQL           │  │            Kafka               │
          │  documents   (status)         │  │  doc.signed                   │
          │  signing_sessions (workflow)  │  │      → Webhook Service         │
          │  audit_events (append-only)  │  │        (retry, idempotent)     │
          │  user_certificates (certs)   │  └───────────────────────────────┘
          └──────────────────────────────┘
                  │ cert cache
          ┌───────▼──────────────────────────────────────────────┐
          │                     Redis                            │
          │  cert:{serial}     → cert PEM       (1hr TTL)       │
          │  revoked_serials   → Set of CRL     (60s refresh)   │
          │  status:{doc_id}   → signing status (5min TTL)      │
          └──────────────────────────────────────────────────────┘
          ┌───────────────────────────────────────────────────────┐
          │             PKI Infrastructure                        │
          │  CA Root Cert     (offline, HSM — air-gapped)        │
          │  Intermediate CA  (online — signs user certs)        │
          │  CRL published every 60s → Redis Set                 │
          └───────────────────────────────────────────────────────┘

KEY INVARIANT:
   Audit table is append-only + immutable — DB trigger prevents UPDATE/DELETE.
   Non-repudiation: every signature event records timestamp (UTC), cert serial,
   signature hash, client IP — legally defensible, tamper-proof.
   Redis cert cache keeps certificate lookup sub-millisecond on the critical path.
   Kafka decouples webhook delivery — external system down never fails a signing.
   Signing uses STRONG CONSISTENCY — signing_sessions update is immediate,
   not eventually consistent — legal state cannot be ambiguous.
```

**WHICH audit trail immutability approach?**

| Option | Strength | Weakness | Verdict |
|---|---|---|---|
| Application-only enforcement (code never calls DELETE) | Simple | Malicious DBA or code bug wipes audit trail; legally indefensible | ❌ Trust in code alone is not enough for legal compliance |
| DB-level trigger + append-only constraint | Immutability enforced at DB layer — survives code bugs, DBA mistakes, and SQL injection | Trigger overhead ~5%; soft-delete patterns blocked | ✅ Best — auditors accept DB constraints as evidence of immutability |
| Blockchain-backed audit log | True cryptographic immutability; no central authority | Slow (consensus overhead); high cost; overkill for a signing system with its own CA | ❌ Cost and complexity not justified at this scale |

> 📖 Full: `SystemDesignConcepts/Production-Grade/System-Design-Patterns/13-security-pki.md`

**WHICH signing workflow model?**

| Option | Strength | Weakness | Verdict |
|---|---|---|---|
| Simple set (JSON array `[{user, signed: true/false}]`) | Zero extra code; fast | No enforced order; can't reject + rollback; can't escalate or expire; not ESIGN-compliant | ❌ Can't meet legal signing order requirements |
| State machine (explicit PENDING→SIGNED→REJECTED transitions) | Enforces sequential order; handles rejections + rollback + expiry; standards-compliant | Transition validation logic needed | ✅ Best — matches enterprise e-signature workflows |
| Event-sourced workflow | True audit trail; fully replayable state | Expensive queries (replay all events for current state); overkill for MVP | ⚠️ Good for complex compliance; migrate here at DocuSign's full complexity |

> 📖 Full: `SystemDesignConcepts/Production-Grade/System-Design-Patterns/13-security-pki.md`

**WHICH webhook delivery?**

| Option | Strength | Weakness | Verdict |
|---|---|---|---|
| Sync HTTP (call customer URL inline) | Simple; immediate delivery | Customer server slow/down → signing response delayed or fails; couples signing availability to external systems | ❌ Breaks signing availability |
| Redis pub/sub | Low latency | At-most-once — if Webhook Service is down when event fires, event is permanently lost | ❌ Unreliable for legal notification |
| Kafka (at-least-once, durable) | Signing returns immediately after Kafka publish; webhook retried until delivered; dead-letter queue for persistent failures | Extra infra | ✅ Best — signing and webhook delivery are completely decoupled |

> 📖 Full: `SystemDesignConcepts/Production-Grade/Infrastructure/19-message-queues-kafka-rabbitmq.md`

---

### Data Flow Walkthrough (say this out loud)

**Flow 1 — Creating a document for signature:**
1. `POST /v1/documents` with metadata + signer list. API Gateway validates JWT, checks tenant.
2. Document Service creates `documents` record (PENDING_SIGNATURE) + one `signing_sessions` row per signer with `signer_order`.
3. First signer notified via Kafka → Notification Service (email/push). Ready to sign.

**Flow 2 — Signing a document (Stage 2):**
1. `POST /v1/documents/{id}/sign` with `{signature_bytes, certificate_serial}`.
2. Signature Service: (a) GET cert from Redis (`cert:{serial}`, 1hr TTL) — cache miss → DB fetch → cache; (b) `SISMEMBER revoked_serials {serial}` — O(1) revocation check; (c) RSA verify (~50ms); (d) if valid: UPDATE `signing_sessions`, INSERT `audit_signature_events` (immutable); (e) publish `doc.signed` to Kafka → return 200 immediately.
3. Kafka consumer (Webhook Service) delivers notification to customer URL with retries. Signing is not affected by delivery outcome.

**Flow 3 — Querying audit trail (legal discovery):**
1. `GET /v1/documents/{id}/audit` (JWT auth, tenant isolation).
2. Audit Service queries `audit_signature_events ORDER BY event_timestamp ASC` — no filter, pure chronological append-only log.
3. Returns: who signed, when (UTC), from where (IP), with which cert, signature hash. Immutable forever — DB trigger prevents tampering.

---

## Section 7 — 🔬 Core Component Deep Dives (Minutes 25–40)

### Deep Dive 1: Digital Signature Verification (PKI Layer)

**Why this is the most critical component:**
This is where the system's legal binding and trust come from. A signature verification failure means the document is legally questionable. At 35 sig/sec, you must verify signatures quickly (< 50ms) without bottlenecking. If the CA infrastructure fails, no one can sign.

**Options considered:**

| Option | Pros | Cons |
|---|---|---|
| **Option A: DocuSign-hosted CA** (we generate user RSA key pairs, store private keys in HSM — Hardware Security Module, a tamper-proof physical device that stores private keys; keys cannot be extracted even with physical access to the hardware — and sign on user's behalf) | Seamless UX; no BYOK complexity; we control the entire cert lifecycle | High security risk (private keys at rest on our hardware); users don't own their signing identity; auditors question "non-repudiation" (who actually signed?) |
| **Option B: User BYOK** (user generates RSA key pair offline, uploads public key, signs locally with their private key, sends us the signature) | User owns their private key; cryptographically cleaner non-repudiation (they sign); auditors accept it | Complex UX (users must manage keys); key rotation requires re-uploading; if user loses private key, can't sign anymore |
| **Option C: Hybrid** (DocuSign issues certificates to users, but users hold private keys in secure enclave / TPM on device) | Best of both: DocuSign manages cert lifecycle; user owns private key | Device-dependent (works on desktop, tricky on mobile); requires SE/TPM support |

**Decision: Option A (DocuSign-hosted CA) for MVP, with Option C path planned.**

Because the target is enterprise adoption (DocuSign's market). Enterprises want "easy, one-click signing." Asking users to manage RSA keys is a support nightmare. We issue certificates to users (signed by our Intermediate CA), generate their key pairs in an HSM, and sign on their behalf with the private key.

**Non-repudiation guarantee:** The audit log proves "user_id=john, certificate_serial=0x1234abcd, signed_at=2026-06-24T15:30:00Z." If John disputes the signature, we produce: (1) the signed certificate linking his identity to cert serial, (2) the audit log, (3) IP address + device info. This is legally defensible because we can show the entire signing context.

**The trade-off I'm accepting:** Users don't "own" their signing key in the cryptographic sense (we do). But in practice, only John has access to his account (2FA), so only John could have triggered the signature. This is acceptable for most enterprises and regulators.

**Implementation sketch:**

```java
// Service that handles signature verification
public class SignatureVerificationService {
    private final CertificateStore certStore;  // Redis + DB
    private final AuditLogger auditLogger;

    /**
     * Verify a digital signature using PKI.
     * Called when a user signs a document.
     * @param documentBytes the document content that was signed
     * @param signatureBytes the digital signature (encrypted hash)
     * @param certificateSerial the cert serial number from the user's auth token
     * @return true if signature is valid (doc hash matches)
     */
    public boolean verifySignature(
            byte[] documentBytes,
            byte[] signatureBytes,
            String certificateSerial,
            String userId,
            String tenantId) throws SignatureInvalidException {
        
        // Step 1: Look up the user's certificate (from Redis cache or DB)
        Certificate userCert = certStore.getCertificate(userId, certificateSerial);
        if (userCert == null) {
            throw new SignatureInvalidException("Certificate not found or revoked");
        }
        
        // Step 2: Check certificate validity (not expired, not revoked)
        if (userCert.isExpired() || userCert.isRevoked()) {
            throw new SignatureInvalidException("Certificate expired or revoked");
        }
        
        // Step 3: Extract the public key from the certificate
        PublicKey publicKey = userCert.getPublicKey();  // RSA public key
        
        // Step 4: Verify the signature (this re-hashes the document and compares)
        // SHA256withRSA: first hash the document with SHA-256 (produces a 256-bit fingerprint),
        // then the private key "signs" that hash (encrypts it with RSA math);
        // the verifier decrypts with the public key and checks whether the hashes match
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(publicKey);
        verifier.update(documentBytes);
        
        boolean isValid = false;
        try {
            isValid = verifier.verify(signatureBytes);
        } catch (SignatureException e) {
            isValid = false;
        }
        
        // Step 5: Log to audit trail (immutable append)
        // This log proves the signing happened at this moment with this certificate
        auditLogger.logSignatureEvent(
            userId,
            tenantId,
            documentBytes.length,  // For integrity verification
            certificateSerial,
            isValid,
            Instant.now(),  // UTC timestamp — never changes
            RequestContext.getCurrentIp(),
            RequestContext.getCurrentUserAgent()
        );
        
        if (!isValid) {
            throw new SignatureInvalidException("Signature verification failed");
        }
        
        return true;
    }

    /**
     * Cache user certificates in Redis for fast lookups.
     * TTL = certificate expiry + 1 day (after expiry, the cert is no longer usable).
     */
    private void cacheUserCertificate(String userId, Certificate cert) {
        String cacheKey = "cert:" + userId + ":" + cert.getSerialNumber();
        long ttl = Duration.between(Instant.now(), cert.getNotAfter()).getSeconds() + 86400;
        redisCache.set(cacheKey, cert.serialize(), ttl);
    }
}
```

**Why this deep dive matters:**
- Signature verification is the trust foundation. If you get this wrong, the entire system is legally questionable.
- The certificate lookup path (Redis → DB) must be fast (< 20ms) because it's on the critical path.
- Revocation checks must be present (if a user's cert is compromised, they can't sign anymore).
- The audit log is written AFTER verification succeeds, so you're only logging valid signatures.

---

### Deep Dive 2: Audit Trail (Immutability + Non-Repudiation)

**Why this is the most critical component:**
This is the legal proof that John signed the document. If the audit trail can be tampered with, the system is worthless. At 1M signature events/day, you're writing to the audit log ~11.6 times/sec, and reading it (legal discovery) ~175 times/sec. Immutability must not compromise performance.

**Options considered:**

| Option | Pros | Cons |
|---|---|---|
| **Option A: Event Sourcing** (every state change is an immutable event; recreate state by replaying events) | True immutability; can always audit the full history; easy to debug | Expensive to query (must replay all events); slow at scale; large storage overhead |
| **Option B: Append-only audit log + soft deletes** (signing table is mutable, separate audit_events table is append-only) | Fast queries on current state (signing table indexed); immutable audit trail (audit_events never changes); best of both | Requires discipline (devs must use soft-delete functions, not raw DELETE) |
| **Option C: Blockchain-backed** (every signature is recorded on a blockchain; immutable by design) | True immutability; cryptographic proof; auditors love it | Slow (consensus overhead); overkill for signing; cost (blockchain transactions) |

**Decision: Option B (Append-only audit log + soft deletes).**

Because it balances immutability (audit_events table has no UPDATE or DELETE triggers) with performance (current state queries are fast on the signing table). This is industry standard for legal compliance (e.g., financial audit logs, healthcare records).

**Implementation sketch:**

```sql
-- The signing event table (mutable state)
CREATE TABLE signing_sessions (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL,
    signer_user_id UUID NOT NULL,
    signer_order INT,  -- 1 = first, 2 = second, etc. (if sequential)
    signed_at TIMESTAMP,  -- NULL until they sign
    signature_hash CHAR(64),  -- SHA-256 of the signature bytes (for audit proof)
    status VARCHAR(20) CHECK (status IN ('PENDING', 'SIGNED', 'REJECTED')),
    rejection_reason TEXT,
    tenant_id UUID NOT NULL,
    
    -- For UI queries: "who has signed?"
    INDEX idx_doc_status (document_id, status),
    INDEX idx_signer_pending (signer_user_id, status)
);

-- The append-only audit trail (immutable legal record)
-- This table ONLY has INSERT operations. No UPDATE, no DELETE.
-- Constraint: DB-level triggers prevent modifications.
CREATE TABLE audit_signature_events (
    id BIGSERIAL PRIMARY KEY,  -- auto-increment, ensures strict ordering
    signing_session_id UUID NOT NULL,
    document_id UUID NOT NULL,
    signer_user_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    
    -- The proof: what happened, when, from where
    action VARCHAR(20),  -- 'SIGNED', 'REJECTED', 'ATTEMPTED'
    signature_hash CHAR(64),  -- SHA-256 of the actual signature bytes
    certificate_serial VARCHAR(255),  -- which cert was used
    certificate_subject_dn TEXT,  -- "CN=John Doe, O=Acme Corp"
    
    -- Tamper-evidence
    client_ip INET,
    user_agent TEXT,
    http_host VARCHAR(255),
    
    -- Immutable timestamp (UTC, never changes, never in local tz)
    event_timestamp TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    -- For integrity checks
    event_hash CHAR(64),  -- HMAC(previous_event_hash + this_event_data, secret_key)
    
    tenant_id UUID NOT NULL,
    
    -- Append-only enforcement: via trigger below (see prevent_audit_modification)
    -- DO NOT add a CHECK constraint here — Postgres has no native append-only constraint;
    -- the trigger is the correct mechanism.

    INDEX idx_doc_audit (document_id, event_timestamp),
    INDEX idx_signer_audit (signer_user_id, event_timestamp),
    INDEX idx_tenant_audit (tenant_id, event_timestamp)
);

-- Trigger: prevent updates and deletes on the audit table
CREATE OR REPLACE FUNCTION prevent_audit_modification() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Audit table is append-only. No UPDATE or DELETE allowed.';
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_immutable_trigger
    BEFORE UPDATE OR DELETE ON audit_signature_events
    FOR EACH ROW
    EXECUTE FUNCTION prevent_audit_modification();
```

**Audit logging on signature:**

```java
public class AuditLogger {
    private final JdbcTemplate jdbc;
    private final SecretKey hmacKey;  // for event_hash tamper-evidence

    /**
     * Log a signature event (append-only).
     * This is called AFTER signature verification succeeds.
     */
    public void logSignatureEvent(
            String signingSessionId,
            String documentId,
            String signerUserId,
            String tenantId,
            String action,  // 'SIGNED' or 'REJECTED' or 'ATTEMPTED'
            byte[] signatureBytes,
            String certificateSerial,
            String certificateSubjectDn,
            String clientIp,
            String userAgent) {
        
        String signatureHash = sha256Hex(signatureBytes);
        Instant now = Instant.now();  // UTC timestamp
        
        // Build the event data for HMAC
        String eventData = String.format(
            "%s|%s|%s|%s|%s|%s|%s",
            documentId, signerUserId, action, signatureHash, certificateSerial, clientIp, now
        );
        String eventHash = hmacSha256Hex(eventData, hmacKey);
        
        // Append to audit table (single INSERT, no UPDATE/DELETE)
        String sql = """
            INSERT INTO audit_signature_events (
                signing_session_id, document_id, signer_user_id, tenant_id,
                action, signature_hash, certificate_serial, certificate_subject_dn,
                client_ip, user_agent, http_host, event_timestamp, event_hash
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        
        jdbc.update(sql,
            signingSessionId, documentId, signerUserId, tenantId,
            action, signatureHash, certificateSerial, certificateSubjectDn,
            clientIp, userAgent, RequestContext.getHost(), now, eventHash
        );
    }

    /**
     * Query the audit trail (for legal discovery).
     * No filtering by status or approval — just raw chronological order.
     */
    public List<AuditEvent> getAuditTrail(String documentId, String tenantId) {
        String sql = """
            SELECT signing_session_id, document_id, signer_user_id, action,
                   signature_hash, certificate_serial, certificate_subject_dn,
                   client_ip, event_timestamp, event_hash
            FROM audit_signature_events
            WHERE document_id = ? AND tenant_id = ?
            ORDER BY event_timestamp ASC
        """;
        
        return jdbc.query(sql, new Object[]{documentId, tenantId}, (rs, rowNum) -> {
            AuditEvent event = new AuditEvent();
            event.setSigningSessionId(rs.getString("signing_session_id"));
            event.setAction(rs.getString("action"));
            event.setSignatureHash(rs.getString("signature_hash"));
            event.setCertificateSerial(rs.getString("certificate_serial"));
            event.setEventTimestamp(rs.getTimestamp("event_timestamp").toInstant());
            event.setClientIp(rs.getString("client_ip"));
            return event;
        });
    }
}
```

**Why this deep dive matters:**
- The audit trail is the legal proof. If John denies signing, you produce the audit_signature_events rows showing: (1) exact timestamp (UTC), (2) signature hash, (3) certificate, (4) IP/user-agent (context).
- Immutability at the database level (triggers) prevents accidental or malicious modification.
- Event hashing (HMAC) allows you to detect tampering: if someone modifies a row, the event_hash breaks.
- The separate signing_sessions table keeps queries fast (for UI: "who has signed?"), while audit_signature_events stays pure append-only.

---

### Deep Dive 3: Multi-Party Signing Workflow (State Machine)

**Why this is the most critical component:**
Many enterprise documents require multiple signers (sequential: CEO signs, then CFO, then COO; or parallel: all department heads sign together). At 35 sig/sec, you're managing thousands of concurrent signing sessions. State transitions must be atomic (can't leave a document in an invalid state).

**Options considered:**

| Option | Pros | Cons |
|---|---|---|
| **Option A: Simple set** (store signers as a JSON array `[{user_id, signed: false}, {user_id, signed: true}]`; toggle signed flag when they sign) | Simple; cheap; easy to understand | No ordering; can't enforce sequential signing; no state transitions (approved/rejected); rollback complex |
| **Option B: State machine with workflow** (define state transitions: PENDING → IN_PROGRESS → COMPLETED or REJECTED; each signer has a session with explicit status) | Enforces signing order; allows rejections + rollback; audit trail per state; standards-compliant (matches e-signature law) | Slightly more complex; needs transition validation logic |
| **Option C: Event-sourced workflow** (every state change is an event; document state is computed by replaying events) | True audit trail; debuggable; flexible | Overkill for this problem; slower queries |

**Decision: Option B (State machine with workflow).**

Because e-signature laws (ESIGN, GDPR, eIDAS — the EU regulation for Electronic IDentification, Authentication and trust Services; the European equivalent of the US ESIGN Act, legally recognising electronic signatures across all EU member states) expect ordered signing workflows with explicit approval/rejection states. Enterprises also expect "if signer rejects, the document goes back to signer 0" — Option A can't do this efficiently.

**Implementation sketch:**

```java
// State machine for multi-party signing
public enum SigningSessionStatus {
    PENDING,          // waiting for this signer to act
    SIGNED,           // signer has signed
    REJECTED,         // signer rejected the document
    EXPIRED,          // signer didn't sign in time
    CANCELLED;        // someone cancelled the document

    public static final Map<SigningSessionStatus, List<SigningSessionStatus>> VALID_TRANSITIONS = Map.ofEntries(
        Map.entry(PENDING, List.of(SIGNED, REJECTED, EXPIRED, CANCELLED)),
        Map.entry(SIGNED, List.of(CANCELLED)),  // can't unsigned a signature
        Map.entry(REJECTED, List.of(PENDING)),  // allow re-send if rejected
        Map.entry(EXPIRED, List.of(PENDING)),   // allow re-send if expired
        Map.entry(CANCELLED, List.of())         // terminal state
    );
}

public class SigningWorkflowService {
    private final SigningSessionRepository sessionRepo;
    private final DocumentRepository docRepo;
    private final AuditLogger auditLogger;

    /**
     * Transition a signing session to a new status.
     * Validates the transition, appends to audit log, and propagates to next signer if sequential.
     */
    public void transitionSigningSession(
            String sessionId,
            SigningSessionStatus newStatus,
            String reason,  // e.g., "Signature verification failed" or "User rejected"
            String tenantId) throws InvalidStateTransitionException {
        
        SigningSession session = sessionRepo.findById(sessionId);
        SigningSessionStatus oldStatus = session.getStatus();
        
        // Validate transition
        List<SigningSessionStatus> validNextStates = 
            SigningSessionStatus.VALID_TRANSITIONS.get(oldStatus);
        
        if (!validNextStates.contains(newStatus)) {
            throw new InvalidStateTransitionException(
                String.format("Cannot transition from %s to %s", oldStatus, newStatus)
            );
        }
        
        // Atomic update + audit
        session.setStatus(newStatus);
        session.setTransitionedAt(Instant.now());
        session.setTransitionReason(reason);
        sessionRepo.save(session);
        
        // Audit log
        auditLogger.logStateTransition(
            session.getDocumentId(), session.getId(), oldStatus, newStatus, reason, tenantId
        );
        
        // If this signer signed successfully and signing is sequential, activate the next signer
        if (newStatus == SigningSessionStatus.SIGNED) {
            String documentId = session.getDocumentId();
            int signerOrder = session.getSignerOrder();
            
            // Find next signer (order = signerOrder + 1)
            SigningSession nextSigner = sessionRepo.findByDocumentAndOrder(documentId, signerOrder + 1);
            if (nextSigner != null) {
                // This signer is now ready to sign
                sendNotificationToSigner(nextSigner);  // email/webhook
            } else {
                // No more signers — document is fully signed!
                Document doc = docRepo.findById(documentId);
                doc.setStatus(DocumentStatus.FULLY_SIGNED);
                doc.setFullySignedAt(Instant.now());
                docRepo.save(doc);
                
                // Emit event for webhook fanout
                eventPublisher.publishEvent(new DocumentFullySignedEvent(documentId, tenantId));
            }
        }
        
        // If this signer rejected, revert all previous signers to PENDING
        if (newStatus == SigningSessionStatus.REJECTED) {
            String documentId = session.getDocumentId();
            List<SigningSession> allSessions = sessionRepo.findByDocument(documentId);
            for (SigningSession s : allSessions) {
                if (s.getSignerOrder() < session.getSignerOrder() && s.getStatus() == SigningSessionStatus.SIGNED) {
                    // Reset them to PENDING (they'll need to re-sign)
                    s.setStatus(SigningSessionStatus.PENDING);
                    sessionRepo.save(s);
                    auditLogger.logStateTransition(
                        documentId, s.getId(), SigningSessionStatus.SIGNED, SigningSessionStatus.PENDING,
                        "Document was rejected by a later signer; this signer must re-sign", tenantId
                    );
                }
            }
            
            // Send the document back to the first signer
            SigningSession firstSigner = sessionRepo.findByDocumentAndOrder(documentId, 1);
            sendNotificationToSigner(firstSigner);
        }
    }
}
```

**Why this deep dive matters:**
- Sequential signing is common in enterprises (approval chains), so the state machine must enforce order.
- Rejections and rollbacks are complex — if signer 3 rejects, signers 1-2 must re-sign. The state machine handles this.
- Atomic transitions (one UPDATE on signing_sessions) + one INSERT on audit_log ensure the document never gets into a broken state.

---

## Section 9 — 🗄️ Data Model

### Core Tables

```sql
CREATE TABLE documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    created_by_user_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(20) CHECK (status IN ('DRAFT', 'PENDING_SIGNATURE', 'FULLY_SIGNED', 'REJECTED', 'EXPIRED', 'CANCELLED')),
    
    signing_order VARCHAR(20) CHECK (signing_order IN ('SEQUENTIAL', 'PARALLEL')),
    rejection_reason TEXT,
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    fully_signed_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE,  -- e.g., 30 days from creation
    
    tenant_id UUID NOT NULL,
    
    INDEX idx_tenant_created (tenant_id, created_at DESC),
    INDEX idx_tenant_status (tenant_id, status),
    INDEX idx_tenant_creator (tenant_id, created_by_user_id)
);

CREATE TABLE signing_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL REFERENCES documents(id),
    signer_user_id UUID NOT NULL,
    signer_order INT NOT NULL,  -- 1 for first, 2 for second, etc.
    
    status VARCHAR(20) CHECK (status IN ('PENDING', 'SIGNED', 'REJECTED', 'EXPIRED', 'CANCELLED')),
    signature_hash CHAR(64),  -- SHA-256 of signature bytes (for proof)
    certificate_serial VARCHAR(255),  -- which cert they used
    
    signed_at TIMESTAMP WITH TIME ZONE,
    rejected_at TIMESTAMP WITH TIME ZONE,
    rejection_reason TEXT,
    
    tenant_id UUID NOT NULL,
    
    INDEX idx_doc_order (document_id, signer_order),
    INDEX idx_signer_pending (signer_user_id, status),
    UNIQUE (document_id, signer_user_id)  -- one entry per signer per doc
);

-- Append-only audit log (immutable legal record)
CREATE TABLE audit_signature_events (
    id BIGSERIAL PRIMARY KEY,
    signing_session_id UUID NOT NULL REFERENCES signing_sessions(id),
    document_id UUID NOT NULL REFERENCES documents(id),
    signer_user_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    
    action VARCHAR(20),  -- 'SIGNED', 'REJECTED', 'ATTEMPTED', 'STATE_TRANSITION'
    signature_hash CHAR(64),
    certificate_serial VARCHAR(255),
    certificate_subject_dn TEXT,  -- "CN=John Doe, O=Acme"
    
    client_ip INET,
    user_agent TEXT,
    http_host VARCHAR(255),
    
    event_timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    event_hash CHAR(64),  -- HMAC for tamper detection
    
    INDEX idx_doc_audit (document_id, event_timestamp),
    INDEX idx_signer_audit (signer_user_id, event_timestamp),
    INDEX idx_tenant_audit (tenant_id, event_timestamp)
);

-- Trigger to prevent modifications to audit table
CREATE OR REPLACE FUNCTION prevent_audit_modification() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Audit table is append-only. No UPDATE or DELETE allowed.';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_immutable_trigger
    BEFORE UPDATE OR DELETE ON audit_signature_events
    FOR EACH ROW
    EXECUTE FUNCTION prevent_audit_modification();

-- Certificate store (user public keys + certs)
CREATE TABLE user_certificates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    certificate_serial VARCHAR(255) NOT NULL,
    certificate_pem TEXT NOT NULL,  -- PEM-encoded (Privacy Enhanced Mail format): Base64 text bounded by -----BEGIN CERTIFICATE----- and -----END CERTIFICATE----- headers; the standard text format for X.509 certificates and public keys — copy-pasteable, human-readable, and universally supported
    certificate_subject_dn TEXT,
    
    issued_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,  -- NULL if not revoked
    revocation_reason VARCHAR(255),
    
    INDEX idx_user_certs (user_id, expires_at),
    INDEX idx_serial (certificate_serial),
    UNIQUE (user_id, certificate_serial)
);
```

### Key Schema Decisions

- **documents.status**: Tracks the overall document state (who's responsible next). Separate from signing_sessions.status (who's responsible for which step).
- **signing_sessions.signer_order**: Enforces sequential signing (order 1 → 2 → 3). Parallel signing has all orders = 1 or orders are independent.
- **audit_signature_events (append-only)**: Immutable by constraint. This table is never updated, only inserted. It's the legal proof.
- **signature_hash**: We store the SHA-256 hash of the signature bytes, not the bytes themselves (too large for the row). This allows us to verify: "you signed this document" without storing gigabytes of binary data.
- **event_timestamp**: Always in UTC. Never local time. This is immutable and standardized.
- **certificate_serial**: Links back to the user's public key for verification. If the cert is revoked, new signatures are invalid.
- **Indexing strategy:**
  - `idx_tenant_created` on documents: fast "list all documents for this customer" queries
  - `idx_signer_pending` on signing_sessions: fast "what documents are waiting for me to sign?" queries
  - `idx_doc_audit` on audit_signature_events: fast "show me the legal trail for this document" queries
  - `idx_tenant_audit`: GDPR compliance (data localization): "show me all audit events for this tenant in this region"

---

## Section 10 — ⚠️ Trade-Offs + Failure Modes (Minutes 45–52)

### Trade-off 1: Signature Bytes Storage (Store vs Derive)

**Chose:** Store signature_hash (SHA-256), not the full signature bytes.

**Gain:** Storage efficiency (256 bits vs 256+ bytes for RSA signature); faster queries; audit table stays small (~550 GB/year vs 5.5 TB/year if we stored raw signatures).

**Lose:** Can't re-verify signatures without the original signature bytes. If a dispute arises, we'd need to ask the user for the original signature bytes to re-verify.

**Failure mode if wrong:** If you store the full signature bytes and you have 1B signature events/year at 256 bytes each, you're at 256 TB storage. Your S3 bill and query latency become untenable. Postgres can't handle 100GB+ audit tables efficiently. **Business impact:** Audit log queries for a specific envelope's signing history time out (>30s) as the table grows past 100GB — for DocuSign this means a compliance officer investigating a disputed signature cannot retrieve the event trail during a live legal hearing, must escalate to a DBA, and the delay is measured in hours instead of seconds — creating a discovery response failure.

---

### Trade-off 2: Key Management (Hosted vs BYOK)

**Chose:** DocuSign-hosted CA + HSM-secured private keys (Option A).

**Gain:** Seamless UX (users don't manage keys); fast signing (DocuSign signs on behalf); enterprise adoption (no BYOK complexity).

**Lose:** Higher security responsibility (we hold private keys); regulatory scrutiny (auditors might question whether users "really" signed if we hold the key).

**Failure mode if wrong:** If you choose BYOK and users lose their private keys (not uncommon), you get "I can't sign anymore — please reset my key" support tickets every day. At 1M users with 5% key-loss rate = 50K support tickets/year. If you choose hosted and the HSM is compromised, all user keys are exposed + non-repudiation claim fails (auditors reject the signatures). **Business impact:** For DocuSign: 50K annual support tickets from key-loss (BYOK failure mode) each require manual key rotation assistance — at $30/ticket cost-to-serve, that's $1.5M/year in support cost, plus every locked-out user cannot sign envelopes, stalling their counterparties' workflows. The HSM compromise scenario is existential: every historical DocuSign signature becomes legally contestable.

---

### Trade-off 3: Audit Trail Immutability (DB Constraints vs Application-Only)

**Chose:** Database-level trigger + application logic to prevent modifications.

**Gain:** Audit trail is protected even if code is hacked (attacker can't UPDATE audit_signature_events even with direct DB access); legally defensible (auditors see the constraints in the schema).

**Lose:** Slightly slower writes (trigger overhead ~5-10%); can't use soft-delete patterns on audit table (DELETE is blocked by trigger).

**Failure mode if wrong:** If you only enforce immutability in application code (e.g., "don't call DELETE on audit logs"), a malicious DBA or code bug can easily wipe the audit trail. Then you've lost non-repudiation and have no proof John signed the document. You lose the lawsuit. **Business impact:** For DocuSign: a wiped audit trail in an active litigation means DocuSign cannot produce the legally required signing evidence — the court may rule the signature inadmissible, invalidating the contract, and DocuSign faces liability for failing to maintain the legal record it sold as a product feature.

---

## Section 11 — 🔐 DocuSign-Specific Depth (Minutes 52–57)

**Why this question is on the DocuSign R2 interview:**

DocuSign IS a digital signature company. Their entire business depends on the legitimacy and legal binding of signatures. An interviewer from DocuSign will ask you about non-repudiation, audit trails, and certificate management because **this is their core product**.

**DocuSign-specific angles your answer must address:**

1. **Non-repudiation guarantee**: "John can't later claim he didn't sign." The audit trail proves: (1) certificate linked to John's identity, (2) signature was valid, (3) timestamp, (4) context (IP, device). This is legally defensible in courts.

2. **Multi-party signing workflows**: Enterprises sign agreements with lawyers, accountants, and executives. Your state machine must enforce: "lawyer approves → accountant reviews → CEO signs" (or parallel variations). DocuSign's product has this as a core feature.

3. **Compliance & audit trail**: GDPR (data residency, right to deletion — but audit logs can't delete), ESIGN Act (signature must be unambiguous), SOC 2 (audit trail must be tamper-evident). Your design must address all three.

4. **Certificate revocation**: "If an employee leaves, we revoke their certificate, and they can't sign anymore." Your PKI infrastructure must support this.

5. **Webhook on completion**: When a document is fully signed, DocuSign notifies the customer's systems (CRM, contract management, etc.). Your design must support async, idempotent webhooks with retry logic.

6. **PAdES — DocuSign's actual PDF signing format:**

PAdES (PDF Advanced Electronic Signatures — the international standard, ISO 32000-2 / ETSI EN 319 132, that defines how cryptographic signatures are embedded *inside* a PDF file in a legally interoperable format) is what DocuSign actually uses under the hood. It is not what most candidates describe.

Most candidates say: "hash the document, sign the hash, store it separately." This is **not** how production PDF signing works.

PAdES works differently:
- The PDF is pre-processed to create a **byte-range placeholder** — a gap in the file where the signature will go
- SHA-256 is computed over everything in the file **except** the placeholder gap (the byte ranges before and after)
- The hash is signed using the signer's RSA private key
- The resulting DER-encoded signature is embedded **inside the PDF** in that placeholder gap
- The result is a single self-contained PDF that carries its own signature proof

**Why byte-range, not whole-file hash?** Because when you embed the signature bytes into the PDF, the file changes. If you hashed the whole file before embedding, the hash would be invalid after embedding. Byte-range lets you hash everything except the space where the signature will sit.

**Subsequent signers:** PAdES supports incremental updates — signer B appends their signature to the PDF without modifying signer A's byte range. The file grows, and each signer's byte range is independent.

```
PDF structure after 2 signers:
┌─────────────────────────────────────────┐
│ PDF Header + Document Content           │ ← byte range A (before Alice's sig)
├─────────────────────────────────────────┤
│ Alice's Signature Bytes (DER/PKCS#7)    │ ← embedded at byte offset X
├─────────────────────────────────────────┤
│ Incremental Update (signer 2 section)   │ ← byte range B (before Bob's sig)
├─────────────────────────────────────────┤
│ Bob's Signature Bytes (DER/PKCS#7)      │ ← embedded at byte offset Y
└─────────────────────────────────────────┘

KEY INVARIANT:
   Each signer's hash covers the byte ranges of all prior content.
   Neither signer's signature covers their own embedded bytes (the gap).
   Both signatures are self-contained and independently verifiable.
```

**In an interview:** "DocuSign uses PAdES — the signature is embedded inside the PDF using a byte-range hash approach. SHA-256 is computed over everything except the signature placeholder, then the signed hash is embedded there. This produces a self-contained signed PDF — you don't need DocuSign's servers to verify it; any PDF reader with certificate support can." This is the answer that signals you actually know how production signing works, not just the academic description.

7. **RFC 3161 TSA — Trusted Timestamp Authority:**

RFC 3161 (a standard protocol for obtaining a trusted timestamp from an independent third-party server — the Trusted Timestamp Authority — that proves a document existed in a specific form at a specific time, without trusting the signing server's own clock) is the production mechanism for legally defensible timestamps.

**Why your own server clock isn't enough:**
If DocuSign's own server stamps "signed at 2026-06-24 15:14:00 UTC," a signer's lawyer can argue: "DocuSign could have backdated this. You're the same company that benefits from proving this was signed on time." Courts have accepted this argument.

**How RFC 3161 works:**
1. After signing, compute a hash of the signature blob
2. Send that hash to an independent RFC 3161 Timestamp Authority (DigiCert, Sectigo, etc.)
3. The TSA cryptographically signs a `TSTInfo` structure containing: the hash you sent, the TSA's own timestamp, and a TSA serial number
4. The TSA's signed token is embedded into the PDF alongside the signature (in the `SignatureTimeStamp` attribute)
5. To dispute the timestamp, you'd have to prove the **independent TSA** was compromised — a far higher bar than disputing DocuSign's own clock

**The two timestamp types in PAdES:**
- **Signature timestamp** (from RFC 3161 TSA): "This signature existed at this time" — attached immediately after signing
- **Document timestamp** (LTA profile): "This entire signed PDF, including all previous timestamps, existed at this time" — added periodically to extend the signature's validity beyond the signing certificate's lifetime

**In an interview:** "For legally defensible timestamps, I'd integrate RFC 3161 — after each signature, we obtain a timestamp token from an independent TSA like DigiCert. The TSA signs our signature hash with their own key and their own timestamp. This means even if someone disputes DocuSign's server clock, we have an independent cryptographic proof from a globally trusted third party. The TSA token is embedded directly in the signed PDF per PAdES standards." That sentence separates you from every candidate who says "we store `CURRENT_TIMESTAMP` from the DB."

**Your answer should include:**

> "The audit trail is immutable at the database level — a trigger prevents any UPDATE or DELETE. This ensures non-repudiation: John can't deny signing because the audit_signature_events table is tamper-proof. The timestamp is UTC (never local), and we store the certificate serial number, which proves which key was used. If John disputes the signature, we produce: (1) the audit log entry, (2) the certificate chain (proving his cert was issued by our CA), (3) the signature hash (proving it's cryptographically valid), and (4) the context (IP, user-agent, browser). This is legally defensible under ESIGN Act and acceptable in court."

> "For multi-tenant isolation, the signing_sessions and documents tables are filtered by tenant_id on every query. A customer's audit logs are never visible to another customer. For compliance, we store audit logs in the region the customer's account is in (EU customers' logs stay in EU data centers). We never delete audit logs (immutable forever) to satisfy regulatory requirements."

---

## Section 12 — 🔬 Where the Interviewer Will Probe (Minutes 57–60)

### Tier 1 — Surface Probe (Do you understand PKI?)

**Q: "How do you ensure that a signature was actually created by the user, not forged by someone else?"**

> The signature is created by encrypting a SHA-256 hash of the document with the user's private key. Only John has access to his private key (it's stored in our HSM, and the database/system design ensures only John's auth session can trigger a signing operation). The signature is mathematically linked to that specific private key — anyone can verify it with John's public key, but only John's private key could have created it. This is the essence of non-repudiation.

### Tier 2 — Deep Probe (Do you understand the failure modes?)

**Q: "What if the HSM (Hardware Security Module) storing users' private keys is compromised or fails? How does that affect the signature's legal validity, and what's your recovery strategy?"**

> If the HSM fails, we can't sign new documents — signing is blocked (safe-fail: availability loss, not security loss). If an attacker compromises the HSM, all private keys are exposed. Here's the recovery: (1) immediately revoke all certificates (update user_certificates.revoked_at = now()), (2) issue new certificates + re-generate key pairs on a fresh HSM, (3) notify all customers that signatures created during [breach window] must be re-done (we provide a way to re-send docs). Legally, old signatures may be questioned because the private key was potentially compromised. This is why HSM security is critical (FIPS 140-2 Level 3 — a US government standard for cryptographic hardware; Level 3 requires tamper-resistance AND tamper-response: the device zeroizes its keys if opened or attacked; the de-facto minimum bar for a production key-management HSM; 24/7 monitoring, air-gapped backup).

### Tier 3 — Cross-Concept Probe (Can you reason across concepts?)

**Q: "Your audit trail is immutable forever. GDPR allows users to request data deletion. How do you reconcile these two requirements?"**

> This is a real DocuSign problem. GDPR's right to be forgotten says "delete my data." But the audit trail for "I signed this contract" is the legal proof that the contract was signed, so deleting it violates the signature's validity and the counterparty's rights. The answer: immutable audit logs are **exempt from GDPR deletion requests** because they're evidence of a legal transaction. We can pseudonymize the audit log (replace user_id with a UUID, remove email/IP), but we keep the core proof (timestamp, signature hash, certificate serial). This is GDPR-compliant because we've minimized the personal data (GDPR principle: data minimization) while preserving the legal record.

---

### New Deep Probes (Tier 2 — added Jul 4, 2026)

**Q: "You check certificate revocation on every signing operation. At 35 sig/sec with thousands of active certificates, how do you keep that check under 1ms?"**
> There are two standard protocols for revocation checking:
>
> - **OCSP** (Online Certificate Status Protocol — a real-time HTTP request sent to the CA asking "is this specific certificate revoked?" — like calling a government office to verify an ID is still valid): adds 50–200ms per request on the critical path. Unusable at our signing latency budget.
>
> - **CRL** (Certificate Revocation List — a file periodically published by the CA containing all revoked certificate serial numbers; you download the entire list and cache it locally): download once per minute, load into a Redis HashSet. Revocation check = `SISMEMBER revoked_serials {serial}` = O(1), under 1ms.
>
> **Our approach:** Download CRL from the CA's distribution point every 60 seconds, load all revoked serials into `revoked_serials` Redis Set. On every signature verification, check `SISMEMBER revoked_serials {certificate_serial}` — sub-millisecond lookup. Worst case: a cert is revoked at T=0, CRL is refreshed at T+60s. For a 60-second window, a revoked cert could still sign. Acceptable for commercial signing; not acceptable for banking. To shrink the window: refresh every 10 seconds.
>
> **In an interview:** "OCSP is too slow for my 1ms latency budget. I cache the CRL in Redis as a HashSet, refreshed every minute. Revocation is O(1) in Redis. The 60-second revocation window is a deliberate trade-off: tighter (10s) is possible at the cost of more CA traffic."

---

**Q: "Your signing workflow is a custom state machine in code. At DocuSign scale with 1M+ concurrent signing sessions and complex approval chains (conditional routing, expiry reminders, escalation), would you reconsider using a dedicated workflow tool like Temporal?"**
> Yes — this is exactly the right question to ask at DocuSign's scale. My custom state machine (transitions stored in `signing_sessions.status`, transitions validated in `SigningWorkflowService`) works well at 35 sig/sec and simple sequential/parallel workflows. The code is readable, the state is in Postgres, and the trade-offs are clear.
>
> But DocuSign's actual product has:
> - **Expiry reminders** (send email at T+3 days if not signed)
> - **Escalation** (if signer A doesn't sign in 5 days, route to signer B)
> - **Conditional routing** (if field X is above $50K, require CFO counter-signature)
> - **Legal hold** (freeze all workflows for this document)
>
> A custom state machine for these requirements becomes a ~5,000-line service with edge cases that are hard to test and debug.
>
> **Temporal** (a distributed workflow orchestration system — think of it as a "state machine as a service" where each workflow step is a function, and Temporal automatically persists state and retries failed steps across crashes; unlike a plain Postgres state machine, Temporal provides built-in timers, signals, and child workflows with full execution history) handles expiry timers, retries, and complex routing natively. Every step is automatically logged. If the signing service crashes mid-workflow, Temporal replays the workflow from the last durable checkpoint.
>
> **Trade-off:** Temporal requires a Temporal cluster (additional infrastructure to operate and scale). The right answer for an interview: "I'd use a custom state machine for MVP — it's simple and debuggable. At DocuSign's actual scale and workflow complexity, I'd migrate to Temporal to avoid building timeout management, retry orchestration, and audit history myself."

---

### New Cross-Concept Probe (Tier 3 — added Jul 4, 2026)

**Q: "DocuSign serves enterprises in the EU (GDPR) and the US (ESIGN). If an EU customer signs a document at 3 AM UTC and you later need to present the timestamp in US court, what's your audit timestamp strategy?"**
> Three common mistakes:
> 1. Storing timestamps in the user's local timezone — a US court sees "3:14 PM" but you can't prove the offset without knowing where the user was at signing time.
> 2. Storing timestamps with a system-default timezone — brittle if the server ever changes timezone config.
> 3. Storing timestamps as Unix epoch integers — correct, but requires conversion in every query.
>
> **Correct approach: always store as `TIMESTAMP WITH TIME ZONE` in UTC, never nullable.** In Postgres, `TIMESTAMP WITH TIME ZONE` normalizes to UTC internally regardless of server timezone. The audit_signature_events table has `event_timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP` — this always writes UTC. When presenting in court (or to an EU DPA), we convert to local time at display time only.
>
> **Additional practice:** Log the signer's declared timezone (from their profile) alongside the UTC timestamp. This allows you to show the user-facing time ("You signed at 3:14 PM IST") while the legally authoritative time remains UTC. If the user disputes the time, you produce both — the UTC record and the timezone derivation.
>
> **In an interview:** "I store all timestamps as UTC. Display-time conversion happens in the API layer or the client. For legal proceedings, we produce the UTC timestamp plus the user's registered timezone. This is standard practice for any multi-jurisdiction legal system."

---

## Section 13 — 🐞 Common Mistakes on This Question

- **Mistake 1:** "I'll use encryption to protect signatures." → **Why it's wrong:** Encryption is for confidentiality (hiding data). Signatures are for authentication + non-repudiation (proving who created it). Use RSA signatures (asymmetric crypto), not encryption. → **What to say instead:** "I'll use RSA-2048 signing with SHA-256 hashing. The private key signs, the public key verifies. This provides non-repudiation because only the key holder could have signed."

- **Mistake 2:** "I'll store full signature bytes in the audit table." → **Why it's wrong:** At 1M signatures/day × 256 bytes = 256 MB/day = 93 GB/year. At 10 years, you're at 930 GB. Queries on a 930 GB table time out. → **What to say instead:** "I'll store the SHA-256 hash of the signature (256 bits) in the audit table for proof of signing. The full signature bytes are optionally stored in cold storage (S3) for later re-verification if disputed."

- **Mistake 3:** "Eventual consistency is fine for signing — if it takes a few seconds to show 'fully signed,' that's OK." → **Why it's wrong:** Signing is a legal transaction. If a user signs, the system must immediately show "signed" (strong consistency). Eventual consistency could lead to race conditions: user A thinks doc isn't signed, clicks "sign" again, creating duplicate signatures. → **What to say instead:** "Signing transitions must be strongly consistent. When a user signs, the signing_sessions row must be immediately updated and visible to all other signers and the initiator. I'll use a single Postgres instance with replication (HA) to achieve this, and I'll shard by customer_id at 18 months when storage approaches 5 TB."

---

## Section 14 — 🧭 DocuSign Dimensions Checklist

| Dimension | Relevant? | How your design addresses it |
|---|---|---|
| **Testability** | ✅ | Signature verification is deterministic (same doc + same key = same signature hash). Test by generating test certificates, creating test documents, signing, verifying. Audit trail is queryable for validation. State machine transitions are unit-testable. |
| **Usability** | ✅ | API is REST with clear 201/200 status codes. Error messages are specific ("SIGNATURE_VERIFICATION_FAILED" vs generic "400"). Sequential signing is guided (each signer knows whose turn it is via next_signer response). Webhook notifications tell external systems when to act. |
| **Extensibility** | ✅ | Multi-party signing (sequential + parallel) is configurable per document. Webhook URL is customer-provided (customer can change notif targets without re-deploying). Audit events are extensible (new action types can be added to audit_signature_events without breaking queries). |
| **Security** | ✅ | JWT auth on every endpoint. RSA-2048 signatures (industry standard). Immutable audit trail (DB trigger prevents tampering). Tenant isolation (tenant_id on every query). Certificate revocation (revoked_at check on every signature). Keys stored in HSM (not disk). |
| **Availability** | ✅ | Postgres with HA replication (2 replicas). If one replica fails, signing still works. API is stateless (load-balance across N instances). Message queue (SQS/Kafka) for webhook fanout (retries, not lost). 99.9% SLO achievable with this design. |
| **Scalability** | ✅ | At 35 sig/sec, single Postgres instance handles easily. At 100M users (10× scale), shard by customer_id (each customer's signing_sessions + documents on a separate shard). Audit table indexed by document_id + event_timestamp, so "show me this document's audit trail" is O(log N + K) where K = number of signings. |
| **Observability & Traceability** | ✅ | Every request has X-Request-ID header (traced through logs). Signature verification logs include timestamp, user, certificate, client_ip. Audit trail is queryable (GET /audit endpoint). State transitions are logged (audit_signature_events tracks every status change). Can trace: "did John sign this? When? From where?" |

---

## Section 15 — 🧾 TL;DR Answer Summary

> "A digital signature system at DocuSign scale requires three critical pieces: (1) PKI infrastructure to manage user certificates + key pairs (we use a hosted CA with HSM-secured private keys); (2) signature verification via SHA-256 hashing + RSA to ensure non-repudiation; (3) an immutable append-only audit trail with database-level constraints to prove 'John signed on June 24 at 3:14 PM UTC from 192.168.1.1' — this is the legal proof if disputes arise. The architecture is a Postgres cluster sharded by customer_id (for multi-tenancy + compliance), with a state machine for sequential/parallel signing workflows, webhooks for async notification, and strong consistency on the signing_sessions table (users can't race-condition and sign twice). Trade-offs: (1) we store signature hashes (not bytes) to keep the audit table queryable, (2) we hold private keys in HSM (simplifies UX, but requires strict security practices), (3) we never delete audit logs (immutable forever for legal compliance). The system addresses all 7 DocuSign dimensions: security (PKI + immutability), availability (HA Postgres), scalability (sharding by customer_id), observability (audit trail queries), and usability (clear API + state transitions)."

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 24, 2026 | **D1-digital-signature.md created.** Full 15-section solution framework for Mixed A+B interview type. Covers: PKI infrastructure (cert management, signature verification), audit trail immutability (append-only + DB triggers), multi-party signing state machine (sequential + parallel), GDPR/compliance angles, and DocuSign-specific depth (non-repudiation guarantees). Scale: 1M users, 35 sig/sec peak. Prerequisite: `13-security-pki.md`. |
| Jul 4, 2026 | **4 new Q&As added to Section 12.** (1) **Certificate revocation check latency** — OCSP is 50–200ms (unusable on critical path); CRL cached as Redis HashSet, refreshed every 60s; O(1) `SISMEMBER` check under 1ms; 60-second revocation window is explicit trade-off; (2) **Temporal as alternative to custom state machine** — custom FSM works for MVP at 35 sig/sec; Temporal handles expiry timers, escalation, conditional routing, and legal hold natively with full execution history; migration decision: Temporal at DocuSign's actual workflow complexity; (3) **UTC timestamp strategy for multi-jurisdiction legal compliance** — `TIMESTAMP WITH TIME ZONE` in Postgres always stores UTC; log user's declared timezone alongside UTC; court-presentable via UTC + timezone derivation. |
| Jul 5, 2026 | **Section 6 restructured into 2-stage progressive HLD.** Stage 1 (monolith + direct DB) — identifies two breaking points: sync webhook couples external system availability to signing success; uncached cert lookup hits DB on every sign request. Stage 2 (service split + Redis + Kafka) — cert cache in Redis (sub-ms CRL check + cert PEM TTL); Kafka decouples webhook delivery (signing returns immediately after publish); PKI infrastructure separated. Four decision tables added: key management (Hosted CA ✅ vs BYOK ❌ vs Hybrid ⚠️), audit trail immutability (DB trigger ✅ vs application-only ❌ vs blockchain ❌), signing workflow (state machine ✅ vs set ❌ vs event-sourced ⚠️), webhook delivery (Kafka ✅ vs Redis pub/sub ❌ vs sync HTTP ❌). Verdict alignment verified: all Section 6 table verdicts match Section 7 deep dive choices (Hosted CA ✅, DB trigger ✅, state machine ✅). |
| Jul 5, 2026 | **Section 10 business impact pass.** Added explicit **Business impact:** label to all 3 trade-offs — compliance officer timing out during live legal hearing due to unindexed audit log full table scan, HSM compromise making every historical DocuSign signature legally contestable at $1.5M/year key-loss support cost, non-repudiation destroyed when audit record digest doesn't match signature invalidating a court-submitted contract. |
