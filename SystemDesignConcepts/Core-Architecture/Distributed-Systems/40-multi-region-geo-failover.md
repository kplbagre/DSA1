# 40 — Multi-Region Architecture & Geo-Failover

## 📖 What is Multi-Region Architecture?

**Full form:** Multi-Region Architecture — a deployment topology where application services and databases are replicated across two or more geographically separate data centers (AWS regions, GCP zones, Azure regions), enabling low-latency serving for global users and continued operation when one region fails.

**Simple analogy:** A national bank operates branch offices in New York, London, and Singapore — not one central office that the world must call. A customer in London walks into their local branch and completes a transaction in seconds. If the New York headquarters experiences a fire, London and Singapore branches continue serving their own customers without interruption. Deposits made in London are synchronized to New York nightly (or in real time) to keep all branches consistent. Multi-region architecture applies this model to software: serve users from their nearest region, survive regional failures gracefully.

**Core principle:** Route each user's request to their geographically nearest region (minimizing network latency). Replicate data across regions so that a region failure does not lose data. Decide whether both regions accept writes simultaneously (Active-Active) or only one region accepts writes while the other is a standby (Active-Passive).

**Why it matters in system design:** Any global service with a single-region deployment has a geographic latency penalty for distant users (150ms+ across continents) and a single point of failure for the entire system. Multi-region is the architectural answer to both problems.

---

## 📖 Terminology Table

| Term | Plain-English Meaning | Example |
|------|----------------------|---------|
| **Active-Active** | two or more regions both accept reads AND writes simultaneously; requires conflict resolution | us-east-1 and eu-west-1 both accept orders; continuous cross-region sync runs |
| **Active-Passive** | one region (primary) accepts writes; standby region takes over only on failure | us-east-1 is primary; eu-west-1 is warm standby — no writes until failover |
| **GeoDNS** | DNS that returns different IP addresses based on the requester's geographic location | US client → us-east-1 IP; EU client → eu-west-1 IP; same domain name |
| **Anycast BGP** | same IP address announced from multiple routers worldwide; BGP routes each packet to the nearest | Cloudflare's 1.1.1.1 — same IP reaches different physical servers depending on location |
| **LWW (Last Write Wins)** | conflict resolution rule: whichever write has the later timestamp survives | user updates profile in US at T=100ms, in EU at T=110ms → EU version is kept |
| **CRDT (Conflict-free Replicated Data Type)** | data structure that can be merged from two regions without conflict by design | increment counters: US adds 5, EU adds 3 → merge = 8, always mathematically correct |
| **RPO (Recovery Point Objective)** | maximum acceptable data loss (measured in time) when a region fails | RPO=0 → synchronous cross-region replication required; RPO=1min → async OK |
| **RTO (Recovery Time Objective)** | maximum acceptable time to restore service after a region failure | RTO=30s → automated DNS failover; RTO=1h → manual flip acceptable |
| **Cross-Region Replication Lag** | delay between a write committed in one region appearing in another region's replica | us-east-1 writes at T=0; eu-west-1 replica visible at T=80ms → 80ms lag |
| **Route53 / Cloudflare** | DNS providers with GeoDNS and health-check-based automatic failover | Route53 health check fails on us-east-1 → all traffic redirected to eu-west-1 |

---

## 🎯 Why This Matters

- **Problem:** A single AWS `us-east-1` deployment means users in India experience 200ms base latency, and a regional failure (AWS outage, natural disaster, network partition) takes down your entire service globally.
- **Interview signal:** "Design Netflix, Uber, or a global payment system" — the answer always includes multi-region and the candidate must choose Active-Active or Active-Passive and explain why.
- **Senior expectation:** You must explain RPO (Recovery Point Objective — the maximum acceptable data loss, measured in time) and RTO (Recovery Time Objective — the maximum acceptable downtime), Active-Active vs Active-Passive trade-offs, conflict resolution strategies, and failover mechanics.

---

## 🧠 The Mental Model

Imagine a multi-city postal service where each city has its own sorting center and its own post office for local customers.

**Active-Passive (one primary office, one backup):** The New York center is the authoritative postal office — all address changes, all account registrations go there. The Chicago backup center maintains a full copy of New York's records, updated every hour by a courier. If New York burns down, the postmaster calls Chicago: "You are now the primary." Chicago spends 30 minutes confirming their records are up to date (RTO — time to switch). Their records are at most 1 hour old (RPO — maximum data loss). Chicago opens and New York customers call their letters there. Simple, no conflicts, but: (1) during switchover, the service is unavailable, (2) customers trying to reach New York during the outage get errors.

**Active-Active (both offices running simultaneously):** New York and Chicago BOTH accept mail registrations. A Los Angeles customer registers a new address in their nearest office (Los Angeles, connected to Chicago). A New York customer registers theirs at New York. Every night, both offices sync their records with each other. Problem: what if the same customer changes their address in both offices on the same day? Two records now differ. You need a conflict resolution rule: "last write wins" (whichever timestamp is later), "New York wins" (one office is authoritative for conflicts), or "merge" (apply both changes). This complexity is the price of zero downtime and low latency.

**GeoDNS routing** is the postal zip-code lookup: when a letter arrives at the national sorting hub, they look at the zip code and route it to the correct regional office automatically. No letter needs to cross the continent unless necessary.

**The key insight is:** Active-Active buys you zero-failover latency and lower global latency, but forces you to solve write conflicts. Active-Passive is simpler and consistent, but you pay failover time and always route writes across regions (adding latency for distant users).

---

## 🎨 Visual — System Topology & Component Flow

```
FULL SYSTEM TOPOLOGY (Active-Active across two regions):

Client Tier — Global
┌─────────────┐                      ┌─────────────┐
│ US Client   │                      │ EU Client   │
└──────┬──────┘                      └──────┬──────┘
       │ DNS query: api.example.com          │ DNS query: api.example.com
       │                                    │
       ▼                                    ▼
GeoDNS / Anycast Tier
┌──────────────────────────────────────────────────────────────┐
│  Route53 / Cloudflare (GeoDNS — maps client IP → nearest    │
│  region endpoint)                                           │
│                                                             │
│  US Client → us-east-1.api.example.com                      │
│  EU Client → eu-west-1.api.example.com                      │
└──────────────────────────────────────────────────────────────┘
       │                                    │
       ▼                                    ▼
CDN Tier (per-region)
┌────────────────────┐         ┌────────────────────┐
│ CDN PoP (US)       │         │ CDN PoP (EU)        │
│ (static assets,    │         │ (static assets,     │
│  API caching)      │         │  API caching)       │
└─────────┬──────────┘         └──────────┬──────────┘
          │                               │
          ▼                               ▼
Load Balancer Tier (per-region)
┌────────────────────┐         ┌────────────────────┐
│ ALB — us-east-1    │         │ ALB — eu-west-1     │
└─────────┬──────────┘         └──────────┬──────────┘
          │                               │
          ▼                               ▼
Service Tier (per-region, stateless)
┌────────────────────┐         ┌────────────────────┐
│ Service Pods       │         │ Service Pods        │
│ (us-east-1)        │         │ (eu-west-1)         │
│                    │         │                     │
│ Accept reads AND   │         │ Accept reads AND     │
│ writes (A-A)       │         │ writes (A-A)         │
└─────────┬──────────┘         └──────────┬──────────┘
          │                               │
          ▼                               ▼
Cache Tier (per-region)
┌────────────────────┐         ┌────────────────────┐
│ Redis Cluster (US) │         │ Redis Cluster (EU)  │
└─────────┬──────────┘         └──────────┬──────────┘
          │                               │
          ▼                               ▼
Database Tier — CROSS-REGION REPLICATION (core of multi-region)
┌─────────────────────┐         ┌─────────────────────┐
│ Primary DB (US)     │◀───────▶│ Primary DB (EU)      │
│ DynamoDB Global     │  async  │ DynamoDB Global      │
│ Tables / Cassandra  │  repl.  │ Tables / Cassandra   │
│ multi-DC            │         │ multi-DC             │
└─────────────────────┘         └─────────────────────┘

KEY INVARIANT:
   Both regions accept writes (Active-Active).
   Cross-region replication is typically ASYNC (< 1s lag but not zero).
   Conflict resolution policy (LWW / CRDT) must be defined before launch.
   If eu-west-1 fails: Route53 health checks detect failure, update DNS to
   route ALL traffic to us-east-1 — no failover needed (traffic shift only).


COMPONENT DETAIL — Active-Active vs Active-Passive + Conflict Resolution:

┌──────────────────────────────────────────────────────────────────────┐
│ ACTIVE-ACTIVE TOPOLOGY:                                              │
│                                                                      │
│  Region A (US)                    Region B (EU)                      │
│  ┌─────────────────┐              ┌─────────────────┐                │
│  │ Write: user     │              │ Write: user     │                │
│  │ profile update  │◀──async──────│ profile update  │                │
│  │ (version=5,     │──async──────▶│ (version=6,     │                │
│  │  ts=T1)         │              │  ts=T2)         │                │
│  └─────────────────┘              └─────────────────┘                │
│                                                                      │
│  CONFLICT: same user, different value, ts T1 vs T2                   │
│                                                                      │
│  Resolution strategies:                                              │
│  1. LWW (Last Write Wins) — higher timestamp wins                    │
│     ts T2 > T1 → EU version (version=6) wins globally               │
│     Risk: if clocks are skewed, wrong version wins                   │
│                                                                      │
│  2. CRDT (Conflict-free Replicated Data Type) — merge without loss   │
│     G-Counter: US counter=5, EU counter=6 → merged=11               │
│     OR-Set: US adds item A, EU removes item A → merge is "removed"   │
│     No conflicts: all operations are designed to compose             │
│                                                                      │
│  3. Application-level merge                                          │
│     Application receives both versions and merges (manual logic)     │
│     Most flexible, highest development cost                          │
│                                                                      │
│ ACTIVE-PASSIVE TOPOLOGY:                                             │
│                                                                      │
│  Region A (PRIMARY — US)          Region B (STANDBY — EU)            │
│  ┌─────────────────┐              ┌─────────────────┐                │
│  │ All writes here │──async──────▶│ Read-only        │                │
│  │ All reads here  │              │ replica of A     │                │
│  │ (or from rep.)  │              │ No writes        │                │
│  └─────────────────┘              └─────────────────┘                │
│           ↓ fails                                                    │
│  FAILOVER STEPS:                                                     │
│  1. Route53 health check detects us-east-1 is down                  │
│  2. DNS TTL expires (30–60 seconds) — clients start hitting EU       │
│  3. EU standby promoted to primary (accepts writes)                  │
│  4. RPO = data written to US after last replication (seconds to min) │
│  5. RTO = DNS TTL + promotion time (30s–5 min total)                 │
│                                                                      │
│ FAILOVER MECHANICS:                                                  │
│  Route53 health check → ALB endpoint unhealthy → DNS TTL expires    │
│  → Global Accelerator (anycast routing to healthy region)            │
│  → New region receives traffic                                       │
└──────────────────────────────────────────────────────────────────────┘

KEY INVARIANT:
   Active-Active: zero failover time, conflict resolution required.
   Active-Passive: simple consistency, failover delay (RTO = DNS TTL + promotion).
   RPO and RTO are the two axes of the trade-off: lower both = higher cost + complexity.
```

---

## ⚙️ How It Actually Works

### Active-Active — DynamoDB Global Tables

**Steps:**
1. Create a DynamoDB (Amazon's managed NoSQL database) table and enable Global Tables — AWS automatically replicates all writes to every added region.
2. Write to your nearest region; DynamoDB replicates to other regions asynchronously (typical lag: < 1 second).
3. DynamoDB uses LWW (Last Write Wins — the write with the later timestamp replaces earlier writes for the same key) as the default conflict resolution policy. You must design your data model to avoid conflicts (append-only writes, conditional writes with version checks) or accept LWW.
4. For reads, query the local region's replica; data may be slightly stale (eventual consistency window).

```java
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class MultiRegionDynamoDBService {
    // Step 1 — connect to local region; DynamoDB Global Tables handle replication
    private final DynamoDbClient dynamoDb = DynamoDbClient.builder()
        .region(Region.US_EAST_1)  // change to eu-west-1 for EU pods
        .build();

    private static final String TABLE = "UserProfiles";

    // Step 2 — write to local region (replicated async to all other regions)
    public void updateUserProfile(String userId, String email, long version) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("userId", AttributeValue.fromS(userId));
        item.put("email", AttributeValue.fromS(email));
        // Step 3 — include version + timestamp for LWW conflict detection
        item.put("version", AttributeValue.fromN(String.valueOf(version)));
        item.put("updatedAt", AttributeValue.fromN(String.valueOf(Instant.now().toEpochMilli())));

        // Use condition expression to prevent overwriting a newer version
        // (optimistic locking — a version-check write that rejects stale updates)
        PutItemRequest request = PutItemRequest.builder()
            .tableName(TABLE)
            .item(item)
            .conditionExpression("attribute_not_exists(version) OR version < :newVersion")
            .expressionAttributeValues(
                Map.of(":newVersion", AttributeValue.fromN(String.valueOf(version)))
            )
            .build();

        try {
            dynamoDb.putItem(request);
        } catch (ConditionalCheckFailedException e) {
            // A newer version exists in this region; discard this write
            throw new StaleWriteException("Newer version exists for userId: " + userId, e);
        }
    }

    // Step 4 — read from local region (may be milliseconds behind primary)
    public Map<String, AttributeValue> getUserProfile(String userId) {
        GetItemRequest request = GetItemRequest.builder()
            .tableName(TABLE)
            .key(Map.of("userId", AttributeValue.fromS(userId)))
            .consistentRead(false)  // eventually consistent — local replica
            .build();

        GetItemResponse response = dynamoDb.getItem(request);
        return response.item();
    }
}
```

---

### Active-Passive — Failover with Route53 Health Checks

**Steps:**
1. Deploy primary services in `us-east-1` (primary region) and standby services in `eu-west-1` (standby region).
2. Database in `us-east-1` replicates asynchronously to `eu-west-1` read replica (PostgreSQL streaming replication or Aurora Global Database).
3. Configure Route53 (AWS's DNS service) with a health check on the primary ALB (Application Load Balancer) endpoint and a failover routing policy: primary → `us-east-1`, secondary → `eu-west-1`.
4. When the primary goes down: Route53 health check detects failure → DNS record switches to secondary → after DNS TTL expires (30–60 seconds), new requests hit `eu-west-1` → standby DB is promoted to primary (write-accepting).
5. RTO (Recovery Time Objective) = DNS TTL + DB promotion time. RPO (Recovery Point Objective) = replication lag at time of failure.

```java
import software.amazon.awssdk.services.route53.Route53Client;
import software.amazon.awssdk.services.route53.model.ChangeResourceRecordSetsRequest;
import software.amazon.awssdk.services.route53.model.Change;
import software.amazon.awssdk.services.route53.model.ChangeAction;
import software.amazon.awssdk.services.route53.model.ResourceRecordSet;
import software.amazon.awssdk.services.route53.model.ResourceRecord;
import software.amazon.awssdk.services.route53.model.RRType;
import software.amazon.awssdk.services.route53.model.ChangeBatch;
import java.util.List;

public class GeoFailoverController {
    private final Route53Client route53 = Route53Client.create();
    private static final String HOSTED_ZONE_ID = "Z1234567890ABC";
    private static final String DOMAIN = "api.example.com.";

    // Step 4 — manually trigger failover (also done automatically by Route53 health checks)
    public void failoverToSecondaryRegion(String secondaryRegionAlbDns) {
        // Update DNS A record to point to secondary region ALB
        ResourceRecord record = ResourceRecord.builder()
            .value(secondaryRegionAlbDns)
            .build();

        ResourceRecordSet recordSet = ResourceRecordSet.builder()
            .name(DOMAIN)
            .type(RRType.CNAME)
            .ttl(30L)   // 30 second TTL — short TTL allows fast DNS propagation
            .resourceRecords(List.of(record))
            .build();

        Change change = Change.builder()
            .action(ChangeAction.UPSERT)
            .resourceRecordSet(recordSet)
            .build();

        ChangeResourceRecordSetsRequest request = ChangeResourceRecordSetsRequest.builder()
            .hostedZoneId(HOSTED_ZONE_ID)
            .changeBatch(ChangeBatch.builder().changes(List.of(change)).build())
            .build();

        route53.changeResourceRecordSets(request);
        // DNS propagation: ~30 seconds (= DNS TTL value)
        // After propagation: all new requests hit secondary region
    }

    // Step 5 — promote standby DB to primary (PostgreSQL example via JDBC admin call)
    public void promoteStandbyDatabase(java.sql.Connection adminConn) throws java.sql.SQLException {
        // pg_promote() tells a PostgreSQL standby replica to accept writes
        adminConn.createStatement().execute("SELECT pg_promote()");
        // After this call: standby begins accepting writes
        // RPO is determined by replication lag at time of primary failure
    }
}
```

---

### Conflict Resolution Strategy 1: Last Write Wins (LWW)

**Steps:**
1. Every write includes a timestamp (epoch millis) or a logical clock value.
2. When two conflicting writes arrive at the same node (or replication detects a conflict), the write with the higher timestamp wins.
3. The losing write is discarded silently.
4. Risk: if clocks are skewed between regions (NTP — Network Time Protocol — drift), a genuinely newer write with a lower clock value may be discarded.

```java
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

public class LWWConflictResolver<T> {
    // Map: key → (value, timestamp)
    private final ConcurrentHashMap<String, VersionedValue<T>> store = new ConcurrentHashMap<>();

    public void write(String key, T value) {
        long timestamp = Instant.now().toEpochMilli();
        store.merge(
            key,
            new VersionedValue<>(value, timestamp),
            (existing, incoming) -> {
                // Step 2 — LWW: higher timestamp wins; discard lower
                if (incoming.timestamp > existing.timestamp) {
                    return incoming;
                }
                return existing;
            }
        );
    }

    public T read(String key) {
        VersionedValue<T> entry = store.get(key);
        return entry == null ? null : entry.value;
    }

    private static class VersionedValue<T> {
        final T value;
        final long timestamp;

        VersionedValue(T value, long timestamp) {
            this.value = value;
            this.timestamp = timestamp;
        }
    }
}
```

---

### Conflict Resolution Strategy 2: CRDT — Grow-Only Counter (G-Counter)

**Steps:**
1. A CRDT (Conflict-free Replicated Data Type — a data structure designed so that concurrent updates from multiple nodes always merge into a consistent result without conflicts) stores a per-node counter array. Node N increments only its own slot.
2. To get the global count, sum all slots.
3. To merge two replicas: for each slot, take the maximum value. This merge is commutative and associative — order of merging does not matter.
4. Use case: like counts, view counts, inventory decrement ledgers.

```java
import java.util.Arrays;

public class GCounter {
    private final int nodeCount;
    private final int myNodeIndex;
    private final int[] counts; // one slot per node

    public GCounter(int nodeCount, int myNodeIndex) {
        this.nodeCount = nodeCount;
        this.myNodeIndex = myNodeIndex;
        this.counts = new int[nodeCount];
    }

    // Step 1 — each node increments only its own slot
    public void increment() {
        counts[myNodeIndex]++;
    }

    // Step 2 — global value = sum of all slots
    public int value() {
        int total = 0;
        for (int count : counts) {
            total += count;
        }
        return total;
    }

    // Step 3 — merge: element-wise max (commutative, no conflicts)
    public GCounter merge(GCounter other) {
        GCounter merged = new GCounter(nodeCount, myNodeIndex);
        for (int i = 0; i < nodeCount; i++) {
            merged.counts[i] = Math.max(this.counts[i], other.counts[i]);
        }
        return merged;
    }

    // Step 4 — serialize for replication (send to other regions)
    public int[] getState() {
        return Arrays.copyOf(counts, counts.length);
    }

    public void setState(int[] state) {
        System.arraycopy(state, 0, counts, 0, nodeCount);
    }
}
```

---

### Latency-Aware Routing — GeoDNS Configuration Sketch

**Steps:**
1. Register two A/CNAME records for the same domain, tagged with geolocation routing policies.
2. Route53 (or Cloudflare) resolves the domain to the nearest region based on the client's IP geo-location.
3. Set a low TTL (30–60 seconds) to enable fast failover — when a region goes down, DNS stops returning its address within TTL seconds.
4. Use Route53 health checks: if ALB in us-east-1 returns 5xx or times out for 3 consecutive checks, stop including it in DNS responses.

```java
import software.amazon.awssdk.services.route53.Route53Client;
import software.amazon.awssdk.services.route53.model.ChangeResourceRecordSetsRequest;
import software.amazon.awssdk.services.route53.model.Change;
import software.amazon.awssdk.services.route53.model.ChangeAction;
import software.amazon.awssdk.services.route53.model.ResourceRecordSet;
import software.amazon.awssdk.services.route53.model.ResourceRecord;
import software.amazon.awssdk.services.route53.model.RRType;
import software.amazon.awssdk.services.route53.model.GeoLocation;
import software.amazon.awssdk.services.route53.model.ChangeBatch;
import java.util.List;

public class GeoDNSConfigurator {
    private final Route53Client route53 = Route53Client.create();
    private static final String HOSTED_ZONE_ID = "Z1234567890ABC";

    // Step 2 — configure geolocation routing for US traffic
    public void configureUSRegionRecord(String usAlbDns, String healthCheckId) {
        ResourceRecordSet usRecord = ResourceRecordSet.builder()
            .name("api.example.com.")
            .type(RRType.CNAME)
            .setIdentifier("us-east-1")
            .geoLocation(GeoLocation.builder().continentCode("NA").build()) // North America
            .ttl(30L)           // Step 3 — short TTL for fast failover
            .healthCheckId(healthCheckId) // Step 4 — auto-remove if health check fails
            .resourceRecords(List.of(ResourceRecord.builder().value(usAlbDns).build()))
            .build();

        Change usChange = Change.builder()
            .action(ChangeAction.UPSERT)
            .resourceRecordSet(usRecord)
            .build();

        route53.changeResourceRecordSets(
            ChangeResourceRecordSetsRequest.builder()
                .hostedZoneId(HOSTED_ZONE_ID)
                .changeBatch(ChangeBatch.builder().changes(List.of(usChange)).build())
                .build()
        );
    }

    // EU region record configuration (similar to US but different continent code + ALB)
    public void configureEURegionRecord(String euAlbDns, String euHealthCheckId) {
        ResourceRecordSet euRecord = ResourceRecordSet.builder()
            .name("api.example.com.")
            .type(RRType.CNAME)
            .setIdentifier("eu-west-1")
            .geoLocation(GeoLocation.builder().continentCode("EU").build())
            .ttl(30L)
            .healthCheckId(euHealthCheckId)
            .resourceRecords(List.of(ResourceRecord.builder().value(euAlbDns).build()))
            .build();

        Change euChange = Change.builder()
            .action(ChangeAction.UPSERT)
            .resourceRecordSet(euRecord)
            .build();

        route53.changeResourceRecordSets(
            ChangeResourceRecordSetsRequest.builder()
                .hostedZoneId(HOSTED_ZONE_ID)
                .changeBatch(ChangeBatch.builder().changes(List.of(euChange)).build())
                .build()
        );
    }
}
```

---

### What is Anycast, and why does it fit here?

**Anycast** — a network addressing scheme where a single IP address is announced from multiple geographically distributed servers simultaneously. The internet's routing infrastructure (BGP — Border Gateway Protocol) automatically sends each client's packets to the topologically nearest server announcing that IP. In an interview: *"Cloudflare and Google use Anycast to route users to the nearest PoP (Point of Presence) without DNS — the routing happens at the network layer, below DNS, making it faster and more resilient than GeoDNS."*

### What is Aurora Global Database, and why does it fit here?

**Aurora Global Database** — AWS Aurora's cross-region replication feature that maintains a primary region that handles all writes and up to five secondary read-only regions with < 1 second replication lag. Secondary regions can be promoted to primary (failover) in < 1 minute. In an interview: *"For Active-Passive with an RTO under 1 minute, I'd use Aurora Global Database — it manages cross-region replication automatically and the promotion is a single API call."*

---

## 🏢 Real World — Where Companies Use This

- **Netflix (Active-Active across three AWS regions):** Netflix runs Active-Active across `us-east-1`, `us-west-2`, and `eu-west-1`. All regions accept playback requests simultaneously. During the 2011 AWS `us-east-1` outage that took down many services, Netflix remained partially available because they had already shifted traffic to other regions. They use Cassandra multi-datacenter replication (with tunable consistency — operators can configure how many replicas must acknowledge a write before it is considered successful) for user preferences and view history.
- **Uber (geo-sharding + Active-Active for trip data):** Uber shards trip data by city/region. Each city's data is Active-Active across two nearby data centers. A trip in Mumbai is stored in Mumbai and Singapore. Surge pricing calculations are local to each geo cluster. Uber's architecture avoids cross-region write conflicts by ensuring a trip is "owned" by one region (the region where it started) — effectively geo-partitioning the write domain.
- **Stripe (Active-Passive for payment consistency):** Stripe chose Active-Passive over Active-Active specifically because payment operations require strong consistency — charging a card twice is worse than a few seconds of unavailability. One primary region accepts all writes. A secondary region replicates synchronously (before every write is acknowledged). RPO = 0 data loss. RTO = ~1 minute for promotion. This is a deliberate trade-off: consistency over availability, per the CAP theorem (Consistency-Availability-Partition tolerance trade-off in distributed systems).
- **Google (global Anycast for infrastructure services):** Google serves Google.com, Gmail, and YouTube via Anycast — a single IP address announced from 200+ Points of Presence globally. Users in Tokyo hit a Tokyo PoP; users in São Paulo hit a South American PoP. No GeoDNS required. Anycast routing is handled at the BGP (Border Gateway Protocol — the internet's routing protocol) layer, providing sub-10ms routing decisions.
- **Cloudflare (global Anycast CDN + DDoS mitigation):** Cloudflare's entire network operates on Anycast. Every customer domain is served from the nearest of 300+ data centers globally. When a region is attacked (DDoS — Distributed Denial of Service), BGP withdraws that region's announcement; traffic automatically reroutes to the next-nearest PoP. Failover is near-instantaneous (BGP convergence: seconds).
- **DynamoDB Global Tables (AWS managed Active-Active):** DynamoDB Global Tables automatically replicate writes to all configured regions. Conflict resolution uses LWW with timestamps. Used by Duolingo, Lyft, and Redfin for globally distributed user session data. Write to the nearest region; reads are local. Typical replication lag < 1 second.

---

## 🧭 When to Use vs When NOT to Use

| Use this when | Do NOT use when |
|---|---|
| Users are globally distributed and round-trip latency to a single region exceeds 100ms | All users are in one geography (single-region is simpler and cheaper) |
| Your SLA requires RTO < 5 minutes and RPO < 1 minute | Writes require strict cross-entity ACID transactions (multi-region complicates distributed transactions enormously) |
| Data residency regulations (GDPR, CCPA) mandate EU data stays in EU | Your consistency model requires synchronous cross-region replication (cost: 150ms+ latency on every write) |
| Business continuity requires surviving a full regional cloud provider failure | Team lacks expertise to operate cross-region replication, conflict resolution, and geo-failover runbooks |
| Service is a global product (payments, streaming, ride-hailing) with SLA = "99.99%" | You are still in early startup phase — operational complexity far exceeds the benefit |

**The common mistake:** Choosing Active-Active without a conflict resolution strategy. Engineers deploy Active-Active because "it sounds more resilient," then discover two concurrent writes to the same user record in different regions silently lose one of the writes (LWW) — and this goes unnoticed until a customer reports data loss. Define your conflict resolution policy before your first multi-region write reaches production.

---

## ⚠️ Trade-offs

| | |
|---|---|
| **You gain** | Low global latency (serve from nearest region); regional failure isolation (one region down does not kill global service); regulatory compliance (geo-sharding for GDPR); reduced blast radius for incidents. |
| **You lose** | Cross-region replication lag introduces eventual consistency (reads may see stale data); conflict resolution adds complexity; cross-region synchronous writes double or triple write latency; operational cost is 2–3× single-region (duplicate compute, storage, egress); testing failover requires drills and chaos engineering. |
| **Failure mode** | Replication lag during a primary region failure means the standby region is seconds to minutes behind — on promotion, those last writes are lost (RPO is non-zero). In Active-Active, LWW conflicts silently discard legitimate writes if clock skew is not tightly controlled (NTP drift > conflict window). Split-brain during network partition: both regions believe they are primary and diverge — requires a fencing mechanism (STONITH — Shoot The Other Node In The Head — or DynamoDB conditional writes) to prevent. |

---

## 🔬 Interview Q&As

### Q: "What is the difference between Active-Active and Active-Passive multi-region?"

> Active-Active: both regions accept reads and writes simultaneously. No failover delay — traffic is already flowing in both regions. Requires conflict resolution for concurrent writes to the same record. Lower global latency because writes go to the nearest region. Active-Passive: one primary region accepts writes; standby is a read-only replica. On primary failure, standby is promoted (accepts writes). Simpler consistency model (no conflicts). Failover delay = DNS TTL + promotion time (30s–5 minutes). Choose Active-Active for latency-sensitive global services; Active-Passive for consistency-critical services (payments, financial ledgers).

### Q: "What is RPO and RTO, and how do Active-Active vs Active-Passive differ?"

> RPO (Recovery Point Objective) — maximum acceptable data loss measured in time: "we can tolerate losing at most 1 minute of writes." RTO (Recovery Time Objective) — maximum acceptable service downtime: "we must be back up within 5 minutes." Active-Passive: RPO = replication lag at time of failure (typically 1–60 seconds with async replication, 0 with synchronous). RTO = DNS TTL + DB promotion time (30s–5 minutes). Active-Active: RPO ≈ 0 (other region was already serving writes), RTO ≈ 0 (other region already has traffic). Active-Passive with synchronous replication can achieve RPO = 0 but at the cost of adding cross-region round-trip to every write latency (~150ms for US ↔ EU).

### Q: "How does DynamoDB Global Tables handle write conflicts in Active-Active?"

> DynamoDB Global Tables uses LWW (Last Write Wins) — the write with the higher timestamp replaces older writes for the same primary key. If us-east-1 writes `user=alice, email=old@example.com` at T=100ms and eu-west-1 writes `user=alice, email=new@example.com` at T=101ms, the EU write (T=101) wins globally. The US write is silently discarded. To avoid silent data loss: (1) use conditional writes (`version < :newVersion`) so stale writes fail explicitly rather than silently. (2) Design your access patterns so concurrent writes to the same key from different regions are impossible (e.g., a user can only update their profile from their home region). ⭐ **Tier 2 — design**

### Q: "Why can't I just use synchronous cross-region replication everywhere to get RPO = 0?"

> You can — it gives RPO = 0 — but every write must wait for acknowledgment from all regions before returning to the caller. US-to-EU round-trip latency: ~80ms. US-to-APAC: ~180ms. A write that previously took 5ms now takes 85ms or 185ms. Under load, this compounds: if EU is experiencing network degradation (even temporarily), every write globally slows down or times out. Synchronous replication also means the failure of ANY region blocks ALL writes globally until the failed region is removed or failover is triggered. Async replication accepts a small RPO (seconds) in exchange for write latency independence and failure isolation. Choose sync only for strict financial audit logs; use async + conditional writes for everything else. ⭐ **Tier 2 — trade-off**

### Q: "Design a global payments system for 100 countries. Active-Active or Active-Passive?"

> Active-Passive, with synchronous replication. Reasoning: payment operations are idempotent (each payment has a unique idempotency key — a unique identifier ensuring a duplicate request does not result in a duplicate charge) but not associative — charging a card twice causes real financial and reputational harm. LWW conflicts are unacceptable for payment records. Active-Passive with synchronous replication gives RPO = 0. Write latency is higher (add cross-region round-trip for writes), but payment writes are user-initiated and users can tolerate 200–300ms. RTO: configure Aurora Global Database with hot standby, < 1 minute promotion. Stripe uses exactly this model.

### Q: "What happens during a network partition between your two Active-Active regions?"

> This is the CAP theorem partition scenario. During partition: both regions continue accepting writes (A — Availability chosen). The two regions diverge — same keys may receive conflicting writes on both sides. When partition heals, replication catches up and conflict resolution runs (LWW or CRDT). For LWW, one region's writes win; the other's are discarded. For CRDT counters, both sides' increments merge (no loss). To detect and alert on divergence: implement a vector clock (a logical timestamp tracking per-node write history) comparison on replication; alert if divergence exceeds a threshold (e.g., > 100 conflicting writes) so engineers can verify data integrity post-partition. ⭐ **Tier 2 — failure mode**

### Q: "Your Route53 DNS TTL is 300 seconds. A region fails. How long are users affected?"

> Up to 300 seconds (5 minutes), depending on the resolver. DNS TTL is a hint — caching resolvers (ISPs, corporate DNS caches) respect it, but many cache longer. To minimize failover time: (1) Set TTL to 30–60 seconds on your Route53 records before any anticipated maintenance. (2) Use AWS Global Accelerator instead of or in addition to Route53 — it uses Anycast, so failover happens at the BGP level in seconds, not DNS TTL seconds. (3) Implement client-side retry with exponential backoff — even during 30 seconds of DNS propagation, clients retrying after 2 seconds will hit the healthy region. RTO is not just DNS TTL: also includes DB promotion time (30s–60s for Aurora Global), application warm-up, and cache priming. ⭐ **Tier 2 — operations**

### Q: "What is a CRDT and when would you use it over LWW for conflict resolution?"

> A CRDT (Conflict-free Replicated Data Type) is a data structure with mathematically guaranteed conflict-free merging: any two replicas can merge in any order and always produce the same result. Example: G-Counter (Grow-Only Counter) — each node increments only its own slot; merge takes element-wise maximum; global value is the sum. Unlike LWW, no write is ever discarded — every increment is preserved. Use CRDT when: (1) Data loss is unacceptable (view counts, like counts, inventory decrements — every increment matters). (2) The operation is commutative (merge order does not change the outcome: A+B = B+A). Avoid CRDT for: arbitrary object updates (profile photos, email addresses) where commutative merge is not naturally defined. LWW is simpler and sufficient when the "latest value wins" semantics are acceptable (e.g., user's display name — keep the most recent update).

---

## 🧾 TL;DR

> "Multi-region architecture serves users from their nearest region to minimize latency and survives regional failures — Active-Active (both regions accept writes, conflict resolution required, near-zero RTO) is for latency-sensitive global services, Active-Passive (standby replicates, promotes on failure, RTO = DNS TTL + DB promotion) is for consistency-critical services like payments; the two key metrics are RPO (how much data you can afford to lose) and RTO (how long you can be down), and the trade-off between them drives every architectural decision."

---

## 🔗 Related Concepts

- `34-cap-theorem-consistency-models.md` — multi-region directly embodies the CAP theorem: during partition, choose Consistency (Active-Passive) or Availability (Active-Active)
- `38-sharding-strategy.md` — geo sharding and multi-region often compose: data is geo-sharded AND each shard is replicated within its region
- `29-db-replication-failover.md` — single-region replication (primary + replica) is the building block; multi-region extends this across data centers
- `21-leader-election-consensus.md` — Active-Passive failover requires consensus on "which region is primary" to prevent split-brain
- `37-consensus-algorithms-raft-vs-paxos.md` — DynamoDB, Cassandra, and CockroachDB use Raft internally to replicate within a region before cross-region sync

---

## 📚 Further Reading (Optional — after the note)

| Resource | What it adds | Time |
|---|---|---|
| **Netflix Tech Blog — "Active-Active for Multi-Regional Resiliency"** (netflixtechblog.com) | Netflix's actual architecture decisions, trade-offs, and war stories from running Active-Active in production | ~20 min read |
| **AWS Whitepaper — "Disaster Recovery of Workloads on AWS"** (docs.aws.amazon.com) | RPO/RTO trade-off matrix across 4 DR strategies (backup/restore, pilot light, warm standby, multi-site active/active) with cost comparison | ~30 min read |
| **Martin Kleppmann — "Designing Data-Intensive Applications" Chapter 5** (book) | Definitive technical treatment of replication lag, conflict resolution, LWW pitfalls, and CRDTs — this note summarizes; the chapter formalizes | ~40 min read |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 25, 2026 | Created as Concept 40. Active-Active (DynamoDB Global Tables, LWW, conditional writes) and Active-Passive (Route53 failover, Aurora Global Database promotion) with full Java implementations. CRDT G-Counter conflict resolution. GeoDNS Route53 geolocation routing. RPO/RTO quantified in Q&A. Seven Q&As covering Active-Active vs Active-Passive, RPO/RTO trade-offs, synchronous vs async replication, DynamoDB LWW conflict handling, CRDT vs LWW selection, network partition behavior, and DNS TTL failover timing. |
