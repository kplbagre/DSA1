# eBay MTS1 — AI & ML-Flavored Interview Prep

> **Companion files:**
> - Research: **`DSA/Interview/ebay-mts1-research.md`** (confirmed sources + interview format)
> - DSA problems: **`DSA/Interview/ebay-mts1-dsa-problems.md`** (Sections 1–22)
> - SD + LLD: **`DSA/Interview/ebay-mts1-sd-lld.md`** (pending)

---

## 🗺️ Table of Contents

1. [Scope — Where AI Shows Up (and Where It Doesn't)](#scope)
2. [Terminology — First-Use Glossary](#terminology)
3. [eBay AI Products Reference](#ebay-ai-products)
4. [Behavioral: "How Have You Used AI in Your Work?"](#behavioral)
5. [SD: Design Magic Listing (Photo → Listing Generator)](#magic-listing)
6. [SD: Design a Recommendation Service](#recommendation)
7. [SD: Design Search Autocomplete with AI Catalog Awareness](#autocomplete)
8. [Monitoring & Rollback Probe — The "10% Traffic" Question](#monitoring)
9. [What NOT to Prepare — ML Theory Out of Scope](#not-in-scope)

---

## 🎯 Scope — Where AI Shows Up (and Where It Doesn't) {#scope}

> Read this section before anything else. The signal on AI is real but narrow.
> Don't derail DSA prep for this — budget at most 4–6 hours total.

| Interview Round | AI Signal | Tier |
|----------------|-----------|------|
| R1 Coding (DSA) | **None confirmed.** Zero reports of ML/AI questions in R1. | ✗ Not here |
| R2 System Design | **Emerging.** ML-flavored SD questions appearing at MTS2+ level, moving toward MTS1. Primarily: design a recommendation service, design Magic Listing backend, search ranking. | ⭐ Prepare 1 scenario deep |
| R3 Director / Behavioral | **"How have you used AI tools in your work?"** — seen empirically. 1–2 sentences is enough. | ✅ Prepare a sentence |
| Technical bar-raiser | **Possible.** Same SD-level questions as R2. | ⭐ Covered by R2 prep |

**What this means for your prep:**
- Nail 1 AI SD scenario **deeply** (Magic Listing — it's eBay's flagship AI product)
- Have 1 behavioral sentence ready about your own AI work
- Know eBay's AI products by name so you can connect design questions to their real products
- Do **not** prepare ML theory (bias-variance, SHAP, CUPED, model training math)

---

## 📖 Terminology — First-Use Glossary {#terminology}

> These terms appear throughout this file. Glossed once here — bare everywhere else.

| Term | Plain English |
|------|--------------|
| **LLM** (Large Language Model) | A neural network trained on massive text corpora that generates coherent text by predicting the next token — like GPT-4 or Claude |
| **Embedding** | A fixed-length numeric vector that encodes the *meaning* of a piece of text or image — similar things have similar vectors |
| **Two-tower model** | A neural architecture where user features and item features each go through their own neural network ("tower") and the dot product of their output vectors measures relevance |
| **Feature store** | A centralised database that pre-computes and caches ML feature values (e.g., user's 30-day click history) so both training and serving can read them without recomputing |
| **RAG** (Retrieval-Augmented Generation) | A technique where a vector search retrieves relevant documents first, then feeds them as context to an LLM — so the LLM answers with grounded facts, not hallucinations |
| **ANN** (Approximate Nearest Neighbor) | An algorithm (e.g., Faiss, HNSW) that finds the closest embedding vectors in a large index in milliseconds — used for similarity search at scale |
| **Canary deployment** | Rolling a new model version to a small percentage of traffic (e.g., 5–10%) first, to catch regressions before full rollout |
| **p99 latency** | The 99th percentile response time — 99% of requests complete within this threshold; a good proxy for tail latency that real users experience |
| **GMV** (Gross Merchandise Value) | The total dollar value of goods sold on a marketplace — eBay's primary business metric |
| **CTR** (Click-Through Rate) | % of impressions that result in a click — a proxy for ranking relevance |
| **Knowledge graph** | A structured database of entities and their relationships — eBay uses one to link products to attributes (brand, category, compatible parts) for grounding LLM output |
| **Training-serving skew** | When the features seen at model training time differ from features seen at inference time — a silent cause of model degradation in production |
| **@Tool annotation** | LangChain4j's way of marking a Java method as callable by an LLM agent — the annotation's string is the description the LLM reads to decide when to call the method |

---

## 🧭 eBay AI Products Reference {#ebay-ai-products}

> Know these by name. If an interviewer asks "what do you know about eBay's AI work?",
> pick two from this table and describe their backend architecture implication.

### 🔹 Magic Listing

**What it does:** Seller takes a photo of an item → Magic Listing auto-generates the listing title, description, category, and suggested price.

**Scale:** 10M+ sellers; 200M+ listings generated via Magic Listing since launch (eBay engineering blog 2023 — this is a cumulative adoption figure, not the total eBay catalog which is 1.5B+ active listings); "several billion dollars GMV" improvement cited.

**Backend architecture implication:** Computer vision pipeline (object detection → feature extraction) → product knowledge graph lookup (to ground the LLM's output in eBay's structured catalog) → LLM inference → output validation. Latency budget is tight (seller is waiting, photo just taken).

**Confirmed in:** eBay Eng Blog 2023, eBay AI Product page, multiple eBay engineers' LinkedIn posts about this feature.

---

### 🔹 Shop the Look

**What it does:** Personalized fashion discovery — shows outfit combinations from listings the user hasn't seen. Computer vision + embeddings to match styles across catalog.

**Backend architecture implication:** Embedding similarity search (ANN index) + personalization model that weights user style history. eBay reported a "10+ fashion item views in 180 days" engagement threshold for personalization to kick in.

---

### 🔹 Explore

**What it does:** Personalized product discovery feed — curated, not search-driven. User scrolls a feed of items they're likely to want before they know they want them.

**Backend architecture implication:** Feature store + ranking model (click/conversion feedback loop). Needs real-time user context (recent views, dwell time) combined with batch-computed user embeddings.

---

### 🔹 AI CRM — Email Subject Line Optimization

**What it does:** LLM rewrites email subject lines for seller outreach campaigns. Reported a 40%+ increase in "quality visits" from personalized subject lines.

**Backend architecture implication:** LLM fine-tuned on eBay's historical email engagement data. Offline batch: generate candidate subject lines → score against engagement model → send top performer. A/B testing framework sits underneath this.

---

### 🔹 Google PLA Title Optimization

**What it does:** LLM rewrites eBay listing titles in Google Product Listing Ad (PLA) format to improve Google Quality Score → drives incremental GMV.

**Backend architecture implication:** Batch pipeline: crawl listings → LLM rewrite per listing type → A/B test quality score uplift → if GMV positive, replace original title for ad bidding. Production concern: 1.5B+ active listings — you can't rewrite them all at once.

---

### 🔹 LLM Platform Upgrade

**What it does:** Internal eBay platform that supports models 100× larger than their 2023 baseline. Enables all the above products to run at scale.

**Backend architecture implication:** Distributed inference, model serving at scale (GPU cluster management, KV-cache, request batching). Not an interview topic unless you're applying for MLSys.

---

## 🧠 Behavioral: "How Have You Used AI in Your Work?" {#behavioral}

> ⭐ This is one of the highest-value sections in this file.
> The question appears in Director-level behavioral rounds and increasingly in bar-raiser rounds.
> Ground your answer in your actual TransNova/aiPnSBackend work — never generic.

### 🎯 The Question Variants

- "How have you used AI tools in your daily work?"
- "Have you integrated AI into any of your projects?"
- "What's your experience building AI-powered systems?"
- "How do you stay current with AI developments in backend engineering?"

---

### 🚀 Your Answer — 3 Levels

**30-second version (behavioral prompt opener):**

> "Most recently I built the domain layer for an AI-powered debugging assistant for Walmart's Promise and Shipping platform. Engineers type natural language questions — like 'why is this offer not eligible for 2-day delivery in Mexico?' — and the AI automatically calls the right internal APIs, chains the results, and gives a reasoned answer. I specifically owned the 40+ tool implementations using LangChain4j and the system prompt that encodes PNS business logic. That work reduced investigation time from 15-20 minutes of manual multi-system debugging to under 10 seconds."

**60-second version (when they ask "tell me more"):**

> "The system uses Azure OpenAI's gpt-oss-120b model behind LangChain4j. I implemented over 40 @Tool-annotated Java methods — each one wraps a specific PNS API: DCC for template and carrier method data, Wakanda for inventory, the PNO Ingestor for offer replay, and Cassandra for raw MCSE data. The hardest part was writing tool descriptions that the LLM reads correctly — if three tools all relate to 'carrier data,' the LLM has to pick the right one based on the question context, and that requires the descriptions to be precise enough to disambiguate. I validated the agent's answers using 20 real past incidents where we already knew the root cause — ran those questions through the agent and checked whether it identified the correct tool call sequence and root cause."

**If they ask "what was technically hard about it?":**

> "Two things. First, tool description disambiguation — I had three different tools for carrier-related data, and the LLM would pick randomly if the descriptions overlapped. Getting the right level of specificity in those descriptions was iterative — test a real scenario, see which tool the LLM called, revise the description, retest. Second, the system prompt iteration — early versions had the AI checking inventory before carrier restrictions, which is backwards for most PNS debugging scenarios. Encoding the right reasoning order required understanding how promise failures actually propagate through our system."

---

### 🎨 Visual — What You Actually Built

```
KAPIL'S CONTRIBUTION (PNS Domain Layer)
════════════════════════════════════════

  User: "Why is offer 12345 not 2-day eligible in Mexico?"
             │
             ▼
  ┌─────────────────────────────────────────────────────────────┐
  │  SPRING BOOT API LAYER  (team-built infrastructure)         │
  └──────────────────────────────┬──────────────────────────────┘
                                 │
             ┌───────────────────▼──────────────────────────┐
             │  PnsConversationHandler.java   ← YOU BUILT   │
             │  Routes query to PNS AI Agent                 │
             │  Multi-market validation (MX / CA)            │
             └───────────────────┬──────────────────────────┘
                                 │
             ┌───────────────────▼──────────────────────────┐
             │  LangChain4j Agent (gpt-oss-120b)            │
             │  ┌────────────────────────────────────┐      │
             │  │ System Prompt ← YOU WROTE          │      │
             │  │ "Check template first, then        │      │
             │  │  carrier restrictions, then zone,  │      │
             │  │  then inventory. MX default postal:│      │
             │  │  06600."                           │      │
             │  └────────────────────────────────────┘      │
             │  Decides: call getOfferTemplateNodeMapping    │
             └───────────────────┬──────────────────────────┘
                                 │
       ┌─────────────────────────▼────────────────────────────┐
       │  40+ @Tool-annotated Methods  ← YOU BUILT            │
       │                                                       │
       │  DccApiTools.java           WakandaApiTools.java      │
       │  getTemplateInfo()          getWakandaInventory()     │
       │  getCarrierMethodInfo()     getInventoryByPostal()    │
       │  getZoneCharges()                                     │
       │  getCarrierRestrictions()   PnoIngestorTools.java     │
       │  getSellerNodeMapping()     checkOfferEligibility()   │
       │  ...                        replaySingleOffer()       │
       │                                                       │
       │  McseCassandraDbLookupTools.java                      │
       │  getCassandraOfferInfo()    getTntInformation()        │
       └─────────────────────────────────────────────────────-┘
                                 │
       ┌─────────────────────────▼────────────────────────────┐
       │  REAL PNS SYSTEMS (called by your tools)             │
       │  DCC API | Wakanda | Cassandra | PNO Ingestor        │
       └──────────────────────────────────────────────────────┘

AI answer: "Offer 12345 is blocked by carrier method CM789:
            maxSLA=3 days, Zone=4. 2-day requires Zone≤3.
            Recommend: check template T234 carrier settings."

TIME SAVED: 15-20 minutes → under 10 seconds
```

> **Key invariant to communicate:** The AI infrastructure (LangChain4j, Cassandra memory, Milvus, Spring Boot wiring) was built by the team. You owned the part that required 5 years of PNS domain knowledge — the tool implementations and the business logic in the system prompt.

---

## 1. SD: Design Magic Listing — Photo → Listing Generator {#magic-listing}

> 🧩 **Tier: ⭐ High priority.** Magic Listing is eBay's flagship AI product.
> Most likely AI SD scenario at eBay. Go deep here.

### 🎯 Problem Statement

Design the backend system that powers Magic Listing: a seller takes a photo of an item on their phone → the system automatically generates a title, description, category, and suggested price for the eBay listing.

**Scale:**
- 10M+ active sellers
- 200M+ listings generated via Magic Listing (cumulative since launch — distinct from total eBay catalog, which is 1.5B+)
- Peak traffic: 50,000 photo uploads/minute (weekends, garage sale season)
- Latency SLA: < 3 seconds end-to-end (seller is looking at their phone waiting)

---

### 🧭 Clarifying Questions First

Before drawing anything, ask these — they set the design direction:

1. **What counts as "done"?** Does the seller accept the generated listing or can they edit? → Yes, always editable. We're generating a starting point.
2. **How accurate must the category classification be?** → Wrong category is bad UX but not catastrophic — seller can fix it. Wrong price range is worse.
3. **Do we need to handle multi-item photos?** → V1: one main item per photo. V2: multiple items.
4. **What's the latency budget?** → < 3 seconds for title + category. Price can be async (another 1-2s).
5. **What languages?** → English only for V1. eBay is global — keep i18n hooks in the design.
6. **Can we use eBay's existing product catalog?** → Yes. eBay has a product knowledge graph with structured item data (brand, model, compatible parts).

---

### 🎨 Visual — Magic Listing Architecture

```
MAGIC LISTING — BACKEND ARCHITECTURE
══════════════════════════════════════════════════════════════════

  SELLER (mobile app)
     │ POST /api/magic-listing/analyze
     │ { photo: base64, sellerId: "s123", market: "US" }
     ▼
  ┌─────────────────────────────────────────────────────────────┐
  │  API GATEWAY (rate limit: 50 rps/seller, auth, routing)     │
  └────────────────────┬────────────────────────────────────────┘
                       │
       ┌───────────────▼────────────────────────────────────┐
       │  LISTING ORCHESTRATOR SERVICE  (stateless Java)     │
       │  - Validates image (size, format, safe content)     │
       │  - Assigns request ID                               │
       │  - Fans out to 3 parallel services                  │
       └───┬───────────────┬────────────────────┬───────────┘
           │               │                    │
           ▼               ▼                    ▼
  ┌────────────────┐ ┌──────────────────┐ ┌──────────────────────┐
  │  VISION        │ │  PRODUCT         │ │  PRICE               │
  │  PIPELINE      │ │  KNOWLEDGE       │ │  ESTIMATION          │
  │  SERVICE       │ │  GRAPH SERVICE   │ │  SERVICE             │
  │                │ │                  │ │                      │
  │ 1. Resize/     │ │  Looks up item   │ │  Queries sold-item   │
  │    preprocess  │ │  entity in eBay  │ │  history for         │
  │ 2. Object      │ │  product graph   │ │  similar items       │
  │    detection   │ │  by detected     │ │  → median price      │
  │    (YOLO-class)│ │  object class    │ │  → price range       │
  │ 3. Feature     │ │  → brand, model  │ │                      │
  │    extraction  │ │  → category tree │ │  Separate from main  │
  │    embedding   │ │  → typical attrs │ │  path (can be async) │
  └──────┬─────────┘ └──────┬───────────┘ └──────────┬───────────┘
         │                  │                         │
         └──────────────────▼─────────────────────────┘
                            │  (image embedding + product context)
                            ▼
              ┌─────────────────────────────────────┐
              │  LLM INFERENCE SERVICE               │
              │  (gpt-4-vision or similar)           │
              │                                      │
              │  Input:                              │
              │  - Image (base64 or URL)             │
              │  - Product graph context (JSON)      │
              │  - eBay-specific system prompt       │
              │  - Category path from vision         │
              │                                      │
              │  Output:                             │
              │  - Title (80 char max)               │
              │  - Description (HTML, 1000 char)     │
              │  - Item specifics (key-value pairs)  │
              │  - Condition suggestion              │
              └─────────────────┬───────────────────┘
                                │
              ┌─────────────────▼───────────────────┐
              │  OUTPUT VALIDATOR                    │
              │  - Title length check                │
              │  - Prohibited words filter           │
              │  - Category plausibility score       │
              │  - Price range sanity check          │
              └─────────────────┬───────────────────┘
                                │
              ┌─────────────────▼───────────────────┐
              │  RESPONSE CACHE  (Redis, TTL 1h)     │
              │  Key: SHA256(image) → avoids re-     │
              │  processing identical photos         │
              └─────────────────┬───────────────────┘
                                │
                                ▼
              Seller sees: pre-filled listing form

KEY INVARIANT:
   The vision pipeline runs FIRST and its output (detected object class +
   embedding) gates everything downstream. Product knowledge graph lookup
   and LLM grounding both depend on knowing what the object IS before
   they can add structured context. Never skip this sequencing.
```

---

### 🔬 Deep Dive — Key Components

#### Vision Pipeline

The vision pipeline has two stages:

**Stage 1 — Object Detection:** Identifies what the item is. Output: class label + bounding box. ("Nike sneaker", "Vintage guitar", "iPhone 14 case"). eBay uses a model fine-tuned on eBay's own catalog images — off-the-shelf ImageNet classifiers work poorly on diverse secondhand items.

**Stage 2 — Embedding Extraction:** Converts the image into a vector (1024 or 2048 dimensions). This embedding is used to search the product knowledge graph for similar items that have structured metadata. Think of it like: "I don't know exactly what this guitar is, but I can find 10 eBay listings where the photo looks similar."

#### Product Knowledge Graph

eBay's product catalog has structured item definitions:
- Entity: "Fender Stratocaster Electric Guitar"
- Attributes: brand=Fender, model=Stratocaster, material=alder/mahogany, color=[list], compatible accessories=[list]
- Category: Musical Instruments → Guitars & Basses → Electric Guitars

The vision embedding is used to retrieve the closest matching entities from the graph. Those entities provide structured context that grounds the LLM — so instead of hallucinating "vintage guitar," the LLM can say "Fender Stratocaster Electric Guitar — likely 1990s based on headstock shape."

#### LLM Inference — The Prompt Design

The system prompt matters enormously. eBay's engineering blog confirms iterating on this:

```
System: You are an eBay listing assistant. Generate a listing title
and description for the item in the image. Follow these rules:
- Title: max 80 characters, include brand/model if visible, no spam words
- Description: highlight condition, key features, include item specifics
- Use eBay's standard item condition vocabulary: "Brand New", "Like New",
  "Very Good", "Good", "Acceptable"
- Category must match one of the following: [list from vision pipeline]
- Do not invent technical specs not visible in the image
- Seller's context: {sellerId, historicalListingStyle}

Product knowledge graph context:
{json blob from knowledge graph lookup}

User: [image provided via vision API]
```

The key discipline: the LLM is **grounded** by the product knowledge graph. It's not inventing attributes — it's selecting from a constrained structured context. This dramatically reduces hallucination.

#### Caching Strategy

Identical photos (same SHA256 hash) get cached responses for 1 hour. This matters for:
- Same seller uploads same photo twice (common — phone glitch)
- Multiple sellers selling the same popular item (e.g., new iPhone boxes — thousands of identical photos)

---

### ⚠️ Trade-offs

| Decision | Option A | Option B | Recommended design choice / rationale (inferred — not sourced from eBay internal) |
|----------|----------|----------|----------------------|
| **Vision model** | General (CLIP/BLIP2) | Fine-tuned on eBay catalog | Fine-tuned — eBay's item diversity is too specific for general models |
| **LLM modality** | Text-only (describe the image externally first) | Multimodal LLM (feeds image directly) | Multimodal — fewer hops, better coherence |
| **Price estimation** | Synchronous (blocks main response) | Asynchronous (send title/desc first, price appears 1s later) | Async — prices need time to check sold history, sellers can wait 1 extra second |
| **Product graph** | Static graph (monthly updates) | Real-time graph (syncs with catalog) | Near-real-time (Kafka CDC from catalog service) — 1B+ listings change frequently |
| **Knowledge graph retrieval** | Exact match (requires correct category) | Embedding similarity (works even if category is misdetected) | Embedding similarity — more robust to vision errors |

---

### 🔁 Follow-Up Probes

**Q1: How do you handle hallucinations in the generated title?**
> The output validator checks the title against: (a) prohibited eBay words list, (b) category plausibility — title says "guitar" but detected category is "Electronics" → flag for human review. Long term: human-in-the-loop feedback loop where accepted vs. rejected generated titles feed fine-tuning.

**Q2: What if the vision pipeline misidentifies the object?**
> Two safety nets: (a) confidence threshold — if object detection confidence < 0.7, fallback to "describe what you see" prompt without product graph grounding. (b) Seller edits — every field is editable. eBay's data shows sellers correct < 15% of Magic Listing fields, meaning the system is right 85%+ of the time.

**Q3: How do you A/B test improvements to the LLM prompt?**
> Shadow mode: new prompt generates a response in parallel but doesn't show it to seller. Compare accepted-listing rates and edit-percentage between control and treatment. When treatment wins on edit-rate and accepted-listing-rate, promote.

**Q4: How does this scale to 50,000 photos/minute?**
> Stateless services scale horizontally. The bottleneck is LLM inference (GPU-bound). Solutions: (a) request batching in the inference layer — batch 8–16 images per GPU call. (b) LLM inference auto-scaling (GPU clusters with queue-based scale triggers). (c) Smaller fine-tuned model for common item categories where quality is already high enough.

**Q5: How do you handle the seller privacy concern — photos are personal?**
> Images are processed in-region (US data stays on US servers, EU on EU). Images are not stored permanently — they're deleted after the listing draft is created unless the seller confirms. SHA256 hash cache stores the hash, not the image. Images are never used for external training without seller opt-in (GDPR/CCPA consideration).

---

## 2. SD: Design a Recommendation Service {#recommendation}

> 🧩 **Tier: ⭐ Confirmed at MTS2+ level, increasingly appearing at MTS1.**
> Know the architecture. Don't need to go as deep as Magic Listing.

### 🎯 Problem Statement

Design the backend for eBay's "Shop the Look" or "Explore" — a personalized product recommendation service that shows users items they haven't seen but are likely to want.

**Scale:**
- 130M+ active buyers worldwide
- 1.5B+ active listings
- Recommendation requests: 500M/day (every home page load, search result page)
- Latency SLA: < 100ms P99 (users notice > 150ms)

---

### 🎨 Visual — Two-Tower Recommendation Architecture

```
RECOMMENDATION SERVICE — TWO-TOWER MODEL
══════════════════════════════════════════════════════════════════

OFFLINE PATH (runs continuously, batch + streaming)
─────────────────────────────────────────────────────────────────

  RAW SIGNALS (Kafka topics)
  clicks, views, purchases, dwell time, saves
        │
        ▼
  ┌─────────────────────────────────────────┐
  │  FEATURE PIPELINE (Spark Streaming)     │
  │  - User signals → user features         │
  │  - Item metadata → item features        │
  │  - Writes to FEATURE STORE              │
  └─────────────────────────────────────────┘
        │
        ▼
  ┌─────────────────────────────────────────┐
  │  TWO-TOWER MODEL TRAINING (Spark/PyTorch│
  │                                         │
  │   USER TOWER         ITEM TOWER         │
  │  ┌──────────┐       ┌──────────┐        │
  │  │ userId   │       │ itemId   │        │
  │  │ category │       │ category │        │
  │  │ history  │  ∙    │ price    │        │
  │  │ location │       │ brand    │        │
  │  └────┬─────┘       └────┬─────┘        │
  │       │                  │              │
  │  [128-dim embedding] [128-dim embedding]│
  │       └──────────────────┘              │
  │              dot product                │
  │         = relevance score               │
  └────────────────┬────────────────────────┘
                   │  trained item embeddings
                   ▼
  ┌─────────────────────────────────────────┐
  │  ANN INDEX (Faiss / ScaNN)              │
  │  1.5B item embeddings pre-indexed       │
  │  Refresh: nightly full + hourly delta   │
  └─────────────────────────────────────────┘


ONLINE PATH (per-request, < 100ms budget)
─────────────────────────────────────────────────────────────────

  USER REQUEST
  { userId: "u123", context: "home_page", device: "iOS" }
       │
       ▼
  ┌──────────────────────────────────────────────────────────┐
  │  RETRIEVAL LAYER  (~30ms budget)                         │
  │                                                          │
  │  1. Read user features from FEATURE STORE (Redis)        │
  │  2. Run USER TOWER → 128-dim user embedding              │
  │  3. ANN search on item index → top 500 candidates        │
  └─────────────────────┬────────────────────────────────────┘
                        │ 500 candidates
                        ▼
  ┌──────────────────────────────────────────────────────────┐
  │  RANKING LAYER  (~40ms budget)                           │
  │                                                          │
  │  Takes 500 candidates → scores each with richer model    │
  │  Features added at rank time:                            │
  │    - Real-time inventory (is it still available?)        │
  │    - Price competitiveness score                         │
  │    - Quality score (seller rating, return rate)          │
  │    - Business rules (promoted listings get boost)        │
  │                                                          │
  │  Output: top 20 ranked items                             │
  └─────────────────────┬────────────────────────────────────┘
                        │ 20 items
                        ▼
  ┌──────────────────────────────────────────────────────────┐
  │  FILTERING LAYER  (~10ms budget)                         │
  │                                                          │
  │  - Remove already-purchased items                        │
  │  - Remove out-of-stock items                             │
  │  - Diversity enforcement (max 3 per seller)              │
  │  - Safe-search filters (policy compliance)               │
  └─────────────────────┬────────────────────────────────────┘
                        │ final 20 items
                        ▼
  RESPONSE to client in < 100ms

KEY INVARIANT:
   Retrieval (500 candidates, fast ANN) runs FIRST to cut the
   search space from 1.5B items to 500. Only then does the
   expensive ranking model run. The ranking layer sees at most
   500 items — never the full corpus. This is why p99 < 100ms
   is achievable.
```

---

### 🔬 Deep Dive — Feature Store

The feature store (like Tecton or a custom Redis+Kafka setup) holds:

| Feature | Freshness | Where stored |
|---------|-----------|-------------|
| User embedding (batch) | Updated daily | Redis (pre-computed) |
| User recent views (last 24h) | Near-real-time | Redis (Kafka consumer writes) |
| Item embedding | Updated nightly | Faiss ANN index |
| Item price, availability | Real-time | Item service cache |
| User purchase history | Batch | Cassandra |

**Training-serving skew is the #1 silent bug:** If the model trained on "user's 30-day click history" but at serving time you read "user's 24-hour view history" from Redis, the embedding will be off. Solve by using the same feature computation code for both training pipeline and serving pipeline.

---

### ⚠️ Trade-offs

| Decision | Trade-off |
|----------|-----------|
| **Two-tower vs. single model** | Two-tower allows pre-computing item embeddings offline — enables ANN search on 1.5B items. A single model scoring every item at runtime is O(n) — too slow. |
| **ANN vs. exact NN** | ANN trades tiny recall loss (< 1%) for 1000× speed gain. At 1.5B items, exact search takes minutes. |
| **Batch embeddings vs. real-time** | Batch: embeddings are stale (up to 24h). New items miss exposure. Real-time: expensive, complex. Hybrid: batch for all items + streaming updates for new/trending items. |
| **Collaborative filtering vs. content-based** | CF needs interaction history — cold start problem for new users. Content-based uses item attributes — works for cold start but misses serendipity. Two-tower blends both. |

---

### 🔁 Follow-Up Probes

**Q1: How do you handle new users with no click history (cold start)?**
> Fallback to content-based signals: location, device type, onboarding category preference. Show trending items in those categories. After 3–5 interactions, hybrid model kicks in.

**Q2: How do you measure recommendation quality?**
> Offline: NDCG (Normalized Discounted Cumulative Gain — a measure of ranking quality that gives more credit for relevant items appearing earlier in the list). Online A/B test: CTR + conversion rate + GMV per session. Caution: optimizing CTR alone drives clickbait. eBay's target metric is "quality visit" (click that leads to engagement), not raw CTR.

**Q3: What happens when a new item is listed?**
> Item embedding is computed within 1 hour via streaming Spark job. Until then, the item appears in content-based fallback (title/category search), not personalised recommendations. This is the standard "item cold start" problem.

---

## 3. SD: Design Search Autocomplete with AI Catalog Awareness {#autocomplete}

> 🧩 **Tier: ⭐ One confirmed report — know the shape, don't over-invest.**

### 🎯 Problem Statement

Design eBay's search autocomplete that handles 1M catalog updates per day and uses AI/LLM to suggest query rewrites (not just prefix matching).

**Scale:**
- 500M search queries/day → ~50% start from autocomplete suggestions
- 1M catalog additions/deletions/updates daily
- Latency SLA: < 50ms (suggestions must appear as user types)

---

### 🎨 Visual — Autocomplete with AI Awareness

```
SEARCH AUTOCOMPLETE — HYBRID TRIE + AI REWRITE
══════════════════════════════════════════════════════════════════

INGESTION PATH (keep index fresh with 1M daily catalog changes)
─────────────────────────────────────────────────────────────────

  eBay Catalog Service
  (new listings, price changes, sold/ended)
        │ CDC events (Change Data Capture)
        ▼
  ┌─────────────────────────────────────────┐
  │  Kafka — catalog-updates topic          │
  │  1M events/day ≈ 12 events/second avg  │
  └─────────────┬───────────────────────────┘
                │
  ┌─────────────▼───────────────────────────┐
  │  AUTOCOMPLETE INDEX UPDATER (Flink)     │
  │  - Extracts title n-grams + search terms│
  │  - Computes term frequency × GMV weight │
  │  - Updates Redis Sorted Set per prefix  │
  │  - Updates Elasticsearch suggest index  │
  └─────────────┬───────────────────────────┘
                │
  ┌─────────────▼───────────────────────────┐
  │  INDEX STORES                           │
  │  Redis: prefix → [top 10 suggestions]   │
  │         (hot prefix cache, < 1ms reads) │
  │  Elasticsearch: long-tail suggestions   │
  │         (slower, richer matching)       │
  └─────────────────────────────────────────┘


QUERY PATH (< 50ms)
─────────────────────────────────────────────────────────────────

  User types "nik" → keypress event
       │
       ▼
  ┌──────────────────────────────────────────────────────────┐
  │  CLIENT DEBOUNCE (150ms after last keypress)             │
  │  Prevents firing for every keystroke                     │
  └──────────────────┬───────────────────────────────────────┘
                     │
                     ▼
  ┌──────────────────────────────────────────────────────────┐
  │  AUTOCOMPLETE SERVICE                                    │
  │                                                          │
  │  1. Redis lookup for prefix "nik" → hit?                 │
  │     Yes → return top 10 immediately (< 5ms)             │
  │     No  → query Elasticsearch (< 30ms)                  │
  │                                                          │
  │  2. AI REWRITE LAYER (async, does NOT block response)    │
  │     - Detects probable intent ("nike" = brand search)    │
  │     - Adds rewrite suggestions: "nike air max", ...      │
  │     - Streamed in AFTER initial suggestions appear       │
  │                                                          │
  │  3. Personalisation boost (logged-in users only)         │
  │     - Boost suggestions matching user's purchase history │
  │     - User who bought "nike shoes" gets shoe suggestions │
  │       ranked higher than "nike apparel"                  │
  └──────────────────────────────────────────────────────────┘

KEY INVARIANT:
   Baseline suggestions (from Redis/Elasticsearch) NEVER wait for
   the AI rewrite layer. AI rewrites stream in as progressive
   enhancement. This guarantees < 50ms for all users regardless
   of LLM inference latency.
```

---

### ⚠️ Fault Tolerance for 1M Daily Updates

The stated challenge in candidate reports: "fault-tolerant autocomplete ingesting 1M catalog updates/day."

**What fault-tolerant means here:**

1. **Kafka durability:** Catalog events are persisted in Kafka with 7-day retention. If the index updater goes down, it replays from its committed offset — no events are lost.

2. **Index updater idempotency:** Each catalog event has a unique `listingId + version`. The updater upserts by this key — processing the same event twice is safe.

3. **Redis as hot cache, not source of truth:** Redis can be rebuilt from Elasticsearch if it loses data. Elasticsearch is the durable store.

4. **Graceful degradation:** If Redis is down → fall back to Elasticsearch (slightly higher latency, but correct). If Elasticsearch is down → serve from Redis-only with stale-but-present data. If both down → return empty suggestions (search still works, autocomplete is empty).

---

## ⚠️ Monitoring & Rollback Probe — The "10% Traffic" Question {#monitoring}

> This is a common follow-up in eBay SD rounds. It's not specifically AI — it's
> about ML-aware production engineering. Appears after any recommendation or
> ranking system design.

### 🎯 The Probe

> "You've rolled a new search ranking model to 10% of traffic. CTR is flat but
> conversion rate is down 0.8% and p99 latency is up by 25ms. What do you do?"

---

### 🧠 How to Think Through This

This question tests whether you understand that **multiple metrics can move in opposite directions and each tells a different story.**

**Decompose the signals:**

| Signal | What It Tells You |
|--------|------------------|
| CTR flat | Users click at the same rate — relevance hasn't gotten worse for the click decision |
| Conversion −0.8% | Users who click are NOT buying — items shown are clickbait or wrong price point |
| p99 latency +25ms | Model is slower — could be larger model, more features, or a slow feature store call |

**Likely root cause:** The new model ranks items higher that attract clicks but don't convert — possibly over-fitted on click labels rather than purchase labels. The latency increase tells you the model itself is heavier (or a new feature lookup is slow).

---

### 🚀 The Answer Framework (English Steps First)

**Steps in plain English:**

1. **Do not roll back immediately** — gather more data first (at 10% traffic, conversion difference of 0.8% may not be statistically significant yet).
2. **Check statistical significance** — run a power calculation: with 10% of traffic and current conversion rate, how many days to reach 95% confidence? If we already have enough data and the drop is significant → proceed to rollback.
3. **Check the latency root cause** — is +25ms coming from the model inference itself, or from a feature retrieval call the new model requires that the old model didn't? These are different problems.
4. **Check for training-serving skew** — did the model train on "purchase" labels but the feature pipeline at serving time computes features differently?
5. **Decide: rollback or fix?** If conversion loss is significant and p99 is already violating SLA → rollback to old model immediately. If conversion is borderline and latency is fixable → fix latency, continue observation.
6. **Define rollback gate ahead of time** — this should have been defined before launch, not after you observe the problem.

```
DECISION TREE FOR MODEL ROLLOUT ISSUES
═══════════════════════════════════════════════════

  Conversion drop observed (−0.8%, p99 +25ms)
           │
           ▼
  Is the drop statistically significant?
  ┌─── YES ──────────────────┐   ┌─── NO ──────────────────┐
  │                          │   │                         │
  ▼                          │   ▼                         │
Is p99 violating SLA?         │   Continue observation       │
  │ YES → ROLLBACK NOW        │   Add more metrics:          │
  │ NO  → continue ────────┐  │   - Revenue per click        │
  │                        │  │   - Return rate              │
  └────────────────────────┘  │   - Basket size              │
                              └─────────────────────────────-┘
  │ (no rollback but conversion still down)
  ▼
Diagnose root cause:
  1. Training-serving skew?      → Fix feature pipeline
  2. Model over-fitting on CTR?  → Retrain with GMV labels
  3. Latency from feature fetch? → Optimize feature store call
  4. Cohort bias (10% isn't representative)? → Expand to 20%

ROLLBACK GATE (define before launch, not after):
  - Conversion drop > 0.5% AND statistically significant
  - p99 > 150ms (absolute SLA violation)
  - Revenue-per-session drop > 1%
  → Any one of these → immediate rollback
```

---

### 🔁 Follow-Up Probes on This Question

**Q1: What monitoring would you set up before the rollout?**
> Real-time dashboard on: CTR, conversion rate, GMV per session, p99, p95, error rate, feature store cache hit rate. Set automated alert thresholds that page on-call if conversion drops > 0.3% (warning) or > 0.5% (critical).

**Q2: How do you ensure the A/B test is not biased?**
> Randomise by userId, not by session — the same user should always see the same model version during the experiment. Ensure 10% is drawn uniformly across user cohorts (new users, power buyers, mobile-only users).

**Q3: What if rollback to old model is also risky?**
> In practice, rollback to a previously validated model is always safer than a new unvalidated model. Keep the last 2 model versions deployed and tagged — rollback means traffic-shift to the previous version, not a re-deploy.

---

## ⚠️ What NOT to Prepare — ML Theory Out of Scope for MTS1 Backend {#not-in-scope}

> This section is as important as the prep content.
> Studying these will cost you 20+ hours with zero eBay interview ROI at MTS1 backend level.

| Topic | Why it's out of scope for MTS1 backend |
|-------|----------------------------------------|
| **Bias-variance tradeoff math** | ML Engineer / Data Science role. eBay backend engineers are consumers of models, not builders of training pipelines. |
| **SHAP values / feature importance** | Model interpretability — MLE role. Backend SD question is about serving, not explaining model decisions. |
| **CUPED (Controlled-Experiment using Pre-Experiment Data)** | Advanced A/B statistics. eBay's experimentation team owns this. Backend interview is about whether you know to A/B test, not the statistics. |
| **Backpropagation / gradient descent derivations** | Not required unless applying to eBay's ML research or MLSys team. MTS1 backend doesn't write training code. |
| **Transformer architecture (attention heads, positional encoding)** | Deep learning theory. Know LLMs exist and produce embeddings — that's enough. |
| **Loss functions (cross-entropy, hinge loss, etc.)** | Training math. Backend engineers pick "use a ranking model" — they don't derive the loss function. |
| **Neural architecture search** | Research topic. Not an eBay MTS1 backend question. |
| **Federated learning** | Advanced ML privacy technique. Interesting but not an eBay backend interview topic. |

**What you DO need at ML-awareness level (the right altitude):**

- Know what an **embedding** is and why it enables similarity search (mental model: two things that are similar have vectors that are close in space)
- Know what a **feature store** is and why training-serving skew is dangerous
- Know the **retrieval → ranking → filtering** pattern for recommendation at scale
- Know that **LLMs can hallucinate** and how to mitigate it (grounding, output validation)
- Know that **A/B testing is mandatory** before full rollout and what metrics you'd watch
- Know what **canary deployment** is and what your rollback gates should be

---

## 🔄 Changelog

| Date | Change |
|------|--------|
| July 2026 | File created. AI research from 8+ web searches synthesised into this permanent reference. Tier labels applied across all sections. Behavioral answer grounded in TransNova/aiPnSBackend actual work (AGENTS.md §External Context Folders). Three SD scenarios written at depth appropriate to their confirmed tier. Monitoring probe added as standalone section. |
