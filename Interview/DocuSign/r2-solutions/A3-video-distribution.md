# A3 — Architect a Worldwide Video Distribution System

> **Read `solution-notes-standards.md` first.** This file is a 60-minute interview answer framework, not a concept reference. Concept-level depth lives in `SystemDesignConcepts/`.

---

## 📚 Prerequisites — Study These First

Before reading the solution, make sure you can explain the bolded items below from memory.

| Concept | File in `SystemDesignConcepts/` | Why you need it for this question |
|---|---|---|
| **CDN / edge caching** | `Production-Grade/Performance-Optimization/28-cdn-edge-caching.md` | The entire delivery architecture — CDN pre-warming, origin shielding, and cache hit ratio are the core scalability answers; without this you cannot answer "how do 1M viewers watch the same video simultaneously" |
| **Blob / document storage** | `Foundations/Data-Fundamentals/14-document-blob-storage.md` | S3-style object storage, pre-signed URLs, multipart uploads for large files — the upload path and sealed storage pattern |
| **Handling large blobs** | `Patterns/DeepDive/08-handling-large-blobs.md` | Chunked upload, resumable upload, range-request streaming — the complete large-file pattern for upload and playback |
| **Long-running tasks** | `Patterns/DeepDive/06-long-running-tasks.md` | Transcoding is async and takes minutes — know the job queue + worker + status polling pattern and why it must not block the upload API |
| **Job scheduling at scale** | `Production-Grade/System-Design-Patterns/47-job-scheduling-at-scale.md` | Transcoding job queue design, worker scaling, priority queues for live vs VOD content |
| **Scaling reads** | `Patterns/DeepDive/01-scaling-reads.md` | Video serving is almost 100% reads — know the CDN + origin read replica + cache strategy to handle global read amplification |

---

## 🎯 What Is This System?

**In plain English:** A video distribution system accepts large video uploads, transcodes them into multiple quality levels, and delivers them through a global CDN so any viewer — anywhere in the world — can start watching within seconds at the best quality their connection supports.

**Real-world examples:**

| System / Company | What they built |
|---|---|
| **YouTube** | 500 hours of video uploaded every minute; 2B daily viewers |
| **Netflix** | 15% of global internet bandwidth at peak; 17,000+ device types |
| **Twitch** | Live streaming + VOD for gaming content |
| **TikTok** | Short-form video with aggressive CDN pre-warming for viral content |
| **Vimeo** | High-quality video hosting for creators and enterprises |
| **Instagram Reels** | Short-form video built on Facebook's CDN infrastructure |

**Core user journey:** Creator uploads a 2GB video → within 5 minutes, viewers in São Paulo, Frankfurt, and Singapore can all stream it smoothly — the player switches automatically from 360p to 1080p as their available bandwidth allows.

**Why it's hard to build at scale:** At 1 million concurrent viewers × 4 Mbps per stream, peak egress reaches 4 Tbps — no single origin server can sustain this; only a global CDN with 200+ edge nodes can; and the CDN must be pre-warmed before the first viewer arrives or the initial click triggers a thundering-herd cache miss that reaches the origin.

---

## 🧠 How to Use This File

**This file is an instantiation of the solution-notes-standards.md framework.** Every section below maps to one phase of the 60-minute delivery rhythm.

**Before your interview:**
1. Read `solution-notes-standards.md` once to understand the format (15 min)
2. Memorize the 6 Memory Anchors below (2 min)
3. Read Sections 2, 7, 10, 11, 12, 13 in full (30 min)
4. The morning of: re-read Section 15 (TL;DR) only

**The time budget:**
- Minutes 0–5: Sections 1–2 (Opener + Clarifying questions)
- Minutes 5–10: Sections 3–4 (Requirements + Scale estimation)
- Minutes 10–25: Sections 5–6 (Requirements variation + HLD 3-stage progression)
- Minutes 25–40: Section 7 (Deep dives: 2–3 riskiest components)
- Minutes 40–48: Section 10 (Trade-offs: exactly 3, with failure modes)
- Minutes 48–52: Section 11 (DocuSign-specific depth)
- Minutes 52–60: Section 12 (Interviewer probes — Tier 1/2/3 prepared answers)

**Stay on this schedule.** If you're at minute 42 and still in the CDN deep dive — stop. Pivot to trade-offs. The rubric rewards trade-off reasoning over implementation completeness.

---

## 💾 Memory Anchors (Memorize These 6)

Before every interview, say these 6 sentences to yourself (takes 30 seconds):

1. **"CDN is the answer to geography."** — Every global latency problem is solved at the edge. Name PoP (Point of Presence), cache hit ratio, and TTL in the first 10 minutes.
2. **"Upload and delivery are two different systems."** — Ingestion pipeline (upload → transcode → store) is separate from the delivery path (CDN → client). Don't conflate them.
3. **"Transcode once, serve everywhere."** — Never serve raw upload to clients. Transcoding normalizes format, generates multiple bitrates, and protects origin from direct traffic.
4. **"HLS and DASH split content into chunks."** — Adaptive bitrate (ABR) is not a magic switch; it requires chunked manifests. Know what HLS/DASH means at the file level.
5. **"Cold cache is a first-delivery problem."** — Pull CDN is great at steady state; it fails at launch (every edge has a cache miss). Pre-push (proactive warming) solves this.
6. **"Multi-region S3 is the origin for CDN edges."** — CDN edges don't pull from a single US S3 bucket. Replicate to regional S3 buckets so EU edges pull from eu-west, APAC edges pull from ap-southeast.

**Bonus — DocuSign pivot (critical for Section 11):**
- "DocuSign doesn't distribute videos — it distributes signed PDFs, envelope templates, and signing ceremony assets. The CDN architecture is identical; the content type is different."

---

## 🔑 Technology Quick Reference

> **Read this once before the file.** These are the only acronyms and technologies you need to know cold for this question.

| Term | Plain-English meaning |
|---|---|
| **CDN** (Content Delivery Network) | A global network of edge servers that cache your content close to the user. Instead of every viewer hitting your origin server in US-East, they hit a nearby edge node — Mumbai, Frankfurt, São Paulo. |
| **PoP** (Point of Presence) | One physical CDN edge location. Cloudflare has 300+ PoPs globally. Think of each PoP as a local cache. |
| **Origin** | The source of truth that CDN edges fetch from on a cache miss — in this design, multi-region S3 buckets. Not your app server. |
| **Transcoding** | Converting one raw uploaded video into multiple formats and resolutions (360p, 720p, 1080p). You never serve the raw upload directly to viewers. |
| **Rendition** | One specific output of transcoding — e.g., "the 720p rendition" is the 720p version of the video. One upload produces 4-5 renditions. |
| **HLS** (HTTP Live Streaming) | Apple's standard that splits a video into 2–10 second chunks and generates a manifest file listing all chunks. The player downloads chunks sequentially and switches quality tiers between chunks. |
| **DASH** (Dynamic Adaptive Streaming over HTTP) | Google/MPEG's equivalent of HLS. Same concept — chunked delivery with quality switching. Most players support both. |
| **ABR** (Adaptive Bitrate) | The automatic quality-switching behavior enabled by HLS/DASH — starts at 360p on slow 3G, switches to 1080p once bandwidth is confirmed. Not a magic switch; requires HLS/DASH manifest + multiple renditions. |
| **RTMP** (Real-Time Messaging Protocol) | The protocol live-streaming software (OBS, Streamlabs) uses to push live video to an ingest server. Only relevant for live streaming — today's design is on-demand only. |
| **Pre-warming** | Proactively pushing content to CDN edge nodes before users request it. Solves the thundering-herd cold-cache problem — without it, every edge misses on a video's first request and hammers the origin simultaneously. |
| **VOD** (Video on Demand) | Pre-recorded content users can watch any time. Opposite of live streaming. This design is VOD. |
| **TTL** (Time To Live) | How long a CDN edge caches a piece of content before re-fetching from origin. Long TTL = fewer origin requests, stale content risk. Short TTL = fresh content, more origin load. |
| **Multi-region S3** | Replicating S3 buckets across regions (us-east-1, eu-west-1, ap-southeast-1) so CDN edges in each region pull from a nearby origin instead of all hitting one US bucket. |

---

## Section 0 — Question Identity Card

| | |
|---|---|
| **Question** | Architect a Worldwide Video Distribution System |
| **Interview Type** | Type A — System Design (Infrastructure) |
| **Confirmed or Likely** | ⭐ Confirmed asked (DocuSign official prep guide PDF p.3 — "Architect a worldwide video distribution system" listed explicitly) |
| **Concept notes prerequisite** | `SystemDesignConcepts/Foundations/Data-Fundamentals/14-document-blob-storage.md`, `SystemDesignConcepts/Foundations/Performance-and-Scale/03-caching.md`, `SystemDesignConcepts/Core-Architecture/Distributed-Systems/40-multi-region-geo-failover.md`, `SystemDesignConcepts/Production-Grade/System-Design-Patterns/47-job-scheduling-at-scale.md` |
| **DocuSign-specific angle** | DocuSign does not stream video — this question tests CDN + distributed storage architecture that maps directly to DocuSign's actual problem: distributing signed envelope PDFs, signing ceremony JavaScript bundles, and envelope thumbnail previews to 1B+ signers in 180 countries. Name this pivot in Section 11. The same origin + CDN + multi-region S3 architecture applies; the content is documents, not video. |
| **Time budget** | 5 min clarify → 5 min estimate → 15 min HLD → 15 min deep dive → 8 min trade-offs → 7 min Q&A buffer |

---

## Section 1 — 🚀 The One-Sentence Opener

> "Before I draw anything, let me ask a few clarifying questions — specifically about scale (how many concurrent viewers), the content model (live vs on-demand), upload-to-playback latency requirements, and whether we need adaptive bitrate — because those drive completely different architectures."

Then immediately go to Section 2. Do NOT start drawing.

---

## Section 2 — 🔍 Clarifying Questions Script (Minutes 0–5)

**Say this out loud after your opener:**
> "I have a few clarifying questions to make sure I design the right system..."

---

**Q: "Is this live streaming (real-time broadcast) or video-on-demand (pre-recorded content)?"**
- Why ask: live streaming requires ultra-low latency (RTMP ingest, HLS with 2s segments, < 10s end-to-end delay), chunked upload, and no pre-processing time. On-demand allows async transcoding pipeline, batch optimization, and longer CDN TTLs. They share CDN delivery but diverge completely at ingestion.
- Live → RTMP ingest server, real-time segment generation, HLS Live. Complex, different architecture.
- On-demand → upload → transcode → store → CDN. This is today's design.

**Q: "What is the expected scale — how many concurrent viewers globally, and how many uploads per day?"**
- Why ask: concurrent viewers determine CDN bandwidth and PoP count; uploads per day determine transcoding pipeline capacity. These are the two numbers that drive every sizing decision.
- 10K concurrent viewers → single-region CDN (Cloudflare single-region plan) is sufficient.
- 10M concurrent viewers → global CDN with multi-region S3 origin replication is mandatory.

**Q: "Do we need adaptive bitrate streaming (automatically adjusting quality based on network speed)?"**
- Why ask: adaptive bitrate (ABR — the technique of breaking video into short chunks and switching quality tiers mid-playback based on current bandwidth) requires HLS or DASH manifest generation during transcoding. This adds complexity to the transcoding pipeline. Without ABR, a single bitrate is simpler but gives poor experience on mobile/3G.
- ABR required → HLS or DASH manifests, multiple renditions (360p, 720p, 1080p, 4K), chunk-based delivery.
- ABR not required → single MP4, simpler pipeline, range-request-based seeking.

**Q: "What is the acceptable upload-to-playback latency — how long after upload should the video be watchable?"**
- Why ask: this determines whether transcoding is on-demand (transcode when first requested — fast to upload, slow to first view) or eager (transcode immediately after upload — slower pipeline, faster viewer experience).
- Minutes → eager transcoding on upload.
- Seconds → too fast for batch transcoding; live streaming architecture.
- Hours OK → deferred transcoding queue for cost optimization.

**Q: "Is content globally public or are there geographic access restrictions (geo-blocking)?"**
- Why ask: geo-blocking (restricting content to specific countries) requires CDN-level policy rules and signed URLs with geo-scope; without it, global CDN can serve any edge freely.
- Geo-blocking required → CDN edge rules, signed URL with allowed country codes.
- Public globally → no restriction logic needed; simpler.

**Q: "How is content protected — public URLs, or signed/authenticated URLs per viewer?"**
- Why ask: public URLs can be cached aggressively at CDN (high hit ratio); signed URLs expire (lower CDN hit ratio because each URL is unique). This is the most common DocuSign angle — signed PDFs must use pre-signed URLs, not publicly cacheable paths.
- Public → long CDN TTL, maximum cache efficiency.
- Signed URL per viewer → short TTL, CDN still caches the underlying object but validates the signature.

---

## Section 3 — 📋 Requirements

### Functional Requirements (what the system does)

- Users can upload videos; the system stores and transcodes them into multiple bitrates and formats (HLS with 360p, 720p, 1080p renditions)
- Viewers worldwide can stream videos with adaptive bitrate quality (ABR) — playback adjusts quality automatically to available bandwidth
- Upload-to-playback latency: < 5 minutes (eager transcoding after upload)
- Supported clients: web (HTML5 HLS.js), iOS (native HLS), Android (ExoPlayer / DASH)
- Content is globally public (no geo-blocking, no per-viewer signed URLs — simplification; note how to extend in Section 11)

**Out of scope today:**
- Live streaming (real-time broadcast)
- DRM (Digital Rights Management) encryption
- Content moderation (NSFW detection, copyright scanning)
- Per-viewer analytics (play events, buffering ratio)

### Non-Functional Requirements

- **Scale:** 10M DAU, 60% international (outside US); 100K video uploads/day; 1M concurrent viewers at peak
- **Latency:** Time-to-first-byte (TTFB) for video chunks: P99 < 50ms globally (CDN edge must serve, not origin)
- **Availability:** 99.99% — video playback must be available even during origin failure (CDN serves cached content)
- **Throughput:** 1M concurrent viewers × 4 Mbps (1080p) = 4 Tbps peak egress — only a global CDN can sustain this
- **Storage durability:** 99.999999999% (11 nines) — S3 standard durability; videos cannot be lost
- **Upload reliability:** resumable uploads (large files fail mid-upload on flaky connections; must resume from offset)

---

## Section 4 — 🔢 Scale Estimation (Minutes 5–8)

```
Content volume:
  - Uploads/day: 100K videos
  - Average raw upload size: 2 GB (1080p, 60-minute video)
  - Total raw upload ingest: 100K × 2 GB = 200 TB/day
  - After transcoding (3 renditions: 360p + 720p + 1080p):
      360p:  ~200 MB  (10% of 1080p)
      720p:  ~800 MB  (40% of 1080p)
      1080p: ~2,000 MB (100% of 1080p)
    Total per video: ~3 GB stored
  - Total transcoded storage/day: 100K × 3 GB = 300 TB/day
  - 1 year: 300 TB × 365 = ~110 PB — S3 at scale, tiered storage required

Transcoding capacity:
  - 100K uploads/day = ~1.16/sec
  - Transcoding time: 1 minute of video takes ~30 CPU-seconds to transcode
  - 60-minute video = 1,800 CPU-seconds = 30 CPU-minutes per video
  - At 100K videos/day with 5-minute SLO: need ~600 parallel transcode workers
  - In practice: use auto-scaling transcode workers, target 50% utilization → steady state ~300 workers

CDN bandwidth:
  - 1M concurrent viewers × 4 Mbps = 4 Tbps peak
  - Only Cloudflare, Akamai, Fastly, AWS CloudFront can sustain 4 Tbps globally
  - CDN cache hit ratio target: >95% (only 5% of requests hit origin/S3)
  - Origin bandwidth: 4 Tbps × 5% = 200 Gbps — multi-region S3 distributes this

Storage math:
  - Hot content (< 30 days): 110 PB/year × (30/365) ≈ 9 PB on S3 Standard
  - Warm content (30–180 days): ~55 PB on S3 Infrequent Access
  - Cold content (> 180 days): remainder on S3 Glacier

Key conclusions:
  - "At 4 Tbps peak egress, CDN is not optional — it is the architecture."
  - "At 100K uploads/day with 5-minute transcoding SLO, we need ~300 parallel workers — auto-scaling job queue."
  - "Storage grows to 110 PB/year — tiered storage (S3 Standard → Infrequent Access → Glacier) by recency."
```

---

## Section 5 — 🔄 Requirements Variation Table ⭐ KEY SECTION

| If the interviewer says... | Your architecture changes to... | The reasoning |
|---|---|---|
| "10K concurrent viewers, US only" | Single-region S3 + single CloudFront distribution; no multi-region replication; no push CDN | At 10K viewers, CDN pull with cold-start on first request is acceptable; complexity of multi-region is unjustified |
| "1B concurrent viewers (YouTube scale)" | Multi-CDN (Akamai + Cloudflare + Fastly) for failover; tiered PoP (mega PoP + regional PoP + edge PoP); P2P-assisted delivery for reducing CDN egress cost | Single CDN vendor can fail globally; P2P offloads ~30% of CDN bandwidth (used by YouTube) |
| "Live streaming required" | RTMP ingest server; HLS with 2-second segments; near-zero CDN TTL (no caching for live segments); real-time segment push to all edges | Live segments are never cacheable at normal TTLs; latency matters more than throughput |
| "P99 < 10ms video chunk delivery" | PoP co-located in major ISPs (BGP peering, not internet routing); content pre-pushed to every PoP before any viewer requests it; Anycast routing (the technique of routing clients to the nearest PoP via a single IP address) | CDN cache hit is 30–50ms via HTTPS; to get to 10ms, you need direct peering inside ISP networks, not CDN edges |
| "DRM encryption required" | Widevine (Google), FairPlay (Apple), PlayReady (Microsoft) key server; encrypted HLS/DASH segments; per-viewer token in manifest URL | CDN still serves encrypted chunks; DRM adds key exchange at playback start — separate key server required |
| "User uploads must be resumable for files > 1 GB" | TUS protocol (the open protocol for resumable file uploads) or S3 multipart upload API; client sends chunks of 8 MB; each chunk is independently uploaded and verified | Single HTTP PUT for 2 GB fails on mobile; multipart isolates failure to the current chunk and resumes from last successful chunk |
| "Signed/authenticated URLs for access control" | Pre-signed S3 URLs (15-min TTL) or CDN signed URLs with HMAC; token includes `viewer_id`, `video_id`, `expiry`, signature | URLs cannot be cached at CDN level (every URL is unique per viewer); CDN validates signature at edge without origin round-trip |

---

## Section 8 — 🌐 API Design (Before HLD)

> **Why here:** Define the external contract before drawing the architecture — the HLD shows how these endpoints are implemented. For Type A, this is concise (3–5 minutes); the architecture is the primary deliverable.

### 🧠 How to Derive These Endpoints

Video distribution has two distinct flows: **upload** (creator sends video to the platform) and **playback** (viewer watches it). The key insight: the REST API handles metadata and orchestration — the actual video bytes move directly between the browser/CDN without touching your API service.

"Creator uploads a large video file" → you cannot accept a 2GB POST body in a REST API server — that would tie up connections, blow memory, and timeout at any reasonable gateway limit. The pattern: `POST /v1/uploads` returns a pre-signed S3 multipart upload URL; the client PUTs chunks directly to S3. Your server never sees the bytes. `PUT /v1/uploads/{id}/part/{n}` is the direct-to-S3 call (presigned URL auth, not JWT). `POST /v1/uploads/{id}/complete` tells your server "all chunks uploaded" — the server assembles the multipart upload in S3 and triggers the transcoding pipeline.

"Viewer watches a video" → `GET /v1/videos/{id}` returns metadata and `manifest_url`. The viewer's player then calls `GET /v1/videos/{id}/manifest` which returns a `302 Redirect` to the CDN HLS `master.m3u8` URL. The player follows the redirect and talks directly to the CDN forever after — your API server exits the delivery path completely. This is the standard pattern: the API server is the directory, the CDN is the library.

"Delete a video" → `DELETE /v1/videos/{id}` soft-deletes the metadata record and triggers S3/CDN purge. The interviewer will ask: "Does CDN cache get invalidated?" Answer: CDN purge API call, plus `Cache-Control: no-cache` in the 302 response stops the player from caching the now-invalid manifest URL.

Validation check: the transcoding pipeline (SQS → Worker → S3) has no REST endpoint — it's triggered by `POST /v1/uploads/{id}/complete` internally, not exposed. Correct.

### Core Endpoints

| Method | Path | Auth | Request | Response | Status Codes |
|---|---|---|---|---|---|
| POST | `/v1/uploads` | JWT Bearer | `{title, file_size, content_type}` | `{upload_id, chunk_size, upload_url (presigned)}` | 201, 400 |
| PUT | `/v1/uploads/{id}/part/{n}` | Presigned URL | Binary chunk (8 MB) | `{etag}` | 200, 400 |
| GET | `/v1/uploads/{id}/status` | JWT Bearer | — | `{upload_id, parts_completed, total_parts}` | 200, 404 |
| POST | `/v1/uploads/{id}/complete` | JWT Bearer | `[{part_number, etag}]` | `{video_id, status: "processing"}` | 202, 400 |
| GET | `/v1/videos/{id}` | Public | — | `{video_id, title, status, manifest_url, thumbnail_url}` | 200, 404 |
| GET | `/v1/videos/{id}/manifest` | Public | — | `302` redirect to CDN `master.m3u8` URL | 302, 404 |
| DELETE | `/v1/videos/{id}` | JWT Bearer (owner) | — | `{video_id, deleted: true}` | 200, 404 |

### 🔍 Endpoint Stories

**`POST /v1/uploads` → `PUT chunk` → `POST /v1/uploads/{id}/complete`** is a three-step upload protocol. Most candidates propose one `POST /v1/uploads` with a file body — that breaks at 100MB. The multipart protocol: (1) initiate upload, get back a presigned S3 multipart upload URL; (2) client splits the file into 8MB chunks and PUTs each directly to S3 (parallel, not sequential); (3) client calls `complete` with all the `{part_number, etag}` pairs to finalize the multipart upload. S3 assembles the parts in order. Your API server handles step 1 and step 3 only — the bytes never touch it.

**`GET /v1/videos/{id}/manifest`** is the entry point to video delivery, and it returns a `302 Redirect` — not the manifest content. Why? The manifest file is a CDN-hosted `.m3u8` file. Your API server redirects to `https://cdn.example.com/{video_id}/master.m3u8`. The player follows the redirect, gets the HLS manifest listing all quality variants, then fetches segments directly from CDN. `Content-Type` of the CDN response is `application/vnd.apple.mpegurl` — video players look for this MIME type. Your API server is out of the delivery path after the initial 302.

**`GET /v1/uploads/{id}/status`** is the polling endpoint for the creator's UI. Transcoding can take 5–30 minutes for a long video. The UI polls this every 10 seconds to show a progress bar (`{parts_completed: 8, total_parts: 12}`). When `status` transitions to `READY`, the UI starts showing the public playback link. Alternative: push a WebSocket event when transcoding completes — eliminates polling but adds complexity. For MVP, polling is fine.

**`GET /v1/videos/{id}`** returns `manifest_url` as a convenience field (same as calling `/manifest` and following the redirect). Smart clients use `manifest_url` directly; standard video players use `/manifest`. Both point to the same CDN content. The `status` field transitions through `UPLOADING → TRANSCODING → READY → DELETED` — clients should poll until `READY` before showing the video player.

---

## Section 6 — 🏗️ High-Level Architecture (Minutes 8–20)

### 🎨 Visual — Video Distribution Evolution (3-Stage Progression)

Start with Stage 1. Explain what breaks. Evolve to Stage 2. Explain what breaks. Evolve to Stage 3. This is how senior engineers think about architecture — not as a final answer, but as a progression of trade-offs.

---

```
══════════════════════════════════════════════════════════════════════════
STAGE 1 — Single-Region Origin Server  (MVP, no CDN, no transcoding)
══════════════════════════════════════════════════════════════════════════

  [User — Tokyo] ──────────────────────────────────────────► [Origin — US-East]
                               ~180ms RTT                       │
  [User — London] ─────────────────────────────────────────►   │ Serves raw MP4 directly
                               ~120ms RTT                       │ No transcoding
  [User — São Paulo] ──────────────────────────────────────►   │
                               ~200ms RTT                     [Single Server]
                                                               [Local Disk / NAS]


  BREAKING POINT 1: All international users have 100–200ms+ RTT to origin.
    For video, TTFB = RTT + server processing. P99 globally > 500ms.
    Buffering starts immediately for low-bandwidth users (no quality fallback).

  BREAKING POINT 2: A viral video (1M concurrent viewers) → 4 Tbps egress.
    A single origin server has ~10 Gbps NIC. It falls over at 2,500 viewers.
    No CDN = origin is DDoS'd by its own users.

  BREAKING POINT 3: Raw upload served directly = single bitrate for all clients.
    A 4K viewer and a 3G mobile user both get the same file.
    Mobile viewer buffers constantly; expensive bandwidth wasted on 4K for
    viewers who can't render it.

══════════════════════════════════════════════════════════════════════════
STAGE 2 — Transcoding Pipeline + S3 + Pull CDN  (Better, not production)
══════════════════════════════════════════════════════════════════════════

  Upload path:
  [User Upload] → [Upload Service] → [S3: raw/] → [SQS Queue]
                                                         │
                                              [Transcode Workers — EC2 Auto Scaling]
                                                         │
                                              [S3: transcoded/video_id/360p.m3u8]
                                              [S3: transcoded/video_id/720p.m3u8]
                                              [S3: transcoded/video_id/1080p.m3u8]

  Delivery path:
  [Viewer — Tokyo] → [CloudFront PoP — Tokyo]
                          │  Cache MISS (first viewer in Tokyo)
                          ▼
                     [S3 — us-east-1]  ◄── pull from single-region origin
                          │  Fetch + cache segment at PoP
                          │  ~150ms for first Tokyo viewer
                          ▼
                     [CloudFront PoP — Tokyo]
                          │  Cache HIT for all subsequent Tokyo viewers
                          ▼  ~30ms
                     [Viewer — Tokyo] ✓

  > 📖 Full: `SystemDesignConcepts/Foundations/Data-Fundamentals/14-document-blob-storage.md`
  > 📖 Full: `SystemDesignConcepts/Foundations/Performance-and-Scale/03-caching.md`

  BREAKING POINT 1: Cold cache = first Tokyo viewer fetches from us-east-1 S3.
    RTT from Tokyo PoP to us-east-1: ~150ms.
    First viewer gets slow start. If this is a live event launch (100K viewers
    simultaneously), 100K cache misses hit origin at once — thundering herd.

  BREAKING POINT 2: Single S3 region as origin for all CDN edges.
    APAC CDN edges → 150ms to US-East S3.
    EU CDN edges → 80ms to US-East S3.
    These cross-region pull latencies appear at every cache miss.
    With 95% cache hit ratio: 5% of 1M viewers = 50K cross-region pulls/min.

  BREAKING POINT 3: No adaptive bitrate in this design.
    HLS manifests generated, but if the player can't switch segments smoothly
    (poor manifest design, large segment sizes), the viewer experience degrades.
    Segment duration: 10s is too coarse; 2s is too many requests; 6s is standard.

══════════════════════════════════════════════════════════════════════════
STAGE 3 — HLS/DASH + Multi-Region S3 + Hot-Content Pre-Push  (Production)
══════════════════════════════════════════════════════════════════════════

  Upload + Transcoding:
  [User Upload] → [Upload Service] → [S3: us-east-1/raw/] → [SQS Queue]
                                                                    │
                                            [Transcode Workers — EC2 Auto Scaling]
                                            Generates per video:
                                            ├── master.m3u8  (HLS master manifest)
                                            ├── 360p/seg000.ts → seg999.ts  (6s each)
                                            ├── 720p/seg000.ts → seg999.ts
                                            └── 1080p/seg000.ts → seg999.ts
                                                                    │
                                            [S3 CRR — Cross-Region Replication]
                                            S3 us-east-1 ──► S3 eu-west-1
                                                         ──► S3 ap-southeast-1
                                                         ──► S3 sa-east-1

  Content warming (hot content pre-push):
  [Popularity Service] monitors upload metadata and trending signals
  For videos predicted to be popular (>1K views in first hour):
      → Push all HLS segments to CDN edges in all regions (pre-warm)
      → Before any viewer requests it, every edge already has it cached
      → Cache hit ratio for popular content: 100% from second request onward

  Delivery path (production, cache-warm):
  [Viewer — Tokyo]
      │  HLS player requests master.m3u8
      ▼
  [CloudFront PoP — Tokyo]
      │  Cache HIT (< 30ms TTFB)
      ▼
  [Player selects 1080p.m3u8 based on bandwidth estimate]
      │  Requests segment: /1080p/seg042.ts
      ▼
  [CloudFront PoP — Tokyo]
      │  Cache HIT — segment already at edge (pre-pushed or cached by prior viewer)
      │  TTFB: ~20ms
      ▼
  [Player buffers next 3 segments while displaying current]
      Player detects bandwidth drop → switches to 720p.m3u8 for next segment
      Seamless quality switch — viewer never pauses

  Delivery path (cold start, cache miss):
  [Viewer — Frankfurt] → [CloudFront PoP — Frankfurt]
      │  Cache MISS → pull from S3 eu-west-1  (nearest regional S3)
      │  RTT: Frankfurt PoP → eu-west-1 ≈ 5ms (same region)
      │  TTFB: 5ms + segment read ≈ 20ms (not 150ms)
      ▼  Cache and return to viewer

  > 📖 Full: `SystemDesignConcepts/Core-Architecture/Distributed-Systems/40-multi-region-geo-failover.md`
  > 📖 Full: `SystemDesignConcepts/Production-Grade/System-Design-Patterns/47-job-scheduling-at-scale.md`

  KEY INVARIANTS:
    1. CDN edge NEVER falls back to origin on miss — it falls back to nearest
       regional S3 (same-region pull ≈ 5ms, not cross-region 150ms).
    2. HLS segments are immutable once created — a segment URL never changes
       content. This enables infinite CDN TTL (Cache-Control: max-age=31536000).
    3. Only the master manifest and rendition playlist (.m3u8) have short TTL
       (60s) because they may update for live or rolling playlists.
    4. Transcoding is a one-time cost — raw video never served to clients.
       All delivery is from transcoded chunks via CDN.
```

---

### Decision Table 1 — CDN Strategy: Pull vs Push vs Hybrid

| Option | Strength | Weakness | Verdict |
|---|---|---|---|
| Pull CDN (cache on first miss) | Zero infrastructure to pre-populate; simple to operate; correct for long-tail content | First viewer in each PoP gets high latency (cold miss → origin pull); thundering herd on popular content launch | ⚠️ Good for long-tail; insufficient for hot content |
| Push CDN (pre-populate all edges) | First viewer in any PoP gets cached content; perfect for known-popular content | Must push ALL content to ALL edges; 90% of content is never viewed in most regions — wasteful bandwidth and storage cost | ⚠️ Good for hot content; wasteful for long-tail |
| Hybrid (pull by default, push for hot content) | Combines benefits: long-tail served by pull (efficient); hot content pre-pushed (zero cold-start); popularity prediction reduces push waste | Requires a popularity prediction service; push timing must precede demand spike | ✅ Choose this — YouTube, Netflix, Cloudflare all use this model |

> 📖 Full: `SystemDesignConcepts/Foundations/Performance-and-Scale/03-caching.md`

---

### Decision Table 2 — Transcoding Architecture

| Option | Strength | Weakness | Verdict |
|---|---|---|---|
| Single transcode server (FFmpeg on origin) | Simple; no queue needed | Single point of failure; 100K uploads/day overwhelms one server; no parallelism within a video | ❌ Does not scale |
| SQS + EC2 Auto Scaling transcode workers | Horizontally scalable; EC2 Spot instances reduce cost 70%; SQS provides durability (no job loss on worker crash) | Segment-parallel transcoding within a single video requires splitting video before queuing | ✅ Choose this — standard cloud-scale transcoding pattern |
| Managed transcoding service (AWS Elastic Transcoder / MediaConvert) | Zero operational overhead; handles segment splitting, multiple output formats, DRM integration | Higher per-minute cost than Spot EC2; less customizable (e.g., custom codec parameters) | ⚠️ Use for early stage or low volume; own the pipeline at scale for cost control |

> 📖 Full: `SystemDesignConcepts/Production-Grade/System-Design-Patterns/47-job-scheduling-at-scale.md`

---

### Decision Table 3 — Multi-Region Storage

| Option | Strength | Weakness | Verdict |
|---|---|---|---|
| Single S3 region (us-east-1) as CDN origin | Simplest; no replication cost or lag | CDN edges in EU and APAC pull from US S3 on every cache miss → 100–150ms cross-region latency on cold start | ⚠️ Acceptable at small scale; breaks at global scale with cold start latency |
| S3 CRR (Cross-Region Replication) to 3–4 regional buckets | CDN edges pull from nearest S3 region (5ms, not 150ms); no single-region SPOF for S3 reads | Replication cost (~$0.02/GB); replication lag (< 1 min typical); slight consistency window | ✅ Choose this — the 5ms vs 150ms cold-miss latency difference justifies the replication cost |
| Multi-CDN with shared origin | CDN vendor failover (if Cloudflare goes down, fail to Akamai); latency benefits of multiple vendor PoP footprints | DNS failover complexity; origin must be accessible from both CDN vendors; config duplication | ⚠️ Use at extreme scale (YouTube, Netflix) — overkill for most designs |

> 📖 Full: `SystemDesignConcepts/Core-Architecture/Distributed-Systems/40-multi-region-geo-failover.md`

---

### Data Flow Walkthrough (Stage 3 — say this out loud)

**Upload path:**
1. **User uploads video** via multipart upload to Upload Service (TUS protocol for resumability); chunks stored to S3 `raw/` prefix as they arrive
2. **S3 triggers event** → SQS message with `{video_id, s3_key, raw_size}` queued
3. **Transcode worker** (EC2 Spot) picks up the SQS message; runs FFmpeg to generate HLS master manifest + segments for 360p, 720p, 1080p; writes output to S3 `transcoded/{video_id}/` with `Cache-Control: max-age=31536000` (immutable segments)
4. **S3 CRR** automatically replicates all segment objects to eu-west-1, ap-southeast-1, sa-east-1 within ~60 seconds
5. **Metadata Service** updates video record: `status = READY`, `manifest_url = cdn.example.com/{video_id}/master.m3u8`

**Delivery path (hot content, pre-warmed):**
6. **Viewer requests** `GET cdn.example.com/{video_id}/master.m3u8`
7. **CDN PoP** (nearest to viewer) returns master manifest from cache — `TTFB < 20ms`
8. **HLS player** inspects bandwidth → requests `720p.m3u8` rendition playlist
9. **Player requests segment** `720p/seg042.ts` → CDN PoP cache HIT (pre-warmed) → `TTFB < 20ms`
10. **Player pre-fetches** next 3 segments while displaying current; bandwidth drops → player requests next segment from `360p.m3u8` — quality degrades seamlessly with no buffer stall

---

## Section 7 — 🔬 Core Component Deep Dives (Minutes 20–38)

---

### Deep Dive 1: Transcoding Pipeline

**Why this is the most critical component:**
Raw video cannot be served to clients — it is the wrong format, the wrong bitrate, and it is unresumable. Every viewer's experience depends on the transcoding step completing correctly and producing valid HLS chunks. This is where most system complexity lives.

**Options considered:**

| Option | Pros | Cons |
|---|---|---|
| Sequential transcoding (360p, then 720p, then 1080p) | Simple; predictable memory; easy to debug | 3× slower than parallel; 30-minute video takes 90 CPU-minutes instead of 30 |
| Parallel rendition transcoding (3 EC2 workers per video) | 3× faster; each rendition is independent | 3× cost; coordination overhead (who marks the video as READY when all 3 finish?) |
| Segment-parallel transcoding (split video → transcode chunks → reassemble) | Linearly scalable; a 60-minute video can be processed in 5 minutes using 60 workers (one per minute of content) | Most complex; requires segment boundary alignment (can't split mid-GOP); needs final reassembly step |

**Decision: SQS + parallel rendition transcoding (3 workers per video, coordinated via a job tracker)**
Because the 5-minute upload-to-playback SLO requires parallel transcoding. A 60-minute video takes 30 CPU-minutes per rendition — sequential would take 90 CPU-minutes, violating the SLO. The coordination problem (who sets `status = READY`?) is solved by a completion counter in DynamoDB:

```
TranscodeJob table (DynamoDB):
  video_id       → primary key
  renditions_expected  = 3
  renditions_done      = 0 (incremented by each worker on completion)
  status         = PROCESSING | READY | FAILED

Each worker on completion:
  renditions_done = INCREMENT(video_id, renditions_done)
  IF renditions_done == renditions_expected:
    UPDATE videos SET status = 'READY'
    trigger CDN pre-warm for popular content
```

The trade-off I'm accepting: 3× the compute cost per video vs sequential. At 100K uploads/day × $0.10/video transcode cost = $10K/day. Spot instances reduce this by 70% → $3K/day. Acceptable for a platform at this scale.

**Segment duration: 6 seconds (not 2s, not 10s)**
- 2-second segments: too many HTTP requests per minute; CDN overhead; too many cache objects
- 10-second segments: ABR quality switch takes 10s to take effect; mobile viewers buffer during quality drop
- 6-second segments: industry standard (Apple HLS docs, Netflix); balances request overhead with ABR responsiveness

**⚠️ Production gotcha: GOP boundary alignment for segment-parallel transcoding**

The table above notes "can't split mid-GOP" — this is the most common production mistake in video transcoding systems that candidates mention without understanding.

**What is a GOP?** A GOP (Group of Pictures — the unit of video compression in MPEG/H.264/H.265; it starts with an I-frame (a complete "keyframe" that can be decoded without any other frames), followed by P-frames (predicted frames that only store the difference from the previous frame) and B-frames (bidirectionally predicted frames); a typical GOP is 30–120 frames; you cannot decode any frame in the GOP without its anchor I-frame) is the atomic unit of video that can be independently decoded.

**The problem with arbitrary byte-range splits:**

If you split a 60-minute video into 60 one-minute chunks at arbitrary byte offsets, some chunks will start mid-GOP — in the middle of a B-frame that references an I-frame from the previous chunk. The transcoder for that chunk has no access to the reference I-frame. The result:

```
Chunk 1: ends mid-GOP at frame 2,345
          → transcoders process frames 1–2,345 correctly

Chunk 2: starts at frame 2,346 (B-frame that references I-frame at 2,289)
          → transcoder has no frame 2,289 (it's in Chunk 1)
          → decodes garbage: visible as glitches, macroblocking, or full black frames
          → HLS segment seg000.ts plays fine, seg001.ts shows corruption at the first frame
```

**The fix: detect and split only at I-frame (keyframe) boundaries.**

Before splitting, scan the video's metadata to find all keyframe timestamps:

```bash
# FFprobe finds all I-frame timestamps:
ffprobe -select_streams v \
        -show_entries packet=pts_time,flags \
        -of csv \
        input.mp4 \
        | grep K | awk -F, '{print $2}'
# Output: 0.000, 2.001, 4.003, 6.005, 8.002, ...
```

Then split the video into chunks that start and end exactly at these I-frame timestamps. Two workers now share NO reference dependencies between their chunks — each chunk starts with a complete I-frame and is fully self-contained.

**Practical implication:** The source video must have I-frames at regular intervals. Most encoders default to a keyframe every 250 frames (~10 seconds at 25fps). For 6-second HLS segments, you must configure the encoder with `keyint=150` (I-frame every 6 seconds at 25fps) so every segment boundary aligns with an I-frame. If the source was uploaded without this constraint, you either:
1. Re-encode the entire source with forced keyframes (adds one pass but makes segmentation clean)
2. Accept that some segment boundaries fall between I-frames (results in small quality glitches at segment joins)

**In an interview:** "Segment-parallel transcoding requires splitting only at GOP boundaries — at I-frames. Splitting mid-GOP breaks video decoding for the downstream segment because it references frames that only exist in the previous chunk. I'd use FFprobe to detect I-frame timestamps first, then split at those boundaries. The source video should be encoded with a fixed keyframe interval matching the segment duration (6s → 150 frames at 25fps) to ensure clean boundaries everywhere."

**HLS manifest structure:**

```
master.m3u8  (short TTL: 60s — top-level manifest)
  #EXTM3U
  #EXT-X-VERSION:3
  #EXT-X-STREAM-INF:BANDWIDTH=500000,RESOLUTION=640x360
  360p/playlist.m3u8
  #EXT-X-STREAM-INF:BANDWIDTH=2000000,RESOLUTION=1280x720
  720p/playlist.m3u8
  #EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080
  1080p/playlist.m3u8

720p/playlist.m3u8  (short TTL: 60s — rendition playlist)
  #EXTM3U
  #EXT-X-VERSION:3
  #EXT-X-TARGETDURATION:6
  #EXT-X-PLAYLIST-TYPE:VOD
  #EXTINF:6.000,
  seg000.ts
  #EXTINF:6.000,
  seg001.ts
  ...
  #EXT-X-ENDLIST

720p/seg000.ts  (immutable — Cache-Control: max-age=31536000, immutable)
720p/seg001.ts  (immutable)
```

> 📖 Full: `SystemDesignConcepts/Production-Grade/System-Design-Patterns/47-job-scheduling-at-scale.md`

---

### Deep Dive 2: CDN Cache Strategy + Hot Content Pre-Warming

**Why this is the most critical component:**
The CDN is the entire delivery system. If cache hit ratio drops from 95% to 80%, origin traffic 4×. If popular content is not pre-warmed, the launch of a viral video generates 100K simultaneous cache misses — a thundering herd that overwhelms origin regardless of its capacity.

**Cache TTL strategy:**

| Content Type | TTL | Why |
|---|---|---|
| `master.m3u8` | 60 seconds | May change if new renditions added; can't cache forever |
| `{rendition}/playlist.m3u8` | 60 seconds | Same as master — playlist may be updated |
| `{rendition}/seg*.ts` | 31,536,000 seconds (1 year) + `immutable` | Segments are content-addressed; their URL encodes their content; once written they never change |
| Thumbnails | 86,400 seconds (24h) | Rarely changes; high cache efficiency |

**Pre-warming logic:**

```python
# PopularityService — runs after each transcode completion
def should_prewarm(video_id: str) -> bool:
    metadata = get_video_metadata(video_id)
    signals = [
        metadata.creator_follower_count > 100_000,   # creator with large audience
        metadata.is_trending_topic,                   # tagged with trending hashtag
        metadata.upload_hour in range(8, 10),         # posted during morning commute peak
    ]
    return sum(signals) >= 2   # pre-warm if 2+ signals match

def prewarm_video(video_id: str):
    all_segments = list_s3_objects(f"transcoded/{video_id}/")
    for segment in all_segments:
        cdn_api.push_to_all_pops(segment.key, segment.cdn_url)
        # CDN vendor API: CloudFront cache warming, Fastly instant purge/pre-populate
```

**Cache hit ratio math:**
- Long-tail videos (< 100 views): CDN pull; each PoP caches on first local request → cache hit ratio converges to ~90% over time
- Hot videos (> 10K views): pre-warmed → 100% cache hit from first request
- Overall target: 97%+ cache hit ratio globally
- At 97%: 3% of 4 Tbps = 120 Gbps origin traffic (distributed across 4 regional S3 buckets = 30 Gbps each — manageable)

> 📖 Full: `SystemDesignConcepts/Foundations/Performance-and-Scale/03-caching.md`

---

### Deep Dive 3 (if time permits): Resumable Upload with S3 Multipart

**Why this matters:**
A 2 GB raw video upload over a mobile connection will fail mid-transfer. Without resumability, the user retries from scratch — terrible UX and wasted bandwidth.

**S3 Multipart Upload flow:**

```
Client                          Upload Service                    S3
  │                                    │                          │
  │── POST /uploads ──────────────────►│                          │
  │                                    │── CreateMultipartUpload ►│
  │                                    │◄── UploadId: xyz ────────│
  │◄── {upload_id: xyz, chunk_size: 8MB} ──────────────────────── │
  │                                    │                          │
  │── PUT /uploads/xyz/part/1 (8 MB) ─►│── UploadPart(1) ────────►│
  │◄── {etag: "abc"} ──────────────────│◄── ETag: "abc" ──────────│
  │                                    │                          │
  │  [Network drops]                   │                          │
  │                                    │                          │
  │── GET /uploads/xyz/status ────────►│── ListParts(xyz) ───────►│
  │◄── {completed_parts: [1,2,3]} ─────│◄── [{part:1},{part:2}] ──│
  │                                    │                          │
  │── PUT /uploads/xyz/part/4 (8 MB) ─►│── UploadPart(4) ────────►│  [Resume from part 4]
  │                                    │                          │
  │── POST /uploads/xyz/complete ─────►│── CompleteMultipart ────►│
  │                                    │◄── s3://raw/video_id.mp4 │
  │◄── {status: "processing"} ─────────│                          │
```

**Why 8 MB chunk size:** S3 minimum part size is 5 MB; recommended is 8–16 MB. Smaller = more requests overhead; larger = more data lost on retry. 8 MB is the industry sweet spot.

---

## Section 9 — 🗄️ Data Model

### Core Tables

```sql
-- Videos metadata table (Postgres)
CREATE TABLE videos (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id        UUID NOT NULL,                 -- user or organization
    title           VARCHAR(500) NOT NULL,
    description     TEXT,
    status          VARCHAR(20) NOT NULL
                        CHECK (status IN ('UPLOADING','TRANSCODING','READY','FAILED','DELETED')),
    raw_s3_key      VARCHAR(500),                  -- s3://bucket/raw/{id}.mp4
    manifest_cdn_url VARCHAR(500),                 -- https://cdn.example.com/{id}/master.m3u8
    thumbnail_cdn_url VARCHAR(500),
    duration_seconds INT,                          -- populated after transcoding
    file_size_bytes  BIGINT,
    view_count       BIGINT NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_videos_owner ON videos(owner_id, created_at DESC);
CREATE INDEX idx_videos_status ON videos(status) WHERE status IN ('TRANSCODING','UPLOADING');

-- Transcode jobs (DynamoDB or Postgres — DynamoDB for atomic counter increment)
-- Represented here as SQL for clarity:
CREATE TABLE transcode_jobs (
    video_id            UUID PRIMARY KEY REFERENCES videos(id),
    renditions_expected SMALLINT NOT NULL DEFAULT 3,   -- 360p, 720p, 1080p
    renditions_done     SMALLINT NOT NULL DEFAULT 0,
    status              VARCHAR(20) NOT NULL
                            CHECK (status IN ('QUEUED','PROCESSING','DONE','FAILED')),
    worker_ids          TEXT[],                        -- which EC2 instances processed each rendition
    started_at          TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Upload sessions (for resumable multipart uploads)
CREATE TABLE upload_sessions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    video_id        UUID NOT NULL REFERENCES videos(id),
    s3_upload_id    VARCHAR(200) NOT NULL,             -- S3 multipart upload ID
    chunk_size_bytes INT NOT NULL DEFAULT 8388608,     -- 8 MB
    total_chunks    INT NOT NULL,
    completed_chunks INT NOT NULL DEFAULT 0,
    expires_at      TIMESTAMPTZ NOT NULL,              -- S3 multipart expires in 7 days
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

**Storage tiers (S3 lifecycle policy, not a DB table):**
```
S3 Lifecycle Rules on bucket: video-transcoded-{region}
  Rule 1: Transition to S3-IA after 30 days (saves ~45% vs Standard)
  Rule 2: Transition to S3 Glacier after 180 days (saves ~80% vs Standard)
  Rule 3: Expire raw uploads (raw/ prefix) after 7 days (raw no longer needed once transcoded)
```

> 📖 Full: `SystemDesignConcepts/Foundations/Data-Fundamentals/14-document-blob-storage.md`

---

## Section 10 — ⚠️ Trade-offs + Failure Modes (Minutes 38–45)

### Trade-off 1: Pull CDN vs Pre-Push CDN for Hot Content

- **Chose:** Hybrid — pull by default, pre-push for predicted-hot content
- **Gain:** Long-tail videos (99% of catalog) require zero proactive bandwidth cost; only the 1% of videos that will actually go viral are pre-pushed; origin is never overwhelmed by launch thundering herd for hot content
- **Lose:** Popularity prediction errors mean some hot videos are served via pull (cold start for first viewers); complexity of maintaining a popularity signal pipeline
- **Failure mode if wrong (pull-only):** A celebrity posts a video at 9am; 1M viewers request it in the first minute; all 100+ CDN PoPs have cache misses simultaneously → 1M requests hit S3 in 60 seconds → S3 request rate throttling → all viewers see buffering; Twitter fills with complaints. **Business impact:** The product demo video is inaccessible during peak launch traffic — for DocuSign this means a prospect visiting the homepage during a marketing campaign sees a buffering screen instead of the e-signature demo, abandons the page, and the conversion is lost.

### Trade-off 2: S3 Cross-Region Replication vs Single-Region S3 Origin

- **Chose:** S3 CRR to 4 regional buckets (us-east-1, eu-west-1, ap-southeast-1, sa-east-1)
- **Gain:** CDN edges in each region pull from regional S3 bucket on cache miss (5ms, not 150ms cross-region); no single-region SPOF for CDN cold-start latency; same-region S3 GET is cheaper ($0.004/10K) than cross-region transfer ($0.02/GB)
- **Lose:** Replication cost (~$0.02/GB transferred); replication lag (~60s for new segments to appear in all regions — viewers in EU see "video not found" for first 60 seconds after upload); operational complexity of 4 S3 buckets vs 1
- **Failure mode if wrong (single S3 region):** APAC CDN edge on cold miss → 150ms to us-east-1 S3 → user buffering on every segment not yet cached. For ABR with 6s segments: buffering starts if TTFB > 6s. At 150ms, this is fine most of the time, but under S3 congestion (many cache misses simultaneously), TTFB can spike to 2–3s per segment. **Business impact:** APAC prospects experience multi-second buffering on every uncached video segment — for DocuSign this means an enterprise sales demo video (a key conversion asset) loads at degraded quality for a Tokyo or Singapore prospect, the sales engineer can't run the live demo smoothly, and the deal is delayed or lost.

### Trade-off 3: Eager Transcoding (on upload) vs Lazy Transcoding (on first view)

- **Chose:** Eager transcoding — immediately on upload, within 5 minutes
- **Gain:** First viewer always gets a transcoded stream; no cold-start penalty for unpopular videos; predictable SLO for creators ("your video will be available within 5 minutes")
- **Lose:** 100K uploads/day × transcode cost = significant compute spend (even for videos that get 0 views); transcode queue backpressure under upload spikes (if 10K videos upload simultaneously, queue depth grows)
- **Failure mode if wrong (lazy transcoding):** First viewer requests video before transcoding completes → API returns "transcoding in progress" → bad UX. Worse: if 1,000 viewers hit an untranscoded video simultaneously, 1,000 transcode jobs fire at once — the queue thundering herd problem re-emerges at view time instead of upload time. **Business impact:** A new customer uploads their onboarding walkthrough video and immediately shares the link with their team — for DocuSign this means the video is unavailable for the first several minutes after upload, the onboarding session stalls, and the customer files a support ticket on day one of their contract, creating a negative first impression and churn risk.

---

## Section 11 — 🔐 DocuSign-Specific Depth

### The Pivot: This Architecture Is DocuSign's Document Distribution Problem

DocuSign does not stream video. However, the question "architect a worldwide video distribution system" is in DocuSign's prep PDF because the same CDN + blob storage + multi-region origin architecture solves DocuSign's **actual** distribution problem: delivering signed envelope PDFs, signing ceremony JavaScript bundles, and envelope thumbnail previews to signers in 180 countries with sub-100ms latency.

**Name this pivot explicitly in the interview:**

> "This architecture maps directly to a problem DocuSign actually has: a signed envelope PDF (the legal document a signer just completed) must be available for download by all parties — the sender, all co-signers, and any CC'd parties — instantly, globally, permanently. The CDN + multi-region S3 design I just described solves that. Let me show you the mapping..."

**Mapping: Video → DocuSign documents**

| Video distribution | DocuSign equivalent |
|---|---|
| Raw video upload | Envelope PDF upload by sender |
| Transcoding (multiple formats) | PDF optimization: thumbnail generation, full-text extraction, watermarking |
| HLS segments (immutable, content-addressed) | Signed PDF pages (immutable after signing — every bit is legally sealed) |
| CDN delivery to global viewers | CDN delivery of signed PDF to global signers |
| Pre-warming for popular videos | Pre-warming for high-volume templates (standardized contracts sent to thousands of signers) |
| Adaptive bitrate (quality based on bandwidth) | PDF streaming (page-by-page rendering for large documents on mobile) |
| Signed CDN URLs (per-viewer token) | DocuSign pre-signed URL with expiry (only the authorized signer can download for 24h) |

**DocuSign-specific requirements that change the design:**

1. **Immutability is legally required, not just a cache optimization.** Once an envelope is signed, the PDF cannot change — ever. This is not a CDN TTL decision; it is a legal requirement. The S3 object is written with `x-amz-object-lock: GOVERNANCE` to prevent deletion or overwrite.

2. **Audit trail for every access.** CloudFront access logs record every PDF download: who (IP, JWT claims), when (timestamp), what (envelope ID). These logs feed DocuSign's compliance reporting.

3. **Pre-signed URLs are mandatory, not optional.** DocuSign PDFs are never publicly cached. Every download URL is a pre-signed S3 URL or signed CDN URL with `viewer_id + envelope_id + expiry + HMAC`. Cache hit ratio is still achievable by caching the underlying S3 object at the CDN edge while validating the signature at the edge (CloudFront Lambda@Edge for signature validation — no origin round-trip).

4. **High-volume templates behave exactly like "hot content."** A mortgage company sends the same PDF template to 10,000 borrowers. That template should be pre-warmed to all CDN edges. The popularity signal: `COUNT(sends of envelope_template_id) > 1,000` → pre-push.

**Type A dimensions — DocuSign PDF applies these:**
- **Testability:** Transcode worker accepts a test video and asserts output segments exist and are valid HLS — pure function, testable in isolation
- **Security:** Signed CDN URLs prevent unauthorized access; S3 Object Lock prevents document tampering; HTTPS everywhere (TLS 1.3 minimum)
- **Availability:** CDN cache means signed PDFs remain downloadable even during S3 or origin outage (CDN TTL covers the outage window); multi-region S3 eliminates single-region failure
- **Scalability:** CDN absorbs 4 Tbps; auto-scaling transcode workers handle 100K uploads/day; multi-region S3 distributes origin load
- **Observability:** CloudFront access logs → S3 → Athena for query (who downloaded what, when); transcode job duration metrics (P99 transcoding time per rendition); CDN cache hit ratio dashboard (alert when < 90%)

---

## Section 12 — 🔬 Where the Interviewer Will Probe

### Surface Probe (Tier 1 — every candidate gets this)

**Q: "Why do you need CDN? Can't you just serve from S3 directly?"**
> S3 can serve video, but at 4 Tbps peak egress from a single S3 region, the cross-region latency for international viewers would be 100–200ms per request — too slow for smooth video streaming (need < 50ms TTFB). S3 is also priced at $0.09/GB egress, while CDN is $0.008/GB — 10× cheaper at scale. CDN PoPs are co-located inside ISPs, which eliminates internet routing hops. S3 is the origin behind the CDN, not the delivery mechanism.

**Q: "What happens to the CDN cache when a video is deleted?"**
> Cached segments have a 1-year TTL. If a video is deleted (DMCA takedown, user deletion), we must invalidate the CDN cache. CloudFront supports cache invalidation by URL pattern: `DELETE /videos/{video_id}` triggers `aws cloudfront create-invalidation --paths "/{video_id}/*"`. This propagates to all PoPs within 5–10 minutes. Simultaneously, the S3 objects are deleted (except Object Lock-protected documents, which require governance override). CDN invalidation costs $0.005 per 1,000 paths — acceptable for a legal requirement.

### Deep Probe (Tier 2 — tests real understanding)

**Q: "How does the HLS player know which bitrate to start at? What is the initial bandwidth estimate?"**
> On first load, the player has no bandwidth measurement. HLS players (HLS.js, AVPlayer) start with a heuristic: typically 25–50% of the lowest bitrate (to guarantee smooth start) or use a "fast start" segment (a 2-second intro chunk encoded at the lowest bitrate regardless of measured bandwidth). After the first 2–3 segments are downloaded, the player computes a rolling average download speed and selects the highest bitrate that keeps the buffer full. This is why segment size matters: 6-second segments give the ABR algorithm 6 data points per minute. Finer segments (2s) allow faster quality switches but create more HTTP request overhead.

**Q: "How would you handle a sudden viral spike — a video goes from 100 to 10M viewers in 60 seconds?"**
> This is the thundering herd problem for CDN. If the video is not pre-warmed, all 100+ CDN PoPs get a cache miss simultaneously, and they all pull from S3 concurrently. At 10M viewers in 60 seconds — assuming even distribution across 100 PoPs — each PoP makes ~100K segment requests to S3 in one minute. S3 can handle high request rates with key prefix randomization, but the real problem is that each PoP is fetching the same segments from S3 in parallel. Solution: CDN "request coalescing" — if 100 simultaneous cache misses arrive for the same URL at a single PoP, the CDN makes ONE origin request and serves the response to all 100 waiting clients. CloudFront, Fastly, and Akamai all support request coalescing. Additionally, S3 prefix sharding (randomizing segment keys) avoids S3's per-prefix rate limit.

### Cross-Concept Probe (Tier 3 — separates senior candidates)

**Q: "If you needed to support live streaming from the same infrastructure, what would change?"**
> Live streaming fundamentally changes the write path but not the CDN delivery. For live: (1) ingest uses RTMP or SRT (Secure Reliable Transport) to a live ingest server, which generates 2-second HLS segments in real time; (2) those segments are pushed to S3 as they are created (not pulled — the ingest server is the pusher); (3) the CDN playlist (`playlist.m3u8`) has no `#EXT-X-ENDLIST` tag (it's an open-ended, growing playlist); (4) CDN TTL on the playlist must be set to 2 seconds (the segment duration) so viewers always fetch the latest segment list — you cannot cache a live playlist for 60 seconds; (5) the CDN must support "live streaming mode" (Akamai, CloudFront, Fastly all support this). The segment files themselves remain immutable with long TTLs once created. The key constraint: live requires real-time segment generation, which means no transcoding queue — the ingest server must transcode in real time to 3 renditions simultaneously. This requires GPU-accelerated instances (AWS g4dn) rather than the CPU-based Spot instances used for VOD.

---

## Section 13 — 🐞 Common Mistakes on This Question

- **Mistake 1: Serving video directly from origin (no CDN)** → **Why it's wrong:** A single origin cannot sustain 4 Tbps. Cross-region latency for international viewers kills the user experience. CDN egress is 10× cheaper than S3 egress. **What to say instead:** "CDN is the delivery mechanism — origin only serves cache misses, which should be < 5% of requests at steady state."

- **Mistake 2: Single S3 bucket for all CDN edges globally** → **Why it's wrong:** CDN edge in Frankfurt pulling from S3 us-east-1 on every cache miss has 100–150ms origin RTT. At 5% cache miss rate for a global platform, this degrades cold-start experience for international viewers significantly. **What to say instead:** "I replicate transcoded segments to regional S3 buckets via CRR — CDN edges pull from their nearest S3 region. Cache miss latency drops from 150ms to 5ms."

- **Mistake 3: Forgetting to pre-warm CDN for popular content launch** → **Why it's wrong:** If a major creator drops a video and millions of viewers hit it in the first minute, every CDN PoP gets a cache miss at the same time — thundering herd. **What to say instead:** "For predicted-hot content (creator with >100K followers, or content explicitly marked as a 'launch'), I pre-warm all CDN PoPs before publish. The popularity signal triggers a pre-push job, not just a cache-miss-and-pull."

- **Mistake 4: Using mutable S3 URLs for segments** → **Why it's wrong:** If the URL can change (e.g., `/latest/seg042.ts`), CDN cannot cache it reliably. If content behind the URL changes, viewers get stale segments. **What to say instead:** "Segment URLs are content-addressed — they include the video ID, rendition, and segment number in the path. The content behind a given URL never changes. This allows `Cache-Control: max-age=31536000, immutable` — CDN caches forever without validation."

- **Mistake 5: Conflating upload latency and playback latency** → **Why it's wrong:** "Upload is fast because the user is near the upload service" is not a valid assumption. The question is about delivery to global viewers, not upload. The bottleneck for delivery is CDN TTFB, not upload speed. **What to say instead:** "Upload latency is a separate concern — I'd use regional upload endpoints (EU users upload to eu-west-1, APAC users upload to ap-southeast-1). Delivery latency is what we're primarily optimizing — that's the CDN cache hit ratio and PoP placement."

---

## Section 14 — 🧭 DocuSign Dimensions Checklist

| Dimension | Relevant? | How this design addresses it |
|---|---|---|
| Testability | ✅ | Transcode worker is a pure function (input: S3 path; output: HLS segments); testable with a synthetic test video; CDN pre-warm logic testable by mocking the CloudFront API; origin failover testable by blocking S3 access and verifying CDN serves cached content |
| Usability | ✅ | HLS manifest URL is stable and bookmarkable; player auto-selects quality — no user intervention; upload API supports resumable multipart — no "start over" on failure; `GET /videos/{id}` returns `status` field so clients can poll for transcoding completion |
| Extensibility | ✅ | New rendition (4K HDR) = new Transcode Worker parameter, new S3 prefix, new playlist entry in master.m3u8 — no API changes; new CDN vendor = new origin pull config, same S3 backend; new storage tier = S3 lifecycle rule addition, no code changes |
| Security | ✅ | Pre-signed CDN URLs for content requiring access control (DocuSign documents); TLS 1.3 minimum for all CDN delivery; S3 bucket policy: `public` ACL blocked — only CDN can access; S3 Object Lock for legally immutable documents; CloudFront access logs for audit trail |
| Availability | ✅ | CDN serves content during S3 outage (content cached at edge); multi-region S3 eliminates single-region SPOF for origin; transcode SQS queue is durable (messages survive worker crashes); S3 11-nines durability for stored segments |
| Scalability | ✅ | CDN absorbs 4 Tbps peak without scaling any origin infrastructure; transcode workers auto-scale to 100K uploads/day; S3 scales to unlimited storage; CDN PoP count increases by adding vendor PoPs, not changing architecture |
| Observability & Traceability | ✅ | CloudFront access logs (who fetched what, when, from which PoP); transcode job duration histogram (P99 transcoding time by video length); CDN cache hit ratio by region (alert < 90%); upload completion rate (alert if > 5% uploads fail to complete multipart); segment error rate (4xx/5xx from CDN edge) |

---

## Section 15 — 🧾 TL;DR Answer Summary

> "I'd architect worldwide video distribution in three layers: a transcoding pipeline that converts raw uploads into HLS manifests with 360p/720p/1080p renditions (6-second chunks, immutable URLs, processed via an SQS-driven auto-scaling EC2 fleet within 5 minutes of upload); a multi-region S3 origin with cross-region replication to 4 geographic buckets (so CDN edges pull from a regional S3 with 5ms latency rather than a US-only origin with 150ms cross-region latency); and a global CDN with request coalescing and a hybrid caching strategy — pull for long-tail content, proactive pre-push for predicted-hot content before first viewer arrives. Immutable segment URLs carry `Cache-Control: max-age=31536000, immutable`, which enables permanent CDN caching without validation. For DocuSign specifically, this same architecture solves their actual problem: distributing signed envelope PDFs to global signers — the documents are legally immutable after signing (perfect for CDN caching), high-volume template envelopes behave exactly like hot video content and should be pre-warmed, and pre-signed CDN URLs with HMAC signatures enforce that only authorized parties download completed envelopes."

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Jul 6, 2026 | **🔑 Technology Quick Reference table added.** 13-row glossary covering CDN, PoP, Origin, Transcoding, Rendition, HLS, DASH, ABR, RTMP, Pre-warming, VOD, TTL, Multi-region S3 — inserted before Section 0 so the file is readable without prior video streaming knowledge. |
| Jul 5, 2026 | File created. Full 15-section 60-min interview-ready solution. Type A (Infrastructure). PDF-confirmed question. Covers: 3-stage progressive HLD (single-region origin → transcoding+S3+pull-CDN → HLS/DASH+multi-region+push), 3 decision tables, HLS manifest structure, S3 multipart upload flow, DocuSign pivot (envelope PDF distribution maps 1:1 to video distribution architecture), Tier 1/2/3 probe answers, 5 common mistakes. Cross-refs verified against actual SystemDesignConcepts files. |
