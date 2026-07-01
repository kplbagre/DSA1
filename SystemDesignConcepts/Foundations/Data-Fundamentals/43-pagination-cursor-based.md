# 43 — Pagination: Offset vs Cursor-Based

## 📖 What is Pagination?

**Full form:** Pagination — the technique of splitting a large result set into discrete pages (chunks) returned one at a time, so that clients retrieve results incrementally instead of receiving the entire dataset in a single response.

**Simple analogy:** A library catalog printed across 500 pages. You can either flip directly to page 200 (offset pagination — fast to navigate but you might skip a page if a card was removed between visits), or use a bookmark (cursor pagination — you always resume from exactly where you left off, regardless of what else changed).

**Core principle:** Instead of returning all N rows at once, the server returns a batch of rows and a pointer to where the next batch starts. Offset pagination uses a numerical row skip count; cursor pagination uses a stable marker (an encoded key from the last row seen) that the database can use to index into the data without scanning prior rows.

**Why it matters in system design:** Feeds, search results, and API list endpoints at scale (millions of rows) cannot afford full table scans. Cursor-based pagination eliminates the O(N) scan cost of offset pagination and produces stable results under concurrent writes, making it the default choice for any production API serving live data.

---

## 📖 Terminology Table

| Term | Plain-English Meaning | Example |
|------|----------------------|---------|
| **Offset Pagination** | skip N rows then take M: `LIMIT 10 OFFSET 50000`; DB scans 50,010 rows to discard 50,000 | page 5001 of 10: `OFFSET 50000 LIMIT 10` → DB reads 50,010 rows, returns 10 |
| **Cursor Pagination (Keyset)** | use last-seen row's key as a WHERE clause: `WHERE id > 8374921 LIMIT 10`; index seek, O(log N) | `WHERE created_at < '2024-01-15T10:30:00' ORDER BY created_at DESC LIMIT 10` |
| **Cursor Token** | opaque, client-facing encoding of the keyset value (usually base64); hides DB internals from clients | `next_cursor: "eyJpZCI6ODM3NDkyMX0="` decodes to `{id: 8374921}` |
| **Keyset Pagination** | same as cursor pagination; the "key" is one or more indexed columns that define sort order | keyset: `(created_at, id)` — composite cursor for stable ordering when timestamps collide |
| **Stable Results** | cursor pagination returns consistent pages even if rows are inserted/deleted between requests | row deleted at offset 4999: offset page 5001 skips a row; cursor page picks up at exact key |
| **Drift / Skip (offset problem)** | concurrent inserts/deletes shift row positions; offset pagination shows duplicates or skips items | user A deletes row 5000; user B fetching page 501 at OFFSET 5000 sees row 5001 twice |
| **Opaque Cursor** | base64-encoded cursor the client passes back; client cannot parse or manipulate it | `eyJpZCI6ODM3NDkyMX0=` — client treats as opaque string; server decodes internally |
| **Composite Cursor** | cursor encoding multiple sort columns when primary key alone doesn't guarantee stable order | `{created_at: "2024-01-15T10:30", id: 8374}` — handles ties in created_at |

---

## 🎯 Why This Matters

- **Problem solved:** Offset pagination causes DB full-scans at high offsets (LIMIT 10 OFFSET 1,000,000 means the database reads 1,000,010 rows to discard 1,000,000 of them) and produces inconsistent pages if rows are inserted or deleted between requests.
- **Interview signal:** Comes up in every feed/timeline design (Twitter, Instagram, Slack), any API design round, and any question about DB query optimization at scale.
- **Senior expectation:** You must distinguish offset from cursor, explain the O(log N) vs O(N) scan cost difference, know how to encode cursors safely (base64, opaque), and explain keyset pagination for composite sort keys.

---

## 🧠 The Mental Model

Imagine a very long queue at a theme park — thousands of people waiting for a ride. You are a photographer hired to photograph groups of ten in sequence.

**Offset pagination (the naive approach):** You start at position 1. For each group, you say "skip the first 1,000 people and take a photo of people 1,001–1,010." But to do that, you physically walk past all 1,000 people before you start shooting. If someone leaves the queue mid-walk (a row is deleted), the people behind them shift forward by one. You now accidentally photograph person 1,001 again (duplicate) or skip person 1,003 entirely. And walking further back each time takes longer and longer.

**Cursor pagination (the library bookmark approach):** Instead, you photograph the last person in each group and write down their wristband number. Next time, you show the entrance staff that wristband number: "start from the person after this one." The staff uses the wristband index to jump directly to that spot — no walking required. If ten people left the queue since last time, your bookmark still lands you exactly at the right person. The cost is O(log N) via index lookup, not O(N) via a walk.

**The key insight is:** A cursor is a stable, indexed marker in sorted order — the database can seek to it in O(log N) via a B-tree index, while an offset forces the database to count rows from the beginning on every request.

---

## 🎨 Visual — System Topology & Component Flow

```
FULL SYSTEM TOPOLOGY:
                                        Data Tier
Client Tier    Service Tier             ┌──────────────────────────────────┐
┌─────────┐   ┌─────────────────────┐  │                                  │
│ Client  │──▶│  API / Controller   │  │  ┌───────────────────────────┐   │
│         │   │                     │  │  │ PostgreSQL / MySQL         │   │
│ sends   │   │  reads cursor from  │──┼─▶│                           │   │
│ cursor= │   │  query param,       │  │  │ Offset:                   │   │
│ "abc1"  │   │  builds WHERE clause│  │  │  LIMIT 10 OFFSET 50000    │   │
└─────────┘   │                     │  │  │  (scans 50,010 rows)      │   │
              │  returns next_cursor│◀─┼──│                           │   │
              │  in response body   │  │  │ Cursor:                   │   │
              └─────────────────────┘  │  │  WHERE id > 8374921       │   │
                                        │  │  ORDER BY id LIMIT 10     │   │
                                        │  │  (seeks via index, O(logN))│  │
                                        │  └───────────────────────────┘   │
                                        └──────────────────────────────────┘

COMPONENT DETAIL — Offset Problem vs Cursor Stability:

TIME →        Page 1 fetched          Page 2 fetched
              (rows 1-10)             (OFFSET 10, rows 11-20)

Offset risk:
Row 1  ──────────────────────────────────────── [row 1]
Row 2  ──────────────────────────────────────── [row 2]
...
Row 8  ──────────────────────────────────────── [row 8]
Row 9  ─ DELETED between requests ─────────── ← GONE
Row 10 ──────────────────────────────────────── [row 10] ← now at pos 9
Row 11 ──────────────────────────────────────── [row 11] ← now at pos 10

Result: OFFSET 10 now lands on old row 12. Row 11 is SKIPPED entirely.

Cursor stability:
After page 1: cursor = encode(id=10, created_at=T1)
Page 2 query: WHERE (created_at, id) < (T1, 10) ORDER BY created_at DESC
              → Row 9 deleted? Doesn't matter. Seek starts from cursor anchor.
              → Always returns the correct "next" rows. No skips, no duplicates.

KEY INVARIANT:
  Cursor points to the LAST ROW SEEN, not a position count.
  The WHERE clause turns a position into an indexed key seek (O(log N)).
  Deletes and inserts between pages never shift the cursor anchor.
```

---

## ⚙️ How It Actually Works

**Steps — Offset Pagination (simple case):**
1. **Client sends page number** — API converts page=5, size=10 to LIMIT 10 OFFSET 40.
2. **Database scans from row 1** — even though only rows 41–50 are needed, the DB engine counts through 40 rows first.
3. **Results are position-dependent** — if row 20 is deleted between page 1 and page 2 fetches, all rows shift by one and the client sees a duplicate or a skip.

**Steps — Cursor-Based Pagination:**
1. **Client sends cursor token** — first request has no cursor (returns first page). Subsequent requests send the cursor from the prior response.
2. **Server decodes the cursor** — base64-decode the opaque token to extract the last-seen sort key (e.g., `{id: 8374921, created_at: "2026-06-24T10:00:00Z"}`).
3. **Server builds an indexed WHERE clause** — `WHERE created_at < :lastCreatedAt OR (created_at = :lastCreatedAt AND id < :lastId)` with `ORDER BY created_at DESC, id DESC LIMIT 10`.
4. **Database uses the index** — the B-tree index on `(created_at, id)` allows a direct seek to the cursor position in O(log N). No prior rows are scanned.
5. **Server encodes the next cursor** — take the last row returned, base64-encode its sort key fields, return it as `next_cursor` in the response body.
6. **Client checks for exhaustion** — if the response contains fewer rows than the page size (or `next_cursor` is null), there are no more pages.

### What is base64 encoding here, and why does it fit?

**Plain English:** Base64 converts binary data (or a JSON string like `{"id":8374921,"ts":"2026-06-24T10:00:00Z"}`) into a URL-safe ASCII string. It makes the cursor opaque — clients cannot see or tamper with the raw sort key values. In an interview, if asked: "We base64-encode the cursor so the internal sort key schema is hidden from clients and can change without breaking the API contract."

```java
import java.util.Base64;
import java.util.List;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

// Cursor structure (internal — never exposed raw)
public record PageCursor(long lastId, String lastCreatedAt) {

    public static PageCursor decode(String token) {
        byte[] bytes = Base64.getUrlDecoder().decode(token);
        String json = new String(bytes);
        // json = {"id":8374921,"ts":"2026-06-24T10:00:00Z"}
        // parse manually or with Jackson; simplified here:
        String[] parts = json.replaceAll("[{}\"]", "").split(",");
        long id = Long.parseLong(parts[0].split(":")[1].trim());
        String ts = parts[1].split(":", 2)[1].trim();
        return new PageCursor(id, ts);
    }

    public String encode() {
        String json = "{\"id\":" + lastId + ",\"ts\":\"" + lastCreatedAt + "\"}";
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes());
    }
}

// Post entity assumed to have fields: id (Long), createdAt (String/Instant), content
public class PostPaginationService {

    private final EntityManager em;

    public PostPaginationService(EntityManager em) {
        this.em = em;
    }

    // --- OFFSET approach (simple but O(N) scan at high pages) ---
    public List<Post> getPageOffset(int page, int size) {
        int offset = page * size;
        // Step 1: LIMIT + OFFSET — DB must scan `offset` rows first
        return em.createQuery(
                "SELECT p FROM Post p ORDER BY p.createdAt DESC, p.id DESC",
                Post.class)
            .setFirstResult(offset)
            .setMaxResults(size)
            .getResultList();
    }

    // --- CURSOR approach (O(log N) via index seek) ---
    public CursorPage<Post> getPageCursor(String encodedCursor, int size) {
        List<Post> rows;

        if (encodedCursor == null || encodedCursor.isBlank()) {
            // Step 2a: First page — no cursor, return first N rows
            rows = em.createQuery(
                    "SELECT p FROM Post p ORDER BY p.createdAt DESC, p.id DESC",
                    Post.class)
                .setMaxResults(size)
                .getResultList();
        } else {
            // Step 2b: Decode cursor to extract last-seen sort key
            PageCursor cursor = PageCursor.decode(encodedCursor);

            // Step 3: Build keyset WHERE clause using composite key (created_at, id)
            // This uses the (createdAt, id) index — O(log N) seek, not a full scan
            TypedQuery<Post> query = em.createQuery(
                    "SELECT p FROM Post p " +
                    "WHERE p.createdAt < :lastTs " +
                    "   OR (p.createdAt = :lastTs AND p.id < :lastId) " +
                    "ORDER BY p.createdAt DESC, p.id DESC",
                    Post.class);
            query.setParameter("lastTs", cursor.lastCreatedAt());
            query.setParameter("lastId", cursor.lastId());
            query.setMaxResults(size);
            rows = query.getResultList();
        }

        // Step 4: Encode next cursor from last row returned
        String nextCursor = null;
        if (rows.size() == size) {
            Post last = rows.get(rows.size() - 1);
            nextCursor = new PageCursor(last.getId(), last.getCreatedAt()).encode();
        }

        // Step 5: Return page + next cursor (null = no more pages)
        return new CursorPage<>(rows, nextCursor);
    }
}

// Response wrapper
public record CursorPage<T>(List<T> items, String nextCursor) {}
```

### What is Keyset Pagination, and why does it fit here?

**Plain English:** Keyset pagination is cursor pagination where the cursor is made up of the actual column values from the sort key (e.g., `created_at + id`) rather than an opaque row ID. Using a composite key `(created_at, id)` guarantees uniqueness even when two rows share the same timestamp. In an interview, if asked: "Keyset pagination is cursor pagination using the sort columns directly as the seek key — it's safe with composite sort orders where a single column like timestamp isn't unique."

```java
// Redis sorted set — for feed pagination (e.g., Twitter-style timeline)
// Score = Unix timestamp (millis). Member = postId.
// ZREVRANGEBYSCORE key +inf (lastScore-1) LIMIT 0 10
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.ZRangeParams;
import java.util.Set;

public class FeedPaginationRedis {

    private final Jedis jedis;
    private static final String FEED_KEY = "user:feed:1001";

    public FeedPaginationRedis(Jedis jedis) {
        this.jedis = jedis;
    }

    // First page: max score = now, no cursor
    public Set<String> firstPage(int size) {
        // ZREVRANGEBYSCORE key +inf -inf LIMIT 0 size
        return jedis.zrevrangeByScore(
            FEED_KEY,
            "+inf",
            "-inf",
            0,
            size
        );
    }

    // Subsequent pages: cursor = score of last item seen
    public Set<String> nextPage(double lastScore, int size) {
        // Exclude lastScore using "(" prefix (exclusive bound)
        String maxScore = "(" + lastScore;
        return jedis.zrevrangeByScore(
            FEED_KEY,
            maxScore,
            "-inf",
            0,
            size
        );
    }
}
```

### What is a Redis Sorted Set, and why does it fit here?

**Plain English:** A Redis sorted set stores members (e.g., post IDs) each associated with a numeric score (e.g., Unix timestamp). Members are always kept in score order, and range queries by score are O(log N). It is the natural data structure for chronologically ordered feeds. In an interview, if asked: "A Redis sorted set stores post IDs scored by timestamp — ZREVRANGEBYSCORE with the last-seen score as the exclusive upper bound is an O(log N) cursor-based page fetch on an in-memory index."

---

## 🏢 Real World — Where Companies Use This

- **Twitter / X (timeline):** The home timeline is cursor-based — `max_id` (exclusive) or `since_id` bounds every timeline API call. Using an offset on a timeline with millions of real-time inserts would produce duplicate and missing tweets between page fetches.
- **Instagram (feed API):** Uses cursor tokens in feed pagination. Their internal feed is a Redis sorted set keyed by user ID, scored by post timestamp. ZREVRANGEBYSCORE with the last-seen score drives each page fetch.
- **GitHub API (List repositories / commits):** Uses RFC 5988 `Link: <url>; rel="next"` headers where the URL contains a cursor (an opaque page token). Developers cannot manually jump to page 50 — they follow the link header chain, which is the correct pattern for large, live datasets.
- **Stripe API (List charges, customers, events):** Explicit cursor-based pagination: `starting_after=ch_abc123` and `ending_before=ch_xyz789` let clients page forward and backward. Stripe never uses numeric offsets — their API design guide explicitly rejects them for live financial data.
- **Google APIs (Search, PubSub, BigQuery):** Server-side page tokens (opaque strings). The server maps the token to the next query internally. Clients cannot decode or construct tokens — they pass the token from the previous response verbatim.
- **Elasticsearch (large result sets):** Uses `search_after` (keyset pagination) for deep pagination instead of `from/size` (offset). `from=10000` triggers a full scan of 10,000+ documents; `search_after` seeks directly into the sorted index. Elasticsearch documentation explicitly warns against `from/size` beyond 10,000 hits.

---

## 🧭 When to Use vs When NOT to Use

| Use cursor-based pagination when | Use offset pagination instead when |
|---|---|
| Dataset is large (>10K rows) and growing | Small, static datasets where full scans are cheap |
| Data changes frequently (inserts/deletes between pages) | Admin UIs where users need to jump to page 50 |
| Building public API endpoints (feeds, timelines, event lists) | Reporting dashboards with stable, batch-loaded data |
| Sort order is by a unique or composite key (id, timestamp+id) | Users need total page count ("Page 3 of 47") |
| Mobile clients that page sequentially (infinite scroll) | Simple prototype or internal tool with small data |

**The common mistake:** Engineers use `OFFSET` for the first version ("it's simpler") and never migrate when the table grows. At row 1,000,000, a single page request at OFFSET 999,990 scans and discards a million rows — invisible in staging but catastrophic in production.

---

## ⚠️ Trade-offs

| | |
|---|---|
| **You gain** | O(log N) indexed seeks instead of O(N) scans; stable pages under concurrent writes; scales to billions of rows without degradation; safe for public APIs where internal schema must be hidden (opaque base64 cursor). |
| **You lose** | No random access ("jump to page 50"); total page count is unavailable (client cannot know how many pages exist); cursor tokens are stateful and tied to the sort order — changing the sort column invalidates all outstanding cursors; bidirectional pagination requires two cursor types (next + previous) and careful implementation. |
| **Failure mode** | Using cursor pagination with a non-unique sort key (e.g., `created_at` alone when many rows share the same timestamp) causes rows to be silently skipped or duplicated at page boundaries. Always use a composite key `(created_at, id)` as the cursor to guarantee uniqueness. |

---

## 🔬 Interview Q&As

### Q: "Why is OFFSET pagination slow at large page numbers?"

> When you write `LIMIT 10 OFFSET 1000000`, the database must read and discard 1,000,000 rows before returning the 10 you asked for. Even with an index, the engine counts through 1,000,000 index entries. This is O(N) in the offset value. Cursor pagination replaces the offset with a WHERE clause that seeks directly into the index, which is O(log N) via a B-tree lookup.

### Q: "What is a cursor in cursor-based pagination?"

> A cursor is an opaque, encoded pointer to the last row returned on the previous page. Typically it encodes the sort key values of that last row — for example, `{id: 8374921, created_at: "2026-06-24T10:00:00Z"}` base64-encoded. The server decodes it on the next request and uses it to build a WHERE clause that seeks directly to the next row in sorted order.

### Q: "When would you still use offset pagination?"

> Offset pagination is appropriate when: (1) the dataset is small and stable enough that full scans are cheap, (2) the UI requires jumping to an arbitrary page ("go to page 47 of 200"), or (3) you need to show total page count. Examples: admin reporting tables, batch exports with predictable data, internal analytics dashboards.

### Q: "How does Instagram paginate its feed at scale?"

> Instagram stores user feed items in a Redis sorted set keyed by user ID, with each post scored by its publish timestamp. Pagination is a ZREVRANGEBYSCORE call using the last-seen score as the exclusive upper bound. The score (timestamp in milliseconds) acts as the cursor. This gives O(log N) seeks on an in-memory sorted structure, which is critical for sub-10ms feed response times.

### Q: "Why must you use a composite key (created_at, id) instead of just (created_at) as the cursor?" (Tier 2)

> `created_at` alone is not unique — many rows can share the same timestamp (batch inserts, millisecond collisions). If the cursor lands on a timestamp where 50 rows exist, the WHERE clause `created_at < :ts` would skip all rows AT that timestamp boundary, silently losing data. Adding `id` as a tiebreaker makes the key unique: `WHERE (created_at, id) < (:ts, :id)` using lexicographic comparison returns exactly the rows after the cursor without skipping any.

### Q: "What happens if a cursor is sent after the underlying sort order changes (e.g., the API switches from chronological to relevance sort)?" (Tier 2)

> Cursors encode the sort key values of a specific sort order. If the sort order changes, the cursor's encoded values become meaningless in the new sort context — the WHERE clause would seek to a position that does not correspond to the intended page boundary. The safe approach is to version cursors (include a sort-mode version in the encoded token) and reject outdated cursors with a 422 response, forcing clients to restart pagination with the new sort order. Google's API design guide handles this via server-side page tokens that the server maps to the full query state, making sort-order changes transparent to the client.

### Q: "How do you implement bidirectional cursor pagination (both next and previous pages)?"

> You need two cursors: a `next_cursor` (encodes the last row on the current page for forward navigation) and a `prev_cursor` (encodes the first row on the current page for backward navigation). The `prev_cursor` query reverses the inequality: `WHERE (created_at, id) > (:ts, :id) ORDER BY created_at ASC, id ASC LIMIT 10` then re-reverses the result list. The complexity is manageable but the symmetric WHERE clause logic must be tested carefully for boundary rows.

### Q: "Stripe uses `starting_after` and `ending_before` as cursor parameters. How is this different from a base64 cursor?"

> Stripe's approach exposes the resource ID itself as the cursor (e.g., `starting_after=ch_abc123`), whereas a base64 cursor hides the sort key behind encoding. Both are cursor-based in concept. Stripe's design is more human-readable and debuggable but leaks the fact that IDs are sortable (a minor schema coupling). A base64-encoded composite cursor is more opaque and can embed any sort key fields without exposing the internal schema — preferred when the sort key is not the public-facing resource ID.

---

## 🧾 TL;DR — One Interviewer-Ready Line

> "Offset pagination is O(N) and produces inconsistent pages under writes; cursor pagination replaces the row count with an indexed key seek that's O(log N) and stable — the tradeoff is you lose random-access page jumping."

---

## 🔗 Related Concepts

- `../07-cdc-outbox.md` — CDC events are paginated using cursor-based patterns when replaying event logs
- `../../Core-Architecture/Resilience-and-Fault-Tolerance/10-backpressure.md` — Backpressure + pagination together control data flow rates from DB to consumers
- `../../Core-Architecture/Data-and-Storage/03-caching.md` — Redis sorted sets used in feed pagination are a caching layer around the primary DB
- `../../Core-Architecture/Resilience-and-Fault-Tolerance/20-circuit-breaker-resilience.md` — Pagination endpoints need circuit breakers if upstream DB is slow at deep pages

---

## 📚 Further Reading (Optional — after the note)

| Resource | What it adds | Time |
|---|---|---|
| **Pagination at Instagram** — Instagram Engineering Blog | Deep dive into how Instagram migrated from offset to cursor-based pagination on their feed Redis sorted sets at billions-of-row scale | ~8 min read |
| **Stripe API pagination design** — stripe.com/docs/api/pagination | Authoritative example of cursor-based API design from a production financial API; shows `starting_after`/`ending_before` pattern with real JSON examples | ~5 min read |
| **Elasticsearch search_after** — elastic.co/guide/search-after | Shows why `from/size` breaks at depth and exactly how `search_after` with a sort value array replaces it — useful for search system designs | ~6 min read |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 25, 2026 | Note created. Covers offset vs cursor vs keyset pagination, encoding strategy, Redis sorted set feed pagination, Spring Data JPA code for both approaches, bidirectional cursors. Six real-world examples. Eight Q&As including two Tier-2 probe questions. |
