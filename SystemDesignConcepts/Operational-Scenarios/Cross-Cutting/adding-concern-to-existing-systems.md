# Operational Scenario: Adding a Cross-Cutting Concern to Existing Systems

> **When this appears in an interview:** Interviewer says "add distributed tracing to all 20 of our services" or "every API needs rate limiting" or "add auth to internal service-to-service calls." The keywords are **cross-cutting concern**, **add X to all services**, or any scenario where the same requirement must be retroactively applied to many existing services.
> **Patterns used:** Feature Flag Gating (`../../Patterns/DeepDive/14-feature-flag-gating.md`) for the expand → observe → enforce rollout. Shadow Mode (`../../Patterns/DeepDive/13-shadow-mode-dark-launch.md`) for observe-only validation before enforcement.

---

## 🎯 The Situation

A new mandatory concern must be applied to every service in your system. It could be:
- **Observability**: distributed tracing, centralized logging, custom metrics
- **Security**: authentication on internal calls, API rate limiting, audit logging
- **Reliability**: circuit breakers, timeouts, health checks
- **Compliance**: data masking, audit trails, PII handling

The challenge: 20 services, each with their own team, their own deploy cadence, and no current enforcement mechanism.

**Classic triggers in interviews:**
- "We need distributed tracing across all 20 services — how do you roll it out?"
- "Add rate limiting to every API — walk me through the approach"
- "Every service-to-service call needs to be authenticated — design the rollout"
- "Add audit logging to all services that touch customer data — how do you do it safely?"

---

## 🧠 The Decision You Make First

Ask one clarifying question before proposing any solution:

> *"Should this concern live at the infrastructure layer (API gateway, service mesh), or must it be inside each service?"*

| Answer | Approach | Trade-off |
|---|---|---|
| Infra layer can handle it | Centralize in gateway or service mesh — zero per-service code change | Gateway can't see business context (user ID, action type) needed for fine-grained audit logs |
| Must be inside each service | Shared SDK/library, rolled out per service | Requires coordination across 20 teams; risk of inconsistent implementation |
| Mix: gateway for external, SDK for internal | Two-phase rollout | Most realistic for auth, rate limiting, and audit logging |

**The answer changes the entire implementation strategy.** Observability (tracing, metrics) → usually infra layer (sidecar, service mesh). External rate limiting → API gateway. Auth for internal calls → service mesh (mTLS) or SDK. Audit logging → must be per-service because business context lives inside the service, not in the network layer.

---

## 🎨 Visual — Expand → Observe → Enforce

> **Before:** 0 of 20 services enforce the concern. No visibility into which calls are compliant. Attempting to enforce immediately would break 3–5 services that don't know they need to comply.
> **After:** all 20 services enforce the concern. Coverage metric at 100%. Non-compliant calls are rejected at the point of entry.

```
WRONG WAY (big-bang enforcement):
──────────────────────────────────
  Enable enforcement on all 20 services simultaneously
  → 3 services break immediately (callers didn't know about the requirement)
  → 1am incident. Emergency rollback. Back to zero. Trust damaged.

RIGHT WAY (expand → observe → enforce):
─────────────────────────────────────────

  EXPAND PHASE (add concern in observe-only mode):
    Concern is wired up. Missing concern → LOGGED, not rejected.
    Callers get time to comply without anything breaking.
    Response header hints at enforcement deadline:
      X-Auth-Required-By: 2026-08-15

  OBSERVE PHASE (watch the violation metric):
    Metric: "requests_missing_concern_per_day"
    Exit criterion: metric = 0 for 7 consecutive days
    If metric doesn't reach 0: filter by caller to find the straggler.

  ENFORCE PHASE (flip the switch — service by service):
    Missing concern → request REJECTED (401, 429, etc.)
    Only flip after observe metric has held at 0 for 7 days.

  Service rollout (highest-risk first → lowest-risk last):

  Service:   [A]   [B]   [C]   [D]   [E]   ...  [T]
  Week 1:    EXP   EXP   EXP    ·     ·           ·     ← admin endpoints first
  Week 2:    OBS   OBS   OBS   EXP   EXP          ·     ← internal calls added
  Week 3:    ENF   ENF   ENF   OBS   OBS          EXP   ← A,B,C enforce
  Week 4:     ✓     ✓     ✓    ENF   ENF          OBS   ← ...until all 20 done

KEY INVARIANT:
   NEVER go from 0 to enforce without an observe phase.
   The observe phase finds the callers who WILL break on enforcement
   so you can fix them BEFORE they break.
   Enforcement with a surprise is an incident. Enforcement with data is a migration.
```

---

## ⚠️ PREREQUISITE — Inventory All Entry Points First

> **Class 8 (Incomplete change surface):** The most common mistake is instrumenting the main REST API and missing every other entry point. Each of the following is a separate surface where the concern must be applied.

| Entry point | Example | Often missed? |
|---|---|---|
| Public REST / GraphQL API | `GET /api/v1/orders` | No — usually the starting point |
| Internal service-to-service calls | Service A calls Service B's `/internal/validate` | **Yes** — often unauthenticated |
| Async message consumers | Kafka consumer processing `order.created` events | **Yes** — no HTTP, concern doesn't apply automatically |
| Batch / cron jobs | Nightly reconciliation job reading from DB | **Yes** — long-running, no HTTP layer |
| Admin endpoints | `/admin/backfill`, `/admin/users` | **Yes** — often highest privilege, least protected |
| Health check probes | `/health`, `/actuator/health` | Sometimes — must NOT require auth (K8s liveness probe will fail) |
| Metrics scrape endpoints | `/actuator/prometheus` | Sometimes — network-level protection is usually sufficient here |

**The inventory is the first deliverable** before any code is written.

---

## 🗂️ The 5-Phase Rollout Playbook

---

### Phase 1 — Inventory: Map Every Entry Point

Don't write a line of code until you know every surface that needs the concern.

```
Steps:
  1. List all services that need the concern.
     Source: service registry, team roster, architecture diagram.
     Do not rely on memory — cross-check against the service registry.

  2. For each service, enumerate ALL entry points:
     HTTP endpoints:    grep for @RestController, @Controller, @Path
     Kafka consumers:   grep for @KafkaListener, @ConsumeEvent
     Scheduled tasks:   grep for @Scheduled, @Cron
     Admin routes:      grep for /admin/, /internal/, /backfill/

  3. For each entry point, record:
     → Does it currently have the concern? (yes / no / partial)
     → Who calls it? (another service? external client? cron scheduler?)
     → Can the caller tolerate enforcement immediately, or does it need lead time?

  Output — the rollout table:

  Service   | Entry Point            | Concern? | Caller         | Enforce By
  ──────────────────────────────────────────────────────────────────────────────
  orders    | POST /orders           | NO       | frontend       | Week 3
  orders    | GET  /internal/orders  | NO       | inventory-svc  | Week 2
  orders    | kafka: order.created   | NO       | event bus      | Week 2
  orders    | /admin/backfill        | NO       | ops team       | Week 1 ← fix first
```

**Say in interview:**
> *"Before writing any code, I inventory every entry point across all 20 services: REST endpoints, Kafka consumers, batch jobs, admin routes. The admin routes are usually the most dangerous — highest privilege, least protected. The inventory table becomes the rollout schedule."*

---

### Phase 2 — Choose the Injection Point (Infra vs Per-Service)

Pick where the concern lives before writing a line of code.

```
OPTION A — API Gateway (centralized, external traffic only):
  ┌──────────┐     ┌────────────────┐     ┌───────────┐
  │  Client  │────►│  API Gateway   │────►│  Service  │
  └──────────┘     └────────────────┘     └───────────┘
  Concern applied at gateway: rate limiting, TLS, external auth headers
  Pros:  zero service code change; one config location; consistent
  Cons:  gateway only sees HTTP context — no business data;
         does NOT cover internal service-to-service calls or async consumers

OPTION B — Service Mesh / Sidecar (all service-to-service traffic):
  Each pod gets a sidecar proxy (Envoy / Linkerd)
  Sidecar intercepts all inbound + outbound traffic
  mTLS, circuit breaking, tracing injected at mesh layer
  Pros:  covers internal calls; zero app code change
  Cons:  mesh must be installed + configured; does NOT cover async (Kafka)

OPTION C — Shared SDK / Library (per-service, any entry point):
  Internal library published to artifact registry
  Each service adds it as a dependency
  Library provides middleware / filter / interceptor
  Pros:  accesses business context (user ID, action, payload for audit logs)
  Cons:  requires each team to update + deploy; risk of version drift

OPTION D — Mix (most realistic):
  Gateway:  external client rate limiting + TLS
  Mesh:     internal service-to-service mTLS
  SDK:      audit logging + fine-grained authorization (needs business context)
```

**Say in interview:**
> *"I choose the injection point based on what context the concern needs. Rate limiting and external auth → API gateway, zero service changes. Internal mTLS → service mesh. Audit logging must be per-service via SDK, because the audit event needs business context — who did what to which record — and the gateway doesn't have that."*

---

### Phase 3 — Expand: Add in Observe-Only Mode

Roll out the concern to each service in passive mode — log violations, don't reject.

**Rollout order: highest-risk entry points first.**

```
Priority order:
  1. Admin endpoints      (unprotected, highest privilege — fix these first)
  2. Internal service-to-service calls (callers may not know they need to comply)
  3. Async consumers      (easy to miss; silent violations; no enforcement yet)
  4. Public API           (callers expect existing behavior — announce before enforcing)
```

**Example: Spring Boot filter for auth concern in expand mode:**

```java
@Component
public class InternalAuthFilter implements OncePerRequestFilter {

    private static final Logger log =
            LoggerFactory.getLogger(InternalAuthFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {
        String authHeader = request.getHeader("X-Internal-Auth");
        boolean enforcing = featureFlagService.isEnabled("internal-auth-enforce");

        if (authHeader == null || !authTokenValidator.isValid(authHeader)) {
            if (enforcing) {
                // Enforce phase: reject non-compliant requests
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
            // Expand phase: log only — do not reject
            log.warn("MISSING_INTERNAL_AUTH endpoint={} caller={}",
                    request.getRequestURI(),
                    request.getHeader("X-Calling-Service"));

            // Hint to caller when enforcement will begin
            response.setHeader("X-Auth-Required-By", "2026-08-15");
        }

        chain.doFilter(request, response);
    }
}
```

**Expand phase exit metric:** `requests_missing_internal_auth_per_day`  
**Exit criterion:** metric = 0 for 7 consecutive days.

> ⚠️ **Class 4 (Missing prerequisite):** The violation metric MUST be wired up and visible on a dashboard BEFORE the expand phase starts. If you can't measure whether violations are declining, you cannot safely decide when to enforce. Metric first, then expand.

**Say in interview:**
> *"I roll out in expand mode: the concern is wired up but violations are logged, not rejected. I expose a dashboard metric for violations per day and give callers a response header telling them when enforcement starts. Exit criterion is that metric holding at zero for 7 consecutive days. I don't flip enforce until I have the data."*

---

### Phase 4 — Observe: Confirm Coverage Before Enforcing

Watch the violation metric. Investigate anything that doesn't trend to zero.

```
Questions to answer during the observe phase:

  1. Is the metric decreasing?
     → Decreasing: callers are updating. Let it run.
     → Stuck at non-zero: filter metric by caller identity to find the straggler.
       Contact their team directly with the specific endpoint and call volume.

  2. Are there callers you didn't know about?
     → Check violation logs for X-Calling-Service headers not in your inventory.
     → These are undiscovered callers — add them to the rollout plan now.

  3. Is the concern working correctly (not just present)?
     → Send an intentionally invalid credential — confirm it appears in the logs.
     → In expand phase: it should be logged.
     → In enforce phase: it should be rejected with the right status code.

  4. Async consumers: check separately.
     → HTTP-level violation metrics do NOT capture Kafka consumer violations.
     → Wire a separate metric for each async entry point.
     → Do not assume HTTP coverage = full coverage.
```

> ⚠️ **Class 8 (Incomplete change surface — async is invisible to HTTP metrics):** Kafka consumers and batch jobs don't generate HTTP traffic. A metric that only counts HTTP violations will show zero even if async consumers are completely missing the concern. Instrument async entry points with their own violation counters.

**Say in interview:**
> *"During the observe phase, I watch the violation metric daily. If it's stuck, I filter by caller to find the straggler. I also test async consumers separately — Kafka violations won't appear in HTTP metrics. And I run intentional bad-credential tests to confirm the concern is working correctly, not just present."*

---

### Phase 5 — Enforce: Flip the Switch Service by Service

Only after the observe metric holds at zero for 7 consecutive days.

```
Enforcement order (same as expand order — highest-risk first):
  1. Admin endpoints    → enforce first (lowest traffic volume; highest risk if missed)
  2. Internal calls     → enforce second
  3. Async consumers    → enforce third (requires DLQ — see Class 5 note below)
  4. Public API         → enforce last (highest caller count; highest blast radius)

How to flip:
  Feature flag "internal-auth-enforce" = true
  → The same filter that was logging now rejects
  → No redeploy needed; flag propagates within seconds

Rollback:
  If enforcement causes unexpected failures:
  → Flip flag back to expand mode immediately (under 10 seconds; no redeploy)
  → Investigate: which caller broke? Why weren't they in the violation logs?
     (Common cause: a caller that only runs on certain days — cron, end-of-month batch)
  → Fix the caller. Re-observe for 7 days. Re-enforce.
```

> ⚠️ **Class 5 (Failure residue — async consumer enforcement):** If you enforce the concern on a Kafka consumer and a message arrives without it, what happens to the message? Without explicit handling, it gets silently discarded. Route rejected messages to a Dead Letter Queue (DLQ) before enforcing. A DLQ lets you inspect, fix, and replay the message. Silent discard = silent data loss.

> ⚠️ **Class 4 (Missing prerequisite — flag propagation time):** The feature flag is your enforcement circuit breaker. Before starting the enforce phase, confirm the flag service propagation time — how quickly does flipping the flag take effect across all pods? If propagation takes 30 seconds and an incident happens, you need to know the rollback lag before you're in an incident.

**Say in interview:**
> *"I enforce service by service, admin endpoints first, public API last. The feature flag is my circuit breaker — if enforcement breaks something unexpected, I flip back to observe mode in under 10 seconds without a redeploy. For Kafka consumers, I add DLQ handling before enforcing: a message that fails the concern check goes to the DLQ, not into the void."*

---

## 🧩 Interview Probe Q&As

**"What if one team refuses to update their service before the enforcement deadline?"**
> First, understand why: technical constraint (their deploy cycle is 6 weeks), prioritization conflict, or a misunderstanding of impact? Then set the deadline from leadership, not just your team — cross-team compliance requires organizational weight behind it. The feature flag controls enforcement centrally: you can hold off on enforcing for that team's services specifically while enforcing the rest. If they miss the hard deadline, enforce anyway. The observe phase showed them their violation count for weeks — they had the data. Their callers will get 401s; that becomes their problem to fix, not a reason to hold the entire rollout hostage.

**"How do you add audit logging to 20 services without duplicating code?"**
> Shared SDK approach. Build a library that provides a single `auditLog.record(event)` call. The library handles serialization and publishes to a message queue (Kafka/SQS) asynchronously — the publish must never block the primary request path. A centralized consumer reads from the queue and writes to the audit store. The SDK is versioned: when the audit schema changes, release a new version; services adopt it on their own deploy cycle without a forced coordinated release. The consumer handles schema normalization, so older SDK versions still produce valid events.

**"What if the concern can't be put into observe mode — it's either on or off?"**
> Almost every concern can be staged. Rate limiting can start at a limit so high no legitimate caller reaches it (observe-only in practice). Auth can log violations before rejecting. Circuit breakers can start with a threshold so high they never open. If the concern truly has no observe mode (a hard compliance deadline from a regulator), freeze the rollout window, contact every caller personally, get explicit written confirmation from each team that they're ready, then flip the switch. No-observe-mode enforcement is high-risk by definition — compensate with more contact time and more explicit confirmation, not less.

**"How do you handle a concern that needs business context the gateway doesn't have?"**
> Per-service SDK. Fine-grained authorization (does this user have permission to modify this specific resource?), audit logging (what action did user X take on record Y?), and PII masking (which fields in this response are sensitive for this user?) all require business context that lives inside the service — the gateway only sees HTTP headers and paths. The SDK approach injects the concern into the service's request handling layer where the business model is accessible. For authorization specifically: if the policy rules are complex, use a centralized policy engine (OPA — Open Policy Agent — a policy-as-code engine that evaluates rules against context you supply) so the logic isn't duplicated across 20 services, but the context is still provided per-service.

---

## 🧾 TL;DR — What to Say in the Interview (2 Minutes)

> *"My universal pattern for adding any cross-cutting concern to existing services is expand → observe → enforce. First, I inventory every entry point — public REST API, internal service-to-service calls, Kafka consumers, batch jobs, admin endpoints. Admin endpoints are usually the most dangerous and least protected, so I address those first. Second, I choose the injection point: rate limiting and external auth go in the API gateway with zero service changes. Internal mTLS goes in the service mesh. Audit logging must be per-service via shared SDK because it needs business context the gateway doesn't have. Third, I roll out in expand mode: the concern is wired up but violations are logged, not rejected. I expose a dashboard metric for violations per day and give callers a response header with the enforcement deadline. Exit criterion: metric = 0 for 7 consecutive days. Fourth, I observe: watch for callers I didn't know about in the inventory, check async consumers separately because they don't appear in HTTP metrics, and run intentional violation tests to confirm the concern is working. Fifth, I enforce via feature flag — admin endpoints first, public API last. The feature flag is my circuit breaker: if enforcement breaks something unexpected, I flip back to observe mode in under 10 seconds, no redeploy. For Kafka consumers, DLQ handling is a prerequisite before enforcing — rejected messages must go to a DLQ, not be silently dropped."*

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Jul 21, 2026 | **Note created.** Batch 4 of Operational-Scenarios gap closure. Cross-cutting concern rollout — the "add X to all 20 services" interview question. Covers expand→observe→enforce pattern, injection point selection (gateway vs mesh vs SDK), full entry point inventory. Class 8: async consumers are invisible to HTTP violation metrics — must instrument separately. Class 5: DLQ required before enforcing on async consumers. Class 4: violation metric must exist before expand; flag propagation time must be known before enforce. Class 1: runbooks must specify exact commands and exact success metrics. |
