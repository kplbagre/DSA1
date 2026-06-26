# Leader Election & Consensus — Fundamentals

---

## 🎯 Why This Matters

You have 3 database replicas. Which one is the primary (handles writes)? If the primary crashes, how do the other 2 decide who takes over — without both becoming primary (split-brain disaster). Consensus algorithms ensure all nodes agree on a single truth, even if one node is unreachable. At SDE 3: you must know Raft basics, quorum voting, and when you need consensus (databases, distributed locks, service registries).

---

## 📖 What is Leader Election?

**Full form:** Leader Election / Consensus Algorithm (Raft, Paxos, etc.)

**Simple analogy:** A group of friends needs to decide on a restaurant, but one friend might be unreachable (network failure). Instead of calling everyone independently (conflicting decisions), they use a voting protocol: elect one friend as coordinator, they propose a restaurant, others vote, majority wins. If the coordinator goes silent, they elect a new one.

**Core principle:** In distributed systems, multiple replicas (database nodes, service instances) need to **agree on a single source of truth**. A consensus algorithm (Raft, Paxos) ensures all nodes agree on the same decision, even if some nodes are down or unreachable. A **leader** (or coordinator) is elected to make decisions; other nodes follow its instruction and replicate its state.

**Key concept — Quorum:** Decisions require a majority vote (quorum), ensuring no two leaders can emerge (preventing split-brain).

**Why it matters in system design:** Without consensus, distributed systems can diverge into inconsistent states (e.g., two database primaries both accepting writes, causing data loss). Consensus guarantees correctness under partial failures.

---

## 🎨 Visual — System Topology: Leader Election in Architecture

```
DISTRIBUTED DATABASE / SERVICE CLUSTER
┌──────────────────────────────────────────────────────────┐
│                                                          │
│  Node 1 (Primary)              Node 2 (Replica)          │
│  Role: LEADER                  Role: FOLLOWER            │
│  ┌──────────────────────────┐  ┌──────────────────────┐ │
│  │ Accepts writes/reads      │  │ Accepts reads only    │ │
│  │ Replicates to followers   │  │ Replicates from      │ │
│  │ Sends heartbeats: "I'm   │  │ leader (append log)  │ │
│  │ alive every 150ms"        │  │ Votes in election    │ │
│  └────────┬──────────────────┘  └──────────┬──────────┘ │
│           │                                 │            │
│           │ Heartbeat + Log replication     │            │
│           │ (every 150ms)                   │            │
│           ▼                                 ▼            │
│  ┌────────────────────────────────────────────────────┐ │
│  │ Log: [Entry1, Entry2, Entry3, ...]                │ │
│  │ (same across all replicas when committed)         │ │
│  └────────────────────────────────────────────────────┘ │
│                                                          │
│  Node 3 (Replica)                                       │
│  Role: FOLLOWER                                         │
│  ┌──────────────────────────────────────────────────┐  │
│  │ Follows same protocol as Node 2                 │  │
│  └──────────────────────────────────────────────────┘  │
│                                                          │
│  ─────────────────────────────────────────────────     │
│  QUORUM = 2 out of 3 nodes                             │
│  (Partition with ≥2 nodes can commit; single node      │
│   cannot commit, preventing split-brain)              │
│                                                          │
└──────────────────────────────────────────────────────────┘

ELECTION TRIGGER: If Node 1 (leader) crashes
  → Nodes 2 & 3 notice heartbeat timeout
  → New election: Node 2 or 3 runs and becomes leader
  → New leader sends heartbeats; system continues
```

---

## 🎨 Visual — Leader Election & Quorum Voting (Component Detail)

Imagine a jury of 5 judges voting on a verdict:

- **Naive approach:** All 5 judges independently vote. If they disagree, who's right? Chaos.
- **Consensus approach (Raft):** One judge is elected **leader**. The leader proposes a verdict. Other judges vote on it. If 3 out of 5 agree (quorum), it's decided. The verdict is written to all 5 judges' records.
- **Leader crash:** If the leader dies mid-proposal, the other judges notice (heartbeat timeout = leader hasn't checked in for 3 seconds). They hold a new election. A new judge is elected. Any verdict that wasn't yet committed (didn't get 3+ votes) is discarded.
- **Split-brain prevention:** If 3 judges are in room A (leader is here) and 2 judges in room B (network partition), room A continues (3 > 5/2). Room B stops (2 ≤ 5/2). No two leaders. No conflicting decisions.

**The key insight:** Consensus requires a **quorum** (majority) to commit a decision. A single node cannot decide; it must ask others. This is expensive but guarantees correctness under failures.

---

## 🎨 Visual — Leader Election & Quorum Voting

```
3 DATABASE REPLICAS (initial state):
┌────────────┐    ┌────────────┐    ┌────────────┐
│ Node 1     │    │ Node 2     │    │ Node 3     │
│ Role: ?    │    │ Role: ?    │    │ Role: ?    │
│ Term: 1    │    │ Term: 1    │    │ Term: 1    │
└────────────┘    └────────────┘    └────────────┘
        ↓              ↓                  ↓
    [Election Phase]
    No leader detected; heartbeat timeout

Node 1 runs for leader:
    "Vote for me! I have the latest data."
    Sends RequestVote to Nodes 2 & 3

┌──────────────────────────────────────────────┐
│ Node 1 (Candidate)                           │
│ ↓ votes for self                             │
│ Votes: 1/3 (need 2 for quorum)               │
│ Waiting for votes from 2 & 3                 │
└──────────────────────────────────────────────┘

Node 2 checks: "Does Node 1 have latest data? Yes."
    → votes for Node 1

Node 3 checks: "Does Node 1 have latest data? Yes."
    → votes for Node 1

┌──────────────────────────────────────────────┐
│ Node 1 Elected LEADER                        │
│ Votes: 3/3 (quorum achieved!)                │
│ Term: 2 (new term started)                   │
│ Role: LEADER                                 │
│                                              │
│ Nodes 2 & 3 become FOLLOWERS                 │
└──────────────────────────────────────────────┘

Node 1 now sends heartbeats every 150ms:
    "I'm alive. All good."
    Nodes 2 & 3 reset their timeout timers

WRITE OPERATION (on Leader):
  Client: "Commit: Balance = $1000"
    ↓
  Node 1 (Leader):
    - Append to own log: [... $1000]
    - Send to Nodes 2 & 3: "Replicate this"
    ↓
  Nodes 2 & 3 (Followers):
    - Append to own log: [... $1000]
    - Reply: "Received"
    ↓
  Node 1:
    - Receives 2 confirmations (quorum!)
    - Commits: [... $1000] ← permanent
    - Replies to client: "OK"
    - Sends next heartbeat: "Entry #5 is committed"
    ↓
  Nodes 2 & 3:
    - Receive "Entry #5 is committed"
    - Commit: [... $1000] ← permanent on all

LEADER CRASH:
  Node 1 crashes ❌
  Nodes 2 & 3 don't receive heartbeat
  After 3 second timeout:
    ↓
  Nodes 2 & 3 run new election
  (same process repeats; new leader elected)
  ↓
  Entries committed on quorum survive
  Entries NOT committed are discarded/re-proposed

SPLIT-BRAIN PREVENTION (Network Partition):
  ┌─────────────┐         ┌─────────────┐
  │ Nodes 1,2   │ ========X======== │ Node 3      │
  │ Partition A │         (no link) │ Partition B │
  └─────────────┘                   └─────────────┘

  Partition A (2 nodes): 2 < 3/2? No, continue
  Node 1 is leader, can commit
  Client writes: replies OK

  Partition B (1 node): 1 < 3/2? Yes, stop
  Node 3 refuses writes: "I don't have quorum"
  Client gets error

  Network heals:
    Nodes synchronize logs
    Entries from Partition A (committed on quorum) are canonical
    Node 3 discards any uncommitted entries it had

KEY INVARIANT:
   Quorum > N/2 ensures at most ONE leader at a time
   Committed entries survive failures
   Uncommitted entries may be lost on rebalance
   Heartbeat timeout triggers election; too short = flapping
```

---

## ⚙️ How It Actually Works

**Pattern 1: Raft Election**

**Steps:**
1. Each node starts as a **follower**.
2. If a follower doesn't hear from the leader for an **election timeout** (150–300ms), it becomes a **candidate** and runs for leader.
3. Candidate sends **RequestVote** to all other nodes, including its term and log information.
4. If a node's log is at least as up-to-date as the candidate's, it votes for the candidate.
5. First candidate to get a **quorum** (majority) of votes becomes leader.
6. Leader sends **heartbeats** (empty AppendEntries) periodically to prevent elections.

```java
// Simplified Raft implementation (conceptual, not production)
public class RaftNode {
    private NodeRole role = NodeRole.FOLLOWER;
    private int currentTerm = 0;
    private int votedFor = -1;
    private List<LogEntry> log = new ArrayList<>();
    private long lastHeartbeatTime = System.currentTimeMillis();
    private static final long ELECTION_TIMEOUT = 300; // ms
    private static final long HEARTBEAT_INTERVAL = 150; // ms

    private int nodeId;
    private List<RaftNode> otherNodes;

    // Step 2 — check for election timeout
    public void tick() {
        long now = System.currentTimeMillis();

        if (role == NodeRole.FOLLOWER || role == NodeRole.CANDIDATE) {
            if (now - lastHeartbeatTime > ELECTION_TIMEOUT) {
                // Step 2 — become candidate, start election
                startElection();
            }
        } else if (role == NodeRole.LEADER) {
            // Step 6 — send heartbeats
            if (now - lastHeartbeatTime > HEARTBEAT_INTERVAL) {
                sendHeartbeats();
            }
        }
    }

    // Step 2-5 — election
    private void startElection() {
        role = NodeRole.CANDIDATE;
        currentTerm++;
        votedFor = nodeId; // Step 3 — vote for self

        int votes = 1; // self vote

        // Step 3 — send RequestVote to all nodes
        for (RaftNode node : otherNodes) {
            RequestVoteResponse response = node.requestVote(
                currentTerm,
                nodeId,
                log.size(),
                log.isEmpty() ? 0 : log.get(log.size() - 1).term
            );

            // Step 4 — count votes
            if (response.voteGranted) {
                votes++;
            }
        }

        // Step 5 — check quorum
        int quorumSize = (otherNodes.size() + 1) / 2 + 1;
        if (votes >= quorumSize) {
            becomeLeader();
        }
    }

    // Step 5 — on quorum achieved
    private void becomeLeader() {
        role = NodeRole.LEADER;
        lastHeartbeatTime = System.currentTimeMillis();
        System.out.println("Node " + nodeId + " became LEADER in term " + currentTerm);
    }

    // Step 6 — leader sends heartbeats
    private void sendHeartbeats() {
        for (RaftNode node : otherNodes) {
            node.appendEntries(
                currentTerm,
                nodeId,
                log.size() - 1, // prevLogIndex
                log.isEmpty() ? 0 : log.get(log.size() - 1).term, // prevLogTerm
                new ArrayList<>(), // empty for heartbeat
                0 // leaderCommit
            );
        }
    }

    // Follower receives RequestVote
    public RequestVoteResponse requestVote(int term, int candidateId, int lastLogIndex, int lastLogTerm) {
        // Step 4 — check if candidate is up-to-date
        if (term < currentTerm) {
            return new RequestVoteResponse(term, false); // outdated term
        }

        // Check log currency
        int myLastLogTerm = log.isEmpty() ? 0 : log.get(log.size() - 1).term;
        boolean logAtLeastAsUpToDate = (lastLogTerm > myLastLogTerm) ||
                                       (lastLogTerm == myLastLogTerm && lastLogIndex >= log.size());

        if (logAtLeastAsUpToDate && (votedFor == -1 || votedFor == candidateId)) {
            votedFor = candidateId; // Step 4 — grant vote
            return new RequestVoteResponse(currentTerm, true);
        }

        return new RequestVoteResponse(currentTerm, false);
    }

    // Follower receives heartbeat
    public void appendEntries(int term, int leaderId, int prevLogIndex, int prevLogTerm,
                             List<LogEntry> entries, int leaderCommit) {
        // Step 1 — reset heartbeat timer
        lastHeartbeatTime = System.currentTimeMillis();

        if (term < currentTerm) {
            return; // ignore from old leader
        }

        // Step 2 — acknowledge leadership
        role = NodeRole.FOLLOWER;
        currentTerm = term;
        votedFor = leaderId;
    }

    enum NodeRole {
        FOLLOWER, CANDIDATE, LEADER
    }

    static class LogEntry {
        int term;
        Object value;
    }

    static class RequestVoteResponse {
        int term;
        boolean voteGranted;

        RequestVoteResponse(int term, boolean voteGranted) {
            this.term = term;
            this.voteGranted = voteGranted;
        }
    }
}
```

---

**Pattern 2: Consensus via Zookeeper (for service coordination)**

**Steps:**
1. Service publishes its existence to Zookeeper: `/election/candidate-1`, `/election/candidate-2`, etc.
2. Zookeeper automatically creates an ordered list (sequential znodes).
3. Service watches the node before itself in the list.
4. When that node is deleted, Zookeeper notifies: "You're next in line."
5. First service in the list is the leader.

```java
// Zookeeper-based Leader Election
@Component
public class ZookeeperLeaderElection {
    private final CuratorFramework curator;
    private final LeaderLatch leaderLatch;

    public ZookeeperLeaderElection(CuratorFramework curator) {
        this.curator = curator;
        // Step 1 — create leader latch (automatic election)
        this.leaderLatch = new LeaderLatch(
            curator,
            "/myapp/leader",     // path
            "node-1"             // participant ID
        );
    }

    @PostConstruct
    public void start() throws Exception {
        // Step 2-4 — Zookeeper handles election
        leaderLatch.start();

        // Step 5 — watch if I'm the leader
        leaderLatch.addListener(new LeaderLatchListener() {
            @Override
            public void isLeader() {
                System.out.println("I am the LEADER!");
                startLeadingTasks();
            }

            @Override
            public void notLeader() {
                System.out.println("Not leader anymore. Stopping tasks.");
                stopLeadingTasks();
            }
        });
    }

    public boolean isLeader() {
        return leaderLatch.hasLeadership();
    }

    private void startLeadingTasks() {
        // Only leader runs these
        // e.g., periodic job, rebalancing, etc.
    }

    private void stopLeadingTasks() {
        // Stop leadership tasks
    }

    @PreDestroy
    public void stop() throws Exception {
        leaderLatch.close();
    }
}
```

---

**What are Quorum, Term, and Heartbeat, and why do they fit here?**

- **Quorum:** A majority (> N/2) of nodes. In 5 nodes, quorum = 3. Ensures only one leader can exist; any two quorums overlap. In an interview: *"Quorum prevents split-brain — two partitions can't both think they're the leader."*

- **Term:** A logical clock. Each election increments the term. Nodes reject requests from outdated terms. Prevents old leaders from conflicting with new ones. In an interview: *"Terms ensure old leaders don't overwrite decisions made by new leaders."*

- **Heartbeat:** Periodic "I'm alive" message from leader. If followers don't receive it, they assume leader is dead and start election. In an interview: *"Heartbeat timeout controls how fast the cluster detects a leader failure — faster timeout = faster recovery but risk of flapping."*

---

## 🏢 Real World — Where Companies Use This

- **etcd (Kubernetes):** Raft-based consensus for cluster configuration. All API servers read from the same etcd; changes replicate to quorum. If a node crashes, others continue.
- **Consul (HashiCorp):** Raft for service registry. 3 servers (typical), quorum = 2. Losing 1 server is fine; losing 2 = read-only mode.
- **Apache Kafka:** Broker leader election for each partition. Zookeeper or KRaft (Kafka Raft) determines who the leader is. If leader crashes, quorum elects a new one.
- **CockroachDB:** Raft for distributed transactions across multiple regions. Quorum spanning 3 regions ensures fault tolerance.

---

## 🧭 When to Use vs When NOT to Use

| Use consensus when | Do NOT use when |
|---|---|
| You have multiple replicas and need to agree on state (database replication, distributed locks) | Single point of failure is acceptable (single-node database) |
| Split-brain would be catastrophic (conflicting writes to two primaries) | Eventual consistency is fine (cache invalidation) |
| Failures are expected and must be tolerated | Failures are rare and manual recovery is OK |
| You have 3+ nodes (odd numbers; 2 is not quorum-safe) | You have only 1–2 nodes |

**The common mistake:** Using consensus for everything. Consensus is expensive (quorum writes must wait for majority). Use it for critical data (schema, configuration, leader election). Don't use it for application data that can be cached/sharded.

---

## ⚠️ Trade-offs

| | |
|---|---|
| **You gain** | Fault tolerance (survive N-1 failures with N nodes). Split-brain prevention. Strong consistency (quorum writes are durable). |
| **You lose** | Latency (write must wait for quorum acknowledgment, typically 10–100ms added). Complexity (election timeouts, term management, log replication). Reduced availability (3 nodes: lose 1 = 66% available; lose 2 = 0% available). |
| **Failure mode** | Split-brain on network partition (rare but catastrophic if not handled). Election flapping if timeout is too aggressive. If quorum is lost (> half nodes down), entire cluster is read-only. |

---

## 🔬 Interview Q&As

### Q: "Why does Raft require an odd number of nodes (3, 5, 7)?"

> Quorum requires > N/2 votes. With even N (e.g., 4 nodes), quorum = 3. If network partitions 2-2, neither side has 3 nodes; both are blocked (correct). But 3 nodes is smaller than 4 and more efficient. Odd numbers give better fault tolerance: 3 nodes tolerate 1 failure (quorum = 2); 4 nodes also tolerate 1 failure (quorum = 3) but use 1 extra node. So odd is more efficient. General rule: use 3 (tolerate 1 failure), 5 (tolerate 2), or 7 (tolerate 3) nodes. ⭐ **Tier 2 — conceptual**

### Q: "Your Raft cluster keeps flapping (repeatedly electing new leaders). Why?"

> Election timeout is too aggressive (e.g., 50ms) relative to network latency or GC pause. A follower doesn't receive a heartbeat for 60ms (GC), thinks leader is dead, starts election. Meanwhile, the leader is still alive, sends a heartbeat, and another election starts. Solution: increase election timeout to 150–300ms, or fix underlying GC (tune heap, use low-latency GC). Also: check network latency — if average latency is 100ms, minimum election timeout should be 300ms. ⭐ **Tier 2 — operational**

### Q: "How does Raft handle split-brain (network partition)?"

> In a 5-node cluster partitioned 3-2: the 3-node partition has quorum and continues as the leader. The 2-node partition doesn't have quorum and stops accepting writes (reads work but returns stale data). Clients in the 2-node partition get "leader not found" errors. Once the network heals, the 2-node partition syncs with the 3-node partition and catches up. No conflicting writes because the 2-node partition never became leader. ⭐ **Tier 2 — failure handling**

### Q: "Design a distributed lock using Zookeeper and quorum."

> Clients create an ephemeral sequential znode under `/locks`: `/locks/lock-001`, `/locks/lock-002`, etc. Each client watches the lock before itself. When it's deleted (client releases or crashes), Zookeeper notifies the next client. First client in the ordered list holds the lock. Zookeeper's quorum ensures all replicas agree on the lock order. If a node crashes, quorum continues; lock is safe. Recovery: Zookeeper removes the crashed client's ephemeral znode, and next client is notified. ⭐ **Tier 2 — system design**

### Q: "What's the difference between Raft and Paxos?"

> Both solve consensus; Raft is simpler to understand and implement. Raft has clearer separation (leader election → log replication). Paxos is more general and flexible but harder to reason about. In practice: Raft is used in etcd, Kafka, CockroachDB. Paxos is used in Google Chubby, Zookeeper. For an interview: know Raft deeply; mention Paxos as an alternative but don't dive in unless asked. ⭐ **Tier 2 — comparative**

### Q: "Your Raft cluster just lost 2 out of 5 nodes. What happens?"

> 2 out of 5 = 3 nodes left. 3 > 5/2 = quorum still achievable. Cluster continues to operate. If a 3rd node crashes (only 2 left), quorum is lost: 2 ≤ 5/2? The 2 nodes become read-only. New writes are rejected until a node recovers. This is why 5 nodes is common in production: tolerate 2 failures and still run; 3 failures = read-only. ⭐ **Tier 2 — availability**

---

## 🧾 TL;DR

> "Consensus algorithms (Raft, Paxos) ensure all replicas agree on state using quorum voting. Leader is elected when followers don't receive heartbeats. Quorum > N/2 prevents split-brain. Use for databases, service registries, distributed locks. Trade-off: strong consistency for latency."

---

## 🔗 Related Concepts

- **`18-service-discovery-dns.md`** — service registries (Zookeeper, Consul, etcd) use consensus internally
- **`16-connection-pooling-db-performance.md`** — database replication uses consensus for leader election
- **`06-distributed-locking.md`** — distributed locks often use consensus (Zookeeper) as backend
- **`07-cdc-outbox.md`** — CDC systems use consensus to track LSN (log sequence number) across replicas

---

## 📚 Further Reading (Optional — after the note)

| Resource | What it adds | Time |
|---|---|---|
| **Arpit Bhayani — "Raft Consensus Algorithm"** (YouTube) | Deep dive on Raft state machine, log replication, term management | ~20 min |
| **The Raft Consensus Algorithm Paper** (raft.github.io) | Official specification; readable even for non-academics | ~30 min read |
| **ByteByteGo — "Leader Election in Distributed Systems"** (YouTube) | Visual walkthrough with animations, quorum voting | ~8 min |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 25, 2026 | Created as Concept 21. Covered Raft election, terms, quorum voting, split-brain prevention, Zookeeper leader latch pattern. |
