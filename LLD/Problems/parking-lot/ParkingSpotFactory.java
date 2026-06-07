/**
 * Factory for creating ParkingSpot instances.
 * Adding a new spot type = add one enum value + one case here.
 * No other class changes.
 */
public class ParkingSpotFactory {

    public static ParkingSpot create(SpotType type) {
        switch (type) {
            case COMPACT:
                return new CompactSpot();
            case LARGE:
                return new LargeSpot();
            case HANDICAPPED:
                return new HandicappedSpot();
            case EV:
                return new EVSpot();
            default:
                throw new IllegalArgumentException("Unknown SpotType: " + type);
        }
    }
}
