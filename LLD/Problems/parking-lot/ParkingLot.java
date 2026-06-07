import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main orchestrator for the parking lot.
 *
 * thread-safe:
 *   - parkVehicle() is synchronized — prevents check-then-act race on spot assignment
 *   - activeTickets is a ConcurrentHashMap (a thread-safe hash map) — safe concurrent reads/writes
 *   - unparkVehicle() does not need a full lock; ConcurrentHashMap.remove() is atomic
 */
public class ParkingLot {

    private final String name;
    private final List<ParkingFloor> floors;
    private final FeeStrategy feeStrategy;

    // ConcurrentHashMap: ticketId → ticket; safe for concurrent park + unpark operations
    private final Map<String, ParkingTicket> activeTickets;

    public ParkingLot(String name, List<ParkingFloor> floors, FeeStrategy feeStrategy) {
        this.name = name;
        this.floors = floors;
        this.feeStrategy = feeStrategy;
        this.activeTickets = new ConcurrentHashMap<>();
    }

    /**
     * Parks a vehicle in the first available spot of the requested type.
     *
     * synchronized: the check (findAvailableSpot) and the assign (assignVehicle) must be
     * one atomic operation. Without this lock, Thread A and B can both find the same spot
     * available and both call assignVehicle() — two cars in one spot.
     *
     * @throws IllegalStateException if no available spot of the requested type exists
     */
    public synchronized ParkingTicket parkVehicle(Vehicle vehicle, SpotType spotType) {
        for (ParkingFloor floor : floors) {
            ParkingSpot spot = floor.findAvailableSpot(spotType);
            if (spot != null) {
                // Assign vehicle to the spot
                spot.assignVehicle(vehicle);
                floor.decrementAvailable();

                // Issue a ticket
                String ticketId = UUID.randomUUID().toString();
                ParkingTicket ticket = new ParkingTicket(ticketId, vehicle, spot, floor.getFloorNumber());
                activeTickets.put(ticketId, ticket);
                return ticket;
            }
        }
        throw new IllegalStateException("No available spot of type: " + spotType + " in " + name);
    }

    /**
     * Unparks a vehicle: vacates the spot and calculates the fee.
     *
     * No full lot lock needed here — ConcurrentHashMap.remove() is atomic.
     * Only this thread holds the ticket reference after remove(), so spot operations are safe.
     *
     * @return the completed ticket with fee and exit time set
     * @throws IllegalArgumentException if the ticketId is not found
     */
    public ParkingTicket unparkVehicle(String ticketId) {
        ParkingTicket ticket = activeTickets.remove(ticketId);
        if (ticket == null) {
            throw new IllegalArgumentException("Ticket not found: " + ticketId);
        }

        // Record exit time and calculate duration
        LocalDateTime exitTime = LocalDateTime.now();
        ticket.setExitTime(exitTime);
        long durationMinutes = Duration.between(ticket.getEntryTime(), exitTime).toMinutes();

        // Strategy calculates the fee — ParkingLot doesn't know which algorithm runs
        double fee = feeStrategy.calculate(ticket.getVehicle(), durationMinutes);
        ticket.setFee(fee);

        // Vacate the spot; increment floor availability counter
        ticket.getSpot().removeVehicle();
        findFloor(ticket.getFloorNumber()).incrementAvailable();

        return ticket;
    }

    /**
     * Returns total available spots of the given type across all floors.
     */
    public int getAvailableCount(SpotType type) {
        int total = 0;
        for (ParkingFloor floor : floors) {
            // Note: this scans spot lists — acceptable at interview scale
            if (floor.findAvailableSpot(type) != null) {
                total++;
            }
        }
        return total;
    }

    private ParkingFloor findFloor(int floorNumber) {
        for (ParkingFloor floor : floors) {
            if (floor.getFloorNumber() == floorNumber) {
                return floor;
            }
        }
        throw new IllegalStateException("Floor not found: " + floorNumber);
    }

    public String getName() {
        return name;
    }
}
