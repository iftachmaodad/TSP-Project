package tspSolution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Route<T extends City> {
    private final List<T> path;
    private final List<Double> arrivalTimes;

    private double totalDistance;
    private double totalTime;
    private boolean valid;

    private String debugLog = "";

    public Route(T start) {
        if (start == null)
            throw new IllegalArgumentException("Start city cannot be null");

        this.path = new ArrayList<>();
        this.arrivalTimes = new ArrayList<>();

        this.path.add(start);
        this.arrivalTimes.add(0.0);

        this.totalDistance = 0;
        this.totalTime = 0;
        this.valid = true;

        if (start.hasDeadline() && totalTime > start.getDeadline()) {
            valid = false;
            debugLog = "Start city deadline missed immediately.";
        }
    }

    public void addStep(T nextCity, double distFromLast, double timeFromLast) {
        if (nextCity == null) {
            this.valid = false;
            this.debugLog = "Attempted to add null city to route.";
            return;
        }

        // ✅ NEW: if any edge is NaN, route is invalid
        if (Double.isNaN(distFromLast) || Double.isNaN(timeFromLast)) {
            this.valid = false;
            this.debugLog = "Matrix has N/A edge into " + nextCity.getID();
            this.path.add(nextCity);
            this.arrivalTimes.add(Double.NaN);
            this.totalDistance = Double.NaN;
            this.totalTime = Double.NaN;
            return;
        }

        this.totalDistance += distFromLast;
        this.totalTime += timeFromLast;

        this.path.add(nextCity);
        this.arrivalTimes.add(this.totalTime);

        if (nextCity.hasDeadline() && this.totalTime > nextCity.getDeadline()) {
            this.valid = false;
            this.debugLog = "Missed deadline at " + nextCity.getID();
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ROUTE REPORT ===\n");
        sb.append("Status: ").append(valid ? "VALID" : "INVALID").append("\n");
        sb.append("Total Distance: ").append(String.format("%.2f", totalDistance)).append(" m\n");
        sb.append("Total Time: ").append(String.format("%.2f", totalTime)).append(" s\n");

        if (!debugLog.isEmpty()) {
            sb.append("Log: ").append(debugLog).append("\n");
        }

        sb.append("\n--- Itinerary ---\n");

        for (int i = 0; i < path.size(); i++) {
            T city = path.get(i);
            String arrivalStr = "N/A";
            double arrival = -1;

            if (i < arrivalTimes.size()) {
                arrival = arrivalTimes.get(i);
                if (!Double.isNaN(arrival)) arrivalStr = String.format("%.1f", arrival);
                else arrivalStr = "NaN";
            }

            String note = "";
            if (city.hasDeadline() && !Double.isNaN(arrival)) {
                double due = city.getDeadline();
                if (arrival > due) note = " [LATE! Due: " + String.format("%.0f", due) + "]";
                else note = " [Due: " + String.format("%.0f", due) + "]";
            } else if (city.hasDeadline()) {
                note = " [Due: " + String.format("%.0f", city.getDeadline()) + "]";
            }

            sb.append(String.format("%-2d | %-8s | %-15s %s\n", (i + 1), arrivalStr, city.getID(), note));
        }

        return sb.toString();
    }

    public void setDebugLog(String log) { this.debugLog = log; }

    public int size() { return path.size(); }
    public double getTotalDistance() { return totalDistance; }
    public double getTotalTime() { return totalTime; }
    public boolean isValid() { return valid; }
    public List<T> getPath() { return Collections.unmodifiableList(path); }
    public T getLastCity() { return path.isEmpty() ? null : path.get(path.size() - 1); }
}