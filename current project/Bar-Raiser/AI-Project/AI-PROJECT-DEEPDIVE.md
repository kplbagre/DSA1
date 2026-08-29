# AI Project — Deep-Dive, Concepts & Architectural Decisions
### The complete "explain the AI project" note — the system, the AI concepts (with diagrams), and WHY every choice was made

> **What this is:** the full technical explainer for your AI project. Three things in one: (1) what the system is + how it's built, (2) the **AI concepts taught properly, with diagrams** (LLMs, tokens, attention, RAG, embeddings, the agent loop, memory), and (3) a **decision log** (every choice → alternatives → trade-off) in the `AllChoicesInitial.md` style. You reason FROM this.
>
> **Not here:** STAR stories / ownership / behavioral pushbacks → [AI-PNS-STORIES.md](AI-PNS-STORIES.md).
>
> ⚠️ **Scrub rule:** internal service codenames, class names, the exact internal model name, and market-specific literals → concepts. Example IDs (offer 12345, CM789, T123) are illustrative placeholders. Standard/OSS names (LangChain4j, Milvus, pgvector, Pinecone, Cassandra, Apache Tika, RAG, ReAct) and generic config values (temperature, tokens, 1536-dim) are safe.
>
> ⚠️ **Honesty rule:** you owned the **domain layer** (40+ tools, system prompt, conversation handler). Infrastructure (framework wiring, memory, vector DB, WebSocket, auth, frontend) was team-built. Never claim the whole app.
>
> **Source coverage:** distilled + scrubbed from `aiPnSBackend/prep` `01` (big picture), `02` (how built), `03` (contribution), `04` (28+ Q&A), `06` (transformers/LLMs), `07` (RAG & agents), `08` (design decisions). Raw originals stay local.

---

## 🧭 Contents

**Part 1 — The Project**
- [§1 · What the app is](#1--what-the-app-is)
- [§2 · Architecture (6 layers)](#2--architecture-6-layers)
- [§3 · Stack](#3--stack)
- [§3.5 · How it's implemented (backend mechanics)](#35--how-its-implemented-backend-mechanics)

**Part 2 — How LLMs & Agents Work (concepts, with diagrams)**
- [§4 · LLM fundamentals](#4--llm-fundamentals)
- [§5 · RAG](#5--rag-retrieval-augmented-generation)
- [§6 · AI agents](#6--ai-agents)
- [§7 · RAG vs Tools](#7--rag-vs-tools-when-to-retrieve-vs-call)
- [§7.5 · Why an agent, not a script?](#75--why-an-agentllm-at-all-not-a-hand-written-script)

**Part 3 — Architectural Decisions (why-this-not-that)**
- [§8 · The 8 decisions](#8--the-decisions)
- [§9 · Concrete configs](#9--concrete-configs-the-details-that-prove-depth)

**Part 4 — Interview**
- [§10 · Question → where answered](#10--likely-questions--where-answered)
- [§11 · What I'd do differently](#11--what-id-do-differently-never-nothing)
- [§12 · Salesforce bridge](#12--salesforce-bridge)
- [§13 · Interview Q&A (real questions + pushbacks)](#13--interview-qa-real-questions--pushbacks)

> Companion: [AI-PNS-STORIES.md](AI-PNS-STORIES.md) (stories, ownership, behavioral pushbacks).

---

# PART 1 — THE PROJECT

## §1 — What the App Is

**The pain:** a PNS on-call engineer gets paged — "why isn't offer 12345 getting 2-day delivery in Mexico?" To answer, they manually click through 4–5 systems (template/config, carrier data, inventory, promise data), connecting the dots by hand at 2am. **15–25 minutes per incident.**

**The app:** one plain-English question → the agent decides which systems to query, chains them, and returns a **reasoned root cause + fix in ~8–12 seconds.**

### 🎨 Visual — the mental model: "the smart intern with a phone"

```
┌──────────────────────────────────────────────────────────────┐
│  A very smart intern who:                                    │
│    ✓ can reason through complex problems                     │
│    ✓ has read every PNS doc                                  │
│    ✗ has NEVER logged into a live system                     │
│                                                              │
│  ...but you hand them a PHONE with 40+ contacts:             │
│    📞 "config/template service" → getTemplateInfo, ...       │
│    📞 "inventory service"       → getInventory, ...          │
│    📞 "promise-aggregation svc" → getPromiseData, ...        │
│                                                              │
│  Now they can call any system, reason over the results,      │
│  and give a clear answer.                                    │
│                                                              │
│    intern            = the LLM (an internal ~120B model)     │
│    phone directory   = the 40+ tools          ← MINE         │
│    the day-1 briefing= the system prompt      ← MINE         │
└──────────────────────────────────────────────────────────────┘

KEY INVARIANT:
   The LLM supplies REASONING; the tools supply LIVE DATA.
   Neither alone is enough — the value is the combination.
```

**Users:** on-call/ops engineers (Mexico & Canada) via the chat UI; an admin team (access, analytics, feedback); a "tools explorer" to invoke any tool manually for verification.

---

## §2 — Architecture (6 layers)

### 🎨 Visual — the 6 layers, top (user) to bottom (data)

```
┌─ LAYER 1 · FRONTEND ────────────────────────────────────────┐
│  React + TypeScript chat UI · market/domain selectors ·      │
│  session history · a live "agent thinking" panel             │
│  message streams over SSE (server→client)                    │
└───────────────┬──────────────────────────────────────────────┘
                ▼
┌─ LAYER 2 · API ENTRY ───────────────────────────────────────┐
│  Spring Boot · enterprise SSO (JWT) · market-access check    │
└───────────────┬──────────────────────────────────────────────┘
                ▼
┌─ LAYER 3 · ORCHESTRATION ───────────────────────────────────┐
│  transactional memory (save user msg first) · domain         │
│  detection · tenant/market context · memory management       │
└───────────────┬──────────────────────────────────────────────┘
                ▼
┌─ LAYER 4 · AI AGENT (LangChain4j) ──────────────────────────┐
│  builds prompt (system instructions + RAG docs + history +   │
│  tool defs) → runs the loop: call model → run tools → repeat │
│  ← the SYSTEM PROMPT is MINE                                 │
└───────────────┬──────────────────────────────────────────────┘
                ▼
┌─ LAYER 5 · TOOLS ───────────────────────────────────────────┐
│  40+ tool classes wrapping internal PNS APIs   ← MINE        │
└───────────────┬──────────────────────────────────────────────┘
                ▼
┌─ LAYER 6 · DATA ────────────────────────────────────────────┐
│  Cassandra (conversation memory) · Milvus (doc vectors) ·    │
│  live PNS APIs                                               │
└──────────────────────────────────────────────────────────────┘

KEY INVARIANT:
   Layers 1–3 + 6 are team-built infrastructure. Layer 5 and the
   prompt in Layer 4 are the DOMAIN layer — mine — where PNS
   expertise (not AI plumbing) was the hard part.
```

**Request path:** user asks → SSE opens → API validates auth+market → orchestration **saves the user message**, classifies intent, loads memory → agent reads the system prompt and runs the agent loop over tools → synthesizes root cause + fix → streams token-by-token → **saves the final answer.**

---

## §3 — Stack

| Layer | Tech | One-line why |
| --- | --- | --- |
| Frontend | React + TypeScript, SSE | streaming chat UX |
| Backend | Spring Boot, Java 17 | matches the existing PNS Java stack |
| AI orchestration | LangChain4j | Java agent/tool framework — no second language |
| LLM | internal ~120B open-weight model on Azure tenant | data stays in Walmart |
| Embeddings | large embedding model (`text-embedding-3-large`, 1536-dim) | semantic search for RAG |
| Conversation memory | Cassandra | write-heavy, partition-key access |
| Vector DB | Milvus | high-dim similarity search at scale, self-hosted |
| Document parsing | Apache Tika (+ OCR) | ingest PDFs/Word/images for RAG |
| Real-time | SSE (chat) + WebSocket (tools protocol) | right protocol per direction |

---

## §3.5 — How It's Implemented (Backend Mechanics)

> **For "how did you achieve it, code-wise?"** Read this in two parts:
> **(A) What I built — go deep here** (they'll push on it). **(B) Framework wiring — understand conceptually, don't over-claim** (the team set it up; you speak to it by *mapping each piece to a Spring/Java pattern you already know*).
>
> 🔑 **The unlock:** every AI-framework construct is just a Spring/Java pattern you've used for years, wearing a new name. Learn the *mapping*, not the class.

### A. What I built — go deep (your domain layer)

**1) The tools (40+ of them) — this is the core of my work.**
- A **tool is just an annotated Java method** the LLM is allowed to call. The annotation registers it — **exactly like `@KafkaListener` registers a consumer or `@RequestMapping` registers an endpoint.** The only twist: instead of Kafka or an HTTP route triggering it, the **LLM decides when to call it — by reading the annotation's description.**
- Each tool is a Spring `@Component`; the method body just **delegates to the underlying REST service client** (the same PNS APIs I've called for years) and returns a **formatted string.** On failure it returns a **structured error string, never throws** — so the model reports "config service timed out" instead of hallucinating.

```java
// The description is what the LLM reads to decide when to call this.
@Tool("Use to get SLA / zone / express support for a carrier method — "
    + "primary tool for carrier eligibility failures")
String getCarrierMethodInfo(@P("carrier method id") String cmId) {
    return carrierService.get(cmId);   // just a normal REST call underneath
}
```
> **Say it:** "A tool is an annotated Java method — like a `@KafkaListener`, but the *LLM* triggers it based on the description I write. The body is a normal REST call to our PNS APIs. The hard part wasn't the code — it was writing descriptions precise enough that the model picks the *right* tool."

**2) The system prompt — the other thing I owned.**
- It's a **block of instructions injected at the top of every request** — think **config-/rules-driven behavior**, but for the LLM. Marked with the `@SystemMessage` annotation on the agent interface.
- Mine encodes the **debugging order** (template → carrier → zone → transit → inventory-last — how a senior engineer actually debugs) and **market defaults**, and **anti-hallucination rules** ("if a tool returns empty, say so; never invent; cite the API"). It was iterative — every gap showed up as a wrong answer.
> **Say it:** "The system prompt is rules injected before every question. I encoded the PNS debugging order and market defaults into it — that's domain knowledge turned into instructions the model follows."

### B. Framework wiring — understand conceptually (map to what you know)

> You didn't build these; the team did. **Speak to each by its Spring/Java analogy — that's honest and it's the senior move.** You do NOT need to defend the internals.

| Framework piece | You already know this as… | One line to say |
| --- | --- | --- |
| **The agent** (LangChain4j `AiServices`) | a **Feign client / Spring dynamic proxy** — you declare an *interface*, the framework generates the impl | "I *declared* the agent interface; the framework runs the loop — call model → run tools → repeat. I didn't hand-roll it." |
| **The LLM** (`AzureOpenAiChatModel`) | a **REST client bean** to an endpoint, built with a builder (temperature, timeout from config) | "It's a configured client to our internal model on Azure." |
| **Memory** (`MessageWindowChatMemory` + Cassandra store) | a **DAO/repository** — load/save the last N messages by key | "Last 30 messages, persisted to Cassandra by a session key — plain CRUD." |
| **RAG retrieval** (retriever over Milvus) | a **search client** — "give me the 5 nearest docs" | "A nearest-document search over Milvus; the top 5 get injected into the prompt." |
| **Chat streaming** (`SseEmitter`) | a **streaming HTTP response** — push events as work completes | "Server-sent events — I push each step to the UI as it finishes." |
| **Tenant context** (`ThreadLocal` + interceptor) | **your MCSE `RequestContextHolder` / MDC correlationId pattern** | "Same as how we carry correlationId in MCSE — a ThreadLocal set per request, here for market." |
| **Transactional memory** | the **write-ahead-log** pattern | "Save the user message before running the AI, save the answer after — so a crash mid-call doesn't lose the question." |

> **The whole "how did you build it" answer (say this):**
> "I didn't hand-roll the agent loop — I *declared* the agent interface with LangChain4j and the framework runs call-model → run-tools → repeat. **What I built is the domain layer:** the 40+ tools — annotated Java methods that wrap our PNS APIs, where the LLM picks which to call from the description I wrote — and the system prompt that encodes our debugging order. The rest — memory as a Cassandra-backed window, retrieval as a Milvus search, streaming as server-sent events — is standard framework wiring the team set up. Every one of those maps to a Spring pattern I already use."

---

# PART 2 — HOW LLMs & AGENTS WORK (the concepts)

> These are the concepts a deep AI probe hits. Learn the *why*, not the syntax.

## §4 — LLM Fundamentals

### 4.1 An LLM is "autocomplete that grew up"

An **LLM** (large language model — a neural network trained to predict the next word-piece) is your phone keyboard's autocomplete, scaled to the entire internet.

```
PHONE AUTOCOMPLETE:  "I will be there" → suggests "soon" / "at 5"
                     (learned from YOUR past messages)

LLM:  "Why is offer 12345 not" → predicts "eligible" → "for" →
      "2-day" → "delivery" ...   (learned from trillions of words)

Each token is predicted ONE AT A TIME, using everything before it.
```
> The "magic" is just very good next-token prediction at enormous scale.

### 4.2 A token is a word-*piece*, and it drives everything

A **token** (the unit the model processes) ≈ ¾ of a word. "carrier" = 1 token; "carrier method" = 2; "getCarrierMethodInfo" ≈ 4–5. Rule of thumb: **~750 words ≈ 1,000 tokens.**

### 🎨 Visual — the context-window budget

```
The LLM can only process a MAXIMUM number of tokens at once =
the CONTEXT WINDOW. Ours: ~8,192 tokens. Everything must fit:

  ┌─────────────────────────────────────────────┐
  │  System prompt         ~800 tokens          │
  │  Tool definitions (40+) ~2,000 tokens       │
  │  RAG documents (top-5)  ~1,500 tokens       │
  │  Conversation history   ~2,000 tokens       │
  │  User message           ~200 tokens         │
  │                         ───────────────      │
  │                         ~6,500 used          │
  │                         ~1,700 remaining     │
  └─────────────────────────────────────────────┘

KEY INVARIANT:
   The 30-message memory cap is a DIRECT consequence of this budget.
   Want deeper history? Bigger-context model, or summarize old turns.
```

### 4.3 The context window is a spotlight

Everything inside the window, the LLM sees; everything outside doesn't exist to it. A message from 3 days ago (outside the window) is simply forgotten — which is *why* history is persisted externally (Cassandra) and re-injected.

### 🎨 Visual — temperature = the creativity dial

```
  0.0        0.2         0.5         1.0          2.0
   │          │           │           │            │
 ROBOT     OUR APP     BALANCED    CREATIVE      CHAOTIC
"max SLA   "max SLA    "around     "probably     "maybe
 is 3       is 3        3 days"     3 or so"      three?"
 days"      days"

We use 0.2 (near-deterministic). A debug tool must answer
"3 days" every time — creative variation is a BUG here.
```
> **Temperature** = how random the next-token choice is. 0 = always the most probable token (deterministic); 1 = real randomness (creative writing).

### 4.4 Attention — why long context = smarter reasoning

The **attention mechanism** lets the model connect related words far apart. Predicting after "…does not support **express**", it assigns high attention back to "CM789" (mentioned 15 words earlier) and low attention to filler words — so it links them correctly.

```
"...carrier method CM789 ... for Mexico Zone 4 ... does not support express..."
  attention weights when generating after "express":
    CM789   → 0.45  (HIGH — the thing being described)
    Mexico  → 0.20
    carrier → 0.15
    the     → 0.01  (filler)
```
> **You don't need to explain *how* attention works — only *why*:** it lets the model connect your question to a tool result mentioned 2,000 tokens earlier. Longer context = more it can connect.

### 4.5 Training freezes the weights → which is *why* we need tools

```
Training: show "carrier method supports ___" → model guesses "banana" →
correct is "standard" → nudge weights → repeat over TRILLIONS of examples.
AFTER training: weights are FROZEN. The model stops learning.

⇒ the LLM does NOT know last week's config change, today's inventory,
  or CM789's current SLA. It has a training cutoff.
⇒ THIS is why tools exist: tools call the LIVE systems at runtime.
  LLM reasons; tools provide current data. Perfect division of labor.
```

### The 6 numbers to know cold
`~120B` params · `~8,192` token context · `0.2` temperature · `~120s` LLM call timeout · `1536` embedding dims · `1 token at a time` (sequential generation).

---

## §5 — RAG (Retrieval-Augmented Generation)

### 5.1 Closed-book vs open-book exam

A plain LLM has never seen your internal runbooks → it says "I don't know" or **hallucinates** (confidently makes something up). **RAG** = fetch the most relevant doc chunks and put them in the prompt *before* the model answers — turning a closed-book exam into an open-book one.

You can't just put *everything* in the prompt — thousands of pages = millions of tokens ≫ the context window. So retrieve only the top-k relevant chunks per question.

### 🎨 Visual — embeddings: text as GPS coordinates

```
Convert text → a vector (1536 numbers). Similar MEANING → nearby points,
even with NO shared words.

   "carrier method"  ●
                       ● "delivery method"
                     ● "CM_ID"
                    ● "shipping option"        ← these cluster (close)

                                    ● "banana"
                                       ● "football"   ← far away

DISTANCE = MEANING DIFFERENCE. Retrieval = nearest-neighbor search.

KEY INVARIANT:
   This is SEMANTIC search — by meaning, not keywords. "carrier method"
   finds "delivery option" even though they share zero words.
```
> An **embedding model** is a *different* model from the LLM: it *converts* text to a vector (doesn't generate). Analogy: the embedding model organizes the library by meaning; the LLM reads the relevant books and writes the answer.

### 🎨 Visual — the full RAG pipeline

```
PHASE 1 · INDEXING (once, offline)
  Runbook PDF → Apache Tika → plain text
    → split into ~1,000–1,200-char chunks (~200–300 overlap, property-configurable)
    → embed each chunk → 1536-dim vector
    → store {text + vector} in MILVUS  (thousands of chunks)

PHASE 2 · RETRIEVAL (every query)
  User question → embed with the SAME model → query vector
    → Milvus: "5 nearest vectors" (cosine similarity, min score 0.5)
    → returns top-5 most similar chunks

PHASE 3 · GENERATION
  [system prompt] + [5 retrieved chunks] + [history] + [tools] + [question]
    → LLM answers grounded in the retrieved runbook content

KEY INVARIANT:
   Same embedding model for docs AND the query — they must live in the
   same vector space or "nearest" is meaningless.
```

**RAG numbers:** ~1,000–1,200-char chunks · **~200–300-char overlap** (so a sentence split across a boundary isn't lost) · top-5 returned · min similarity 0.5 · 1536 dims · cosine similarity · IVF_FLAT index (fast at scale). *(All property-configurable; code defaults ~1,000/200.)*

---

## §6 — AI Agents

### 🎨 Visual — the agent loop (ReAct = Reason + Act)

```
USER: "Why is offer 12345 not getting 2-day delivery?"
        │
  ╔═════▼══════════════════ AGENT LOOP (repeats) ══════════════╗
  ║ REASON  "Need the offer's template." → getOfferTemplate(12345)
  ║ ACT     → template service → returns T123, node N456        ║
  ║ OBSERVE "Template T123. Now get its details."               ║
  ║ REASON  → getTemplateInfo(T123)                             ║
  ║ ACT     → returns: STANDARD only, carrier CM789             ║
  ║ OBSERVE "Only Standard. CM789 is the carrier — check it."   ║
  ║ REASON  → getCarrierMethodInfo(CM789)                       ║
  ║ ACT     → returns: maxSLA=3, express=false                  ║
  ║ OBSERVE "Enough. CM789 doesn't support express = root cause"║
  ║ FINAL   generate answer, EXIT loop                          ║
  ╚═════════════════════════════════════════════════════════════╝
        │
        ▼
"Offer 12345 can't get 2-day because carrier method CM789 on template
 T123 only supports Standard delivery."

Average 3–7 tool calls/question; hard cap ~100 iterations.

KEY INVARIANT:
   Reason → Act → Observe, repeat until it has enough to answer.
   The system prompt encodes the ORDER (template → carrier → …).
```

### 🎨 Visual — how a tool call actually works

```
The LLM does NOT execute code — it REQUESTS a call; the framework runs it.

  LLM emits: "TOOL_CALL getCarrierMethodInfo {cmId:'CM789'}"
      │
  LangChain4j intercepts → finds the Java method →
      calls the real API → {maxSLA:3, express:false}
      │
  result appended to the prompt → LLM called again with it

The tool DEFINITION the LLM reads to decide:
  @Tool("Use this to understand SLA, zone coverage, and express support
         for a carrier method — primary tool for carrier eligibility
         failures.")
  String getCarrierMethodInfo(@P("carrier method id") String cmId)

KEY INVARIANT:
   The DESCRIPTION is the code. Vague description → wrong tool → wrong
   answer. Writing these precisely = the hardest part of my work.
```

### 🎨 Visual — the 3 types of memory

```
TYPE 1 · IN-CONTEXT   last 30 messages, inside the prompt, this convo only
TYPE 2 · EXTERNAL     all messages ever, in Cassandra, permanent
TYPE 3 · SEMANTIC     PNS docs/runbooks, in Milvus, retrieved via RAG

WHICH ANSWERS WHAT:
  "What did I ask 3 messages ago?"  → Type 1 (in-context)
  "What did I ask last week?"       → Type 2 (Cassandra)
  "What's our escalation process?"  → Type 3 (Milvus RAG)
  "What's the CURRENT config?"      → a TOOL CALL (live, not memory)
```

### What LangChain4j does for you
Without it you'd hand-build: call the model → detect tool requests → find/execute the method → append results → loop → manage/trim/persist history → inject RAG → handle retries/timeouts. **With it**, you *declare* the agent interface + `@Tool`-annotated methods + system prompt, and the framework runs the whole loop.

### The system prompt (the "day-1 briefing")
A permanent block sent to the LLM before every request — it shapes every decision. **Yours encodes:** identity ("PNS on-call assistant"), **tool-call order** ("for eligibility: template first → carrier restrictions → zone → transit → inventory last" — mirrors how a senior engineer debugs), **market defaults** (a default tenant/postal so queries missing them still work), and **anti-hallucination rules** ("if a tool returns empty/fails, say so; never invent data; cite which API you called"). It was *iterative* — every gap showed up as a wrong answer.

### Evaluation — how you know it's correct
"How do you know the AI is right?" is a top probe. The answer: **validated against ~20 real historical incidents** where the root cause was already known from manual debugging — check the agent called the right tools, in the right order, and reached the correct cause; each miss traced back to a tool description or a prompt rule. It doubles as a **regression set** (a PNS change that breaks a known case fails the check). Plus expert review of a weekly sample for *reasoning soundness*, not just plausible-looking output. *(Full story: [AI-PNS-STORIES.md](AI-PNS-STORIES.md) AI-4.)*

---

## §7 — RAG vs Tools (when to retrieve vs call)

| Use RAG (retrieve a doc) | Use a Tool (call an API) |
| --- | --- |
| Answer is in **static text** — runbooks, processes, postmortems | Answer is in **live system state** — current config, inventory, promise data |
| "What's our Sev1 escalation process?" | "Why is *this* offer not 2-day eligible *right now*?" |
| Semantic search over embeddings | Function-call reasoning over real-time data |

> Senior line: "A static prompt only holds data known at authoring time; PNS state is live. No prompt contains what config says *right now* — so the agent calls the API at query time. For a debug tool, stale data is worse than no data."

---

## §7.5 — Why an Agent/LLM at All, Not a Hand-Written Script?

> A fair, common pushback ("this is a fixed template→carrier→zone flow — why not just code a script or decision tree?"). Three legs to the answer:

1. **The input is free-form natural language, not a fixed form.** Engineers ask "why is this offer slow in MX?" a hundred different ways. A script must pre-code every phrasing; the LLM parses intent from free text.
2. **Offers fail for *many* reasons, not one fixed path.** Template, carrier, zone, capacity, inventory, cutoff… The order in the prompt is a *starting heuristic* — the agent adapts based on what each tool returns (that's the ReAct loop). A decision tree would need every branch hand-maintained as PNS evolves.
3. **The output is a synthesized, plain-English root cause + fix across 4–5 systems** — not raw data. A script returns fields; the value here is the *reasoning + explanation* an on-call engineer can act on at 2am.

> **Say it:** "A script handles one exact question shape. The win is that engineers ask in free text, offers fail for many different reasons, and they want a reasoned answer — not five raw API dumps to interpret at 2am. That's reasoning over live data, which is what an agent is for. Where a step *is* deterministic, I kept it in the tool, not the LLM."

---

# PART 3 — ARCHITECTURAL DECISIONS (why-this-not-that)

> Frame every one as: **"We needed Y; X gave us Z; the trade-off was W."** No trade-off = junior.

## §8 — The Decisions

### D1 — Cassandra (not PostgreSQL) for conversation memory
- **Need:** thousands of concurrent users × ~1 write/message × 10–30 turns = **1,000+ writes/sec**; primary query = "get all messages for a session" (partition-key lookup).
- **Why Cassandra:** horizontal write scaling; partition-key read = one seek; team already runs it.
- **Rejected:** PostgreSQL — vertical scaling ceiling on write-heavy load.
- **Trade-off:** no JOINs / cross-session analytics (run separately).

```
POSTGRES (vertical):  more users → bigger box → hits a ceiling
CASSANDRA (horizontal): more users → add nodes → scales linearly
   [Node1][Node2]  →  [Node1][Node2][Node3]   (writes spread across all)
```

### D2 — Milvus (not pgvector or Pinecone) for vectors
- **Need:** top-5 similar chunks from potentially millions of 1536-dim vectors, in ms, every query.
- **Why Milvus:** purpose-built indexes (IVF_FLAT) hold performance at scale; **self-hosted → data stays in Walmart.**
- **Rejected:** **Pinecone** (external cloud → data-residency violation); **pgvector** (degrades beyond a few million vectors — PostgreSQL isn't built for high-dim similarity search).
- **Trade-off:** another DB to operate; Java SDK less mature.

```
                data residency   scale perf      ops
  pgvector      in Walmart ✓     degrades >1M ✗  low ✓
  Pinecone      external ✗       good            zero ✓
  Milvus        in Walmart ✓     excellent ✓     medium
                → Milvus: residency rules out Pinecone, scale rules out pgvector
```

### D3 — SSE for chat / WebSocket for tools
- **Need:** stream each step to the UI (server→client); separately, a bidirectional tools protocol.
- **Why:** **SSE** = plain HTTP, proxy/CDN-friendly, sufficient for one-way push (classifier → planner → tool events → answer). **WebSocket** for tools because the client also *sends* calls (full duplex).
- **Trade-off:** SSE is one-way (fine — chat only pushes); WebSocket is more complex (used only where bidirectional is truly needed).

### D4 — Transactional memory (write-ahead-log for AI memory)
- **Need:** LLM calls take up to ~120s; a crash mid-call must not lose the user's question.
- **Why:** save user message **immediately** → run AI **in memory** → save response **only on success.**

```
NAIVE:  receive → run AI (90s) → save both      crash@90s ⇒ question LOST
WAL:    receive → SAVE user msg → run AI → save response on success
        crash@90s ⇒ question already persisted (known-incomplete, not lost)
```
- **This is the write-ahead log pattern** applied to conversational memory. **Trade-off:** one extra write per message.

### D5 — ThreadLocal tenant context
- **Need:** one deployment serves Mexico & Canada; concurrent requests must not leak market context.
- **Why:** market in a **ThreadLocal** — each request thread gets its own isolated copy; no shared mutable state.
- **Trade-off:** must clear it on thread reuse (pool threads recycle).

### D6 — In-memory tool results (not persisted)
- **Need:** tools return ~30KB raw API JSON per question.
- **Why:** keep tool results in working memory during the loop; persist only the final human answer. Raw data is stale immediately and would pollute history + waste writes.
- **Trade-off:** can't replay exact intermediate data (fine — it changes every call).

### D7 — Java + LangChain4j (not Python)
- **Need:** ship on the existing PNS platform without a second language/pipeline.
- **Why:** team owns Java/Spring; LangChain4j covers the core agent/tool capabilities; JVM concurrency for parallel tools; one deployment/monitoring stack.
- **Rejected (honestly):** Python — richer/more-mature AI ecosystem, but a second language + pipeline + ops surface.
- **Trade-off:** less mature ecosystem — occasionally build what Python gets free. *(Say the candor out loud — that's the senior signal.)*

### D8 — Internal ~120B model on Azure (not public GPT-4/Claude/Gemini)
- **Need:** every call carries **real Walmart business data** — it cannot leave the network.
- **Why:** an internal ~120B open-weight model on Walmart's **Azure tenant** — same Azure OpenAI interface (no code change), data never leaves; 120B is enough to chain 3–7 tool calls coherently.
- **Rejected:** public OpenAI/Anthropic/Google (data leaves = compliance non-starter); 7B/13B (loses track mid-chain).
- **Trade-off:** not GPT-4-level; depends on the internal ML team to maintain it.

### Quick trade-off reference
| Decision | ✅ Gained | ❌ Gave up |
|---|---|---|
| Cassandra (not Postgres) | write throughput, horizontal scale, familiarity | no JOINs / analytics |
| Milvus (not pgvector/Pinecone) | scale perf + data in Walmart | extra DB to operate |
| SSE chat / WS tools | simplicity + right tool per direction | SSE one-way |
| Transactional memory | crash resilience, no silent loss | extra write/message |
| ThreadLocal context | request isolation | must clear on reuse |
| In-memory tool results | clean history, fewer writes | no exact replay |
| Java + LangChain4j | one language/pipeline, JVM concurrency | smaller AI ecosystem |
| Internal ~120B on Azure | data governance, enough reasoning | not GPT-4, team-maintained |

---

## §9 — Concrete Configs (the details that prove depth)

- **Temperature 0.2** — near-deterministic; consistency is correctness for a debug tool.
- **Context budget ~8,192 tokens** → the ~30-message memory cap follows directly (see §4.2).
- **RAG:** ~1,000–1,200-char chunks / ~200–300 overlap (property-configurable; code default ~1,000/200) / top-5 / min-sim 0.5 / 1536-dim / cosine / IVF_FLAT.
- **Multi-tenancy — 3 isolation levels:** (1) market in a ThreadLocal; (2) Cassandra memory under a composite key `market_domain:tenant:session`; (3) a separate Milvus collection per tenant.
- **Tool resilience:** 45s timeout, 3 retries w/ backoff; on final failure return a **structured error string** (not an exception) so the agent reports the outage instead of hallucinating.
- **Token accounting:** a counting wrapper logs every call's tokens → cost per query/market/day.

---

# PART 4 — INTERVIEW

## §10 — Likely Questions → Where Answered
| They ask… | Reach for |
| --- | --- |
| "Walk me through the architecture" | §2 (6-layer diagram, 90s) |
| "What is RAG / an agent / a token / temperature / attention?" | §4–§6 (with diagrams) |
| "Why Java / Milvus / this model?" | §8 (D7, D2, D8) |
| "How does the LLM pick a tool?" | §6 (description-driven) |
| "How do you prevent hallucination?" | §4.5 + §6 system prompt + §9 structured errors |
| "Multi-tenancy / correctness / slow API?" | §9 (3-level) · §6 eval · §9 resilience |
| "Hardest part / your contribution / how much you built" | AI-PNS-STORIES |
| "What would you change?" | §11 |

## §11 — What I'd Do Differently (never "nothing")
1. **Per-tool test harness (mocked APIs)** — we validated end-to-end, so a wrong answer was hard to isolate (bad tool data vs bad LLM interpretation).
2. **Structured logging of tool-selection reasoning** — we see the wrong answer but not *why* a tool was picked; logging the reasoning chain speeds iteration.
3. *(third, if asked)* standard OAuth2 resource-server instead of hand-rolled JWT; distributed tracing (HTTP → model → each API); per-user rate limiting to protect shared model quota.

## §12 — Salesforce Bridge
The JD makes AI fluency a **core expectation** ("think agentic — decompose problems into agent-executable workflows"). This project *is* that: decomposing a multi-system on-call investigation into tool calls the agent sequences, validated against real incidents, collapsing 15–25 min → ~10 s — and you can defend **every choice with its trade-off** (the SMTS bar). Most candidates can't speak to real agentic work at this depth.

---

## §13 — Interview Q&A (real questions + pushbacks)

> **How to use:** read the **Q** as they'll ask it, say the **A** in your own words (~45–60s), and have the **Pushback → answer** ready. Answers are honest and practical — including where the app *isn't* production-perfect (admitting that is the senior signal).
>
> **Behavioral/ownership Qs** ("tell me about the project", "how much did *you* build", "hardest part", "what would you change") → [AI-PNS-STORIES.md](AI-PNS-STORIES.md). **This section = the technical/concept probes.**

### A. Core — "what is it / how does it work"

**Q1. "Walk me through the architecture."**
- **A:** "Six layers. A React chat UI that streams over SSE. A Spring Boot API doing auth + market check. An orchestration layer that saves the user message, detects the domain, loads memory. The agent layer — LangChain4j — builds the prompt and runs the loop: call the model, run tools, repeat. Below that, 40+ tools wrapping our internal PNS APIs. And data: Cassandra for conversation memory, Milvus for document vectors. The part I built is the tools and the system prompt — the domain layer."
- **Pushback:** *"Which parts did you build vs the team?"* → "I built the domain layer — the 40+ tools and the system prompt. The team wired the framework, memory, vector DB, WebSocket, auth, frontend. That split is deliberate — the plumbing is generic; the domain layer needed five years of PNS."

**Q2. "What is RAG and how is it implemented here?"**
- **A:** "RAG = give the model the relevant docs *before* it answers, instead of hoping it memorized them. The model has never seen our runbooks, so without it, it guesses. We chunk the docs (~1,000–1,200 chars), embed each chunk into a 1536-dim vector, and store them in Milvus. On a question, we embed the question the same way, pull the top-5 nearest chunks by cosine similarity, and inject them into the prompt. It's basically a semantic cache lookup that feeds context in."
- **Pushback:** *"Why not just put all the docs in the prompt?"* → "Thousands of pages = millions of tokens; the context window is ~8K. So we retrieve only the 5 most relevant chunks per question."

**Q3. "What's an AI agent vs just calling an LLM?"**
- **A:** "A plain LLM is text-in, text-out over its training data — it can't take actions or see live data. An agent adds three things: tools (it can call real systems), memory (conversation context), and a loop (call model → run a tool → feed the result back → repeat until it can answer). For us, a plain LLM would invent carrier configs; the agent calls the real API and reasons over actual values."
- **Pushback:** *"So the agent is smarter?"* → "No — same model. It's *equipped* differently: it has hands (tools) and can loop. The intelligence is the same; the access to live data is what changes."

**Q4. "How does the LLM decide which tool to call?"**
- **A:** "Purely from the tool descriptions. LangChain4j puts all the tool definitions — name, description, params — in the prompt, and the model matches the question against them. So the *description is the code*: if it's vague, the model picks wrong. Writing those descriptions precisely — especially disambiguating three similar carrier tools — was the hardest part of my work."
- **Pushback:** *"Isn't that unreliable?"* → "It's as reliable as the descriptions. I validated tool-selection against real scenarios and tightened descriptions until it picked correctly and consistently — measured, not hoped."

**Q5. "This is a fixed debugging flow — why an LLM, not a script?"**
- **A:** "Three reasons: the input is free-text — engineers ask a hundred different ways, a script needs every phrasing pre-coded. Offers fail for *many* reasons, not one path — the order is a heuristic, the agent adapts to what each tool returns. And the output is a synthesized plain-English root cause, not raw fields. Where a step *is* deterministic, I kept it in the tool, not the LLM."
- **Pushback:** *"Couldn't a good rules engine do all that?"* → "It could approximate the fixed parts, but not free-text intent or novel combinations — and you'd hand-maintain every branch as PNS changes. The LLM absorbs that variability."

### B. Trade-offs — "why this, not that"

**Q6. "Why Java + Spring Boot and not Python?"**
- **A:** "First, to be clear — this was a *choice*, not a comfort thing. I've worked in Python (I used it through college), so I could have built it either way. Honestly, Python has the richer AI ecosystem — I'll say that plainly. But our PNS platform runs on Java/Spring: the team owns it, the pipelines and monitoring are Java-native, and it sits next to the same APIs the tools call. Adding Python would mean a *second* language, pipeline, and ops surface for a team that would have to maintain it. LangChain4j — the Java port of LangChain — covers what we needed. So we traded ecosystem maturity for operational simplicity on a team-owned production service."
- **Pushback:** *"What would you have done in Python — and would it have been better?"* → "In Python I'd have used LangChain proper — it's more mature, with a bigger library of integrations and faster prototyping; more community examples to lean on. What I'd give up: it becomes a separate stack from our Java platform (extra deploy + monitoring), and I lose JVM-native concurrency for running tool calls in parallel. For a *standalone* AI service with no existing Java platform, Python would be a very reasonable — maybe better — call. For *this* context, embedded in a Java team's ecosystem, Java won."
- **Pushback:** *"So you just don't know Python?"* → "No — I've used it since college and I'm comfortable in it. That's exactly why I can say Java was the right call *here* rather than the only one I knew. Picking the stack that fits the team over the stack with the shiniest ecosystem is the engineering decision."

**Q7. "Why Milvus and not Pinecone or pgvector?"**
- **A:** "Data residency ruled out Pinecone — vectors of our operational content can't go to an external cloud. Among self-hosted options, pgvector was tempting (one less DB) but degrades past a few million vectors — Postgres isn't built for high-dim similarity search. Milvus uses purpose-built indexes that hold up at scale. Trade-off: another database to operate."
- **Pushback:** *"When would pgvector have been fine?"* → "Smaller corpus — under ~a million vectors — or if we wanted to avoid running a separate system. At our expected scale, the performance cliff made it the wrong call."

**Q8. "Why an internal model and not GPT-4 or Claude?"**
- **A:** "Data governance. Every call carries real Walmart business data — offer IDs, carrier configs. Public OpenAI/Anthropic APIs send that outside our network — a compliance non-starter. We run an internal ~120B open-weight model on our Azure tenant: same interface, data never leaves. 120B is enough reasoning to chain 3–7 tool calls; a 7B would lose track mid-chain."
- **Pushback:** *"Isn't it weaker than GPT-4?"* → "Yes, not GPT-4-level. But for structured tool-chaining with a clear system prompt, 120B is sufficient — and the compliance win is non-negotiable. Capability we didn't need traded for governance we did."

### C. Production / depth

**Q9. "How does multi-tenancy work?"**
- **A:** "One deployment serves Mexico and Canada, isolated at three levels. The market lives in a ThreadLocal set per request — same pattern as correlationId in my MCSE work. Conversation memory is keyed by a composite `market_domain:tenant:session`, so markets never share history. And each tenant has its own Milvus collection, so a RAG query only searches that market's docs."
- **Pushback:** *"What if the ThreadLocal leaks across requests?"* → "It's cleared after each request; pool threads get a fresh context. If it weren't cleared, a recycled thread could carry the previous market — that's the one bug you have to guard, exactly like MDC in our platform."

**Q10. "What's the transactional memory pattern and why?"**
- **A:** "LLM calls can take up to ~120s. If the server crashes mid-call and I saved everything at the end, I'd lose the user's question — a gap in the conversation. So I save the user message *immediately* on receipt, run the AI in memory, and save the response only on success. A crash at second 90 leaves the question safely stored. It's the write-ahead log pattern applied to AI memory."
- **Pushback:** *"What's the cost?"* → "One extra write per message. Cheap insurance against silent data loss."

**Q11. "How do you prevent the AI from hallucinating?"**
- **A:** "Three things. Tools — it fetches real config instead of inventing it. Temperature 0.2 — near-deterministic, no creative embellishment. And the tools return a *structured error string* on failure, not an exception — so if a system is down the agent says 'the config service timed out,' instead of making up an answer. Remaining risk is in synthesis, which is what the validation set covers."
- **Pushback:** *"How do you know the error-return actually stops it?"* → "I tested induced failures — kill the API, confirm the agent reports the outage rather than a fabricated root cause. Verified, not assumed."

**Q12. "How do you know its answers are actually correct?"**
- **A:** "I validated against ~20 real historical incidents where we already knew the root cause — checked the agent called the right tools in the right order and reached the correct cause. Every miss traced back to a tool description or a prompt rule, which I fixed. That set doubles as a regression suite. Plus domain experts reviewed a weekly sample for reasoning soundness."
- **Pushback:** *"20 is a small sample."* → "Agreed — it's a *validation* set, not a statistical guarantee. The honest next step is growing it into a continuous regression set so every new incident becomes a test case. I'd call 20 the floor, not the ceiling."

**Q13. "How does the context-window limit shape the design?"**
- **A:** "The window is ~8K tokens and everything competes for it: system prompt (~800), 40+ tool defs (~2,000), top-5 RAG chunks (~1,500), history, and the question. That's why memory is capped at ~30 messages — it's a direct consequence, not arbitrary. If we needed deeper history, we'd summarize older turns or move to a larger-context model."
- **Pushback:** *"What breaks if you exceed it?"* → "The model truncates or errors — so we budget deliberately: cap history, keep tool defs lean, retrieve only top-5, not top-50."

**Q14. "Fine-tuning vs RAG — which and why?"**
- **A:** "RAG. Fine-tuning bakes knowledge into the weights — you'd retrain every time a runbook changes. Our docs change often, so RAG is right: update Milvus and the agent immediately has the new content, model untouched. Fine-tuning would make sense for teaching a fixed *style* or vocabulary that rarely changes — not for fast-moving facts."
- **Pushback:** *"Would you ever fine-tune here?"* → "Maybe to reduce prompt size — bake the fixed debugging order into the model so I don't spend tokens on it every call. But that's an optimization, not day-one."

**Q15. "How would you scale this 10×?"**
- **A:** "The bottleneck isn't our systems — Spring Boot is stateless (scale horizontally), Cassandra scales by adding nodes, Milvus shards. The real ceiling is the **LLM quota**. So: negotiate quota, add a per-user token budget with graceful backoff, and cache/short-circuit repeated identical questions. I'd protect the shared model capacity before anything else."
- **Pushback:** *"What about latency at 10×?"* → "Latency is dominated by the LLM call and tool round-trips, not our tier — so concurrency + quota, plus running independent tool calls in parallel, is where the wins are."

**Q16. "Why store history in Cassandra, not the LLM's own memory?"**
- **A:** "The LLM is stateless between calls — it has no memory unless you put history in the prompt. LangChain4j manages the in-prompt window, but that needs a durable backing store to survive restarts and work across multiple instances. In-memory would lose history on restart; Cassandra gives durability, horizontal scale, and the exact partition-key access pattern we need. The team already ran Cassandra, so it was the natural fit."
- **Pushback:** *"Why not Redis?"* → "Redis would work, but Cassandra gave durability + the session-keyed access we wanted, and the team already operated it — no new system."

**Q17. "What are the security considerations — and what's not production-perfect?"**
- **A:** "Auth is enterprise SSO with JWT validation, plus a separate market-access check — a valid login isn't enough, you need explicit permission for that market. On data, everything stays in our Azure tenant; Milvus is self-hosted for the same reason. Honestly, one gap: the JWT handling is hand-rolled rather than Spring Security's standard OAuth2 resource server — it works, but production hardening would move it to standard OIDC."
- **Pushback:** *"Why does the hand-rolled auth matter?"* → "Hand-rolled auth is where subtle bugs hide — token validation edge cases, expiry, rotation. Standard OAuth2 is battle-tested. Naming it is the point: I know what production-grade looks like versus what we shipped."

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| Aug 28, 2026 | Created (concept-level), then rewritten as a decision log. |
| Aug 28, 2026 | **Rebuilt rich + diagram-heavy** after full read of `aiPnSBackend/prep` `01/02/03/04/06/07/08`. Added Part 2 (LLM/RAG/agent concepts taught with ASCII diagrams — autocomplete/tokens/context-window/temperature/attention/training, embeddings-as-GPS, full RAG pipeline, ReAct loop, tool-call flow, 3 memory types, system prompt), kept Part 3 decision log (8 decisions + diagrams for Cassandra/Milvus/WAL), Part 1 system + Part 4 interview. Honors the repo ASCII-visualization standard. Scrubbed: internal codenames/model-name → concepts; example IDs kept as illustrative; standard/OSS names + generic configs retained. |
| Aug 28, 2026 | **Added clickable ToC** + **§3.5 rewritten to teach** the backend mechanics (bridge each construct to a Spring/Java pattern; "what I built deep" vs "framework wiring conceptual") from the actual codebase. **Gap-fill pass:** added §7.5 (why an agent, not a script), an Evaluation subsection in §6 (how you know it's correct), reconciled chunk-size wording (~1,000–1,200/~200–300, property-configurable). |
| Aug 28, 2026 | **Added §13 — Interview Q&A** (17 real technical/concept questions in `Q → say → pushback → answer` format, drawn from the proven `04_INTERVIEW_QA` phrasings): architecture, RAG, agent-vs-LLM, tool selection, LLM-vs-script, Java/Milvus/model trade-offs, multi-tenancy, transactional memory, hallucination, evaluation, context-window, fine-tuning-vs-RAG, 10× scaling, Cassandra-vs-LLM-memory, security (with the honest hand-rolled-auth gap). Behavioral Qs cross-referenced to AI-PNS-STORIES. |
