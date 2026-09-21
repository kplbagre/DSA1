# 07 — Mental Model: RAG & AI Agents
### How retrieval and tool-calling actually work — visuals first

> **Read time:** 40 min
> **Companion video:** Krish Naik — LangChain RAG tutorial (watch after this)
> **Goal:** Understand RAG and the agent loop deeply enough to explain
> and defend them in an interview.

---

## Index

| # | Section | What you get |
|---|---|---|
| | **PART A — RAG** | |
| 1 | [Closed-Book vs Open-Book Exam](#mental-model-1--the-closed-book-vs-open-book-exam) | Why LLMs hallucinate without RAG |
| 2 | [Why Not Put Everything in the Prompt?](#mental-model-2--why-not-just-put-everything-in-the-prompt) | Context window limits + why chunking exists |
| 3 | [Embeddings: Text as GPS Coordinates](#mental-model-3--embeddings-text-as-gps-coordinates) | How semantic search actually works |
| 4 | [The Full RAG Pipeline](#mental-model-4--the-full-rag-pipeline) | End-to-end: ingest → embed → store → retrieve → answer |
| 5 | [RAG Numbers to Remember](#rag-numbers-to-remember) | Token counts, dimensions, chunk sizes |
| | **PART B — AI AGENTS** | |
| 6 | [The Agent Loop (ReAct Pattern)](#mental-model-5--the-agent-loop-react-pattern) | Think → Act → Observe → Repeat |
| 7 | [How Tools Work](#mental-model-6--how-tools-work) | @Tool annotation, descriptions, execution |
| 8 | [3 Types of Memory in an Agent](#mental-model-7--the-3-types-of-memory-in-an-agent) | In-context, external (Cassandra), semantic (Milvus) |
| 9 | [LangChain4j's Role](#mental-model-8--langchain4js-role-the-manager) | What the framework manages vs what you write |
| 10 | [The System Prompt](#mental-model-9--the-system-prompt-the-day-1-briefing) | Instructions, market defaults, reasoning order |
| | **REFERENCE** | |
| 11 | [RAG vs Tools — Side by Side](#side-by-side-rag-vs-tools) | When to retrieve a doc vs when to call a tool |
| 12 | [Interview-Ready Summaries](#interview-ready-summaries) | One-paragraph answers to each core concept |
| 13 | [Deep Dive: Embeddings](#️-deep-dive--embeddings-10-15-min-optional-but-recommended) | Optional — 10 min for depth questions |
| 14 | [Deep Dive: LangChain4j](#️-deep-dive--langchain4j-10-12-min-optional-but-recommended) | Optional — 10 min for framework questions |

---

# PART A — RAG (Retrieval-Augmented Generation)

## Mental Model #1 — The Closed-Book vs Open-Book Exam

```
CLOSED BOOK EXAM (Plain LLM):
┌──────────────────────────────────────────────────────┐
│  Question: "What is our PNS on-call escalation       │
│            process for a Sev1?"                      │
│                                                      │
│  LLM: It was trained on the public internet.         │
│       It has NEVER seen your internal runbook.       │
│       It will either:                                │
│       a) Say "I don't know"                          │
│       b) Make something up (hallucinate)             │
└──────────────────────────────────────────────────────┘

OPEN BOOK EXAM (LLM + RAG):
┌──────────────────────────────────────────────────────┐
│  Question: "What is our PNS on-call escalation       │
│            process for a Sev1?"                      │
│                                                      │
│  BEFORE asking the LLM:                              │
│  → Find the 5 most relevant pages from your runbook  │
│  → Add them to the prompt                            │
│                                                      │
│  NOW ask the LLM — it has the runbook in front of it │
│  → It reads the relevant pages + answers correctly   │
└──────────────────────────────────────────────────────┘

RAG = Giving the LLM the relevant pages before it answers.
```

---

## Mental Model #2 — Why Not Just Put Everything in the Prompt?

```
YOUR TOTAL PNS DOCUMENTATION:
  Runbooks, incident reports, postmortems, wikis, process docs...
  = Thousands of pages = Millions of tokens

YOUR CONTEXT WINDOW: 8,192 tokens

  Millions  >>>>>>>>>>>>>>>>>>>>>>  8,192
  (won't fit)

RAG SOLUTION:
  Don't put everything in.
  Put only the 5 most RELEVANT chunks in.

  Question about escalation process?
    → Find the 5 chunks about escalation
    → Add those 5 chunks (maybe 1,500 tokens total)
    → Context window: fine ✓

  Question about carrier routing?
    → Find the 5 chunks about carriers
    → Different 5 chunks for a different question

  Smart. Targeted. Fits.
```

---

## Mental Model #3 — Embeddings: Text as GPS Coordinates

This is the key to understanding HOW RAG finds the right chunks.

```
PROBLEM: How do you find "relevant" chunks?
         You can't just search for matching keywords.
         "Carrier method" and "delivery option" mean the same thing
         but share no words.

SOLUTION: Convert text to coordinates (vectors/embeddings).
          Similar meaning → similar coordinates.

THINK OF IT LIKE GPS:

  "carrier method"       → coordinates (0.12, -0.45, 0.87, ... )  1536 numbers
  "delivery method"      → coordinates (0.11, -0.44, 0.86, ... )  ← CLOSE
  "CM_ID"                → coordinates (0.13, -0.43, 0.85, ... )  ← CLOSE
  "shipping carrier"     → coordinates (0.10, -0.46, 0.88, ... )  ← CLOSE

  "banana"               → coordinates (0.91,  0.23, -0.61, ... ) ← FAR AWAY
  "football match"       → coordinates (0.77,  0.61, -0.44, ... ) ← FAR AWAY

  DISTANCE = MEANING DIFFERENCE

  ┌─────────────────────────────────────────────────────────┐
  │  "carrier method"  ●                                    │
  │                      ● "delivery method"                │
  │                    ● "CM_ID"                            │
  │                   ● "shipping option"                   │
  │                                                         │
  │                                      ● "banana"         │
  │                                         ● "football"    │
  └─────────────────────────────────────────────────────────┘
                 These cluster together ↑

So when you search for "carrier method",
you find ALL the nearby points — even if they use different words.

This is SEMANTIC SEARCH — search by meaning, not keywords.
```

**In our app:**
- Model used: `text-embedding-3-large`
- Vector size: **1,536 numbers** per chunk
- Similarity metric: **cosine similarity** (angle between vectors)

---

## Mental Model #4 — The Full RAG Pipeline

```
PHASE 1: INDEXING (happens once, offline)
═══════════════════════════════════════════

  PNS Runbook PDF
       │
       ▼
  Apache Tika parses the PDF → plain text
       │
       ▼
  Split into chunks
  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
  │ Chunk 1      │  │ Chunk 2      │  │ Chunk 3      │
  │ 1200 chars   │  │ 1200 chars   │  │ 1200 chars   │
  │ (300 overlap)│  │ (300 overlap)│  │ (300 overlap)│
  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘
         │                 │                  │
         ▼                 ▼                  ▼
  text-embedding-3-large converts each chunk to 1536-dim vector
         │                 │                  │
         ▼                 ▼                  ▼
  ┌─────────────────────────────────────────────────┐
  │              MILVUS VECTOR DATABASE             │
  │  Chunk 1 text + [0.12, -0.45, 0.87, ...]        │
  │  Chunk 2 text + [0.15, -0.41, 0.83, ...]        │
  │  Chunk 3 text + [0.09, -0.48, 0.91, ...]        │
  │  ... thousands of chunks stored ...             │
  └─────────────────────────────────────────────────┘


PHASE 2: RETRIEVAL (happens on every user query)
══════════════════════════════════════════════════

  User asks: "What is the escalation process for a carrier Sev1?"
       │
       ▼
  Same embedding model converts question → vector
  [0.11, -0.44, 0.86, ...]
       │
       ▼
  Milvus: "Find me the 5 stored vectors closest to this query vector"
  (cosine similarity, min score 0.5)
       │
       ▼
  Returns top 5 most semantically similar chunks
       │
       ▼
  Those 5 chunks added to the LLM prompt


PHASE 3: GENERATION
════════════════════

  [System prompt]
  [5 retrieved runbook chunks]  ← RAG content injected here
  [Conversation history]
  [Tools]
  [User question]
       │
       ▼
  gpt-oss-120b answers using the runbook content
```

---

## RAG Numbers to Remember

```
Chunk size:          1,200 characters
Chunk overlap:       300 characters (so context isn't lost at boundaries)
Max chunks returned: 5 per query
Min similarity:      0.5 (50% similar — below this, not returned)
Vector dimensions:   1,536
Similarity metric:   Cosine (angle between vectors)
Index type:          IVF_FLAT (optimised for similarity search at scale)
```

**Why 300 char overlap?**
```
WITHOUT overlap:
  Chunk 1 ends: "...if carrier method does not support express"
  Chunk 2 starts: "in Zone 4, check the zone configuration..."

  The connection between "express" and "Zone 4" is LOST.

WITH overlap:
  Chunk 1 ends: "...if carrier method does not support express"
  Chunk 2 starts: "...does not support express. In Zone 4, check..." ← repeated

  Now both chunks have the bridge sentence. Context preserved.
```

---

---

# PART B — AI Agents

## Mental Model #5 — The Agent Loop (ReAct Pattern)

The core pattern used by every AI agent, including ours.
ReAct = **Re**ason + **Act**

```
  USER: "Why is offer 12345 not getting 2-day delivery?"
           │
           ▼
  ╔══════════════════════════════════════════════════════╗
  ║              AGENT LOOP (repeats)                   ║
  ║                                                     ║
  ║  REASON: "To answer this, I need to know which      ║
  ║           template offer 12345 is on. I'll call     ║
  ║           getOfferTemplateNodeMapping."              ║
  ║           │                                         ║
  ║  ACT:     │ → call getOfferTemplateNodeMapping(12345)║
  ║           │   → DCC API called → returns T123, N456  ║
  ║           │                                         ║
  ║  OBSERVE: "Template T123, node N456. Now I need     ║
  ║           the template details."                    ║
  ║           │                                         ║
  ║  REASON:  "I'll call getTemplateInfo(T123)"         ║
  ║  ACT:     → call getTemplateInfo(T123)              ║
  ║           → returns: STANDARD only, CM789           ║
  ║           │                                         ║
  ║  OBSERVE: "Template only has Standard delivery.     ║
  ║           CM789 is the carrier. Let me check it."   ║
  ║           │                                         ║
  ║  REASON:  "Call getCarrierMethodInfo(CM789)"        ║
  ║  ACT:     → call getCarrierMethodInfo(CM789)        ║
  ║           → returns: maxSLA=3, express=false        ║
  ║           │                                         ║
  ║  OBSERVE: "I have enough. CM789 doesn't support     ║
  ║           express. That's the root cause."          ║
  ║           │                                         ║
  ║  FINAL:   Generate answer and EXIT loop             ║
  ╚══════════════════════════════════════════════════════╝
           │
           ▼
  "Offer 12345 cannot get 2-day delivery because carrier
   method CM789 on template T123 only supports Standard..."
```

**The loop can repeat up to 100 times** (our app's limit).
**Average: 3-7 tool calls per question.**

---

## Mental Model #6 — How Tools Work

```
A TOOL = A Java method the LLM is allowed to call

The LLM doesn't execute code. It REQUESTS a tool call.
LangChain4j detects the request and executes the actual method.

FLOW:
  LLM says: "TOOL_CALL: getCarrierMethodInfo, args: {cmId: 'CM789'}"
      │
      ▼
  LangChain4j intercepts this
      │
      ▼
  Finds the Java method DccApiTools.getCarrierMethodInfo()
      │
      ▼
  Calls it: dccApiService.getCarrierMethod("CM789")
      │
      ▼
  Gets result: {maxSLA: 3, zone: "Zone4", express: false}
      │
      ▼
  Adds result to the prompt: "Tool result: {maxSLA: 3...}"
      │
      ▼
  Calls LLM again with updated prompt


THE TOOL DEFINITION (what the LLM reads to decide):

  @Tool("Use this to understand the SLA, zone coverage, and
         express delivery support for a specific carrier method.
         This is the primary tool for eligibility failures
         involving carriers.")
  public String getCarrierMethodInfo(@P("Carrier method ID") String cmId)

         ↑ This description is what the LLM reads.
           The quality of this description = quality of tool selection.
           Too vague → LLM picks wrong tool → wrong answer.
```

---

## Mental Model #7 — The 3 Types of Memory in an Agent

```
  TYPE 1: IN-CONTEXT MEMORY (the conversation history)
  ─────────────────────────────────────────────────────
  What: Last 30 messages in the prompt
  Where: Inside the context window
  Lifetime: This conversation only
  In our app: Loaded from Cassandra, put in prompt

  TYPE 2: EXTERNAL MEMORY (Cassandra)
  ─────────────────────────────────────────────────────
  What: All messages ever, for all sessions
  Where: Cassandra database
  Lifetime: Permanent
  In our app: Saves every message, loads last 30 per session

  TYPE 3: KNOWLEDGE STORE (Milvus RAG)
  ─────────────────────────────────────────────────────
  What: PNS documentation, runbooks, incident reports
  Where: Milvus vector database
  Lifetime: Permanent until re-indexed
  In our app: Queried semantically for every user question


WHICH MEMORY DOES WHAT:

  "What did I ask you 3 messages ago?"  → In-context (Type 1)
  "What did I ask last week?"           → Cassandra (Type 2)
  "What is our escalation process?"     → Milvus RAG (Type 3)
  "What is the current DCC config?"     → TOOL CALL (not memory — live data)
```

---

## Mental Model #8 — LangChain4j's Role (The Manager)

```
WITHOUT LangChain4j — you'd build this yourself:

  You → call Azure OpenAI API
  You → detect if response has a tool call request
  You → find the right Java method
  You → call it
  You → add result to prompt
  You → call Azure OpenAI again
  You → repeat until no more tool calls
  You → manage conversation history (load, trim, save)
  You → inject RAG documents
  You → handle retries, timeouts, errors

  = Months of work. Bug-prone. Unmaintained.


WITH LangChain4j:

  YOU define:
    interface PnsAiAgent {
        @SystemMessage("You are a PNS assistant...")
        String chat(String message);
    }

  YOU annotate tools:
    @Tool("...") String getTemplateInfo(String id) { ... }

  LangChain4j does everything else automatically.

  YOU call:
    String answer = agent.chat("Why is offer not eligible?");

  LangChain4j handles:
    → Building the full prompt
    → Calling Azure OpenAI
    → Detecting tool call requests
    → Executing tools
    → Feeding results back
    → Looping until final answer
    → Managing memory
    → Injecting RAG docs
    → Retrying on failure
```

---

## Mental Model #9 — The System Prompt (The Day-1 Briefing)

```
WHAT IT IS:
  A permanent block of text that is sent to the LLM at the START
  of every single request — before the user's message, before tool
  definitions, before conversation history.

  The LLM reads it EVERY TIME. It shapes every decision the LLM makes.

THE ANALOGY:
  You hire a smart intern.
  On Day 1 you sit them down and say:

    "You work for the PNS on-call team at Walmart.
     When someone asks about offer eligibility, always check
     the template FIRST, then the carrier method.
     For Mexico questions, default to tenantCode hvgqan.
     Never make up data — if you don't know, say so.
     Always cite which API you called."

  That briefing = System Prompt.
  The intern (LLM) reads it before every conversation.

WHAT IS INSIDE OUR SYSTEM PROMPT (Kapil wrote this):

  ┌──────────────────────────────────────────────────────────────┐
  │  IDENTITY          "You are a PNS AI assistant for on-call   │
  │                     engineers at Walmart..."                  │
  │                                                              │
  │  TOOL ORDER        "For eligibility questions:               │
  │                     1. Call getOfferTemplateNodeMapping first │
  │                     2. Then getTemplateInfo                   │
  │                     3. Then check carrier method"             │
  │                                                              │
  │  MARKET RULES      "If no market specified and context       │
  │                     suggests Mexico, use tenantCode hvgqan"  │
  │                                                              │
  │  UNCERTAINTY RULE  "If API returns no data, say so. Do not  │
  │                     guess or hallucinate a value."           │
  │                                                              │
  │  RESPONSE FORMAT   "Always explain what you checked and why" │
  └──────────────────────────────────────────────────────────────┘

WHY IT MATTERS:
  Without a system prompt → LLM behaves like a generic chatbot.
  With a PNS system prompt → LLM behaves like a trained PNS engineer.

  The quality of the system prompt directly determines answer quality.
  Too vague → LLM calls wrong tool or gives generic answer.
  Too restrictive → LLM can't adapt to unusual questions.

  Getting this right required 5 years of PNS domain knowledge.
  That is Kapil's contribution.

WHERE IT LIVES IN CODE:
  PnsAiAgent.java — the @SystemMessage annotation on the interface.
  The text content is loaded from a prompt template file.
```

**Interview answer on System Prompt:**
> "The system prompt is the permanent instruction set that fires at the start of every LLM call. I wrote it to encode PNS-specific business rules — which tool to call first for eligibility questions, how to handle multi-market defaults, when to stop and say 'no data found' vs keep searching. Without that encoding, the LLM would call tools in arbitrary order and give inconsistent answers. The prompt engineering was iterative — I'd test a real PNS scenario, see where the LLM made the wrong decision, and tighten the instruction."

---

## Side by Side: RAG vs Tools

```
  ┌──────────────────────────┬──────────────────────────────┐
  │          RAG             │           TOOLS              │
  ├──────────────────────────┼──────────────────────────────┤
  │ Static documents         │ Live system calls            │
  │ (runbooks, wikis)        │ (DCC, Wakanda, Cassandra)    │
  ├──────────────────────────┼──────────────────────────────┤
  │ Pre-indexed offline      │ Called at query time         │
  ├──────────────────────────┼──────────────────────────────┤
  │ Retrieved by similarity  │ Selected by description      │
  │ (Milvus cosine search)   │ (LLM reads @Tool annotation) │
  ├──────────────────────────┼──────────────────────────────┤
  │ Good for: HOW questions  │ Good for: WHAT IS questions  │
  │ "How do we escalate?"    │ "What is CM789's max SLA?"   │
  ├──────────────────────────┼──────────────────────────────┤
  │ Returns: document text   │ Returns: real-time API data  │
  └──────────────────────────┴──────────────────────────────┘

  For most PNS questions → TOOLS are primary
  For process/documentation questions → RAG supplements
  For complex questions → both used together
```

---

## Interview-Ready Summaries

**On RAG:**
> "RAG solves the problem that the LLM knows nothing about our internal
> documentation. We pre-process PNS runbooks into 1,200-character chunks,
> convert them to 1,536-dimensional vectors using Azure OpenAI's embedding model,
> and store them in Milvus. On every query, we embed the question, find the 5
> most semantically similar document chunks using cosine similarity, and inject
> only those into the prompt. The LLM gets targeted, relevant context — not
> thousands of irrelevant pages."

**On AI Agents:**
> "An agent adds tools and memory to an LLM. The LLM reasons about which tool
> to call, LangChain4j executes it, the result comes back, and the loop repeats
> until the LLM has enough data to answer. For our app, a single question about
> offer eligibility typically triggers 3-7 tool calls — template lookup, carrier
> method check, zone validation — before the LLM can give a reasoned answer.
> That multi-step investigation is the core value of the agent over a plain LLM."

---

## ⬇️ DEEP DIVE — Embeddings (10-15 min, optional but recommended)

> Read this if you want to answer "how does Milvus actually find similar chunks?"
> with real depth. This is where most candidates stay surface-level.

---

### Deep Dive 1 — The Two-Model Setup (Most People Don't Know This)

```
COMMON MISTAKE: thinking gpt-oss-120b generates the embeddings.
It doesn't. There are TWO completely separate models.

  ┌───────────────────────────────────────────────────────────┐
  │  MODEL 1: text-embedding-3-large                          │
  │  Job: Convert text → vector (1536 numbers)                │
  │  Used for: indexing documents + embedding user queries    │
  │  Does NOT generate any text. Only outputs numbers.        │
  ├───────────────────────────────────────────────────────────┤
  │  MODEL 2: gpt-oss-120b                                    │
  │  Job: Reason, call tools, generate the final answer       │
  │  Used for: the agent loop + response generation           │
  │  Does NOT deal with embeddings at all.                    │
  └───────────────────────────────────────────────────────────┘

HOW IT WORKS IN SEQUENCE:

  1. User asks: "Why is CM789 blocking 2-day?"
       │
       ▼
  2. text-embedding-3-large converts the QUESTION → vector
     [0.11, -0.44, 0.86, ...]
       │
       ▼
  3. Milvus finds the 5 closest stored chunk vectors
       │
       ▼
  4. Those 5 chunk TEXTS are added to the prompt
       │
       ▼
  5. gpt-oss-120b reads the prompt + calls tools + answers

  The embedding model only runs in steps 2-3.
  The LLM only runs in step 5.
  They never interact with each other directly.
```

---

### Deep Dive 2 — Cosine Similarity Explained Simply

```
MM3 said: "cosine similarity = angle between vectors"
But what does that actually mean?

FIRST — why NOT use straight-line distance?

  Imagine two vectors in 2D (simplified from 1536D):

  Text A: "carrier method"   → (3, 4)
  Text B: "delivery method"  → (6, 8)    ← same direction, just bigger
  Text C: "banana split"     → (1, 7)    ← different direction

  Euclidean distance (straight line):
    A to B = 3.6  (seems far!)
    A to C = 3.2  (seems closer!)

  But B clearly has the same MEANING as A — just expressed
  with more words (longer vector). C means something different.

  Straight-line distance would say A is MORE similar to C.
  WRONG.

COSINE SIMILARITY — measure the ANGLE, not the distance:

       C● (1,7)
        |   /
        |  /  ← big angle (different direction = different meaning)
        | /
        |/____●B (6,8)
        /  ← tiny angle (same direction = same meaning)
       /
      ●A (3,4)

  A and B → tiny angle → cosine similarity ≈ 1.0 (very similar)
  A and C → big angle  → cosine similarity ≈ 0.3 (different)

  Score range:
    1.0  = identical direction = same meaning
    0.5  = our minimum threshold (below this → not returned)
    0.0  = perpendicular = completely unrelated
   -1.0  = opposite direction = opposite meaning

WHY THIS IS SMART:
  "carrier method" (short phrase) and
  "the carrier method is used to determine the delivery option" (long sentence)
  → same direction even though very different lengths
  → cosine similarity ≈ 0.9
  → correctly identified as related
```

---

### Deep Dive 3 — What 1536 Dimensions Actually Means

```
WHY 1536 NUMBERS and not just 2 or 3?

  2 dimensions:
    You can capture: maybe concrete vs abstract, positive vs negative
    But "carrier method" and "ship node" would look similar → wrong

  1536 dimensions:
    Each dimension captures a tiny aspect of meaning.
    Some dimensions might represent:
      → "is this about logistics?" (high for carrier, low for banana)
      → "is this about time?" (high for SLA, schedule)
      → "is this a code/ID?" (high for CM789, T123)
      → "is this about restrictions?" (high for exclusions, blocks)
      ... 1532 more subtle aspects

  More dimensions = finer ability to distinguish between concepts
  that are SLIGHTLY different in meaning.

  "carrier method"   vs   "carrier restriction"
  Both are about carriers. But they mean different things.
  1536 dimensions can capture that difference.
  2 dimensions probably can't.

  WHY 1536 SPECIFICALLY:
    That's the output size of text-embedding-3-large.
    It's a design choice by the model creators.
    You don't choose it — it's fixed by the model.
```

---

### Deep Dive 4 — The Threshold (min score 0.5)

```
NOT every query returns 5 chunks. Here's why:

  Min similarity score: 0.5

  If the user asks: "What is a carrier method?"
    → Embedding model converts question → vector
    → Milvus searches → finds 5 chunks with score 0.7, 0.68, 0.65, 0.6, 0.55
    → All above 0.5 → all 5 returned ✅

  If the user asks something very general: "Hello, how are you?"
    → No PNS runbook chunk is about greetings
    → Milvus searches → best match has score 0.2
    → Below 0.5 → NO chunks returned
    → Prompt goes to LLM without any RAG context

  WHY THIS MATTERS:
    You don't want irrelevant chunks injected into every prompt.
    A chunk about "carrier SLA" added when someone says "hello"
    would just confuse the LLM.
    The threshold is a quality gate — only inject when relevant.

  IN INTERVIEW:
  "The 0.5 threshold means we only inject RAG context when the
   query is genuinely similar to stored documentation. Below that,
   the LLM answers from tools and training only — no noise injected."
```

---

---

## ⬇️ DEEP DIVE — LangChain4j (10-12 min, optional but recommended)

> Read this if you want to answer "how does LangChain4j actually work?"
> beyond "it manages the agent loop." MM8 gives the what — this gives the how.

---

### Deep Dive 5 — How Tool Calling Actually Works at the Protocol Level

```
COMMON MISCONCEPTION: "The LLM calls the Java method directly."

It doesn't. The LLM cannot execute code. Here is exactly what happens:

STEP 1: LangChain4j sends tool DESCRIPTIONS to the LLM
─────────────────────────────────────────────────────────
  Before calling the LLM, LangChain4j builds a prompt that includes
  the tool definitions in a format the LLM understands:

  {
    "name": "getCarrierMethodInfo",
    "description": "Use this to understand the SLA, zone coverage,
                    and express delivery support for a specific
                    carrier method. Primary tool for eligibility
                    failures involving carriers.",
    "parameters": {
      "cmId": { "type": "string", "description": "Carrier method ID" }
    }
  }

  The LLM reads this definition. It does NOT see the Java code.
  It only sees the name + description + parameter names.


STEP 2: LLM outputs a JSON tool call REQUEST (not the answer)
─────────────────────────────────────────────────────────────
  Instead of generating a text answer, the LLM outputs:

  {
    "tool_call": {
      "name": "getCarrierMethodInfo",
      "arguments": { "cmId": "CM789" }
    }
  }

  This is just text/JSON output from the LLM.
  The LLM stopped generating the answer and asked for data instead.


STEP 3: LangChain4j intercepts the JSON and executes the method
─────────────────────────────────────────────────────────────
  LangChain4j sees the tool_call JSON.
  It maps "getCarrierMethodInfo" → DccApiTools.getCarrierMethodInfo()
  It passes "CM789" as the argument.
  It calls the actual Java method.
  It gets back: { maxSLA: 3, zone: "Zone4", express: false }


STEP 4: Result is added to the prompt and LLM is called again
─────────────────────────────────────────────────────────────
  LangChain4j appends to the conversation:
    "Tool result for getCarrierMethodInfo(CM789):
     { maxSLA: 3, zone: Zone4, express: false }"

  Now calls the LLM again with this updated context.
  The LLM reads the tool result and either:
    a) Calls another tool (loop continues)
    b) Has enough data → generates the final text answer


SUMMARY VISUAL:

  LLM → "I need data" → outputs JSON tool call
    │
    ▼
  LangChain4j intercepts JSON
    │
    ▼
  Java method executes (real API call)
    │
    ▼
  Result appended to prompt
    │
    ▼
  LLM called again → repeat or give final answer
```

**Why this matters for your interview:**
> If asked "how does the LLM call your tools?" — most candidates say "LangChain4j
> handles it." You can say: "The LLM outputs a structured JSON tool call request —
> it's just text output. LangChain4j intercepts that JSON, maps it to the right
> Java method, executes it, and feeds the result back into the next LLM call.
> The LLM itself never executes code — it only requests and receives data."

---

### Deep Dive 6 — @Tool Description Quality (Kapil's Hardest Problem)

```
THIS IS DIRECTLY YOUR CONTRIBUTION STORY.

The @Tool annotation has two parts:

  @Tool("THIS TEXT IS WHAT THE LLM READS TO DECIDE WHICH TOOL TO CALL")
  public String getCarrierMethodInfo(@P("Carrier method ID") String cmId)

The LLM picks the tool by reading the description.
It does NOT look at the method name or the Java code.
Only the description text.


WHAT HAPPENS WITH A VAGUE DESCRIPTION:

  @Tool("Get carrier info")
  public String getCarrierMethodInfo(String cmId)

  User asks: "Why is offer 12345 not getting 2-day delivery?"
  LLM sees 3 tools:
    → "Get carrier info"
    → "Get template info"
    → "Get offer eligibility"

  "Get offer eligibility" sounds most relevant to the question.
  LLM calls that first. Gets generic eligibility result.
  Misses that the ROOT CAUSE is the carrier method.
  Answer is wrong or incomplete.


WHAT HAPPENS WITH A PRECISE DESCRIPTION:

  @Tool("Use this to understand the SLA, zone coverage, and express
         delivery support for a specific carrier method. This is the
         PRIMARY tool for eligibility failures involving carriers.
         Call this when: carrier method ID is known, eligibility is
         failing, or when the template check shows a carrier issue.")
  public String getCarrierMethodInfo(String cmId)

  Now the LLM reads this and understands:
    → This is specifically for carrier-related eligibility failures
    → It should be called when the template check reveals a carrier
    → It gives SLA, zone, and express delivery data

  User asks about 2-day delivery failure → LLM correctly calls
  template first (as system prompt instructs), sees CM789,
  then calls getCarrierMethodInfo(CM789) with high confidence.
  Gets { express: false }. Root cause found.


THE CASCADING FAILURE PATTERN:

  Wrong tool called first
       │
       ▼
  Wrong data in prompt
       │
       ▼
  LLM reasons from wrong data
       │
       ▼
  Calls more wrong tools
       │
       ▼
  Final answer is confidently wrong

  One vague description → entire chain breaks.


HOW YOU FIXED IT (your interview answer):
  "I tested with real PNS scenarios — actual offer IDs, actual
   carrier methods. Where the LLM picked the wrong tool, I looked
   at ALL the tool descriptions visible at that moment and figured
   out why one sounded more relevant than the correct one.
   Then I rewrote the description to make the correct tool
   unambiguously right for that scenario. This took multiple
   iterations — it's not something you get right in one pass."
```

---

**Next: `08_MM_SYSTEM_DESIGN.md`**
