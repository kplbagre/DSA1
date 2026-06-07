/**
 * A compact-size parking spot.
 * Spot-level methods are synchronized to guard parkedVehicle.
 * Note: ParkingLot.parkVehicle() is also synchronized — the spot-level
 * lock is a secondary defence-in-depth guard.
 */
public class CompactSpot implements ParkingSpot {

    private Vehicle parkedVehicle;

    @Override
    public SpotType getType() {
        return SpotType.COMPACT;
    }

    @Override
    public synchronized boolean isAvailable() {
        return parkedVehicle == null;
    }

    @Override
    public synchronized void assignVehicle(Vehicle vehicle) {
        if (parkedVehicle != null) {
            throw new IllegalStateException("CompactSpot already occupied by: " + parkedVehicle);
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
