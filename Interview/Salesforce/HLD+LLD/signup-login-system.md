# Sign-Up / Login System at Scale — HLD + LLD Combined Round

---

## 0.  Identity

| | |
|---|---|
| **Problem** | Sign-up and login for a billion users — registration, authentication, session/token management, unique usernames |
| **Format** | HLD+LLD combined (Salesforce SMTS), 90 min confirmed |
| **Time budget** | 35 min LLD -> 45 min HLD -> 10 min buffer |
| **Frequency rank** | **#7 pick** in `questions-by-frequency.md`, but it carries the **most explicit combined-format quote in the entire research set** (CodingKaro, Jul 2025, MTS I Bangalore): *"Design a high-level and low-level design for a sign-up and login page system that can handle a billion users with unique usernames."* HLD and LLD named in the same sentence by the candidate. Corroborated by a Dec 2025 HM-round report: *"System Design: Asked to design a login page. I focused on gathering requirements, security considerations, and protection mechanisms."* |
| **Salesforce-specific angle** | Direct product hit — Salesforce Identity, SSO/SAML, Connected Apps, OAuth flows, and MFA are core platform features. Per-org identity policies (session timeout, password complexity, IP ranges) are configuration-as-data across 150K tenants. |

**The critical framing note:** the Dec 2025 report says the interviewer wanted *"security considerations and protection mechanisms."* This problem is graded on **security judgment first, scale second**. A beautiful architecture that stores passwords with SHA-256 fails. Lead with the security posture.

---

## 1.  Dual-Layer Map

| HLD Box (system view) | LLD Class(es) (code view) | The interface that makes it swappable |
|---|---|---|
| Sign-up API | `RegistrationService`, `Credential` | `UserRepository` |
| Username uniqueness | `UsernameReservationService` | **`UniquenessStrategy`** — DB constraint vs reservation table |
| Password hashing | `PasswordHasher` | **`PasswordHasher`** — bcrypt/argon2, must be swappable for rotation |
| Credential validation | `PasswordPolicy`, `PolicyChain` | **`PasswordRule`** — composable |
| Login / authentication | `AuthenticationService` | **`AuthProvider`** — password, SSO/SAML, OAuth, MFA |
| Token issuance | `TokenService`, `AccessToken`, `RefreshToken` | **`TokenStrategy`** — JWT vs opaque |
| Session store / revocation | `SessionStore` | **`SessionStore`** — Redis, DB |
| Brute-force protection | `LoginAttemptTracker` | (-> the Rate Limiter problem) |
| MFA | `MfaChallenge`, `MfaVerifier` | **`MfaMethod`** — TOTP, SMS, push |

**The zoom sentence:** *"`PasswordHasher` is one class calling bcrypt in LLD. In HLD it's the reason auth servers need dedicated CPU and can't be co-located with the API tier — bcrypt at the right cost factor deliberately burns ~100ms of CPU per login, so at 100K logins/sec that's a fleet sized entirely by hashing cost."*

---

## 2.  LLD Half (target: 35 min)

### 2.1  Problem Statement

Design the registration and authentication system: users sign up with a globally unique username and a password, log in to receive a session/token, and the system protects against credential attacks — at a scale where usernames must be unique across a billion accounts.

### 2.2  Requirements

**Functional:**
- Register with unique username + email + password
- Log in with username/email + password, receive a token
- Log out (single session and sions)
- Password reset via emailed one-time token
- MFA (TOTP at minimum) as an optional second factor

**Non-Functional — security first, and say them in this order:**
- **Passwords never stored recoverably** — slow adaptive hash (bcrypt/argon2) with per-user salt
- **Username uniqueness is a hard invariant** — no two accounts with the same username, ever
- **Brute-force resistant** — per-account and per-IP throttling with lockout
- **No user enumeration** — the system must not reveal whether an account exists
- Thread-safe; login is read-heavy, sign-up is write-heavy but far lower volume

**Out of scope (say it):** full OAuth2 authorization-server semantics and social-login federation, unless they pivot there.

### 2.3  Class Design

#### 2.3.1  Deriving the classes (say this out loud, minutes 2-6)

| # | Requirement | Noun / variation point | Becomes | Why it earns its own type (and what breaks if you inline it) |
|---|---|---|---|---|
| 1 | "register with username + email" | noun: *the person's account* | **`User`** (entity) | Identity, profile, status. Note what it does **not** contain — see row 2. |
| 2 | "+ a password" | noun: *the secret* | **`Credential`** (separate entity) | **The most important split in this design.** Password hash, algorithm, salt, and rotation metadata live in a *separate* table/class from `User`. Reason: profile reads happen constantly (every page render) while credentials should be read only during authentication. Putting the hash on `User` means every innocuous profile query drags the password hash through logs, caches, and API serializers — the #1 way hashes leak. Separate lifetime, separate blast radius, separate class. |
| 3 | "**unique** usernames" (at a billion) | verb: *claim a name atomically* | **`UsernameReservation`** + **`UniquenessStrategy`** | Uniqueness at this scale is its own problem, not a `UNIQUE` column afterthought — see 2.5. It needs a named component because the answer differs between a single DB and a sharded one. |
| 4 | "password" + security NFR | the *hashing algorithm* varies over time | **`PasswordHasher`** (interface) | **Must be swappable — this is not speculative.** Algorithms age (MD5 -> SHA -> bcrypt -> argon2) and cost factors must increase as hardware improves. The stored hash records which algorithm made it so old and new coexist during migration. Hardcoding bcrypt means a future rotation is a rewrite plus a forced password reset for a billion users. |
| 5 | "password" + complexity rules | the *validation rules* compose | **`PasswordRule`** (interface) + `MinLengthRule`, `BreachedPasswordRule`, `NotSimilarToUsernameRule` | Rules combine and differ per tenant. A single `validate()` with nested ifs can't express per-org policy, and each rule is independently testable as a small predicate. |
| 6 | "log in and receive a token" | noun: *proof of authentication* | **`AccessToken`** + **`RefreshToken`** | Two distinct things with different lifetimes and revocation semantics — conflating them is the classic mistake. Short-lived access, long-lived revocable refresh. |
| 7 | same — "log in" | the *authentication mechanism* varies | **`AuthProvider`** (interface) | Password today; SAML/SSO, OAuth, and passkeys tomorrow — and on Salesforce, SSO is table stakes. Inlining password checks means adding SSO edits the login core. |
| 8 | "log out **all** sessions" | noun: *the live session* | **`Session`** + **`SessionStore`** | Server-side revocation requires server-side state. This class is precisely the answer to "how do you log out a JWT?" — see 2.5. |
| 9 | "brute-force resistant" | verb: *count and throttle attempts* | **`LoginAttemptTracker`** | Needs its own home because it's checked *before* password verification and updated after. Also the seam to the Rate Limiter problem — say the connection. |
| 10 | "MFA" | the *second factor* varies | **`MfaMethod`** (interface) | TOTP, SMS, push are genuinely different flows sharing one contract. |
| 11 | account lifecycle | *state* of an account | **`UserStatus`** (enum) | `PENDING_VERIFICATION / ACTIVE / LOCKED / SUSPENDED / DELETED`. Behavior-free gating -> enum, not State pattern. |

**One-liner after the table:** *"The split that matters most is `User` versus `Credential` — profile data is read constantly, secrets should be read only at authentication, and keeping them in one class is how password hashes end up in logs and cache dumps."*

#### 2.3.2  Entity fields

```
User                                 <- read constantly; contains NO secrets
  - userId:      UUID
  - username:    String              <- unique, immutable after creation
  - email:       String              <- unique, changeable (re-verify on change)
  - displayName: String
  - status:      UserStatus
  - createdAt:   Instant

Credential                           <- read ONLY during authentication
  - userId:        UUID              <- 1:1 with User
  - passwordHash:  String            <- bcrypt/argon2 output (salt embedded)
  - algorithm:     HashAlgorithm     <- enables rotation without forced resets
  - updatedAt:     Instant
  - mustChange:    boolean
  - previousHashes: List<String>     <- prevents password reuse (last N)

Session
  - sessionId:    String             <- opaque, high-entropy
  - userId:       UUID
  - refreshToken: String (hashed!)   <- store the HASH, not the token itself
  - deviceInfo:   String
  - ipAddress:    String
  - createdAt:    Instant
  - expiresAt:    Instant
  - revokedAt:    Instant            <- null = live

LoginAttempt
  - key:          String             <- "user:{id}" or "ip:{addr}"
  - failedCount:  int
  - firstFailAt:  Instant
  - lockedUntil:  Instant

UserStatus (enum): PENDING_VERIFICATION, ACTIVE, LOCKED, SUSPENDED, DELETED
HashAlgorithm (enum): BCRYPT_12, ARGON2ID, LEGACY_PBKDF2
```

**Say this about `refreshToken` being hashed:** *"Refresh tokens are stored hashed, same as passwords. If the session table leaks and tokens are in plaintext, the attacker has live sessions for every user — a database read becomes total account takeover without ever cracking a password."*

#### 2.3.3  Relationships — with the composition-vs-aggregation call made explicit

| Relationship | Type | Composition or aggregation — and why that one |
|---|---|---|
| `User` — `Credential` | **1:1, referenced by `userId`, NOT composed** | The deliberate choice. Conceptually the credential belongs to the user (composition-ish), but modelling it as a separate aggregate with an ID reference is what keeps the hash out of every `User` load. **The lifecycle argument for composition is real; the security argument for separation wins**, and saying that trade-off explicitly is the point. |
| `Credential` — `HashAlgorithm` | **HAS-A** -> **composition** | An immutable value field created with the credential. |
| `User` — `Session` | **HAS-MANY** -> **aggregation** (1:N) | Sessions have independent lifecycle — they expire and are revoked without touching the user, and they outlive individual logins. Definitely not composition: deleting a session must not imply anything about the user. |
| `AuthenticationService` — `AuthProvider` | **HAS-A** -> **aggregation** | Injected, stateless, shared. Resolved by provider type at login. |
| `AuthenticationService` — `PasswordHasher` | **HAS-A** -> **aggregation** | Injected shared singleton. Must be swappable at runtime for algorithm rotation, which forbids constructing it internally. |
| `PasswordPolicy` — `List<PasswordRule>` | **HAS-A** -> **aggregation** | Rules are shared, stateless singletons composed into per-tenant policies; the same `MinLengthRule` instance serves every org. |
| `Session` — `AccessToken` | **no reference** (deliberate) | Access tokens are stateless JWTs that aren't stored at all. Only the refresh token is bound to the session. Modelling access tokens as entities would defeat their entire purpose. |
| `LoginAttemptTracker` — `CounterStore` | **USES** (injected) | Same seam as the Rate Limiter problem — in-memory locally, Redis in production. |

#### 2.3.4  ASCII class diagram — interfaces before implementations, always

```
                       RegistrationService                AuthenticationService
                       - userRepo: UserRepository         - authProviders: Map<Type, AuthProvider>
                       - reservation: UsernameReservation - hasher:  PasswordHasher
                       - policy: PasswordPolicy           - tokens:  TokenService
                       - hasher: PasswordHasher           - attempts: LoginAttemptTracker
                       + register(req): User              + login(req): AuthResult
                                |                                   |
        +-----------------------+                +------------------+------------------+
        v                                        v                  v                  v
  <<interface>>                          <<interface>>       <<interface>>      <<interface>>
  UniquenessStrategy                     AuthProvider        PasswordHasher     TokenStrategy
  + tryClaim(username,                   + authenticate(     + hash(raw):       + issue(user):
      userId): boolean                       creds): User        String             TokenPair
  + release(username)                    + getType()         + verify(raw,      + validate(token)
        ^                                      ^                  hash): bool         ^
        | implements                           | implements       ^                   | implements
  +-----+------------+           +-------------+------+          | implements   +-----+------+
  |                  |           |             |      |     +----+-----+       |            |
DbConstraint    ShardedReservation Password   Saml   OAuth  Bcrypt  Argon2  JwtStrategy  OpaqueToken
Strategy        Strategy          Provider   Provider Provider Hasher Hasher              Strategy

                       <<interface>>                    <<interface>>
                       PasswordRule                     MfaMethod
                       + check(pw, ctx): Result         + challenge(user)
                             ^                          + verify(user, code): bool
             +---------------+--------------+                 ^
             |               |              |          +------+------+
       MinLength      BreachedPassword  NotSimilarTo   TOTP   SMS   Push
       Rule           Rule              UsernameRule   Method Method Method

                       User  ----1:1 by userId----> Credential   (separate aggregate:
                        |                            - passwordHash    secrets isolated
                        |                            - algorithm       from profile reads)
                        +----1:N aggregation-------> Session
                                                     - refreshToken (HASHED)
```

#### 2.3.5  Follow-ups they will ask after this section — and your answers

| Their question | Your answer (one breath) |
|---|---|
| "Why is `Credential` separate from `User`?" | "Blast radius. Profile data is read on every request; the password hash should only ever be read during authentication. Keeping them together means the hash rides along in every profile query, cache entry, log line, and API serialization — that's how hashes leak. Separate aggregate, separate access path." |
| "Why is `PasswordHasher` an interface if you're using bcrypt?" | "Because algorithms age and cost factors must rise with hardware. The stored `algorithm` field lets old and new hashes coexist: on successful login I verify with the recorded algorithm and transparently re-hash with the current one. Hardcoding it means the eventual migration forces a password reset on a billion users." |
| "Composition or aggregation for `User` and `Session`?" | "Aggregation — sessions have independent lifecycles, expire on their own, and are revoked without touching the user. Composition would imply deleting a session means something about the user, which is wrong." |
| "Why hash the refresh token?" | "Same reason as the password. A leaked session table with plaintext tokens is instant account takeover for every active user — no cracking required. Hashed, the table is useless to a reader." |
| "How do you log out a JWT?" | "You can't invalidate a stateless JWT, which is exactly why access tokens are short-lived (~15 min) and the revocable state lives in the refresh token and `Session` row. Logout revokes the session so no new access tokens are issued; the worst case is one 15-minute window. Calling that out is more honest than claiming JWTs are revocable." |
| "Isn't `PasswordRule` over-engineering?" | "It earns its place through per-tenant policy — Salesforce orgs configure their own complexity requirements, so the rule set is data, not code. It also keeps 'check against a breached-password list' as an independently testable unit. For a single-tenant app with fixed rules I'd collapse it." |
| "Why not just a `UNIQUE` constraint on username?" | "That's exactly right for one database, and I'd start there. It stops working when users are sharded by `user_id` — the constraint is per-shard, so two shards can both accept the same username. That's why uniqueness is its own strategy; see my design decisions." |

### 2.4  Key Interfaces

```java
/**
 * Must be swappable — algorithms age and cost factors rise.
 * verify() takes the stored algorithm so old and new hashes coexist.
 */
public interface PasswordHasher {
    String hash(char[] rawPassword);
    boolean verify(char[] rawPassword, String storedHash);
    HashAlgorithm getAlgorithm();
    /** True when a valid hash was made by an outdated algorithm/cost -> re-hash on login. */
    boolean needsRehash(String storedHash);
}
```

**Note `char[]` not `String`:** *"Strings are immutable and sit in the pool until GC, so a password in a `String` lingers in memory and can surface in a heap dump. `char[]` can be zeroed immediately after use. It's a small thing that signals you've handled credentials before."*

```java
/** Uniqueness as a strategy — the answer differs single-DB vs sharded. */
public interface UniquenessStrategy {
    boolean tryClaim(String username, UUID userId);
    void release(String username);
}
```

```java
/** Password, SAML/SSO, OAuth, passkey — one contract. */
public interface AuthProvider {
    AuthenticatedPrincipal authenticate(AuthRequest request) throws AuthException;
    AuthProviderType getType();
}
```

```java
/** Composable, independently testable, per-tenant configurable. */
public interface PasswordRule {
    RuleResult check(char[] password, PasswordContext context);
}
```

```java
public interface SessionStore {
    void save(Session session);
    Optional<Session> findByRefreshTokenHash(String hash);
    void revoke(String sessionId);
    void revokeAllForUser(UUID userId);   // "log out everywhere"
}
```

### 2.5  Design Decisions

**Question 1 you must be ready for: "How do you guarantee unique usernames across a billion users?"** This is the explicit ask in the sourced prompt.

| Option | How it works | Pros | Cons | Verdict |
|---|---|---|---|---|
| `UNIQUE` constraint on one table | DB enforces it | Perfectly correct; zero extra machinery | Requires all usernames in one unsharded table; a billion rows is large but a username-only table is ~50 GB — actually viable | **Chosen for the general case** |
| Check-then-insert in application | `SELECT` then `INSERT` | Simple to write | **Check-then-act race** — two concurrent signups both see "free" and both insert | Rejected — the classic bug |
| Shard users by `user_id`, unique per shard | Natural user sharding | Scales user data | **Uniqueness breaks** — the constraint is per-shard, so two shards accept the same name | Rejected — silently wrong |
| **Separate `usernames` table sharded by `hash(username)`** | The name itself is the shard key, so one name maps to exactly one shard, where a local `UNIQUE` is authoritative | Correct under sharding; O(1) lookup; small rows | Two-step signup (claim name, then create user) needs cleanup for abandoned claims | **Chosen when user data must shard** |
| Distributed lock per username | Redis lock around the check | Works | Adds a dependency to signup for something a constraint does for free | Unnecessary |

**Decision:** a dedicated `usernames` table (username as PK), **sharded by `hash(username)` so the shard key is the uniqueness key**. Say the insight: *"The trick is choosing a shard key that makes the invariant local. Sharding users by `user_id` scatters usernames across shards and makes global uniqueness expensive; sharding the username table by the username itself means every claim for a given name lands on exactly one shard, where a plain `UNIQUE` constraint is sufficient."*

**Question 2: "JWT or opaque session tokens?"** Have the trade memorized:

| | Stateless JWT | Opaque token + server session |
|---|---|---|
| Validation cost | Signature check, no I/O | Store lookup per request |
| Revocation | **Impossible before expiry** | Immediate |
| Scale | Excellent — no shared state | Needs a fast shared store |
| Payload leakage | Claims readable by anyone holding it | Opaque |

**Decision: both, by role.** Short-lived JWT access token (~15 min, validated with no I/O) plus a long-lived **opaque, hashed, server-stored refresh token** (revocable). *"This is the standard split precisely because it puts each token where its weakness doesn't matter: the un-revocable token expires fast, and the long-lived one is revocable."*

**Question 3: "How do you avoid user enumeration?"** The security-judgment question:
- Login failures return **one generic message** regardless of whether the username exists.
- Sign-up must not say "username taken" in a way that lets an attacker mine the namespace... **but** usability demands telling users a name is taken. **State the honest resolution:** *"Username availability is inherently public — anyone can try to register it. The real defenses are rate-limiting the availability endpoint and never leaking whether an *email* is registered, since email is the sensitive identifier. Password reset must always respond 'if an account exists, we sent a link.'"*
- **Timing:** always perform a dummy hash when the user doesn't exist. Otherwise a 100ms bcrypt on real accounts vs 2ms on missing ones is a trivially measurable oracle.

| Decision | Pattern Chosen | Strongest Alternative Considered | Why the alternative loses |
|---|---|---|---|
| `Credential` split from `User` | **Aggregate separation** | Hash as a column on `users` | Every profile read carries the hash into logs/caches/serializers; one careless `SELECT *` in a log line leaks it |
| Adaptive hash (bcrypt/argon2) w/ per-user salt | **Slow hashing by design** | SHA-256 + salt | SHA is built to be fast — billions of guesses/sec on a GPU. Slowness is the *feature*; a fast hash makes a stolen table crackable in hours |
| Access JWT + opaque refresh | **Split token model** | JWT for everything | No revocation — a stolen long-lived JWT is valid until expiry with no kill switch |
| `usernames` table sharded by name | **Shard key = invariant key** | Users sharded by `user_id` with a unique index | Per-shard constraints can't enforce a global invariant; duplicates appear silently |
| Dummy hash on unknown user | **Constant-time-ish response** | Return early when no user found | The timing difference is a reliable account-existence oracle |
| Rules as composable objects | **Chain / Specification** | One `validate()` with nested ifs | Can't express per-tenant policy as data, and combinations become untestable |

### 2.6  Visual — Object Interaction

```
== SIGN-UP ==
RegistrationService.register(username, email, rawPassword)
      |
      +--> policy.validate(rawPassword, ctx)         [composable rules]
      |        MinLength, BreachedPassword, NotSimilarToUsername
      |        -> fail fast BEFORE any expensive work
      |
      +--> uniqueness.tryClaim(username, userId)     [atomic INSERT on shard hash(username)]
      |        |
      |        +-- false -> UsernameTakenException
      |
      +--> hash = hasher.hash(rawPassword)           [bcrypt ~100ms — deliberately slow]
      |        then Arrays.fill(rawPassword, '\0')   [zero the char[] immediately]
      |
      +--> userRepo.save(User(status = PENDING_VERIFICATION))
      +--> credentialRepo.save(Credential(userId, hash, BCRYPT_12))
      |        (both in ONE transaction; on failure, release the username claim)
      |
      +--> emailVerification.send(user)              [async, outside the transaction]
      v
   User (PENDING_VERIFICATION — cannot log in yet)

== LOGIN ==
AuthenticationService.login(identifier, rawPassword, deviceInfo)
      |
      +--> attempts.check("user:" + id) and check("ip:" + addr)
      |        |
      |        +-- locked -> AccountLockedException  [BEFORE touching the password]
      |
      +--> user = userRepo.findByUsernameOrEmail(identifier)
      |        |
      |        +-- not found -> hasher.verify(rawPassword, DUMMY_HASH)  <- burn the same
      |                         throw InvalidCredentialsException          ~100ms, then
      |                         (generic message)                          generic error
      |
      +--> credential = credentialRepo.findByUserId(user.id)   [only read here]
      +--> if !hasher.verify(rawPassword, credential.hash):
      |        attempts.recordFailure(...)
      |        throw InvalidCredentialsException     [same generic message]
      |
      +--> if hasher.needsRehash(credential.hash):
      |        credentialRepo.update(hasher.hash(rawPassword))  [transparent upgrade]
      |
      +--> if user.mfaEnabled:
      |        return AuthResult.mfaRequired(challengeToken)    [short-lived, single-use]
      |
      +--> attempts.reset(...)
      +--> session = Session(refreshTokenHash = sha256(refreshToken), expiresAt = +30d)
      +--> sessionStore.save(session)
      +--> accessToken = tokens.issueJwt(user, ttl = 15m)
      v
   AuthResult(accessToken, refreshToken, expiresIn)
```

**Two lines to narrate:**
1. *"The attempt check happens **before** password verification — otherwise the lockout mechanism itself becomes the expensive operation an attacker triggers a million times."*
2. *"The dummy hash on 'user not found' is not paranoia. Without it, response time alone tells an attacker which usernames exist, and they'll enumerate the namespace before trying a single password."*

### 2.7  Coding Skeleton

```java
// 1. Enums first
public enum HashAlgorithm { BCRYPT_12, ARGON2ID, LEGACY_PBKDF2 }
public enum UserStatus { PENDING_VERIFICATION, ACTIVE, LOCKED, SUSPENDED, DELETED }

// 2. Interface before implementation
public interface PasswordHasher {
    String hash(char[] rawPassword);
    boolean verify(char[] rawPassword, String storedHash);
    boolean needsRehash(String storedHash);
    HashAlgorithm getAlgorithm();
}

// 3. Implementation — note the cost factor is config, not a constant
public class BcryptPasswordHasher implements PasswordHasher {
    private final int costFactor;    // 12 today; raised as hardware improves

    @Override
    public String hash(char[] rawPassword) {
        return BCrypt.withDefaults()
                     .hashToString(costFactor, rawPassword);   // salt is embedded
    }

    @Override
    public boolean verify(char[] rawPassword, String storedHash) {
        return BCrypt.verifyer().verify(rawPassword, storedHash).verified;
    }

    @Override
    public boolean needsRehash(String storedHash) {
        return extractCost(storedHash) < costFactor;   // drives transparent upgrade
    }
}

// 4. Orchestrator — the method to narrate live
public class AuthenticationService {
    private static final String DUMMY_HASH =
        "$2a$12$abcdefghijklmnopqrstuv0123456789012345678901234567890";

    private final UserRepository userRepo;
    private final CredentialRepository credentialRepo;
    private final PasswordHasher hasher;
    private final LoginAttemptTracker attempts;
    private final SessionStore sessions;
    private final TokenService tokens;

    public AuthResult login(String identifier, char[] rawPassword, DeviceInfo device) {
        try {
            // 1. Throttle BEFORE any expensive work
            attempts.assertNotLocked(identifier, device.getIpAddress());

            Optional<User> maybeUser = userRepo.findByUsernameOrEmail(identifier);

            // 2. Unknown user: burn the same CPU, return the same error
            if (maybeUser.isEmpty()) {
                hasher.verify(rawPassword, DUMMY_HASH);
                attempts.recordFailure(identifier, device.getIpAddress());
                throw new InvalidCredentialsException();      // generic
            }

            User user = maybeUser.get();
            if (user.getStatus() != UserStatus.ACTIVE) {
                throw new InvalidCredentialsException();      // same generic message
            }

            Credential cred = credentialRepo.findByUserId(user.getUserId())
                .orElseThrow(InvalidCredentialsException::new);

            if (!hasher.verify(rawPassword, cred.getPasswordHash())) {
                attempts.recordFailure(identifier, device.getIpAddress());
                throw new InvalidCredentialsException();      // same generic message
            }

            // 3. Transparent algorithm upgrade on successful login
            if (hasher.needsRehash(cred.getPasswordHash())) {
                credentialRepo.updateHash(user.getUserId(),
                                          hasher.hash(rawPassword),
                                          hasher.getAlgorithm());
            }

            attempts.reset(identifier, device.getIpAddress());

            if (user.isMfaEnabled()) {
                return AuthResult.mfaRequired(tokens.issueMfaChallenge(user));
            }
            return issueTokens(user, device);

        } finally {
            Arrays.fill(rawPassword, '\0');   // zero the secret, always
        }
    }

    private AuthResult issueTokens(User user, DeviceInfo device) {
        String refreshToken = SecureRandomString.generate(48);
        Session session = Session.create(
            user.getUserId(),
            sha256(refreshToken),          // store the HASH, never the token
            device,
            Duration.ofDays(30)
        );
        sessions.save(session);
        String accessToken = tokens.issueJwt(user, Duration.ofMinutes(15));
        return AuthResult.success(accessToken, refreshToken);
    }
}
```

### 2.8  Concurrency — Making It Thread-Safe

| Race | Where | Fix | Why this fix |
|---|---|---|---|
| **Two signups claim the same username** | check-then-insert | Atomic `INSERT` into the `usernames` table; let the **PK/unique violation** be the arbiter | The DB decides, not the application. A prior `SELECT` is only a UX nicety — the insert is the truth |
| **Concurrent failed logins bypass lockout** | read-modify-write on the attempt counter | Atomic `INCR` in Redis with TTL | A read-then-write counter under 100 parallel attempts undercounts badly — the attacker gets far more tries than the limit allows |
| **Refresh-token replay / rotation race** | two clients refresh with the same token | **Refresh token rotation**: issuing a new one atomically invalidates the old (CAS on the session row); reuse of a rotated token revokes the whole session family | Detects stolen tokens — a legitimate client and a thief cannot both keep refreshing; the second use is a theft signal |
| **Password change vs active sessions** | credential update | Revoke all sessions in the same transaction as the hash update | Otherwise "change your password because you were compromised" leaves the attacker's session alive — the single most damaging gap in a naive implementation |

**Say this explicitly:** *"Password change must revoke sessions atomically with the credential update. If those are separate operations and the second fails, the user believes they've locked the attacker out and they haven't."*

### 2.9  "What Would You Do Differently?"

**I'd move toward passkeys/WebAuthn rather than optimizing password handling.** Every mechanism above — hashing cost, breach lists, lockout, enumeration defenses — exists to compensate for shared secrets being a bad primitive. Passkeys remove the shared secret entirely: nothing crackable is stored server-side, and phishing resistance is built in. **Trade-off:** recovery becomes the hard problem (lose the device, lose the account), so you still need a fallback path, and that fallback tends to be the weakest link.

**Second:** I'd treat the **breached-password check as mandatory, not optional**. Blocking known-compromised passwords at signup (k-anonymity range query against a breach corpus, so the password never leaves the boundary) prevents more account takeovers than any complexity rule — credential stuffing beats brute force in practice, and complexity rules mostly produce `Password1!`.

### 2.10  Interview Q&As (prep-only)

| Q | A |
|---|---|
| "Where's the salt stored?" | "Inside the bcrypt/argon2 output string itself, alongside the cost factor and algorithm ID — that's why the hash round-trips without a separate salt column. Per-user salts are what make precomputed rainbow tables useless." |
| "How do you handle password reset?" | "Single-use, short-TTL (15 min) high-entropy token, stored hashed, emailed as a link. Response is always 'if an account exists we sent a link.' Using it invalidates the token and revokes all sessions." |
| "Rate limit by username or IP?" | "Both, different thresholds. Per-account stops targeted brute force; per-IP stops spraying across many accounts. Per-account alone lets an attacker try one password against a million accounts — credential stuffing, which is what actually happens." |
| "How do you scale login to 100K/sec?" | "Bcrypt is intentionally ~100ms of CPU, so it's CPU-bound: 100K/sec x 0.1s = 10,000 cores of pure hashing. That's the sizing driver — dedicated auth fleet, and cost factor becomes an explicit security/capacity trade-off decided deliberately, not by default." |
| "What if the session store goes down?" | "Access tokens keep validating (stateless JWT), so existing users continue for up to 15 minutes — the split-token model degrades gracefully. New logins and refreshes fail. Fail *closed* here, unlike rate limiting: auth is exactly where you don't relax under failure." |
| "Multi-device logout?" | "`revokeAllForUser` marks every session revoked; access tokens expire within 15 minutes. If instant global revocation is required, you need a revocation check per request, which sacrifices JWT statelessness — a real trade, not a free upgrade." |

### 2.11  TL;DR — 30-Second Pitch (LLD)

The structural decision that matters most is splitting `Credential` from `User`: profile data is read on every request while the password hash should only ever be read during authentication, and keeping them together is how hashes end up in logs and caches. `PasswordHasher` is an interface, not a hardcoded bcrypt call, because the stored `algorithm` field lets old and new hashes coexist and transparently upgrade on login — otherwise a future rotation forces a password reset on a billion users. Uniqueness is its own strategy since a `UNIQUE` constraint stops working once users shard by `user_id`; the fix is a `usernames` table sharded by `hash(username)` so the shard key *is* the invariant key. Tokens are split by role — short-lived stateless JWT for access, opaque hashed revocable refresh token in a `Session` — and the security details that separate a real answer from a textbook one are the dummy hash on unknown users to kill the timing oracle, and revoking all sessions atomically with any password change.

### 2.12  Patterns Used

| Pattern | Where | Why |
|---|---|---|
| **Strategy** | `PasswordHasher`, `AuthProvider`, `UniquenessStrategy`, `MfaMethod`, `TokenStrategy` | Five genuine variation points; hasher swappability is a security requirement, not speculation |
| **Chain / Specification** | `PasswordRule` composition | Per-tenant policy as data; each rule independently testable |
| **Aggregate separation** | `User` vs `Credential` | Security blast-radius isolation, not just modelling taste |
| **Repository** | `UserRepository`, `CredentialRepository`, `SessionStore` | Distinct access paths and distinct sharding strategies per aggregate |
| **Registry** | `AuthenticationService.authProviders` | Provider lookup without a switch |
| **Token rotation** | refresh-token flow | Theft detection: reuse of a rotated token revokes the session family |

---

## 3.  HLD Half (target: 45 min)

### 3.1 Clarifying Questions (0-3 min)

| Question | Architectural Fork |
|---|---|
| "A billion registered users — what's the peak login rate?" | 1B accounts but 10M DAU -> ~1-2K logins/sec, very manageable. 100M DAU with a morning spike -> ~100K logins/sec, and **bcrypt CPU becomes the entire sizing problem**. |
| "Global or single region? Is data residency required?" | Single region -> one primary. Global with GDPR/residency -> regional partitioning where EU credentials never leave the EU, which constrains replication topology far more than performance does. |
| "Passwords only, or SSO/social/MFA too?" | Passwords only -> the design above. SSO/SAML (mandatory for Salesforce-style B2B) -> the identity provider becomes an external dependency and provisioning/JIT user creation is a whole extra flow. |
| "Session lifetime expectations — banking-strict or consumer-sticky?" | 15-minute idle timeout -> refresh traffic dominates. 30-day sticky sessions -> revocation and device management matter much more. |

### 3.2 Requirements

**Functional (5):**
- Register with globally unique username; verify email
- Authenticate and issue tokens; refresh; revoke (single + all devices)
- Password reset and forced rotation
- MFA enrollment and challenge
- SSO/SAML for enterprise tenants

**Non-Functional (4):**
- Scale: **1B accounts**, **100M DAU**, peak **~100K logins/sec**
- Latency: P99 **< 300ms** for login (bcrypt alone is ~100ms of it)
- **Security:** slow adaptive hashing, no enumeration, brute-force resistant, breach-resistant storage
- Availability 99.99% — auth down means *every* product is down; it's the highest-criticality tier

### 3.3 Core Entities

| Entity | Nature |
|---|---|
| **User** | transactional — read constantly, written rarely |
| **Credential** | transactional — **read only during auth**, isolated blast radius |
| **UsernameClaim** | transactional — write-once, the global uniqueness registry |
| **Session** | ephemeral — TTL'd, high-churn, revocable |
| **LoginAttempt** | ephemeral — TTL'd counters, never durable |
| **AuditEvent** | append-only — logins, resets, lockouts; compliance requirement |

### 3.4 Scale Estimation

- **The number that drives everything — hashing CPU:** bcrypt at cost 12 is ~100ms of CPU per verification *by design*. At **100K logins/sec x 0.1 CPU-seconds = 10,000 CPU cores** doing nothing but hashing. Say this plainly: *"This is the only system I'd design where the security parameter directly sizes the fleet. Dropping the cost factor halves the hardware and halves the attacker's work too — that trade must be made deliberately, not by accepting a default."*
- **Storage:** 1B users x ~500 bytes = **500 GB** for users, ~200 GB for credentials, ~50 GB for the username registry. All modest — **this is not a storage problem.**
- **Session volume:** 100M DAU x ~3 sessions = **300M live sessions** x ~200 bytes = **~60 GB in Redis**, requiring a sharded cluster; TTLs keep it bounded.
- **Read amplification:** token validation happens on *every* API request across the platform — potentially millions/sec. **This is why access tokens must be stateless JWTs**; a store lookup per request would dwarf every other load in the company.

### 3.5 Architecture Diagram

#### Stage 1 — Naive: one auth service, one database

```
   Clients
      |
      v
  +----------------------+          +---------------------+
  |   Auth Service       |--------->|  Postgres           |
  |   - signup           |          |  users (hash inline)|
  |   - login (bcrypt)   |          |  sessions           |
  |   - session lookup   |          +---------------------+
  |     on every request |
  +----------------------+
```

**BREAKING POINT 1 — session lookup on every request (the throughput failure).** If every API call across the platform validates by reading the session row, auth becomes the busiest database in the company. At millions of requests/sec against a store sized for 100K logins/sec, it saturates by orders of magnitude — and it takes down *every* product simultaneously, because everything depends on auth.

**BREAKING POINT 2 — bcrypt CPU starves the API tier.** Hashing at ~100ms/login on the same pods serving normal traffic means 100K logins/sec consumes 10,000 cores that were supposed to be handling requests. Login latency and general API latency degrade together, and autoscaling on CPU flaps.

**BREAKING POINT 3 — username uniqueness blocks sharding.** A `UNIQUE` constraint on `users.username` requires that table to be unsharded. At a billion users, once you need to shard by `user_id`, the global invariant silently breaks — two shards happily accept the same name.

**DECISION — how are tokens validated?**

| Option | Strength | Weakness | Verdict |
|---|---|---|---|
| Server-side session lookup per request | Instant revocation | A store read on every API call platform-wide; auth becomes the SPOF and the hottest path | Rejected at this scale |
| Long-lived stateless JWT | Zero validation I/O | **No revocation** — a stolen token is valid for its full lifetime | Rejected — unacceptable security posture |
| **Short-lived JWT + revocable refresh token** | No I/O on the hot path; revocation within one short window | Up to ~15 min of residual validity after revocation | **Chosen** |
| JWT + a revocation bloom filter at the edge | Near-instant revocation, still cheap | Extra infrastructure; false positives force a fallback lookup | Good addition if the 15-min window is unacceptable |

#### Stage 2 — Dedicated auth tier, split tokens, sharded identity

```
   Clients (web / mobile / API)
        |
        v
  +--------------------------------------------------------------+
  |                    API Gateway / Edge                         |
  |   validates JWT locally (public key, cached) — NO auth call   |
  |   ~millions/sec handled with zero I/O                         |
  +---------------------------+----------------------------------+
                              | only login / signup / refresh
                              v
        +---------------------------------------------------+
        |         Auth Service  (dedicated, CPU-optimized)   |
        |   - isolated fleet: bcrypt burns CPU here only     |
        |   - autoscaled on CPU, separate from product tiers |
        +--+---------------+---------------+-----------------+
           |               |               |
           v               v               v
  +----------------+ +-------------+ +---------------------+
  | Username       | | User +      | | Redis Cluster       |
  | Registry       | | Credential  | |  - sessions (TTL)   |
  | sharded by     | | sharded by  | |  - login attempt    |
  | hash(username) | | hash(userId)| |    counters (INCR)  |
  |  UNIQUE(name)  | |             | |  - MFA challenges   |
  | <- global      | | credentials | +---------------------+
  |    invariant   | | in a SEPARATE|
  |    is LOCAL    | | table/store  |
  +----------------+ +------+-------+
                            |
                            v
                 +-------------------------+
                 |  Kafka: auth-events     |
                 +-----------+-------------+
                             |
              +--------------+--------------+
              v              v              v
        Audit log      Anomaly/fraud    Notification Svc
        (compliance)   detection        ("new device login")
```

**Three things this buys:**
- **JWT validated at the edge** removes auth from the hot path entirely — the gateway checks a signature with a cached public key, so millions of requests/sec cost zero auth I/O.
- **A dedicated auth fleet** contains bcrypt's CPU appetite; it scales on its own curve and a login spike can't degrade product APIs.
- **Two different shard keys on purpose:** the username registry shards by `hash(username)` (making global uniqueness a local constraint), while user/credential data shards by `hash(userId)`. Different invariants, different keys — say this explicitly, it's the core scaling insight of the problem.

**BREAKING POINT (Stage 2) — the login stampede after an outage.** When any dependency recovers, every client retries at once. 100M users reconnecting inside a few minutes means logins far above the 100K/sec steady peak, and because each one costs 100ms of CPU, the auth fleet cannot absorb it — it's CPU-bound, so it can't burst the way an I/O-bound service can. **Mitigations:** (a) refresh tokens with jittered expiry so renewals never synchronize, (b) a queue with admission control in front of login (fail fast with `Retry-After` rather than collapsing), (c) prioritize refresh over full login, since refresh skips bcrypt entirely and keeps existing users working.

### 3.6 Deep Dive: Credential Storage and the Breach Scenario (the riskiest component)

**Why this one:** every other failure is recoverable. A credential breach is permanent, affects a billion people, and — because of password reuse — compromises accounts on *other* services too.

**Defense in depth, and what each layer buys:**

| Layer | Mechanism | What it survives |
|---|---|---|
| 1. Never store plaintext | bcrypt/argon2, per-user salt embedded | Casual DB access, backups, log leaks |
| 2. **Slow by design** | cost factor tuned to ~100ms | Offline cracking: turns billions of guesses/sec into thousands |
| 3. Per-user salt | unique per credential | Rainbow tables and cross-user amortization — each hash must be attacked alone |
| 4. **Pepper** (secret key in a KMS/HSM, not the DB) | `hash(password + pepper)` | **A database-only breach.** The attacker has hashes but not the pepper, so offline cracking is impossible without also breaching the KMS |
| 5. Separate credential store | different table/DB, tighter access | An app bug or SQL injection scoped to the profile store |
| 6. Breach-list rejection at signup | k-anonymity range query | Credential stuffing — the attack that actually happens |
| 7. Audit + anomaly detection | Kafka -> detection | Detecting an in-progress attack rather than learning from the news |

**The pepper is the layer most candidates miss** — say it deliberately: *"Salts stop precomputation but they're stored next to the hash, so a DB breach still permits offline cracking. A pepper is a secret held in a KMS and never in the database, so a database-only compromise yields hashes the attacker mathematically cannot attack. The operational cost is real — rotating a pepper requires re-hashing on next login, which is exactly why `PasswordHasher` needed to be swappable in the LLD."*

**Options for the cost factor:**

| Option | Pros | Cons |
|---|---|---|
| Low cost (bcrypt 8, ~10ms) | Cheap fleet; fast logins | ~10x faster cracking for an attacker too |
| **Moderate (bcrypt 12, ~100ms)** | Industry norm; strong resistance | 10,000 cores at peak — the dominant infrastructure cost |
| High (argon2id, memory-hard, ~250ms) | Best GPU/ASIC resistance (memory-hard defeats parallel hardware) | Higher latency and memory per login; sizing gets harder |

**Decision:** argon2id for new credentials, bcrypt-12 verification retained for legacy, with transparent upgrade on login. *"The `algorithm` column plus `needsRehash()` is what makes that migration possible without ever forcing a billion password resets — the LLD choice and the HLD migration strategy are the same decision viewed at two zoom levels."*

### 3.7 Trade-offs

**Trade-off 1: Short-lived JWT + refresh vs server-side sessions**
- **Chose:** 15-minute JWT access + revocable opaque refresh
- **Gain:** token validation costs zero I/O at millions of requests/sec; auth is off the hot path of every product
- **Lose:** revocation isn't instant — up to 15 minutes of residual validity
- **Failure mode if wrong:** server-side validation per request means one auth-store incident takes down *every* product simultaneously, because nothing can validate a request without it. Auth becomes the single point of failure for the entire company.

**Trade-off 2: bcrypt cost factor 12 vs 8**
- **Chose:** 12 (~100ms), migrating to argon2id
- **Gain:** offline cracking is ~10x harder than cost 8; matches current guidance
- **Lose:** roughly 10,000 cores at peak versus ~1,000 — a large, permanent infrastructure line item
- **Failure mode if wrong:** cost 8 to save money means that when a breach happens, the stolen hashes crack ~10x faster; a password that would have taken a year falls in weeks. **This is the rare case where the infrastructure bill *is* the security control**, and it should be a deliberate, documented decision rather than a default.

**Trade-off 3: Separate username registry vs `UNIQUE` on the users table**
- **Chose:** dedicated registry sharded by `hash(username)`
- **Gain:** global uniqueness stays a *local* constraint while user data shards independently
- **Lose:** signup is two steps (claim then create), needing cleanup of abandoned claims
- **Failure mode if wrong:** keeping `UNIQUE(username)` on a sharded users table means the constraint is per-shard — two users on different shards get the same username, and you discover it when they collide in a URL or a mention. That data-corruption class of bug is extremely painful to repair after the fact.

### 3.8 API Design

| Method | Path | Auth | Request | Response | Status Codes |
|---|---|---|---|---|---|
| POST | `/v1/signup` | none | `{username, email, password}` + `Idempotency-Key` | `{userId, status: PENDING_VERIFICATION}` | 201, 409 (username taken), 422 (weak/breached password), 429 |
| GET | `/v1/usernames/{name}/available` | none | — | `{available}` | 200, **429 (aggressively rate-limited)** |
| POST | `/v1/login` | none | `{identifier, password, deviceInfo}` | `{accessToken, refreshToken, expiresIn}` or `{mfaRequired, challengeToken}` | 200, **401 (always generic)**, 423 (locked), 429 |
| POST | `/v1/token/refresh` | refresh token | `{refreshToken}` | `{accessToken, refreshToken}` (rotated) | 200, 401 |
| POST | `/v1/logout` | access token | `{allDevices?}` | — | 204 |
| POST | `/v1/password/reset-request` | none | `{email}` | **always** `{message: "if an account exists, we sent a link"}` | **200 always** |
| POST | `/v1/password/reset` | reset token | `{token, newPassword}` | — | 204, 422, 410 (expired) |
| POST | `/v1/mfa/verify` | challenge token | `{code}` | `{accessToken, refreshToken}` | 200, 401, 429 |

**Three security-derivation notes worth saying:**
- **`401` is always identical** for unknown user, wrong password, and locked-but-not-disclosed. Any variation is an enumeration oracle.
- **Reset-request always returns 200** even for unknown emails — this is the single most commonly leaked signal in real systems.
- **Refresh rotates the token.** Reuse of an already-rotated refresh token means the token was stolen, so the correct response is revoking the entire session family, not just rejecting the call.

### 3.9 Data Model

```sql
-- Sharded by hash(username): the shard key IS the uniqueness key,
-- so a plain local UNIQUE enforces a GLOBAL invariant.
CREATE TABLE usernames (
    username     VARCHAR(64) PRIMARY KEY,     -- case-folded on write
    user_id      UUID NOT NULL,
    claimed_at   TIMESTAMPTZ DEFAULT now(),
    status       VARCHAR(16) DEFAULT 'ACTIVE' -- ACTIVE | RESERVED | RELEASED
);

-- Sharded by hash(user_id).
CREATE TABLE users (
    user_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id       UUID,                        -- multi-tenant: the Salesforce angle
    username     VARCHAR(64) NOT NULL,        -- denormalized copy for reads
    email        VARCHAR(320) NOT NULL,
    email_verified BOOLEAN DEFAULT FALSE,
    display_name VARCHAR(128),
    status       VARCHAR(24) NOT NULL DEFAULT 'PENDING_VERIFICATION',
    mfa_enabled  BOOLEAN DEFAULT FALSE,
    created_at   TIMESTAMPTZ DEFAULT now()
);

CREATE UNIQUE INDEX idx_email ON users (lower(email));

-- SEPARATE table, tighter grants, ideally a separate database.
-- Nothing here is ever selected by a profile query.
CREATE TABLE credentials (
    user_id        UUID PRIMARY KEY REFERENCES users(user_id),
    password_hash  VARCHAR(255) NOT NULL,     -- salt + cost embedded in the string
    algorithm      VARCHAR(24) NOT NULL,      -- BCRYPT_12 | ARGON2ID  -> enables rotation
    pepper_version SMALLINT NOT NULL DEFAULT 1, -- which KMS key; supports rotation
    must_change    BOOLEAN DEFAULT FALSE,
    updated_at     TIMESTAMPTZ DEFAULT now()
);

-- Prevents reuse of the last N passwords.
CREATE TABLE credential_history (
    user_id      UUID NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    algorithm    VARCHAR(24) NOT NULL,
    retired_at   TIMESTAMPTZ DEFAULT now(),
    PRIMARY KEY (user_id, retired_at)
);

-- Primary home is Redis; this is the durable mirror for device management.
CREATE TABLE sessions (
    session_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL,
    refresh_token_hash  CHAR(64) NOT NULL,     -- SHA-256 of the token, never the token
    family_id           UUID NOT NULL,         -- rotation lineage: reuse revokes the family
    device_fingerprint  VARCHAR(128),
    ip_address          INET,
    created_at          TIMESTAMPTZ DEFAULT now(),
    expires_at          TIMESTAMPTZ NOT NULL,
    revoked_at          TIMESTAMPTZ,

    UNIQUE (refresh_token_hash)
);

CREATE INDEX idx_user_sessions ON sessions (user_id)
    WHERE revoked_at IS NULL;

-- Compliance + anomaly detection. Append-only, never updated.
CREATE TABLE auth_audit (
    id           BIGSERIAL PRIMARY KEY,
    user_id      UUID,
    event_type   VARCHAR(32) NOT NULL,  -- LOGIN_OK | LOGIN_FAIL | LOCKOUT | RESET | MFA_FAIL
    ip_address   INET,
    user_agent   TEXT,
    occurred_at  TIMESTAMPTZ DEFAULT now()
) PARTITION BY RANGE (occurred_at);
```

| Decision | Why | What breaks otherwise |
|---|---|---|
| `credentials` as a separate table with tighter grants | Profile queries can never accidentally read the hash | One `SELECT *` in a debug log or a serializer leaks hashes for every user it touches |
| `algorithm` + `pepper_version` columns | Rotation of both algorithm and pepper without forced resets | An algorithm upgrade requires resetting a billion passwords — operationally impossible |
| `usernames` sharded by the name itself | Makes a global invariant enforceable locally | Per-shard uniqueness on a `user_id`-sharded table silently permits duplicate usernames |
| `refresh_token_hash`, never the token | A session-table leak yields nothing usable | Plaintext tokens in a leaked table = instant takeover of every live session |
| `family_id` on sessions | Rotation lineage: reuse of a rotated token revokes the whole family | Stolen refresh tokens can be used indefinitely alongside the legitimate client |
| `lower(email)` unique index | Emails are case-insensitive in practice | `User@x.com` and `user@x.com` become two accounts, then collide at reset time |
| `credential_history` | Enforces no-reuse policies | Users cycle back to a known-breached password on the next forced rotation |
| No login-attempt table | Counters are ephemeral, high-write, TTL'd — Redis, not Postgres | A durable write per failed login makes a brute-force attack a self-inflicted database DoS |

### 3.10 Salesforce Multi-Tenancy Angle

> *"This is Salesforce Identity, and it's the strongest product fit of anything I'd prep. `org_id` scopes users, and identity **policy becomes per-org configuration data**: password complexity, session timeout, IP login ranges, MFA enforcement, and SSO settings all vary per tenant — which is exactly why `PasswordRule` is a composable chain rather than a hardcoded validator, since the rule set is loaded per org. Usernames on Salesforce are globally unique across all orgs (they look like emails), which maps precisely to the separate globally-sharded username registry rather than per-org uniqueness."*

Two more points that land:
- **SSO/SAML is the default for enterprise tenants, not an add-on** — many orgs never use a Salesforce-stored password at all, which is why `AuthProvider` is an interface. Per-org, the *provider itself* is configuration.
- **Per-org lockout policy is a fairness concern:** one tenant's aggressive lockout settings must not consume shared attempt-tracking capacity for others — the same per-tenant quota pattern as the Rate Limiter problem.

---

## 4.  Navigation Pivots — THIS Problem

**Opening Protocol (first 2 minutes — verbatim, per `format.md` Section 2):**

> "Before I start — should I do LLD first or HLD first, or do you have a preference?"
> *(If no preference:)* "I'll start with LLD — the entity split, credential handling, and the security mechanisms — then zoom out to how this scales to a billion users, where hashing cost and username uniqueness under sharding are the interesting constraints. I'll flag the transition."

| Interviewer Says | What They Want | Your Move |
|---|---|---|
| "How do you store passwords?" | **The gate question** — get it right immediately | bcrypt/argon2, per-user salt, cost factor tuned to ~100ms, plus a KMS-held pepper. Never SHA/MD5, and say *why* fast hashes are the flaw |
| "How do you guarantee unique usernames at a billion?" | The explicit ask in the sourced prompt | Separate registry sharded by `hash(username)` — the shard key is the invariant key; explain why `UNIQUE` on a `user_id`-sharded table silently fails |
| "JWT or sessions?" | Token-model judgment | Both, split by role: short JWT access (no I/O at the edge) + opaque revocable refresh; state the 15-min revocation window honestly |
| "How do you log out everywhere?" | Revocation understanding | Revoke sessions server-side; access tokens die within their short TTL. Don't claim JWTs are revocable |
| "How do you prevent brute force?" | Security depth | Per-account **and** per-IP counters (atomic `INCR`), lockout with backoff, checked *before* hashing — and note this is the Rate Limiter problem |
| "What if the database is stolen?" | The best question here | The 7-layer table in 3.6, and lead with the pepper — the layer that makes a DB-only breach uncrackable |
| "Scale login to 100K/sec" | HLD sizing | 100K x 100ms = 10,000 cores of bcrypt; dedicated CPU-optimized auth fleet; the cost factor is simultaneously a security and a capacity decision |
| "How does Salesforce do this?" | Domain awareness | Salesforce Identity: globally unique usernames across orgs, per-org policy as config, SSO/SAML as the enterprise default |

---

## 5.  TL;DR — Dual-Level Pitch

At the class level the decisive split is `Credential` separated from `User` — profile data is read on every request while password hashes must only be touched during authentication, and merging them is how hashes reach logs and caches — with `PasswordHasher` as an interface plus a stored `algorithm` column so bcrypt can migrate to argon2id transparently on login rather than forcing a billion password resets. Uniqueness gets its own strategy because a `UNIQUE` constraint silently stops working once users shard by `user_id`, so the username registry is sharded by `hash(username)`, making the shard key the invariant key and turning a global constraint into a local one. Tokens split by role: a 15-minute stateless JWT validated at the edge with zero I/O (essential when every API request in the company validates a token), plus an opaque, hashed, rotating refresh token whose reuse revokes the entire session family as a theft signal. The defining scale insight is that bcrypt is deliberately slow, so 100K logins/sec is 10,000 CPU cores of pure hashing on a dedicated fleet — the only system where the infrastructure bill *is* the security control — and the defining security layer is a KMS-held pepper, which makes a database-only breach mathematically uncrackable. On Salesforce this is Identity: globally unique usernames across all orgs, per-org policy as configuration driving the composable `PasswordRule` chain, and SSO/SAML as the enterprise default rather than an add-on.

---

##  Changelog

| Date | Change |
|---|---|
| Aug 2026 | File created — sixth and final problem of the top-7 build in `Interview/Salesforce/HLD+LLD/`. Grounded in CodingKaro Jul 2025 (the most explicit HLD+LLD-in-one-sentence prompt found in research) and corroborated by a Dec 2025 HM-round report emphasizing *"security considerations and protection mechanisms"* — hence security-first framing throughout. Follows `solution-notes-standards.md` and matches the derivation-first bar from the prior five files. |
