# Service Discovery & DNS — Fundamentals

---

## 🎯 Why This Matters

In a monolith, your app talks to one database at localhost:5432. In a microservices world, you have 50 services, each with 10 replicas across 3 regions. How does service A find service B? Which replica? Is service B healthy? Service discovery answers this: it's a dynamic directory of services + health status. At SDE 3: you need to know DNS, service registries (Consul, Zookeeper), and how they differ.

---

## 📖 What is Service Discovery?

**Full form:** Service Registry / Service Discovery Mechanism

**Simple analogy:** Think of a phone directory for your microservices. In the old days, you printed a phonebook and distributed it; if someone's number changed, everyone's copy became stale. Service discovery is a **dynamic directory** — when a service starts, it registers itself; when it crashes, it's automatically removed. When service A needs service B, it looks in the directory: "Where is service B right now? Which instances are healthy?"

**Core principle:** Instead of hardcoding IP addresses (`service-b.example.com = 10.0.0.5`), services register and deregister themselves dynamically. A central registry (DNS, Consul, Zookeeper) maintains the authoritative list of who's alive. Clients query the registry to find healthy instances; failures are automatically detected and removed.

**Why it matters in system design:** At scale with 100s of services and 1000s of instances, manual IP management is impossible. Service discovery automation enables self-healing, zero-downtime deployments, and transparent failover.

---

## 📖 Terminology Table

| Term | Plain-English Meaning | Example |
|------|----------------------|---------|
| **Service Registry** | central database mapping service name → healthy instance IPs; the phone directory for microservices | Consul: `order-service` → `[10.0.0.2:8080, 10.0.0.3:8080]` |
| **Health Check** | periodic probe to verify a service instance is alive; failing instances are removed from the registry | HTTP `GET /health` every 10s; 3 consecutive timeouts → deregister instance |
| **Dynamic Deregistration** | automatic removal of crashed or stopped instances — no manual cleanup needed | service crashes → misses 3 heartbeats → registry removes its IP |
| **Client-Side Discovery** | the calling service queries the registry itself and picks an instance to call | Order Service queries Consul, load-balances across 3 User Service IPs directly |
| **Server-Side Discovery** | a proxy (load balancer) queries the registry on behalf of the caller; caller hits one stable URL | AWS ALB + ECS: caller sends to `order-service.internal`, LB resolves dynamically |
| **DNS SRV Record** | DNS record that includes port as well as IP — enables service-level routing without extra registry | `_order._tcp.example.com → 10.0.0.2:8080` |
| **Consul / Eureka / Zookeeper** | popular service registries with health-check, watch, and leader-election APIs | Consul: K8s-adjacent stacks; Eureka: Spring Cloud default |
| **Quorum** | registry nodes require majority agreement before updating state; prevents split-brain during network partition | 3-node Consul cluster: 2 nodes must agree before any service entry is added/removed |

---

## 🎨 Visual — System Topology: Service Discovery in Architecture

```
┌────────────────────────────────────────────────────────┐
│                MICROSERVICES WORLD                     │
│                                                        │
│  ┌──────────┐   ┌──────────┐   ┌──────────┐          │
│  │Service A │   │Service B │   │Service C │          │
│  │(10 inst.)│   │(5 inst.) │   │(15 inst.)│          │
│  └──────────┘   └──────────┘   └──────────┘          │
│       │               │               │               │
│       └───────────────┼───────────────┘               │
│                       │                                │
│                       ▼                                │
│      ┌────────────────────────────────┐               │
│      │  Service Registry / DNS        │               │
│      │  - Consul / Zookeeper / Eureka │               │
│      │  - Maintains: service → IPs    │               │
│      │  - Health checks every 10s     │               │
│      │  - Removes unhealthy instances │               │
│      └────────────────────────────────┘               │
│              ▲                    │                    │
│              │ register/heartbeat │ query              │
│              │                    ▼                    │
│      ┌───────────────┐  ┌──────────────────┐         │
│      │ Service A     │  │ Service B        │         │
│      │ Instance 1    │  │ Instances:       │         │
│      │ :9001         │  │ - 10.0.0.2:9002  │         │
│      └───────────────┘  │ - 10.0.0.3:9002  │         │
│                         │ - 10.0.0.4:9002  │         │
│                         └──────────────────┘         │
│                                                      │
└────────────────────────────────────────────────────────┘

KEY INVARIANT:
   Registry = source of truth for service locations
   Clients query → receive healthy instance list
   Failed instances auto-removed (health check failure)
   New instances auto-added (registration)
```

---

## 🎨 Visual — Service Discovery Flow & Interaction (Component Detail)

Imagine a hospital with 50 departments spread across 5 buildings. A patient needs cardiology. There are three cardiologists, located in different buildings, working different schedules. Instead of calling each building to find Dr. Ahmed, the patient calls one central switchboard:

- **Patient (service A):** "Where is the Cardiology department?"
- **Switchboard (service registry):** "Cardiology is at Building 2 Room 410. Dr. Ahmed is on duty right now. Here's his direct number: 5551234."
- **Patient:** Calls Dr. Ahmed directly.

But here's the twist: if Dr. Ahmed goes on break, the switchboard updates its directory. Next patient gets Dr. Patel's number instead. And if a new cardiologist, Dr. James, joins tomorrow, the switchboard adds him. No one has to manually update a phonebook.

**The key insight:** Services don't hardcode other services' locations. They ask a central registry: "Who is available now?" The registry keeps an up-to-date list and handles failures transparently.

---

## 🎨 Visual — Service Discovery Flow

```
Service A (client) wants to call Service B

1. SERVICE REGISTRATION (happens at startup):
   ┌─────────────────┐
   │ Service B       │
   │ Instance 1:9001 │ ──→ [Service Registry]
   │ Instance 2:9002 │     (Consul, Zookeeper,
   │ Instance 3:9003 │      or DNS)
   └─────────────────┘
        Health: checks every 10s

2. SERVICE DISCOVERY (when Service A needs Service B):
   ┌────────────────────┐
   │ Service A (client) │
   └────────┬───────────┘
            │ Query: "Give me healthy Service B"
            ↓
       [Service Registry]
       (returns list with health status)
            │
            ├─ Instance 1:9001 ✅ (healthy)
            ├─ Instance 2:9002 ❌ (unhealthy, CPU high)
            └─ Instance 3:9003 ✅ (healthy)
            │
            ↓
   ┌────────────────────────────────────┐
   │ Service A picks one (LB algo)      │
   │ Load Balancer: choose Instance 1   │
   └────────┬─────────────────────────┘
            │ HTTP request to 10.0.0.2:9001
            ↓
       ┌─────────────────────┐
       │ Service B Instance 1 │
       └─────────────────────┘

3. FAILURE & RE-DISCOVERY:
   Instance 1 crashes ❌
   Health check fails → Registry removes it
   Next request from Service A:
   - Query registry → only Instances 2 & 3 available
   - Service A re-routes to Instance 3

KEY INVARIANT:
   Service A never hardcodes Service B's address
   Registry maintains authoritative view of who's alive
   Client-side caching + health checks prevent stale routing
```

---

## ⚙️ How It Actually Works

**Three common patterns:**

### Pattern 1: DNS (Simplest)

**Steps:**
1. Service B publishes multiple A records (one per instance).
2. Service A queries DNS for `service-b.internal.example.com`.
3. DNS returns all healthy IPs (via SRV records or A record round-robin).
4. Service A caches the result and picks one (or load balancer picks).
5. On failure, client retries and refreshes DNS.

```java
// Service B registration (happens at container startup)
// DNS entry: service-b.internal.example.com
// A records:
//   10.0.0.1 (instance 1)
//   10.0.0.2 (instance 2)
//   10.0.0.3 (instance 3)

// Service A (client-side discovery)
public class ServiceBClient {
    private String serviceUrl;

    public ServiceBClient(String hostname) throws UnknownHostException {
        // Step 2 — DNS lookup
        InetAddress[] addresses = InetAddress.getAllByName(hostname);

        // Step 4 — pick one (round-robin or LB)
        Random rand = new Random();
        InetAddress chosen = addresses[rand.nextInt(addresses.length)];
        this.serviceUrl = "http://" + chosen.getHostAddress() + ":9001";
    }

    public String callServiceB(String request) {
        // Step 5 — make request; on timeout, client retries
        try {
            return doHttpRequest(serviceUrl + "/api/endpoint", request);
        } catch (IOException e) {
            // Retry with DNS refresh (next attempt gets fresh list)
            throw new RuntimeException("Service B unavailable", e);
        }
    }

    private String doHttpRequest(String url, String body) throws IOException {
        // Actual HTTP call
        // Simplified for example
        return "response";
    }
}
```

**Pros:** Simple, works with standard DNS. **Cons:** DNS caching hides failures (stale results); no active health checks; doesn't support service metadata (e.g., "prefer this instance if in same AZ").

---

### Pattern 2: Client-Side Service Registry (Consul, Eureka)

**Steps:**
1. Service B registers itself with Consul (HTTP API): `POST /v1/agent/service/register`.
2. Service B sends health checks periodically (e.g., HTTP GET `/health`).
3. Service A queries Consul for Service B's healthy instances (HTTP API).
4. Consul returns instances with health status.
5. Service A caches and updates periodically (every 30s).

```java
// Service B — registration (at startup, using Spring Cloud Consul)
@SpringBootApplication
@EnableDiscoveryClient
public class ServiceBApplication {
    public static void main(String[] args) {
        SpringApplication.run(ServiceBApplication.class, args);
        // Spring auto-registers with Consul
        // Service name = "service-b"
        // Port = 9001
        // Health check = /actuator/health (Spring default)
    }

    @GetMapping("/actuator/health")
    public Map<String, String> health() {
        // Step 2 — Consul polls this
        return Map.of("status", "UP");
    }
}

// Service A (client-side discovery using Spring Cloud Consul)
@Service
public class ServiceBClient {
    @Autowired
    private DiscoveryClient discoveryClient;

    public String callServiceB(String request) {
        // Step 3 — query Consul for healthy instances
        List<ServiceInstance> instances = discoveryClient.getInstances("service-b");

        if (instances.isEmpty()) {
            throw new RuntimeException("Service B has no healthy instances");
        }

        // Step 4 — pick one (could use load balancer)
        ServiceInstance chosen = instances.get(0);
        String url = chosen.getUri() + "/api/endpoint";

        // Step 5 — cache and use
        return restTemplate.getForObject(url, String.class);
    }
}
```

**Pros:** Active health checks, rich metadata (tags, weights), works across multiple datacenters. **Cons:** Requires service registry infrastructure; additional operational complexity.

---

### Pattern 3: Server-Side Discovery (API Gateway / Load Balancer)

**Steps:**
1. Service B registers with service registry (Consul, DNS, or k8s API server).
2. API Gateway polls the registry periodically (every 10s).
3. API Gateway maintains an internal routing table: Service B → [IP1, IP2, IP3].
4. Client calls API Gateway with a logical service name (e.g., `GET /service-b/api/endpoint`).
5. API Gateway routes to a healthy backend instance.

```java
// Service B — similar registration as Pattern 2

// API Gateway (routing table maintained)
@RestController
public class APIGateway {
    private final ServiceRegistry serviceRegistry;
    private final LoadBalancer loadBalancer;

    // Periodically refresh routing table (every 10s)
    @Scheduled(fixedDelay = 10000)
    public void refreshRoutingTable() {
        // Step 2 — poll service registry
        Map<String, List<ServiceInstance>> services = 
            serviceRegistry.getAllHealthyInstances();
        
        // Step 3 — update internal routing table
        for (String serviceName : services.keySet()) {
            routingTable.put(serviceName, services.get(serviceName));
        }
    }

    // Step 4 — client hits API Gateway
    @RequestMapping("/service-b/**")
    public ResponseEntity<String> routeToServiceB(HttpServletRequest request) {
        // Step 3 & 5 — lookup and route
        List<ServiceInstance> instances = routingTable.get("service-b");
        ServiceInstance chosen = loadBalancer.selectInstance(instances);

        String targetUrl = chosen.getUri() + request.getRequestURI();
        return restTemplate.exchange(targetUrl, HttpMethod.GET, null, String.class).getBody();
    }
}
```

**Pros:** Single point of control, centralized health checks, gateway can apply policies (rate limiting, auth). **Cons:** Gateway becomes a bottleneck; adds latency.

---

**What is Consul (or Zookeeper), and why does it fit here?**

Consul is a distributed service registry + configuration store. Services self-register with health checks. Unlike DNS, Consul is aware of service health in real-time and supports advanced queries (e.g., "give me services tagged as 'database' in zone 'us-west'"). In an interview, if asked: *"Consul provides server-side service discovery with health checks, metadata support, and multi-datacenter awareness — better than plain DNS for complex microservices."*

---

## 🏢 Real World — Where Companies Use This

- **Uber (service mesh):** Uses Consul for service discovery across 5 datacenters. When a driver app instance crashes, Consul removes it within 5 seconds; new instances get routed around it.
- **Netflix (Eureka):** Client-side service discovery. Each service keeps a local cache of all other services. On failure, cache is used until registry is refreshed (eventual consistency).
- **Kubernetes (built-in):** Services are DNS names (e.g., `my-service.default.svc.cluster.local`). kube-dns automatically updates DNS records when pods are added/removed. Load balancing via iptables or Envoy proxy.
- **AWS ECS (managed):** Services registered with AWS CloudMap (service registry). ECS automatically deregisters unhealthy tasks; load balancer only sends traffic to registered instances.

---

## 🧭 When to Use vs When NOT to Use

| Use this when | Do NOT use when |
|---|---|
| Multiple instances of the same service (need to discover which one is healthy) | Single instance or monolith (no discovery needed) |
| Services dynamically scale up/down (instances created/destroyed frequently) | Static infrastructure (hardcoded IPs are fine, e.g., batch jobs) |
| You need health-aware routing (skip failing instances automatically) | Any instance failure is terminal anyway (discovery doesn't help) |
| Cross-datacenter or multi-region deployment | Single-region, tightly coupled services |

**The common mistake:** Using DNS for stateful services without sticky sessions. Service A resolves `service-b.internal` and gets 3 IPs; caches them; makes 10 requests. If Service B instances store state in-memory (sessions, caches), requests must go to the same instance. Without sticky sessions, state is lost.

---

## ⚠️ Trade-offs

| | |
|---|---|
| **You gain** | Automatic failover (no manual intervention). Services find each other dynamically. Easy horizontal scaling — add a new instance, it auto-registers. |
| **You lose** | Added latency (registry lookup, or DNS query). Service registry itself is a single point of failure (mitigation: replicate the registry). DNS caching can hide failures temporarily. |
| **Failure mode** | Service registry crashes → all new discoveries fail. Old cached entries work temporarily, then requests timeout as services crash. Mitigation: use a replicated registry with quorum (Consul has 3 replicas). |

---

## 🔬 Interview Q&As

### Q: "How is service discovery different from load balancing?"

> Service discovery finds which instances exist and are healthy. Load balancing picks one of those instances for a request. Discovery = "who is available?", LB = "which one gets this request?". In a monolith, neither is needed (one DB). In microservices, service discovery is essential; load balancing is applied at the load balancer or service-mesh level. ⭐ **Tier 2 — conceptual**

### Q: "We switched from DNS to Consul. Now services are slow. Why?"

> DNS is a cache-heavy protocol (clients cache results for minutes). Consul has faster failure detection (seconds) but adds latency (HTTP lookup + deserialization). If you're making this trade-off, cache Consul results client-side for 10–30 seconds, not seconds. Also, ensure Consul cluster is performant (3 replicas in same region, not across continents). Check if the latency is from registry lookup or from the network (Consul and services in same AZ helps). ⭐ **Tier 2 — operational**

### Q: "Design service discovery for a payment service that spans 3 regions."

> Use a multi-region Consul cluster: 3 replicas in each region, replicated across regions. Regions are independent (eventual consistency, not strong consistency — fine for this use case). Within each region, clients query the local Consul replica (low latency). Payment Service instances register health checks that are region-specific (e.g., "is this server's connection to the payment processor healthy?"). If regional payment processor is down, instances mark themselves unhealthy; Consul removes them; requests route to other regions. ⭐ **Tier 2 — system design**

### Q: "How does Kubernetes service discovery work?"

> Kubernetes uses DNS + iptables. Each Service gets a DNS name (e.g., `api.default.svc.cluster.local`). kube-dns updates DNS records as pods are added/removed. When a pod queries the Service name, kube-dns returns the virtual IP (ClusterIP). iptables on each node routes traffic from that VIP to an actual pod IP. This is server-side discovery (node's kernel does the routing). New Kubernetes uses Envoy proxy for more sophisticated routing (observability, retries, circuit breaking). ⭐ **Tier 2 — k8s specific**

### Q: "A service instance crashes. How long until traffic stops going to it?"

> Depends on your discovery method: **DNS (client-side):** Until the client's DNS cache expires (could be minutes). **Consul (client-side polling every 30s):** Within 30 seconds of the health check failure. **Health checks (active from registry):** Depends on check frequency; could be 5–10 seconds. **Kubernetes:** Within 10 seconds (endpoint controller removes the pod from the service). Faster = more operational overhead. For critical services, prioritize fast failure detection (5–10s). ⭐ **Tier 2 — trade-offs**

### Q: "What if the service registry itself becomes unavailable?"

> Clients' cached copies of the registry continue to work temporarily (minutes to hours, depending on TTL). But new instances can't register, failing instances can't be removed, and new clients get no service info. Mitigation: replicate the registry (Consul with 3 replicas across 3 AZs). If 1 replica is down, the other 2 maintain quorum. If 2 are down, the cluster is read-only (can't register, but existing registrations work). If all 3 are down, rely on cached entries and manual failover. ⭐ **Tier 2 — failure mode**

---

## 🧾 TL;DR

> "Service discovery is a dynamic directory of healthy service instances. Use DNS for simplicity, Consul/Eureka for advanced features and faster failure detection, or server-side discovery (k8s/API Gateway) for centralized control. Cache discovery results locally to avoid every request hitting the registry."

---

## 🔗 Related Concepts

- **`17-load-balancing-algorithms.md`** — service discovery provides the list of instances; load balancer picks one
- **`16-connection-pooling-db-performance.md`** — service discovery for databases (which replica to connect to?)
- **`20-circuit-breaker-resilience.md`** — circuit breaker patterns work on top of discovered instances (fail fast if an instance is degraded)
- **`02-rate-limiting.md`** — service discovery often paired with per-instance rate limiting

---

## 📚 Further Reading (Optional — after the note)

| Resource | What it adds | Time |
|---|---|---|
| **Arpit Bhayani — "Service Discovery"** (YouTube) | Compares DNS, Consul, Kubernetes; when to use each; failure scenarios | ~15 min |
| **Consul Documentation — "Service Discovery"** (HashiCorp) | Official patterns, health checks, multi-region setup | ~20 min reference |
| **ByteByteGo — "How Service Discovery Works in Microservices"** (YouTube) | Visual walkthrough of client-side vs server-side discovery | ~8 min |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 25, 2026 | Created as Concept 18. Added three patterns (DNS, client-side registry, server-side discovery) with code examples. |
