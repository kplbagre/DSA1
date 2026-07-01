# API Gateway — Single Entry Point for All Client Requests

> The API Gateway sits at the edge of your system — your public-facing URL. It's where authentication, request routing, rate limiting, and protocol translation happen before requests reach your internal services. At SDE 3: you must know why a gateway is NOT just a load balancer, how it filters/routes requests, and how it breaks down under scale.

---

## 🎯 Why This Matters

Your system has 10 internal microservices. Each needs authentication, rate limiting, and monitoring. Do you duplicate this logic in each service, or do it once at the entry point? The API Gateway is the answer — a single place to enforce cross-cutting concerns. In interviews, candidates confuse it with a load balancer; you'll explain the difference and describe its role in request routing and service-to-service calls.

---

## 📖 Terminology Table

| Term | Plain-English Meaning | Example |
|------|----------------------|---------|
| **API Gateway** | single-entry-point proxy that handles cross-cutting concerns (auth, routing, rate limits) before forwarding to internal services | Kong, AWS API Gateway, Spring Cloud Gateway |
| **Request Routing** | forwarding incoming requests to the correct downstream service based on path, headers, or host | `/api/orders` → Order Service; `/api/users` → User Service |
| **JWT Validation** | verifying a signed token at the gateway edge so internal services skip auth logic | gateway extracts token, validates signature, injects user-id header downstream |
| **Rate Limiting (gateway)** | counting requests per client at the entry point and rejecting excess with HTTP 429 | 100 req/min per API key; request 101 → `429 Too Many Requests` |
| **Protocol Translation** | converting between external protocol (REST/JSON) and internal protocol (gRPC/Protobuf) | mobile client sends JSON → gateway translates to gRPC for Order Service |
| **Trace ID Injection** | gateway stamps a unique trace ID on every request so distributed traces can be assembled later | `X-Trace-Id: abc123` header added at entry; propagated through all services |
| **L7 Proxy** | operates at the HTTP layer, can inspect headers, paths, and cookies — unlike L4 (TCP) which is blind to HTTP semantics | path-based routing, cookie-based sticky sessions — only possible at L7 |
| **BFF (Backend for Frontend)** | gateway variant tailored per client type; aggregates multiple microservice calls into one response shaped for that client | mobile BFF combines User + Orders + Cart into one payload to reduce round trips |

---

## 🧠 The Mental Model

Imagine a theme park with 10 different attractions. Without a gate:
- Each attraction needs its own ticket booth (duplicate authentication).
- Each attraction counts visitors separately (duplicate monitoring).
- Visitors get lost; no central routing.

With a gate:
1. **Entry point:** All visitors enter through one gate. Gate agent checks ID, issues wristband (authentication token).
2. **Routing:** Visitor says "I want the roller coaster." Gate agent directs them: "Take path A, Attraction 3."
3. **Rate limiting:** Gate agent counts: "We've let in 1000 people for the roller coaster today. No more until tomorrow." (prevents one attraction from getting overwhelmed).
4. **Transformation:** Visitor has a paper map (HTTP). Some attractions need digital tickets (gRPC). Gate agent translates.

**The key insight:** The API Gateway is a **facade** — it hides internal complexity. Clients see ONE URL; internally, 10 services work independently.

---

## 🎨 Visual — API Gateway in System Architecture

### Full System Topology — Where API Gateway Sits

```
INTERNET / CLIENTS
    ↓
┌─────────────────────────────────────────────────────────────┐
│ API GATEWAY                                                 │
│ ┌──────────────────────────────────────────────────────┐   │
│ │ [Authenticate] [Rate Limit] [Route] [Transform]      │   │
│ └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
    ↓
┌──────────────────────────────────────────────────────┐
│ LOAD BALANCER                                        │
│ (Round Robin, Least Connections)                    │
└──────────────────────────────────────────────────────┘
    ↓         ↓         ↓
   ┌──────┐ ┌──────┐ ┌──────┐
   │ Pod1 │ │ Pod2 │ │ Pod3 │
   │Order │ │User  │ │Catalog
   │Service├─┤Service├─┤Service
   └──────┘ └──────┘ └──────┘
    ↓
   ┌──────────────────┐
   │   CACHE          │
   │  (Redis)         │
   └──────────────────┘
    ↓
   ┌──────────────────┐
   │  DATABASE        │
   │ (Postgres)       │
   └──────────────────┘

KEY INVARIANT:
   API Gateway sits at edge, between clients and internal services.
   Single entry point for ALL external requests.
   Load Balancer distributes to service replicas BEHIND the gateway.
   Services don't talk directly to clients (gateway is facade).
```

### Component Detail — Internal Gateway Flow

```
REQUEST ARRIVES AT GATEWAY
    ↓
┌──────────────────────────────┐
│ 1. AUTHENTICATION            │
│    Extract JWT token         │
│    Validate signature         │
│    Check expiry               │
│    ❌ Invalid? → 401 Unauthorized (reject)
│    ✅ Valid? → continue
└──────────────────────────────┘
    ↓
┌──────────────────────────────┐
│ 2. RATE LIMITING             │
│    Check client rate quota    │
│    Decrement token bucket     │
│    ❌ Over quota? → 429 Too Many Requests (reject)
│    ✅ Within quota? → continue
└──────────────────────────────┘
    ↓
┌──────────────────────────────┐
│ 3. REQUEST ROUTING           │
│    Match URL path + method    │
│    Example: POST /orders/*    │
│    → route to Order Service   │
└──────────────────────────────┘
    ↓
┌──────────────────────────────┐
│ 4. REQUEST TRANSFORMATION    │
│    Header injection           │
│    (add X-User-ID, trace-id) │
│    Protocol conversion        │
│    (HTTP REST → gRPC backend) │
└──────────────────────────────┘
    ↓
FORWARD TO BACKEND SERVICE
    ↓
WAIT FOR RESPONSE
    ↓
┌──────────────────────────────┐
│ 5. RESPONSE TRANSFORMATION   │
│    Status code mapping        │
│    Header filtering           │
│    Compression (gzip)         │
└──────────────────────────────┘
    ↓
RETURN TO CLIENT

KEY INVARIANT:
   Every request passes through authentication, rate limiting, and routing.
   Failed checks → immediate rejection at gateway (no backend load).
   Backend never sees unauthenticated or rate-limited requests.
```

---

## ⚙️ How It Actually Works

**Steps in plain English:**

1. **Client sends HTTP request** (e.g., `GET /orders/123` with JWT header).
2. **Gateway extracts and validates JWT** — ensures token is signed correctly and not expired.
3. **Gateway checks rate limit** — looks up client's token bucket; if empty, rejects with 429.
4. **Gateway routes request** based on URL path: `/orders/*` → Order Service Pod, `/users/*` → User Service Pod.
5. **Gateway transforms request** — adds metadata (trace ID, user ID), converts protocol if needed (HTTP to gRPC).
6. **Gateway forwards** to backend service (via load balancer or directly to service URL).
7. **Gateway receives response** from backend.
8. **Gateway transforms response** — filters headers, applies compression, logs metrics.
9. **Gateway returns response** to client.

```java
// API Gateway Handler (simplified using Spring Cloud Gateway or custom Netty-based gateway)

@Configuration
public class ApiGatewayConfig {
    // Step 1-2 — Authentication (JWT validation)
    @Bean
    public GatewayFilter authenticationFilter() {
        return (exchange, chain) -> {
            String token = exchange
                .getRequest()
                .getHeaders()
                .getFirst("Authorization");
            
            if (token == null) {
                // Step 2 — No token → reject
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }
            
            // Validate JWT signature and expiry
            Claims claims;
            try {
                claims = Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            } catch (Exception e) {
                // Token invalid → reject
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }
            
            // Step 3 — Extract user ID and add to request headers for backend
            String userId = claims.getSubject();
            ServerHttpRequest mutatedRequest = exchange
                .getRequest()
                .mutate()
                .header("X-User-ID", userId)
                .build();
            
            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        };
    }

    // Step 3 — Rate limiting (token bucket per client)
    @Bean
    public GatewayFilter rateLimitingFilter() {
        return (exchange, chain) -> {
            String clientId = exchange
                .getRequest()
                .getHeaders()
                .getFirst("X-Client-ID");
            
            // Check rate limit
            boolean allowed = rateLimiter.allowRequest(clientId);
            if (!allowed) {
                // Step 3 — Over quota → reject
                exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                return exchange.getResponse().setComplete();
            }
            
            return chain.filter(exchange);
        };
    }

    // Step 4 — Request routing (by path pattern)
    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
            // Step 4 — Route POST /orders/* to Order Service
            .route("orders", r -> r
                .path("/orders/**")
                .uri("http://order-service:8080"))
            
            // Step 4 — Route GET /users/* to User Service
            .route("users", r -> r
                .path("/users/**")
                .uri("http://user-service:8080"))
            
            // Step 4 — Route GET /products/* to Catalog Service
            .route("products", r -> r
                .path("/products/**")
                .uri("http://catalog-service:8080"))
            
            .build();
    }

    // Step 5 — Request/response transformation (add trace ID, compress response)
    @Bean
    public GatewayFilter transformationFilter() {
        return (exchange, chain) -> {
            // Step 5 — Add trace ID header
            String traceId = UUID.randomUUID().toString();
            ServerHttpRequest mutatedRequest = exchange
                .getRequest()
                .mutate()
                .header("X-Trace-ID", traceId)
                .header("X-Request-Start-Time", String.valueOf(System.currentTimeMillis()))
                .build();
            
            ServerHttpResponse response = exchange.getResponse();
            
            return chain
                .filter(exchange.mutate().request(mutatedRequest).build())
                .then(Mono.fromRunnable(() -> {
                    // Step 8 — Response transformation
                    response.getHeaders().add("Content-Encoding", "gzip");
                    response.getHeaders().add("X-Trace-ID", traceId);
                }));
        };
    }
}
```

### What is Spring Cloud Gateway, and why does it fit here?

Spring Cloud Gateway is a **reactive gateway built on Spring WebFlux** that handles requests asynchronously without blocking threads. In an interview, if asked: *"Spring Cloud Gateway is a non-blocking HTTP gateway that sits at the entry point of your microservices architecture. It provides built-in support for path-based routing, rate limiting filters, and request/response transformation. We chose it because it scales to thousands of concurrent connections on a single instance (unlike servlet-based alternatives like Zuul) and integrates seamlessly with Spring Boot services."*

---

## 🏢 Real World — Where Companies Use This

- **Netflix (Zuul):** Every external API call enters through Zuul gateway — authenticates user, checks subscription tier (rate limit per tier), routes to right service region. If Zuul fails, entire Netflix is down; highly redundant.
- **Uber (Custom Gateway):** Uber's gateway routes requests to region-specific services, enforces rate limits per customer segment (premium gets higher limit), and injects headers with ride context (city, user tier) for downstream services.
- **Swiggy (Kong):** Food delivery gateway authenticates delivery partners and customers, enforces rate limits by endpoint (search gets higher limit than order, preventing crawlers), routes to order/restaurant/delivery services.
- **Razorpay (Custom):** Payments gateway validates API keys before any request hits backend, rate-limits by key (free tier: 100/min, paid: 1000/min), routes webhooks separately from payment API.
- **Stripe (Custom):** Stripe's gateway enforces client certificate validation for B2B APIs, routes idempotency keys to deduplication store, and maintains separate rate limit buckets per endpoint.

---

## 🧭 When to Use vs When NOT to Use

| Use API Gateway when | Do NOT use when |
|---|---|
| You have multiple internal services behind a single public URL | Your entire backend is a monolith (no routing needed) |
| You need centralized authentication/authorization | Each service has independent auth (rare, poor design) |
| You need rate limiting per client/endpoint | Rate limiting is per-service only (less scalable) |
| You have different clients (mobile, web, third-party) requiring different response formats | All clients speak the same protocol |
| You want to aggregate logs, metrics, traces in one place | Each service logs independently (hard to correlate) |

**The common mistake:** Using the API Gateway for business logic (e.g., "check if product is in stock before forwarding"). Gateways should only do cross-cutting concerns (auth, rate limiting, routing). Push business logic to services.

---

## ⚠️ Trade-offs

| | |
|---|---|
| **You gain** | Single entry point (clients don't know about internal services). Centralized auth/rate limiting (don't duplicate in every service). Request routing by URL path or method. Protocol translation (REST ↔ gRPC). Hiding internal service versions. |
| **You lose** | Added latency (request passes through gateway layer). Gateway becomes a bottleneck (must be scaled horizontally). Extra operational complexity (gateway is another service to monitor/debug). Cross-cutting logic in one place = harder to scale per-concern (if auth is slow, whole gateway slows down). |
| **Failure mode** | API Gateway crashes → ENTIRE system unreachable (even though services are healthy). Mitigation: run multiple gateway instances behind a load balancer, auto-restart on crash, set aggressive circuit breaker on gateway itself. Rate limiter state shared across gateway instances? Need distributed state (Redis) — adds dependency. Request routing misconfiguration sends traffic to wrong service. |

---

## 🔬 Interview Q&As

### Q: "What's the difference between an API Gateway and a Load Balancer?"

> Load Balancer distributes requests ACROSS replicas of the SAME service — it's about horizontal scaling of one service. API Gateway routes requests TO DIFFERENT services based on URL path or headers — it's about service composition. A Load Balancer asks: "Which Pod 1/2/3 should this request hit?" An API Gateway asks: "Should this request go to Order Service, User Service, or Catalog Service?" They sit at different layers. Load Balancer is typically Layer 4 (TCP/IP); API Gateway is Layer 7 (HTTP). ⭐ **Tier 2 — Architecture decision**

### Q: "Why can't you just expose each microservice directly to clients?"

> Direct exposure means: (1) clients hardcode multiple service URLs (brittle — if service moves, clients break), (2) each service duplicates auth/rate limiting logic (code duplication, inconsistent), (3) no central logging of all requests (hard to debug), (4) clients must know about internal services (couples clients to internals). API Gateway centralizes these concerns and acts as a facade. ⭐ **Tier 2 — Design reasoning**

### Q: "If your API Gateway is single-threaded and bottlenecked, can you just add more threads?"

> Adding threads helps, but it's a temporary fix. Real bottleneck is usually not threading but **I/O blocking on backend calls**. If each gateway thread waits for backend response (blocking I/O), you're limited to #threads × backend latency. Solution: use async I/O (Spring WebFlux with Netty, not Spring MVC with servlets). WebFlux runs on a handful of threads; each thread handles thousands of concurrent connections using async/await. ⭐ **Tier 2 — Performance debugging**

### Q: "How do you handle authentication state in a gateway? Can't attackers forge tokens?"

> Tokens are not stored; each request validates the token signature using a public key (asymmetric crypto). Attacker can't forge a token because they don't have the private key (only auth service does). JWT structure: `Header.Payload.Signature`. Gateway validates: hash(Header.Payload) with public key must equal Signature. If attacker modifies Payload, signature becomes invalid. Additional layer: store token in Redis (revocation list); if token is blacklisted, reject. ⭐ **Tier 2 — Security**

### Q: "Your gateway forwards requests to 3 different backend services. Service A is fast (10ms), Service B is slow (500ms), Service C crashes. What happens to request latency?"

> Request latency = gateway latency + slowest backend latency. If Service C crashes and gateway retries, latency spikes to timeout duration (10-30 seconds). Mitigation: (1) set per-service timeout (if C doesn't respond in 5s, fail fast), (2) circuit breaker pattern (after 5 consecutive failures, don't even try C for 30s — return 503 immediately), (3) bulkhead isolation (dedicate a thread pool per backend service; if C is slow, only its pool fills up, others unaffected). ⭐ **Tier 2 — Fault tolerance**

### Q: "How do you test a gateway in production without affecting real traffic?"

> Canary deployment + shadow traffic. Deploy new gateway version to 5% of replicas. Route 95% of traffic to stable version, 5% to new version. Monitor both; compare error rates and latency. Once confident, route 50/50, then 100% to new version. For protocol changes (adding gRPC support), use shadow traffic: copy requests to new code path (gRPC backend), but discard responses. Monitor new path's latency/errors without affecting clients. ⭐ **Tier 1 — Operations**

### Q: "You need to route requests based on user tier (premium users → fast region, free users → standard region). Where does this logic go?"

> In the gateway. Gateway checks JWT token, extracts user tier, routes based on tier. This is a cross-cutting concern (applies to all requests), so it belongs in the gateway, not in individual services. If you put it in each service, you duplicate code and risk inconsistency. ⭐ **Tier 1 — Design**

---

## 🧾 TL;DR

> "API Gateway is a facade — single entry point for all clients. It centralizes auth, rate limiting, routing, and protocol translation. Sits between clients and internal services. Distinct from load balancer: gateway routes TO different services; load balancer distributes ACROSS replicas of one service."

---

## 🔗 Related Concepts

- **`17-load-balancing-algorithms.md`** — load balancer distributes WITHIN a service; gateway routes BETWEEN services
- **`02-rate-limiting.md`** — rate limiting implemented at gateway layer
- **`13-security-pki.md`** — JWT validation happens in gateway
- **`20-circuit-breaker-resilience.md`** — gateway wraps backend calls with circuit breaker
- **`11-api-design.md`** — API contracts are what clients see through gateway

---

## 📚 Further Reading (Optional — after the note)

| Resource | What it adds | Time |
|---|---|---|
| **ByteByteGo — "API Gateway Explained"** (YouTube) | Visual walkthrough of gateway architecture, routing, and scaling patterns | ~10 min |
| **hellointerview.com — API Gateway** | Detailed coverage of gateway responsibilities and when to use vs alternatives | ~15 min read |
| **Netflix Engineering Blog — Edge Services** | Real-world patterns from Netflix's production gateway (authentication, routing, circuit breaker) | ~20 min read |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 25, 2026 | Created as Concept 24. Covered API Gateway as system-wide facade, two-diagram topology (where it sits in architecture + internal flow), distinction from load balancer, rate limiting/auth/routing patterns with Spring Cloud Gateway code example. |
