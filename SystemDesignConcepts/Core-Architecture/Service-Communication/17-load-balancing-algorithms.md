# Load Balancing Algorithms — Fundamentals

---

## 🎯 Why This Matters

You have 10 backend servers. A request arrives. Which one handles it? Pick badly (e.g., always server 1) and it gets overloaded while others sit idle. Load balancing distributes requests fairly across servers to maximize throughput and minimize latency. At SDE 3: you must know when to use Round Robin vs Least Connections, why consistent hashing matters for stateful services, and what happens when a server fails.

---

## 📖 What is Load Balancing?

**Full form:** Load Balancer / Reverse Proxy

**Simple analogy:** Imagine a help desk with multiple support agents. A receptionist at the front desk distributes incoming calls across agents based on who's free or least busy. If one agent always gets calls first, they burn out; the receptionist "balances the load" by spreading work fairly.

**Core principle:** A **load balancer is a proxy server** that sits between clients and backend servers. Instead of all requests hitting server 1 (overloading it), the load balancer directs each request to a less-busy server, ensuring even utilization across the fleet. This maximizes throughput and minimizes latency for any single client.

**Why it matters in system design:** At scale (10,000+ req/sec), a single server can't handle all traffic. Horizontal scaling means deploying 100s of servers. A load balancer distributes traffic fairly, ensuring no single server is a bottleneck, and automatically removes failing servers from the pool.

---

## 🎨 Visual — System Topology: Load Balancing in Architecture

```
INTERNET / CLIENTS
    │
    │ (many concurrent requests)
    │
    ▼
┌─────────────────────────┐
│   Load Balancer (LB)    │
│  - reverse proxy        │
│  - health checks        │
│  - distribute algorithm │
└────────┬────────────────┘
         │
    ┌────┴─────────────────────────────────────┐
    │                                          │
    ▼                                          ▼
┌──────────────┐                        ┌──────────────┐
│Server Pool   │                        │Server Pool   │
│  Instance 1  │ ...                    │  Instance N  │
│  (active=5)  │                        │  (active=2)  │
└──────────────┘                        └──────────────┘
    │                                          │
    └────────────────┬────────────────────────┘
                     │
                     ▼
            ┌──────────────────┐
            │ Shared Database  │
            │  (backend data)  │
            └──────────────────┘

SERVICE TIER

KEY INVARIANT:
   LB is stateless — it doesn't care about request content
   Each request independently routed to healthiest server
   All servers identical (or weighted by capacity)
```

---

## 🎨 Visual — Load Balancing Algorithms (Component Detail)

Imagine a bank with 5 tellers and a receptionist (load balancer). Customers arrive and line up:

- **Round Robin:** Receptionist sends the next customer to the next teller in rotation (C1 → T1, C2 → T2, C3 → T3, C4 → T4, C5 → T5, C6 → T1 again). Fair in theory, but if T1 handles complex accounts (slow), T5 handles deposits (fast), T1 gets backed up while T5 is idle.
- **Least Connections:** Receptionist looks at how many customers each teller is serving. Sends the next customer to whoever has the fewest waiting. If T1 has 3 customers, T5 has 0, send the new customer to T5. Adapts to actual load.
- **Weighted Round Robin:** T5 is a new teller in training, so we give them only 1 out of every 3 customers. T1 (experienced) gets 2 out of 3. Explicit control based on capacity.
- **Session Stickiness (Sticky Sessions):** A customer being helped with a loan application must stay with the same teller throughout. If they get reassigned, they have to re-explain everything. So the receptionist checks: "Are you already being helped?" — if yes, route back to the same teller. This is "session affinity."

**The key insight:** The receptionist doesn't care about the algorithm — they care about the outcome: all tellers equally busy, customers served fast.

---

## 🎨 Visual — Request Distribution Across Servers

```
Load Balancer (Reverse Proxy)
        │
        ├─ Algorithm selector
        │  (Round Robin / Least Connections / etc.)
        │
        ├──────────────────────────────────────────────────┐
        │                                                  │
    ┌───┴────┐  ┌────────┐  ┌────────┐  ┌────────┐  ┌────┴───┐
    │Server 1│  │Server 2│  │Server 3│  │Server 4│  │Server 5│
    │Active: 2│  │Active: 1│  │Active: 5│  │Active: 0│  │Active: 3│
    │Healthy│  │Healthy│  │Healthy│  │Healthy│  │Unhealthy
    └────────┘  └────────┘  └────────┘  └────────┘  └────────┘

ROUND ROBIN (stateless):
  Request 1 → S1 (active=3) ✗ overloaded, but RR doesn't care
  Request 2 → S2 (active=1) ✓ lighter, but RR doesn't care
  Request 3 → S3
  Request 4 → S4
  Request 5 → S5 (marked unhealthy) ✗ fails, request dropped

LEAST CONNECTIONS (dynamic):
  Request 1 → S4 (active=0, least busy)
  Request 2 → S2 (active=1, next least)
  Request 3 → S1 (active=2)
  Request 4 → S5 ✗ health check marks as down, skip
  Request 4 → S3 (next available)

KEY INVARIANT:
   Algorithm ensures: request distribution ≈ server capacity
   Health checks remove failing servers from rotation
   Sticky sessions = same request → same server (for stateful apps)
```

---

## ⚙️ How It Actually Works

**Four common algorithms:**

### Algorithm 1: Round Robin (Stateless)

**Steps:**
1. Maintain a counter `i = 0` for the current server index.
2. On each request, assign it to `servers[i]`.
3. Increment `i` (modulo server count) for the next request.
4. Ignore server load and health checks (naive version).

```java
public class RoundRobinLoadBalancer {
    private final List<Server> servers;
    private int currentIndex = 0;

    public RoundRobinLoadBalancer(List<Server> servers) {
        this.servers = servers;
    }

    public Server selectServer() {
        // Step 2 — get current server
        Server selected = servers.get(currentIndex);

        // Step 3 — rotate index
        currentIndex = (currentIndex + 1) % servers.size();

        return selected;
    }
}
```

**When to use:** Stateless services where all servers have equal capacity. Simple, predictable, low overhead.

---

### Algorithm 2: Least Connections

**Steps:**
1. On each request, scan all servers and find the one with the fewest active connections.
2. Route the request to that server.
3. Increment the active connection count on that server.
4. Decrement when the request completes.

```java
public class LeastConnectionsLoadBalancer {
    private final List<Server> servers;

    public LeastConnectionsLoadBalancer(List<Server> servers) {
        this.servers = servers;
    }

    public Server selectServer() {
        // Step 1 — find server with minimum active connections
        Server selected = servers.stream()
            .filter(Server::isHealthy)           // Skip unhealthy
            .min(Comparator.comparingInt(Server::getActiveConnections))
            .orElseThrow(() -> new RuntimeException("No healthy servers"));

        // Step 3 — increment active count
        selected.incrementActive();

        return selected;
    }

    // Step 4 — call this when request completes
    public void releaseServer(Server server) {
        server.decrementActive();
    }
}

class Server {
    private final String name;
    private int activeConnections = 0;
    private boolean healthy = true;

    public synchronized void incrementActive() {
        activeConnections++;
    }

    public synchronized void decrementActive() {
        activeConnections--;
    }

    public int getActiveConnections() {
        return activeConnections;
    }

    public boolean isHealthy() {
        return healthy;
    }

    public void setHealthy(boolean status) {
        this.healthy = status;
    }
}
```

**When to use:** Long-lived connections (e.g., WebSocket, gRPC), varying server capacity, or requests with different execution times.

---

### Algorithm 3: Weighted Round Robin

**Steps:**
1. Assign each server a weight (T1 = 2, T2 = 1, T3 = 1). Total = 4.
2. Create a rotation list: [T1, T1, T2, T3].
3. Apply round robin on the weighted list.

```java
public class WeightedRoundRobinLoadBalancer {
    private final List<Server> weightedServers;
    private int currentIndex = 0;

    public WeightedRoundRobinLoadBalancer(Map<Server, Integer> serverWeights) {
        // Step 1 & 2 — expand servers by weight
        this.weightedServers = new ArrayList<>();
        serverWeights.forEach((server, weight) -> {
            for (int i = 0; i < weight; i++) {
                weightedServers.add(server);
            }
        });
    }

    public Server selectServer() {
        // Step 3 — round robin on weighted list
        Server selected = weightedServers.get(currentIndex);
        currentIndex = (currentIndex + 1) % weightedServers.size();
        return selected;
    }
}

// Usage:
Map<Server, Integer> weights = new LinkedHashMap<>();
weights.put(server1, 2);  // 2x capacity
weights.put(server2, 1);  // 1x capacity
weights.put(server3, 1);
LoadBalancer lb = new WeightedRoundRobinLoadBalancer(weights);
```

**When to use:** Servers with different hardware (e.g., one beefy machine, several smaller ones). Predictable, low overhead.

---

### Algorithm 4: Sticky Sessions (Session Affinity)

**Steps:**
1. Client establishes a session (e.g., logs in, gets `session_id = abc123`).
2. On subsequent requests, client sends the session ID in a cookie.
3. Load balancer hashes the session ID to the same server.
4. If that server goes down, rehash to a different server (loss of session state).

```java
public class StickySessionLoadBalancer {
    private final List<Server> servers;

    public StickySessionLoadBalancer(List<Server> servers) {
        this.servers = servers;
    }

    public Server selectServer(String sessionId) {
        // Step 3 — hash session ID to consistent server
        int hash = sessionId.hashCode() % servers.size();
        // Handle negative hash
        if (hash < 0) {
            hash += servers.size();
        }

        Server selected = servers.get(hash);
        // If selected server is down, linear search for next healthy
        if (!selected.isHealthy()) {
            // Step 4 — rehash to another server
            for (int i = 1; i < servers.size(); i++) {
                Server fallback = servers.get((hash + i) % servers.size());
                if (fallback.isHealthy()) {
                    return fallback;
                }
            }
        }

        return selected;
    }
}
```

**When to use:** Stateful services (e.g., user shopping cart stored in-memory on the server), WebSocket connections, or expensive session initialization.

---

**What is Consistent Hashing, and why does it fit here?**

For sticky sessions at scale (thousands of servers), simple hash-modulo fails: if one server crashes, `hash % servers.size()` changes, all sessions rehash. Consistent hashing uses a ring: servers are placed on the ring, requests hash to the nearest server. If one server disappears, only its fraction of sessions rehash — not all. Reference: **`05-consistent-hashing.md`** for full depth.

---

## 🏢 Real World — Where Companies Use This

- **Netflix (Zuul load balancer):** Routes millions of requests across AWS regions. Uses weighted round-robin (some regions have higher capacity). Health checks remove degraded instances in <5 seconds.
- **Uber (Ringpop):** Consistent hashing for stateful services (driver location cache, trip state). Each driver_id hashes to a specific node, ensuring location updates land on the same server.
- **Swiggy:** Least connections for order-processing services — orders are CPU-intensive, some servers slower. LB adapts by sending more orders to servers with fewer active tasks.
- **AWS ELB (Elastic Load Balancer):** Round robin by default (simplest), but supports stickiness via cookies. E-commerce sites enable sticky sessions during checkout (shopping cart in app memory).

---

## 🧭 When to Use vs When NOT to Use

| Use this when | Do NOT use when |
|---|---|
| Multiple backend servers and you need fair request distribution | Single server (no balancing needed) |
| Servers have different capacities or request execution times | All servers identical and requests uniform (round robin is fine) |
| You want to hide a server failure gracefully | Requests are short-lived stateless queries (failure doesn't matter as much) |
| You need sticky sessions for stateful apps | Servers are stateless and replicated (session affinity adds unnecessary overhead) |

**The common mistake:** Assuming round robin is always better than least connections. Round robin is deterministic but blind to server load. Under unequal load (some queries are slow), least connections adapts faster.

---

## ⚠️ Trade-offs

| | |
|---|---|
| **You gain** | Distributes load evenly (or by weight/connections). Single server failure removes only that server's capacity, not all. Hides slowness in the cluster. |
| **You lose** | Adds latency (LB must route every request). If session affinity is enabled, sessions can't migrate (limits flexibility). Rebalancing on server failures can cause brief spike in latency as in-flight requests fail over. |
| **Failure mode** | If the load balancer itself fails, all traffic is lost. Single point of failure. Mitigation: use multiple LBs with failover (active-passive or active-active). |

---

## 🔬 Interview Q&As

### Q: "We have 5 servers and 1000 requests/sec. How do you choose a load balancing algorithm?"

> First ask: are the servers and requests identical (stateless, uniform execution time)? If yes, round robin is simple and sufficient. If no (varied server capacity or request duration), use least connections — it adapts to actual load. If you have expensive session setup (e.g., user authentication), use sticky sessions to amortize the cost. For 1000 req/sec across 5 servers, that's 200 req/sec per server. Check what each server can sustain. If one is a beast and others are wimpy, weight the beast 3x and others 1x. ⭐ **Tier 2 — design choice**

### Q: "A server crashes. What happens with round robin vs sticky sessions?"

> **Round robin:** Requests immediately redirect to healthy servers. Stateless requests continue unaffected. Small spike in latency as the LB failover kicks in. **Sticky sessions:** User sessions on the crashed server are lost (shopping cart, login state, etc.). Sessions on other servers continue fine. Recovery: user logs back in and gets a new session. This is why sticky sessions trade availability for performance — they're unsuitable for mission-critical apps. ⭐ **Tier 2 — failure mode**

### Q: "How does load balancing interact with DNS?"

> DNS round robin (multiple A records pointing to different IPs) is not a load balancer — clients cache the DNS result and may not rebalance on failures. A reverse proxy load balancer (nginx, HAProxy) is better because it inspects health in real-time. However, for geographic distribution, you might use DNS (Route53) to direct users to the nearest region's load balancer, then that LB distributes within the region. Layered: DNS (region level) → LB (server level). ⭐ **Tier 2 — distributed systems**

### Q: "Design a load balancer that handles a 10x traffic spike gracefully."

> Use two strategies: (1) **Autoscaling:** trigger new servers to spin up when CPU > 70%. (2) **Graceful degradation:** if no new servers are available and all existing servers are at capacity, start rejecting requests with HTTP 503 (Service Unavailable) rather than queuing indefinitely. This signals the client/browser to back off and retry. The load balancer must track queue depth; if queue > threshold, reject. This prevents cascading failure (all requests hanging indefinitely, eating memory). ⭐ **Tier 2 — system design**

### Q: "How does consistent hashing differ from round robin?"

> Round robin assigns by request order (deterministic, simple). Consistent hashing assigns by content (request ID or session ID) to the same server. If you add/remove a server, round robin continues; consistent hashing rehashes some requests but not all. Consistent hashing is used for stateful services where you need the same request to land on the same server to preserve cache/state. Round robin is stateless and faster (no hashing). ⭐ **Tier 2 — conceptual**

### Q: "We use sticky sessions, but sessions are getting lost during deploy. Why?"

> When you deploy new code, servers shut down one by one. Sessions on those servers are lost (they're in-memory). The load balancer tries to rehash to healthy servers, but if the LB doesn't know the session state, recovery is partial. Solution: drain sessions gracefully — before shutting down a server, stop accepting new connections (mark it "draining"), let in-flight requests finish, then shut down. Or use a distributed session store (Redis) instead of in-memory, so sessions survive server restarts. ⭐ **Tier 2 — operational**

---

## 🧾 TL;DR

> "Load balancing distributes requests fairly across servers to maximize throughput. Round robin is simple for stateless services; least connections adapts to actual load. Use sticky sessions for stateful apps, but accept that you lose failover resilience. Consistent hashing preserves session affinity even when servers change."

---

## 🔗 Related Concepts

- **`05-consistent-hashing.md`** — consistent hashing is the advanced cousin of round robin, used for stateful scaling
- **`18-service-discovery-dns.md`** — load balancers must discover healthy servers; DNS + health checks interact here
- **`20-circuit-breaker-resilience.md`** — load balancer often pairs with circuit breakers to fail fast on degraded servers
- **`16-connection-pooling-db-performance.md`** — load balancing the database separately (read replicas) follows the same principles

---

## 📚 Further Reading (Optional — after the note)

| Resource | What it adds | Time |
|---|---|---|
| **ByteByteGo — "Load Balancing Strategies"** (YouTube) | Visual walkthrough of round robin, least connections, weighted algorithms with animations | ~8 min |
| **Arpit Bhayani — "Consistent Hashing"** (YouTube) | Deep dive on consistent hashing for stateful load balancing (mentions round robin vs hashing trade-offs) | ~18 min |
| **System Design Primer — "Load Balancing"** (GitHub) | Reference guide covering algorithms, health checks, sticky sessions, geographic distribution | ~15 min read |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 25, 2026 | Created as Concept 17. Added four algorithms (round robin, least connections, weighted RR, sticky sessions) with code examples and mental model. |
