# 09 — System Design Interview Notes
### Agentic AI Backend — Architecture, Trade-offs, and Deep Dives

> **This file is for system design interview preparation.**
> It treats `aiPnSBackend` as the design subject — the same way a senior engineer
> would be asked "design an AI-powered operations assistant" and then expected
> to defend every decision made.
>
> **Reading time:** 90 min for full depth | 30 min for core sections only
> **Goal:** Walk into any system design conversation about this project and speak
> fluently about architecture, trade-offs, scaling, and failure modes.

---

## Index

| # | Section | What you get |
|---|---|---|
| 0 | [What Is This System?](#-what-is-this-system) | 30-sec orientation + real-world analogies |
| 1 | [The Problem: Why This System Exists](#section-1--the-problem-why-this-system-exists) | Before/after story with exact time numbers |
| 2 | [Full System Architecture](#section-2--full-system-architecture) | Complete 6-zone ASCII diagram + one-sentence per zone |
| 3 | [The Agentic AI Loop](#section-3--the-agentic-ai-loop-the-core-design) | ReAct pattern walked through with a real PNS example |
| 4 | [Tool Design](#section-4--tool-design-the-hardest-engineering-problem) | Disambiguation problem, 3-layer pattern, failure handling |
| 5 | [The System Prompt](#section-5--the-system-prompt-business-logic-as-code) | Structure, tool order rules, why iteration matters |
| 6 | [RAG Design](#section-6--rag-retrieval-augmented-generation) | Embedding pipeline, Milvus per-tenant, why RAG over fine-tuning |
| 7 | [Memory Design](#section-7--memory-design-cassandra-backed-conversation) | Why Cassandra, memory key format, transactional pattern, window math |
| 8 | [Streaming Architecture (SSE)](#section-8--streaming-architecture-sse) | SSE vs WebSocket decision, raw event stream |
| 9 | [Multi-Tenancy Design](#section-9--multi-tenancy-design) | ThreadLocal lifecycle, where tenantId drives behavior |
| 10 | [Key Design Trade-offs](#section-10--key-design-trade-offs) | Java vs Python, monolith vs microservices, Cassandra vs Postgres |
| 11 | [Scaling & Failure Modes](#section-11--scaling--failure-modes) | 3 bottlenecks, 4 failure scenarios, observability gaps |
| 12 | [System Design Interview Q&A](#section-12--system-design-interview-qa) | 6 questions with structured answers |
| 13 | [Quick Reference: The Numbers](#section-13--quick-reference-the-numbers) | Every metric an interviewer could probe |
| 14 | [Architecture Diagram Cheat Sheet](#section-14--architecture-diagram-cheat-sheet) | Whiteboard-ready 3-minute diagram |
| 15 | [3 Sentences That Win the Interview](#section-15--3-sentences-that-win-the-interview) | Memorize and deliver in first 90 seconds |

---

## 🎯 What Is This System?

**In plain English:** An AI-powered debugging assistant for Walmart's Promise and
Shipping (PNS) platform. Engineers ask it questions in plain English — "why is
offer 12345 not 2-day eligible in Mexico?" — and it automatically queries the right
PNS systems, chains the results together, and gives a reasoned answer in 8–12 seconds.

**Real-world analogies:**

| System | What they built |
|---|---|
| **This app** | Conversational AI agent over internal PNS APIs — for on-call debugging |
| **Stripe's AI support bot** | Answers API questions by querying internal documentation |
| **GitHub Copilot Workspace** | Multi-step agent that reads code, calls tools, plans changes |
| **Salesforce Einstein** | Domain-specific AI that queries CRM data on request |
| **Amazon Q for AWS** | Natural language queries over your AWS account state |

**Core user journey:** On-call engineer types "offer 12345 not 2-day eligible in Mexico"
→ AI queries DCC, Wakanda, Cassandra automatically → Delivers root cause + recommended
fix in under 12 seconds.

**Why it's hard to build at scale:**
Multiple AI tool calls must chain correctly with context, conversation memory must persist
across sessions, and tool descriptions must be precise enough that the LLM always picks
the right API for the right question — all while streaming intermediate steps to the UI in real time.

---

## Section 1 — The Problem: Why This System Exists

Before drawing a single box, anchor the design in the real user pain.

```
THE BEFORE STATE (Manual Debugging):
══════════════════════════════════════════════════════
  Engineer gets paged at 2am.
  Offer 12345 not getting 2-day delivery in Mexico.

  STEP 1 → Open DCC Dashboard
           → Look up offer's template mapping
           → Find: template ID = T123

  STEP 2 → Go back to DCC
           → Look up template T123
           → Find: carrier method = CM789

  STEP 3 → DCC again
           → Look up CM789
           → Find: max SLA = 3 days, Zone 4, express NOT supported

  STEP 4 → Open Wakanda
           → Check inventory at relevant node
           → Find: inventory IS available

  STEP 5 → Connect dots manually
           → Conclusion: carrier method is the blocker

  TOTAL: 15–25 minutes | 4 systems | Cognitive load at 2am
══════════════════════════════════════════════════════

THE AFTER STATE (AI Agent):
══════════════════════════════════════════════════════
  Engineer types:
  "Why is offer 12345 not 2-day eligible in Mexico?"

  AI runs: getOfferTemplateNodeMapping → getTemplateInfo
          → getCarrierMethodInfo → getWakandaInventory

  Response arrives in 8–12 seconds:
  "Offer 12345 is mapped to template T123, using carrier method
   CM789. This carrier method has a max SLA of 3 days (Zone 4)
   and does not support express fulfillment. Wakanda shows
   inventory is available. Root cause: carrier method restriction."

  TOTAL: 8–12 seconds | 1 interface | No system knowledge required
══════════════════════════════════════════════════════
```

**This framing is your interview anchor.** Return to it whenever you are asked
"why did you do X?" — the answer is always rooted in solving this specific pain.

---

## Section 2 — Full System Architecture

### The 6-Zone Map

```
╔══════════════════════════════════════════════════════════════════════════════╗
║                      aiPnSBackend — FULL SYSTEM MAP                        ║
╠══════════════════════════════════════════════════════════════════════════════╣
║                                                                              ║
║  ┌──────────────────────────────────────────────────────────────────────┐   ║
║  │  ZONE 1: FRONTEND  (React 19 + TypeScript)                          │   ║
║  │  ai-support-chatbot.walmart.com                                      │   ║
║  │                                                                      │   ║
║  │  Chat Window │ Market Selector (MX/CA) │ Domain Selector            │   ║
║  │  Session Sidebar │ Agent Thinking Panel │ MCP Tools Explorer        │   ║
║  └────────────────────────┬─────────────────────────────────────────────┘   ║
║                           │  REST (auth/sessions)  │  SSE (chat stream)    ║
║  ┌────────────────────────▼─────────────────────────────────────────────┐   ║
║  │  ZONE 2: API GATEWAY  (Spring Boot 3.2.3 / Java 17)                 │   ║
║  │                                                                      │   ║
║  │  PingFederate SSO → JWT validation → TenantInterceptor               │   ║
║  │  Route: /api/pns-agent/stream  /api/mcp/*  /api/admin/*             │   ║
║  └────────────────────────┬─────────────────────────────────────────────┘   ║
║                           │                                                  ║
║  ┌────────────────────────▼─────────────────────────────────────────────┐   ║
║  │  ZONE 3: ORCHESTRATION  (AdvancedConversationalFlowService)          │   ║
║  │                                                                      │   ║
║  │  1. Save user message to Cassandra (START)                          │   ║
║  │  2. Detect domain → route to PnsConversationHandler                 │   ║
║  │      OR ConfluenceConversationHandler                               │   ║
║  │  3. Execute AI agent (ASYNC, up to 120s)                            │   ║
║  │  4. Save AI response to Cassandra (COMMIT)                          │   ║
║  └────────────────────────┬─────────────────────────────────────────────┘   ║
║                           │                                                  ║
║  ┌────────────────────────▼─────────────────────────────────────────────┐   ║
║  │  ZONE 4: AI AGENT  (LangChain4j 0.27.1 + Azure OpenAI)              │   ║
║  │                                                                      │   ║
║  │  ┌─────────────┐   ┌──────────────┐   ┌───────────────────────┐    │   ║
║  │  │ System      │   │ Conversation │   │ RAG Documents         │    │   ║
║  │  │ Prompt      │   │ History      │   │ (from Milvus)         │    │   ║
║  │  │ (PNS rules) │   │ (Cassandra)  │   │ (top-5 relevant docs) │    │   ║
║  │  └──────┬──────┘   └──────┬───────┘   └───────────┬───────────┘    │   ║
║  │         └──────────────────┴───────────────────────┘                │   ║
║  │                            │ Combined context                        │   ║
║  │                            ▼                                         │   ║
║  │                   gpt-oss-120b (Azure OpenAI)                       │   ║
║  │                            │                                         │   ║
║  │              ┌─────────────▼──────────────┐                         │   ║
║  │              │   AGENT LOOP (ReAct)        │                         │   ║
║  │              │   Think → Call Tool         │                         │   ║
║  │              │   → Observe Result          │                         │   ║
║  │              │   → Think Again             │                         │   ║
║  │              │   → Repeat until done       │                         │   ║
║  │              └─────────────┬──────────────┘                         │   ║
║  └────────────────────────────┼──────────────────────────────────────────┘   ║
║                               │ tool calls                                    ║
║  ┌────────────────────────────▼──────────────────────────────────────────┐   ║
║  │  ZONE 5: TOOLS LAYER  (40+ @Tool-annotated Java methods)              │   ║
║  │                                                                        │   ║
║  │  DccApiTools (8)         WakandaApiTools (2)    PnoIngestorTools (8)  │   ║
║  │  McseCassandraTools (5)  UnifiedPromiseTools    GScopeApiTools        │   ║
║  │  McsePromiseApiTools     OrderServiceTools      EnhancedPnsTools      │   ║
║  │                                                                        │   ║
║  │         ↓ each calls a real downstream PNS service ↓                 │   ║
║  └───────────────────────────────────────────────────────────────────────┘   ║
║                                                                              ║
║  ┌─────────────────────┐          ┌──────────────────────────────────────┐  ║
║  │  MEMORY STORE       │          │  KNOWLEDGE STORE                     │  ║
║  │  (Apache Cassandra) │          │  (Milvus Vector DB)                  │  ║
║  │                     │          │                                      │  ║
║  │  ConversationMemory │          │  PNS Runbooks, Incident Docs         │  ║
║  │  per session        │          │  Process Guides — stored as          │  ║
║  │  max 30 messages    │          │  1536-dim vectors                    │  ║
║  │  per tenant/market  │          │  (text-embedding-3-large)            │  ║
║  └─────────────────────┘          └──────────────────────────────────────┘  ║
╚══════════════════════════════════════════════════════════════════════════════╝
```

### One-Sentence Per Zone

| Zone | One-sentence role |
|---|---|
| **Frontend** | Chat UI that streams agent thinking steps in real time |
| **API Gateway** | Auth, tenant detection, request routing |
| **Orchestration** | Transactional message save → domain routing → AI invocation |
| **AI Agent** | LLM + tools + memory + RAG → agentic reasoning loop |
| **Tools Layer** | 40+ Java methods wrapping real PNS APIs (what YOU built) |
| **Cassandra** | Durable, write-heavy memory store for conversation history |
| **Milvus** | Vector store for semantic document retrieval (RAG source) |

---

## Section 3 — The Agentic AI Loop (The Core Design)

This is the most important section for a system design interview.
The agentic loop is what separates this from a simple API call to an LLM.

### 3.1 — What "Agentic" Means

```
SIMPLE LLM CALL (not what this app does):
─────────────────────────────────────────
  User  ──→  LLM (gpt-oss-120b)
  User  ←──  One response
  
  Problem: LLM only knows what was in its training data.
           Cannot query DCC. Cannot check Wakanda inventory.
           Must hallucinate or say "I don't know".


AGENTIC AI LOOP (what this app does):
──────────────────────────────────────
  User message
     │
     ▼
  LLM reads: system prompt + history + RAG docs + tool definitions
     │
     ▼
  LLM decides: "I need to call getOfferTemplateNodeMapping(offerId=12345)"
     │
     ▼
  Tool executes: actual HTTP call to DCC API → returns template data
     │
     ▼
  LLM reads: the tool result (added to context as "observation")
     │
     ▼
  LLM decides: "Now I need carrier method info — call getCarrierMethodInfo(CM789)"
     │
     ▼
  Tool executes: another HTTP call to DCC API → returns carrier data
     │
     ▼
  LLM decides: "I have enough data to answer now"
     │
     ▼
  LLM generates: final synthesised response
     │
     ▼
  User receives: root cause + explanation
```

### 3.2 — The ReAct Pattern (Reason + Act)

The LLM alternates between two modes:

```
  THINK: "The user wants to know why offer 12345 isn't eligible.
          For eligibility failures I should check template mapping first
          (as the system prompt instructs). I'll call getOfferTemplateNodeMapping."

  ACT:   getOfferTemplateNodeMapping(offerId="12345", market="MX")
         → Returns: {templateId: "T123", nodeId: "N456"}

  THINK: "Template T123 uses carrier method CM789. I need to check
          if this carrier method supports express. I'll call getCarrierMethodInfo."

  ACT:   getCarrierMethodInfo(carrierMethodId="CM789", tenantId="hvgqan")
         → Returns: {maxSLA: 3, zone: "Zone4", expressSupported: false}

  THINK: "Express is not supported. I should verify inventory isn't the
          issue. I'll check Wakanda."

  ACT:   getWakandaInventory(offerId="12345", nodeId="N456")
         → Returns: {available: true, quantity: 150}

  THINK: "Inventory is fine. The blocker is carrier method CM789 not
          supporting express. I have enough to give a final answer."

  RESPOND: "Root cause found: carrier method CM789 (Zone 4) does not
            support express fulfillment. Inventory is available. Template
            T123 is correct. Recommend checking CM789 express configuration
            in DCC."
```

**Why this pattern matters in an interview:**
> "This is the ReAct pattern — Reasoning and Acting interleaved. The LLM doesn't
> just generate text — it reasons about what it needs, acts by calling a tool,
> observes the result, and reasons again. LangChain4j orchestrates this loop.
> The loop continues until the LLM decides it has enough context to give a final
> answer or reaches the maximum iteration count."

---

## Section 4 — Tool Design: The Hardest Engineering Problem

This is where domain expertise becomes code. The tools layer is not just
"wrapping APIs" — it requires solving the **tool disambiguation problem**.

### 4.1 — Tool Disambiguation Problem

```
THE PROBLEM:
  The LLM reads tool DESCRIPTIONS to decide which tool to call.
  If descriptions are vague or overlapping → wrong tool → wrong answer.

EXAMPLE — Three carrier-related tools exist:

  ❌ BAD DESCRIPTIONS (LLM picks randomly):
  ──────────────────────────────────────────
  getCarrierData()           → "Gets carrier data."
  getCarrierMethodInfo()     → "Gets carrier method information."
  getMcseCarrierInfo()       → "Retrieves carrier info from MCSE."

  ✅ GOOD DESCRIPTIONS (LLM picks correctly):
  ────────────────────────────────────────────
  getCarrierData()
  → "Fetches top-level carrier entity data from DCC. Use when you need
     the carrier name, carrier type, or carrier-level configuration.
     NOT for carrier method specifics like SLA or zone."

  getCarrierMethodInfo()
  → "Retrieves configuration for a specific carrier METHOD — the actual
     delivery option (e.g., ground, express). Use THIS tool when you need
     max SLA, zone assignment, or express eligibility for a given CM ID.
     This is the primary tool for eligibility failure investigation."

  getMcseCarrierInfo()
  → "Reads raw carrier data directly from MCSE Cassandra. Use ONLY when
     DCC API is unavailable or you need to compare DCC vs raw MCSE data.
     Requires direct Cassandra access, slower than DCC API."
```

**The engineering insight the interviewer is looking for:**
> "Tool description quality directly determines answer quality. Vague descriptions
> are a correctness bug — not a style issue. We iterated on descriptions by running
> real PNS scenarios and checking which tool the LLM called. When it called the wrong
> one, I revised the description and retested. The descriptions are effectively
> function contracts written for an LLM consumer."

### 4.2 — Tool Architecture Pattern

```java
// Pattern used across all 40+ tools
@Component
public class DccApiTools {

    @Autowired
    private DccApiService dccApiService;  // Service handles HTTP, retry, timeout

    @Tool(
        "Retrieves configuration for a specific carrier METHOD — the actual " +
        "delivery option (e.g., ground, express). Use THIS tool when you need " +
        "max SLA, zone assignment, or express eligibility for a given CM ID. " +
        "This is the primary tool for eligibility failure investigation. " +
        "Requires: carrierMethodId (mandatory), tenantId (optional: 'hvgqan'=MX, 'qxjed8'=CA)."
    )
    public String getCarrierMethodInfo(String carrierMethodId, String tenantId) {
        // Tool layer is INTENTIONALLY thin — just translation between LLM and service
        return dccApiService.getCarrierMethod(carrierMethodId, tenantId);
    }
}
```

**Three layers, three responsibilities:**

```
  @Tool annotation  →  LLM interface (what the AI reads)
  Tool method body  →  Translation layer (what parameters to pass)
  DccApiService     →  HTTP execution (retry, timeout, error handling)
```

**Why this separation matters:**
The tool layer is the LLM's API surface. The service layer is the backend
engineering concern. Mixing them would mean changing HTTP logic every time
you tune an LLM description — a single-responsibility violation.

### 4.3 — Tool Failure Handling

```
WRONG (throws exception → LLM hallucinates):
  if (api.fails()) { throw new RuntimeException("DCC unavailable"); }
  // LLM gets confused, may generate a fake answer

RIGHT (returns structured error → LLM responds correctly):
  if (api.fails()) {
      return "ERROR: DCC API timeout after 45s for carrierMethodId=" + id +
             ". Cannot determine carrier configuration. " +
             "Please check DCC availability and retry.";
  }
  // LLM says: "I tried to get carrier info but DCC is not responding"
```

**Design principle:** Tools should never throw exceptions to the LLM. Structured
error strings let the LLM communicate failures intelligently to the user.

---

## Section 5 — The System Prompt: Business Logic as Code

The system prompt is the most opaque but most consequential piece of the system.
It is where 5 years of PNS domain knowledge became executable logic.

### 5.1 — What the System Prompt Contains

```
SYSTEM PROMPT STRUCTURE:
═══════════════════════════════════════════════════════════════════
  1. ROLE DEFINITION
     "You are an AI assistant for Walmart's Promise & Shipping platform.
      You help on-call engineers debug delivery eligibility failures."

  2. TOOL CALL PRIORITY ORDER (the most critical section)
     "For eligibility failure questions, follow this sequence:
      1. Call getOfferTemplateNodeMapping first (always)
      2. Then getTemplateInfo with the returned template ID
      3. Then getCarrierMethodInfo with the returned carrier method ID
      4. Check zone charges if express SLA is the question
      5. Check Wakanda inventory LAST — inventory is rarely the blocker"

  3. MARKET DEFAULTS
     Mexico (tenantId: hvgqan):
       - Default postal code: 06600 (CDMX)
       - Default store: 2648
     Canada (tenantId: qxjed8):
       - Default postal code: M5V 2T6 (Toronto)

  4. RESPONSE FORMAT RULES
     "Always state the root cause before the data.
      Format tool results as structured text, not JSON dumps.
      If DCC and Cassandra disagree, flag the discrepancy."

  5. ESCALATION RULES
     "If offer status is INACTIVE, note this and do not investigate further.
      If Wakanda returns 0 inventory, this may be a data freshness issue."
═══════════════════════════════════════════════════════════════════
```

### 5.2 — Why Tool Call ORDER Matters

```
WRONG ORDER (what the LLM did without the ordering rule):
  1. Check Wakanda inventory → inventory is fine
  2. Check Unified Promise → promise looks normal
  3. Finally check carrier method → FOUND THE BUG
  Time: 3 extra tool calls, 15+ extra seconds

RIGHT ORDER (after adding priority rule to system prompt):
  1. Check offer template mapping → T123
  2. Check template carrier method → CM789
  3. Check carrier method config → maxSLA = 3 days, express = false
  Time: 3 tool calls, root cause found immediately

The system prompt ENCODES the debugging intuition of an experienced PNS engineer.
The LLM has no PNS experience — it has this document instead.
```

**Interview point:**
> "The system prompt is not a static string we wrote once. It evolved through
> many iterations of running real PNS scenarios and measuring whether the LLM
> reached the right answer via the right path. Each iteration fixed a specific
> failure mode — wrong tool order, missing market defaults, incorrect error
> interpretation."

---

## Section 6 — RAG: Retrieval-Augmented Generation

### 6.1 — Why RAG Is Needed

```
WITHOUT RAG:
  User: "What is the on-call escalation process for a PNS Sev1?"
  LLM:  Trained on public internet. Has NEVER seen Walmart's runbooks.
        → Hallucination risk: makes up a plausible-sounding but wrong process.

WITH RAG:
  BEFORE sending to LLM:
  1. Convert question to vector: embed("on-call escalation PNS Sev1")
  2. Search Milvus for top-5 nearest documents to that vector
  3. Retrieve: [PNS-Runbook-Sev1.pdf, Escalation-Policy-2025.docx, ...]
  4. Add those documents as context to the LLM prompt

  NOW ask the LLM — it reads from the actual runbook, not from memory.
```

### 6.2 — The Embedding Pipeline

```
DOCUMENT INGESTION (offline, one-time per document):
═══════════════════════════════════════════════════
  PNS Runbook PDF
       │
       ▼
  Apache Tika + Tesseract (parsing + OCR if needed)
       │
       ▼
  Split into chunks (~500 tokens each with 50-token overlap)
       │
       ▼
  Azure OpenAI text-embedding-3-large
  → Each chunk → 1536-dimensional float vector
       │
       ▼
  Store in Milvus:
  Collection: walmart_ai_support_{tenantId}
  Fields: [vector, text, source_file, chunk_index, market]

QUERY TIME (per user message):
═════════════════════════════
  User message → embed → 1536-dim vector
       │
       ▼
  Milvus ANN (approximate nearest neighbor) search
  → Returns top-5 chunks with cosine similarity > 0.75
       │
       ▼
  Inject chunks into LLM prompt as context
```

### 6.3 — Milvus Collection Per Tenant

```
  Collection: walmart_ai_support_hvgqan   ← Mexico PNS documents
  Collection: walmart_ai_support_qxjed8   ← Canada PNS documents

WHY separate collections?
  → Mexico and Canada have different runbooks, different carrier configs,
    different incident histories.
  → Searching the wrong market's docs would return irrelevant or wrong answers.
  → Tenant isolation at the vector store level prevents cross-contamination.
```

---

## Section 7 — Memory Design: Cassandra-Backed Conversation

### 7.1 — Why Cassandra for Conversation Memory

```
REQUIREMENTS for conversation memory:
  → Write-heavy: every message in every session is written
  → Fast reads: history must load in < 100ms on session start
  → High availability: memory failure = broken chat (P0)
  → TTL support: old sessions should auto-expire
  → Multi-tenant: sessions must not leak across markets

WHY CASSANDRA FITS:
  ✓ Write-optimised (LSM tree structure)
  ✓ P99 reads < 10ms for small key lookups
  ✓ Built-in TTL support per row
  ✓ Partition key design enforces tenant isolation
  ✗ Not suitable for complex queries — we don't need them here
```

### 7.2 — The Memory Key Design

```
Memory key format:
  {market}_{domain}:{tenantId}:{sessionId}

Example (Mexico PNS):
  MX_pns:hvgqan:sess_abc123

Example (Canada PNS):
  CA_pns:qxjed8:sess_xyz789

WHY THIS KEY DESIGN:
  → Market prefix: fast partition scan per market
  → TenantId: prevents Mexico session leaking to Canada
  → SessionId: unique per conversation thread
  → Domain: PNS vs Confluence memory is separate (even for same user)
```

### 7.3 — The Transactional Memory Pattern

```
PROBLEM: LLM calls can take up to 120 seconds.
         If server crashes at second 90, what happens to the message?

NAIVE (broken):
  1. Call LLM → wait 90s → crash → user message is LOST
     Next session restart → conversation has a gap → confusing state

TRANSACTIONAL PATTERN (what was built):
  1. Save USER message to Cassandra    ← COMMIT step 1 (fast, < 10ms)
  2. Call LLM + tools                 ← Long operation (up to 120s)
  3. Save AI response to Cassandra    ← COMMIT step 2

  If crash at step 2:
  → User message is already saved
  → On reconnect: show user their message was received
  → AI response marked as pending/failed
  → User can retry without losing context

  Analogy: like a database 2-phase commit — write intent,
           then write result.
```

### 7.4 — Memory Window Management

```
Max messages per session: 30

WHY 30?
  Context window budget:
  ┌────────────────────────────────────────────────────┐
  │  Total context: 8,192 tokens (gpt-oss-120b limit)  │
  │                                                    │
  │  System prompt:       ~800 tokens                  │
  │  Tool definitions:  ~2,000 tokens (40+ tools)      │
  │  RAG documents:     ~1,500 tokens (5 chunks)       │
  │  Conversation hist: ~2,000 tokens (30 messages)    │
  │  Current message:     ~200 tokens                  │
  │  ─────────────────────────────────────────         │
  │  Total used:        ~6,500 tokens                  │
  │  Remaining:         ~1,700 tokens (LLM response)   │
  └────────────────────────────────────────────────────┘

  30 messages ≈ 2,000 tokens. More than 30 → context overflow.
  Less than 30 → wastes memory quality.
  30 is the calibrated balance.

EVICTION STRATEGY: Sliding window (MessageWindowChatMemory)
  When message 31 comes in → message 1 is evicted from context
  (but stays in Cassandra for audit/history viewing)
```

---

## Section 8 — Streaming Architecture (SSE)

### 8.1 — The Streaming Design Decision

```
OPTION A: Polling (bad for this use case)
  Client → POST /chat → wait → GET /status?id=123 → repeat
  Problem: LLM takes 8–30s. Polling every 1s = wasted requests.
           No intermediate steps visible (user sees blank screen).

OPTION B: WebSocket (two-way, over-engineered for chat)
  Full duplex — either side can send at any time.
  Overkill for chat where server does all the talking.
  More complex state management.

OPTION C: SSE — Server-Sent Events (chosen approach)
  One-way: server → client only (which is all chat streaming needs)
  HTTP-native: no protocol upgrade, works through proxies, CDN-friendly
  Auto-reconnect: browser handles reconnection natively
  Text-based: each AI step is one event, easy to render

SSE EVENT STREAM (what the browser receives):
  event: status
  data: {"type":"classifier","message":"Intent detected: offer eligibility"}

  event: status
  data: {"type":"planner","message":"Planning 3 tool calls"}

  event: status
  data: {"type":"tool_start","message":"Calling getOfferTemplateNodeMapping"}

  event: status
  data: {"type":"tool_done","message":"Template T123 found"}

  event: status
  data: {"type":"tool_start","message":"Calling getCarrierMethodInfo"}

  event: status
  data: {"type":"synthesizer","message":"Generating final answer"}

  event: response
  data: {"content":"Root cause: carrier method CM789...","duration_ms":8400}

  event: done
  data: {"tool_calls":3,"tokens":847}
```

### 8.2 — Why Not WebSocket for Chat?

```
WebSocket is used for the MCP Tools Explorer page only:
  → Engineers manually call tools interactively
  → Client sends tool selection events
  → Server sends tool results back
  → True bidirectional communication needed

SSE is used for chat because:
  → Once you send a message, you don't need to send again until next message
  → Server streams everything: status, tool events, final answer
  → SSE is simpler, lighter, and HTTP-proxy-compatible
  → Right tool, right job
```

---

## Section 9 — Multi-Tenancy Design

### 9.1 — Tenant Context Flow

```
HTTP Request arrives:
  Headers: {
    Authorization: "Bearer eyJ..." (JWT with tenantCode: "hvgqan"),
    X-Market: "MX",
    X-Domain: "pns"
  }

TenantInterceptor (Spring filter):
  → Decode JWT
  → Extract: tenantCode=hvgqan, market=MX, domain=pns
  → Store in ThreadLocal: TenantContextManager

WHY ThreadLocal?
  Spring Boot serves requests on multiple threads concurrently.
  ThreadLocal gives EACH REQUEST THREAD its own private copy.
  No shared state → no tenant context bleed between concurrent requests.

THREAD-LOCAL LIFECYCLE:
  Request in  → TenantInterceptor.preHandle()  → store context
  Processing  → any class calls TenantContextManager.get*() → reads context
  Request out → TenantInterceptor.afterCompletion() → CLEAR context
                                    ↑ critical: prevents memory leaks
```

### 9.2 — Where Tenant Context Drives Behavior

```
┌──────────────────────┬──────────────────────────────────────────────────┐
│  Component           │  How tenantId changes behavior                   │
├──────────────────────┼──────────────────────────────────────────────────┤
│  Milvus query        │  Searches collection walmart_ai_support_{tenantId}│
│  Cassandra memory    │  Key prefix: {market}_{domain}:{tenantId}:sess   │
│  Tool parameters     │  tenantId passed to all DCC/Wakanda/MCSE calls   │
│  System prompt       │  Injects market-specific defaults (postal, store) │
│  RAG collection      │  Tenant-isolated document namespace               │
└──────────────────────┴──────────────────────────────────────────────────┘
```

---

## Section 10 — Key Design Trade-offs

These are the "why" answers — the ones interviewers probe hardest.

### Trade-off 1: Java vs Python

```
PYTHON ADVANTAGES (acknowledged):
  → Richer AI ecosystem (LangChain Python > LangChain4j in maturity)
  → More AI/ML tooling, easier prototyping
  → Stronger community for LLM integrations

WHY JAVA WAS CHOSEN:
  → PNS platform runs on Java Spring Boot — single language across team
  → Existing deployment pipelines are Java-optimised (Looper, KITT)
  → Monitoring, alerting, and observability tooling already Java-native
  → Introducing Python = second language + second pipeline + second on-call burden

ACCEPTED TRADE-OFF:
  Less mature AI ecosystem (LangChain4j) in exchange for
  operational simplicity and team expertise alignment.
```

### Trade-off 2: Monolith vs Microservices

```
WHAT WAS BUILT: A well-structured monolith with AI + domain capabilities
                in a single Spring Boot application.

WHY NOT MICROSERVICES:
  → AI agent layer and domain tools are tightly coupled by design
    (the agent decides which tools to call based on context — not via API)
  → Network hops between AI service and tool service would add 50–100ms per
    tool call, and a complex query makes 5–10 tool calls → 500ms–1s overhead
  → Team size: 4–6 engineers. Microservices overhead not justified.

ACCEPTED TRADE-OFF:
  Deployment simplicity and tool invocation performance in exchange for
  a larger single deployment unit.
```

### Trade-off 3: Cassandra vs PostgreSQL for Conversation Memory

```
POSTGRES ADVANTAGES:
  → ACID transactions (stronger guarantee)
  → Rich query support (joins, aggregations)
  → Simpler operational model

WHY CASSANDRA:
  → Write pattern: every message in every active session writes simultaneously
    → write-heavy workload that Cassandra's LSM tree handles better
  → Memory access pattern: always read by session key (no joins needed)
    → simple key lookup = Cassandra's strongest case
  → TTL: native per-row TTL for auto-expiring old sessions (no cron jobs)
  → Availability: Cassandra's masterless design handles node failures
    gracefully (conversation memory must not go down)

ACCEPTED TRADE-OFF:
  Eventual consistency (rarely relevant for chat) in exchange for
  write throughput and availability.
```

### Trade-off 4: Tool Granularity — Many Small Tools vs Few Large Tools

```
OPTION A: FEW LARGE TOOLS (e.g., one tool: "queryPnsSystem")
  Input: {systemName: "DCC", query: "carrier method CM789"}
  Problem: LLM must generate the right query format.
           Too much ambiguity → more hallucination → wrong results.
           Harder to write a good single description for all use cases.

OPTION B: MANY SMALL TOOLS (chosen: 40+ tools)
  Each tool has one specific job with a specific, precise description.
  LLM reads description → can decide exactly when to use it.
  Each tool's parameters are minimal and unambiguous.

ACCEPTED TRADE-OFF:
  More tools to maintain and describe in exchange for more predictable
  LLM tool selection and more reliable answers.
```

---

## Section 11 — Scaling & Failure Modes

This section covers how the system behaves under stress — the territory
system design interviews explore after the happy path.

### 11.1 — Bottlenecks and How to Address Them

```
BOTTLENECK 1: Azure OpenAI API (gpt-oss-120b calls)
  ─────────────────────────────────────────────────
  Problem:  API has rate limits (tokens per minute, requests per minute)
  At scale: 1000 concurrent users → 1000 concurrent LLM calls → rate limit hit

  Mitigation:
  → Request queuing (virtual thread pool with backpressure)
  → Exponential backoff with jitter on rate limit 429 responses
  → Streaming reduces perceived latency (user sees partial responses faster)
  → Cache common tool call results (Caffeine in-memory, e.g., static carrier method data)

BOTTLENECK 2: Downstream PNS API calls per tool
  ───────────────────────────────────────────────
  Problem:  Each tool makes an HTTP call to DCC, Wakanda, etc.
            One complex query → 5–10 sequential HTTP calls → 20–40s latency

  Current state: Sequential tool calls (LLM calls one at a time)
  Improvement:   Parallel tool execution where tools are independent
                 (e.g., Wakanda inventory + Unified Promise can run in parallel)
                 ParallelProductAnalysisTools.java was added for this purpose

  Further:  Cache DCC template data (static, rarely changes)
            with Caffeine + short TTL (5 min)

BOTTLENECK 3: Milvus vector search
  ─────────────────────────────────
  Problem:  Each query requires an ANN search over potentially millions of vectors
  At scale: High QPS → search latency spikes

  Mitigation:
  → Milvus supports sharding and index types (HNSW for speed/recall balance)
  → Tenant-isolated collections → smaller search spaces per query
  → Cache embedding vectors for repeated questions (same question = same vector)
```

### 11.2 — Failure Scenarios

```
SCENARIO 1: Azure OpenAI is down
  Impact: All chat queries fail
  Detection: Health endpoint /api/health returns AI_UNAVAILABLE
  Response: Return error with retry guidance, log with alert
  Design decision: No fallback model (complexity vs gain ratio not justified)

SCENARIO 2: Cassandra is down
  Impact: Memory reads/writes fail → conversation history lost
  Detection: Circuit breaker (Resilience4j) fires on connection timeouts
  Response: Degrade gracefully — continue chat without persistent memory
           (user sees a fresh conversation but AI still functions)
  Key insight: Memory is important but not critical path for AI reasoning

SCENARIO 3: One downstream PNS API is down (e.g., DCC)
  Impact: Tools that call DCC return structured error strings
  LLM behavior: Tells user "DCC is unavailable, cannot check carrier config"
  Design decision: Structured error return (not exception throw) — see Section 4.3

SCENARIO 4: Tool returns stale Cassandra data
  Impact: AI gives answer based on outdated configuration
  Mitigation: System prompt instructs AI to note when data might be stale
             (e.g., "Cassandra data may lag behind DCC by up to 5 minutes")
             DCC API call preferred over Cassandra for most questions
```

### 11.3 — Observability Design

```
THREE PILLARS:

  LOGS (Structured JSON via AiInteractionLogger):
  ─────────────────────────────────────────────────
  Every AI interaction logged:
  {
    "sessionId": "sess_abc123",
    "tenantId": "hvgqan",
    "market": "MX",
    "userMessage": "why is offer 12345...",
    "toolsCalled": ["getOfferTemplateNodeMapping", "getCarrierMethodInfo"],
    "durationMs": 8400,
    "tokenCount": 847,
    "responseQuality": "tool_success"
  }

  METRICS (JVM + custom):
  ────────────────────────
  - ai_response_duration_ms (histogram)
  - tool_calls_per_request (counter by tool name)
  - llm_token_usage (counter by tenant)
  - memory_operations_total (Cassandra read/write)

  TRACES:
  ───────
  - Distributed trace spans per request (Spring Boot auto-instrumentation)
  - Tool call spans: start time, end time, tool name, success/failure
  - LLM call span: token count, model, tenant

WHAT YOU'D WANT THAT'S MISSING (interview honesty point):
  "One gap: we don't have structured logging for LLM reasoning steps —
   which tool description influenced a specific tool selection. If the
   LLM picks the wrong tool, we can see the wrong answer but can't
   easily trace which description caused it. Adding a 'reasoning trace'
   log would accelerate iteration on tool descriptions."
```

---

## Section 12 — System Design Interview Q&A

### Q1: "Design an AI assistant for your engineering operations team."

**60-second answer structure:**

> "Start with the problem: on-call engineers need to debug multi-system
> failures fast. My design has six layers.
>
> A chat frontend streams agent thinking steps in real time using SSE —
> users see which APIs are being called as it happens.
>
> A Spring Boot API layer handles auth (JWT/SSO), tenant detection, and
> routing. It stores every message transactionally in Cassandra — write
> the user message first, then run the AI, then write the response.
> This handles crashes during long LLM calls.
>
> The AI layer uses LangChain4j to orchestrate the ReAct loop: the LLM
> reasons about which tool to call, the tool executes a real API call,
> the result feeds back into the LLM context. This repeats until the
> LLM has enough data to answer.
>
> Tools are the key engineering challenge — 40+ methods wrapping domain
> APIs. The descriptions must be specific enough that the LLM reliably
> picks the right one.
>
> Cassandra handles conversation memory (write-heavy, key-based reads).
> Milvus handles RAG — internal documents stored as vectors, retrieved
> by semantic similarity before each LLM call.
>
> Multi-tenancy uses ThreadLocal context per request — separate memory
> namespaces and Milvus collections per market."

---

### Q2: "How does your AI agent avoid hallucinating?"

> "Three defences. First, grounding in real data — every answer is based
> on tool call results from actual PNS APIs, not from the LLM's training
> memory. Second, structured tool descriptions — precise descriptions
> prevent the LLM from calling the wrong tool and getting wrong data.
> Third, RAG documents — for policy questions, relevant runbooks are
> injected as context so the LLM reads from the source rather than
> guessing.
>
> The remaining risk is tool result misinterpretation — the data is
> correct but the LLM reasons over it incorrectly. We mitigate this
> with system prompt rules that encode the correct reasoning order for
> common PNS debugging scenarios."

---

### Q3: "How would you scale this to 10x users?"

> "Three scaling dimensions.
>
> LLM throughput: Add request queuing with backpressure. Cache tool
> results for static data (carrier method configs don't change minute-to-minute).
> Use Azure OpenAI's PTU (provisioned throughput) tier to guarantee capacity.
>
> Tool latency: Convert sequential tool calls to parallel where tools are
> independent. We partially did this with ParallelProductAnalysisTools.
> Full parallelism would need the LLM to declare tool dependencies upfront —
> a harder problem.
>
> Memory and vector store: Cassandra scales horizontally by adding nodes —
> partition key design is already sharding-ready. Milvus can scale collection
> replicas for search throughput.
>
> The true bottleneck at 10x would be Azure OpenAI rate limits — that's an
> external constraint, not an architecture one. The answer there is provisioned
> capacity, not system redesign."

---

### Q4: "How do you ensure tool selection is correct as the domain grows?"

> "Today we test with historical incidents — real past cases where the
> correct answer is known. We run them through the agent and check whether
> it called the right tools in the right order.
>
> As the tool count grows (we're at 40+), tool description management becomes
> harder. The right direction is a tool evaluation framework: automated test
> cases per tool with expected tool selections, run on every description change.
> This is the equivalent of unit tests for LLM tool routing.
>
> The other risk is tool overlap as new tools are added. The solution is
> a tool taxonomy — explicitly grouping tools and adding 'use this instead
> of X when Y' guidance in descriptions."

---

### Q5: "Why not just expose the PNS APIs directly to engineers instead?"

> "Engineers already have access to those APIs. The gap this solves is
> not access — it's interpretation. The APIs return raw data. The AI
> provides reasoning over that data.
>
> An engineer looking at DCC knows what maxSLA=3 means — but connecting
> that to 'therefore the offer can't get 2-day' requires knowing the
> eligibility logic, the zone-to-SLA mapping, and the carrier restriction
> precedence. That reasoning is what we encoded in the system prompt.
> The AI doesn't replace the APIs — it interprets them."

---

### Q6: "What would you do differently if you were designing this from scratch today?"

> "Three things.
>
> First, a tool unit test suite — not integration tests through the full
> agent, but isolated tests with mocked API responses. This would have
> cut the validation loop from days to hours.
>
> Second, structured logging for LLM reasoning traces — which tool
> description influenced a specific selection. Right now we see wrong
> answers but can't easily trace the description that caused them.
>
> Third, a more explicit evaluation framework for the system prompt —
> a dataset of (question, expected_tool_sequence, expected_root_cause)
> triples that run automatically on any system prompt change. Without
> this, prompt iteration is manual and slow."

---

## Section 13 — Quick Reference: The Numbers

Interviewers probe numbers. Know these.

| Metric | Value | Why it matters |
|---|---|---|
| Tools implemented | 40+ | Shows scope of domain coverage |
| Average response time | 8–12 seconds | Baseline for latency questions |
| Max LLM call duration | 120 seconds | Motivates transactional pattern |
| Context window | 8,192 tokens | Explains 30-message memory limit |
| Memory max messages | 30 | Calibrated to context budget |
| Embedding dimensions | 1536 | text-embedding-3-large |
| LLM model | gpt-oss-120b (Azure OpenAI) | Enterprise, data stays in Walmart |
| Embedding model | text-embedding-3-large | High recall, standard for RAG |
| Backend language | Java 17 | Not Python — explain the trade-off |
| Framework | Spring Boot 3.2.3 + LangChain4j 0.27.1 | |
| Streaming protocol | SSE (chat) + WebSocket (MCP Tools) | Different jobs, different protocols |
| Conversation DB | Apache Cassandra | Write-heavy, TTL, HA |
| Vector DB | Milvus | ANN search, tenant-isolated |
| Markets | Mexico (hvgqan), Canada (qxjed8) | Multi-tenant, not global |
| LLM temperature | 0.2 | Precise, not creative |

---

## Section 14 — Architecture Diagram Cheat Sheet

The simplest version to draw on a whiteboard in 3 minutes:

```
  [React UI]
       │ SSE stream
       ▼
  [Spring Boot — Auth + Routing]
       │
       ▼
  [Orchestration — Save → Route → Execute → Save]
       │
       ▼
  [LangChain4j Agent]
  System Prompt + Cassandra Memory + Milvus RAG
       │
       ▼ ReAct loop
  [gpt-oss-120b] ←→ [40+ Tools] → [DCC, Wakanda, Cassandra, Unified Promise]
       │
       ▼
  [Final Answer streamed back via SSE]

  Side stores:
  Cassandra ← conversation history (write-heavy, key-lookup)
  Milvus    ← PNS docs as vectors (semantic search, per-tenant)
```

---

## Section 15 — 3 Sentences That Win the Interview

Practise these until they are automatic.

**1. The problem sentence:**
> "This app collapses 15–25 minutes of manual multi-system PNS debugging
> into a single plain-English question answered in 8–12 seconds."

**2. The architecture sentence:**
> "LangChain4j manages the ReAct loop — the LLM reasons, calls a tool,
> observes the result, and repeats until it has enough data to answer —
> backed by Cassandra for conversation memory and Milvus for document RAG."

**3. The contribution sentence:**
> "I owned the PNS domain layer — 40-plus tool implementations that expose
> real PNS APIs to the AI, and the system prompt that encodes the debugging
> reasoning logic that 5 years of PNS experience gave me."

---

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
✅  This file covers system design questions for agentic AI work.
    Cross-reference:
    → Architecture depth:        02_HOW_IT_IS_BUILT.md
    → Contribution details:      03_MY_CONTRIBUTION.md
    → LLM/Transformer concepts:  06_MM_TRANSFORMERS_LLM.md
    → RAG & Agent deep dive:     07_MM_RAG_AND_AGENTS.md
    → All Q&A compiled:          04_INTERVIEW_QA.md
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```
