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
