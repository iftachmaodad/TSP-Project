package tspSolution;

import java.util.HashSet;

public class Solver {
	//Properties
	private DistanceMatrix solverMatrix;
	
	//Constructors
	public Solver() {this.solverMatrix = DistanceMatrix.getInstance();}
	public Solver(HashSet<City> cities) {this.solverMatrix = DistanceMatrix.getInstance(cities);}
	public Solver(DistanceMatrix solverMatrix) {this.solverMatrix = solverMatrix;}
	
	public Solver(boolean toReset) {
		if(toReset) DistanceMatrix.reset();
		this.solverMatrix = DistanceMatrix.getInstance();
	}
	public Solver(boolean toReset, HashSet<City> cities) {
		if(toReset) DistanceMatrix.reset();
		this.solverMatrix = DistanceMatrix.getInstance(cities);
	}
	
	//Methods | TSP algorithms
    public Route solveNearestNeighborTSP(City start) {
    	if(start == null) {
    		System.out.println("ERROR: null error -> start city is null");
    		return null;
    	}
    	if(!solverMatrix.contains(start)) {
    		System.out.println("ERROR: couldn't find starting city -> city not in list");
    		return null;
    	}
    	if(solverMatrix.getCities().isEmpty()) {
    		System.out.println("ERROR: solver has no cities to process -> solver matrix is empty");
    		return null;
    	}
    	
    	if(solverMatrix.size() <= 1) System.out.println("WARNING: solver initialized with less than two cities -> trivial route.");
    	if(!solverMatrix.checkIntegrity()) System.out.println("WARNING: matrix integrity failed before solving");
    	
    	
        Route route = new Route(start);
        HashSet<City> unvisited = new HashSet<>(solverMatrix.getCities());
        unvisited.remove(start);

        City current = start;
        while(!unvisited.isEmpty()) {
            City nearest = null;
            double minDist = 0;
            boolean first = true;
            for(City candidate : unvisited) {
                double dist = solverMatrix.getDistance(current, candidate);
                if(Double.isNaN(dist) || dist < 0) continue;
                if(first || dist < minDist) {
                	minDist = dist;
                    nearest = candidate;
                    first = false;
                }
            }
            if(nearest == null) {
            	System.out.println("ERROR: no valid next city found from " + current);
            	break;
            }
            route.addCity(nearest, minDist);
            unvisited.remove(nearest);
            current = nearest;
        }
        double returnDist = solverMatrix.getDistance(current, start);
        route.addCity(start, returnDist);

        return route;
    }
    
    //Utility Methods
    public boolean checkIntegrity() {return solverMatrix.checkIntegrity();}
    public void addCity(City other) {solverMatrix.addCity(other);}
    public void removeCity(City other) {solverMatrix.removeCity(other);}
    
    //Override Methods | toString Methods
    @Override
    public String toString() {
    	return solverMatrix.toString();
    }
    
    public String toString(boolean partialPrint) {
    	return solverMatrix.toString(partialPrint);
    }
}