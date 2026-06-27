# Command Pattern

> **Standard followed:** `LLD/notes-standards.md`
>
> **Why this file exists:** The Elevator problem (LLD TODO item 6) is built on Command — each floor request is a command object queued in a scheduler. You can't design that system without internalising this pattern first.

---

## 🎯 What Problem Does It Solve?

When a caller wants to trigger an operation but shouldn't know who performs it or when — hardwiring the call couples the invoker (who asks) to the receiver (who does). Command decouples them by wrapping the request as an object. That object can be queued, logged, delayed, retried, or reversed (undo). The invoker holds commands and fires them without knowing what they do.

---

## 🧠 Mental Model

Think of a **restaurant order slip**. The waiter (invoker) takes your order and writes it on a slip (the Command object). They hand the slip to the kitchen (receiver). The kitchen reads the slip and cooks. Three things to notice:

1. The waiter never cooks. They don't need to know the recipe.
2. Slips can be queued (rush hour), cancelled (customer changed mind), or replayed ("same as yesterday").
3. Adding a new dish type = new slip format. The waiter and kitchen are unchanged.

In code: `ElevatorController` is the waiter. `MoveToFloorCommand` is the slip. `ElevatorMotor` is the kitchen. The controller never calls the motor directly — it queues commands and fires them.

---

## 🔌 The Interface Contract

```java
// Every command obeys this contract — the invoker only calls execute()
public interface Command {

    // Perform the action encapsulated in this command
    void execute();
}
```

```java
// Extend if undo support is needed (text editor, transaction rollback)
public interface UndoableCommand extends Command {

    // Reverse the effect of execute()
    void undo();
}
```

---

## ⚙️ Implementation

**Steps in plain English:**

1. **Define the receiver** — the class that actually performs the work.
2. **Write concrete Commands** — each wraps one action on the receiver; `execute()` delegates to it.
3. **Write the invoker** — holds a queue of `Command` objects; never imports the receiver directly.
4. **Wire at startup** — caller creates commands, passes them to the invoker; invoker fires them.

```java
// Step 1 — receiver: knows how to move and open doors
public class ElevatorMotor {

    private int currentFloor = 0;

    public void moveTo(int floor) {
        // physically move the elevator
        this.currentFloor = floor;
    }

    public void openDoor() {
        // open the door at current floor
    }
}
```

```java
// Step 2a — concrete command: move to a specific floor
public class MoveToFloorCommand implements Command {

    private final ElevatorMotor motor;
    private final int targetFloor;

    public MoveToFloorCommand(ElevatorMotor motor, int targetFloor) {
        this.motor = motor;
        this.targetFloor = targetFloor;
    }

    @Override
    public void execute() {
        motor.moveTo(targetFloor);
    }
}
```

```java
// Step 2b — concrete command: open the door
public class OpenDoorCommand implements Command {

    private final ElevatorMotor motor;

    public OpenDoorCommand(ElevatorMotor motor) {
        this.motor = motor;
    }

    @Override
    public void execute() {
        motor.openDoor();
    }
}
```

```java
// Step 3 — invoker: holds a queue, fires commands one by one
// thread-safe: uses a blocking queue so the scheduler loop waits when empty
public class ElevatorScheduler {

    private final Queue<Command> commandQueue = new LinkedList<>();

    // Caller submits a command without knowing the motor exists
    public void submit(Command command) {
        commandQueue.add(command);
    }

    // Background loop: drain and execute in FCFS order
    public void processNext() {
        Command next = commandQueue.poll();
        if (next != null) {
            next.execute();
        }
    }
}
```

```java
// Step 4 — wiring: caller creates commands, submits to scheduler
ElevatorMotor motor = new ElevatorMotor();
ElevatorScheduler scheduler = new ElevatorScheduler();

// Floor 3 presses the button — a command is created and queued
scheduler.submit(new MoveToFloorCommand(motor, 3));
scheduler.submit(new OpenDoorCommand(motor));
// Scheduler fires them — motor runs, scheduler never imports ElevatorMotor method names
```

### 🎨 Visual — Invoker → Command → Receiver

```
  INVOKER                  COMMAND                    RECEIVER
  ┌──────────────┐         ┌─────────────────────┐    ┌──────────────┐
  │ ElevatorSche-│  submit │  MoveToFloorCommand  │    │ ElevatorMoto │
  │ duler        │────────▶│  - motor             │───▶│ r            │
  │              │         │  - targetFloor       │    │ moveTo(3)    │
  │ Queue<Cmd>   │         │  execute() ──────────┘    └──────────────┘
  └──────────────┘         └─────────────────────┘

  Scheduler never calls motor.moveTo() directly.
  It only calls command.execute() — the command knows the rest.

KEY INVARIANT:
   The invoker is decoupled from the receiver at compile time.
   You can swap, queue, delay, or undo commands without touching the invoker.
```

---

## 🏢 Real World Usage

- **Java `Runnable` / `Callable`** — `Runnable` IS a Command (single `run()` method). `ExecutorService.submit(runnable)` is the invoker. The thread pool fires commands without knowing what they do.
- **Spring `@Async` / `@Scheduled`** — Spring queues method calls as command-like tasks dispatched to a thread pool. The calling thread submits; the pool executes independently.
- **Database transaction log** — Each SQL statement is recorded as a command object. Crash recovery replays them (execute) or rolls them back (undo) by reversing the log.
- **Transactional Outbox pattern** — Each domain event is a Command persisted to an outbox table. A relay process fires them in order — decoupled from the originating transaction.
- **Git commits** — A commit IS a command: `execute()` = apply the diff, `undo()` = `git revert`. The history is a command log.

---

## 🧭 When to Use vs When NOT to Use

| Use Command when | Do NOT use when |
|---|---|
| You need to queue or defer operations | The caller directly controls timing — direct call is simpler |
| You need undo / redo support | The operation is fire-and-forget with no state to reverse |
| You need audit log of operations performed | The "command" is just one method call — Command is overkill |
| You want to decouple who requests from who performs | The invoker and receiver are always the same object |
| You need retry / replay of failed operations | There's only one concrete command type — no polymorphism needed |

**Common mistake:** Wrapping every method call in a Command for "future flexibility." If there's no queue, no undo, and one receiver, a direct call is cleaner.

---

## 🧩 LLD Problems That Use Command Pattern

- **Elevator System** — `MoveToFloorCommand`, `OpenDoorCommand`, `CloseDoorCommand` are queued in the scheduler. SCAN or FCFS algorithm reorders the queue without knowing what the commands do.
- **Text Editor (Undo/Redo)** — `InsertTextCommand`, `DeleteTextCommand`, `BoldTextCommand` all implement `UndoableCommand`. An undo stack pops the last command and calls `undo()`.
- **Vending Machine** — `DispenseItemCommand`, `RefundCoinsCommand` encapsulate state transitions. The machine controller queues them without knowing the physical dispensing mechanism.
- **Job Queue / Rate Limiter** — Deferred tasks are wrapped as Commands, enqueued behind the rate limiter, and fired when capacity allows. The rate limiter never imports the business logic it's protecting.
- **Smart Home / IoT** — `TurnOnLightCommand`, `SetThermostatCommand` sent over a message bus. The hub queues and fires them; devices are the receivers. Supports scheduling and macro (batch) execution.

---

## 🔬 Interview Q&As

### Q: "How does Command differ from Strategy?"
> Both wrap behaviour behind an interface. The difference is intent: **Strategy** encapsulates an *algorithm* that varies at runtime (how to calculate a fee). **Command** encapsulates a *request* — an action to be performed, possibly stored, queued, or undone. Strategy has no concept of undo or queuing. Command is about invoker-receiver decoupling and operation lifecycle.

### Q: "How would you add undo to a text editor using Command?"
> Every edit is an `UndoableCommand` with both `execute()` and `undo()`. When the user types, the controller calls `execute()` and pushes the command onto an undo stack. When the user hits Ctrl+Z, pop the stack and call `undo()`. Redo is a second stack: after undo, push to redo stack. This scales to any number of undo steps without the editor knowing any edit details.

### Q: "What is Java's `Runnable` and how does it relate to Command?"
> `Runnable` IS a Command — it has a single `run()` method (the `execute()`). `ExecutorService` is the invoker that queues and fires Runnables. There's no undo because thread execution can't be reversed. Command is the general pattern; Runnable is a simplified Java-stdlib variant for one-shot execution.

### Q: "How would you make the Elevator scheduler thread-safe?"
> Replace `LinkedList` queue with `LinkedBlockingQueue`. The scheduler's `processNext()` loop becomes a background thread calling `commandQueue.take()` — it blocks when the queue is empty instead of spinning. Multiple threads can safely call `submit()` concurrently because `LinkedBlockingQueue` is thread-safe. One background thread drains — no contention on execution order.

---

## 🧾 TL;DR — One Interviewer-Ready Line

> *"Command wraps a request as an object so the invoker can queue, delay, log, or undo it without knowing what the request does or who handles it. In the Elevator, every floor request is a `MoveToFloorCommand` queued in the scheduler — the SCAN algorithm reorders the queue without importing a single motor method. Java's `Runnable` is the simplest Command — one `run()`, no undo, fired by `ExecutorService`."*

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 2026 | File created. Elevator system as primary worked example. UndoableCommand variant included for text editor. |
