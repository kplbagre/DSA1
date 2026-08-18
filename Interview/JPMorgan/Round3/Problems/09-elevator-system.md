# Elevator System — JPMC Round 3 (LLD)

> **JPMC context:** Confirmed by Blind panelists and LLD prep guides. OOP-focused — the
> interviewer does **not** expect distributed systems or HLD components. The whole problem
> tests two OOP skills: (1) **Strategy pattern for scheduling** — how do you pick which
> elevator handles a request, and (2) **direction state machine** — how does an elevator
> know when to move, stop, and reverse.
>
> **The one distinction to make in the first 60 seconds:** the `pendingFloors` on each
> elevator is a **sorted set** (`TreeSet<Integer>`), not a list or queue. LOOK works
> by always processing the nearest pending floor in the current direction and reversing
> only when no more floors exist in that direction. The sorted set makes "nearest floor
> above" a one-line `higher()` call instead of a scan.

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

Design an elevator system for a building with N floors and K elevators. Users press buttons on floors (outside the elevator) to request service in a direction (up or down), and press buttons inside the elevator to select a destination floor. The system decides which elevator serves each external request and moves elevators efficiently by minimizing total travel.

**The one-line framing to say out loud in the interview:**
> *"This is a scheduling OOP problem. An ElevatorController receives all requests and
> delegates assignment to a SchedulingStrategy — Strategy pattern. Each Elevator tracks
> its direction (UP/DOWN/IDLE) and a pendingFloors TreeSet. The LOOK algorithm says:
> keep moving in the current direction, stop at every pending floor, reverse only when
> no more floors exist in the current direction."*

---

## §2 — ❓ Clarifying Questions

**System scope**

1. How many elevators and floors? (affects whether a simple O(N) linear scan in `assign()` is acceptable)
2. Is this a simulation (step-by-step turns) or an event-driven system? (defines whether I write a `step()` method or an event handler)
3. Are there freight elevators or express elevators that skip floors? (constraints on `SchedulingStrategy`)

**Request types**

4. Two types of button presses: (a) external — on a floor, requesting UP or DOWN, and (b) internal — inside an elevator, selecting a destination. Confirm both are in scope.
5. Can a user cancel a request (button is already lit)?

**Scheduling**

6. Which algorithm — LOOK (preferred), SCAN, FCFS? (if interviewer says "you decide," use LOOK)
7. Should the system minimize wait time, minimize total travel, or both?

**Load and capacity**

8. Does the elevator have a weight/capacity limit? (adds a `currentLoad: int` field and check in `step()`)
9. Can a user press the same button twice? (must be idempotent — `TreeSet` handles this naturally)

**Out of scope for this round**

10. Real-time hardware communication, door sensors, emergency protocols — confirm out of scope.

---

## §3a — 🏗️ LLD — Construction Guide (7 Moves)

---

**Move 1 — List domain nouns — don't draw yet**

Read the statement, then do **two passes**: one for nouns in the problem, one for entities the constraints force.

**From the statement directly:** Elevator, Floor, Button, User

**Derived — say the reason out loud:**
- *"There are two types of button presses: an external button press (on a floor, requesting UP or DOWN) and an internal button press (inside an elevator, selecting a destination floor). Both result in the elevator needing to stop at a floor. I can unify them into a single `Request` — a floor number plus an optional direction."* → **Request** entity (captures both press types; direction is null for internal requests)
- *"The system needs to decide which elevator handles each external request. The decision algorithm varies — LOOK, SCAN, FCFS are different strategies. If I hardcode the algorithm in ElevatorController, changing the algorithm edits the controller."* → **SchedulingStrategy** (Strategy pattern — the assignment algorithm is a plug-in)
- *"An elevator moves in a direction, stops at floors, then either continues or reverses. The direction is the state."* → **Direction** enum (UP, DOWN, IDLE — the state machine)
- *"LOOK requires knowing which floors are pending in the current direction. A sorted structure lets me find the 'nearest floor above' or 'nearest floor below' in O(log N) without scanning."* → **pendingFloors: TreeSet\<Integer\>** on each Elevator (the key data structure that makes LOOK efficient)

> **Say:** "`Request` unifies both button press types — I don't need separate `ExternalRequest` and `InternalRequest` classes because both result in the same action: add a floor to an elevator's pending set. `SchedulingStrategy` is the entity I'm adding because 'which elevator gets this request?' is a standalone algorithm that the interviewer will probe. `TreeSet` is the data structure choice that makes LOOK a one-liner."

**Your board at the end of Move 1:**

```
From statement:  Elevator, Floor, Button, User
Derived:         Request (unifies external + internal presses),
                 Direction (UP/DOWN/IDLE — the state machine enum),
                 SchedulingStrategy (algorithm abstraction for assignment),
                 pendingFloors: TreeSet<Integer> (sorted set enabling LOOK)
```

---

**Move 2 — Classify each noun: entity / enum / service**

`Direction` and `DoorState` are finite → enums. `Request` and `Elevator` are entities. Now add **services** — from asking *"who does the work?"*:

- *"Something receives all incoming requests and dispatches to the right elevator — that is `ElevatorController`."* (the central coordinator)
- *"The selection algorithm (LOOK, SCAN, FCFS) is variable behavior — that is `SchedulingStrategy`."* (interface)

> **Floor as an entity?** A `Floor` class (with `upButton: boolean`, `downButton: boolean`) is optional. In practice, the only floor state is whether a button is pressed. I represent this as the `Request.floorNumber` field — I do not need a full `Floor` entity unless the interviewer asks for it. "I can add a `Floor` class if we need to model button-lit state for the UI, but for the algorithm, `Request.floorNumber` is sufficient."

**Your board at the end of Move 2:**

```
enum:    Direction (UP · DOWN · IDLE), DoorState (OPEN · CLOSED)
entity:  Request, Elevator
service: SchedulingStrategy (iface) → LOOKStrategy, FCFSStrategy
         ElevatorController (orchestrator — the entry point for all requests)
```

---

**Move 3 — Draw enums first. Explain non-obvious states.**

`Direction` is the elevator's state machine. The three values are also the three states.

```
Direction / State machine:

  IDLE ──────────► MOVING_UP        (new request for a floor ABOVE current)
  IDLE ──────────► MOVING_DOWN      (new request for a floor BELOW current)
  MOVING_UP ─────► MOVING_UP        (continue: more floors above in pendingFloors)
  MOVING_UP ─────► MOVING_DOWN      (LOOK reversal: no more floors above)
  MOVING_UP ─────► IDLE             (no more floors anywhere)
  MOVING_DOWN ───► MOVING_DOWN      (continue: more floors below in pendingFloors)
  MOVING_DOWN ───► MOVING_UP        (LOOK reversal: no more floors below)
  MOVING_DOWN ───► IDLE             (no more floors anywhere)
```

> **Why LOOK reversal at "no more floors in current direction" rather than at a fixed endpoint?**
> *"SCAN goes all the way to the top or bottom floor even if no requests are there. LOOK reverses as soon as there are no more requests in the current direction. LOOK is more efficient because it avoids unnecessary travel to building extremes. The `TreeSet.higher(currentFloor)` call in `step()` is what checks this in O(log N) — it returns null if there is no pending floor above."*

> **Door state:** "I do not model door-open as a state in the Direction machine. When `step()` removes a floor from `pendingFloors`, the elevator opens the door (callback or event), waits for passengers, then closes. Door mechanics are an action at a stop — not a peer state to MOVING_UP. Adding a `DOOR_OPEN` state to the direction machine mixes two concerns and makes the step logic harder to follow."

**Your board at the end of Move 3:**

```
Direction: IDLE · MOVING_UP · MOVING_DOWN     ← the elevator's state machine
DoorState: OPEN · CLOSED                      ← door is a separate action at each stop
```

---

**Move 4 — Draw entities smallest → largest. Name what each knows + can do.**

`Request` (smallest) → `Elevator` (the main stateful entity)

> **For `pendingFloors: TreeSet<Integer>` on Elevator:** "I chose `TreeSet` (red-black tree, sorted, O(log N) operations) over a `List` (O(N) search for next floor) because LOOK queries are: 'next floor above current' (`higher(currentFloor)`) and 'next floor below current' (`lower(currentFloor)`). These are one-liners on `TreeSet`. A `List` would require filtering and sorting on every step."

> **Why `TreeSet` also handles duplicate button presses for free:** "If a user presses floor 5 twice (or two users both press floor 5), the `TreeSet.add(5)` is idempotent — only one entry exists. No duplicate-check logic required."

> **For `Request.direction`:** "External presses (on a floor) include a direction: UP or DOWN. Internal presses (inside the elevator) have no direction — the user is already in the elevator and heading wherever the elevator goes. I model this with `direction: Direction` that can be null for internal requests. In `SchedulingStrategy`, I use this field to prefer elevators that are already heading in the same direction as the external request."

**Your board at the end of Move 4:**

```
Request
  - requestId: String
  - floorNumber: int
  - direction: Direction (nullable — null for internal presses)
  - requestedAt: Instant

Elevator
  - elevatorId: String
  - currentFloor: int
  - direction: Direction               ← the state machine field
  - pendingFloors: TreeSet<Integer>    ← sorted; LOOK queries = higher() / lower()
  - doorState: DoorState
  + addPendingFloor(int floor): void   ← also triggers IDLE → MOVING if needed
  + step(): boolean                    ← advances one floor; returns false if IDLE
```

---

### 🎨 Visual — LOOK algorithm on a 10-floor building

```
Floor  Elevator A (currentFloor=3, MOVING_UP)
  10   ·
   9   ·
   8   ✓  ← pending (destination button inside)
   7   ·
   6   ✓  ← pending (floor button pressed from outside, going UP)
   5   ·
   4   ·
→  3   ← currentFloor
   2   ✓  ← pending (floor button, going DOWN — will serve after LOOK reversal)
   1   ·

LOOK traversal order: 3→4→5→6(stop)→7→8(stop) then no higher pending → REVERSE
                      8→7→6→5→4→3→2(stop) then no lower pending → IDLE

KEY INVARIANT:
  Elevator A never goes to floor 9 or 10 (nothing pending there).
  SCAN would go to floor 10 anyway. LOOK is the efficient variant.
  pendingFloors.higher(currentFloor) returns null → reversal trigger.
```

---

**Move 5 — Identify variable behavior. Extract interfaces.**

The only variable behavior is **how the controller selects an elevator for each request**.

> **Why `SchedulingStrategy` is an interface (Strategy pattern):** "LOOK, SCAN, and FCFS each have a different algorithm for selecting an elevator. The controller should not know which one is active — it calls `strategy.assign(elevators, request)` and gets back the chosen elevator. Swapping from FCFS to LOOK is a constructor injection change, not a code change in the controller."

> **The interface signature:** `Elevator assign(List<Elevator> elevators, Request request)`
> — takes the full list of available elevators and the incoming request. Returns the one elevator that should serve it. The strategy has full visibility into each elevator's current floor, direction, and pending load.

**Your board at the end of Move 5:**

```
interface SchedulingStrategy {
    Elevator assign(List<Elevator> elevators, Request request);
}
   ├─ LOOKStrategy   (cost = travel distance accounting for current direction)
   └─ FCFSStrategy   (cost = size of pendingFloors — least-loaded elevator)
```

---

**Move 6 — Add the orchestrating service last. Its constructor deps = your design.**

`ElevatorController` receives all incoming requests. Its only non-trivial dependency is the `SchedulingStrategy` — everything else is the list of elevator instances.

> **Why the strategy is constructor-injected:** "This makes the algorithm a runtime decision — the building operator configures which strategy to use at startup. The controller's code never changes when the algorithm changes."

> **What `addRequest` does in one sentence:** "Find the best elevator via the strategy, add the request's floor to that elevator's pendingFloors, and — if the elevator was IDLE — transition it to MOVING_UP or MOVING_DOWN based on whether the floor is above or below."

> **What `step()` on the controller does:** "Advances the simulation by one floor for every non-IDLE elevator. In a real system, this is replaced by hardware callbacks — each elevator sends 'arrived at floor N' events. In an interview simulation, `step()` models it."

**Your board at the end of Move 6:**

```
ElevatorController (elevators: List<Elevator>, strategy: SchedulingStrategy)
  + addRequest(Request r) : void
     ── strategy.assign(elevators, r) → Elevator e
     ── e.addPendingFloor(r.floorNumber)
  + step()               : void   ← advances simulation; replace with event callbacks in production
```

---

**Move 7 — Name the hot resource. One sentence tying all locks to it.**

There is one shared hot resource per elevator: **`Elevator.pendingFloors`**. Concurrent button presses (two users on different floors pressing the UP button at the same moment) each trigger `addRequest` → `addPendingFloor`. Both calls mutate `pendingFloors` and may read `direction` to decide the IDLE→MOVING transition.

> **Say:** "The contended resource is `Elevator.pendingFloors` — a `TreeSet` that is not thread-safe. I guard it with `synchronized` on the `Elevator` instance (or replace with `ConcurrentSkipListSet`, which is thread-safe and sorted). The IDLE→MOVING transition inside `addPendingFloor` must also be guarded by the same lock — a check-then-act race (two threads both see `direction == IDLE`, both set direction to MOVING_UP for different floors) would corrupt the state machine."

**Your board at the end of Move 7:**

```
HOT RESOURCE: Elevator.pendingFloors + Elevator.direction (both in one lock boundary)
  Option A: synchronized(elevator) { pendingFloors.add(floor); if IDLE, set direction }
  Option B: replace TreeSet with ConcurrentSkipListSet (lock-free sorted set);
            guard direction transitions with AtomicReference<Direction> + CAS
  → both correct; Option A is simpler in an interview
```

---

### 75% Rule — What to Draw First If Time Is Short

```
Priority 1 — must reach (10 min):
  • Direction enum + state machine transitions (IDLE → MOVING_UP/DOWN → reversal → IDLE)
  • Elevator entity: currentFloor + direction + pendingFloors: TreeSet
  • Elevator.step() logic: move one floor, remove from pendingFloors, check for reversal
  • SchedulingStrategy interface + LOOKStrategy.assign signature

Priority 2 — draw if time allows:
  • ElevatorController.addRequest (brief — one-liner per step)
  • LOOKStrategy.costToServe scoring function (the most interesting code)
  • Elevator.addPendingFloor with IDLE→MOVING transition

Priority 3 — verbally mention, never draw:
  • FCFSStrategy (mention it as an alternative, don't draw it)
  • ConcurrentSkipListSet (mention as the thread-safe alternative, don't draw it)
  • Door-open behavior (say "at each stop, door opens for passengers, then closes")
```

---

## §3b — 🏗️ LLD — Complete Class Diagram — What You're Building Toward

```
┌─────────────────────────────────────────────────────────────┐
│ «enum» Direction                                             │
│   UP · DOWN · IDLE                                           │
└─────────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────────┐
│ «enum» DoorState                                             │
│   OPEN · CLOSED                                              │
└─────────────────────────────────────────────────────────────┘

┌───────────────────────────────────────────────┐
│ Request                                        │
├───────────────────────────────────────────────┤
│ - requestId: String                           │
│ - floorNumber: int                            │
│ - direction: Direction   (null = internal)    │
│ - requestedAt: Instant                        │
└───────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────┐
│ Elevator                           «the main stateful actor»│
├──────────────────────────────────────────────────────────┤
│ - elevatorId: String                                     │
│ - currentFloor: int                                      │
│ - direction: Direction        ← the state machine field  │
│ - pendingFloors: TreeSet<Integer>  ← sorted; LOOK-ready  │
│ - doorState: DoorState                                   │
├──────────────────────────────────────────────────────────┤
│ + addPendingFloor(int floor): void  ← triggers IDLE→MOVING│
│ + step(): boolean                   ← move one floor      │
│ + getCurrentFloor(): int                                 │
│ + getDirection(): Direction                              │
│ + getPendingFloors(): TreeSet<Integer>                   │
└──────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────┐
│ «interface» SchedulingStrategy                            │
│ + assign(elevators: List<Elevator>, r: Request): Elevator │
└──────────────────────────────────────────────────────────┘
   ├─ LOOKStrategy   (score by travel cost accounting for direction)
   └─ FCFSStrategy   (score by pending queue size — least loaded)

┌─────────────────────────────────────────────────────────────┐
│ ElevatorController                                           │
├─────────────────────────────────────────────────────────────┤
│ - elevators: List<Elevator>                                 │
│ - strategy: SchedulingStrategy                              │
├─────────────────────────────────────────────────────────────┤
│ + addRequest(Request r): void                               │
│ + step(): void                                              │
└─────────────────────────────────────────────────────────────┘

KEY INVARIANT: pendingFloors is a TreeSet — LOOK's "next floor above" and
"next floor below" are O(log N) calls (higher/lower). The direction field
and pendingFloors are always mutated inside the same synchronized block.
```

---

## §4 — 🧭 LLD — Design Decisions

| Decision | Why this | What I rejected and why |
|---|---|---|
| **`pendingFloors: TreeSet<Integer>`** | O(log N) `higher()` / `lower()` for "next floor in current direction"; duplicate button presses are idempotent (Set) | `List<Integer>` — O(N) scan to find the nearest floor; duplicates must be manually filtered |
| **Direction enum IS the state machine** | Elevator direction and elevator state are the same thing for LOOK — MOVING_UP means moving up, IDLE means stopped | Separate `ElevatorState` enum (MOVING_UP, MOVING_DOWN, IDLE, DOOR_OPEN) — DOOR_OPEN mixes the door action into the movement state machine and complicates `step()` |
| **Door-open as an action at a stop, not a state** | `step()` logic stays clean: move, remove from set, check reversal. Door open/close is a side effect, not a state transition | `DOOR_OPEN` as a peer state — every step check must also handle `if (DOOR_OPEN) skip_move; wait; close_door` in addition to movement logic |
| **`SchedulingStrategy` with `costToServe` scoring** | The selection algorithm is swap-able; LOOK's cost function is explicitly reasoned and interviewable | Hardcoded round-robin in ElevatorController — no plug-in; "which elevator?" becomes a hardcoded detail |
| **Request unifies external + internal presses** | Both result in the same action: `elevator.addPendingFloor(floor)`. The `direction` field is used only by `SchedulingStrategy` for preference matching; if null, the strategy treats it as direction-agnostic | Separate `ExternalRequest` / `InternalRequest` with a type hierarchy — two classes for essentially the same operation; the polymorphism adds complexity without benefit |
| **ElevatorController holds the strategy** | The controller is the decision point; injecting the strategy there means swapping algorithms is a constructor-injection change | Strategy on each Elevator — an elevator should not decide which requests it gets; that is the controller's job |

---

## §5 — 🔌 LLD — Key Interfaces

| Interface | Contract |
|---|---|
| `SchedulingStrategy` | Given the current state of all elevators and an incoming request, returns the single elevator that should serve the request. |

```java
public interface SchedulingStrategy {
    // Returns the elevator that should serve this request.
    // Implementations may inspect currentFloor, direction, and pendingFloors on each elevator.
    Elevator assign(List<Elevator> elevators, Request request);
}
```

---

## §6 — ⚙️ LLD — Code to Write

Three methods carry the design: the **LOOK scoring function**, the **step function with direction reversal**, and the **addPendingFloor with IDLE→MOVING state transition**.

---

### 1. The LOOK scoring function — `LOOKStrategy.assign`

**Steps in plain English:**

1. **Score each elevator** using `costToServe`. Lower score = better match.
2. **If IDLE:** cost = absolute distance to the request floor.
3. **If heading toward the request (same direction, request floor in front):** cost = distance. Will pick the rider up on the way — minimal disruption.
4. **If heading away or wrong direction:** cost = distance to finish the current run (reach the farthest pending floor) PLUS distance from there to the request floor. Accurate estimate of when this elevator could arrive.
5. **Return the elevator with the lowest cost.**

```java
public Elevator assign(List<Elevator> elevators, Request request) {
    // Step 5 — pick the elevator with the minimum cost
    return elevators.stream()
        .min(Comparator.comparingInt(e -> costToServe(e, request)))
        .orElseThrow(() -> new IllegalStateException("No elevators configured"));
}

private int costToServe(Elevator elevator, Request request) {
    int floor = request.getFloorNumber();
    int curr = elevator.getCurrentFloor();
    Direction dir = elevator.getDirection();
    TreeSet<Integer> pending = elevator.getPendingFloors();

    // Step 2 — idle: just the travel distance
    if (dir == Direction.IDLE) {
        return Math.abs(curr - floor);
    }

    boolean headingToward =
        (dir == Direction.UP && floor >= curr) ||
        (dir == Direction.DOWN && floor <= curr);

    // Step 3 — heading toward: will pass through; minimal cost
    if (headingToward) {
        return Math.abs(curr - floor);
    }

    // Step 4 — heading away: must finish current run first, then serve
    // Farthest pending floor in the current direction = turnaround point
    int turnaround = (dir == Direction.UP)
        ? pending.last()   // highest pending floor
        : pending.first(); // lowest pending floor
    return Math.abs(curr - turnaround) + Math.abs(turnaround - floor);
}
```

> **The lines to defend:** `pending.last()` and `pending.first()` — O(1) on `TreeSet` (it keeps a pointer to the max and min). "If the elevator is going UP, the turnaround point is the highest floor in `pendingFloors`. If DOWN, the lowest. I get these in O(1) because `TreeSet` is a sorted tree."

---

### 2. The step function — `Elevator.step`

**Steps in plain English:**

1. **If IDLE or nothing pending, return false.** Nothing to do.
2. **Move one floor** in the current direction (increment or decrement currentFloor).
3. **Check if this floor is in pendingFloors.** If yes, remove it and open the door (passengers board/exit).
4. **Check for LOOK reversal.** After moving: if no more floors exist in the current direction in `pendingFloors`, reverse direction. If no floors anywhere, become IDLE.

```java
public synchronized boolean step() {
    // Step 1 — nothing to do
    if (direction == Direction.IDLE || pendingFloors.isEmpty()) {
        return false;
    }

    // Step 2 — move one floor
    if (direction == Direction.UP) {
        currentFloor++;
    } else {
        currentFloor--;
    }

    // Step 3 — stop if this floor was requested
    if (pendingFloors.remove(currentFloor)) {
        // Door opens; passengers board or exit.
        // In a real system: fire a DoorOpenEvent; await DoorCloseEvent.
        doorState = DoorState.OPEN;
        doorState = DoorState.CLOSED; // simplified; real system: callback/event
    }

    // Step 4 — LOOK reversal check
    if (direction == Direction.UP) {
        // Any pending floor above current position?
        if (pendingFloors.higher(currentFloor) == null) {
            // No more floors above — reverse or idle
            direction = pendingFloors.isEmpty() ? Direction.IDLE : Direction.DOWN;
        }
    } else {
        // Any pending floor below current position?
        if (pendingFloors.lower(currentFloor) == null) {
            direction = pendingFloors.isEmpty() ? Direction.IDLE : Direction.UP;
        }
    }
    return true;
}
```

> **The key lines to explain:** `pendingFloors.higher(currentFloor)` returning null = no more floors above = LOOK reversal trigger. `pendingFloors.remove(currentFloor)` — returns true if the floor was in the set (the elevator should stop here); idempotent otherwise. `synchronized` keyword on `step()` — because `addPendingFloor` (below) also modifies `pendingFloors` and `direction`, and both can be called concurrently.

---

### 3. The IDLE → MOVING transition — `Elevator.addPendingFloor`

**Steps in plain English:**

1. **Add the floor to the pending set.** TreeSet handles duplicates — idempotent.
2. **If the elevator is IDLE**, it must start moving. Determine direction from the new floor's position relative to current floor.

```java
public synchronized void addPendingFloor(int floor) {
    // Step 1 — add; TreeSet is idempotent: duplicate press does nothing
    pendingFloors.add(floor);

    // Step 2 — if idle, decide direction and start moving
    if (direction == Direction.IDLE) {
        if (floor > currentFloor) {
            direction = Direction.UP;
        } else if (floor < currentFloor) {
            direction = Direction.DOWN;
        }
        // If floor == currentFloor: the elevator is already there; open door immediately.
        // (omit for brevity; add as edge case if asked)
    }
    // If already MOVING: the floor is just queued; step() picks it up on the way
}
```

> **Why `synchronized` here too:** "Both `step()` and `addPendingFloor()` modify `direction` and `pendingFloors`. If they run concurrently without a lock, a thread in `step()` might read `direction == IDLE` and leave it IDLE, while another thread in `addPendingFloor()` adds a floor and correctly sets direction. The `synchronized` on the same Elevator instance serializes them — same lock as `step()`."

---

## §7 — 🔁 LLD — Concurrency

| Shared field | What breaks without a guard | Fix |
|---|---|---|
| `Elevator.pendingFloors` | `TreeSet` is not thread-safe — concurrent `add()` and `remove()` corrupt the tree structure | `synchronized(elevator)` on both `step()` and `addPendingFloor()`. Alternative: replace with `ConcurrentSkipListSet` (lock-free, sorted). |
| `Elevator.direction` | Check-then-act race: two threads both read `direction == IDLE`, both write different directions for different floors — the second write wins and the first floor's direction is lost | Both read+write of `direction` inside the same `synchronized` block as `pendingFloors` |

**Why the `synchronized` approach beats `ConcurrentSkipListSet` alone:**
Even if `pendingFloors` is a `ConcurrentSkipListSet` (atomically safe), the check-then-act on `direction` (read `IDLE`, then write UP/DOWN) is a separate compound operation. It needs its own guard. Using `synchronized` on the Elevator instance covers both fields in one lock boundary — simpler and correct. If the interviewer asks about lock-free, mention `AtomicReference<Direction>` with CAS.

---

## §8 — 🧨 Java Depth Probes

| What you say in design | What they immediately ask | Your answer |
|---|---|---|
| "`pendingFloors: TreeSet<Integer>`" | "Why TreeSet specifically — why not a PriorityQueue?" | `TreeSet` supports both `higher()` (next floor above) and `lower()` (next floor below) — I need both when moving UP and DOWN. A `PriorityQueue` gives me the minimum (or maximum) efficiently, but not arbitrary ceiling/floor queries. It also doesn't deduplicate naturally. `TreeSet` is the right tool. |
| "LOOK reversal when `pendingFloors.higher(currentFloor) == null`" | "What if the elevator is at floor 10 and there are floors pending at 2, 3, 4 — all below?" | After the last move to floor 10, `higher(10)` returns null → direction becomes DOWN. Next `step()`: elevator moves to 9, 8, ... stopping at 4, 3, 2. Correct. |
| "`synchronized` on Elevator" | "This is one lock per elevator — does it create a bottleneck?" | In a real building with 4-8 elevators, the contention is minimal — most concurrent button presses go to different elevators. If there are 100+ elevators (uncommon), a `ConcurrentSkipListSet` + `AtomicReference<Direction>` CAS avoids the lock entirely. For an interview, synchronized is the right starting point. |
| "`LOOKStrategy.costToServe`" | "What if two elevators have the same cost?" | `Stream.min()` picks the first one in stream order (stable). I could add a tie-breaker: prefer the elevator with the smaller `pendingFloors.size()` (less loaded). In practice, exact ties are rare enough that the simpler code is the right default. |
| "Request.direction is null for internal presses" | "How does SchedulingStrategy handle a null direction?" | For internal presses, the elevator is already selected (the user is inside) — `addPendingFloor` is called directly on that elevator, bypassing `assign()`. `assign()` is only called for external requests, which always have a non-null direction. |

---

## §9 — 🌐 HLD — This Is an OOP Problem

**The interviewer does not expect distributed systems for Elevator System.** If the interviewer asks "how would this work in a real building?" or "how would you make this production-grade?", see §10.

Do NOT volunteer Kafka, Redis, or microservices during the LLD phase — the interviewer is probing OOP depth. The elevator scheduling algorithm is the bar.

---

## §10 — 🏛️ If Asked About Production

**The one production question that naturally comes up:** "How would the ElevatorController communicate with real elevator hardware?"

> "In production, the ElevatorController is an event-driven service. Each elevator publishes 'arrived at floor N' events and 'door closed' events via a message bus (could be as simple as a UDP broadcast in the building's LAN). The controller subscribes to these events and calls `addPendingFloor` on the right elevator. The `step()` simulation loop is replaced by event handlers. The state machine and scheduling algorithm are identical — only the trigger changes from a simulation clock to hardware events."

| OOP model | Real system |
|---|---|
| `step()` called in a simulation loop | Elevator hardware fires `ArrivedAtFloor(elevatorId, floor)` event |
| `addPendingFloor(floor)` called in-process | Button panel publishes `ButtonPressed(floor, direction)` event; controller subscribes |
| `synchronized` on Elevator | Controller is single-threaded per building (events processed sequentially); no lock needed |

---

## §11 — 📡 API Design (brief)

```
POST /v1/requests/external
Body: { floorNumber: 3, direction: "UP" }
Response: 200 OK  { assignedElevatorId: "E2", estimatedArrival: "~2 floors" }

POST /v1/requests/internal
Body: { elevatorId: "E2", destinationFloor: 8 }
Response: 200 OK

GET /v1/elevators
Response: [{ elevatorId, currentFloor, direction, pendingFloors }]
```

---

## §12 — 🛤️ Happy + Unhappy Paths

**Happy path — two users, one elevator building:**
1. User A on floor 2 presses UP. `addRequest(Request(floor=2, dir=UP))`.
2. `LOOKStrategy.assign`: only Elevator E1 (currentFloor=1, IDLE). costToServe = |1-2| = 1. Assigned.
3. `E1.addPendingFloor(2)`: IDLE → MOVING_UP (floor 2 > currentFloor 1).
4. `step()`: currentFloor = 2; `pendingFloors.remove(2)` = true → door opens, User A boards. Direction: `higher(2)` = null. IDLE (no more floors).
5. User A presses floor 7 inside the elevator. `E1.addPendingFloor(7)`: IDLE → MOVING_UP.
6. `step()` × 5: floors 3,4,5,6,7. At floor 7: door opens, User A exits. IDLE.

**Unhappy path — duplicate button press:**
→ User A presses floor 5 UP. User B on floor 5 also presses UP (same floor, same time).
→ Both calls: `E1.addPendingFloor(5)`. `TreeSet.add(5)` is idempotent — only one entry.
→ Elevator stops at floor 5 once. Both users board. Correct.

**Unhappy path — elevator already passing the floor:**
→ Elevator at floor 3, MOVING_UP, heading to floor 8. User on floor 2 presses UP.
→ `LOOKStrategy.assign`: E1 is `headingToward = false` (floor 2 < currentFloor 3 and direction is UP). Cost = |3-8| + |8-2| = 5 + 6 = 11.
→ If there is a second elevator E2 (IDLE, currentFloor 1): cost = |1-2| = 1. E2 is assigned.
→ If E1 is the only elevator: it finishes going UP, reverses at floor 8, comes DOWN to floor 2.

---

## §13 — 🔧 Fault Tolerance (brief)

This is an OOP problem — fault tolerance is minimal. Mention these only if asked:

| Concern | What breaks | What you add |
|---|---|---|
| Elevator stuck mid-floor | Elevator stops updating `currentFloor` — pending requests time out | Watchdog: if an elevator's `currentFloor` does not change for N steps, reassign its pending requests to another elevator |
| Controller crash | All in-memory state lost; pending floors lost | Persist `pendingFloors` + `currentFloor` for each elevator to a DB; on restart, restore state |
| All elevators busy | New request cannot be served immediately | `addRequest` still queues the request on the best (least-loaded) elevator — rider waits longer; system does not drop the request |

---

## §14 — 🔬 Q&A — JPMC Probes

### Q: "Walk me through LOOK for a 10-floor building with one elevator at floor 3 heading UP with pending floors at 6 and 8, and a new request at floor 2 going DOWN."
> Floor 2 (going DOWN) comes in. The elevator is heading UP — not toward floor 2. `costToServe`: current=3, heading UP, pending.last()=8 (turnaround). Cost = |3-8| + |8-2| = 5 + 6 = 11. Since it's the only elevator, it is assigned. `addPendingFloor(2)` adds 2 to the TreeSet. `step()` continues: floor 4, 5, 6 (stop — open door), 7, 8 (stop — open door). `higher(8)` = null → reverse to DOWN. Now: 7, 6, 5, 4, 3, 2 (stop — open door, DOWN user boards). `lower(2)` = null → IDLE.

### Q: "Why is `step()` synchronized but `getCurrentFloor()` is not — is that a problem?"
> In a simulation, `step()` and `addPendingFloor()` are the write operations — they both modify `direction` and `pendingFloors`. `getCurrentFloor()` is a read — it may see a slightly stale value under concurrent writes, but in a simulation loop that's acceptable. If the interviewer needs strict visibility: mark `currentFloor` as `volatile` (ensures memory visibility; reads always see the latest write without locking). `volatile` is sufficient for reads of a single int; `synchronized` is only needed when multiple fields must be read/written atomically together.

### Q: "What if I want to add a new scheduling algorithm — say, 'nearest car'?"
> Add a new class `NearestCarStrategy implements SchedulingStrategy` and inject it into `ElevatorController`'s constructor. Zero changes to `Elevator`, `ElevatorController`, or `LOOKStrategy`. That is the payoff of the Strategy pattern — new algorithms are new classes, not edits to existing ones.

### Q (OOP depth): "What if the elevator's pendingFloors TreeSet is empty but direction is MOVING_UP — is that a valid state?"
> No — that is an illegal state. It means the elevator is in motion with nowhere to go. The state machine prevents this: in `step()`, after removing the last floor from `pendingFloors`, if `pendingFloors.isEmpty()`, direction is set to IDLE. `addPendingFloor` only sets direction to non-IDLE if IDLE was the prior state. So the invariant holds in the normal path: `direction ≠ IDLE → pendingFloors.isEmpty() == false`. (The converse — pendingFloors nonempty → direction ≠ IDLE — holds unless the `floor == currentFloor` edge case in `addPendingFloor` is unhandled; for the interview, acknowledge it: "if a request arrives for the floor the elevator is already at, I open the door immediately and stay IDLE — no movement needed.")

---

## §15 — 🧾 TL;DR — 30-Second Pitch

> "Elevator System is a scheduling OOP problem. Two design decisions carry it:
> one, `pendingFloors: TreeSet<Integer>` on each Elevator — sorted, deduplicated,
> giving O(log N) 'next floor above' (`higher()`) and 'next floor below' (`lower()`)
> needed by the LOOK algorithm. Two, `SchedulingStrategy` as a Strategy interface —
> `ElevatorController.addRequest` delegates to `assign(elevators, request)`, so
> swapping from LOOK to FCFS is a constructor injection change.
> The LOOK algorithm: move in the current direction, stop at every pending floor,
> reverse only when `pendingFloors.higher(currentFloor) == null`.
> The direction field IS the state machine: IDLE → MOVING_UP/DOWN → reversal → IDLE.
> Door-open is an action at a stop, not a peer state in the machine.
> Concurrency: `step()` and `addPendingFloor()` both modify `direction` and `pendingFloors`
> — both are `synchronized` on the same Elevator instance."

---

## §16 — 🔄 Changelog

| Date | Change |
|------|--------|
| Aug 17, 2026 | Note created. JPMC Round 3 OOP-only (Blind panelist confirmed). Full 16-section arc: TreeSet for pendingFloors + LOOK algorithm + Direction state machine + SchedulingStrategy interface + synchronized step/addPendingFloor concurrency. No HLD. §9–§13 kept brief per round format. All 7 moves include derivation reasoning. ASCII visualization for LOOK traversal order. |
