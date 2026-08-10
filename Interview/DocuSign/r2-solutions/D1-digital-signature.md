# D1 — Design a Digital Signature System

> **Read `solution-notes-standards.md` FIRST.** This file is a 60-minute interview answer framework.
> Its structure mirrors DELIVERY-RECIPE: clarify (5 min) → estimate (5 min) → HLD (15 min) → deep dives (15 min) → trade-offs (8 min) → Q&A (7 min).
> **Say this out loud** before your interview — don't just read it. Practice until the memory anchors feel natural.

---

## 📚 Prerequisites — Study These First

Before reading the solution, make sure you can explain the bolded items below from memory.

| Concept | File in `SystemDesignConcepts/` | Why you need it for this question |
|---|---|---|
| **PKI / Security fundamentals** (asymmetric crypto, X.509, certificate chains) | `Production-Grade/Auth-and-Security/13-security-pki.md` | Digital signatures are built on RSA/ECDSA key pairs — you must explain: how a private key signs a document hash, how a public key verifies it, and what tamper-evidence means at the byte level |
| **Auth / Authz fundamentals** | `Production-Grade/Auth-and-Security/27-auth-authz-fundamentals.md` | Multi-party signing needs identity verification for each signer — know JWT-based signer identity, role-scoped access to envelopes, and how OAuth 2.0 scopes map to envelope permissions |
| **State machines / workflows** | `Production-Grade/System-Design-Patterns/49-state-machines-workflows.md` | The envelope lifecycle (Created → Sent → Partially Signed → Fully Signed → Voided) is a state machine — routing_order drives sequential vs parallel signing |
| **Message queues (Kafka)** | `Core-Architecture/Service-Communication/19-message-queues-kafka-rabbitmq.md` | Webhook fan-out after each signing event — the Connect webhook system is a Kafka consumer publishing to external HTTP endpoints |
| **Idempotency** | `Foundations/Concurrency-and-Consistency/04-idempotency.md` | Signing must be idempotent — a network retry on "Apply Signature" must not double-apply or corrupt the audit trail |
| **Multi-step processes** | `Patterns/DeepDive/05-multi-step-processes.md` | Sequential signing (Signer 1 must complete before Signer 2 receives the document) is a multi-step workflow with dependency ordering |
| **CAP theorem** | `Core-Architecture/Distributed-Systems/34-cap-theorem-consistency-models.md` | Signed records = CP (you cannot have two conflicting "signed" states); notifications = AP (delivery staleness is acceptable, duplicates are recoverable) |

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

## 🔑 Technology Quick Reference

> **Read this once before the file.** These are the only cryptography and security acronyms you need to know cold for this question.

| Term | Plain-English meaning |
|---|---|
| **Asymmetric encryption** | Encryption where the key that locks and the key that unlocks are different. You publish the public key freely; you never share the private key. The basis of all digital signatures. |
| **Private key** | The secret half of an RSA key pair. Used to *sign* a document. Only the signer holds this — if compromised, all signatures are forged. |
| **Public key** | The shareable half. Anyone can use it to *verify* a signature made by the matching private key. Published in a certificate. |
| **RSA** | The most common asymmetric algorithm. Produces a key pair. Signing = hashing the document and encrypting the hash with the private key. Verification = decrypting the signature with the public key and comparing hashes. |
| **PKI** (Public Key Infrastructure) | The entire system — CAs, certificates, key pairs, revocation lists — that makes digital signatures legally trustworthy at scale. |
| **Certificate (X.509)** | A file that binds a public key to an identity (name, email, organization). Issued and digitally signed by a Certificate Authority. Like a passport — proves who owns a public key. |
| **CA** (Certificate Authority) | A trusted entity that issues certificates. DocuSign runs its own CA to sign user certificates. The CA's own certificate is the root of trust. |
| **HSM** (Hardware Security Module) | A tamper-resistant physical device that stores private keys. The key cannot be extracted even if you have physical access. DocuSign's CA private key lives in an HSM. |
| **Non-repudiation** | The legal guarantee that a signer cannot later deny having signed. Enforced cryptographically — the signature can only have been made by the holder of that exact private key. |
| **BYOK** (Bring Your Own Key) | The user generates their own RSA key pair and uploads only the public key. DocuSign never sees the private key. Higher security for users, higher verification complexity for DocuSign. |
| **OCSP** (Online Certificate Status Protocol) | A real-time protocol to check if a certificate has been revoked. 50–200ms latency — too slow to put on the signing critical path. |
| **CRL** (Certificate Revocation List) | A cached file listing all revoked certificate serial numbers. DocuSign loads this into Redis — O(1) lookup, sub-ms. Updated every 60 seconds. The practical alternative to OCSP on the critical path. |
| **Audit trail** | An immutable, append-only log of every event on a document — opened, signed, declined, completed — with timestamp, IP address, and certificate serial number. The legal evidence that non-repudiation claims are based on. |
| **Webhook** | An HTTP callback DocuSign sends to a customer's server when an event occurs (e.g., "all parties signed" → POST to `https://customer.com/callbacks`). Needs idempotent delivery with retries. |
| **Envelope** | DocuSign's term for one signing transaction — one document + one set of recipients + one signing workflow. |

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

| Entity | What it represents |
|---|---|
| **Document** | The file requiring signatures — metadata (title, owner, tenant), status, S3 reference |
| **SigningSession** | One complete signing workflow for a document — tracks all signers, order, overall status |
| **Signer** | A participant in a signing session — their role, signing status, timestamp when they signed |
| **UserCertificate** | User's RSA public key + certificate chain — used to verify their signature is genuine |
| **AuditEvent** | Cryptographically tamper-proof record of every action — signed, viewed, rejected, webhook sent; append-only |

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

**`POST /v1/documents/{id}/sign`** carries the cryptographic payload. `signature_bytes` is the RSA/ECDSA signature of the document hash, base64-encoded. The server verifies it using the public key from `UserCertificate` matching `certificate_serial`. `400 Bad Request` has three named triggers here, all of them "your request is malformed, retrying won't help": `signature_bytes` is not valid base64 or decodes to the wrong length for the algorithm, `certificate_serial` is absent or references no certificate for this user, or the `Idempotency-Key` header is missing. Note what is deliberately *not* a 400 — a well-formed signature that fails cryptographic verification is `422 Unprocessable Entity` in a stricter reading, but this design returns `400` with error code `SIGNATURE_VERIFICATION_FAILED`; the interviewer may push on that, and the defensible answer is that the *body* was syntactically fine, so 422 is more correct. Two more interesting status codes: `403 Forbidden` means it's not your turn (sequential order, the previous signer hasn't signed yet) — you're an authorized user but not authorized for this action right now. `409 Conflict` means you already signed — the idempotency check. The `Idempotency-Key` header is mandatory on this endpoint: if the client POSTs, the server signs the document, but the response is lost in transit, the retry must return the same `{document_id, status}` without signing twice. Implementation: cache response in Redis for 24 hours keyed by `Idempotency-Key`.

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
- At **~550 GB/year** (5.5 TB by year 10, not per year), **sharding by customer_id becomes necessary around year 2**, when the audit table passes ~1 TB and its composite indexes stop fitting in RAM — that's the real trigger, not the raw table size
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

### 💾 Data Store Selection (say this in 45 seconds — Section 4 numbers justify it)

| Store | Used for | Why this, not alternatives | Trade-off |
|---|---|---|---|
| **PostgreSQL** | Document metadata, SigningSession, Signer, UserCertificate, AuditEvent | 35 sig/sec — well within Postgres limits; ACID mandatory (signing state is a court-admissible record); 5.5 TB over 10 years manageable on a sharded cluster | Strong consistency required — no eventual consistency for legal records; shard by `customer_id` needed at year 5 |
| **Redis** | CRL cache (certificate revocation list — the blacklist of invalidated signing certificates; 1-hour TTL), idempotency keys (24h TTL) | CRL lookup happens on every signature verification (35/sec); caching avoids DB hit on the critical signing path | Volatile — if Redis restarts, CRL cache is cold for one lookup cycle; fallback is a Postgres hit, not a wrong answer |
| **S3** | Document binary content (immutable — bytes never change after upload) | Document bytes are immutable post-sign; S3 is purpose-built for immutable blobs at any scale; served via pre-signed URL (app server never touches bytes) | No atomic transaction spanning S3 + Postgres — metadata-first: commit Postgres record (with the S3 key as a field) before uploading bytes; if S3 upload fails, retry; document stays UPLOAD_PENDING until bytes are confirmed in S3 |

> **Switch if:** Scale hits 10M users → shard Postgres by `customer_id`; 7-year audit archive → S3 Glacier (cold storage — retrieval takes 3–12 hours, but $0.004/GB vs $0.023/GB for standard S3); BYOK compliance → replace S3 SSE with KMS.

---

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
   Step 6 — synchronous webhook. The exhausted resource is the API server's
   request thread pool, not the DB. A 200-thread Tomcat pool with a 30s HTTP
   client timeout is fully occupied once 200 requests are parked on a dead
   customer endpoint — at 35 sig/sec that takes under 6 seconds. Because the
   threads only free at the timeout, sustained throughput collapses to
   200 threads / 30s = 6.6 sig/sec, i.e. one unresponsive customer webhook
   endpoint caps the ENTIRE signing service at 19% of its peak rate.
   Observable symptom: P99 on POST /sign jumps from ~150ms to the full 30s
   timeout, then 503s from the load balancer as the accept queue fills —
   for every tenant, not just the one whose endpoint is down.

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

        // Step 2a: Validate the chain up to a trusted root BEFORE trusting the
        // public key. A certificate is only meaningful if our CA issued it:
        // check each issuer signature up to the trust anchor, and check
        // basicConstraints (only a CA cert may sign others) and keyUsage
        // (this leaf must assert digitalSignature / nonRepudiation).
        // Skipping this is how a self-signed cert with subject "CN=John Doe"
        // gets accepted as John.
        certStore.validateChainToTrustedRoot(userCert);

        // Step 2b: Validity window. THIS IS THE SIGNING PATH — the cert must be
        // valid RIGHT NOW, because we are creating a new signature.
        // Do NOT reuse this method to re-verify an old signature: an RSA cert
        // typically lives 1-3 years, so checking `isExpired()` against
        // wall-clock time would declare every signature over 3 years old
        // invalid. Historical verification goes through
        // verifyArchivedSignature() below, which evaluates validity as of the
        // trusted timestamp instead.
        if (userCert.isExpiredAt(Instant.now()) || userCert.isRevoked()) {
            throw new SignatureInvalidException("Certificate expired or revoked");
        }

        // Step 3: Extract the public key from the certificate
        // RSA public key
        PublicKey publicKey = userCert.getPublicKey();
        
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
     * Re-verify a signature that was created years ago (dispute, eDiscovery,
     * audit). This is a DIFFERENT question from "can this user sign now?".
     *
     * The legal question is: was the certificate valid AT THE MOMENT OF
     * SIGNING? So validity is evaluated as of the trusted timestamp, using
     * the revocation data archived at signing time — not today's CRL, which
     * may no longer even list a long-expired certificate.
     */
    public boolean verifyArchivedSignature(SignedDocument doc)
            throws SignatureInvalidException {

        // Step 1: The RFC 3161 timestamp token is the clock we trust — not our
        // DB's signed_at column, which we could have backdated.
        Instant signingTime = timestampAuthority.verifyAndExtractTime(doc.getTimestampToken());

        // Step 2: Chain must have been valid at signingTime, not now.
        Certificate signerCert = doc.getEmbeddedSignerCertificate();
        certStore.validateChainAsOf(signerCert, signingTime);

        // Step 3: Revocation as of signingTime, from the CRL/OCSP response we
        // archived with the signature. "Not revoked today" is not the question;
        // "not revoked when they signed" is.
        if (doc.getArchivedRevocationData().wasRevokedAt(signerCert, signingTime)) {
            throw new SignatureInvalidException("Certificate was revoked before signing");
        }

        // Step 4: Only now does the cryptographic check mean anything.
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(signerCert.getPublicKey());
        verifier.update(doc.getSignedByteRanges());
        return verifier.verify(doc.getSignatureBytes());
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
- **Chain validation is not optional and is not the same as "the cert is in our database."** A certificate is a claim; the issuer's signature over it is the proof. Without walking the chain to a trusted root and checking `basicConstraints` and `keyUsage`, a self-signed certificate whose subject reads `CN=John Doe` verifies perfectly against its own key — you have cryptographically confirmed that whoever made the key signed the document, which is not the same as confirming John did.
- **Two different verification questions — conflating them is the single most common PKI error in this interview.** *Signing path:* "may this user sign right now?" → the certificate must be valid **now**. *Verification path:* "is this three-year-old signature still good?" → the certificate must have been valid **at signing time**, proven by the RFC 3161 timestamp, checked against the revocation data archived alongside the signature. Certificates live 1–3 years; signed contracts live 7+. A verifier that checks `notAfter` against wall-clock time marks every legitimate signature invalid the day the signer's certificate expires. Section 11 item 9 covers the long-term-validity machinery this requires.

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
    tenant_id UUID NOT NULL
);

-- For UI queries: "who has signed?"
CREATE INDEX idx_doc_status      ON signing_sessions (document_id, status);
CREATE INDEX idx_signer_pending  ON signing_sessions (signer_user_id, status);

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
    event_hash CHAR(64)  -- HMAC(previous_event_hash + this_event_data, secret_key)

    -- Append-only enforcement: via trigger below (see prevent_audit_modification)
    -- DO NOT add a CHECK constraint here — Postgres has no native append-only constraint;
    -- the trigger is the correct mechanism.
);

-- Indexes are separate statements in Postgres (inline INDEX is MySQL syntax).
CREATE INDEX idx_doc_audit    ON audit_signature_events (document_id, event_timestamp);
CREATE INDEX idx_signer_audit ON audit_signature_events (signer_user_id, event_timestamp);
CREATE INDEX idx_tenant_audit ON audit_signature_events (tenant_id, event_timestamp);

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
    expires_at TIMESTAMP WITH TIME ZONE  -- e.g., 30 days from creation
);

-- Postgres has no inline INDEX clause inside CREATE TABLE — that is MySQL
-- syntax and fails to parse. Indexes are always separate statements.
CREATE INDEX idx_tenant_created ON documents (tenant_id, created_at DESC);
CREATE INDEX idx_tenant_status  ON documents (tenant_id, status);
CREATE INDEX idx_tenant_creator ON documents (tenant_id, created_by_user_id);

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
    
    -- one entry per signer per doc
    UNIQUE (document_id, signer_user_id)
);

CREATE INDEX idx_doc_order       ON signing_sessions (document_id, signer_order);
CREATE INDEX idx_signer_pending  ON signing_sessions (signer_user_id, status);

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

    -- Archived validation material for long-term validity (Section 11 item 9):
    -- the RFC 3161 token, the chain as presented, and the revocation data as of
    -- signing. Without these, this signature stops being verifiable the day the
    -- signer's certificate expires.
    timestamp_token BYTEA,
    certificate_chain_pem TEXT,
    revocation_snapshot BYTEA
);

CREATE INDEX idx_doc_audit    ON audit_signature_events (document_id, event_timestamp);
CREATE INDEX idx_signer_audit ON audit_signature_events (signer_user_id, event_timestamp);
CREATE INDEX idx_tenant_audit ON audit_signature_events (tenant_id, event_timestamp);

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
    
    UNIQUE (user_id, certificate_serial)
);

CREATE INDEX idx_user_certs ON user_certificates (user_id, expires_at);
CREATE INDEX idx_serial     ON user_certificates (certificate_serial);
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

**Gain:** The audit row stays narrow and fixed-width. A `CHAR(64)` hex hash is 64 bytes; the raw signature is 256 bytes for RSA-2048 and 512 for RSA-4096. At Section 4's 365M signature events/year that is the difference between ~23 GB/year and ~93 GB/year of signature payload inside the hottest, most-indexed table in the system — and because Postgres stores rows in 8 KB pages, wider rows mean fewer rows per page and proportionally more I/O for every audit scan.

**Lose:** The audit table alone cannot re-verify a signature — it can only prove that a signature with this digest was recorded. Re-verification requires the actual signature bytes, which is why they live where they belong: embedded in the signed PDF in S3 (PAdES, Section 11 item 6). The audit row's hash is what binds the two together.

**Failure mode if wrong:** **Technical:** duplicating signature bytes into `audit_signature_events` adds ~93 GB/year of payload to a table that already carries three composite indexes; index bloat, not raw size, is what hurts — every index page holding the tenant/document/timestamp keys gets pushed further apart, `idx_doc_audit` lookups start missing shared_buffers, and the table becomes the dominant cost of every vacuum and every logical-replication slot. **Business impact:** For DocuSign, an audit query is a legal-discovery query with a clock on it. Once `GET /v1/documents/{id}/audit` degrades past a few seconds, a compliance officer responding to a subpoena during a live hearing cannot produce the signing trail on the call and must escalate to a DBA — turning a sub-second lookup into an hours-long discovery response, which is the failure mode of the exact product feature the customer bought. **The deeper point:** the choice is not "hash vs bytes", it's *where the bytes live* — S3 with the signed PDF (immutable, cheap, self-verifying, retrievable for the rare dispute) rather than in the row you read on every audit request.

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

8. **Embedded signing — Fast Path + Safe Path (confirmed probe topic from 2025 candidate reports):**

DocuSign's embedded signing flow is a specific design question interviewers probe from within D1. Most candidates describe only the signing ceremony; interviewers push on what happens after the signature and how you confirm completion programmatically.

**The embedded signing flow:**
1. Sender calls API → creates envelope → receives a `signingUrl` from DocuSign
2. That URL is embedded in the sender's web application (iframe or redirect)
3. Signer completes the signing ceremony inside the embedded experience
4. DocuSign calls the `returnUrl` configured on the envelope with event status as a query param

**The Three Signals (say all three in the interview):**

| Signal | What it is | Trust level |
|---|---|---|
| `returnUrl` callback | UI callback fired by DocuSign with `event=signing_complete` query param | **Fast path — do NOT trust alone** |
| `Envelopes:get` REST call | Backend calls `GET /v2/accounts/{id}/envelopes/{envelopeId}` to read actual status | **Safe path — authoritative** |
| Connect webhook | DocuSign's Connect fires `envelope-completed` to your registered callback URL async | **Audit path — compliance record** |

**Why `returnUrl` alone fails:** The returnUrl is a browser callback — the query params can be spoofed by a malicious signer who knows your endpoint. Example: signer constructs `https://yourapp.com/signing/complete?event=signing_complete&envelopeId=XYZ` without actually signing → your app thinks the document is complete.

**Correct pattern:**
1. `returnUrl` fires → update UI ("signing complete") as a UX fast path
2. Immediately call `Envelopes:get` from backend → confirm `status = "completed"` is the actual DocuSign state
3. Connect webhook fires async → write to audit log (immutable event record)

**Candidates who stop at returnUrl fail the probe.** This is explicitly flagged in 2025 candidate reports as a rejection reason.

**In an interview:** "For embedded signing confirmation I use three signals: returnUrl for fast UI feedback, Envelopes:get REST call for authoritative backend confirmation, and the Connect webhook for audit trail. I never treat the returnUrl alone as sufficient — it can be spoofed, and the network can drop the callback before the backend processes it."

9. **Long-term validity (PAdES-LTV) — why a signature must outlive its own certificate:**

This is the gap that separates candidates who have read about PKI from candidates who have shipped it. Everything above describes how to *create* a valid signature. LTV answers a harder question: **a mortgage signed today must still verify in 2040, but the signer's certificate expires in 2029.** Naively, every DocuSign signature would become "invalid" a year or two after signing — and a signature that cannot be verified is not evidence.

**Why expiry doesn't invalidate the signature (but naive verification says it does):** The maths never stops working — the hash still matches, the key pair is still mathematically bound. What stops working is the *trust evaluation*: a verifier asks "was this certificate valid?", compares `notAfter` to today, sees it's in the past, and returns INVALID. The certificate expiring is not evidence of anything wrong; it's just the CA declining to vouch for the key any longer. The fix is to freeze the evidence at signing time so the verifier can evaluate validity **as of then** rather than as of now.

**The four things you must archive at signing time** (a signature file that carries only the signature bytes is not long-term valid):

| Artifact | Why it must be archived | What breaks without it |
|---|---|---|
| **RFC 3161 timestamp token** | Independent cryptographic proof of *when* the signature existed | You cannot prove the signature predates the certificate's expiry — or predates a later revocation |
| **The full certificate chain** (leaf + intermediate + root) | CAs retire intermediates; the issuing intermediate may not be fetchable in 2040 | Chain cannot be rebuilt → "issuer unknown" → INVALID |
| **Revocation data as of signing** (CRL or signed OCSP response) | CAs stop publishing revocation data for expired certs — an expired cert simply drops off the CRL | You cannot prove the cert wasn't revoked at signing; a verifier must assume the worst |
| **Document timestamps, renewed periodically** (PAdES-LTA) | The TSA's *own* certificate expires, and SHA-256 will eventually weaken | The timestamp that protects the signature becomes unverifiable — the proof chain rots from the outside in |

**The renewal chain — the part almost nobody says out loud:** each new document timestamp covers everything before it, including the previous timestamp. So the archive is a chain of overlapping proofs: signature ← signature timestamp ← document timestamp (2029) ← document timestamp (2039) ← … Each link is added **while the previous link's certificates are still valid**, so validity is carried forward indefinitely without ever re-signing the document. In PAdES this material lives in the PDF's DSS (Document Security Store) dictionary and is appended by incremental update — the original signed byte ranges are never touched.

```
Signed 2026 ────────────────────────────────────────────────────▶ verified 2040

  [doc + Alice's signature]
        │ covered by
        ▼
  [signature timestamp — TSA cert valid 2026-2029]
        │ covered by
        ▼
  [document timestamp added 2028 — TSA cert valid 2028-2031]
        │ covered by
        ▼
  [document timestamp added 2030 — TSA cert valid 2030-2033]   … renewed on

KEY INVARIANT:
   Every link is added BEFORE the previous link's certificate expires.
   Verification never asks "is this cert valid today?" — it asks
   "was it valid at the timestamp of the link that covers it?"
   Break one link (miss a renewal window) and every proof after it
   is unanchored: the signature is still mathematically intact but
   is no longer legally verifiable without expert testimony.
```

**For DocuSign specifically:** this is a product feature with a retention SLA behind it. Envelopes are retained 7+ years for SOC 2 and for statutory record-keeping, and eIDAS defines exactly these levels — **PAdES-B-T** (signature + trusted timestamp) and **PAdES-B-LTA** (long-term with archival timestamps), where LTA is what an EU Qualified Electronic Signature relies on for long-term admissibility. Without archived validation material, a compliance officer pulling a 2019 envelope during litigation gets "signature validity unknown — issuer certificate expired" from any standards-compliant PDF reader, and DocuSign is in the position of asking a court to trust its own database instead of the cryptography it sold.

**In an interview:** "Signature creation is the easy half — the hard half is that the signature has to remain verifiable for 7+ years while the signer's certificate is valid for 2. So at signing time I archive the whole validation set: the RFC 3161 timestamp, the full chain, and the CRL or OCSP response as of that instant, in the PDF's DSS dictionary. Then a background job appends a fresh document timestamp before the previous timestamp's certificate expires. That's PAdES-LTA, and it's the difference between a signature that's evidence and a signature that's just a blob that used to verify."

---

**Your answer should include:**

> "The audit trail is immutable at the database level — a trigger prevents any UPDATE or DELETE. This ensures non-repudiation: John can't deny signing because the audit_signature_events table is tamper-proof. The timestamp is UTC (never local), and we store the certificate serial number, which proves which key was used. If John disputes the signature, we produce: (1) the audit log entry, (2) the certificate chain (proving his cert was issued by our CA), (3) the signature hash (proving it's cryptographically valid), and (4) the context (IP, user-agent, browser). This is legally defensible under ESIGN Act and acceptable in court."

> "For multi-tenant isolation, the signing_sessions and documents tables are filtered by tenant_id on every query. A customer's audit logs are never visible to another customer. For compliance, we store audit logs in the region the customer's account is in (EU customers' logs stay in EU data centers). We never delete audit logs (immutable forever) to satisfy regulatory requirements."

---

## Section 12 — 🔬 Where the Interviewer Will Probe (Minutes 57–60)

### Tier 1 — Surface Probe (Do you understand PKI?)

**Q: "How do you ensure that a signature was actually created by the user, not forged by someone else?"**

> The signature is created by hashing the document with SHA-256 and applying John's private key to that hash — never to the document itself, because RSA can only operate on a block smaller than the modulus, and hash-then-sign is what makes signing a 500 MB contract the same cost as signing a 5 KB one. Only John has access to his private key (it's stored in our HSM, and the design ensures only John's authenticated session can trigger a signing operation). The signature is mathematically bound to that specific key pair — anyone can verify it with John's public key, but only the holder of the private key could have produced it. This is the essence of non-repudiation.
>
> **Say the precise version if they push:** signing is *not* "encrypting the hash with the private key," even though every tutorial says so. RSA signing applies a padding scheme first (PKCS#1 v1.5, or preferably PSS, which adds a random salt so signing the same document twice yields different signatures) and then the private-key operation — and for ECDSA there is no encryption anywhere in the algorithm at all. "Encrypting the hash" is a workable teaching analogy that happens to be true only for textbook RSA; a DocuSign interviewer probing PKI depth will notice which version you reach for.

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

- **Mistake 3:** "Eventual consistency is fine for signing — if it takes a few seconds to show 'fully signed,' that's OK." → **Why it's wrong:** Signing is a legal transaction. If a user signs, the system must immediately show "signed" (strong consistency). Eventual consistency could lead to race conditions: user A thinks doc isn't signed, clicks "sign" again, creating duplicate signatures. → **What to say instead:** "Signing transitions must be strongly consistent. When a user signs, the signing_sessions row must be immediately updated and visible to all other signers and the initiator. I'll use a single Postgres instance with replication (HA) to achieve this, and I'll shard by customer_id around year 2, when the audit table passes ~1 TB at 550 GB/year and its indexes no longer fit in memory."

---

- **Mistake 4:** "The signature is valid because the certificate is valid." → **Why it's wrong:** It ties the lifetime of the evidence to the lifetime of the credential. Signing certificates live 1–3 years; a signed mortgage or lease is evidence for 7–30. A verifier that compares the certificate's `notAfter` to today's date declares every signature invalid the day the signer's certificate expires — and CAs stop publishing revocation data for expired certificates, so you can no longer even prove the certificate wasn't revoked at signing time. Candidates fall into this in code, not in words: `if (cert.isExpired()) return INVALID` on the verification path is the bug. → **What to say instead:** "Validity is evaluated **as of the signing time**, proven by an RFC 3161 timestamp from an independent TSA, not by my own database's `signed_at` column. At signing time I archive the full validation set — timestamp token, complete certificate chain, and the CRL or OCSP response as of that instant — and a background job appends a fresh document timestamp before the previous one's certificate expires. That's PAdES-LTA, and it's why a 2019 envelope still verifies in 2040."

---

## Section 14 — 🧭 DocuSign Dimensions Checklist

| Dimension | Relevant? | How your design addresses it |
|---|---|---|
| **Testability** | ✅ | Signature verification is a pure function of (document bytes, signature bytes, certificate) — testable with a fixture CA: generate a throwaway root, issue a leaf, sign a 5 KB PDF, assert verify passes; then flip one byte of the document and assert it fails. The tests that actually matter are the ones DocuSign's domain requires and that unit tests usually skip: a certificate that expired *after* signing must still verify (fixed clock at the RFC 3161 timestamp, not `Instant.now()`), and a certificate revoked *before* signing must fail. The 60-second CRL refresh window is testable by injecting the Redis `revoked_serials` set directly. The state machine's 5 statuses × 5 transitions is a 25-cell truth table — exhaustively unit-testable with no infrastructure. |
| **Usability** | ✅ | For a DocuSign sender chasing a 3-signer NDA, the API answers "who is blocking this?" in one call: `GET /v1/documents/{id}` returns every signer's status, and `POST /sign` returns `next_signer` so the sender's CRM can send the nudge without polling. Errors are specific enough to act on — `403` means "not your turn in the sequential order" (wait), `409` means "you already signed" (safe to ignore, the retry succeeded). At 50K documents/day, that distinction is the difference between a support ticket and a self-service resolution. |
| **Extensibility** | ✅ | `signing_order` is per-document data, not code, so DocuSign's parallel-signing and sequential-signing products share one code path. The state machine's `VALID_TRANSITIONS` map is where new product features land: adding DocuSign's real-world `EXPIRED → PENDING` re-send and legal-hold freeze is a map entry plus a timer, not a schema migration. `audit_signature_events.action` is an open string, so a new event type (`ENVELOPE_CORRECTED`, `SEALED`) appends to the 1M events/day stream without breaking the `GET /audit` contract that customers' compliance integrations depend on. |
| **Security** | ✅ | The 60-second CRL refresh window is the sharpest security trade-off in the design and should be named as a number: an employee terminated at 14:00:00 can still produce a valid signature until 14:01:00. For DocuSign that window is the difference between a clean offboarding and a signed contract the customer must litigate to void — so the answer is 60s for commercial signing, 10s for regulated financial customers, and OCSP-stapled hard checks for the handful of tenants who pay for it. Layered underneath: chain validation to a trusted root (not just "the cert is in our table"), private keys in a FIPS 140-2 Level 3 HSM, `tenant_id` on every query so customer A never reads customer B's envelopes, and a DB trigger making the audit trail immutable to the application, a bad migration, and a malicious DBA alike. |
| **Availability** | ✅ | The 99.9% SLO (≈9 hours/year) is spent where it matters: signing must stay up, delivery need not be instant. Kafka is what makes that true — a dead customer webhook endpoint parks messages in a topic instead of parking 200 request threads (Section 6 Stage 1's breaking point, where one unresponsive endpoint capped the whole service at 6.6 sig/sec). Redis is a cache, never a dependency: a cold cert cache costs one 10–20ms Postgres read per signature, so at 35 sig/sec a full Redis outage degrades P99 by ~20ms rather than failing signings. What is *not* survivable is HSM loss — no signing at all — so it's active-active across two regions, because "DocuSign is down" during a quarter-end contract rush is the incident that reaches the customer's CEO. |
| **Scalability** | ✅ | 35 sig/sec peak is small; the growth problem is storage, not throughput. At 550 GB/year the audit table passes ~1 TB around year 2, and the trigger for sharding by `customer_id` is when its three composite indexes stop fitting in RAM — not the raw table size. Sharding by customer is the natural cut because every query is already tenant-scoped and it doubles as GDPR data residency (EU tenants on EU shards). The audit read path stays O(log N + K) via `idx_doc_audit (document_id, event_timestamp)`, which is what keeps a 7-year eDiscovery pull on one envelope sub-second even as the global table reaches 5.5 TB by year 10. |
| **Observability & Traceability** | ✅ | Two distinct needs, deliberately not conflated. *Operational:* `X-Request-ID` propagated through gateway → signature service → Kafka → webhook service, so "why did this envelope's webhook arrive 40 minutes late" is one trace query. *Legal:* the audit trail is the product, not telemetry — it is queried by compliance officers under subpoena, so it lives in Postgres with an immutability trigger and a 7-year retention, never in a log aggregator with a 30-day window. The alerts that matter are domain-specific: signature verification failure rate above baseline (a tenant's integration is signing with a stale certificate), CRL refresh age > 120s (the revocation window has silently doubled), and any `UPDATE`/`DELETE` attempt caught by the audit trigger (which should be exactly zero, forever — a single occurrence is a security incident, not a bug). |

---

## Section 15 — 🧾 TL;DR Answer Summary

> "A digital signature system at DocuSign scale requires three critical pieces: (1) PKI infrastructure to manage user certificates + key pairs (we use a hosted CA with HSM-secured private keys); (2) signature verification via SHA-256 hashing + RSA to ensure non-repudiation; (3) an immutable append-only audit trail with database-level constraints to prove 'John signed on June 24 at 3:14 PM UTC from 192.168.1.1' — this is the legal proof if disputes arise. The architecture is a Postgres cluster sharded by customer_id (for multi-tenancy + compliance), with a state machine for sequential/parallel signing workflows, webhooks for async notification, and strong consistency on the signing_sessions table (users can't race-condition and sign twice). Trade-offs: (1) we store signature hashes (not bytes) to keep the audit table queryable, (2) we hold private keys in HSM (simplifies UX, but requires strict security practices), (3) we never delete audit logs (immutable forever for legal compliance). The system addresses all 7 DocuSign dimensions: security (PKI + immutability), availability (HA Postgres), scalability (sharding by customer_id), observability (audit trail queries), and usability (clear API + state transitions)."

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Aug 2026 | **Audit pass — long-term validity gap closed + PKI correctness fixes.** (1) **The big one: `verifySignature()` checked `userCert.isExpired()` against wall-clock time on the verification path.** Since signing certificates live 1–3 years and signed contracts are evidence for 7+, that code marks every legitimate signature invalid the day the signer's cert expires. Split into two paths with the distinction stated explicitly: signing-time validation (cert must be valid *now*) vs `verifyArchivedSignature()` (validity evaluated *as of the RFC 3161 timestamp*, against the revocation data archived at signing). (2) **Certificate chain validation added** — the original code looked the cert up and read its public key without ever verifying the issuer chain, `basicConstraints`, or `keyUsage`; a self-signed cert with subject `CN=John Doe` would have verified perfectly. (3) **Section 11 item 9 added — long-term validity (PAdES-LTV/LTA):** the four artifacts that must be archived at signing time (timestamp token, full chain, revocation snapshot, renewed document timestamps), the overlapping renewal chain with a Picture + Invariant diagram, the DSS dictionary, and the eIDAS B-T / B-LTA levels. Corresponding columns added to `audit_signature_events`. (4) **Trade-off 1 math corrected:** claimed "1B events/year × 256 bytes = 256 TB" (off by 1000× — it's ~93 GB) and "550 GB/year vs 5.5 TB/year", which contradicted Section 13's own correct figure; rewritten around the real cost (row width, index bloat, page density) and the real answer (bytes belong in the PAdES PDF in S3, not in the hot audit table). (5) **Section 4 + Mistake 3 fixed:** "at 5.5 TB/year" was the 10-year total, not annual; the sharding trigger is now ~1 TB around year 2 when the audit indexes stop fitting in RAM. (6) **DDL fixed:** `documents` and Deep Dive 2's `audit_signature_events` each declared `tenant_id` twice (would not create), and every table used MySQL's inline `INDEX ... (...)` clause inside `CREATE TABLE`, which does not parse on Postgres — all converted to `CREATE INDEX`. (7) **Stage 1 breaking point 1 quantified:** thread-pool exhaustion math (200 threads / 30s timeout = one dead customer endpoint caps the entire service at 6.6 sig/sec, 19% of peak) replacing "response is blocked or times out". (8) **Named the `400` triggers** on `POST /sign` and noted the 422-vs-400 defence. (9) **Tier-1 probe made cryptographically precise** — added the hash-then-sign rationale and the correction that RSA signing is not "encrypting the hash" (padding: PKCS#1 v1.5 / PSS; ECDSA involves no encryption at all). (10) **Mistake 4 added** (signature validity tied to certificate validity). (11) **Section 14 rewritten** — all 7 cells were boilerplate with no numbers; each now names a figure from Section 4 (35 sig/sec, 1M events/day, 550 GB/year, 60s CRL window, 200-thread pool) and a concrete DocuSign scenario (terminated-employee revocation window, quarter-end signing rush, 7-year eDiscovery pull, subpoena response). |
| June 24, 2026 | **D1-digital-signature.md created.** Full 15-section solution framework for Mixed A+B interview type. Covers: PKI infrastructure (cert management, signature verification), audit trail immutability (append-only + DB triggers), multi-party signing state machine (sequential + parallel), GDPR/compliance angles, and DocuSign-specific depth (non-repudiation guarantees). Scale: 1M users, 35 sig/sec peak. Prerequisite: `13-security-pki.md`. |
| Jul 4, 2026 | **4 new Q&As added to Section 12.** (1) **Certificate revocation check latency** — OCSP is 50–200ms (unusable on critical path); CRL cached as Redis HashSet, refreshed every 60s; O(1) `SISMEMBER` check under 1ms; 60-second revocation window is explicit trade-off; (2) **Temporal as alternative to custom state machine** — custom FSM works for MVP at 35 sig/sec; Temporal handles expiry timers, escalation, conditional routing, and legal hold natively with full execution history; migration decision: Temporal at DocuSign's actual workflow complexity; (3) **UTC timestamp strategy for multi-jurisdiction legal compliance** — `TIMESTAMP WITH TIME ZONE` in Postgres always stores UTC; log user's declared timezone alongside UTC; court-presentable via UTC + timezone derivation. |
| Jul 5, 2026 | **Section 6 restructured into 2-stage progressive HLD.** Stage 1 (monolith + direct DB) — identifies two breaking points: sync webhook couples external system availability to signing success; uncached cert lookup hits DB on every sign request. Stage 2 (service split + Redis + Kafka) — cert cache in Redis (sub-ms CRL check + cert PEM TTL); Kafka decouples webhook delivery (signing returns immediately after publish); PKI infrastructure separated. Four decision tables added: key management (Hosted CA ✅ vs BYOK ❌ vs Hybrid ⚠️), audit trail immutability (DB trigger ✅ vs application-only ❌ vs blockchain ❌), signing workflow (state machine ✅ vs set ❌ vs event-sourced ⚠️), webhook delivery (Kafka ✅ vs Redis pub/sub ❌ vs sync HTTP ❌). Verdict alignment verified: all Section 6 table verdicts match Section 7 deep dive choices (Hosted CA ✅, DB trigger ✅, state machine ✅). |
| Jul 6, 2026 | **🔑 Technology Quick Reference table added.** 15-row glossary covering asymmetric encryption, private/public key, RSA, PKI, X.509 certificate, CA, HSM, non-repudiation, BYOK, OCSP, CRL, audit trail, webhook, envelope — inserted before Section 0. |
| Jul 9, 2026 | **Section 11 addition.** Embedded signing Fast Path + Safe Path pattern added: returnUrl (fast UI path — do NOT trust alone), Envelopes:get REST call (safe authoritative confirmation), Connect webhook (async audit record). Explains why returnUrl-only is a probe failure reason. Three-signal pattern with trust levels table and spoofing attack example. |
| Jul 5, 2026 | **Section 10 business impact pass.** Added explicit **Business impact:** label to all 3 trade-offs — compliance officer timing out during live legal hearing due to unindexed audit log full table scan, HSM compromise making every historical DocuSign signature legally contestable at $1.5M/year key-loss support cost, non-repudiation destroyed when audit record digest doesn't match signature invalidating a court-submitted contract. |
