# Relational Data Modeling

> **Standard followed:** `SystemDesignConcepts/notes-standards.md`

---

## 🎯 Why This Matters

Every system design question ends with "what does your schema look like?" Interviewers use schema design to test whether you understand data integrity, read/write patterns, and performance at scale — not just which tables to create. DocuSign confirmed C2 (Expense Report) and C3 (Pagination API) both required data model design. Getting the schema right is the difference between a system that works at 1,000 rows and one that works at 100 million.

**Which round:** R2 System Design — schema design is the close of almost every design answer.
**Why senior engineers own this:** Junior engineers add tables until the query works. Senior engineers design schemas where the hard queries are efficient, the invariants are enforced at the database layer, and denormalization decisions are conscious trade-offs, not accidents.

---

## 📖 What is Data Modeling?

**Full form:** Relational Data Model / Schema Design

**Simple analogy:** Think of a relational database like a government office with filing cabinets. Each cabinet type holds one kind of record (births, taxes, property). Each record has slots with values (name, date, reference number). Good filing = fast lookups + no contradictions. Bad filing = documents scattered everywhere + data corruption.

**Core principle:** Data modeling defines HOW data is structured and WHAT rules protect it (tables, columns, keys, constraints, relationships). A good schema is efficient at scale, enforces integrity (no duplicates, no orphaned records), and supports the system's read/write patterns.

**Why it matters in system design:** A poorly designed schema breaks at 10M rows. A well-designed schema scales to 1B rows. Schema design is where architectural understanding meets database internals.

---

## 🎨 Visual — System Topology: Data Modeling in Architecture

```
APPLICATION / SERVICES
    │
    │ Queries:
    │ "Get expense reports for user 123"
    │ "Update report status to APPROVED"
    │ "Sum expenses by category"
    │
    ▼
┌────────────────────────────────────┐
│ ORM / Query Builder                │
│ (Hibernate, MyBatis, SQLAlchemy)   │
│                                    │
│ Translates app logic into SQL      │
└────────────────┬───────────────────┘
                 │
                 ▼ SQL queries
        ┌──────────────────────┐
        │ Database Server      │
        │                      │
        │ ┌──────────────────┐ │
        │ │   users table    │ │
        │ ├──────────────────┤ │
        │ │ id | name | dept │ │
        │ └──────────────────┘ │
        │          ▲ FK        │
        │          │           │
        │ ┌──────────────────┐ │
        │ │ expense_reports  │ │
        │ ├──────────────────┤ │
        │ │ id | user_id     │ │ ← Foreign Key to users
        │ │ | status | total │ │   (enforces referential integrity)
        │ └──────────────────┘ │
        │          ▲ FK        │
        │          │           │
        │ ┌──────────────────┐ │
        │ │ expense_items    │ │
        │ ├──────────────────┤ │
        │ │ id | report_id   │ │ ← Foreign Key to reports
        │ │ | category_id    │ │   (cascade delete if report deleted)
        │ └──────────────────┘ │
        │                      │
        │ INDEXES:            │
        │ - (user_id) → fast   │
        │ - (status) → filter  │
        │ - (created_at) → sort│
        └──────────────────────┘

KEY INVARIANT:
   Schema enforces structure & integrity (not application code)
   Foreign keys prevent orphaned records
   Indexes speed up frequent queries
   Normalization reduces redundancy; denormalization improves reads
```

---

## 🎨 Visual — Expense Report Schema (Detailed ERD) (Component Detail)

Think of a relational database as a **well-organised government office**.

The office keeps records — birth certificates, tax filings, property deeds. Each type of record lives in its own filing cabinet (a **table**). Each drawer in the cabinet holds one record (a **row**). Each row has labelled slots — name, date, reference number (the **columns**).

Now the key rules the office follows, and why:

**Rule 1 — No mixed bags in a slot (1NF — First Normal Form):**
Every slot holds exactly one value. If you need to store multiple phone numbers for a person, you don't cram them all in one slot separated by commas — you give phone numbers their own filing cabinet (their own table). Commas in a column = bad smell. Each slot, one value.

**Rule 2 — Everything in a cabinet is about one thing (2NF — Second Normal Form):**
If a cabinet is for "Tax Filings", every field in the drawer is about that filing. If you find yourself storing the taxpayer's address in the filing cabinet, stop — the address belongs in the "Taxpayer" cabinet, not the "Filing" cabinet. A field that belongs to only part of the key (or could exist without the record) is a signal to split.

**Rule 3 — Nothing is derived from a non-key field (3NF — Third Normal Form):**
If you can calculate a field from another field in the same row, don't store it — store the source and calculate on read. Storing `full_name` when you already store `first_name` and `last_name` is redundant. If `last_name` changes, you now have two places to update, and they can fall out of sync.

**Where the analogy breaks — deliberate denormalisation:**
Sometimes the office deliberately makes copies. Tax returns include the taxpayer's name and address even though those live in the Taxpayer cabinet. Why? Because tax returns are frozen in time — if the taxpayer moves, you still need to know their address as of the filing year. That's intentional denormalisation: copy the data when the historical snapshot matters more than staying in sync.

**Foreign keys — the cross-reference slip:**
When the Filing cabinet references the Taxpayer cabinet, it keeps a cross-reference slip: "See Taxpayer #4521." That slip is the **foreign key**. If Taxpayer #4521 is deleted, what happens to the filings? That's the **cascade rule**: delete the filings too (CASCADE DELETE), or block the deletion (RESTRICT), or set the reference to null (SET NULL).

**Indexes — the alphabetical tab dividers:**
The office adds tab dividers (A, B, C...) so clerks can find a taxpayer by name instantly — they don't scan every drawer. Those tab dividers are **indexes**. Fast lookups come at a cost: adding a new record now requires updating the tabs. More indexes = faster reads, slower writes.

**The key insight is:** Normalise to protect integrity. Denormalise deliberately when your read performance or snapshot requirements demand it. Know exactly which query you're optimising for before you add an index or a duplicate column.

---

## 🎨 Visual — Expense Report Schema (the confirmed DocuSign C2 question)

```
  EXPENSE REPORT SCHEMA — 3NF with indexes and FK rules
  ─────────────────────────────────────────────────────────────────

  ┌───────────────────────────────┐     ┌───────────────────────────────┐
  │           users               │     │         categories            │
  ├───────────────────────────────┤     ├───────────────────────────────┤
  │ id          BIGINT  PK        │     │ id          INT     PK        │
  │ email       VARCHAR UNIQUE    │     │ name        VARCHAR UNIQUE    │
  │ name        VARCHAR NOT NULL  │     │ description TEXT              │
  │ department  VARCHAR           │     └───────────────────────────────┘
  │ created_at  TIMESTAMP         │              ▲
  └───────────────────────────────┘              │ FK
            ▲                                    │
            │ FK (user_id)                       │ FK (category_id)
            │                                    │
  ┌─────────┴─────────────────────────────────────────────────────┐
  │                       expense_reports                          │
  ├────────────────────────────────────────────────────────────────┤
  │ id            BIGINT     PK  AUTO_INCREMENT                    │
  │ user_id       BIGINT     FK → users.id    NOT NULL             │
  │ title         VARCHAR    NOT NULL                              │
  │ status        ENUM('DRAFT','SUBMITTED','APPROVED','REJECTED')  │
  │ submitted_at  TIMESTAMP  NULL  (null until submitted)          │
  │ created_at    TIMESTAMP  NOT NULL DEFAULT NOW()                │
  └────────────────────────────────────────────────────────────────┘
            ▲
            │ FK (report_id)
            │
  ┌─────────┴─────────────────────────────────────────────────────┐
  │                       expense_items                            │
  ├────────────────────────────────────────────────────────────────┤
  │ id            BIGINT     PK  AUTO_INCREMENT                    │
  │ report_id     BIGINT     FK → expense_reports.id  NOT NULL     │
  │ category_id   INT        FK → categories.id       NOT NULL     │
  │ amount        DECIMAL(12,2)  NOT NULL  ← exact precision: 12 digits total, 2 after decimal │
  │ currency      CHAR(3)    NOT NULL  DEFAULT 'USD'               │
  │ description   TEXT                                             │
  │ receipt_url   VARCHAR    NULL                                  │
  │ expense_date  DATE       NOT NULL                              │
  └────────────────────────────────────────────────────────────────┘

  INDEXES:
  ─────────────────────────────────────────────────────────────────
  expense_reports → INDEX(user_id)              ← "my reports" query
  expense_reports → INDEX(status, submitted_at) ← "pending reports" queue
  expense_items   → INDEX(report_id)            ← "items in this report"
  expense_items   → INDEX(category_id)          ← "all travel expenses"

  KEY INVARIANT:
     Each table has ONE responsibility. expense_items don't store user_name
     (that's users.name). The total_amount of a report is calculated from
     expense_items — never stored as a column (would fall out of sync).
```

---

## ⚙️ How It Actually Works

### Part 1 — Normalisation Steps (using Expense Report as the worked example)

**Starting point — the bad flat table:**

```
// ❌ Bad: one table, everything jammed in
// report_id | user_email | user_name | category_name | amount | ...
// Problems:
//   - If user changes email, must update every row
//   - If category name changes, must update every row
//   - Can't query "all reports by user" without scanning all rows
//   - Duplicate data everywhere (user_name repeated per item)
```

**Steps to reach 3NF:**

1. **Apply 1NF** — one value per cell. If `category` stores `"Travel, Food"`, split to a separate `categories` table and a join table.
2. **Apply 2NF** — every non-key column depends on the WHOLE primary key. `user_name` doesn't depend on `report_id` — it depends on `user_id`. Move it to `users`.
3. **Apply 3NF** — no column depends on another non-key column. `total_amount` depends on the `expense_items` rows — don't store it, calculate it.

```java
// Clean schema in JPA — 3NF enforced by the entity design
@Entity
@Table(name = "expense_reports")
public class ExpenseReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // FK to users — not user_name, not user_email
    // FetchType.LAZY = do NOT load the User object from DB unless explicitly accessed.
    // Without LAZY, every time you load an ExpenseReport, JPA also runs a SELECT on users — wasteful.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String title;

    // @Enumerated(EnumType.STRING) = store the enum as the string "DRAFT"/"SUBMITTED" in DB.
    // Without it, JPA stores the ordinal integer (0, 1, 2...) — fragile if enum order changes.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportStatus status;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    // Items are in their own table — not a CSV column.
    // orphanRemoval = true: if you remove an item from this list and save, JPA deletes the DB row.
    // Without it, removing from the list only breaks the Java reference — the DB row remains (orphan).
    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ExpenseItem> items = new ArrayList<>();

    // total is calculated, NOT stored — never out of sync
    public BigDecimal calculateTotal() {
        return items.stream()
            .map(ExpenseItem::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
```

---

### Part 2 — Foreign Key Cascade Rules

When a parent row is deleted, the database needs a policy for dependent rows.

```java
// Three cascade options — pick deliberately, not by default

// OPTION 1: CASCADE DELETE — deleting a report deletes all its items
// Use when: child data has no meaning without the parent
@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)

// OPTION 2: RESTRICT (default in most DBs) — block deletion if children exist
// Use when: you must clean up children first (forces explicit data management)
// In SQL: FOREIGN KEY (report_id) REFERENCES expense_reports(id) ON DELETE RESTRICT

// OPTION 3: SET NULL — set FK to null when parent is deleted
// Use when: child records should survive, just disassociated
// In SQL: FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE SET NULL
```

---

### Part 3 — Indexing Strategy

```java
// Rule: index columns you filter by (WHERE), join by (FK), or order by (ORDER BY)
// Cost: every index slows down INSERT/UPDATE/DELETE — it must be updated too

// Single-column index — use when the column alone has high selectivity
@Index(name = "idx_reports_user_id", columnList = "user_id")

// Composite index — use when you always filter by BOTH columns together
// Query: WHERE status = 'SUBMITTED' ORDER BY submitted_at DESC
// Index on (status, submitted_at) serves this — NOT two separate indexes
@Index(name = "idx_reports_status_submitted", columnList = "status, submitted_at")

// Covering index — index includes all columns the query needs
// Query: SELECT id, title FROM expense_reports WHERE user_id = 5
// Index on (user_id, id, title) — DB never touches the table row at all
```

**The index decision test:** Write the exact query first. Look at the `WHERE`, `JOIN`, and `ORDER BY` clauses. Those columns are your index candidates.

### What is a B-tree index, and why does column order matter?

**A B-tree** (Balanced Tree) is the data structure behind almost every relational DB index. It's a sorted tree where each node branches into multiple children — like a phone book divided into sections A-F, G-M, N-Z, then subdivided further. Finding a value is O(log N) instead of O(N) full scan.

**Why this matters for composite indexes — the leftmost prefix rule:**
A B-tree index on `(status, submitted_at)` is like a phone book sorted first by last name, then by first name within each last name. You can look up "all Smiths" (filter on `status` alone) OR "all Smiths named John" (filter on both). But you cannot efficiently look up "all people named John" across all last names — there's no shortcut, you'd scan the whole book.

Same rule in DB: an index on `(status, submitted_at)` serves:
- `WHERE status = 'SUBMITTED'` ✅ (uses leftmost column)
- `WHERE status = 'SUBMITTED' ORDER BY submitted_at` ✅ (uses both columns)
- `WHERE submitted_at > '2026-01-01'` ❌ (skips leftmost column — full scan)

**In an interview, if asked:** "A B-tree index is a sorted tree structure the DB maintains alongside the table. Lookup is O(log N). For a composite index, the DB can only use it from the leftmost column — an index on (A, B, C) serves queries filtering on A, or A+B, or A+B+C, but NOT on B alone or C alone. This is why you design the composite index around your most selective and most-used filter column first."

---

### Part 4 — Schema Evolution: Adding Columns at Scale

When your production table grows to millions or billions of rows, adding or modifying columns is risky — a naive `ALTER TABLE ADD COLUMN` can lock the table for hours, blocking all reads and writes. Modern relational databases (Postgres 11+, MySQL 8.0.23+) have optimizations, but the safest pattern is the **add-null → backfill-in-batches → add-constraint** approach.

**The problem:** A table with 200 million rows. You need to add a new column `approved_by` (FK to users). Running `ALTER TABLE expense_reports ADD COLUMN approved_by BIGINT NOT NULL DEFAULT 1` on the live table:
- Postgres < 11: rewrites the entire table — hours of locking.
- MySQL with ALGORITHM=INPLACE: still acquires a metadata lock, blocking writes.
- Result: your API returns "database locked" errors to users.

**The safe pattern:**

**Step 1 — Add the column as nullable (metadata-only, fast):**

```sql
ALTER TABLE expense_reports ADD COLUMN approved_by BIGINT NULL;
```

In modern Postgres/MySQL, this is a metadata-only change — doesn't rewrite the table, completes in seconds.

**Step 2 — Backfill in batches (avoid lock contention):**

```sql
-- Backfill in chunks of 10,000 rows at a time
-- Allows other transactions to interleave writes
DECLARE @batchSize INT = 10000;
DECLARE @maxId BIGINT = (SELECT MAX(id) FROM expense_reports);

FOR @i = 0 TO @maxId STEP @batchSize BEGIN
    UPDATE expense_reports
    SET approved_by = 1  -- or any default logic
    WHERE id BETWEEN @i AND @i + @batchSize - 1
      AND approved_by IS NULL;
    
    -- Small sleep between batches to reduce I/O contention
    WAITFOR DELAY '00:00:00.100';
END
```

**Step 3 — Add the constraint once backfilled (now it's safe):**

```sql
ALTER TABLE expense_reports
MODIFY COLUMN approved_by BIGINT NOT NULL;

-- Optional: add the FK constraint
ALTER TABLE expense_reports
ADD CONSTRAINT fk_approved_by FOREIGN KEY (approved_by)
    REFERENCES users(id) ON DELETE SET NULL;
```

**Key invariants:**
- Every column add at scale is: **NULL add → batch backfill → constraint add**.
- Never do `ALTER TABLE ... ADD COLUMN ... NOT NULL DEFAULT value` on a large live table — forces a full rewrite.
- Batch size typically 1,000–50,000 rows depending on row width and load. Smaller batches = more interleaving, slower overall but less blocking.

**What is a database migration, and why are they risky?**

A **database migration** is a structured change to the schema — adding a column, changing a type, creating an index, etc. They're risky at scale because:
- **Lock contention:** A rewrite-based migration (like adding a NOT NULL column to a large table) acquires a lock, blocking all writes.
- **Rollback complexity:** If the migration fails halfway, rolling back can be as slow or slower than the forward migration.
- **Zero-downtime deployments:** You can't take the service offline to migrate the schema in microservices — you need the schema change to happen while the app is live.

The safest migrations use **expansion-then-contraction** pattern:
1. **Expansion:** Add the new schema element (column, table, index) without removing the old one.
2. **Soak period:** Let the new element exist while the old one is still active. Monitor for correctness.
3. **Switch traffic:** Update the application to use the new element.
4. **Contraction:** Only after traffic has moved, drop the old element.

**In an interview, if asked:** "Adding a column to a 200M-row table requires the add-null → batch-backfill → add-constraint pattern. Use small batches (10K–50K rows) to avoid lock contention. Never add a NOT NULL column with a rewrite — always add it as NULL first. Plan major schema changes as separate, reversible deployment steps. This is why many teams use tools like pt-online-schema-change (Percona Toolkit) or gh-ost to automate safe migrations."

---

### Part 5 — Many-to-Many Relationships (Junction Tables)

A many-to-many relationship — one booking can have many seats, one seat can be in many bookings — cannot be expressed with a single FK. You need a **junction table** (also called a join table or association table) that holds both FKs.

```
  EXAMPLE: Bookings ↔ Seats (BookMyShow)

  ┌─────────────┐         ┌──────────────────┐         ┌────────────────┐
  │  bookings   │         │  booking_seats   │         │    seats       │
  ├─────────────┤         ├──────────────────┤         ├────────────────┤
  │ id   PK     │◀────────│ booking_id  FK   │─────────▶│ id   PK       │
  │ user_id     │         │ seat_id     FK   │         │ show_id        │
  │ show_id     │         │ price_paid       │         │ row_number     │
  └─────────────┘         │                  │         │ seat_number    │
                          │ PK: (booking_id, │         └────────────────┘
                          │      seat_id)    │
                          └──────────────────┘

  The junction table can carry its OWN data (price_paid at booking time).
  PK is composite: (booking_id, seat_id) — ensures no duplicate seat per booking.
```

```java
// JPA mapping for many-to-many with extra column (price_paid)
// When the junction table carries its own data, use @Entity, not @ManyToMany
@Entity
@Table(name = "booking_seats")
public class BookingSeat {

    @EmbeddedId
    private BookingSeatId id;  // composite PK: (bookingId, seatId)

    @ManyToOne
    @MapsId("bookingId")
    private Booking booking;

    @ManyToOne
    @MapsId("seatId")
    private Seat seat;

    // Extra column on the junction table — price at time of booking
    @Column(name = "price_paid", nullable = false)
    private BigDecimal pricePaid;
}

// Pure many-to-many (no extra columns) — simpler @ManyToMany annotation
@Entity
public class User {
    @ManyToMany
    @JoinTable(
        name = "user_roles",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();
}
```

**When you need a junction table:** Any time you'd write "one X can have many Y, AND one Y can belong to many X."
Examples: users ↔ roles, bookings ↔ seats, students ↔ courses, tags ↔ articles.

---

### Part 6 — UUID vs BIGINT AUTO_INCREMENT as Primary Key

A common interview question: "Should your PK be a UUID or a BIGINT AUTO_INCREMENT?"

| | BIGINT AUTO_INCREMENT | UUID (e.g., UUID v4) |
|---|---|---|
| **Size** | 8 bytes | 16 bytes |
| **Uniqueness scope** | Unique within this DB only | Globally unique |
| **Index performance** | Sequential — inserts always go to end of B-tree. Fast. | Random — inserts scatter across the B-tree. Causes page splits and fragmentation. Slow on large tables. |
| **Readability** | Easy to read: `id=5024` | Hard to read: `550e8400-e29b-41d4-a716-446655440000` |
| **Guessability** | Predictable — clients can guess `id=5025` | Unguessable — good for security |
| **Multi-service / distributed** | Collision risk if two DBs generate `id=1` | Safe to generate in the application without a DB round-trip |

**The interview answer:** BIGINT for single-DB services — it's faster (sequential inserts), smaller, and simpler. UUID for distributed systems where you need to generate IDs in the application layer (across multiple services or databases) without coordination. If you use UUID, consider UUID v7 (time-ordered) instead of v4 (random) — it's monotonically increasing, so B-tree inserts are sequential again.

---

### Part 7 — Validation: DB Layer vs Application Layer

```java
// DB-layer validation — enforced by the database itself, cannot be bypassed
// Use for: invariants that must NEVER be violated regardless of which service writes
//   NOT NULL          → required field
//   UNIQUE            → no duplicates (email, idempotency key)
//   CHECK             → value in range (amount > 0, status IN (...))
//   FOREIGN KEY       → referential integrity

// Example: amount must be positive — enforce at DB level
// ALTER TABLE expense_items ADD CONSTRAINT chk_positive_amount CHECK (amount > 0);

// Application-layer validation — use for: user-facing error messages, business rules
// that involve multiple tables, or rules too complex for SQL CHECK constraints
public void validateExpenseItem(ExpenseItemRequest req) {
    if (req.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
        throw new ValidationException("Amount must be positive");
    }
    if (req.getExpenseDate().isAfter(LocalDate.now())) {
        throw new ValidationException("Expense date cannot be in the future");
    }
}

// Both layers together = defense in depth:
// Application layer catches it early with a helpful error message.
// DB constraint is the last-resort safety net if a bug bypasses the app layer.
```

---

### Part 8 — SQL vs NoSQL: The Interview Decision Framework

### What is ACID, and why does it matter here?

**ACID** is the set of four guarantees a relational database makes for every transaction (a group of DB operations treated as one unit):

| Letter | Property | Plain English |
|---|---|---|
| **A** — Atomicity | All-or-nothing | Either all operations in a transaction succeed, or none of them are saved. No partial updates. |
| **C** — Consistency | Invariants always hold | After every transaction, the DB is in a valid state — all constraints (NOT NULL, FK, CHECK) are satisfied. |
| **I** — Isolation | Concurrent transactions don't interfere | If two transactions run at the same time, each sees the DB as if it were running alone. |
| **D** — Durability | Committed data survives crashes | Once a transaction commits, the data is on disk — a server crash won't lose it. |

**Expense report example:** When a user submits an expense report, you update `expense_reports.status = SUBMITTED` AND insert a row into `audit_log`. If the DB crashes between the two writes, Atomicity ensures neither write is saved — the report stays in DRAFT. Without ACID, you'd have a "submitted" report with no audit trail.

**In an interview, if asked:** "ACID means the DB guarantees Atomicity (all-or-nothing per transaction), Consistency (constraints always hold), Isolation (concurrent transactions don't corrupt each other), and Durability (committed data survives crashes). These guarantees are why SQL databases are the right choice when data integrity is non-negotiable — payments, financial records, document submissions."

---

| Use SQL (Postgres, MySQL) when | Use NoSQL (MongoDB, DynamoDB, Cassandra) when |
|---|---|
| Data has clear relationships (users → reports → items) | Data is document-like, self-contained, deeply nested |
| You need ACID transactions across multiple tables | You need horizontal write scale (millions of writes/sec) |
| Ad-hoc queries with joins at runtime | Access patterns are known upfront, queries are simple |
| Schema is relatively stable | Schema changes frequently, fields vary per record |
| Compliance / audit trail requirements | Eventually consistent is acceptable |

**For DocuSign's expense report (C2):** SQL. The data is relational (users, reports, items, categories), requires ACID (can't have partial expense submissions), and the queries are join-heavy.

---

## 🏢 Real World — Where Companies Use This

- **DocuSign** — Envelope schema: `envelopes` table FK'd to `users`, `recipients` table (each envelope can have multiple signers), `signature_fields` table (position + type per document page). This is exactly the 3NF multi-table design — not a flat JSON blob per envelope.
- **Razorpay** — Payment schema with `payments` → `payment_attempts` (one payment can have multiple retry attempts). `payment_attempts.payment_id` FK with RESTRICT — cannot delete a payment if attempts exist. Idempotency key stored as UNIQUE column in `payment_attempts`.
- **BookMyShow** — Ticket booking: `shows` → `seats` (inventory) → `bookings` → `booking_seats` (the junction table). Composite index on `(show_id, seat_id)` in `seats` for fast "is this seat taken?" checks.
- **Swiggy** — Order schema: `orders` → `order_items` → `menu_items`. The `menu_items.price` at time of order is COPIED into `order_items.price_at_time` — intentional denormalization. If the restaurant changes prices tomorrow, the historical order is not affected.
- **Flipkart** — Product catalog: `products` → `product_variants` (size, colour) → `inventory` per variant per warehouse. Composite index on `(variant_id, warehouse_id)` for availability checks. Separate `price_history` table rather than updating the `products.price` column — preserves pricing history for analytics.

---

## 🧭 When to Use vs When NOT to Use

| Normalise (3NF) when | Denormalise when |
|---|---|
| Data is updated frequently (user profile, prices) | Data is written once and read many times (order history, audit logs) |
| Multiple services write to the same data | Read performance is critical and eventual consistency is acceptable |
| You need consistent queries across related data | Historical snapshots matter (price at time of purchase) |
| Compliance requires a single source of truth | You're building a read model / reporting DB |

**The common mistake:** Adding indexes "just in case." Every index has a write cost. An unindexed INSERT is O(1). An insert with 5 indexes must update 5 B-tree structures. On a table that's written 100K times/second, 5 unnecessary indexes is a real performance problem.

---

## ⚠️ Trade-offs

| | |
|---|---|
| **You gain** | Data integrity enforced at the DB layer — no code path can corrupt the invariant. Efficient queries when indexes match access patterns. Schema changes are explicit and reviewable. |
| **You lose** | Schema migrations are painful at scale (adding a column to a 100M-row table takes time). Joins are expensive if not indexed. Rigid structure — adding an ad-hoc field requires a migration. |
| **Failure mode** | Normalising without thinking about queries — you end up with beautiful 3NF tables that require 8-table joins to answer a basic question, and every query times out in production. Design schema AND queries together. |

---

## 🔬 Interview Q&As

### Tier 1 — Surface Questions

### Q: "Design the data model for an expense report system."
> Three core tables: `users`, `expense_reports` (FK to users, status enum, submitted_at nullable), `expense_items` (FK to reports, amount, category, receipt_url, expense_date). A `categories` lookup table. Keep it 3NF — don't store `user_name` in `expense_reports`, don't store `total_amount` (calculate from items). Indexes on `expense_reports(user_id)` for "my reports" queries and `expense_reports(status, submitted_at)` for the approval queue. FK cascade rules: deleting a report cascades to its items. The drawn schema takes 2 minutes — state the indexes and cascade rules explicitly, most candidates skip them.

### Q: "What is normalisation and when would you break it?"
> Normalisation removes redundancy by splitting data into focused tables linked by foreign keys. First normal form: one value per cell, no arrays in columns. Second: every column depends on the full primary key. Third: no column derives from a non-key column. I'd break 3NF deliberately in two cases: (1) snapshot data — copy the price into an order line item so historical orders aren't affected by future price changes; (2) read performance — if a query needs 6 joins and is called 10K times/second, a denormalised summary table can be the right trade-off, maintained by background jobs or triggers.

### Q: "What's the difference between a single-column index and a composite index?"
> A single-column index is for queries filtering on one column. A composite index covers multiple columns and is most useful when queries filter on those columns together. The order matters — a composite index on `(status, submitted_at)` serves `WHERE status = 'SUBMITTED' ORDER BY submitted_at` efficiently, but an index on `(submitted_at, status)` doesn't help that same query because the leftmost prefix rule: the DB can use the index only if the query starts from the leftmost column. Think of it like a phone book sorted by last name then first — you can look up "Smith, John" but not "John, ?" without scanning.

### Q: "When do you choose Postgres over MongoDB?"
> Postgres when the data is relational (entities that reference each other), you need multi-table ACID transactions, or your queries are ad-hoc with joins. MongoDB when the data is document-shaped (each record is self-contained), you need schema flexibility (different fields per document), or you need horizontal write sharding across many nodes. For most backend services — user systems, payment systems, document management — Postgres wins on integrity guarantees. MongoDB shines for content management, product catalogs with variable attributes, or event logs.

---

### Tier 2 — Cross / Probe Questions

### Q: "You need to add a `total_amount` column to `expense_reports` for fast lookups. How do you keep it consistent with `expense_items`?"
> Never store a derived value without a consistency mechanism. Three options: (1) Calculate on the fly — `SELECT SUM(amount) FROM expense_items WHERE report_id = X`. Fast enough for moderate load, always correct. (2) Materialized column updated by a DB trigger — when `expense_items` changes, trigger updates `expense_reports.total_amount`. Consistent but triggers are invisible and hard to test. (3) Application-layer update — service calculates and writes `total_amount` explicitly on every item add/remove, wrapped in a transaction. My preference: option 1 unless query profiling shows it's the bottleneck. Option 3 with transactions if option 1 is too slow. Triggers last resort — hard to debug.

### Q: "Your `expense_reports` table has 200 million rows. Adding a new `approved_by` column is blocking your deployment. What do you do?"
> `ALTER TABLE` on a 200M-row table acquires a lock and can run for hours, blocking all reads and writes. The safe pattern: (1) Add the column as `NULL` with no default first — Postgres/MySQL can do this as a metadata-only change (fast). (2) Backfill in batches: `UPDATE expense_reports SET approved_by = NULL WHERE id BETWEEN X AND Y` — small batches avoid lock contention. (3) Once backfilled, add constraints or defaults if needed. (4) Never do `ALTER TABLE ... ADD COLUMN ... NOT NULL DEFAULT value` on a large table in a single transaction — it forces a full table rewrite.

### Q: "Two services both write to the `expense_reports` table. Service A updates `status`, Service B updates `submitted_at`. How do you avoid race conditions?"
> Optimistic locking: add a `version` column (integer) to `expense_reports`. Both services read the current version when they fetch the row. On update: `UPDATE ... WHERE id = X AND version = {read_version}` + increment `version`. If zero rows updated, someone else changed it first — retry or return a conflict error. This avoids DB-level locks while detecting the race. Pessimistic locking (`SELECT FOR UPDATE`) is an alternative but serializes all concurrent writers — only use it when the race is expected to be frequent and retries are expensive.

---

## 🧾 TL;DR — One Interviewer-Ready Line

> *"I design schemas in 3NF first — each table owns one concept, FKs enforce relationships, no derived columns. Then I consciously break it: snapshot critical data (price at time of order), add indexes for the specific queries that will be hot, and choose SQL over NoSQL when the data is relational and ACID matters."*

---

## 🔗 Related Concepts

- **`11-api-design.md`** — The API request/response shapes map directly to the schema. The `CreateExpenseItemRequest` fields should mirror the `expense_items` table columns.
- **`01-optimistic-pessimistic-locking.md`** — Concurrent writes to the same row (the `version` column pattern) are the data modeling answer to the locking question.
- **`04-idempotency.md`** — The idempotency key table described there is a direct example of a single-purpose table with a UNIQUE constraint — data modeling in action.

---

## 📚 Further Reading (Optional — after the note)

| Resource | What it adds | Time |
|---|---|---|
| **hellointerview.com — Data Models** | Deep-dive on SQL vs NoSQL decision and storage patterns. URL: https://www.hellointerview.com/learn/system-design/deep-dives/sql | ~15 min |
| **"Database Internals" — Arpit Bhayani (YouTube)** | How B-tree indexes actually work — why index order matters. Search: "Arpit Bhayani database internals" | ~25 min |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 2026 | File created. DocuSign R2 prep — C2 (Expense Report) and C3 (Pagination) both confirmed required data modeling. Covers 3NF, FK cascade rules, indexing, validation layers, SQL vs NoSQL. |
| June 2026 | Gaps patched: ACID definition + 4-property table added before SQL/NoSQL section. B-tree index explanation + leftmost prefix rule added after indexing code. JPA annotations explained inline (LAZY, orphanRemoval, EnumType.STRING). Many-to-many junction table section added (Part 4). UUID vs BIGINT PK debate added (Part 5). DECIMAL(12,2) glossed in schema. |
| June 23, 2026 | Added Part 4 — "Schema Evolution: Adding Columns at Scale" — covers add-null → batch-backfill → add-constraint pattern, zero-downtime migrations, lock contention avoidance. Explains expansion-then-contraction migration strategy. Defines "database migration" with first-use gloss. Renumbered subsequent parts (Part 5→Part 8). Existing 200M-row Q&A already covers practical schema evolution answer. File grew 466→539 lines (+16%). |
