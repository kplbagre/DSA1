# Handling Large Blobs — Quick Reference

> **Read this:** 30 min before an interview involving file upload, file serving, or storage systems.
> **Deep study:** `DeepDive/08-handling-large-blobs.md`

---

## 🎯 What kind of problem is this?

Use this pattern when: **users upload or download files, or the system produces large outputs** — file bytes must never touch your API servers.

Trigger words: "user uploads photo/video", "file storage", "design Google Drive / Dropbox", "profile photo", "video upload", "PDF export", "data export", "serve files to millions of users", "resume upload".

---

## 🧭 Decision Sequence

```
START: Your system needs to store or serve files

Step 1 → NEVER store file bytes in the database.
         Always use object storage (S3 / GCS / Azure Blob).
         Store only metadata (URL, size, content-type) in DB.

Step 2 → How big are the files?
         < 5MB, low traffic    → direct upload via API server is acceptable
         < 5GB, any traffic    → presigned URL (Strategy 2) — production default
         > 100MB or need resume → multipart upload (Strategy 3)
         > 5GB                  → multipart upload required (S3 single PUT limit)

Step 3 → Who reads the files?
         Public content, many users → CDN (Strategy 4)
         Private/per-user content   → presigned GET URLs (short TTL, 15–60 min)
         One-off downloads          → generate presigned GET URL on demand

Step 4 → Do you need to process the file after upload?
         Yes (resize, scan, transcode) → S3 event notification → SQS → worker
         This is Pattern 6 (Long-Running Tasks) triggered by the upload event.

Step 5 → Do you need to scan for viruses before the file is accessible?
         Post-upload scan: S3 event → Lambda → ClamAV → tag/quarantine
         Pre-acceptance scan: route through your server (only justified for this case)
```

---

## ⚡ Strategies at a Glance

| Strategy | Use when | Don't use when |
|---|---|---|
| **Direct Upload via API Server** | Files < 5MB, low traffic, need to validate first | Any real production file upload system |
| **Presigned URL** | Any file upload from real users — production default | Files > 5GB (use multipart) |
| **Multipart Upload** | Files > 100MB, unreliable networks, mobile, need resume | Files < 100MB (overhead not worth it) |
| **CDN for Serving** | Public content, many users, global reach | Private/per-user content; one-off downloads |

**Key numbers to remember:**
- S3 single PUT limit: 5GB (above this, multipart is required)
- Multipart minimum part size: 5MB (except last part)
- Presigned URL TTL: 15 min for uploads, 60 min for private downloads
- CDN cache hit: < 5ms; S3 origin fetch: 50–200ms
- CDN offload: 80–95% of reads for cacheable content — S3 is hit once per CDN PoP per TTL
- API server involvement in presigned pattern: zero bytes flow through it

---

## 🎨 Key Architecture Diagram

```
                      ┌─────────────────────────────────────────┐
   Client upload ────▶│       Metadata API                       │
   POST /presign-url   │  1. Auth: verify user can upload         │
                       │  2. Generate presigned PUT URL (15 min)  │
                       │  3. Record upload record (status:PENDING)│
                       │  4. Return presigned_url + upload_id     │
                       └──────────────────────────────────────────┘
                                        ↑ metadata only
                                        │ (no file bytes)
   ┌────────────────────────────────────┼──────────────────────────┐
   │                                    │                          │
   │  Client  ──PUT {presigned_url}──▶  S3 Bucket                 │
   │           (file bytes direct)      │  /uploads/{uuid}.mp4     │
   │                                    │  /images/{uuid}.jpg      │
   │  Client  ──POST /complete──────▶  Metadata API               │
   │                                    │  (HEAD verify → READY)   │
   │                                    │                          │
   │                        S3 Event ───▶ SQS ──▶ Worker           │
   │                        (on upload)          (resize, scan,    │
   │                                              transcode)       │
   └────────────────────────────────────────────────────────────── ┘

   ┌─────────────────────────────────────────────────────────────┐
   │  Serving Layer                                               │
   │                                                             │
   │  Public files:  client ──▶ CDN ──(miss)──▶ S3              │
   │  Private files: API generates presigned GET URL (60 min)    │
   │                 client ──▶ S3 directly with signed URL      │
   └─────────────────────────────────────────────────────────────┘

KEY INVARIANT:
   File bytes never pass through your API server.
   API server only handles metadata and URL generation (< 50ms responses).
   S3 moves the data. CDN caches it. DB records where it is.
```

---

## 🔬 Interview Q&A

### Q: "A user is uploading a 10GB video. How does your upload system handle it?"

> Multipart presigned upload. (1) Client calls `POST /uploads/init` → server calls `CreateMultipartUpload` on S3, returns `upload_id` and a target key. (2) Client splits the 10GB file into 200 × 50MB chunks. (3) For each chunk: client calls `POST /uploads/{id}/presign-part?part={n}` → server returns a presigned URL for that specific part. (4) Client uploads each part directly to S3. Can parallelize: 4 concurrent part uploads. (5) Client calls `POST /uploads/{id}/complete` with the list of `(part_number, ETag)` → server calls `CompleteMultipartUpload`. S3 assembles the 10GB object. (6) If connection drops at any point: client calls `ListParts` to find which parts succeeded, resumes from the first missing part.

---

### Q: "How do you prevent a user from uploading to another user's S3 path?"

> The presigned URL is the security boundary. The server generates a URL targeting a specific S3 key that includes the authenticated user's ID: `users/{authenticated_user_id}/uploads/{uuid}.jpg`. The presigned URL is cryptographically signed — the client cannot change the destination key without the signature becoming invalid. The URL is also time-limited (15 minutes). Even if a user extracts the presigned URL, it only works for the specific key and expires quickly. S3 never needs to know about your auth layer — the signature on the presigned URL is the proof.

---

### Q: "How do you serve private files securely? (user can only download their own statements)"

> Presigned GET URLs. When the user requests their file: (1) Your API authenticates the user. (2) Server generates a presigned GET URL for the specific S3 key, valid for 60 minutes. (3) Return the URL to the client. Client downloads directly from S3. The URL expires — if the user copies the URL and shares it, it stops working after 60 minutes. For shorter-lived content (receipts, financial data), use 5–15 minute TTLs. Never expose the raw S3 URL — always go through your API to generate a fresh presigned GET URL per request.

---

### Q: "How do you handle CDN cache invalidation when a user updates their profile photo?"

> Two approaches: (1) **Versioned filenames** (preferred) — when user uploads a new photo, generate a new UUID-based key (`users/abc/avatar-{uuid}.jpg`). Update the DB to point to the new key. Old CDN cache for the old URL becomes irrelevant — the URL is gone, not updated. New URL has no cache entry yet. Simple, no invalidation needed. (2) **Explicit invalidation** — keep the same URL (`users/abc/avatar.jpg`), call the CDN invalidation API when updated. Adds latency (invalidation propagates in 1–30 seconds depending on CDN), has cost per invalidation at scale. Use versioned filenames wherever possible.

---

### Q: "How do you scan uploaded files for viruses without routing them through your server?"

> S3 event-driven scan. (1) Configure S3 to emit an event (to SQS or Lambda trigger) on every `ObjectCreated`. (2) Lambda (or dedicated worker) picks up the event, downloads the file from S3, runs ClamAV or a commercial AV SDK. (3) On clean: tag the S3 object with `scan-status: clean`. Your API only returns presigned GET URLs for objects tagged `clean`. (4) On infected: move to a quarantine bucket, tag `scan-status: infected`, alert ops. The file never reaches users. The key insight: you don't need to scan at upload time — you scan asynchronously and only make the file accessible after it passes. This is a natural fit with Pattern 6 (Long-Running Tasks post-upload processing).

---

### Q: "A user abandons a multipart upload halfway. What happens to the partial parts in S3?"

> Orphaned parts accumulate and incur storage costs. S3 charges for partial parts even if `CompleteMultipartUpload` is never called. Prevention: (1) Set an S3 Lifecycle Policy to automatically `AbortIncompleteMultipartUpload` after N days (e.g., 7 days). S3 cleans up the parts automatically. (2) Your application can call `AbortMultipartUpload` explicitly when it detects an abandoned upload (e.g., user session ended, upload_id not seen for 1 hour). Lifecycle policy is the safety net; explicit abort is the fast path.

---

### Q: "How do you implement a resumable upload on a mobile app with unreliable connectivity?"

> Multipart upload with part tracking. (1) On upload start: call your API to get an `upload_id` from S3. Persist `upload_id` locally on the device. (2) Upload parts sequentially or in parallel. After each successful part, persist `(part_number, ETag)` locally. (3) On disconnect: the upload stops. Partial parts already uploaded are preserved in S3. (4) On reconnect: call `ListParts(upload_id)` to get parts S3 already has. Resume from the first missing part. (5) Complete as normal when all parts uploaded. The mobile app only needs to persist `upload_id` and successful `(part_number, ETag)` pairs between sessions.

---

### Q: "Design the file storage system for Google Drive."

> (1) **Upload:** Client uploads directly to GCS via resumable upload API (Google's version of multipart). Files split into 256KB blocks — only changed blocks re-uploaded on edit (deduplication). Each file version gets a unique content-addressed hash. (2) **Storage:** GCS stores the raw bytes. Drive DB stores: file metadata (name, owner, permissions), file version tree, content hash → GCS object mapping. (3) **Serving:** Private files: short-lived presigned GET URLs generated per download request. Shared-publicly files: CDN-cached. (4) **Dedup:** Files with identical content (same SHA-256 hash) share the underlying GCS object — stored once, referenced many times. (5) **Processing:** Document conversion (e.g., `.docx` → Google Docs format) happens via async worker triggered on upload (Pattern 6). Thumbnail generation similarly async.

---

### Q: "Your API is returning the S3 URL directly to users instead of presigned URLs. Why is this a problem?"

> Two problems: (1) **Access control** — raw S3 URLs never expire. If a user shares the URL or it ends up in a log file, it's permanently accessible by anyone with the URL. Private files are no longer private. Presigned GET URLs expire in minutes and are bound to the specific object. (2) **Object moves** — if you ever need to move objects between buckets, rename keys, or migrate to a different storage provider, all the raw URLs you've handed out become broken. Presigned URLs are generated on-demand from your DB — you can change the underlying storage key without breaking the client-facing URL. Always serve files through a URL generation layer, never expose raw S3 URLs.

---

## ⚠️ Anti-patterns (don't say these)

- **Routing file bytes through your API server** — 50 concurrent 1GB uploads = 50GB in-flight through your API; presigned URLs eliminate this entirely
- **Storing binary files as BLOBs in the database** — 500MB video in Postgres `BYTEA` kills DB buffer pool; can't CDN it; can't resume it; store in S3, URL in DB
- **Not setting `Content-Disposition` on served files** — without it, browser saves file as raw UUID key (the S3 key); set `Content-Disposition: attachment; filename="report.pdf"` on the presigned GET URL

---

## 🧩 Common Interview Problems

| Problem | Key Design Choice | Notes |
|---|---|---|
| Design Google Drive / Dropbox | Presigned URLs + versioned keys + CDN | Dedup via content-hash; versioned blocks |
| Design YouTube Upload | Multipart presigned upload + transcode worker | S3 event → Kafka → GPU workers |
| Design Instagram | Presigned URL + CDN for serving images | UUID key per upload, CDN for public images |
| Design S3 / Object Storage | Content-addressed chunks + erasure coding | The storage system itself |
| Design a Data Export System | Async job → large CSV/ZIP → S3 → presigned GET URL | GDPR "download my data" |
| Design Netflix CDN | CDN hierarchy (multi-tier) + object storage origin | Videos cached at edge, not re-served from origin |

---

## 🔗 Full notes

`DeepDive/08-handling-large-blobs.md` — multipart mechanics, CDN invalidation strategies, full failure mode Q&A
