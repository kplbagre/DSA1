/**
 * Charges a fixed flat rate regardless of how long the vehicle was parked.
 * Used for event days, daily passes, etc.
 */
public class FlatRateFeeStrategy implements FeeStrategy {

    private final double flatRate;

    public FlatRateFeeStrategy(double flatRate) {
        this.flatRate = flatRate;
    }

    @Override
    public double calculate(Vehicle vehicle, long durationMinutes) {
        return flatRate;
    }
}
