/**
 * Represents a player in the game.
 *
 * Each player has a display name and the symbol they play (X or O).
 * Immutable after construction — a player's symbol never changes mid-game.
 */
public class Player {

    private final String name;
    private final Cell.CellState symbol;

    /**
     * @param name   display name of the player (e.g. "Alice")
     * @param symbol the symbol this player plays — X or O
     */
    public Player(String name, Cell.CellState symbol) {
        if (symbol == Cell.CellState.EMPTY) {
            throw new IllegalArgumentException("Player symbol cannot be EMPTY");
        }
        this.name = name;
        this.symbol = symbol;
    }

    /**
     * Returns the player's display name.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the symbol (X or O) this player places on the board.
     */
    public Cell.CellState getSymbol() {
        return symbol;
    }

    @Override
    public String toString() {
        return name + "(" + symbol + ")";
    }
}
