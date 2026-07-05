# D2 — Design a Document Storage & Retrieval Service

> **Read `solution-notes-standards.md` FIRST.** This file is a 60-minute interview answer framework.
> Its structure mirrors DELIVERY-RECIPE: clarify (5 min) → estimate (5 min) → HLD (15 min) → deep dives (15 min) → trade-offs (8 min) → Q&A (7 min).
> **Say this out loud** before your interview — don't just read it.

---

## 🎯 What Is This System?

**In plain English:** A document storage service accepts file uploads (PDFs, contracts, images, any binary), stores them durably with versioning and access control, and retrieves them on demand — with encryption at rest, compliance-friendly retention policies, and audit logging of every access.

**Real-world examples:**

| System / Company | What they built |
|---|---|
| **Google Drive** | 1B+ users; versioned file storage with real-time collaboration |
| **Dropbox** | Consumer and enterprise file sync + cloud storage |
| **Box** | Enterprise content management with granular permission controls |
| **AWS S3** | The infrastructure most of the above are built on |
| **SharePoint / OneDrive** | Microsoft's enterprise document management, integrated with Office 365 |
| **DocuSign Envelope storage** | After signing completes, the sealed PDF lives here — 7-year retention |

**Core user journey:** User uploads a 5MB signed contract → gets a stable, permanent URL → can retrieve version 1 (original) and version 2 (countersigned) at any time → only authorized team members can access it → document is retained for 7 years to satisfy SOC 2 and GDPR compliance requirements.

**Why it's hard to build at scale:** Binary blobs cannot live in a relational DB — they go in object storage (S3), while only metadata lives in Postgres; access control must be per-document, not just per-folder; versioning must be immutable (you cannot alter a signed legal document); and compliance requires that you can prove nobody accessed a document except authorized parties.

---

## Section 0 — Question Identity Card

| | |
|---|---|
| **Question** | **Design a Document Storage & Retrieval Service** (high availability, with versioning and audit trail) |
| **Interview Type** | **Type B — Product Architecture** (focus: data model, API design, storage patterns, compliance) |
| **Confirmed or Likely** | 🔶 Likely (Glassdoor 2025, DesignGurus DocuSign guide) |
| **Concept notes prerequisite** | `14-document-blob-storage.md` (metadata DB + blob store pattern, versioning, pre-signed URLs), `03-caching.md` (read-heavy caching strategy) |
| **DocuSign-specific angle** | **Core to DocuSign's product.** Answers must cover: document versioning (track all edits), immutable audit trail (legal compliance), soft deletes (GDPR + legal hold), encryption at rest (SOC 2), multi-region compliance (GDPR data residency), and pre-signed URLs (secure download without exposing credentials). |
| **Time budget** | 5 min clarify → 5 min estimate → 15 min HLD → 15 min deep dive → 8 min trade-offs → 7 min Q&A buffer |

---

## Section 1 — 🚀 The One-Sentence Opener

> "Before I design, let me clarify: are we storing user-uploaded documents or system-generated documents? How many versions per document do we need to track? And what are the compliance requirements (GDPR, SOC 2, legal holds)?"

Then pivot to Section 2 (clarifying questions).

---

## Section 2 — 🔍 Clarifying Questions Script (Minutes 0–5)

**Q: "Are documents immutable after upload, or can they be edited/overwritten?"**
- Why ask: Immutable → simpler (never delete, only new versions); mutable → need conflict detection + rollback capability
- If immutable → versioning strategy is straightforward (one S3 key per version)
- If mutable → need row-level versioning + eventual consistency handling

**Q: "Do we need to track who accessed/downloaded a document and when (audit trail for compliance)?"**
- Why ask: Audit logging adds overhead; compliance (GDPR, SOC 2, e-signature law) often requires it
- If yes → insert into audit_log on every download; index by document_id + timestamp
- If no → simpler (no logging DB writes on reads)

**Q: "What's the document size distribution — are we storing 1 MB PDFs or 100 MB video files?"**
- Why ask: S3 is designed for arbitrary sizes, but download strategy changes
- Small (< 10 MB) → inline download via presigned URL
- Large (> 100 MB) → multipart upload on write, range requests on read, resumable downloads

**Q: "Do users need to permanently delete documents (GDPR right-to-deletion), or only soft-delete for audit trail?"**
- Why ask: Permanent deletion breaks audit trail (legal liability); soft delete preserves it
- If permanent → set status = DELETED + scheduled job to purge S3 after 7 years (legal retention)
- If soft delete only → keep all data forever, set deleted_at timestamp, filter in queries

**Q: "What's the scale — how many documents, how many concurrent reads/writes?"**
- Why ask: Single-region S3 + Postgres is fine at 10M docs; at 1B docs, need sharding + caching

**Q: "Do documents need to be encrypted at rest, and who holds the keys (S3-managed vs customer-managed)?"**
- Why ask: Encryption adds compliance credibility; customer-managed keys add complexity (key rotation, versioning)
- If S3-managed (SSE-S3) → simpler, still HIPAA/PCI-DSS compliant
- If customer-managed (AWS KMS) → customer controls key lifecycle; higher compliance bar

---

## Section 3 — 📋 Requirements (Functional + Non-Functional)

**Functional Requirements:**
- Users should be able to **upload documents** (PDF, image, binary, etc.)
- Users should be able to **retrieve documents** by ID
- System should support **document versioning** (track all edits, revert to older versions)
- System should **prevent unauthorized access** (only document owner and authorized users can download)
- Users should be able to **soft-delete documents** (mark deleted, preserve for audit trail)
- System should provide **audit trail** (who accessed/downloaded, when, from where)
- Out of scope: Full-text search on document content, OCR, document collaboration (multiple simultaneous editors)

**Non-Functional Requirements:**
- Scale: 10M users, 50M documents (avg 5 docs/user), 100M reads/day (~1.16K reads/sec), 10K document uploads/day (~0.12 writes/sec)
- Document size: avg 5 MB; range 100 KB to 500 MB
- Total storage: 50M × 5 MB = 250 TB
- Latency: P99 download < 2s (via pre-signed URL, handled by S3, not app server)
- Availability: 99.9% SLO (9 hours downtime/year)
- Consistency: **Strong consistency required** — document metadata updates are immediately visible (no eventual consistency)
- Durability: **Immutable audit trail** (downloads logged forever; no purge except via legal hold release)
- Compliance: SOC 2, GDPR (data residency by user region), e-signature compliance (tamper-proof audit trail)
- Encryption: at rest (SSE-S3 — Server-Side Encryption with S3-managed keys: AWS handles key generation and rotation automatically, sufficient for most compliance requirements; or KMS — AWS Key Management Service: you control the key lifecycle and can audit every encryption/decryption operation, meeting stricter compliance bars like BYOK), in transit (HTTPS)

---

## Section 3.5 — 🗂️ Core Entities (~2 minutes)

> **Say this out loud:** "Before I sketch the architecture, let me name the key data objects the system manages."

| Entity | What it represents | Storage |
|---|---|---|
| **Document** | Metadata record for a file — owner, name, type, size, S3 key, current version, soft-delete flag | PostgreSQL |
| **DocumentVersion** | Immutable snapshot of each edit — S3 key, checksum, uploader, created_at; never updated | PostgreSQL |
| **DocumentAccess** | Per-document ACL entry — which user has which permissions (read/write/delete) | PostgreSQL |
| **LegalHold** | A compliance lock on a document — prevents deletion even if owner requests it | PostgreSQL |
| **AuditLog** | Append-only record of every operation — upload, download, delete, permission change | PostgreSQL (append-only) |

**Key relationships:**
- A `Document` has many `DocumentVersions`; the latest version is the "current" one (one-to-many)
- A `Document` has many `DocumentAccess` rows — one per authorized user (one-to-many ACL)
- A `LegalHold` blocks `Document` deletion regardless of owner permissions
- `AuditLog` rows are never deleted — even if the document is soft-deleted, its audit trail stays
- **Important split:** file *bytes* live in S3; `Document` metadata lives in PostgreSQL — the two are joined by `s3_key`

---

## Section 8 — 🌐 API Design (Minutes 8–13) ⭐ Type B Primary Deliverable

> **Why here, not later:** For Type B (Product Architecture), the API contract is the primary deliverable. Naming the nouns in Section 3.5 (Document, DocumentVersion, LegalHold, AuditLog) directly unlocks the URL paths below.

### 🧠 How to Derive These Endpoints

Five FRs, five endpoints — but the *how* of each one is where the interview happens.

"Users upload documents" → CREATE → resource is a document → `POST /v1/documents` with multipart body. The FR says documents can be large (contracts, signed PDFs) → you can't send a 500MB file as a JSON body. Two options: multipart upload inline, or return a pre-signed S3 upload URL and let the client PUT directly to S3. The latter keeps your service stateless. Either is defensible — say which and why.

"Users retrieve documents" → READ → but what does "retrieve" mean for a binary file? You don't serve bytes through your API server — that would turn your API tier into a bandwidth-burning blob proxy. The correct endpoint is `GET /v1/documents/{id}/download` which returns a `302 Redirect` to a pre-signed S3 URL (15-minute expiry). Client talks to S3 directly; your service never touches the bytes. The `/download` path makes the intent explicit — `GET /v1/documents/{id}` would return metadata, not the file.

"Track all edits / versioning" → `GET /v1/documents/{id}/versions`. It's a sub-resource of the document. Each version is an immutable record. The endpoint returns the ordered list — the caller decides which version to download.

"Legal compliance — audit every access" → `GET /v1/documents/{id}/audit`. This endpoint exists because someone will eventually ask in court "who accessed this document?" The audit log is append-only; this endpoint is read-only. The fact that it exists as a first-class endpoint (not buried in a generic search API) signals that you take compliance seriously.

"Soft delete with legal hold blocking" → `DELETE /v1/documents/{id}`. The response code story: `200` with body `{status: "DELETED"}` when successful. `423 Locked` when a legal hold is active — `423` is the right code (not `403`). `403` means you don't have permission; `423` means you have permission but the resource is locked. Most candidates return `400` for everything and collapse the signal.

Validation check: every FR maps to an endpoint. The "compliance → 7-year retention" FR has no endpoint — retention is enforced by the storage layer (S3 lifecycle policy), not a REST call. Correct to have no endpoint.

### Core Endpoints

| Method | Path | Auth | Request | Response | Status Codes |
|---|---|---|---|---|---|
| POST | `/v1/documents` | JWT Bearer | `{title, file (multipart), type}` | `{document_id, version: 1, s3_key, status}` | 201, 400, 409 |
| GET | `/v1/documents/{id}/download` | JWT Bearer | — | `302` redirect to pre-signed S3 URL | 302, 403, 404 |
| GET | `/v1/documents/{id}/versions` | JWT Bearer | — | `{versions: [{version, created_at, created_by, size}], current_version}` | 200, 403, 404 |
| DELETE | `/v1/documents/{id}` | JWT Bearer | — | `{document_id, status: "DELETED"}` | 200, 403, 404, 423 |
| GET | `/v1/documents/{id}/audit` | JWT Bearer | — | `{audit_logs: [{action, timestamp, user_id, reason}]}` | 200, 403, 404 |

### 🔍 Endpoint Stories

**`POST /v1/documents`** is the upload entry point — but the interesting question is where the bytes go. If you accept multipart binary in the request body, your API server becomes a byte-pumping pipe: every upload thread holds a connection open for the duration of the upload. At 10K uploads/day that's manageable; at 10M/day it isn't. The better pattern: POST returns a pre-signed S3 upload URL; the client uploads directly to S3; S3 triggers a Lambda or webhook to confirm the upload completed. The `document_id` is created in Postgres before the bytes land — so the metadata exists immediately and the async confirmation fills in `s3_key`. Most candidates describe the simple multipart approach; mentioning the pre-signed URL pattern is the differentiator.

**`GET /v1/documents/{id}/download`** returns a `302 Redirect`, not bytes. This surprises most candidates who expect `200 OK` with a file body. The redirect to a 15-minute pre-signed S3 URL is the standard pattern: your API service is stateless, the CDN caches the file at the edge, and the URL auto-expires so sharing it doesn't grant permanent access. Every access that generates a pre-signed URL is logged in `audit_logs` — the act of generating the URL counts as a "download attempt," even if the client never follows the redirect.

**`GET /v1/documents/{id}/versions`** is the versioning audit trail from the client's perspective. The probe: "How do you retrieve a specific version for download?" You add `?version=2` to the download endpoint — `GET /v1/documents/{id}/download?version=2` — returning a pre-signed URL to the version-specific S3 key. The versions list endpoint tells the client what versions exist; the download endpoint serves them.

**`DELETE /v1/documents/{id}`** is a soft delete — the `documents` table gets `deleted_at = NOW()`, the S3 object is not deleted (retained per policy). The `423 Locked` status code is what the interviewer will probe: "Why 423 and not 403?" Because the caller has deletion permission — they own the document. The lock is on the resource, not on the caller's access. `423 Locked` is an HTTP-spec status code specifically for "temporarily locked" — WebDAV introduced it, but it's valid for any resource lock. DocuSign-specific: legal holds are a real compliance primitive; being specific about `423` shows you've thought about compliance workflows, not just CRUD operations.

**`GET /v1/documents/{id}/audit`** is the compliance endpoint. The probe: "Should this be paginated?" Yes — a document with 100,000 access events over 7 years would return an enormous payload. Add `?cursor=` and `?from=&to=` date filters. The audit log is append-only and immutable: rows are never updated or deleted, even if the document itself is soft-deleted. The audit trail outlives the document.

---

## Section 4 — 🔢 Scale Estimation (Minutes 5–10)

**Traffic:**
- DAU: 10M users
- Documents: 50M total (avg 5 per user)
- Reads/day: 100M document downloads = ~1.16K reads/sec baseline, 3.5K peak
- Writes/day: 10K new documents = 0.12 writes/sec baseline, 0.3 peak
- Document size: avg 5 MB

**Storage:**
- Total: 50M docs × 5 MB = 250 TB
- Versions: assume avg 2.5 versions per document → 125M document objects = 625 TB with history
- Metadata DB: 50M documents × 1 KB metadata = 50 GB (fits in single Postgres instance)
- Audit logs: 100M reads/day × 200 bytes/log = 20 TB/year (archive after 1 year; S3 Glacier for compliance — AWS cold storage tier: ~$0.004/GB/month vs $0.023/GB for S3 Standard, but retrieval takes 3-12 hours; ideal for compliance archives you almost never need to read)

**Bandwidth:**
- Inbound (uploads): 10K docs/day × 5 MB = 50 GB/day = 0.6 MB/sec
- Outbound (downloads): 100M reads/day × 5 MB = 500 TB/day = 5.8 GB/sec (!!!all via S3 pre-signed URLs, app server NOT involved)

**Key conclusions:**
- At 0.12 writes/sec, single Postgres instance handles easily (typical: 1K writes/sec capacity)
- At 1.16K reads/sec, DB reads would bottleneck — but we cache in Redis (metadata + recent versions)
- S3 stores 250 TB comfortably (unlimited capacity); costs ~$5-6K/month
- Audit logs at 20 TB/year require archival to Glacier (cheaper: $0.004/GB/month vs $0.023/GB/month for S3 standard)
- **Critical insight:** Downloads happen via S3 pre-signed URLs (direct S3 → client), NOT through app server. App server is only metadata + presigned URL generation.

---

## Section 5 — 🔄 Requirements Variation Table ⭐ Key Differentiator

| Requirement | Small scale (1K docs) | Large scale (1B docs) | Impact on design |
|---|---|---|---|
| **Read pattern** | Few reads; DB query OK | 100M reads/day; must cache | Single DB → Redis cache (metadata + doc URLs) + S3 presigned URL generation |
| **Versioning** | No versioning needed | Full version history required | Simple column → one row per version; immutable S3 keys per version |
| **Audit trail** | Nice-to-have | GDPR/SOC 2 compliance required | Lightweight logging → append-only audit_log table + S3 Glacier archive |
| **Compliance** | None | Multi-region (GDPR), encryption, legal hold | Single-region S3 → multi-region replication; no encryption → KMS; simple delete → legal hold workflow |
| **Access control** | All-or-nothing (owner or not) | Fine-grained (owner, editor, viewer roles) | Simple boolean → RBAC table; DB filter on every query |
| **Document size** | < 1 MB (fits in memory) | 100 MB+ (multipart upload/download) | Streaming upload/download; range requests on read |

---

## Section 6 — 🏗️ High-Level Architecture (Minutes 10–25)

### Stage 1 — App Server + Local/NFS Disk (Baseline)

> Start here. Works for small scale (< 100K documents). Two breaking points: (1) local disk doesn't survive node failure — no HA; (2) downloads routed through the app server — 5.8 GB/sec bandwidth is impossible for a server farm.

```
── Stage 1: App Server + Disk ────────────────────────────────────────

 ┌────────────┐  POST /v1/documents  ┌────────────────────────────────┐
 │   Client   │─────────────────────▶│          API Server            │
 └────────────┘                      │  1. Write file to /var/data/   │
       ▲  302 → download URL         │  2. INSERT metadata to Postgres │
       │                             │  3. Return doc_id              │
 ┌────────────┐  GET /download/{id}  └──────────────┬─────────────────┘
 │   Client   │◀────────────────────                 │ reads
 └────────────┘                      ┌──────────────▼─────────────────┐
  (streams bytes                     │           PostgreSQL            │
   through server)                   │  documents (metadata + s3_key) │
                                     │  audit_logs (append-only)      │
                                     └────────────────────────────────┘
                                     ┌────────────────────────────────┐
                                     │     /var/data/ (local disk)    │
                                     │  docs/owner_id/doc_id/v1.pdf   │
                                     └────────────────────────────────┘

BREAKING POINT 1:
   Local disk: if the app server node fails, all documents are lost.
   No replication, no HA. At 250 TB, local disk is not even viable.

BREAKING POINT 2:
   Downloads stream through the app server: 100M downloads/day × 5 MB avg
   = 5.8 GB/sec. No server farm can handle this bandwidth.
   App server is a bandwidth bottleneck that kills the download path.
```

**WHICH content storage?**

| Option | Strength | Weakness | Verdict |
|---|---|---|---|
| Local disk / NFS | Zero setup cost; simple streaming | Single point of failure; no HA; can't reach 250 TB; no geographic redundancy | ❌ Not viable for 250 TB with 99.9% SLA |
| On-prem SAN / NAS | High-throughput; centralized | Expensive ($100K+); ops burden; still single datacenter risk | ⚠️ Works in enterprise data centers; not cloud-native |
| Amazon S3 (object storage) | Unlimited capacity; 99.999999999% durability (11 nines); $6K/month for 250 TB; multi-AZ; immutable via unique keys | Eventual consistency on overwrite (not an issue — we never overwrite, only add new keys) | ✅ Best — unlimited scale, built-in HA, cheap, immutable |

> 📖 Full: `SystemDesignConcepts/Production-Grade/System-Design-Patterns/14-document-blob-storage.md`

---

### Stage 2 — S3 + Metadata DB + Pre-Signed URLs (Production)

> **Why we evolve:** Stage 1 breaks at both storage durability and download bandwidth. Fix: move content to S3 (unlimited, durable), generate pre-signed URLs (client downloads directly from S3 — bypasses app server entirely). Redis caches hot metadata reads at 3.5K QPS.

```
── Stage 2: Production ───────────────────────────────────────────────

── Upload Flow ──────────────────────────────────────────────────────

 ┌────────────┐  POST /v1/documents  ┌────────────────────────────────┐
 │   Client   │─────────────────────▶│          API Server            │
 └────────────┘                      │  1. PUT to S3 key:             │
       ▲  {doc_id, version: 1}       │     docs/{owner}/{doc_id}/v1.pdf│
       └─────────────────────────────│  2. INSERT metadata to Postgres │
                                     │  3. Return doc_id              │
                                     └──────────────┬─────────────────┘
                                                    │ writes
                                     ┌──────────────▼─────────────────┐
                                     │           PostgreSQL            │
                                     │  documents  (metadata, s3_key) │
                                     │  audit_logs (append-only)      │
                                     │  legal_holds (compliance)      │
                                     └──────────────┬─────────────────┘
                                                    │ S3 PUT
                                     ┌──────────────▼─────────────────┐
                                     │           Amazon S3             │
                                     │  docs/{owner}/{doc_id}/v1.pdf  │
                                     │  docs/{owner}/{doc_id}/v2.pdf  │
                                     │  (immutable; never overwritten) │
                                     └────────────────────────────────┘

── Download Flow ────────────────────────────────────────────────────

 ┌────────────┐  GET /download/{id}  ┌────────────────────────────────┐
 │   Client   │─────────────────────▶│          API Server            │
 └────────────┘                      │  1. GET doc:{id} from Redis    │
       ▲  302 → presigned URL        │     miss → SELECT from Postgres │
       │  (15-min expiry)            │  2. Check ACL (owner or viewer) │
       │                             │  3. Generate presigned URL     │
       └─────────────────────────────│  4. INSERT audit_logs          │
                                     └──────────────┬─────────────────┘
                                                    │ cache-aside
                                     ┌──────────────▼─────────────────┐
                                     │       Redis (1hr TTL)          │
                                     │  doc:{id} → metadata JSON      │
                                     └────────────────────────────────┘

 ┌────────────┐  follows 302 redirect
 │   Client   │──────────────────────────────────────────────────────▶ Amazon S3
 └────────────┘  (downloads bytes DIRECTLY from S3 — app server not involved)

── Soft Delete Flow ─────────────────────────────────────────────────

 DELETE /v1/documents/{id}
 → Check legal_holds: if active hold → 423 Locked
 → UPDATE documents SET status = 'DELETED', deleted_at = NOW()
 → INSERT audit_logs (append-only, immutable)
 → S3 object: NEVER DELETED (preserves audit trail + legal hold)

KEY INVARIANT:
   Metadata (Postgres) is source of truth for queries: who owns? version? deleted?
   Content (S3) is source of truth for bytes: immutable, versioned by key.
   Pre-signed URLs bypass the app server — S3 handles 5.8 GB/sec download bandwidth.
   Soft deletes preserve audit trail while hiding docs from user queries.
   S3 objects are NEVER deleted — legal holds and audit compliance require this.
```

**WHICH download strategy?**

| Option | Strength | Weakness | Verdict |
|---|---|---|---|
| App server proxies download (reads S3, streams to client) | Full control; can add streaming logic | Bandwidth bottleneck: 100M downloads/day = 5.8 GB/sec through app servers; unscalable | ❌ Bandwidth kills the server farm |
| CDN (CloudFront in front of S3) | Low latency via edge; caches popular docs globally | Cached URLs can't have per-user access control; public cache vs private content | ⚠️ Good for public/shared docs; not for private documents |
| Pre-signed S3 URLs (app signs URL, client downloads directly) | S3 handles all bandwidth; 15-min expiry limits leak window; access control enforced at generation time | URL can be shared (mitigated by short expiry); expired URL returns 403 mid-download | ✅ Best — scales to any bandwidth, per-user access control |

> 📖 Full: `SystemDesignConcepts/Production-Grade/System-Design-Patterns/14-document-blob-storage.md`

**WHICH versioning approach?**

| Option | Strength | Weakness | Verdict |
|---|---|---|---|
| Overwrite single row (update s3_key on each version) | Simple schema; small table | Version history lost on each update; can't revert; audit trail incomplete | ❌ Loses history — legally indefensible |
| One row per version (immutable rows, `is_latest` flag) | Full history preserved; revert = query; audit trail is natural | More rows (50M docs × 2.5 versions = 125M rows); needs `is_latest` for current-version queries | ✅ Best — history preserved, queries simple with index on (id, is_latest) |
| Hybrid (current row + separate `version_history` table) | Fast current-version queries + full history | Two tables to keep in sync; INSERT + UPDATE per version — atomic transaction required | ⚠️ Viable; more complex to maintain consistency |

> 📖 Full: `SystemDesignConcepts/Production-Grade/System-Design-Patterns/14-document-blob-storage.md`

---

### Data Flow Walkthrough (say this out loud)

**Flow 1 — Upload:**
1. Client `POST /v1/documents`. API generates S3 key `docs/{owner_id}/{doc_id}/v1.pdf`, PUTs to S3.
2. S3 returns ETag (entity tag — a checksum computed for the uploaded object; compare against local checksum to confirm no bit-rot in transit).
3. API INSERTs metadata row: `(doc_id, owner_id, s3_key, version=1, is_latest=TRUE, status=ACTIVE)`. Returns `{doc_id, version: 1}`.

**Flow 2 — Download (pre-signed URL):**
1. Client `GET /download/{id}`. API checks Redis (`doc:{id}`, 1hr TTL) → cache miss → Postgres.
2. ACL check (owner or viewer role). Generate S3 presigned URL (15-min expiry). INSERT `audit_logs`.
3. Return 302 with `Location: {presigned_url}`. Client follows redirect → downloads directly from S3 (app server not in data path).

**Flow 3 — New version:**
1. API generates new S3 key `v2.pdf`, PUTs to S3.
2. Atomic transaction: `UPDATE documents SET is_latest=FALSE WHERE id=? AND is_latest=TRUE`; `INSERT (v2, is_latest=TRUE)`; `INSERT version_history`. All or nothing.

**Flow 4 — Soft delete:**
1. API checks `legal_holds` — if active hold → 423 Locked (compliance block).
2. `UPDATE documents SET status='DELETED', deleted_at=NOW()`. `INSERT audit_logs`. S3 object untouched.
3. Future queries filter `WHERE status='ACTIVE'`; deleted docs invisible to owner, queryable by legal team.

---

## Section 7 — 🔬 Core Component Deep Dives (Minutes 25–40)

### Deep Dive 1: Metadata Schema + Versioning Strategy

**Why this is the most critical component:**
The metadata table is the source of truth for everything queryable. A bad schema forces inefficient queries or makes versioning a nightmare. DocuSign's product **requires** accurate version tracking (user must be able to revert to an older document). The schema design determines whether queries are O(1) or O(N).

**Options considered:**

| Option | Pros | Cons |
|---|---|---|
| **Option A: Single row per document (overwrite on update)** | Simple; small table | Lost version history; can't revert; audit trail incomplete |
| **Option B: One row per version (immutable history)** | Full history preserved; audit trail built-in; revert is a query | More rows; larger table; need `is_latest` flag for current version queries |
| **Option C: Hybrid (current row + separate version_history table)** | Fast current-version queries + full history + audit trail | Two tables to keep in sync; INSERT + UPDATE per version |

**Decision: Option B (one row per version).**

Because it's the cleanest: full history is naturally preserved (immutable rows), queries are simple (WHERE id = ? AND is_latest = TRUE), and versioning is atomic (one INSERT on new version). At 50M documents × 2.5 versions/doc = 125M rows, it's manageable (Postgres can handle 100M+ row tables easily with proper indexing).

**Implementation sketch:**

```sql
-- Core metadata table: one row per version
CREATE TABLE documents (
    id              UUID          NOT NULL,             -- document ID (stable across versions)
    owner_id        UUID          NOT NULL,
    title           VARCHAR(500)  NOT NULL,
    s3_key          VARCHAR(1000) NOT NULL UNIQUE,     -- pointer to S3 object (unique per version)
    s3_bucket       VARCHAR(255)  NOT NULL DEFAULT 'docusign-prod',
    content_type    VARCHAR(100),                       -- e.g. "application/pdf"
    size_bytes      BIGINT        NOT NULL,
    checksum_sha256 VARCHAR(64)   NOT NULL,             -- integrity check
    
    -- Versioning
    version         INT           NOT NULL,              -- 1, 2, 3, ...
    is_latest       BOOLEAN       NOT NULL,
    
    -- Status + compliance
    status          VARCHAR(20)   CHECK (status IN ('ACTIVE', 'DELETED', 'LEGAL_HOLD')),
    data_region     VARCHAR(50)   NOT NULL DEFAULT 'us-east-1',  -- GDPR: where data lives
    
    -- Audit trail
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by      UUID          NOT NULL,
    deleted_at      TIMESTAMPTZ,   -- NULL unless status = 'DELETED'
    deleted_by      UUID,          -- who initiated the soft delete
    
    -- Composite primary key: document + version
    PRIMARY KEY (id, version),
    
    -- Indexes for common queries
    INDEX idx_owner_latest (owner_id, is_latest) WHERE is_latest = TRUE,
    INDEX idx_owner_status (owner_id, status),
    INDEX idx_created_at (created_at DESC),
    INDEX idx_s3_key (s3_key)  -- for deduplication/orphan detection
);

-- Version history (why the change, when, who)
CREATE TABLE version_history (
    id              BIGSERIAL PRIMARY KEY,
    document_id     UUID       NOT NULL,
    old_version     INT,            -- NULL if first version
    new_version     INT       NOT NULL,
    change_reason   VARCHAR(500),   -- e.g. "Minor edits", "Signature added"
    changed_by      UUID       NOT NULL,
    changed_at      TIMESTAMPTZ DEFAULT NOW(),
    
    INDEX idx_doc_history (document_id, changed_at DESC)
);

-- Legal holds: prevent deletion per document (compliance)
CREATE TABLE legal_holds (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id     UUID       NOT NULL REFERENCES documents(id),
    reason          VARCHAR(500),   -- "Litigation hold per case #12345"
    hold_issued_by  VARCHAR(255),
    hold_date       TIMESTAMPTZ NOT NULL,
    expected_release_date TIMESTAMPTZ,
    
    INDEX idx_doc_holds (document_id)
);
```

**Querying patterns:**

```java
// Get current version of a document (most common query)
SELECT * FROM documents
WHERE id = ? AND is_latest = TRUE;
// Uses index: idx_owner_latest or idx_s3_key
// Latency: < 5ms

// Get all versions of a document (for version history UI)
SELECT * FROM documents
WHERE id = ?
ORDER BY version DESC;
// Scans the (id, version) primary key
// Latency: < 50ms (even with 100 versions)

// List all documents for a user
SELECT * FROM documents
WHERE owner_id = ? AND is_latest = TRUE AND status = 'ACTIVE'
ORDER BY created_at DESC
LIMIT 50;
// Uses index: idx_owner_latest (filtered to ACTIVE, is_latest = TRUE)
// Latency: < 20ms

// Check if document is under legal hold (before allowing deletion)
SELECT COUNT(*) FROM legal_holds
WHERE document_id = ? AND expected_release_date > NOW();
// Fast: tiny table, indexed by document_id
// Latency: < 2ms
```

**Why this deep dive matters:**
- The schema design is the foundation for all queries and versioning logic
- Immutable rows (one per version) make audit trails natural and prevent accidental overwrites
- Indexes on (owner_id, is_latest) make the most common query (current version) O(1)
- Legal holds are a separate table (keeps documents table clean; compliance can be queried independently)

---

### Deep Dive 2: Pre-Signed URLs + Access Control

**Why this is the most critical component:**
At 100M downloads/day, you cannot route all downloads through the app server (5.8 GB/sec bandwidth would overwhelm any server farm). Pre-signed URLs let S3 handle the download directly. But they're a security tool: a 15-minute expiry prevents URL leaks; per-user URL generation prevents replay attacks; access control checks ensure users can only download docs they own.

**Options considered:**

| Option | Pros | Cons |
|---|---|---|
| **Option A: Direct S3 downloads (no auth)** | Simple; S3 handles all bandwidth | Anyone with the URL can download (massive security hole); no access control |
| **Option B: App server proxies all downloads** | Full control; logs every download | Bandwidth bottleneck: 5.8 GB/sec = unscalable; 100ms+ latency for every download |
| **Option C: Pre-signed URLs with per-user generation** | S3 handles bandwidth; app server generates time-limited, user-specific URLs; access control enforced at URL generation time | URL could be leaked/shared (mitigated by short expiry); requires client to get new URL if it expires mid-download |

**Decision: Option C (pre-signed URLs with per-user generation).**

Because it scales (S3 handles bandwidth) while maintaining access control (app server enforces auth before issuing URL). 15-minute expiry is acceptable (if a download takes > 15 min, client gets a 403 and requests a new URL).

**Implementation sketch:**

```java
public class DocumentDownloadService {
    private final DocumentRepository docRepo;
    private final AmazonS3 s3Client;
    private final AccessControlService acl;
    private final AuditLogger auditLogger;

    /**
     * Generate a pre-signed URL for downloading a document.
     * This is called by the API; the client then downloads directly from S3 using the URL.
     */
    public String generatePresignedUrl(String userId, String documentId) throws AccessDeniedException {
        // Step 1: Load document metadata
        Document doc = docRepo.findById(documentId);
        if (doc == null) {
            throw new DocumentNotFoundException(documentId);
        }

        // Step 2: Check access control
        // User can download if:
        // - they are the owner, OR
        // - they have explicit "viewer" role in the access_control table
        if (!acl.canRead(userId, documentId)) {
            throw new AccessDeniedException("User " + userId + " cannot read document " + documentId);
        }

        // Step 3: Check if document is deleted
        // If deleted but under legal hold, allow (for legal review)
        // If deleted and no legal hold, return 404
        if ("DELETED".equals(doc.getStatus())) {
            if (!isUnderLegalHold(documentId)) {
                throw new DocumentNotFoundException(documentId + " (deleted)");
            }
        }

        // Step 4: Generate pre-signed URL with 15-minute expiry
        String s3Key = doc.getS3Key();  // e.g., "docs/owner-id/doc-id/v3.pdf"
        Date expiry = new Date(System.currentTimeMillis() + 15 * 60 * 1000);  // 15 min
        
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(
            doc.getS3Bucket(),
            s3Key
        ).withMethod(HttpMethod.GET)
         .withExpiration(expiry);

        URL presignedUrl = s3Client.generatePresignedUrl(request);

        // Step 5: Log the download attempt (access audit trail)
        auditLogger.logAccess(
            documentId,
            userId,
            "DOWNLOAD_URL_GENERATED",
            RequestContext.getClientIp(),
            RequestContext.getUserAgent()
        );

        return presignedUrl.toString();
    }

    private boolean isUnderLegalHold(String documentId) {
        // Check if ANY legal hold is active (expected_release_date in future)
        return legalHoldRepo.countByDocumentIdAndReleaseDateAfter(
            documentId,
            Instant.now()
        ) > 0;
    }
}
```

**API contract:**

```java
// GET /v1/documents/{doc_id}/download
@GetMapping("/documents/{docId}/download")
public ResponseEntity<?> downloadDocument(
        @PathVariable String docId,
        @RequestHeader("Authorization") String jwt,
        HttpServletRequest request) throws AccessDeniedException {
    
    String userId = extractUserId(jwt);
    
    // Generate pre-signed URL (access control is enforced here)
    String presignedUrl = downloadService.generatePresignedUrl(userId, docId);
    
    // Return redirect to S3
    return ResponseEntity
        .status(HttpStatus.FOUND)  // 302 redirect
        .header("Location", presignedUrl)
        .build();
}
```

**Flow from client perspective:**
1. Client calls `GET /v1/documents/{doc_id}/download` (with JWT in header)
2. API validates JWT, checks access, generates pre-signed URL
3. API returns 302 redirect with `Location: https://s3.amazonaws.com/bucket/...?X-Amz-Signature=...&Expires=1234567890`
4. Client browser follows redirect → downloads directly from S3
5. S3 validates the signature + expiry
6. S3 streams the file directly to client (app server NOT involved in data transfer)

**Why this deep dive matters:**
- Pre-signed URLs are the key to scaling downloads to 100M/day
- Access control is enforced at URL generation time (not at S3; S3 blindly trusts the signature)
- 15-minute expiry prevents URL replay and sharing (if someone leaks the URL, it expires in 15 min)
- Audit logging captures every URL generation (downstream of access control, so only authorized users are logged)

---

### Deep Dive 3: Soft Deletes + Legal Holds for Compliance

**Why this is the most critical component:**
GDPR says "users have the right to deletion." E-signature law says "audit trails are immutable forever." These two requirements are in direct conflict. Soft deletes resolve it: mark the document deleted (user thinks it's gone), but keep the S3 object (audit trail is intact). Legal holds add another layer: even if a document is "deleted," it can be marked for legal review (litigation, GDPR data subject access request), and the document becomes inaccessible to the owner but queryable by legal/compliance.

**Options considered:**

| Option | Pros | Cons |
|---|---|---|
| **Option A: Hard delete (physically remove from S3)** | User satisfaction (data truly gone); simple | Breaks audit trail; violates e-signature law; non-compliant with legal holds; exposed to lawsuits |
| **Option B: Soft delete (DB flag only, S3 kept)** | Audit trail preserved; GDPR-compliant (marked deleted); cheap (S3 object still there) | User might expect permanent deletion; requires filter on every query (deleted records must be hidden) |
| **Option C: Hybrid (soft delete + delayed hard delete after 90 days)** | User satisfaction + compliance; audit trail for 90 days (covers most investigations) | More complex; scheduled job to hard-delete; what if legal hold issued on day 89? |

**Decision: Option B (soft delete, S3 kept forever).**

Because it's the cleanest for compliance: audit trails are truly immutable, GDPR requests are handled by access control (if deleted by user, they can't see it; if under legal hold, legal team can see it), and S3 object preservation prevents accidental data loss.

**Implementation sketch:**

```java
public class DocumentDeletionService {
    private final DocumentRepository docRepo;
    private final LegalHoldRepository holdRepo;
    private final AuditLogger auditLogger;

    /**
     * Soft-delete a document (user initiates deletion, but audit trail is preserved).
     * If document is under legal hold, deletion is blocked.
     */
    @Transactional
    public void deleteDocument(String userId, String documentId) throws DeletionBlockedException {
        // Step 1: Load document + check legal holds
        Document doc = docRepo.findById(documentId);
        if (doc == null) {
            throw new DocumentNotFoundException(documentId);
        }

        // Check if user is owner (can only delete your own docs)
        if (!userId.equals(doc.getOwnerId())) {
            throw new AccessDeniedException("Only owner can delete");
        }

        // Step 2: Check if under legal hold
        // Legal holds block deletion (compliance requirement)
        if (hasActiveLegalHold(documentId)) {
            throw new DeletionBlockedException(
                "Document is under legal hold. Contact legal team to release hold before deletion."
            );
        }

        // Step 3: Soft delete (no S3 deletion)
        // Atomic UPDATE: set status = 'DELETED' + deleted_at + deleted_by
        doc.setStatus("DELETED");
        doc.setDeletedAt(Instant.now());
        doc.setDeletedBy(userId);
        docRepo.save(doc);  // saves to Postgres only; S3 object is NOT touched

        // Step 4: Log to audit trail (immutable)
        auditLogger.logDeletion(
            documentId,
            userId,
            "DOCUMENT_SOFT_DELETED",
            "User initiated deletion",
            Instant.now()
        );
    }

    /**
     * Legal team issues a legal hold on a document (e.g., during litigation).
     * This blocks deletion until the hold is released.
     */
    @Transactional
    public void issueLegalHold(
            String documentId,
            String holdReason,
            String issuedBy,
            LocalDate expectedReleaseDate) {
        
        LegalHold hold = new LegalHold();
        hold.setDocumentId(documentId);
        hold.setReason(holdReason);  // e.g., "Litigation hold - Case 2026-12345"
        hold.setIssuedBy(issuedBy);
        hold.setIssuedAt(Instant.now());
        hold.setExpectedReleaseDate(expectedReleaseDate);
        
        holdRepo.save(hold);

        auditLogger.logLegalAction(
            documentId,
            "LEGAL_HOLD_ISSUED",
            "Hold by: " + issuedBy + ", Reason: " + holdReason,
            Instant.now()
        );
    }

    /**
     * Legal team releases a legal hold (document can now be deleted if desired).
     */
    @Transactional
    public void releaseLegalHold(String holdId, String releasedBy) {
        LegalHold hold = holdRepo.findById(holdId);
        hold.setReleasedAt(Instant.now());
        hold.setReleasedBy(releasedBy);
        holdRepo.save(hold);

        auditLogger.logLegalAction(
            hold.getDocumentId(),
            "LEGAL_HOLD_RELEASED",
            "Released by: " + releasedBy,
            Instant.now()
        );
    }

    private boolean hasActiveLegalHold(String documentId) {
        // Check if ANY legal hold is currently active
        return holdRepo.countByDocumentIdAndReleasedAtIsNull(documentId) > 0;
    }
}
```

**Why this deep dive matters:**
- Soft deletes enable GDPR compliance (user can request deletion; system marks it deleted but preserves audit trail)
- Legal holds block deletion (litigation/investigation requirements override user deletion requests)
- S3 objects are never deleted (audit trail + cost-saving; old data is cheap in S3)
- Audit logs track who deleted what when, enabling compliance investigations and audit reviews

---

## Section 9 — 🗄️ Data Model

### Core Tables (Already Detailed in Section 7)

See **Deep Dive 1** for the complete schema. Key tables:
- **documents** (one row per version)
- **version_history** (audit trail of version changes)
- **legal_holds** (compliance: blocks deletion)
- **access_logs** (audit: who downloaded when)

### Additional Tables for Compliance

```sql
-- Who has what access (fine-grained ACL)
CREATE TABLE document_access (
    document_id UUID NOT NULL,
    user_id     UUID NOT NULL,
    role        VARCHAR(20) CHECK (role IN ('OWNER', 'EDITOR', 'VIEWER')),
    granted_by  UUID NOT NULL,
    granted_at  TIMESTAMPTZ DEFAULT NOW(),
    
    PRIMARY KEY (document_id, user_id),
    INDEX idx_user_docs (user_id, document_id)
);

-- Audit log: every access (read-only, immutable)
CREATE TABLE audit_logs (
    id              BIGSERIAL PRIMARY KEY,
    document_id     UUID       NOT NULL,
    user_id         UUID       NOT NULL,
    action          VARCHAR(50),  -- 'DOWNLOAD_URL_GENERATED', 'DELETED', 'VIEWED'
    action_reason   VARCHAR(255),
    client_ip       INET,
    user_agent      TEXT,
    action_timestamp TIMESTAMPTZ DEFAULT NOW(),
    
    INDEX idx_doc_audit (document_id, action_timestamp DESC),
    INDEX idx_user_audit (user_id, action_timestamp DESC)
);

-- Trigger: prevent modifications to audit_logs
CREATE TRIGGER audit_immutable_trigger
    BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW
    EXECUTE FUNCTION prevent_modification();
```

---

## Section 10 — ⚠️ Trade-Offs + Failure Modes (Minutes 45–52)

### Trade-off 1: Pre-Signed URL Expiry (Security vs Usability)

**Chose:** 15-minute expiry.

**Gain:** Limits window for URL leaks/replay attacks; acceptable for most use cases (downloads complete in < 30 seconds).

**Lose:** If a download takes > 15 minutes (rare, but happens for 500 MB files on slow connections), client gets a 403 and must request a new URL.

**Failure mode if wrong:** If expiry is 1 day (for usability), URL can be leaked and shared; attacker downloads all your documents. If expiry is 1 minute, users on slow connections get frequent 403s. **Business impact:** For DocuSign: a 24-hour pre-signed URL for a signed contract PDF gets forwarded in an email thread — opposing counsel clicks it and downloads the full agreement before disclosure is authorized. Conversely, a 1-minute expiry causes a signer on a mobile connection to receive a 403 when clicking the completion download link 90 seconds later — they believe the signing failed and call support, despite the ceremony completing successfully.

---

### Trade-off 2: Soft Deletes vs Hard Deletes

**Chose:** Soft deletes (status = DELETED, S3 object kept).

**Gain:** Audit trail preserved; GDPR-compliant; reversible (customer can undelete if needed); cheap (S3 storage is <$0.01/GB/month for archived data).

**Lose:** Customer might expect "truly gone"; requires filtering (queries must WHERE status = 'ACTIVE'); orphaned S3 objects if metadata is accidentally lost.

**Failure mode if wrong:** If you hard-delete (remove S3 object) and customer requests GDPR access report 2 years later, you have no proof what documents existed or were deleted. Non-compliant. If you soft-delete and forget to filter queries, deleted documents show up in listings (confusing user experience). **Business impact:** DocuSign is subject to 7-year document retention laws in financial services (SEC 17a-4) — hard-deleting a document to fulfill a GDPR deletion request destroys the legally required audit trail, exposing DocuSign to regulatory fines and litigation. The inverse failure: a sender sees a "deleted" envelope in their active listing, opens it, and shares the wrong document version with a counterparty — creating a contract dispute.

---

### Trade-off 3: Redis Caching vs Direct DB Queries

**Chose:** Redis cache with 1-hour TTL + DB fallback.

**Gain:** At 3.5K reads/sec, Redis (100 microseconds per hit) scales easily; DB queries reduce to 350/sec (10% cache hit rate). Cache misses fall back to DB.

**Lose:** Stale metadata (up to 1 hour); if user updates a document's title, it takes up to 1 hour for the cache to reflect the change.

**Failure mode if wrong:** If you bypass cache, 3.5K reads/sec hit the database. Postgres can handle ~1K sustained queries; DB becomes bottleneck, queries slow to 100ms+, users see timeouts. If you cache without fallback and Redis is down, API is broken (no fallback to DB). **Business impact:** For DocuSign: 3.5K document metadata reads/sec without cache causes the envelope listing API to time out — an enterprise legal team running an eDiscovery export of 10K envelopes finds the API non-responsive during a court deadline — a publicly traded company faces a discovery sanctions risk. If Redis fails without DB fallback, 100% of document access breaks, blocking all in-progress signing ceremonies until Redis recovers.

---

## Section 11 — 🔐 DocuSign-Specific Depth (Minutes 52–57)

**Why this question is on the DocuSign R2 interview:**

Document storage is fundamental to DocuSign's product. Every signed contract must be securely stored, versioned (show edit history), and audit-logged (legal compliance). A senior engineer must understand: immutable versioning (so contracts can't be retroactively changed), soft deletes (GDPR + legal holds), and pre-signed URLs (how to scale downloads to 100M/day without bottlenecking the app server).

**DocuSign-specific angles your answer must address:**

1. **Versioning for compliance**: Contracts go through multiple iterations (draft → reviewed → signed). Each version must be preserved, timestamped, and attributed (who made the change?). Your system must support "show me who edited what on what date."

2. **Non-repudiation**: A signed document must be immutable after signing. The S3 key never changes (immutable); the metadata row marks it signed and locked (no further edits). Legal admissibility depends on this.

3. **Audit trail forever**: DocuSign must prove "Document X was downloaded by User Y on [timestamp]" for 7+ years. Your audit_logs table is append-only and archived to S3 Glacier after 1 year (cheap, compliant).

4. **Legal holds**: Compliance/legal teams can issue a hold on a document (e.g., during litigation), which blocks deletion even if the user requests it. This must be queryable and enforceable at the API layer.

5. **Multi-region compliance (GDPR)**: If a user is in the EU, their documents must stay in the EU (data residency). Your schema has `data_region` column; documents are routed to region-specific S3 buckets.

6. **S3 Object Lock (WORM) — the correct legal compliance mechanism:**

A soft delete (marking a DB row `status = 'DELETED'`) is a helpful UX pattern. It is **not** compliance-grade legal hold. The distinction matters:

- A soft delete is enforced by application code. A determined admin with DB write access (or a bug) can physically DELETE the row, removing the document from the audit trail.
- Compliance standards for regulated industries (SEC 17a-4, FINRA, HIPAA, GDPR data retention) require **WORM storage** (Write Once Read Many — a storage constraint that physically prevents any modification or deletion of an object for a specified retention period, regardless of who requests it, including AWS account root).

**S3 Object Lock** is AWS's WORM implementation. It operates at the S3 API level — below your application, below your IAM policies, below even your AWS account admin.

Two modes:
- **Governance mode**: Account users with a special `s3:BypassGovernanceRetention` permission can override the lock. Used for internal compliance (audit, SOX).
- **Compliance mode**: No one can delete or overwrite the object during the retention period — not even root. Used for SEC 17a-4, FINRA, and any regulation that requires tamper-proof storage. Once set, the retention period can only be extended, never shortened.

**How to apply it at upload time:**

```java
// When a document is "SIGNED" and legally sealed, apply object lock
s3Client.putObjectRetention(PutObjectRetentionRequest.builder()
    .bucket("docusign-prod-eu")
    .key("docs/" + ownerId + "/" + docId + "/v" + version + ".pdf")
    .retention(ObjectLockRetention.builder()
        .mode(ObjectLockRetentionMode.COMPLIANCE)
        // Retain for 7 years from signing date (common legal requirement)
        .retainUntilDate(Instant.now().plus(Duration.ofDays(365 * 7)))
        .build())
    .build());
```

**What soft-delete covers vs what Object Lock covers:**

| Concern | Soft Delete | S3 Object Lock |
|---|---|---|
| Hide from user queries | ✅ filters out `status = DELETED` | ❌ not a query concept |
| Prevent accidental app-level deletion | ✅ guards normal code paths | ✅ guards S3 delete API calls |
| Prevent admin/root physical deletion | ❌ a DBA can run `DELETE FROM documents` | ✅ AWS API physically rejects delete during retention period |
| Passes SEC 17a-4 / FINRA audit | ❌ insufficient | ✅ specifically designed for this |
| Supports legal hold (indefinite) | ✅ `legal_hold = true` in DB | ✅ `ObjectLockLegalHold = ON` at S3 API level |

**In an interview:** "Soft delete handles UX — documents disappear from listings. For legal compliance, signed documents get S3 Object Lock in Compliance mode with a 7-year retention. This is WORM storage — no one, not even AWS root, can delete or overwrite the object during the retention window. This is what passes a SOC 2 or SEC 17a-4 audit; soft delete alone does not."

**Your answer should include:**

> "Every document version is stored immutably in S3 with a unique key (`docs/{owner}/{doc_id}/v{n}.pdf`), and the metadata table tracks: owner, version, s3_key, status, and timestamps. When a user requests a download, the API checks access control (user is owner or has 'viewer' role), generates a pre-signed URL with 15-minute expiry, and logs the access to an immutable audit_logs table. This audit trail proves who downloaded what when, satisfying e-signature law and GDPR compliance requirements."

> "For multi-party contracts, when a document is fully signed, the metadata row is marked with a signature_completed_at timestamp. No further versions can be added after signing (status = 'SIGNED', immutable). If a legal hold is issued (e.g., during litigation), the document is marked with legal_hold = true, which blocks deletion and makes the document queryable by legal teams. When the hold is released, the document can be deleted by the owner."

> "Documents are never hard-deleted; soft deletes preserve the audit trail and allow GDPR data subject access requests ('show me all documents about me'). The S3 object is archived to Glacier after 7 years (cheap long-term storage, compliant with legal retention)."

---

## Section 12 — 🔬 Where the Interviewer Will Probe (Minutes 57–60)

### Tier 1 — Surface Probe (Do you understand the architecture?)

**Q: "Why don't you store document content in the database instead of S3?"**

> A database is optimized for structured, queryable data (rows, columns, indexes). Document content is large, binary, and unstructured. At 250 TB, storing in a database would cost $100K+/month and make queries slow. S3 is ~$6K/month and scales to petabytes. The pattern is: metadata (DB) + content (S3), two systems working together.

### Tier 2 — Deep Probe (Do you understand the failure modes?)

**Q: "What happens if a document is soft-deleted but the S3 object is accidentally deleted by a rogue admin? How do you detect and recover from this?"**

> Good catch. We'd have metadata without content (orphaned record). A reconciliation job runs hourly: SELECT documents WHERE status = 'ACTIVE' AND s3_key NOT IN (SELECT keys FROM s3). It alerts ops if any mismatch. Recovery: if S3 object is accidentally deleted, it can be restored from S3 versioning (if enabled) or from backup. If no backup, we contact the customer, issue a legal letter, and restore from a database backup (which also restores the document to a previous state). Prevention: S3 versioning is enabled, and delete permissions are restricted to a minimal IAM role.

### Tier 3 — Cross-Concept Probe (Can you reason across concepts?)

**Q: "Your audit_logs table logs every download. At 100M downloads/day, that's 100M audit inserts/day. How do you prevent the audit table from becoming a bottleneck, and how do you query it efficiently?"**

> The audit_logs table is write-heavy but read-light. To avoid write bottleneck, we use a **partitioned table** (partition by month: audit_logs_202606, audit_logs_202607, etc.). New inserts go to the current-month partition (hot). Old partitions are archived to S3 Glacier. To query efficiently, we add indexes on (document_id, action_timestamp DESC) for "show me this document's audit trail" and (user_id, action_timestamp DESC) for "show me this user's activity." Both are O(log N) lookups. We also cache "recent audit events" in Redis (5-day window), so 80% of audit queries hit Redis instead of Postgres.

---

### New Deep Probes (Tier 2 — added Jul 4, 2026)

**Q: "A user uploads a document. Your design immediately writes to S3 and inserts the metadata row. But what if the uploaded file contains malware? How do you prevent a malicious PDF from entering the production bucket?"**
> **Quarantine bucket pattern.** Instead of writing user uploads directly to the production S3 bucket, all uploads land in a quarantine bucket (`docusign-quarantine/{doc_id}/filename`). The file is never user-accessible from here.
>
> On S3 upload completion, an S3 event notification fires a Lambda (a serverless function — code that runs on AWS's infrastructure in response to an event, without you managing a server) → the Lambda runs a malware scanner (ClamAV — an open-source antivirus engine that scans file bytes for known malware signatures) against the file bytes:
>
> - **Clean:** Lambda copies the file from quarantine to the production bucket (`docusign-prod/{owner_id}/{doc_id}/v1.pdf`), then inserts the metadata row in Postgres with `status = 'ACTIVE'`. The document is now accessible.
> - **Infected:** Lambda moves the file to a security-review bucket (`docusign-quarantine-hold/{doc_id}`), inserts metadata with `status = 'QUARANTINED'`, and sends a Kafka event → Notification Service informs the user: "Your upload failed our security scan."
>
> The presigned URL generation service checks `status = 'ACTIVE'` before issuing a URL — a `QUARANTINED` document can never be downloaded by the user.
>
> **Trade-off:** Scanning adds 2–10 seconds to the upload-to-available latency. Acceptable for documents; not acceptable for real-time video. In an interview: "I'd use a quarantine bucket to ensure no file enters production without a clean scan. The user sees 'processing…' during the scan window — typically 2–5 seconds for a 5MB PDF."

---

**Q: "S3 and Postgres can get out of sync — what if the metadata row is inserted but the S3 object was never written (network failure mid-upload), or the S3 object exists but there's no metadata row (Lambda crashed between copy and INSERT)?"**
> These are the two orphan-state failure modes. Both require a reconciliation job to detect:
>
> **Mode 1 — Postgres row exists, no S3 object:**
> Triggered by: S3 write failed after metadata INSERT, or S3 object deleted by accident.
> Effect: `generatePresignedUrl()` generates a URL that returns S3 404 when the client tries to download.
> Fix: Before returning the presigned URL, call `s3.headObject(bucket, key)`. If it returns 404, return HTTP 404 to the client immediately and alert ops. Recovery: restore from S3 versioning (if enabled) or from cross-region replica.
>
> **Mode 2 — S3 object exists, no Postgres row (orphaned object):**
> Triggered by: Lambda/API crashed after S3 write but before INSERT.
> Effect: File sits in S3 forever, consuming storage, never accessible.
> Fix: Reconciliation job (runs daily) lists all S3 objects via `S3.listObjectsV2`, checks each key against the documents table. Orphaned keys (no row in documents) are flagged. For recently uploaded files (< 24h), auto-insert the metadata row (likely a crash recovery). For older orphans (> 7 days), move to a glacier bucket and alert ops for manual review.
>
> **In an interview:** "I'd make the upload idempotent: S3 write is the source of truth. Metadata INSERT only happens after S3 confirms the object exists. A daily reconciliation job catches any remaining discrepancies. This belt-and-suspenders approach means no data is ever truly lost — just temporarily inaccessible until reconciliation corrects it."

---

### New Cross-Concept Probe (Tier 3 — added Jul 4, 2026)

**Q: "DocuSign has enterprise customers in Germany (GDPR strict — data must stay in the EU). Your design stores all documents in a single S3 bucket in us-east-1. How do you handle data residency requirements at the document level?"**
> Data residency is one of the hardest multi-tenant constraints. Three approaches:
>
> **(A) Single region, contractual compliance:** Store everything in US; claim GDPR Standard Contractual Clauses (SCCs) cover the transfer. Problem: Germany's DPA (Datenschutzbeauftragter — data protection authority) has cracked down on this; it no longer accepts SCCs alone for most use cases. Risk: GDPR fines up to 4% of global revenue.
>
> **(B) Per-customer dedicated region (chosen):** At account creation, assign the customer a home region (EU tenant → Frankfurt S3 bucket; US tenant → us-east-1 bucket). Store `data_region` on every document and the tenant config. The API reads `tenant.data_region` and routes S3 calls to the appropriate regional bucket. Cross-region copies are never made for EU tenants.
>
> Implementation:
> - `documents.data_region = 'eu-central-1'` (set at upload time from tenant config)
> - Upload: `s3client.putObject("docusign-prod-eu", key, file)` (Frankfurt bucket)
> - Presigned URL: `s3client.generatePresignedUrl("docusign-prod-eu", key, expiry)` (Frankfurt URL)
>
> **(C) Encryption + key control:** Store ciphertext in US bucket, but hold the KMS key in the EU. Under GDPR, if the data is cryptographically inaccessible without the EU-resident key, it's effectively "not transferred." This is legally grey; not all DPAs accept it.
>
> **Recommendation:** Option B. It's legally clear, operationally simple (one bucket per region), and aligns with what DocuSign actually does (they have dedicated EU data centers for GDPR compliance). The `data_region` column in `documents` makes routing deterministic — no global coordination required.

---

## Section 13 — 🐞 Common Mistakes on This Question

- **Mistake 1:** "I'll store document content in the database as BLOB." → **Why it's wrong:** At 250 TB, your database costs 50× more than S3; queries slow to 1+ second; you can't scale to 100M downloads/day. → **What to say instead:** "Metadata goes in Postgres (owner, version, timestamp); document content goes in S3 (immutable, versioned by key). Pre-signed URLs let clients download directly from S3 (no app server bottleneck)."

- **Mistake 2:** "I'll hard-delete documents immediately when users request deletion." → **Why it's wrong:** GDPR investigations, litigation, audit trails all require historical data. Hard-delete breaks compliance. E-signature law requires immutable audit trails. → **What to say instead:** "Soft delete: set status = 'DELETED' in the metadata table. S3 object is never deleted (preserves audit trail). Legal holds can block deletion (compliance requirement). Audit logs are immutable and archived to Glacier for long-term compliance."

- **Mistake 3:** "Every document download goes through the app server (the app reads from S3 and streams to the client)." → **Why it's wrong:** At 100M downloads/day = 5.8 GB/sec bandwidth, no app server farm can handle this. You'll need 1000+ servers just for proxying. → **What to say instead:** "Pre-signed URLs. API generates a time-limited, user-specific S3 URL (15-minute expiry). Client downloads directly from S3 (S3 handles all bandwidth). App server only validates access and generates the URL; data transfer doesn't touch the app."

---

## Section 14 — 🧭 DocuSign Dimensions Checklist

| Dimension | Relevant? | How your design addresses it |
|---|---|---|
| **Testability** | ✅ | Pre-signed URL generation is testable (mock S3 client). Version history is testable (insert version, query, verify order). Soft delete is testable (delete, verify status = DELETED, verify access denied). No end-to-end S3 dependency needed. |
| **Usability** | ✅ | Users can upload, download, view versions, and delete. Version history UI shows who edited what when. Download is fast (pre-signed URL → direct S3, no app server latency). |
| **Extensibility** | ✅ | New features (sharing, collaboration) are easy: add a `document_access` table with roles (OWNER, EDITOR, VIEWER). Tagging, comments, annotations are separate tables (don't modify core schema). |
| **Security** | ✅ | Pre-signed URLs expire (15 min), so leaked URLs are time-limited. S3 encryption at rest (SSE-S3 or KMS). Per-document access control (RBAC). Audit logs capture every access. |
| **Availability** | ✅ | S3 is multi-AZ (99.99% SLO). Postgres has read replicas (HA). Cache (Redis) reduces DB load. If S3 is down, app can return cached metadata, but downloads fail (acceptable). 99.9% SLO achievable. |
| **Scalability** | ✅ | S3 scales infinitely (250 TB, 100M downloads/day — no problem). Postgres metadata table is 50GB with good indexes (scales fine). Redis cache handles 3.5K reads/sec. Pre-signed URL generation is stateless (load-balance across servers). |
| **Observability & Traceability** | ✅ | Every document has an audit_logs table (who accessed, when, from where, success/failure). Metadata includes created_by, created_at, deleted_by, deleted_at. S3 CloudTrail logs all S3 API calls. CloudWatch metrics: downloads/sec, avg download latency, errors. |

---

## Section 15 — 🧾 TL;DR Answer Summary

> "A document storage system at DocuSign scale requires clean separation: metadata (Postgres) + content (S3). Every document version gets an immutable S3 key (`docs/{owner}/{doc_id}/v{n}.pdf`), and the metadata table tracks owner, version, status, s3_key, and timestamps. Downloads use pre-signed URLs (15-min expiry, generated by API after access control checks), so S3 handles 100M downloads/day directly without bottlenecking the app server. Soft deletes (status = 'DELETED', S3 object kept) preserve audit trails for GDPR and e-signature compliance. Legal holds block deletion and allow compliance teams to review documents during litigation. Immutable audit_logs table captures every download/deletion (for 7+ year retention). The system balances three competing requirements: (1) scale (pre-signed URLs + S3), (2) compliance (immutable audit trail + soft deletes), (3) legal (legal holds block deletion, version history is forensic)."

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 24, 2026 | **D2-document-storage.md created.** Final solution file (8 of 8). Full 15-section solution framework for Type B Product Architecture. Covers: metadata schema design (one row per version), S3 blob storage pattern (immutable content + indexed metadata), pre-signed URL generation (security + scalability), soft deletes + legal holds (compliance), audit trail (immutable append-only), and multi-region GDPR compliance. Scale: 250 TB storage, 100M downloads/day, 10K uploads/day. Prerequisites: `14-document-blob-storage.md`, `03-caching.md`. |
| Jul 4, 2026 | **4 new Q&As added to Section 12.** (1) **Quarantine bucket pattern for malware scanning** — uploads land in quarantine S3 bucket, Lambda runs ClamAV on upload completion, clean files copied to prod bucket + metadata inserted, infected files moved to hold bucket + status = QUARANTINED; presigned URL generation blocked for non-ACTIVE docs; adds 2–5s latency; (2) **S3/Postgres out-of-sync failure modes** — Mode 1 (row exists, no S3 object): `s3.headObject()` check before presigned URL + ops alert + restore from versioning; Mode 2 (S3 object exists, no row): daily reconciliation job via `listObjectsV2`, auto-insert for <24h orphans, glacier for >7-day orphans; (3) **Data residency for EU customers (GDPR)** — per-customer region assignment at account creation, `data_region` column routes S3 calls to Frankfurt or us-east-1 bucket; documents.data_region set at upload time; approach aligns with how DocuSign operates dedicated EU data centers. |
| Jul 5, 2026 | **Section 6 restructured: single final-state diagram → 2-stage progressive HLD.** Stage 1 (App Server + Local/NFS Disk): single-node upload+download, no HA, downloads proxied through app server — BREAKING POINTs: disk failure = data loss; 5.8 GB/sec bandwidth saturates app server fleet. Stage 2 (S3 + Metadata DB + Pre-Signed URLs): separate upload/download/soft-delete flows, metadata in Postgres, content in S3, pre-signed URLs bypass app server entirely. Three inline decision tables added: (1) content storage — Local disk ❌ / SAN-NAS ⚠️ / S3 ✅; (2) download strategy — App proxy ❌ / CDN ⚠️ / Pre-signed URLs ✅; (3) versioning approach — Overwrite ❌ / One-row-per-version ✅ / Git-style delta ⚠️. Verdict alignment verified: all Section 6 verdicts match Section 7 deep-dive choices. |
| Jul 5, 2026 | **Section 10 business impact pass.** Added **Business impact:** to all 3 trade-offs — 24-hour pre-signed URL leaking to opposing counsel during litigation + 1-minute expiry causing 403 immediately after signing ceremony completes (URL lifetime), SEC 17a-4 7-year retention conflicting with GDPR right-to-erasure exposing regulatory fines in both jurisdictions simultaneously (compliance conflict), eDiscovery export non-responsive during court deadline + Redis failure blocking all document access including active signing ceremonies (cache dependency). |
