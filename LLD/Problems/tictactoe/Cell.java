/**
 * A single cell on the Tic-Tac-Toe board.
 *
 * Owns its own position (row, col) and current state.
 * mark() guards against double-marking at the cell level.
 */
public class Cell {

    /** Possible states a cell can be in. */
    public enum CellState {
        EMPTY,
        X,
        O
    }

    private final int row;
    private final int col;
    private CellState state;

    public Cell(int row, int col) {
        this.row = row;
        this.col = col;
        // every cell starts empty
        this.state = CellState.EMPTY;
    }

    /**
     * Marks this cell with the given symbol.
     *
     * @param symbol the player's symbol (X or O)
     * @throws IllegalStateException if the cell is already marked
     */
    public void mark(CellState symbol) {
        if (state != CellState.EMPTY) {
            throw new IllegalStateException(
                "Cell (" + row + ", " + col + ") is already marked as " + state
            );
        }
        // record the player's symbol
        this.state = symbol;
    }

    /**
     * Returns true if this cell has been marked by any player.
     */
    public boolean isMarked() {
        return state != CellState.EMPTY;
    }

    public int getRow() {
        return row;
    }

    public int getCol() {
        return col;
    }

    public CellState getState() {
        return state;
    }

    @Override
    public String toString() {
        return state == CellState.EMPTY ? "." : state.name();
    }
}
