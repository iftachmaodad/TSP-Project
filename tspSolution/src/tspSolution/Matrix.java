package tspSolution;

import java.util.*;

public final class Matrix<T extends City> {
    // --- Singleton Instance ---
    private static Matrix<?> instance;
    private final Class<T> type;
    
    // --- API Instance ---
    private static GoogleMapsService googleService = null;
    
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
        if (cities.add(city)) invalidate();
    }
    
    public void removeCity(T city) {
        if (cities.remove(city)) invalidate();
    }

    public void invalidate() {
        distanceMatrix = null;
        timeMatrix = null;
        cityListSnapshot.clear();
    }
    
    public boolean checkIntegrity() {
        if (distanceMatrix == null || timeMatrix == null) return false;
        if (cityListSnapshot.isEmpty()) return false;

        int n = cityListSnapshot.size();
        if (distanceMatrix.length != n || timeMatrix.length != n) return false;

        for (int i = 0; i < n; i++) {
            if (distanceMatrix[i] == null || distanceMatrix[i].length != n) return false;
            if (timeMatrix[i] == null || timeMatrix[i].length != n) return false;
        }
        return true;
    }
    
    // --- Population Logic ---
    public boolean populateMatrix() {
        if (cities.isEmpty()) return false;
        
        cityListSnapshot.clear();
        cityListSnapshot.addAll(cities);
        
        int size = cityListSnapshot.size();
        distanceMatrix = new double[size][size];
        timeMatrix = new double[size][size];
        
        CityRegistry.CalculationStrategy strategy = CityRegistry.getStrategy(this.type);
        if (strategy == null) {
            System.out.println("ERROR: No strategy registered for type " + this.type.getSimpleName());
            return false;
        }

        if (strategy == CityRegistry.CalculationStrategy.API_REQUIRED) {
            return populateViaAPI();
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
                
                T c1 = cityListSnapshot.get(i);
                T c2 = cityListSnapshot.get(j);

                distanceMatrix[i][j] = c1.distance(c2);
                timeMatrix[i][j] = c1.time(c2);
            }
        }
        return true;
    }

    private boolean populateViaAPI() {
        if (googleService == null) {
            System.out.println("ERROR: GoogleMapsService is not set. Call Matrix.setGoogleMapsService(...) first.");
            return false;
        }

        int n = cityListSnapshot.size();
        if (n == 0) return false;

        boolean ok = googleService.fillMatrix(this);
        if (!ok) {
            System.out.println("ERROR: GoogleMapsService failed to fill matrix.");
            return false;
        }

        return checkIntegrity();
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
    
    // --- Setters ---
    public static void setGoogleMapsService(GoogleMapsService service) {googleService = service;}
    
    // --- Getters ---
    public int size() { return cities.size(); }
    public Class<T> getType() { return type; }
    public double[][] getDistanceMatrix() { return distanceMatrix; }
    public double[][] getTimeMatrix() { return timeMatrix; }
    
    public List<T> getCities() { 
        if (!cityListSnapshot.isEmpty()) return Collections.unmodifiableList(cityListSnapshot);
        return new ArrayList<>(cities);
    }
    
    public int getIndexOf(City c) { 
        if (cityListSnapshot.isEmpty()) return -1;
        return cityListSnapshot.indexOf(c); 
    }
    
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