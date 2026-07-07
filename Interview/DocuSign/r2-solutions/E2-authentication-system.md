# E2 — Design an Authentication & Authorization System

> **Read `solution-notes-standards.md` FIRST.** This file is a 60-minute interview answer framework.
> Its structure mirrors DELIVERY-RECIPE: clarify (5 min) → estimate (5 min) → HLD (15 min) → deep dives (15 min) → trade-offs (8 min) → Q&A (7 min).
> **Say this out loud** before your interview.

---

## 🎯 What Is This System?

**In plain English:** An authentication & authorization system verifies who you are (authentication) and decides what you're allowed to do (authorization). It issues short-lived credentials (JWT tokens) after verifying identity, and validates those credentials on every API request — without hitting the database each time.

**Real-world examples:**

| System / Company | What they built |
|---|---|
| **Auth0** | Hosted identity-as-a-service; OAuth 2.0 + OIDC + MFA out of the box |
| **Okta** | Enterprise SSO and identity platform (used by FedEx, T-Mobile, DocuSign) |
| **AWS Cognito** | Auth for AWS apps — user pools, federated identity, JWT issuance |
| **Keycloak** | Open-source identity provider; self-hostable OIDC/SAML server |
| **Google Identity Platform** | Powers "Sign in with Google" for millions of apps |
| **Firebase Authentication** | Simple auth for mobile/web with social providers + email/password |

**Core user journey:** User submits email + password → server verifies against bcrypt hash → issues a signed JWT (15-min TTL) plus a refresh token (7-day TTL) → client attaches JWT to every API request header → server validates the signature without any DB lookup → when JWT expires, client silently exchanges the refresh token for a new JWT.

**Why it's hard to build at scale:** JWTs are stateless by design — you cannot "revoke" one without a token blacklist (which requires a DB lookup, defeating the point); refresh token rotation must be atomic to prevent race conditions on concurrent refreshes from multiple devices; and MFA must be optional, per-tenant, and pluggable without rewiring the core login flow.

---

## 🔑 Technology Quick Reference

> **Read this once before the file.** These are the only auth/security acronyms you need to know cold for this question.

| Term | Plain-English meaning |
|---|---|
| **JWT** (JSON Web Token) | A compact, self-contained token with 3 base64url-encoded parts: header (algorithm), payload (claims: user_id, roles, expiry), and signature. Stateless — the server validates the signature without any DB lookup. |
| **RS256** | The JWT signing algorithm used here. Uses RSA asymmetric signing — private key signs at login, public key verifies on every request. More secure than HS256 (shared secret) because the public key can be distributed safely. |
| **Access token** | The short-lived JWT (15-min TTL) attached to every API request in the `Authorization: Bearer` header. Expires quickly to limit the damage if stolen. |
| **Refresh token** | A long-lived opaque token (7-day TTL) stored in an httpOnly cookie. Used only to get a new access token when the current one expires. Never sent on API requests. |
| **Token blacklist** | A Redis store of revoked JWT IDs (`jti` claim → "revoked", TTL = token's remaining lifetime). The only way to immediately invalidate a stateless JWT before it naturally expires. |
| **jti** (JWT ID) | A unique identifier claim inside every JWT. Used as the Redis key when blacklisting a revoked token. |
| **OAuth 2.0** | The open standard for *delegated authorization* — "allow app X to act on your behalf without giving it your password." DocuSign uses it so third-party apps can send envelopes on a user's behalf. |
| **OIDC** (OpenID Connect) | The identity layer built on top of OAuth 2.0 that adds *authentication* (who you are). Adds an `id_token` containing the user's identity. Powers "Sign in with Google" style flows. |
| **PKCE** (Proof Key for Code Exchange) | A security extension for OAuth in mobile/SPA apps. Prevents an attacker who intercepts the auth code from exchanging it for a token (because they don't know the `code_verifier`). RFC 7636. |
| **RBAC** (Role-Based Access Control) | Assign users a role (admin, signer, viewer). The role defines allowed actions. Simple and fast — one lookup: "what role does this user have?" |
| **ACL** (Access Control List) | Per-resource permissions — user A can view doc 123, user B can sign doc 456. Fine-grained but expensive to query. DocuSign uses RBAC + per-document ACL together. |
| **MFA** (Multi-Factor Authentication) | Requiring a second proof of identity beyond password. Prevents account takeover even if password is stolen. |
| **TOTP** (Time-based One-Time Password) | The 6-digit rotating code from Google Authenticator or Authy. Changes every 30 seconds. Requires a shared secret established at setup time. More secure than SMS OTP (not interceptable). |
| **OTP** (One-Time Password) | A single-use code sent via email or SMS. Simpler than TOTP but depends on email/phone delivery. Vulnerable to SIM-swapping for SMS. |
| **SSO** (Single Sign-On) | One login grants access to multiple services. User authenticates once with a central Identity Provider; all connected apps trust that session without re-asking for credentials. Used by DocuSign enterprise customers (law firms, banks). |
| **SAML 2.0** | XML-based standard for enterprise SSO. The Identity Provider (Okta, Azure AD) sends a signed XML "assertion" to DocuSign proving who the user is. DocuSign validates the assertion's signature against the IdP's certificate. |
| **bcrypt** | A one-way slow hashing algorithm for passwords. Slow by design — makes brute-force attacks expensive. Never store passwords in plaintext or with MD5/SHA1. |

---

## Section 0 — Question Identity Card

| | |
|---|---|
| **Question** | **Design an Authentication & Authorization System** (OAuth/OIDC, JWT, MFA, RBAC, session management) |
| **Interview Type** | **Type B — Product Architecture** (focus: API design, data model, token management, access control) |
| **Confirmed or Likely** | 🔶 Likely (Every company asks; fundamental SDE-3 skill; common system design follow-up) |
| **Concept notes prerequisite** | `13-security-pki.md` (cryptography, signing, certificates); `11-api-design.md` (API contract, error handling) |
| **DocuSign-specific angle** | e-signature requires legal compliance: audit trail of who accessed what document when, multi-factor authentication (prevent account takeover), per-document RBAC (viewer, signer, approver roles), SSO for enterprise customers. |
| **Time budget** | 5 min clarify → 5 min estimate → 15 min HLD → 15 min deep dive → 8 min trade-offs → 7 min Q&A buffer |

---

## Section 1 — 🚀 The One-Sentence Opener

> "Before I design, let me clarify: are we building a proprietary auth system or using OAuth/OIDC? Do we need multi-factor authentication? And what's the scope of authorization: simple role-based (admin/user) or fine-grained (per-document permissions)?"

Then pivot to Section 2.

---

## Section 2 — 🔍 Clarifying Questions Script (Minutes 0–5)

**Q: "Are we building OAuth/OIDC (industry standard) or a proprietary auth system?"**
- Why ask: OAuth is battle-tested; proprietary auth is custom/risky
- If OAuth/OIDC (OAuth 2.0 = the open standard for *delegated authorization* — "allow app X to act on your behalf without seeing your password"; OIDC = OpenID Connect, the identity layer built on top of OAuth that adds *authentication* — who you are, not just what you can do) → use standard flows (Authorization Code, Client Credentials); libraries handle complexity
- If proprietary → design from scratch; higher security risk; not recommended at scale

**Q: "What's the session/token strategy — stateless JWT or stateful session tokens?"**
- Why ask: JWT is scalable (no server state); session tokens are simpler (server validates)
- If JWT → need key rotation, revocation blacklist; faster (no DB lookup on every request)
- If sessions → need session store (Redis); slower (DB lookup per request) but simpler

**Q: "Do we need multi-factor authentication (MFA), and how strict?"**
- Why ask: MFA (email code, authenticator app, SMS) prevents account takeover; adds latency
- If yes → need MFA verification service; second auth factor (email, phone)
- If no → simpler (just username/password)

**Q: "What's the authorization model — simple RBAC (admin/user) or fine-grained (per-resource)?"**
- Why ask: RBAC is simple; fine-grained is complex (ACL per document)
- If RBAC (Role-Based Access Control — you assign users a role like "admin" or "viewer", and the role defines what actions are allowed; one lookup per request: "what role does this user have?") → 5-10 roles; users have role; all resources visible to role
- If fine-grained → users have per-document permissions; complex queries

**Q: "How many users, sessions, and concurrent login requests per second?"**
- Why ask: Drives session store capacity, token generation throughput, auth service scaling

**Q: "Do we need to support SSO (Single Sign-On — one login grants access to multiple services; the user authenticates once with a central Identity Provider and all connected apps trust that session without asking for credentials again) for enterprise customers?"**
- Why ask: SSO adds complexity (SAML, OpenID Connect with custom IdP)
- If yes → need SAML 2.0 support (SAML 2.0 = Security Assertion Markup Language, an XML-based open standard for exchanging authentication data between an Identity Provider like Okta/Azure AD and a Service Provider like DocuSign; the IdP sends a signed XML "assertion" proving who the user is) or OIDC federation; customers use their corporate IdP
- If no → simpler (just username/password + optional MFA)

---

## Section 3 — 📋 Requirements (Functional + Non-Functional)

**Functional Requirements:**
- Users should **log in with email + password** (or SSO for enterprises)
- System should **issue access tokens** (JWT or opaque token) for authenticated requests
- System should **refresh access tokens** (issue long-lived refresh token, short-lived access token)
- Users should **log out** (revoke tokens)
- System should support **multi-factor authentication** (email code, authenticator app)
- System should enforce **role-based access control** (admin, editor, viewer, signer roles)
- System should support **per-document permissions** (Alice is signer on doc X but viewer on doc Y)
- Out of scope: Passwordless auth, biometric auth, third-party OAuth (Facebook/Google login)

**Non-Functional Requirements:**
- Scale: 10M users, 100M login attempts/day (~1.16K logins/sec baseline, 3.5K peak), 1M concurrent active sessions
- Latency: P99 login < 1 second; P99 token validation < 10ms (on every request)
- Availability: 99.99% SLO (auth failure = service down; highest uptime requirement)
- Consistency: **Strong consistency required** (token validation must be immediate; can't have stale session data)
- Durability: User credentials are encrypted; never plaintext in DB
- Security: Passwords hashed with bcrypt; tokens signed with RSA; tokens expire (15 min access, 7 day refresh)
- Compliance: Audit trail of logins (who, when, from where); GDPR (right to deletion = revoke all tokens)

---

## Section 3.5 — 🗂️ Core Entities (~2 minutes)

> **Say this out loud:** "Before I sketch the architecture, let me name the key data objects the system manages."

| Entity | What it represents | Storage |
|---|---|---|
| **User** | Account holder — email, bcrypt password hash, tenant, MFA config | PostgreSQL |
| **Role** | Named permission bundle — e.g., `admin`, `editor`, `viewer`, `signer`; defines allowed actions | PostgreSQL |
| **UserRole** | Join record assigning a Role to a User (many-to-many) | PostgreSQL |
| **ResourcePermission** | Per-document ACL entry — which user has which permission on which specific document | PostgreSQL |
| **TokenBlacklist** | Set of revoked JWT IDs (`jti`) — checked on every request to detect logged-out tokens | Redis (TTL matches token expiry) |
| **AccessAuditLog** | Append-only record of every login, failed attempt, token issue, and access denial | PostgreSQL (append-only) |

**Key relationships:**
- A `User` has many `UserRoles` — roles apply system-wide (e.g., "this user is an editor")
- A `User` can also have `ResourcePermissions` — per-document overrides (e.g., "viewer on doc X only")
- Authorization check = "does this user's roles OR resource permissions allow this action on this resource?"
- `TokenBlacklist` in Redis is the only way to invalidate a stateless JWT before it expires naturally; entries are auto-expired by TTL when the JWT would have expired anyway

---

## Section 8 — 🌐 API Design (Minutes 8–13) ⭐ Type B Primary Deliverable

> **Why here, not later:** For Type B (Product Architecture), the API contract is the primary deliverable. Authentication endpoints are the system's public face — state them before the internals.

### 🧠 How to Derive These Endpoints

Authentication flows are state machines: unauthenticated → credential-checked → MFA-verified → token-issued → token-expired → refreshed → logged-out. Each state transition is an endpoint.

"Users can log in with email + password" → state transition: unauthenticated → credential-checked → `POST /auth/login`. What does the response need to handle? If MFA is enabled, the caller isn't authenticated yet — you can't issue a token. Return `{mfa_required: true, request_id: "..."}` and wait for the MFA step. If MFA is disabled, issue tokens immediately. One endpoint, two response shapes based on tenant config.

"MFA is required for enterprise tenants" → state transition: credential-checked → MFA-verified → `POST /auth/verify-mfa`. This is a separate endpoint, not an additional field on login, because the client goes to a different screen, waits for user input, and makes a second call. The `request_id` from login links the two calls without re-sending the password.

"Access tokens expire in 15 minutes" → state transition: expired → refreshed → `POST /auth/refresh`. Takes the long-lived refresh token, issues a new short-lived access token. Refresh token rotation on every call: each refresh invalidates the old refresh token and issues a new one — detecting replay attacks is then easy (a reused refresh token means the old one was stolen).

"Logout" → state transition: authenticated → logged-out → `POST /auth/logout`. Takes the refresh token in the body and blacklists it in Redis. The JWT itself is stateless — you can't "revoke" it — but you can prevent refresh. `jti` of the access token also goes to the blacklist; every request checks Redis for the `jti` in the Bearer token. This is the only hole in stateless JWT: you must tolerate up to 15 minutes of access after logout. Acceptable tradeoff.

Validation check: "service accounts need programmatic access" → `client_credentials` OAuth grant. No endpoint to add — this is handled by `POST /auth/login` with a different request body (`client_id` + `client_secret` instead of email + password). One endpoint, two grant types.

### Core Endpoints

| Method | Path | Auth | Request | Response | Status Codes |
|---|---|---|---|---|---|
| POST | `/auth/login` | — | `{email, password}` | `{mfa_required?, request_id, access_token?, refresh_token?}` | 200, 401, 429 |
| POST | `/auth/verify-mfa` | — | `{request_id, mfa_code}` | `{access_token, refresh_token, expires_in}` | 200, 401, 423 |
| POST | `/auth/refresh` | — | `{refresh_token}` | `{access_token, expires_in}` | 200, 401 |
| POST | `/auth/logout` | JWT Bearer | `{refresh_token}` | `{success: true}` | 200 |
| GET | `/auth/user` | JWT Bearer | — | `{user_id, email, roles, permissions}` | 200, 401 |
| GET | `/auth/keys` | — | — | `{keys: [{kid, public_key}]}` | 200 |

### 🔍 Endpoint Stories

**`POST /auth/login`** has two response shapes and that's the interview probe. If `mfa_enabled = false`, the response body contains `access_token` and `refresh_token` — login is complete in one call. If `mfa_enabled = true`, the response body contains `mfa_required: true` and a `request_id` (a short-lived nonce stored in Redis for 5 minutes). The `request_id` is how the MFA step proves it's continuing the same login flow — without re-sending the password. Most candidates miss this: they issue a partial JWT or put MFA inline. The two-step design keeps login clean and security boundaries clear.

**`POST /auth/verify-mfa`** completes the MFA flow. `423 Locked` is the status code for "account locked after too many wrong attempts" — not `401` (wrong credentials) and not `429` (rate limit). `423` is the right HTTP status for "temporarily locked by security policy." The `request_id` is consumed on success — replaying it returns `401`. The full token pair (access + refresh) is issued only here; the previous login step issues nothing token-like.

**`POST /auth/refresh`** is where refresh token rotation happens. Old refresh token in → new access token + new refresh token out. The old refresh token is immediately blacklisted. If an attacker steals the old refresh token and tries to use it after the legitimate client already refreshed, the system detects the replay: both the legitimate client and the attacker now have different valid refresh tokens from the same parent — when the attacker's old token arrives, it's already in the blacklist.

**`POST /auth/logout`** is the most misunderstood endpoint. The access token cannot be revoked because it's stateless. What logout does: (1) blacklists the `jti` of the current access token in Redis with TTL = remaining access token lifetime, (2) blacklists the refresh token so no new access tokens can be issued. For up to 15 minutes, the access token technically still validates — but it won't refresh. This "eventual revocation" window is the accepted tradeoff for stateless JWTs at scale.

**`GET /auth/keys`** — the JWKS (JSON Web Key Set) endpoint (the standard for publishing public key material so downstream services can verify JWTs without calling the auth service). Every service that needs to validate a JWT fetches this endpoint once on startup and caches the public key. When the auth service rotates its signing key, it publishes the new key at a new `kid` (key ID) alongside the old one. JWTs signed with the old key still validate until they expire; new JWTs use the new key. Zero-downtime key rotation. Most candidates miss that key rotation without JWKS causes all services to fail validation simultaneously.

---

## Section 4 — 🔢 Scale Estimation (Minutes 5–10)

**Traffic:**
- DAU: 10M users
- Login attempts/day: 100M = ~1.16K logins/sec baseline, 3.5K peak
- Token validations: assume every request validates token = 10× traffic = 11.6K validations/sec baseline, 35K peak
- Active sessions: 1M users with 2 devices each = 2M active sessions

**Storage:**
- User credentials: 10M users × 500 bytes (email, hashed password, salt) = 5 GB (fits in single Postgres instance)
- Session data: 2M sessions × 500 bytes (user_id, roles, metadata) = 1 GB (fits in single Redis instance)
- Audit logs: 100M logins/day × 200 bytes = 20 TB/year (archive after 1 year to S3 Glacier)
- Token blacklist (revocation): 10M users × (logout events/user/year) = assume 50 revocations/user/year = 500M revocations/year = 50M active revocations (cached in Redis)

**Bandwidth:**
- Inbound (login requests): 3.5K logins/sec × 500 bytes = 1.75 MB/sec
- Outbound (token responses): 3.5K logins/sec × 2 KB (JWT token + metadata) = 7 MB/sec

**Key conclusions:**
- At 35K token validations/sec, **stateless JWT is essential** (can't do 35K DB lookups/sec on session store)
- Token validation should be < 10ms (JWT signature verification is ~1-2ms, acceptable)
- Session store (Redis) holds 2M sessions; no bottleneck at 2 devices/user
- Login service (token generation) at 3.5K logins/sec is fine (bcrypt hashing is slow ~200ms per login, so need 3-4 parallel instances)

---

## Section 5 — 🔄 Requirements Variation Table ⭐ Key Differentiator

| Requirement | Simple (startup) | Complex (enterprise) | Impact on design |
|---|---|---|---|
| **Auth method** | Username/password only | + SSO, + MFA, + passwordless | Simple DB query → OAuth/OIDC server + MFA provider + IdP federation |
| **Authorization** | Simple RBAC (admin/user) | Fine-grained ACL per resource | Role table → role_assignments + resource_permissions tables |
| **Token strategy** | Opaque tokens (server validates) | Stateless JWT | Session store (Redis) → JWT with signature verification |
| **Sessions** | No sessions (stateless) | Stateful (active session tracking) | JWT only → session table + logout tracking |
| **Scale** | 100K users | 10M users | Single auth service → distributed token generation + validation |
| **Compliance** | Basic password security | GDPR, HIPAA, SOC 2 | Plain hashing → bcrypt + audit log + encryption at rest + token revocation |

---

## Section 6 — 🏗️ High-Level Architecture (Minutes 10–25)

### 🎨 Visual — Auth System Architecture (3-Stage Evolution)

```
── Stage 1: Session-Based Auth (Stateful) ────────────────────────

Client sends credentials; server creates a session; every subsequent
request sends the session cookie and the server looks up the session.

 ┌──────────┐  POST /auth/login   ┌────────────────────────┐
 │  Client  │ ──────────────────→ │     Auth Service       │
 └──────────┘  {email, password}  │  1. bcrypt verify      │
                                  │  2. create session     │
                                  └──────┬─────────────────┘
                                         │
                                ┌────────▼────────┐
                                │  Redis Session  │
                                │  session:{id} → │
                                │  {user_id,      │
                                │   roles, tenant}│
                                └─────────────────┘

 ┌──────────┐  GET /v1/documents   ┌────────────────────────┐
 │  Client  │  Cookie: sid=abc ──→ │   API Gateway          │
 └──────────┘                      │  1. GET session:{sid}  │◀────▶ Redis
                                   │     from Redis         │
                                   │  2. Extract user_id    │
                                   │  3. Forward to service │
                                   └────────────────────────┘

BREAKING POINT 1: At 35K API requests/sec, every request does a Redis
   session lookup (10–50ms each). Redis session store becomes the
   hot bottleneck: 35K reads/sec × 50ms = ~1,750 CPU-seconds/sec.
   Even with horizontal Redis, this limits request throughput.

BREAKING POINT 2: Session state is tied to Redis. If Redis is unavailable
   (even for a failover), no request can be validated — auth is down.
   Horizontal scaling requires all Auth Service instances to share session state.

BREAKING POINT 3: Single-factor login — if Alice's password is leaked,
   her account is fully compromised. DocuSign cannot accept this risk for
   high-value e-signature workflows.
```

**DECISION — WHICH token/session strategy?**

| Option | Strength | Weakness | Verdict |
|---|---|---|---|
| Stateful session (opaque session ID, Redis lookup on every request) | Simple; instant revocation (delete session row) | 35K session lookups/sec → Redis bottleneck; shared state complicates horizontal Auth Service scaling | ❌ Bottleneck at scale |
| Stateless JWT (RSA-signed claims; signature verification only; no server lookup) | ~1-2ms CPU-bound validation; scales to 35K/sec easily; Auth Service is fully stateless | Revocation requires a separate blacklist (token valid until expiry without it) | ✅ Best |
| Hybrid (JWT for most requests + session store for revocation events only) | Fast validation + near-instant revocation; best of both | Both systems to operate; added complexity | ⚠️ Viable but operationally heavier |

> 📖 Full: **`SystemDesignConcepts/Production-Grade/Auth-and-Security/27-auth-authz-fundamentals.md`**

```
── Stage 2: Stateless JWT (No Blacklist, No MFA) ─────────────────

Auth Service issues RSA-signed JWTs. API Gateway validates the signature
locally — no session store lookup. Fast. But no revocation, no second factor.

 ┌──────────┐  POST /auth/login    ┌──────────────────────────┐
 │  Client  │ ──────────────────→ │      Auth Service        │
 └──────────┘  {email, password}  │  1. bcrypt verify (200ms)│
                                   │  2. generate JWT         │
                                   │     RS256 signed         │
                                   │     exp: +15min          │
                                   │     claims: user+roles   │
                                   └───────────┬──────────────┘
                                               │ JWT returned
                                               ▼
                                         ┌──────────┐
                                         │  Client  │
                                         │  stores  │
                                         │  JWT     │
                                         └────┬─────┘
                                              │  Bearer JWT on every request
                                              ▼
                                   ┌──────────────────────────┐
                                   │   API Gateway            │
                                   │  1. Parse JWT            │
                                   │  2. Verify RS256 sig     │◀─ public key
                                   │     with public key      │   (no DB call)
                                   │  3. Check exp claim      │
                                   │  4. Extract user_id+roles│
                                   └──────────────────────────┘

BREAKING POINT 1: No revocation. User logs out → JWT remains valid for
   up to 15 minutes. If JWT is stolen (XSS, device theft), the attacker
   has a 15-minute window with no way to close it. At DocuSign scale,
   15 minutes of unauthorized document access is unacceptable.

BREAKING POINT 2: No MFA. A compromised password = immediate full account
   access. DocuSign cannot accept single-factor auth for high-value
   e-signature workflows where legal non-repudiation is required.
```

**DECISION — WHICH revocation strategy for JWTs?**

| Option | Strength | Weakness | Verdict |
|---|---|---|---|
| No revocation (accept up-to-15-min window after logout) | Zero infra overhead; fastest validation | Stolen or admin-revoked token remains valid for full TTL — security gap unacceptable for DocuSign | ❌ Security gap |
| Very short TTL (1-minute tokens, high refresh rate) | Smaller attack window | Client must refresh every 60s; 35K × more refresh requests/sec; poor UX | ⚠️ Impractical |
| Redis blacklist (jti → revoked flag, TTL = token's remaining expiry; O(1) lookup) | Revocation is immediate; entries auto-expire (no cleanup job); adds only 1–2ms per request | Redis failure must be handled safely (fail-closed = deny access; fail-open = security risk); must choose | ✅ Best |

> 📖 Full: **`SystemDesignConcepts/Production-Grade/Auth-and-Security/27-auth-authz-fundamentals.md`**

```
── Stage 3: JWT + Redis Blacklist + MFA + RBAC (Production) ──────

Adds: (1) Redis blacklist for instant revocation, (2) MFA second factor
for login, (3) fine-grained RBAC + per-document ACL for authorization.

LOGIN FLOW (with MFA):

 ┌──────────┐  POST /auth/login    ┌──────────────────────────────┐
 │  Client  │ ──────────────────→ │        Auth Service          │
 └──────────┘  {email, password}  │  1. bcrypt verify            │
                                   │  2. MFA enabled?             │
                                   │     yes → send OTP via email │──→ Email Provider
                                   │     return {mfa_required: true}│
                                   └──────────────────────────────┘
 POST /auth/verify-mfa {code}              │
 ──────────────────────────────────────────▼
                                   ┌──────────────────────────────┐
                                   │  Auth Service (continued)    │
                                   │  3. Verify OTP from Redis    │◀────▶ Redis
                                   │     (mfa:{userId}, TTL 5min) │
                                   │  4. Generate JWT             │
                                   │     RS256, exp 15 min, jti   │
                                   │  5. Log to audit_log         │──→ Postgres
                                   └──────────────────────────────┘

API REQUEST FLOW (validation + authorization):

 ┌──────────┐  GET /v1/documents/doc-123
 │  Client  │  Authorization: Bearer {JWT}
 └────┬─────┘
      │
      ▼
 ┌─────────────────────────────────────────┐
 │          API Gateway / Middleware       │
 │  1. Parse JWT → header.payload.sig     │
 │  2. Verify RS256 sig (public key)      │
 │  3. Check exp claim (< now() → 401)    │
 │  4. Redis: blacklist:{jti} exists?     │◀────▶ Redis (1-2ms)
 │     yes → 401 (token revoked)          │
 │  5. Extract user_id, roles, tenant_id  │
 └──────────────────┬──────────────────────┘
                    │  authenticated context
                    ▼
 ┌──────────────────────────────────────────────────────┐
 │               Document Service                       │
 │  1. Check: user is admin role → grant               │
 │  2. Check: user is document owner → grant           │
 │  3. Query: resource_permissions WHERE               │
 │     resource_id = doc-123 AND user_id = alice       │◀── Postgres (1-2ms)
 │  4. If row exists and role ∈ {signer, viewer, ...}  │
 │     → grant; else → 403 Forbidden                   │
 │  5. Log to access_audit_log (immutable)             │──→ Postgres
 └──────────────────────────────────────────────────────┘

KEY INVARIANT:
   Login = bcrypt verify + optional MFA OTP (Redis TTL 5min).
   JWT = RS256 signature proves it came from Auth Service (unforgeable).
   Validation = 2-4ms total: RS256 verify (CPU) + Redis blacklist (1-2ms).
   Revocation = Redis SET blacklist:{jti} EX {remaining_TTL} on logout.
   Authorization = RBAC role claim in JWT + per-document ACL in Postgres.
   Audit = every login + every access (granted or denied) → immutable log.
```

**DECISION — WHICH authorization model?**

| Option | Strength | Weakness | Verdict |
|---|---|---|---|
| Simple RBAC (admin/user/editor, system-wide roles only) | O(1) lookup; roles embedded in JWT | Can't handle per-document permissions — admin sees ALL documents; can't make Alice a signer on doc-123 only | ❌ Insufficient for DocuSign |
| ABAC — Attribute-Based Access Control (policy engine evaluates attributes of user + resource + environment at runtime) | Maximum flexibility; can encode complex policies | Policy engine adds 10-50ms per request; complex to debug; overkill for DocuSign's relatively stable permission model | ❌ Overkill |
| RBAC + fine-grained ACL (system roles in JWT + per-document resource_permissions table) | Role check is O(1) from JWT; per-document lookup is O(log N) with (resource_id, user_id) index; balances simplicity and precision | One extra DB query per document access (1-2ms, acceptable) | ✅ Best |

> 📖 Full: **`SystemDesignConcepts/Production-Grade/Auth-and-Security/27-auth-authz-fundamentals.md`**

**DECISION — WHICH MFA method?**

| Option | Strength | Weakness | Verdict |
|---|---|---|---|
| No MFA (password only) | Zero friction UX | Compromised password = immediate full account access; unacceptable for legal e-signature | ❌ Too risky |
| SMS OTP | No app install; familiar | Per-SMS cost; SIM swap attacks (attacker transfers victim's phone number to own SIM, intercepts OTP codes) | ❌ Attackable |
| Email OTP (default) + TOTP authenticator app (optional upgrade) | Email: accessible to all users, no extra install; TOTP: offline, phishing-resistant, instant codes | Email: delivery can be delayed (5-min OTP window mitigates this); TOTP: requires app install, device loss = lockout | ✅ Best |

> 📖 Full: **`SystemDesignConcepts/Production-Grade/Auth-and-Security/13-security-pki.md`**

**Data flow walkthrough (say this out loud):**

**Flow 1 — Login (with MFA):**
1. Client calls `POST /auth/login { email, password }`
2. Auth Service bcrypt-verifies password against DB (slow: ~200ms by design — brute-force resistance)
3. If mismatch → 401 Unauthorized
4. If match and MFA enabled → generate 6-digit code, store in Redis (`mfa:{userId}`, TTL 5 min), send via email
5. Client calls `POST /auth/verify-mfa { code }`; Auth Service checks Redis key, deletes on success (one-time use)
6. Auth Service generates JWT: RS256-signed, exp +15 min, claims: `sub`, `roles`, `tenant_id`, `jti`
7. Returns `{ access_token, refresh_token (7-day), expires_in: 900 }`
8. Writes to `access_audit_log` (user, IP, timestamp, success)

**Flow 2 — API Request (validation + authorization):**
1. Client sends `Authorization: Bearer {JWT}` on every request
2. API Gateway: split on `.`, verify RS256 signature with public key (~1ms CPU), check `exp`, check Redis blacklist on `jti` (~1ms I/O)
3. Any check fails → 401
4. Extracts `user_id`, `roles`, `tenant_id` — passes as request context
5. Document Service checks: admin role in claims? owner? explicit row in `resource_permissions`?
6. Access granted → serve; denied → 403; all outcomes written to `access_audit_log`

**Flow 3 — Logout (Revocation):**
1. Client calls `POST /auth/logout { refresh_token }`
2. Auth Service: `redis.set("blacklist:{jti}", 1, EX remaining_ttl)` — TTL = token's remaining lifetime
3. Future requests with this `jti` → Redis key exists → 401 immediately
4. After TTL, Redis auto-expires the entry (no cleanup job needed)

---

## Section 7 — 🔬 Core Component Deep Dives (Minutes 25–40)

### Deep Dive 1: JWT Token Design + Signature Verification

**Why this is the most critical component:**
JWT is the contract between Auth Service and every other service. A forged or tampered JWT could grant unauthorized access. Signature verification proves the token came from the trusted Auth Service. At 35K token validations/sec, verification must be fast (~1-2ms).

**Options considered:**

| Option | Pros | Cons |
|---|---|---|
| **Option A: Opaque tokens (server stores session)** | Simple; revocation is instant; server controls lifetime | 35K validations/sec = 35K DB/Redis lookups/sec = bottleneck; slower (10-50ms per validation) |
| **Option B: JWT (stateless tokens with signature)** | Stateless; fast validation (1-2ms signature check); scales to 35K QPS easily | Complex; revocation requires blacklist (eventual consistency); key rotation needed |
| **Option C: Hybrid (JWT + session store)** | Best of both: fast validation + instant revocation | Added complexity; larger attack surface |

**Decision: Option B (JWT with signature verification).**

Because at 35K validations/sec, stateless JWT is the only scalable option. Blacklist adds 1-2ms overhead (Redis lookup), acceptable.

**Implementation sketch:**

```java
// JWT Generation (Auth Service)
public class JwtTokenProvider {
    private final RSAPrivateKey privateKey;  // kept secret in Auth Service only
    private final String issuer = "auth-service.docusign.com";

    public String generateAccessToken(String userId, List<String> roles, String tenantId) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(15, ChronoUnit.MINUTES);

        // Header
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)  // RS256: RSA signature + SHA-256 hash; the private key signs a SHA-256 fingerprint of the token header+payload; any service with the public key can verify it was us who signed it, without needing the private key
            .keyID("key-1")  // kid (Key ID): tells the verifier which public key to use for this token; when you rotate keys, new tokens say "key-2", old tokens still say "key-1"; services fetch the matching public key from the JWKS endpoint and both keys work simultaneously during rotation
            .build();

        // Payload (claims)
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .subject(userId)  // sub: who the token is about
            .issuer(issuer)   // iss: who issued the token
            .audience("api-service.docusign.com")  // aud: who can use it
            .issueTime(Date.from(issuedAt))
            .expirationTime(Date.from(expiresAt))
            .claim("roles", roles)  // custom claim: user's roles
            .claim("tenant_id", tenantId)  // custom claim: which tenant
            .claim("jti", UUID.randomUUID().toString())  // jti (JWT ID): a unique identifier for this specific token; when a user logs out, you add their jti to the Redis blacklist; on every request, check "is this jti in the blacklist?" to detect revoked tokens even before expiry
            .build();

        // Sign with private key
        SignedJWT signedJWT = new SignedJWT(header, claims);
        JWSSigner signer = new RSASSASigner(privateKey);
        signedJWT.sign(signer);

        return signedJWT.serialize();  // Returns: "eyJhbGciOiJSUzI1NiIsImtpZCI6ImtleS0xIn0.eyJz..."
    }
}

// JWT Verification (API Gateway / every service)
public class JwtTokenValidator {
    private final RSAPublicKey publicKey;  // Auth Service publishes this publicly
    private final RedisTemplate redis;

    public JWTClaimsSet validateToken(String token) throws JWTException {
        try {
            // Step 1: Parse JWT (split on ".")
            SignedJWT signedJWT = SignedJWT.parse(token);

            // Step 2: Verify signature using public key
            JWSVerifier verifier = new RSASSAVerifier(publicKey);
            if (!signedJWT.verify(verifier)) {
                throw new JWTException("Invalid signature — token forged");
            }

            // Step 3: Check expiry
            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            if (claims.getExpirationTime().before(new Date())) {
                throw new JWTException("Token expired");
            }

            // Step 4: Check token blacklist (revocation)
            String jti = claims.getStringClaim("jti");
            // hasKey() is O(1), returns Boolean (nullable) — Boolean.TRUE.equals is NPE-safe
            // Prefer hasKey over get() to avoid type-cast issues with generic RedisTemplate
            if (Boolean.TRUE.equals(redis.hasKey("blacklist:" + jti))) {
                throw new JWTException("Token was revoked (logged out)");
            }

            // Step 5: Validate issuer and audience
            if (!claims.getIssuer().equals("auth-service.docusign.com")) {
                throw new JWTException("Untrusted issuer");
            }

            return claims;
        } catch (ParseException e) {
            throw new JWTException("Invalid JWT format", e);
        }
    }
}
```

**Performance detail:**
- JWT signature verification (RSA-SHA256): ~1-2ms (CPU-bound, not I/O)
- Token blacklist lookup (Redis): ~1-2ms (network, but fast)
- Total validation: ~2-4ms per token (35K tokens/sec = 35K × 0.004s = 140 CPU-sec/sec = ~0.5 cores; negligible)

**Why this deep dive matters:**
- JWT signature verification is the foundation of token trust
- Public key is safe to distribute (can't forge signature without private key)
- Blacklist adds eventual consistency (user logs out, token is immediately blacklisted in Redis, future requests get 401)

---

### Deep Dive 2: Fine-Grained Authorization (RBAC + ACL)

**Why this is the most critical component:**
Authorization determines what users can access. A bug here could expose documents to wrong users (security disaster). DocuSign has multi-party documents (5 signers, each with different roles). Authorization must be per-document + per-role.

**Options considered:**

| Option | Pros | Cons |
|---|---|---|
| **Option A: Simple RBAC (admin/user roles)** | Simple; 1 DB lookup; fast (O(1)) | Can't handle per-document permissions (all admins see all docs) |
| **Option B: Fine-grained ACL (per-resource)** | Precise; each user's permissions explicit | Complex queries; slower (O(N) per document if many users) |
| **Option C: Attribute-Based Access Control (ABAC — instead of checking "what role does this user have?", it evaluates policies against attributes of the user, the resource, and the environment at request time; e.g., "allow if user.department == doc.department AND doc.status == 'draft' AND time is business hours"; extremely flexible but each access check runs a policy engine)** | Most flexible; policies on document attributes (status, owner, date) | Complex; slow; hard to debug |

**Decision: Option B (RBAC + ACL).**

Because it balances simplicity (roles) with granularity (per-document permissions). Query is O(log N) with proper indexing.

**Implementation sketch:**

```sql
-- Users table
CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255),  -- bcrypt hash (bcrypt is a slow, salted password-hashing function; "slow by design" — it takes ~200ms to hash one password, making brute-force of a stolen DB impractical; "salted" means a random value is mixed in before hashing so two users with the same password get different hashes, defeating precomputed rainbow-table attacks)
    tenant_id UUID NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    
    INDEX idx_tenant_email (tenant_id, email)
);

-- Roles (global: admin, editor, viewer, signer, approver)
CREATE TABLE roles (
    id UUID PRIMARY KEY,
    name VARCHAR(50) NOT NULL,  -- admin, editor, viewer, signer
    permissions TEXT[],  -- e.g., ["read:documents", "write:documents", "sign:documents"]
    UNIQUE (name)
);

-- User roles (which role does each user have?)
CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users(id),
    role_id UUID NOT NULL REFERENCES roles(id),
    tenant_id UUID NOT NULL,
    
    PRIMARY KEY (user_id, role_id),
    INDEX idx_user_roles (user_id, tenant_id)
);

-- Fine-grained permissions per document
CREATE TABLE resource_permissions (
    id UUID PRIMARY KEY,
    resource_id UUID NOT NULL,  -- document ID
    user_id UUID NOT NULL,
    role VARCHAR(50),  -- signer, viewer, approver (per-document roles)
    granted_by UUID,  -- who granted this permission
    granted_at TIMESTAMPTZ DEFAULT NOW(),
    
    INDEX idx_resource_user (resource_id, user_id),
    UNIQUE (resource_id, user_id)  -- one permission per user per resource
);

-- Audit log (who accessed what when)
CREATE TABLE access_audit_log (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    resource_id UUID NOT NULL,
    action VARCHAR(50),  -- LOGIN, READ_DOCUMENT, SIGN_DOCUMENT, DELETE_DOCUMENT
    allowed BOOLEAN,  -- true if access granted, false if denied
    timestamp TIMESTAMPTZ DEFAULT NOW(),
    ip_address INET,
    user_agent TEXT,
    
    INDEX idx_user_audit (user_id, timestamp DESC),
    INDEX idx_resource_audit (resource_id, timestamp DESC)
);
```

**Authorization logic:**

```java
public class AuthorizationService {
    private final ResourcePermissionRepository permRepo;
    private final UserRoleRepository userRoleRepo;
    private final AuditLogRepository auditRepo;

    /**
     * Check if user can perform action on resource (document).
     * Returns true if authorized, false otherwise.
     * Always logs to audit trail.
     */
    public boolean canAccess(String userId, String documentId, String action) {
        // Step 1: Check if user has admin role (admins can access everything)
        boolean isAdmin = userRoleRepo.hasRole(userId, "admin");
        if (isAdmin) {
            logAccess(userId, documentId, action, true);
            return true;
        }

        // Step 2: Check if user is the document owner
        Document doc = documentRepo.findById(documentId);
        if (doc.getOwnerId().equals(userId)) {
            logAccess(userId, documentId, action, true);
            return true;
        }

        // Step 3: Check explicit permission in resource_permissions table
        ResourcePermission perm = permRepo.findByResourceAndUser(documentId, userId);
        if (perm == null) {
            logAccess(userId, documentId, action, false);
            return false;  // No permission
        }

        // Step 4: Check if permission's role allows the action
        // Example: if perm.role = "viewer", can only read; can't write/sign
        boolean allowed = canPerformAction(perm.getRole(), action);
        logAccess(userId, documentId, action, allowed);
        return allowed;
    }

    private boolean canPerformAction(String role, String action) {
        // Mapping: role -> allowed actions
        switch (role) {
            case "signer":
                return action.equals("read") || action.equals("sign");
            case "approver":
                return action.equals("read") || action.equals("approve");
            case "viewer":
                return action.equals("read");
            case "editor":
                return action.equals("read") || action.equals("write");
            default:
                return false;
        }
    }

    private void logAccess(String userId, String docId, String action, boolean allowed) {
        AccessAuditLog log = new AccessAuditLog();
        log.setUserId(userId);
        log.setResourceId(docId);
        log.setAction(action);
        log.setAllowed(allowed);
        log.setTimestamp(Instant.now());
        log.setIpAddress(RequestContext.getClientIp());
        log.setUserAgent(RequestContext.getUserAgent());
        auditRepo.save(log);
    }
}
```

**Query performance:**
- `hasRole(user, admin)`: `SELECT COUNT(*) FROM user_roles WHERE user_id = ? AND role = 'admin'` — ~1ms (indexed)
- `findByResourceAndUser(doc_id, user_id)`: `SELECT * FROM resource_permissions WHERE resource_id = ? AND user_id = ?` — ~1-2ms (indexed)
- Total authorization check: ~3-5ms per request (acceptable)

**Why this deep dive matters:**
- Authorization is the security boundary; must be correct
- Fine-grained permissions are essential for multi-party documents (5 signers, different roles per signer)
- Audit logging provides compliance proof (who accessed what when)

---

### Deep Dive 3: Multi-Factor Authentication (MFA) Flow

**Why this is the most critical component:**
MFA prevents account takeover (common attack vector). At DocuSign scale (10M users), even 0.1% account takeovers = 10K compromised accounts. MFA adds 1-2 seconds to login but dramatically improves security. Implementation must be reliable (MFA codes must not get lost in email).

**Options considered:**

| Option | Pros | Cons |
|---|---|---|
| **Option A: Email OTP (One-Time Password)** | Simple; no app required; familiar to users | Slow (users wait for email); email can be delayed; phishable (attacker creates a fake login page, user types code there, attacker replays it in real-time) |
| **Option B: Authenticator app (TOTP — Time-based One-Time Password: the app and server share a secret seed; both compute `HMAC(seed, current_30_second_window)` independently and compare; codes expire every 30 seconds; works offline)** | Fast (code is instant); no email dependency; phishing-resistant (codes are bound to the exact origin domain, a fake site gets a code that doesn't work on the real site) | Requires app installation; users lose phone = locked out |
| **Option C: SMS OTP** | Fast; no app; high UX | Expensive (per-SMS cost); SMS can be intercepted (less secure) |

**Decision: Option A + B (Email for free tier, authenticator app optional for high-security accounts).**

Because email is simple and accessible; authenticator app for power users/admins who want stronger security.

**Implementation sketch:**

```java
public class MFAService {
    private final UserRepository userRepo;
    private final EmailService emailService;
    private final RedisTemplate redis;

    /**
     * Step 2 of login: user has valid password.
     * If MFA enabled → generate code, send via email.
     */
    public void sendMFACode(String userId) {
        User user = userRepo.findById(userId);
        if (!user.isMfaEnabled()) {
            return;  // Skip MFA
        }

        // Step 1: Generate 6-digit code
        String code = generateRandomCode(6);  // e.g., "123456"

        // Step 2: Store in Redis with TTL (5 minutes)
        String cacheKey = "mfa:" + userId;
        redis.opsForValue().set(cacheKey, code, Duration.ofMinutes(5));

        // Step 3: Send via email
        String subject = "Your DocuSign MFA Code";
        String body = String.format(
            "Your code is: %s\nValid for 5 minutes.\nDo not share this code.",
            code
        );
        emailService.sendEmail(user.getEmail(), subject, body);
    }

    /**
     * Step 3 of login: user submits MFA code from email.
     * Returns true if code is correct, false otherwise.
     */
    public boolean verifyMFACode(String userId, String submittedCode) {
        String cacheKey = "mfa:" + userId;
        String storedCode = redis.opsForValue().get(cacheKey, String.class);

        if (storedCode == null) {
            // Code expired or was already used
            return false;
        }

        if (!storedCode.equals(submittedCode)) {
            // Code mismatch
            return false;
        }

        // Code is valid: delete it (one-time use)
        redis.delete(cacheKey);
        return true;
    }

    /**
     * Alternative: TOTP (Time-based One-Time Password).
     * User sets up authenticator app (Google Authenticator, Authy).
     * App generates 6-digit code every 30 seconds (TOTP algorithm).
     */
    public String generateTOTPSecret(String userId) {
        // Generate random secret (Base32-encoded)
        String secret = generateRandomBase32Secret(32);  // e.g., "JBSWY3DPEBLW64TMMQ======"

        // Store in user table (encrypted)
        User user = userRepo.findById(userId);
        user.setTotpSecret(encrypt(secret));  // encrypted at rest
        userRepo.save(user);

        // Return QR code for user to scan with authenticator app
        String qrCodeUrl = generateTOTPQRCode(userId, secret);
        return qrCodeUrl;
    }

    /**
     * Verify TOTP code (from authenticator app).
     * Code is 6 digits, changes every 30 seconds.
     */
    public boolean verifyTOTPCode(String userId, String submittedCode) {
        User user = userRepo.findById(userId);
        String secret = decrypt(user.getTotpSecret());

        // TOTP algorithm: compute expected code for current time
        String expectedCode = generateTOTPCode(secret, Instant.now());

        return submittedCode.equals(expectedCode);
    }

    private String generateRandomCode(int length) {
        Random random = new Random();
        return String.format("%0" + length + "d", random.nextInt((int) Math.pow(10, length)));
    }
}
```

**MFA flow:**
1. User logs in: `POST /auth/login { email: alice@example.com, password }`
2. Auth Service validates password, checks MFA enabled
3. If enabled → sends code via email → returns `{ mfa_required: true, request_id: "..." }`
4. User receives email with code
5. User submits code: `POST /auth/verify-mfa { request_id, mfa_code: "123456" }`
6. If correct → issue JWT access token
7. If incorrect (3× in 5 minutes) → lock account, send alert to user email

**Why this deep dive matters:**
- MFA is the strongest defense against account takeover (even if password is compromised)
- Email OTP is simple but slow; TOTP is faster but requires app setup
- Rate limiting on failed attempts (3 failures = account locked for 15 min) prevents brute force

---

## Section 9 — 🗄️ Data Model

See **Deep Dive 2** for complete schema. Key tables:
- **users** (email, password_hash, mfa_enabled, totp_secret)
- **user_roles** (user_id, role_id — global roles)
- **resource_permissions** (resource_id, user_id, role — per-document)
- **access_audit_log** (immutable, append-only)

---

## Section 10 — ⚠️ Trade-Offs + Failure Modes (Minutes 45–52)

### Trade-off 1: Stateless JWT vs Stateful Sessions

**Chose:** Stateless JWT.

**Gain:** Scales to 35K validations/sec; no session store bottleneck; can run auth service on multiple instances without state sharing.

**Lose:** Revocation has latency (user logs out, but token stays valid until expiry or blacklist check). Requires token blacklist (Redis) for revocation.

**Failure mode if wrong:** If you use stateful sessions (Redis) at 35K validations/sec, Redis becomes bottleneck (typical capacity: 50K ops/sec; you're at 70% of max). Adds 10-50ms latency per request. **Business impact:** Every API call in a signing ceremony must validate the session — 50ms added latency × 35K req/sec means all API calls degrade simultaneously — for DocuSign this means the signing ceremony UX (where a signer is waiting to click "Adopt & Sign" — the moment of highest legal significance) adds a 50ms penalty to every page interaction, degrading the experience at exactly the wrong moment and increasing ceremony abandonment rate.

---

### Trade-off 2: Email MFA vs Authenticator App

**Chose:** Email OTP (default), Authenticator app (optional).

**Gain:** Email is accessible to all users; no app installation needed. Authenticator is stronger (offline, phishing-resistant).

**Lose:** Email can be delayed (SLA: 5 minutes); users without authenticator app are vulnerable if email is compromised.

**Failure mode if wrong:** If only authenticator app, 30% of users (non-technical, older age group) won't set it up; adoption drops. If only email, account takeover risk higher (email can be intercepted/delayed). **Business impact:** For DocuSign: a 30% non-adoption rate for authenticator-only MFA leaves 30% of signers unprotected against SIM-swapping and email compromise — an attacker who gains account access can forge signing events, redirect envelopes to themselves, and the resulting fraudulent contracts carry real legal weight — a critical reputational and regulatory liability for DocuSign in regulated industries (finance, healthcare, legal).

---

### Trade-off 3: Token Blacklist (Redis) vs Signed Tokens Only

**Chose:** JWT signature + Redis blacklist.

**Gain:** Revocation is immediate (logout is instant); tokens can be forcefully invalidated.

**Lose:** Redis adds 1-2ms latency to every validation; requires Redis uptime for logout to work.

**Failure mode if wrong:** If no blacklist, user logs out but token is still valid (until expiry). User thinks they're logged out but aren't (security hole). If Redis is down, logout fails (availability issue). **Business impact:** A signer uses a public computer, logs out, and believes their session is terminated — but the JWT remains valid for its remaining 60-minute lifetime — for DocuSign this means the next person at that computer clicks "back" and can view, download, or interact with in-progress contracts and signed PDFs, a serious legal privacy violation that exposes PII and confidential agreement terms without the account holder's consent.

---

## Section 11 — 🔐 DocuSign-Specific Depth (Minutes 52–57)

**Why this question is fundamental for SDE-3:**

Auth is the first line of defense. A single bypass could expose 10M users' private contracts. DocuSign requires: (1) strong auth (MFA for high-value accounts), (2) fine-grained permissions (5 signers, different roles), (3) audit trails (legal compliance), (4) per-document access control (Signer can sign doc X but only view doc Y).

**DocuSign-specific angles:**

1. **Legal compliance**: Audit log must prove "who accessed what when" for 7+ years (for litigation discovery)
2. **Multi-party signing**: Each signer has a role (signer, approver, reviewer) per document
3. **Key rotation**: Auth Service rotates signing keys quarterly; old keys remain for token validation (backward compatibility)
4. **Revocation during signing**: If a signer is removed mid-process, their permissions are immediately revoked (token blacklist)

5. **CSRF (Cross-Site Request Forgery) — must name this at SDE-3:**

CSRF (Cross-Site Request Forgery — an attack where a malicious website tricks a logged-in user's browser into making an unintended request to your site; because the browser automatically includes cookies with every request, the victim's authenticated session is used without their knowledge; classic example: user logged into DocuSign, visits attacker's site, attacker's page triggers `<form action="https://docusign.com/documents/123/sign" method="POST">` — browser sends request with DocuSign's session cookie, signing happens without the user's intent) is a live threat for any application that uses cookies for session management.

**Why it matters for DocuSign:** A signed contract is legally binding. If an attacker can forge a signature request using the victim's session cookie, they can sign legally binding documents on behalf of the victim. This is a catastrophic attack surface.

**The defense — three layers:**

1. **Use JWT Bearer tokens in the `Authorization` header, not cookies.** Browsers don't auto-attach `Authorization` headers to cross-origin requests. CSRF only works when the browser automatically attaches credentials (cookies). JWT in `Authorization` header does not auto-attach → CSRF is structurally eliminated for pure API clients.

2. **For any session cookie-based path (web UI, SSO callback):** Use the `SameSite=Strict` cookie attribute. This instructs the browser: "only send this cookie when the request originates from the same domain." Cross-origin form submissions or fetch requests from attacker's domain → browser withholds the cookie → CSRF fails.

3. **CSRF token (Double Submit Cookie pattern) for legacy or browser-based flows:**
   - Server issues a random CSRF token in a non-HttpOnly cookie (readable by JS)
   - Client JS reads the cookie, sends it as a custom header `X-CSRF-Token: <value>`
   - Server validates the header matches the cookie value
   - Cross-origin attackers can't read the cookie (SOP) → can't set the header → CSRF fails

**In an interview:** "For our API endpoints, CSRF is mitigated by using JWT Bearer tokens in the Authorization header — browsers don't auto-attach these cross-origin, so there's no vector. For the web UI login flow that sets a session cookie, I'd use `SameSite=Strict` as a defense-in-depth layer. For the SAML SSO callback endpoint (must accept POST from a third-party IdP), I'd use the Double Submit Cookie pattern. At DocuSign, any CSRF bypass on signing endpoints would allow an attacker to forge legally binding contracts on behalf of victims — this must be addressed, not assumed to be handled by the framework."

6. **Session fixation — must name this in any auth design:**

Session fixation (an attack where the attacker pre-sets the victim's session ID before they log in, then waits for the victim to authenticate; since the session ID is fixed by the attacker, the attacker knows the ID of the now-authenticated session and can hijack it without ever stealing a cookie) is a classic attack that predates JWT.

**Example attack:**
1. Attacker visits DocuSign, gets a new session ID: `session_abc`
2. Attacker sends the victim a link: `docusign.com/login?session_id=session_abc` (or sets a session cookie via XSS)
3. Victim logs in using that session ID — the server elevates it to an authenticated session
4. Attacker now knows `session_abc` is authenticated → hijacks the session

**The fix (one line of code):** **On every successful login, invalidate the existing session and issue a brand new session ID.** Never elevate a pre-login session to post-login.

```java
// WRONG — session fixation vulnerability:
public String login(String email, String password, String existingSessionId) {
    User user = authenticate(email, password);
    session.setUserId(existingSessionId, user.getId()); // attacker knew existingSessionId
    return existingSessionId;
}

// CORRECT — rotate session ID on login:
public String login(String email, String password, String existingSessionId) {
    User user = authenticate(email, password);
    sessionStore.invalidate(existingSessionId);        // destroy pre-login session
    String newSessionId = UUID.randomUUID().toString(); // generate new, unpredictable ID
    sessionStore.create(newSessionId, user.getId());
    return newSessionId;
}
```

**Why JWT avoids this by design:** JWTs are stateless — there's no pre-auth token to fixate on. The token is generated for the first time post-authentication. Session fixation is structurally impossible with JWTs. Name this as a reason to prefer JWT over session cookies, not just as a performance argument.

**In an interview:** "Session fixation is mitigated by rotating the session ID on successful login — the pre-auth session is invalidated and a new one is issued. This is why JWT is preferable for our use case: the access token only exists post-authentication, so there's no session for an attacker to pre-set. For any cookie-based flows (e.g., web UI), I'd enforce session rotation as a matter of Spring Security baseline configuration — `SessionManagementConfigurer.sessionFixation().newSession()`."

**Your answer should include:**

> "Login returns JWT access token (15 min TTL) + refresh token (7 day TTL). JWT is signed with RSA private key (only Auth Service can sign); every service validates signature with public key (no server state needed). At 35K token validations/sec, signature verification is fast (~1-2ms). Token revocation happens via Redis blacklist: on logout, token's jti (unique ID) is added to blacklist; future requests are denied."

> "Authorization is: (1) admin role grants access to all documents, (2) document owner has full access, (3) explicit per-document permissions for other users (signer, viewer, approver roles). Audit log captures every access (user, document, action, timestamp, IP) for legal compliance. MFA is optional but recommended for accounts with sensitive documents."

---

## Section 12 — 🔬 Where the Interviewer Will Probe (Minutes 57–60)

### Tier 1 — Surface Probe

**Q: "Why JWT and not session cookies?"**

> At 35K token validations/sec, stateless JWT (1-2ms signature check) scales better than session cookies (10-50ms Redis/DB lookup per validation). JWT can run on distributed API instances without state; session cookies require shared session store.

### Tier 2 — Deep Probe

**Q: "How do you handle key rotation when Auth Service changes its signing key?"**

> Auth Service publishes public keys at `/auth/keys` with key ID (kid). JWT header includes which key was used (kid). Old keys are kept for 30 days (allow tokens signed with old key to validate). New keys are published before rotation. Services pull `/auth/keys` and cache locally (update every 24h). This allows seamless key rotation without breaking existing tokens.

### Tier 3 — Cross-Concept Probe

**Q: "Your token blacklist (Redis) has single-node failure mode. If Redis goes down, how does logout work?"**

> If Redis is down, revocation checks fail (return 5xx error). Options: (1) fail open (allow access despite revocation failure — availability over security), (2) fail closed (deny access until Redis recovers — security over availability). DocuSign should fail closed (security-first). During Redis outage, recent logouts might not be revoked immediately, but system is still secure. Recovery: Redis has persistence (AOF/RDB), so it recovers with blacklist intact. For long outages (> 1 hour), can manually invalidate user sessions via direct auth service call.

---

### New Deep Probes (Tier 2 — added Jul 4, 2026)

**Q: "Your mobile app needs to perform OAuth login. But mobile apps can't safely store a client_secret (an attacker can decompile the APK). How do you secure the OAuth flow for native apps?"**
> The standard solution is **PKCE** (Proof Key for Code Exchange — pronounced "pixy"; an OAuth 2.0 extension where the client proves it started the flow without needing a client_secret, making it safe for public clients like mobile apps that can't store secrets securely):
>
> **Flow:**
> 1. Mobile app generates a random `code_verifier` (43–128 character random string, e.g., `s7n4r2k...`)
> 2. App computes `code_challenge = BASE64URL(SHA256(code_verifier))` — a one-way hash
> 3. App sends `GET /authorize?code_challenge={hash}&code_challenge_method=S256` to auth server
> 4. User logs in; auth server stores the challenge, returns an authorization code
> 5. App sends `POST /token {code, code_verifier}` — note: sends the ORIGINAL verifier, not the hash
> 6. Auth server hashes the received verifier and checks it matches the stored challenge
> 7. If match: issues JWT. If no match: reject.
>
> **Why this prevents attacks:** If an attacker intercepts the authorization code (via URL redirect sniffing on a rooted device), they can't exchange it for a token — they don't know the `code_verifier` (it was never transmitted, only its hash was). The one-way hash ensures knowledge of the challenge doesn't reveal the verifier.
>
> **In an interview:** "For mobile OAuth, I'd use PKCE. No client_secret needed — the code_verifier proves the token requester is the same client that started the flow. It's the RFC-recommended approach (RFC 7636) for all public clients — mobile apps, SPAs, and CLI tools."

---

**Q: "Your refresh token lasts 7 days. If it's stolen (e.g., by malware reading app storage), the attacker has 7 days of silent access. How do you detect and limit this?"**
> **Refresh token rotation** — every time a refresh token is used, the auth server issues a NEW refresh token and immediately invalidates the old one. Tokens form a "family" (a chain of one-time-use refresh tokens for one session).
>
> **Why this detects theft:**
> - User legitimately uses RT1 → gets AT + RT2 (RT1 invalidated)
> - Attacker also has RT1 (stolen), uses it → auth server sees RT1 is already invalidated (reuse detected!)
> - Auth server detects anomaly: revokes the ENTIRE token family → forces re-login for everyone holding tokens in this session
>
> **Implementation:**
> ```sql
> -- token_family table: tracks one session's token chain
> token_family_id, user_id, current_rt_hash, parent_rt_hash, status
>
> -- On refresh token use:
> -- 1. Find row by current_rt_hash
> -- 2. If status = USED → token reuse detected → revoke entire family (UPDATE ... SET status = REVOKED WHERE token_family_id = ?)
> -- 3. If status = ACTIVE → mark this row USED, insert new row with new RT hash
> ```
>
> **Trade-off:** If the legitimate user's refresh request fails mid-flight (network error before they receive the new RT), they effectively lose their session (old RT invalidated, new RT never received). Fix: implement retry with idempotency key on the token endpoint — same request twice returns same RT.
>
> **In an interview:** "Refresh token rotation limits the attack window from 7 days to 'one legitimate use.' The first time the attacker uses the stolen RT, both the attacker and the legitimate user are forced to re-login. It's not perfect (attacker might use the RT first), but it guarantees detection on first use."

---

### New Cross-Concept Probe (Tier 3 — added Jul 4, 2026)

**Q: "DocuSign supports enterprise SSO via SAML 2.0. An enterprise customer's Okta sends a SAML assertion when their employee logs in. How does your auth service trust and process that assertion, and how do you prevent a forged SAML assertion?"**
> SAML 2.0 (Security Assertion Markup Language — the XML-based protocol that enterprise Identity Providers like Okta and Azure AD use to send signed "proof of identity" assertions to Service Providers like DocuSign; an assertion is an XML document saying "User john@acme.com authenticated successfully at 2026-07-04T10:30:00Z, here are their groups") works via XML digital signatures:
>
> **Trust setup (one-time, per customer):**
> - Enterprise IT admin provides their IdP's X.509 certificate (public key) to DocuSign
> - DocuSign stores it: `saml_identity_providers(tenant_id, idp_entity_id, idp_certificate_pem)`
>
> **Login flow:**
> 1. User visits DocuSign → redirected to their company's Okta (IdP)
> 2. Okta authenticates user, generates SAML assertion XML (contains: user email, groups, timestamp, expiry)
> 3. Okta signs the assertion with their private key (RSA signature over the XML)
> 4. Okta redirects user back to DocuSign's assertion consumer service URL with the signed XML
>
> **DocuSign validation (critical steps):**
> 1. Look up the tenant's IdP certificate from DB
> 2. Verify the XML signature using the IdP's public key — if tampered, signature fails
> 3. Check `NotOnOrAfter` (assertion expiry timestamp) — reject if in the past (replay attack prevention)
> 4. Check `InResponseTo` (ties assertion to a specific auth request) — prevents cross-site assertion injection
> 5. Extract `NameID` (user's email), look up or provision the user in DocuSign's DB
> 6. Issue DocuSign JWT for this user (now they're authenticated in DocuSign's system)
>
> **Forgery prevention:** Steps 2, 3, 4 are non-negotiable. A forged assertion fails step 2 (wrong signature). A replayed assertion fails step 3 (expired). A stolen assertion from another SP fails step 4 (wrong InResponseTo).
>
> **In an interview:** "SAML trust is bootstrapped by exchanging certificates. At assertion time, we verify the XML signature cryptographically — an attacker would need to compromise the customer's Okta private key to forge a valid assertion. Additionally, the assertion has a short TTL (5 min) and is tied to a specific request, preventing replay and cross-site attacks."

---

## Section 13 — 🐞 Common Mistakes on This Question

- **Mistake 1:** "Every request validates token by querying the session table in Postgres." → **Why it's wrong:** At 35K validations/sec, DB becomes bottleneck (typical capacity: 1-2K queries/sec). → **What to say instead:** "Stateless JWT: signature verification is CPU-bound (~1-2ms), not I/O. No DB lookup needed per request. Revocation via Redis blacklist (O(1) lookup)."

- **Mistake 2:** "Store user passwords in plaintext or with simple hash (MD5)." → **Why it's wrong:** Password breach = 10M users compromised. Attackers can rainbow-table (a rainbow table is a giant precomputed lookup of hash→password pairs; if you know the hash of "password123" from MD5 is `482c811da5d5b4bc6d497ffa98491e38`, you build a table of billions of such mappings offline; then a stolen DB of MD5 hashes is cracked in seconds by lookup; bcrypt's per-user salt makes each hash unique so no precomputed table can apply) simple hashes. → **What to say instead:** "Hash with bcrypt (salted, slow by design ~200ms per hash). Never store plaintext. Even if DB is breached, passwords are useless."

- **Mistake 3:** "Access token lifetime is 7 days (same as refresh token)." → **Why it's wrong:** If access token is compromised, attacker has 7 days of access. Long-lived tokens are high-risk. → **What to say instead:** "Access token: 15 minutes (if stolen, window of vulnerability is small). Refresh token: 7 days (user doesn't re-login often). Refresh token is more closely guarded (not sent on every request)."

---

## Section 14 — 🧭 DocuSign Dimensions Checklist

| Dimension | Relevant? | How your design addresses it |
|---|---|---|
| **Testability** | ✅ | JWT validation is deterministic (same RS256 signature + same token → always VALID or always INVALID). Unit test: sign a test token with a test private key, verify with the public key — no live auth service needed. RBAC authorization logic (hasPermission(user, resource, action)) is a pure function testable with mock user + mock resource. |
| **Usability** | ✅ | POST /login → {access_token (15min), refresh_token (7 days)} — transparent token refresh via POST /auth/refresh means users never see a re-login prompt during normal usage. MFA is opt-in: non-MFA users see no friction. For DocuSign: a signer who clicks an envelope link is auto-authenticated via the signing ceremony URL token — no separate login screen interrupts the signing flow. |
| **Extensibility** | ✅ | New permission types (sign, view, approve, manage_team) are new rows in resource_permissions — no code changes. New MFA methods (hardware FIDO2 key, magic link) = new MFAStrategy implementation injected via Spring bean. For DocuSign: adding a new role "Notary" with a specific permission set = one INSERT into roles + role_permissions, zero code deployment. |
| **Security** | ✅ | RS256 signature prevents token forgery (private key never leaves Auth Service; all validators use public key). Bcrypt (cost factor 12, ~200ms) makes brute-force of 1M passwords take ~57 hours per attacker request. Redis blacklist (jti → revoked, TTL = token's remaining lifetime) ensures immediate revocation on logout. For DocuSign: a stolen JWT from a leaked log file is immediately invalidated once the user logs out — the blacklist closes the replay window. |
| **Availability** | ✅ | JWT signature validation is CPU-only (RSA verify ~1-2ms, no external call) — at 35K validations/sec (Section 4), 10 stateless auth service instances handle validation in parallel. Redis blacklist checked via GET (< 1ms). If Redis is down: fail-closed (deny access) — correct for security. Redis HA via Sentinel prevents unplanned downtime. |
| **Scalability** | ✅ | Stateless JWT handles 35K validations/sec (Section 4) across 10 horizontally scaled instances — no session store bottleneck. Bcrypt login throughput: 200ms/hash × 10 instances = 50 logins/sec capacity (Section 4: 3.5K logins/sec peak → scale to 70 instances during peak). RBAC authorization: O(log N) index lookup on (user_id, resource_id) — sub-5ms even at 10M permission rows. |
| **Observability & Traceability** | ✅ | Immutable access_audit_log captures every login (user_id, timestamp, ip_address, user_agent, mfa_used, success/fail) — for DocuSign's 7+ year legal retention requirement, this is the tamper-proof record of "who logged in before the contested envelope was signed." Alert: > 5 consecutive failed logins for one user_id → brute-force attempt → temporarily lock + notify security. MFA adoption rate metric (alert if < 70% enterprise users enrolled). |

---

## Section 15 — 🧾 TL;DR Answer Summary

> "Auth is a two-part system: (1) **Identity** (login): user proves they are who they claim via email/password + optional MFA. Auth Service issues JWT (access token: 15 min, refresh token: 7 days). JWT is signed with RSA private key; signature proves it came from Auth Service. (2) **Authorization** (per-request): API Gateway validates JWT signature (O(1), ~1-2ms) + checks blacklist for revocation (Redis, O(1)). Document Service then checks: is user admin? owner? or has explicit role permission (signer/viewer/approver)? All accesses logged to immutable audit trail (legal compliance). At 35K validations/sec, stateless JWT scales perfectly (no session store bottleneck). Trade-off: revocation has eventual consistency (user logs out, but old token might still validate for ~1-2 seconds until blacklist propagates)."

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 24, 2026 | **E2-authentication-system.md created.** Final solution file. Full 15-section solution framework for Type B Product Architecture. Covers: JWT tokens with RSA signature verification, token blacklist (revocation), MFA (email OTP + TOTP), fine-grained RBAC+ACL authorization, audit trails (immutable), key rotation. Scale: 10M users, 100M logins/day = 3.5K logins/sec peak, 35K token validations/sec. Prerequisites: `13-security-pki.md`, `11-api-design.md`. |
| Jul 5, 2026 | **Section 6 restructured: single final-state diagram → 3-stage progressive HLD.** Stage 1 (Session-Based Auth): stateful session cookie, Redis session lookup on every request — BREAKING POINTs: 35K session lookups/sec saturates Redis; single-factor login too risky for DocuSign. Stage 2 (Stateless JWT, no blacklist): RS256-signed JWT; validation is CPU-only (1-2ms); no session store — BREAKING POINTs: no revocation (stolen token valid for 15 min); no MFA (compromised password = full access). Stage 3 (JWT + Redis Blacklist + MFA + RBAC — production): Redis blacklist (jti → revoked, TTL = token's remaining lifetime); email OTP + optional TOTP MFA; RBAC roles in JWT + per-document resource_permissions table (fine-grained ACL); immutable access_audit_log. Four inline decision tables: (1) token strategy — session ❌ / JWT ✅ / hybrid ⚠️; (2) revocation — none ❌ / very short TTL ⚠️ / Redis blacklist ✅; (3) authorization model — simple RBAC ❌ / ABAC ❌ / RBAC+ACL ✅; (4) MFA method — none ❌ / SMS OTP ❌ / Email OTP+TOTP ✅. All Section 6 verdicts verified against Section 7 deep dive choices — no contradictions. |
| Jul 4, 2026 | **4 new Q&As added to Section 12.** (1) **PKCE for mobile OAuth** — code_verifier generated by app, code_challenge = BASE64URL(SHA256(verifier)) sent during authorization, original verifier sent at token exchange; auth server hashes verifier and checks against stored challenge; prevents intercepted auth codes from being exchanged by attackers who don't know the verifier; RFC 7636 recommended approach for all public clients; (2) **Refresh token rotation for theft detection** — every RT use issues new RT + invalidates old; stored as token family chain with `current_rt_hash`; RT reuse (already-used RT presented) → revoke entire family; detects theft on first use but requires retry-with-idempotency for network failures; (3) **SAML 2.0 SSO trust and forgery prevention** — trust bootstrapped via customer's IdP X.509 certificate; at assertion time: XML signature verification, NotOnOrAfter expiry check, InResponseTo cross-site injection prevention; forged assertions fail signature; replayed assertions fail expiry; cross-site assertions fail InResponseTo. |
| Jul 6, 2026 | **🔑 Technology Quick Reference table added.** 17-row glossary covering JWT, RS256, access/refresh token, token blacklist, jti, OAuth 2.0, OIDC, PKCE, RBAC, ACL, MFA, TOTP, OTP, SSO, SAML 2.0, bcrypt — inserted before Section 0. |
| Jul 5, 2026 | **Section 10 business impact + Section 14 DocuSign dimensions pass.** Section 10: added **Business impact:** to all 3 trade-offs — signing ceremony latency degradation at the "Adopt & Sign" moment (auth service dependency at highest legal significance), 30% non-MFA-adoption leaving users vulnerable to SIM-swapping with fraudulent contracts carrying full legal weight (enforcement cost), public computer 60-minute JWT validity post-logout exposing in-progress contracts (token lifetime). Section 14: rewrote all 7 dimension cells — FIDO2 and notary role RBAC extensibility (Extensibility), stolen JWT 15-minute blacklist closure window (Security), 70% enterprise MFA adoption rate alert + 7-year legal retention for `access_audit_log` (Observability). |
