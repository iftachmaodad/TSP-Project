package tspSolution;

import java.util.ArrayList;

public class Route {
	//Properties
	private ArrayList<City> path;
	private double totalDistance;
	
	//Constructors
	public Route(City start) {
		path = new ArrayList<City>();
		path.add(start);
		totalDistance = 0;
	}
	
	//Getters
	public int size() {return path.size();}
	public double getDistance() {return totalDistance;}
	public ArrayList<City> getPath() {return new ArrayList<City>(path);}
	
	//Setters
	public void addCity(City other, double distance) {
		if(other == null) {
			System.out.println("ERROR: can't add null city to route");
			return;
		}
		if(Double.isNaN(distance)) System.out.println("WARNING: distance is not a number -> check source");
		if(path.contains(other) && !other.equals(path.getFirst())) System.out.println("WARNING: city " + other + " is already in route");
		
		path.add(other);
		totalDistance += distance;
	}
	
	//Override Methods
	@Override
	public String toString() {
		StringBuilder stringBuilder = new StringBuilder("Route: || ");
		int size = 1;
		for(City onPath : path) {
			if(size == path.size()) {
				stringBuilder.append(onPath.getID()).append(" ||");
				continue;
			}
			size ++;
			stringBuilder.append(onPath.getID()).append(" -> ");
		}
		stringBuilder.append("\nTotal Distance: ").append(String.format("%.2f", totalDistance));
		return stringBuilder.toString();
	}
}
