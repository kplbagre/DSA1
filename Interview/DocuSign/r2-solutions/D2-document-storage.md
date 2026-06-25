# D2 — Design a Document Storage & Retrieval Service

> **Read `solution-notes-standards.md` FIRST.** This file is a 60-minute interview answer framework.
> Its structure mirrors DELIVERY-RECIPE: clarify (5 min) → estimate (5 min) → HLD (15 min) → deep dives (15 min) → trade-offs (8 min) → Q&A (7 min).
> **Say this out loud** before your interview — don't just read it.

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
- Encryption: at rest (SSE-S3 or KMS), in transit (HTTPS)

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
- Audit logs: 100M reads/day × 200 bytes/log = 20 TB/year (archive after 1 year; S3 Glacier for compliance)

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

### 🎨 ASCII Architecture Diagram

```
  DOCUMENT STORAGE & RETRIEVAL SERVICE — HIGH-LEVEL ARCHITECTURE
  ───────────────────────────────────────────────────────────────

  CLIENT (Web, Mobile, API)
         │
         ▼
  ┌──────────────────────────────────────────┐
  │         API Server (Stateless)           │
  │  ┌────────────────────────────────────┐  │
  │  │ POST /v1/documents (upload)        │  │
  │  │ GET  /v1/documents/{id} (download) │  │
  │  │ PATCH /v1/documents/{id}/versions  │  │
  │  │ DELETE /v1/documents/{id}          │  │
  │  └────────────────────────────────────┘  │
  └──────┬──────────────────────────────────┘
         │
    ┌────┴─────────────────────────────┐
    │                                  │
    ▼                                  ▼
┌──────────────────────┐        ┌─────────────────────┐
│  Postgres (metadata) │        │   Redis (cache)     │
│  ┌────────────────┐  │        │  ┌───────────────┐  │
│  │ documents      │  │        │  │ doc:{id}      │  │
│  │ versions       │  │        │  │ (metadata)    │  │
│  │ access_logs    │  │        │  │ TTL: 1 hour   │  │
│  │ audit_logs     │  │        │  └───────────────┘  │
│  │ legal_holds    │  │        └─────────────────────┘
│  └────────────────┘  │
│                      │
│  ┌────────────────┐  │
│  │ Indexes:       │  │
│  │ owner_id       │  │
│  │ doc_id+version │  │
│  │ created_at     │  │
│  └────────────────┘  │
└──────────────────────┘

  Download flow (API → S3 directly):
    API generates pre-signed URL (15 min expiry)
    Client downloads directly from S3 (bypasses app server)
    S3 logs download to CloudTrail (audit)

    ┌─────────────────┐
    │  Amazon S3      │
    │ (immutable docs)│
    │                 │
    │ docs/{user_id}/ │
    │ {doc_id}/       │
    │   v1.pdf        │
    │   v2.pdf        │
    │   v3.pdf        │
    └─────────────────┘
           │
           │ Large downloads
           │ (direct S3 → client)
           ▼
        CLIENT

  Upload flow (multipart):
    Client → S3 multipart upload (resumable)
    On completion → trigger Lambda → INSERT metadata in Postgres
    Postgres INSERT → triggers replication to read replicas

    ┌──────────────────┐
    │  AWS Lambda      │
    │  (on S3 upload   │
    │   completion)    │
    └──────────────────┘
           │
           ▼
    Update Postgres metadata

SUPPORTING INFRASTRUCTURE
───────────────────────────────────────────────────────────────
┌─────────────────────────────────────────────────────────────┐
│  Audit Trail (Append-Only)                                  │
│  ┌────────────────────────────────────────────────────────┐ │
│  │ • access_logs (download attempts)                      │ │
│  │ • version_history (version changes, reverts)           │ │
│  │ • S3 CloudTrail (S3 API calls, by AWS)                │ │
│  │ • Archived to S3 Glacier after 1 year (compliance)    │ │
│  └────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│  Compliance Layer                                            │
│  ┌────────────────────────────────────────────────────────┐ │
│  │ • Legal Holds (prevent deletion per user/doc)          │ │
│  │ • Data Residency (route to region per user)            │ │
│  │ • Encryption (SSE-S3 or KMS)                           │ │
│  │ • Soft Deletes (never purge from S3)                   │ │
│  └────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘

KEY INVARIANT:
   Metadata table is source of truth for queries (who owns? version? deleted?).
   S3 is source of truth for content (immutable, versioned, never deleted).
   These are synchronized: metadata.s3_key → S3 object exists.
```

**Data flow walkthrough (say this out loud):**

**Flow 1 — Document Upload:**
1. Client calls `POST /v1/documents` with metadata (title, type) + file
2. API Server generates a unique S3 key: `docs/{owner_id}/{doc_id}/v1.pdf`
3. **Option A (simple):** API server streams file to S3 directly (works for small files < 100 MB)
   - `s3Client.putObject(bucket, key, file.getInputStream(), metadata)`
4. **Option B (multipart, resumable):** API initiates multipart upload, returns presigned URLs for each part; client uploads parts in parallel; client notifies API when done
5. S3 returns ETag (checksum) confirming the upload
6. API inserts metadata row into `documents` table:
   ```sql
   INSERT INTO documents (id, owner_id, s3_key, version, is_latest, status, created_at)
   VALUES (?, ?, 'docs/{owner_id}/{doc_id}/v1.pdf', 1, TRUE, 'ACTIVE', NOW())
   ```
7. Return to client: `{document_id, version: 1, s3_key, status: 'ACTIVE'}`

**Flow 2 — Document Download (Via Pre-Signed URL):**
1. Client calls `GET /v1/documents/{doc_id}`
2. API queries metadata table (check access control: is requester owner or authorized?):
   ```sql
   SELECT s3_key, owner_id, status FROM documents WHERE id = ? AND version = (latest_version)
   ```
3. If access denied → 403 Forbidden
4. If deleted (status = 'DELETED') → check legal_holds table; if legal hold exists, allow access; else 404
5. **Generate pre-signed URL** (expires in 15 minutes):
   ```java
   String presignedUrl = s3Client.generatePresignedUrl(bucket, s3_key, expiryTime);
   ```
6. Log the download attempt to `access_logs` table (append-only): timestamp, user_id, doc_id, IP
7. Return presigned URL to client (metadata in response body, URL in header)
8. Client downloads directly from S3 using presigned URL (no app server involved for data transfer)

**Flow 3 — Document Versioning (New Version Uploaded):**
1. Client uploads new version of document `{doc_id}`
2. API generates new S3 key: `docs/{owner_id}/{doc_id}/v2.pdf`
3. Upload to S3 (same flow as Flow 1)
4. **Atomically update metadata table (in one transaction):**
   ```sql
   BEGIN;
   UPDATE documents SET is_latest = FALSE WHERE id = ? AND is_latest = TRUE;
   INSERT INTO documents (id, owner_id, s3_key, version, is_latest, status, created_at)
   VALUES (?, ?, 'docs/{owner_id}/{doc_id}/v2.pdf', 2, TRUE, 'ACTIVE', NOW());
   INSERT INTO version_history (document_id, old_version, new_version, changed_by, changed_at)
   VALUES (?, 1, 2, current_user_id, NOW());
   COMMIT;
   ```
5. Both metadata updates + version history insert succeed or all roll back

**Flow 4 — Soft Delete:**
1. Client calls `DELETE /v1/documents/{doc_id}`
2. API updates metadata (does NOT delete S3 object):
   ```sql
   UPDATE documents SET status = 'DELETED', deleted_at = NOW() WHERE id = ?;
   INSERT INTO audit_logs (document_id, action, user_id, timestamp)
   VALUES (?, 'DELETED', current_user_id, NOW());
   ```
3. **S3 object is never deleted** (preserves audit trail + enables legal hold + GDPR compliance)
4. Future queries filter by `status = 'ACTIVE'` (soft delete invisibility)

**Why each component:**
- **Postgres (metadata only, not content)**: Queryable, indexed, fast (< 10ms); holds owner_id, version, status, s3_key pointer
- **Redis (cache)**: Metadata queries at 3.5K QPS would hammer Postgres; cache hits reduce DB load to 10% baseline
- **S3 (blob storage)**: Unlimited capacity (250 TB), cheap ($6K/month), immutable, versioned via keys
- **Pre-signed URLs**: Clients download directly from S3 (no app server involved); eliminates server bottleneck for 100M downloads/day
- **Audit logs (append-only)**: Downloads logged for compliance; queryable by document_id + timestamp
- **Legal holds + soft deletes**: Comply with GDPR (no purge until legal hold released) + e-signature law (tamper-proof history)

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

## Section 8 — 🌐 API Design

### Core Endpoints

| Method | Path | Auth | Request body | Response | Status codes |
|---|---|---|---|---|---|
| POST | `/v1/documents` | JWT Bearer | `{title, file (multipart), type}` | `{document_id, version: 1, s3_key, status}` | 201, 400, 409 |
| GET | `/v1/documents/{doc_id}/download` | JWT Bearer | — | 302 redirect to pre-signed URL (or 403, 404) | 200, 302, 403, 404 |
| GET | `/v1/documents/{doc_id}/versions` | JWT Bearer | — | `{versions: [{version, created_at, created_by, size}], current_version}` | 200, 403, 404 |
| DELETE | `/v1/documents/{doc_id}` | JWT Bearer | — | `{document_id, status: "DELETED"}` | 200, 403, 404, 423 (locked due to legal hold) |
| GET | `/v1/documents/{doc_id}/audit` | JWT Bearer | — | `{audit_logs: [{action, timestamp, user_id, reason}]}` | 200, 403, 404 |

### Key Design Decisions

- **Multipart uploads**: For large files (> 100 MB), return presigned URLs for S3 multipart upload; client uploads parts in parallel.
- **Pre-signed URL generation**: GET `/documents/{doc_id}/download` returns 302 redirect with S3 pre-signed URL (15-minute expiry).
- **Version history**: GET `/documents/{doc_id}/versions` lists all versions (queryable, ordered by version DESC).
- **Audit endpoint**: GET `/documents/{doc_id}/audit` returns full access/deletion history (compliance).
- **Legal hold blocking**: DELETE returns 423 Locked if legal hold is active.
- **Auth**: JWT with `sub` (user_id) and `tenant_id` claims; access control checked for each operation.

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

**Failure mode if wrong:** If expiry is 1 day (for usability), URL can be leaked and shared; attacker downloads all your documents. If expiry is 1 minute, users on slow connections get frequent 403s.

---

### Trade-off 2: Soft Deletes vs Hard Deletes

**Chose:** Soft deletes (status = DELETED, S3 object kept).

**Gain:** Audit trail preserved; GDPR-compliant; reversible (customer can undelete if needed); cheap (S3 storage is <$0.01/GB/month for archived data).

**Lose:** Customer might expect "truly gone"; requires filtering (queries must WHERE status = 'ACTIVE'); orphaned S3 objects if metadata is accidentally lost.

**Failure mode if wrong:** If you hard-delete (remove S3 object) and customer requests GDPR access report 2 years later, you have no proof what documents existed or were deleted. Non-compliant. If you soft-delete and forget to filter queries, deleted documents show up in listings (confusing user experience).

---

### Trade-off 3: Redis Caching vs Direct DB Queries

**Chose:** Redis cache with 1-hour TTL + DB fallback.

**Gain:** At 3.5K reads/sec, Redis (100 microseconds per hit) scales easily; DB queries reduce to 350/sec (10% cache hit rate). Cache misses fall back to DB.

**Lose:** Stale metadata (up to 1 hour); if user updates a document's title, it takes up to 1 hour for the cache to reflect the change.

**Failure mode if wrong:** If you bypass cache, 3.5K reads/sec hit the database. Postgres can handle ~1K sustained queries; DB becomes bottleneck, queries slow to 100ms+, users see timeouts. If you cache without fallback and Redis is down, API is broken (no fallback to DB).

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
