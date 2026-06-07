import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One floor in the parking lot.
 * Owns the list of spots on this floor.
 * findAvailableSpot is synchronized — floor-level search is a unit.
 */
public class ParkingFloor {

    private final int floorNumber;
    private final List<ParkingSpot> spots;

    // AtomicInteger (a thread-safe counter — no explicit lock needed for increment/decrement)
    // tracks total available spots; avoids scanning the list just to answer "is this floor full?"
    private final AtomicInteger availableCount;

    public ParkingFloor(int floorNumber, List<ParkingSpot> spots) {
        this.floorNumber = floorNumber;
        this.spots = new ArrayList<>(spots);
        this.availableCount = new AtomicInteger(spots.size());
    }

    /**
     * Returns the first available spot of the requested type, or null if none.
     * synchronized: scan + return must be atomic from the caller's perspective.
     * (ParkingLot.parkVehicle() provides the outer lock, but this is defence-in-depth.)
     */
    public synchronized ParkingSpot findAvailableSpot(SpotType type) {
        for (ParkingSpot spot : spots) {
            if (spot.getType() == type && spot.isAvailable()) {
                return spot;
            }
        }
        return null;
    }

    public void decrementAvailable() {
        availableCount.decrementAndGet();
    }

    public void incrementAvailable() {
        availableCount.incrementAndGet();
    }

    public boolean hasAvailability() {
        return availableCount.get() > 0;
    }

    public int getFloorNumber() {
        return floorNumber;
    }

    public int getAvailableCount() {
        return availableCount.get();
    }
}
