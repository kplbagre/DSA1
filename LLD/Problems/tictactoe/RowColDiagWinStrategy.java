/**
 * Classic Tic-Tac-Toe win detection.
 *
 * A player wins if they own every cell in any row, any column,
 * the main diagonal (top-left to bottom-right), or the anti-diagonal
 * (top-right to bottom-left). Works for any NxN board size.
 */
public class RowColDiagWinStrategy implements WinStrategy {

    /**
     * Checks whether the given player has a complete row, column, or diagonal.
     *
     * @param board  the current board state
     * @param player the player whose symbol is being checked
     * @return true if that player wins
     */
    @Override
    public boolean checkWin(Board board, Player player) {
        Cell.CellState symbol = player.getSymbol();
        int size = board.getSize();

        // check every row
        for (int r = 0; r < size; r++) {
            if (isRowWin(board, r, symbol, size)) {
                return true;
            }
        }

        // check every column
        for (int c = 0; c < size; c++) {
            if (isColWin(board, c, symbol, size)) {
                return true;
            }
        }

        // check main diagonal (top-left → bottom-right)
        if (isMainDiagWin(board, symbol, size)) {
            return true;
        }

        // check anti-diagonal (top-right → bottom-left)
        if (isAntiDiagWin(board, symbol, size)) {
            return true;
        }

        return false;
    }

    /**
     * Returns true if every cell in the given row belongs to symbol.
     */
    private boolean isRowWin(Board board, int row, Cell.CellState symbol, int size) {
        for (int c = 0; c < size; c++) {
            if (board.getCell(row, c).getState() != symbol) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns true if every cell in the given column belongs to symbol.
     */
    private boolean isColWin(Board board, int col, Cell.CellState symbol, int size) {
        for (int r = 0; r < size; r++) {
            if (board.getCell(r, col).getState() != symbol) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns true if every cell on the main diagonal belongs to symbol.
     */
    private boolean isMainDiagWin(Board board, Cell.CellState symbol, int size) {
        for (int i = 0; i < size; i++) {
            if (board.getCell(i, i).getState() != symbol) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns true if every cell on the anti-diagonal belongs to symbol.
     */
    private boolean isAntiDiagWin(Board board, Cell.CellState symbol, int size) {
        for (int i = 0; i < size; i++) {
            // anti-diagonal: row i, col (size - 1 - i)
            if (board.getCell(i, size - 1 - i).getState() != symbol) {
                return false;
            }
        }
        return true;
    }
}
