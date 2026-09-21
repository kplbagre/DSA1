# 05 — Resources, Cheat Sheet & Study Plan
### Videos to watch, the one-page cheat sheet, and your day-before checklist

> **Reading time:** 30 min to read + bookmark | Videos: 3-4 hours total
> Watch these BEFORE or ALONGSIDE reading Files 01 and 02.
> Visual explanations will make the text notes stick much faster.

---

## PART 1 — Must-Watch Videos

### 🎯 WATCH FIRST — The LLM Foundation (2 videos, ~60 min total)

---

**Video 1 — "But what is a GPT? Visual intro to transformers"**
Channel: 3Blue1Brown
Duration: 27 minutes
Link: https://www.youtube.com/watch?v=wjZofJX0v4M

> Why watch: This is the BEST visual explanation of how GPT models work.
> 3Blue1Brown uses animations — no code, no math jargon. After watching
> you will understand exactly why the LLM "predicts the next token" and
> what that means in practice.
>
> What to take away: tokens, context window, why the model generates
> text one piece at a time, and why temperature affects output.
>
> When an interviewer asks "how does gpt-oss-120b actually work?" —
> this video is your foundation.

---

**Video 2 — "Attention in transformers, visually explained"**
Channel: 3Blue1Brown
Duration: 26 minutes
Link: https://www.youtube.com/watch?v=eMlx5fFNoYc

> Why watch: Explains the attention mechanism — the core reason LLMs
> can connect "carrier method" to "CM789" 500 tokens apart in the prompt.
> You don't need the math. You need the mental model: the LLM can weigh
> which earlier words matter most for predicting the next word.
>
> What to take away: Why context window matters, why the LLM can "see"
> connections across a long prompt.
>
> Optional: Watch after Video 1 if you want deeper understanding.

---

### 🎯 WATCH SECOND — RAG and AI Agents (2 videos, ~60 min total)

---

**Video 3 — "LangChain RAG Tutorial — Complete Walkthrough"**
Channel: Krish Naik (Indian creator, explains very clearly in simple English)
Search: "Krish Naik LangChain RAG tutorial"
Duration: ~45-60 minutes

> Why watch: Krish Naik is one of the best Indian AI educators on YouTube.
> He explains LangChain, RAG, and embeddings with actual code — step by step.
> His style is very accessible, not academic.
>
> What to take away:
> - What LangChain does in practice (not just theory)
> - How RAG works step by step (document → chunk → embed → store → retrieve)
> - How embeddings are generated and why similar text gives similar vectors
>
> After watching: the Milvus + RAG section of File 02 will make complete sense.

---

**Video 4 — "AI Agents Explained — How LLM Agents Work"**
Channel: Krish Naik OR search "LLM agents tool calling explained"
Duration: ~30-45 minutes

> Why watch: Explains the agent loop — how the LLM decides to call a tool,
> how the result comes back, and how the loop continues until a final answer.
> This is the core mechanism of everything in this application.
>
> What to take away:
> - The ReAct pattern (Reason → Act → Observe → repeat)
> - How tool definitions are sent to the LLM
> - Why the LLM stops looping and gives a final answer

---

### 🎯 WATCH THIRD — System Design Context (1 video, ~30 min)

---

**Video 5 — System Design by Arpit Bhayani**
Channel: Arpit Bhayani (Indian creator, ex-Amazon, excellent depth)
Search: "Arpit Bhayani Cassandra system design" or "why Cassandra"
Duration: ~30 minutes

> Why watch: Arpit explains distributed databases with excellent clarity.
> Understanding WHY Cassandra for conversation storage will help you answer
> "why not PostgreSQL?" with real depth.
>
> What to take away:
> - What makes Cassandra different from relational databases
> - Write-heavy use cases and horizontal scaling
> - When Cassandra is the right choice vs when it isn't
>
> Arpit's channel: https://www.youtube.com/@arpit_bhayani
> He also has excellent videos on Kafka, which connects to your existing work.

---

### 📚 OPTIONAL — Additional Resources

**Codebasics — AI/ML concepts in simple English (Indian creator)**
Channel: https://www.youtube.com/@codebasics
Good for: Embeddings explained, vector databases, machine learning basics
Watch if: You want the mathematical intuition behind embeddings

**Gaurav Sen — Distributed systems**
Channel: https://www.youtube.com/@GauravSensei
Good for: Why distributed databases, CAP theorem, system design interviews
Watch if: You get questions about distributed architecture

---

## PART 2 — The One-Page Mental Model Cheat Sheet

Print this or keep it open during your practice sessions.

```
╔══════════════════════════════════════════════════════════════════════╗
║                    MENTAL MODEL CHEAT SHEET                         ║
╠══════════════════════════════════════════════════════════════════════╣
║                                                                      ║
║  THE APP IN 1 SENTENCE                                               ║
║  ─────────────────────────────────────────────────────────────────  ║
║  AI assistant for PNS on-call: ask in plain English,               ║
║  it calls DCC/Wakanda/Cassandra/UP and gives a reasoned answer.     ║
║                                                                      ║
║  THE INTERN ANALOGY                                                  ║
║  ─────────────────────────────────────────────────────────────────  ║
║  AI Agent = Smart intern with a phone                               ║
║  LLM (gpt-oss-120b) = The smart brain                              ║
║  Tools = Phone contacts (DCC, Wakanda, UP, GScope...)               ║
║  System Prompt = Day-1 briefing you gave the intern                 ║
║  RAG (Milvus) = The intern's notepad of internal docs               ║
║  Cassandra = The logbook of all conversations                       ║
║                                                                      ║
║  THE 6 LAYERS (top to bottom)                                       ║
║  ─────────────────────────────────────────────────────────────────  ║
║  1. React Frontend (ChatPage.tsx, AgentThinkingPanel)               ║
║  2. Spring Boot API (TransactionalAiController)                     ║
║  3. Orchestration (AdvancedConversationalFlowService)               ║
║  4. AI Agent (PnsAiAgent + LangChain4j)                            ║
║  5. Tools Layer (DccApiTools, WakandaApiTools...) ← YOUR CODE       ║
║  6. Data (Cassandra + Milvus + External PNS APIs)                  ║
║                                                                      ║
║  THE 3-STEP TRANSACTIONAL FLOW                                      ║
║  ─────────────────────────────────────────────────────────────────  ║
║  1. START → Save user message to Cassandra FIRST                    ║
║  2. PROCESS → AI agent runs in memory (tools called, not saved)     ║
║  3. COMPLETE → Save final AI answer to Cassandra                    ║
║                                                                      ║
║  KEY NUMBERS                                                        ║
║  ─────────────────────────────────────────────────────────────────  ║
║  8,192 tokens → LLM context window                                  ║
║  30 messages → Max conversation history                             ║
║  40+ tools → PNS API wrappers                                       ║
║  1,536 dims → Embedding vector size                                 ║
║  5 docs → Max RAG results per query                                 ║
║  0.2 → LLM temperature (near-deterministic)                        ║
║  45s → Tool timeout                                                 ║
║  120s → LLM call timeout                                            ║
║  8-12s → Typical end-to-end response time                          ║
║                                                                      ║
║  FRONTEND CONNECTION TYPES                                          ║
║  ─────────────────────────────────────────────────────────────────  ║
║  REST → Login, session loading, admin actions                       ║
║  SSE → Chat streaming (server pushes events to browser)             ║
║  WebSocket → MCP Tools Explorer (bidirectional)                     ║
║                                                                      ║
║  YOUR CONTRIBUTION CLAIM                                            ║
║  ─────────────────────────────────────────────────────────────────  ║
║  ✓ 40+ PNS Tool implementations (DCC, Wakanda, PNO, Cassandra)     ║
║  ✓ System prompt (PNS business rules + tool call ordering)          ║
║  ✓ PnsConversationHandler (domain routing + multi-market)           ║
║  ✗ LangChain4j infrastructure setup (team)                         ║
║  ✗ Cassandra schema and memory management (team)                    ║
║  ✗ Milvus and RAG pipeline setup (team)                            ║
║  ✗ Frontend React application (team)                                ║
║                                                                      ║
║  5 PHRASES THAT SOUND SENIOR                                        ║
║  ─────────────────────────────────────────────────────────────────  ║
║  "The trade-off we accepted was..."                                 ║
║  "The failure mode I thought about was..."                          ║
║  "What I'd do differently is..."                                    ║
║  "The reason that split made sense was..."                          ║
║  "We validated this by..."                                          ║
╚══════════════════════════════════════════════════════════════════════╝
```

---

## PART 3 — 3-Day Study Plan

### Day 1 — Build the Mental Model (2 hours)

```
MORNING / FIRST SESSION (1 hr):
  ✓ Watch Video 1 (3Blue1Brown — What is GPT?) — 27 min
    [Already watched ✅]
  ✓ Read File 06: MM Transformers & LLM — 35 min
    → Reinforces what you just watched with PNS examples
  ✓ Read File 01: Big Picture — 55 min
  ✓ After reading: close file, draw the 6-zone system map from memory

EVENING / SECOND SESSION (1 hr):
  ✓ Read File 07: MM RAG & Agents — 40 min
    → Read this BEFORE watching the RAG video (builds the model first)
  ✓ Watch Video 3 (Krish Naik — LangChain RAG) when you get time
    → Will make much more sense after File 07
  ✓ Look at the Cheat Sheet and test yourself on key numbers
```

---

### Day 2 — Get Into the Code (2.5 hours)

```
MORNING (1.5 hr):
  ✓ Read File 08: MM System Design — 35 min
    → Trade-offs for every tech decision. Must know before interviews.
  ✓ Watch Video 4 (AI Agents explained) — 35 min (optional, File 07 covers it)
  ✓ Read File 02: How It Is Built (core section only) — 75 min
  ✓ After reading: narrate the full request flow out loud
    Start: "When the user sends a message..."
    End: "...and the answer is streamed back via SSE"

EVENING (1 hr):
  ✓ Read File 03: My Contribution (core section only) — 55 min
  ✓ Say the 30-second story out loud, 3 times
  ✓ Say the 2-minute story out loud, 2 times
```

---

### Day 3 — Interview Readiness (2 hours)

```
MORNING (1.5 hr):
  ✓ Read File 04: Interview Q&A (core section, Q1-Q28) — 75 min
  ✓ For each question: read the approach, cover the answer,
    try to say it yourself, then compare

EVENING (30 min):
  ✓ Review the Cheat Sheet
  ✓ Say these 3 things out loud without any notes:
    - The 30-second project pitch
    - The full request flow (5 steps)
    - Your contribution answer (honest version)
```

---

### If You Have More Time (Days 4-5)

```
Day 4 — Deep Dives:
  ✓ Watch Video 2 (3Blue1Brown — Attention mechanism)
  ✓ Watch Video 5 (Arpit Bhayani — Cassandra)
  ✓ Read all Deep Dive sections in Files 01, 02, 03, 04
  ✓ Read the actual tool class files in the codebase:
    → /src/main/java/.../tools/DccApiTools.java
    → /src/main/java/.../tools/WakandaApiTools.java
    → /src/main/java/.../service/domain/PnsAiAgent.java

Day 5 — Full Mock:
  ✓ Do a mock interview with yourself (or a friend)
  ✓ Start with "Tell me about your most interesting project"
  ✓ Let them probe. Answer everything from memory.
  ✓ Note every gap — go back to the relevant file for that gap.
```

---

## PART 4 — Day-Before Checklist

The night before an interview, do ONLY this. Not the full notes. Trust the prep.

```
□ Re-read the Cheat Sheet once (10 min)

□ Say the 30-second pitch out loud (2 times)
  "It is an AI assistant for PNS on-call operations. Engineers ask it
  questions in plain English — like why an offer is not eligible —
  and it calls the actual PNS APIs, chains results, and gives a
  reasoned answer..."

□ Say the full request flow out loud (1 time)
  "Controller validates JWT... orchestration starts transaction...
  LangChain4j builds prompt... LLM calls tools... completes transaction...
  SSE streams answer back..."

□ Say your contribution answer out loud (1 time)
  "I owned the PNS domain layer — 40-plus tools, system prompt,
  PNS conversation handler. The team built the infrastructure..."

□ Review your "What would you change?" answer (2 min)
  "Tool testing harness + distributed tracing"

□ Sleep. The prep is done.
```

---

## PART 5 — 5 Sentences You Must Know Cold

These are the 5 sentences that carry the most interview weight.
Say each one until it sounds natural, not recited.

---

**Sentence 1 — The project pitch (30 seconds)**
> "It is an AI assistant for Walmart's Promise and Shipping domain. Engineers
> ask questions in plain English — like why an offer is not getting 2-day
> delivery — and the agent automatically calls the right PNS APIs, chains
> the results together, and gives a reasoned answer in about 10 seconds.
> It replaced 15-20 minutes of manual multi-system debugging."

---

**Sentence 2 — Your contribution (20 seconds)**
> "I owned the PNS domain layer — specifically the 40-plus tool implementations
> that expose PNS APIs to the AI, the system prompt that encodes PNS business
> logic, and the conversation handler. The team built the AI infrastructure
> around that."

---

**Sentence 3 — Why your part was hard (20 seconds)**
> "The hardest part was writing tool descriptions precise enough that the LLM
> consistently picks the right tool. Vague descriptions cause wrong tool selection,
> which cascades into wrong answers. It took multiple iterations of testing
> real scenarios and refining descriptions."

---

**Sentence 4 — The honest "how much did you build?" answer (25 seconds)**
> "Team effort. The AI infrastructure — LangChain4j, Cassandra memory,
> Milvus RAG pipeline — was collaborative. My ownership was the domain layer.
> That split made sense because the infrastructure is domain-agnostic —
> any backend engineer can wire LangChain4j. Knowing which PNS API to call
> for which question took 5 years on this platform."

---

**Sentence 5 — What you'd change (20 seconds)**
> "Two things: a tool testing harness for isolated unit testing of each tool,
> and distributed tracing end-to-end — right now debugging a latency spike
> requires correlating logs manually across the LLM call and each API call."

---

## PART 6 — What Good Looks Like in the Interview

```
SIGN YOU'RE DOING WELL          SIGN TO COURSE-CORRECT
──────────────────────────────────────────────────────────────────
Interviewer nods while you talk  Interviewer looks confused
They say "interesting, tell me   They repeat the same question
more about X"                    in different words

They ask follow-ups that dig     They ask something unrelated —
deeper into the same topic       they want to change the topic

They challenge a decision        They go quiet after your answer
("why not Y?") — they're
engaged, not sceptical

You say "the trade-off was..."   You say "I don't know" and stop
and they lean forward            there — no redirect to what
                                 you DO know
```

---

**You are now fully prepared. Go build that confidence.**
