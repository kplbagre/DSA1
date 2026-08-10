# Design a Disposable / Temporary Email Service (TempMail)

> **Interview Type:** Type 2 — Full System Design
> **Frequency:** ⭐ Most repeated Confluent question (3+ reports — Jul 2025, May 2025, May 2026)
> **Bloom filter warning:** One round (May 2026, Hack2Hire) had the Bloom filter discussion consume most of the session. Section 8 Deep Dive #1 prepares you for that.
> **Standards file:** `solution-notes-standards.md`

---

## 🎯 What Is This System?

**In plain English:** A service that generates throwaway email addresses on demand. Each address lives for a short window (5–60 minutes), receives any email sent to it, and is then deleted with all its messages. No registration, no identity, no long-term storage.

**Real-world examples:**

| System / Company | What they built |
|---|---|
| **Mailinator** | Public disposable email since 2003; any address @mailinator.com works instantly |
| **Guerrilla Mail** | User-generated temp addresses; 1-hour default TTL |
| **10 Minute Mail** | Exactly 10-minute TTL; one of the most-cited in interview reports |
| **Temp-Mail.org** | Commercial API + consumer product; 24-hour TTL |
| **Apple Hide My Email** | Permanent forwarding aliases, not TTL-based — a contrast worth knowing |

**Core user journey:** User visits the site, gets an email address like `f4k2s9@tempmail.io`, pastes it into a signup form, waits for the confirmation email to appear in the inbox, clicks the link, closes the tab — address auto-deletes in 10 minutes.

**Why it's hard to build at scale:** The system must receive arbitrary SMTP traffic from the entire internet (no allowlist), match it against millions of ephemeral addresses in milliseconds, and garbage-collect those addresses and their messages atomically when TTL expires — while maintaining zero false deliveries to expired addresses.

**Tableflow parallel:** TTL-based data expiration = Kafka topic retention (messages deleted when retention.ms expires) + Iceberg snapshot expiry (table snapshots purged on a schedule). The address lifecycle is exactly how Kafka manages ephemeral log segments.

---

## 🚀 Section 1 — The One-Sentence Opener

> "Before I start, let me ask a few clarifying questions to make sure I'm solving the right version of this problem — TTL policies and SMTP ingress scope will significantly shape the architecture."

Then immediately Section 2. Never draw a box before the scope is set.

---

## 🔍 Section 2 — Clarifying Questions Script (Minutes 0–5)

**Q: "What's the TTL on an address — fixed or user-configurable?"**
- Why ask: fixed TTL = simple timer; configurable = extension APIs, billing tiers, longer storage
- If fixed (10 minutes) → hard-coded TTL, simple sweeper, no extension endpoint needed
- If configurable (1 hour, 24 hours, custom) → need TTL field in DB, extension API, and tiered retention costs

**Q: "Do we support custom domains, or only our own @tempmail.io?"**
- Why ask: custom domains = DNS ownership verification, multi-tenant MX routing, domain-specific storage partitioning
- If our domain only → one MX record (a DNS record that tells sending mail servers which IP accepts email for our domain), one SMTP server, simpler routing
- If custom domains → need domain registration flow, per-domain MX, significantly more complex

**Q: "Receive-only inbox, or do users need to reply or forward?"**
- Why ask: SMTP ingress only = no SMTP egress, no SPF/DKIM signing for outbound; reply = we need a full MTA with auth
- If receive-only → skip outbound entirely; simpler infra
- If reply/forward → need outbound SMTP, SPF record, DKIM signing, abuse controls

**Q: "What scale — DAU and peak email delivery rate?"**
- Why ask: shapes whether Postgres can absorb writes directly or we need Kafka buffering between SMTP ingress and storage
- If ~1M DAU → single Postgres handles it
- If ~100M DAU → need Kafka + async processing between receipt and storage

**Q: "Any requirement for real-time push (SSE (Server-Sent Events — a one-way HTTP push channel where the server streams events to the browser over a persistent connection, eliminating polling)/webhook) when email arrives, or is polling acceptable?"**
- Why ask: polling = simple; push = need event bus (Kafka or SSE server) + connection state
- If polling → `GET /v1/addresses/{id}/messages` on a timer
- If push → Server-Sent Events channel or WebSocket, adds stateful connection management

**Assume for this session:** Fixed 10-minute TTL, our domain only, receive-only, ~10M DAU, polling acceptable. Interviewer can vary any axis.

---

## 📋 Section 3 — Requirements

**Functional Requirements:**
- Users can generate a new temp email address (system-assigned, random)
- System receives any email sent to an active address via SMTP
- Users can list and read messages in their inbox
- Address (and all its messages) auto-deletes after 10 minutes from creation
- Users can manually delete their address early
- Out of scope: custom domains, reply/forward, attachments > 5MB, long-term archiving

**Non-Functional Requirements:**
- Scale: 10M DAU; ~116 new addresses/sec **average** (~350/sec peak); ~347 incoming messages/sec **average** (~1,040/sec peak); ~1,157 inbox reads/sec **average** (~3,500/sec peak). Section 4 derives all six; peaks are 3× average. Quote the peak when sizing, the average when costing.
- Latency: Address creation P99 < 200ms; inbox poll P99 < 100ms; SMTP acceptance P99 < 500ms — where "acceptance" means *our* work inside the session (`RCPT TO` lookup + Kafka publish + `250 OK`), **not** the wall-clock session length. A session can last ~500ms end-to-end and still meet this budget, because most of that wall clock is round trips to the remote sender, which we do not control
- Availability: 99.9% SLO for address generation + inbox reads (SMTP ingress can briefly queue)
- Consistency: Eventual for inbox (message appears within 2 seconds of SMTP acceptance)
- Durability: No durability requirement — TTL-expired data is intentionally deleted; in-window messages must not be lost
- TTL atomicity: When address expires, ALL its messages must be unreachable within 1 TTL window (no stale reads after expiry)

---

## 🗂️ Section 3.5 — Core Entities

| Entity | What it represents |
|---|---|
| **EmailAddress** | Ephemeral — the temp address itself; exists only for TTL duration; identified by UUID + address string |
| **Message** | Append-only — an email received at an active address; immutable after receipt; deleted when parent address expires |
| **BloomFilter** | Centralized in Redis, read by every SMTP ingress instance — probabilistic index of active addresses; never persisted as a row. See the verdict box in Section 8 Deep Dive 1: at 10M DAU an exact Redis SET is the better call; the filter is the 100M+ DAU answer |
| **SMTPSession** | Ephemeral — the in-flight connection state while receiving an email; not stored, discarded after processing |

### 🎨 Visual — Entity Relationships

```
┌──────────────────────────────────────────────┐
│                  addresses                   │
│──────────────────────────────────────────────│
│ id  PK                                       │
│ address  UNIQUE  (e.g. f4k2s9@tempmail.io)   │
│ expires_at  ← indexed; TTL sweeper scans here│
│ created_at                                   │
│ created_ip  ← indexed for per-IP rate limit  │
└────────────────────┬─────────────────────────┘
                     │ 1
                     │ ON DELETE CASCADE ← correctness guarantee
                     N
┌────────────────────▼─────────────────────────┐
│                   messages                   │
│──────────────────────────────────────────────│
│ id  PK                                       │
│ address_id  FK                               │
│ from_address                                 │
│ subject                                      │
│ body_text / body_html                        │
│ received_at  ← indexed (address_id, DESC)    │
└──────────────────────────────────────────────┘

NOTE: BloomFilter and SMTPSession are NOT persisted as rows.
  BloomFilter  → bit array in Redis (250KB); rebuilt every 5 min
  SMTPSession  → in-memory connection state; discarded after DATA ack

KEY INVARIANT:
  DELETE FROM addresses WHERE expires_at < NOW()
  Postgres CASCADE fires → ALL messages for that address deleted atomically.
  No orphaned messages can exist. No application-level cleanup loop needed.
  The CASCADE IS the TTL atomicity guarantee.
```

---

## 🔢 Section 4 — Scale Estimation (Type 2)

**Traffic:**
- DAU: 10M
- Address creation: 10M/day ÷ 86,400 ≈ **116/sec** (peak 3× = ~350/sec)
- Emails received: assume 3 emails per session = 30M/day ÷ 86,400 ≈ **347/sec** (peak 3× = ~1,040/sec)
- Inbox polls: assume 10 polls per session = 100M/day ÷ 86,400 ≈ **1,157/sec** (peak 3× = ~3,500/sec)

**Storage:**
- Active addresses at any time: 350/sec × 600s (10-min TTL) = **210,000 live addresses**
- Per address row: ~200 bytes → 210,000 × 200B = ~42 MB (trivially small)
- Per message: avg email body ~10KB → 1,040 msg/sec × 10KB = ~10MB/sec write throughput
- Messages are deleted at TTL (10 min window maximum): max live message storage = 10MB/sec × 600s = **~6GB at peak**
- After TTL sweep: net storage growth = ~0 (ephemeral system)

**Key conclusions:**
- At 3,500 read/sec + 1,040 write/sec, a single Postgres with connection pooling handles reads fine (~10K read/sec capacity) but SMTP write path must not block under burst — justifies Kafka buffering in Section 7.
- 210K active addresses easily fits in Redis. Two footprints, do not confuse them: **42MB is the Postgres row footprint** (210K × 200B, including `expires_at`/`created_at`/`created_ip`); a Redis SET holding only the address *strings* is **~10MB**. Section 8 Deep Dive 1 compares that ~10MB exact set against a 250KB Bloom filter.
- 6GB live message storage fits on a single large Postgres instance — no sharding required at 10M DAU. That 6GB is the *logical* live set; with 1,040 inserts/sec plus 1,040 CASCADE deletes/sec of MVCC dead tuples awaiting autovacuum, plus indexes, budget **2–3× that on disk** (~15–20GB) and size the volume accordingly.

---

## 🔄 Section 5 — Requirements Variation Table ⭐

| If the interviewer says... | Your architecture changes to... | The reasoning |
|---|---|---|
| "100K DAU, not 10M" | Single Postgres, no Kafka, no Bloom filter — direct SMTP → DB write | At 1.2 addr/sec + 3.5 msg/sec, a single Postgres handles everything; Bloom filter overhead not worth it |
| "100M DAU" | Kafka + async message processors (fan-out); Cassandra for messages; Redis Cluster for Bloom filter | 10× Section 4 = **~10,400 msg/sec peak** (not 3,500 — that is the 10M-DAU *inbox-poll* rate). That is close to the ~15K msg/sec Stage 2 ceiling, so the single-primary WAL and the 450K-row CASCADE sweep both give out; need async buffering + wide-column storage with per-row TTL. This is also where the Bloom filter finally beats an exact set |
| "Multi-region (US + EU + APAC)" | Regional SMTP ingress; address table replicated via Kafka compacted topic; Bloom filter per region synced via changelog | SMTP is latency-sensitive — regional ingress avoids cross-ocean hops; address lookup must be local-region-fast |
| "1-hour TTL instead of 10 min" | Same architecture, increase TTL sweeper interval; active address count grows 6× — 210,000 × 6 = **~1.26M active ≈ 1.5MB** of Bloom filter (or ~60MB as an exact Redis SET) — still trivial | Linear scaling on active address count; no architectural change |
| "User can extend their address TTL" | Add `PATCH /v1/addresses/{id}` endpoint; TTL becomes a mutable field; sweeper must re-read before deleting | Introduces write-after-read race condition at expiry — need atomic TTL extension (CAS update) |
| "Attachments up to 100MB" | Message body offloaded to S3; DB stores S3 key, not body; SMTP ingress streams to S3 during SMTP session | Size the *worst case*, not the mean: 100MB is the cap, not the average email, so 100MB × 1,040 msg/sec = 100GB/sec is the ceiling if every sender maxed out, not a forecast. State it that way before the interviewer does. Even a realistic 1% of messages at 100MB is ~1GB/sec — still far beyond what Postgres `TEXT` columns or an in-memory MIME buffer can hold, which is what actually forces the S3 offload |
| "Real-time push when email arrives" | SSE endpoint per address; SMTP ingress publishes to Kafka; SSE server consumes and pushes to connected clients | Polling at 3,500/sec generates unnecessary DB reads; SSE cuts read load by ~90% on active sessions |
| "Must block spam senders" | Add RBL (real-time blacklist) lookup at SMTP `MAIL FROM`; rate-limit per sending IP | Spam is the #1 abuse vector for disposable email; the RBL DNS lookup adds ~10ms to `MAIL FROM` — the same stage where the check runs. Rejecting at `MAIL FROM` beats `HELO` because the envelope sender is what the blacklist is keyed on |

---

## ⭐ Section 6 — API Design ← CONFLUENT'S PRIMARY EVALUATION AXIS

> **MANDATORY: API defined before HLD.** The architecture implements this contract — not the other way around.
> HTTP verb/code/header rules: **`api-design-cheatsheet.md`**

### 🧠 Part 1 — Derivation Framework

Every endpoint starts from a functional requirement: **FR → operation → resource → HTTP method → contract.**

**"Users can generate a new temp email address"** → create operation → resource is an `address` → `POST /v1/addresses`.
Who calls it? Anonymous user (no auth required — that's the point of disposable email). Minimum payload? None — the system assigns the address. What do they get back? The address string + expiry timestamp + an ID to use for subsequent calls. Why return `expires_at`? Client can show a countdown timer without polling.

**"Users can list messages in their inbox"** → read collection operation → resource is `messages` under `addresses` → `GET /v1/addresses/{id}/messages`.
Pagination strategy: **cursor-based, not offset**. Why? With TTL-based deletions running concurrently, offset pagination drifts — rows disappear mid-read, causing duplicates or skips. Cursor on `received_at` (stable order, never mutates) gives consistent pages regardless of concurrent deletes. This is a probe-level decision — Confluent will ask.

**"Address auto-deletes after TTL"** → the system deletes, not the user — no endpoint for expiry. But the status code on a read after expiry is non-obvious: `410 Gone` (not `404`). Why? `404` means "never existed or you have the wrong ID." `410` means "existed, is permanently gone." Precise semantics let clients distinguish "I have a bad ID" from "my address expired — I need a new one."

**Validation check:** Every FR maps to an endpoint. No orphan endpoints.

---

### Part 2 — Core Endpoints

| Method | Path | Auth | Request Body | Response | Status Codes |
|---|---|---|---|---|---|
| `POST` | `/v1/addresses` | None | `{ "ttl_seconds": 600 }` (optional override) | `{ "id", "address", "expires_at" }` | 201, 429, 503 |
| `GET` | `/v1/addresses/{id}` | None | — | `{ "id", "address", "expires_at", "message_count" }` | 200, 404, 410 |
| `GET` | `/v1/addresses/{id}/messages` | None | — | `{ "messages": [...], "next_cursor" }` | 200, 404, 410, 400 |
| `GET` | `/v1/addresses/{id}/messages/{messageId}` | None | — | `{ "id", "from", "subject", "body_text", "body_html", "received_at" }` | 200, 404, 410 |
| `DELETE` | `/v1/addresses/{id}` | None | — | — | 204, 404, 410 |

> **Budget note on `message_count`.** Section 9 has no counter column, so this field costs a **second query** — `SELECT COUNT(*) FROM messages WHERE address_id = $1` — on top of the address row lookup. Cheap but not free: it is served index-only from `idx_messages_cursor`, and a live inbox holds ~3 rows, so ~0.2ms. Say the number yourself rather than letting an interviewer find an unpriced query on a carefully-costed path. The alternative — a denormalized `message_count` column on `addresses` — trades that read for a row UPDATE per message insert (write contention on the hot address row), which is the worse deal at 3 messages per inbox.

---

### 🪪 Part 2.5 — The Two Identifiers (answer this before anyone asks)

Every endpoint path is `/v1/addresses/{id}`, but `POST /v1/addresses` sends no ID. So where does `{id}` come from, and why isn't it just the email address? This system deliberately carries **two** identifiers for one row, and knowing why is the security story of the whole design.

| Field | Example | Who uses it | Visibility |
|---|---|---|---|
| **`id`** | `3f8ac912-…` (UUID v4) | REST API paths — `GET /v1/addresses/{id}` | **Secret.** Returned by the creation call; the client must persist it, because it cannot be recovered from the address. |
| **`address`** | `f4k2s9@tempmail.io` | SMTP `RCPT TO` routing | **Public.** The user pastes it into signup forms. |

**Where `{id}` comes from:** the server mints it; the client never invents it. `POST /v1/addresses` responds `201` with both the `Location: /v1/addresses/{id}` header and the `id` in the body. The client stores it and uses it for every later call. It is `gen_random_uuid()` at INSERT time (Section 9).

**How the client carries the UUID on every GET call:** the UUID is a **URL path parameter** — it is baked into the URL itself, not sent in a header or cookie. There is no `Authorization` header. The URL IS the credential (W3C calls this a **capability URL** — used by Google Docs "anyone with link", GitHub Gist secret links, Dropbox shared links).

```
GET /v1/addresses/3f8ac912-ab22-4f10-b3d0-9c8e1f2a7d44/messages
```

Where each client type stores the UUID between the POST and the next GET:

| Client | Storage location |
|---|---|
| Browser web app | `localStorage.setItem('tmpmail_id', uuid)` — survives tab close, and persists until explicitly overwritten on a `410`. `localStorage` has **no TTL** and no expiry mechanism, so nothing removes the dead UUID; it lingers until the client overwrites it (or calls `removeItem`) |
| Mobile app | `SharedPreferences` (Android) / `UserDefaults` (iOS) |
| CLI / curl script | Shell variable or `~/.tmpmail_session` temp file |
| API client (Postman) | Environment variable |

The browser flow in practice. Note the two things a sketch usually gets wrong: `fetch` resolves to a `Response`, not to the parsed body, and it does **not** throw on `410` — so the expiry branch has to be written explicitly, and it is the single most important client behaviour in this section.

**Steps in plain English:**

1. **Store both identifiers on create** — the UUID is the secret credential, the address string is what the user sees.
2. **Poll with the stored UUID** in the path; there is no `Authorization` header.
3. **Branch on `410` before parsing.** A `410` means the inbox expired — mint a new address and overwrite both keys. Nothing else clears `localStorage`.
4. **Only then parse JSON**, because only a `200` has a message list in its body.

```js
// Step 1 — on the POST /v1/addresses response, persist both identifiers
const created = await fetch('/v1/addresses', { method: 'POST' });
const { id, address } = await created.json();
// secret — never render this to the user
localStorage.setItem('tmpmail_id', id);
// public — this is what the user copies into signup forms
localStorage.setItem('tmpmail_addr', address);

// Polling loop, called every 5s
async function pollInbox() {
    // Step 2 — the UUID in the path IS the credential
    const currentId = localStorage.getItem('tmpmail_id');
    const resp = await fetch(`/v1/addresses/${currentId}/messages`);

    // Step 3 — fetch does NOT throw on 410; branch on it explicitly
    if (resp.status === 410) {
        localStorage.removeItem('tmpmail_id');
        localStorage.removeItem('tmpmail_addr');
        // mint a fresh address and store the new pair
        await createNewAddress();
        return [];
    }

    if (!resp.ok) {
        throw new Error(`inbox poll failed: ${resp.status}`);
    }

    // Step 4 — only a 200 carries a message list
    const body = await resp.json();
    return body.messages;
}
```

> **Say this in the interview if asked:** "There is no Authorization header — the unguessable UUID in the path is the bearer token. Anyone who extracts it from localStorage or sees it in a network tab can read the inbox. That's acceptable here because the lifetime is 10 minutes and there is no PII — the risk window is tiny."

**Why not key the API on the email address instead?** Because every endpoint here has **Auth: None** — that is the product (no signup, no identity). With no auth, the UUID *is* the credential: a bearer capability that grants inbox access. The address, by design, gets handed to strangers. If the path were `GET /v1/addresses/f4k2s9@tempmail.io`, then anyone who knows your throwaway address — including the very site you just gave it to — could read your inbox and steal the confirmation link. The split is what makes an unauthenticated inbox safe:

> **Say this in the interview:** "The address is public by function and the UUID is secret by function, so they cannot be the same field. Knowing the address must not grant read access to the inbox."

Two consequences to state before being probed:
- The UUID must be **unguessable**, so `gen_random_uuid()` (CSPRNG-backed) — never a sequential ID, or `/v1/addresses/1001` walks every live inbox.
- The UUID must never appear in a URL that leaves the client. It is in the path, so it lands in access logs and `Referer` headers — acceptable here given a 10-minute lifetime, but worth naming as the residual risk.

**How the address string is generated:** 6 characters from the **31-character** alphabet `abcdefghjkmnpqrstuvwxyz23456789` — 23 letters (a–z minus `i`, `l`, `o`) plus 8 digits (2–9) — drawn from `SecureRandom`. That is 31⁶ = **887,503,681 ≈ 887M** combinations. Against 210K live addresses (Section 4), the chance any single generated code collides with a live one is 210,000 ÷ 887M ≈ **0.024%** — so the retry loop below effectively never runs twice.

> **Count the alphabet before you quote it.** Calling this "base32 minus look-alikes" is self-refuting: base32 is *by definition* 32 symbols, so removing any leaves fewer than 32. Either quote 31⁶ ≈ 887M (what the code below actually does) or add a 32nd character and quote 32⁶ ≈ 1.07B. The conclusion is unchanged either way — the collision rate stays ~0.02% — but an interviewer who counts the string and finds 31 will discount every other number in your answer.
>
> **And be honest about *why* the look-alikes are gone.** Nothing here is ever transcribed by hand: the server generates the code and the client copies and pastes it. The retained set still contains `2`/`z`, `5`/`s`, `6`/`b` and `9`/`q`, so it is not look-alike-free on its own terms anyway. The real rationale is modest and worth stating as such: dropping the worst offenders costs nothing and helps the one user in a thousand who reads the address aloud or retypes it from a screenshot.

**Steps in plain English:**

1. **Enforce the per-IP rate limit first**, before minting anything. Part 3 promises `429` above 10 addresses/minute from one IP, and Section 9 builds `idx_addresses_ip_created` for exactly this query — so the check has to actually appear in the code, or `callerIp` is a column we write and never read.
2. **Generate** a random 6-char code from the alphabet using `SecureRandom`.
3. **Reject re-mints of recently-expired codes** by consulting the `recently_used_codes` guard (see the reuse hazard below) — a hit means loop and mint again.
4. **Attempt the INSERT** directly — do not SELECT first. A check-then-insert is a TOCTOU race (Section 13, Mistake 2).
5. **Let the UNIQUE index arbitrate.** On constraint violation, generate a fresh code and retry.
6. **Cap the retries** so a mass-collision bug surfaces as a 503 rather than an infinite loop.

```java
private static final String ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";
private static final int CODE_LENGTH = 6;
private static final int MAX_ATTEMPTS = 3;
private static final int MAX_PER_IP_PER_MINUTE = 10;

public Address createAddress(Duration ttl, InetAddress callerIp) {
    // Step 1 — per-IP rate limit, served by idx_addresses_ip_created
    int recentFromIp = addressRepo.countCreatedSince(
        callerIp,
        Instant.now().minus(Duration.ofMinutes(1))
    );
    if (recentFromIp >= MAX_PER_IP_PER_MINUTE) {
        throw new TooManyRequestsException("address creation rate exceeded");
    }

    for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
        // Step 2 — random code from the 31-character alphabet
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(ALPHABET.charAt(secureRandom.nextInt(ALPHABET.length())));
        }
        String email = code + "@tempmail.io";

        // Step 3 — refuse to re-mint a code that expired within the guard
        // window, or in-flight mail for the previous owner lands here
        if (recentlyUsedCodes.contains(code.toString())) {
            continue;
        }

        try {
            // Step 4 — insert blind; the DB owns uniqueness, not the app
            // id is assigned by gen_random_uuid() and returned to the caller
            return addressRepo.insert(email, Instant.now().plus(ttl), callerIp);
        } catch (DuplicateKeyException e) {
            // Step 5 — collision: loop and mint a different code
        }
    }
    // Step 6 — 3 collisions at a 0.02% rate means something is broken
    throw new ServiceUnavailableException("address space exhausted");
}
```

> **Reuse hazard — raise this yourself, it scores well.** Because expired rows are deleted, a code like `f4k2s9` can be re-minted for a *different* user later. Mail still in flight for the previous owner would then land in the new owner's inbox — a cross-user data leak with no error anywhere. The fix is cheap and worth naming: keep a `recently_used_codes` set in Redis with a TTL of one or two TTL windows, and reject any generated code that appears in it (Step 3 above).
>
> **Do not call the odds "tiny" — multiply by the rate.** Per creation the collision probability is ~0.02%, which sounds negligible, but this is a *rate*, not a one-off event. At the **average** 116 creations/sec: 116 × 86,400 × (210,000 ÷ 887M) ≈ **~2,370 re-mints/day**. At the **peak** 350/sec the instantaneous rate is ~3× that, so a peak-heavy day lands in the **5,000–7,000/day** range. (Quote one convention or the other — the older phrasing multiplied the 120/sec *average* against a 210K live-address count that is itself derived from the 350/sec *peak*, mixing both in one expression.) Every one of those is a potential silent cross-user inbox leak. That is not "tiny"; it is a paging-worthy incident class, and saying so **strengthens** the recommendation rather than weakening it — the guard set is not optional polish, it is load-bearing.
>
> **Notice what that guard set actually is.** `recently_used_codes` holds ~210K–420K **exact** entries with a TTL — which is precisely the data structure that makes the Bloom filter unnecessary at this scale (Section 8 Deep Dive 1). As drawn, the design pays for *both* an approximate address index and an exact recent-code index and reconciles neither. One exact keyspace does both jobs: `SET addr:<code> <state> EX <ttl>` serves the `RCPT TO` existence check **and** the reuse guard, with `<state>` distinguishing `live` from `recently-expired`. Collapsing the two is the cheapest simplification available in this design.
>
> This is the same "delete-then-reuse" hazard the Bloom filter rebuild has (Deep Dive 1), applied to the address namespace.

---

### 🔍 Part 3 — Endpoint Stories

**`POST /v1/addresses`** — Creates a new ephemeral address and starts the TTL clock. Non-obvious: the system assigns *both* identifiers — the `address` string and the `id` UUID (see Part 2.5) — and the client can choose neither. Server-assigned address strings prevent squatting attacks where a bad actor reserves `paypal-verify@tempmail.io`; the server-assigned UUID is what protects the inbox in the absence of auth. Returns 201 with the `id` in the body and a `Location: /v1/addresses/{id}` header so the client knows where to poll. Returns 429 if the requesting IP has exceeded address creation rate (e.g., >10 addresses/minute from one IP) — named trigger: IP-based rate limit bucket exhausted. Returns 503 during SMTP infrastructure maintenance windows, or if 3 consecutive short-code collisions occur (a signal that the code space or the generator is broken).

**`GET /v1/addresses/{id}`** — Checks whether an address is still alive. 200 = active. 404 = ID never existed in the system (client has a corrupt or fabricated ID). **410 = address existed but TTL expired** — this is the precise distinction Confluent will probe. A client that gets 410 knows to generate a new address, not retry the same ID. `message_count` in the response body lets the client know whether to bother fetching messages.

**`GET /v1/addresses/{id}/messages`** — Lists inbox with cursor pagination. `?cursor=` is a base64-encoded `received_at` + `message_id` tuple — opaque to the client, but server can decompose it into a `WHERE received_at < :cursor_time AND id < :cursor_id` query. Returns 410 (not 404) if the address has expired, because the address DID exist — this lets clients show "your inbox expired" vs "invalid address." Returns 400 if the cursor value is malformed (unparseable base64 or out-of-range timestamp) — named trigger: cursor decode fails validation.

**`GET /v1/addresses/{id}/messages/{messageId}`** — Fetches full message body. Non-obvious: both the address AND the message can independently be "not found." If the address expired (410) and the message table cascade-deleted the messages, the 410 on the address ID takes precedence — return 410, not 404 for the message. This ordering matters: a 404 on a message inside a 410-dead address would confuse clients.

**`DELETE /v1/addresses/{id}`** — Early manual deletion. Returns 204 (no body on success — nothing to return). Returns 410 if the address already expired before the client hit delete — the deletion already happened via TTL sweeper. Client should treat 410 as success (idempotent intent: "I want this gone" = it's gone). Returns 404 only if the ID literally never existed.

---

## 🏗️ Section 7 — High-Level Architecture (Type 2, Minutes 18–30)

> **Delivery note — build it up, don't draw the finished thing.** Start with the simplest system that actually accepts an email and shows it in an inbox (Stage 1), then let the Section 4 numbers — 1,040 msg/sec peak, 3,500 inbox polls/sec, 210K live addresses — force each next stage. Do not open with Kafka: a Confluent interviewer knows exactly what Kafka costs and will ask why it is there. Earn it with a number.

---

### 🧠 SMTP Session Mental Model (read once, internalize forever)

An SMTP session is a TCP conversation between the sending server (Gmail, Outlook) and yours:

```
Sender → EHLO gmail.com          ← introduce yourself
Sender → MAIL FROM: <a@gmail.com>
Sender → RCPT TO: <f4k2s9@tempmail.io>
Sender → DATA
Sender → Subject: Confirm...
Sender → .                        ← end of email body
  ← 250 Message accepted          ← YOU send this
[TCP closes]
```

**The rule that drives everything:** `250 OK` is a **durability promise**. Once you send it, the sender stops retrying — forever. If you crash after sending 250 but before writing to DB, the email is silently lost. No bounce, no retry.

**Consequence:** the SMTP session must stay open until the message is durably stored. Session duration = time to write to DB. That is exactly the resource Stage 1 exhausts.

---

### Stage 1 — SMTP Writes Straight to Postgres (handles ~1M DAU)

> One SMTP listener, one Postgres, no Kafka, no Bloom filter. The SMTP session does the address lookup and the message insert inline, then returns 250 OK. This is a correct, working disposable-email service.

```
── Stage 1: Synchronous SMTP → Postgres ───────────────────────────

  [Browser]                      [External Mail Server]
      │ POST /v1/addresses            │ SMTP port 25
      ▼                               ▼
 ┌──────────────┐          ┌────────────────────────────────┐
 │ API Gateway  │          │   SMTP Ingress (1 process)      │
 └──────┬───────┘          │─────────────────────────────────│
        ▼                  │ RCPT TO → SELECT addresses      │
 ┌──────────────┐          │   WHERE address=? AND           │
 │Address Svc   │          │         expires_at > NOW()      │
 └──────┬───────┘          │ DATA   → parse MIME inline      │
        │ INSERT           │        → INSERT messages        │
        │                  │ then reply 250 OK               │
        ▼                  └───────────────┬────────────────┘
 ┌────────────────────────────────────────▼────────────────┐
 │              Postgres (single primary)                   │
 │   addresses  ·  messages (ON DELETE CASCADE)             │
 │   INDEX addresses (expires_at)  ← for the sweeper        │
 └──────────────────────────────────────────────────────────┘
              ▲
              │ every 30s: DELETE FROM addresses
              │            WHERE expires_at < NOW()
     ┌────────┴────────┐
     │  TTL Sweeper    │
     └─────────────────┘

At 1M DAU: ~104 msg/sec peak, ~350 inbox polls/sec, 21K live
addresses. Each SMTP session lasts ~500ms wall clock (remote-sender
round trips + MIME parse), so 104 × 0.5s ≈ 52 concurrent sessions —
inside a 200-connection Postgres pool even if we naively pin one
connection for the whole session, which reason (b) below shows we
should not be doing in the first place.

BREAKING POINT: Stage 1's real ceiling is DURABILITY, not throughput.
  Order the two reasons by which one actually forces Stage 2:
   (a) PRIMARY — no durable landing zone. `250 OK` is an irrevocable
       promise: the sender stops retrying forever. Die between replying
       250 and committing and the email is gone with no bounce and no
       retry — silent, unrecoverable loss. Worse, a MIME parse bug on
       one malformed email throws inside the SMTP listener and kills
       sessions for every other sender. Neither is a tuning problem;
       both are structural, and no scale number makes them go away.
   (b) SECONDARY — connection-pool exhaustion, but only in the NAIVE
       implementation. Pin a pool connection across the whole SMTP
       conversation and concurrency = rate × session length, so a
       200-connection pool caps at 200 ÷ 0.5s = ~400 msg/sec. But that
       ~500ms is remote-sender round trips + MIME parse — time spent
       waiting on the network PEER, not on Postgres. A pool is meant to
       be checked out around DB work only: release the connection
       between the RCPT TO lookup and the insert and Postgres holds it
       ~15ms (Section 10, Trade-off 1), giving 200 ÷ 0.015s ≈ 12,500
       msg/sec — 12× the 10M DAU peak. So connections do NOT justify
       Kafka; do not rest the case here or an interviewer takes it
       apart. Note also that the ~500ms session is a wall-clock MEAN;
       it is not the "SMTP acceptance P99 < 500ms" NFR, which budgets
       only our own work inside the session (Section 3).
  Observable symptom: user reports of "the email never arrived" with no
  error anywhere in the logs (the durability failure); plus, in the
  naive build only, SMTP 421 with pg_stat_activity at max_connections.
  Why Stage 2 is needed: the accepted bytes must land somewhere
  REPLAYABLE before we say 250, and MIME parsing must be quarantined
  off the listener thread.
  THE CHEAPER ALTERNATIVE — name it before the interviewer does. A
  local disk spool (write raw bytes to a maildir, fsync, reply 250,
  process from disk) satisfies BOTH durability and parser quarantine at
  a fraction of Kafka's cost, and is what Postfix has always done.
  Kafka earns the difference only when you also want replay across a
  stateless fleet, consumer-group parallelism, and no per-host disk to
  babysit or drain before a node is retired. State that trade rather
  than pretending a spool would not work — especially at Confluent.

══════════════════════════════════════════════════════════════════
```

---

### Stage 2 — Kafka Buffer + Redis Bloom Filter (10M DAU — our target)

> SMTP ingress does one cheap thing: check the Bloom filter, publish the raw bytes to Kafka, reply 250. Everything expensive moves off the session. This is the design for the stated 10M DAU requirement.

### 🎨 Visual — System Data Flow

```
── Stage 2: Kafka-Decoupled SMTP + Bloom Pre-filter ───────────────

  USER FLOW (HTTP)                    EMAIL FLOW (SMTP)
  ════════════════                    ═════════════════

 ┌──────────────┐                   ┌──────────────────────┐
 │   Browser    │                   │ External Mail Servers │
 └──────┬───────┘                   └───────────┬──────────┘
        │ POST /v1/addresses                    │ port 25
        │ GET  /v1/addresses/{id}/messages      │
        ▼                                       ▼
 ┌──────────────┐                   ┌──────────────────────┐
 │ API Gateway  │                   │   LB (TCP, port 25)  │
 └──────┬───────┘                   └───────────┬──────────┘
        ▼                            ┌──────────┼──────────┐
 ┌──────────────────┐                ▼          ▼          ▼
 │  Address Service │           ┌────────┐ ┌────────┐ ┌────────┐
 │  (N stateless)   │           │ SMTP   │ │ SMTP   │ │ SMTP   │
 │──────────────────│           │Ingress │ │Ingress │ │Ingress │
 │ create → INSERT  │           │   1    │ │   2    │ │   N    │
 │ read   → cursor  │           └───┬────┘ └───┬────┘ └───┬────┘
 │          page    │               │ ① RCPT TO: check     │
 └───┬──────────┬───┘               └──────────┼──────────┘
     │          │                              ▼
     │          │ ② ADD on create   ┌──────────────────────┐
     │          └──────────────────▶│  Redis Bloom Filter  │
     │            (write-only)      │  250KB, 210K addrs   │
     │                              │  rebuilt every 5 min │
     │                              └──────────┬───────────┘
     │                        ③ NO → SMTP 550  │  MAYBE
     │                           (99% of spam, │  exists
     │                            zero DB I/O) ▼
     │                              ┌──────────────────────┐
     │                       ④ confirm in Postgres:        │
     │                         WHERE address=? AND         │
     │                               expires_at > NOW()    │
     │                              └──────────┬───────────┘
     │                          not alive ─────┤ alive
     │                          → SMTP 550     ▼
     │                              ┌──────────────────────┐
     │                              │ Kafka: raw-messages  │
     │                              │ 32 partitions        │
     │                              │ key = address_id     │
     │                              │ ⑤ publish ~5ms       │
     │                              │   → reply 250 OK     │
     │                              └──────────┬───────────┘
     │                                         ▼
     │                              ┌──────────────────────┐
     │                              │ Message Processor    │
     │                              │ (consumer group, 32) │
     │                              │ ⑥ parse MIME         │
     │                              │   enforce size limit │
     │                              └──────────┬───────────┘
     │  ⑦ read path                            │ ⑧ INSERT
     ▼  3,500 polls/sec                        ▼
 ┌──────────────────────────────────────────────────────────┐
 │                Postgres (single primary)                  │
 │   addresses  ──1:N──▶  messages   (ON DELETE CASCADE)     │
 │   INDEX (expires_at) · INDEX (address_id, received_at ▼)  │
 └────────────────────────────┬─────────────────────────────┘
                              ▲
                              │ ⑨ every 30s
                    ┌─────────┴──────────────────────┐
                    │  TTL Sweeper (scheduled job)    │
                    │  DELETE FROM addresses          │
                    │  WHERE expires_at < NOW()       │
                    │    - INTERVAL '30 seconds'      │
                    │  → CASCADE removes messages     │
                    └────────────────────────────────┘

KEY INVARIANT:
   The Bloom filter is a fast NEGATIVE check only, and the arrows are
   deliberately asymmetric: Address Service WRITES it (step ②), SMTP
   Ingress only READS it (step ①). Postgres remains the sole ground
   truth for address existence and expiry (step ④).
   Bloom says NO  → definitively absent → SMTP 550, zero DB I/O.
   Bloom says YES → maybe present → one Postgres read confirms.
   A false negative is impossible WITHIN a single filter generation,
   which is why a NO can be trusted to reject mail outright. A false
   positive costs one wasted read (~1ms) and still ends in a correct 550.

   ⚠ "Within a generation" is load-bearing. The REBUILD SWAP can
   manufacture real false negatives if written naively, and a false
   negative here is not a performance cost — it is total, silent mail
   loss for a live inbox. See Deep Dive 1, "The rebuild swap can
   produce false negatives." Until the swap merges concurrent writes,
   treat this line as a claim about steady state, not a guarantee.
```

**Data flow walkthrough** (numbers match the diagram):

1. **Address creation (②):** Client → API Gateway → Address Service → INSERT into Postgres `addresses` → ADD the address string to the Redis Bloom filter → return 201 with the address and `expires_at`. The Bloom write happens *after* the Postgres commit, never before: a filter entry for an address that failed to insert would cause a pointless confirming read on every future email to it.

2. **Incoming email (① → ⑤):** External server opens SMTP → LB picks any ingress instance (they hold no state) → `RCPT TO: f4k2s9@tempmail.io` → check Redis Bloom **(①)**. If NO → `550 No such user here` immediately, zero DB I/O **(③)**. If MAYBE → confirm in Postgres `WHERE address = ? AND expires_at > NOW()` **(④)**. If alive → accept `DATA`, publish raw bytes to Kafka keyed by `address_id`, reply `250 OK` **(⑤)**. The publish is ~5ms, and — the point that matters — the bytes are **durable before the `250`**, which is what Stage 1 could not promise.

   **One `RCPT TO` is a simplification.** SMTP permits multiple `RCPT TO` commands in a single `DATA` session, so one inbound message can be addressed to two live tempmail addresses at once. The Kafka key is the singular `address_id`, so that message is **published once per recipient** — same raw bytes, different key, landing in different partitions and inserted as two independent rows. That is correct (each inbox owns its own copy, and CASCADE deletes them independently), but it is worth saying out loud, because the diagram and the state machine in Deep Dive 3 both draw only the single-recipient case.

3. **Message processing (⑥ → ⑧):** The 32-member consumer group consumes `raw-messages`, parses MIME, enforces the size limit, and inserts into `messages`. Fully decoupled from the SMTP session — the sender never waits on a DB write, and a MIME parse bug can no longer kill live SMTP sessions.

4. **Inbox read (⑦):** Client polls `GET /v1/addresses/{id}/messages` → Address Service → Postgres with cursor pagination on `(received_at DESC, id DESC)`, served by the single `INDEX (address_id, received_at DESC, id DESC)` — which also serves the plain newest-first list, since that query only uses the index's leading columns (Section 9). This is the 3,500 polls/sec path and it reads the same primary the processors write to — note it, because it is what Stage 3 eventually splits out.

5. **TTL sweep (⑨):** Every 30s, `DELETE FROM addresses WHERE expires_at < NOW() - INTERVAL '30 seconds'` → CASCADE removes every child message atomically. The 30-second grace buffer prevents a race where the sweeper deletes an address microseconds before an in-flight API read completes. The Bloom filter is rebuilt from the surviving active set every 5 minutes (standard Bloom filters cannot delete — see Section 8 Deep Dive 1).

**Each box justified:**
- **Redis Bloom filter** — rejects most SMTP traffic aimed at non-existent addresses without hitting Postgres. **Get the traffic split right:** the 1,040 msg/sec from Section 4 is *legitimate* mail to *live* addresses (3 emails per session), so spam is **additional** traffic on top of it, not a fraction of it. Assume ~4,000/sec of spam to non-existent addresses — a plausible ratio for a public disposable-email domain, and clearly flagged as an assumption rather than a derived number. Those ~4,000/sec are what the filter absorbs. At 10M DAU an **exact Redis SET would do this job better** (Section 8 Deep Dive 1 verdict); the filter is here as the 100M+ DAU answer and because it is the component Confluent probes hardest
- **Kafka** — the durable landing zone that makes `250 OK` an honest promise, and the quarantine boundary that keeps a MIME parse bug off the SMTP listener. Also decouples acceptance from processing and buffers bursts — but note that a local disk spool would satisfy the durability and quarantine goals more cheaply (Stage 1 breaking point); Kafka earns the difference through fleet-wide replay and consumer-group parallelism
- **Message Processor** — separate from SMTP ingress so MIME parsing (CPU) doesn't block SMTP sessions
- **TTL Sweeper** — simpler than per-row expiry triggers; a single scan every 30s handles 10M DAU workload with index on `expires_at`

**Capacity check against Section 4:** 1,040 msg/sec peak × 10KB = ~10MB/sec into Kafka; 210K live addresses = a 250KB Bloom filter (or a ~10MB exact Redis SET) and ~6GB of live message storage.

**The Postgres read budget, derived properly** — this is the number the whole address-index argument turns on, so build it from parts and label the assumption:

| Read source | Reads/sec (peak) | Where it comes from |
|---|---|---|
| Inbox polls | 3,500 | Section 4, derived |
| `RCPT TO` confirms for **legitimate** mail | 1,040 | Section 4, derived — Bloom says MAYBE, one confirming read |
| `RCPT TO` for **spam** to non-existent addresses | ~4,000 | **Assumed**, not derived — see below |
| **Total with no address cache at all** | **~8,540** | ~85% of a single primary's ~10K reads/sec |
| **Total with an address cache (Bloom *or* exact SET)** | **~4,580** | Spam collapses to ~40/sec at 1% FPR; **0/sec** with an exact SET |

Two things to be scrupulous about here. First, **the ~4,000/sec spam figure is an assumption, not arithmetic.** Section 4's 1,040 msg/sec is *legitimate* mail to *live* addresses (3 emails per user session); spam to addresses that never existed is **additional** traffic on top of that, so the older phrasing — "80% of the 1,040 is spam" — double-counted, deriving the filter's value from traffic Section 4 had already allocated to real users. Second, **the conclusion is sensitive to that assumption**: at the double-counted 832/sec the cache would have saved only ~8% of read capacity (comfortable headroom either way, so barely worth building); at an assumed 4,000/sec it saves ~40% and a cache clearly pays for itself. Say which number you are using and that it is an estimate.

But notice what that table does **not** establish: it justifies *a cache*, not *a Bloom filter*. An exact Redis SET eliminates the same ~4,000 reads/sec and eliminates the 40/sec of false-positive reads too. See the verdict box opening Section 8 Deep Dive 1.

Every other component here is sized off a Section 4 number and none is close to its limit — that is the point of stopping at Stage 2 for the stated requirement.

```
WHY KAFKA EARNS ITS PLACE HERE (name this explicitly — it is the probe):
  1. PRIMARY — it is the durable landing zone that fixes Stage 1
     failure (a). Once the bytes are in the topic we can honestly say
     250 OK; a Message Processor crash re-consumes from the last
     committed offset instead of losing the email. `250 OK` is an
     irrevocable promise, so the bytes must be replayable BEFORE it.
  2. PRIMARY — it quarantines the MIME parser. A malformed email now
     poisons one consumer (route it to a DLQ), not the SMTP listener
     that every other sender is talking to.
  3. SECONDARY, and state it carefully — it takes the DB write off the
     session entirely. True, but NOT the justification: a pool checked
     out around only the ~15ms insert already reaches ~12,500 msg/sec,
     12× our peak. Claim durability and quarantine; do NOT claim Kafka
     rescued us from connection exhaustion, because it did not.
  THE HONEST SCOREBOARD vs a local disk spool: the spool also delivers
  (1) and (2), and cheaper. Kafka buys fleet-wide replay, consumer-
  group parallelism, and stateless SMTP hosts with no per-host disk to
  drain before decommissioning. That is the trade — volunteer it.
  Retention is set to the TTL window (10 min ≫ processing lag), so the
  topic is also our replay buffer for the whole life of a message.

BREAKING POINT: Stage 2 breaks at ~15K msg/sec (roughly 100M+ DAU),
  and the two ceilings arrive from opposite directions:
   (a) The WRITE side. 32 Kafka partitions → 32 Message Processor
       instances → 32 concurrent Postgres write sessions at ~450
       inserts/sec each. At ~15K inserts/sec of 10KB bodies (150MB/sec)
       a single primary's WAL fsync I/O saturates. Adding processors
       makes it worse — they all converge on one WAL.
   (b) The DELETE side, which candidates always miss. Live messages =
       15K/sec × 600s TTL = 9M rows / ~90GB. The every-30s sweeper must
       CASCADE-delete ~450K message rows per pass, and in Postgres a
       DELETE is itself a WAL-logged write that leaves dead tuples for
       autovacuum. The cleanup now competes with the ingest for the same
       WAL, autovacuum falls permanently behind, and the table bloats
       even though the logical row count is flat.
  Observable symptom: INSERT latency > 500ms; Kafka consumer lag grows
  unbounded (Tableflow signal: this is exactly how a slow Iceberg table
  sink falls behind); pg_stat_user_tables n_dead_tup climbing while
  n_live_tup stays constant; disk usage growing on an ephemeral system.
  Why Stage 3 is needed: the message store must accept parallel writes
  with no single WAL, and expiry must stop being a DELETE workload.

══════════════════════════════════════════════════════════════════
```

---

### Stage 3 — Cassandra Message Store, Postgres Keeps Only Addresses (15K+ msg/sec)

> Split the two entities by their nature. Addresses are small, unique-constrained and transactional — they stay in Postgres. Messages are high-volume, append-only and self-expiring — they move to Cassandra, where TTL is a column-level feature instead of a background DELETE job.

```
── Stage 3: Cassandra-backed Message Store ────────────────────────

  [External Mail Server] ──SMTP──▶ ┌──────────────────────┐
                                   │  SMTP Ingress (N)    │
                                   └──────┬───────┬───────┘
                        Bloom NO → 550    │       │
                          ┌───────────────┘       │ publish raw bytes
                          ▼                       ▼
              ┌────────────────────┐  ┌────────────────────────────┐
              │  Redis Bloom       │  │ Kafka: raw-messages         │
              │  ~2.5MB @ 2.1M     │  │ 32 partitions, key=address  │
              │  live addresses    │  └─────────────┬──────────────┘
              └────────────────────┘                ▼
                          ▲             ┌────────────────────────────┐
                          │ rebuild     │  Message Processor group    │
                          │ every 5 min │  32 members: parse MIME,    │
                          │             │  enforce size, then write   │
              ┌───────────┴──────────┐  └─────────────┬──────────────┘
              │  Postgres (small)     │                ▼
              │  addresses ONLY       │  ┌──────────────────────────────┐
              │  ~420MB · UNIQUE      │  │  Cassandra — messages          │
              │  on address string    │  │  PARTITION KEY  address_id     │
              │  ground truth for     │  │  CLUSTERING  received_at DESC, │
              │  existence + expiry   │  │              message_id DESC   │
              └───────────┬──────────┘  │  USING TTL 600  (per row)      │
                          │             │  TimeWindowCompactionStrategy   │
                          │             └──────────────┬───────────────┘
   GET /v1/addresses/{id} │                            ▲
   /messages ─────────────┴──────[Inbox API]───────────┘
                                  1. verify address alive (Postgres)
                                  2. slice ONE Cassandra partition

  NOTE: there is no TTL sweeper for messages any more. Rows carry
  their own TTL; TWCS drops a whole SSTable once every row inside
  it has expired.

At 15K msg/sec: 150MB/sec ingest, 9M live rows, ~90GB live data.
With RF=3 that is ~270GB replicated; a 12-node ring holds ~23GB/node.
Bloom filter grows to 2.1M live addresses ≈ 2.5MB (Deep Dive 1 math).

WHY CASSANDRA EARNS ITS PLACE HERE (the probe is "why not shard Postgres?"):
  1. It removes the exact resource Stage 2 exhausted. The write is
     append-only, single-partition, with no cross-row transaction, so
     a leaderless ring adds write capacity linearly by adding nodes —
     there is no one WAL for 32 processors to converge on.
  2. Per-row TTL is native. This deletes the entire Deep Dive 2 sweeper
     problem — ceiling (b) above simply stops existing, because expiry
     is no longer a 450K-row DELETE competing with ingest. With TWCS
     an expired SSTable is dropped whole: no tombstone scan, no vacuum.
  3. The read is Cassandra's best case and matches our API contract
     verbatim. `GET /v1/addresses/{id}/messages` with a cursor on
     (received_at, message_id) is one partition key plus a clustering
     slice — rows are already stored in that order, so no sort and no
     secondary index.
  4. Addresses deliberately do NOT move. They need the UNIQUE
     constraint on the address string to prevent two users being handed
     the same mailbox, and Cassandra would need a lightweight
     transaction (Paxos round, ~4× latency) to do that. Postgres holds
     420MB — keeping it is free, and it stays the ground truth that
     makes a Bloom false positive safe.

CEILING OF STAGE 3: single-region SMTP ingress, forced by two shared,
  un-sharded components:
   (i) ONE Redis instance answers every RCPT TO check, so it is both a
       SPOF for mail acceptance and the target of a 2.5MB atomic filter
       swap every 5 minutes.
  (ii) ONE region terminates every SMTP session on earth. SMTP is
       chatty — EHLO / MAIL FROM / RCPT TO / DATA is four round trips,
       so a sender in Singapore talking to us-east pays ~600ms and
       starts retrying. Geography, not hardware, is the wall.
  NOT a ceiling, though it is tempting to claim: the addresses INSERT
  rate. 3,500 inserts/sec × ~200B rows is ~700 KB/sec — three orders of
  magnitude under the 150MB/sec that saturates the SAME primary on the
  Stage 2 write path. And a B-tree over 2.1M rows is ~3-4 levels deep;
  it does not meaningfully "grow" with scale. Do not offer a bottleneck
  the arithmetic contradicts — an interviewer who divides 3,500 by 200
  bytes in their head will stop trusting the rest of the answer.
  Observable symptom: Redis P99 spikes aligned to the rebuild cadence,
  with SMTP sessions timing out during the swap; and rising SMTP
  session timeouts correlated with sender geography, not with load.
  Next moves, in order:
   1. Shard the Bloom filter across a Redis Cluster keyed by address
      prefix, so no single instance serves all RCPT TO checks
      (Section 5, "100M DAU" row).
   2. Shard the addresses table by hash of the address string — or move
      it to Cassandra with a lightweight transaction for uniqueness,
      accepting the Paxos latency once per creation.
   3. Regional SMTP ingress with a per-region Bloom filter fed by a
      log-compacted Kafka `addresses` topic, because SMTP is chatty —
      four cross-ocean round trips per session is ~600ms and senders
      start retrying (Section 5, multi-region row).
   4. Offload bodies over ~1MB to S3 and keep only the object key in
      Cassandra, so ingest bandwidth stops scaling with attachment size
      (Section 5, attachments row).
```

---

## 🔬 Section 8 — Core Component Deep Dives (Minutes 30–45)

### Deep Dive 1: Bloom Filter — ⭐ Most Likely to Consume Entire Round

> **⚠ READ THE VERDICT BEFORE THE DEEP DIVE. At 10M DAU, the Bloom filter is the wrong tool — an exact Redis SET with per-key TTL is the right call.** One `SET addr:f4k2s9 live EX 600` per address is ~10MB for all 210K live addresses, and it beats the filter on **every axis this file itself says matters**:
>
> | Axis | Bloom filter (250KB) | Exact Redis SET (~10MB) |
> |---|---|---|
> | False positive rate | 1% — wasted Postgres reads | **Zero** |
> | False negatives | Possible on a naive rebuild swap (below) — **silent total mail loss** | **Impossible** |
> | Deletion on expiry | Not supported — the entire deletion problem below exists only here | **Free and native** — per-key `EX` TTL, Redis evicts it |
> | Rebuild job | Required, plus a distributed lock, plus a swap | **None** |
> | Stale window | Bounded by the rebuild interval | **None** — the key expires exactly when the address does |
> | Memory | 250KB | ~10MB — irrelevant at this scale |
>
> The per-key TTL is the quiet killer: it dissolves the deletion problem, the rebuild job, the stale window, and the false-negative-on-swap hazard **all at once**, and it does it in one line of Redis. It also collapses into the same keyspace as the `recently_used_codes` reuse guard from Part 2.5 (`<state>` = `live` or `recently-expired`), so one exact keyspace serves both jobs instead of the design paying for two indexes and reconciling neither.
>
> **So why is the full Bloom deep dive still here, at length?** Two reasons, both good ones:
>
> 1. **It is the right answer at 100M+ DAU.** At 2.1M live addresses the exact set is ~100MB — per region. Once Section 5's multi-region row lands and every regional SMTP ingress needs its own local copy, replicating 100MB per region on every rebuild is a real cost, and 2.5MB is not. **That** is where the filter earns its place, and it is how you should frame it: "at our stated scale I'd ship the exact set; here is the scale at which I'd switch, and here is the machinery for when we do."
> 2. **A Confluent round in May 2026 spent most of its time on exactly this** (see the header note). You will be asked to size it, to explain the deletion problem, and to defend rebuild vs Counting. Knowing the machinery cold *and* knowing it is over-engineered at 10M DAU is strictly better than knowing only one of the two — the second half is what separates a candidate who pattern-matched from one who costed the decision.
>
> Say the verdict first, then demonstrate the depth. Leading with the filter and being *asked* "why not just a Redis SET with a TTL?" is the version of this conversation you lose.

**Why a fast address index matters at all (this part is sound — it just does not single out Bloom):**
Every `RCPT TO` for an address that does not exist would otherwise be a Postgres lookup. Per the Stage 2 read budget, that is an assumed ~4,000/sec of spam on top of 1,040/sec of legitimate confirms and 3,500/sec of inbox polls — ~8,540 reads/sec against a single primary's ~10K/sec, or ~85% of capacity. Collapsing the spam component to near zero is clearly worth doing. Note carefully what that argues for: **a cache**. It does not by itself argue for a *probabilistic* one — an exact SET removes the same reads and the 1% FPR residue too.

**What a Bloom filter is (define it in the interview — Confluent wants this):**
A Bloom filter (a probabilistic data structure that answers "is this element in the set?" in O(k) time using a bit array of size m and k hash functions — like a set that can say "definitely not" but never "definitely yes") guarantees zero false negatives **for elements that were added to the filter you are querying**. If the filter says NO → the address definitely does not exist → reject immediately, no DB lookup. If the filter says YES → address MIGHT exist → verify in Postgres.

Keep that qualifier attached. The guarantee is a property of *one* filter instance, and it says nothing about a filter you **replaced**. Swapping generations is an operation the data structure does not define, and it is exactly where this design leaks — see "The rebuild swap can produce false negatives" below. Stating the guarantee unqualified is how the bug survives review.

**Sizing the Bloom filter:**

```
Formula: m = -n × ln(p) / (ln 2)²
         k = (m/n) × ln(2)

Where: n = number of active addresses = 210,000 (10-min TTL × 350 addr/sec)
       p = target false positive rate = 1% (0.01)

m = -210,000 × ln(0.01) / (0.693)²
  = -210,000 × (-4.605) / 0.480
  = 210,000 × 9.594
  ≈ 2,014,740 bits ≈ 250 KB

k = (2,014,740 / 210,000) × 0.693 ≈ 9.59 × 0.693 ≈ 6.6 → 7 hash functions
```

**250KB for 1% FPR on the entire active address space. Trivially fits in Redis.**

If the interviewer scales to 100M DAU (3,500 addr/sec × 600s = 2.1M active addresses):
- m = ~2.5MB — still fits trivially in a single Redis instance (max Redis memory ~256GB)

**What happens on a false positive:**
SMTP ingress checks Bloom → "maybe exists" → checks Postgres → address not found → returns SMTP 550 "User unknown" to sender. Cost: one extra Postgres read (~1ms). No data corruption, no false delivery. Bloom false positives are safe.

**The deletion problem — what interviewers probe hardest:**

Standard Bloom filters do not support deletion. When an address expires (after 10 minutes), you cannot remove it from the filter. The filter fills up with stale entries → false positive rate rises over time.

```
Options considered:
```

| Option | Pros | Cons |
|---|---|---|
| **Periodic full rebuild** | Simple; accurate; Postgres is ground truth | Staleness is bounded by the rebuild **interval**, not the rebuild duration — at a 5-minute cadence a just-expired address can linger in the filter for up to 5 minutes. Also introduces the swap hazard below |
| **Counting Bloom filter** | Supports deletion; FPR stays stable | ~4× memory (4-bit counters instead of bits): **250KB → 1MB** at current scale; counters can overflow |
| **Time-windowed (two filters)** | No deletion needed; automatic rotation | More complex; double memory; brief overlap window |
| **Exact Redis SET, per-key TTL** | Zero FPR; deletion native and free; no rebuild, no swap, no stale window | ~10MB instead of 250KB — and ~100MB **per region** at 100M DAU, which is the one number that eventually favours the filter |

**Decision: periodic full rebuild every 5 minutes** (half the TTL — so any given address is re-confirmed at least once before it expires, and the worst-case staleness is half a TTL rather than a whole one).

Because: Postgres query `SELECT address FROM addresses WHERE expires_at > NOW()` returns 210K rows in milliseconds; the rebuild itself is a few hundred ms including serialization. During the rebuild, keep serving the old filter. **Be precise about what "stale" costs:** the worst case is a just-expired address that lingers in the filter for up to one rebuild interval (5 minutes) → one extra Postgres lookup that correctly returns "expired" → SMTP 550. That is a wasted read, not a wrong answer. The dangerous direction — a *live* address **missing** from the filter — is the swap bug below, and it is not acceptable in the way staleness is.

**The trade-off accepted:** a Counting Bloom filter eliminates the stale window but quadruples memory (**250KB → 1MB** at current scale — trivial) and adds counter-overflow risk. For our scale, periodic rebuild is simpler and auditable — you can verify it worked by counting set bits. Note that the exact Redis SET beats both on staleness anyway, for the reason in the verdict box: a native per-key TTL expires the entry at exactly the right instant, with no job to schedule.

**Implementation sketch:**

```java
// Rebuild job — runs every TTL_SECONDS / 2 (5 minutes for 10-min TTL)
public void rebuildBloomFilter() {
    // expected insertions: 10-min TTL × 350 addr/sec peak = 210K active
    // desired false positive rate: 1%
    BloomFilter<String> newFilter = BloomFilter.create(
        Funnels.stringFunnel(Charset.UTF_8),
        210_000,
        0.01
    );

    // Query only addresses whose expiry is in the future
    List<String> activeAddresses = addressRepo
        .findActiveAddresses(Instant.now());

    for (String address : activeAddresses) {
        newFilter.put(address);
    }

    // Atomic swap — old filter serves reads during rebuild
    // ⚠ THIS LINE IS THE BUG. See below, then use rebuildBloomFilterSafely.
    redisBloomFilter.atomicReplace(newFilter.toByteArray());
}
```

---

#### ⚠ The rebuild swap can produce FALSE NEGATIVES — and a false negative here is silent, total mail loss

This is the highest-severity defect in the whole design, and it is one line of code. Trace it:

1. At time **T**, the rebuild job runs `SELECT address FROM addresses WHERE expires_at > NOW()` and gets the active set as it stood at T.
2. Between **T** and the swap at **T+Δ**, the Address Service keeps serving `POST /v1/addresses`. Every new address is INSERTed into Postgres and then ADDed to *the filter currently in Redis* — which is the **old** one. The rebuild job has never heard of these addresses; they were not in its SELECT, and it is building its new filter in local memory.
3. At **T+Δ**, `atomicReplace(newFilter.toByteArray())` **overwrites** the Redis filter wholesale. Every address added during the window is discarded.

Those addresses are **live in Postgres** but **absent from the filter**. Every email sent to one of them gets a Bloom NO at `RCPT TO` → `550 No such user here` → and because 550 is a *permanent* rejection, the sending MTA logs a hard failure and **never retries**. The user watches an empty inbox forever. There is no bounce to us, no error in our logs, no consumer lag, no alert. The inbox is simply dead for its entire lifetime.

**Magnitude, using this file's own numbers:**

| Rebuild duration Δ | Creations during Δ | Dead inboxes per rebuild | Per day at a 5-min cadence |
|---|---|---|---|
| 100ms (the figure quoted above) | 350/sec × 0.1s | **~35** | ~10,000 |
| ~500ms (realistic: a 210K-row SELECT, 210K filter `put`s, then serializing 250KB) | 350/sec × 0.5s | **~175** | ~50,000 |

**This contradicts two claims the file makes elsewhere** — the Stage 2 KEY INVARIANT ("a false negative is impossible by construction, which is why a NO can be trusted to reject mail outright") and the definition above ("guarantees zero false negatives"). Both are true of a *single* filter generation. Neither survives a generation swap that drops concurrent writes. **An invariant that a maintenance job silently violates is not an invariant.**

> **This is the same hazard family the file already catches elsewhere.** Part 2.5 spots the delete-then-reuse problem for the *address namespace* — a code re-minted for a new owner while mail for the old owner is still in flight — and fixes it with a guard set. This is the identical shape (state rebuilt from a snapshot while concurrent mutations land elsewhere), one layer down, in the *filter*. It is worth noticing out loud in the interview that the file caught it there and missed it here: the same failure pattern is much harder to see when it lives inside a "just rebuild it periodically" maintenance job than when it lives in a user-facing ID allocator. **That generalization is the point** — periodic-rebuild-from-snapshot is a pattern, and every instance of it needs a merge step.

**The remedy — two options, either is sufficient:**

**(A) OR the old filter's bit array into the new one immediately before the swap.** Bloom filters are just bit arrays and union is bitwise OR, so `new | old` is a valid filter over the union of both sets. It cannot lose an address. The cost is that entries expired since the last rebuild survive one extra generation — i.e. you trade the false negative (catastrophic) for slightly more staleness and a marginally higher FPR (harmless, and exactly the cost the design already accepts).

**(B) Double-write during the rebuild.** Publish a "rebuild in progress" flag; while it is set, the Address Service ADDs each new address to both the live filter and the pending one. Strictly tighter on FPR than (A), but it couples the Address Service to the rebuild job's lifecycle and needs care if the rebuild dies mid-flight — so (A) is the better default, and (B) is the answer if an interviewer pushes on FPR.

**Steps in plain English (option A):**

1. **Snapshot the active set** from Postgres — the ground truth as of time T.
2. **Build the new filter in memory** from that snapshot.
3. **Read the current live filter's bits** back out of Redis, as late as possible — after step 2, not before.
4. **OR them into the new filter.** Any address created during the window is in the old bits, so the union cannot lose it.
5. **Swap atomically**, and do the read-OR-write inside a Lua script so no ADD can slip between step 3 and step 5.

```java
// Rebuild job — runs every 5 minutes (TTL_SECONDS / 2 for a 10-min TTL)
public void rebuildBloomFilterSafely() {
    // Step 1 — snapshot ground truth as of now
    List<String> activeAddresses = addressRepo
        .findActiveAddresses(Instant.now());

    // Step 2 — build the replacement in local memory
    BloomFilter<String> newFilter = BloomFilter.create(
        Funnels.stringFunnel(Charset.UTF_8),
        210_000,
        0.01
    );
    for (String address : activeAddresses) {
        newFilter.put(address);
    }

    // Steps 3-5 — union with whatever is live, then swap, atomically.
    // The Lua script GETs the current bits, ORs in ours, and SETs the
    // result, so no concurrent ADD can land between the read and write.
    // Without this union, every address created since Step 1 is lost
    // from the filter while still live in Postgres — a false negative,
    // which means a permanent 550 and an inbox that never receives.
    redisBloomFilter.unionAndSwap(newFilter.toByteArray());
}
```

> **Say this in the interview if the Bloom filter comes up at all.** "Rebuild-and-swap has a write-window hazard: addresses created between the snapshot and the swap exist in Postgres but not in the new filter, so they get a permanent 550 and the inbox silently never receives mail. I fix it by OR-ing the old bit array into the new one inside the swap — union can't lose an element. And note that this whole class of bug disappears with an exact Redis SET keyed per address with a native TTL, which is what I'd actually ship at this scale." That last sentence is the one that lands: you demonstrated the depth *and* the judgment to not need it.

---

### Deep Dive 2: TTL Expiry Mechanism

**Why this is a critical component:**
TTL enforcement is a correctness requirement, not a performance one. An expired address that still serves messages leaks PII and violates the service contract.

**Options considered:**

| Option | Pros | Cons |
|---|---|---|
| **Lazy expiry (check on read)** | No background job; simple | Expired data stays in DB until queried; storage bloat; inconsistent |
| **Background sweeper** | Proactive cleanup; bounded storage | Sweep interval = max stale window |
| **Kafka topic retention (TTL via log retention)** | Native TTL, no custom job | Messages as Kafka records — operational complexity |
| **DB-level TTL (Redis native or Postgres cron job)** | Atomic; DB manages cleanup | Postgres has no native row TTL; Redis TTL fits KV but not relational |

**Decision: Background sweeper + lazy check on read (two-layer defense).**

```sql
-- Sweeper runs every 30 seconds
DELETE FROM addresses
WHERE expires_at < NOW() - INTERVAL '30 seconds';
-- CASCADE deletes all messages for expired addresses

-- Lazy check on every API read (belt + suspenders)
SELECT * FROM addresses
WHERE id = $1 AND expires_at > NOW();
-- If no row returned → 410 Gone
```

The `INTERVAL '30 seconds'` buffer prevents a race condition where the sweeper deletes an address 1ms before the API check completes.

**Index required for sweeper performance:**
```sql
CREATE INDEX idx_addresses_expires_at ON addresses (expires_at);
```
Without this index, the sweeper does a full-table scan every 30 seconds. At 210K rows that is ~20ms per scan — fine for now. Scale the row count and the time scales with it, **linearly**: 210K rows in 20ms implies ~10.5M rows/sec, so 2.1M rows at 100M DAU is **~190ms** per scan, not 2 seconds. (Scaling rows 10× and time 100× is a common slip — a sequential scan is O(n), not O(n²).) ~190ms every 30s is still real load to run against the primary for no reason, and it grows without bound as the table does; the index makes it ~1ms regardless of table size, so build it. The honest framing is "linear cost I refuse to pay," not "cliff I am avoiding."

---

### Deep Dive 3: SMTP Ingress and MX Record Setup

**Why this matters:**
The SMTP layer is the system's external interface. Getting MX records wrong means no email arrives. Getting SMTP error codes wrong means legitimate emails are silently dropped.

**MX Record:**
```
tempmail.io.  IN  MX  10  mx1.tempmail.io.
tempmail.io.  IN  MX  20  mx2.tempmail.io.  (backup)
mx1.tempmail.io.  IN  A  203.0.113.10
```

The priority numbers (10, 20) tell sending servers to prefer `mx1` and fall back to `mx2`. Without an MX record, email to `@tempmail.io` is undeliverable — every major MTA (Mail Transfer Agent — the software that routes and delivers email between servers, e.g. Postfix, Sendmail, Exchange) will refuse to route it.

**SMTP state machine at ingress:**
```
EHLO sender.domain
  → 250 OK (accept all)

MAIL FROM: <spammer@evil.com>
  → 250 OK (we don't block on sender for a disposable service)

RCPT TO: <f4k2s9@tempmail.io>
  → Bloom filter check → Postgres check
  → 250 OK (address valid)
    OR 550 No such user here (address invalid/expired)

  ⚠ SMTP allows MULTIPLE RCPT TO in one session. Each is checked and
    answered independently; a session can end up with 2 live
    recipients and 3 rejected ones, and still proceed to DATA.

DATA
  → 354 Start mail input
  → [email body]
  → Publish to Kafka ONCE PER ACCEPTED RECIPIENT, keyed by that
    recipient's address_id (the key is singular by design), so a
    2-recipient message becomes 2 records → 2 rows → 2 inboxes
  → 250 Message accepted for delivery

QUIT
```

**On the multi-recipient case:** the diagrams in Section 7 and the state machine above both draw a single `RCPT TO` for readability, so say the general case out loud. Publishing once per recipient is the correct behaviour — each inbox owns its own copy of the message, so each expires on its own address's TTL and each is removed by its own CASCADE, with no shared row to reference-count. The cost is body duplication for multi-recipient mail, which is negligible here: two strangers' throwaway addresses rarely appear on the same envelope.

The critical decision: where to do the address check. Do it at `RCPT TO` (not at `DATA`). Why? If we accept `DATA` and then realize the address is invalid, we've received the full email payload (potentially megabytes) for nothing — worse, some SMTP etiquette says once you accept DATA you should deliver it. Rejecting at `RCPT TO` is cleaner and wastes no bandwidth.

---

## 🗄️ Section 9 — Data Model / SQL Schema

```sql
-- Primary table: one row per temp address
CREATE TABLE addresses (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    address     VARCHAR(254) NOT NULL,
    expires_at  TIMESTAMP   NOT NULL,
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW(),
    created_ip  INET        NOT NULL
);

-- address lookup by string (SMTP ingress + API reads)
CREATE UNIQUE INDEX idx_addresses_address ON addresses (address);

-- TTL sweeper scan + expiry check on reads
CREATE INDEX idx_addresses_expires_at ON addresses (expires_at);

-- Rate limit lookup: how many addresses has this IP created recently?
CREATE INDEX idx_addresses_ip_created ON addresses (created_ip, created_at DESC);

-- Messages table: messages belong to an address, deleted when address deleted
CREATE TABLE messages (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    address_id      UUID        NOT NULL REFERENCES addresses(id) ON DELETE CASCADE,
    from_address    VARCHAR(254) NOT NULL,
    subject         VARCHAR(998),
    body_text       TEXT,
    body_html       TEXT,
    received_at     TIMESTAMP   NOT NULL DEFAULT NOW()
);

-- ONE index serves both the simple list and cursor pagination.
-- (address_id, received_at DESC) is a strict PREFIX of this, so a
-- separate index on it would be pure write amplification. See below.
CREATE INDEX idx_messages_cursor ON messages (address_id, received_at DESC, id DESC);
```

**Key Schema Decisions:**

- **`ON DELETE CASCADE` on messages.address_id** — When TTL sweeper deletes from `addresses`, Postgres atomically deletes all messages for that address. No orphaned messages. No two-phase delete logic in application code. This is the correctness guarantee for TTL atomicity.

- **`UNIQUE INDEX` on `addresses.address`** — Prevents two concurrent address creation requests from generating the same address string. Without this, two processes could generate `f4k2s9@tempmail.io` simultaneously, both pass the uniqueness check, and both insert — one succeeds, one fails with constraint violation (handle with retry).

- **One index on messages, not two — and dropping the second is a real fix, not tidying.** An earlier draft carried `idx_messages_address_received (address_id, received_at DESC)` alongside `idx_messages_cursor (address_id, received_at DESC, id DESC)`. The first is a **strict prefix** of the second, so Postgres answers every query the first could serve from the second at effectively identical cost — the extra trailing column changes nothing for a query that never mentions `id`. The redundant index was therefore 100% dead weight, and not free dead weight: **every INSERT maintained both B-trees**, doubling index WAL for the messages table and giving autovacuum a second structure to clean on every dead tuple. That lands squarely on the resource Stage 2 identifies as its own ceiling (WAL fsync throughput, with autovacuum already losing the race against 450K-row CASCADE deletes) — so the useless index makes the documented breaking point arrive **sooner**. Redundant-prefix indexes are the most common self-inflicted write-amplification bug in Postgres schemas; being able to spot one is worth saying out loud.

- **No `raw_size_bytes` column** — an earlier draft stored it on every row, and nothing ever read it. The size limit is enforced **pre-insert**, in the Message Processor (Section 7, step ⑥), so by the time a row exists the value can only be within limits; and the message response body (Section 6) does not expose it. Storing a number no code path reads is 4 bytes plus a lifetime of "what is this for?" — if size ever needs monitoring, that is a metric emitted at parse time, not a column.

- **`created_ip` for rate limiting** — Stored at address creation time. The index lets us do `SELECT COUNT(*) WHERE created_ip = ? AND created_at > NOW() - INTERVAL '1 minute'` in ~1ms to enforce per-IP rate limits.

- **SQL vs NoSQL choice:** SQL (Postgres) because the access patterns are relational (messages belong to addresses; cascade delete is a business requirement, not just a nice-to-have), the data volume at 10M DAU is small (~6GB live), and Confluent explicitly evaluates SQL schema design. Cassandra would be chosen at 100M+ DAU where Postgres write throughput saturates.

---

## ⚠️ Section 10 — Trade-offs + Failure Modes (Minutes 45–53)

### Trade-off 1: Async (Kafka) vs Sync SMTP Processing

- **Chose:** Async — SMTP ingress publishes to Kafka, Message Processor consumes asynchronously
- **Gain:** SMTP acceptance takes < 5ms (Kafka publish); SMTP session completes fast; bursts absorbed by Kafka queue; processor can crash/restart without losing messages
- **Lose:** 2-3 second delivery lag between SMTP acceptance and message appearing in inbox (eventual consistency); if Kafka is down, SMTP must fall back to sync processing or refuse new messages
- **Failure mode if wrong (if sync):** [Technical]: At 1,040 SMTP msg/sec, each session waits ~15ms for Postgres write. With a 500ms SMTP timeout, a Postgres slowdown (GC pause, lock contention) causes SMTP connections to pile up → thread pool exhaustion → SMTP 421 "temporarily unavailable." [Streaming impact]: Kafka consumer lag equivalent — ingestion stalls; the "pipe" backs up. For Tableflow, this is the equivalent of a slow Iceberg table sink: the pipeline falls behind real-time and downstream consumers see hours-old data instead of near-real-time.

### Trade-off 2: Probabilistic (Bloom filter) vs Exact (Redis SET) Address Index

- **Chose at 10M DAU:** the **exact Redis SET** (`SET addr:f4k2s9 live EX 600`, ~10MB). **Chose at 100M+ DAU:** the Bloom filter. State it as a scale-dependent decision, because it is one — see the verdict box in Section 8 Deep Dive 1.
- **What the Bloom filter actually gains:** memory, and only memory. 250KB vs ~10MB at 10M DAU (40× smaller); **2.5MB vs ~100MB at 100M DAU** (the numbers in Section 8 and Stage 3 are megabytes — 2.5MB, not 2.5GB; and an exact set of 2.1M address strings is ~100MB, not 10GB). At 10M DAU that saving is ~9.75MB, which buys nothing on a Redis instance with gigabytes free. At 100M DAU, replicated to every regional SMTP ingress, ~100MB per region per rebuild is a genuine cost and 2.5MB is not. **That** is the whole case, and it is a good one at the right scale.
- **What it does NOT gain — latency.** An earlier draft claimed "Bloom check is O(k) bit operations — faster than Redis GET," which is self-contradictory: the filter *lives in Redis* (Section 3.5), so the check is a Lua script doing 7 `GETBIT`s (Section 12), and **7 bit reads inside one Redis round trip are strictly slower than one `GET`** — same network hop, more work at the far end. And the read it avoids is priced at ~1ms (Section 8) while a Redis round trip is ~0.5–1ms, so the per-request latency saving is roughly **zero**. The filter saves Postgres **capacity**, not time. Claim capacity; do not claim speed.
- **Lose:** 1% false positive rate (wasted Postgres reads); no delete support, hence a rebuild job, a distributed lock, a stale window bounded by the rebuild interval, and the false-negative-on-swap hazard that has to be engineered around (Section 8). The exact SET has none of these — its per-key TTL is the deletion mechanism.
- **Failure mode if wrong (no address cache at all):** [Technical]: per the Stage 2 read budget, ~3,500 inbox polls/sec + ~1,040 legitimate `RCPT TO` confirms/sec + an assumed ~4,000/sec of spam to non-existent addresses ≈ **8,540 reads/sec against a ~10K/sec primary — ~85% of capacity, with no room for a burst**. (Note the assumption: the spam is *additional* to Section 4's 1,040 msg/sec of legitimate mail, not a percentage of it. The older "80% of 1,040 = 832/sec" framing double-counted traffic Section 4 had already allocated to real users, and it made the cache look ~5× less valuable than it is.) At 100M DAU every term scales 10× and the primary is gone. [Streaming impact]: Equivalent to a Kafka consumer without offset commits — the system "processes" every message (reads DB) regardless of whether it needs to, wasting throughput on noise rather than signal.

### Trade-off 3: Background Sweeper vs Lazy-Only Expiry

- **Chose:** Background sweeper every 30 seconds + lazy check on reads (dual layer)
- **Gain:** Bounded storage (max ~6GB live, then cleaned); no indefinite growth; expired data removed proactively
- **Lose:** Brief stale window (up to 30s between expiry and deletion); sweeper adds periodic DB load (low — indexed scan on `expires_at`)
- **Failure mode if wrong (lazy-only):** [Technical]: Expired addresses and messages accumulate indefinitely. At 116 new addresses/sec × 86,400s/day × average 3 messages × 10KB = ~300GB/day of un-deleted data. In 30 days: 9TB. Postgres VACUUM cannot keep up with dead tuples. [Streaming impact]: Equivalent to a Kafka topic with `retention.ms = -1` (infinite retention) — the log grows forever, disk exhausts, broker crashes. For Tableflow: Iceberg table snapshots never expire → table metadata grows unbounded → snapshot reads slow down as Iceberg must scan all historical snapshots.

---

## 🌊 Section 11 — Confluent/Tableflow Angle

**TTL = Kafka topic retention + Iceberg snapshot expiry:**
The disposable email's TTL lifecycle is architecturally identical to Kafka's log retention. In Kafka, a topic with `retention.ms = 600000` (10 minutes) automatically purges log segments older than 10 minutes. Our `addresses.expires_at` + TTL sweeper is the same mechanism at the application layer. For Confluent's Tableflow team, this is a solved problem: Iceberg's `expire_snapshots` procedure purges snapshots older than a configured timestamp. Mentioning this parallel signals you understand Confluent's core product — not just the interview question.

**Kafka as ingestion backbone:**
In Stage 2, Kafka's role is exactly what Tableflow uses it for: a reliable, ordered, replayable ingestion buffer between an external event source (SMTP sender = Kafka producer) and a stateful processor (Message Processor = Kafka Streams application writing to Iceberg). The at-least-once delivery guarantee means a processor crash doesn't lose emails — exactly the durability guarantee Tableflow provides for table sync operations.

**Compacted topic as address registry:**
A Kafka log-compacted topic with `address → expiry_time` as key-value is effectively the same as our Bloom filter + Postgres combination — the compacted topic retains the latest value per key (address), and consuming it rebuilds an exact in-memory address set. This is how a Tableflow control plane could track table metadata: each table name is a key, current schema is the value, Kafka compaction ensures the latest schema survives log cleanup.

**Multi-cloud consideration:**
If TempMail must serve global users with < 50ms SMTP acceptance (e.g., US + EU + APAC), regional SMTP ingress is needed. Each region maintains its own Bloom filter, synchronized via a Kafka topic — the address creation event streams globally. This is the same pattern Confluent uses for multi-region cluster replication (CRR — Cluster Linking replicates topics across regions). Mentioning CRR signals Confluent domain fit.

---

## 🔬 Section 12 — Where the Interviewer Will Probe

### Surface Probe (Tier 1 — every candidate gets this)

**Q: "Why do you need a Bloom filter at all? Can't you just query Postgres?"**
> Two separate questions, and I'd answer them separately. **Do we need an address cache?** Yes. Section 4's 1,040 msg/sec is legitimate mail to live addresses; spam to addresses that never existed is *additional* traffic on top of it, and I'd assume around 4,000/sec for a public disposable-email domain. Add the 3,500 inbox polls/sec and the 1,040 confirming reads and we're at ~8,540 reads/sec against a primary good for ~10K — 85% of capacity with nothing left for a burst. So a cache is clearly justified. **Do we need a *probabilistic* cache?** No, not at this scale. An exact Redis SET with a native per-key TTL — `SET addr:f4k2s9 live EX 600` — is about 10MB for 210K live addresses and eliminates the same reads with zero false positives, no rebuild job, no stale window, and deletion for free. The Bloom filter's only advantage is 250KB vs 10MB, and 9.75MB is not a saving worth the rebuild machinery. I'd ship the exact set at 10M DAU and switch to the filter at 100M+, where the exact set is ~100MB *per region* and has to be replicated to every regional SMTP ingress. Happy to go deep on the filter either way — the sizing math and the rebuild hazards are the interesting part.

**Q: "What happens when the address expires mid-delivery — the sender sends the email while the address is alive but it gets processed after TTL?"**
> The Bloom filter + Postgres check happens at `RCPT TO` (during the SMTP session, before we accept the DATA). If the address is alive at `RCPT TO`, we return SMTP 250 and accept the email. The message is published to Kafka. If the TTL sweeper fires before the Message Processor writes to the messages table, the processor's INSERT will fail the foreign key constraint (address_id references a deleted address). The processor catches this constraint violation and discards the message — the address is gone, so there's no inbox to deliver to anyway. This is correct behavior: the sender was told "250 accepted" but the address expired before delivery completed. A better UX would be a grace period in the sweeper, but that's a product decision, not a correctness bug.

---

### Deep Probe (Tier 2 — tests real understanding)

**Q: "Your Bloom filter has 1% FPR. Walk me through what happens on a false positive end-to-end."**
> SMTP ingress receives `RCPT TO: <ghost123@tempmail.io>`. This address was deleted 2 minutes ago but our Bloom filter hasn't been rebuilt yet (max stale = one rebuild interval = 5 minutes). Filter returns "maybe exists." SMTP ingress queries Postgres: `SELECT id FROM addresses WHERE address = 'ghost123@tempmail.io' AND expires_at > NOW()`. Returns zero rows. SMTP ingress returns `550 No such user here` to the sending server. The sender's MTA logs a permanent failure and stops retrying. Total cost: one extra Postgres read (~1ms). No data corruption, no false delivery, no user impact. A false positive is a performance cost, not a correctness bug.

**Q: "How do you handle the Bloom filter across multiple SMTP ingress servers?"**
> The Bloom filter lives in Redis, shared across all SMTP ingress instances. Each instance does a Redis GET on the bit positions for the incoming address (7 hash lookups via Lua script for atomicity). The filter rebuild job runs on one designated instance (with a distributed lock via Redis `SET NX PX`) and updates the filter in Redis atomically. No SMTP ingress server maintains local state — they all read from the same Redis. This means a single Redis instance is a SPOF for the Bloom filter, but since a Bloom filter failure mode is graceful degradation (just query Postgres for every message, which we already handle), we can tolerate brief Redis downtime.

**Q: "Counting Bloom filter vs periodic rebuild — when would you switch?"**
> First, get the denominator right, because it is easy to flatter yourself here. Staleness of a periodically-rebuilt filter is governed by the rebuild **interval**, not the rebuild **duration**. With a 5-minute cadence against a 10-minute TTL, an expired address can linger in the filter for up to 5 minutes — **50% of the TTL**, not the 0.017% you get by dividing a 100ms rebuild duration by a 600s TTL. That division is a category error, and it makes the design look ~3,000× better supported than it is; the duration only bounds how long the *old* generation keeps serving, which is a sub-second effect nobody notices. Second, the good news: that staleness is in the harmless direction — a stale entry means a false positive, so one wasted Postgres read ending in a correct 550. The *dangerous* direction, a live address missing from the filter, comes from the rebuild swap, and I fix that by OR-ing the old bit array into the new one (Deep Dive 1). Third, when would I switch to Counting? When staleness starts costing real read capacity — roughly when (a) the active set reaches 2M+ and the rebuild scan itself takes > 1s, so I can't shorten the interval much further, or (b) the TTL drops below ~1 minute, where a 5-minute interval would exceed the TTL entirely and the filter would be mostly ghosts. For a 10-minute TTL at 10M DAU, periodic rebuild is the right call among *Bloom* options: simpler code, no counter overflow, fully auditable by counting set bits. Though as I said, at this scale I'd rather have the exact Redis SET and no rebuild at all.

---

### Cross-Concept Probe (Tier 3 — separates senior candidates)

**Q: "How does your architecture change if this system needs to be the authoritative email provider for a paid tier — addresses live for 30 days, users have accounts, and messages must never be lost?"**
> Three structural changes: (1) TTL sweeper becomes opt-in rather than mandatory — paid addresses have `expires_at = NULL` and are exempt from the sweep; (2) Messages must have durability guarantees — promote the Kafka `raw-messages` topic to a compacted topic (by address_id as key) so messages survive processor restarts with guaranteed delivery; (3) Storage changes — ephemeral Postgres with 6GB cap is replaced by Cassandra (address_id as partition key, received_at as clustering key) for 30-day × potentially millions of messages per user. The Bloom filter no longer makes sense for long-lived addresses — an exact Redis SET or secondary Postgres index replaces it. Essentially: the ephemeral architecture optimizes for fast cleanup; the paid architecture optimizes for durability and at-least-once delivery — same Kafka backbone, different retention and storage tier.

---

## 🐞 Section 13 — Common Mistakes on This Question

- **Mistake 1: Returning 404 for expired addresses** → **Why it's wrong:** 404 means "never existed or bad ID." An expired address DID exist — the client has a valid ID. 404 misleads clients into thinking they have a wrong ID and creating a new request chain instead of understanding their session expired. → **What to say instead:** "I use 410 Gone for expired addresses — it's semantically precise, tells the client the resource existed and is permanently gone, and lets the frontend show 'your inbox expired, click to get a new address' instead of 'error: invalid address.'"

- **Mistake 2: Starting address creation with a uniqueness check in application code** → **Why it's wrong:** Check-then-insert is a TOCTOU race — two concurrent requests can both pass the check, both attempt INSERT, and you get a constraint violation or duplicate address. → **What to say instead:** "The UNIQUE INDEX on the address column makes Postgres the arbiter. Application code attempts INSERT; if it gets a unique constraint violation, it generates a new random address and retries — the DB guarantees uniqueness, not the application."

- **Mistake 3: Doing the address existence check at DATA (not at RCPT TO)** → **Why it's wrong:** Accepting DATA means receiving the full email body (potentially megabytes) before deciding to reject it. At an assumed ~4,000 spam msg/sec aimed at non-existent addresses (additional to Section 4's 1,040/sec of legitimate mail, not a slice of it), that's 4,000 × avg 10KB = **~40MB/sec** of wasted inbound bandwidth ingested before rejection. SMTP etiquette says: reject early. → **What to say instead:** "The check happens at RCPT TO — before DATA. If the address doesn't exist, we return 550 immediately. The sender gets a definitive rejection without sending the email body."

- **Mistake 4: Bloom filter without explaining the deletion problem** → **Why it's wrong:** Any interviewer with distributed systems experience knows Bloom filters don't support deletion. If you introduce a Bloom filter without addressing this, they'll probe it — and if you don't have an answer, it looks like you copied the pattern without understanding it. → **What to say instead:** "Standard Bloom filters don't support deletion, so I rebuild the filter every 5 minutes from the current active address set in Postgres. During the rebuild I serve from the old filter — the stale window is bounded at one rebuild *interval* (5 minutes, not the 100ms rebuild duration), and a stale entry causes at most one extra Postgres lookup, not a false delivery. The subtle part is the swap itself: addresses created between the snapshot and the swap would be dropped from the filter while still live in Postgres, which is a **false negative** and means a permanent 550 to the sender and an inbox that silently never receives — so I OR the old bit array into the new one before swapping. And the reason I'd reach for an exact Redis SET with a per-key TTL at this scale is that it makes all of this go away."

- **Mistake 5: Using offset pagination for the inbox** → **Why it's wrong:** With concurrent TTL deletes, offset pagination produces duplicates and gaps. If page 1 returns messages 1-10, then messages 2 and 5 expire before the client requests page 2, `OFFSET 10` now starts at what was message 13 — the client skips 11 and 12. → **What to say instead:** "Cursor pagination on `received_at DESC` with the message ID as a tiebreaker. The cursor is a stable position in the sorted order — deletes behind the cursor don't affect forward pagination."

---

## 🧭 Section 14 — Confluent Evaluation Axes Checklist

| Axis | Relevant? | How your design addresses it |
|---|---|---|
| **API Design Precision** | ✅ | `POST /v1/addresses` returns 201 with `Location` header; `GET /v1/addresses/{id}` returns 410 (not 404) for expired addresses; `GET /../messages` uses cursor pagination (`received_at DESC + id` tiebreaker) because offset breaks under concurrent TTL deletes; `DELETE /v1/addresses/{id}` returns 204 and treats 410 as idempotent success |
| **Trade-off Defense** | ✅ | Bloom filter vs exact Redis SET decided **by scale** — exact set at 10M DAU (zero FPR, native TTL, no rebuild), filter at 100M+ (2.5MB vs ~100MB per region), with the sizing math for both; Kafka vs a local disk spool, named before being asked; async vs sync SMTP processing; background sweeper vs lazy expiry; each with an explicit "failure mode if wrong" at numbers |
| **SQL / Data Modeling** | ✅ | `ON DELETE CASCADE` for TTL atomicity; UNIQUE INDEX on `addresses.address` for concurrent-safe creation; composite index `(address_id, received_at DESC, id DESC)` for cursor pagination; `created_ip + created_at` index for per-IP rate limiting |
| **Distributed Systems** | ✅ | Bloom filter as distributed cache (Redis, shared across SMTP ingress fleet); Kafka decoupling SMTP ingress from storage for burst absorption; Stage 1→2 transition justified on the durability of `250 OK` rather than on connection counts, with the connection arithmetic corrected rather than quietly reused |
| **Pipeline Resilience** | ✅ | Kafka at-least-once delivery between SMTP ingress and Message Processor; SMTP returns 250 before Postgres write (fast accept); processor crash does not lose messages (Kafka offset commit on success); Bloom filter failure degrades gracefully to Postgres-only (not a hard failure) |
| **Concurrency** | ✅ | Concurrent address creation handled by DB UNIQUE constraint (not application-level check); Bloom filter rebuild uses a Redis distributed lock (`SET NX PX`) to prevent concurrent rebuilds, **and unions the live bit array into the new filter inside the swap** so addresses created during the rebuild window are not lost (the false-negative hazard in Deep Dive 1); sweeper uses DELETE with indexed scan (no application-level locking needed); cursor pagination eliminates race conditions from concurrent deletes |

---

## 🧾 Section 15 — TL;DR Answer Summary

> "TempMail is an ephemeral SMTP-receiving system, and at the stated 10M DAU the core architectural challenge is **durability at the moment of `250 OK`**, not address lookup. `250 OK` is an irrevocable promise — the sending MTA stops retrying forever — so the accepted bytes must land somewhere replayable *before* we send it, and the MIME parser must be quarantined off the SMTP listener. That is what Kafka buys, and I'd say up front that a local disk spool satisfies both goals more cheaply; Kafka earns the difference through fleet-wide replay and consumer-group parallelism. Address lookup is a solved sub-problem at this scale: it's ~1,040 msg/sec of legitimate mail plus maybe 4,000/sec of spam, not millions of concurrent connections, and I'd serve it with an **exact Redis SET carrying a native per-key TTL** — zero false positives, deletion for free, no rebuild job. The 250KB Bloom filter is the *100M+ DAU* answer, where the exact set becomes ~100MB per region and has to be replicated to every regional SMTP ingress; I can size it, and I can name the trap in it — a naive rebuild swap drops addresses created during the rebuild, which is a false negative, a permanent 550, and an inbox that silently never receives, so the swap has to OR the old bit array into the new one. The API contract uses 410 (not 404) for expired addresses because semantic precision matters at Confluent: a client needs to distinguish 'my ID is wrong' from 'my session expired.' The TTL mechanism — a background sweeper plus `ON DELETE CASCADE` — is architecturally identical to Kafka topic retention and Iceberg snapshot expiry: the address lifecycle IS the log retention lifecycle."

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Aug 2026 | File created. Type 2, Full System Design. Bloom filter deep dive (Section 8.1) expanded to cover sizing math, deletion problem, and rebuild vs Counting filter trade-off — research shows this took an entire round at Confluent (Hack2Hire, May 2026). |
| Aug 2026 | **Section 7 restructured as a true incremental build-up, Stage 3 added.** Stage 1 promoted from a 4-line text note into a full stage block with its own ASCII diagram (synchronous SMTP → Postgres, ~1M DAU). Stage 2 now wraps the existing data-flow visual with a `WHY KAFKA EARNS ITS PLACE HERE` block and a Section 4 capacity check. **Stage 3 drawn for the first time** — the Cassandra message store the old Stage 2 breaking point already promised but never diagrammed: `messages` keyed by `address_id` with clustering on `(received_at DESC, message_id DESC)`, per-row `USING TTL 600` and TWCS replacing the sweeper entirely, `addresses` deliberately left in Postgres for the UNIQUE constraint. Added `WHY CASSANDRA EARNS ITS PLACE HERE` and a `CEILING OF STAGE 3` (single Redis Bloom instance + single addresses primary at ~3,500 creations/sec) with four ordered next moves tied to Section 5 rows. **Numeric fix:** the Stage 1→2 threshold was stated as ~2,000 SMTP msg/sec, which sits *above* the 10M DAU peak of 1,040 msg/sec from Section 4 — meaning Kafka was never actually justified. Corrected to ~400 msg/sec, derived from the real exhausted resource (200-connection pool ÷ ~500ms SMTP session length), and Stage 2's break at 15K msg/sec now names the DELETE/autovacuum ceiling alongside the WAL ceiling. |
| Aug 2026 | **Stage 2 diagram redrawn — it was materially weaker than Stage 1's.** Six concrete defects fixed: (1) it had abandoned the box-drawing charset used by Stage 1 and Stage 3 for plain-ASCII `\|`/`v`, making the section look inconsistent mid-answer; (2) the Bloom arrow was semantically backwards — it was drawn as `Address Service ──▶ Redis Bloom` sitting on the read path, implying the service *queries* the filter, when in fact Address Service **writes** it on create and SMTP Ingress **reads** it on `RCPT TO`; the arrows are now deliberately asymmetric and the KEY INVARIANT calls that out, because an interviewer who spots a reversed dependency will assume the whole flow is memorised rather than understood; (3) no horizontal scale was visible anywhere despite decoupling being the entire point of the stage — now shows the TCP load balancer, the N-instance stateless SMTP ingress cluster, the 32 Kafka partitions keyed by `address_id`, and the 32-member consumer group; (4) the 3,500 polls/sec inbox read path was described in the walkthrough but completely absent from the diagram — now drawn as step ⑦, which also sets up why Stage 3 splits reads off the write primary; (5) the TTL Sweeper floated as an unconnected box with no arrows — now wired to the Postgres primary with the `- INTERVAL '30 seconds'` grace buffer shown inline; (6) the two flows were drawn as parallel columns that merged ambiguously. Added ①–⑨ step markers and rewrote the walkthrough to reference them, so the diagram and the narration are cross-indexed. Also added two ordering rationales that were implicit: the Bloom write happens *after* the Postgres commit (a filter entry for a failed insert causes a pointless confirming read on every future email to that address), and the 30-second sweeper grace buffer exists to prevent deleting an address microseconds before an in-flight API read completes. Verified at 69 characters max width. |
| Aug 2026 | **Added Part 2.5 — The Two Identifiers.** Reading the note surfaced an unanswered question: every path is `/v1/addresses/{id}` but `POST /v1/addresses` sends no ID, and the file never said where `{id}` came from, how the `f4k2s9` address string was generated, or why the row carries two identifiers at all. All three are now answered up front. **The important one is the security rationale:** every endpoint in this design has `Auth: None` (that is the product — no signup, no identity), so the UUID *is* the credential, a bearer capability granting inbox access, while the address string is public by function because the user pastes it into signup forms. Keying the API on the address would mean anyone who knows your throwaway address — including the site you just handed it to — could read your inbox and steal the confirmation link. Added the two consequences that follow: the UUID must be CSPRNG-backed and never sequential (or `/v1/addresses/1001` walks every live inbox), and it necessarily lands in access logs and `Referer` headers, which is an acceptable residual risk only because of the 10-minute lifetime. Also specified the previously-undefined address generator: 6 chars from a 32-char look-alike-free alphabet (base32 minus `0/O` and `1/l/I`) = 32⁶ ≈ 1.07B, `SecureRandom`, collision chance vs 210K live addresses ≈ 0.02%, with English-steps-then-Java showing insert-blind-and-let-the-UNIQUE-index-arbitrate (never check-then-insert, per Section 13 Mistake 2) and a 3-attempt cap that surfaces a broken generator as a 503 rather than an infinite loop. Flagged the **code-reuse hazard** the note had not raised: because expired rows are deleted, a code can be re-minted for a different user, so in-flight mail for the previous owner lands in the new owner's inbox — a silent cross-user leak — fixed with a `recently_used_codes` Redis set held for one or two TTL windows. |
| Aug 2026 | **Correctness pass — one silent-data-loss bug, two unearned justifications, and ~15 arithmetic/unit errors.** ⚠ **The critical one: the Bloom rebuild swap could produce FALSE NEGATIVES.** `atomicReplace(newFilter.toByteArray())` overwrote the live filter wholesale, so every address created between the rebuild job's `SELECT` at time T and the swap at T+Δ was discarded — live in Postgres, absent from the filter. Mail to those addresses got a Bloom NO → `550 No such user here` → a *permanent* rejection the sending MTA never retries: total, silent mail loss for a live inbox, with no bounce, no log line, no consumer lag, no alert. At 350 creations/sec that is ~35 dead inboxes per rebuild at the quoted 100ms, ~175 at a realistic ~500ms. This flatly contradicted two claims the file made elsewhere ("a false negative is impossible by construction, which is why a NO can be trusted to reject mail outright" in the Stage 2 KEY INVARIANT, and "guarantees zero false negatives" in the Deep Dive definition) — both true of a *single* filter generation, neither surviving a generation swap that drops concurrent writes. Fixed by qualifying both claims, adding a full hazard section with the trace and the magnitude table, and replacing the swap with `unionAndSwap` (OR the live bit array into the new filter inside a Lua script; union cannot lose an element) plus the double-write alternative. Noted that this is the **same delete-then-reuse hazard family Part 2.5 already catches for the address namespace** — the file spotted it there and missed it here, because the pattern is far harder to see inside a "just rebuild it periodically" maintenance job than in a user-facing ID allocator. **Stage 1→2 rests on the wrong reason.** Reason (a), connection-pool exhaustion, priced a Postgres connection as held for the full ~500ms SMTP session — but the file's own text defines that 500ms as "remote-sender round trips + MIME parse," time spent waiting on the network *peer*, and a pool is checked out around DB work. Using the file's own ~15ms Postgres write (Trade-off 1) gives 200 ÷ 0.015 ≈ **12,500 msg/sec**, 12× the 10M DAU peak — so connections never justified Kafka at all. Reason (b) — `250 OK` is an irrevocable durability promise, plus MIME-parser quarantine — is sound and does the real work, so it is now **primary**, (a) is reframed honestly as a naive-implementation artifact, and the **local disk spool** alternative (maildir + fsync + ack, what Postfix has always done) is named up front as the cheaper way to get both, with Kafka earning the difference on fleet-wide replay and stateless hosts. Also resolved the NFR contradiction where a ~500ms *mean* session sat inside a "P99 < 500ms" budget: the NFR now scopes to our own work inside the session. **Bloom filter reframed, not removed** (it stays in full — it consumed most of a May 2026 Confluent round). Added a verdict box up front: at 10M DAU an **exact Redis SET with native per-key TTL** (`SET addr:f4k2s9 live EX 600`, ~10MB) beats the filter on every axis the file itself says matters — zero FPR, deletion free and native, no stale window, no rebuild job, no false-negative-on-swap — and it collapses into the same keyspace as Part 2.5's `recently_used_codes` guard, which the design was otherwise paying for *twice* without reconciling. The filter is now presented as the **100M+ DAU** answer (~2.5MB vs ~100MB *per region*, replicated to every regional SMTP ingress) and as interview preparation. Killed the supporting overclaims: "wasting that capacity leaves no headroom for real reads"; the TL;DR's "millions of concurrent SMTP connections" (Section 4 says 1,040 msg/sec); and "Bloom check is O(k) bit operations — faster than Redis GET," which is self-contradictory since the filter *is* in Redis — 7 `GETBIT`s in a Lua script are slower than one `GET`, and against a ~1ms Postgres read the per-request latency saving is ~zero. The filter saves **capacity**, not time. **Part 2.5:** `ALPHABET` is **31 characters** (23 letters + 8 digits), not 32 — 31⁶ = 887,503,681 ≈ 887M, and "base32 minus look-alikes" is self-refuting since base32 is by definition 32 symbols; the "human-safe" rationale was imported from a different problem (nothing here is hand-transcribed, and the retained set still holds 2/z, 5/s, 6/b, 9/q), so it is now stated modestly. The re-mint hazard dropped its **rate** term: per event 0.02% is small, but 116/sec × 86,400 × (210,000 ÷ 887M) ≈ **~2,370 re-mints/day**, each a potential silent cross-user inbox leak — a paging-worthy incident class, which *strengthens* the guard-set recommendation; also stopped mixing the 120/sec average against a 210K figure derived from the 350/sec peak. `id` is no longer "returned once" (`GET /v1/addresses/{id}` returns it on every read) but "returned by the creation call, unrecoverable from the address"; `localStorage` is no longer "cleared on expiry" (it has no TTL — nothing expires it); the JS snippet gained the missing `.json()`, an explicit `if (resp.status === 410)` branch (`fetch` does not throw on 410) and English steps; and `createAddress` now actually **performs** the per-IP rate-limit check that Part 3 promises as a 429 and Section 9 builds `idx_addresses_ip_created` to serve. **Arithmetic and units:** "100M DAU" is ~10,400 msg/sec (10× Section 4's 1,040), not 3,500 — which was the 10M-DAU *inbox-poll* rate — and no longer contradicts Stage 2's 15K ceiling by 4×; Stage 3 ceiling (ii) deleted (3,500 × 200B address rows = **700 KB/sec**, three orders of magnitude below the 150MB/sec that saturates the same primary, and a B-tree over 2.1M rows is ~3–4 levels and does not "grow") and replaced with the real wall, single-region SMTP geography; 1-hour TTL is **~1.26M** active ≈ 1.5MB, not "~5M"; **2.5MB** not 2.5GB at 100M DAU, and ~100MB not 10GB for the exact set; Counting filter is **250KB → 1MB**, not 1MB → 4MB; a 2.1M-row sequential scan is **~190ms**, not 2 seconds (rows were scaled 10× but time 100× — a seq scan is O(n)); staleness is governed by the rebuild **interval** (5 min = 50% of TTL), not the rebuild **duration**, so the "0.1/600 = 0.017%" line was a category error making the case look ~3,000× better supported than the prose allowed; rebuild cadence unified on **5 minutes** everywhere (was 10 in seven places, 5 in two); "210K fits in Redis (42MB)" separated into the 42MB *Postgres row* footprint vs the ~10MB Redis SET of address strings; the 6GB live-storage figure gained the 2–3× on-disk clause for MVCC dead tuples plus indexes; the 100MB × 1,040 msg/sec = 100GB/sec attachment row reframed as a worst case rather than a forecast; the RBL row no longer prices a `MAIL FROM` check "at SMTP HELO"; the NFR line labels averages vs peaks; and "BloomFilter — Client-held by SMTP ingress … lives in Redis" dropped the contradictory "client-held." **Biggest single correction: the spam double-count.** Section 4 derives 1,040 msg/sec as *legitimate* mail to *live* addresses (3 per session), then three later passages claimed 80% of that same 1,040 was spam to non-existent addresses — deriving the filter's whole value from traffic already allocated to real users. Now stated as 1,040/sec legitimate **plus an assumed ~4,000/sec** of spam, with the read budget re-derived as a table (~8,540 reads/sec uncached vs ~4,580 cached, against a ~10K/sec primary) and the assumption flagged as an assumption — which also shows the conclusion is sensitive to it, since the old double-counted 832/sec implied only ~8% of read capacity saved. **Dead weight removed:** `idx_messages_address_received` is a strict **prefix** of `idx_messages_cursor`, so it served no query the latter could not, while doubling index WAL on every INSERT and giving autovacuum a second B-tree per dead tuple — making the Stage 2 WAL/autovacuum ceiling the file itself identifies arrive *sooner*; dropped, and the schema note that had described the two as if they differed is rewritten. `raw_size_bytes` dropped (written on every row, read by nothing — the limit is enforced pre-insert and the response never exposes it). `message_count` now carries an explicit budget note: no counter column exists, so it is a second `COUNT(*)`, served index-only at ~0.2ms — priced rather than left as an unbudgeted query on a carefully-costed path. Finally, added the **multi-`RCPT TO`** case the diagrams omit: SMTP permits several recipients per session, the Kafka key is the singular `address_id`, so a message to two live addresses is published once per recipient — correct, since each inbox owns a copy that expires on its own TTL, but previously unstated. |
