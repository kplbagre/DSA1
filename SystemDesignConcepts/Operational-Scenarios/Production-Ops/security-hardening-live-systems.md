# Operational Scenario: Security Hardening Live Systems

> **When this appears in an interview:** Interviewer says "how would you add authentication to existing unauthenticated endpoints?" or "your secrets aren't rotated — how do you fix that without taking the service down?" or "how do you add rate limiting to a system that doesn't have it?" The keyword is any security control added to a **live system** that already has users.
> **Patterns used:** Feature Flag Gating (`14-feature-flag-gating.md`) for observe-then-enforce rollouts. Expand-Migrate-Contract (`11-expand-migrate-contract.md`) for the expand-enforce pattern.

---

## 🎯 The Situation

Security wasn't fully built into the system on day one. Now you have real users and real traffic, and you need to add a control that the system was never designed for. You cannot take it down. You cannot break existing callers.

**Classic triggers in interviews:**
- "Our write API has no authentication — how do you add auth without breaking existing callers?"
- "Our DB password hasn't been rotated in 3 years — how do you rotate it without downtime?"
- "We have no rate limiting and we're getting abused — how do you add it safely?"
- "We have no audit log and compliance requires one — how do you add it?"
- "Walk me through adding any cross-cutting security concern to a live system."

---

## 🧠 The Decision You Make First

Before anything else, ask one clarifying question:

> *"Which type of security control are we adding — authentication, secrets rotation, rate limiting, or audit logging? And are there existing callers who depend on the current (unsecured) behaviour?"*

The answer determines your expand-enforce timeline:

| Control | Existing callers at risk? | Migration window needed? |
|---|---|---|
| Auth on unauthenticated endpoint | Yes — callers without credentials will break | Yes — expand (optional auth) before enforce (required auth) |
| Secrets rotation | Yes — callers using old secret will break if rotated too fast | Yes — dual-secret window |
| Rate limiting | No — callers aren't broken, just throttled | Shorter — observe → configure → enforce |
| Audit logging | No — callers are unaffected, it's additive | None — can add without caller migration |

**The universal rule:** *Every security control added to a live system is a migration. Use the expand-enforce pattern — add the capability before making it mandatory.*

---

## 🎨 Visual — The Expand-Enforce Pattern (Universal)

> **Before:** security control absent, callers assume no auth/limits/rotation needed, live users unaffected by the absence.
> **After:** control enforced, zero caller breakage during rollout, metric confirmation before enforcement.

```
WRONG WAY — one-shot enforcement (breaks existing callers):
──────────────────────────────────────────────────────────────
  Day 1: Deploy "auth required"
         → All callers without credentials → 401
         → Caller teams scramble to add credentials
         → Production broken for hours/days

RIGHT WAY — expand-enforce (zero breakage):
──────────────────────────────────────────────────────────────

PHASE A: EXPAND — Add the capability (callers unaffected)
  → Auth accepted but NOT required
  → Log who is calling without credentials
  → Send warning headers: "Authentication will be required on 2026-08-01"
  → Caller teams have time to migrate at their own pace

PHASE B: OBSERVE — Confirm all callers have migrated
  → Metric: "unauthenticated call rate" → should trend toward zero
  → Alert if callers still calling without auth
  → Do NOT move to enforce until metric is at zero for N days

PHASE C: ENFORCE — Make it mandatory
  → Deploy "auth required"
  → Zero-caller breakage: no legitimate caller is still unauthenticated
  → Unexpected 401s = misconfigured caller, not a design error

KEY INVARIANT:
   You cannot skip Phase B and jump from Expand to Enforce.
   Phase B is not just "wait a week" — it is "confirm zero unauthenticated
   callers" using the metric. Without the metric, you don't know if it's
   safe to enforce.
```

---

## 🗂️ The 4 Security Hardening Scenarios

---

### Scenario 1 — Secrets Rotation (API Keys, DB Passwords, JWT Signing Keys)

**Say in interview:**
> *"I use a dual-secret window. I create the new secret alongside the old one — both accepted simultaneously. I roll out all service instances to use the new secret and verify in the connection logs that every instance has picked it up. Only then do I revoke the old secret. Zero downtime: the transition window is just deployment time, and both secrets are valid throughout."*

**The problem:** Your DB password has been the same for 3 years. If it leaks, attackers have permanent access. You need to rotate it without any service downtime.

**The naive (wrong) approach:**

```
Wrong:
  Step 1: Change the DB password → all service instances immediately lose DB connections
  Step 2: Update secret in your secret store
  Step 3: Deploy services to read new secret → services were down for the deployment gap
  
  Downtime window = time between Step 1 and Step 3.
  If deployment takes 10 minutes, that's 10 minutes of DB connection failures.
```

**The correct approach — dual-secret window:**

```
Step 1: Add the new secret alongside the old one
        → In Vault / AWS Secrets Manager: create new version of the secret
        → DB: CREATE USER app_db_user_v2 WITH PASSWORD 'new-password';
               GRANT same permissions as v1 user
        → Both old secret (v1) and new secret (v2) are now valid at the same time
        → No service is broken yet

Step 2: Update all services to use the new secret
        → Update Vault secret reference / secret store path to point to v2
        → Roll out a new deployment (or trigger a restart to pick up new secret)
        → Verify all instances have picked up the new secret:
            check DB connection logs — new connections should be using v2 credentials

Step 3: Remove the old secret
        → REVOKE the old DB user / invalidate the old API key
        → Delete the v1 secret version from the secret store
        → At this point, only v2 exists — no instance is using v1 anymore

Timeline: Step 1 to Step 3 = however long your deployment takes.
          Zero downtime because both secrets are valid during the transition.
```

**The critical prerequisite (Class 4):**

> ⚠️ **Your secret storage must support multiple active secret versions simultaneously.** AWS Secrets Manager and HashiCorp Vault support this natively. Kubernetes Secrets (plain) do not — you must mount both versions and coordinate the rollover. Before starting a rotation, verify your secret store can hold two versions active at the same time. If it can't, you need to migrate your secret storage mechanism before rotating secrets.

**What to rotate and how often:**

```
DB credentials:          rotate every 90 days (compliance standard)
API keys (external):     rotate every 90 days, immediately on suspected leak
JWT signing keys:        rotate every 30 days — see below for key rotation
Service-account keys:    rotate every 90 days

JWT signing key rotation is special:
  → Existing tokens are signed with old key
  → During transition: accept tokens signed with EITHER old or new key
  → After transition: stop accepting old key
  → This is the same dual-key window, applied to token validation
  → Token validation must support a key list, not a single key
```

---

### Scenario 2 — Adding Auth to Unauthenticated Endpoints

**Say in interview:**
> *"Three steps: expand, observe, enforce. First I deploy with auth optional — credentials accepted but not required, and every unauthenticated call is logged. I add a response header giving callers a hard deadline. The metric I'm watching is unauthenticated calls per day — it must reach zero and stay there for 7 consecutive days before I flip to enforcement. The log from Step 1 gives me a complete inventory of every caller that needs to migrate. No caller breaks if they've migrated."*

**The problem:** Your write API was built for internal use with no auth. Now it needs to be secured before being exposed more broadly — but existing internal callers don't have credentials yet.

**The 3-step expand-enforce pattern:**

```
STEP 1: EXPAND — auth accepted, not required
─────────────────────────────────────────────
  Deploy: if Authorization header is present → validate it
          if Authorization header is absent  → allow the request
                                               AND log the caller:
                                               caller IP, service name, timestamp

  Also add a warning header to ALL responses:
    X-Auth-Required-By: 2026-08-01

  This gives callers:
    (a) time to get credentials and add them to their requests
    (b) visibility into exactly which services haven't migrated yet (from the logs)
    (c) a hard deadline

STEP 2: OBSERVE — metric gates the transition to enforce
──────────────────────────────────────────────────────────
  Metric to watch:
    unauthenticated_calls_per_day = COUNT(requests WHERE auth_header IS NULL)

  Gate:
    Target = 0 unauthenticated calls for 7 consecutive days
    If metric > 0: reach out to caller teams, don't move to enforce
    If metric = 0 for 7 days: safe to enforce

  NEVER skip this step by assuming "we notified everyone, they must have migrated."
  Check the metric. A silent misconfigured service may still be calling unauthenticated.

STEP 3: ENFORCE — auth required
─────────────────────────────────
  Deploy: if Authorization header is absent → return 401 Unauthorized
  Unexpected 401s at this point = caller that missed the migration
  → Investigate which caller it is (using the log from Step 1)
  → Add their credentials and they're unblocked immediately
  → No design change needed — the endpoint hasn't changed, the caller just forgot to add auth
```

**Class 8 — all entry points must be covered:**

> ⚠️ **"Add auth to the public API" is not the complete change surface.** Before enforcing auth, audit ALL callers — not just the ones you know about:
>
> - External clients (mobile, web, partner integrations) — these are obvious
> - Internal service-to-service calls — may be using direct HTTP, not going through the API gateway
> - Batch jobs and cron jobs — often built once and forgotten, calling the API without credentials
> - Admin endpoints — may have been left intentionally open "for now"
> - Monitoring probes and health checks — may hit authenticated endpoints
>
> The unauthenticated call log from Step 1 will surface all of these. That log is the comprehensive caller inventory — don't rely on your mental model of "who calls this endpoint."

---

### Scenario 3 — Rate Limiting Rollout

**Say in interview:**
> *"I start in observe-only mode — log what would have been throttled without blocking anything. After a full traffic cycle including weekend peaks, I have the P99 request rate per legitimate client. I set the limit at 2–4x that P99 and enforce, starting with 2 services via a feature flag before expanding to all. The observe step is not optional — setting a limit without real traffic data means I might throttle legitimate callers."*

**The problem:** Your API is getting hammered. One client is sending 10,000 requests per second. You need to add rate limiting without blocking legitimate traffic.

**Why you cannot just flip it on:**

```
Wrong: enable rate limiting with a 100 req/sec limit for all clients
       → Legitimate clients that normally send 200 req/sec are throttled
       → You don't know what the "right" limit is without real traffic data
       → Misconfigured rate limits are worse than no rate limits

Right: observe first, configure based on real data, then enforce
```

**The 3-phase rate limiting rollout:**

```
PHASE 1: OBSERVE-ONLY MODE (1–2 weeks)
──────────────────────────────────────
  Deploy rate limiting in "observe" mode:
    → Count every request per client per minute
    → Log: "client X sent 450 requests/min — would have been rate-limited at 100/min"
    → Do NOT block anything
    → Collect this data for the entire traffic cycle: weekday peaks, weekend patterns

  After 1–2 weeks, you have:
    → P99 request rate per client (the "normal" ceiling)
    → Which clients send traffic in bursts vs steady stream
    → What limit would have throttled abuse without affecting legitimate traffic

PHASE 2: CONFIGURE THRESHOLDS
───────────────────────────────
  From observe data:
    Typical client P99:     50 req/min
    Abusive client traffic: 5,000 req/min
    Limit to set:           200 req/min per client
    → 4x above P99 normal traffic
    → 25x below the abusive traffic
    → If a legitimate client hits this limit → it's a bug in their code, not the limit

  Also configure:
    → Per-client limit (not global)
    → Burst allowance: 300 req over 10 seconds (short burst OK, sustained high rate not)
    → Response: 429 Too Many Requests with Retry-After header (tells client when to retry)

PHASE 3: ENFORCE
─────────────────
  Deploy with enforce mode on.
  Monitor: 429 rate by client
    → High 429 rate on a specific client = review their usage pattern
    → High 429 rate on many clients = limit may be too aggressive → adjust

  Feature flag at the API gateway layer controls this per service:
    → Start with 1–2 services, verify limits work, then expand to all services
    → See: Feature Flag Gating (14-feature-flag-gating.md) for cross-cutting rollout
```

**Class 8 — cover all entry points:**

> ⚠️ **Rate limiting only on the public API is incomplete.** Rate limiting must cover:
>
> - Public REST/GraphQL API (obvious)
> - Internal service-to-service APIs — a runaway microservice can DDoS an internal endpoint
> - Batch job API calls — a misconfigured batch job can issue millions of requests per hour
> - Admin endpoints — admin operations often skip the public rate limiter
> - Webhook endpoints — a partner who sends millions of webhook events can overwhelm the receiver
>
> Check all inbound traffic sources, not just the ones that go through the public API gateway.

---

### Scenario 4 — Audit Logging

**Say in interview:**
> *"Audit logging is additive — no existing callers are affected. The key decision is write path: the audit log write must never block or fail the user's primary request. I publish audit events to a queue asynchronously; a consumer writes to an append-only audit DB. Audit log delays are acceptable; user-facing 500 errors caused by an audit write failure are not. For tamper-evidence, I either use hash chaining or write to immutable storage like S3 with Object Lock."*

**The problem:** Compliance requires a record of who did what, when, and from where. The system was never built with this in mind.

**The core requirements:**

```
Every audit log entry must capture:
  WHO:    user_id or service_account_id (who made the request)
  WHAT:   action type (create, read, update, delete) + entity affected (order #1234)
  WHEN:   UTC timestamp (not application server time — use DB clock for consistency)
  WHERE:  source IP, user agent, request ID (for tracing)

Optional but strongly recommended:
  BEFORE: the value before the change (for mutations)
  AFTER:  the value after the change (for mutations)
  → Without before/after, you can detect that a change happened but not what changed
```

**Where to write audit logs:**

```
Option A — Separate audit table in the same DB:
  Pros:  consistent transactions (audit log is committed with the data change)
  Cons:  audit table is in the same DB as the data — can be tampered with

Option B — Separate append-only DB / logging service:
  Pros:  tamper-evident (even DBAs can't easily alter it)
          compliance-grade (can be locked down to append-only access)
  Cons:  separate write is not in the same transaction as the data change
          → audit write may fail even if data write succeeds

Best practice: use Option A for transactional consistency, and also
  stream audit logs to an immutable sink (CloudTrail, S3 with Object Lock,
  or a WORM-compliant (Write Once Read Many) logging service) for compliance.
```

**The async write problem (Class 5 — failure residue):**

```
WRONG pattern:
  User request → write data → write audit log → return response
  If the audit log write fails → return error to user
  → User's action is undone because audit log failed
  → The audit is more important than the action? That inverts priorities.

CORRECT pattern:
  User request → write data (in transaction) → return response to user
                                  │
                                  └──► async audit event published to queue
                                          (Kafka / SQS / internal event bus)
                                            │
                                            ▼
                                        Audit log consumer writes to audit DB
                                        If consumer fails → message is in the queue
                                          (dead letter queue with retry)
                                        Audit log catches up asynchronously

Rule: the audit log write MUST NOT block or fail the user's primary request.
     An audit write failure is an ops alert (investigate the consumer),
     not a user-facing error.
```

**Making audit logs tamper-evident:**

```
Tamper-evident ≠ tamper-proof.
Goal: if someone modifies or deletes an audit log entry, make it detectable.

Approach 1: Hash chaining
  Each audit entry includes:
    hash = SHA256(previous_entry_hash + current_entry_content)
  Deleting or modifying entry N breaks the hash chain from N onward.
  Detection: re-compute hashes across the log, compare to stored hashes.

Approach 2: Write to immutable storage
  AWS S3 with Object Lock: objects can be set non-deletable for a retention period.
  CloudTrail: AWS-managed audit log, immutable by construction.
  These are compliance-grade — no user or admin can delete the log entries.

Approach 3: Separate access controls
  The audit log DB has a separate admin account.
  Application writes to it (append only).
  No application code has DELETE or UPDATE permission on the audit table.
  Only the compliance team (or auditors) have read access.
  This doesn't prevent a privileged insider attack, but it does raise the bar.
```

---

## 🧩 Interview Probe Q&As

**"How do you add auth to endpoints that are currently unauthenticated, without breaking existing callers?"**
> Three steps: expand, observe, enforce. First I deploy with auth optional — if the caller sends credentials I validate them, if they don't I let them through and log the unauthenticated call. I add a response header telling callers when enforcement starts. After reaching zero unauthenticated calls for 7 consecutive days — verified via metric, not assumption — I flip to enforcement. The log from Step 1 gives me a complete inventory of every caller that needs to migrate. No caller breaks if they've migrated; the only 401s at enforcement time are bugs or forgotten services, not design failures.

**"How do you rotate a DB password in production without downtime?"**
> Dual-secret window. Create the new DB user or password while the old one is still active — both are valid at the same time. Roll out a deployment that reads the new credential from the secret store. Once every running instance is confirmed to be using the new credential (verified in connection logs), revoke the old credential. The window between "new credential created" and "old credential revoked" is your deployment time — typically 5–15 minutes — with zero service interruption. The key prerequisite is that your secret store (Vault, AWS Secrets Manager) supports multiple active versions simultaneously.

**"How do you set a rate limit you haven't measured before?"**
> I start in observe-only mode — log what WOULD have been rate-limited without actually blocking anything. After a full traffic cycle (including weekend peaks and any scheduled batch jobs), I have the P99 request rate per legitimate client. I set the limit at 2–4x that P99 — enough headroom for bursts, but well below any abusive client's traffic. Then I enforce. The observe step is not optional: if I set a limit without real traffic data and legitimate traffic exceeds the limit, I'm the one who caused the outage.

**"What if the audit log write fails? Do you fail the user's request?"**
> No — the audit log write is always async. User writes data, data is committed, response returned to user. The audit event is published to a queue (Kafka/SQS) as a side effect. A consumer reads from the queue and writes to the audit log. If the consumer fails, the message stays in the queue — it's retried by the dead letter queue mechanism. The audit log may be a few seconds or minutes behind the actual writes, but it always catches up. Failing the user's request because the audit log is temporarily unavailable inverts the priority: the user's action is more important than the audit record. The audit is for compliance, not for the user's primary path.

**"What does 'tamper-evident audit log' mean and how do you implement it?"**
> Tamper-evident means: if someone modifies or deletes an audit log entry, it's detectable — even if it's not preventable. The simplest approach: hash chaining — each audit entry contains the SHA256 hash of the previous entry's hash concatenated with its own content. Deleting or modifying any entry breaks the hash chain from that point onward. A compliance audit re-computes the chain and detects the break. For stronger guarantees: write audit logs to AWS S3 with Object Lock or CloudTrail — immutable by construction, no admin can delete them. For database-level enforcement: the application's DB user has INSERT permission only on the audit table — no UPDATE or DELETE.

**"What if you need to add rate limiting to 20 services simultaneously?"**
> Use the API gateway layer with a feature flag, same as with auth and any other cross-cutting concern. Rate limiting lives at the gateway, not inside each service. A feature flag controls which services have it enabled. Start with 2 services in enforce mode — validate the limits are correct, check 429 rates, adjust if needed. Then expand to the remaining services via the flag config. No code change in any service. See the Feature Flag Gating pattern for the cross-cutting rollout approach.

---

## 🧾 TL;DR — What to Say in the Interview (2 Minutes)

> *"Every security control added to a live system is a migration — I use the expand-enforce pattern. Never one-shot enforcement. For auth on unauthenticated endpoints: deploy with auth optional first, log every unauthenticated call, give callers a hard deadline, wait for the unauthenticated call metric to reach zero for 7 consecutive days, then enforce. For secrets rotation: use a dual-secret window — add the new secret alongside the old one, roll out all instances to use the new secret, then revoke the old. Zero downtime because both secrets are valid during the transition. For rate limiting: observe-only mode first — log what would have been throttled without blocking anything. After a full traffic cycle, configure limits at 2–4x the measured P99. Then enforce, starting with 2 services via a feature flag before expanding to all. For audit logging: async write — user's primary path never fails because of an audit write failure. Audit events go to a queue; a consumer writes to an append-only, tamper-evident audit DB. The universal rule: check all entry points — public API, internal service calls, batch jobs, admin endpoints. Rate limiting and auth that only cover the public API are incomplete."*

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Jul 11, 2026 | **Note created.** Batch 3 of Operational-Scenarios gap closure. Security hardening live systems — a high-signal question for senior engineers because it combines security knowledge with migration discipline. Written with 8 known mistake classes applied: Class 4 (prerequisites) — dual-secret window requires secret store to support multiple active versions; auth observe-only phase requires the log metric to confirm zero unauthenticated callers; PITR is a prerequisite for data recovery. Class 8 (incomplete change surface) — rate limiting and auth must cover all entry points, not just the public API. Class 5 (failure residue) — audit log async pattern ensures the audit write failure does not propagate to the user's primary path. |
