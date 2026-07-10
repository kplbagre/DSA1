# Availability

> **Standard followed:** `SystemDesignConcepts/Interview-Resources/Metadata/notes-standards.md`

---

## 📖 What is Availability?

**Full form:** Availability (no acronym — also expressed as "uptime" or "nines" in SLA contracts)

**Simple analogy:** A hospital emergency room. A hospital that is "open 99.9% of the time" is closed ~9 hours per year — no big deal for most wards. But an emergency room? Nine hours of unexpected closure per year means people die in the parking lot. The availability target must match the cost of being down.

**Core principle:** Availability is the percentage of time a system successfully serves requests over a measurement window. It is achieved not by making individual components perfect, but by structuring the system so that no single component's failure makes the whole system unavailable — through redundancy, health monitoring, and automated failover.

**Why it matters in system design:** "What's your availability target?" is a gateway question. The answer determines how many nines of redundancy you need, whether you need multi-AZ or multi-region, and whether you choose synchronous or asynchronous replication — three choices that each have significant cost and consistency trade-offs.

---

## 🎯 Why This Matters

Availability targets drive architecture. 99% is achievable with a single well-configured instance. 99.99% requires multi-AZ redundancy + automated failover. 99.999% requires multi-region, zero-downtime deployment, and chaos testing. Getting the nines wrong is expensive in both directions: under-engineer for 99.999% and you page at 3am; over-engineer for 99% and you've wasted months of engineering effort.

**Round:** System design, deep dives, and "describe your system's reliability posture" behavioral questions.

**Why senior engineers own this:** They know the math (serial multiplication makes microservices fragile), the cost (each nine is disproportionately expensive), and the trade-off (higher availability requires weaker consistency guarantees or higher write latency).

---

## 🧠 The Mental Model

Think of the electrical grid in a city. The grid delivers 99.999% uptime — not because every power line is perfect, but because:

1. Power flows through multiple parallel paths — if one line fails, the grid reroutes
2. Failures are detected within seconds — smart meters and sensors
3. Backup generators exist at hospitals, data centers, and critical infrastructure
4. Load is distributed across substations — no single substation failure kills the whole city

A single-server application is like a house with one power line — the line goes down, the house is dark. Adding redundancy is like installing a backup generator (active-passive) or connecting to two independent power feeds (active-active). The generator adds availability; the second feed eliminates the SPOF.

**The key insight is:** Availability is not a property of a single component — it is an emergent property of the system's redundancy topology. And redundant components in *series* multiply downtime, while redundant components in *parallel* reduce it.

---

## 🎨 Visual — System Topology with Failover Modes + Availability Math

```
FULL SYSTEM TOPOLOGY — Active-Passive vs Active-Active:

ACTIVE-PASSIVE (Primary-Standby):
                                    ┌──────────┐
                                    │ Primary  │ ◀── All traffic goes here
   Client ──▶ LB (health check) ──▶│  Pod 1   │
                   │                └──────────┘
                   │ (primary fails         │ replication
                   │  → promote standby)    ▼
                   │                ┌──────────┐
                   └──────────────▶ │ Standby  │ ◀── Warm, ready to promote
                                    │  Pod 2   │     (30-60 sec failover time)
                                    └──────────┘

ACTIVE-ACTIVE (Both serve traffic):

   Client ──▶ LB ──▶ ┌──────────┐    All pods receive traffic.
                      │  Pod 1   │    If one fails, LB redistributes.
                      └──────────┘    Failover time ≈ 0 (next request
                   ──▶ ┌──────────┐   goes to surviving pod).
                      │  Pod 2   │    BUT: writes need conflict resolution
                      └──────────┘    or you need to ensure the same write
                   ──▶ ┌──────────┐   doesn't hit two pods simultaneously.
                      │  Pod 3   │
                      └──────────┘


COMPONENT DETAIL — Availability Math:

  THE NINES TABLE:
  ┌────────────┬─────────────────────┬─────────────────────┐
  │ Uptime SLA │ Max downtime / year │ Max downtime / month│
  ├────────────┼─────────────────────┼─────────────────────┤
  │  99%       │  87.6 hours         │  7.3 hours          │
  │  99.9%     │   8.76 hours        │  43.8 minutes       │
  │  99.99%    │  52.6 minutes       │   4.4 minutes       │
  │  99.999%   │   5.26 minutes      │  26 seconds         │
  └────────────┴─────────────────────┴─────────────────────┘

  SERIAL DEPENDENCY (microservices hurt availability):
  3 services each at 99.9%:
    A = 0.999 × B = 0.999 × C = 0.999
    Total = 0.999³ = 0.997 ≈ 99.7%  ← worse than any single service
  
  This is why microservices need circuit breakers + graceful degradation.

  PARALLEL REDUNDANCY (redundancy improves availability):
  2 instances each at 99% (both must fail to cause outage):
    Total = 1 - (1 - 0.99)² = 1 - 0.0001 = 99.99%
  
  Formula: A_redundant = 1 - (1 - A_single)^n

KEY INVARIANT:
   Dependencies in SERIES multiply your downtime risk.
   Dependencies in PARALLEL (redundancy) exponentially reduce it.
   A chain of microservices with no circuit breakers is inherently
   less available than a monolith.
```

---

## ⚙️ How It Actually Works

**Steps:**

1. **Set the target** — what nines? What is the business cost of one minute of downtime? (99.9% ≈ 9hrs/year vs 99.99% ≈ 52min/year; each nine is disproportionately expensive to achieve)
2. **Map all dependencies in series** — for each service chain, multiply the availability; the result shows you where to add redundancy or circuit breakers
3. **Add redundancy at each critical layer** — at minimum: load balancer (VIP/floating IP), service pods (multiple instances), and database (primary + replica)
4. **Automate failure detection** — health checks (liveness = is the process alive? readiness = is the process ready to accept traffic?) must fire fast and correctly
5. **Automate failover** — don't rely on human response; K8s probe + restart, RDS Multi-AZ promotion, Route53 health check DNS failover

```java
// Health check endpoint — differentiates liveness from readiness
// Kubernetes liveness probe: is the pod alive? (restart if not)
// Kubernetes readiness probe: is the pod ready for traffic? (remove from LB if not)
@RestController
public class HealthController {

    private final DatabaseHealthCheck dbHealthCheck;
    private final CacheHealthCheck cacheHealthCheck;

    // Liveness: "is the process stuck in a deadlock or OOM?"
    // Return 200 as long as the JVM is responsive
    @GetMapping("/health/live")
    public ResponseEntity<String> liveness() {
        return ResponseEntity.ok("alive");
    }

    // Readiness: "can this pod serve traffic right now?"
    // DB down = pod removed from LB rotation (not restarted)
    @GetMapping("/health/ready")
    public ResponseEntity<HealthStatus> readiness() {
        boolean dbHealthy = dbHealthCheck.isHealthy();
        boolean cacheHealthy = cacheHealthCheck.isHealthy();
        if (!dbHealthy) {
            // Remove from LB — cannot serve requests without DB
            return ResponseEntity.status(503)
                .body(HealthStatus.of("DOWN", "database unreachable"));
        }
        // Cache unhealthy is degraded but still serviceable (fall through to DB)
        HealthLevel level = cacheHealthy ? HealthLevel.UP : HealthLevel.DEGRADED;
        return ResponseEntity.ok(HealthStatus.of(level.name(), "ready"));
    }
}
```

### What is liveness vs readiness, and why does the distinction matter?

**Liveness probe** (is the process alive?) — if this fails, K8s restarts the pod. Used for detecting deadlocks or memory corruption. Should be extremely cheap (no DB call).

**Readiness probe** (is the pod ready for traffic?) — if this fails, K8s removes the pod from the Service's endpoint list (LB stops routing to it), but does NOT restart it. Used for detecting a degraded pod that needs time to warm up or whose dependencies are temporarily unavailable. In an interview: "my readiness probe checks the DB connection pool and returns 503 if connections are exhausted — the pod stays alive and will recover when connections free up."

---

## 🏢 Real World — Where Companies Use This

- **Amazon S3** (object storage): 99.99% availability SLA backed by multi-AZ replication — every object stored in at least three AZs. S3's internal architecture uses active-active routing across AZs; an AZ failure reroutes to surviving AZs transparently. S3 never "fails over" — it has no single primary.

- **Stripe** (payment processing): 99.99%+ target because every second of downtime is lost revenue for thousands of merchants. Multi-region active-active with strong consistency via distributed transactions. Stripe specifically avoids active-passive because 30-60 seconds of failover time is unacceptable at checkout.

- **Netflix** (video streaming): Chaos Engineering team (Netflix Simian Army — random termination of production instances) validates that multi-region active-active survives real failures. Netflix deployed to three AWS regions; loss of one region fails over gracefully via Route53 DNS failover and Edge Load Balancing.

- **Google Spanner** (distributed SQL): 99.999% global availability through synchronous replication via Paxos across multiple zones. Synchronous replication costs write latency (~10ms cross-zone) but guarantees zero data loss on failure.

- **Swiggy** (food delivery platform): Graceful degradation at 99.9% for non-critical paths — if the ETA estimation service is down, orders still go through without showing ETA. If the restaurant menu service is down, cached menus are served. Core order flow is protected even when auxiliary services degrade.

---

## 🧭 When to Use vs When NOT to Use

| Strategy | Use when | Do NOT use when |
|---|---|---|
| **Active-Passive** | Stateful services needing a single writer (PostgreSQL primary); 30-60s failover time is tolerable | Zero-downtime requirement; active-active is feasible for the service type |
| **Active-Active** | Stateless services (web/API pods); global load distribution; zero-tolerance for failover delay | Service has strong write consistency requirements and no distributed conflict resolution |
| **Multi-AZ** | 99.99% availability target; protection against AZ-level hardware failure | Very cost-sensitive; latency between AZs is unacceptable |
| **Multi-Region** | 99.999% target; global user base; protection against regional disaster | Data residency laws prohibit cross-region replication; latency is a hard constraint |
| **Sync replication** | Zero data loss requirement (financial, medical); can tolerate higher write latency | Write latency SLA is tight; replica availability must not block primary writes |
| **Async replication** | Write latency is critical; replica availability must not block writes | Any scenario where losing the last few seconds of writes is unacceptable |

**The common mistake:** Targeting 99.999% when the business actually needs 99.9%. Each nine is exponentially harder and more expensive to achieve. A payment processing company needs five nines; a blog needs two. Design to the actual cost of downtime, not to a prestige number.

---

## ⚠️ Trade-offs

| | |
|---|---|
| **You gain** | Resilience to component failures, automated recovery without human intervention, the ability to do rolling deployments without downtime, reduced blast radius when one component degrades |
| **You lose** | Consistency guarantees (sync replication adds write latency; async replication risks data loss on failover); operational complexity (health check configuration, failover testing, split-brain handling); infrastructure cost (standby or active-active doubles resource usage) |
| **Failure mode** | **Sync replication**: replica becomes unavailable → primary blocks on every write (cascade failure from a supposed availability feature). **Active-active without conflict resolution**: two writes to same record on different nodes → data divergence. **Health check misconfigured**: false positives take healthy pods out of rotation → fewer pods serving the same load → overload cascade |

---

## 🔬 Interview Q&As

### Q: "How do you achieve 99.99% availability?"
> Three layers: redundancy at every tier (multiple pods behind an LB, DB primary + replica), automated health checks that remove degraded instances before they serve bad responses, and multi-AZ deployment so an AZ-level failure (power, network) doesn't kill the service. The math: two AZs each at 99.9% deployed active-active gives 1 - (0.001 × 0.001) = 99.9999% — well above 99.99%. The operational cost is the LB health check latency (typically 10-30 seconds to detect and reroute), which is the minimum downtime window.

### Q: "What's the difference between active-passive and active-active failover?"
> Active-passive: one primary handles all traffic; a warm standby sits idle. On failure, the standby promotes and takes traffic — typical failover time is 30-60 seconds. Simple to implement for stateful services (single writer). Cost: 50% of capacity is idle. Active-active: all nodes handle live traffic. Failure redistributes load to surviving nodes — failover time is near-zero (next request goes to another node). Complexity: writes must be consistent across nodes, or you need conflict resolution. Stateless services (web/API pods) are trivially active-active.

### Q: "How does synchronous vs asynchronous replication affect availability?"
> Synchronous replication: the primary waits for the replica to confirm before acknowledging the write. Zero data loss on failover. But if the replica is slow or unavailable, the primary blocks — a replica outage degrades write availability. Asynchronous replication: primary acknowledges immediately, replica catches up. Better write availability and latency. But if primary fails before the replica has replicated the last N transactions, those N transactions are lost — the replication lag is your data loss window.

### Q (Tier 2): "You have 3 microservices each at 99.9%. What's your end-to-end SLA?"
> 0.999 × 0.999 × 0.999 = 0.997, so 99.7% — that's 26 hours of combined downtime per year. Worse than any individual service. This is the fundamental microservices availability trap: serial dependencies multiply downtime risk. The fix is circuit breakers that let the user flow complete partially when one service is down, rather than blocking the entire chain. A circuit breaker turns a series dependency into a graceful degradation, keeping the end-to-end availability close to the best individual service rather than worse than all of them.

### Q (Tier 2): "You moved from a monolith to microservices. Why did your availability get worse?"
> Three reasons: (1) serial dependency multiplication — the chain's availability is the product of all services; (2) network calls replace in-process function calls — network failures now cause errors that didn't exist in the monolith; (3) distributed failure modes — a partially failed service might return 200 with corrupt data rather than failing cleanly. The monolith's availability was driven by a single deployment unit; microservices need circuit breakers, retries with exponential backoff, and graceful degradation at each service boundary to compensate.

### Q (Tier 2): "Your DB primary dies. You promote the replica. But the replica had a 3-second replication lag. What happened?"
> The last 3 seconds of writes to the primary were not replicated. Those writes are lost. If this is a financial system — lost transactions. If it's a social feed — lost posts. The mitigation options are: (1) synchronous replication with a lag budget of zero (costs write latency); (2) accept the loss and replay from the application layer (if writes were sourced from an event log/Kafka, replay from the offset at which the replica stopped); (3) the Outbox pattern — write events to a transactional outbox in the same DB transaction; on failover, the Outbox can reconstruct what was lost. Which mitigation depends on whether you can afford the write latency cost of synchronous replication.

---

## 🧾 TL;DR

> "Availability is achieved by putting redundant components in parallel at every critical layer, automating health detection and failover, and using the serial multiplication formula (A = A1 × A2 × A3) to find where a microservices chain is weaker than its individual components."

---

## 🔗 Related Concepts

- **`57-spof.md`** — SPOF identification and mitigation; every availability hole is a SPOF in disguise
- **`29-db-replication-failover.md`** — sync vs async replication deep dive; read replica failover mechanics
- **`20-circuit-breaker-resilience.md`** — breaks serial dependency chains; converts series failures into graceful degradation
- **`44-graceful-degradation-fallbacks.md`** — what to do when a dependency is unavailable; partial availability beats zero availability
- **`../../Foundations/Performance-and-Scale/55-scalability.md`** — horizontal scaling and availability overlap; more pods = higher availability + throughput

---

## 📚 Further Reading (Optional — after the note)

| Resource | What it adds | Time |
|---|---|---|
| **"Site Reliability Engineering" Ch. 3** — Google SRE Book (sre.google) | Error budget framework — how Google translates nines into a quarterly budget engineers consume; explains the 100% availability fallacy | ~25 min read |
| **AWS High Availability Whitepaper** — aws.amazon.com/architecture | Multi-AZ vs Multi-Region decision framework with real cost numbers; useful for capacity questions | ~20 min read |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Jul 10, 2026 | File created. Covers nines table, serial dependency math, parallel redundancy formula, active-passive vs active-active, sync vs async replication, liveness/readiness health check code, and 6 Q&As including 3 Tier 2 probe questions. |
