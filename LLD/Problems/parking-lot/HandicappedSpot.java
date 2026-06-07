/**
 * A handicapped-reserved parking spot.
 * Identical logic to CompactSpot — only the SpotType differs.
 * Business rule (permit validation) can be added in a Validator layer.
 */
public class HandicappedSpot implements ParkingSpot {

    private Vehicle parkedVehicle;

    @Override
    public SpotType getType() {
        return SpotType.HANDICAPPED;
    }

    @Override
    public synchronized boolean isAvailable() {
        return parkedVehicle == null;
    }

    @Override
    public synchronized void assignVehicle(Vehicle vehicle) {
        if (parkedVehicle != null) {
            throw new IllegalStateException("HandicappedSpot already occupied by: " + parkedVehicle);
        }
        this.parkedVehicle = vehicle;
    }

    @Override
    public synchronized Vehicle removeVehicle() {
        Vehicle vacating = this.parkedVehicle;
        this.parkedVehicle = null;
        return vacating;
    }

    @Override
    public synchronized Vehicle getParkedVehicle() {
        return parkedVehicle;
    }
}
