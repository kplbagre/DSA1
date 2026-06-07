/**
 * An electric-vehicle parking spot.
 * Identical logic to CompactSpot — only the SpotType differs.
 * Charging capability can be added by also implementing a ChargingCapable interface.
 */
public class EVSpot implements ParkingSpot {

    private Vehicle parkedVehicle;

    @Override
    public SpotType getType() {
        return SpotType.EV;
    }

    @Override
    public synchronized boolean isAvailable() {
        return parkedVehicle == null;
    }

    @Override
    public synchronized void assignVehicle(Vehicle vehicle) {
        if (parkedVehicle != null) {
            throw new IllegalStateException("EVSpot already occupied by: " + parkedVehicle);
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
