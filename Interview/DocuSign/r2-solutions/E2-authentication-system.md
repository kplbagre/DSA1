# E2 — Design an Authentication & Authorization System

> **Read `solution-notes-standards.md` FIRST.** This file is a 60-minute interview answer framework.
> Its structure mirrors DELIVERY-RECIPE: clarify (5 min) → estimate (5 min) → HLD (15 min) → deep dives (15 min) → trade-offs (8 min) → Q&A (7 min).
> **Say this out loud** before your interview.

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

### 🎨 ASCII Architecture Diagram

```
  AUTHENTICATION & AUTHORIZATION SYSTEM — HIGH-LEVEL ARCHITECTURE
  ────────────────────────────────────────────────────────────────

  CLIENT (Web, Mobile, API)
         │
         ├─→ POST /auth/login (email, password) [with optional MFA]
         │
         ▼
  ┌──────────────────────────────────────────────┐
  │         Auth Service (Stateless)             │
  │  (can run 10 instances, load balanced)      │
  │                                              │
  │  1. Hash password, compare with DB          │
  │  2. Check if MFA required, if yes send code │
  │  3. Generate JWT (access + refresh tokens)  │
  │  4. Add to token blacklist if needed        │
  └─────────────┬────────────────────────────────┘
                │
    ┌───────────┼───────────────────────┐
    │           │                       │
    ▼           ▼                       ▼
┌─────────┐ ┌──────────┐         ┌──────────────┐
│Postgres │ │  Redis   │         │ Email/SMS    │
│(users)  │ │ (session │         │ Provider     │
│(roles)  │ │  + token │         │ (MFA codes)  │
│(audit)  │ │ blacklist)         │              │
└─────────┘ └──────────┘         └──────────────┘


  AUTHORIZATION PATH (validate request)
  ══════════════════════════════════════════════════════════════

  Client (has JWT token from login)
    │
    ├─→ GET /v1/documents/doc-123
    ├─→ Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
    │
    ▼
  ┌──────────────────────────────────────┐
  │      API Gateway / Middleware        │
  │  (validate JWT on every request)    │
  │                                      │
  │  1. Extract JWT from header         │
  │  2. Verify signature with public key│
  │  3. Check expiry (exp claim)         │
  │  4. Check token blacklist (Redis)  │
  │  5. Extract user_id + roles         │
  └─────────────┬──────────────────────┘
                │
                ├─→ If valid: continue to service
                └─→ If invalid: return 401 Unauthorized


  FINE-GRAINED AUTHORIZATION (per-document)
  ══════════════════════════════════════════════════════════════

  Document Service receives authenticated request (user_id + roles in JWT)
    │
    │ Check if user can access document:
    │ (1) If user has admin role → grant access
    │ (2) If user is document owner → grant access
    │ (3) If user has explicit permission in role_assignments → grant access
    │ (4) Otherwise → deny (403 Forbidden)
    │
    ▼
  ┌────────────────────────────────────────────┐
  │    Role Assignments Table (Postgres)      │
  │  (user_id, resource_id, role)             │
  │                                            │
  │  alice, doc-123, SIGNER                   │
  │  bob, doc-123, VIEWER                     │
  │  charlie, doc-456, APPROVER               │
  └────────────────────────────────────────────┘


FLOW: Login Request
═════════════════════════════════════════════════════════════════

Client                  Auth Service          Postgres            Redis
──────                  ────────────          ────────            ─────
POST /login
{email, password} ──→   Check password ──→   Query users
                        table
                        ◀─ {password_hash,
                           roles: [EDITOR],
                           mfa_enabled: true}

                        Generate JWT ──→     [store session]
                        (exp: 15 min)
                        ◀─── {access_token,
                              refresh_token,
                              expires_in: 900}

◀─── {access_token, refresh_token}

[Client stores JWT in localStorage/secure cookie]


FLOW: API Request (Authorization)
═════════════════════════════════════════════════════════════════

Client
GET /v1/documents/doc-123
Authorization: Bearer {access_token}
       │
       ▼
API Gateway / Middleware
[1] Verify JWT signature using public key
    - Hash header.payload with private key signature
    - Compare: should match JWT's signature
    - If no match → token forged → 401

[2] Check expiry (exp claim)
    - If exp < now() → 401

[3] Check token blacklist (Redis)
    - Key: "blacklist:{token_jti}" (jti = unique token ID)
    - If exists → token was revoked → 401

[4] Extract claims
    - user_id = JWT sub claim
    - roles = JWT roles claim
    - tenant_id = JWT tenant claim

[5] Pass to Document Service with (user_id, roles) in context


Document Service
Check authorization:
    IF role = "ADMIN"
        Grant access ✓
    ELSE IF user_id = document.owner_id
        Grant access ✓
    ELSE
        Query: SELECT role FROM role_assignments
               WHERE user_id = ? AND resource_id = ?
        IF role IN [SIGNER, EDITOR, VIEWER]
            Grant access ✓
        ELSE
            Return 403 Forbidden


KEY INVARIANT:
   JWT contains claims (user_id, roles, tenant_id, exp).
   Signature proves JWT was issued by auth service (can't be forged).
   Token validation is O(1) (verify signature + check blacklist in Redis).
   Authorization checks are O(1) for simple RBAC, O(log N) for fine-grained (DB lookup).
```

**Data flow walkthrough (say this out loud):**

**Flow 1 — Login:**
1. Client calls `POST /auth/login { email: alice@docusign.com, password: "secret123" }`
2. Auth Service hashes password with bcrypt, compares with DB (slow: ~200ms)
3. If mismatch → return 401 Unauthorized
4. If match → check if MFA enabled
5. If MFA enabled → generate MFA code, send via email, return 202 (waiting for MFA)
6. Client calls `POST /auth/verify-mfa { email, mfa_code }`
7. If mfa_code matches → generate JWT:
   ```json
   {
     "alg": "RS256",
     "typ": "JWT"
   }
   .
   {
     "sub": "user-alice-uuid",
     "email": "alice@docusign.com",
     "tenant_id": "tenant-123",
     "roles": ["EDITOR"],
     "iat": 1719241200,
     "exp": 1719242100,  // 15 minutes
     "jti": "token-id-xyz"  // unique token ID for revocation
   }
   .
   [signature = RSA-SHA256(header.payload, private_key)]
   ```
8. Return `{ access_token: "eyJ...", refresh_token: "eyJ...", expires_in: 900 }`
9. Log to audit table: `{user_id, email, login_timestamp, ip, user_agent, success: true}`

**Flow 2 — API Request (Authorization):**
1. Client calls `GET /v1/documents/doc-123` with `Authorization: Bearer {access_token}`
2. API Gateway middleware extracts JWT:
   - Splits on "." → header, payload, signature
   - Verifies signature: `RSA_SHA256_VERIFY(header.payload, signature, public_key)`
   - Checks expiry: `exp > now()`
   - Checks blacklist: `redis.exists("blacklist:{jti}")` (if user logged out)
3. If any check fails → return 401
4. If valid → extract `sub`, `roles`, `tenant_id` from JWT
5. Document Service receives request + (user_id=alice, roles=[EDITOR], tenant_id=123) in context
6. Check authorization:
   ```sql
   SELECT owner_id, status FROM documents WHERE id = 'doc-123'
   ```
7. If `owner_id == alice` → grant access
8. Else: `SELECT role FROM role_assignments WHERE user_id = alice AND resource_id = 'doc-123'`
9. If role IN [SIGNER, EDITOR, VIEWER] → grant access
10. Else → return 403 Forbidden

**Flow 3 — Logout (Token Revocation):**
1. Client calls `POST /auth/logout { refresh_token: "..." }`
2. Auth Service adds JWT to blacklist: `redis.set("blacklist:{jti}", true, ttl=exp-now())`
3. After TTL expires, blacklist entry is automatically deleted (no need for cleanup job)
4. Future requests with revoked token will fail the blacklist check

**Flow 4 — Refresh Token (Extend Session):**
1. Client's access token is about to expire
2. Client calls `POST /auth/refresh { refresh_token: "eyJ..." }`
3. Auth Service validates refresh token (same JWT validation)
4. Issues new access token (15-min exp) + new refresh token (7-day exp)
5. Old access token becomes invalid (user must use new one)

**Why each component:**
- **Auth Service**: Handles login, token generation, MFA verification; stateless (can scale horizontally)
- **JWT tokens**: Stateless validation (no server lookup); fast (~1-2ms signature verification)
- **Redis (token blacklist)**: Fast revocation check on every request; O(1) lookup
- **Postgres (roles/permissions)**: Source of truth for user data; indexed for fast queries
- **Email/SMS provider**: Send MFA codes; decoupled from auth service (retryable)
- **API Gateway middleware**: Intercepts every request; validates JWT before reaching service layer

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
            Boolean isBlacklisted = redis.opsForValue().get("blacklist:" + jti, Boolean.class);
            if (isBlacklisted != null && isBlacklisted) {
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

## Section 8 — 🌐 API Design

### Core Endpoints

| Method | Path | Auth | Request | Response | Status |
|---|---|---|---|---|---|
| POST | `/auth/login` | — | `{email, password}` | `{mfa_required?, request_id, access_token?, refresh_token?}` | 200, 401, 429 |
| POST | `/auth/verify-mfa` | — | `{request_id, mfa_code}` | `{access_token, refresh_token, expires_in}` | 200, 401, 423 |
| POST | `/auth/refresh` | — | `{refresh_token}` | `{access_token, expires_in}` | 200, 401 |
| POST | `/auth/logout` | JWT Bearer | `{refresh_token}` | `{success: true}` | 200 |
| GET | `/auth/user` | JWT Bearer | — | `{user_id, email, roles, permissions}` | 200, 401 |
| GET | `/auth/keys` | — | — | `{keys: [{kid, public_key}]}` | 200 |

### Key Design Decisions

- **MFA optional but configurable**: Users can enable (enterprise) or skip (free tier)
- **Refresh token rotation**: Each refresh issues a new refresh token (reduces risk of token theft)
- **Key rotation**: Auth Service publishes public keys at `/auth/keys`; kid (key ID) in JWT header
- **Rate limiting**: 10 failed login attempts = lock account for 15 min; prevent brute force
- **Token lifetime**: Access token (15 min, short-lived), Refresh token (7 days, long-lived)

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

**Failure mode if wrong:** If you use stateful sessions (Redis) at 35K validations/sec, Redis becomes bottleneck (typical capacity: 50K ops/sec; you're at 70% of max). Adds 10-50ms latency per request.

---

### Trade-off 2: Email MFA vs Authenticator App

**Chose:** Email OTP (default), Authenticator app (optional).

**Gain:** Email is accessible to all users; no app installation needed. Authenticator is stronger (offline, phishing-resistant).

**Lose:** Email can be delayed (SLA: 5 minutes); users without authenticator app are vulnerable if email is compromised.

**Failure mode if wrong:** If only authenticator app, 30% of users (non-technical, older age group) won't set it up; adoption drops. If only email, account takeover risk higher (email can be intercepted/delayed).

---

### Trade-off 3: Token Blacklist (Redis) vs Signed Tokens Only

**Chose:** JWT signature + Redis blacklist.

**Gain:** Revocation is immediate (logout is instant); tokens can be forcefully invalidated.

**Lose:** Redis adds 1-2ms latency to every validation; requires Redis uptime for logout to work.

**Failure mode if wrong:** If no blacklist, user logs out but token is still valid (until expiry). User thinks they're logged out but aren't (security hole). If Redis is down, logout fails (availability issue).

---

## Section 11 — 🔐 DocuSign-Specific Depth (Minutes 52–57)

**Why this question is fundamental for SDE-3:**

Auth is the first line of defense. A single bypass could expose 10M users' private contracts. DocuSign requires: (1) strong auth (MFA for high-value accounts), (2) fine-grained permissions (5 signers, different roles), (3) audit trails (legal compliance), (4) per-document access control (Signer can sign doc X but only view doc Y).

**DocuSign-specific angles:**

1. **Legal compliance**: Audit log must prove "who accessed what when" for 7+ years (for litigation discovery)
2. **Multi-party signing**: Each signer has a role (signer, approver, reviewer) per document
3. **Key rotation**: Auth Service rotates signing keys quarterly; old keys remain for token validation (backward compatibility)
4. **Revocation during signing**: If a signer is removed mid-process, their permissions are immediately revoked (token blacklist)

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

## Section 13 — 🐞 Common Mistakes on This Question

- **Mistake 1:** "Every request validates token by querying the session table in Postgres." → **Why it's wrong:** At 35K validations/sec, DB becomes bottleneck (typical capacity: 1-2K queries/sec). → **What to say instead:** "Stateless JWT: signature verification is CPU-bound (~1-2ms), not I/O. No DB lookup needed per request. Revocation via Redis blacklist (O(1) lookup)."

- **Mistake 2:** "Store user passwords in plaintext or with simple hash (MD5)." → **Why it's wrong:** Password breach = 10M users compromised. Attackers can rainbow-table (a rainbow table is a giant precomputed lookup of hash→password pairs; if you know the hash of "password123" from MD5 is `482c811da5d5b4bc6d497ffa98491e38`, you build a table of billions of such mappings offline; then a stolen DB of MD5 hashes is cracked in seconds by lookup; bcrypt's per-user salt makes each hash unique so no precomputed table can apply) simple hashes. → **What to say instead:** "Hash with bcrypt (salted, slow by design ~200ms per hash). Never store plaintext. Even if DB is breached, passwords are useless."

- **Mistake 3:** "Access token lifetime is 7 days (same as refresh token)." → **Why it's wrong:** If access token is compromised, attacker has 7 days of access. Long-lived tokens are high-risk. → **What to say instead:** "Access token: 15 minutes (if stolen, window of vulnerability is small). Refresh token: 7 days (user doesn't re-login often). Refresh token is more closely guarded (not sent on every request)."

---

## Section 14 — 🧭 DocuSign Dimensions Checklist

| Dimension | Relevant? | How your design addresses it |
|---|---|---|
| **Testability** | ✅ | JWT validation is deterministic (same signature = always valid). Mock signing key for unit tests. No randomness in signature verification. |
| **Usability** | ✅ | Simple login API: `POST /login { email, password }`. MFA is optional (doesn't slow down non-MFA users). Transparent token refresh (client handles automatically). |
| **Extensibility** | ✅ | New roles are added to role table; existing code doesn't change. New permission types (read, write, sign, approve) are extensible. TOTP and new MFA methods can be added. |
| **Security** | ✅ | RSA signature prevents token forgery. Bcrypt passwords are slow to hash (brute-force resistant). JWT exp claim prevents replay after expiry. Revocation via blacklist. MFA prevents account takeover. |
| **Availability** | ✅ | Token validation is local (no external service call). Auth Service can run on 10 instances (stateless). Session store (Redis) is replicated (HA). If Redis down, fail closed (deny access safely). |
| **Scalability** | ✅ | Stateless JWT handles 35K validations/sec. Token generation (bcrypt ~200ms) needs 3-4 parallel instances (3.5K logins/sec ÷ 5 logins/instance/sec). Authorization queries are O(log N) with indexes. |
| **Observability & Traceability** | ✅ | Audit log captures every login + access attempt. Metrics: login latency, MFA success rate, token validation rate. Failed login patterns trigger alerts. |

---

## Section 15 — 🧾 TL;DR Answer Summary

> "Auth is a two-part system: (1) **Identity** (login): user proves they are who they claim via email/password + optional MFA. Auth Service issues JWT (access token: 15 min, refresh token: 7 days). JWT is signed with RSA private key; signature proves it came from Auth Service. (2) **Authorization** (per-request): API Gateway validates JWT signature (O(1), ~1-2ms) + checks blacklist for revocation (Redis, O(1)). Document Service then checks: is user admin? owner? or has explicit role permission (signer/viewer/approver)? All accesses logged to immutable audit trail (legal compliance). At 35K validations/sec, stateless JWT scales perfectly (no session store bottleneck). Trade-off: revocation has eventual consistency (user logs out, but old token might still validate for ~1-2 seconds until blacklist propagates)."

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 24, 2026 | **E2-authentication-system.md created.** Final solution file. Full 15-section solution framework for Type B Product Architecture. Covers: JWT tokens with RSA signature verification, token blacklist (revocation), MFA (email OTP + TOTP), fine-grained RBAC+ACL authorization, audit trails (immutable), key rotation. Scale: 10M users, 100M logins/day = 3.5K logins/sec peak, 35K token validations/sec. Prerequisites: `13-security-pki.md`, `11-api-design.md`. |
