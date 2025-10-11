package tspSolution;

import java.util.HashMap;
import java.util.HashSet;

public class DistanceMatrix {
	//Properties
	private static DistanceMatrix instance = null;
	
	private HashMap<City, HashMap<City, Double>> matrix;
	
	//Constructors
	private DistanceMatrix() {
		matrix = new HashMap<City , HashMap<City, Double>>();
	}
	
	private DistanceMatrix(HashSet<City> targets) {
		matrix = new HashMap<City , HashMap<City, Double>>();
		this.matrixBuilder(targets);
	}
	
	//Singleton methods
	public static DistanceMatrix getInstance() {
		if(instance == null) {
			instance = new DistanceMatrix();
		}
		return instance;
	}
	
	public static DistanceMatrix getInstance(HashSet<City> targets) {
		if(instance == null) {
			instance = new DistanceMatrix(new HashSet<City>(targets));
		}
		return instance;
	}
	
	public static void reset() {
	    instance = null;
	}
	
	//Methods
	private void matrixBuilder(HashSet<City> targets) {
		if(!matrix.isEmpty()) matrix.clear();
		
		for(City MainKey : targets) {
			if(MainKey == null) continue;
			matrix.put(MainKey, new HashMap<City , Double>());
			for(City SecKey : targets) {
				if(SecKey == null) continue;
				matrix.get(MainKey).put(SecKey, MainKey.distance(SecKey));
			}
		}
	}
	
	public double getDistance(City a, City b) {
		if(a == null || b == null) {
			System.out.println("ERROR: NullPointerException -> one or more of the cities you entered is null");
			return -1;
		}
		if(!matrix.containsKey(a) || !matrix.containsKey(b)) {
			System.out.println("ERROR: can't calculate distance -> one or more of the cities you entered aren't in the matrix");
			return -1;
		}
		
		Double distance = matrix.get(a).get(b);
		if(distance == null) {
			System.out.println("ERROR: distance between (" + a + ") and (" + b + ") not found");
			return -1;
		}
		return distance;
	}
			
	
	public void addCity(City other) {
		if(other == null) {
			System.out.println("ERROR: can't add city -> value is null");
			return;
		}
		
		if(!matrix.containsKey(other)) {
			matrix.put(other, new HashMap<City, Double>());
			for(City existing : matrix.keySet()) {
				if(existing.equals(other)) continue;
				matrix.get(existing).put(other, existing.distance(other));
			}
			
			for(City target : matrix.keySet()) 
				matrix.get(other).put(target, other.distance(target));
		}else
			System.out.println("ERROR: can't add city (" + other + ") -> already exists in matrix");
	}
	
	public void removeCity(City other) {
		if(other == null) {
			System.out.println("ERROR: can't remove city -> value is null");
			return;
		}
		
		if(matrix.containsKey(other)) {
			matrix.remove(other);
			for(City key : matrix.keySet()) {
				matrix.get(key).remove(other);
			}
		}else
			System.out.println("ERROR: can't remove city (" + other + ") -> city doesn't exist in matrix");
	}
	
	//Override methods
	@Override
	public String toString() {
		if(matrix.isEmpty()) return "";
		String returnString = "[Matrix] ";
		
		for(City existing : matrix.keySet())
			returnString += String.format("%8s", existing.getID());
		returnString += "\n";
		
		for(City fromCity : matrix.keySet()) {
			returnString += String.format("%8s", fromCity.getID() + ":");
			for(City toCity : matrix.keySet()) {
				String floatlim = String.format("%8.2f",matrix.get(fromCity).get(toCity));
				returnString += floatlim + " ";
			}
			returnString += "\n";
		}
			
		return returnString;
	}
}
