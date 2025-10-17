package tspSolution;

public class City {
	//Properties
	private static int index = 1;
	
	// X -> longitude Y -> latitude
	private String ID;
	private double x, y;
	
	//Constructors
	public City(double x, double y) {
		this("City", x, y);
	}

	public City(String ID ,double x, double y) {
		//ID builder temporary for now
		if(x < -180 || x > 180 || y < -90 || y > 90) {
			System.out.println("WARNING: city coordinates out of normal lon/lat range -> entering edge cases");
			
			x = ((x + 180) % 360 + 360) % 360 - 180;
			y = Math.max(-90, Math.min(90, y));
		}
		if(ID == null || ID.toLowerCase().startsWith("city")) ID = "City" + index;
		this.ID = ID;
		this.x = x;
		this.y = y;
		index++;
	}
	
	//Methods
	public double distance(City other) {
		if (other == null) {
		    System.out.println("WARNING: tried to calculate distance to a null city");
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
