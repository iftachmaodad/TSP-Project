package tspSolution;

public class City {
	
	private static int index = 0;
	
	private String ID;
	private double x, y;
	
	public City(double x, double y) {
		new City("City", x, y);
	}

	public City(String ID ,double x, double y) {
		this.ID = ID + index;
		this.x = x;
		this.y = y;
		index++;
	}
	
	public double distance(City other) {
		return Math.sqrt(Math.pow(this.x-other.x, 2)+Math.pow(this.y - other.y, 2));
	}
}
