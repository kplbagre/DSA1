# Tic-Tac-Toe

> **Standard followed:** `LLD/notes-standards.md`

---

## Problem Statement

Design a Tic-Tac-Toe game engine that supports two (or more) players taking turns to mark cells on an NxN board. When a player completes a full row, column, or diagonal they win. If all cells are filled with no winner, the game ends in a draw. The engine must be thread-safe so that a future WebSocket server can drive it without corrupting game state.

---

## Requirements

**Functional:**
- Support an NxN board (default 3x3, configurable)
- Two or more players — each assigned a unique symbol (X, O, …)
- Players take turns in the order they were added; turns rotate cyclically
- A move is valid only if: the cell is within bounds and the cell is EMPTY
- After each move, detect win (full row / column / diagonal) or draw (board full, no winner)
- Expose current player, game state (IN_PROGRESS, WON, DRAW), and winner

**Non-functional:**
- **Thread-safe** — `makeMove()` is `synchronized`; two WebSocket frames arriving concurrently must never both succeed for the same player turn
- **Extensible to NxN** — `Board` is constructed with `size`; all loops iterate to `size`; `RowColDiagWinStrategy` works for any N
- **Multiplayer extension point** — `players` is a `List<Player>`; adding a third or fourth player requires zero changes to `Game`, `Board`, or `WinStrategy`
- **Win strategy is swappable** — injecting a different `WinStrategy` (e.g., connect-K) requires zero changes to `Game`

---

## Class Design

### Visual — Class Structure

```
┌──────────────────────────────────────────────────────────┐
│                          Game                            │
│  - board: Board                                          │
│  - players: List<Player>                                 │
│  - winStrategy: WinStrategy                              │
│  - currentPlayerIndex: int                               │
│  - state: GameState  (IN_PROGRESS | WON | DRAW)          │
│  - winner: Player                                        │
│  + makeMove(row, col)   [synchronized]                   │
│  + getCurrentPlayer(): Player                            │
│  + getState(): GameState                                 │
│  + getWinner(): Player                                   │
└───┬────────────────────────┬────────────────────────┬────┘
    │ owns 1                 │ owns 1..*              │ uses
    ▼                        ▼                        ▼
┌──────────────┐    ┌─────────────────┐   ┌──────────────────────┐
│    Board     │    │     Player      │   │  <<interface>>       │
│  - size: int │    │ - name: String  │   │  WinStrategy         │
│  - grid: [][] │   │ - symbol: Enum  │   │  + checkWin(Board,   │
│  + makeMove()│    │ + getName()     │   │            Player)   │
│  + getCell() │    │ + getSymbol()   │   └──────────┬───────────┘
│  + isFull()  │    └─────────────────┘              │ implements
└──────┬───────┘                                     ▼
       │ 1..*                             RowColDiagWinStrategy
       ▼                                   checks rows, cols,
┌──────────────────┐                       main diag, anti-diag
│       Cell       │
│  - row: int      │
│  - col: int      │
│  - state: Enum   │  CellState { EMPTY, X, O }
│  + mark(symbol)  │
│  + isMarked()    │
└──────────────────┘

KEY INVARIANT:
   Game holds the lock — makeMove() is one atomic read-check-mark-evaluate.
   No two threads can advance the game state simultaneously.
```

---

## Key Interfaces

```java
/**
 * Strategy interface for win detection.
 *
 * Extracting win logic into its own strategy keeps Board simple and lets
 * us swap in alternative rules (e.g., connect-K instead of full row/col/diag)
 * without touching Game or Board.
 *
 * Known implementations:
 *   RowColDiagWinStrategy — classic Tic-Tac-Toe: full row, column, or diagonal
 */
public interface WinStrategy {

    /**
     * Returns true if the given player has won on the current board state.
     *
     * Called after every move with the player who just moved —
     * only their symbol needs to be checked.
     *
     * @param board  the current board state
     * @param player the player whose last move is being evaluated
     * @return true if that player has achieved a winning line
     */
    boolean checkWin(Board board, Player player);
}
```

---

## Design Decisions

| Decision | Why |
|---|---|
| **WinStrategy extracted as interface** | Win rules vary independently of Game and Board. Classic row/col/diag, connect-K, and torus-wrap are all different algorithms. Each gets its own class. Game calls `winStrategy.checkWin()` without knowing which runs — open-closed principle. |
| **Board owns the Cell grid** | Board is the authority on cell positions and bounds. WinStrategy reads cells via `board.getCell(r, c)` — it never holds its own reference to the grid. One source of truth. |
| **`synchronized` on `makeMove()`** | The read of `currentPlayerIndex`, the board mutation, the win check, and the index increment must be one atomic operation. Without the lock, two WebSocket frames can both enter as the "current player" and both place a mark — corrupting turn order and potentially producing a false win. |
| **`players` is a `List<Player>`** | Supports rotation over any number of players with `(index + 1) % players.size()`. Adding a third player for an experimental variant requires only passing a longer list. |
| **`Cell.mark()` throws on double-mark** | Defence-in-depth: even if `Board.makeMove()` is called without checking `isMarked()` first, the cell refuses to be overwritten. Invariant lives as close to the data as possible. |

---

## Visual — Object Interaction

```
Player Makes a Move:

Client         Game (synchronized)        Board              Cell
  │                   │                     │                  │
  │  makeMove(r, c)   │                     │                  │
  │──────────────────▶│  LOCK acquired       │                  │
  │                   │                     │                  │
  │                   │  validate state     │                  │
  │                   │  current = players  │                  │
  │                   │    .get(index)      │                  │
  │                   │                     │                  │
  │                   │  board.makeMove(r,c,symbol)            │
  │                   │────────────────────▶│                  │
  │                   │                     │  bounds check    │
  │                   │                     │  cell.isMarked() │
  │                   │                     │──────────────────▶
  │                   │                     │◀── false ────────│
  │                   │                     │  cell.mark(sym)  │
  │                   │                     │──────────────────▶
  │                   │                     │  markedCount++   │
  │                   │◀── returns ─────────│                  │
  │                   │                     │                  │
  │                   │  winStrategy.checkWin(board, current)  │
  │                   │  → false (game continues)              │
  │                   │                     │                  │
  │                   │  board.isFull() → false                │
  │                   │                     │                  │
  │                   │  currentPlayerIndex = (index+1) % n   │
  │                   │  LOCK released      │                  │
  │◀── returns ───────│                     │                  │

KEY INVARIANT:
   The entire read-mark-evaluate-advance cycle runs under one lock.
   No interleaving is possible between any two concurrent makeMove() calls.
```

---

## Concurrency

**Why `makeMove()` must be synchronized:**

`makeMove()` performs a compound action — it reads `currentPlayerIndex`, places a mark, evaluates the win condition, and increments the index. These four steps are individually safe but collectively a check-then-act race:

| Step | Without lock | With `synchronized` |
|---|---|---|
| Read `currentPlayerIndex` | Both Thread A and B read `index = 0` | Only one thread runs at a time |
| `board.makeMove()` | Both mark a cell as Player-0's symbol | Sequential; second call sees up-to-date board |
| `winStrategy.checkWin()` | May evaluate a board with two marks from the same player | Evaluates exactly one new mark |
| `currentPlayerIndex++` | Both threads increment from 0 → only Player-0 ever moves | Advances cleanly to Player-1 after Player-0's turn |

**Race condition prevented:**

Without the lock, Thread A (Player-0's WebSocket frame) and Thread B (a duplicate or racing frame) both read `currentPlayerIndex = 0`, both call `board.makeMove()` with Player-0's symbol, and the game logs two moves for the same player — turn order is permanently corrupted. The `synchronized` keyword ensures the entire compound action is atomic: once Thread A enters `makeMove()`, Thread B waits at the monitor until A exits.

---

## SDE-3 Differentiator — Multiplayer over WebSocket

To extend this engine for real-time multiplayer over WebSocket, introduce a `GameSession` as the WebSocket room abstraction: each session holds a `Game` instance, a map of player IDs to their WebSocket channels, and a reference to a `GameEventPublisher`. When a client sends a move frame, the session's handler calls the existing `game.makeMove(row, col)` — the server remains the authoritative source of truth, so clients can never spoof turn order or board state. After a valid move, the session publishes a `MoveMadeEvent` (containing the updated board snapshot, the moving player, and the new `GameState`) to a Kafka topic partitioned by `gameId` — this guarantees ordering within a game while allowing horizontal scaling across many concurrent games. A downstream WebSocket push service consumes the Kafka topic and fans the event out to every connected client in the session over their persistent WebSocket connection. Reconnecting clients request the current board snapshot from a Redis cache (keyed by `gameId`) rather than replaying the full Kafka log. The core `Game`, `Board`, and `WinStrategy` classes need zero changes — the WebSocket and Kafka concerns live entirely in the session layer above them.

---

## Coding Skeleton

**Interview coding order — write in this sequence to never get stuck:**

1. **Enum** — `CellState` inside `Cell` (zero dependencies, write first)
2. **Cell** — owns row, col, state; `mark()` and `isMarked()`
3. **Player** — name + symbol (depends only on `CellState`)
4. **Interface** — `WinStrategy` (define the contract before any class uses it)
5. **Board** — grid of `Cell`s; `makeMove()`, `getCell()`, `isFull()`
6. **`RowColDiagWinStrategy`** — implements `WinStrategy`; loops rows, cols, diagonals
7. **`Game`** — orchestrator: inject board + players + strategy; synchronized `makeMove()`

**Why this order?** Enums → leaf classes → interfaces → composed classes → orchestrator. Nothing at step N depends on something not yet written.

---

## Concurrency Q&As

### Q: "Walk me through what goes wrong without `synchronized` on `makeMove()`."
> Thread A (Alice's frame) and Thread B (a racing duplicate of Alice's frame) both read `currentPlayerIndex = 0`. Both call `board.makeMove()` with symbol X. Board marks two cells as X. `winStrategy.checkWin()` runs twice on a board that has two X marks placed in this "turn". Turn index increments twice — it wraps back to 0. Alice effectively moves twice; Bob's turn is skipped. The synchronized block collapses read + mark + check + advance into one indivisible unit: only one thread enters, all others wait.

### Q: "Why Strategy for win detection instead of a method on Board?"
> Because win rules vary independently of the board data structure. Classic row/col/diag, connect-K, torus-wrap, and "no diagonal" variants are all different algorithms, not parameter differences. With `WinStrategy`, I inject the right one at construction time — `Game` calls `checkWin()` without knowing which algorithm runs. Adding a new win rule is one new class and one constructor argument change. With win logic on `Board`, every variant requires editing `Board`.

### Q: "How do you extend this to a 4-player game?"
> Pass four `Player` objects in the list. `currentPlayerIndex = (currentPlayerIndex + 1) % players.size()` already rotates over any number of players. `Board`, `Cell`, `WinStrategy`, and all win-check logic are untouched. The only decision is assigning each player a distinct `CellState` symbol — which means extending the `CellState` enum with `P3`, `P4` etc. Everything else composes cleanly.

---

## Patterns Used

- **Strategy** — `WinStrategy` swaps the win-detection algorithm at runtime. See **`LLD/DesignPatterns/01-factory-strategy.md`** (Strategy section).
- **Single Responsibility** — `Cell` knows only its own state; `Board` knows only grid management; `Game` orchestrates; `WinStrategy` knows only win detection.
