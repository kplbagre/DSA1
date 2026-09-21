# 08 — Mental Model: System Design
### Key design decisions in this app — the WHY behind every choice

> **Read time:** 35 min
> **Companion:** Arpit Bhayani's Cassandra video (watch for deeper intuition)
> **Goal:** Answer every "why not X?" question with a clear trade-off.
> These are the questions that separate junior from senior in interviews.

---

## Index

| # | Section | What you get |
|---|---|---|
| 0 | [The Senior Engineer Mindset](#the-senior-engineer-mindset) | How to frame every design answer |
| 1 | [Cassandra for Conversation Storage](#design-decision-1--cassandra-for-conversation-storage) | Why Cassandra over PostgreSQL for memory |
| 2 | [Milvus for Vector Storage](#design-decision-2--milvus-for-vector-storage) | Why vector DB, how collections are isolated per tenant |
| 3 | [SSE vs WebSocket for Chat](#design-decision-3--sse-vs-websocket-for-chat) | The streaming protocol decision + when each is right |
| 4 | [The Transactional Memory Pattern](#design-decision-4--the-transactional-memory-pattern) | Save message first, then run LLM — why it matters |
| 5 | [Thread-Local for Tenant Context](#design-decision-5--thread-local-for-tenant-context) | Multi-tenancy isolation without shared state |
| 6 | [In-Memory Tool Results (Not Persisted)](#design-decision-6--in-memory-tool-results-not-persisted) | Why tool outputs stay in LLM context, not the DB |
| 7 | [Java Over Python](#design-decision-7--java-over-python) | The honest trade-off: ecosystem vs operational simplicity |
| 8 | [gpt-oss-120b (Internal Model)](#design-decision-8--gpt-oss-120b-internal-model-vs-public-api) | Why internal Azure OpenAI vs public API |
| 9 | [Quick Trade-off Reference](#the-quick-trade-off-reference) | One-line summary of every decision |
| 10 | [Interview-Ready Summaries](#interview-ready-summaries) | Scripts for the most likely design questions |

---

## The Senior Engineer Mindset

Before any design question, always think in 3 parts:

```
  ┌─────────────────────────────────────────────────────┐
  │  1. WHAT DID WE NEED?   (the requirement/constraint) │
  │  2. WHY DOES X FIT?     (why this choice works)     │
  │  3. WHAT DID WE GIVE UP? (the trade-off)            │
  └─────────────────────────────────────────────────────┘

  "We chose X because we needed Y.
   It gave us Z.
   The trade-off was W."

  Answers without trade-offs sound junior.
  Answers with trade-offs sound senior.
```

---

## Design Decision 1 — Cassandra for Conversation Storage

### The Requirement

```
WHAT HAPPENS IN THIS APP:

  Thousands of concurrent users
  Each message = 1 write
  Each AI response = 1 write
  Each session has 10-30 exchanges

  = THOUSANDS OF WRITES PER SECOND

  Is that a lot? Compare:

  Regular blog app:    10-100 writes/min   → PostgreSQL is fine
  This chat app:       1,000+ writes/sec   → Need something different
```

### Why Cassandra Fits

```
  CASSANDRA'S SUPERPOWER: Horizontal Write Scaling

  ┌────────────────────────────────────────────────────────┐
  │                                                        │
  │  POSTGRESQL (vertical scaling):                        │
  │                                                        │
  │  More users → server gets slower                       │
  │  Solution:   Buy a bigger server 💰                    │
  │  Limit:      There's a ceiling. Can't keep upgrading.  │
  │                                                        │
  │  ──────────────────────────────────────────────────    │
  │                                                        │
  │  CASSANDRA (horizontal scaling):                       │
  │                                                        │
  │  More users → Add more nodes                           │
  │  ┌──────┐ ┌──────┐      ┌──────┐ ┌──────┐ ┌──────┐   │
  │  │Node 1│ │Node 2│  →   │Node 1│ │Node 2│ │Node 3│   │
  │  └──────┘ └──────┘      └──────┘ └──────┘ └──────┘   │
  │                                                        │
  │  Writes spread across all nodes automatically.         │
  │  Add nodes = handle more. Linearly.                    │
  │                                                        │
  └────────────────────────────────────────────────────────┘
```

### Access Pattern Match

```
  OUR QUERY PATTERN:
  "Get all messages for session MX_pns:hvgqan:sess_123"

  This is a PARTITION KEY lookup — Cassandra's strongest move.

  Cassandra stores all data for one partition key together.
  This query = 1 disk seek. Extremely fast.

  Compare: "Get all sessions that mentioned carrier CM789"
  → That's a full-table scan → Cassandra is terrible at this.
  → We don't do this query. Analytics goes elsewhere.
```

### Trade-off

```
  ✅ GAINED:
    → Thousands of writes/second, no bottleneck
    → Horizontal scale: add nodes as users grow
    → Team already runs MCSE on Cassandra — no new ops burden

  ❌ GAVE UP:
    → No JOINs (can't do "find all sessions where user asked about CM789")
    → No complex SQL analytics
    → Eventual consistency (in some configs)
    → Analytics dashboard can't query conversation data directly
```

**Interview answer:**
> "Write-heavy, time-series data at scale — that's Cassandra's sweet spot.
> Our primary query is 'get messages for session X' — a partition key lookup.
> Trade-off: no complex cross-session analytics. Acceptable because we run
> analytics separately."

---

## Design Decision 2 — Milvus for Vector Storage

### The Requirement

```
  WHAT WE NEED:
  "Find me the 5 most semantically similar chunks
   from thousands of stored document chunks"

  This is NOT a keyword search. "Similar meaning" ≠ same words.

  You need: similarity search over high-dimensional vectors (1536 dims)
  At scale: potentially millions of document chunks
  Fast:     must complete in milliseconds (it's part of every query)
```

### Options Compared

```
  ┌─────────────────┬──────────────┬──────────────┬───────────────┐
  │                 │  pgvector    │   Pinecone   │    Milvus     │
  │                 │ (PostgreSQL) │  (Managed)   │ (Self-hosted) │
  ├─────────────────┼──────────────┼──────────────┼───────────────┤
  │ Data residency  │ In Walmart   │ External ❌  │ In Walmart ✅ │
  │                 │ infra ✅     │ (their cloud)│ (self-hosted) │
  ├─────────────────┼──────────────┼──────────────┼───────────────┤
  │ Performance at  │ Degrades at  │ Good         │ Excellent     │
  │ scale           │ >1M vectors  │              │ (purpose-built│
  │                 │ ❌           │              │ for this) ✅  │
  ├─────────────────┼──────────────┼──────────────┼───────────────┤
  │ Operational     │ Low (reuses  │ Zero (managed│ Medium        │
  │ overhead        │ PostgreSQL)  │ service)     │ (new DB to    │
  │                 │ ✅           │ ✅           │ operate)      │
  ├─────────────────┼──────────────┼──────────────┼───────────────┤
  │ Cost            │ Low          │ Per-query $  │ Fixed infra   │
  │                 │              │ at scale ❌  │ cost          │
  └─────────────────┴──────────────┴──────────────┴───────────────┘

  DECISION: Milvus
  → Data governance rules out Pinecone (external cloud)
  → pgvector performance too low at our expected scale
  → Milvus operational overhead accepted as the price for fit
```

### Trade-off

```
  ✅ GAINED:
    → Data stays in Walmart's infrastructure (governance ✅)
    → Purpose-built vector indexes (IVF_FLAT) → fast at scale
    → Multi-tenant collections per market

  ❌ GAVE UP:
    → Another database to operate, monitor, backup
    → Java SDK less mature than Python SDK
    → Self-hosted = we own the uptime
```

---

## Design Decision 3 — SSE vs WebSocket for Chat

```
  TWO OPTIONS FOR REAL-TIME COMMUNICATION:

  ┌─────────────────────────────────────────────────────────┐
  │  SSE (Server-Sent Events)                               │
  │  ─────────────────────────────────────────────────────  │
  │  Direction:  Server → Client ONLY                       │
  │  Protocol:   Plain HTTP                                 │
  │  Connection: Open HTTP connection, server pushes events │
  │                                                         │
  │  Client: "I have a question."  → sends HTTP POST       │
  │  Server: "Classifying..."      → SSE event             │
  │  Server: "Calling DCC tool..." → SSE event             │
  │  Server: "Here's the answer"   → SSE event             │
  │  Server: "Done. 4.2s, 3 calls" → SSE event + close    │
  │                                                         │
  │  ✅ Simpler (just HTTP, works through proxies/CDNs)     │
  │  ✅ Right for this: server just needs to push events    │
  │  ❌ One direction only (client can't send mid-stream)   │
  └─────────────────────────────────────────────────────────┘

  ┌─────────────────────────────────────────────────────────┐
  │  WebSocket                                              │
  │  ─────────────────────────────────────────────────────  │
  │  Direction:  BOTH ways simultaneously                   │
  │  Protocol:   Custom (ws://)                             │
  │  Connection: Persistent, bidirectional                  │
  │                                                         │
  │  Client → Server → Client → Server (interleaved)        │
  │                                                         │
  │  ✅ Full duplex — both sides can send anytime           │
  │  ❌ More complex, proxy/CDN support varies              │
  │  ❌ Overkill if you only need server → client           │
  └─────────────────────────────────────────────────────────┘

  OUR CHOICE:
  Chat: SSE  → Server pushes streaming events. Simple. Sufficient.
  MCP tools: WebSocket → Bidirectional protocol. Client sends tool
             calls, server responds. Both directions needed.

  RIGHT TOOL FOR RIGHT JOB.
```

---

## Design Decision 4 — The Transactional Memory Pattern

```
  THE PROBLEM:

  LLM calls can take up to 120 seconds.
  What if the server crashes at second 90?

  NAIVE APPROACH:
  ┌───────────────────────────────────────────────────┐
  │  1. Receive user message                          │
  │  2. Run AI (tool calls, LLM reasoning...)         │
  │  3. Save BOTH message and response to Cassandra   │
  │                    ▲                              │
  │                    │                              │
  │              Crash here?                          │
  │              User message LOST.                   │
  │              Conversation has a gap.              │
  └───────────────────────────────────────────────────┘

  TRANSACTIONAL APPROACH (what we use):
  ┌───────────────────────────────────────────────────┐
  │  1. Receive user message                          │
  │  2. IMMEDIATELY save user message to Cassandra ←  │
  │  3. Run AI (in memory only — tools not saved)     │
  │  4. ONLY on success: save AI response             │
  │                                                   │
  │  Crash at step 3?                                 │
  │  → User message is already in Cassandra ✅        │
  │  → AI response missing — known incomplete state   │
  │  → Much better than silent data loss              │
  └───────────────────────────────────────────────────┘

  THIS IS THE WRITE-AHEAD LOG PATTERN:
  Same idea databases use internally for crash recovery.
  Write the intention first, then execute.
  Applied to conversational AI memory.
```

---

## Design Decision 5 — Thread-Local for Tenant Context

```
  THE PROBLEM:
  Spring Boot handles many requests simultaneously
  across many threads in a thread pool.

  ┌──────────────────────────────────────────────────────┐
  │  Thread Pool                                         │
  │  ┌─────────┐  ┌─────────┐  ┌─────────┐             │
  │  │Thread 1 │  │Thread 2 │  │Thread 3 │             │
  │  │Mexico   │  │Canada   │  │Mexico   │             │
  │  │user A   │  │user B   │  │user C   │             │
  │  └─────────┘  └─────────┘  └─────────┘             │
  └──────────────────────────────────────────────────────┘

  If tenant context is in a SHARED OBJECT:
  → Thread 1 sets market = MX
  → Thread 2 sets market = CA  (overwrites!)
  → Thread 1 tries to use market context → gets CA ❌
  → User A gets Canada data. Bug.

  SOLUTION: ThreadLocal
  → Each THREAD gets its own private copy of the context
  → Thread 1's context: MX (isolated)
  → Thread 2's context: CA (isolated)
  → Thread 3's context: MX (isolated)
  → No sharing. No overwriting. Each request is safe.
```

---

## Design Decision 6 — In-Memory Tool Results (Not Persisted)

```
  DURING AI AGENT PROCESSING:
  Tool results (DCC response, Wakanda data, etc.)
  are kept in memory ONLY — not saved to Cassandra.

  WHY?

  ┌──────────────────────────────────────────────────────┐
  │  Tool call 1: DCC returns template data (~5KB JSON)  │
  │  Tool call 2: Wakanda returns inventory data (~8KB)  │
  │  Tool call 3: Cassandra returns offer data (~3KB)    │
  │  Tool call 4: DCC returns carrier data (~4KB)        │
  │  Tool call 5: Unified Promise returns (~10KB)        │
  │                                                      │
  │  Total intermediate data: ~30KB per question         │
  │  × 1,000 questions/day = 30MB/day of raw API data   │
  │                                                      │
  │  PROBLEM: This data changes on every call.           │
  │  It's stale immediately. Storing it pollutes         │
  │  conversation history and wastes Cassandra writes.   │
  │                                                      │
  │  SOLUTION: Keep in memory during the agent loop.     │
  │  Only save the FINAL human-readable answer.          │
  │  That's what matters for conversation continuity.    │
  └──────────────────────────────────────────────────────┘
```

---

## Design Decision 7 — Java Over Python

```
  HONEST COMPARISON:

  ┌─────────────────────┬──────────────────────────────────────┐
  │  PYTHON + LangChain │  JAVA + LangChain4j                  │
  ├─────────────────────┼──────────────────────────────────────┤
  │  Richer AI ecosystem│  Existing team expertise             │
  │  More AI libraries  │  Same deployment pipeline as MCSE    │
  │  LangChain is more  │  Java-native performance for         │
  │  mature             │  concurrent tool execution           │
  │                     │  Spring Boot enterprise features     │
  └─────────────────────┴──────────────────────────────────────┘

  WHY JAVA WON:
  → The PNS backend team owns Java. Adding Python = second language,
    second pipeline, second monitoring setup.
  → LangChain4j covers all the core agent capabilities we need.
  → CPU-intensive work (document parsing, concurrent tools) plays
    to Java's multi-threading strengths.

  TRADE-OFF ACCEPTED:
  → LangChain4j is less mature than Python LangChain.
  → Fewer community examples and plugins.
  → We might need to build some things Python users get for free.
```

---

## Design Decision 8 — gpt-oss-120b (Internal Model vs Public API)

### The Requirement

```
WHAT THE APP SENDS TO THE LLM ON EVERY CALL:

  ┌──────────────────────────────────────────────────┐
  │  System prompt (PNS business rules)              │
  │  User question: "why is offer 12345 not 2-day?"  │
  │  Tool results: DCC data, carrier configs...      │
  │  Conversation history: last 30 messages          │
  └──────────────────────────────────────────────────┘

  All of this contains REAL WALMART BUSINESS DATA.
  Offer IDs, carrier configurations, inventory levels,
  distributor mappings, SLA data — live production data.

  QUESTION: Where does this data go when you call the LLM?

  OpenAI public API  → Data leaves Walmart, goes to OpenAI's servers
  Anthropic API      → Data leaves Walmart, goes to Anthropic's servers
  gpt-oss-120b       → Data stays inside Walmart's Azure tenant
```

### What gpt-oss-120b Actually Is

```
  gpt  -  oss  -  120b
   │        │        │
   │        │        └── 120 billion parameters
   │        │             (large = complex reasoning ability)
   │        └─────────── "open source" — based on an open-source
   │                      foundation model (Meta LLaMA family)
   └──────────────────── deployed on Azure, named in GPT style
                          by Walmart's ML team

  IT IS NOT:
  → GPT-4 (OpenAI's proprietary model, accessed via OpenAI API)
  → Claude (Anthropic's model)
  → Gemini (Google's model)

  IT IS:
  → An open-source base model that Walmart deployed on their OWN
    Azure infrastructure via Azure OpenAI Service
  → Same API interface as Azure OpenAI — so LangChain4j code
    doesn't need to change
  → All data stays inside Walmart's Azure tenant
```

### Why This Choice

```
  NEED 1: DATA GOVERNANCE (most important)
  ─────────────────────────────────────────
  Enterprise compliance requires PNS operational data
  to stay within Walmart's network boundary.
  Using OpenAI or Anthropic APIs = data leaves Walmart.
  Using gpt-oss-120b on Azure = data stays inside.

  NEED 2: CAPABILITY (120B parameters = strong reasoning)
  ─────────────────────────────────────────────────────────
  This app chains 3-7 tool calls to answer one question.
  The LLM must:
    → Understand which tool to call next
    → Interpret API results correctly
    → Connect results across multiple calls
    → Give a reasoned final answer

  A smaller model (7B, 13B) would lose track mid-chain.
  120B parameters gives strong enough reasoning for this.

  NEED 3: AZURE INTEGRATION
  ─────────────────────────────────────────────────────────
  Walmart runs on Azure. Azure OpenAI Service lets you
  deploy models within your own Azure tenant.
  Same SDK, same LangChain4j integration, enterprise SLA.
```

### Trade-off Accepted

```
  ✅ GAINED:                      ❌ GAVE UP:
  ─────────────────────────────────────────────────
  Data never leaves Walmart       Not GPT-4 level capability
  Enterprise compliance met       Smaller open-source ecosystem
  Azure SLA + support             Dependent on Walmart ML team
  120B = sufficient reasoning     to maintain the deployment
  No per-token cost to OpenAI
```

**Interview answer:**
> "We chose gpt-oss-120b primarily for data governance. Every LLM call contains
> real Walmart business data — offer IDs, carrier configs, distributor mappings.
> Using OpenAI's public API would send that data outside Walmart's network, which
> is a compliance non-starter. gpt-oss-120b is an open-source 120B parameter model
> deployed on Walmart's own Azure tenant — same Azure OpenAI interface, but data
> never leaves our infrastructure. The 120B size also gives it enough reasoning
> capability to chain 3-7 tool calls and synthesize a coherent answer."

---

## The Quick Trade-off Reference

```
  ┌──────────────────┬──────────────────────┬──────────────────────┐
  │  DECISION        │  ✅ GAINED           │  ❌ GAVE UP          │
  ├──────────────────┼──────────────────────┼──────────────────────┤
  │  Cassandra       │  Write throughput    │  No complex queries  │
  │  (not Postgres)  │  Horizontal scale    │  No JOINs            │
  │                  │  Team familiarity    │                      │
  ├──────────────────┼──────────────────────┼──────────────────────┤
  │  Milvus          │  Data stays Walmart  │  Ops overhead        │
  │  (not Pinecone)  │  Scale performance   │  Self-managed        │
  ├──────────────────┼──────────────────────┼──────────────────────┤
  │  SSE for chat    │  Simplicity          │  One direction only  │
  │  (not WebSocket) │  Proxy-friendly      │                      │
  ├──────────────────┼──────────────────────┼──────────────────────┤
  │  Java            │  Team expertise      │  Smaller AI          │
  │  (not Python)    │  One pipeline        │  ecosystem           │
  ├──────────────────┼──────────────────────┼──────────────────────┤
  │  Azure OpenAI    │  Data in Walmart     │  Tied to Azure       │
  │  (not direct OAI)│  Enterprise SLA      │  pricing/quotas      │
  ├──────────────────┼──────────────────────┼──────────────────────┤
  │  gpt-oss-120b    │  Data stays Walmart  │  Not GPT-4 quality   │
  │  (not GPT-4/     │  Compliance met      │  Walmart ML team     │
  │   Claude/Gemini) │  120B = capable      │  must maintain it    │
  ├──────────────────┼──────────────────────┼──────────────────────┤
  │  Transactional   │  No silent data loss │  Extra Cassandra     │
  │  memory pattern  │  Crash resilience    │  write per message   │
  ├──────────────────┼──────────────────────┼──────────────────────┤
  │  ThreadLocal     │  Request isolation   │  Must clear on       │
  │  tenant context  │  No shared state     │  thread reuse        │
  └──────────────────┴──────────────────────┴──────────────────────┘
```

---

## Interview-Ready Summaries

**On Cassandra:**
> "Write-heavy time-series data at scale — that's where Cassandra shines.
> Our primary access pattern is partition key lookup: get all messages for
> a session ID. Cassandra is purpose-built for that. Trade-off: no cross-session
> analytics queries. We run those separately."

**On Milvus:**
> "Data governance ruled out Pinecone — vectors of PNS operational content
> can't go to an external cloud. Among self-hosted options, Milvus beat
> pgvector on performance at scale — pgvector degrades beyond a few million
> vectors because PostgreSQL wasn't designed for high-dimensional similarity
> search. Trade-off: an additional database to operate."

**On SSE vs WebSocket:**
> "SSE for chat because we only need server-to-client streaming — events
> pushed as each pipeline stage completes. WebSocket for the MCP tools
> protocol because it's bidirectional — the client also sends tool call
> requests. Right tool for the right communication pattern."

**On the Transactional Memory Pattern:**
> "It's the write-ahead log pattern applied to AI memory. Save user message
> immediately, process AI in memory, save response on success. If a 90-second
> LLM call crashes at second 89, the user's message is already in Cassandra.
> No silent data loss."

---

**You have now covered all 3 mental model files.**
**Return to `05_RESOURCES.md` to check your Day 2 plan.**
