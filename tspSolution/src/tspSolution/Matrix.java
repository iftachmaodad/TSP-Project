package tspSolution;

import java.util.*;

public final class Matrix<T extends City> {
    // --- Singleton Instance ---
    private static Matrix<?> instance;

    // SAFETY: We remember exactly what type this matrix was built for
    private final Class<T> type;
    
    // --- Properties ---
    private final Set<T> cities = new LinkedHashSet<>();
    private final List<T> cityListSnapshot = new ArrayList<>();
    private double[][] distanceMatrix;
    private double[][] timeMatrix;

    // --- Constructors ---
    private Matrix(Class<T> type) {
        this.type = type;
    }
    
    // --- Singleton Getter ---
    @SuppressWarnings("unchecked")
    public static <T extends City> Matrix<T> getInstance(Class<T> requestedType) {
        // 1. Validation
        if (!CityRegistry.exists(requestedType)) {
            throw new IllegalArgumentException(
                "Type '" + requestedType.getSimpleName() + "' is not registered in CityRegistry."
            );
        }
        
        // 2. Creation
        if (instance == null) {
            instance = new Matrix<>(requestedType);
        }
        
        // 3. Safety
        if (!instance.type.equals(requestedType)) {
            throw new IllegalStateException(
                "CRITICAL ERROR: Matrix is already initialized for type [" + instance.type.getSimpleName() + 
                "]. You requested [" + requestedType.getSimpleName() + "]. " +
                "You must call Matrix.reset() before switching modes."
            );
        }
        return (Matrix<T>) instance;
    }
    
    public static void reset() {
        instance = null;
    }

    // --- Methods ---
    public void addCity(T city) {
        if (city == null) return;
        
        if (cities.add(city)) {
            distanceMatrix = null;
            timeMatrix = null;
            cityListSnapshot.clear();
        }
    }
    
    public void removeCity(T city) {
        if (cities.remove(city)) {
            distanceMatrix = null;
            timeMatrix = null;
            cityListSnapshot.clear();
        }
    }
    
    public boolean checkIntegrity() {
        if (distanceMatrix == null || timeMatrix == null) return false;
        return distanceMatrix.length == cities.size();
    }
    
    // --- Population Logic ---
    public boolean populateMatrix() {
        if (cities.isEmpty()) return false;
        
        cityListSnapshot.clear();
        cityListSnapshot.addAll(cities);
        
        int size = cityListSnapshot.size();
        distanceMatrix = new double[size][size];
        timeMatrix = new double[size][size];
        
        CalculationStrategy strategy = CityRegistry.getStrategy(this.type);
        if (strategy == null) {
            System.out.println("ERROR: No strategy registered for type " + this.type.getSimpleName());
            return false;
        }

        if (strategy == CalculationStrategy.API_REQUIRED) {
             // TODO: API Logic would go here
            return populateViaMath(size);
        } else {
            return populateViaMath(size);
        }
    }

    private boolean populateViaMath(int size) {
        for(int i=0; i<size; i++) {
            for(int j=0; j<size; j++) {
                if (i == j) {
                    distanceMatrix[i][j] = 0;
                    timeMatrix[i][j] = 0;
                    continue;
                }
                
                // USE SNAPSHOT: Access by index is safe here
                T c1 = cityListSnapshot.get(i);
                T c2 = cityListSnapshot.get(j);

                distanceMatrix[i][j] = c1.distance(c2);
                timeMatrix[i][j] = c1.time(c2);
            }
        }
        return true;
    }

    // --- Override Methods ---
    @Override
    public String toString() {
        if (cities.isEmpty()) return "MATRIX : [Empty]";
        if (distanceMatrix == null || timeMatrix == null) return "MATRIX : [Not Populated] (Cities: " + cities.size() + ")";

        List<T> viewList = (!cityListSnapshot.isEmpty()) ? cityListSnapshot : new ArrayList<>(cities);
        
        StringBuilder sb = new StringBuilder();
        sb.append("=== MATRIX (Type: ").append(type.getSimpleName()).append(") ===\n\n");

        sb.append("--- Distance Matrix (Meters) ---\n");
        printTable(sb, distanceMatrix, viewList);
        
        sb.append("\n");

        sb.append("--- Time Matrix (Seconds) ---\n");
        printTable(sb, timeMatrix, viewList);

        return sb.toString();
    }

    private void printTable(StringBuilder sb, double[][] data, List<T> viewList) {
        sb.append(String.format("%-15s", "[To ->]")); 
        for (T c : viewList) {
            String id = (c.getID().length() > 10) ? c.getID().substring(0, 9) + "." : c.getID();
            sb.append(String.format("%-12s", id));
        }
        sb.append("\n");

        for (int i = 0; i < viewList.size(); i++) {
            String rowId = viewList.get(i).getID();
            if (rowId.length() > 14) rowId = rowId.substring(0, 13) + ".";
            sb.append(String.format("%-15s", rowId));

            for (int j = 0; j < viewList.size(); j++) {
                double val = data[i][j];
                if (Double.isNaN(val)) {
                    sb.append(String.format("%-12s", "N/A"));
                } else {
                    sb.append(String.format("%-12.2f", val));
                }
            }
            sb.append("\n");
        }
    }
    
    // --- Getters ---
    public List<T> getCities() { 
        if (!cityListSnapshot.isEmpty()) return Collections.unmodifiableList(cityListSnapshot);
        return new ArrayList<>(cities);
    }
    
    public int getIndexOf(City c) { 
        if (cityListSnapshot.isEmpty()) return -1;
        return cityListSnapshot.indexOf(c); 
    }
    
    public int size() { return cities.size(); }
    
    public double getDistance(int i, int j) {
        if (distanceMatrix == null) return Double.NaN;
        if (i < 0 || i >= size() || j < 0 || j >= size()) return Double.NaN;
        return distanceMatrix[i][j];
    }

    public double getTime(int i, int j) {
        if (timeMatrix == null) return Double.NaN;
        if (i < 0 || i >= size() || j < 0 || j >= size()) return Double.NaN;
        return timeMatrix[i][j];
    }
}