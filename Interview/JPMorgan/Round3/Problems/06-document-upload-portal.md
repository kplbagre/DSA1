# Document Upload & Validation Portal — JPMC Round 3 (LLD + HLD)

> **JPMC context:** Round 3, reported across two separate LeetCode Discuss threads.
> The whole problem is a test of **one skill: handling third-party async latency gracefully.**
> A document is uploaded, then validated by an external service that takes **2–3 seconds**
> per document. If you try to validate synchronously inside the upload request, you fail
> the interview — the entire design pivots on recognizing that the slow external call must
> be decoupled from the user-facing request.
>
> **Why this problem is different from the others:** Parking Lot and Movie Ticket are a
> *hot-resource race* (one seat, one spot, many claimants). Payment is *money correctness*.
> Document Upload is the **async-ingestion archetype**: accept fast, acknowledge with a
> tracking handle, do the slow work off the request path, and let the caller find out later.
> The SDE-3 signal is that you reach for `202 Accepted + tracking ID` in the first 60 seconds.

---

## Index

| § | Section |
|---|---------|
| §1 | 🎯 Problem Statement |
| §2 | ❓ Clarifying Questions |
| §3a | 🏗️ LLD — Construction Guide (7 Moves) |
| §3b | 🏗️ LLD — Complete Class Diagram |
| §4 | 🧭 Design Decisions |
| §5 | 🔌 Key Interfaces |
| §6 | ⚙️ Code — Three Methods |
| §7 | 🔁 Concurrency |
| §8 | 🧨 Java Depth Probes |
| §9 | 🌐 HLD — 3-Phase Construction Guide |
| §10 | 🏛️ HLD Decisions |
| §11 | 📡 API Design |
| §12 | 🛤️ Happy + Unhappy Paths |
| §13 | 🔧 Fault Tolerance |
| §14 | 🔬 Q&A — Tier-2 JPMC Probes |
| §15 | 🧾 TL;DR |
| §16 | 🔄 Changelog |

---

## §1 — 🎯 Problem Statement

Design a document upload and validation portal. A user uploads a file (KYC document, contract, ID proof). The system stores the raw bytes, then hands the document to an **external validation service** (a third-party API that takes **2–3 seconds** to virus-scan, OCR, and content-check each file). The user must get an immediate acknowledgement with a **tracking handle**, and later learn whether the document passed or failed validation.

**The one-line framing to say out loud in the interview:**
> *"This is an async-ingestion problem. The external validator is slow (2–3s), so I accept
> the upload, return a tracking ID immediately (202 Accepted), and run validation off the
> request path via a worker fleet. My whole design protects one invariant: the slow
> third-party call never blocks the user-facing request, and no document is ever silently
> lost between 'uploaded' and 'validated'."*

---

## §2 — ❓ Clarifying Questions

**Scale**

1. How many documents/day, and what is the peak upload rate? (drives worker fleet size)
2. Average file size? (drives whether bytes can flow through our servers or must go direct-to-S3)
3. Read:write ratio on status polling — how often does a client check status per upload?

**Functional scope**

4. Is validation one external call, or a pipeline of checks (virus scan + format + content policy)?
5. Do we return the validated document back to the user, or just a pass/fail verdict?
6. Is re-upload of a failed document in scope? Versioning of the same logical document?

**Consistency**

7. Is it acceptable for the status endpoint to be a few seconds stale (eventual), or must a poll always reflect the latest state?
8. If the same file is uploaded twice (double-click), is that one document or two?

**Latency SLA**

9. What is the acceptable end-to-end time from upload to a final VALID/INVALID verdict? (30s? 5 min?)
10. What response time is acceptable on the upload request itself? (should be sub-second — it does no slow work)

**Third-party**

11. What is the validator's SLA and timeout? Does it expose a synchronous call, or its own async callback/webhook?
12. Does the validator itself deduplicate, or must we guard against sending the same document twice?
13. What is the validator's throughput cap — can we saturate it, or must we rate-limit our worker fleet?

**Failure model**

14. If validation fails transiently (validator down), is automatic retry acceptable? How many times before we give up and surface FAILED?

**Compliance**

15. Any data-residency, encryption-at-rest, or audit-trail requirement on the stored documents? (KYC/financial docs usually demand all three)

---

## §3a — 🏗️ LLD — Construction Guide (7 Moves)

---

**Move 1 — List domain nouns — don't draw yet**

Read the statement, then do **two passes**: one for nouns that are literally in the problem, one for entities the constraints force you to invent.

**From the statement directly:** Document, User, Validator (external)

**Derived — say the reason out loud for each one:**
- *"The problem says 'immediate acknowledgement with a tracking handle.' I need an object that holds that handle, the upload URL, and when the URL expires."* → **UploadSession**
- *"The validator takes 2–3s, so I cannot call it inline. I need a persistent unit of async work that a background worker can pick up, track attempts on, and claim exclusively so two workers don't both process the same document."* → **ValidationJob**
- *"The problem says the user learns 'pass or fail.' I need to record that outcome and its reason separately from the job tracking."* → **ValidationResult**
- *"The document moves through distinct stops — uploading, uploaded, validating, valid, invalid, failed. That is a finite closed set → enum, not a string."* → **DocumentStatus**

> **Say:** "Most of these come straight from the problem. `UploadSession` and `ValidationJob` are the two I'm adding because the 2–3s constraint forces async — I need a session to hold the URL, and a job to hold the async work."

**Your board at the end of Move 1:**

```
nouns: Document, UploadSession, ValidationJob, ValidationResult,
       DocumentStatus, User, Validator (external), Notification
```

---

**Move 2 — Classify each noun: entity / enum / service**

`DocumentStatus` and `ValidationOutcome` are finite → enums. `Document`, `UploadSession`, `ValidationJob` are entities. Now add **services** — these do not come from the nouns list; they come from asking *"who does the work?"*

- *"Something must handle the upload request and return 202 — that is `DocumentService`."*
- *"Something must do the 2–3s validator call off the request path — that is `ValidationWorker`. It is separate from `DocumentService` because mixing them would let the slow call back up the request handler under load. The split is the thing that solves the problem."*
- *"The validator is a third-party — I abstract it behind `DocumentValidator` so I can inject a fake in unit tests instead of hitting the real 3-second API."*
- *"Bytes go to S3 — I abstract that behind `StorageClient` for the same reason: swap in an in-memory stub for tests."*

> **Say:** "`DocumentStatus` is an enum — a finite, compile-time-checked lifecycle. I split `DocumentService` and `ValidationWorker` at the speed boundary: `DocumentService` must respond in milliseconds; `ValidationWorker` is allowed to be slow. Mixing them breaks that guarantee."

**Your board at the end of Move 2:**

```
enum:    DocumentStatus, ValidationOutcome
entity:  Document, UploadSession, ValidationJob, ValidationResult
service: DocumentValidator (iface), StorageClient (iface),
         NotificationService (iface), DocumentService, ValidationWorker
```

---

**Move 3 — Draw enums first. Explain non-obvious states.**

The lifecycle is the spine of this problem. Draw it and defend the two non-obvious states:

**Why `PENDING_UPLOAD` and not just starting at `UPLOADED`?** Because I insert the metadata row *before* the bytes arrive — that is what makes idempotency work. If a client double-clicks, both requests arrive before either has PUT bytes to S3. With `PENDING_UPLOAD` I can create the row on the first request and return the same row on the second. If I only created the row when S3 confirmed receipt, two requests in that window would race to insert two rows.

**Why `INVALID` and `FAILED` as separate states?** — The interviewer will ask this.

> **Interviewer:** "Why both INVALID and FAILED?"
> **Answer:** "INVALID is a *successful* validation with a negative verdict — the file is a virus, retrying won't help. FAILED is an *unsuccessful* validation — the validator timed out or errored, so a retry might succeed. They lead to different actions: INVALID notifies the user to submit a clean document; FAILED goes to a retry queue then a dead-letter queue."

**Your board at the end of Move 3:**

```
DocumentStatus:
  PENDING_UPLOAD ─▶ UPLOADED ─▶ VALIDATING ─▶ VALID
                                    │ └─────────▶ INVALID  (validator ran; rejected)
                                    └──────────▶ FAILED    (validator errored/timeout)

ValidationOutcome: PASS · REJECT
```

---

**Move 4 — Draw entities smallest → largest. Name what each knows + can do.**

`ValidationResult` (smallest) → `ValidationJob` → `UploadSession` → `Document` (aggregate root).

> **Say:** "The non-obvious field on `Document` is `idempotencyKey` — the content hash + uploader. That is how a double-click becomes one document, not two. The key method is `transition()` — it guards the lifecycle."

> **For `ValidationJob`:** "`claimedBy` and `claimedAt` are there because multiple workers run in parallel. Without a claim, two workers would both pick up the same job and call the validator twice — double spend, double notify. `attempts` is there so a job that keeps failing eventually gives up rather than looping forever. These two fields are what make a naive 'do this later' into a safe async worker."

**Your board at the end of Move 4:**

```
Document
  - documentId, uploaderId, s3Key, contentType, sizeBytes
  - status: DocumentStatus
  - idempotencyKey  (sha256(bytes)+uploaderId)  ← dedup handle
  - uploadedAt, validatedAt
  + transition(DocumentStatus next)

ValidationJob
  - jobId, documentId, attempts, claimedBy, claimedAt
    ↑ claimedBy: which worker owns this job right now (null = unclaimed)
    ↑ attempts:  how many times we've tried (bounds infinite retry)
  + claim(workerId) / complete(outcome) / fail()

UploadSession { sessionId, documentId, presignedUrl, expiresAt }
ValidationResult { jobId, outcome: ValidationOutcome, reason, validatedAt }
```

---

**Move 5 — Identify variable behavior. Extract interfaces.**

Validation is a *pipeline of swappable checks* → `DocumentValidator` (Strategy). Storage is swappable (S3 in prod, in-memory in test) → `StorageClient`. Notification varies (email/webhook) → `NotificationService`.

> **Say:** "I am extracting `DocumentValidator` as an interface because the checks vary and grow — virus scan today, content-policy tomorrow. New check = new class, zero edits to the worker. That is OCP."

**Your board at the end of Move 5:**

```
interface DocumentValidator   { ValidationResult validate(Document d); }
   ├─ VirusScanValidator
   ├─ FormatValidator
   └─ ContentPolicyValidator      (composed in a chain)

interface StorageClient       { String presignedPutUrl(key, ct, ttl); }
interface NotificationService { void notify(userId, docId, status); }
```

---

**Move 6 — Add the orchestrating services last. Their constructor deps = your design.**

`DocumentService` (handles upload-request + status) and `ValidationWorker` (consumes the queue). Their injected dependencies *are* the design.

> **Why two services?** "I split at the speed boundary. `DocumentService` is on the request path — must respond in milliseconds. `ValidationWorker` is off the request path — allowed to be slow. The constructor of each tells you exactly what it can touch: `DocumentService` never holds a reference to the validator, so it physically cannot make the slow call, even accidentally."

> **Say:** "I inject the validator, storage client, and notifier — I do not `new` them. That is DIP, and it is what makes the worker unit-testable with a fake validator instead of the real 3-second one."

**Your board at the end of Move 6:**

```
DocumentService (storage, docRepo, queue)
  + requestUpload(userId, meta) : UploadSession   // 202 path
  + getStatus(documentId)       : DocumentStatus

ValidationWorker (validator, docRepo, jobRepo, notifier)
  + processNext()   // claim → validate → transition → notify
```

---

**Move 7 — Name the hot resource. One sentence tying all locks to it.**

There is **no shared hot resource** the way a seat is. The concurrency concern is different: **two workers must never process the same `ValidationJob`.** The guarded resource is `ValidationJob.claimedBy`.

> **Say:** "The contended resource is the job claim — `ValidationJob.claimedBy`. My whole concurrency strategy is one atomic conditional update: `UPDATE ... WHERE claimed_by IS NULL`. Exactly one worker wins; the rest skip. No application-level mutex needed."

**Your board at the end of Move 7:**

```
HOT RESOURCE: ValidationJob.claimedBy
  guard = atomic conditional claim
          (UPDATE ... WHERE claimed_by IS NULL)
          or SELECT ... FOR UPDATE SKIP LOCKED
  → at most one worker validates a given document
```

---

### 75% Rule — What to Draw First If Time Is Short

```
Priority 1 — must reach (10 min):
  • DocumentStatus lifecycle (incl. INVALID vs FAILED distinction)
  • Document with idempotencyKey + transition()
  • DocumentValidator interface (Strategy pattern)
  • DocumentService.requestUpload — the 202 return path
  • The job-claim concurrency line

Priority 2 — draw if time allows:
  • ValidationJob.claim() with attempts counter
  • UploadSession + pre-signed URL flow
  • NotificationService

Priority 3 — verbally mention, never draw:
  • ValidationResult fields, audit table, encryption-at-rest
```

---

## §3b — 🏗️ LLD — Complete Class Diagram — What You're Building Toward

```
┌─────────────────────────────────────────────────────────────┐
│ «enum» DocumentStatus                                        │
│  PENDING_UPLOAD · UPLOADED · VALIDATING · VALID ·            │
│  INVALID · FAILED                                            │
└─────────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────────┐
│ «enum» ValidationOutcome   PASS · REJECT                     │
└─────────────────────────────────────────────────────────────┘

┌───────────────────────────────────────────┐
│ Document                  «aggregate root» │
├───────────────────────────────────────────┤
│ - documentId: String                      │
│ - uploaderId: String                      │
│ - s3Key: String                           │
│ - contentType: String                     │
│ - sizeBytes: long                         │
│ - status: DocumentStatus                  │
│ - idempotencyKey: String  ← dedup handle  │
│ - uploadedAt, validatedAt: Instant        │
├───────────────────────────────────────────┤
│ + transition(next: DocumentStatus): void  │
└───────────────────┬───────────────────────┘
                    │ 1
                    │ has 0..*
                    ▼
┌──────────────────────────────────────┐      ┌─────────────────────────────┐
│ ValidationJob                        │ 1──1 │ ValidationResult            │
├──────────────────────────────────────┤      ├─────────────────────────────┤
│ - jobId: String                      │      │ - jobId: String             │
│ - documentId: String                 │      │ - outcome: ValidationOutcome│
│ - attempts: int                      │      │ - reason: String            │
│ - claimedBy: String  ← HOT (CAS)    │      │ - validatedAt: Instant      │
│ - claimedAt: Instant                 │      └─────────────────────────────┘
├──────────────────────────────────────┤
│ + claim(workerId): boolean           │
│ + complete(outcome): void            │
│ + fail(): void                       │
└──────────────────────────────────────┘

┌──────────────────────────────┐
│ UploadSession                │
├──────────────────────────────┤
│ - sessionId: String          │
│ - documentId: String         │
│ - presignedUrl: String       │
│ - expiresAt: Instant         │
└──────────────────────────────┘

┌────────────────────────────────────┐     ┌───────────────────────────────┐
│ «interface» DocumentValidator      │◀────│ VirusScanValidator            │
│ + validate(Document)               │◀────│ FormatValidator               │
│     : ValidationResult             │◀────│ ContentPolicyValidator        │
└────────────────────────────────────┘     └───────────────────────────────┘

┌────────────────────────────────────┐  ┌───────────────────────────────────┐
│ «interface» StorageClient          │  │ «interface» NotificationService   │
│ + presignedPutUrl(key,ct,ttl):Str  │  │ + notify(userId,docId,status):void│
└────────────────────────────────────┘  └───────────────────────────────────┘

┌──────────────────────────────────────────────────────────────┐
│ DocumentService   deps: StorageClient, DocumentRepo, Queue    │
│  + requestUpload(userId, meta): UploadSession                 │
│  + markUploaded(documentId): void     (S3 event callback)     │
│  + getStatus(documentId): DocumentStatus                      │
└──────────────────────────────────────────────────────────────┘
┌──────────────────────────────────────────────────────────────┐
│ ValidationWorker                                              │
│  deps: DocumentValidator, DocumentRepo, JobRepo,              │
│        NotificationService                                    │
│  + processNext(): void  (claim → validate → transition →      │
│                           notify)                             │
└──────────────────────────────────────────────────────────────┘

KEY INVARIANT: the slow (2–3s) DocumentValidator call lives ONLY inside
ValidationWorker.processNext — never on the DocumentService request path.
A document always ends in exactly one terminal state: VALID, INVALID, or FAILED.
```

---

## §4 — 🧭 LLD — Design Decisions

| Decision | Why this | What I rejected and why |
|---|---|---|
| **202 Accepted + tracking ID, not synchronous validation** | The validator takes 2–3s; blocking the request thread exhausts the pool under load | Sync validate-in-request — 2–3s × burst = thread starvation → 503s; the whole reason this problem exists |
| **State machine with `transition()` guard** | A document has a strict lifecycle; illegal jumps (e.g., VALID → UPLOADED) must throw | Free-form status setter — any bug can push a validated doc back to VALIDATING |
| **`INVALID` separate from `FAILED`** | REJECT is terminal (retry is pointless); validator-error is retryable | One "not valid" status — you'd retry viruses forever and give up on transient blips |
| **`idempotencyKey = sha256(bytes)+uploader`** | Double-click or client retry must not create two documents or double-spend validator quota | Trust the client to dedupe — clients retry on timeout; server must dedupe unconditionally |
| **`DocumentValidator` as a Strategy chain** | Checks grow over time (virus → format → policy); each is independent | if-else pipeline in the worker — every new check edits the worker (OCP violation) |
| **Pre-signed S3 URL — bytes go direct to S3** | Large files must not stream through our app servers (bandwidth + memory) | Multipart through the API — app servers become a data plane, memory blows up on big files |
| **Atomic job claim (`claimedBy` CAS / SKIP LOCKED)** | Two workers pulling the same job = double validator spend + double notify | Unlocked queue read — classic double-processing race at scale |

---

## §5 — 🔌 LLD — Key Interfaces

| Interface | Contract |
|---|---|
| `DocumentValidator` | Runs one validation check; `validate(Document)` → `ValidationResult`. Implementations are chained. |
| `StorageClient` | Issues a time-boxed direct-upload URL; `presignedPutUrl(key, contentType, ttlSeconds)`. |
| `NotificationService` | Fire-and-forget terminal-state notice; `notify(userId, documentId, status)`. |

```java
public interface DocumentValidator {
    // Runs one check against the stored document; never mutates the document.
    ValidationResult validate(Document document);
}

public interface StorageClient {
    // Returns a pre-signed URL the client PUTs bytes to directly;
    // expires after ttlSeconds.
    String presignedPutUrl(String key, String contentType, int ttlSeconds);
}

public interface NotificationService {
    // Fire-and-forget notice of a terminal document state
    // (VALID / INVALID / FAILED).
    void notify(String userId, String documentId, DocumentStatus status);
}
```

---

## §6 — ⚙️ LLD — Code to Write

Three methods carry the whole problem: the **202 accept path**, the **lifecycle guard**, and the **atomic job claim**. Nothing else needs code.

---

### 1. The async accept path — `requestUpload` (returns immediately; does no slow work)

**Steps in plain English:**

1. **Compute the idempotency key** — hash the declared content + uploader so a retry maps to the same document.
2. **Short-circuit on duplicate** — if a document with that key exists, return its existing session; do not create a second.
3. **Insert metadata** in status `PENDING_UPLOAD` — the row exists before the bytes do.
4. **Mint a pre-signed URL** so the client uploads bytes straight to S3.
5. **Return 202** with the documentId (the tracking handle) — no validation happens here.

```java
public UploadSession requestUpload(String userId, UploadMeta meta) {
    // Step 1 — idempotency key: content hash + uploader → stable across retries
    String iKey = Hashing.sha256(meta.contentHash() + ":" + userId);

    // Step 2 — short-circuit: same key means same logical document
    Optional<Document> existing = docRepo.findByIdempotencyKey(iKey);
    if (existing.isPresent()) {
        return sessionRepo.activeSessionFor(existing.get().getDocumentId());
    }

    // Step 3 — persist metadata BEFORE bytes arrive; status = PENDING_UPLOAD
    String documentId = IdGenerator.newId();
    String s3Key = "docs/" + userId + "/" + documentId;
    Document doc = Document.create(
        documentId, userId, s3Key,
        meta.contentType(), meta.sizeBytes(), iKey);
    docRepo.insert(doc);

    // Step 4 — pre-signed URL: bytes go direct to S3, never through us
    String url = storage.presignedPutUrl(s3Key, meta.contentType(), 300);

    // Step 5 — 202 Accepted: documentId is the polling handle; no validation here
    UploadSession session = UploadSession.of(documentId, url,
        Instant.now().plusSeconds(300));
    sessionRepo.insert(session);
    return session;
}
```

---

### 2. The lifecycle guard — `Document.transition`

**Steps in plain English:**

1. **Look up the legal next-states** for the current status.
2. **Reject** an illegal jump with a clear exception.
3. **Apply** the transition and stamp `validatedAt` on terminal states.

```java
private static final Map<DocumentStatus, Set<DocumentStatus>> LEGAL = Map.of(
    DocumentStatus.PENDING_UPLOAD, Set.of(DocumentStatus.UPLOADED),
    DocumentStatus.UPLOADED,       Set.of(DocumentStatus.VALIDATING),
    DocumentStatus.VALIDATING,     Set.of(
                                       DocumentStatus.VALID,
                                       DocumentStatus.INVALID,
                                       DocumentStatus.FAILED),
    DocumentStatus.FAILED,         Set.of(DocumentStatus.VALIDATING)
);

public void transition(DocumentStatus next) {
    // Step 1 + 2 — guard: only declared transitions are legal
    Set<DocumentStatus> allowed =
        LEGAL.getOrDefault(this.status, Set.of());
    if (!allowed.contains(next)) {
        throw new IllegalStateException(
            "Illegal transition: " + this.status + " -> " + next);
    }
    // Step 3 — apply; stamp time on a terminal verdict
    this.status = next;
    if (next == DocumentStatus.VALID || next == DocumentStatus.INVALID) {
        this.validatedAt = Instant.now();
    }
}
```

---

### 3. The atomic job claim — `ValidationJob.claim`

**Steps in plain English:**

1. **Attempt an atomic conditional update** — set `claimedBy` only if it is still null.
2. **Interpret the row-count** — 1 means you won the claim, 0 means another worker already has it.
3. **Only the winner** proceeds to the slow validator call.

```java
// thread-safe: the UPDATE is the lock; no application-level mutex needed
public boolean claim(String workerId) {
    // Step 1 + 2 — CAS at the DB: exactly one worker flips claimed_by from NULL
    int rows = jobRepo.update(
        "UPDATE validation_job " +
        "SET claimed_by = ?, claimed_at = now() " +
        "WHERE job_id = ? AND claimed_by IS NULL",
        workerId, this.jobId);
    // Step 3 — 1 row => this worker owns the job; 0 => someone else does
    return rows == 1;
}
```

---

## §7 — 🔁 LLD — Concurrency

| Shared field | What breaks without a guard | Fix |
|---|---|---|
| `ValidationJob.claimedBy` | Two workers pull the same job → validator charged twice, user notified twice | Atomic conditional claim (`UPDATE ... WHERE claimed_by IS NULL`) or `SELECT ... FOR UPDATE SKIP LOCKED` |
| `Document.status` | Two events (S3-upload + a stale retry) transition concurrently → lost update | `transition()` runs inside the same DB tx as the claim; `@Version` optimistic lock on the row |
| `document(idempotencyKey)` | Two simultaneous uploads of the same file both INSERT | `UNIQUE(idempotency_key)` DB constraint — the second insert fails, caller returns existing |

**Critical section — the claim is the whole concurrency story:**

```java
// thread-safe: DB-level CAS is the mutex; no synchronized block needed
public void processNext() {
    // SELECT ... FOR UPDATE SKIP LOCKED: one row per worker, no blocking
    ValidationJob job = jobRepo.nextUnclaimed();
    if (job == null || !job.claim(workerId)) {
        // lost the race, or queue empty — skip, do NOT validate
        return;
    }
    // exactly one worker reaches here for this jobId
    Document doc = docRepo.load(job.getDocumentId());
    doc.transition(DocumentStatus.VALIDATING);
    docRepo.save(doc);
    // slow validator call happens AFTER the claim is safely won
}
```

**Trade-off:** the claim serializes only the *job acquisition*, not the validation itself — the 2–3s validator call runs fully parallel across the fleet. The actual bottleneck becomes the validator's throughput cap, which is why §9 rate-limits the worker fleet rather than the claim.

---

## §8 — 🧨 Java Depth Probes

| What you say in design | What they immediately ask | Your answer |
|---|---|---|
| "workers pull from a queue" | "How do you stop two workers processing the same job?" | Atomic conditional `UPDATE ... WHERE claimed_by IS NULL`. The DB is the arbiter — exactly one worker gets 1 row updated; the rest get 0 and skip. |
| "pre-signed S3 URL" | "What if the URL expires before the client uploads?" | TTL is 5 min. On expiry the client calls `requestUpload` again; the idempotency key maps it to the same `PENDING_UPLOAD` document, and we mint a fresh URL — no duplicate row. |
| "async via Kafka/SQS" | "What if the S3 event is never delivered?" | A sweeper scans `PENDING_UPLOAD`/`UPLOADED` rows older than N minutes and re-enqueues them. The queue is an optimization; the DB row is the source of truth. |
| "circuit breaker on the validator" | "What are the breaker states?" | CLOSED (calls flow), OPEN (validator failing → fail fast, jobs park in retry), HALF-OPEN (probe a few calls; if they pass, close). Resilience4j handles this. |
| "retry then dead-letter" | "How many retries, and why not infinite?" | 3 attempts with exponential backoff. Infinite retries on a permanently-broken document burns validator quota forever; after 3 we mark FAILED and surface it for manual review. |
| "idempotency key includes uploaderId" | "Two users upload the same file — one document or two?" | Two. The key includes the uploaderId, so identical bytes from different users are distinct documents — each user owns their own KYC record. |

---

## §9 — 🌐 HLD — How to Build This Diagram in the Interview — 3 Phases

### Phase 1 — Numbers First (≈2 min)

```
DAU                          100K uploaders
Docs/user/day                ~3 average     → 300K documents/day
Active upload window         8 h (28,800 s)
Avg upload rate              300K / 28,800          ≈ 10 uploads/sec
Peak upload rate (5× spike)                         ≈ 50 uploads/sec
Validator latency            2–3 s/doc (external, fixed)
Concurrent validations       50/sec × 2.5 s         ≈ 125 in-flight
  → worker fleet             ~150 workers (I/O-bound; virtual threads work)
Status polls                 ~4 polls/upload → 1.2M/day ≈ 70/sec peak
Avg file size                ~2 MB
Upload byte throughput       50/sec × 2 MB          = 100 MB/sec
Metadata row                 ~300 B × 300K/day × 365 ≈ 33 GB/year
```

**What the numbers force:**
- **125 concurrent 2–3s calls** → a **worker fleet off the request path** (not sync).
- **100 MB/sec of bytes** → **pre-signed S3 direct upload** (app servers cannot be the data plane).
- **70 status polls/sec** → a **Redis status cache** (do not hit MySQL for every poll).
- **33 GB/year metadata** → **one MySQL, no sharding**. (Do not add sharding that the numbers do not demand.)

---

### Phase 2 — Skeleton: The Simplest System That Could Work (≈3 min)

```
── Skeleton: Day-One System ──────────────────────────────────────

   ┌──────────────────────────────────────────────────┐
   │  Client   Web · Mobile                            │
   └──────────────────────┬───────────────────────────┘
                          │ HTTPS
   ┌──────────────────────▼───────────────────────────┐
   │  DocumentService  (single service)                │
   │   - requestUpload → presigned URL + 202           │
   │   - S3 event handler → validate INLINE (2–3s!)    │
   └──────┬───────────────────────────────┬────────────┘
          │ presigned PUT (direct)         │ synchronous
          ▼                                ▼
   ┌───────────────────┐        ┌──────────────────────────────────┐
   │  S3 (file bytes)  │        │  ThirdPartyValidator (2–3s)       │
   └───────────────────┘        └──────────────────────────────────┘
          │ metadata + status
          ▼
   ┌─────────────────────────────────────────────────────────────────┐
   │  MySQL   document · validation_job    ← DocumentService          │
   └─────────────────────────────────────────────────────────────────┘

BREAKING POINT:
  (a) S3-event handler validates INLINE (2–3s) — at 50 uploads/sec the thread
      pool exhausts in seconds → 503s on the upload itself.
  (b) Validator timeout leaves the document stuck in VALIDATING forever —
      no retry, no dead-letter, no recovery.
  (c) 70 status polls/sec hit MySQL for a value that changes once — pointless
      read load on the primary.
  (d) If the S3 event is dropped, the document sits in UPLOADED permanently —
      nothing ever validates it.
══════════════════════════════════════════════════════════════════
```

---

### Phase 3 — Upgrade It: One Fix per Pain Point (≈5 min)

```
PAIN POINT (a) → Add a Queue (Kafka / SQS) + dedicated ValidationWorker fleet
  Why this works: the 2–3s call moves OFF the request path entirely.
  Upload returns 202 in milliseconds; ~150 workers drain the queue in
  parallel, limited only by the validator's own throughput cap.

PAIN POINT (b) → Add retry-with-backoff + Dead-Letter Queue + circuit breaker
  Why this works: a transient validator failure retries (3× exponential
  backoff); a permanent failure lands in the DLQ and the document goes
  FAILED (not stuck). The circuit breaker stops hammering a down validator.

PAIN POINT (c) → Add Redis status cache (key = doc:{id}:status, TTL 5s)
  Why this works: 70 polls/sec served from memory; MySQL sees ~1 read/doc
  on a cache miss. Status is write-through on each transition.

PAIN POINT (d) → Add a sweeper (scheduled) over stale PENDING_UPLOAD/UPLOADED rows
  Why this works: the DB row is the source of truth; the sweeper re-enqueues
  anything the event stream dropped. The queue becomes an optimization, not
  a single point of silent loss.
```

---

### ✅ Production Diagram — What You're Building Toward

```
── Production: All Upgrades Applied ──────────────────────────────

   ┌────────────────────────────────────────────────┐
   │  Client   Web · Mobile                          │
   └─────────────────────┬──────────────────────────┘
                         │ HTTPS
   ┌─────────────────────▼────────────────────────────────────────┐
   │  API Gateway  (JWT · rate-limit · TLS · routing)             │
   └──────┬────────────────────────────────────────┬─────────────┘
          │ GET /documents/{id}/status              │ POST /documents
          │                                         │ + S3 event → markUploaded
   ┌──────▼────────────────┐   ┌─────────────────────▼──────────────────┐
   │  StatusService        │   │  DocumentService                       │
   │  (read-heavy poll)    │   │  requestUpload → presigned URL + 202   │
   │                       │   │  markUploaded  → enqueue job           │
   └──────┬────────────────┘   └─────┬───────────────────────┬──────────┘
          │ GET (cache-aside)        │ presigned PUT          │ INSERT / UPDATE
          ▼                          ▼ (bytes direct)         ▼
   ┌────────────────────────────┐  ┌───────────────┐   ┌─────────────────────┐
   │  Redis                     │  │  S3            │   │  MySQL (ACID)       │
   │  doc:{id}:status → status  │  │  file bytes    │   │  document ·         │
   │    · TTL 5s                │  └───────┬────────┘   │  validation_job     │
   │    ← StatusService (read)  │          │ Object-     │  ← DocumentService  │
   │    ← DocumentSvc (write)   │          │ Created evt │  ← StatusService    │
   └───────────┬────────────────┘          └──────┬──────└──────────┬──────────┘
               │ cache miss                        │                 │
               └──────────────────────────▶ MySQL  │  enqueue        │
                                                   ▼                ▼
                               ┌────────────────────────────────────────────┐
                               │  Queue  Kafka  (topic: doc-uploaded,        │
                               │  key = documentId)                          │
                               │   └──▶ ValidationWorker fleet (~150)         │
                               │         claim SKIP LOCKED → validate         │
                               │         → transition → write status          │
                               └────────────────────┬───────────────────────┘
                                                    │ calls (5s timeout)
                               ┌────────────────────▼───────────────────────┐
                               │  ThirdPartyValidator (2–3s, circuit breaker)│
                               │   fail → retry 3× backoff → DLQ → FAILED   │
                               └────────────────────┬───────────────────────┘
                                                    │ terminal state event
                               ┌────────────────────▼───────────────────────┐
                               │  NotificationWorker                         │
                               │   email / webhook: VALID · INVALID · FAILED │
                               └────────────────────────────────────────────┘

KEY INVARIANT: the 2–3s validator call executes ONLY inside the worker fleet,
never on the upload or status path. Every document reaches exactly one terminal
state (VALID / INVALID / FAILED); a dropped event is recovered by the sweeper,
so no document is silently lost.
══════════════════════════════════════════════════════════════════
```

---

## §10 — 🏛️ HLD — Decisions

| Component | Why chosen | What I rejected and why |
|---|---|---|
| **Pre-signed S3 URL (direct upload)** | 100 MB/sec of bytes must skip our app servers | Multipart through the API — app tier becomes a data plane; memory + bandwidth blow up |
| **Queue + worker fleet** | Decouples the 2–3s validator from the request; parallelism absorbs bursts | Sync validation in the handler — thread-pool exhaustion at 50 uploads/sec |
| **`SELECT … FOR UPDATE SKIP LOCKED`** | Cheap, correct single-job-per-worker without an extra system | Redis lock for claim — works, but adds a dependency the DB already covers at this scale |
| **Redis status cache (TTL 5s)** | 70 polls/sec served from memory; status changes once per document lifecycle | Poll MySQL directly — needless load on a value that rarely changes |
| **Retry + DLQ + circuit breaker** | Transient validator failures self-heal; permanent ones surface as FAILED | Infinite retry — burns validator quota, hides permanent failures |
| **Single MySQL, no sharding** | 33 GB/year metadata fits comfortably | Sharding — unjustified complexity; the numbers do not demand it |
| **Sweeper over stale rows** | Guarantees no document is lost if an event is dropped | Trust the queue as the source of truth — dropped message = silently stuck document |

---

## §11 — 📡 HLD — API Design

```
POST /v1/documents
X-Idempotency-Key: <sha256(content)+uploader>   ← client sends; server dedupes
Authorization: Bearer <token>

Request:  { fileName, contentType, sizeBytes, contentHash }
Response: 202 Accepted
          {
            documentId : "doc_9f3a…",   ← the polling handle
            uploadUrl  : "https://s3…/docs/…?X-Amz-Signature=…",
            expiresAt  : "2026-08-17T10:05:00Z",
            status     : "PENDING_UPLOAD"
          }

// Client then PUTs raw bytes directly to uploadUrl (S3) — bytes never touch us.
// Idempotency: a repeat POST with the same key returns the SAME documentId
// and a fresh uploadUrl — never a second document.
```

```
GET /v1/documents/{documentId}/status
Authorization: Bearer <token>

Response: 200 OK
          { documentId, status: "VALIDATING", updatedAt }
          // terminal: { status: "VALID" }
          //           { status: "INVALID", reason: "virus detected" }
          //           { status: "FAILED",  reason: "validator timeout x3" }

// Served from Redis (TTL 5s); cache miss falls through to MySQL.
```

Optional push alternative:
```
POST {callbackUrl}   ← client-registered webhook; fired on terminal state
Body: { documentId, status, reason?, validatedAt }
```

---

## §12 — 🛤️ HLD — Happy + Unhappy Paths

**Happy path:**
1. Client → `POST /documents` → DocumentService computes idempotency key, inserts `PENDING_UPLOAD`, returns 202 + pre-signed URL + documentId.
2. Client PUTs bytes directly to S3 (sub-second, off our servers).
3. S3 emits `ObjectCreated` → DocumentService `markUploaded` → status `UPLOADED` → enqueue documentId to Kafka.
4. A ValidationWorker claims the job (`SKIP LOCKED`), transitions document to `VALIDATING`.
5. Worker calls ThirdPartyValidator (2–3s, 5s timeout) → `PASS`.
6. Worker transitions to `VALID`, write-through updates Redis status, emits terminal event.
7. NotificationWorker emails or webhooks the user. Client's next poll returns `VALID` from Redis.

**Unhappy path — validator timeout (transient failure):**
→ Validator exceeds 5s → circuit breaker notes the failure
→ Worker does NOT transition to a terminal state; job re-queued with exponential backoff (attempt 2, 3)
→ If a retry passes → `VALID`/`INVALID` as normal
→ If all 3 attempts fail → job to DLQ, document → `FAILED`, user notified "please retry upload"

**Unhappy path — duplicate upload (double-click / client retry):**
→ Two `POST /documents` with the same idempotency key arrive together
→ `UNIQUE(idempotency_key)` lets exactly one INSERT win
→ The loser catches the constraint violation → returns the existing documentId
→ One document, one validation, validator quota charged once

**Unhappy path — dropped S3 event:**
→ `ObjectCreated` never arrives → document stuck in `UPLOADED`
→ Sweeper (runs every N min) finds `UPLOADED` rows with no job progress → re-enqueues
→ Worker picks it up normally; no document is silently lost

**Unhappy path — worker crash mid-validation:**
→ Worker claims job, transitions to `VALIDATING`, then the pod dies before completing
→ `claimed_at` ages past the lease threshold → sweeper resets `claimed_by = NULL`
→ Another worker re-claims; `attempts` counter prevents infinite reprocessing

---

## §13 — 🔧 HLD — Fault Tolerance

| External call | What breaks | What you add |
|---|---|---|
| ThirdPartyValidator | Timeout / 5xx → document stuck VALIDATING | 5s timeout + circuit breaker; retry 3× exponential backoff → DLQ → `FAILED` |
| S3 (upload URL) | Pre-signed URL expires before client PUTs | 5-min TTL; client re-requests → idempotency maps to same document, fresh URL minted |
| S3 (ObjectCreated event) | Event dropped → stuck UPLOADED | Sweeper re-enqueues stale `UPLOADED` rows; DB row is the source of truth |
| Kafka | Broker down → jobs not delivered | DB row persists; sweeper re-enqueues; workers are idempotent on `documentId` |
| Redis (status cache) | Node down → status reads fail | Cache-aside: on Redis miss/error, fall through to MySQL (correct, just slower) |
| NotificationService | Email/webhook down → user not told | Terminal state durable in MySQL; notification retried from its own DLQ |

> *"Once the happy path works, I ask: what happens if THIS call takes 10× longer or fails permanently? For the validator that question is the entire design — it is why validation lives behind a queue with a breaker and a DLQ, not on the request thread."*

---

## §14 — 🔬 Q&A — Tier-2 JPMC Probes

### Q: "Walk me through what happens the instant a user clicks Upload."
> `POST /documents` returns in milliseconds with a documentId and a pre-signed S3 URL — it does no validation. The client uploads bytes straight to S3. An S3 event enqueues the documentId; a worker fleet validates off the request path. The user polls `GET /status` (served from Redis) or receives a webhook. The upload request never waits on the 2–3s validator.

### Q: "The validator takes 2–3 seconds. Why not just call it in the request and return the result?"
> At 50 uploads/sec, each holding a thread for 2–3s, I would need 125+ live threads just for validation and the pool would exhaust in seconds → 503s. Decoupling via a queue lets the upload response stay sub-second while ~150 workers absorb the validator latency in parallel.

### Q (Tier-2): "A worker claims a job, transitions to VALIDATING, then the pod dies. What state is the document in, and who fixes it?"
> It is stuck in VALIDATING with a stale `claimedBy`. I put a **lease** on the claim: `claimedAt` plus a threshold. A sweeper resets `claimedBy = NULL` on expired leases so another worker re-claims. The `attempts` counter bounds reprocessing so a poison document eventually lands in the DLQ as FAILED rather than looping forever.

### Q (Tier-2): "How does your idempotency survive a client that retries because it never got my 202 response?"
> The idempotency key is `sha256(content)+uploaderId`, enforced by a `UNIQUE` DB constraint. The first request created the document; the retry hits the constraint, and I return the existing documentId with a fresh pre-signed URL. The client cannot create a second document or double-spend validator quota, regardless of how many times it retries.

### Q (Tier-2): "Your status endpoint reads from Redis with a 5s TTL. Can a user ever see VALIDATING after the document is actually VALID?"
> Yes — up to the TTL, worst case ~5s. That is acceptable: the status is monotonic toward a terminal state and I write-through Redis on each transition, so the stale window is small. If the product needed zero staleness I would drop the TTL and invalidate on write, or push via webhook instead of poll — but for a validation portal a few seconds is fine.

### Q (Tier-2): "The third-party validator goes fully down for 10 minutes. What does a user experience?"
> The circuit breaker opens after a failure threshold, so workers fail fast instead of piling up 5s timeouts. Jobs stay queued (not lost) and retry with backoff. Users see `VALIDATING` longer than usual but never an error on upload. When the breaker half-opens and probes succeed, the backlog drains automatically. Only documents exhausting 3 retries flip to FAILED.

### Q: "Why store metadata in MySQL but bytes in S3?"
> Bytes are large (2 MB avg, 100 MB/sec aggregate) and need cheap durable blob storage with direct client upload — S3. Metadata is small, relational, and queried by status — MySQL, 33 GB/year, no sharding needed. Mixing them would either bloat the DB or lose queryability.

---

## §15 — 🧾 TL;DR — 30-Second Pitch

> "This is the async-ingestion archetype. The external validator takes 2–3 seconds, so the
> upload request must not wait on it. I return **202 Accepted with a documentId** as the
> tracking handle, give the client a **pre-signed S3 URL** so bytes never touch my app
> servers, then validate **off the request path** via a worker fleet draining a queue.
> The document is a **state machine** — PENDING_UPLOAD → UPLOADED → VALIDATING →
> VALID / INVALID / FAILED — and I keep INVALID (terminal reject) separate from FAILED
> (retryable error). Concurrency is one atomic job claim (`SKIP LOCKED`) so two workers
> never double-process. The validator gets a **5s timeout + circuit breaker + retry/DLQ**,
> and a **sweeper** guarantees no document is silently lost if an event is dropped. The one
> thing I want to confirm first: does the validator expose a sync call or its own webhook?
> That decides whether the worker blocks-and-polls or registers a callback."

---

## §16 — 🔄 Changelog

| Date | Change |
|------|--------|
| Aug 17, 2026 | Note created. JPMC Round 3 async-ingestion archetype (2 LeetCode Discuss reports). Full 16-section arc: 202-accept path + pre-signed S3 + worker fleet + state machine (INVALID vs FAILED) + atomic `SKIP LOCKED` claim + circuit-breaker/DLQ + sweeper recovery. Confluent single-column HLD; 3-phase construction; numbers force S3-direct, worker fleet, Redis poll cache, single MySQL. |
| Aug 17, 2026 | §3a revised: all 7 moves now include derivation reasoning — every non-obvious entity explains the constraint that forces it (`UploadSession` ← tracking handle req, `ValidationJob` ← 2–3s async constraint, `PENDING_UPLOAD` ← idempotency before bytes arrive, `claimedBy/attempts` ← parallel worker safety). Two-service split justified at the speed boundary. |
