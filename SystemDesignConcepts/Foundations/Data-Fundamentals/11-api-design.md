# API Design

> **Standard followed:** `SystemDesignConcepts/notes-standards.md`

---

## 🎯 Why This Matters

Every system design interview involves an API. The question is whether you design it deliberately or accidentally. DocuSign's confirmed R2 format gives candidates an explicit "API design" option — you can own this question entirely if you know the contract patterns. Senior engineers are expected to design APIs that external clients can depend on, version correctly, and retry safely — not just APIs that work on the first call.

**Which round:** R2 System Design (Variant A — API Design choice).
**Why senior engineers own this:** Junior engineers make endpoints work. Senior engineers make endpoints correct — idempotent, versioned, consistent in error codes, and pagination-ready for millions of records.

---

## 📖 What is API Design?

**Full form:** Application Programming Interface / API Contract Design

**Simple analogy:** A restaurant menu is a contract: customers don't need to know how food is cooked, just what's available, pricing, and what happens if something's out of stock. An API is the same: clients don't care how you store data internally; they need clear endpoints, consistent behavior, and guaranteed error handling.

**Core principle:** An API is a **contract between a server and its clients**. Good API design means:
- **Consistency:** Same patterns everywhere (naming, error codes, status codes)
- **Idempotency:** Safe to retry without side effects (for POST/PUT/PATCH)
- **Evolvability:** Can evolve without breaking old clients (via versioning)
- **Predictability:** Clients can learn your API once and use it confidently

**Why it matters in system design:** A poorly designed API breaks external clients; a well-designed API becomes a public platform. Senior engineers design APIs deliberately, not accidentally.

---

## 🎨 Visual — System Topology: API in Architecture

```
EXTERNAL CLIENTS
(Web, Mobile, Third-party Integrations)
    │
    │ HTTP/REST calls
    │ (GET, POST, PUT, DELETE)
    │
    ▼
┌──────────────────────────────────┐
│ API Gateway / Server             │
│                                  │
│ ┌───────────────────────────────┐│
│ │ Routing                        ││
│ │ - POST   /v1/documents         ││
│ │ - GET    /v1/documents/{id}    ││
│ │ - PUT    /v1/documents/{id}    ││
│ │ - DELETE /v1/documents/{id}    ││
│ └───────────────────────────────┘│
│                                  │
│ ┌───────────────────────────────┐│
│ │ Request Validation             ││
│ │ - Idempotency-Key check        ││
│ │ - Authorization check          ││
│ │ - Rate limiting check          ││
│ └───────────────────────────────┘│
│                                  │
│ ┌───────────────────────────────┐│
│ │ Response Formatting            ││
│ │ - Status codes (200, 201, 4xx) ││
│ │ - Standard error shape         ││
│ │ - Pagination (if needed)       ││
│ └───────────────────────────────┘│
└──────────┬───────────────────────┘
           │
           ▼
    Internal Services
    (Database, Cache, etc.)

KEY INVARIANT:
   API is the boundary between client and server
   Contract must be explicit, versioned, and stable
   Changes require new version (v2) to avoid breaking clients
```

---

## 🎨 Visual — API Design Patterns: HTTP Verbs & Pagination (Component Detail)

Think of an API as a **restaurant menu**.

The menu is a **contract** between the kitchen and the customer. The customer doesn't need to know how the food is cooked — they just need the menu to be clear: what items are available, what comes with each item, how much it costs, and what to do if the kitchen is out of something.

Now think about what makes a good menu vs a bad one:

**A bad menu:** Items with vague names ("House Special"), no description of what you get, inconsistent pricing format (some in dollars, some in pence), and no indication of what happens if an item is unavailable — do you get a refund? A substitute? Nothing?

**A good menu:** Every item has a clear name, exact description, listed price, and a stated policy for unavailability. The categories are logical (starters, mains, desserts). The language is consistent throughout.

An API is the same contract. The "menu items" are your endpoints (`POST /documents`, `GET /documents/{id}`). The "price" is what the client must send in the request. The "description" is what they get back. The "unavailability policy" is your error codes and messages.

The three things that make or break an API contract:

1. **Consistency** — same verb semantics everywhere, same error code meanings everywhere, same field naming conventions. A client that learns your `/users` endpoint can correctly guess how `/documents` behaves.

2. **Idempotency** — safe to retry without side effects. The client's network dropped after sending a request — can they safely send it again? `GET` and `DELETE` are naturally idempotent. `PUT` is idempotent by design. `POST` is not — you need an `Idempotency-Key` header to make it safe.

3. **Evolvability** — the contract can change without breaking existing clients. This is versioning. `/v1/documents` stays stable; new behaviour goes in `/v2/documents`. Old clients keep working.

**The key insight is:** An API is a promise you make to every client that will ever call you. Breaking that promise silently is the most expensive bug you can ship.

---

## 🎨 Visual — REST Verb × Idempotency × Safe Matrix

```
  HTTP VERB DECISION MATRIX
  ─────────────────────────────────────────────────────────────────

  Verb     │ Safe?  │ Idempotent? │ Use for
  ─────────┼────────┼─────────────┼──────────────────────────────
  GET      │  ✅    │     ✅      │ Read — fetch resource(s)
  POST     │  ❌    │     ❌      │ Create — new resource per call
  PUT      │  ❌    │     ✅      │ Replace entire resource
  PATCH    │  ❌    │     ❌*     │ Partial update (design carefully)
  DELETE   │  ❌    │     ✅      │ Remove — repeat calls are no-ops

  *PATCH is idempotent ONLY if the operation is absolute ("set status=ACTIVE"),
   NOT if it is relative ("increment count by 1").

  KEY INVARIANT:
     Safe = no side effects (read-only). Idempotent = calling N times = calling once.
     Safe implies idempotent. Idempotent does NOT imply safe.
     POST is neither — treat every POST as "might create a duplicate if retried."


  PAGINATION CHOICE MATRIX
  ─────────────────────────────────────────────────────────────────

  OFFSET PAGINATION                   CURSOR PAGINATION
  ─────────────────────────────────   ──────────────────────────────────
  GET /documents?page=2&size=20       GET /documents?cursor=eyJpZCI6MTAwfQ

  Page 2, 20 items                    "Give me items after this bookmark"

  Pros:                               Pros:
    ✅ Simple to implement              ✅ Stable — inserts/deletes don't
    ✅ Client can jump to any page         shift pages
    ✅ Easy to show "Page 3 of 47"      ✅ O(1) DB query — just WHERE id > X
                                        ✅ No duplicate items across pages

  Cons:                               Cons:
    ❌ If row inserted before page 2,   ❌ Client can't jump to page 47
       items shift — duplicates or      ❌ Can't show "Page 3 of 47"
       skips                            ❌ Cursor must be opaque (clients
    ❌ OFFSET N is slow at large N         should not parse it)
       (DB must scan N rows to skip)

  USE WHEN:                           USE WHEN:
    Small datasets (<100K rows)         Large datasets, infinite scroll,
    User needs to jump to a page        feeds, event logs, APIs

  KEY INVARIANT:
     Cursor pagination's cursor is a bookmark, not a page number.
     It encodes "where I was" — typically a base64-encoded last-seen ID or timestamp.
     The client passes it back opaque; the server decodes and queries: WHERE id > X.
```

---

## ⚙️ How It Actually Works

### Part 1 — Designing the REST Contract

**Steps:**
1. **Name resources as nouns, not verbs.** The verb is the HTTP method.
2. **Use standard HTTP verbs** exactly as the matrix above defines — don't invent `POST /getUser`.
3. **Return the right status codes** — not just 200 vs 500. Be specific.
4. **Define the error response shape** consistently — every error looks the same.
5. **Version from day one** — even if you never ship v2, `/v1/` signals maturity.

```java
// ✅ Good endpoint design — resource-first, verb from HTTP method
// POST /v1/documents                → create a new document
// GET  /v1/documents/{id}           → fetch one document
// GET  /v1/documents?status=ACTIVE  → list documents with filter
// PUT  /v1/documents/{id}           → replace a document
// DELETE /v1/documents/{id}         → delete a document

// ❌ Bad — verbs in the URL, action-style naming
// POST /createDocument
// GET  /getDocument?id=123
// POST /deleteDocument

// Standard error response shape — EVERY error returns this
public record ApiError(
    String code,        // machine-readable: "DOCUMENT_NOT_FOUND"
    String message,     // human-readable: "No document found with id 123"
    String requestId,   // trace ID for debugging
    Instant timestamp
) {}
```

### Status Code Reference (the ones interviewers drill)

| Code | Meaning | When to use |
|---|---|---|
| `200 OK` | Success with body | GET, PUT, PATCH responses |
| `201 Created` | Resource created | POST that creates a new resource — include `Location` header |
| `204 No Content` | Success, no body | DELETE, or PUT/PATCH with no return body |
| `400 Bad Request` | Client sent invalid data | Missing required field, wrong format |
| `401 Unauthorized` | Not authenticated | Missing or invalid token |
| `403 Forbidden` | Authenticated but not allowed | Valid token, but no permission for this resource |
| `404 Not Found` | Resource doesn't exist | GET/DELETE on unknown ID |
| `409 Conflict` | State conflict | Creating a resource that already exists, optimistic lock failure |
| `422 Unprocessable Entity` | Semantically invalid | Fields valid format but business rule violated (e.g., end date before start date) |
| `429 Too Many Requests` | Rate limited | Always include `Retry-After` header |
| `500 Internal Server Error` | Your bug | Never return internal stack traces to clients |

---

### Part 2 — Idempotency Key for POST

**The problem:** Client sends `POST /v1/payments`. Network times out before the response arrives. Client doesn't know if the payment was created. If they retry, they might create a duplicate payment.

**Steps:**
1. **Client generates a UUID** and sends it in the `Idempotency-Key: <uuid>` header on every POST.
2. **Server checks** an `idempotency_keys` table: has this key been seen before?
3. **If not seen:** process the request, store the key + response in the table, return the result.
4. **If already seen:** return the stored response immediately — don't process again.
5. **Key expires** after 24 hours (or per your policy).

```java
// Idempotency check in service layer
public DocumentResponse createDocument(CreateDocumentRequest req, String idempotencyKey) {
    // Step 2: check cache/DB for this key
    Optional<IdempotencyRecord> existing = idempotencyRepo.findByKey(idempotencyKey);
    if (existing.isPresent()) {
        // Step 4: return stored response — do NOT process again
        return existing.get().getStoredResponse();
    }

    // Step 3: process and store
    DocumentResponse response = documentService.create(req);
    idempotencyRepo.save(new IdempotencyRecord(idempotencyKey, response, Instant.now()));
    return response;
}
```

---

### Part 3 — Pagination (Cursor vs Offset)

**Steps for cursor pagination:**
1. **First request:** client calls `GET /v1/documents?size=20` — no cursor yet.
2. **Server returns** 20 items + a `nextCursor` field in the response body (or `Link` header).
3. **Next request:** client calls `GET /v1/documents?size=20&cursor=eyJpZCI6MjB9`.
4. **Server decodes cursor** (base64 decode → `{"id": 20}`) and queries: `WHERE id > 20 LIMIT 20`.
5. **Last page:** server returns items + `nextCursor: null` — client knows it's done.

```java
public PageResponse<Document> listDocuments(String encodedCursor, int size) {
    // Step 4: decode cursor — default to 0 if first page
    long lastSeenId = 0;
    if (encodedCursor != null) {
        String decoded = new String(Base64.getDecoder().decode(encodedCursor));
        lastSeenId = Long.parseLong(decoded);
    }

    // DB query using cursor — efficient even at millions of rows
    List<Document> docs = documentRepo.findByIdGreaterThanOrderByIdAsc(lastSeenId, PageRequest.of(0, size));

    // Step 5: build next cursor
    String nextCursor = null;
    if (docs.size() == size) {
        long lastId = docs.get(docs.size() - 1).getId();
        nextCursor = Base64.getEncoder().encodeToString(String.valueOf(lastId).getBytes());
    }
    return new PageResponse<>(docs, nextCursor);
}
```

---

### Part 4 — API Versioning

Three strategies, one clear winner for REST APIs:

| Strategy | Example | When to use |
|---|---|---|
| **URL path versioning** | `/v1/documents` | ✅ Default choice — visible, cacheable, easy to route |
| **Header versioning** | `Accept-Version: 2` header | Internal APIs, when URLs must stay clean |
| **Query param versioning** | `/documents?version=2` | Avoid — fragments/busts HTTP caches (query string is part of the cache key) and semantically mixes versioning with resource filtering |

**Breaking vs non-breaking changes:**

| Non-breaking (safe to deploy) | Breaking (need new version) |
|---|---|
| Add optional request field | Remove or rename existing field |
| Add field to response body | Change field type (string → int) |
| Add new endpoint | Change status code semantics |
| Add new enum value | Remove endpoint |

---

## 🧭 REST vs GraphQL vs gRPC + 202 + HATEOAS (commonly probed)

**Protocol choice:**

| | REST | GraphQL | gRPC |
| --- | --- | --- | --- |
| **Best for** | Public/partner APIs, CRUD, cacheable resources | Client-driven UIs with varied data needs; mobile (avoid over/under-fetching) | Internal service-to-service, low latency, streaming |
| **Shape** | Many endpoints, HTTP verbs + status codes | Single endpoint, client specifies exact fields | Binary (protobuf) over HTTP/2, typed contract |
| **Downsides** | Over/under-fetching; N calls for related data | Caching is harder; query cost/complexity attacks; N+1 resolvers | Not browser-native without a proxy; opaque payloads |

**`202 Accepted` — the async workhorse:** for long-running operations, return `202` immediately with a `Location`/status URL the client polls (or a webhook). Standard for "kick off a job, get the result later" — pairs with idempotency keys so a retried kickoff doesn't start the job twice.

**HATEOAS** (Hypermedia As The Engine Of Application State) — REST's top maturity level (Richardson Level 3): responses embed links to the next valid actions (`{"status":"pending","_links":{"cancel":"/orders/42/cancel"}}`) so clients discover transitions instead of hard-coding URLs. Rarely fully adopted in practice, but worth naming — most "REST" APIs stop at Level 2 (verbs + status codes).

---

## 🏢 Real World — Where Companies Use This

- **DocuSign** — Cursor pagination on `/v2/accounts/{id}/envelopes` for large envelope histories. The confirmed R2 question (C3) maps exactly to this: design pagination where clients can retrieve thousands of records without duplicates or skips on live data.
- **Stripe** — `Idempotency-Key` header is mandatory on all write operations. Their docs explicitly state: "Stripe's idempotent APIs use this key to guarantee at-most-once execution." Every payment SDK sends this header automatically.
- **GitHub API** — **media-type (header) versioning**: `v3` was selected via the `Accept: application/vnd.github.v3+json` content-negotiation header (not a URL path). The modern REST API uses a **date-based** header `X-GitHub-Api-Version: 2022-11-28`. (Note: GitHub `v4` is a *different* API — GraphQL — not a REST path version.) A real example of header/date-based versioning.
- **Razorpay** — Returns `422 Unprocessable Entity` for business-rule violations (invalid beneficiary account) distinct from `400 Bad Request` (missing field). This distinction is what separates a "client input error" from a "your data passed validation but violated business rules" error.
- **Swiggy / Zomato** — Cursor pagination on order history feeds. `GET /orders?cursor=<token>` on a feed that changes in real time (new orders arrive while you paginate) — offset would give duplicates or skips; cursor is stable.
- **AWS SDK** — Every mutating call accepts a `ClientToken` (AWS's name for idempotency key) on resource creation. `CreateStack` with the same `ClientToken` twice returns the same stack, not two stacks.

---

## 🧭 When to Use vs When NOT to Use

| Use cursor pagination when | Use offset pagination when |
|---|---|
| Data is updated in real time (feeds, logs, event lists) | Data is static or rarely changes |
| Dataset is large (>100K rows) — offset gets slow | Client needs "jump to page N" behaviour |
| Infinite scroll / streaming pattern | Dataset is small (<10K rows) |
| Correctness matters (no duplicates) | "Page X of Y" UI is required |

| Add an Idempotency-Key when | Skip it when |
|---|---|
| POST creates a resource (payment, order, document) | GET, DELETE, PUT (already idempotent) |
| Clients could retry on network failure | Internal idempotent operations |
| Operation has financial or irreversible side effects | High-frequency reads |

**The common mistake:** Using `200 OK` for everything and encoding the error in the response body (`{"status": "error", "message": "not found"}`). HTTP status codes exist for a reason — load balancers, monitoring tools, SDKs, and retry logic all depend on them. Returning 200 for an error breaks the entire HTTP ecosystem.

---

## ⚠️ Trade-offs

| | |
|---|---|
| **You gain** | A stable, predictable contract that external clients can depend on without reading your source code. Versioning lets you evolve without breaking existing integrations. Idempotency makes the system resilient to retries. |
| **You lose** | Flexibility to change quickly — once a contract is public, breaking it has a cost. Versioning adds operational overhead (maintaining v1 and v2 simultaneously). Idempotency key storage adds a DB table and lookup on every write. |
| **Failure mode** | Inconsistent contracts — different endpoints use different error shapes, different pagination styles, different verb semantics. Clients end up writing per-endpoint custom code. The API becomes impossible to SDK-wrap. |

---

## 🔬 Interview Q&As

### Tier 1 — Surface Questions

### Q: "Walk me through how you'd design the API for a document management system like DocuSign."
> Start by naming the resources: Document, Envelope, Recipient, SignatureField. For each resource, define the standard CRUD endpoints using correct HTTP verbs — `POST /v1/envelopes` to create, `GET /v1/envelopes/{id}` to fetch, `PUT /v1/envelopes/{id}` to update. Version from day one — `/v1/` in the path. For listing envelopes (which can be thousands per account), use cursor pagination — a `nextCursor` token in the response so clients fetch next pages without duplicates. For creating envelopes, add an `Idempotency-Key` header — a retry after a network timeout should return the same envelope, not create a duplicate.

### Q: "When would you use cursor pagination over offset pagination?"
> Cursor when the data changes in real time and correctness matters — if a row is inserted before page 2 loads, offset shifts everything and you get duplicates or skips. Cursor is a bookmark, not a page number — it says "give me items after id=X" so inserts don't affect you. Also, `OFFSET N` forces the database to scan and skip N rows — at millions of records this is slow. Cursor uses `WHERE id > X` — efficient with an index regardless of dataset size. I'd only use offset when the UI requires "jump to page N" or the dataset is small and static.

### Q: "What's the difference between 400, 401, 403, 404, 409, and 422?"
> `400` — malformed request, missing required field, wrong data type. `401` — not authenticated, no valid token. `403` — authenticated but not authorized for this resource. `404` — resource doesn't exist. `409` — resource exists but state conflicts (creating a duplicate, optimistic lock failure). `422` — request is well-formed but semantically invalid — the business rule failed (end date before start date, insufficient balance). The key distinction: `400` means "I can't parse your input." `422` means "I parsed it, but it violated a rule."

### Q: "How does an idempotency key work?"
> The client generates a UUID and sends it in the `Idempotency-Key` header on every non-idempotent POST. The server checks an idempotency table keyed by that UUID — if it exists, return the stored response immediately without processing. If not, process normally, store the key and response, return the result. This makes retries safe: if a network timeout causes the client to retry, the second call hits the cache and returns the exact same result. Typically keys expire after 24 hours. Stripe, Razorpay, and AWS all implement exactly this pattern.

---

### Tier 2 — Cross / Probe Questions

### Q: "Your API is on v1. You need to add a required field to an existing endpoint's request. Is that a breaking change? How do you handle it?"
> Yes — making a previously optional or absent field required is a breaking change. Existing clients that don't send the new field will start getting `400 Bad Request`. The safe approach: (1) Make the field optional in v1, apply a sensible default server-side. (2) Announce the migration timeline — give clients 6-12 months. (3) Release v2 where the field is required. (4) Deprecate v1 with a `Deprecation` response header. If the change is urgent (security), you can do it in v1 but you must notify clients proactively — not silently.

### Q: "Two requests come in simultaneously with the same idempotency key — both check the DB and both see it's not there yet. How do you handle this race condition?"
> The idempotency table needs a unique constraint on the `key` column. Both requests race to insert — exactly one will succeed, the other will get a unique constraint violation. The losing request should catch that exception and then re-read the table (the winner has now written the result) and return the stored response. Alternatively, use a database-level lock: `SELECT ... FOR UPDATE` on the idempotency key row. The second thread waits until the first commits, then reads the stored result. The unique constraint approach is simpler and more scalable.

### Q: "A client is receiving inconsistent results when paginating through your document list — sometimes they see the same document twice, sometimes they miss one. What's wrong?"
> Almost certainly offset pagination on live data. If a new document is inserted between page 1 and page 2 requests, all IDs shift — what was at offset 20 is now at offset 21, and the document at offset 20 gets returned twice (once at end of page 1, once at start of page 2). The fix is cursor pagination: `WHERE id > {last_seen_id}`. Inserts and deletes of documents the client hasn't seen yet don't affect the cursor's position. The client gets a consistent, stable view of "documents I've seen so far."

### Q: "How do you version an API when you can't change the URL (e.g., it's already hardcoded in client apps)?"
> Header versioning: client sends `Accept: application/vnd.yourapi.v2+json` and the server dispatches to the v2 handler. This keeps the URL `/documents` stable while routing internally to different logic. The downside: it's invisible in logs and browser bookmarks, and harder to cache (CDNs key on URL, not headers). Another option: a `X-API-Version: 2` custom header. Both are second choices to URL versioning — but if URL is truly frozen, header versioning is the right call.

---

## 🧾 TL;DR — One Interviewer-Ready Line

> *"A well-designed API is a stable contract — I use REST conventions (noun resources, correct verbs, specific status codes), cursor pagination for any list that might grow large or change in real time, and an idempotency key on all POST operations so retries are safe. Version from day one, even if v2 never ships."*

---

## 🔗 Related Concepts

- **`04-idempotency.md`** — The idempotency key pattern introduced here is fully detailed there. Same concept, deeper dive on the DB table design and Kafka consumer deduplication.
- **`12-data-modeling.md`** — Every API endpoint maps to a schema. The request/response fields must align with the DB columns you design.
- **`02-rate-limiting.md`** — The `429 Too Many Requests` response + `Retry-After` header is the handoff between API design and rate limiting.

---

## 📚 Further Reading (Optional — after the note)

| Resource | What it adds | Time |
|---|---|---|
| **hellointerview.com — API Design** | Full interview walkthrough with grading rubric. URL: https://www.hellointerview.com/learn/system-design/core-concepts/api-design | ~15 min |
| **DocuSign Engineering Blog — Pagination** | Real DocuSign cursor pagination decisions. URL: https://www.docusign.com/blog/developers/the-trenches-api-pagination | ~10 min |
| **Arpit Bhayani — API Versioning** (YouTube) | Breaking vs non-breaking changes, versioning strategies in production. Search: "Arpit Bhayani API versioning" | ~20 min |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 2026 | File created. DocuSign R2 prep — confirmed format "API design OR server-side". Covers REST contract, idempotency key, cursor pagination, versioning. |
| Jul 19, 2026 | **Factual fixes + gaps.** (1) Query-param versioning rationale was backwards ("gets lost in logging" — it isn't); corrected to the real downside (cache fragmentation + mixing versioning with filtering). (2) Fixed the GitHub versioning example — it's media-type/header versioning (`Accept: ...v3+json`) and now date-based (`X-GitHub-Api-Version`), not "URL versioning in headers"; `v4` is GraphQL, not a REST path. (3) Added a REST vs GraphQL vs gRPC comparison + `202 Accepted` async pattern + HATEOAS — all commonly probed and previously absent. |
