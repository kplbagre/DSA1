# Stateful vs Stateless Services

> **Standard followed:** `SystemDesignConcepts/Interview-Resources/Metadata/notes-standards.md`

---

## 📖 What are Stateful and Stateless Services?

**Full form:** No acronym. Two architectural modes that describe whether a service instance retains memory of a client across requests.

**Simple analogy:** Two types of restaurant waiters. **Stateless waiter:** every time a table waves them over, they read the written order slip from scratch — they don't remember the table at all. Swap the waiter mid-meal and service is uninterrupted because the information is on the slip, not in the waiter's head. **Stateful waiter:** they memorized your dietary restrictions, that you hate coriander, and that you already ordered dessert — but if they leave early, the replacement waiter has none of that context and the experience breaks.

**Core principle:** A stateless service keeps no per-client memory between requests — each request carries all the information needed to process it. Any instance can handle any request. A stateful service retains per-client context — either in memory (sticky sessions) or by requiring access to a specific shared store. Stateless services scale horizontally without constraint; stateful services require careful session routing or session externalization.

**Why it matters in system design:** The session management choice — sticky sessions, centralized session store, or JWT — determines whether your service can freely scale horizontally, survive pod failures without user disruption, and deploy without downtime.

---

## 🎯 Why This Matters

Every horizontal scaling question eventually becomes a stateful/stateless question. "Pod A dies — what happens to the users connected to it?" is a direct probe of whether you've designed session state correctly. JWT vs Redis session store is a trade-off question that appears in auth design, API gateway design, and microservices security rounds.

**Round:** System design, deep dives on auth/session management, reliability rounds. Directly related to WebSocket design (connections are inherently stateful).

**Why senior engineers own this:** Junior engineers reach for sticky sessions because it's the path of least resistance. Senior engineers know that sticky sessions create invisible SPOFs and uneven load distribution, and they design around it with externalized state or token-based auth.

---

## 🧠 The Mental Model

Picture a self-storage facility. You store a box (your session) in unit #47. Every time you visit, you must go to unit #47 — you can't go to unit #12 because your box is not there. That is a sticky session: a specific server holds your state, and you must return to it.

Now imagine cloud storage. You put your files in the cloud, accessible by ID from any device. Any device, any location — the state is externalized from the physical device. That is a centralized session store: the server pod holds nothing; it fetches your session from Redis by the session ID in your cookie.

Now imagine you're carrying a passport. Every country's border control verifies it on the spot — they don't call your home country's database to confirm your identity. The passport contains everything they need, signed by an authority they trust. That is a JWT: self-contained, signed, verifiable by any pod without a central lookup.

**The key insight is:** Stateless is not about whether your system has state — every useful system has state. Stateless means the *server pod* holds no state. The state lives either in the client (JWT), in a dedicated shared store (Redis session), or in the database. Free the pod from state, and it becomes disposable — add it, remove it, replace it, and users never notice.

---

## 🎨 Visual — Three Session Architectures + Request Flow on Pod Failure

```
FULL SYSTEM TOPOLOGY — Three ways to handle session state:

1. STICKY SESSIONS (stateful pods):

   Client ──▶ LB (cookie: pod=1) ──▶ ┌──────────┐ ← Session in memory
                  │                   │  Pod 1   │   User A, B, C pinned here
                  └── always pod 1    └──────────┘
                                      ┌──────────┐
                                      │  Pod 2   │ ← User D, E, F pinned here
                                      └──────────┘
   Pod 1 dies → Users A, B, C lose session → must re-login ❌
   Scale Pod 3 in → LB adds it, but Pod 1's users stay on Pod 1 until session
   expires → uneven load distribution ❌

2. CENTRALIZED SESSION STORE (externalized state):

   Client ──▶ LB (round-robin) ──▶ ┌──────────┐ ── GET session:X → Redis
                                    │  Pod 1   │
                                    └──────────┘ ✅ any pod handles any user
                                    ┌──────────┐ ── GET session:X → Redis
                                    │  Pod 2   │
                                    └──────────┘
                                         │
                                    ┌──────────┐
                                    │  Redis   │ ← Session lives here
                                    │ Session  │   session:X → {userId, cart}
                                    │  Store   │
                                    └──────────┘
   Pod 1 dies → LB routes to Pod 2 → Pod 2 fetches session from Redis ✅
   Add Pod 3 → immediately takes traffic → no rebalancing needed ✅
   Redis dies → all sessions gone ← Redis is now the critical component

3. JWT (truly stateless):

   Client ──▶ LB (round-robin) ──▶ ┌──────────┐ ← verifies JWT signature
                                    │  Pod 1   │   reads claims from token
                                    └──────────┘   no DB/Redis lookup needed
                                    ┌──────────┐
                                    │  Pod 2   │
                                    └──────────┘
   No session store. Pod verifies signature with public key and reads
   user claims (userId, role, expiry) from the token payload directly.
   Pod 1 dies → no impact → Pod 2 handles next request ✅
   Scale out → zero configuration needed ✅
   User logs out → token still valid until expiry ← CANNOT revoke ❌


COMPONENT DETAIL — Comparison Table:

  ┌─────────────────┬──────────────────┬──────────────────┬──────────────┐
  │                 │ Sticky Sessions  │  Redis Session   │     JWT      │
  ├─────────────────┼──────────────────┼──────────────────┼──────────────┤
  │ State location  │ Pod memory       │ Redis            │ Client token │
  │ Pod failure     │ Session lost ❌  │ No impact ✅     │ No impact ✅ │
  │ Horizontal      │ Hard (uneven LB) │ Easy ✅          │ Easiest ✅   │
  │ scale           │                  │                  │              │
  │ Revoke session  │ Trivial ✅       │ Delete key ✅    │ Need         │
  │                 │                  │                  │ blocklist ❌ │
  │ Session data    │ Unlimited        │ Unlimited        │ ~4KB max     │
  │ size            │                  │                  │              │
  │ Extra latency   │ None             │ Redis RTT ~1ms   │ None         │
  │ Single point    │ Each pod is one  │ Redis is one     │ None         │
  │ of failure      │                  │                  │              │
  │ Best for        │ Legacy apps,     │ Cart, checkout,  │ Microservice │
  │                 │ WebSocket (but   │ multi-step flows │ auth, mobile │
  │                 │ needs pub/sub)   │                  │ API tokens   │
  └─────────────────┴──────────────────┴──────────────────┴──────────────┘

KEY INVARIANT:
   Sticky sessions trade scaling freedom for simplicity.
   Redis sessions trade a small per-request RTT (~1ms) for full horizontal
   freedom — worth it in almost every case.
   JWT trades revocability for zero-lookup statelessness — the right choice
   for service-to-service auth where tokens are short-lived (< 15 min).
```

---

## ⚙️ How It Actually Works

**Steps:**

1. **Decide what state the service actually needs** — user identity (handled by JWT), cart or multi-step flow state (needs a store), connection state (WebSocket — handled by pub/sub fan-out)
2. **Choose the storage strategy** — in-pod sticky sessions (simplest, worst scaling), Redis centralized store (best balance), JWT (no lookup, but no revocation)
3. **Implement session creation** — assign session ID on login, store in Redis with TTL, return session ID as HttpOnly cookie (or return JWT as Bearer token for API clients)
4. **On subsequent requests** — extract session ID from cookie, fetch from Redis, validate; OR verify JWT signature and read claims directly from the token
5. **On logout** — for Redis sessions: delete the key; for JWT: either short TTL (expire naturally) or maintain a blocklist in Redis

```java
// 1. JWT VALIDATION — stateless: any pod validates without a lookup
@Service
public class JwtTokenService {

    private final Key signingKey;

    // Any pod can call this — no Redis, no DB, no shared state needed
    public UserClaims validateToken(String bearerToken) {
        try {
            Claims claims = Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(bearerToken)
                .getBody();
            return UserClaims.builder()
                .userId(claims.getSubject())
                .role(claims.get("role", String.class))
                .build();
        } catch (ExpiredJwtException e) {
            throw new UnauthorizedException("Token expired");
        } catch (JwtException e) {
            throw new UnauthorizedException("Invalid token");
        }
    }
}

// 2. REDIS SESSION LOOKUP — externalized state: any pod can fetch it
@Service
public class SessionService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    // Any pod can call this — session is in Redis, not the pod
    public UserSession getSession(String sessionId) {
        String sessionJson = redisTemplate.opsForValue()
            .get("session:" + sessionId);
        if (sessionJson == null) {
            throw new SessionExpiredException("Session not found or expired");
        }
        return objectMapper.readValue(sessionJson, UserSession.class);
    }

    // Store session in Redis with a 30-minute TTL
    public void createSession(String sessionId, UserSession session) {
        String sessionJson = objectMapper.writeValueAsString(session);
        redisTemplate.opsForValue().set(
            "session:" + sessionId,
            sessionJson,
            Duration.ofMinutes(30)
        );
    }

    // Logout: delete from Redis — immediate revocation
    public void invalidateSession(String sessionId) {
        redisTemplate.delete("session:" + sessionId);
    }
}
```

### What is JWT, and why does it fit stateless service-to-service auth?

**JWT** (JSON Web Token — a compact, URL-safe token that carries a signed JSON payload called "claims." The token is signed by the issuer with a secret key; any receiver with the public key can verify the signature without calling the issuer. Contains: header (algorithm), payload (userId, role, expiry), signature) is ideal for service-to-service authentication in microservices because each downstream service validates the token independently. No central auth service bottleneck on every request. In an interview: "for user-facing sessions I prefer Redis for immediate revocability; for service-to-service I prefer short-lived JWTs (15 minutes) because revocation is not needed and the zero-lookup benefit is significant at high RPS."

---

## 🏢 Real World — Where Companies Use This

- **Netflix** (hybrid model): Stateless API services across thousands of pods — no sticky sessions anywhere. User session data (preferences, watch history, playback position) stored in EVCache (a distributed Memcached layer on top of their regional cache), not in pods. Service-to-service auth uses internal JWT tokens with 15-minute expiry. WebSocket for UI updates uses Redis Pub/Sub for cross-pod fan-out (the connection is stateful per pod, but the message delivery is made stateless via pub/sub).

- **Flipkart** (cart and checkout): Cart state stored in Redis with user ID as key, not in application memory. During flash sales, pods are auto-scaled from 20 to 200 instances in minutes — only possible because pods carry no state. Each new pod starts handling cart operations immediately by looking up Redis.

- **Razorpay** (payment APIs): JWT-based API authentication for merchant integrations. Merchant's server-side API key generates JWTs; Razorpay's gateway pods validate signatures without a central lookup. At peak (festival sales), Razorpay processes 5,000+ payments/second — a central session lookup per payment would be a bottleneck; JWT eliminates it.

- **Swiggy** (delivery tracking): WebSocket connections for real-time delivery tracking are stateful — each connection is pinned to one pod. The delivery location updates (Kafka events from driver app) are broadcast to the correct pod via Redis Pub/Sub. The connection statefulness is contained within the pod; the inter-pod messaging is stateless.

- **Airbnb** (booking flow): Multi-step booking (search → select → add guests → payment) uses a Redis-backed session with a 30-minute TTL. If a user's pod dies mid-booking, they reload, fetch the session from Redis, and pick up exactly where they left off. This is only possible because session is externalized from the pod.

---

## 🧭 When to Use vs When NOT to Use

| Use stateless (JWT) when | Use centralized session store (Redis) when | Use sticky sessions when |
|---|---|---|
| Service-to-service auth with short-lived tokens (< 15 min) | User sessions that need immediate revocability (logout, ban) | Legacy application that cannot be modified to support Redis |
| Mobile / API clients where token storage is client-managed | Multi-step flows where server-side state grows per session (cart, checkout) | WebSocket connections (but add Redis Pub/Sub for cross-pod messaging) |
| Horizontal scaling is the top priority and no per-user server state is needed | Horizontal scaling needed + session cannot expire until user logs out | Proof-of-concept or dev environment only |

**The common mistake:** Using sticky sessions because "it's simpler" in a cloud environment, then being surprised that pod autoscaling doesn't work correctly (new pods receive no traffic because all existing users are pinned to old pods) and pod failures cause mass re-logins during peak traffic.

---

## ⚠️ Trade-offs

| | |
|---|---|
| **You gain (stateless)** | Free horizontal scaling — any pod handles any request; zero impact from pod death; simpler load balancing (round-robin, no affinity rules); zero-downtime rolling deploys without draining sessions |
| **You lose** | JWT: cannot revoke before expiry without a blocklist (which re-introduces a central store); token size limits (can't store large session objects); Redis session: adds 1 Redis RTT (~1ms) to every authenticated request; Redis becomes a critical shared dependency (but Redis Cluster is highly available) |
| **Failure mode** | **JWT with long expiry**: user account compromised → can't revoke access → must wait for token expiry (sometimes hours). Fix: short TTL (15 min) + refresh token pattern. **Redis session store down**: all authenticated requests fail until Redis recovers — Redis is now your availability bottleneck. Fix: Redis Sentinel or Cluster; fallback to read-only or graceful degradation. **Sticky sessions + pod scale-out**: new pods sit idle while old pods are overloaded because all users are pinned to the old set. Fix: externalize session before scaling. |

---

## 🔬 Interview Q&As

### Q: "You have a horizontal scaling problem — user sessions keep breaking when pods restart. Why?"
> Sessions are stored in pod memory with sticky sessions. When a pod restarts, its in-memory session store is cleared — every user pinned to that pod must re-authenticate. The fix is session externalization: move session data to Redis, give each session a unique ID stored in an HttpOnly cookie. Now any pod can serve any user by looking up the session ID in Redis — pod restarts are transparent to users.

### Q: "What's wrong with sticky sessions at scale?"
> Three problems: (1) pod failure = session loss for all users pinned to that pod; (2) load distribution becomes uneven when pods scale in/out — new pods get no existing traffic because existing users are pinned to old pods; (3) rolling deployments require draining pods before restart, adding deployment time proportional to session TTL. Sticky sessions trade scaling freedom for the simplicity of not implementing a session store. That trade is almost always wrong in a cloud environment.

### Q: "How does Netflix handle user sessions across thousands of pods?"
> Netflix uses no sticky sessions for any stateless service. User session data (preferences, profile, plan) is stored in EVCache (their distributed Memcached layer) — any API pod fetches the session by user ID on each request. For service-to-service authentication, they use short-lived JWT tokens generated by their internal auth service — validated by each downstream service without a central lookup. WebSocket connections for real-time UI updates use Redis Pub/Sub for fan-out, so the connection's pod doesn't need to receive every event — the event is broadcast to the correct pod via a user-specific Redis channel.

### Q (Tier 2): "You moved to JWT tokens. Your security team discovers a compromised token. How do you revoke it before it expires?"
> JWT's fundamental weakness: it is self-validating, so there is nothing to revoke on the server. Three options: (1) short TTL (15 minutes) + refresh token — compromise exposure window is minimal; accept the small risk; (2) token blocklist — store revoked JWT IDs (jti claim) in Redis with TTL matching the token's remaining lifetime; every pod checks the blocklist on validation; this effectively re-introduces a central lookup and loses the zero-lookup benefit; (3) rotate the signing key — invalidates all tokens immediately but logs out every user in the system. In practice: short TTL is the right design choice. If you need instant revocation, use a Redis session store instead of JWT for user-facing sessions.

### Q (Tier 2): "Your WebSocket server is stateful — users are pinned to a pod. What happens when that pod dies?"
> The WebSocket connection is dropped — the client must reconnect. On reconnect, the load balancer routes to any available pod (the dead pod is removed from rotation by health checks). The new pod has no context about which clients were connected to the dead pod. Mitigation: (1) client-side reconnect with exponential backoff — reconnects automatically within seconds; (2) Redis Pub/Sub for cross-pod message delivery — when the client reconnects to a different pod, the new pod subscribes to the user's Redis channel and immediately starts receiving events; (3) Kafka for offline storage — any messages delivered while the client was disconnected are replayed from Kafka offset when the client reconnects. The WebSocket pod's stateful connection is unavoidable; the state that matters (undelivered messages, user presence) must be externalized so any replacement pod can resume from where the failed pod stopped.

### Q (Tier 2): "Stateless services can scale infinitely. But your downstream database still has one primary. Did you actually solve the scaling problem?"
> Stateless at the service tier eliminates one bottleneck — the pod count is no longer the constraint. But the database bottleneck remains. Free pod scaling increases the number of concurrent DB connections, which can exhaust the DB's connection pool and make the DB the new bottleneck. Statelessness is a prerequisite for horizontal scaling, not the complete solution. Pair it with: a connection pooler (PgBouncer) to cap DB connections, read replicas to handle read traffic, caching (Redis) to absorb repeated reads, and sharding if write volume exceeds a single primary's capacity.

---

## 🧾 TL;DR

> "Stateless services scale freely because any pod handles any request — achieve this by externalizing session state to Redis (for full sessions with revocability) or by using short-lived JWTs (for service-to-service auth where zero-lookup performance matters more than instant revocation)."

---

## 🔗 Related Concepts

- **`../../Production-Grade/System-Design-Patterns/26-websocket-real-time-communication.md`** — WebSocket connections are stateful; the Redis Pub/Sub cross-pod fan-out pattern solves the stateful connection scaling problem
- **`../../Foundations/Performance-and-Scale/03-caching.md`** — Redis session store and caching use the same Redis infrastructure; TTL and eviction policies apply to both
- **`34-cap-theorem-consistency-models.md`** — externalized session state introduces eventual consistency: a session write may not be immediately visible on a different pod that reads from a Redis replica
- **`../Resilience-and-Fault-Tolerance/57-spof.md`** — sticky sessions introduce a per-pod SPOF; Redis session store consolidates the SPOF to the Redis cluster (which is HA)
- **`../../Foundations/Performance-and-Scale/55-scalability.md`** — stateless services are a prerequisite for horizontal scaling; stateful services require session drain or externalization before pods can be freely added/removed

---

## 📚 Further Reading (Optional — after the note)

| Resource | What it adds | Time |
|---|---|---|
| **RFC 7519 — JSON Web Token (JWT)** — jwt.io/introduction | JWT structure, claims vocabulary, algorithm choices (RS256 vs HS256), and common vulnerabilities (alg:none attack, weak secrets) | ~15 min read |
| **"Designing Data-Intensive Applications" Ch. 9** — Martin Kleppmann | Consistency guarantees when session state is distributed — what "reading your own writes" means in a Redis replica setup; linearizability vs sequential consistency | ~20 min read |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Jul 10, 2026 | File created. Covers sticky sessions vs Redis session store vs JWT with side-by-side comparison table, full topology diagrams showing pod failure impact, Java code for both JWT validation and Redis session lookup/invalidation, 5 real company examples (Netflix, Flipkart, Razorpay, Swiggy, Airbnb), and 6 Q&As including 3 Tier 2 probe questions. |
