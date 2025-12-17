package tspSolution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Route<T extends City> {
    // --- Properties ---
    private final List<T> path;
    private double totalDistance;
    private double totalTime;
    private boolean valid;

    // --- Constructors ---
    public Route(T start) {
        if (start == null)
            throw new IllegalArgumentException("Start city cannot be null");

        this.path = new ArrayList<>();
        this.path.add(start);
        this.totalDistance = 0;
        this.totalTime = 0;
        this.valid = true;

        if (start.hasDeadline() && totalTime > start.getDeadline()) {
            valid = false;
        }
    }

    // --- Methods ---
    public void addStep(T nextCity, double distance, double time) {
        if (nextCity == null) {
            System.out.println("ERROR: cannot add null city to route");
            valid = false;
            return;
        }
        if (Double.isNaN(distance) || Double.isNaN(time)) {
            System.out.println("WARNING: invalid distance or time detected for city " + nextCity.getID());
            valid = false;
        }

        path.add(nextCity);
        totalDistance += distance;
        totalTime += time;

        if (nextCity.hasDeadline() && totalTime > nextCity.getDeadline()) {
            valid = false;
        }
    }

    // --- Override Methods ---
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Route: || ");
        for (int i = 0; i < path.size(); i++) {
            sb.append(path.get(i).getID());
            if (i < path.size() - 1) sb.append(" -> ");
        }
        
        sb.append(" ||\n");
        sb.append("Total Distance: ").append(String.format("%.2f", totalDistance)).append("\n");
        sb.append("Total Time: ").append(String.format("%.2f", totalTime)).append("\n");
        sb.append("Route Valid: ").append(valid ? "YES" : "NO");

        return sb.toString();
    }
    
    // --- Getters ---
    public int size() {return path.size();}
    public double getTotalDistance() {return totalDistance;}
    public double getTotalTime() {return totalTime;}
    public boolean isValid() {return valid;}
    public List<T> getPath() {return Collections.unmodifiableList(path);}
    public T getLastCity() {return path.get(path.size() - 1);}
}
