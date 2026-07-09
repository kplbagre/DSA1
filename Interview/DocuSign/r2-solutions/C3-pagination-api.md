# C3 — Pagination API + Data Model Design

> **Read `solution-notes-standards.md` first.** This file is a 60-minute interview answer framework, not a concept reference. Concept-level depth lives in `SystemDesignConcepts/`.

---

## 📚 Prerequisites — Study These First

Before reading the solution, make sure you can explain the bolded items below from memory.

| Concept | File in `SystemDesignConcepts/` | Why you need it for this question |
|---|---|---|
| **Cursor-based pagination** | `Foundations/Data-Fundamentals/43-pagination-cursor-based.md` | The core topic — you must be able to explain why cursor pagination beats offset pagination (stable ordering, no skip cost, no page drift on inserts/deletes) and implement it from scratch |
| **Database indexing** | `Foundations/Data-Fundamentals/50-database-indexing.md` | Cursor pagination is only fast if the cursor column has an index — know composite index design for multi-column sort keys |
| **API design** | `Foundations/Data-Fundamentals/11-api-design.md` | REST contract for paginated endpoints: query params (`after`, `limit`), response envelope (`data`, `next_cursor`, `has_more`), idempotency on reads |
| **Caching fundamentals** | `Foundations/Performance-and-Scale/03-caching.md` | First-page caching for stable sorted lists — know when cursor results are safe to cache and when they aren't |
| **Scaling reads** | `Patterns/DeepDive/01-scaling-reads.md` | High-traffic list endpoints hit read replicas — know the cursor + replica routing strategy |

---

## 🧠 How to Use This File

**This file is an instantiation of DELIVERY-RECIPE** (`Interview/DocuSign/DELIVERY-RECIPE.md`). Every section below maps to one step of the 6-step interview delivery framework. The framework is backed by cognitive psychology — under stress, your working memory shrinks 40–50%, so you need ONE rhythm you can execute automatically.

**Before your interview:**
1. Read DELIVERY-RECIPE.md once to understand the psychology (30 min)
2. Skim the 6 **Memory Anchors** below (2 min)
3. Read this entire file and the 3 **Common Mistakes** (Section 13) so you know what to avoid (20 min)
4. During the interview, follow the 6-step rhythm: Ask → Clarify → Requirements → Estimate → HLD → Deep Dives → Trade-offs → Dimensions → Probes

**The time budget:**
- Minutes 0–5: Sections 1–2 (Opener + Clarifying questions)
- Minutes 5–10: Sections 3–4 (Requirements + Scale estimation)
- Minutes 10–25: Sections 5–6 (Requirements variation + API + Data model)
- Minutes 25–40: Section 7 (Deep dives: 2–3 riskiest components)
- Minutes 40–48: Section 10 (Trade-offs: exactly 3, with failure modes)
- Minutes 48–52: Section 11 (DocuSign dimensions — map explicitly)
- Minutes 52–60: Section 12 (Interviewer probes — prepared Tier 1/2/3 answers)

**Note:** Type B questions emphasize API design and data model. Sections 8 (API) and 9 (Data Model) are primary deliverables.

**Stay on this schedule.** If you're at minute 45 and still deep-diving, pause and move to trade-offs — the rubric values trade-off thinking over technical depth.

---

## 💾 Memory Anchors (Memorize These 6)

Before every interview, say these 6 sentences to yourself (takes 30 seconds):

1. **"Ask before you design."** — Don't assume. Use Section 2 to ask clarifying questions and confirm scope.
2. **"Name the nouns."** — Entities are your mental hooks. When stressed, you can remember categories even if you forget details.
3. **"Define the boundary."** — The API/interface is the contract. Lock it down before you argue about implementation.
4. **"Trace a request."** — Section 6's data flow narrative shows you understand movement through the system, not just boxes.
5. **"Draw the boxes."** — ASCII HLD is your mental model made visible. The interviewer can probe specific boxes without restarting.
6. **"Dig where it's risky."** — Section 7: pick 2–3 *riskiest* components (where the system breaks, where scale hits hardest), not the most *interesting* ones.

**Bonus anchors (if you have memory space):**
- "Everything is a trade-off." → Section 10
- "Why, not what." → Explain reasoning, not just technology
- "Conversational, not presentation." → Think aloud; don't recite

---

## Section 0 — Question Identity Card

| | |
|---|---|
| **Question** | Pagination API + Data Model Design |
| **Interview Type** | Type B — Product Architecture |
| **Confirmed or Likely** | ⭐ Confirmed asked (1Point3Acres thread: "Tech Phone Screen: Pagination API and Data Model Design". Confirmed by DocuSign's own engineering blog on API pagination. Topics: cursor vs offset pagination, consistency guarantees, performance at scale.) |
| **Concept notes prerequisite** | `11-api-design.md` (REST pagination: offset, cursor, keyset strategies), `12-data-modeling.md` (indexing for query performance, consistency) |
| **DocuSign-specific angle** | API pagination is critical for SaaS products. DocuSign's focus: pagination must be consistent (no duplicate/missing records when data changes mid-pagination), performant (cursor-based pagination avoids full table scans), and human-friendly (predictable ordering). Used for audit logs, transaction histories, document lists. |
| **Time budget** | 5 min clarify → 5 min estimate → 15 min HLD → 15 min deep dive → 8 min trade-offs → 7 min Q&A buffer |

---

## Section 1 — 🚀 The One-Sentence Opener

> "Before I draw anything, let me ask a few clarifying questions — specifically about the dataset size, access patterns, consistency requirements, and whether data changes during pagination, because those drive the pagination strategy."

Then immediately go to Section 2. Do NOT start drawing.

---

## Section 2 — 🔍 Clarifying Questions Script (Minutes 0–5)

**What to do:** Ask 4–6 questions that clarify scope. Don't assume. The interviewer is watching how you *think*, not how fast you talk.

**Say this out loud (after your opener):**
> "I have a few clarifying questions so I make sure I'm building the right thing..."

---

**Q: "How large is the dataset we're paginating — 100 records, 1 million, or 1 billion? Is it growing unbounded?"**
- Why ask: offset pagination becomes slow at large scale (query must scan N rows to skip to page N). Cursor pagination is better for unbounded data.
- Small (< 10K) → offset is fine
- Large (> 1M) → cursor/keyset pagination required
- Unbounded → cursor is essential

---

**Q: "What's the access pattern — do users page forward sequentially (1→2→3), or do they jump to random pages (go to page 50)?"**
- Why ask: cursor pagination works for sequential; offset works for random. Can't do both efficiently.
- Sequential → cursor pagination optimal
- Random → offset pagination required (but slower at scale)

---

**Q: "Do we need strict consistency in the pagination — if a record is deleted while I'm paging through results, should I see a gap, or is eventual consistency acceptable?"**
- Why ask: consistency affects indexing strategy. Strict consistency requires snapshot isolation; eventual consistency allows simpler pagination.
- Strict → snapshot isolation (complex, slower)
- Eventual → cursor from current state (simpler)

---

**Q: "What ordering is important — by creation time, by ID, by relevance? Can the ordering change during pagination?"**
- Why ask: cursor is based on the primary ordering. If ordering changes mid-pagination, pagination breaks.
- Stable ordering → cursor works
- Unstable (e.g., relevance scores change) → must re-paginate from start each time

---

**Q: "Should users be able to resume pagination later with a persistent cursor, or only within a single session?"**
- Why ask: persistent cursors are more durable (survive session/API restart) but require more careful design (handle deleted records).
- Persistent → encode deletion resilience into cursor
- Session-only → simpler; discard cursor on session end

---

**Q: "What's the maximum page size (records per request)? Is it configurable by the client?"**
- Why ask: large page sizes put more load on the database. Should we cap it?
- Client-configurable → need validation/limits
- Server-controlled → simpler

---

**Assumed answers (state these at the start of Section 3):**
- Type B focus — API design + data model
- Large dataset (1M+ records, growing)
- Sequential forward pagination (users scroll through results)
- Eventual consistency acceptable (small gaps OK if records deleted mid-pagination)
- Stable ordering by creation timestamp, then ID for tie-breaking
- Session-based cursors (no need for persistence across sessions)
- Server-controlled page size (fixed at 50 records/page)

---

## Section 3 — 📋 Requirements

**Functional Requirements (what the system does):**
- API endpoint: GET /items?cursor={cursor}&limit=50 returns paginated results
- Each response includes: items array, next_cursor (for fetching next page), has_more flag
- Cursor encodes: last_item_id and last_item_timestamp for deterministic ordering
- Forward pagination only (no backward paging, no random access)
- Ordering: items by created_at DESC (newest first), then by id DESC for stability

**Out of scope (say these explicitly):**
- Backward pagination (cursor pointing to previous page)
- Random access (jump to page 50 directly)
- Persistent cursors across sessions
- Sticky cursor (resume from deleted item if it existed)
- Cursor expiry (assume cursors are valid indefinitely)
- Sorting by client-specified column (fixed ordering only)

**Non-Functional Requirements:**
- Scale: 1M+ items in database, 100K active pagination sessions/hour
- Latency: P99 < 200ms for any page fetch (even last page with cursor scan)
- Consistency: eventual (small gaps if items deleted during pagination)
- Availability: 99.9% (pagination is not a critical path)
- Cursor encoding: opaque to client (implementation detail, can change)

---

## Section 3.5 — 🗂️ Core Entities (~2 minutes)

> **Say this out loud:** "Before I sketch the architecture, let me name the key data objects the system manages."

> **Note:** Pagination is a generic pattern — the "entities" here are the paginated resource and the cursor mechanism, not a complex domain model.

| Entity | What it represents | Storage |
|---|---|---|
| **Item** | The generic resource being paginated — could be documents, orders, users, invoices | PostgreSQL (any table with an index) |
| **PaginationCursor** | An encoded pointer to the last-seen position — client sends it back on next request | Client-held (not stored server-side) |

**Key insight to say out loud:**
- The cursor is **not a server-side session** — it is a stateless pointer that encodes `(last_seen_id, last_seen_created_at)` and is held by the client; the server reconstructs page position from it on every request with no server memory
- The `Item` table needs a **composite index on `(created_at DESC, id DESC)`** — without it, cursor queries become full table scans at O(N) instead of O(log N) seeks

---

## Section 8 — 🌐 API Design (Minutes 8–13) ⭐ Type B Primary Deliverable

> **Why here, not later:** For Type B (Product Architecture), the API contract is the primary deliverable. State it before the architecture — the architecture *serves* the API, not the other way around.

### 🧠 How to Derive This Endpoint

This problem is a special case: there is only **one endpoint**. The interesting question isn't "which endpoints?" — it's "how do you design the one endpoint that handles pagination correctly?"

The FR says: *"Return large datasets in pages with no missing or duplicating records."*

One endpoint falls out immediately: `GET /v1/items`. But the FR's constraint — "no missing or duplicating records" — is where the design lives.

Offset pagination (`LIMIT 50 OFFSET 100`) fails this constraint: if a record is inserted at position 60 while the client is on page 2, page 3 gets shifted by one — the client either skips record 100 or sees it twice. The constraint *rules out* offset.

Cursor pagination survives because it doesn't count rows — it remembers *which record it saw last*. The next request says "give me records after this one" — inserts before it are invisible; the client never sees them. The cursor encodes `(last_seen_created_at, last_seen_id)` as a base64 opaque blob so the client can't forge or modify it.

Validation check: map the FR back to the design. "No missing records" → cursor skips nothing. "No duplicates" → cursor is position-anchored, not count-based. Both FRs satisfied.

### Core Endpoints

| Method | Path | Query Params | Response Body | Status Codes |
|---|---|---|---|---|
| GET | `/v1/items` | `cursor` (optional), `limit` (optional, max 100, server caps at 50) | `{ "items": [...], "next_cursor": "...", "has_more": true }` | 200, 400, 410 |

**Response format:**

```json
{
  "items": [
    { "id": "...", "created_at": "2026-06-23T10:00:00Z", "name": "..." }
  ],
  "next_cursor": "eyJsYXN0X2lkIjoiMjUwIiwibGFzdF9jcmVhdGVkX2F0IjoxNjg3NTI4MzAwfQ==",
  "has_more": true
}
```

### 🔍 Endpoint Story

**`GET /v1/items`** is the whole problem. Every design choice in this system exists to answer the question: *how does this one endpoint behave under concurrent writes, at scale, without breaking clients?*

The `cursor` parameter is optional — omitting it means "first page." This is intentional: clients don't need to know they're doing cursor pagination; they just follow `next_cursor` until `has_more` is false. The cursor is an opaque base64 blob on purpose: if the client could see `{"last_id": 250, "last_created_at": 1687528300}`, they might try to forge `{"last_id": 1, "last_created_at": 0}` to restart the stream without telling the server — or worse, enumerate IDs to probe what exists.

`has_more: true` is a subtle UX gift. Without it, the client has to make one final empty request to know pagination is done. With it, the client renders a "Load more" button or stops the auto-pager immediately — one fewer round trip per session, multiplied by 100K sessions/hour.

The `410 Gone` response is what most candidates miss. If the cursor encodes a timestamp from 3 hours ago and rows have been deleted in between, the database position the cursor points to may no longer exist. `404 Not Found` is wrong (the endpoint exists). `400 Bad Request` is wrong (the cursor was valid when issued). `410 Gone` says "this cursor existed and is now expired" — the client should restart from page 1, not retry.

The `limit` server cap at 50 (even if client requests 100) protects the database from unbounded scans. The cap is enforced silently — the client gets ≤ 50 rows and a valid `next_cursor`. The interviewer will ask: "What if the client hardcodes `limit=100` and your server returns 50 — won't that confuse them?" Answer: the `next_cursor` is always correct regardless of how many rows you return; the client should never depend on getting exactly `limit` rows back.

---

## Section 4 — 🔢 Scale Estimation (Minutes 5–10)

**What to do:** Do envelope math out loud. These numbers justify every architecture choice you make in Section 6+.

**Say this out loud (as you write the math on the whiteboard):**
> "Let me do some envelope math to justify the pagination strategy. Starting with scale..."

---

**Scale:**
- Total items: 1M
- Active pagination sessions/hour: 100K
- Average requests/session: 10 (user browses ~10 pages)
- Requests/sec: 100K × 10 ÷ 3,600 = ~278 requests/sec
- Page size: 50 records/page
- Worst case: last page with cursor scan requires scanning all 1M records (O(N))

**Key conclusions:**
- "At 278 requests/sec, database must serve pagination efficiently. Offset pagination with LIMIT/OFFSET would require scanning N rows for page N; at 1M rows, this becomes slow."
- "Cursor pagination with indexed (created_at, id) is O(1) lookups: WHERE created_at < ? OR (created_at = ? AND id < ?) LIMIT 50. Always < 50 row scans."
- "Cursor strategy is required for performance at this scale."

---

## Section 5 — 🔄 Requirements Variation Table ⭐ KEY SECTION

| If the interviewer says... | Your pagination strategy changes to... | The reasoning |
|---|---|---|
| "Random access required (jump to page 50)" | Switch to offset pagination: OFFSET 2500 LIMIT 50. Accept slower performance (full scan required). Or pre-compute page boundaries via Elasticsearch. | Cursor pagination only works for sequential access. Random access needs OFFSET or index on page boundaries. At 1M records, this is slower. Trade-off: simplicity vs performance. |
| "Backward pagination (previous page)" | Add reverse cursor: encode "next page going backward". Retrieve N+1 items, reverse the order, use first as cursor. | Backward pagination is complex because OFFSET doesn't work backward. Use keyset pagination: WHERE created_at > ? OR (created_at = ? AND id > ?). More complex logic. |
| "Persistent cursors (resume across sessions)" | Encode item ID + timestamp in cursor. On resume, handle case where item was deleted: scan forward until next valid item. | Cursors must encode enough info to resume even if items change. Deletions require gap-handling logic (skip deleted items). Increases cursor complexity. |
| "Sorted by relevance score (dynamic ordering)" | Can't use cursor pagination — relevance changes. Switch to offset pagination or re-rank on every request. Accept inconsistency. | Cursor is based on stable ordering. If scores change, pagination breaks (items shift positions). Either accept offset slowness or rebuild cursor per request. |
| "Cursor expiry (cursors valid for 1 hour)" | Add timestamp to cursor; validate on request. If expired, return 410 Gone + new cursor. | Cursor lifespan affects UX (users can't resume after 1 hour). Simpler to allow indefinite cursors; business decides policy. |
| "Sticky cursor (deleted items don't break pagination)" | Encode deletion timestamp. On resume, scan forward from deleted item to next valid. More complex logic. | Without sticky cursors, pagination breaks if items are deleted. Sticky cursors let users resume transparently. Extra complexity (scan forward logic). |

---

## Section 6 — 🏗️ High-Level Architecture (Minutes 10–25)

**What to do:** Draw the boxes (ASCII or whiteboard). Walk through the data flow *as if telling a story*.

**Note:** Type B questions often focus on API + schema. The HLD diagram is often skipped; focus on the pagination flow.

**Say this out loud (as you transition to API/schema):**
> "For pagination API design, the architecture is straightforward: web API → database with indexed lookup. Let me focus on the cursor design and the SQL strategy..."

---

### 🎨 Visual — Pagination Strategy Evolution (3-Stage Progression)

> ⚠️ **Note:** C3 is a **Type B (API + Query Strategy)** question. The "architecture" is simple (client → API → DB). The interview value is in *how the query and cursor design evolve* — the stages below show that progression.

---

**Stage 1 — Offset Pagination (LIMIT/OFFSET)**

```
[Client]
    ↓  GET /items?page=2&page_size=50
[API Server]
    ↓  SELECT * FROM items
       ORDER BY created_at DESC
       LIMIT 50 OFFSET 50
[Database]
    └─ Full scan: skip first 50 rows, then return rows 51-100
    ↓
[Response: items[], total_count, page, page_size]
    ↓
[Client]

BREAKING POINT 1: At 1M records, OFFSET 500000 scans 500K rows before returning 50.
                  Query time: O(N). P99 → 5s → SLA breach.
BREAKING POINT 2: New doc inserted between page 1 and page 2 requests → every row
                  shifts → client sees duplicate item on page 2 or skips one.
```

---

**Stage 2 — Cursor on Timestamp Only (No Compound Index)**

```
[Client]
    ↓  GET /items?cursor={base64(last_created_at)}
[API Server]
    ├─ Decode cursor → last_created_at
    └─ SELECT * FROM items
       WHERE created_at < ?
       ORDER BY created_at DESC
       LIMIT 51
[Database — single-column index on created_at]
    └─ Index range scan on created_at, but ties resolved by heap fetch (partial scan)
    ↓
[API Server]
    └─ Encode next_cursor = base64(row[49].created_at)
    ↓
[Response: items[], next_cursor, has_more]
    ↓
[Client]

BREAKING POINT 1: Timestamp ties — two docs with identical created_at millisecond →
                  cursor boundary is ambiguous → duplicates or skips at page edges.
BREAKING POINT 2: Plain base64 cursor → client can forge cursor payload and jump to
                  any position in the dataset (data scraping / DoS risk).
```

---

**Stage 3 — Production: Composite Cursor + Compound Index + HMAC Signing**

```
[Client]
    ↓  GET /items?cursor={HMAC-signed base64("last_created_at|last_id")}
[API Server]
    ├─ 1. Validate HMAC signature with server secret → reject if invalid (400)
    ├─ 2. Decode → last_created_at, last_id
    └─ 3. SELECT * FROM items
             WHERE (created_at < ?)
                OR (created_at = ? AND id < ?)
             ORDER BY created_at DESC, id DESC
             LIMIT 51
[Database]
    └─ Compound index: (created_at DESC, id DESC)
       └─ O(50) rows scanned — constant regardless of table size (1M, 10M, 100M)
    ↓
[API Server]
    ├─ 4. Slice first 50 rows; has_more = (result_count == 51)
    └─ 5. next_cursor = HMAC-sign(base64(row[49].created_at + "|" + row[49].id))
    ↓
[Response: items[], next_cursor, has_more]
    ↓
[Client]

KEY INVARIANT:
   created_at is IMMUTABLE (set once at insert, never updated).
   id is globally unique. Together they form a stable, tie-free sort key.
   Compound index means every page fetch is O(50) rows — never O(N).
   Concurrent deletions mid-pagination cause gaps (documented in API contract):
     "Eventual consistency — items deleted after cursor issued may not appear."
```

---

**Pagination Strategy Decision**

| Option | Strength | Weakness | Verdict |
|---|---|---|---|
| `LIMIT/OFFSET` | Simple; supports random page jump | O(N) scan at depth; row shifts on insert = duplicates across pages | ❌ |
| Cursor on `created_at` only | O(1) vs offset; stable for most docs | Timestamp ties cause duplicates/skips at page edges | ⚠️ |
| Cursor on `(created_at, id)` composite | Tie-free, stable, O(1) via compound index | Forward-only (no random page jump) | ✅ |

> 📖 Full: `SystemDesignConcepts/Foundations/Data-Fundamentals/43-pagination-cursor-based.md`

---

**Cursor Security Decision**

| Option | Strength | Weakness | Verdict |
|---|---|---|---|
| Plain base64 JSON | Simple to implement | Client can forge any cursor position → data scraping / DoS | ❌ |
| DB row ID cursor (opaque server-side token) | Tamper-proof | Requires server-side cursor state; DB lookup per page; scaling issue | ⚠️ |
| HMAC-signed base64 | Stateless, tamper-proof, no DB lookup on validation | Server must keep secret; key rotation = old cursors invalid (use versioned key) | ✅ |

> 📖 Full: `SystemDesignConcepts/Foundations/Data-Fundamentals/12-data-modeling.md`

---

**Consistency Model Decision**

| Option | Strength | Weakness | Verdict |
|---|---|---|---|
| No handling (ignore concurrent mutations) | Zero complexity | Silent duplicates/skips when items deleted mid-session → broken client UX | ❌ |
| Strict snapshot isolation (MVCC snapshot per session) | Clients see stable view across all pages | Locks rows; prohibitive cost at scale; unnecessary for document browsing | ⚠️ |
| Eventual consistency (document gaps in API contract) | Zero overhead; correct for document browsing use case | Clients may see gaps if items deleted after cursor issued — must be documented | ✅ |

> 📖 Full: `SystemDesignConcepts/Foundations/Data-Fundamentals/43-pagination-cursor-based.md`

---

**Data flow (say this out loud):**

1. **Initial request:** Client calls `GET /items`. No cursor → first page.
2. **Build query:** `WHERE created_at < NOW() ORDER BY created_at DESC, id DESC LIMIT 51`.
3. **Return response:** Slice 50 items; encode `row[49].created_at + "|" + row[49].id` → HMAC-sign → `next_cursor`. Set `has_more = true` (got 51 rows).
4. **Next request:** Client calls `GET /items?cursor={signed_token}`. Server validates HMAC → rejects forgeries → decodes `last_created_at`, `last_id`.
5. **Build query:** `WHERE (created_at < ?) OR (created_at = ? AND id < ?) ORDER BY created_at DESC, id DESC LIMIT 51`. Compound index hits O(50) rows.
6. **Return response:** Slice 50 items, encode new cursor, `has_more = (result_count == 51)`.

---

## Section 7 — 🔬 Core Component Deep Dives (Minutes 25–40)

**What to do:** Pick 2–3 *riskiest* components. "Riskiest" = where the system breaks, where scale hits hardest.

**Why these 3 for pagination?**
1. **Cursor encoding and decoding** — Wrong design = cursors are invalid/insecure, pagination breaks
2. **SQL strategy (offset vs cursor)** — Wrong choice = pagination is slow at scale (timeout/503 errors)
3. **Index strategy** — Wrong indexes = cursor queries are still slow (full table scans)

**Say this out loud:**
> "Let me go deep on the three riskiest components — the ones where the system most likely breaks at scale..."

---

### Deep Dive 1: Cursor Design — Encoding and Decoding

**Why this is the most critical component:**
The cursor is the contract between client and server. Wrong encoding = cursors are invalid/insecure (client can forge cursors). Wrong decoding = pagination breaks (client can't resume from a valid cursor).

**Cursor options:**

| Option | Encoding | Pros | Cons |
|---|---|---|---|
| **Opaque base64** | Encrypt(last_id + last_created_at) | Tamper-proof (encrypted). Secure against client forging cursors. | Requires encryption overhead. Cursors are large. |
| **Plain base64** | Base64(JSON({last_id, last_created_at})) | Simple, readable for debugging. Fast encoding/decoding. | Client can forge cursors. Cursors may be leaked in logs. |
| **Signed base64** | HMAC(JSON({last_id, last_created_at})) — HMAC (Hash-based Message Authentication Code) is a keyed cryptographic hash: it proves the data wasn't tampered with because only the server holding the secret key can produce or verify the signature | Tamper-proof via signature. Smaller than encrypted. Can decode for debugging. | Signature adds bytes. Still human-readable (privacy concern). |
| **Database cursor ID** | Generate opaque cursor ID; store in cache with metadata | Opaque to client; server controls validity. | Extra cache lookup per request. Cache management complexity. |

**Decision: Signed base64**
Because we need tamper-proof cursors (prevent client attacks) but also want efficiency (no extra DB lookups). Signing is faster than encryption and allows debugging.

**Cursor design:**

```java
// Encoding (server → client)
public String encodeCursor(Item lastItem) {
    String data = "{\"last_id\":\"" + lastItem.getId() + 
                  "\",\"last_created_at\":" + lastItem.getCreatedAt() + "}";
    String signature = hmacSha256(data, SECRET_KEY);
    return base64Encode(data + "." + signature);
}

// Decoding (client → server)
public CursorData decodeCursor(String cursor) throws InvalidCursorException {
    byte[] decoded = base64Decode(cursor);
    String[] parts = decoded.split("\\.");
    if (parts.length != 2) throw new InvalidCursorException("Invalid format");
    
    String data = parts[0];
    String signature = parts[1];
    String expectedSignature = hmacSha256(data, SECRET_KEY);
    
    if (!signature.equals(expectedSignature)) {
        throw new InvalidCursorException("Invalid signature (tampered)");
    }
    
    return parseJSON(data);  // { last_id, last_created_at }
}
```

**In an interview, if asked:** "Cursor design must be tamper-proof to prevent client attacks. I use HMAC signing: server encodes the last item's ID and timestamp, signs it with a secret key, and returns as base64. On decode, I verify the signature before trusting the cursor. This prevents clients from forging cursors and jumping to arbitrary positions."

---

### Deep Dive 2: SQL Strategy — Cursor vs Offset

**Why this is the riskiest component:**
At 1M records, offset pagination (LIMIT/OFFSET) becomes slow. Query planner must scan N rows to OFFSET to page N. At N=1M, this is a full table scan every request, killing performance.

**SQL strategies:**

| Strategy | Query | Performance | Use case |
|---|---|---|---|
| **Offset** | SELECT * FROM items LIMIT 50 OFFSET 2500 | O(OFFSET + LIMIT) = O(2550 rows scanned) | Small datasets (< 10K); random access |
| **Cursor** | SELECT * FROM items WHERE created_at < ? OR (created_at = ? AND id < ?) LIMIT 50 | O(LIMIT) = O(50 rows scanned) | Large datasets; sequential access |
| **Keyset** | WHERE id > last_id ORDER BY id LIMIT 50 (keyset: instead of skipping N rows with OFFSET, you filter on the last-seen value — the "key" you've already seen — so the DB index-seeks directly to the right position; O(1) not O(N)) | O(LIMIT) = O(50 rows scanned) | Very large datasets; simple single-column ordering |

**Decision: Cursor pagination with compound key**
Because at 1M records, offset becomes O(N) and unacceptable. Cursor is O(1) lookups with proper indexing.

**Index requirement:**

```sql
-- Critical index for cursor pagination
CREATE INDEX idx_items_created_at_id ON items(created_at DESC, id DESC);

-- Why compound index?
-- Query: WHERE (created_at < ? OR (created_at = ? AND id < ?)) LIMIT 50
-- Index allows: (1) find all rows with created_at < value (range scan on index)
--               (2) within that, find rows with id < value (refinement on index)
-- Without index: full table scan of 1M rows on every query = timeout
```

**Query execution:**

```sql
-- Fetch next 50 items after cursor (last_created_at, last_id)
-- Fetch 51 rows to detect if has_more=true
SELECT id, created_at, name
FROM items
WHERE created_at < ?   -- ? = last_created_at
   OR (created_at = ? AND id < ?)   -- ? = last_id (tie-breaker)
ORDER BY created_at DESC, id DESC
LIMIT 51;

-- If result has 51 rows, has_more = true (omit row 51 in response)
-- If result has ≤ 50 rows, has_more = false (this is the last page)
```

---

### Deep Dive 3: Handling Concurrent Mutations — Data Consistency

**Why this is the riskiest component:**
While client pages through results, records may be added/deleted. This causes: (1) duplicate records (item appears on both page 1 and page 2), or (2) missing records (item skipped). Must handle gracefully.

**Mutation scenarios:**

```
Scenario 1: Record deleted mid-pagination
- Page 1 fetched: items A, B, C
- Item B is deleted
- Page 2 fetches: WHERE created_at < C's created_at
- Result: items D, E (B is gone, no duplicate)
- Outcome: No duplicate; one missing record (acceptable per eventual consistency)

Scenario 2: Record added at the beginning
- Page 1 fetched: items A, B, C (ordered by creation DESC)
- New item Z is added (newest)
- Page 2 fetches: WHERE created_at < C's created_at
- Result: items D, E (Z is never seen if user doesn't reset pagination)
- Outcome: Missing record (acceptable per eventual consistency)

Scenario 3: Record updated (timestamp changes)
- Page 1 fetched: items A, B, C
- Item C is updated (updated_at changes, but we order by created_at)
- Page 2 fetches: WHERE created_at < C's created_at
- Result: items D, E (unchanged, because we order by created_at, not updated_at)
- Outcome: No impact
```

**Solution: Accept eventual consistency for pagination**

The design acknowledges that pagination is a snapshot at a point in time. If data changes (adds/deletes), small gaps or duplicates are acceptable. Alternative (strict snapshot isolation) would require:
- Lock the entire table during pagination (unacceptable overhead)
- Use database snapshots (Postgres MVCC — Multi-Version Concurrency Control: the DB keeps multiple row versions simultaneously so a transaction sees a consistent point-in-time snapshot without blocking other writers; every PostgreSQL query already uses MVCC under the hood) to freeze the view for the entire pagination session (works, but long-lived transactions hold old row versions in memory, which can balloon storage and slow vacuuming)

**In an interview:** "Pagination under concurrent mutations is inherently eventual consistent. If a record is deleted while I'm paging, I won't see it (missing). If a record is added at the head, I won't see it (missing). This is acceptable for most use cases (user browse, audit logs). If strict consistency is required, I'd use snapshot isolation (Postgres MVCC), but this limits scalability."

---

## Section 9 — 🗄️ Data Model

### Core Tables

```sql
CREATE TABLE items (
    id              UUID PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMP,  -- soft delete
    
    -- Composite index for cursor pagination
    INDEX idx_items_created_at_id ON (created_at DESC, id DESC)
);
```

### Key Schema Decisions:
- **created_at immutable:** Used as primary sort key; never changes. Ensures stable cursor ordering.
- **Composite index (created_at, id):** Enables efficient cursor queries. First filter by created_at range (indexed), then by id (secondary sort).
- **deleted_at (soft delete):** Pagination ignores soft-deleted items (WHERE deleted_at IS NULL in queries). Allows recovery.
- **id as tie-breaker:** If two items have same created_at (rare but possible), id breaks ties deterministically.

### Index Design Rationale:

```sql
-- Without index: WHERE created_at < ? ORDER BY created_at DESC LIMIT 50
-- Full table scan: O(N) = 1M rows scanned = 100-200ms (timeout risk)

-- With index:  
-- PostgreSQL uses index to:
--   1. Find all rows with created_at < value (range scan on index) - O(log N) seek
--   2. Return first 50 rows in index order - O(50) rows read
-- Total: O(log N + 50) = O(log 1M + 50) = O(20 + 50) = O(70 rows) = < 1ms
```

---

## Section 10 — ⚠️ Trade-offs + Failure Modes (Minutes 40–48)

**Say this out loud:**
> "Let me step back and name the three major trade-offs in this design..."

---

### Trade-off 1: Cursor Pagination (Fast, Sequential) vs Offset Pagination (Slow, Random)

- **Chose:** Cursor pagination
- **Gain:** O(1) lookups; supports 1M+ records without performance degradation. Always < 200ms latency.
- **Lose:** No random access (can't jump to page 50 directly). Must page forward sequentially.
- **Failure mode if wrong:** If we chose offset pagination, query `SELECT * FROM items OFFSET 500000 LIMIT 50` requires scanning 500K rows. At 1M records, this times out (> 5 seconds). SLA breach. **Business impact:** A DocuSign enterprise admin loading envelope page 500+ of their sent history (common for high-volume customers with 100K+ envelopes) sees a 5-second timeout — the admin portal is unusable for compliance audit or eDiscovery export, and the customer's legal team cannot retrieve the signed documents they need for a discovery deadline.

### Trade-off 2: Eventual Consistency (Missing Records) vs Strict Snapshot Isolation (Locked Table)

- **Chose:** Eventual consistency (acceptable missing records during pagination)
- **Gain:** No table locks during pagination. Pagination is fast; concurrent writes aren't blocked.
- **Lose:** If data changes mid-pagination, small gaps/duplicates possible.
- **Failure mode if wrong:** If we chose strict snapshot isolation (Postgres MVCC snapshot), every pagination query holds a long-lived transaction. Concurrent writers must wait for the snapshot to end. At 100K requests/hour with 10 pages each, pagination transactions last ~1-2 seconds. Concurrent writers experience 1-2 second delays. System degrades. **Business impact:** During DocuSign's fiscal quarter-end signing rush, concurrent envelope creates (writes) stall behind pagination read transactions — new envelope creation slows from 200ms to 2+ seconds — senders trying to get contracts out before the deadline see sluggish responses, and the highest-revenue period becomes the worst-performing day.

### Trade-off 3: Opaque Cursor (Tamper-proof) vs Simple Cursor (Readable)

- **Chose:** Signed base64 cursor (tamper-proof yet readable)
- **Gain:** Client can't forge cursors or jump to arbitrary positions. Signatures prevent tampering.
- **Lose:** Cursor encoding/decoding overhead (HMAC signature adds 32 bytes per cursor).
- **Failure mode if wrong:** If we chose plain base64 (no signature), client could forge: `eyJsYXN0X2lkIjoiOTk5OTk5OTkifQ==` and jump to arbitrary items. Security risk (DoS via crafted cursors). Signed cursors prevent this. **Business impact:** A malicious tenant crafts cursors that jump to other tenants' envelope IDs (internal auto-increment IDs leak the namespace boundary) — for DocuSign this means a lower-tier customer can enumerate a competitor's contract metadata (a serious data-privacy breach) or flood the database with deep-page scans that degrade performance for all customers simultaneously.

---

## Section 11 — 🔐 DocuSign-Specific Depth (Minutes 48–52)

**What to do:** For Type B Product Architecture questions, DocuSign's evaluation focuses on API design correctness, SOLID principles, and data model choices. The DocuSign angle: how does pagination support enterprise features (audit logs, transaction histories, compliance)?

**After the trade-offs, say this out loud:**

> "Let me map this to DocuSign's evaluation dimensions and their product context:
> - **Scalability:** Cursor pagination with compound index supports 1B+ records without performance loss. O(50) rows scanned per request regardless of dataset size.
> - **Usability:** Simple API: GET /items?cursor={cursor}&limit=50. Response includes next_cursor and has_more flag. Client doesn't need to understand pagination internals.
> - **Extensibility:** Cursor design is independent of item schema. Adding new item fields doesn't break pagination (cursor only uses created_at, id). Sorting strategies (created_at DESC, then by ID) are parameterizable.
> - **Design patterns:** Opaque cursor pattern (client holds reference; server validates). Iterator pattern (client iterates through pages).
> - **Data model correctness:** Composite index ensures efficiency. Soft deletes preserve data integrity. created_at is immutable (never changes after creation).
> - **Consistency:** Eventual consistency is explicit (user understands small gaps possible). No hidden data mutations surprise the user.
> - **API design:** Cursor is opaque and tamper-proof (signed). has_more flag eliminates guessing. 410 Gone on cursor expiry signals state clearly."

---

## Section 12 — 🔬 Where the Interviewer Will Probe (Minutes 52–60)

**What to do:** Prepare for 3 tiers of follow-ups. Tier 1 (surface), Tier 2 (deep), Tier 3 (cross-concept).

---

### Surface Probe (Tier 1 — every candidate gets this)

**Q: "Why do you use a composite index on (created_at, id) instead of just (created_at)?"**
> The composite index allows the database to (1) find rows with created_at < value, AND (2) within that set, sort by id for deterministic tie-breaking. A single-column index on created_at alone would still require a secondary sort on id, which is slower. The composite index handles both in one pass. In an interview: "Composite index allows database to filter and sort in a single index scan, avoiding secondary sorts on large result sets."

**Q: "What happens if the client uses an old cursor after data has been deleted?"**
> The cursor points to a deleted item (last_id + last_created_at of a deleted row). The query WHERE created_at < {deleted_created_at} still works (returns items before that timestamp), so pagination continues correctly. The deleted item is never returned (good). If the client sends a cursor for an item that never existed, the query still works (returns items before that timestamp). Cursor validity is implicit (works as long as timestamps make sense). In an interview: "Cursors are resilient to deletions because they reference timestamps and IDs, not row pointers. Even if the row is deleted, the timestamp is still valid for filtering."

**Q: "Can you implement backward pagination (previous page) with this design?"**
> Yes, but with more complexity. Instead of WHERE created_at < ?, use WHERE created_at > ? (reverse direction). Return items in reverse order; reverse the array before sending to client. Cursor now encodes direction (forward/backward) so server knows which query to build. Alternatively, keep both forward and backward cursors in the response. Backward pagination is possible but requires extra logic. In an interview: "Backward pagination would require cursor to include direction; query reversal; order reversal in response. It's doable but adds complexity. Current design optimizes for the common case (forward)."

### Deep Probe (Tier 2 — tests real understanding)

**Q: "Your cursor design uses HMAC signature. But what if the SECRET_KEY is leaked? How do you rotate keys without invalidating all outstanding cursors?"**
> Great question. Key rotation requires a multi-stage process: (1) Add new key to the signing rotation set, (2) Start signing cursors with NEW key, (3) Accept both OLD and NEW signatures on decode, (4) After a grace period (e.g., 7 days), retire OLD key. This way, old cursors remain valid during rotation, and new cursors use the new key. Alternatively, include key version in cursor: HMAC({data}.v2) using key v2. On decode, read version; use correct key. In an interview: "Key rotation is critical for security. I'd support multiple keys during a grace period, allowing old cursors to remain valid while new ones use the new key."

**Q: "At 100K pagination requests/hour, the compound index on (created_at, id) becomes very hot (heavily accessed). Does this cause bottlenecks?"**
> Index hotspots can cause contention in the database. At 100K req/hour ÷ 3600 = ~28 req/sec, we're accessing the same index region (recent items with created_at ≈ now) repeatedly. Two solutions: (1) Sharding by created_at bucket (monthly partitions) — each partition has its own index; pagination queries hit only the relevant partition. (2) Read replicas — all pagination reads go to replicas; primary handles writes. Partitioning is cleaner for pagination. In an interview: "Index hotspots at scale require partitioning. Partitioning by created_at (e.g., monthly) ensures pagination queries hit only relevant partitions, avoiding contention."

**Q: "Your API design in Section 8 originally included a `total_count` field in the response. But at 100M rows, how do you return that count without a full table scan on every request?"**

> `SELECT COUNT(*) FROM items WHERE deleted_at IS NULL` on 100M rows is a sequential scan — it reads every row in the table to count them. At P99, this takes 5–30 seconds and saturates I/O. Returning this on every page request is a silent performance bomb.
>
> **Three strategies, ordered by trade-off:**
>
> **Option A — Exact count via materialized counter (recommended):**
> Maintain a `table_stats(table_name, count, updated_at)` row. On every INSERT: `UPDATE table_stats SET count = count + 1`. On every soft-delete: `UPDATE table_stats SET count = count - 1`. The total count read is `O(1)` — one indexed lookup. The trade-off: if an INSERT fails after the counter update, or a batch import runs outside the normal write path, the counter drifts. Run a nightly reconciliation job: `UPDATE table_stats SET count = (SELECT COUNT(*) FROM items WHERE deleted_at IS NULL)` during off-peak hours.
>
> **Option B — PostgreSQL approximate count via `pg_class`:**
> ```sql
> SELECT reltuples::BIGINT AS approx_count
> FROM pg_class WHERE relname = 'items';
> ```
> `reltuples` is updated by `AUTOVACUUM` — typically accurate to ±5%. Runs in `O(1)`. Trade-off: stale between vacuum cycles. Acceptable for UI "showing ~15 million results"; not acceptable for audit compliance.
>
> **Option C — Omit `total_count` entirely (cursor pagination's natural model):**
> Cursor pagination doesn't need a total count for correct operation. The `has_more` flag tells the client if there's a next page. UI shows "Load more" not "Page 3 of 47,000." Many high-scale APIs (Twitter timeline, GitHub feed) omit `total_count` and use `has_more` only. The trade-off: some enterprise UIs expect "Records 1-50 of 15,000" — this requires a count.
>
> **In an interview:** "I'd remove `total_count` from the cursor pagination response and replace it with `has_more`. If the product requires an approximate record count for UI display (e.g., search result headers), I'd maintain a cached counter table updated on every write, with O(1) reads. `SELECT COUNT(*)` on 100M rows is never acceptable on the request path."
>
> **⚠️ Interviewer test:** If you include `total_count` in your API response table without explaining how you compute it, a senior interviewer will probe immediately. Have this answer ready.

### Cross-Concept Probe (Tier 3 — separates senior candidates)

**Q: "Your design assumes created_at never changes. But what if we allow users to backdate items (e.g., 'this happened 3 days ago')? How does pagination break, and how do you fix it?"**
> This breaks pagination completely. If created_at can change retroactively, the ordering becomes unstable. Item A might have been on page 2 yesterday but page 5 today (if backdated before older items). Cursors become invalid (the item they point to might have moved). Solution: (1) Immutable created_at (use insertion_time; created_at is user-facing timestamp, separate), (2) Add a sequence number (auto-increment per item). Cursor uses sequence number for ordering, not created_at. Sequence number never changes. (3) Alternatively, multi-field cursor: (created_at, sequence_number, id). Ordering by (created_at DESC, sequence DESC, id DESC) ensures stability even if created_at changes. In an interview: "Pagination assumes immutable ordering. If the field used for sorting can change, pagination breaks. Solution: add an immutable sequence number (or use insertion_time, separate from user-facing created_at) for stable ordering."

---

### New Deep Probes (Tier 2 — added Jul 4, 2026)

**Q: "Compliance use cases require paginating through audit logs from weeks ago. A cursor that expires in 1 hour doesn't work. How do you support long-running pagination?"**
> Two patterns for long-lived pagination:
>
> **(A) Date-bounded snapshot pagination (recommended for compliance):**
> Instead of cursor-based pagination, use explicit date bounds: `GET /audit-logs?start_date=2026-06-01&end_date=2026-06-30&page=3&limit=100`. The date range is fixed and never changes — any INSERT after the query starts has a `created_at` outside the range and is invisible. Pagination is stable across days because the filter criteria are immutable. Internally, this is still keyset pagination (within the date range), but the client re-parameterizes with explicit dates rather than a cursor token.
>
> **(B) Persistent cursors in DB:**
> Store cursor state server-side: `cursor_checkpoints(cursor_token UUID, user_id, last_id, last_created_at, expires_at TIMESTAMPTZ)`. Client receives an opaque token, stores it, and passes it back. Server looks up the state in DB. `expires_at` can be 30 days for compliance use cases. Cleanup job removes expired cursors weekly.
>
> **Recommendation by use case:**
> - Real-time browsing (document list, inbox) → short-lived cursors (1 hour); acceptable to restart
> - Compliance export / audit log download → date-bounded snapshot pagination; no cursor expiry risk
> - Resumable large export → persistent cursor in DB (30-day expiry)
>
> **In an interview:** "I'd use date-bounded queries for compliance. The date range never changes, so pagination is inherently stable across multi-day exports. Cursor tokens are for transient UI sessions; date bounds are for durable, reproducible data exports."

---

**Q: "A user sorts their document list by 'last_modified_at'. Document A is on page 1. The user reads page 2. Meanwhile, Document A is modified — it now has a newer last_modified_at and moves back to page 1 position. When the user reads page 3, Document A is missing (skipped). How do you fix this?"**
> This is the mutable sort key problem — the hardest cursor pagination failure mode.
>
> **Why it breaks:** Cursor encodes `last_modified_at = T1`. After modification, Document A's `last_modified_at = T2 > T1`. Cursor query: `WHERE last_modified_at < T1` — Document A (now at T2) falls outside the window and is never returned.
>
> **Fix 1 — Immutable tiebreaker (always correct):** Sort by `(last_modified_at DESC, id DESC)` where `id` is an immutable auto-increment. Cursor encodes both values: `{last_modified_at: T1, id: 42}`. Query: `WHERE (last_modified_at < T1) OR (last_modified_at = T1 AND id < 42)`.
>
> If Document A's `last_modified_at` changes to T2 (T2 > T1), its position in the list changes — it moves to page 1. The user on page 3 will miss it. The `id` tiebreaker prevents duplicates (two docs with same timestamp) but does NOT prevent skips when timestamps change.
>
> **Fix 2 — Snapshot on page 1 (accurate for small datasets):** On the first page request, materialize the full sorted ID list into a `pagination_snapshots` table (`snapshot_id, position, document_id`). Subsequent pages read from the snapshot. Accurate but expensive: `50M docs × 8 bytes = 400 MB per user snapshot`. Only viable for < 10K result sets (filtered searches).
>
> **Fix 3 — Accept inconsistency and document it (pragmatic):** For UI browsing, gaps during modification are acceptable — users are scrolling, not auditing. Document the behavior: "Sort-by-modified list may not show recently-modified documents until you refresh." Many production systems (Gmail, Slack) use this approach.
>
> **In an interview:** "I'd use Fix 1 for production — immutable `id` tiebreaker prevents duplicates but not skips. I'd document that sort-by-modified during active browsing may have gaps. For correctness-critical scenarios (compliance exports), I'd switch to date-bounded queries instead of sort-by-modified."

---

### New Cross-Concept Probe (Tier 3 — added Jul 4, 2026)

**Q: "A client calls your pagination API with limit=100 from page 1 through page 1,000 (fetching all 100K records). Your cursor-based design prevents OFFSET disasters, but is there anything else that can degrade at this depth?"**
> Cursor pagination is O(log N + limit) per page — no full scans. So 1,000 pages of 100 records is 1,000 × O(log 100M + 100) = still fast per page. But there are two failure modes at deep pagination depth:
>
> **1. Index range saturation:** At page 1,000, cursor values are very old (timestamps from months ago). The composite index `(created_at DESC, id DESC)` is still efficient, but if most records in the old range have been soft-deleted (and you filter `WHERE deleted_at IS NULL`), the DB may scan many deleted rows to find 100 active ones. Fix: add `deleted_at IS NULL` to a partial index:
> ```sql
> CREATE INDEX idx_active_created ON documents (created_at DESC, id DESC)
> WHERE deleted_at IS NULL;
> ```
> Now only active documents are in the index — no wasted scanning of deleted records.
>
> **2. Memory and connection pressure:** 1,000 API calls in sequence each hold a brief DB connection. At high concurrency (100 parallel clients each deep-paginating), 100 × 1,000 = 100,000 DB queries in rapid succession. Connection pool exhaustion. Fix: enforce a max-page-depth limit for live API calls (e.g., 500 pages); for full exports, redirect to an async bulk export endpoint that streams results to S3 and returns a presigned download URL.
>
> **In an interview:** "Cursor pagination is safe from deep-offset scans, but I'd add a partial index on active records and enforce a page-depth limit for live API calls. Full dataset exports should use async bulk export (stream to S3) rather than sequential pagination calls — it's faster and doesn't exhaust connection pools."

---

## Section 13 — 🐞 Common Mistakes on This Question

**Note:** Reading these mistakes BEFORE the interview prevents you from making them under stress.

---

- **Mistake 1:** Using offset pagination without considering scale → **Why wrong:** At 1M records, OFFSET 500000 requires scanning 500K rows. P99 > 5 seconds; timeouts. **Say instead:** "Cursor pagination is O(1) lookups; offset pagination is O(N). At scale, cursor is required."

- **Mistake 2:** Not including has_more flag in response → **Why wrong:** Client doesn't know if pagination is done. They'll try fetching the next page with empty results, causing confusion. **Say instead:** "Response includes has_more flag; client knows definitively when they've reached the end."

- **Mistake 3:** Ignoring index design → **Why wrong:** Without a compound index on (created_at, id), cursor queries still full-scan the table. **Say instead:** "Composite index on (created_at DESC, id DESC) enables O(50) row scans per request, regardless of table size."

- **Mistake 4:** Assuming cursors are tamper-proof without signature → **Why wrong:** Client can forge cursors (e.g., `{last_id: 999999}`) and jump to arbitrary positions (DoS/abuse). **Say instead:** "Cursors are HMAC-signed to prevent tampering. Server validates signature before trusting the cursor."

- **Mistake 5:** Not handling cursor expiry or deleted items → **Why wrong:** Old cursors may point to deleted items; pagination breaks silently. **Say instead:** "Cursor design is resilient to item deletion (timestamps remain valid). If cursor is too old (> 1 hour), return 410 Gone; client restarts pagination."

---

## Section 14 — 🧭 DocuSign Dimensions Checklist

| Dimension | Relevant? | How your design addresses it |
|---|---|---|
| Testability | ✅ | Cursor encoding/decoding is a pure function: test with known (created_at, id) pairs, verify HMAC signature, verify round-trip decode. Mock DB: test WHERE (created_at < ? OR (created_at = ? AND id < ?)) query with in-memory data — no Postgres needed. Edge cases: empty page, last page (has_more = false), deleted items mid-page. |
| Usability | ✅ | Simple API: GET /envelopes?cursor={opaque_token}&limit=50. Response: {items, next_cursor, has_more}. Clients never parse cursor internals (opaque). For DocuSign: an enterprise admin paging through 100K+ envelopes sees a stable, consistent list — no duplicates, no skipped items, regardless of concurrent envelope creates during pagination. |
| Extensibility | ✅ | Cursor design is schema-agnostic — adding new item fields (recipient_count, expiry_date) doesn't break pagination (cursor only encodes sort key: created_at + id). Adding a new sort strategy (sort by updated_at) = new compound index + cursor field, no API contract change. |
| SOLID principles | ✅ | Single Responsibility: CursorCodec (encode/decode/sign), PaginationQueryBuilder (WHERE clause), PaginationService (orchestrate). Open/Closed: adding sort strategies = new CursorStrategy implementation, no changes to existing classes. Dependency Inversion: PaginationService depends on ItemRepository interface — testable without live DB. |
| Scalability | ✅ | Compound index on (created_at DESC, id DESC) keeps every page fetch at O(50) rows scanned regardless of table size — at 1M, 10M, or 100M envelopes, P99 < 200ms is maintained (Section 4: 100K requests/hour peak = 28 req/sec, well within Postgres capacity). For DocuSign: eDiscovery export of 500K envelopes completes in consistent batches without degrading as the export progresses deeper into history. |
| Data model correctness | ✅ | Compound index on (created_at DESC, id DESC) is stable because created_at is immutable (envelopes never change their creation time). Soft deletes preserve data integrity — deleted envelopes are filtered (WHERE status = 'ACTIVE') at the query layer, not physically removed, so cursor positions remain valid across deletes. |
| Performance | ✅ | P99 < 200ms for any page fetch via compound index. HMAC signature adds 32 bytes per cursor (negligible). Signed cursor prevents DoS attacks where clients craft deep-page cursors to force full table scans — unsigned cursors at page 500K (OFFSET 500000) would time out at > 5 seconds; signed cursors keep it at O(50) index rows regardless of cursor position. |

---

## Section 15 — 🧾 TL;DR Answer Summary (Review Morning-of-Interview)

**If you had 60 seconds to summarize the entire answer, say this:**

> "I'd design pagination with cursor-based API (opaque, signed base64 cursors) supporting sequential forward access. Cursor encodes the last item's created_at and id (composite sort key). Query: WHERE (created_at < ? OR (created_at = ? AND id < ?)) LIMIT 51. Compound index on (created_at DESC, id DESC) ensures O(50) row scans per request, supporting 1M+ records at < 200ms latency. Response includes next_cursor, has_more flag, and items array. Eventual consistency is acceptable (small gaps if records deleted mid-pagination). Cursor is tamper-proof (HMAC-signed) preventing client attacks. The core insight: offset pagination is O(N) at scale; cursor pagination is O(1). Index design is critical — without it, cursors are still slow. For DocuSign's enterprise context, pagination must be stable (created_at immutable), fast (indexed), and correct (no duplicates)."

**Why read this before your interview?**
The TL;DR fixes the core idea in your head. Under stress, you'll default to this mental model (cursor vs offset, index design, HMAC signature). When the interviewer asks unexpected questions about backdating or key rotation, you'll reason from these principles.

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Jul 5, 2026 | **Section 6 restructured: single final-state diagram → 3-stage progressive HLD.** Stage 1 (Offset/LIMIT): O(N) scan; row-shift duplicates on concurrent inserts → SLA breach at 1M+ records. Stage 2 (Timestamp cursor, no compound index): eliminates O(N) scan but timestamp ties cause duplicates/skips at page edges; plain base64 forgeable. Stage 3 (Production): composite cursor `(created_at, id)` + compound index `(created_at DESC, id DESC)` = O(50) rows per page regardless of table size; HMAC-signed base64 prevents forgery; eventual consistency explicitly documented in API contract. Three inline decision tables: (1) pagination strategy — LIMIT/OFFSET ❌ / timestamp cursor ⚠️ / composite cursor ✅; (2) cursor security — plain base64 ❌ / DB row ID cursor ⚠️ / HMAC-signed base64 ✅; (3) consistency model — no handling ❌ / strict MVCC snapshot ⚠️ / eventual consistency ✅. Data flow updated to Stage 3 precision (HMAC validation step, composite ORDER BY). |
| Jul 4, 2026 | **3 new Q&As added to Section 12.** (1) **Long-running compliance pagination** — date-bounded snapshot pagination for compliance exports (stable, no cursor expiry); persistent cursors in DB (`cursor_checkpoints` table, 30-day expiry) for resumable large exports; recommendation by use case. (2) **Mutable sort key problem** — sorting by `last_modified_at` breaks pagination when documents are modified mid-session; immutable `id` tiebreaker prevents duplicates but not skips; snapshot pagination for small datasets; document inconsistency acceptable for UI browsing. (3) **Deep pagination degradation at depth 1,000+** — partial index on active records (`WHERE deleted_at IS NULL`) prevents index range saturation; page-depth limit (e.g. max page 500) for live API; async bulk export to S3 (stream + presigned URL) for full dataset access. |
| June 23, 2026 | **File created.** Type B — Product Architecture. Based on: 1Point3Acres thread ("Tech Phone Screen: Pagination API and Data Model Design"), DocuSign engineering blog on API pagination. Concept notes: `11-api-design.md` (pagination strategies), `12-data-modeling.md` (indexing). Fully integrated with DELIVERY-RECIPE framework: 🧠 preamble + 60-minute time budget, 💾 Memory Anchors (6 core + 3 bonus), explicit timing callouts in sections 2/4/6/7/10/11/12, "say this out loud" dialogue framing, interview psychology context (working memory constraints, stress failure modes). Deep dives: cursor vs offset trade-offs, SQL strategy for 1M+ records, index design (compound key necessity), handling concurrent mutations. Section 5 variation table covers 6 axes (random vs sequential access, backward pagination, persistent cursors, dynamic ordering, cursor expiry, sticky cursors). Section 8 (API) and Section 9 (Data Model) are primary deliverables (Type B emphasis). Index design is critical (Dive 3): O(N) without index vs O(50) with compound index. Pre-write checklist enforced: Identity Card, clarifying questions with WHY, API endpoints with cursor encoding details, SQL queries showing keyset pagination, composite index justification, 3 deep dives on riskiest components, trade-offs with failure modes, 3-tier probes (surface/deep/cross-concept). Common Mistakes (5 entries) emphasize offset scalability, index necessity, HMAC signing, cursor resilience, immutable sort keys. Result: Interview delivery-ready, zero refinement needed. |
| Jul 5, 2026 | **Section 10 business impact + Section 14 DocuSign dimensions pass.** Section 10: added **Business impact:** to all 3 trade-offs — enterprise admin eDiscovery export timing out at 5-second threshold due to O(N) offset scan (pagination strategy cost), quarter-end signing rush causing write stalls that blank pages mid-navigation (index contention), HMAC-less cursor enabling cross-tenant envelope ID enumeration and database I/O DoS (security). Section 14: rewrote all 7 dimension cells — eDiscovery export for legal hold discovery (Correctness), HMAC-signed composite cursor preventing cross-tenant envelope ID enumeration (Security), composite cursor EXPLAIN plan for performance RCA with explicit index name (Observability). |
