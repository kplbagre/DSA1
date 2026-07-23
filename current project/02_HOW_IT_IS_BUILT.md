# 02 — How It Is Built
### The actual code layers, key classes, and how everything connects

> **Reading time:** Core = 75 min | Deep Dive = 50 min
> **Goal:** Understand the code well enough to speak about it as someone
> who worked on it — naming specific files, explaining specific decisions.

---

## 1. The Technology Stack — At a Glance

```
╔════════════════════════════════════════════════════════════════╗
║  LAYER              │ TECHNOLOGY           │ WHY               ║
╠════════════════════════════════════════════════════════════════╣
║  Frontend           │ React 19 + TypeScript│ Modern, fast UI   ║
║  UI Components      │ Material UI (MUI)    │ Enterprise design  ║
║  Animations         │ Framer Motion        │ Smooth UX          ║
║  HTTP Client        │ Axios                │ API calls          ║
║  Streaming          │ SSE (EventSource)    │ Real-time updates  ║
╠════════════════════════════════════════════════════════════════╣
║  Backend Framework  │ Spring Boot 3.2.3    │ Walmart Java stack ║
║  Language           │ Java 17              │ LTS, modern syntax ║
║  Build Tool         │ Maven                │ Dependency mgmt    ║
╠════════════════════════════════════════════════════════════════╣
║  AI Orchestration   │ LangChain4j 0.27.1   │ Agent framework    ║
║  LLM               │ Azure OpenAI          │ Data stays Walmart ║
║  Model             │ gpt-oss-120b          │ Strong reasoning   ║
║  Embedding Model    │ text-embedding-3-large│ 1536-dim vectors   ║
╠════════════════════════════════════════════════════════════════╣
║  Conversation DB    │ Apache Cassandra      │ Write-heavy, fast  ║
║  Vector DB          │ Milvus                │ Semantic search    ║
║  Caching            │ Caffeine              │ In-memory cache    ║
╠════════════════════════════════════════════════════════════════╣
║  Real-time Comms    │ WebSocket             │ MCP protocol       ║
║  Document Parsing   │ Apache Tika + Tesseract│ PDF, Word, images ║
║  Auth              │ PingFederate SSO       │ Walmart SSO        ║
╚════════════════════════════════════════════════════════════════╝
```

**Key interview insight:** Why Java and not Python (where most AI libraries are)?
> "The PNS domain already runs on a Java/Spring Boot stack. LangChain4j is
> the Java port of LangChain — gives us the same AI capabilities without
> introducing a second language and deployment pipeline. Operational simplicity
> beat ecosystem richness."

---

## 2. The 6 Code Layers — The Master Map

```
╔══════════════════════════════════════════════════════════════════╗
║                    CODE LAYERS (Top to Bottom)                  ║
╠══════════════════════════════════════════════════════════════════╣
║                                                                  ║
║  LAYER 1 │ FRONTEND          React + TypeScript                  ║
║          │                   ChatPage.tsx (4926 lines)           ║
║          │                   AgentThinkingPanel.tsx              ║
║          │                   MarketSelector, SessionSidebar      ║
║──────────┼───────────────────────────────────────────────────── ║
║          │                                                        ║
║  LAYER 2 │ API ENTRY POINT   TransactionalAiController.java      ║
║          │                   AuthController.java                 ║
║          │                   JWT Validation + Market Check       ║
║──────────┼───────────────────────────────────────────────────── ║
║          │                                                        ║
║  LAYER 3 │ ORCHESTRATION     AdvancedConversationalFlowService   ║
║          │                   DomainDetectionService              ║
║          │                   TenantContextManager                ║
║          │                   TransactionalMemoryManager          ║
║──────────┼───────────────────────────────────────────────────── ║
║          │                                                        ║
║  LAYER 4 │ AI AGENT          PnsAiAgent.java (interface)         ║
║          │                   PnsConversationHandler.java         ║
║          │                   LangChainConfig.java                ║
║          │                   System Prompt (inside PnsAiAgent)   ║
║──────────┼───────────────────────────────────────────────────── ║
║          │                                                        ║
║  LAYER 5 │ TOOLS          ← YOUR CONTRIBUTION                    ║
║          │                   DccApiTools.java                    ║
║          │                   WakandaApiTools.java                ║
║          │                   PnoIngestorTools.java               ║
║          │                   McseCassandraDbLookupTools.java     ║
║          │                   UnifiedPromiseApiTools.java         ║
║          │                   GscopeApiTools.java                 ║
║          │                   + 10 more tool files                ║
║──────────┼───────────────────────────────────────────────────── ║
║          │                                                        ║
║  LAYER 6 │ DATA              Cassandra (conversation memory)     ║
║          │                   Milvus (document vectors)           ║
║          │                   External PNS APIs (DCC, Wakanda..)  ║
╚══════════════════════════════════════════════════════════════════╝
```

---

## 3. Layer 1 — The Frontend (React)

### What ChatPage.tsx does

`ChatPage.tsx` is the largest file in the frontend — 4,926 lines. It is the
main chat interface and handles everything the user sees.

**Key things it manages:**
- The text input where you type your question
- Sending the question to the backend via SSE stream
- Showing the Agent Thinking Panel with live updates
- Rendering the AI's markdown response (with code blocks, tables)
- Managing session history (create, load, delete sessions)
- Market and domain selection state

### How SSE Streaming Works in the Frontend

```
USER TYPES: "Why is offer 12345 not 2-day eligible?"
     │
     ▼
ChatPage.tsx calls ChatContext.sendMessage()
     │
     ▼
api.service.ts opens SSE stream:
POST /api/pns-agent/stream
     │
     ▼ (events come back one by one)

Event 1: { type: "session_start", executionId: "exec_abc" }
   → AgentThinkingPanel: "Starting..."

Event 2: { type: "classifier", intent: "diagnose_offer", complexity: "moderate" }
   → AgentThinkingPanel: "Classified as offer diagnosis"

Event 3: { type: "planner", planned_tools: ["getOfferTemplateMapping", ...] }
   → AgentThinkingPanel: "Planning 3 tool calls"

Event 4: { type: "tool_executor", tool: "getOfferTemplateMapping", status: "running" }
   → AgentThinkingPanel: "⟳ Calling DCC..."

Event 5: { type: "tool_executor", tool: "getOfferTemplateMapping", status: "done" }
   → AgentThinkingPanel: "✓ DCC responded"

Event 6: { type: "synthesizer", response: "Offer 12345 cannot..." }
   → Chat window: shows the final answer

Event 7: { type: "done", duration: 8342, totalToolCalls: 3, totalTokens: 4259 }
   → Chat window: shows metadata under the message
```

**Why this matters for interviews:**
> "The thinking panel was important for user trust. Without it, the user
> stares at a blank screen for 8-12 seconds. With it, they see exactly which
> systems the AI is querying — and if something is slow, they know which API
> is the bottleneck. It also helps engineers understand HOW the AI reached
> its answer."

### Key Frontend Components

```
MarketSelector.tsx
  → Dropdown: Mexico 🇲🇽 / Canada 🇨🇦 / US 🇺🇸
  → Stored in localStorage (persists across sessions)
  → Sent with every chat request as X-Market header

DomainSelector.tsx
  → Promise / Unified Promise / Sourcing
  → Changes which tools and context the AI uses

ModelSelector.tsx
  → gpt-oss-120b / Claude Sonnet 4.6 / Gemma 4
  → Can switch model per conversation

AgentThinkingPanel.tsx
  → Receives SSE events
  → Shows animated node progress (planning → calling → answering)
  → Collapses when answer arrives

SessionSidebar.tsx
  → Lists all past sessions for this user
  → Click to load history
  → Search sessions by content
```

---

## 4. Layer 2 — The API Entry Point (Spring Boot)

### The Main Controller: TransactionalAiController.java

This is the first thing that handles your HTTP request on the backend.

```
Incoming Request:
POST /api/ai/v2/chat
Headers: X-User-ID, X-Session-ID, X-Market: MX
Body: { "message": "Why is offer not eligible?", "sessionId": "sess_123" }

TransactionalAiController does 4 things:
┌────────────────────────────────────────────────────────┐
│  1. Spring Security checks JWT token                   │
│     → Valid? Continue. Invalid? → 401 Unauthorized     │
│                                                        │
│  2. Extract user context from headers                  │
│     → userId, sessionId, market, module type           │
│                                                        │
│  3. Validate market access                             │
│     → Does this user have MX market permission?        │
│     → Yes? Continue. No? → 403 Forbidden               │
│                                                        │
│  4. Delegate to service layer                          │
│     → conversationalFlowService.processMessage(...)    │
└────────────────────────────────────────────────────────┘
```

**Design principle:** The controller is thin. It validates and delegates.
Zero business logic lives here. This is intentional — controllers should
only be entry/exit points, not decision makers.

### Authentication: PingFederate SSO

Walmart uses PingFederate as its SSO provider. When a user logs in:
```
User clicks Login → Redirected to PingFederate login page
User authenticates → PingFederate issues an OAuth token
Frontend receives token → Sends with every API request
Backend validates token → Extracts user identity and permissions
```

New users get auto-created in the system on first SSO login.

---

## 5. Layer 3 — The Orchestration Brain

### AdvancedConversationalFlowService.java

This is the most complex service in the application. It coordinates the
entire pipeline from receiving a message to returning an answer.

**The 3-Step Transactional Flow:**

```
WHY "TRANSACTIONAL"?
If the AI call takes 60 seconds and then the server crashes,
you'd lose the user's message. The transactional approach
ensures the user message is saved FIRST, answer saved LAST.
(Same idea as database write-ahead logs.)

STEP 1: START TRANSACTION
┌─────────────────────────────────────────────────────────┐
│  TransactionalMemoryManager.startTransaction()          │
│  → Save user message to Cassandra IMMEDIATELY           │
│  → Get transaction ID back                             │
│  → Even if everything fails after this, message saved  │
└─────────────────────────────────────────────────────────┘
         │
         ▼
STEP 2: PROCESS (in memory, nothing saved during this)
┌─────────────────────────────────────────────────────────┐
│  DomainDetectionService.detectDomain()                  │
│  → Priority: headers → context → message content       │
│  → Default: "pns"                                       │
│                                                         │
│  TenantContextManager.setContext(market, domain)        │
│  → Thread-local: isolated per request                   │
│                                                         │
│  Select handler: PnsConversationHandler                 │
│  → Based on detected domain                             │
│                                                         │
│  PnsConversationHandler.handle() → calls AI agent       │
│  → Tool calls happen here (in memory only)              │
│  → Tool RESULTS are NOT saved to Cassandra             │
└─────────────────────────────────────────────────────────┘
         │
         ▼
STEP 3: COMPLETE TRANSACTION
┌─────────────────────────────────────────────────────────┐
│  TransactionalMemoryManager.completeTransaction()       │
│  → Save final AI response to Cassandra                  │
│  → Save metadata: tokens, duration, tools called       │
│  → Mark transaction complete                            │
└─────────────────────────────────────────────────────────┘
```

**Interview question you must be ready for:**
> "Why don't you save tool results to Cassandra?"
>
> "Tool results are intermediate data — they can be large, they change on
> every call, and storing them would bloat the conversation record. What
> matters for conversation continuity is the final human-readable answer,
> not the raw JSON from DCC. We only persist what is useful for the next
> message — the answer."

---

## 6. Layer 4 — The AI Agent

### What LangChain4j Does (Mental Model)

```
WITHOUT LangChain4j, you'd build this yourself:
  → HTTP client to Azure OpenAI
  → Detect when LLM wants to call a tool
  → Execute the tool
  → Feed result back to LLM
  → Loop until final answer
  → Manage conversation history
  → Inject RAG documents
  → Handle retries and timeouts
  = Months of work

WITH LangChain4j:
  → Define agent as a Java interface (4 lines)
  → Annotate tools as Java methods
  → LangChain4j handles the rest
```

### PnsAiAgent.java — The Agent Interface

```java
// This is the entire "agent definition" — LangChain4j
// creates the implementation automatically at startup

public interface PnsAiAgent {

    @SystemMessage("""
        You are an AI assistant for Walmart's Promise and Shipping domain.

        When debugging offer eligibility:
         1. First check the offer template mapping via getOfferTemplateNodeMapping
         2. Then check carrier restrictions via getOfferCarrierRestrictions
         3. Then verify carrier method details via getCarrierMethodInfo
         ...

        For Mexico queries: default postal code = 06600, store ID = 2648
        For Canada queries: default postal code = M5V 2T6
        ...
        """)
    String chat(@UserMessage String userMessage);
}
```

The `@SystemMessage` annotation is where **PNS domain rules become code**.
This is the briefing given to the smart intern every time a conversation
starts. It encodes:
- Which tool to call first for which type of question
- Market-specific defaults
- How to reason about eligibility failures
- When to check Wakanda vs when to check DCC first

**This annotation is yours.** You knew which tool to call first. You knew
the Mexico postal code default. You knew the evaluation order for carrier
restrictions. That domain knowledge is the content of this system prompt.

### The Agent Loop — How LangChain4j Manages It

```
PnsAiAgent.chat("Why is offer 12345 not eligible?")
     │
     ▼
LangChain4j builds the full prompt:
  [System message: PNS rules and instructions]
  [RAG: top 5 relevant docs from Milvus]
  [History: last 30 messages from Cassandra]
  [Tools: all 40+ tool definitions]
  [User message: "Why is offer 12345 not eligible?"]
     │
     ▼
Sent to Azure OpenAI (gpt-oss-120b)
     │
     ▼
LLM Response: "I'll call getOfferTemplateNodeMapping first."
     │
     ▼ (LangChain4j detects tool call request)
Execute: DccApiTools.getOfferTemplateNodeMapping("12345")
     │   → Real HTTP call to DCC API
     │   → Returns: { templateId: "T123", nodeId: "N456" }
     │
     ▼
LLM sees result, responds: "Now I need template details."
     │
     ▼
Execute: DccApiTools.getTemplateInfo("T123")
     │   → Returns: { carrierMethods: ["CM789"], deliveryTypes: ["STANDARD"] }
     │
     ▼
LLM sees result, responds: "Now carrier method details."
     │
     ▼
Execute: DccApiTools.getCarrierMethodInfo("CM789")
     │   → Returns: { maxSLA: 3, expressSupported: false, zone: "Zone4" }
     │
     ▼
LLM has enough data → Generates final answer
     │
     ▼
Returns: "Offer 12345 cannot receive 2-day because carrier method
          CM789 on template T123 only supports Standard (3-day max)..."
```

**Key configuration settings:**
```
Temperature: 0.2       → Near-deterministic (no creative variation)
Max context: 8,192 tokens → LLM's working memory
Max tool calls: 100     → Safety limit per conversation
Tool timeout: 45s       → Each tool has 45 seconds to respond
Retries: 3             → Retry failed tools 3 times
Concurrent tools: Yes   → Can call independent tools in parallel
```

---

## 7. Layer 5 — The Tools Layer (Your Code)

This is the most PNS-specific layer. Every file here requires knowing
what the underlying API does, when to use it, and what to expect back.

### How a Tool is Defined

```java
// File: DccApiTools.java
// This is the pattern used for EVERY tool in this app

@Component  // Spring manages this as a bean
public class DccApiTools {

    @Autowired
    private DccApiService dccApiService;  // actual HTTP client

    @Tool("Get template information for a given template ID. Use this to " +
          "understand the fulfillment configuration, carrier mappings, or " +
          "eligibility rules for a specific template.")
    public String getTemplateInfo(
        @P("The template ID to look up") String templateId,
        @P("Market code: MX for Mexico, CA for Canada") String market
    ) {
        return dccApiService.getTemplate(templateId, market);
    }
}
```

**Three parts you must understand:**
1. `@Tool("...")` — The description the LLM reads to decide when to use this tool
2. `@P("...")` — Descriptions of each parameter the LLM will fill in
3. The method body — Calls the real service which calls the real API

**The description is domain engineering.** If you write "gets template info"
the LLM doesn't know when to use it. If you write "use this to understand
carrier mappings and eligibility rules for an offer's template" — the LLM
picks this tool when someone asks about eligibility. That distinction requires
PNS knowledge.

### The 8 Tool Groups (40+ tools total)

```
GROUP 1: DccApiTools.java — DCC API Wrappers
─────────────────────────────────────────────
getTemplateInfo()              → Template config and carrier mappings
getOfferTemplateNodeMapping()  → Which template is an offer on?
getCarrierMethodInfo()         → Carrier method details and SLA
getCarrierMethodZoneCharges()  → Zone-level charges and coverage
getOfferCarrierRestrictions()  → What carrier restrictions block this offer?
getSellerNodeMapping()         → Which node does a seller fulfil from?
getSellerZipcodeExclusions()   → Which zips does this seller exclude?
getSellerDefaultTemplateMapping() → Default template for a seller

GROUP 2: WakandaApiTools.java — Inventory
─────────────────────────────────────────
getWakandaInventory()          → Item stock at a specific node
getWakandaInventoryByPostalCode() → What can ship to this postal code?

GROUP 3: PnoIngestorTools.java — Offer Replay & Validation
──────────────────────────────────────────────────────────
replaySingleOffer()            → Replay one offer through the pipeline
replayMultipleOffers()         → Replay batch of offers
replayOffersByUpc()            → Replay all offers for a UPC
validateOffers()               → Validate offers against current rules
checkOfferEligibility()        → Is this offer eligible? Why/why not?
checkOfferStatus()             → Current status of an offer
checkPnoIngestorHealth()       → Is the ingestor service up?
executeBulkOperations()        → Mass operations on offers

GROUP 4: McseCassandraDbLookupTools.java — Direct DB Reads
──────────────────────────────────────────────────────────
getCassandraOfferInfo()        → Raw offer record from Cassandra
getCassandraDistributorInfo()  → Distributor record from Cassandra
getCarrierData()               → Carrier data
getTntInformation()            → Transit time data
getTransitSchedule()           → Node delivery schedule

GROUP 5: UnifiedPromiseApiTools.java — Promise Data
────────────────────────────────────────────────────
getUnifiedPromiseData()        → Full promise response for an item
getMultipleUnifiedPromiseData() → Promise for multiple items (batch)

GROUP 6: GscopeApiTools.java — Order Tracing
──────────────────────────────────────────────
lookupCorrelationId()          → Find trace for a specific order flow
lookupMultipleCorrelationIds() → Batch correlation lookup

GROUP 7: McseApiTools — MCSE Service Layer
──────────────────────────────────────────
getMcseCarrierInfo()           → Carrier info via MCSE API
getMcseCapacityDataInfo()      → FC capacity
getMcseShipZipLookupInfo()     → Carrier zip coverage
getFcCapacityInfo()            → Fulfillment centre capacity detail

GROUP 8: FulfillmentTools — Schedules & Store Data
────────────────────────────────────────────────────
getDeliverySchedule()          → When can this node deliver?
getPickupShedCalendar()        → Pickup slot availability
getStoreAvailabilitySlots()    → Store SFS slot data
getSfsStoreInfo()              → Ship-from-store configuration
getPreferredCarrierMethod()    → Best carrier for a route
getShipZipLookup()             → Zip coverage check
```

**Interview insight:** When asked "how does the agent know which tool to call?"
> "Each tool has a description annotation that the LLM reads before deciding.
> The descriptions are written in terms of the problem the tool solves — not
> what the API does technically. For example, `getOfferCarrierRestrictions`
> has a description saying 'use this when you need to understand why a specific
> carrier is blocked for an offer' — so when someone asks 'why can't this offer
> use express?', the LLM correctly picks this tool, not a generic carrier lookup."

---

## 8. Layer 6 — The Data Layer

### Cassandra — Conversation Memory

```
What's stored in Cassandra:
┌────────────────────────────────────────────────────────────┐
│  TABLE: conversation_messages                              │
│                                                            │
│  session_id: "MX_pns:hvgqan:sess_abc123"                  │
│  message_id: UUID                                          │
│  role: "USER" or "AI"                                      │
│  content: the message text                                 │
│  timestamp: 2024-01-15T10:32:47Z                           │
│  tokens_used: 4259                                         │
│  tools_called: ["getOfferTemplateMapping", ...]            │
│  duration_ms: 8342                                         │
└────────────────────────────────────────────────────────────┘

WHY CASSANDRA (not PostgreSQL)?
→ Chat generates thousands of writes per second across users
→ Cassandra is designed for horizontal write scalability
→ Add more nodes = handle more writes (no machine upgrade needed)
→ The team already runs MCSE on Cassandra — operational familiarity
→ Trade-off: no complex SQL queries across conversations (acceptable)
```

### Milvus — The Knowledge Store (RAG)

```
WHAT IS RAG? (Retrieval-Augmented Generation)

Problem: The LLM doesn't know your internal runbooks, incident reports,
         or team documentation. Its training data is public internet.

Solution: Before answering, find the 5 most relevant internal docs
          and add them to the prompt. Now the LLM can use them.

HOW MILVUS FITS:
─────────────────────────────────────────────────────────────────
OFFLINE (document ingestion):
  Take PNS runbook → Split into chunks (1200 chars each)
         ↓
  Convert each chunk to a vector (1536 numbers)
  using Azure OpenAI text-embedding-3-large
         ↓
  Store vector + original text in Milvus

ONLINE (every user query):
  User question → Convert to same 1536-dim vector
         ↓
  Milvus finds 5 most similar vectors (cosine similarity)
         ↓
  Returns those 5 document chunks
         ↓
  Added to LLM prompt before answering

RESULT: LLM can answer "what is our escalation process?"
        using YOUR internal runbook, not public internet guessing.
```

**Why not just put everything in the system prompt?**
> "The context window is 8,192 tokens. Our documentation is thousands of pages.
> We can't put it all in the prompt every time. RAG lets us retrieve only the
> 5 most relevant chunks for each specific question. Targeted context, not
> everything at once."

---

## 9. The Full Request Flow — Master Diagram

**Read this 3 times. This is the most asked question.**

```
USER: Types "Why is offer 12345 not 2-day eligible?" in Mexico chat
             │
             │ POST /api/pns-agent/stream (SSE)
             ▼
┌────────────────────────────────────────────────────────────────┐
│ SPRING BOOT — TransactionalAiController.java                   │
│  → Validate JWT token (PingFederate SSO)                       │
│  → Extract: userId, sessionId, market=MX                       │
│  → Check: does user have MX market access?                     │
│  → Delegate to: AdvancedConversationalFlowService              │
└────────────────────────────┬───────────────────────────────────┘
                             │
                             ▼
┌────────────────────────────────────────────────────────────────┐
│ ORCHESTRATION — AdvancedConversationalFlowService.java         │
│                                                                │
│  STEP 1 START TRANSACTION                                      │
│    TransactionalMemoryManager.startTransaction()               │
│    → Save user message to Cassandra NOW                        │
│                                                                │
│  STEP 2 DETECT & ROUTE                                         │
│    DomainDetectionService → domain = "pns"                     │
│    TenantContextManager → market=MX, tenant=hvgqan            │
│    Memory key = "MX_pns:hvgqan:sess_abc123"                   │
│    Route to: PnsConversationHandler                            │
└────────────────────────────┬───────────────────────────────────┘
                             │
                             ▼
┌────────────────────────────────────────────────────────────────┐
│ AI AGENT — PnsAiAgent (via LangChain4j)                        │
│                                                                │
│  LangChain4j builds prompt:                                    │
│    [System prompt: PNS rules + tool guidance]                  │
│    [RAG: 5 relevant docs from Milvus]                          │
│    [History: last 30 messages from Cassandra]                  │
│    [40+ tool definitions]                                      │
│    [User message]                                              │
│                                                                │
│  → Sent to gpt-oss-120b (Azure OpenAI)                         │
│  ← LLM: "Call getOfferTemplateNodeMapping(12345)"              │
│  → Execute tool → DCC API called → result returned             │
│  ← LLM: "Call getTemplateInfo(T123)"                           │
│  → Execute tool → DCC API called → result returned             │
│  ← LLM: "Call getCarrierMethodInfo(CM789)"                     │
│  → Execute tool → DCC API called → result returned             │
│  ← LLM: "I have enough data. Final answer: ..."               │
└────────────────────────────┬───────────────────────────────────┘
                             │
                             ▼
┌────────────────────────────────────────────────────────────────┐
│ TRANSACTION COMPLETE                                           │
│    TransactionalMemoryManager.completeTransaction()            │
│    → Save AI response to Cassandra                             │
│    → Save metadata: 3 tools, 8.3s, 4259 tokens                │
└────────────────────────────┬───────────────────────────────────┘
                             │
                             │ SSE: stream answer to browser
                             ▼
USER sees the answer with tool call timeline shown in the UI
```

---

## 10. Interview Q&A — Technical Questions

### Q1: "Walk me through what happens when a user sends a message."

**Approach:** Use the 5-step summary. Don't go into every class name.
Name 3-4 key classes maximum. End with what the user sees.

**Say:**
> "When a message comes in, the Spring Boot controller validates the JWT token
> and checks market access. The orchestration service saves the user message to
> Cassandra immediately — before doing any AI work — so we never lose a message
> even if the LLM call times out. It then detects the domain, sets the Mexico or
> Canada tenant context, and passes the message to the PNS AI agent. LangChain4j
> builds the full prompt — system instructions, RAG context from Milvus,
> conversation history from Cassandra, and all 40 tool definitions — and sends
> it to gpt-oss-120b. The LLM decides which PNS tools to call, we execute them,
> feed results back, and loop until the LLM has enough data to generate the
> final answer. We save that answer to Cassandra and stream it back to the
> browser via SSE. The user sees each tool call happening live in the thinking panel."

---

### Q2: "Why did you choose LangChain4j?"

**Approach:** Start with what it replaces (building the agent loop yourself).

**Say:**
> "Without LangChain4j we'd have to build the agent loop ourselves — detecting
> when the LLM requests a tool call, executing it, feeding results back,
> looping until done, managing conversation history, injecting RAG documents.
> That's months of infrastructure work. LangChain4j gives all of that pre-built.
> We define the agent as a Java interface with an annotation for the system
> prompt. Tools are just Java methods with @Tool annotations. Spring AI was
> also considered but was at milestone release stage — not production stable
> at the time. LangChain4j was more mature for agent orchestration."

---

### Q3: "Why Cassandra for conversation storage and not PostgreSQL?"

**Approach:** Framing first (what's the access pattern?), then the fit.

**Say:**
> "The primary access pattern is write-heavy time-series data — every user message
> and every AI response is a write, across potentially thousands of concurrent
> conversations. Cassandra is purpose-built for high-throughput distributed
> writes — you scale horizontally by adding nodes, not vertically by buying
> bigger hardware. PostgreSQL is excellent but vertically scaled and not ideal
> for this pattern. The trade-off is we can't do complex SQL analytics across
> conversations — but our analytics dashboard handles that separately. Also, the
> team already runs MCSE production workloads on Cassandra, so operational
> familiarity was a real factor."

---

### Q4: "What is RAG and why did you need it?"

**Approach:** Problem first. The LLM doesn't know internal docs. Then solution.

**Say:**
> "RAG — Retrieval-Augmented Generation — solves the problem that our LLM was
> trained on public internet data and knows nothing about our internal runbooks,
> incident postmortems, or PNS documentation. The context window is 8,192 tokens,
> so we can't put thousands of pages in every prompt. Instead, we convert all
> our documents into 1536-dimensional vectors using Azure OpenAI's embedding model
> and store them in Milvus, our vector database. When a question comes in, we
> embed the question, find the 5 most semantically similar document chunks using
> cosine similarity, and add only those to the prompt. The LLM can then answer
> using relevant internal context, not just general knowledge."

---

### Q5: "What is the difference between REST and SSE? Why SSE for chat?"

**Say:**
> "REST is request-response — you send a request, you wait, you get one response.
> For chat, the AI takes 8-12 seconds and generates the answer progressively.
> SSE — Server-Sent Events — keeps an open HTTP connection and lets the server
> push multiple events to the browser as they happen. We push one event per
> pipeline step: classifier done, tool executing, tool result, synthesiser
> writing, done. The user sees real-time progress instead of waiting at a blank
> screen. We chose SSE over WebSocket for chat because SSE is unidirectional
> — server pushes only — which is all we need here. WebSocket is bidirectional
> and is used for the MCP tools protocol where the client also sends tool calls."

---

### Q6: "What is the system prompt and who writes it?"

**Approach:** Explain what it is, then be specific about what it contains.

**Say:**
> "The system prompt is the set of instructions given to the LLM before every
> conversation. It defines the agent's persona, its reasoning approach, and
> the business rules it should follow. In our app it encodes PNS-specific logic —
> which tool to call first for eligibility questions, what the default postal
> codes are for Mexico and Canada, how to interpret carrier method restrictions,
> when to check Wakanda versus DCC first. This content came directly from domain
> expertise — knowing the right evaluation order for promise failures is not
> something you can derive from reading the LangChain4j docs. It requires
> understanding how the PNS pipeline actually works."

---

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
✅  INTERVIEW READY — You have covered all 6 layers, the full
    request flow, and 6 key technical questions.
    Stop here if short on time.
    Deep Dive below adds code-level details and edge cases.
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

## 🔬 DEEP DIVE — The Memory Key System

Every session in Cassandra is stored under a composite key:

```
Format: "{MARKET}_{DOMAIN}:{TENANTCODE}:{SESSIONID}"

Mexico PNS session:  "MX_pns:hvgqan:sess_abc123"
Canada PNS session:  "CA_pns:qxjed8:sess_xyz789"
Mexico Confluence:   "MX_confluence:hvgqan:sess_abc123"
```

Why composite? Because:
- Same user (sess_abc123) in Mexico and Canada = different context
- Same session in PNS and Confluence = different memory
- This prevents cross-market and cross-domain memory bleed

The `TenantContextManager` uses Java's `ThreadLocal` to store this.
ThreadLocal = each request thread gets its own copy. Concurrent requests
don't share state.

---

## 🔬 DEEP DIVE — Token Counting and Cost Monitoring

The app wraps the LLM model with a custom `TokenCountingChatModelWrapper`:

```java
// Decorator pattern — wraps the real model, adds token counting
TokenCountingChatModelWrapper wrappedModel =
    new TokenCountingChatModelWrapper(originalModel, analyticsService);
```

Every LLM call logs:
- Input tokens (system prompt + history + tools + RAG)
- Output tokens (the LLM's response)
- Total tokens × price per token = cost per query

Why this matters: Azure OpenAI charges per token. A complex query with 10
tool calls might use 15,000 tokens. At scale (thousands of queries/day),
this adds up. The analytics dashboard lets you monitor cost per user,
per market, per question type.

---

## 🔬 DEEP DIVE — Concurrent Tool Execution

When the LLM determines it needs two independent pieces of data, it can
request both tools simultaneously:

```
LLM: "I need getCarrierMethodInfo AND getWakandaInventory — these are
      independent, I'll request both."

LangChain4j (concurrent execution enabled):
  → Calls both tools in parallel
  → DCC API call + Wakanda API call run simultaneously
  → Both results available in ~max(DCC_time, Wakanda_time)
  → vs sequential: DCC_time + Wakanda_time

Config: langchain4j.tools.concurrent-execution=true
        langchain4j.tools.max-concurrent-executions=100
```

This is why response times are 8-12 seconds even with 5-7 tool calls —
many of them run in parallel.

---

## 🔬 DEEP DIVE — Document Chunking Strategy

Before PNS runbooks go into Milvus, they're split into chunks:

```
CHUNK SIZE: 1,200 characters
OVERLAP: 300 characters (chunks share 300 chars with neighbours)

Why overlap?
  Imagine a runbook section ends with "...if the carrier method
  does not support express delivery, check the zone configuration"
  and the next chunk starts "in Zone 4 specifically..."

  Without overlap: the connection between "carrier method" and "Zone 4"
  is lost across chunks.
  With overlap: both chunks contain the bridge sentence.

Tools: Apache Tika (parses PDF, Word, txt) + Tesseract OCR (for images/scans)
```

---

**Next: `03_MY_CONTRIBUTION.md` — What you built and how to tell that story.**
