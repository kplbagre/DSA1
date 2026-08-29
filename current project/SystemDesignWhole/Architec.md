**yes update and write it fully in next message**

## WFE System Architecture: Complete Interconnect Diagram (Revised)

---

# Part 1: The Big Picture

## 1.1 What Problem Does This System Solve?

When a customer visits an e-commerce website and looks at a product, they see a **delivery promise** — "Get it by Friday, Dec 15". This seemingly simple message requires orchestrating dozens of systems in real-time:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                                                                             │
│   Customer sees:  "Get it by Friday, Dec 15"                                │
│                                                                             │
│   Behind the scenes, the system must answer:                                │
│                                                                             │
│   ┌─────────────────────────────────────────────────────────────────────┐   │
│   │                                                                     │   │
│   │  1. Is this item in stock?                    → Inventory Service   │   │
│   │  2. Which warehouse should fulfill it?        → Sourcing Engine     │   │
│   │  3. Does that warehouse have capacity?        → Capacity Service    │   │
│   │  4. What carriers can ship from there?        → Carrier Service     │   │
│   │  5. How long will transit take?               → Transit Time Service│   │
│   │  6. Are there any delivery slots available?   → Slot Service        │   │
│   │  7. Is this item eligible for express?        → Eligibility Service │   │
│   │  8. What's the shipping cost?                 → Pricing Service     │   │
│   │                                                                     │   │
│   │  ALL OF THIS IN < 100 MILLISECONDS                                  │   │
│   │                                                                     │   │
│   └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 1.2 System Layers Overview

The architecture is organized into **five distinct layers**, each with a specific responsibility:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         SYSTEM LAYERS OVERVIEW                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  LAYER 1: ORCHESTRATION LAYER                                       │   │
│  │  ─────────────────────────────                                       │   │
│  │  Purpose: Entry point, request routing, response aggregation         │   │
│  │  Services: Unified Promise                                           │   │
│  │  Pattern: Receives customer request, routes to appropriate services, │   │
│  │           aggregates responses, applies circuit breakers             │   │
│  │                                                                      │   │
│  │  Key Responsibilities:                                               │   │
│  │  • Accept promise requests from clients (web, mobile, checkout)      │   │
│  │  • Route to computation layer based on item type and tenant          │   │
│  │  • Aggregate responses from multiple downstream calls                │   │
│  │  • Apply circuit breakers and fallback logic                         │   │
│  │  • Publish trace events for monitoring and analytics                 │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                              │                                              │
│                              ▼                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  LAYER 2: COMPUTATION LAYER (SOURCING ENGINE)                       │   │
│  │  ────────────────────────────────────────────                        │   │
│  │  Purpose: Execute sourcing logic, calculate ESD/EDD, select nodes   │   │
│  │  Services: MCSE-Lite, Promise-Date                                   │   │
│  │  External: GBAS (for marketplace/3P items)                          │   │
│  │  Pattern: Real-time computation aggregating multiple data sources   │   │
│  │                                                                      │   │
│  │  Key Responsibilities:                                               │   │
│  │  • Fetch inventory availability from NLI (Node Level Inventory)     │   │
│  │  • Get distributor/carrier configurations                           │   │
│  │  • Calculate transit times (TNT) with weather/holiday buffers       │   │
│  │  • Check fulfillment center capacity (FCAP) constraints             │   │
│  │  • Check carrier capacity (CCAP) constraints                        │   │
│  │  • Calculate ESD (Estimated Ship Date) and EDD (Estimated Delivery) │   │
│  │  • Apply cutoff time rules and calendar adjustments                 │   │
│  │  • Rank and select optimal ship nodes                               │   │
│  │  • Handle multi-hop fulfillment paths                               │   │
│  │  • Apply consolidation logic for multi-item orders                  │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                              │                                              │
│                              ▼                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  LAYER 3: DATA SERVING LAYER                                        │   │
│  │  ─────────────────────────────                                       │   │
│  │  Purpose: Serve pre-computed/cached data with ultra-low latency     │   │
│  │  Services: MCSE-Lite (Cache Services), Item-Speed-Eligibility       │   │
│  │  Pattern: Read from cache/database, respond in milliseconds          │   │
│  │                                                                      │   │
│  │  Key Responsibilities:                                               │   │
│  │  • Serve transit time data from cache                               │   │
│  │  • Serve distributor/carrier configurations                         │   │
│  │  • Serve capacity flip status (is node full?)                       │   │
│  │  • Serve pre-computed EDD dates from Hollow Cache                   │   │
│  │  • Serve item speed eligibility (same-day, next-day)                │   │
│  │  • Multi-tier caching: In-memory → Distributed → Database           │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                              │                                              │
│                              ▼                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  LAYER 4: DATA INGESTION LAYER                                      │   │
│  │  ─────────────────────────────                                       │   │
│  │  Purpose: Consume events, transform, persist to database             │   │
│  │  Services: MCSE-Data-Ingestion (Listener + Biz)                      │   │
│  │  Pattern: Kafka consumers → Event handlers → Database writes         │   │
│  │                                                                      │   │
│  │  Key Responsibilities:                                               │   │
│  │  • Consume events from 10+ Kafka topics                             │   │
│  │  • Route events via EventManager (40+ handlers)                     │   │
│  │  • Transform DTOs to Cassandra entities                             │   │
│  │  • Persist with retry logic and TTL support                         │   │
│  │  • Publish downstream events for other consumers                    │   │
│  │  • Handle multi-tenant event routing (US, CA, MX)                   │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                              │                                              │
│                              ▼                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  LAYER 5: CAPACITY & CONFIGURATION LAYER                            │   │
│  │  ─────────────────────────────────────────                           │   │
│  │  Purpose: Manage fulfillment capacity and system configuration       │   │
│  │  Services: Capacity Engine, Fulfillment Capacity, DC-Square          │   │
│  │  Pattern: Event-driven updates, batch processing                     │   │
│  │                                                                      │   │
│  │  Key Responsibilities:                                               │   │
│  │  • Track real-time fulfillment center capacity                      │   │
│  │  • Process order/shipment events to update consumption              │   │
│  │  • Publish capacity flip events when nodes are full                 │   │
│  │  • Manage distributor/carrier/coverage configurations               │   │
│  │  • Handle bulk configuration uploads                                │   │
│  │  • Regional capacity management (US, CA)                            │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

# Part 2: Complete System Interconnect Diagram

```
┌──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                                                                                                      │
│                                    COMPLETE SYSTEM ARCHITECTURE                                                      │
│                                                                                                                      │
├──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                                                      │
│   EXTERNAL CLIENTS                                                                                                   │
│   ────────────────                                                                                                   │
│   ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐                                            │
│   │  Item Page   │  │   Cart Page  │  │   Checkout   │  │  Mobile App  │                                            │
│   │  (Browse)    │  │  (Review)    │  │  (Purchase)  │  │              │                                            │
│   └──────┬───────┘  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘                                            │
│          │                 │                 │                 │                                                     │
│          └─────────────────┴────────┬────────┴─────────────────┘                                                     │
│                                     │                                                                                │
│                                     │ REST: "When can I get this item?"                                              │
│                                     ▼                                                                                │
│ ┌────────────────────────────────────────────────────────────────────────────────────────────────────────────────┐  │
│ │                                                                                                                │  │
│ │   LAYER 1: ORCHESTRATION LAYER                                                                                 │  │
│ │   ════════════════════════════                                                                                 │  │
│ │                                                                                                                │  │
│ │   ┌────────────────────────────────────────────────────────────────────────────────────────────────────────┐  │  │
│ │   │                              UNIFIED PROMISE                                                           │  │  │
│ │   │                              ───────────────                                                           │  │  │
│ │   │                                                                                                        │  │  │
│ │   │  PURPOSE:                                                                                              │  │  │
│ │   │  Single entry point for all promise requests. Shields clients from internal complexity.               │  │  │
│ │   │  Without this, every client would need to orchestrate 8+ service calls themselves.                    │  │  │
│ │   │                                                                                                        │  │  │
│ │   │  RESPONSIBILITIES:                                                                                     │  │  │
│ │   │  • Route requests based on item type (1P vs 3P/Marketplace)                                           │  │  │
│ │   │  • Parallel calls to downstream services using Akka actors                                            │  │  │
│ │   │  • Circuit breaker patterns to prevent cascade failures                                               │  │  │
│ │   │  • Response aggregation and fallback logic                                                            │  │  │
│ │   │  • Publish trace events for monitoring (async, non-blocking)                                          │  │  │
│ │   │                                                                                                        │  │  │
│ │   │  ROUTING LOGIC:                                                                                        │  │  │
│ │   │  ┌─────────────────────────────────────────────────────────────────────────────────────────────────┐  │  │  │
│ │   │  │                                                                                                 │  │  │  │
│ │   │  │   Request ──► Is 1P Item? ──YES──► MCSE-Lite (Internal Sourcing)                               │  │  │  │
│ │   │  │                   │                                                                             │  │  │  │
│ │   │  │                   NO                                                                            │  │  │  │
│ │   │  │                   │                                                                             │  │  │  │
│ │   │  │                   └──────────────► GBAS (External Geo-Based Availability Service)              │  │  │  │
│ │   │  │                                                                                                 │  │  │  │
│ │   │  └─────────────────────────────────────────────────────────────────────────────────────────────────┘  │  │  │
│ │   │                                                                                                        │  │  │
│ │   │  Tech: Akka Actors, Circuit Breakers (Resilience4j), REST APIs                                        │  │  │
│ │   │                                                                                                        │  │  │
│ │   └────────────────────────────────────────────────────────────────────────────────────────────────────────┘  │  │
│ │                                                                                                                │  │
│ │   Kafka: Publishes trace/reporting events (async, non-blocking)                                                │  │
│ │                                                                                                                │  │
│ └────────────────────────────────────────────────────────────────────────────────────────────────────────────────┘  │
│                                     │                                                                                │
│                                     │ REST: Sourcing Request                                                         │
│                                     ▼                                                                                │
│ ┌────────────────────────────────────────────────────────────────────────────────────────────────────────────────┐  │
│ │                                                                                                                │  │
│ │   LAYER 2: COMPUTATION LAYER (SOURCING ENGINE)                                                                 │  │
│ │   ════════════════════════════════════════════                                                                 │  │
│ │                                                                                                                │  │
│ │   ┌────────────────────────────────────────┐         ┌────────────────────────────────────────┐               │  │
│ │   │            MCSE-LITE                   │         │           PROMISE-DATE                 │               │  │
│ │   │     (Sourcing Computation Engine)      │         │      (Date Calculation Service)        │               │  │
│ │   │            ─────────                   │  REST   │           ────────────                 │               │  │
│ │   │                                        │◄───────►│                                        │               │  │
│ │   │  PURPOSE:                              │         │  PURPOSE:                              │               │  │
│ │   │  The brain of sourcing. Calculates     │         │  Specialized date calculations with    │               │  │
│ │   │  which fulfillment center should       │         │  complex buffer logic (weather,        │               │  │
│ │   │  ship an item and when it will arrive. │         │  holidays, carrier reliability).       │               │  │
│ │   │                                        │         │                                        │               │  │
│ │   │  COMPUTATION FLOW:                     │         │  COMPUTATION FLOW:                     │               │  │
│ │   │  ┌──────────────────────────────────┐  │         │  ┌──────────────────────────────────┐  │               │  │
│ │   │  │ 1. Fetch inventory (NLI call)   │  │         │  │ 1. Get transit time from cache   │  │               │  │
│ │   │  │ 2. Get distributor configs      │  │         │  │ 2. Apply weather buffers         │  │               │  │
│ │   │  │ 3. Get carrier configs          │  │         │  │ 3. Apply holiday adjustments     │  │               │  │
│ │   │  │ 4. Check FCAP (FC capacity)     │  │         │  │ 4. Apply carrier reliability     │  │               │  │
│ │   │  │ 5. Check CCAP (carrier capacity)│  │         │  │ 5. Calculate final EDD           │  │               │  │
│ │   │  │ 6. Calculate ESD (ship date)    │  │         │  └──────────────────────────────────┘  │               │  │
│ │   │  │ 7. Calculate EDD (delivery date)│  │         │                                        │               │  │
│ │   │  │ 8. Rank solutions by speed/cost │  │         │  Tech: Reactive (WebFlux), Caching     │               │  │
│ │   │  │ 9. Select optimal ship node     │  │         │                                        │               │  │
│ │   │  └──────────────────────────────────┘  │         └────────────────────────────────────────┘               │  │
│ │   │                                        │                                                                  │  │
│ │   │  Tech: Spring Boot, Reactive Streams   │                                                                  │  │
│ │   │                                        │                                                                  │  │
│ │   └───────────────┬────────────────────────┘                                                                  │  │
│ │                   │                                                                                            │  │
│ │                   │ Calls external services for real-time data                                                 │  │
│ │                   ▼                                                                                            │  │
│ │   ┌─────────────────────────────────────────────────────────────────────────────────────────────────────┐    │  │
│ │   │  EXTERNAL SERVICES (Called synchronously for real-time data)                                        │    │  │
│ │   │  ───────────────────────────────────────────────────────────────                                    │    │  │
│ │   │  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐   │    │  │
│ │   │  │ Inventory   │ │    Slot     │ │   Master    │ │ Reservation │ │   Pricing   │ │  Eligibility│   │    │  │
│ │   │  │  Service    │ │  Service    │ │    Data     │ │   Service   │ │   Service   │ │   Service   │   │    │  │
│ │   │  │   (NLI)     │ │  (CASPR)    │ │   (MDM)     │ │  (Mobius)   │ │             │ │   (LIMO)    │   │    │  │
│ │   │  │             │ │             │ │             │ │             │ │             │ │             │   │    │  │
│ │   │  │ "Is item    │ │ "Any slots  │ │ "Store      │ │ "Reserve    │ │ "Shipping   │ │ "Is item    │   │    │  │
│ │   │  │  in stock?" │ │  available?"│ │  details?"  │ │  capacity?" │ │  cost?"     │ │  eligible?" │   │    │  │
│ │   │  └─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘   │    │  │
│ │   └─────────────────────────────────────────────────────────────────────────────────────────────────────┘    │  │
│ │                                                                                                                │  │
│ └────────────────────────────────────────────────────────────────────────────────────────────────────────────────┘  │
│                                     │                                                                                │
│                                     │ Reads pre-computed/cached data                                                 │
│                                     ▼                                                                                │
│ ┌────────────────────────────────────────────────────────────────────────────────────────────────────────────────┐  │
│ │                                                                                                                │  │
│ │   LAYER 3: DATA SERVING LAYER                                                                                  │  │
│ │   ═══════════════════════════                                                                                  │  │
│ │                                                                                                                │  │
│ │   ┌────────────────────────────────────────┐         ┌────────────────────────────────────────┐               │  │
│ │   │      MCSE-LITE (Cache Services)        │         │      ITEM-SPEED-ELIGIBILITY            │               │  │
│ │   │            ─────────                   │         │      ───────────────────────           │               │  │
│ │   │                                        │         │                                        │               │  │
│ │   │  PURPOSE:                              │         │  PURPOSE:                              │               │  │
│ │   │  Serve pre-computed data with sub-ms   │         │  Determines item delivery speed        │               │  │
│ │   │  latency. Computation layer queries    │         │  eligibility (same-day, next-day).     │               │  │
│ │   │  this for transit times, distributors, │         │  Complex rules pre-computed for        │               │  │
│ │   │  carriers, capacity status.            │         │  fast lookups.                         │               │  │
│ │   │                                        │         │                                        │               │  │
│ │   │  DATA SERVED:                          │         │  DATA SERVED:                          │               │  │
│ │   │  • Transit times (source → dest ZIP)   │         │  • ZIP code eligibility                │               │  │
│ │   │  • Distributor configurations          │         │  • Item type eligibility               │               │  │
│ │   │  • Carrier configurations              │         │  • Carrier coverage eligibility        │               │  │
│ │   │  • Capacity flip status                │         │  • Offer lead times                    │               │  │
│ │   │  • Pre-computed EDD dates              │         │                                        │               │  │
│ │   │                                        │         │                                        │               │  │
│ │   │  Tech: Hollow Cache, Distributed Cache │         │  Tech: Distributed Cache, REST API     │               │  │
│ │   │                                        │         │                                        │               │  │
│ │   └───────────────┬────────────────────────┘         └───────────────┬────────────────────────┘               │  │
│ │                   │                                                  │                                        │  │
│ │                   │ Reads from                                       │ Reads from                             │  │
│ │                   ▼                                                  ▼                                        │  │
│ │   ┌─────────────────────────────────────────────────────────────────────────────────────────────────────┐    │  │
│ │   │                              CACHING HIERARCHY                                                      │    │  │
│ │   │                              ─────────────────                                                      │    │  │
│ │   │                                                                                                     │    │  │
│ │   │   ┌─────────────────────┐    ┌─────────────────────┐    ┌─────────────────────┐                    │    │  │
│ │   │   │   IN-MEMORY CACHE   │    │   DISTRIBUTED       │    │   CASSANDRA         │                    │    │  │
│ │   │   │   (Hollow/Caffeine) │    │   CACHE             │    │   (Read Path)       │                    │    │  │
│ │   │   │                     │    │                     │    │                     │                    │    │  │
│ │   │   │   Latency: ~1μs     │    │   Latency: ~1-5ms   │    │   Latency: ~10-50ms │                    │    │  │
│ │   │   │                     │    │                     │    │                     │                    │    │  │
│ │   │   │   • Hot data        │───►│   • Shared state    │───►│   • Persistent      │                    │    │  │
│ │   │   │   • Per-pod         │miss│   • Cross-pod       │miss│   • Source of truth │                    │    │  │
│ │   │   │   • EDD cache       │    │   • Capacity flips  │    │   • Transit times   │                    │    │  │
│ │   │   │   • TNT cache       │    │   • Eligibility     │    │   • Distributors    │                    │    │  │
│ │   │   └─────────────────────┘    └─────────────────────┘    └─────────────────────┘                    │    │  │
│ │   │                                                                                                     │    │  │
│ │   └─────────────────────────────────────────────────────────────────────────────────────────────────────┘    │  │
│ │                                                                                                                │  │
│ └────────────────────────────────────────────────────────────────────────────────────────────────────────────────┘  │
│                                     ▲                                                                                │
│                                     │ Data written by ingestion layer                                                │
│                                     │                                                                                │
│ ┌────────────────────────────────────────────────────────────────────────────────────────────────────────────────┐  │
│ │                                                                                                                │  │
│ │   LAYER 4: DATA INGESTION LAYER                                                                                │  │
│ │   ═════════════════════════════                                                                                │  │
│ │                                                                                                                │  │
│ │   ┌────────────────────────────────────────────────────────────────────────────────────────────────────────┐  │  │
│ │   │                                                                                                        │  │  │
│ │   │                              MCSE-DATA-INGESTION                                                       │  │  │
│ │   │                              ───────────────────                                                       │  │  │
│ │   │                                                                                                        │  │  │
│ │   │  PURPOSE:                                                                                              │  │  │
│ │   │  Central write pipeline for all sourcing data. Decouples upstream systems from database schema.       │  │  │
│ │   │  Single place for validation, transformation, retry logic. If this didn't exist, every upstream       │  │  │
│ │   │  system would need to know Cassandra schema and handle write failures independently.                  │  │  │
│ │   │                                                                                                        │  │  │
│ │   │   ┌─────────────────────────────────────┐    ┌─────────────────────────────────────┐                  │  │  │
│ │   │   │     LISTENER MODULE                 │    │     BUSINESS MODULE                 │                  │  │  │
│ │   │   │     ───────────────                 │    │     ───────────────                 │                  │  │  │
│ │   │   │                                     │    │                                     │                  │  │  │
│ │   │   │  10+ Kafka Consumers:               │    │  40+ Event Handlers:                │                  │  │  │
│ │   │   │  • DCC Events (distributors)        │───►│  • Validate incoming events         │                  │  │  │
│ │   │   │  • Capacity Events                  │    │  • Transform to DB entities         │                  │  │  │
│ │   │   │  • Item/Offer Events                │    │  • Apply business rules             │                  │  │  │
│ │   │   │  • Transit Time Events              │    │  • Persist to Cassandra             │                  │  │  │
│ │   │   │  • Slot Events                      │    │  • Publish downstream events        │                  │  │  │
│ │   │   │  • Store Events                     │    │                                     │                  │  │  │
│ │   │   │  • Leadtime Events                  │    │  EventManager routes events         │                  │  │  │
│ │   │   │                                     │    │  based on (tenant, eventType)       │                  │  │  │
│ │   │   │                                     │    │                                     │                  │  │  │
│ │   │   └─────────────────────────────────────┘    └──────────────────┬──────────────────┘                  │  │  │
│ │   │                                                                 │                                     │  │  │
│ │   │   WHY THIS SERVICE IS NEEDED:                                   │                                     │  │  │
│ │   │   ───────────────────────────                                   │                                     │  │  │
│ │   │                                                                 │                                     │  │  │
│ │   │   1. DECOUPLING: Upstream systems (DC-Square, Capacity Engine)  │                                     │  │  │
│ │   │      don't need to know about Cassandra schema or write logic.  │                                     │  │  │
│ │   │      They just publish events.                                  │                                     │  │  │
│ │   │                                                                 │                                     │  │  │
│ │   │   2. SINGLE RESPONSIBILITY: One service owns all data writes.   │                                     │  │  │
│ │   │      Schema changes, retry logic, validation — all in one place.│                                     │  │  │
│ │   │                                                                 │                                     │  │  │
│ │   │   3. SCALABILITY: Can scale consumers independently based on    │                                     │  │  │
│ │   │      event volume. High-volume topics get more consumers.       │                                     │  │  │
│ │   │                                                                 │                                     │  │  │
│ │   │   4. RELIABILITY: Kafka provides durability. If ingestion is    │                                     │  │  │
│ │   │      down, events queue up. No data loss.                       │                                     │  │  │
│ │   │                                                                 ▼                                     │  │  │
│ │   │                                              ┌─────────────────────────────────────┐                  │  │  │
│ │   │                                              │        CASSANDRA                    │                  │  │  │
│ │   │                                              │        (Write Path)                 │                  │  │  │
│ │   │                                              │                                     │                  │  │  │
│ │   │                                              │  • High-throughput writes           │                  │  │  │
│ │   │                                              │  • Batch inserts                    │                  │  │  │
│ │   │                                              │  • TTL-based expiry                 │                  │  │  │
│ │   │                                              │  • Retry with backoff               │                  │  │  │
│ │   │                                              └─────────────────────────────────────┘                  │  │  │
│ │   │                                                                                                        │  │  │
│ │   └────────────────────────────────────────────────────────────────────────────────────────────────────────┘  │  │
│ │                                                                                                                │  │
│ └────────────────────────────────────────────────────────────────────────────────────────────────────────────────┘  │
│                                     ▲                                                                                │
│                                     │ Kafka Events                                                                   │
│                                     │                                                                                │
│ ┌────────────────────────────────────────────────────────────────────────────────────────────────────────────────┐  │
│ │                                                                                                                │  │
│ │   LAYER 5: CAPACITY & CONFIGURATION LAYER                                                                      │  │
│ │   ═══════════════════════════════════════                                                                      │  │
│ │                                                                                                                │  │
│ │   ┌──────────────────────────────┐  ┌──────────────────────────────┐  ┌──────────────────────────────┐        │  │
│ │   │      CAPACITY ENGINE         │  │   FULFILLMENT CAPACITY       │  │        DC-SQUARE             │        │  │
│ │   │      ───────────────         │  │   ────────────────────       │  │        ─────────             │        │  │
│ │   │                              │  │                              │  │                              │        │  │
│ │   │  PURPOSE:                    │  │  PURPOSE:                    │  │  PURPOSE:                    │        │  │
│ │   │  Aggregates capacity data    │  │  Manages real-time FC        │  │  Configuration management    │        │  │
│ │   │  from order/shipment events. │  │  capacity. Publishes "flip"  │  │  for fulfillment network.    │        │  │
│ │   │  Calculates available        │  │  events when a node is full  │  │  Source of truth for         │        │  │
│ │   │  capacity per fulfillment    │  │  so promise layer stops      │  │  distributors, carriers,     │        │  │
│ │   │  node.                       │  │  routing orders there.       │  │  coverage areas.             │        │  │
│ │   │                              │  │                              │  │                              │        │  │
│ │   │  WHY NEEDED:                 │  │  WHY NEEDED:                 │  │  WHY NEEDED:                 │        │  │
│ │   │  Fulfillment centers have    │  │  Real-time capacity tracking │  │  Central configuration for   │        │  │
│ │   │  limited capacity. Must      │  │  prevents over-promising.    │  │  the fulfillment network.    │        │  │
│ │   │  track orders, shipments,    │  │  When a FC is full, flip     │  │  Which distributors exist,   │        │  │
│ │   │  and available slots to      │  │  capacity status so promise  │  │  which carriers they use,    │        │  │
│ │   │  avoid over-promising.       │  │  layer stops routing there.  │  │  which areas they cover.     │        │  │
│ │   │                              │  │                              │  │                              │        │  │
│ │   │  INPUTS:                     │  │  INPUTS:                     │  │  INPUTS:                     │        │  │
│ │   │  • Order events              │  │  • Reservation events        │  │  • Admin UI                  │        │  │
│ │   │  • Shipment events           │  │  • Cancellation events       │  │  • Bulk uploads              │        │  │
│ │   │  • Fulfillment events        │  │  • Ship confirmation events  │  │  • Partner integrations      │        │  │
│ │   │                              │  │                              │  │                              │        │  │
│ │   │  OUTPUTS:                    │  │  OUTPUTS:                    │  │  OUTPUTS:                    │        │  │
│ │   │  • Capacity status updates   │  │  • Capacity flip events      │  │  • DCC events (distributor,  │        │  │
│ │   │  • Reporting events          │  │  • Consumption updates       │  │    carrier, coverage)        │        │  │
│ │   │                              │  │                              │  │                              │        │  │
│ │   │  Tech: Cosmos DB, Kafka      │  │  Tech: Cosmos DB, Cassandra  │  │  Tech: SQL Server, Kafka     │        │  │
│ │   │                              │  │        Kafka, Redis          │  │                              │        │  │
│ │   └──────────────┬───────────────┘  └──────────────┬───────────────┘  └──────────────┬───────────────┘        │  │
│ │                  │                                 │                                 │                        │  │
│ │                  │ Kafka                           │ Kafka                           │ Kafka                  │  │
│ │                  │ (capacity events)               │ (flip events)                   │ (DCC events)           │  │
│ │                  │                                 │                                 │                        │  │
│ │                  └─────────────────────────────────┴─────────────────────────────────┘                        │  │
│ │                                                    │                                                          │  │
│ │                                                    ▼                                                          │  │
│ │                                    ┌───────────────────────────────────┐                                      │  │
│ │                                    │         KAFKA CLUSTER             │                                      │  │
│ │                                    │         ─────────────             │                                      │  │
│ │                                    │                                   │                                      │  │
│ │                                    │  Event Categories:                │                                      │  │
│ │                                    │  • Capacity events                │                                      │  │
│ │                                    │  • Flip events                    │                                      │  │
│ │                                    │  • Configuration events           │                                      │  │
│ │                                    │  • Order/Shipment events          │                                      │  │
│ │                                    │  • Trace/Reporting events         │                                      │  │
│ │                                    │                                   │                                      │  │
│ │                                    └───────────────────────────────────┘                                      │  │
│ │                                                    │                                                          │  │
│ │                                                    │ Consumed by MCSE-Data-Ingestion (Layer 4)                │  │
│ │                                                    ▼                                                          │  │
│ │                                                                                                                │  │
│ │   ┌──────────────────────────────┐  ┌──────────────────────────────┐                                          │  │
│ │   │      DC-SQUARE-JOBS          │  │   CA-CAPACITY (FCAP/CCAP)    │                                          │  │
│ │   │      ──────────────          │  │   ───────────────────────    │                                          │  │
│ │   │                              │  │                              │                                          │  │
│ │   │  PURPOSE:                    │  │  PURPOSE:                    │                                          │  │
│ │   │  Batch processing for heavy  │  │  Regional capacity mgmt for  │                                          │  │
│ │   │  operations. Bulk uploads,   │  │  Canada. Different carriers, │                                          │  │
│ │   │  data migrations, periodic   │  │  regulations, cross-border   │                                          │  │
│ │   │  syncs.                      │  │  rules.                      │                                          │  │
│ │   │                              │  │                              │                                          │  │
│ │   │  WHY NEEDED:                 │  │  WHY NEEDED:                 │                                          │  │
│ │   │  Some operations are too     │  │  Different regions have      │                                          │  │
│ │   │  heavy for real-time. Bulk   │  │  different capacity rules,   │                                          │  │
│ │   │  uploads, data migrations,   │  │  carriers, and regulations.  │                                          │  │
│ │   │  cleanup — run as scheduled  │  │  Separate services handle    │                                          │  │
│ │   │  batch jobs.                 │  │  regional complexity.        │                                          │  │
│ │   │                              │  │                              │                                          │  │
│ │   │  Tech: Spring Batch, SQL     │  │  Tech: Kafka, Distributed    │                                          │  │
│ │   │                              │  │        Cache                 │                                          │  │
│ │   └──────────────────────────────┘  └──────────────────────────────┘                                          │  │
│ │                                                                                                                │  │
│ └────────────────────────────────────────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                                                      │
└──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

# Part 3: Data Flow Patterns

## 3.1 The Two Data Paths

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         TWO FUNDAMENTAL DATA PATHS                          │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│                                                                             │
│   PATH 1: REAL-TIME READ PATH (Customer Request)                            │
│   ══════════════════════════════════════════════                            │
│                                                                             │
│   ┌──────────┐    ┌──────────────┐    ┌───────────────┐    ┌─────────────┐ │
│   │ Customer │───►│   Unified    │───►│   MCSE-Lite   │───►│   Cache/    │ │
│   │          │    │   Promise    │    │ (Computation) │    │  Cassandra  │ │
│   └──────────┘    └──────────────┘    └───────────────┘    └─────────────┘ │
│        ▲                                     │                              │
│        │                                     │                              │
│        │                                     ▼                              │
│        │                            ┌───────────────┐                       │
│        │                            │   External    │                       │
│        │                            │   Services    │                       │
│        │                            │ (NLI, CASPR)  │                       │
│        │                            └───────────────┘                       │
│        │                                     │                              │
│        └─────────── "Get it by Friday" ◄─────┘                              │
│                                                                             │
│   Characteristics:                                                          │
│   • Synchronous REST calls                                                  │
│   • Sub-100ms latency requirement                                           │
│   • Computation happens in MCSE-Lite                                        │
│   • Reads from cache first, database second                                 │
│   • External service calls for real-time data (inventory, slots)            │
│                                                                             │
│                                                                             │
│   PATH 2: ASYNC WRITE PATH (Data Updates)                                   │
│   ═══════════════════════════════════════                                   │
│                                                                             │
│   ┌──────────────┐                                                          │
│   │  DC-Square   │──┐                                                       │
│   └──────────────┘  │                                                       │
│                     │     ┌─────────┐     ┌─────────────────┐     ┌───────┐│
│   ┌──────────────┐  ├────►│  Kafka  │────►│ MCSE-Data-      │────►│Cassan-││
│   │  Capacity    │──┤     │         │     │ Ingestion       │     │ dra   ││
│   │  Engine      │  │     └─────────┘     └─────────────────┘     └───┬───┘│
│   └──────────────┘  │                                                 │    │
│                     │                                                 │    │
│   ┌──────────────┐  │                                                 │    │
│   │ Fulfillment  │──┘                                                 │    │
│   │ Capacity     │                                                    │    │
│   └──────────────┘                                                    │    │
│                                                                       ▼    │
│                                                              ┌─────────────┐│
│                                                              │  MCSE-Lite  ││
│                                                              │  (Reads     ││
│                                                              │   fresh     ││
│                                                              │   data)     ││
│                                                              └─────────────┘│
│                                                                             │
│   Characteristics:                                                          │
│   • Asynchronous Kafka events                                               │
│   • Eventual consistency (seconds, not minutes)                             │
│   • High-throughput writes                                                  │
│   • Decoupled producers and consumers                                       │
│   • Single ingestion service owns all writes                                │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

## 3.2 Why Separate Read and Write Paths?

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    CQRS PATTERN: COMMAND QUERY RESPONSIBILITY SEGREGATION   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   PROBLEM: Read and Write have DIFFERENT requirements                       │
│   ─────────────────────────────────────────────────────                     │
│                                                                             │
│   ┌─────────────────────────────┐    ┌─────────────────────────────┐       │
│   │         READS               │    │         WRITES              │       │
│   ├─────────────────────────────┤    ├─────────────────────────────┤       │
│   │ • Must be FAST (<100ms)     │    │ • Can be SLOW (seconds OK)  │       │
│   │ • Millions per minute       │    │ • Thousands per minute      │       │
│   │ • Customer is waiting       │    │ • Background processing     │       │
│   │ • Read from cache/memory    │    │ • Write to durable storage  │       │
│   │ • Stale data OK (seconds)   │    │ • Must not lose data        │       │
│   │ • Computation required      │    │ • Transformation required   │       │
│   └─────────────────────────────┘    └─────────────────────────────┘       │
│                                                                             │
│   SOLUTION: Separate the paths                                              │
│   ────────────────────────────                                              │
│                                                                             │
│   ┌─────────────────────────────────────────────────────────────────────┐  │
│   │                                                                     │  │
│   │   WRITE PATH                           READ PATH                    │  │
│   │   ──────────                           ─────────                    │  │
│   │                                                                     │  │
│   │   DC-Square                            Customer Request             │  │
│   │       │                                      │                      │  │
│   │       ▼                                      ▼                      │  │
│   │   ┌───────┐                            ┌───────────┐                │  │
│   │   │ Kafka │                            │ Unified   │                │  │
│   │   └───┬───┘                            │ Promise   │                │  │
│   │       │                                └─────┬─────┘                │  │
│   │       ▼                                      │                      │  │
│   │   ┌───────────┐                              ▼                      │  │
│   │   │ Ingestion │                        ┌───────────┐                │  │
│   │   │ Service   │                        │ MCSE-Lite │                │  │
│   │   └─────┬─────┘                        │(Compute + │                │  │
│   │         │                              │ Serve)    │                │  │
│   │         ▼                              └─────┬─────┘                │  │
│   │   ┌───────────┐                              │                      │  │
│   │   │ Cassandra │ ─────────────────────────────┘                      │  │
│   │   │ (Write)   │    Data available for reads                         │  │
│   │   └───────────┘                                                     │  │
│   │                                                                     │  │
│   │   Optimized for:                       Optimized for:               │  │
│   │   • Durability                         • Speed                      │  │
│   │   • Throughput                         • Low latency                │  │
│   │   • Consistency                        • High concurrency           │  │
│   │   • Transformation                     • Computation                │  │
│   │                                                                     │  │
│   └─────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

# Part 4: Computation Layer Deep Dive

## 4.1 MCSE-Lite: The Sourcing Brain

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    COMPUTATION LAYER (MCSE-LITE) DEEP DIVE                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ENTRY POINT: SourcingServiceImpl.sourcingService()                         │
│  ───────────────────────────────────────────────────                        │
│                              │                                              │
│                              ▼                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  DateServiceManagerImpl.getDate()                                   │   │
│  │  ─────────────────────────────────                                   │   │
│  │  The CORE computation engine that orchestrates the entire flow      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                              │                                              │
│         ┌────────────────────┼────────────────────┐                         │
│         ▼                    ▼                    ▼                         │
│  ┌─────────────┐      ┌─────────────┐      ┌─────────────┐                 │
│  │ STEP 1:     │      │ STEP 2:     │      │ STEP 3:     │                 │
│  │ Offer Data  │      │ DAL Entity  │      │ Inventory   │                 │
│  │ Generation  │      │ Creation    │      │ Fetch       │                 │
│  │             │      │             │      │             │                 │
│  │ Fetch item  │      │ Create      │      │ Call NLI    │                 │
│  │ details,    │      │ sourcing    │      │ service for │                 │
│  │ offer data  │      │ context,    │      │ availability│                 │
│  │ from cache  │      │ distributors│      │ per node    │                 │
│  └─────────────┘      └─────────────┘      └─────────────┘                 │
│                              │                                              │
│                              ▼                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  STEP 4: FulfillmentPlanner.calculatePreciseDeliveryDate()          │   │
│  │  ─────────────────────────────────────────────────────────          │   │
│  │                                                                      │   │
│  │  For each eligible (offer, distributor, carrier) combination:       │   │
│  │                                                                      │   │
│  │  ┌─────────────────────────────────────────────────────────────┐    │   │
│  │  │  4a. Calculate Order Processing Date (OPD)                  │    │   │
│  │  │      • Apply timezone adjustments                           │    │   │
│  │  │      • Check working calendar (is warehouse open?)          │    │   │
│  │  │      • Apply cutoff time rules (order by 2pm for same-day)  │    │   │
│  │  └─────────────────────────────────────────────────────────────┘    │   │
│  │                              │                                       │   │
│  │                              ▼                                       │   │
│  │  ┌─────────────────────────────────────────────────────────────┐    │   │
│  │  │  4b. Calculate Ship Ready Date                              │    │   │
│  │  │      • OPD + Lead Time + Processing Time                    │    │   │
│  │  │      • Apply SFF (Seller Fulfilled) padding if applicable   │    │   │
│  │  └─────────────────────────────────────────────────────────────┘    │   │
│  │                              │                                       │   │
│  │                              ▼                                       │   │
│  │  ┌─────────────────────────────────────────────────────────────┐    │   │
│  │  │  4c. Calculate Estimated Ship Date (ESD)                    │    │   │
│  │  │      • Find first working day when:                         │    │   │
│  │  │        - Warehouse is operational                           │    │   │
│  │  │        - Carrier pickup is available                        │    │   │
│  │  │      • Check FCAP (Fulfillment Capacity) constraints        │    │   │
│  │  │      • Move ESD forward if capacity unavailable             │    │   │
│  │  └─────────────────────────────────────────────────────────────┘    │   │
│  │                              │                                       │   │
│  │                              ▼                                       │   │
│  │  ┌─────────────────────────────────────────────────────────────┐    │   │
│  │  │  4d. Check Carrier Capacity (CCAP)                          │    │   │
│  │  │      • Verify carrier has capacity on ESD                   │    │   │
│  │  │      • Move ESD forward if carrier capacity unavailable     │    │   │
│  │  └─────────────────────────────────────────────────────────────┘    │   │
│  │                              │                                       │   │
│  │                              ▼                                       │   │
│  │  ┌─────────────────────────────────────────────────────────────┐    │   │
│  │  │  4e. Calculate Estimated Delivery Date (EDD)                │    │   │
│  │  │      • ESD + Transit Time (TNT)                             │    │   │
│  │  │      • Apply weather buffers (storms, snow delays)          │    │   │
│  │  │      • Apply holiday adjustments (no delivery on holidays)  │    │   │
│  │  │      • Apply predictive TNT if enabled (ML-based)           │    │   │
│  │  └─────────────────────────────────────────────────────────────┘    │   │
│  │                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                              │                                              │
│                              ▼                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  STEP 5: Solution Selection & Consolidation                         │   │
│  │  ──────────────────────────────────────────                          │   │
│  │                                                                      │   │
│  │  • Rank solutions by EDD (fastest first)                            │   │
│  │  • Apply cost optimization (minimize shipping cost)                 │   │
│  │  • Consolidate multi-item orders to minimize shipments              │   │
│  │  • Select best solution per SLA tier (standard, express, same-day)  │   │
│  │                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                              │                                              │
│                              ▼                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  STEP 6: Response Generation                                        │   │
│  │  ───────────────────────────                                         │   │
│  │                                                                      │   │
│  │  • Build response with selected solutions                           │   │
│  │  • Include ESD, EDD, carrier, ship node details                     │   │
│  │  • Handle reservations if backend call                              │   │
│  │  • Publish trace events for monitoring                              │   │
│  │                                                                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

## 4.2 Key Insight: Computation vs Data Serving

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    COMPUTATION vs DATA SERVING                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  These are DIFFERENT responsibilities, even if in the same service:        │
│                                                                             │
│  ┌─────────────────────────────────┐  ┌─────────────────────────────────┐  │
│  │       DATA SERVING              │  │       COMPUTATION               │  │
│  ├─────────────────────────────────┤  ├─────────────────────────────────┤  │
│  │                                 │  │                                 │  │
│  │  "What is the transit time     │  │  "Given inventory at FC-123,    │  │
│  │   from FC-123 to ZIP 94025?"   │  │   capacity available, carrier   │  │
│  │                                 │  │   pickup schedule, and 3-day   │  │
│  │  Answer: "3 days"               │  │   transit time, what is the    │  │
│  │                                 │  │   EDD?"                        │  │
│  │  Source: Cache lookup           │  │                                 │  │
│  │                                 │  │  Answer: "Friday, Dec 15"       │  │
│  │                                 │  │                                 │  │
│  │                                 │  │  Source: Complex calculation    │  │
│  │                                 │  │  using multiple data points     │  │
│  │                                 │  │                                 │  │
│  └─────────────────────────────────┘  └─────────────────────────────────┘  │
│                                                                             │
│  MCSE-Lite does BOTH:                                                       │
│  • Serves cached data (transit times, distributors, carriers)              │
│  • Computes delivery dates using that data + external service calls        │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

# Part 5: Service-by-Service Breakdown

## 5.1 Why Each Service Exists

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    SERVICE JUSTIFICATION                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  SERVICE              │ WHY IT EXISTS                                       │
│  ─────────────────────┼─────────────────────────────────────────────────────│
│                       │                                                     │
│  UNIFIED PROMISE      │ Single entry point for all promise requests.        │
│                       │ Without this, every client (web, mobile, API)       │
│                       │ would need to orchestrate 8+ service calls.         │
│                       │ Centralizes routing, caching, fallbacks.            │
│                       │                                                     │
│  ─────────────────────┼─────────────────────────────────────────────────────│
│                       │                                                     │
│  MCSE-LITE            │ The sourcing brain. Computes delivery promises      │
│                       │ by aggregating inventory, capacity, transit times,  │
│                       │ and carrier data. Also serves cached data for       │
│                       │ sub-millisecond lookups. Handles the complex        │
│                       │ ESD/EDD calculation logic.                          │
│                       │                                                     │
│  ─────────────────────┼─────────────────────────────────────────────────────│
│                       │                                                     │
│  PROMISE-DATE         │ Specialized date calculations with complex buffer   │
│                       │ logic. Weather delays, holiday adjustments,         │
│                       │ carrier reliability factors. Separated because      │
│                       │ date math is complex and reusable.                  │
│                       │                                                     │
│  ─────────────────────┼─────────────────────────────────────────────────────│
│                       │                                                     │
│  MCSE-DATA-INGESTION  │ Central write pipeline. All data changes flow       │
│  (Listener + Biz)     │ through here. Decouples upstream systems from       │
│                       │ database schema. Single place for validation,       │
│                       │ transformation, retry logic. Owns all writes.       │
│                       │                                                     │
│  ─────────────────────┼─────────────────────────────────────────────────────│
│                       │                                                     │
│  CAPACITY ENGINE      │ Tracks fulfillment center capacity in real-time.    │
│                       │ Processes order events, calculates remaining        │
│                       │ capacity. Without this, we'd over-promise and       │
│                       │ under-deliver.                                      │
│                       │                                                     │
│  ─────────────────────┼─────────────────────────────────────────────────────│
│                       │                                                     │
│  FULFILLMENT CAPACITY │ Manages capacity at the fulfillment node level.     │
│                       │ Publishes "capacity flip" events when a node        │
│                       │ is full. Promise layer reads these to avoid         │
│                       │ routing orders to full nodes.                       │
│                       │                                                     │
│  ─────────────────────┼─────────────────────────────────────────────────────│
│                       │                                                     │
│  DC-SQUARE            │ Configuration management for the fulfillment        │
│                       │ network. Defines distributors, carriers,            │
│                       │ coverage areas. Admin UI for operations teams.      │
│                       │ Source of truth for network topology.               │
│                       │                                                     │
│  ─────────────────────┼─────────────────────────────────────────────────────│
│                       │                                                     │
│  DC-SQUARE-JOBS       │ Batch processing for heavy operations.              │
│                       │ Bulk uploads, data migrations, periodic syncs.      │
│                       │ Keeps DC-Square responsive by offloading            │
│                       │ long-running tasks.                                 │
│                       │                                                     │
│  ─────────────────────┼─────────────────────────────────────────────────────│
│                       │                                                     │
│  ITEM-SPEED-          │ Determines if an item qualifies for express         │
│  ELIGIBILITY          │ delivery (same-day, next-day). Complex rules        │
│                       │ based on item type, location, carrier coverage.     │
│                       │ Pre-computed for fast lookups.                      │
│                       │                                                     │
│  ─────────────────────┼─────────────────────────────────────────────────────│
│                       │                                                     │
│  CA-CAPACITY          │ Regional capacity management for Canada.            │
│  (FCAP/CCAP)          │ Different carriers, regulations, cross-border       │
│                       │ rules. Isolated to avoid polluting US logic.        │
│                       │                                                     │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

# Part 6: Technology Stack Summary

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    TECHNOLOGY STACK BY LAYER                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  LAYER 1: ORCHESTRATION                                                     │
│  ──────────────────────                                                     │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  • REST APIs (Spring WebFlux, Akka HTTP)                            │   │
│  │  • Circuit Breakers (Resilience4j) — prevent cascade failures       │   │
│  │  • Akka Actors — concurrent request handling                        │   │
│  │  • In-memory caching (Caffeine) — hot data                          │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  LAYER 2: COMPUTATION                                                       │
│  ────────────────────                                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  • Spring Boot with Reactive Streams                                │   │
│  │  • Complex date/calendar calculations                               │   │
│  │  • External service calls (NLI, CASPR, MDM)                         │   │
│  │  • Multi-tier caching for data lookups                              │   │
│  │  • Solution ranking and optimization algorithms                     │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  LAYER 3: DATA SERVING                                                      │
│  ─────────────────────                                                      │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  • Cassandra (read path) — high-throughput reads                    │   │
│  │  • Hollow Cache — in-memory, pre-computed data                      │   │
│  │  • Distributed Cache — shared state across pods                     │   │
│  │  • Reactive drivers — non-blocking I/O                              │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  LAYER 4: DATA INGESTION                                                    │
│  ───────────────────────                                                    │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  • Kafka Consumers — event ingestion                                │   │
│  │  • Spring Application Events — internal routing                     │   │
│  │  • Cassandra (write path) — high-throughput writes                  │   │
│  │  • Retry logic — handle transient failures                          │   │
│  │  • EventManager — routes events to 40+ handlers                     │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  LAYER 5: CAPACITY & CONFIGURATION                                          │
│  ─────────────────────────────────                                          │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  • Cosmos DB — hierarchical capacity data                           │   │
│  │  • SQL Server — relational configuration data                       │   │
│  │  • Kafka Producers — event publishing                               │   │
│  │  • Redis — distributed caching with cross-pod sync                  │   │
│  │  • Batch Jobs (Spring Batch) — scheduled processing                 │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

# Part 7: Request Flow Example

## 7.1 End-to-End: "When Will My Item Arrive?"

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    END-TO-END REQUEST FLOW                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  STEP 1: Customer Request                                                   │
│  ────────────────────────                                                   │
│                                                                             │
│  Customer views item page                                                   │
│       │                                                                     │
│       │  POST /promise                                                      │
│       │  {                                                                  │
│       │    "itemId": "12345",                                               │
│       │    "zipCode": "94025",                                              │
│       │    "quantity": 1                                                    │
│       │  }                                                                  │
│       ▼                                                                     │
│                                                                             │
│  STEP 2: Unified Promise (Orchestration Layer)                              │
│  ─────────────────────────────────────────────                              │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  Unified Promise receives request                                   │   │
│  │       │                                                             │   │
│  │       ├──► Check cache for recent identical request                 │   │
│  │       │    (Cache HIT? Return cached response)                      │   │
│  │       │                                                             │   │
│  │       ├──► Determine routing: 1P item → MCSE-Lite                   │   │
│  │       │                       3P item → GBAS (external)             │   │
│  │       │                                                             │   │
│  │       └──► Route to MCSE-Lite for sourcing computation              │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│       │                                                                     │
│       ▼                                                                     │
│                                                                             │
│  STEP 3: MCSE-Lite (Computation Layer)                                      │
│  ─────────────────────────────────────                                      │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  MCSE-Lite receives sourcing request                                │   │
│  │       │                                                             │   │
│  │       ├──► Fetch item/offer data from cache                         │   │
│  │       │                                                             │   │
│  │       ├──► Call NLI (Inventory Service)                             │   │
│  │       │    "Which warehouses have this item in stock?"              │   │
│  │       │    Response: [FC-123: 50 units, FC-456: 20 units]           │   │
│  │       │                                                             │   │
│  │       ├──► Get distributor/carrier configs from cache               │   │
│  │       │                                                             │   │
│  │       ├──► For each (warehouse, carrier) combination:               │   │
│  │       │    │                                                        │   │
│  │       │    ├──► Check FCAP: "Is FC-123 at capacity?"                │   │
│  │       │    │    Response: Available                                 │   │
│  │       │    │                                                        │   │
│  │       │    ├──► Check CCAP: "Does carrier have capacity?"           │   │
│  │       │    │    Response: Available                                 │   │
│  │       │    │                                                        │   │
│  │       │    ├──► Get transit time from cache                         │   │
│  │       │    │    FC-123 → ZIP 94025 = 3 days                         │   │
│  │       │    │                                                        │   │
│  │       │    ├──► Calculate ESD (Estimated Ship Date)                 │   │
│  │       │    │    Today (Mon) + processing = Tuesday                  │   │
│  │       │    │                                                        │   │
│  │       │    └──► Calculate EDD (Estimated Delivery Date)             │   │
│  │       │         Tuesday + 3 days transit = Friday                   │   │
│  │       │                                                             │   │
│  │       ├──► Rank solutions: FC-123 wins (fastest EDD)                │   │
│  │       │                                                             │   │
│  │       └──► Return selected solution                                 │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│       │                                                                     │
│       ▼                                                                     │
│                                                                             │
│  STEP 4: Response to Customer                                               │
│  ────────────────────────────                                               │
│                                                                             │
│  {                                                                          │
│    "promiseDate": "2024-12-15",                                             │
│    "deliverySpeed": "standard",                                             │
│    "shipNode": "FC-123",                                                    │
│    "carrier": "UPS Ground",                                                 │
│    "cutoffTime": "Order within 3 hours for this date"                       │
│  }                                                                          │
│                                                                             │
│  Total latency: ~80ms                                                       │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

# Part 8: Summary

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              SUMMARY                                        │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  THE SYSTEM IN ONE SENTENCE:                                                │
│  ───────────────────────────                                                │
│  A five-layer architecture that separates orchestration, computation,       │
│  data serving, data ingestion, and capacity management — enabling           │
│  sub-100ms delivery promises while processing thousands of data updates     │
│  per second.                                                                │
│                                                                             │
│                                                                             │
│  KEY ARCHITECTURAL DECISIONS:                                               │
│  ────────────────────────────                                               │
│                                                                             │
│  1. FIVE-LAYER SEPARATION                                                   │
│     Orchestration → Computation → Serving → Ingestion → Configuration       │
│     Each layer has clear responsibility and can scale independently.        │
│                                                                             │
│  2. COMPUTATION LAYER IS THE BRAIN                                          │
│     MCSE-Lite doesn't just serve data — it computes delivery promises       │
│     by aggregating inventory, capacity, transit times, and applying         │
│     complex business rules (cutoffs, calendars, buffers).                   │
│                                                                             │
│  3. READ/WRITE PATH SEPARATION (CQRS)                                       │
│     Reads optimized for speed (cache, memory, computation).                 │
│     Writes optimized for durability (Kafka, Cassandra).                     │
│                                                                             │
│  4. EVENT-DRIVEN DATA FLOW                                                  │
│     Upstream systems publish events, don't call databases directly.         │
│     Ingestion layer owns all writes — single point of control.              │
│                                                                             │
│  5. CACHING AT EVERY LAYER                                                  │
│     In-memory (Caffeine/Hollow) → Distributed → Database (Cassandra)        │
│     Each layer reduces load on the next.                                    │
│                                                                             │
│  6. REGIONAL ISOLATION                                                      │
│     CA-Capacity, regional handlers in ingestion.                            │
│     Different rules don't pollute each other.                               │
│                                                                             │
│                                                                             │
│  THE FIVE LAYERS:                                                           │
│  ─────────────────                                                          │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  Layer 1: ORCHESTRATION    │ Entry point, routing, aggregation     │   │
│  │  Layer 2: COMPUTATION      │ ESD/EDD calculation, sourcing logic   │   │
│  │  Layer 3: DATA SERVING     │ Cache lookups, pre-computed data      │   │
│  │  Layer 4: DATA INGESTION   │ Kafka → Transform → Cassandra         │   │
│  │  Layer 5: CAPACITY/CONFIG  │ Capacity tracking, network config     │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

**Ready to proceed with the Technology Decisions Deep Dive rewrite?**