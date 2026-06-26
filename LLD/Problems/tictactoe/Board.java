/**
 * The NxN game board.
 *
 * Owns the grid of cells and enforces move validity.
 * Board is intentionally unaware of win logic — that belongs to WinStrategy.
 */
public class Board {

    private final int size;
    private final Cell[][] grid;

    // tracks how many cells have been marked; avoids scanning the grid for isFull()
    private int markedCount;

    /**
     * Constructs an NxN board with all cells in EMPTY state.
     *
     * @param size the dimension of the board (3 for standard Tic-Tac-Toe)
     */
    public Board(int size) {
        this.size = size;
        this.grid = new Cell[size][size];
        this.markedCount = 0;

        // initialise every cell to EMPTY
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                grid[r][c] = new Cell(r, c);
            }
        }
    }

    /**
     * Applies a player's symbol to the cell at (row, col).
     *
     * Validates that (row, col) is within bounds and that the target cell is
     * not already occupied before delegating to Cell.mark().
     *
     * @param row    zero-based row index
     * @param col    zero-based column index
     * @param symbol the player's symbol to place
     * @throws IllegalArgumentException if (row, col) is out of bounds
     * @throws IllegalStateException    if the target cell is already marked
     */
    public void makeMove(int row, int col, Cell.CellState symbol) {
        // validate bounds before touching the grid
        if (row < 0 || row >= size || col < 0 || col >= size) {
            throw new IllegalArgumentException(
                "Position (" + row + ", " + col + ") is out of bounds for size " + size
            );
        }

        Cell target = grid[row][col];

        // Cell.mark() throws if already marked; also guard here for a clear message
        if (target.isMarked()) {
            throw new IllegalStateException(
                "Cell (" + row + ", " + col + ") is already occupied"
            );
        }

        // delegate to cell — cell records the symbol
        target.mark(symbol);
        markedCount++;
    }

    /**
     * Returns the cell at (row, col) without bounds-checking.
     * Callers (WinStrategy) are responsible for iterating within [0, size).
     */
    public Cell getCell(int row, int col) {
        return grid[row][col];
    }

    /**
     * Returns true when every cell on the board has been marked.
     */
    public boolean isFull() {
        return markedCount == size * size;
    }

    /**
     * Returns the dimension of the board (e.g. 3 for a 3×3 grid).
     */
    public int getSize() {
        return size;
    }

    /**
     * Renders the board as a human-readable string for debugging or CLI output.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                sb.append(grid[r][c].toString());
                if (c < size - 1) {
                    sb.append("|");
                }
            }
            sb.append("\n");
            if (r < size - 1) {
                // print separator row
                sb.append("-".repeat(size * 2 - 1)).append("\n");
            }
        }
        return sb.toString();
    }
}
