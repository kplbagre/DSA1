/**
 * Strategy interface for parking fee calculation.
 * Swap implementations without changing ParkingLot.
 *
 * Known implementations:
 *   HourlyFeeStrategy  — charges per hour (ceiling)
 *   FlatRateFeeStrategy — fixed charge regardless of duration
 */
public interface FeeStrategy {

    /**
     * @param vehicle         the vehicle being charged
     * @param durationMinutes time parked, from entry to exit
     * @return fee in rupees (or local currency)
     */
    double calculate(Vehicle vehicle, long durationMinutes);
}
