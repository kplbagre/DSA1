/**
 * A large-size parking spot (trucks, SUVs).
 * Identical logic to CompactSpot — only the SpotType differs.
 */
public class LargeSpot implements ParkingSpot {

    private Vehicle parkedVehicle;

    @Override
    public SpotType getType() {
        return SpotType.LARGE;
    }

    @Override
    public synchronized boolean isAvailable() {
        return parkedVehicle == null;
    }

    @Override
    public synchronized void assignVehicle(Vehicle vehicle) {
        if (parkedVehicle != null) {
            throw new IllegalStateException("LargeSpot already occupied by: " + parkedVehicle);
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
