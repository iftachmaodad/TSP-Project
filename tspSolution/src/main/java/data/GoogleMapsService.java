package data;

import com.google.maps.DistanceMatrixApi;
import com.google.maps.GeoApiContext;
import com.google.maps.model.DistanceMatrix;
import com.google.maps.model.DistanceMatrixElement;
import com.google.maps.model.DistanceMatrixElementStatus;
import com.google.maps.model.DistanceMatrixRow;
import com.google.maps.model.LatLng;
import com.google.maps.model.TravelMode;

import domain.City;
import domain.CityRegistry;

import java.util.List;

public class GoogleMapsService {
    // --- Properties ---
    private final GeoApiContext context;

    // --- Constructors ---
    public GoogleMapsService(String apiKey) {
        this.context = new GeoApiContext.Builder()
            .apiKey(apiKey)
            .build();
    }

    // --- Methods ---
    public void shutdown() { context.shutdown(); }

    public boolean fillMatrix(Matrix<?> matrix) {
        if (matrix == null) return false;
        if (matrix.getCities() == null || matrix.getCities().isEmpty()) return false;
        if (matrix.getDistanceMatrix() == null || matrix.getTimeMatrix() == null) return false;
        if (CityRegistry.getStrategy(matrix.getType()) != CityRegistry.CalculationStrategy.API_REQUIRED) return false;

        List<? extends City> cities = matrix.getCities();
        double[][] distanceMatrix = matrix.getDistanceMatrix();
        double[][] timeMatrix = matrix.getTimeMatrix();

        return fillMatrix(cities, distanceMatrix, timeMatrix);
    }

    private boolean fillMatrix(List<? extends City> cities, double[][] distMatrix, double[][] timeMatrix) {
        int size = cities.size();
        LatLng[] locations = new LatLng[size];

        for (int i = 0; i < size; i++) {
            City c = cities.get(i);
            locations[i] = new LatLng(c.getY(), c.getX());
        }

        try {
            DistanceMatrix result = DistanceMatrixApi.newRequest(context)
                .origins(locations)
                .destinations(locations)
                .mode(TravelMode.DRIVING)
                .await();

            for (int i = 0; i < size; i++) {
                DistanceMatrixRow row = result.rows[i];
                for (int j = 0; j < size; j++) {
                    if (i == j) {
                        distMatrix[i][j] = 0;
                        timeMatrix[i][j] = 0;
                        continue;
                    }

                    DistanceMatrixElement el = row.elements[j];
                    if (el.status == DistanceMatrixElementStatus.OK) {
                        distMatrix[i][j] = el.distance.inMeters;
                        timeMatrix[i][j] = el.duration.inSeconds;
                    } else {
                        distMatrix[i][j] = Double.NaN;
                        timeMatrix[i][j] = Double.NaN;
                    }
                }
            }

            return true;
        } catch (Exception e) {
            System.err.println("Google Maps API error: " + e.getMessage());
            return false;
        }
    }
}
