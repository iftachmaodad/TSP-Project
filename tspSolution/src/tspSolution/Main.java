package tspSolution;

import java.util.Random;
import java.util.Scanner;
import java.util.HashSet;

public class Main {

	public static Random ran = new Random();
	public static Scanner in = new Scanner(System.in);
	
	public static HashSet<City> generateRandomCities(int numOfCities) throws IllegalArgumentException{
		if(numOfCities <= 0) throw new IllegalArgumentException("ERROR: invalid input -> entered zero / negative amount of cities");
		if(numOfCities == 1 || numOfCities == 2) System.out.println("WARNING: pointless generation -> number of cities too low +3 recommended");
		
		int range = ran.nextInt(45)+5;
		System.out.println("Range: " + range);
		
		HashSet<City> randCities = new HashSet<City>();
		while (randCities.size() < numOfCities)
			randCities.add(new City(ran.nextDouble()*range, ran.nextDouble()*range));
		return randCities;
	}
	
	public static City randomFromHashSet(HashSet<City> setCities) throws IllegalArgumentException{
		if(setCities == null || setCities.isEmpty()) throw new IllegalArgumentException("ERROR: program has no cities to choose from");
		
		City[] citiesArray = setCities.toArray(new City[setCities.size()]);
		int randStartCity = ran.nextInt(setCities.size());
		City start = citiesArray[randStartCity];
		System.out.println("Starting city: " + start.getID());
		
		return start;
	}
	
	public static HashSet<City> inputCities(int numOfCities) throws IllegalArgumentException{
		if(numOfCities <= 0) throw new IllegalArgumentException("ERROR: invalid input -> entered zero / negative amount of cities");
		if(numOfCities == 1 || numOfCities == 2) System.out.println("WARNING: pointless input -> number of cities too low +3 recommended");

		HashSet<City> inputCities = new HashSet<City>();
		String name;
		double x,y;
		for(int i = 1; i <= numOfCities; i++) {
			System.out.print("Enter name for city [" + i + "] : ");
			name = in.next();
			System.out.print("Enter longitude (x)  for city [" + i + "] : ");
			x = in.nextDouble();
			System.out.print("Enter latitude  (y)  for city [" + i + "] : ");
			y = in.nextDouble();
			System.out.println();
			inputCities.add(new City(name, x, y));
			}
		
		return inputCities;
	}
	
	public static City chooseStartCity(HashSet<City> setCities) throws IllegalArgumentException{
		if(setCities == null || setCities.isEmpty()) throw new IllegalArgumentException("ERROR: no cities to choose from");
		
		System.out.println("Available cities: ");
		City[] citiesArray = setCities.toArray(new City[setCities.size()]);
		for(int i = 0; i < citiesArray.length; i++)
			System.out.println("[" + (i+1) + "] " + citiesArray[i]);
		
		System.out.print("Enter the number of city you want to start from: ");
		int choice = in.nextInt();
		
		if(choice < 1 || choice > citiesArray.length) throw new IllegalArgumentException("ERROR: invalid choice -> number out of range");
		
		City start = citiesArray[choice-1];
		System.out.println("Starting city: " + start.getID() + "\n");
		return start;
	}
	
	public static void main(String[] args) {
		try {
		System.out.print("Would you like to enter the cities manually? (y/n): ");
		String choice = in.nextLine();
	
		Solver solver = null;
		City start = null;
		
		if(choice.equalsIgnoreCase("yes") || choice.equalsIgnoreCase("y")) {
			System.out.print("Enter amount of cities you want to input: ");
			int numOfCities = in.nextInt();
			System.out.println();
			
			HashSet<City> inputCities = inputCities(numOfCities);
			solver = new Solver(true, inputCities);
			System.out.println(solver.toString(false));
			
			start = chooseStartCity(inputCities);
			
		}else if(choice.equalsIgnoreCase("no") || choice.equalsIgnoreCase("n")) {
			System.out.print("Enter amount of cities you want generated: ");
			int numOfCities = in.nextInt();
			System.out.println();

			HashSet<City> randCities = generateRandomCities(numOfCities);
			solver = new Solver(true, randCities);
			System.out.println(solver);
		
			start = randomFromHashSet(randCities);
			
		}else throw new IllegalArgumentException("ERROR: invalid input -> enter only (y/n)");
	
		Route path = solver.solveNearestNeighborTSP(start);
		
		if(path != null)
		System.out.println(path);
		else
			System.out.println("Solver failed to find a valid route");
		}catch(java.util.InputMismatchException e) {System.out.println("ERROR: invalid input type -> please enter numbers only");}
		catch(IllegalArgumentException e) {System.out.println(e.getMessage());}
		finally {in.close();}
	}

}
