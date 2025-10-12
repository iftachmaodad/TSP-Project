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
		path.add(other);
		totalDistance += distance;
	}
	
	//Override Methods
	@Override
	public String toString() {
		StringBuilder stringBuilder = new StringBuilder("||ROUTE START||\n");
		for(City onPath : path) stringBuilder.append(onPath + " -> ");
		stringBuilder.append("\n||ROUTE END||\n Total Distance: ").append(String.format("%.2", totalDistance));
		return stringBuilder.toString();
	}
}
