# Shopping Cart — JPMC Round 3 (LLD)

> **JPMC context:** Confirmed by a Blind panelist. OOP-focused — the interviewer does **not**
> expect distributed systems or HLD components. The whole problem tests one OOP skill:
> **how discounts compose.** Every candidate writes Cart and CartItem. The SDE-3 separates
> by introducing `DiscountStrategy` as a Strategy chain with a defined application order,
> knowing that two discount types (BOGO and percentage-off) must apply in a specific
> sequence to produce the right number.
>
> **The one distinction to make in the first 60 seconds:** a `CartItem` must snapshot the
> unit price at add-time, not reference the live product price. Product prices change;
> what the user agreed to pay does not. Every other design decision flows from there.

---

## Index

| § | Section |
|---|---------|
| §1 | 🎯 Problem Statement |
| §2 | ❓ Clarifying Questions |
| §3a | 🏗️ LLD — Construction Guide (7 Moves) |
| §3b | 🏗️ LLD — Complete Class Diagram |
| §4 | 🧭 Design Decisions |
| §5 | 🔌 Key Interfaces |
| §6 | ⚙️ Code — Three Methods |
| §7 | 🔁 Concurrency |
| §8 | 🧨 Java Depth Probes |
| §9 | 🌐 HLD — This Is an OOP Problem |
| §10 | 🏛️ If Asked to Scale |
| §11 | 📡 API Design (brief) |
| §12 | 🛤️ Happy + Unhappy Paths |
| §13 | 🔧 Fault Tolerance (brief) |
| §14 | 🔬 Q&A — JPMC Probes |
| §15 | 🧾 TL;DR |
| §16 | 🔄 Changelog |

---

## §1 — 🎯 Problem Statement

Design a shopping cart. Users browse products, add items with quantities, and at checkout see a final total that reflects applied discounts. The system must support multiple discount types — percentage off, flat amount off, buy-X-get-Y-free — that may be active simultaneously.

**The one-line framing to say out loud in the interview:**
> *"This is a discount-composition OOP problem. A Cart owns CartItems, each of which
> snapshots the unit price at add-time so that price changes never silently alter a
> user's session. The hard part is how multiple DiscountStrategy implementations
> apply in a defined order to produce the correct final total."*

---

## §2 — ❓ Clarifying Questions

**Functional scope**

1. What discount types are in scope — percentage off, flat amount, BOGO, category discount, combo? (defines which `DiscountStrategy` implementations to design)
2. Can multiple discounts apply to the same cart simultaneously? If yes, in what order — item-level first, then cart-level? (the order-of-application question is the whole problem)
3. Can discounts stack (e.g., 10% off AND $5 flat off), or is only one discount active per cart?
4. Is inventory availability checked at checkout, or at add-to-cart? (defines whether `CartItem` needs a reserved-stock concept)

**User behavior**

5. Can a user have multiple active carts? (most systems: one active cart per user)
6. What happens if a user adds the same product twice — two line items, or quantity merge?

**Lifecycle**

7. How long does a cart persist? (drives abandoned-cart cleanup strategy)
8. Is guest checkout (no user account) in scope?

**Out of scope for this round**

9. Payment processing, order management, delivery scheduling — confirm out of scope.

---

## §3a — 🏗️ LLD — Construction Guide (7 Moves)

---

**Move 1 — List domain nouns — don't draw yet**

Read the statement, then do **two passes**: one for nouns in the problem, one for entities the constraints force.

**From the statement directly:** Cart, Product, User, Discount

**Derived — say the reason out loud:**
- *"A cart contains products with quantities — that relationship is its own thing, not just a list on Cart. The relationship also needs a price snapshot."* → **CartItem** (a Product in a specific Cart with a specific quantity and a snapshotted price)
- *"The price must be snapshotted at add-time because product prices change. If I reference `Product.basePrice` at checkout, a price hike between add and checkout silently changes what the user agreed to pay."* → **CartItem.unitPrice** (the snapshot field that changes nothing else but is the most important field in the model)
- *"There are multiple discount types — percentage, flat, BOGO — and they differ in HOW they compute savings. If I if-else this inside PricingEngine, every new discount type edits the engine. Each type is a different algorithm."* → **DiscountStrategy** (the Strategy pattern)
- *"Cart has a lifecycle: created, active, checked out, abandoned."* → **CartStatus** enum

> **Say:** "`CartItem.unitPrice` is the non-obvious field. I am snapshotting the price — not referencing `Product.basePrice` — because a user's cart is a contract: 'I agreed to pay this price when I clicked Add.' `DiscountStrategy` is the entity I'm adding because the discount-composition question is the whole interview."

**Your board at the end of Move 1:**

```
From statement:  Cart, Product, User, Discount
Derived:         CartItem (line-item entity with snapshotted price),
                 CartStatus (lifecycle enum),
                 DiscountStrategy (algorithm abstraction for each discount type)
```

---

**Move 2 — Classify each noun: entity / enum / service**

`CartStatus` and `DiscountType` are finite → enums. `Cart`, `CartItem`, `Product`, `Discount` are entities. Now add **services** — from asking *"who does the work?"*:

- *"Something orchestrates add/remove/checkout — that is `CartService`."*
- *"Something computes the final price by running all strategies — that is `PricingEngine`."* (not an interface — the computation logic is deterministic)
- *"Something checks whether a product is available to sell — that is `InventoryService`."* (interface — the real check hits a warehouse DB; in tests I want a stub)

> **Say:** "`PricingEngine` is a concrete class because the pricing logic is deterministic — given the same cart and same strategies, the result is always the same. I don't need to swap it. `InventoryService` is an interface because the real implementation hits an external warehouse system and I want to test checkout without that dependency."

**Your board at the end of Move 2:**

```
enum:    CartStatus (ACTIVE · CHECKED_OUT · ABANDONED),
         DiscountType (PERCENTAGE_OFF · FLAT_AMOUNT · BUY_X_GET_Y · CATEGORY_PERCENT)
entity:  Cart, CartItem, Product, Discount
service: DiscountStrategy (iface), InventoryService (iface),
         PricingEngine, CartService
```

---

**Move 3 — Draw enums first. Explain non-obvious states.**

`CartStatus` is simple: ACTIVE → CHECKED_OUT or ABANDONED. But defend the transition rules.

> **Why no intermediate state like `CHECKOUT_IN_PROGRESS`?** For a pure OOP problem, the interviewer does not expect payment retry logic. If they ask: "I would add `CHECKOUT_IN_PROGRESS` in a real system — it separates 'user clicked checkout' from 'payment confirmed' — but I am leaving it out to keep the state machine focused on the OOP design."

The `DiscountType` enum names the types that exist. The *behavior* of each type lives in the corresponding `DiscountStrategy` implementation.

> **Say:** "I keep `DiscountType` as an enum on the `Discount` entity so I can store what type a discount is in the DB. But the *computation* of how that type affects the price lives in `DiscountStrategy`, not on the entity. Data and algorithm are separate."

**Your board at the end of Move 3:**

```
CartStatus:
  ACTIVE ─▶ CHECKED_OUT   (successful checkout)
  ACTIVE ─▶ ABANDONED     (TTL expired, no activity)

DiscountType: PERCENTAGE_OFF · FLAT_AMOUNT · BUY_X_GET_Y · CATEGORY_PERCENT
  ↑ stored in DB on the Discount entity
  ↑ the computation for each type lives in DiscountStrategy implementations
```

---

**Move 4 — Draw entities smallest → largest. Name what each knows + can do.**

`CartItem` (smallest) → `Discount` (independent data record) → `Cart` (aggregate root). `Product` is referenced by `CartItem`, not owned.

> **For `CartItem.unitPrice`:** "This is a snapshot — a `BigDecimal` copied from the product's price at the moment the user clicked Add. After that, `CartItem.unitPrice` never changes, regardless of what happens to `Product.basePrice`. This is the field that makes the cart reliable."

> **For `Cart.addItem`:** "If the same product is added twice (two tabs, two clicks), the service must merge the quantity into the existing `CartItem` — not create a second row. The DB constraint `UNIQUE(cart_id, product_id)` enforces this at the storage level."

> **BigDecimal rule:** "I use `BigDecimal` for every monetary field — `unitPrice`, `value` on Discount, `lineTotal`, `orderTotal`. `double` has floating-point errors that are unacceptable for money."

**Your board at the end of Move 4:**

```
CartItem
  - cartItemId: String
  - productId: String          ← reference only, NOT Product object (avoid eager load)
  - quantity: int
  - unitPrice: BigDecimal      ← SNAPSHOT at add-time; never changes
  + addQuantity(int n)         ← used by Cart.addItem for merge
  + getLineTotal()             → unitPrice × quantity

Discount (the DB record)
  - discountId, type: DiscountType, value: BigDecimal
  - conditions: { minQuantity, applicableProductIds, categoryId, minCartTotal }
  - expiresAt: Instant

Cart (aggregate root)
  - cartId, userId
  - items: List<CartItem>
  - status: CartStatus
  - createdAt, updatedAt
  + addItem(productId, quantity, unitPrice)  ← merge or create
  + removeItem(productId)
  + transition(CartStatus next)
```

---

**Move 5 — Identify variable behavior. Extract interfaces.**

The only variable behavior is **how each discount type computes its savings**, and **how inventory is checked**.

> **Why `DiscountStrategy` is an interface (Strategy pattern):** "Each discount type has a completely different algorithm. BOGO looks at the quantity of a specific product in the line items. Percentage-off multiplies the current subtotal by a rate. Flat amount checks if the cart meets a minimum threshold. If I put all of this in one method with `if (type == BOGO) ... else if (type == PERCENTAGE) ...`, every new discount type edits the same class. With Strategy, a new type is a new class with zero edits elsewhere."

> **The critical design decision on the interface signature:**
> `BigDecimal apply(List<LineItem> lines, BigDecimal currentSubtotal)`
> — strategies receive **both** the line items (for item-level checks like BOGO) and the **running subtotal after all prior strategies** (for cart-level checks like percentage-off). This enables sequential composition: each strategy operates on the already-reduced total, not the original.

**Your board at the end of Move 5:**

```
interface DiscountStrategy {
    BigDecimal apply(List<LineItem> lines, BigDecimal currentSubtotal);
    String getDiscountId();
}
   ├─ PercentageOffStrategy   (uses currentSubtotal)
   ├─ FlatAmountStrategy      (uses currentSubtotal + minimum threshold)
   ├─ BuyXGetYStrategy        (uses lines: quantity of target product)
   └─ CategoryPercentStrategy (uses lines: filter by category, reduce line totals)

interface InventoryService {
    boolean isAvailable(String productId, int quantity);
}
```

---

**Move 6 — Add the orchestrating service last. Its constructor deps = your design.**

`CartService` orchestrates the user-facing operations. `PricingEngine` is a pure computation class with no external deps.

> **Why does `PricingEngine` take `List<DiscountStrategy>` at call time, not in its constructor?** "Because the active strategies are determined at checkout time — different coupons are applied to different carts. The engine does not own the discount selection; `CartService` does. The engine only knows how to run the pipeline."

**Your board at the end of Move 6:**

```
PricingEngine   (no external deps — pure computation)
  + calculateTotal(Cart cart, List<DiscountStrategy> strategies) : OrderSummary

CartService (cartRepo, productRepo, inventoryService, pricingEngine, discountRepo)
  + addItem(userId, productId, quantity) : Cart
  + removeItem(userId, productId)        : Cart
  + checkout(userId)                     : OrderSummary
```

---

**Move 7 — Name the hot resource. One sentence tying all locks to it.**

There is no shared hot resource between users (each user owns their own cart). The concurrency concern is **within a single user's session**: two browser tabs simultaneously adding the same product.

> **Say:** "The contended resource is `Cart.items` — specifically the `CartItem` for a given product. I guard it with two layers: a `UNIQUE(cart_id, product_id)` DB constraint (the second insert fails cleanly), and `@Version` optimistic lock on `Cart` (the second concurrent `addItem` call gets an `OptimisticLockException` and is retried by the service). The DB constraint is the safety net; the OCC lock is the first line of defense."

**Your board at the end of Move 7:**

```
HOT RESOURCE: CartItem row for a given (cart_id, product_id) pair
  Layer 1: @Version on Cart entity → OptimisticLockException on concurrent addItem
  Layer 2: UNIQUE(cart_id, product_id) in DB → second INSERT fails cleanly
  → exactly one CartItem per product per cart; quantity is the merge field
```

---

### 75% Rule — What to Draw First If Time Is Short

```
Priority 1 — must reach (10 min):
  • CartItem.unitPrice as a SNAPSHOT (the most important design decision)
  • DiscountStrategy interface with the two-arg signature (lines + currentSubtotal)
  • PercentageOffStrategy + BuyXGetYStrategy as concrete impls
  • PricingEngine.calculateTotal sequential pipeline
  • Cart.addItem with quantity merge

Priority 2 — draw if time allows:
  • CartStatus state machine (simple, quick to draw)
  • InventoryService interface (brief)
  • Discount entity (type + conditions)

Priority 3 — verbally mention, never draw:
  • BigDecimal rule (say it once, don't write code for it)
  • Guest cart vs. logged-in cart (say it if asked about persistence)
```

---

## §3b — 🏗️ LLD — Complete Class Diagram — What You're Building Toward

```
┌─────────────────────────────────────────────────────────────┐
│ «enum» CartStatus                                            │
│   ACTIVE · CHECKED_OUT · ABANDONED                           │
└─────────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────────┐
│ «enum» DiscountType                                          │
│   PERCENTAGE_OFF · FLAT_AMOUNT · BUY_X_GET_Y ·              │
│   CATEGORY_PERCENT                                           │
└─────────────────────────────────────────────────────────────┘

┌───────────────────────────────────────────────┐
│ CartItem                                       │
├───────────────────────────────────────────────┤
│ - cartItemId: String                          │
│ - productId: String    (reference, not object) │
│ - quantity: int                               │
│ - unitPrice: BigDecimal  ← SNAPSHOT           │
├───────────────────────────────────────────────┤
│ + addQuantity(int n): void                    │
│ + getLineTotal(): BigDecimal   (qty × price)  │
└──────────────────────┬────────────────────────┘
                       │ 1..* owned by
                       │
┌──────────────────────▼────────────────────────┐
│ Cart                         «aggregate root»  │
├───────────────────────────────────────────────┤
│ - cartId: String                              │
│ - userId: String                              │
│ - items: List<CartItem>                       │
│ - status: CartStatus                          │
│ - @Version: int            ← OCC guard        │
│ - createdAt, updatedAt: Instant               │
├───────────────────────────────────────────────┤
│ + addItem(productId, qty, unitPrice): void    │
│ + removeItem(productId): void                 │
│ + transition(CartStatus next): void           │
└───────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│ Discount                  «DB record of discount config» │
├─────────────────────────────────────────────────────────┤
│ - discountId: String                                    │
│ - type: DiscountType                                    │
│ - value: BigDecimal       (% rate or flat amount)       │
│ - minCartTotal: BigDecimal (optional threshold)         │
│ - applicableProductIds: List<String>                    │
│ - categoryId: String      (optional)                    │
│ - expiresAt: Instant                                    │
└─────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────┐
│ «interface» DiscountStrategy                    │
│ + apply(lines: List<LineItem>,                  │
│         currentSubtotal: BigDecimal): BigDecimal│
│ + getDiscountId(): String                       │
└────────────────────────────────────────────────┘
   ├─ PercentageOffStrategy    (% of currentSubtotal)
   ├─ FlatAmountStrategy       (fixed $X if threshold met)
   ├─ BuyXGetYStrategy         (inspect lines: qty of target product)
   └─ CategoryPercentStrategy  (inspect lines: filter by category)

┌────────────────────────────────────────────────┐
│ «interface» InventoryService                    │
│ + isAvailable(productId, qty): boolean          │
└────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────┐
│ PricingEngine        (no external deps — pure computation)    │
│  + calculateTotal(cart, strategies): OrderSummary            │
└──────────────────────────────────────────────────────────────┘
┌──────────────────────────────────────────────────────────────┐
│ CartService                                                   │
│  deps: CartRepo, ProductRepo, InventoryService,               │
│        PricingEngine, DiscountRepo                            │
│  + addItem(userId, productId, quantity): Cart                 │
│  + removeItem(userId, productId): Cart                        │
│  + checkout(userId): OrderSummary                             │
└──────────────────────────────────────────────────────────────┘

KEY INVARIANT: CartItem.unitPrice is a snapshot set at add-time and never updated.
Discounts are applied by PricingEngine in a defined order (item-level first, then
cart-level), each strategy receiving the running subtotal after all prior strategies.
```

---

## §4 — 🧭 LLD — Design Decisions

| Decision | Why this | What I rejected and why |
|---|---|---|
| **`CartItem.unitPrice` is a snapshot** | Product prices change; the user agreed to the price at add-time | Reference `Product.basePrice` at checkout — a price hike between add and checkout silently changes the total; user sees a different number than what they clicked |
| **`DiscountStrategy` as a Strategy chain** | Each discount type has a different algorithm; new type = new class, zero edits to `PricingEngine` | `if-else` on `DiscountType` inside `PricingEngine` — OCP violation; every new discount type edits the engine |
| **Sequential pipeline: each strategy receives `currentSubtotal`** | BOGO reduces item-count first; percentage-off should apply to the already-BOGOed subtotal, not the original | Additive/independent model (each strategy computes against original subtotal, savings are summed) — gives a different (wrong) number when strategies interact |
| **Item-level strategies before cart-level** | The correct order: reduce specific items first (BOGO, category%), then apply cart-wide percentage to the reduced subtotal | Cart-level percentage first — applies to the full original price, then item discounts reduce further; over-discounts the cart |
| **`productId: String` on CartItem, not `Product` object** | Avoids eagerly loading the full product graph when a cart is fetched; product data is fetched when needed | `CartItem { Product product }` — fetching a cart triggers a join to the product catalog, even when you only need quantities |
| **`@Version` + `UNIQUE(cart_id, product_id)`** | Two layers of concurrency protection for the quantity-merge case; DB constraint is the absolute safety net | Only service-level sync — race condition survives to the DB; constraint prevents the duplicate row at storage level |
| **`BigDecimal` for all monetary values** | Exact decimal arithmetic; no rounding artifacts | `double` — `0.1 + 0.2 = 0.30000000000000004` at the DB layer; unacceptable for financial records |

---

## §5 — 🔌 LLD — Key Interfaces

| Interface | Contract |
|---|---|
| `DiscountStrategy` | Sequential-pipeline discount computation. Receives the **current** line items and the **running subtotal** (after all prior strategies). Returns the amount saved (zero if not applicable). |
| `InventoryService` | Checks real-time stock availability for a (product, quantity) pair. |

```java
public interface DiscountStrategy {
    // Receives the cart's line items and the running subtotal AFTER all prior strategies.
    // Returns the amount saved by this strategy (BigDecimal.ZERO if not applicable).
    // Each strategy's saving is computed against currentSubtotal — NOT the original price.
    BigDecimal apply(List<LineItem> lines, BigDecimal currentSubtotal);

    String getDiscountId();
}

public interface InventoryService {
    // Returns true if at least `quantity` units of `productId` are available.
    // Called at checkout; result must be re-checked under a DB lock before decrement.
    boolean isAvailable(String productId, int quantity);
}
```

---

## §6 — ⚙️ LLD — Code to Write

Three methods carry the design: the **sequential discount pipeline**, the **quantity-merge add**, and the **state machine guard**.

---

### 1. The sequential discount pipeline — `PricingEngine.calculateTotal`

**Steps in plain English:**

1. **Build line items** from the cart's CartItems using the snapshotted unit prices.
2. **Compute the initial subtotal** — sum of all line totals.
3. **Run each strategy in order.** Each strategy receives the *current* running subtotal (not the original). If it saves money, reduce the running subtotal and record the application.
4. **Return the summary.** The total discount is `subtotal − finalTotal`. The final total cannot go below zero.

```java
public OrderSummary calculateTotal(Cart cart, List<DiscountStrategy> strategies) {
    // Step 1 — build line items from price snapshots (never from live Product)
    List<LineItem> lines = cart.getItems().stream()
        .map(item -> LineItem.of(
            item.getProductId(), item.getQuantity(),
            item.getUnitPrice(),
            item.getLineTotal()))      // unitPrice × quantity; BigDecimal arithmetic
        .collect(Collectors.toList());

    // Step 2 — initial subtotal
    BigDecimal subtotal = lines.stream()
        .map(LineItem::getLineTotal)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    // Step 3 — sequential pipeline: each strategy sees the ALREADY-REDUCED running total
    BigDecimal runningTotal = subtotal;
    List<DiscountApplication> applied = new ArrayList<>();
    for (DiscountStrategy strategy : strategies) {
        BigDecimal saving = strategy.apply(lines, runningTotal);
        if (saving.compareTo(BigDecimal.ZERO) > 0) {
            runningTotal = runningTotal.subtract(saving).max(BigDecimal.ZERO);
            applied.add(DiscountApplication.of(strategy.getDiscountId(), saving));
        }
    }

    // Step 4 — summary; discount = subtotal minus the final running total
    return OrderSummary.of(
        cart.getCartId(), lines,
        subtotal,
        subtotal.subtract(runningTotal),   // total discount
        runningTotal,                      // final price
        applied);
}
```

> **The key line to explain in interview:** `strategy.apply(lines, runningTotal)` — passing `runningTotal`, not `subtotal`. This is what makes the pipeline sequential: PercentageOffStrategy applied second operates on the already-BOGO-reduced total.

---

### 2. The quantity-merge add — `Cart.addItem`

**Steps in plain English:**

1. **Check if the product already has a CartItem.** If yes: merge (add to the existing quantity; keep the original unit price).
2. **If not present:** create a new CartItem with a snapshot of the current price.

```java
public void addItem(String productId, int quantity, BigDecimal unitPriceNow) {
    // Step 1 — look for an existing line item for this product
    Optional<CartItem> existing = items.stream()
        .filter(item -> item.getProductId().equals(productId))
        .findFirst();

    if (existing.isPresent()) {
        // Merge: add to the existing quantity.
        // Deliberately keep the ORIGINAL unit price — not unitPriceNow.
        // The user agreed to the price they saw first; do not silently update it.
        existing.get().addQuantity(quantity);
    } else {
        // New line item: snapshot the price at this moment
        items.add(CartItem.create(productId, quantity, unitPriceNow));
    }
    this.updatedAt = Instant.now();
}
```

> **The line to defend:** `existing.get().addQuantity(quantity)` with the comment about keeping the original price. An interviewer will ask: "What if the price changed between the first add and the second add?" Answer: "We keep the original. The user clicked Add at a price. If we update the price on a merge, we change the deal mid-session — that is the wrong behavior. The price only moves when the user explicitly removes and re-adds the item."

---

### 3. The state machine guard — `Cart.transition`

```java
private static final Map<CartStatus, Set<CartStatus>> LEGAL = Map.of(
    CartStatus.ACTIVE, Set.of(CartStatus.CHECKED_OUT, CartStatus.ABANDONED)
    // CHECKED_OUT and ABANDONED are terminal — no transitions out
);

public void transition(CartStatus next) {
    Set<CartStatus> allowed = LEGAL.getOrDefault(this.status, Set.of());
    if (!allowed.contains(next)) {
        throw new IllegalStateException(
            "Illegal transition: " + this.status + " -> " + next);
    }
    this.status = next;
}
```

---

## §7 — 🔁 LLD — Concurrency

| Shared field | What breaks without a guard | Fix |
|---|---|---|
| `Cart.items` (same product, two tabs) | Two concurrent `addItem` calls for the same product both see no existing line → both insert → duplicate CartItem rows → total incorrect | `@Version` OCC on Cart — the second writer gets `OptimisticLockException`; service retries the merge. `UNIQUE(cart_id, product_id)` is the DB-level safety net. |
| `Product.stockCount` at checkout | Inventory checked at time of `addItem`, but stock may be depleted by the time checkout runs | Re-check stock at checkout inside a `SELECT FOR UPDATE` on the product row; only then decrement. The `isAvailable()` call in `addItem` is optimistic (fast); the checkout check is pessimistic (correct). |

**Why the two-layer approach?**
The `addItem` inventory check is a fast UX check — show the user immediately if the item is out of stock. It is NOT a reservation. The checkout inventory check under `SELECT FOR UPDATE` is the authoritative check. A user can add an item to the cart that sells out before they check out — that is an expected, handled failure, not a bug.

---

## §8 — 🧨 Java Depth Probes

| What you say in design | What they immediately ask | Your answer |
|---|---|---|
| "unit price is snapshotted" | "What if the admin drops the price after add — does the user get the lower price?" | No — unless the business rule says so. The snapshot is what the user agreed to. If the business wants to honor price drops, that is a policy choice, not the default. I'd expose a `refreshPrices()` on the cart that a product-price-change event triggers. |
| "sequential pipeline, item-level before cart-level" | "Who decides the order of strategies?" | `CartService.checkout()` builds the list in the defined order: item-level discounts first (BuyXGetY, category%), then cart-level (percentage-off subtotal, flat amount). The order is a product/business decision, not an algorithmic one — I make it explicit in code so any engineer can see and change it. |
| "BigDecimal for money" | "Why not use int (cents)?" | Either works. `int` cents is slightly faster and simpler for exact arithmetic. `BigDecimal` is more natural for business rule expressions (0.10 vs 10 cents). I'd use `BigDecimal` in Java with `HALF_UP` rounding; if the team has a preference for cents-as-int, I'd adapt. The key is: not `double`. |
| "`@Version` OCC on Cart" | "What does the service do on `OptimisticLockException`?" | Re-load the cart from the DB (which now has the winning add) and retry the `addItem` — this time it will hit the merge path since the item now exists. One retry is enough; the window is tiny. |
| "BOGO looks at line items, not subtotal" | "How does BuyXGetYStrategy compute its saving?" | It finds the CartItem for the target product, computes `freePairs = quantity / (buyX + getY)`, and returns `freePairs * getY * unitPrice`. It uses `lines`, not `currentSubtotal`. That is why the interface takes both — item-level strategies ignore `currentSubtotal`; cart-level strategies ignore `lines`. |

---

## §9 — 🌐 HLD — This Is an OOP Problem

**The interviewer does not expect distributed systems for Shopping Cart.** If the interviewer asks "how would you scale this?", that is the only HLD question you will get. See §10.

Do NOT volunteer HLD components (Kafka, Redis, microservices) during the LLD phase — the interviewer will think you are deflecting from the OOP depth they are probing.

---

## §10 — 🏛️ If Asked to Scale

**The one HLD question that naturally comes up:** "Where do you store the cart?"

| Approach | When to use | Trade-off |
|---|---|---|
| **Redis session cart** | Guest users, anonymous browsing | Fast, no auth required. Cart lost if session expires. Max TTL = session length (e.g., 24h). |
| **DB-persisted cart (MySQL)** | Logged-in users | Durable, shareable across devices. Survives logout/re-login. Requires user auth on every cart write. |
| **Hybrid** | Most e-commerce systems | Guest cart in Redis; on login, merge Redis cart into the DB cart. |

**If asked about the checkout inventory check at scale:**
> "At checkout, I run `SELECT stockCount FROM product WHERE id = ? FOR UPDATE`, decrement if available, and commit. This serializes checkouts for the same product on that DB shard. For a hot SKU (flash sale), I would move to a Redis DECRBY with a floor of 0 — atomically decrement, if the result goes negative, undo (INCRBY 1) and reject."

---

## §11 — 📡 API Design (brief)

```
POST /v1/carts/{cartId}/items
Body: { productId, quantity }
Response: 200 OK — updated cart with line items and quantities

DELETE /v1/carts/{cartId}/items/{productId}
Response: 200 OK

POST /v1/carts/{cartId}/checkout
Body: { couponCodes: ["SAVE10", "BOGO_SHOES"] }  ← caller specifies discounts to apply
Response: 200 OK  OrderSummary { subtotal, totalDiscount, finalTotal, linesApplied }
          409 Conflict — one or more items out of stock (itemized list of which ones)
```

---

## §12 — 🛤️ Happy + Unhappy Paths

**Happy path — add item, checkout with two discounts:**
1. `CartService.addItem("user123", "shoe-42", 2, 89.99)` → Cart has one CartItem, qty=2, unitPrice=89.99 (snapshot)
2. Admin runs a flash sale; `shoe-42` price drops to 79.99. Cart still shows 89.99 — the snapshot is unchanged.
3. `CartService.checkout("user123")` → builds strategies: [BuyXGetYStrategy(shoe-42, buy1get1 — classic BOGO), PercentageOffStrategy(10%)]
4. PricingEngine runs: subtotal = $179.98. BOGO formula: `freePairs = qty / (buyX + getY) = 2 / (1+1) = 1`. Saves 1 × $89.99 = $89.99 → runningTotal = $89.99. 10% off $89.99 = $9.00 → finalTotal = $80.99.
5. InventoryService confirms 2 units available → stock decremented → OrderSummary returned.

**Unhappy path — item sold out at checkout:**
→ `InventoryService.isAvailable("shoe-42", 2)` returns false at checkout
→ `CartService.checkout` throws `OutOfStockException` listing the unavailable items
→ Cart remains ACTIVE — user can remove out-of-stock item and retry
→ The cart is NOT transitioned to CHECKED_OUT

**Unhappy path — two tabs add same product simultaneously:**
→ Tab A: `addItem(productId="shoe-42", qty=1)` → hits `@Version` lock → inserts CartItem (qty=1)
→ Tab B: `addItem(productId="shoe-42", qty=1)` → hits stale `@Version` → `OptimisticLockException`
→ Service retries Tab B: reloads cart (now has CartItem qty=1) → merges → CartItem qty=2
→ Final: one CartItem with qty=2, correct

---

## §13 — 🔧 Fault Tolerance (brief)

This is an OOP problem — fault tolerance is minimal. Mention these only if asked:

| Concern | What breaks | What you add |
|---|---|---|
| Inventory check at checkout | Stock depleted between `isAvailable()` and decrement | `SELECT FOR UPDATE` on the product row during decrement — authoritative check under lock |
| Cart session expiry | User returns after 24h — Redis cart gone | Persist cart to DB at reasonable intervals; or require login for carts > 1h old |
| Price change during active cart | Product price drops; user still paying old price | Fire a cart-update event on price change; let user accept new price or check out at old price — business policy decision |

---

## §14 — 🔬 Q&A — JPMC Probes

### Q: "Walk me through how your discount pipeline handles BOGO + 10% off."
> I build the strategy list: [BuyXGetYStrategy first, PercentageOffStrategy second]. `PricingEngine.calculateTotal` runs them in order. BOGO operates on the line items directly — it finds the target product, computes `freePairs = quantity / (buyX + getY)`, and returns `freePairs × getY × unitPrice` as savings. That saving is subtracted from `runningTotal`. PercentageOffStrategy then receives the already-reduced `runningTotal` and returns 10% of that. The two discounts are applied sequentially, not additively.

### Q: "Why does PercentageOffStrategy need `currentSubtotal` — why not just look at the line items?"
> It could sum the line items itself, but then it would apply to the *original* subtotal, not the *post-BOGO* subtotal. By receiving `currentSubtotal` (which is already reduced by BOGO), the percentage applies correctly to what the user actually owes after item-level discounts. That is the whole point of the sequential interface signature.

### Q: "What if the same product is in the cart and a BOGO and a flat-amount discount both apply?"
> They are applied in order. BOGO reduces the line total for the specific product. The flat-amount strategy then checks if `currentSubtotal` meets its minimum threshold. If BOGO brought the subtotal below the threshold, the flat discount does not apply — it returns `BigDecimal.ZERO`. That is a correct business outcome: the discounts are ordered, and each strategy decides its own applicability against the state left by prior strategies.

### Q (OOP depth): "Can you violate the state machine from outside the Cart?"
> Not if the entity owns it. `Cart.status` is private; the only way to change it is through `transition()`, which enforces the legal-transitions map. No external code can write `cart.status = CHECKED_OUT` — it does not have field access. That is the point of encapsulating the state machine in the entity.

---

## §15 — 🧾 TL;DR — 30-Second Pitch

> "Shopping Cart is a discount-composition OOP problem. The two design decisions that matter:
> one, `CartItem.unitPrice` is a **snapshot** — not a reference to the live product price,
> because the user agreed to a specific price at add-time. Two, discounts are a
> **Strategy chain** applied sequentially: item-level (BOGO, category%) first, then
> cart-level (percentage off subtotal, flat amount). The sequential order matters — each
> strategy receives the `currentSubtotal` after all prior strategies, so percentage-off
> applies to the post-BOGO total, not the original. The `PricingEngine` is a pure
> computation class; `CartService` decides which strategies apply and in what order.
> `BigDecimal` for all monetary values — never `double`. The only concurrency concern is
> two tabs adding the same product: `@Version` OCC plus a `UNIQUE(cart_id, product_id)`
> DB constraint prevents duplicate rows."

---

## §16 — 🔄 Changelog

| Date | Change |
|------|--------|
| Aug 17, 2026 | Note created. JPMC Round 3 OOP-only (Blind panelist confirmed). Full 16-section arc: CartItem price snapshot + sequential DiscountStrategy pipeline (item-level before cart-level) + addItem quantity merge + CartStatus state machine + @Version + UNIQUE constraint for concurrency. No HLD. §9–§13 kept brief per round format. All 7 moves include derivation reasoning. |
