# InMobi PS Round — Interview Debrief

**Date:** June 5, 2026
**Round:** Problem Solving (Round 3)
**Verdict on my answer:** Partial — ~60-65%. Good structure, missed concurrency depth.

---

## What Was Asked

**Problem:** Design a bus ticket reservation system.

**Interviewer's focus:** Not the full system — specifically how to handle **CRUD operations on DB**, and what happens when **2 passengers try to book the same seat at the same time**.

**Follow-up twist (the hard part):**
> "Our UI/client is dumb — it can't lock anything on the frontend.
> User A requests 10 seats. User B requests 25 seats.
> Both read available = 30 at the same time.
> How do you handle this at the backend?"

---

## How I Answered

**My flow (correct):**
```
Search buses → Select seat → Mark LOCKED with TTL (5 min) → Payment → Write to DB
```

**My composite key:** `BusID_SeatID_TripID`

**My data model:**
- Reservation table: bus_id, seat_id, trip_id, passenger, boarding_stop, drop_stop, status
- Seat has TTL lock on selection
- On payment success → write to reservation table

**What I said about partial routes:**
- Same seat can serve A→B and C→D if segments don't overlap
- Key is per (BusID, SeatID, TripID)

**What I said about concurrent booking:**
- Lock using composite key
- Reservation logs track booking

---

## Where I Fell Short

### Gap 1 — Optimistic Locking (the main miss)

The interviewer's "A=10, B=25, total=30" scenario is an **inventory overselling problem**, not a per-seat locking problem.

My key `BusID_SeatID_TripID` works for cinema-style seat selection (picking seat 14A specifically). It doesn't work when user says "give me any 10 seats."

**What I should have said:**

Add a `version` column to the seat inventory row. Use **optimistic locking**:

```sql
-- Both A and B read: available = 30, version = 7

-- A's update:
UPDATE seat_inventory
SET available_seats = available_seats - 10,
    version = version + 1
WHERE bus_id = 'B1' AND trip_id = 'T1'
  AND version = 7           -- must match what I read
  AND available_seats >= 10;

-- B's update (same time):
UPDATE seat_inventory
SET available_seats = available_seats - 25,
    version = version + 1
WHERE bus_id = 'B1' AND trip_id = 'T1'
  AND version = 7
  AND available_seats >= 25;
```

**What happens:**
- DB processes A first → version becomes 8, available = 20
- B's WHERE `version = 7` no longer matches → 0 rows updated
- B detects failure → retries → reads version=8, available=20
- 20 < 25 → B gets "not enough seats" cleanly

No deadlocks. No row-level locks held. Correct.

### Gap 2 — Didn't name the pattern

Should have said: *"This is optimistic locking — we use a version column to detect concurrent writes. Whoever commits first wins; the other gets 0 rows affected and retries with fresh data."*

Naming the pattern signals seniority.

### Gap 3 — Didn't offer the alternative

Should have compared with **pessimistic locking**:

```sql
BEGIN;
SELECT available_seats FROM seat_inventory
WHERE bus_id = ? AND trip_id = ?
FOR UPDATE;  -- B must wait here until A finishes

-- A checks: 30 >= 10 → proceed, update to 20
COMMIT;

-- B now reads 20, needs 25 → rejected cleanly
```

And then explained when to use which:

| | Optimistic | Pessimistic |
|---|---|---|
| Use when | Conflicts are rare | Conflicts are frequent |
| Advantage | No waiting, high throughput | Simple, no retry logic |
| Risk | Retry storms under high load | Deadlocks, slower |
| Bus booking | ✅ Better | Works but slower |

---

## What Was Good

- Correct high-level flow (Search → Lock → Pay → Confirm)
- TTL-based blocking instinct was right
- Partial route segment overlap awareness (A→B and C→D)
- Reservation table schema was reasonable

---

## The One-Line Answer I Should Have Had Ready

> "I'd use optimistic locking with a version column on the inventory row. Both users read the same version. Whoever writes first wins — version check passes, seats are decremented. The other gets 0 rows affected, retries, and either gets remaining seats or a clean rejection. No locks held, scales well."

---

## Concepts to Study From This

- [ ] **Optimistic Locking** — version-based CAS (Compare And Swap) on DB rows
- [ ] **Pessimistic Locking** — `SELECT FOR UPDATE`, when to use vs optimistic
- [ ] **Inventory overselling problem** — same family as rate limiting, different layer (DB not Redis)
- [ ] **Idempotent retry on 0 rows updated** — what the retry logic looks like

> Added to `SystemDesignConcepts/TODO.md` under Phase 1 additions.

---

## Lesson Learned

> **Lesson learned (June 2026):** Every booking/reservation problem has TWO concurrency layers — (1) per-item locking for specific seat selection, and (2) inventory count management with optimistic/pessimistic locking for bulk requests. When interviewer says "client is dumb and can't lock," they're always asking for the DB-layer answer, not Redis. Know both layers.
