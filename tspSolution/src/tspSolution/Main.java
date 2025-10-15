package tspSolution;

import java.util.Random;
import java.util.Scanner;
import java.util.HashSet;

public class Main {

	public static Random ran = new Random();
	
	public static HashSet<City> generateRandomCities(int numOfCities){
		if(numOfCities <= 0) System.out.println("WARNING: pointless generation -> number of cities to low +3 recommended");
		
		int range = ran.nextInt(45)+5;
		System.out.println("Range: " + range);
		
		HashSet<City> randCities = new HashSet<City>();
		while (randCities.size() < numOfCities)
			randCities.add(new City(ran.nextDouble()*range, ran.nextDouble()*range));
		return randCities;
	}
	
	public static void main(String[] args) {
		Scanner in = new Scanner(System.in);
		
		System.out.println("Enter amount of cities you want generated: ");
		try {
		int numOfCities = in.nextInt();
		
		HashSet<City> randCities = generateRandomCities(numOfCities);
		Solver solver = new Solver(true, randCities);
		System.out.println(solver);
		
		City[] citiesArray = randCities.toArray(new City[numOfCities]);
		int randStartCity = ran.nextInt(numOfCities);
		City start = citiesArray[randStartCity];
		System.out.println("Starting city: " + start.getID());
		
		Route path = solver.solveNearestNeighbor(start);
		
		if(path != null)
		System.out.println(path);
		else
			System.out.println("Solver failed to find a valid route");
		
		}catch(Exception e) {
			
			System.out.println("ERROR: invalid input, please enter a number");
			
		}finally {
			
			in.close();
			
		}
	}

}
