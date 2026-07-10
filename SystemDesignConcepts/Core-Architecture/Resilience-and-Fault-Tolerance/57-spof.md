# Single Point of Failure (SPOF)

> **Standard followed:** `SystemDesignConcepts/Interview-Resources/Metadata/notes-standards.md`

---

## 📖 What is a Single Point of Failure?

**Full form:** SPOF — Single Point of Failure

**Simple analogy:** A chain of Christmas lights wired in series. One dead bulb kills the circuit — all lights go out. The dead bulb is the SPOF. Modern strings are wired in parallel — one bulb fails, the others stay on. The difference is not a better bulb; it is a different wiring topology.

**Core principle:** A SPOF is any component in a system whose failure causes the entire system — or a critical function — to become unavailable. SPOFs are eliminated not by making components more reliable, but by building redundancy: parallel paths so that one component's failure is absorbed rather than propagated.

**Why it matters in system design:** No interviewer will accept a design that has an unacknowledged SPOF for a system requiring 99.9%+ availability. Identifying and eliminating SPOFs is the minimum bar for any production architecture question.

---

## 🎯 Why This Matters

SPOF identification is the first reliability audit step in every architecture review. A system that scaled perfectly but has a single unprotected database or a single unprotected load balancer is not production-ready. Interviewers probe SPOFs directly: "you said you'd use a single Redis instance — what happens if it goes down?" or "your design has one load balancer — isn't that a SPOF?"

**Round:** System design (every question), deep dives on reliability. The interviewer draws your architecture and circles the SPOF.

**Why senior engineers own this:** They have been paged at 3am because one forgotten unprotected dependency killed an entire service — and they never make that mistake again.

---

## 🧠 The Mental Model

Picture a water distribution network for a city. Water flows from reservoir → pump station → main pipe → neighbourhood pipes → house taps.

If there is only one pump station and it fails, the entire city loses water. That pump station is a SPOF. Cities solve this by building multiple pump stations in parallel — if one fails, the others continue pumping. The reservoir, the main pipe, the neighbourhood junction — each is a candidate SPOF until redundancy is added.

The key insight: **any component that sits alone on the only path between a user and their data is a SPOF.** The fix is always the same — add a parallel path. The challenge is finding every such component before a failure finds it for you.

**The hidden SPOFs are harder:** What about the city's single water authority IT system that controls pump scheduling? What about the single engineer who knows how to restart the pump manually? Infrastructure SPOFs are visible; operational and human SPOFs are invisible until disaster.

**The key insight is:** Every SPOF is a missing parallel path. SPOF elimination is the systematic exercise of finding every such path and either adding redundancy or accepting the risk explicitly.

---

## 🎨 Visual — SPOF Architecture vs Resilient Architecture + SPOF Category Map

```
FULL SYSTEM TOPOLOGY — Before (SPOF) vs After (Resilient):

BEFORE — Three SPOFs in one design:
                                           ❌ Single DB
Client ──▶ Single LB ──▶ Single Pod ──▶ ┌──────────┐
           ❌ SPOF        ❌ SPOF        │ Primary  │ (no replica)
                                        └──────────┘

   LB fails → 100% down
   Pod fails → 100% down
   DB fails → 100% down

AFTER — No SPOF (parallel paths everywhere):

                      ┌──────────┐
                      │ LB Node1 │◀── VIP (floating IP)
Client ──▶ VIP ──▶   │          │    If LB1 fails, VIP moves to LB2
                      └────┬─────┘     VRRP/HSRP protocol
                           │
                 ┌─────────┴──────────┐
                 ▼                    ▼
          ┌──────────┐         ┌──────────┐
          │  Pod 1   │         │  Pod 2   │  Pod failure → LB routes to
          └──────────┘         └──────────┘  remaining pod, no downtime
                 └─────────┬──────────┘
                           ▼
                   ┌──────────────┐
                   │  Cache Layer │  (Redis Cluster — no single cache SPOF)
                   └──────┬───────┘
                          ▼
             ┌────────────┴─────────────┐
             ▼                          ▼
      ┌──────────────┐          ┌──────────────┐
      │   Primary DB │ ──────▶  │   Replica DB │  DB failover: replica
      └──────────────┘ replicate└──────────────┘  promotes on primary death


COMPONENT DETAIL — SPOF Category Map:

  Infrastructure SPOFs:
  ├── Single Load Balancer            → Fix: VIP + two LB nodes (VRRP)
  ├── Single Region / AZ             → Fix: Multi-AZ or multi-region
  └── Single DNS resolver            → Fix: Multiple DNS providers

  Data SPOFs:
  ├── Single DB, no replica          → Fix: Primary + read replica
  ├── Single Redis node              → Fix: Redis Sentinel or Redis Cluster
  └── Single Kafka broker            → Fix: Kafka replication factor ≥ 2

  Service SPOFs:
  ├── One pod / process              → Fix: Horizontal scale (≥2 pods)
  └── Shared deployment pipeline     → Fix: Independent deploy pipelines

  External Dependency SPOFs:
  ├── Single payment gateway         → Fix: Primary + fallback gateway
  ├── Single SMS/email provider      → Fix: Provider fallback chain
  └── Single Maps / Geocoding API    → Fix: Circuit breaker + fallback

  Operational SPOFs (often missed):
  ├── One engineer knows deployment  → Fix: Runbooks + cross-training
  └── Shared DB credentials / cert  → Fix: Secret rotation, multi-owner

KEY INVARIANT:
   A SPOF is not a component property — it is a topology property.
   The same database is a SPOF in a single-node setup and not a SPOF
   in a primary + replica setup. Eliminating a SPOF always means
   adding a parallel path, not improving the component itself.
```

---

## ⚙️ How It Actually Works

**Steps:**

1. **Draw the full dependency graph** — every component (LB, pod, cache, DB, message broker, external APIs, DNS, network paths, VPN, cloud provider)
2. **For each node in the graph, ask**: "if this component becomes unavailable right now, what percentage of user requests fail?" — any answer above 0% is a SPOF candidate
3. **Classify by category** — infrastructure, data, service, external dependency, operational (each has different mitigation strategies)
4. **Score by blast radius** — how many users affected? Is it full outage or degraded service? Is the failure detectable immediately or silent?
5. **Mitigate in blast-radius order** — eliminate the highest-impact SPOFs first; document the rest with accepted risk and a monitoring alert

```java
// External dependency SPOF mitigation — payment gateway with fallback
// Before: single gateway, no fallback — gateway timeout = order fails
public PaymentResult chargeWithoutFallback(PaymentRequest request) {
    return primaryGateway.charge(request);
}

// After: primary + fallback gateway — gateway SPOF eliminated
@Service
public class PaymentService {

    private final PaymentGateway primaryGateway;
    private final PaymentGateway fallbackGateway;
    private final CircuitBreaker primaryCircuitBreaker;

    public PaymentResult charge(PaymentRequest request) {
        // Attempt primary gateway (Stripe, Razorpay, etc.)
        if (primaryCircuitBreaker.isAvailable()) {
            try {
                PaymentResult result = primaryGateway.charge(request);
                primaryCircuitBreaker.recordSuccess();
                return result;
            } catch (GatewayUnavailableException ex) {
                primaryCircuitBreaker.recordFailure();
                // Log and fall through to fallback — not a total failure
            }
        }
        // Fallback gateway (PayPal, Braintree, etc.)
        // This eliminates the external payment SPOF
        return fallbackGateway.charge(request);
    }
}
```

### What is a circuit breaker, and why does it appear here?

**Circuit breaker** (a pattern that "opens" a connection to a failing service after N consecutive failures, stopping further calls for a timeout period — like a fuse that breaks the circuit before overload damages the whole system). Without a circuit breaker on the payment gateway fallback logic, a slow primary gateway would cause threads to pile up waiting for timeouts — the SPOF becomes a cascading failure. The circuit breaker detects that the primary is down and routes directly to fallback without waiting, which prevents thread exhaustion. Full detail in `20-circuit-breaker-resilience.md`.

---

## 🏢 Real World — Where Companies Use This

- **Amazon** (EC2 / availability zones): EC2 instances are never recommended to run in a single AZ. AWS itself designed AZs as independent failure domains — power, cooling, and networking are isolated. Auto Scaling Groups are configured across ≥2 AZs by default to eliminate single-AZ as an infrastructure SPOF.

- **Razorpay** (payments): Two acquiring bank integrations (primary + fallback). If HDFC's payment gateway is timing out during peak festival sales, transactions fail over to the secondary bank integration within 2 seconds, transparent to the customer. Eliminating the single-bank SPOF is a regulatory and business necessity.

- **WhatsApp** (messaging): Erlang VM processes replace the single-process SPOF model — millions of lightweight processes on thousands of nodes; a process crash is isolated; the supervisor tree restarts it. No single process crash kills the node, and no single node crash kills the service.

- **Zepto / Blinkit** (quick commerce): Redis Cluster for inventory — never a single Redis node. Cache SPOF in a 10-minute delivery window would cause all stock lookups to fall back to DB, which at scale would collapse the DB in seconds. Redis Sentinel with automatic promotion prevents cache from becoming a data SPOF.

- **PhonePe** (UPI payments): DNS failover as a critical SPOF mitigation — two separate DNS providers (Route53 + secondary) so DNS resolution never becomes an infrastructure SPOF. A DNS outage during UPI transaction volume would halt millions of daily transactions.

- **Netflix** (Chaos Engineering): Chaos Monkey randomly terminates production instances during business hours. Chaos Gorilla kills entire AZs. This is proactive SPOF discovery — find the SPOFs before users find them. Every surviving chaos test proves a SPOF has been eliminated.

---

## 🧭 When to Use vs When NOT to Use

| When to eliminate the SPOF | When to accept it |
|---|---|
| User-facing critical path — order placement, payment, authentication | Internal tooling with low SLA (admin dashboards, batch reporting) |
| Availability SLA requires 99.9%+ | Cost of redundancy exceeds cost of SPOF downtime |
| No graceful degradation available — SPOF failure = 100% outage | Graceful degradation exists — SPOF failure = degraded, not down |
| SPOF has no monitoring — failure is silent | SPOF has alerting and runbook; MTTR (mean time to recovery) is < SLA |
| External dependency with no fallback in a revenue-critical path | External dependency whose failure degrades non-revenue features |

**The common mistake:** Eliminating infrastructure SPOFs but missing external dependency SPOFs. Teams meticulously replicate databases and scale service pods, then rely on a single SMS provider to send OTPs — which fails during the month's highest traffic peak. The external dependency is the forgotten SPOF.

---

## ⚠️ Trade-offs

| | |
|---|---|
| **You gain** | Fault isolation — one component's failure doesn't cascade to the whole system; automated recovery without human intervention; ability to do rolling deployments without downtime; quantifiable blast radius reduction |
| **You lose** | Infrastructure cost (redundancy doubles or triples resource usage for critical components); operational complexity (failover coordination, split-brain handling, consistency across redundant nodes); engineering time to implement and test failover scenarios |
| **Failure mode** | **Split-brain** — two nodes both believe they are the primary (network partition between active-passive pair); both accept writes; data diverges. Fix: quorum-based leader election (Raft, ZooKeeper). **Cascading failure** — you eliminated the SPOF but added a circuit breaker with a too-aggressive threshold; circuit opens prematurely during a traffic spike, routes all traffic to fallback, overwhelms fallback, and both paths are now down. SPOFs eliminated incorrectly can create new, worse SPOFs. |

---

## 🔬 Interview Q&As

### Q: "How do you identify SPOFs in a system you just designed?"
> I follow a structured walk of the dependency graph. Start at the client, follow every request path to its terminal data store, and at each node ask: "what percentage of requests fail if this node disappears right now?" Any node above 0% is a SPOF. I categorize them: infrastructure (LB, network, region), data (DB, cache, queue), service (single pod), and external dependency (payment gateway, SMS provider). Then I prioritize by blast radius — total outage SPOFs first, degraded-service SPOFs second, and I document accepted SPOFs with monitoring and runbooks.

### Q: "Your load balancer is a SPOF — how do you fix it?"
> The standard fix is a Virtual IP (VIP) with two LB nodes running VRRP (Virtual Router Redundancy Protocol). The VIP is a floating IP that normally points to LB Node 1. VRRP sends heartbeats between the two nodes; if LB Node 1 stops sending heartbeats, LB Node 2 claims the VIP and traffic switches over within 1-2 seconds. No DNS change needed — the VIP stays the same. In cloud environments: AWS uses an NLB/ALB with multiple nodes automatically managed across AZs — the SPOF risk at the LB layer is handled by the managed service.

### Q: "Where is the SPOF in a system with multiple pods but a single database?"
> The database is the SPOF. Three scenarios: (1) DB crashes — all pods lose their data store, all requests fail. (2) DB becomes a write bottleneck — pods queue up; latency spikes; effectively unavailable. (3) DB upgrade/maintenance window — all pods must stop writing. The fix is a primary + replica (async replication for higher write throughput, sync for zero data loss), with automated failover via a tool like Patroni (Postgres), MHA (MySQL), or RDS Multi-AZ (managed). The replica eliminates the data SPOF; the automated failover eliminates the operational SPOF of needing a human to promote the replica.

### Q (Tier 2): "You've eliminated every infrastructure SPOF, replicated the DB, and run multi-AZ. Your system still had a full outage. What did you miss?"
> Three categories of non-infrastructure SPOF: (1) External dependency — a payment gateway or authentication provider went down; you had no fallback or circuit breaker. (2) Operational SPOF — a deployment script failed mid-migration, taking both primary and replica offline; no one had the runbook to recover. (3) Shared configuration / secret — a certificate expired, a credentials rotation touched both primary and replica simultaneously, or a bad config deploy rolled out to all pods at once. Modern chaos engineering (Netflix's model) is specifically designed to find these invisible SPOFs — because every infrastructure SPOF you eliminate reveals the operational one hiding behind it.

### Q (Tier 2): "You added a fallback payment gateway to eliminate the payment SPOF. But when the primary gateway failed, your fallback was overwhelmed and also failed. What went wrong?"
> The fallback was undersized — it was designed to handle occasional small traffic, not 100% of payment volume. The fix is two-fold: (1) keep both gateways warm at all times — split traffic 90/10 or 80/20 even under normal operation, so the fallback is always serving live traffic and sized for full load; (2) add a circuit breaker with a rate limit on the fallback so that if the fallback also starts failing (indicating the issue is systemic, not gateway-specific), you degrade gracefully rather than hammering a failing fallback. Failover that creates a new SPOF downstream is worse than no failover.

---

## 🧾 TL;DR

> "A SPOF is any component on the only path between a user and their data — eliminate it by adding a parallel path (redundancy), and don't forget the invisible SPOFs: external dependencies without fallbacks, shared operational procedures known by one person, and certificates that expire on both nodes simultaneously."

---

## 🔗 Related Concepts

- **`56-availability.md`** — availability is the outcome; SPOF elimination is the mechanism
- **`20-circuit-breaker-resilience.md`** — circuit breaker prevents external dependency SPOFs from causing cascading failures
- **`44-graceful-degradation-fallbacks.md`** — graceful degradation is the alternative to full redundancy for non-critical SPOFs
- **`29-db-replication-failover.md`** — detailed mechanics of eliminating the database SPOF
- **`../../Foundations/Performance-and-Scale/55-scalability.md`** — horizontal scaling and SPOF elimination overlap: adding pods also removes a single-pod SPOF

---

## 📚 Further Reading (Optional — after the note)

| Resource | What it adds | Time |
|---|---|---|
| **"Chaos Engineering" Chapter 1** — Casey Rosenthal & Nora Jones (O'Reilly) | The systematic approach Netflix used to find SPOFs through controlled failure injection; explains why proactive chaos beats reactive post-mortems | ~20 min read |
| **"Designing Data-Intensive Applications" Ch. 8** — Martin Kleppmann | Partial failures in distributed systems — network partitions, split-brain, and why "adding redundancy" can sometimes create new failure modes | ~25 min read |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Jul 10, 2026 | File created. Covers SPOF identification methodology, 6 SPOF category types, before/after topology diagrams, external dependency fallback code with circuit breaker integration, 6 real company examples (Amazon, Razorpay, WhatsApp, Zepto, PhonePe, Netflix), and 5 Q&As including 2 Tier 2 probe questions. |
