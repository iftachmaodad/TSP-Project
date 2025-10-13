package tspSolution;

public class City {
	//Properties
	private static int index = 1;
	
	private String ID;
	private double x, y;
	
	//Constructors
	public City(double x, double y) {
		this("City", x, y);
	}

	public City(String ID ,double x, double y) {
		//ID builder temporary for now
		this.ID = ID + index;
		this.x = x;
		this.y = y;
		index++;
	}
	
	//Methods
	//Need to change distance formula when moving to real coordinates
	public double distance(City other) {
		if(this.equals(other))
			return 0;
		return Math.sqrt(Math.pow(this.x-other.x, 2)+Math.pow(this.y - other.y, 2));
	}

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
		return java.util.Objects.hash(Math.round(x * 1e6), Math.round(y * 1e6));
	}
	
	@Override
	public String toString() {
		return ID + " - {" + String.format("%.2f",x) + ", " + String.format("%.2f",y) + "}"; 
	}
	
	//Getters
	public String getID() {return ID;}
	public double getX() {return x;}
	public double getY() {return y;}
	
	//Setters
	public void setID(String ID) {this.ID = ID;}
	public void setX(double x) {this.x = x;}
	public void setY(double y) {this.y = y;}

}
