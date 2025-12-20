package tspSolution;

import java.util.HashMap;
import java.util.Objects;

public abstract class City {
	// --- Properties ---
	private static final HashMap<Class<? extends City>, Integer> typeCounter = new HashMap<>();
	protected final String ID;
	
	// X -> longitude Y -> latitude
	protected final double x, y;
	
	//Deadline
	public final static double NO_DEADLINE = Double.MAX_VALUE;
	protected final double deadline;
	
	// --- Constructors ---
	public City(double x, double y) {this(null, x, y, NO_DEADLINE);}
	public City(double x, double y, double deadline) {this(null, x, y, deadline);}
	public City(String ID, double x, double y) {this(ID, x, y, NO_DEADLINE);}
	
	public City(String ID ,double x, double y, double deadline) {
		if(x < -180 || x > 180 || y < -90 || y > 90) {
			System.out.println("WARNING: city coordinates out of normal lon/lat range -> entering edge cases");
			
			x = ((x + 180) % 360 + 360) % 360 - 180;
			y = Math.max(-90, Math.min(90, y));
		}
		if(ID == null || ID.isBlank() || CityRegistry.startsWithIgnoreCase(ID)) ID = generateDefaultName();
		if(deadline <= 0) deadline = NO_DEADLINE;
		this.ID = ID;
		this.deadline = deadline;
		this.x = x;
		this.y = y;
	}
	
	// --- Methods ---
	public boolean hasDeadline() {return deadline != NO_DEADLINE;}
	
	private String generateDefaultName() {
        Class<? extends City> type = this.getClass();
        int count = typeCounter.getOrDefault(type, 0) + 1;
        typeCounter.put(type, count);
        return type.getSimpleName() + count;
    }
	
	// --- Abstract Methods ---
	public abstract double distance(City other);
	public abstract double time(City other);
	
	//Override Methods
	@Override
	public boolean equals(Object obj) {
		if(this == obj)
			return true;
		if(obj == null || this.getClass() != obj.getClass())
			return false;
		City other = (City) obj;
		//Rounding for float point errors
	    final double EPS = 1e-9;
	    return Math.abs(this.x - other.x) < EPS &&
	           Math.abs(this.y - other.y) < EPS;
	}
	
	@Override
	public int hashCode() {
		//Rounding for float point errors
		return Objects.hash(Math.round(x * 1e6), Math.round(y * 1e6));
	}
	
	@Override
	public String toString() {
		String returnString = ID + " - {" + String.format("%.2f",x) + ", " + String.format("%.2f",y) + "}"; 
		if(hasDeadline()) returnString += "Due - " + deadline;
		return returnString;
	}
	
	// --- Getters ---
	public String getID() {return ID;}
	public double getDeadline() {return deadline;}
	public double getX() {return x;}
	public double getY() {return y;}
	
}
