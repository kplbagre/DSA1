# REST API Design — Mental Model + Reference

> **Why this exists:** Confluent treats API design as a *separate evaluation axis*, not a footnote. "If you do any mistake with verbs/response codes/headers, they highlight it as if the world has ended."
>
> **How to use:** Read Section 0 and 1 to build the mental model. The tables are quick-reference once you understand the why. Revisit the TL;DR the morning of the interview.

---

## 🧠 Section 0 — The Mental Model: What REST Actually Is

Before memorizing any table, understand what REST (Representational State Transfer — a style of building web APIs where everything is a "resource" the client can manipulate using a fixed set of operations; invented by Roy Fielding in 2000) actually says to you as an API designer:

> **Everything on the server is a resource (a noun). The HTTP protocol gives you a small, fixed set of verbs to operate on those nouns. Your entire job is to name the nouns well and map operations to the right verb.**

That's it. REST is not a protocol, not a standard — it's a *style* of API design. The five operations are often called CRUD (Create, Read, Update, Delete — the four fundamental operations any persistent data store needs to support):

```
CRUD          HTTP Verb      What it means
────────────────────────────────────────────
Create      → POST          "Make a new thing"
Read        → GET           "Give me a thing"
Update Full → PUT           "Replace the whole thing"
Update Part → PATCH         "Change one field on the thing"
Delete      → DELETE        "Remove the thing"
```

> **The mental shortcut:** If you can describe your operation as "give me X," "make a new X," "change X," or "remove X" — you have the right verb. If you can't describe it this way, you are probably inventing a verb action that should instead be modelled as a state change on a resource.

**Why does this matter in an interview?** Interviewers are testing whether you instinctively think in *resources*, not *actions*. The wrong signal is an API like `POST /sendEmail` or `GET /deleteExpiredEmails` — those are procedure calls, not REST. The right signal is `POST /emails` (create a resource), `DELETE /emails/{id}` (remove a resource), and `PATCH /emails/{id}/status` (change one field on the resource).

---

## 🧠 Section 1 — HTTP Verbs: The Deep Explanation

### The Table (reference)

| Verb | Meaning | Idempotent? | Safe? | Request Body? | Example |
|---|---|---|---|---|---|
| **GET** | Read / retrieve a resource | ✅ Yes | ✅ Yes | ❌ No | `GET /v1/orders/{id}` |
| **POST** | Create a new resource | ❌ No | ❌ No | ✅ Yes | `POST /v1/orders` |
| **PUT** | Full replace of a resource | ✅ Yes | ❌ No | ✅ Yes | `PUT /v1/orders/{id}` |
| **PATCH** | Partial update of a resource | ❌ No* | ❌ No | ✅ Yes | `PATCH /v1/orders/{id}/status` |
| **DELETE** | Remove a resource | ✅ Yes | ❌ No | ❌ No | `DELETE /v1/emails/{id}` |

### 🧠 What "Safe" and "Idempotent" Actually Mean

These two properties are the hardest to grok. Here's the mental model for each.

---

**Safe** = "Calling this never changes anything on the server."

Analogy: reading the menu at a restaurant is safe. Looking at it three times doesn't change your bill. Only *ordering* changes something.

Only GET (and HEAD, OPTIONS) are safe. Every other verb changes server state.

> **Why you should care in an interview:** If your API design ever has a GET endpoint that modifies data, that is an instant red flag. `GET /emails?delete=true` or `GET /triggerReport` are bugs, not features. Caches, proxies, and browsers all assume GET is safe — they will pre-fetch, retry, and cache GET requests automatically. If GET mutates something, you get double mutations.

---

**Idempotent** (derived from Latin *idem* "same" + *potent* "power") = "Calling this N times produces exactly the same result as calling it once."

Analogy: pressing the elevator button. Already called: pressing again doesn't call two elevators. DELETE is like flipping a light switch OFF — already off, flip again, still off.

POST is NOT idempotent: "Place an order" twice → two orders placed. This is why POST needs idempotency keys (Section 6).

PUT IS idempotent: "Replace the order with this object" twice → same object exists (the second PUT is a no-op because it replaced with the same thing).

PATCH is *technically* not idempotent because `"add 1 to quantity"` applied twice is different from applied once. But if your PATCH semantics are `"set quantity to 5"` (absolute, not relative), then it behaves idempotently in practice.

### 🎨 Visual — Safe vs Idempotent Grid

```
                  IDEMPOTENT?
                  Yes              No
        ┌─────────────────────┬──────────────────────┐
Safe?   │                     │                      │
  Yes   │       GET           │    (nothing here —   │
        │                     │    safe things are   │
        │                     │    always idempotent)│
        ├─────────────────────┼──────────────────────┤
  No    │  PUT, DELETE        │    POST, PATCH*      │
        │  (change things,    │    (change things,   │
        │   but same result   │    different result  │
        │   if called twice)  │    if called twice)  │
        └─────────────────────┴──────────────────────┘

KEY INVARIANT:
  Safe ⊂ Idempotent — everything safe is also idempotent.
  Not every idempotent operation is safe (PUT, DELETE change state
  but repeating them doesn't add more change).
  POST is neither — repeated calls create repeated side effects.
```

---

### ⚠️ Common Verb Mistakes Confluent Will Catch

| ❌ Wrong | ✅ Right | Why |
|---|---|---|
| `POST /v1/orders/{id}` to update | `PATCH /v1/orders/{id}/status` | POST is for *creation*, not update |
| `GET /v1/deleteOrder?id=5` | `DELETE /v1/orders/5` | GET must never mutate state |
| `POST /v1/orders` returning 200 | Return **201 Created** | 200 means "OK, nothing new." 201 means "new resource created at Location" |
| `PUT /v1/orders` (no ID, targets collection) | `PUT /v1/orders/{id}` | PUT replaces a specific resource, not a collection |
| `PATCH` sending the entire object | Use `PUT` for full replace | If you're sending every field, you're doing a PUT, not a PATCH |
| `POST /v1/sendOrderConfirmation` | `POST /v1/orders/{id}/notifications` | Don't use verbs in URLs — model the action as creating a resource |

---

## 🔧 Section 2 — HTTP Response Codes

### 2xx — Success

| Code | Name | When to Use |
|---|---|---|
| **200** | OK | Successful GET, PUT, PATCH, DELETE (when returning a body) |
| **201** | Created | Successful POST that created a new resource. Always include `Location` header with the new resource URL. |
| **204** | No Content | Successful DELETE (or PUT/PATCH) when there is nothing to return — the operation succeeded but the body is empty |

### 3xx — Redirection

| Code | Name | When to Use |
|---|---|---|
| **301** | Moved Permanently | Resource URL changed permanently. Client should use new URL going forward. |
| **304** | Not Modified | Client's cached version is still valid (used with `ETag` / `If-None-Match` — see Section 3) |

### 4xx — Client Error

| Code | Name | When to Use |
|---|---|---|
| **400** | Bad Request | Malformed request — invalid JSON, missing required fields, wrong types |
| **401** | Unauthorized | No authentication provided, or token is invalid/expired. "Who are you?" |
| **403** | Forbidden | Authenticated but not authorized. "I know who you are, but you can't do this." |
| **404** | Not Found | Resource doesn't exist at this URL for this caller |
| **405** | Method Not Allowed | Endpoint exists but the verb is wrong (`DELETE /v1/orders` on a collection) |
| **409** | Conflict | Request conflicts with *current resource state*. Concurrent edit conflict. Invalid state transition. |
| **422** | Unprocessable Entity | Well-formed JSON but semantically invalid. Valid JSON, but email field is not a valid email address. |
| **429** | Too Many Requests | Rate limited. Include `Retry-After` header. |

### 5xx — Server Error

| Code | Name | When to Use |
|---|---|---|
| **500** | Internal Server Error | Unexpected server failure. Never expose stack traces to client. |
| **502** | Bad Gateway | Upstream service returned an invalid response (your service is a proxy) |
| **503** | Service Unavailable | Overloaded or in maintenance. Include `Retry-After` header. |
| **504** | Gateway Timeout | Upstream service didn't respond in time |

---

### 🧠 Mental Models for the Tricky Codes

These are the ones Confluent probes most. The textbook definition is insufficient — you need to know *when each applies and why*.

---

**🔹 400 vs 422 vs 409 — the "what kind of wrong?" triangle**

These three all mean "I can't fulfil this request," but the *reason* is different:

```
400 — The request is structurally broken.
      "You sent me something I can't even parse."
      Examples: malformed JSON, missing Content-Type header,
                required field completely absent.

422 — The request is structurally fine but semantically wrong.
      "I can read your request, but the values don't make sense."
      Examples: email field contains "hello" (not an email address),
                quantity field is -5 (negative not allowed),
                date field is "yesterday" (not a valid date format).

409 — The request is perfectly valid, but it conflicts with
      the CURRENT STATE of the resource on the server.
      "Your request is correct. The world just doesn't allow it right now."
      Examples: trying to create a user with an email that already exists,
                trying to cancel an order that is already SHIPPED,
                two concurrent writes to the same record (optimistic lock failed).
```

### 🎨 Visual — The 400/422/409 Decision

```
  Client sends a request
         │
  Is the request body valid JSON / parseable?
  ├── NO  → 400 Bad Request (can't even read it)
  │
  Are all required fields present and correct format?
  (email is an email, date is a date, quantity is positive INT)
  ├── NO  → 422 Unprocessable Entity (readable but semantically wrong)
  │
  Do the VALUES conflict with the server's current state?
  (user already exists, order is in wrong status, etc.)
  ├── YES → 409 Conflict (valid request vs real-world state clash)
  │
  → 200 / 201 / 204 (success)

KEY INVARIANT:
  400 = broken syntax. 422 = broken semantics. 409 = broken state.
  Three different failure layers. Confluent probes exactly this distinction.
```

---

**🔹 401 vs 403 — "Who are you?" vs "I know who you are, but no."**

Analogy: a nightclub bouncer.

- **401 Unauthorized:** The bouncer doesn't even know if you're on the list because you haven't shown ID. "Where's your ID?" You haven't authenticated yet (no token, or the token is expired/invalid).
- **403 Forbidden:** You showed your ID. The bouncer found you on the list. But this section is "VIP only" and you're not VIP. You're authenticated, but not *authorized* for this action.

> **Memory trick:** Despite being called "Unauthorized," 401 is really an **authentication** failure. 403 is the true **authorization** failure. The naming is confusing by design — it's a historical accident from the early HTTP spec.

---

**🔹 The 404 vs 403 Enumeration Trap — the most important nuance**

The textbook says: 403 = "you can't access this resource."

But in a real API, returning 403 when a user tries to access another user's data *reveals that the resource exists at that ID*. An attacker can enumerate IDs (try `/orders/1`, `/orders/2`, etc.) — every 403 tells them "an order exists here, it's just not yours."

The fix: return **404 Not Found** for any resource that either doesn't exist OR belongs to a different user. The caller cannot distinguish these two cases — and that's intentional.

```
Textbook:
  GET /v1/orders/999
  Order 999 exists, belongs to Alice, caller is Bob.
  → 403 Forbidden  ← tells Bob that order 999 EXISTS (information leak)

Correct production behavior:
  GET /v1/orders/999
  Order 999 exists, belongs to Alice, caller is Bob.
  → 404 Not Found  ← Bob can't tell if 999 exists or doesn't exist

When IS 403 appropriate?
  Use 403 when the resource itself is public/known, but the ACTION
  is not allowed for this user's ROLE.
  Example: GET /v1/admin/dashboard → 403 for non-admin users.
  (The existence of the admin dashboard is not a secret — only access is restricted.)
```

### 🎨 Visual — Full Status Code Decision Flow

```
  Client sends a request
          │
  Is the request well-formed? (parseable JSON, correct Content-Type)
  ├── NO  → 400 Bad Request
  │
  Is the client authenticated? (valid, non-expired JWT / API key present)
  ├── NO  → 401 Unauthorized
  │
  Does the resource exist AND belong to this caller?
  ├── NO  → 404 Not Found
  │       (use 404, not 403, to prevent enumeration — see note above)
  │
  Is the HTTP verb allowed on this resource?
  ├── NO  → 405 Method Not Allowed
  │
  Are the field values semantically valid?
  (correct types, correct formats, within allowed ranges)
  ├── NO  → 422 Unprocessable Entity
  │
  Does the request conflict with the resource's current state?
  (status transition invalid, resource already exists, concurrent edit)
  ├── YES → 409 Conflict
  │
  Is the client over the rate limit?
  ├── YES → 429 Too Many Requests + Retry-After header
  │
  Server processing:
  ├── New resource created → 201 Created + Location header
  ├── Successful, body returned → 200 OK
  ├── Successful, no body → 204 No Content
  ├── Server error → 500 / 502 / 503 / 504

KEY INVARIANT:
  The decision tree runs top to bottom. Authentication before authorization.
  Authorization (via 404) before semantic validation.
  Semantic validation before state conflict check.
  Never reach a 409 without first confirming the request was valid JSON.
```

---

## 🔧 Section 3 — Headers

### Request Headers (sent by the client)

| Header | Purpose | Example |
|---|---|---|
| `Content-Type` | Format of the request body | `application/json` |
| `Accept` | Format the client wants in the response | `application/json` |
| `Authorization` | Auth credential (the JWT — JSON Web Token, a self-contained signed credential encoding the user's identity) | `Bearer eyJhbGci...` |
| `Idempotency-Key` | Safe retry token for POST (see Section 6) | `Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000` |
| `If-None-Match` | Conditional GET — "only give me the resource if it changed since this version" | `If-None-Match: "abc123"` |
| `If-Match` | Conditional PUT/PATCH — "only apply this update if the resource is still at this version" (optimistic locking — the technique of reading a version token and only writing if the version hasn't changed, rather than holding a lock) | `If-Match: "abc123"` |

### Response Headers (sent by the server)

| Header | Purpose | Example |
|---|---|---|
| `Location` | URL of the newly created resource (always with 201) | `Location: /v1/orders/abc123` |
| `ETag` (Entity Tag — a version fingerprint for a resource, typically a hash of its content; the client sends it back in `If-None-Match` to ask "has this changed?") | Version identifier for caching | `ETag: "33a64df5"` |
| `Retry-After` | When the client should retry (with 429 or 503) | `Retry-After: 60` (seconds) |
| `X-Request-Id` | Trace ID for debugging and log correlation | `X-Request-Id: req-abc-123` |
| `Link` | Pagination navigation links (cursor-based) | `Link: </v1/orders?cursor=abc>; rel="next"` |
| `Cache-Control` | How the client (or proxy) should cache the response | `Cache-Control: max-age=3600, private` |

> **Why `Location` with 201 matters to Confluent:** After `POST /v1/orders` returns 201, the client immediately knows where to fetch the created resource (`/v1/orders/abc123`) from the `Location` header — without needing a second round-trip to discover the ID. Missing this header forces the client to parse the response body to get the ID. Not returning `Location` is a common mistake.

---

## 🔧 Section 4 — URL Design

### Rules

| Rule | ✅ Right | ❌ Wrong |
|---|---|---|
| Use **nouns**, not verbs in paths | `/v1/orders` | `/v1/getOrders`, `/v1/createOrder` |
| Use **plural** for collections | `/v1/orders` | `/v1/order` |
| Use **hierarchical nesting** for owned relationships | `/v1/orders/{id}/items` | `/v1/items?orderId=5` |
| Use **kebab-case** (hyphens) | `/v1/order-items` | `/v1/orderItems`, `/v1/order_items` |
| **Version in path** | `/v1/orders` | `/orders` (no version is a breaking-change risk) |
| Use **query params** for filtering, not path segments | `/v1/orders?status=PENDING` | `/v1/pending-orders` |
| Use **sub-resources for actions** that can't be a state change | `/v1/orders/{id}/notifications` | `/v1/sendOrderNotification` |

### Nesting Depth — Stop at 2 Levels

```
✅ /v1/orders/{orderId}/items/{itemId}       ← 2 levels of nesting — fine
❌ /v1/customers/{id}/orders/{id}/items/{id}/reviews  ← 3 levels — too deep
```

If nesting goes 3+ levels, promote the innermost resource to top-level:

```
✅ /v1/items/{itemId}          ← top-level, with query param for context
   /v1/reviews?item_id=xyz    ← top-level, filtered
```

### 🎨 Visual — Modelling Actions as Resources

```
WRONG (procedure-call style):
  POST /v1/cancelOrder          ← verb in URL
  POST /v1/sendConfirmation     ← verb in URL
  GET  /v1/getOrdersByStatus    ← verb in URL

RIGHT (resource + verb style):
  Operation           Resource           HTTP Verb    URL
  ───────────────────────────────────────────────────────────
  Cancel an order  →  order status   →  PATCH  →  PATCH /v1/orders/{id}/status
                       { "status": "CANCELLED" }
  
  Send confirmation→  notification   →  POST   →  POST /v1/orders/{id}/notifications
                       { "type": "CONFIRMATION" }
  
  Get by status    →  orders         →  GET    →  GET /v1/orders?status=PENDING

KEY INVARIANT:
  Every server-side action = a CREATE or an UPDATE on a resource.
  If you can't model it that way, you're writing RPC (Remote Procedure Call —
  a style where the client calls a named function on the server, like
  /cancelOrder — the opposite of REST), not REST.
```

---

## 🔧 Section 5 — Pagination: The Problem It Solves

**Why pagination exists:** If `/v1/orders` returns all of a customer's orders (potentially thousands), that's a slow query, a big payload, and unusable in a UI. Pagination returns results in pages of a fixed size (e.g., 20 per page).

**Two approaches — with a critical difference:**

### Offset-Based Pagination (simple, breaks at scale)

```
GET /v1/orders?offset=40&limit=20

Means: "Skip the first 40, give me the next 20."

Response:
{
    "orders": [...],
    "pagination": {
        "offset": 40,
        "limit": 20,
        "total": 340   ← total count is cheap here (one COUNT(*) query)
    }
}
```

**The problem — "rows shift":** If a new order is placed while you're on page 2, every item after it shifts by 1. You either skip an item or see a duplicate on the next page.

**The performance problem:** `LIMIT 20 OFFSET 1000` makes the database read and discard 1,000 rows. At page 50, you're discarding 1,000 rows on every request. The further the page, the more work.

### Cursor-Based Pagination (scalable, consistent)

```
GET /v1/orders?limit=20&cursor=<opaque_token>

"cursor" encodes the sort key of the LAST item on the previous page.
Means: "Give me 20 orders AFTER this specific one."

Response:
{
    "orders": [...],
    "next_cursor": "eyJjcmVhdGVkX2F0IjoiMjAyNi0wNS0xMFQxMjowMDowMFoifQ==",
    "has_more": true
    // NO total_count — see note below
}
```

The cursor is typically a base64-encoded timestamp or ID of the last item. The server's query becomes:

```sql
SELECT * FROM orders
WHERE  customer_id = :cid
  AND  created_at < :cursor_timestamp
ORDER BY created_at DESC
LIMIT 20;
```

This is an index seek — O(log N) regardless of how far into the list you are.

### 🎨 Visual — Offset vs Cursor

```
OFFSET PAGINATION — what breaks (insert → duplicate):
  ─────────────────────────────────────────────────────────────
  Orders sorted newest-first at time T:  [A, B, C, D, E, F, G, H, I, J]
  Client fetches page 1 (offset=0, limit=5):  [A, B, C, D, E]

  New order X is inserted between D and E while client is reading page 1.
  Orders now:        [A, B, C, D, X, E, F, G, H, I, J]

  Client fetches page 2 (offset=5, limit=5):  [E, F, G, H, I]
  ← E was DUPLICATED — shown on page 1 AND page 2. Client sees it twice.

  (The mirror of this problem: if D were DELETED between pages,
   every item shifts left by one, and E moves to position 4 — offset=5
   starts at F and E is never seen. Deletes → skip; inserts → duplicate.)

CURSOR PAGINATION — why it's immune:
  ─────────────────────────────────────────────────────────────
  Client fetches page 1 with no cursor:  [A, B, C, D, E]
  Last item on page 1 = E. Cursor = E's created_at timestamp.

  New order X inserted between D and E.

  Client fetches page 2 with cursor = E's timestamp:
  Query: WHERE created_at < E's timestamp → returns [F, G, H, I, J]
  ← Correct. E is not repeated. Cursor is anchored to a VALUE, not a POSITION.

KEY INVARIANT:
  Offset navigates by POSITION ("skip N rows"). Cursor navigates by VALUE
  ("give me items strictly before/after this value"). When rows are inserted,
  positions shift; values don't. Cursor pagination is immune to concurrent
  inserts and deletes.
```

### When to Use Which

| Use Offset When | Use Cursor When |
|---|---|
| Small, static dataset (< 10K items) | Large or growing dataset |
| User needs "jump to page 50" UI | Sequential "load more" / infinite scroll |
| You need `total_count` in the response | Data is frequently inserted while the client paginates |

> **`total_count` and cursor pagination conflict:** Cursor pagination cannot return `total_count` cheaply. A `COUNT(*)` with the same filters is a separate query that scans the whole matching set. If you return it, you're paying O(N) for every page read. Either omit `total_count`, return it as an approximate value (updated lazily), or use offset pagination if "Page 3 of 47" UI is a hard requirement.

**In a Confluent interview:** Default to cursor-based. Say explicitly: "I'm using cursor-based pagination here because the order list grows continuously — with offset pagination, any new order inserted between page reads would push existing items to higher offsets, causing duplicates on the next page. Cursor pagination anchors to a value, not a position, so concurrent inserts don't affect it. And the O(offset) DB scan gets expensive as history grows — cursor is an index seek regardless of how far in we are."

---

## 🔧 Section 6 — Idempotency: The Safe Retry Problem

**The problem in plain English:**

A customer clicks "Place Order." Your server processes the order and charges their card. But the network drops before the response reaches the client. The client doesn't know if the order was placed. It retries. Your server receives a second request. Without idempotency, it places a second order and charges the card twice.

### 🎨 Visual — The Retry Problem Without Idempotency

```
WITHOUT IDEMPOTENCY KEY:
─────────────────────────────────────────────────────────────────────
Client                           Network           Server
  │                                │                 │
  ├──── POST /v1/orders ──────────►│────────────────►│
  │     { items: [...] }           │                 ├── INSERT order #101 ✓
  │                                │                 ├── Charge card $99 ✓
  │                                │◄── 201 Created ─┤
  │     ← RESPONSE DROPPED         │                 │
  │     (client never sees it)     │                 │
  │                                │                 │
  ├──── POST /v1/orders ──────────►│────────────────►│  ← RETRY
  │     { items: [...] }           │                 ├── INSERT order #102 ✓ (DUPLICATE!)
  │                                │                 ├── Charge card $99 ✓ (DOUBLE CHARGE!)
  │◄─── 201 Created ───────────────┤◄────────────────┤
  │
  Result: 2 orders, $198 charged. Customer furious.

WITH IDEMPOTENCY KEY:
─────────────────────────────────────────────────────────────────────
Client                           Network           Server
  │                                │                 │
  ├──── POST /v1/orders ──────────►│────────────────►│
  │     Idempotency-Key: uuid-123  │                 ├── INSERT order #101 ✓
  │     { items: [...] }           │                 ├── Charge card $99 ✓
  │                                │                 ├── Store: uuid-123 → 201, order #101
  │                                │◄── 201 Created ─┤
  │     ← RESPONSE DROPPED         │                 │
  │                                │                 │
  ├──── POST /v1/orders ──────────►│────────────────►│  ← RETRY (same key)
  │     Idempotency-Key: uuid-123  │                 ├── Look up uuid-123 → already processed
  │◄─── 201 Created (cached) ──────┤◄────────────────┤  NO new order. NO new charge.
  │
  Result: 1 order, $99 charged. Correct.

KEY INVARIANT:
  The idempotency key is the client's promise: "This request is uniquely identified
  by this key. If you've already processed it, return the cached result — don't
  process it again." The server stores (key → response) in Redis or DB for 24h.
```

**Which verbs need it:**

- **POST** — always (not idempotent by nature)
- **GET, PUT, DELETE** — already idempotent by definition, don't need a key
- **PATCH** — add a key if your PATCH has non-idempotent side effects (e.g., "increment quantity by 1")

**How to say it in an interview:**

> "I'll add an `Idempotency-Key` header on `POST /v1/orders`. The client generates a UUID per order attempt. On the server, before processing, I check if this key exists in a short-lived store (Redis with 24h TTL). If it does, I return the cached response immediately — no processing, no charge. If it doesn't, I process normally and store the result. This makes the client safe to retry on network failures without any risk of duplicate orders."

---

## 🔧 Section 7 — Error Response Format

Every error response from every endpoint in your API must use the same JSON structure. Inconsistent errors are a major red flag.

```json
{
    "error": {
        "code": "INVALID_EMAIL_FORMAT",
        "message": "The 'recipient' field must be a valid email address.",
        "details": [
            {
                "field": "recipient",
                "reason": "Must match pattern: user@domain.tld"
            }
        ],
        "request_id": "req-abc-123"
    }
}
```

**Field rules:**
- `code` — machine-readable string (NOT the HTTP status number). The client's code can `switch` on this to show the right UI message.
- `message` — human-readable. Shown to developers in logs or API explorers.
- `details` — optional array for field-level validation errors (useful for form validation feedback).
- `request_id` — matches the `X-Request-Id` response header. Lets the client report it to support for log correlation.

**Never expose:** stack traces, internal file paths, database error messages, SQL queries, or implementation details. These are security vulnerabilities and are automatically flagged in Confluent (and any serious company) API reviews.

---

## 🔧 Section 8 — Versioning

| Strategy | Example | Pros | Cons |
|---|---|---|---|
| **URL Path** (recommended) | `/v1/orders` | Simple, explicit, easy to test in browser | URL changes on version bump |
| **Header** | `Accept: application/vnd.myapi.v1+json` | Clean URLs | Hidden, harder to test, harder to cache |
| **Query Param** | `/orders?version=1` | Easy to add | Clutters query string, easy to forget |

**In a Confluent interview:** Use URL path versioning. It's the most common, most explicit, and easiest to explain. Just say: "I version in the URL path (`/v1/`) — it makes the version explicit, easy to route at the gateway level, and clients can see it in logs without parsing headers."

---

## 🧭 Section 9 — The Derivation Framework: FR → Endpoint

This is the pattern that separates candidates who *recall* APIs from candidates who *derive* them. Confluent wants to see derivation, not memorization.

**The pattern, step by step:**

```
Functional Requirement
    → What operation is this? (create / read / update / delete)
    → What is the resource? (noun)
    → Who calls it? (customer / admin / system)
    → What is the minimum payload needed?
    → What does the caller need back?
    → What can go wrong? (error codes)
    → Is there a constraint that shapes the contract?
       (idempotency? pagination? status machine?)
    → HTTP method + URL path + request body + response body + status codes
```

**Worked example — "Customers can place an order":**

```
FR: "Customers can place an order containing one or more products."

1. Operation:    CREATE (a new order)
2. Resource:     order (noun — not "placeOrder")
3. Caller:       authenticated customer (JWT required)
4. Min payload:  list of { product_id, quantity } pairs
5. Response:     the created order: id, status, total_amount, items
6. Errors:       400 (malformed), 401 (not authed), 409 (out of stock)
7. Constraint:   Idempotency-Key needed (financial operation; client retries on timeout)
                 total_amount computed server-side (never trust client-sent price)
                 201 not 200 (new resource created)
                 Location header with the new order URL

Result:
  POST /v1/orders
  Authorization: Bearer <token>
  Idempotency-Key: <uuid>
  { "items": [{ "product_id": "p1", "quantity": 2 }] }

  → 201 Created
    Location: /v1/orders/ord-abc123
    { "id": "ord-abc123", "status": "PENDING", "total_amount": 59.98, "items": [...] }
  → 400 if items array is empty or missing
  → 401 if no valid JWT
  → 409 if any requested quantity exceeds available stock
```

**Validation rule:** Every endpoint should map to at least one FR. Every FR should have at least one endpoint. If you have an endpoint with no FR, ask yourself why it exists. If you have a FR with no endpoint, you have a gap.

---

## ⚠️ Section 10 — Confluent Probe Questions (with prepared answers)

These are the exact questions that come up in Confluent's API design round, based on research.

---

**Q: "Why did you use PATCH and not PUT for the status update?"**

> "PUT replaces the entire resource — the client must send every field, even those it doesn't want to change. I'm only changing `status`, so PATCH is correct: it's a partial update. Using PUT here would force the client to send the full order object on every status update, which is both verbose and fragile — if the server adds a new field later, old clients that don't know about it would wipe it on every PUT."

---

**Q: "Why 409 and not 400 when the item is out of stock?"**

> "400 means the request was malformed — bad JSON, missing fields. The order request I received is perfectly valid: correct JSON, correct product IDs, valid quantities. The problem is a state conflict — the current `stock_quantity` in the database makes this impossible to fulfil right now. That's 409 Conflict: a valid request vs an incompatible resource state. Using 400 would suggest the client made a mistake; 409 correctly says 'the request is fine, the state of the world doesn't allow it.'"

---

**Q: "Why do you return 404 for cross-user access instead of 403?"**

> "The textbook says 403 = authenticated but not authorized. But 403 also reveals that the resource *exists* — an attacker trying random order IDs will use 403 responses as an oracle: 'order 1001 exists but isn't mine, order 1002 doesn't exist at all.' Returning 404 uniformly for any resource that either doesn't exist or doesn't belong to the calling user prevents this enumeration. The caller cannot distinguish 'this order doesn't exist' from 'this order exists but is someone else's' — and that's intentional. I use 403 only for role-based access to known resources, like `GET /v1/admin/dashboard` — where the admin panel's existence is public knowledge, only access is restricted."

---

**Q: "What's wrong with returning `total_count` in cursor-based pagination?"**

> "A cursor-based query uses `WHERE created_at < :cursor ORDER BY created_at DESC LIMIT 20` — an index seek that reads exactly 20 rows. Computing `total_count` requires a separate `COUNT(*)` with the same filters, which scans every matching row. For a customer with 2,000 orders, that's 2,000 rows read just to return the number — 100× more work than the actual page query. I either omit `total_count` (and return `has_more: true/false`) or mark it as an approximate value updated on a background job. Returning an exact count on every page request defeats the performance benefit of cursor pagination."

---

**Q: "When do you add the `Idempotency-Key` header to an endpoint?"**

> "Any POST that has financial or operational consequences that must not be duplicated. Order placement, payment initiation, subscription activation — any of these doubled would cause real harm. I wouldn't add it to `POST /v1/emails` (disposable email creation) — creating a duplicate email is harmless. The test is: 'Would a duplicate processing of this request cause a real problem?' If yes, add the key."

---

**Q: "What would you put in the `Location` header after a POST?"**

> "The full path to the newly created resource — `/v1/orders/ord-abc123`. This tells the client exactly where to GET the resource on the next request, without having to parse the response body to find the ID. It's the REST equivalent of saying 'I created it, and here's the receipt to find it again.'"

---

## 🧭 Section 11 — Interview Presentation Template

When the interviewer says "design the API for X," say this out loud and work through it visibly:

**Step 1 — Identify resources (30 seconds):**
> "The core resources I see are: Order, OrderItem, Product. My API will expose endpoints on these nouns."

**Step 2 — Walk one FR → endpoint derivation out loud:**
> "The first functional requirement is 'customers can place an order.' That's a CREATE operation on the Order resource, so POST /v1/orders. Let me think about what goes in the request body and what comes back..."

**Step 3 — Build the endpoint table:**
Show each endpoint with verb, path, status codes, and one-line rationale. Don't just list — narrate the decisions.

**Step 4 — Call out the non-obvious choices proactively (don't wait to be asked):**
> "I want to call out a few non-obvious choices: I'm using 409 for out-of-stock (not 400, because the request is valid — the world state conflicts). I'm using cursor pagination on order history because offset breaks when new orders are placed mid-pagination. I'm adding Idempotency-Key on POST /v1/orders because a double-charge would be a real problem on network retry."

**Step 5 — Invite probing:**
> "That covers the core contract. Should I go deeper on any of these endpoints, or shall we move to the data model?"

---

## 🧾 TL;DR — 60-Second Revision

```
VERBS:    GET=read, POST=create, PUT=full replace, PATCH=partial update, DELETE=remove
          GET is safe (never mutates). POST is not idempotent (two POSTs = two resources).

CODES:    POST → 201 (not 200). DELETE → 204 (no body). Conflict → 409.
          Malformed → 400. Bad semantics → 422. Out of stock → 409 (not 400).
          Cross-user access → 404 (not 403, to prevent enumeration).

HEADERS:  201 must include Location. POST with risk → Idempotency-Key.
          429/503 → include Retry-After. ETag for cache validation.

URLs:     Nouns not verbs. Plural. Kebab-case. Version in path. Max 2 nesting levels.
          Filter by query param (?status=PENDING), not path segments (/pending-orders).

PAGINATE: Cursor for large/growing data. Offset for small/static.
          Cursor = O(log N) index seek. Offset = O(offset) scan (gets slow).
          Cursor cannot return total_count cheaply — omit or approximate.

IDEMPOTENCY: Add Idempotency-Key to POST when duplicate processing causes real harm.
             Server stores (key → response) in Redis for 24h. Second request = cache hit.

ERRORS:   Consistent JSON structure: { error: { code, message, details, request_id } }.
          Never expose stack traces or DB errors.
```

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Jul 2026 | Created. Compact reference card covering HTTP verbs, response codes, headers, URL design, pagination, error format, idempotency, versioning, interview template. |
| Aug 10, 2026 | Major expansion to full mental-model notes. Added: REST first-principles explanation (Section 0), safe vs idempotent deep dive with visual, 400/422/409 decision triangle, 401/403 nightclub analogy, 404 vs 403 enumeration nuance and when each applies, cursor vs offset visual with "rows shift" animation, idempotency network-failure visual, FR → endpoint derivation framework (Section 9), Confluent probe Q&A (Section 10), interview presentation template (Section 11). All terms glossed at first use. All ASCII visuals include KEY INVARIANT. Total count limitation on cursor pagination documented. |
