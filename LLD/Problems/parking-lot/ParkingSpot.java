/**
 * Contract every parking spot must fulfil.
 * Implementations guard the parkedVehicle field with synchronized methods.
 */
public interface ParkingSpot {

    SpotType getType();

    boolean isAvailable();

    /**
     * Assigns a vehicle to this spot.
     * Throws IllegalStateException if the spot is already occupied.
     */
    void assignVehicle(Vehicle vehicle);

    /**
     * Removes and returns the parked vehicle; the spot becomes available again.
     */
    Vehicle removeVehicle();

    Vehicle getParkedVehicle();
}
