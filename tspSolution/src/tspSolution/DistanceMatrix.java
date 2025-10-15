package tspSolution;

import java.util.HashMap;
import java.util.HashSet;

public class DistanceMatrix {
	//Properties
	private static DistanceMatrix instance = null;
	
	private HashSet<City> cities;
	private HashMap<City, HashMap<City, Double>> matrix;
	
	//Constructors
	private DistanceMatrix() {
		matrix = new HashMap<City , HashMap<City, Double>>();
		cities = new HashSet<City>();
	}
	
	private DistanceMatrix(HashSet<City> targets) {
		if(targets != null) {
			matrix = new HashMap<City , HashMap<City, Double>>();
			cities = new HashSet<City>(targets);
			this.matrixBuilder(cities);
		}else {
			matrix = new HashMap<City , HashMap<City, Double>>();
			cities = new HashSet<City>();
		}
	}
	
	//Singleton Methods
	public static DistanceMatrix getInstance() {
		if(instance == null) {
			instance = new DistanceMatrix();
		}
		return instance;
	}
	
	public static DistanceMatrix getInstance(HashSet<City> targets) {
		if(instance == null) {
			if(targets != null)
				instance = new DistanceMatrix(new HashSet<City>(targets));
			else {
				System.out.println("ERROR: tried initilizing with null set -> initilizing empty matrix");
				instance = new DistanceMatrix();
			}
		}
		return instance;
	}
	
	public static void reset() {
	    instance = null;
	}
	
	//Methods
	private void matrixBuilder(HashSet<City> targets) {
		if(targets == null) {
			System.out.println("ERROR: can't build matrix off of null reference");
			return;
		}
		
		if(!matrix.isEmpty()) { 
			matrix.clear();
			cities.clear();
		}
		
		for(City fromCity : targets) {
			if(fromCity == null) continue;
			cities.add(fromCity);
			matrix.put(fromCity, new HashMap<City , Double>());
			for(City toCity : targets) {
				if(toCity == null) continue;
				matrix.get(fromCity).put(toCity, fromCity.distance(toCity));
			}
		}
	}
	
	public double getDistance(City a, City b) {
		if(a == null || b == null) {
			System.out.println("ERROR: NullPointerException -> one or more of the cities you entered is null");
			return Double.NaN;
		}
		if(!matrix.containsKey(a) || !matrix.containsKey(b)) {
			System.out.println("ERROR: can't calculate distance -> one or more of the cities you entered aren't in the matrix");
			return Double.NaN;
		}
		
		Double distance = matrix.get(a).get(b);
		if(distance == null) {
			System.out.println("ERROR: distance between (" + a + ") and (" + b + ") not found");
			return Double.NaN;
		}
		return distance;
	}
			
	
	public void addCity(City other) {
		if(other == null) {
			System.out.println("ERROR: can't add city -> value is null");
			return;
		}
		
		if(matrix.isEmpty()) {
			cities.add(other);
			matrix.put(other, new HashMap<City, Double>());
			matrix.get(other).put(other, 0.0);
			return;
		}
		
		if(!matrix.containsKey(other)) {
			cities.add(other);
			matrix.put(other, new HashMap<City, Double>());
			for(City existing : matrix.keySet()) {
				if(existing.equals(other)) continue;
				matrix.get(existing).put(other, existing.distance(other));
			}
			
			for(City existing : matrix.keySet()) 
				matrix.get(other).put(existing, other.distance(existing));
		}else
			System.out.println("ERROR: can't add city (" + other + ") -> already exists in matrix");
	}
	
	public void removeCity(City other) {
		if(other == null) {
			System.out.println("ERROR: can't remove city -> value is null");
			return;
		}
		
		if(matrix.containsKey(other)) {
			cities.remove(other);
			matrix.remove(other);
			for(City existing : matrix.keySet()) {
				matrix.get(existing).remove(other);
			}
		}else
			System.out.println("ERROR: can't remove city (" + other + ") -> city doesn't exist in matrix");
	}
	
	
	//Utility Methods
	public int size() {return matrix.size();}
	public boolean contains(City other) {return matrix.containsKey(other);}
	public HashSet<City> getCities() {return new HashSet<City>(cities);}
	
	public boolean validateMatrix() {
		boolean flagged = true;
		
		for(City fromCity : cities) {
			for(City toCity : cities) {
				Double dist = matrix.get(fromCity).get(toCity);
				if(dist == null|| dist.isNaN() || dist < 0) {
					System.out.println("WARNING: integrity issue between " + fromCity + " and " + toCity);
					flagged = false;
				}
				if(!matrix.get(fromCity).get(toCity).equals(matrix.get(toCity).get(fromCity))) {
					System.out.println("WARNING: matrix asymmetry between " + fromCity + " and " + toCity);
					flagged = false;
				}
			}
		}
		
		return flagged;
	}
	
	//Override Methods | toString Methods
	@Override
	public String toString() {
		if(matrix.isEmpty()) return "";
		
		StringBuilder stringBuilder = new StringBuilder("Cities: " + cities.toString() + "\n\n");
		stringBuilder.append(String.format("%10s", "[Matrix]"));
		
		for(City existing : matrix.keySet())
			stringBuilder.append(String.format("%11s", existing.getID()));
		stringBuilder.append("\n");
		
		for(City fromCity : matrix.keySet()) {
			stringBuilder.append(String.format("%10s", fromCity.getID())).append(":");
			for(City toCity : matrix.keySet()) {
				String floatlim = String.format("%10.2f",matrix.get(fromCity).get(toCity));
				stringBuilder.append(floatlim + " ");
			}
			stringBuilder.append("\n");
		}
			
		return stringBuilder.toString();
	}
	
	public String toString(boolean topORbottom) {
		if(matrix.isEmpty()) return "";
		
		if(topORbottom) return "Cities: " + cities.toString() + "\n";
		
		StringBuilder stringBuilder = new StringBuilder(String.format("%10s", "[Matrix]"));
		
		for(City existing : matrix.keySet())
			stringBuilder.append(String.format("%11s", existing.getID()));
		stringBuilder.append("\n");
		
		for(City fromCity : matrix.keySet()) {
			stringBuilder.append(String.format("%10s", fromCity.getID())).append(":");
			for(City toCity : matrix.keySet()) {
				String floatlim = String.format("%10.2f",matrix.get(fromCity).get(toCity));
				stringBuilder.append(floatlim + " ");
			}
			stringBuilder.append("\n");
		}
			
		return stringBuilder.toString();
	}
}
