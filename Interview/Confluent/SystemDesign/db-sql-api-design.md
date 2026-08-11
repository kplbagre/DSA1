# Design an E-Commerce Order Management System

> **Interview Type:** Type 1 — API + Data Model
> **Frequency:** ⭐ Tier 3 — 1 confirmed primary report (Apr 2026 1Point3Acres: "DB, SQL, and API Design round")
> **Key signal from research:** No architecture diagram expected. Round is almost entirely ER design, SQL schema DDL, index selection, and REST contract. Confluent explicitly separated this from the HLD round.
> **Standards file:** `solution-notes-standards.md`
> **API rules reference:** `api-design-cheatsheet.md` (verbs, codes, headers, pagination — not reproduced here)

---

## 🎯 What Is This System?

**In plain English:** A backend system that manages the full lifecycle of customer orders — from the moment a customer places an order to the moment it is delivered or cancelled — with a relational database to track every item, price, and status change, and a REST API for clients to interact with it.

**Real-world examples:**

| System / Company | What they built |
|---|---|
| **Amazon Orders** | Billions of orders/year; separate order service, inventory service, payment service |
| **Shopify** | Order management as a core primitive; merchants call the Orders API to process returns, updates |
| **Flipkart** | India-scale order system; relational core with Kafka fan-out for downstream services |
| **Uber Eats** | Restaurant orders with status lifecycle (placed → accepted → preparing → delivered) |
| **Stripe** | Payment + order model; every Stripe PaymentIntent is essentially an order row |

**Core user journey:** Customer selects items → places an order → system reserves inventory and captures payment → merchant ships → order status transitions to DELIVERED → customer sees the completed order in history.

**Why it's hard to build at scale:** Two customers can attempt to buy the last item simultaneously — one must fail, one must succeed, and the system must never oversell. Additionally, product prices change over time, but a customer's historical order must always show the price they *actually paid*, not today's price — which means the schema must snapshot the price at order time, not reference it from the products table.

**Tableflow parallel:** Every order state transition (PENDING → CONFIRMED → SHIPPED → DELIVERED) is a domain event that naturally streams through Kafka — giving Confluent's Tableflow pipeline exactly the append-only event log it is designed to materialize into Iceberg (an open table format for huge analytic datasets) tables for real-time order analytics: revenue by category, fulfilment latency, cancellation rate.

---

## 🚀 Section 1 — The One-Sentence Opener

> "Before I start designing the API, let me ask a few clarifying questions — specifically around whether the product price is a live reference or a snapshot at order time, and whether we need real-time inventory consistency, because those two decisions will shape the entire schema."

Then immediately Section 2.

---

## 🔍 Section 2 — Clarifying Questions Script (Minutes 0–5)

**Q: "Is this a single-seller platform or a marketplace where multiple vendors sell products?"**
- Why ask: single-seller → one `products` table; marketplace → `sellers` entity, each product belongs to a seller, payouts get complex
- If single-seller → `products` has no `seller_id`; orders settle to one payment recipient
- If marketplace → `products` has `seller_id`; each `order_item` needs its own seller reference for split payouts
- **Assume for this session:** Single-seller (simpler schema, shows the core design; mention marketplace as an extension you'd add a `sellers` table for)

---

**Q: "When a customer places an order, should the schema record the product price as it was at that moment, or should it always reference the current price in the products table?"**
- Why ask: this is the single most important normalization decision in an order system — and getting it wrong is the #1 candidate mistake
- If live reference → any product price change rewrites all historical orders' totals; returns and disputes become impossible to reconcile
- If snapshot → price at order time lives in `order_items.unit_price`; `products.price` can change freely; historical fidelity guaranteed
- **Assume:** Snapshot the price at order time. This is the correct answer in any financial or commerce system.

---

**Q: "Does inventory need to be strongly consistent — i.e., can we never oversell — or is slight overselling tolerable (say, for a 'waitlist' scenario)?"**
- Why ask: strong consistency → synchronous DB lock or row-level lock during order placement, which limits throughput; eventual consistency → allow slight oversell and compensate downstream (refund)
- If strict no-oversell → `products.stock_quantity` must be decremented in the same transaction as `orders` INSERT, with a `CHECK(stock_quantity >= 0)` constraint
- If slight oversell OK → optimistic lock or async decrement; simpler, but need compensation logic
- **Assume:** No oversell allowed. Decrement stock atomically in the same transaction as order placement.

---

**Q: "What are the expected scale targets? How many orders per day, and how many products?"**
- Why ask: shapes index strategy and whether read replicas are needed
- At 10K orders/day → single Postgres instance, default indexes, no partition needed
- At 1M orders/day → need to think about `orders` table partitioning by `created_at`, covering indexes, read replicas for order history queries
- **Assume:** 1M orders/day (~12/sec average, 36/sec peak), 100K SKUs in the product catalogue.

---

**Q: "Can a customer cancel an order in any status, or only certain ones?"**
- Why ask: this determines whether the `status` column has business rules encoded in the API or just in application code — and it shapes the PATCH endpoint's validation logic and the API contract
- If any status → no constraints beyond auth; simpler
- If only PENDING and CONFIRMED → the API must return 409 Conflict for cancellation of a SHIPPED order; the schema stores valid transitions
- **Assume:** Cancellation allowed only while in PENDING or CONFIRMED. SHIPPED and DELIVERED orders cannot be cancelled (use returns flow instead — out of scope).

---

## 📋 Section 3 — Requirements

**Functional Requirements (what the system does):**
- Customers can place an order containing one or more products
- System deducts inventory atomically when an order is placed (no oversell)
- Customers can view a specific order's details (items, prices, status, total)
- Customers can list their order history (paginated, filterable by status)
- Order status transitions: PENDING → CONFIRMED → SHIPPED → DELIVERED (or → CANCELLED from PENDING/CONFIRMED)
- Customers can cancel an order that is PENDING or CONFIRMED
- Out of scope: payment processing internals, product catalogue management, user authentication implementation, recommendation engine, returns/refunds flow

**Non-Functional Requirements:**
- Scale: 1M orders/day (12/sec average; 36/sec peak); 100K products
- Latency: Order placement P99 < 500ms; order read P99 < 100ms; order history list P99 < 200ms
- Availability: 99.9% SLO (Service Level Objective — a measurable internal target for uptime, here ~8.7 hours downtime/year)
- Consistency: Strong for inventory (no oversell); eventual for order analytics (real-time dashboards can lag seconds)
- Durability: Orders and payments must not be lost; ACID (Atomicity, Consistency, Isolation, Durability — the four guarantees of relational database transactions) writes required

---

## 🗂️ Section 3.5 — Core Entities

| Entity | What it represents |
|---|---|
| **Customer** | Transactional — a registered user who places orders; identified by `customer_id` |
| **Product** | Immutable metadata record — name, description, category; `price` and `stock_quantity` are mutable columns; the product itself is a stable entity |
| **Order** | Transactional — the root entity binding a customer, items, and payment; has a lifecycle status; append-heavy (mostly inserts and status updates) |
| **OrderItem** | Transactional — one line item within an order; created with the order, never updated; stores `unit_price` as a snapshot of the price at the moment of order placement |
| **Payment** | Transactional — a payment attempt linked to one order; result may be SUCCESS or FAILED; one-to-one with a successfully placed order |

### 🎨 Visual — Entity Relationships

```
┌─────────────────┐
│    customers    │
│─────────────────│
│ id  PK          │
│ email  UNIQUE   │
│ name            │
└────────┬────────┘
         │ 1
         │ places many
         N
┌─────────▼────────────────────────────────┐
│                 orders                   │
│──────────────────────────────────────────│
│ id              PK                       │
│ customer_id     FK → customers.id        │
│ status          ENUM (see lifecycle ↓)   │
│ total_amount    NUMERIC snapshot         │
│ created_at      TIMESTAMP                │
│ updated_at      TIMESTAMP                │
└───────┬──────────────────────────────────┘
        │ 1
        │ contains many
        N
┌───────▼───────────────────────────────────────┐
│                 order_items                   │
│───────────────────────────────────────────────│
│ id              PK                            │
│ order_id        FK → orders.id                │
│ product_id      FK → products.id              │
│ quantity        INT                           │
│ unit_price      NUMERIC ← SNAPSHOT (not live) │
│ line_total      NUMERIC (unit_price × qty)    │
└──────────────────┬────────────────────────────┘
                   │ N
                   │ references
                   1
┌──────────────────▼────────────────────────────┐
│                  products                     │
│───────────────────────────────────────────────│
│ id              PK                            │
│ name                                          │
│ price           NUMERIC ← CURRENT (mutable)   │
│ stock_quantity  INT (decremented on order)    │
│ category                                      │
└───────────────────────────────────────────────┘

        orders 1 ──── 1 payments
┌─────────────────────────────────┐
│           payments              │
│─────────────────────────────────│
│ id            PK                │
│ order_id      FK UNIQUE         │
│ amount        NUMERIC           │
│ status        ENUM (PENDING,    │
│               SUCCESS, FAILED)  │
│ paid_at       TIMESTAMP         │
└─────────────────────────────────┘

ORDER LIFECYCLE:
  PENDING ──▶ CONFIRMED ──▶ SHIPPED ──▶ DELIVERED
     │              │
     └──────────────┴──▶ CANCELLED

KEY INVARIANT:
  order_items.unit_price is NEVER read from products.price.
  It is written ONCE at order creation and NEVER updated.
  products.price can change at any time — historical orders are unaffected.
  This is the most important design decision in the entire schema.
```

---

## 🔢 Section 4 — Scale Estimation

**Type 1 round — brief. Numbers are here to justify index and partitioning choices in Section 9.**

- **Orders/day:** 1M → ~12 writes/sec average; 36/sec peak
- **OrderItems/day:** avg 3 items per order → 3M rows/day into `order_items`
- **Order history reads:** 10:1 read:write ratio → ~120 reads/sec average; 360/sec peak
- **Product reads:** much higher — product page views (out of scope here, but `products` table is a read hot-spot)
- **Storage:** 1 order ≈ 0.5 KB + 3 items × 0.2 KB ≈ 1.1 KB/order → 1M × 1.1 KB = ~1 GB/day → ~400 GB/year

**Key conclusion:** At 36 writes/sec and 360 reads/sec, a single Postgres primary with correct indexes handles this comfortably (Postgres handles thousands of reads/sec). The `order_items` table grows fastest (3M rows/day). Partition `orders` by `created_at` month if the table approaches 1B rows (in ~3 years at this rate). Index design matters more than raw throughput at this scale.

---

## 🔄 Section 5 — Requirements Variation Table

| If the interviewer says... | Your architecture changes to... | The reasoning |
|---|---|---|
| "10K orders/day" | Single Postgres, no partitioning, no read replica — default indexes only | At 0.1 writes/sec, any indexed Postgres query is instant; no need for caching or replication |
| "100M orders/day (Amazon scale)" | Partition `orders` by month; shard by `customer_id` range across multiple Postgres nodes; separate read replicas for order history queries; Redis cache for in-flight order status | 1,000+ writes/sec saturates a single-writer Postgres; sharding + read replicas needed |
| "Product price can change — but orders must show what was paid" | Keep `unit_price` snapshot in `order_items` as designed (Section 3.5). No change needed — this IS the base design | Snapshot pattern already built for this; `products.price` updates freely |
| "Customers want real-time inventory count visible on the product page" | Keep `products.stock_quantity` in the same transaction; add a Redis cache with short TTL for product page reads (eventual consistency acceptable there) | DB decrement stays authoritative; Redis serves product page load at high read rate without hitting Postgres on every page view |
| "Allow partial order cancellation (cancel one item, not the whole order)" | Add a `cancelled_at` column to `order_items`; change CANCEL from a whole-order state to a per-item state; recalculate `orders.total_amount` on each item cancellation | This promotes `order_items` to having its own lifecycle, complicating the API and schema significantly — flag it as a scope increase |
| "Multi-region (serve US and EU from separate data centres)" | Postgres global replication (e.g., AWS Aurora Global Database); write to primary region, replicate to EU read replica; consider EU-local writes if GDPR (General Data Protection Regulation — EU law requiring user data to be stored locally) mandates it | Single-primary, multi-read-replica topology; conflict resolution is simple because only one region writes |
| "Marketplace — multiple sellers per order" | Add `sellers` table; add `seller_id` to `products` and `order_items`; split payments into per-seller payouts | Schema expands significantly: `order_items` must track which seller fulfils each line, and `payments` must be split or aggregated per seller |

---

## ⭐ Section 6 — API Design ← CONFLUENT'S PRIMARY EVALUATION AXIS

> API Design is Confluent's primary evaluation axis. Every verb, code, and header is a test point. See `api-design-cheatsheet.md` for the complete rules. This section derives and justifies the endpoints for this specific question.

---

### 🧠 How to Derive These Endpoints (Reconstruct, Don't Recall)

Every endpoint starts from a functional requirement: **FR → operation → resource → HTTP method → contract.**

**"Customers can place an order."**
The operation is *create*. The resource being created is an `order`. Create → `POST`. The caller is an authenticated customer. Minimum payload: `customer_id` (from the JWT — JSON Web Token, a self-contained authentication credential encoded in the HTTP Authorization header), and a list of `{ product_id, quantity }` pairs. What do they get back? The full order object including the system-assigned `order_id`, `status`, and calculated `total_amount`. Also a `Location: /v1/orders/{id}` header so the client knows where to fetch it again. Status on success: **201 Created** (not 200 — 201 signals "a new resource was created at the Location").

**"System deducts inventory atomically when an order is placed — no oversell."**
This is not a new endpoint — it is a constraint on `POST /v1/orders`. The constraint shapes the error response: if any requested `quantity` exceeds `products.stock_quantity`, the entire order must fail (no partial order). Status: **409 Conflict** — the request was well-formed, but the current state of the resource (insufficient inventory) makes it impossible to fulfil. Do NOT return 400 (that's for malformed requests — the inventory constraint is a business state conflict, not a client error).

**"Customers can view a specific order."**
The operation is *read one*. The resource is `order` identified by `id`. Read → `GET`. Response includes all order details: items (with their snapshot prices), status, total, timestamps. Status: **200 OK**. The non-obvious case: if the customer requests another customer's order — **404 Not Found** rather than 403 Forbidden. Exposing 403 confirms the resource exists, which is an information leak for order IDs. Return 404 as if the order doesn't exist for the requesting user.

**"Customers can list their order history, filterable by status, paginated."**
The operation is *read many*. The resource is `orders` scoped to a `customer`. Read many → `GET`. Pagination strategy: **cursor-based** (not offset). Why? Offset pagination (`LIMIT 20 OFFSET 200`) requires the DB to read and discard 200 rows on every page load — O(offset) cost that grows as the customer's order history grows. Cursor-based uses `WHERE created_at < :cursor ORDER BY created_at DESC LIMIT 20` — O(log N) index seek regardless of how far into history you are. The cursor value is the `created_at` timestamp of the last item on the current page, returned as `"next_cursor"` in the response body.

**"Customers can cancel an order in PENDING or CONFIRMED status."**
The operation is *partial update of status* to CANCELLED. Partial update → `PATCH` (not PUT — PUT replaces the whole resource; PATCH modifies a subset of fields). Path: `PATCH /v1/orders/{id}/status`. Payload: `{ "status": "CANCELLED" }`. Why not `DELETE /v1/orders/{id}`? Because the order record must be retained for financial audit — cancelling does not delete it. Status on success: **200 OK** with the updated order. Status if order is SHIPPED or DELIVERED: **409 Conflict** — the requested status transition is invalid given the current state.

**Validation check:** Map each endpoint back to a FR.
- `POST /v1/orders` → FRs 1 and 2 (place + inventory deduct)
- `GET /v1/orders/{id}` → FR 3 (view order details)
- `GET /v1/customers/{customerId}/orders` → FR 4 (list history)
- `PATCH /v1/orders/{id}/status` → FR 5 (status transitions including CONFIRMED, SHIPPED, DELIVERED by system actors) and FR 6 (customer-initiated cancellation — same endpoint, different caller)

---

### 📋 Core Endpoints Table

| Method | Path | Auth | Request Body | Response Body | Status Codes |
|---|---|---|---|---|---|
| `POST` | `/v1/orders` | JWT (customer) | `{ items: [{ product_id, quantity }] }` | `{ id, customer_id, status, items[], total_amount, created_at }` | 201, 400, 409, 401 |
| `GET` | `/v1/orders/{orderId}` | JWT (customer or admin) | — | `{ id, customer_id, status, items[{ product_id, name, unit_price, quantity, line_total }], total_amount, created_at, updated_at }` | 200, 404, 401 |
| `GET` | `/v1/customers/{customerId}/orders` | JWT (customer — own orders only) | Query: `?status=PENDING&limit=20&cursor=<ts>` | `{ orders[], next_cursor }` | 200, 400, 401, 404 |
| `PATCH` | `/v1/orders/{orderId}/status` | JWT (admin or customer for CANCELLED) | `{ "status": "CONFIRMED" \| "SHIPPED" \| "DELIVERED" \| "CANCELLED" }` | `{ id, status, updated_at }` | 200, 400, 404, 409, 401 |

---

### 🔍 Endpoint Stories

**`POST /v1/orders`** — Creates a new order and atomically deducts inventory for all requested items. The non-obvious detail: if any one item is out of stock, the entire request fails with **409 Conflict** and no partial order is created. This is a business atomicity rule — customers don't want half their cart placed while the rest fails. The request body must include `items` as a non-empty array (empty array → **400 Bad Request**, because there's no meaningful order with zero items). The `total_amount` in the response is computed server-side (sum of `unit_price × quantity` for each item) — the client never sends a total, because the server must own price calculation to prevent tampering.

**`GET /v1/orders/{orderId}`** — Returns the complete order snapshot, including item-level detail with `unit_price` as it was at order time (not today's product price). The critical design point: the response's `items[].unit_price` comes from `order_items.unit_price` (the snapshot column), not from a live join to `products.price`. A customer viewing a 6-month-old order sees exactly what they paid. If a customer requests a valid order that belongs to a different customer → **404 Not Found** (not 403) to prevent order ID enumeration attacks.

**`GET /v1/customers/{customerId}/orders`** — Returns paginated order history, newest first. Uses cursor-based pagination: the `cursor` query parameter is the `created_at` timestamp of the last item on the previous page; the next page fetches `WHERE created_at < :cursor ORDER BY created_at DESC LIMIT :limit`. The response includes `next_cursor` (null if no more pages) — no `total_count` field, because cursor-based pagination cannot cheaply compute total rows without a separate `COUNT(*)` query that scans the whole filtered set. `?status=PENDING` filters by order status — the DB query adds `AND status = 'PENDING'`. A customer can only see their own orders; requesting another customer's orders → **404 Not Found** (same rule as `GET /orders/{id}` — the caller cannot distinguish "this customer ID doesn't exist" from "this customer ID exists but isn't yours"; uniform 404 prevents enumeration attacks on both resources).

**`PATCH /v1/orders/{orderId}/status`** — Handles all status transitions. The same endpoint serves two caller types: customers (can only transition to CANCELLED from PENDING or CONFIRMED) and admins/fulfilment systems (can transition to CONFIRMED, SHIPPED, DELIVERED). Invalid transition → **409 Conflict** with a body like `{ "error": "invalid_transition", "current_status": "SHIPPED", "requested_status": "CANCELLED" }`. Why PATCH not PUT? PUT would require the client to send the full order object. PATCH sends only the field being updated — correct REST semantics for a partial modification.

---

## 🏗️ Section 7 — High-Level Architecture

**Type 1 round — full HLD diagram is not the primary deliverable.** Sketch this briefly (5 minutes) to show the API connects to something real. The interviewer wants to see you can connect schema and API to a working system.

```
┌──────────────────────────────────────────────────────────────────────┐
│                          API Gateway                                 │
│            (JWT validation, rate limiting, routing)                  │
└─────────────────────────────┬────────────────────────────────────────┘
                              │
              ┌───────────────▼────────────────┐
              │         Order Service           │
              │  - Places orders (POST)         │
              │  - Reads order detail (GET)     │
              │  - Handles status transitions   │
              └───────┬───────────────────┬─────┘
                      │                   │
          ┌───────────▼──────┐   ┌────────▼──────────────────┐
          │  Postgres (RW)   │   │   Kafka "order-events"    │
          │  orders          │   │   (append-only event log  │
          │  order_items     │   │   for async consumers)    │
          │  products        │   └────────┬──────────────────┘
          │  customers       │            │
          │  payments        │   ┌────────▼──────────────────┐
          └───────┬──────────┘   │   Tableflow / Analytics   │
                  │              │   (Iceberg tables for      │
          ┌───────▼──────────┐   │    revenue, fulfilment,   │
          │  Postgres (RO)   │   │    cancellation metrics)  │
          │  Read replica    │   └───────────────────────────┘
          │  (order history  │
          │   list queries)  │
          └──────────────────┘
```

**Data flow for `POST /v1/orders`:**
1. Client → API Gateway (JWT validated) → Order Service
2. Order Service opens a DB transaction:
   - Selects `products.price` and `products.stock_quantity` for all requested items, WITH a row-level lock (`SELECT ... FOR UPDATE`)
   - Checks `stock_quantity >= requested_quantity` for each item; if any fail → rollback, return 409
   - Inserts 1 row into `orders`
   - Inserts N rows into `order_items` (one per item, with `unit_price` snapshot from step a)
   - Updates `products.stock_quantity` (`stock_quantity = stock_quantity - quantity`)
   - Transaction commits → ACID guarantee
3. After commit: publishes `order.placed` event to Kafka (async, not in the transaction — eventual for analytics)
4. Returns 201 with the created order to the client

**Every box justified:**
- **API Gateway** — validates JWTs so the Order Service doesn't need to; rate-limits to protect DB from traffic spikes
- **Postgres (RW)** — strong consistency for inventory; ACID transactions for the atomicity requirement
- **Postgres (RO read replica)** — order history list queries (`GET /v1/customers/{id}/orders`) are read-heavy; offloading them to a replica keeps the primary's CPU free for writes
- **Kafka** — decouples order analytics from the critical write path; if the analytics consumer is slow or down, order placement is unaffected; the event log is the source of truth for downstream systems
- **Tableflow/Iceberg** — Confluent's product that materializes Kafka streams into Iceberg tables for SQL analytics; real-time revenue dashboards read from here, not from Postgres

---

## 🔬 Section 8 — Core Component Deep Dives

**Type 1 round — keep these brief (2 minutes each). The interviewer is spending their time on schema and API. Flag these as "areas I'd go deeper on if we have time."**

### Deep Dive: Inventory Atomicity (Prevent Oversell)

**Why this is the most critical component:** If inventory is not decremented in the same transaction as the order INSERT, two concurrent requests for the last item can both read `stock_quantity = 1`, both pass the check, and both succeed — resulting in one oversold unit and a financial dispute.

**Options considered:**

| Option | Pros | Cons |
|---|---|---|
| **Optimistic locking** (`@Version` / CAS) | No lock held; high concurrency; retries on conflict | On high contention (flash sale), many retries → poor latency; retry logic needed in application |
| **Pessimistic locking** (`SELECT FOR UPDATE`) | Guarantees serialized access; no retries; simple logic | Blocks other transactions on same product row; lower throughput under high concurrency |
| **Redis distributed lock** (`SET NX PX`) | Works across multiple Order Service pods | Extra dependency; lock TTL management; distributed lock is complex to get right |

**Decision: Pessimistic locking (`SELECT FOR UPDATE`)** for correctness, with a short transaction scope to minimise lock hold time. The lock is held only for the duration of one DB transaction (expected < 50ms). For flash sale scenarios (thousands of concurrent buyers for 1 item), add a Redis queue in front of the DB transaction — but that is a Stage 2 concern.

```sql
-- Inside the same DB transaction as the INSERT:
SELECT id, price, stock_quantity
FROM products
WHERE id = ANY(:product_ids)
FOR UPDATE;                   -- acquires row-level lock; other txns wait here

-- Check quantity, then:
UPDATE products
SET stock_quantity = stock_quantity - :qty
WHERE id = :product_id
  AND stock_quantity >= :qty; -- belt-and-suspenders: double-check inside the lock
```

---

### Deep Dive: Price Snapshot Pattern

**Why this matters:** Product prices change (sales, inflation, promotions). An order placed in January must show January's price in February, March, and forever — regardless of what `products.price` shows today.

**The mistake:** Joining `order_items` to `products.price` at query time. This makes historical order totals silently wrong every time the price is updated.

**The correct pattern:** Write `products.price` into `order_items.unit_price` at the moment the order is placed. That column is then immutable — it is never updated. Historical correctness is preserved by data structure, not by application convention.

```sql
-- When placing the order, read the live price first:
SELECT id, price AS current_price, stock_quantity
FROM products
WHERE id = ANY(:product_ids)
FOR UPDATE;

-- Then insert into order_items with the snapshot:
INSERT INTO order_items (order_id, product_id, quantity, unit_price, line_total)
VALUES (
    :order_id,
    :product_id,
    :quantity,
    :current_price,                          -- snapshot from SELECT above
    :quantity * :current_price               -- computed at insert time
);
```

---

## 🗄️ Section 9 — Data Model / SQL Schema ← CO-PRIMARY DELIVERABLE FOR TYPE 1

> **This section is as important as Section 6 in a DB + SQL + API Design round.** Actual DDL, correct types, all indexes explicitly named and justified. Confluent probes: "What index serves that query?" — have an answer.

---

### Core Tables

```sql
-- ─────────────────────────────────────────
-- customers
-- ─────────────────────────────────────────
CREATE TABLE customers (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    email      VARCHAR(255) NOT NULL,
    name       VARCHAR(255) NOT NULL,
    created_at TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_customers_email UNIQUE (email)
);

-- ─────────────────────────────────────────
-- products
-- ─────────────────────────────────────────
CREATE TABLE products (
    id             UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    name           VARCHAR(500)   NOT NULL,
    description    TEXT,
    price          NUMERIC(10, 2) NOT NULL CHECK (price > 0),
    stock_quantity INT            NOT NULL DEFAULT 0 CHECK (stock_quantity >= 0),
    category       VARCHAR(100),
    created_at     TIMESTAMP      NOT NULL DEFAULT NOW()
);

-- Index: product page by category (browsing use case)
CREATE INDEX idx_products_category ON products (category);

-- ─────────────────────────────────────────
-- orders
-- ─────────────────────────────────────────
CREATE TYPE order_status AS ENUM (
    'PENDING',
    'CONFIRMED',
    'SHIPPED',
    'DELIVERED',
    'CANCELLED'
);

CREATE TABLE orders (
    id             UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id    UUID           NOT NULL REFERENCES customers(id),
    status         order_status   NOT NULL DEFAULT 'PENDING',
    total_amount   NUMERIC(12, 2) NOT NULL CHECK (total_amount >= 0),
    created_at     TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP      NOT NULL DEFAULT NOW()
);

-- Index 1: customer order history list — the most frequent query pattern
--   Query: GET /v1/customers/{id}/orders
--   SQL:   WHERE customer_id = $1 AND created_at < $cursor ORDER BY created_at DESC LIMIT 20
CREATE INDEX idx_orders_customer_created
    ON orders (customer_id, created_at DESC);

-- Index 2: filter by status within a customer's orders
--   Query: GET /v1/customers/{id}/orders?status=PENDING
--   SQL:   WHERE customer_id = $1 AND status = $2 AND created_at < $cursor
CREATE INDEX idx_orders_customer_status_created
    ON orders (customer_id, status, created_at DESC);

-- ─────────────────────────────────────────
-- order_items
-- ─────────────────────────────────────────
CREATE TABLE order_items (
    id          UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id    UUID           NOT NULL REFERENCES orders(id),
    product_id  UUID           NOT NULL REFERENCES products(id),
    quantity    INT            NOT NULL CHECK (quantity > 0),
    unit_price  NUMERIC(10, 2) NOT NULL CHECK (unit_price > 0),
    -- price SNAPSHOT — written once at order creation, NEVER updated.
    -- This is deliberately NOT a FK join to products.price.
    -- See Section 3.5 KEY INVARIANT for the reasoning.
    line_total  NUMERIC(12, 2) NOT NULL,
    -- line_total = unit_price × quantity, computed at insert time.
    CONSTRAINT ck_order_items_line_total
        CHECK (line_total = unit_price * quantity)
);

-- Index: fetch all items for a given order
--   Query: GET /v1/orders/{id}  →  SELECT * FROM order_items WHERE order_id = $1
CREATE INDEX idx_order_items_order_id ON order_items (order_id);

-- ─────────────────────────────────────────
-- payments
-- ─────────────────────────────────────────
CREATE TYPE payment_status AS ENUM ('PENDING', 'SUCCESS', 'FAILED');

CREATE TABLE payments (
    id         UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id   UUID           NOT NULL REFERENCES orders(id),
    amount     NUMERIC(12, 2) NOT NULL,
    status     payment_status NOT NULL DEFAULT 'PENDING',
    paid_at    TIMESTAMP,
    created_at TIMESTAMP      NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_payments_order_id UNIQUE (order_id)
    -- One payment per order (UNIQUE enforces the 1:1 relationship)
);
```

---

### 🎨 Visual — Query Plan for Customer Order History

```
GET /v1/customers/{customerId}/orders?limit=20&cursor=2026-06-01T12:00:00Z

SQL generated:
  SELECT id, status, total_amount, created_at
  FROM   orders
  WHERE  customer_id = 'abc-123'
    AND  created_at  < '2026-06-01T12:00:00Z'     ← cursor
  ORDER  BY created_at DESC
  LIMIT  20;

Query plan with idx_orders_customer_created:
  ┌──────────────────────────────────────────────────────────────────┐
  │  Index Scan Backward on idx_orders_customer_created              │
  │    (customer_id ASC, created_at DESC)                            │
  │                                                                  │
  │  Seek condition: customer_id = 'abc-123'  ← O(log N) b-tree seek│
  │  Scan:           created_at < cursor      ← sequential read      │
  │  Stop after:     20 rows found            ← LIMIT applied early  │
  └──────────────────────────────────────────────────────────────────┘

Without the index: full table scan of ALL orders, filtered in memory.
  At 1M orders/day for 1 year → 365M rows scanned for every page load.

KEY INVARIANT:
  The composite index (customer_id, created_at DESC) is what makes
  cursor pagination O(log N) per page regardless of history depth.
  No index → O(N) per page, where N grows by 1M every day.
```

---

### Key Schema Decisions

**Decision 1 — `unit_price` snapshot in `order_items`:**
`products.price` changes over time (promotions, repricing). If `order_items` only stored `product_id` and quantity, every historical order read would join to `products.price` and show today's price — making order history financially incorrect. `unit_price` is written once at creation, never updated, and reflects exactly what the customer paid.

**Decision 2 — `line_total` stored, not computed:**
`line_total = unit_price × quantity` could be computed at query time, but storing it pre-computed serves two goals: (a) the `CHECK` constraint makes the DB enforce arithmetic consistency — if application code ever passes wrong values, the DB rejects the row; (b) aggregating order totals (`SUM(line_total)`) in analytics queries is a simple column read rather than a runtime multiplication, which matters at billion-row scale.

**Decision 3 — `ENUM` type for `status`:**
Postgres `ENUM` types are stored as integers internally but displayed as readable strings. They enforce the valid set at the DB level — an application bug can never insert `"PINDING"` (typo). Adding a new status requires an `ALTER TYPE` migration, which is a trade-off: flexibility costs a schema change, correctness is guaranteed.

**Decision 4 — Composite index `(customer_id, status, created_at DESC)` in addition to `(customer_id, created_at DESC)`:**
When a customer filters `?status=PENDING`, the index can satisfy the whole query from the index alone (index-only scan for the columns in the `SELECT`, or at least avoid a full table scan on `orders`). Without it, the DB fetches all of a customer's orders (using the first index) then filters by status in memory — wasteful when a customer has 500+ orders.

**Decision 5 — `UNIQUE (order_id)` on `payments`:**
Enforces the one-payment-per-order business rule at the database level, not just application code. If application code ever creates a duplicate payment row (retry bug), the DB rejects it with a unique constraint violation.

---

### Normalization Analysis (Confluent probes this directly)

| Table | Normal Form | Reasoning |
|---|---|---|
| `customers` | 3NF (no transitive dependencies) | All non-key attributes depend only on `id` |
| `products` | 3NF | `category` is a potential partial-dependency risk; at scale, normalize to a `categories` table |
| `orders` | 3NF | `total_amount` is derived from `order_items`; it is deliberately **denormalized** here as a snapshot for fast reads (avoid re-summing `line_total`s on every order read) — a conscious trade-off |
| `order_items` | 3NF with intentional denormalization | `unit_price` is a snapshot (denormalized from `products.price`); `line_total` is derived (denormalized from `unit_price × quantity`). Both violate strict 3NF but are the correct choice for this domain. |
| `payments` | 3NF | All attributes depend on `id`; `order_id` is a FK, not a data dependency |

> **The key interviewer answer on normalization:** "I'm deliberately denormalizing `unit_price` into `order_items` and `total_amount` into `orders`. Strict normalization would mean joining to `products.price` at query time — but that breaks historical accuracy. The denormalization is a conscious trade-off: I gain correctness and read performance at the cost of a slightly larger write operation on order placement."

---

## ⚠️ Section 10 — Trade-offs + Failure Modes

### Trade-off 1: Price Snapshot (Denormalization) vs Live Join to `products.price`

- **Chose:** Snapshot `unit_price` in `order_items` at creation time
- **Gain:** Historical accuracy (a 6-month-old order shows exactly what was paid); no dependency on `products.price` for reads; order total is immutable
- **Lose:** Slight storage overhead (price duplicated across N `order_items` rows for the same product); schema migration needed if we want to track price history (currently we only know "price was X when order was placed")
- **Failure mode if wrong:** [Technical]: Every time a merchant updates a product's price, all historical order reads silently change total — application shows customers wrong totals for past purchases; disputes and refunds are based on wrong numbers. [Streaming impact]: Any Kafka consumer materializing order data into a Tableflow/Iceberg analytics table would read the live-join total — order revenue analytics would fluctuate as prices change, making "revenue for Q1 2026" an unstable number that changes every time a product is repriced.

---

### Trade-off 2: Pessimistic Locking vs Optimistic Locking for Inventory Deduction

- **Chose:** Pessimistic locking (`SELECT ... FOR UPDATE`) on product rows during order placement
- **Gain:** Guaranteed no oversell; no retry logic needed; correctness is built into the transaction
- **Lose:** Row-level lock is held for the duration of the transaction (~50ms); under high concurrency on one product (flash sale), throughput on that product is serialized — only one order for that product completes at a time
- **Failure mode if wrong:** [Technical]: Optimistic locking without retry causes order placement failures to surface as errors to the customer; aggressive retries under flash sale load generate a retry storm that can saturate the DB connection pool and take down the entire Order Service. [Streaming impact]: Failed order placement events never reach Kafka — the `order.placed` event is never published; downstream Tableflow pipelines see a gap in the event log; inventory analytics undercount order volume during the flash sale window.

---

### Trade-off 3: Cursor-based Pagination vs Offset Pagination for Order History

- **Chose:** Cursor-based pagination (`WHERE created_at < :cursor ORDER BY created_at DESC LIMIT 20`)
- **Gain:** O(log N) per page via the composite index; constant query time regardless of page depth; immune to "rows shift" problem (if an order is inserted on page 2 while the customer is on page 1, cursor-based pagination doesn't skip or duplicate it)
- **Lose:** Cannot jump directly to page 50 (no random page access); the client must walk pages sequentially; cannot show "Page 3 of 12" UI (only "next" / "previous")
- **Failure mode if wrong:** [Technical]: Offset pagination (`LIMIT 20 OFFSET 1000`) requires the DB to read and discard 1,000 rows on every page load. A customer with 2 years of order history triggering page 50 executes a 1,000-row discard on every render. At 360 reads/sec, this CPU spike manifests as P99 degradation on the read replica and can cascade to the primary. [Streaming impact]: The order history endpoint becomes the slowest consumer of Postgres read capacity — replica lag increases under load; Kafka producers downstream (which may rely on up-to-date order data) start seeing stale reads from a lagging replica.

---

## 🌊 Section 11 — Confluent / Tableflow Angle

**Order events are the canonical Kafka use case.** Every state transition in `orders` is an event: `order.placed`, `order.confirmed`, `order.shipped`, `order.delivered`, `order.cancelled`. Publishing these to a Kafka topic after the DB transaction commits gives Confluent's Tableflow pipeline exactly what it needs.

**Specific Kafka design choices for this system:**

- **Topic:** `order-events` with partitioning by `customer_id` — ensures all events for a given customer are processed in order by the same consumer partition; guarantees correct temporal sequencing in the Tableflow materialized view
- **Compaction:** Use a compacted topic for the `current-order-state` materialization — each `order_id` key overwrites the previous value; Tableflow can maintain a live "latest status per order" view without growing unbounded
- **At-least-once delivery:** The Kafka producer fires after the DB transaction commits. On Order Service pod restart between commit and publish, the event is re-published (at-least-once). The downstream Tableflow job must be idempotent on `order_id` — deduplicate by `(order_id, status, updated_at)` to avoid double-counting events

**Iceberg / analytics materializations:** Tableflow materializes the `order-events` stream into Iceberg tables:
- `order_revenue_by_day` — rolling `SUM(total_amount)` partitioned by `DATE(created_at)` and `category`; updated in near-real-time as events land
- `order_fulfilment_latency` — `CONFIRMED_at - PLACED_at` per order; drives SLA monitoring for the merchant operations team

**Multi-cloud reliability:** Confluent operates across AWS, Azure, and GCP. For a globally deployed order system, the Kafka `order-events` topic replicates across cloud regions using Confluent's Cluster Linking — giving the Tableflow analytics pipeline a unified view of orders globally without requiring the Postgres order database to be multi-region.

---

## 🔬 Section 12 — Where the Interviewer Will Probe

### Surface Probe (Tier 1 — every candidate gets this)

**Q: "Why did you store `unit_price` in `order_items` instead of just using the current `products.price`?"**
> Historical accuracy. If a customer placed an order when a product was $29.99 and the merchant later changes it to $39.99, the customer's order history must show $29.99 — what they actually paid. A live join to `products.price` would silently rewrite every historical order. This is not a performance optimization; it is a correctness requirement. Any commerce system that processes refunds or disputes needs this.

**Q: "What indexes does your schema have, and what queries do they serve?"**
> Three key indexes: (1) `idx_orders_customer_created (customer_id, created_at DESC)` — serves `GET /v1/customers/{id}/orders`, the most frequent read. (2) `idx_orders_customer_status_created (customer_id, status, created_at DESC)` — serves the filtered version `?status=PENDING`. (3) `idx_order_items_order_id (order_id)` — serves `GET /v1/orders/{id}` when fetching line items. Without index 3, fetching a 10-item order would require a full scan of the `order_items` table.

**Q: "Why cursor-based pagination and not offset?"**
> Two reasons. Performance: `LIMIT 20 OFFSET 200` makes the DB read and discard 200 rows on every request. The composite index on `(customer_id, created_at DESC)` makes cursor-based pagination an index seek + 20 sequential reads — O(log N) regardless of page depth. Correctness: if a new order is placed while the customer is browsing history, offset-based pages shift — the customer either skips an order or sees a duplicate. Cursor-based pagination is immune to concurrent inserts because it navigates by value, not position.

---

### Deep Probe (Tier 2 — tests real understanding)

**Q: "What happens if two customers try to place the last item simultaneously?"**
> The `SELECT ... FOR UPDATE` on the products row serializes both transactions. The first one to acquire the lock checks `stock_quantity >= requested_quantity` (passes), decrements the stock, inserts the order, and commits. The second transaction waits at the lock, then reads `stock_quantity = 0`, fails the check, rolls back, and the API returns 409 to that customer. The DB `CHECK(stock_quantity >= 0)` constraint is a belt-and-suspenders guard — if application code ever has a bug, the constraint prevents the row going negative.

**Q: "How would you handle a flash sale where 10,000 people try to buy the last 100 units in one second?"**
> At 10,000 concurrent `SELECT FOR UPDATE` on the same 100 product rows, the DB lock queue saturates. The connection pool (typically 50–200 connections) exhausts before all threads are served, causing connection timeout errors. The Stage 2 fix: add a Redis sorted set as an inventory reservation queue — each order request atomically decrements a Redis counter (`DECRBY`) before touching Postgres. Redis processes 100K+ writes/sec on a single instance. Only the requests that get a reservation token (counter ≥ 0) proceed to the DB transaction. The DB sees at most 100 concurrent transactions instead of 10,000.

**Q: "Your `orders.total_amount` is computed from `order_items.line_total`. What if they ever get out of sync?"**
> The `CHECK` constraint on `line_total` (`line_total = unit_price * quantity`) prevents `order_items` rows from being corrupted. For `total_amount` on `orders`, I'd add a trigger or application-level invariant test: on insertion, `total_amount` = `SUM(line_total)` for that order. Since both are written in the same transaction and `order_items` rows are immutable after creation, drift is only possible from application bugs. A daily reconciliation job (`SELECT order_id, SUM(line_total) FROM order_items GROUP BY order_id HAVING SUM(line_total) != orders.total_amount`) would catch any inconsistency.

---

### Cross-Concept Probe (Tier 3 — separates senior candidates)

**Q: "If we needed to add multi-currency support — customers pay in USD, GBP, EUR — what changes in the schema?"**
> Two changes. First: `unit_price`, `line_total`, and `total_amount` must carry a `currency_code` alongside the amount (a `NUMERIC` with no currency label is ambiguous). Second: decide whether `total_amount` is always stored in the customer's payment currency (simpler for display) or always in a base currency (USD) for analytics. I'd store both: `total_amount` in customer currency (for display), `total_amount_usd` as a snapshot of the USD equivalent at the exchange rate at order time (for analytics). The exchange rate itself should be snapshotted — same reasoning as `unit_price` — because rates change and historical revenue analysis must not drift.

**Q: "The `payments` table has a 1:1 relationship with `orders`. What if we introduce installment payments — a customer pays in 3 monthly instalments?"**
> The `UNIQUE (order_id)` constraint on `payments` breaks this. Change the relationship to 1:N (one order, many payment events). Rename the table `payment_events` (or `payment_transactions`), drop the `UNIQUE` constraint, add a `payment_number` column (`1`, `2`, `3`), and add `UNIQUE (order_id, payment_number)` to prevent duplicate instalment records. The `orders.status` lifecycle would gain an intermediate state (e.g., `PARTIALLY_PAID`) between PENDING and CONFIRMED.

---

## 🐞 Section 13 — Common Mistakes on This Question

- **Mistake 1: Joining to `products.price` at query time** → Why it's wrong: historical order totals silently change every time the merchant updates a product's price. What to say instead: "I snapshot the price at order creation time into `order_items.unit_price` — a deliberate denormalization for correctness, not performance."

- **Mistake 2: Using `DELETE /v1/orders/{id}` to cancel an order** → Why it's wrong: cancellation is a status transition, not record deletion. Financial systems must retain order records for audit and dispute resolution — even cancelled orders. What to say instead: "`PATCH /v1/orders/{id}/status` with `{ status: 'CANCELLED' }` — the record persists, the state changes."

- **Mistake 3: Offset pagination for order history** → Why it's wrong: `LIMIT 20 OFFSET 1000` reads and discards 1,000 rows on every page request; performance degrades linearly with page depth. What to say instead: cursor-based pagination using `WHERE created_at < :cursor ORDER BY created_at DESC LIMIT 20`, served by the composite index `(customer_id, created_at DESC)`.

- **Mistake 4: No composite index on `(customer_id, status, created_at DESC)`** → Why it's wrong: `?status=PENDING` query falls back to scanning all of a customer's orders (potentially thousands) and filtering in memory. What to say: "The second composite index covers the filtered query. Without it, status filtering is O(customer's total orders), not O(matched orders)."

- **Mistake 5: Forgetting the `CHECK(stock_quantity >= 0)` constraint** → Why it's wrong: application code bugs or race conditions can decrement stock below zero. The DB constraint is the last line of defence. What to say: "I add `CHECK(stock_quantity >= 0)` to `products`. The application-level `FOR UPDATE` lock prevents most races, but the constraint catches anything the application misses."

- **Mistake 6: Returning 403 instead of 404 when a customer accesses another customer's order or history** → Why it's wrong: 403 Forbidden reveals that the resource ID exists but is owned by someone else — an information leak usable for order-ID or customer-ID enumeration. What to say: "Return 404 Not Found uniformly for any resource that either doesn't exist or doesn't belong to the requesting customer — across both `/orders/{id}` and `/customers/{id}/orders`. The caller cannot distinguish these two cases, and that is intentional."

---

## 🧭 Section 14 — Confluent Evaluation Axes Checklist

| Axis | Relevant? | How your design addresses it |
|---|---|---|
| **API Design Precision** | ✅ | `POST /v1/orders` returns 201 with `Location` header; `PATCH /v1/orders/{id}/status` uses PATCH (not PUT) with 409 on invalid transitions, body naming `current_status` and `requested_status`; uniform 404 (not 403) for cross-customer access on both `/orders/{id}` and `/customers/{id}/orders` — prevents order/customer ID enumeration; cursor-based pagination drops `total_count` because a COUNT scan conflicts with cursor semantics |
| **Trade-off Defense** | ✅ | Three explicit trade-offs: price snapshot (correctness over strict 3NF), pessimistic locking (correctness over throughput), cursor pagination (performance over random-page-access UI). Each trade-off names what is gained AND what is lost. |
| **SQL / Data Modeling** | ✅ | Full `CREATE TABLE` DDL with constraints, `CHECK` clauses, `ENUM` types. Three composite indexes, each named and tied to the specific query that uses it. Normalization decisions explained: intentional denormalization of `unit_price` and `line_total` with reasoning. |
| **Distributed Systems** | ✅ (light) | Pessimistic lock (`FOR UPDATE`) prevents concurrent oversell. Read replica for order history list reads. Mention flash-sale Redis queue as Stage 2 scale mechanism. |
| **Pipeline Resilience** | ✅ | Kafka `order-events` published after DB commit (not inside the transaction). At-least-once delivery acknowledged; downstream Tableflow job must deduplicate by `(order_id, status, updated_at)`. |
| **Concurrency** | ✅ | `SELECT FOR UPDATE` serializes concurrent writes to the same product row. `CHECK(stock_quantity >= 0)` is a DB-level guard if the application-level lock ever has a bug. |

---

## 🧾 Section 15 — TL;DR Answer Summary

> "The central design decision in an order management schema is that `order_items.unit_price` must snapshot the product's price at order creation — never be read from the live `products.price` — because merchants change prices and historical orders must always show what the customer actually paid. The REST API uses `POST /v1/orders` returning 201, cursor-based pagination for order history (composite index on `customer_id, created_at DESC` makes each page O(log N)), `PATCH /v1/orders/{id}/status` for all state transitions with 409 on invalid transitions, and 404 (not 403) when a customer accesses another's order. Inventory atomicity is guaranteed by `SELECT FOR UPDATE` within the same DB transaction as the `INSERT` — no oversell is possible. The three trade-offs I would defend first: (1) price snapshot correctness over strict 3NF, (2) pessimistic locking correctness over maximum throughput, and (3) cursor pagination correctness over offset. For Confluent: every order state transition publishes an `order.placed` / `order.confirmed` etc. event to Kafka after the DB commit, and Tableflow materializes these into Iceberg tables for real-time revenue and fulfilment analytics — with the downstream job handling at-least-once by deduplicating on `(order_id, status, updated_at)`."

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Aug 10, 2026 | File created. Domain: E-Commerce Order Management (relational-heavy, fits the DB + SQL + API Design round confirmed in Apr 2026 1Point3Acres report). Covers all 15 sections per `solution-notes-standards.md`. Domain chosen to differentiate from existing files (feedly = podcast/RSS, aggregate-news-feed = fan-out, tempmail = expiry/TTL, distributed-kv-store = NoSQL consistency). Key teaching: price snapshot pattern, pessimistic locking for inventory, composite index for cursor pagination. |
