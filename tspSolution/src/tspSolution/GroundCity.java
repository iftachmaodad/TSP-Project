package tspSolution;

public class GroundCity extends City{
	// --- Properties ---
	private final String address;
	
	// --- Constructors ---
	public GroundCity(double x, double y) {this(null, x, y, NO_DEADLINE, null);}
	public GroundCity(double x, double y, double deadline) {this(null, x, y, deadline, null);}
	public GroundCity(String ID, double x, double y) {this(ID, x, y, NO_DEADLINE, null);}
	
	
	public GroundCity(String ID,double x, double y, double deadline, String address) {
		super(ID, x, y, deadline);
		if(address == null || address.isBlank()) address = generateDefaultAddress();
		this.address = address;
	}
	
	// --- Methods ---
	private String generateDefaultAddress() {return String.format("{%.6f, %.6f}", y, x);}
	
	@Override
	public double distance(City other) {
		if (other == null) {
		    System.out.println("WARNING: tried to calculate distance to a null city");
		    return Double.NaN;
		}
		if(!(other instanceof GroundCity)) {
			System.out.println("WARNING: tried to calculate distance to invalid city type");
			return Double.NaN;
		}
		if (this.equals(other))
		    return 0;
		
		//Return NaN for API
		return Double.NaN;
	}
	
	@Override
	public double time(City other) {
		if (other == null) {
		    System.out.println("WARNING: tried to calculate time to a null city");
		    return Double.NaN;
		}
		if(!(other instanceof GroundCity)) {
			System.out.println("WARNING: tried to calculate time to invalid city type");
			return Double.NaN;
		}
		if (this.equals(other))
		    return 0;
		
		//Return NaN for API
		return Double.NaN;
	}
	
	// --- Getters ---
	public String getAddress() {return address;}
}
