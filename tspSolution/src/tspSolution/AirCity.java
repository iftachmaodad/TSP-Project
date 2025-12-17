package tspSolution;

public class AirCity extends City{
    // Constant: Average Drone Speed (~60 KM/h)
    private static final double DRONE_SPEED = 16.67; // m/s
	
	// --- Constructors ---
	public AirCity(double x, double y) {super(x, y);}
	public AirCity(double x, double y, double deadline) {super(x, y, deadline);}
	public AirCity(String ID, double x, double y) {super(ID, x, y);}
	public AirCity(String ID, double x, double y, double deadline) {super(ID, x, y, deadline);}
	
	// --- Methods ---
	@Override
	public double distance(City other) {
		if (other == null) {
		    System.out.println("WARNING: tried to calculate distance to a null city");
		    return Double.NaN;
		}
		if(!(other instanceof AirCity)) {
			System.out.println("WARNING: tried to calculate distance to invalid city type");
			return Double.NaN;
		}
		if (this.equals(other))
		    return 0;
		
        final int R = 6371000;
        double lat1 = Math.toRadians(this.y);
        double lat2 = Math.toRadians(other.y);
        double dLat = Math.toRadians(other.y - this.y);
        double dLon = Math.toRadians(other.x - this.x);

        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                   Math.cos(lat1) * Math.cos(lat2) *
                   Math.sin(dLon/2) * Math.sin(dLon/2);
        
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
		}
	
	@Override
	public double time(City other) {
		if (other == null) {
		    System.out.println("WARNING: tried to calculate time to a null city");
		    return Double.NaN;
		}
		if(!(other instanceof AirCity)) {
			System.out.println("WARNING: tried to calculate time to invalid city type");
			return Double.NaN;
		}
		if (this.equals(other))
		    return 0;

		double distance = distance(other);
		
		if(Double.isNaN(distance))
			return Double.NaN;
		
		return distance/DRONE_SPEED;
	}
}
