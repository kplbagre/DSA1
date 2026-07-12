# Operational Scenario: API Versioning and Sunset

> **When this appears in an interview:** "You have a v1 API with 1,000 clients still using it. How do you migrate them to v2 and sunset v1?" or "How do you make a breaking change to an API that external partners depend on?" or "How do you deprecate a feature?" The keyword is **breaking change** or **sunset** — they want to hear a structured client migration strategy, not just "we'll release v2."
> **Patterns used:** Strangler Fig (`10-strangler-fig.md`) + Feature Flag Gating (`14-feature-flag-gating.md`)

---

## 🎯 The Situation

Your API is live. External clients — mobile apps, partner integrations, third-party developers — are calling v1. You need to:
- Change a response field name (breaking for clients that parse it)
- Remove a deprecated endpoint
- Change authentication from API key to OAuth
- Restructure the request/response contract

You cannot just change v1. Those 1,000 clients will break immediately and their developers will be angry.

You need v2 to coexist with v1 long enough for all clients to migrate — then shut v1 down cleanly.

---

## 🧠 The Decision You Make First

Before answering, ask:

> *"Who are the API consumers — internal teams, mobile apps we control, or external third-party developers? And do we have usage telemetry showing which clients are still on v1?"*

| Client type | Migration approach |
|---|---|
| Internal teams only | Coordinate directly, force migration in 4–6 weeks |
| Mobile apps we own | Force migration via app store update, set v1 sunset date tied to app version deprecation |
| External partners (contractual SLA) | 6–12 month sunset window, migration guide, dedicated support |
| Public API (unknown clients) | 12+ months, public deprecation announcement, sunset headers |

---

## 🎨 Visual — API Versioning Architecture

```
WITHOUT VERSIONING (the wrong way)
──────────────────────────────────────────────────────────────
Client calls:  GET /notifications
You change:    response.userName → response.fullName
Result:        All clients break simultaneously. No rollback.


WITH VERSIONING (the right way)
──────────────────────────────────────────────────────────────

Clients call either:
  GET /v1/notifications   ─────────────▶  V1 Handler  ──▶ OLD response shape
  GET /v2/notifications   ─────────────▶  V2 Handler  ──▶ NEW response shape

Both exist simultaneously.
Migration happens client-by-client.
V1 is sunset only after usage reaches zero (or a hard deadline).


URL VERSIONING (most common, what most companies use)
──────────────────────────────────────────────────────────────
/v1/users/{id}    →   old contract
/v2/users/{id}    →   new contract

Header versioning (alternative):
GET /users/{id}
Accept: application/vnd.myapi.v2+json

Query param versioning (least preferred):
GET /users/{id}?version=2


SUNSET SIGNAL HEADERS (add to ALL v1 responses)
──────────────────────────────────────────────────────────────
HTTP/1.1 200 OK
Deprecation: true
Sunset: Sat, 31 Dec 2026 23:59:59 GMT
Link: <https://docs.api.com/migration-v1-to-v2>; rel="deprecation"

These headers let client developers and monitoring tools
detect that they're on a deprecated version.

KEY INVARIANT:
   V1 and V2 must coexist for the entire sunset window.
   Never remove V1 before all clients have migrated.
   The sunset date is a hard deadline, not a soft suggestion.
```

---

## 🗂️ The 5-Phase Playbook

---

### Phase 1 — Build V2 (V1 Untouched)

**What you do:**
- Build the new v2 API at `/v2/...` endpoints
- V1 continues to serve all existing clients unchanged
- V2 is tested internally — no external clients yet

**Say in interview:**
> *"First I build v2 completely without touching v1. V1 keeps serving all existing clients. This phase carries zero risk."*

---

### Phase 2 — Measure V1 Usage

**What you do:**
- Before announcing anything, get the data: who is calling v1, how often, from which clients
- This is the data that drives your sunset timeline

```
Queries to run:
  SELECT client_id, endpoint, COUNT(*) as calls_per_day
  FROM api_request_logs
  WHERE version = 'v1'
  AND timestamp > NOW() - INTERVAL '30 days'
  GROUP BY client_id, endpoint
  ORDER BY calls_per_day DESC;
```

**What you learn:**
- Top 10 clients by v1 call volume → migrate these first (biggest impact)
- Endpoints with highest v1 usage → these need migration guides soonest
- Clients with zero v1 calls in last 30 days → may already be on v2 or inactive

**Say in interview:**
> *"Before announcing the sunset, I pull 30 days of usage telemetry to know exactly who is on v1, how heavily, and from which endpoints. This data drives the migration strategy and lets me identify who needs white-glove support."*

---

### Phase 3 — Announce Sunset + Publish Migration Guide

**What you do:**
- Set a hard sunset date (based on client type — see decision table above)
- Publish a migration guide: v1 endpoint → v2 equivalent, diff of request/response
- Add `Sunset` and `Deprecation` headers to all v1 responses
- Email all registered API clients with the timeline

**Say in interview:**
> *"I set a hard sunset date — not 'we plan to eventually' but a specific date — and send it to all clients. V1 responses start returning Sunset headers so client monitoring tools can surface the warning automatically."*

**Sunset communication template:**
```
Subject: V1 API Sunset — Action Required by Dec 31, 2026

V1 of the [Service] API will be retired on Dec 31, 2026.
After this date, all v1 requests will return HTTP 410 Gone.

Migration guide: [link]
V2 changelog: [link]
Support channel: [link]

Current v1 usage for your account: [X] calls/day on [Y] endpoints.
Endpoints most urgent to migrate: [list top 3]
```

---

### Phase 4 — Monitor Migration Progress

**What you do:**
- Track: `% of calls on v1` → should trend toward 0 over time
- Weekly report: which clients are still on v1, their call volumes
- Proactively reach out to high-volume v1 clients who haven't started migration

```
Migration dashboard (check weekly):
  Total v1 calls this week:    12,000  (was 45,000 last month) ✅ trending down
  Clients still on v1:         34      (was 89 last month) ✅
  Highest-volume v1 client:    AcmeCorp — 8,000 calls/day ⚠️ reach out
  Zero-call clients on v1:     22      (likely migrated, can ignore)
```

**Say in interview:**
> *"I track migration progress weekly. Clients who are high-volume and not moving get a direct outreach from developer relations. I don't wait for the deadline and hope they migrate — I actively manage the laggards."*

---

### Phase 5 — Sunset V1

**What you do:**
- On sunset date: return `HTTP 410 Gone` for all v1 requests
- Do NOT return v2 responses for v1 requests — that masks the breakage from the client's monitoring
- Keep v1 routes registered for 30 more days returning 410 (so clients get a clear error, not a connection refused)
- After 30 days: remove v1 routes entirely

**Say in interview:**
> *"On the sunset date, v1 returns 410 Gone — not a redirect to v2. A redirect would hide the fact that the client is still on v1. 410 makes it obvious. I keep the routes alive for 30 days returning 410, then remove them."*

```
Sunset response:
HTTP/1.1 410 Gone
Content-Type: application/json

{
  "error": "API_SUNSET",
  "message": "V1 API was retired on Dec 31, 2026. Please migrate to V2.",
  "migration_guide": "https://docs.api.com/migration-v1-to-v2",
  "support": "api-support@company.com"
}
```

---

## ⚠️ The 3 Things That Go Wrong

**1. No usage telemetry — you don't know who's on v1**
You announce sunset but can't identify which clients to contact. Sunset date arrives, unknown clients break, escalations flood in.
Fix: instrument API logging from day one — `client_id`, `api_version`, `endpoint`, `timestamp` on every request. If you don't have this, build it before announcing the sunset.

**2. Sunset date is soft ("we plan to sunset soon")**
Clients interpret "soon" as "not yet" and don't migrate. Deadline arrives, 40% of clients still on v1.
Fix: hard date, communicated 3 times (announcement, 60-day warning, 30-day final warning). Hard date = you will return 410 on that date, no exceptions.

**3. V2 is a superset that silently accepts v1 requests**
Team routes v1 requests to v2 endpoints to "be kind." Clients never notice they're on a deprecated path. Usage telemetry shows v1 calls dropping (they're being silently upgraded). Real v1 usage is invisible.
Fix: v1 and v2 are separate routes with separate handlers. Never silently upgrade a v1 request to v2 behavior. The client must explicitly migrate.

---

## 🧩 Interview Probe Q&As

**"What if a client misses the sunset deadline and breaks?"**
> They get 410 Gone with a clear error message pointing to the migration guide and support channel. We've sent 3 communications over the sunset window. At this point, it's on the client to migrate. If they're an enterprise partner with a contractual SLA, their contract should have included a migration window — that's a business conversation, not a technical one.

**"How do you handle mobile apps where you can't force a client update?"**
> Mobile adds complexity — you can't force all users to update the app immediately. Strategy: tie the v1 sunset date to a minimum supported app version. Announce that app versions below X will not be supported after the sunset date (apps auto-update or users are prompted). Check app store analytics for the version distribution to set a realistic sunset date.

**"Why return 410 Gone instead of redirecting to v2?"**
> A redirect silently upgrades the client to v2 behavior. If v2 has a different response shape, the client's code may break anyway — but it breaks with a confusing error, not a clear "you're on a deprecated API" message. 410 is unambiguous: this path is gone, migrate explicitly. It also keeps v1 and v2 usage metrics clean — no v1 calls masquerading as v2.

**"How do you version internally (between microservices)?"**
> Internal services use contract testing (Pact) instead of versioned URLs. A consumer publishes the contract it expects; the provider verifies it hasn't broken it. Breaking change is caught in CI before deployment, not at runtime. Versioned URLs are for external/public APIs where you can't coordinate deployments across clients.

---

## 🧾 TL;DR — What to Say in the Interview (2 Minutes)

> *"API sunset is a 5-phase process. First, build v2 without touching v1. Second, pull 30 days of usage telemetry to know exactly who's on v1 and how heavily — this data drives everything. Third, set a hard sunset date, publish a migration guide, and add Sunset headers to all v1 responses. Fourth, track migration progress weekly and proactively reach out to high-volume laggards — don't wait for the deadline and hope. Fifth, on the sunset date, return 410 Gone — not a redirect, which would hide the breakage. Keep the routes alive returning 410 for 30 more days, then remove them. The two things most teams skip are the usage telemetry before announcing and the proactive outreach to laggards — those two skips are what turn an API sunset into an incident."*

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Jul 11, 2026 | **Note created.** Batch 2 of Operational-Scenarios. API versioning and sunset is a common SDE-3 design question especially at API-platform companies. |
