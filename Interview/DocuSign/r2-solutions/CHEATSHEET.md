# DocuSign R2 — Interview Retrieval Cheat Sheet

> **Purpose:** Open this before the interview. 30-second scan to prime memory. Keep open on second screen during interview for fast lookup.
> **Do NOT read the 13 solution files during the interview.** Use this to navigate to the right file and section in under 10 seconds.

---

## ⚡ Step 1 — Identify the file (hear keyword → find code)

| If the interviewer says... | Go to |
|---|---|
| rate limit, quota, throttle, API abuse, KYC token | **C1** |
| URL shortener, short link, redirect, vanity URL | **A1** |
| chat, messenger, real-time messaging, delivery receipt | **A2** |
| video, streaming, CDN, transcoding, adaptive bitrate | **A3** |
| subscription, billing, payment, invoice, renewal, Stripe | **B1** |
| expense report, approval workflow, SOX audit | **C2** |
| pagination, cursor, offset, large dataset, API design | **C3** |
| booking, reservation, seat, slot, waitlist, class | **CF1** |
| digital signature, PKI, certificate, non-repudiation | **D1** |
| document storage, blob, S3, file upload, versioning | **D2** |
| notification, email, SMS, push, alert, webhook | **D3** |
| search, Elasticsearch, full-text, autocomplete, index | **E1** |
| authentication, JWT, OAuth, login, MFA, session | **E2** |

---

## ⚡ Step 2 — Know the 3 numbers + 3 decisions before opening the file

### A1 — URL Shortener
```
Numbers:   33 shorten/sec (write) | 3,300 redirect/sec (read) | 95% cache hit rate
           → 62^6 = 56 billion URL space (counter enumeration protection)
Decisions: UUID v4 ✅ (no coordination)  |  cache-aside Redis ✅  |  302 not 301 ✅ (analytics)
DocuSign:  "Click here to sign" links — enumeration attack exposes envelope IDs
```

### A2 — Chat Messenger
```
Numbers:   500M DAU | 20B messages/day | 700K msg/sec peak (3× avg)
           → 20 TB/day storage | 7,700 WebSocket servers
Decisions: Cassandra ✅ (time-series append-only)  |  WebSocket ✅  |  Kafka fan-out ✅
DocuSign:  in-document comment sync during signing ceremony — signer sees stale comment
```

### A3 — Video Distribution
```
Numbers:   1M concurrent viewers | 4 Tbps peak egress | 100K uploads/day
           → 200 TB/day raw ingestion | 600 parallel transcode workers | 110 PB/year
Decisions: CDN mandatory at 4 Tbps ✅  |  HLS/DASH ABR ✅  |  S3 multi-region origin ✅
DocuSign:  signing ceremony assets / signed PDF delivery via CDN (not raw upload URL)
```

### B1 — Subscription Billing
```
Numbers:   1.6M paying customers | 6/sec peak payment writes | 5,500/sec entitlement reads
           → 53K renewals/day clustered on month-end (3-day spike)
Decisions: outbox pattern ✅  |  Idempotency-Key header ✅  |  Redis entitlement cache ✅
DocuSign:  every API call checks subscription — Redis cache mandatory (not DB reads)
```

### C1 — Rate Limiter
```
Numbers:   35K req/sec at 5× capacity | 52 min/yr Redis downtime | 6 bytes/Redis key
Decisions: Token Bucket ✅ (burst)  |  Redis Lua atomic ✅  |  fail-open for quota / fail-closed for security ✅
DocuSign:  fail-closed → 100% API rejection during Redis outage → Goldman Sachs SLA breach
```

### C2 — Expense Report
```
Numbers:   6 writes/sec | 50K concurrent approvals at quarterly close
Decisions: rules table ✅ (not hardcoded)  |  audit_log JSONB ✅  |  state machine enum ✅
DocuSign:  quarterly close rush | SOX audit replay of 1M events → TimeoutException without pagination limit
```

### C3 — Pagination API
```
Numbers:   1M+ records | 3.5K QPS | O(50) rows/page at any depth (vs O(N) for offset)
Decisions: composite cursor (created_at, id) ✅  |  compound index ✅  |  HMAC-signed base64 ✅
DocuSign:  eDiscovery export cursor timeout | cross-tenant cursor enumeration if HMAC missing
```

### CF1 — Class Booking (→ Notary Signing Sessions)
```
Numbers:   1M bookings/day | 1K/sec peak burst | 5-min soft reservation TTL
Decisions: Redis Lua atomic DECR ✅ (not SELECT FOR UPDATE)  |  ZSET waitlist ✅  |  Kafka async ✅
DocuSign:  notary signing session slots — overbooking delays real estate / mortgage closing
```

### D1 — Digital Signature
```
Numbers:   1M users | 35 sig/sec peak | $1.5M/year key-loss support cost
Decisions: Hosted CA ✅  |  DB trigger for audit immutability ✅  |  Kafka webhook delivery ✅
DocuSign:  HSM compromise = every historical signature legally contestable | court admissibility
```

### D2 — Document Storage
```
Numbers:   250 TB storage | 100M downloads/day | 10K uploads/day | 3.5K reads/sec
           → 5.8 GB/sec if proxied through app server (why pre-signed URLs are mandatory)
Decisions: S3 ✅  |  pre-signed URLs (bypass app) ✅  |  one-row-per-version ✅
DocuSign:  SEC 17a-4 7-year retention vs GDPR right-to-erasure — two-jurisdiction trap
```

### D3 — Notification Service
```
Numbers:   1B notifs/day | 35K notifs/sec peak | 20 Kafka partitions
           → 3,500 rows/batch outbox polling at 100ms
Decisions: outbox pattern ✅  |  per-channel SQS queues ✅  |  Redis idempotency (24h TTL) ✅
DocuSign:  bulk-send 50K envelopes backs up queue for hours | ZoneId DST in quiet-hours scheduler
```

### E1 — Search System
```
Numbers:   50M docs | 3.5K QPS peak | 50 ES shards (by doc_id hash) | 80% Redis cache hit rate
Decisions: Elasticsearch ✅  |  Kafka async indexing ✅  |  doc_id sharding ✅
DocuSign:  SearchQueryBuilder.buildSecureQuery() — tenant isolation filter mandatory (not optional)
```

### E2 — Authentication System
```
Numbers:   10M users | 3.5K logins/sec | 35K token validations/sec | 70% enterprise MFA adoption
           → 15-min JWT blacklist closure | 7-year access_audit_log retention (legal)
Decisions: JWT RS256 ✅  |  Redis blacklist ✅  |  RBAC + per-doc ACL ✅  |  Email OTP + TOTP ✅
DocuSign:  public computer JWT post-logout | SIM-swap fraudulent contract | "Adopt & Sign" latency risk
```

---

## ⚡ Step 3 — Know where to look inside any file

All 13 files have identical section structure. Memorize this once:

| Section | Content | When to open it |
|---|---|---|
| **S2** | Memory Anchors (6 sentences) | Read aloud in 30s before the interview |
| **S4** | Scale Estimation (exact numbers) | When blanking on a specific number |
| **S5** | Requirements Variation Table | When interviewer changes a requirement mid-interview |
| **S6** | Progressive HLD (3 stages) | When drawing the architecture diagram |
| **S7** | Deep Dives (3 components) | When asked "walk me through how X works" |
| **S10** | Trade-offs + failure modes | When asked "what's the downside of your choice?" |
| **S12** | Probe Q&As (3 tiers) | When probed on a specific edge case |
| **S14** | DocuSign Dimensions | When asked "how does this apply at DocuSign specifically?" |

---

## ⚡ Step 4 — 60-minute time budget (same for all files)

```
Min 0–5:   Clarifying questions (S3) — never skip this
Min 5–10:  Scale estimation (S4) — say 3 numbers aloud, write on board
Min 10–25: HLD walkthrough (S6) — draw 3 stages, not just the final state
Min 25–40: Deep dives (S7) — pick the 2 riskiest components, not the easiest
Min 40–50: Trade-offs (S10) — for each choice, name what breaks if you got it wrong
Min 50–60: SOLID/patterns (S13) + probe Q&As (S12) — be ready for DocuSign angle (S14)
```

---

## ⚡ Common cross-cutting patterns (say these without thinking)

| Pattern | Say this |
|---|---|
| **Why outbox?** | "Dual-write gap: crash between DB write and Kafka publish = silent event loss. Outbox makes DB write + publish atomic under one transaction." |
| **Why cursor not offset?** | "OFFSET 10000 scans 10K rows to discard them — O(N). Keyset cursor jumps to exact row in index — O(log N) regardless of depth." |
| **Why Redis Lua?** | "INCR and EXPIRE are two commands — a crash between them leaves counter decremented with no TTL. Lua executes atomically; there is no gap." |
| **Why 302 not 301?** | "301 is cached by the browser forever. Analytics never see the redirect again. 302 forces every redirect through the server — every click is recorded." |
| **Why pre-signed URLs?** | "If the app proxies downloads, 100M downloads/day × average 5 MB = 500 TB/day through our fleet. Pre-signed URLs offload 100% of that to S3/CDN." |
| **Why token bucket?** | "Sliding Window Log is exact but stores one entry per request — 35K req/sec × 60s window = 2.1M entries in memory. Token bucket is O(1) memory regardless of request rate." |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Jul 5, 2026 | **Created.** Retrieval cheat sheet for all 13 solution files. Trigger map, per-file 3-numbers + 3-decisions + DocuSign angle, section navigation guide, 60-minute time budget, 6 cross-cutting patterns. |
