package tspSolution;

public class AirCity extends City{
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
		
		    final int R = 6371;
		    double lonDistance = Math.toRadians(other.getX() - this.x);
		    double latDistance = Math.toRadians(other.getY() - this.y);
		    
		    double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
		            + Math.cos(Math.toRadians(this.y)) * Math.cos(Math.toRadians(other.getY()))
		            * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
		    
		    double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
		    double distance = R * c;

		    return distance;
		}
}
