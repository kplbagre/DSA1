import java.util.List;

/**
 * Orchestrates a single Tic-Tac-Toe game session.
 *
 * thread-safe:
 *   - makeMove() is synchronized — prevents two threads (two clients) from
 *     concurrently advancing the game state, which would corrupt currentPlayerIndex
 *     and the board simultaneously.
 *
 * Extensibility:
 *   - WinStrategy is injected — swap the win rule without touching Game.
 *   - players is a List — add more than two players (NxN multiplayer) with zero changes here.
 */
public class Game {

    /** Lifecycle states of the game. */
    public enum GameState {
        IN_PROGRESS,
        WON,
        DRAW
    }

    private final Board board;
    private final List<Player> players;
    private final WinStrategy winStrategy;

    // index into players list; advances after each valid move
    private int currentPlayerIndex;
    private GameState state;

    // the player who won; null until the game is WON
    private Player winner;

    /**
     * @param board       the board to play on (size determines NxN)
     * @param players     ordered list of players — turn order follows list order
     * @param winStrategy the win-detection algorithm to use
     */
    public Game(Board board, List<Player> players, WinStrategy winStrategy) {
        if (players == null || players.size() < 2) {
            throw new IllegalArgumentException("A game requires at least two players");
        }
        this.board = board;
        this.players = players;
        this.winStrategy = winStrategy;
        this.currentPlayerIndex = 0;
        this.state = GameState.IN_PROGRESS;
        this.winner = null;
    }

    /**
     * Applies the current player's move at (row, col).
     *
     * synchronized: the read of currentPlayerIndex, the board mutation, the win check,
     * and the index increment must all be one atomic operation. Without this lock, two
     * threads could both enter as the "current player", place two marks, and the turn
     * order would skip or corrupt.
     *
     * @param row zero-based row index
     * @param col zero-based column index
     * @throws IllegalStateException    if the game is already over
     * @throws IllegalArgumentException if (row, col) is out of bounds
     * @throws IllegalStateException    if the target cell is already marked
     */
    public synchronized void makeMove(int row, int col) {
        // reject moves once the game has ended
        if (state != GameState.IN_PROGRESS) {
            throw new IllegalStateException("Game is over. State: " + state);
        }

        Player current = players.get(currentPlayerIndex);

        // delegate bound-checking and cell-empty check to Board
        board.makeMove(row, col, current.getSymbol());

        // check if this move wins the game
        if (winStrategy.checkWin(board, current)) {
            state = GameState.WON;
            winner = current;
            return;
        }

        // check for draw — board full with no winner
        if (board.isFull()) {
            state = GameState.DRAW;
            return;
        }

        // advance to the next player (wraps around for multiplayer)
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
    }

    /**
     * Returns the player whose turn it currently is.
     * If the game is over, returns the last player who moved.
     */
    public synchronized Player getCurrentPlayer() {
        return players.get(currentPlayerIndex);
    }

    /**
     * Returns the current lifecycle state of the game.
     */
    public synchronized GameState getState() {
        return state;
    }

    /**
     * Returns the winning player, or null if the game is still in progress or ended in a draw.
     */
    public synchronized Player getWinner() {
        return winner;
    }

    /**
     * Returns the board for read-only inspection (e.g. rendering).
     */
    public Board getBoard() {
        return board;
    }
}
