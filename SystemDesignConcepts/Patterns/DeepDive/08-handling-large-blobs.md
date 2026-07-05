# Pattern Deep Dive: Handling Large Blobs

> **Read this when:** You need to understand how to store, serve, and process files too large for a database — images, videos, PDFs, exports, backups — without routing them through your API servers.
> **Pre-interview refresh:** Use `Reference/08-handling-large-blobs.md` (5 min).

---

## 🎯 What Problem Does This Pattern Solve?

A user uploads a file. Or your system needs to serve files to millions of users. Or a job produces a large output (PDF report, transcoded video, data export). The instinct is to treat files like any other data — store in the DB, retrieve via API. This fails at any meaningful scale.

Classic large blob scenarios:
- **User uploads:** Profile photo, resume, product image, video upload. Sizes: 100KB–2GB.
- **System outputs:** Generated PDF reports, data exports, transcoded videos. Sizes: 1MB–10GB.
- **User downloads:** 10M users each downloading their monthly statement. High read concurrency.
- **Processing pipelines:** Upload image → resize → watermark → thumbnail. File goes through multiple stages.

The fundamental problem: **databases are row stores, not file stores.** A 500MB video in a Postgres BYTEA column:
- Kills DB memory (buffer pool fills with binary blobs, evicting actual query data)
- Blocks DB connection for the entire duration of the upload/download
- Can't be served from a CDN
- Can't be resumed if the connection drops

And routing files through API servers is equally bad:
- Each upload holds a server connection open for minutes
- 100 concurrent 500MB uploads = 50GB of in-flight data in your server's memory
- API server becomes a bandwidth bottleneck

---

## 💡 Core Insight

**Files belong in object storage. Everything else belongs in the database.**

Store the file in S3 (or GCS / Azure Blob). Store only the metadata (file URL, size, content-type, owner) in the DB. Never route file bytes through your API servers — generate presigned URLs and let clients talk directly to S3.

> **KEY INSIGHT:** "Your API server should never touch file bytes. Its job is to hand out time-limited URLs. Let S3 move the data. The DB stores the map: 'this user's avatar is at s3://bucket/users/abc/avatar.jpg'."

---

## 🗂️ The 4 Strategies

---

### Strategy 1 — Direct Upload via API Server (Baseline — Know This to Reject It)

Client uploads file to your server, server writes to disk or S3, server returns URL.

**When to use:**
- Files < 5MB, very low traffic (internal tools, admin panels)
- You need to validate/scan the file before it reaches storage (antivirus, format check)

**When NOT to use:**
- Any production file upload system with real users
- Files > 5MB or concurrent uploads from many users
- You care about server resource utilization

**Steps in plain English:**
1. **Client POSTs** — Client sends `multipart/form-data` request to your API server.
2. **Server receives** — Server buffers the entire file in memory or streams to disk. Server connection held open until upload completes.
3. **Server writes** — Server writes file to S3 (or disk). Server acts as middleman, re-uploading the bytes it just received.
4. **Server responds** — Returns the file URL to the client.

```
Client ──POST /upload (5GB video)──▶ API Server ──PUT──▶ S3
         (holds TCP connection open)    (holds memory/disk)
         (5GB flows through your server twice — receive + re-upload)

❌ Bad: every concurrent upload consumes server memory + bandwidth
```

---

### Strategy 2 — Presigned URL (Direct-to-Object-Storage Upload)

🧠 **Mental model:** Twitter/X profile photo upload — you tap "change photo," the app asks Twitter for a signed URL, uploads your photo directly to S3, then tells Twitter "done." Twitter's server never handles a single byte of your photo.

Server generates a time-limited, pre-authenticated URL. Client uploads directly to S3. API server never sees the file bytes.

**When to use:**
- Files of any size from 1KB to 5GB (S3 single PUT limit)
- Web and mobile clients uploading user-generated content
- This is the production standard for any real file upload system

**When NOT to use:**
- Files > 5GB (use multipart upload — Strategy 3)
- You need to validate the file before it lands in storage (scan first, then generate URL to final destination)

**Steps in plain English:**
1. **Client requests URL** — Client sends `POST /uploads/presigned-url` with filename and content-type. No file bytes sent yet.
2. **Server generates URL** — Server creates a presigned PUT URL targeting a specific S3 key (e.g., `users/{user_id}/uploads/{uuid}.jpg`). URL expires in 15 minutes. Server records the pending upload in DB (status: PENDING).
3. **Client uploads directly** — Client sends the file bytes directly to S3 using the presigned URL (`PUT {presigned_url}`). Server is not involved.
4. **Client confirms** — Client tells your server the upload is complete (`POST /uploads/{id}/complete`).
5. **Server confirms with S3** — Server checks S3 that the object actually exists (prevents fake confirmations). Updates DB record to READY.

**State synchronization gap:** There's a window where S3 has the object but DB still shows PENDING (client uploaded but didn't call /complete — crashed, network failure). Two fixes: (1) **S3 event notification** — configure S3 `ObjectCreated` event → SQS/Lambda. Lambda independently marks DB record READY without relying on the client's /complete call. (2) **Reconciliation job** — periodically scan PENDING records older than N minutes, HEAD the S3 key. If it exists → mark READY. If not → mark EXPIRED. Both approaches close the gap; event notification is real-time, reconciliation is the safety net.

```
Client                    Your API Server              S3
  │                              │                      │
  │──POST /uploads/presigned-url─▶│                      │
  │  {filename:"video.mp4"}       │                      │
  │                               │──generate presigned──▶
  │◀──{presigned_url, upload_id}──│                      │
  │                               │                      │
  │──────────PUT {presigned_url}────────────────────────▶│
  │   (file bytes go directly to S3, bypassing your API) │
  │                               │                      │
  │──POST /uploads/{id}/complete─▶│                      │
  │                               │──HEAD {s3_key}───────▶│
  │                               │◀──200 OK─────────────│
  │◀──{url: "s3://...", status: READY}──│               │

KEY: API server never touches file bytes. Only metadata flows through it.
```

---

### Strategy 3 — Multipart Upload (Files > 100MB)

🧠 **Mental model:** YouTube video upload from a mobile phone — you're uploading a 2GB video. Connection drops at 70%. Multipart: only re-upload the last incomplete chunk, not the whole file. Netflix and Instagram use this for all mobile uploads.

Split large files into chunks (5MB–500MB each). Upload each chunk independently. S3 assembles them on completion. Resumable: if chunk 7 fails, re-upload chunk 7 only.

**When to use:**
- Files > 100MB (multipart threshold)
- Unreliable networks or mobile uploads (can resume after disconnect)
- Want to upload chunks in parallel (faster total upload time)
- Files > 5GB (required — S3 single PUT limit)

**When NOT to use:**
- Files < 100MB (overhead of initiating multipart, tracking parts, completing is not worth it)
- Simple use case where a failed upload can just be restarted from scratch

**Steps in plain English:**
1. **Initiate** — Call `CreateMultipartUpload` → get `upload_id`. This tells S3 you're starting a multipart upload to a specific key.
2. **Split** — Client splits file into N parts (minimum 5MB each, except the last). Each part gets a sequential `part_number` (1-based).
3. **Upload parts** — Upload each part with `UploadPart(upload_id, part_number, bytes)`. Can be done in parallel. Each part returns an `ETag`.
4. **Track ETags** — Record each `(part_number, ETag)` pair. These are needed to assemble the final object.
5. **Complete** — Call `CompleteMultipartUpload(upload_id, [(part_number, ETag), ...])`. S3 assembles all parts into the final object. Atomic — the object either appears complete or not at all.
6. **Abort on failure** — If upload is abandoned, call `AbortMultipartUpload`. S3 cleans up partial parts (or set a lifecycle policy to auto-abort after N days).

```
                    File: 1.5GB video
                    Parts: 300 × 5MB chunks

Chunk 1 ──────────────────────▶ S3 (ETag: "abc")
Chunk 2 ──────────────────────▶ S3 (ETag: "def")   ← parallel uploads
Chunk 3 ──────────────────────▶ S3 (ETag: "ghi")
   ...
Chunk 300 ────────────────────▶ S3 (ETag: "xyz")

CompleteMultipartUpload(upload_id, [(1,"abc"), (2,"def"), ..., (300,"xyz")])
                                    ▼
                            S3 assembles → final 1.5GB object

Resume example:
  Chunks 1-6 uploaded successfully.
  Connection drops.
  ListParts(upload_id) → returns parts 1-6 with their ETags.
  Resume from chunk 7.
```

---

### Strategy 4 — CDN for Serving (High-Read Public Content)

🧠 **Mental model:** Amazon product images — `iPhone 15 black front.jpg` is requested by 10 million shoppers per day. S3 stores it once. CloudFront caches it at 400+ PoPs worldwide. S3 sees ~1 request per PoP per cache TTL, not 10 million.

Sit a CDN (Content Delivery Network — a globally distributed network of edge servers that caches your files close to users, so a request from Tokyo hits a Tokyo PoP instead of your S3 bucket in us-east-1) in front of your object storage. First request goes to S3, CDN caches the response at the edge. Subsequent requests served from CDN edge — no S3 cost, near-zero latency.

**When to use:**
- Public or semi-public content served to many users (product images, profile photos, static assets, videos)
- Files accessed repeatedly (same image requested by 10K users)
- Global user base (serve from nearest CDN PoP)

**When NOT to use:**
- Private/sensitive content where each access must be authorized (presigned GET URLs instead)
- One-off downloads (data exports, personal files) — CDN won't cache effectively, adds complexity

**Steps in plain English:**
1. **Upload to S3** — File stored at `s3://bucket/images/product-abc.jpg`.
2. **CDN origin** — Configure CDN (CloudFront, Fastly) to use S3 bucket as origin.
3. **Serve via CDN URL** — Expose `cdn.yoursite.com/images/product-abc.jpg` to clients. CDN fetches from S3 on first miss, caches at edge.
4. **Cache invalidation** — When file changes, either (a) use versioned filenames (`product-abc-v2.jpg`) — no invalidation needed, or (b) call CDN invalidation API to purge the path.

```
User (Tokyo) ──▶ CDN Edge (Tokyo) ──cache miss──▶ S3 (us-east-1)
                        │◀──────── file ──────────────│
                        │── cache for 24h ─┐
User (Tokyo) ──▶ CDN Edge (Tokyo) ──▶ served from cache (< 5ms)
User (Seoul) ──▶ CDN Edge (Seoul) ──cache miss──▶ S3 (us-east-1)
                        │◀──────── file ──────────────│
                        │── cache for 24h ─┐
User (Seoul) ──▶ CDN Edge (Seoul) ──▶ served from cache

KEY: S3 is hit once per CDN PoP per cache TTL. Not once per user.
```

---

## 🧭 Decision Sequence

```
START: Your system needs to store or serve files

Step 1 ── NEVER store file bytes in the database.
          Always use object storage (S3 / GCS / Azure Blob).
          Store only metadata (URL, size, content-type) in DB.

Step 2 ── How big are the files?
          < 5MB, low traffic    → direct upload via API server is acceptable
          < 5GB, any traffic    → presigned URL (Strategy 2) — production default
          > 100MB or need resume → multipart upload (Strategy 3)
          > 5GB                  → multipart upload required (S3 single PUT limit)

Step 3 ── Who reads the files?
          Public content, many users → CDN (Strategy 4)
          Private/per-user content   → presigned GET URLs (short TTL, 15–60 min)
          One-off downloads          → generate presigned GET URL on demand

Step 4 ── Do you need to process the file after upload?
          Yes (resize, scan, transcode) → S3 event notification → SQS → worker
          This is Pattern 6 (Long-Running Tasks) triggered by the upload event.

Step 5 ── Do you need to scan for viruses before the file is accessible?
          Quarantine bucket pattern (production default):
            Presigned URL targets a quarantine S3 bucket (not the public bucket).
            S3 ObjectCreated event → Lambda/worker → ClamAV scan.
            Clean: COPY object to public bucket → mark DB READY → delete from quarantine.
            Infected: alert ops → delete from quarantine → mark DB REJECTED.
            Users can only access objects in the public bucket — infected files never reach it.
          Pre-acceptance scan: route file through your server (only justified when you
          need to validate before accepting; adds server bandwidth/memory cost).
```

---

## 🎨 Visual — Full Large Blob Architecture

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

## ⚠️ Anti-patterns

- **Routing file bytes through your API server.** Every concurrent upload holds a server connection open for the duration of the transfer. 50 concurrent 1GB uploads = 50GB of in-flight data coursing through your API layer. Bandwidth, memory, and connection pool all become bottlenecks. The fix is presigned URLs — the API's only job is to sign the URL, not to move bytes. One 30ms endpoint call replaces a 30-second file transfer through your infrastructure.

- **Storing binary files as BLOBs in the database.** A 500MB video in a Postgres `BYTEA` column evicts 500MB of working data from the DB buffer pool. The DB connection is blocked for the entire download. You can't put a DB BLOB behind a CDN. You can't resume a partial BLOB download. The DB is not a file store. Store the file in S3, store the S3 key in the DB, return a presigned URL to the client.

- **Not setting `Content-Disposition` on served files.** `Content-Disposition` (the HTTP response header that tells the browser whether to display a file inline or prompt to save it, and what filename to suggest to the user) is essential. When a user downloads `GET /files/abc123`, the response should include `Content-Disposition: attachment; filename="Q4-2024-report.pdf"`. Without it, the browser either displays a UUID-named file in the tab or prompts the user to save as `abc123` (the raw S3 key). Set `Content-Disposition` with the original filename when generating presigned GET URLs: `response-content-disposition=attachment%3B+filename%3D%22report.pdf%22` as a query param on the presigned URL.

---

## 🗺️ Problems Map

| Interview Problem | Why Large Blobs Applies | Key Design Choice |
|---|---|---|
| Design Google Drive / Dropbox | User file storage and sharing | Presigned URLs + versioned keys + CDN |
| Design YouTube Upload | 10GB video upload from mobile | Multipart presigned upload + transcode worker |
| Design Instagram | User photo upload at scale | Presigned URL + CDN for serving images |
| Design S3 / Object Storage | The storage system itself | Content-addressed chunks + erasure coding |
| Design a Document Generation Service | PDF export (per Pattern 6) | Worker stores PDF in S3, returns presigned GET URL |
| Design a Data Export System | GDPR "download my data" | Async job → large CSV/ZIP → S3 → presigned GET URL |
| Design Netflix CDN | Serve videos to 200M users | CDN hierarchy (multi-tier) + object storage origin |

---

## 🔗 Concept Notes — Go Here for "How It Works"

- **S3 / object storage fundamentals** (buckets, keys, consistency model, storage classes) → `../../Foundations/Data-Fundamentals/14-document-blob-storage.md`
- **CDN fundamentals** (edge caches, TTL, invalidation, push vs pull) → `../../Foundations/Networking-Essentials/cdn-fundamentals.md`
- **Post-upload processing** (S3 event → worker → transcode/resize) → `06-long-running-tasks.md`
- **Presigned URL security model** (HMAC signing, expiry, scope restriction) → `../../Foundations/Security/presigned-urls-and-access-control.md`

---

## 🔄 Changelog

| Date | Change |
|---|---|
| July 2026 | Note created. Batch 3 of the pattern deep dives. |
| July 2026 | Added 🧠 mental model anchors per strategy. Added state synchronization gap + reconciliation to Strategy 2. Expanded Step 5 with quarantine bucket abuse prevention pattern. |
