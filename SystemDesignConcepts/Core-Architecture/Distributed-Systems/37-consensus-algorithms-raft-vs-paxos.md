# 37 — Consensus Algorithms: Raft vs Paxos

## 📖 What is Consensus in Distributed Systems?

**Full form:** Consensus — an algorithm by which multiple nodes in a distributed system agree on a single value (leader election, state replication, distributed locking).

**Simple analogy:** Imagine a village deciding where to build a well. Three families disagree. No central authority. A consensus algorithm lets them vote, and even if one family refuses or can't communicate, the other two agree and build the well. The algorithm guarantees: (1) everyone learns the decision, (2) if reachable, everyone agrees on the same decision, (3) minority can't block majority.

**Core principle:** Consensus algorithms (Raft, Paxos, BFT) ensure that multiple nodes agree on a shared value despite failures and network delays. They're the foundation of leader election, distributed locking, and state machine replication.

**Why it matters in system design:** Every production distributed system uses consensus: Kafka's controller election (Raft), etcd (Raft), Zookeeper (custom Zab algorithm), HBase (Zookeeper-based), Cockroach DB (Raft). Understanding Raft vs Paxos separates engineers who understand consensus from those who just use it.

---

## 🎯 Why This Matters

- **Problem:** How do multiple servers agree on who's the leader? How does a database replica know it's safe to take over if the primary fails?
- **Interview signal:** "Design a leader election system for 5 independent data centers." This reveals whether you know consensus internals.
- **Senior expectation:** You understand Raft's state machine replication well enough to explain why it's simpler than Paxos, and when each applies.

---

## 🧠 The Mental Model

Imagine 5 judges deciding on a verdict with poor communication (network delays, some judges offline).

**Paxos approach (mathematically optimal, hard to implement):**
- Judges vote in multiple rounds. In round 1, judge A proposes "guilty." Others vote to accept or reject. If majority accepts, the proposal is chosen. But what if new judges join mid-voting? Paxos handles this with complex balloting rules. Eventually, everyone converges on one verdict.
- **The problem:** Paxos rules are dense. Implementers disagree on interpretation. Two implementations might diverge slightly.

**Raft approach (simpler, more intuitive):**
- First, elect ONE judge as leader (via voting). Leader proposes "guilty" to others. Others append to their log and ack. Once majority acks, it's committed. If leader dies, remaining judges elect a new leader.
- **The advantage:** Roles are clear (leader, follower). Logic is sequential, not probabilistic. Easier to implement, debug, and teach.

**The key insight:** Paxos = consensus without a leader (more resilient, less efficient). Raft = consensus WITH a leader (simpler, same failure tolerance).

---

## 🎨 Visual — System Topology & Consensus Flow

```
FULL SYSTEM TOPOLOGY:
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│  Node A      │────▶│  Node B      │◀────│  Node C      │
│ (Candidate)  │     │ (Follower)   │     │ (Follower)   │
└──────────────┘     └──────────────┘     └──────────────┘
       ▲                    ▲                    ▲
       │                    │                    │
       │          Leader    │                    │
       └─── election vote ──┼────────────────────┘
            (majority: 3/5)

RAFT STATE MACHINE FLOW (simplified):

┌─────────────────────────────────────────────────────┐
│                  Initial State                      │
│         All nodes = FOLLOWER (no leader)            │
└─────────────────────────────────────────────────────┘
                        ▼
                   Timeout fires
                   (no heartbeat
                    from leader)
                        ▼
┌─────────────────────────────────────────────────────┐
│              Node A Becomes CANDIDATE               │
│  - Increment term number                           │
│  - Request votes from other nodes                  │
│  - Vote for self                                   │
└─────────────────────────────────────────────────────┘
                        ▼
            Nodes B & C vote YES
                        ▼
┌─────────────────────────────────────────────────────┐
│          Node A Becomes LEADER (3/5 majority)      │
│  - Send heartbeat (empty log entries) to followers │
│  - Receive client requests                        │
│  - Replicate log entries to followers             │
└─────────────────────────────────────────────────────┘
                        ▼
     New client request: "SET x = 42"
                        ▼
  ┌────────────────────────────────┐
  │ Leader appends to its log       │
  │ Sends to followers B & C       │
  │ Waits for majority ack         │
  └────────────────────────────────┘
                        ▼
    B & C append & ack (majority!)
                        ▼
  ┌────────────────────────────────┐
  │ Leader COMMITS entry           │
  │ (applies to state machine)     │
  │ Returns "success" to client    │
  └────────────────────────────────┘

KEY INVARIANT:
   1. Only one leader at a time (term number prevents split brain)
   2. Majority of nodes must ack before commit (ensures durability)
   3. If leader fails, followers elect new leader (automatic failover)
   4. All followers apply committed entries in same order (consistency)

PAXOS COMPARISON:
   Paxos: No explicit leader; any node can propose. 3 phases (prepare, promise, accept).
   More resilient (doesn't depend on leader), but complex. Used rarely (Google Chubby).
   Raft: Explicit leader; simpler phases (leader sends, followers append, commit). 
   Used everywhere (Kafka, etcd, Consul, Redis Cluster).
```

---

## ⚙️ How It Actually Works

**Raft Algorithm (simplified):**

1. **Election phase:**
   - Nodes start as followers. Election timeout (150-300ms) fires if no heartbeat.
   - Follower becomes candidate, increments term number, requests votes.
   - If candidate receives votes from majority (3 out of 5), becomes leader.
   - New leader sends heartbeats to all followers.

2. **Replication phase:**
   - Client sends request to leader: "SET key = value"
   - Leader appends entry to its log, sends to all followers
   - Followers append to their logs, acknowledge
   - Leader waits for majority acks (including itself)
   - Once majority acks, leader commits (applies to state machine)
   - Leader notifies followers to commit

3. **Safety guarantees:**
   - **Election safety:** Only one leader per term (term number prevents split-brain)
   - **Leader append-only:** Leader's log is append-only (no deletions, overwrites)
   - **Log matching:** If two logs have entry at same index + term, all entries before are identical
   - **Leader completeness:** Leader always contains all committed entries

**Code example — Raft leader election (simplified Java):**

```java
public class RaftNode {
    private enum State { FOLLOWER, CANDIDATE, LEADER }
    
    private State state = State.FOLLOWER;
    private int currentTerm = 0;
    private String votedFor = null; // Voted for whom in this term
    private List<RaftLogEntry> log = new ArrayList<>();
    private int commitIndex = 0;
    private Timer electionTimer;
    private Timer heartbeatTimer;
    
    public void startElectionIfTimeout() {
        // Called when election timer fires (no heartbeat from leader)
        if (state == State.FOLLOWER) {
            becomeCandidate();
        }
    }
    
    private void becomeCandidate() {
        state = State.CANDIDATE;
        currentTerm++; // Increment term for new election
        votedFor = this.nodeId; // Vote for self
        
        // Request votes from other nodes
        int votes = 1; // Count self
        for (RaftNode peer : peers) {
            RequestVoteRequest request = new RequestVoteRequest(
                currentTerm,
                this.nodeId,
                log.size() - 1, // lastLogIndex
                currentTerm // lastLogTerm
            );
            RequestVoteResponse response = peer.requestVote(request);
            
            if (response.voteGranted) {
                votes++;
            }
            
            if (response.term > currentTerm) {
                // Higher term seen; become follower
                currentTerm = response.term;
                state = State.FOLLOWER;
                return;
            }
        }
        
        // Check if we have majority
        if (votes > (peers.size() + 1) / 2) {
            becomeLeader();
        }
    }
    
    private void becomeLeader() {
        state = State.LEADER;
        // Send heartbeats to all followers
        for (RaftNode peer : peers) {
            sendHeartbeat(peer);
        }
    }
    
    private void sendHeartbeat(RaftNode peer) {
        // Empty AppendEntries RPC (heartbeat)
        AppendEntriesRequest request = new AppendEntriesRequest(
            currentTerm,
            this.nodeId,
            log.size() - 1, // prevLogIndex
            currentTerm, // prevLogTerm
            Collections.emptyList(), // no new entries (heartbeat)
            commitIndex
        );
        peer.appendEntries(request);
    }
    
    public void receiveClientRequest(String key, String value) {
        if (state != State.LEADER) {
            throw new NotLeaderException("Redirect to leader");
        }
        
        // Append to log
        RaftLogEntry entry = new RaftLogEntry(currentTerm, key, value);
        log.add(entry);
        
        // Replicate to followers
        int acks = 1; // Count self
        for (RaftNode peer : peers) {
            AppendEntriesRequest request = new AppendEntriesRequest(
                currentTerm,
                this.nodeId,
                log.size() - 2, // prevLogIndex (before this entry)
                currentTerm, // prevLogTerm
                List.of(entry),
                commitIndex
            );
            AppendEntriesResponse response = peer.appendEntries(request);
            if (response.success) {
                acks++;
            }
        }
        
        // Wait for majority acks
        if (acks > (peers.size() + 1) / 2) {
            commitIndex++; // Commit this entry
            applyToStateMachine(entry);
        }
    }
    
    private void applyToStateMachine(RaftLogEntry entry) {
        // Apply to state machine (e.g., key-value store)
        stateMachine.set(entry.key, entry.value);
    }
}
```

---

## 🏢 Real World — Where Companies Use This

- **Apache Kafka (Raft for controller):** Kafka uses Raft (via KIP-500) to elect the broker that acts as controller. Manages topic metadata, leadership. Previously used Zookeeper; now native Raft for simplicity.

- **etcd (CoreOS, Kubernetes):** Kubernetes stores all cluster state in etcd. etcd uses Raft to replicate state across 3+ nodes. When etcd leader fails, followers elect a new leader in < 1 second. No data loss.

- **Redis Cluster (Raft-inspired):** Redis Cluster uses a Raft-like election (RedisCluster gossip protocol). When primary node fails, replicas vote to determine new primary. Automatic failover.

- **Consul (HashiCorp):** Service discovery and distributed configuration. Uses Raft. Consuls replicate service registrations across data centers via Raft. When Consul leader fails, replicas elect a new leader.

- **CockroachDB (Distributed database):** Uses Raft for every range of data. Multiple replicas of each range use Raft to stay in sync. When a replica fails, Raft automatically rebalances.

---

## 🧭 When to Use vs When NOT to Use

| Use Raft when | Use Paxos when |
|---|---|
| Building a leader-based replicated system (database, cache, config store) | You need Byzantine fault tolerance (3+ independent authorities, malicious nodes possible) |
| You want intuitive implementation (clear state machine) | You already have Paxos experts on staff (rare) |
| Failure tolerance < N/2 (N = total nodes) is acceptable | You need maximum resilience (leader independence) |
| | You're Google/Meta (have Paxos experts) |

**The common mistake:** Trying to implement Raft or Paxos from scratch. Use battle-tested libraries: etcd (Raft), Consul (Raft), Zookeeper (Zab). Don't roll your own — consensus is hard.

---

## ⚠️ Trade-offs

| | |
|---|---|
| **You gain (Raft)** | Simplicity (compared to Paxos); strong leader guarantees consistency; automatic failover; widely implemented |
| **You lose (Raft)** | Depends on leader being reachable (partition = new election delay); not Byzantine-fault-tolerant |
| **You gain (Paxos)** | Leader-independent (more resilient to partitions); Byzantine-fault-tolerant variants exist |
| **You lose (Paxos)** | Complex to understand and implement; slower (multiple rounds); rarely used in practice |
| **Failure mode (Raft)** | Network partition isolates leader. Followers can't reach leader, elect new leader. Brief window (< 1s) of no progress. |
| **Failure mode (Paxos)** | Multiple rounds can deadlock if phase 1 is interrupted. Recovery requires manual intervention or higher-level protocol. |

---

## 🔬 Interview Q&As

### Q: "How does Raft elect a leader? What stops two leaders from being elected?"

> Term numbers prevent split brain. When a node becomes candidate, it increments term number. Other nodes reject votes from lower-term candidates. Only the candidate with highest term who receives majority votes becomes leader. If two regions partition, each tries to elect. One region has majority (> N/2), elects leader. The other region (minority) cannot reach majority quorum, stays leaderless. When partition heals, the minority region accepts the majority's leader.

### Q: "What if the leader fails during replication? Do we lose data?"

> Depends on whether followers have acked. If leader crashes after receiving ack from majority (but before committing), followers still have the entry. New leader is elected and replicates to other followers. Eventually, all replicas converge on the committed entries. Data loss = unlikely if quorum size is odd (3, 5, 7 nodes). Single node = total data loss.

### Q: "Why do we need heartbeats in Raft? Why not just wait for client requests?"

> Without heartbeats, if no client request arrives, followers don't know if the leader is alive or crashed. Election timeout fires, followers become candidates and trigger unnecessary elections. Heartbeats (empty AppendEntries) keep followers informed. If heartbeat doesn't arrive, it signals leader failure, triggering election.

### Q: "How does Raft handle network partitions?"

> When partition occurs, nodes on both sides try to elect leaders. Partition with majority (> N/2 nodes) successfully elects leader. Minority partition cannot reach quorum, no leader elected. Majority side continues operating. Minority side returns errors ("no leader"). When partition heals, minority accepts majority's leader. No split-brain because majority quorum is mandatory.

### Q: "Paxos vs Raft — when should we use Paxos?"

> Raft is used 99% of the time because it's simpler. Paxos is used only if: (1) You need Byzantine fault tolerance (malicious nodes) — Raft isn't Byzantine-safe. (2) You have Paxos experts (rare). (3) Your system must survive complete leader isolation indefinitely (Paxos allows multiple proposers, but slower). For most systems, Raft + replication is sufficient.

### Q: "In a Raft cluster of 5 nodes, what's the quorum size?"

> Quorum = > (N / 2) = > 2.5 = 3 nodes. For leader election, a candidate needs 3 votes (including self). For committing log entries, leader needs 3 acks (including self). This means: cluster can tolerate 2 node failures (3 + 2 = 5). If only 2 nodes survive, they cannot reach quorum, no leader can be elected.

### Q: "How do we know if an entry is committed in Raft?"

> Leader tracks how many followers have each entry. Once > (N/2) followers have acknowledged an entry, leader can commit it. Committed entries are safe — they're on majority, so even if leader fails and new leader is elected, all new leaders will include these entries. For clients, leader waits until entry is committed before responding "success."

---

## 🧾 TL;DR

> "Raft elects a leader via majority voting (term numbers prevent split-brain). Leader replicates log entries to followers. Once majority acknowledges, entry is committed and applied to state machine. If leader fails, followers elect new leader. No consensus algorithm is leaderless without complexity (Paxos); Raft accepts leader-based simplicity."

---

## 🔗 Related Concepts

- **Leader Election (21):** This note is the deep-dive on HOW leader election algorithms work
- **Distributed Locking (06):** Consensus is used to implement distributed locks (Zookeeper-based locks)
- **CAP Theorem (34):** Consensus algorithms typically choose CP (Consistency + Partition Tolerance) at cost of Availability during partitions

---

## 📚 Further Reading (Optional — after the note)

| Resource | What it adds | Time |
|---|---|---|
| **"The Raft Consensus Algorithm" — Diego Ongaro & John Ousterhout** (raft.github.io) | Original Raft paper with clear diagrams and proofs. This note summarizes; the paper formalizes. | ~40 min (paper) |
| **"Paxos Made Simple" — Leslie Lamport** (lamport.azurewebsites.net) | Lamport's simplified explanation of Paxos. For context only — Raft is preferred unless Byzantine required. | ~30 min |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 25, 2026 | Initial creation. Added Raft election flow (state machine: follower → candidate → leader), log replication, term numbers preventing split-brain. Code example for RequestVote RPC and AppendEntries replication. Real-world examples (Kafka, etcd, Redis, Consul, CockroachDB). Seven Q&As covering quorum calculation, partition handling, Byzantine-fault tolerance, Raft vs Paxos trade-off. |
