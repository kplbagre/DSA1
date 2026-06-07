/**
 * Charges per hour, rounding up to the next full hour.
 * Example: 75 minutes → 2 hours → 2 * ratePerHour
 */
public class HourlyFeeStrategy implements FeeStrategy {

    private final double ratePerHour;

    public HourlyFeeStrategy(double ratePerHour) {
        this.ratePerHour = ratePerHour;
    }

    @Override
    public double calculate(Vehicle vehicle, long durationMinutes) {
        // Math.ceil: 61 minutes → 2 hours, not 1
        double hours = Math.ceil(durationMinutes / 60.0);
        return hours * ratePerHour;
    }
}
