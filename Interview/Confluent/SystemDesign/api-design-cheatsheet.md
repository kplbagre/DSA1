# REST API Design Cheatsheet — Confluent Interview

> **Why this exists:** Confluent treats API design as a separate evaluation axis. "If you do any mistake with verbs/response codes/headers, they highlight it as if the world has ended." This is not a footnote — it's the gating factor.
>
> **How to use:** Memorize before the interview. Reference when designing any system's API layer.

---

## 1. HTTP Verbs — When to Use Which

| Verb | Meaning | Idempotent? | Safe? | Request Body? | Example |
|---|---|---|---|---|---|
| **GET** | Read / retrieve a resource | ✅ Yes | ✅ Yes | ❌ No | `GET /api/v1/emails/{id}` |
| **POST** | Create a new resource | ❌ No | ❌ No | ✅ Yes | `POST /api/v1/emails` |
| **PUT** | Full replace of a resource | ✅ Yes | ❌ No | ✅ Yes | `PUT /api/v1/emails/{id}` |
| **PATCH** | Partial update of a resource | ❌ No* | ❌ No | ✅ Yes | `PATCH /api/v1/emails/{id}` |
| **DELETE** | Remove a resource | ✅ Yes | ❌ No | ❌ No | `DELETE /api/v1/emails/{id}` |

> **Idempotent** (calling it twice produces the same result as calling it once) — GET, PUT, DELETE are idempotent. POST is not (two POSTs create two resources). PATCH is technically not idempotent (depends on the patch format), but in practice most implementations are.
>
> **Safe** (does not modify server state) — only GET (and HEAD/OPTIONS) are safe.

### Common Mistakes Confluent Will Catch

| ❌ Wrong | ✅ Right | Why |
|---|---|---|
| `POST /api/v1/emails/{id}` to update | `PUT /api/v1/emails/{id}` or `PATCH` | POST is for creation, not update |
| `GET /api/v1/deleteEmail?id=5` | `DELETE /api/v1/emails/5` | GET must be safe — never mutate on GET |
| `POST /api/v1/emails` returning 200 | Return **201 Created** | 200 means "OK, nothing new." 201 means "new resource created." |
| `PUT /api/v1/emails` (no ID) | `PUT /api/v1/emails/{id}` | PUT targets a specific resource, not a collection |
| `PATCH` to set ALL fields | Use `PUT` for full replace | PATCH is partial. If you're sending the entire object, use PUT. |

---

## 2. HTTP Response Codes — The Full Interview Set

### 2xx — Success

| Code | Name | When to Use |
|---|---|---|
| **200** | OK | Successful GET, PUT, PATCH, DELETE (when returning a body) |
| **201** | Created | Successful POST that created a new resource. Include `Location` header with the new resource URL. |
| **204** | No Content | Successful DELETE (or PUT/PATCH) when there's nothing to return in the body |

### 3xx — Redirection

| Code | Name | When to Use |
|---|---|---|
| **301** | Moved Permanently | Resource URL changed permanently. Client should use new URL going forward. |
| **304** | Not Modified | Client's cached version is still valid (used with `ETag` / `If-None-Match`) |

### 4xx — Client Error

| Code | Name | When to Use |
|---|---|---|
| **400** | Bad Request | Malformed request — invalid JSON, missing required fields, wrong types |
| **401** | Unauthorized | No authentication provided (or invalid token). "Who are you?" |
| **403** | Forbidden | Authenticated but not authorized. "I know who you are, but you can't do this." |
| **404** | Not Found | Resource doesn't exist. `GET /emails/999` when 999 doesn't exist. |
| **405** | Method Not Allowed | Endpoint exists but verb is wrong. `DELETE /api/v1/emails` on a collection. |
| **409** | Conflict | Request conflicts with current state. Creating a resource that already exists. Concurrent edit conflict. |
| **422** | Unprocessable Entity | Well-formed request but semantically invalid. "JSON is valid but email field is not a valid email." |
| **429** | Too Many Requests | Rate limited. Include `Retry-After` header. |

### 5xx — Server Error

| Code | Name | When to Use |
|---|---|---|
| **500** | Internal Server Error | Unexpected server failure. Never expose stack traces to client. |
| **502** | Bad Gateway | Upstream service returned an invalid response (your service is a proxy) |
| **503** | Service Unavailable | Server is overloaded or in maintenance. Include `Retry-After` header. |
| **504** | Gateway Timeout | Upstream service didn't respond in time |

### Decision Flowchart — Which Error Code?

```
  Client sent a request
         │
    Is the request well-formed JSON?
    ├── NO → 400 Bad Request
    │
    Is the client authenticated?
    ├── NO → 401 Unauthorized
    │
    Is the client authorized for this action?
    ├── NO → 403 Forbidden
    │
    Does the resource exist?
    ├── NO → 404 Not Found
    │
    Is the HTTP method allowed on this resource?
    ├── NO → 405 Method Not Allowed
    │
    Are the field values semantically valid?
    ├── NO → 422 Unprocessable Entity
    │
    Does this conflict with current state?
    ├── YES → 409 Conflict
    │
    Is the client rate-limited?
    ├── YES → 429 Too Many Requests
    │
    Did the server succeed?
    ├── YES → 200 / 201 / 204
    └── NO → 500 / 502 / 503 / 504
```

---

## 3. Headers — What to Know

### Request Headers

| Header | Purpose | Example |
|---|---|---|
| `Content-Type` | Format of request body | `application/json` |
| `Accept` | Format client wants in response | `application/json` |
| `Authorization` | Auth credentials | `Bearer eyJhbGci...` |
| `Idempotency-Key` | Safe retry for POST | `Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000` |
| `If-None-Match` | Conditional GET (cache validation) | `If-None-Match: "etag-value"` |
| `If-Match` | Conditional PUT/PATCH (optimistic locking) | `If-Match: "etag-value"` |

### Response Headers

| Header | Purpose | Example |
|---|---|---|
| `Location` | URL of newly created resource (with 201) | `Location: /api/v1/emails/abc123` |
| `ETag` | Version identifier for caching | `ETag: "33a64df5"` |
| `Retry-After` | When to retry (with 429/503) | `Retry-After: 60` |
| `X-Request-Id` | Trace ID for debugging | `X-Request-Id: req-abc-123` |
| `Link` | Pagination links | `Link: </api/v1/emails?cursor=abc>; rel="next"` |
| `X-Total-Count` | Total items (for paginated responses) | `X-Total-Count: 2340` |

---

## 4. URL Design — Resource Naming

### Rules

| Rule | ✅ Right | ❌ Wrong |
|---|---|---|
| Use **nouns**, not verbs | `/api/v1/emails` | `/api/v1/getEmails` |
| Use **plural** for collections | `/api/v1/emails` | `/api/v1/email` |
| Use **hierarchical nesting** for ownership | `/api/v1/emails/{id}/messages` | `/api/v1/messages?emailId=5` |
| Use **kebab-case** | `/api/v1/news-feeds` | `/api/v1/newsFeeds` |
| **Version in path** (simplest) | `/api/v1/emails` | `/api/emails` (no version) |
| Use **query params** for filtering | `/api/v1/emails?status=active` | `/api/v1/active-emails` |

### Nesting Depth — Stop at 2 Levels

```
✅ /api/v1/emails/{emailId}/messages/{messageId}       ← 2 levels, fine
❌ /api/v1/users/{userId}/emails/{emailId}/messages/{messageId}/attachments  ← too deep
```

If nesting gets too deep, promote the sub-resource to a top-level resource:
```
✅ /api/v1/attachments/{attachmentId}?messageId=xyz
```

---

## 5. Pagination — Cursor vs Offset

### Offset-Based (simple, has problems at scale)

```
GET /api/v1/emails?offset=20&limit=10

Response:
{
    "data": [...],
    "pagination": {
        "offset": 20,
        "limit": 10,
        "total": 2340
    }
}
```

**Problem:** If new items are inserted while paginating, items shift — you either skip or duplicate items.

### Cursor-Based (scalable, consistent)

```
GET /api/v1/emails?cursor=eyJpZCI6MTAwfQ&limit=10

Response:
{
    "data": [...],
    "pagination": {
        "next_cursor": "eyJpZCI6MTEwfQ",
        "has_more": true
    }
}
```

**Cursor** = opaque token encoding the last item's sort key (often base64-encoded ID or timestamp). "Give me items after this one."

### When to Use Which

| Use Offset When | Use Cursor When |
|---|---|
| Small datasets (< 10K items) | Large or growing datasets |
| Users need "jump to page 50" | Sequential "load more" / infinite scroll |
| Data rarely changes between requests | Data is frequently inserted/deleted |

**In a Confluent interview:** Default to cursor-based and explain why. Mention offset as the simpler alternative for small datasets.

---

## 6. Error Response Format — Consistent Structure

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

**Rules:**
- Every error response uses the same JSON structure
- `code` = machine-readable string (not the HTTP status code)
- `message` = human-readable explanation
- `details` = optional array for field-level validation errors
- `request_id` = trace ID for debugging (matches `X-Request-Id` header)

**Never expose:** stack traces, internal paths, database errors, or implementation details.

---

## 7. Idempotency — Safe Retries

**Problem:** Client sends `POST /orders`, server processes it, but the response is lost (network timeout). Client retries. Two orders created.

**Solution:** Idempotency key.

```
POST /api/v1/orders
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
Content-Type: application/json

{ "item": "widget", "quantity": 1 }
```

**Server behavior:**
1. First request with this key → process normally, store key + response in cache
2. Second request with same key → return cached response without re-processing

**Which verbs need it:**
- **POST** — always (not idempotent by nature)
- **GET, PUT, DELETE** — already idempotent, don't need it
- **PATCH** — depends on implementation; add if your PATCH has side effects

---

## 8. Versioning — Three Strategies

| Strategy | Example | Pros | Cons |
|---|---|---|---|
| **URL Path** (recommended) | `/api/v1/emails` | Simple, explicit, easy to route | URL changes on version bump |
| **Header** | `Accept: application/vnd.myapi.v1+json` | Clean URLs | Hidden, harder to test in browser |
| **Query Param** | `/api/emails?version=1` | Easy to add | Clutters query string, easy to forget |

**In a Confluent interview:** Use URL path versioning. It's the most common and easiest to explain.

---

## 9. Interview Template — How to Present API Design

When the interviewer says "design the API for X," follow this order:

**Step 1 — Identify resources:**
> "The core resources are: Email, Message, Attachment."

**Step 2 — Define CRUD operations with correct verbs:**

```
POST   /api/v1/emails                    → Create a new disposable email
GET    /api/v1/emails/{id}               → Get email details (address, expiry time)
DELETE /api/v1/emails/{id}               → Delete email before expiry
GET    /api/v1/emails/{id}/messages      → List messages for this email (paginated)
GET    /api/v1/emails/{id}/messages/{mid}→ Get a specific message
```

**Step 3 — Specify request/response bodies:**
```json
POST /api/v1/emails
Request:  { "ttl_minutes": 10 }
Response: 201 Created
          Location: /api/v1/emails/abc123
          { "id": "abc123", "address": "abc123@tempmail.com", "expires_at": "2026-07-30T14:10:00Z" }
```

**Step 4 — Define error cases:**
> "GET on a non-existent email returns 404. POST with invalid TTL returns 422. Rate-limited clients get 429 with Retry-After."

**Step 5 — Discuss pagination:**
> "GET /emails/{id}/messages uses cursor-based pagination because messages are constantly arriving. Response includes `next_cursor` and `has_more`."

**Step 6 — Mention auth, rate limiting, versioning:**
> "All endpoints require `Authorization: Bearer <token>`. Rate limit: 100 requests/min per API key. Version in URL path: `/api/v1/`."

---

## 🧾 TL;DR — 60-Second Revision

- **Verbs:** GET=read, POST=create, PUT=full replace, PATCH=partial update, DELETE=remove
- **201 for POST** (not 200). **204 for DELETE** (no body). **409 for conflicts.** **422 for valid JSON but bad semantics.**
- **Location header** with 201. **Retry-After** with 429/503. **ETag** for caching.
- **Nouns in URLs** (`/emails` not `/getEmails`). Plural. Kebab-case. Max 2 nesting levels.
- **Cursor pagination** for large/dynamic datasets. Offset for small/static.
- **Idempotency-Key header** for POST operations.
- **Consistent error format:** `{ "error": { "code": "...", "message": "...", "request_id": "..." } }`

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Jul 2026 | Created. Covers HTTP verbs, response codes, headers, URL design, pagination, error format, idempotency, versioning, interview template. |
