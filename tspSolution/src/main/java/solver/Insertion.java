package solver;

import domain.City;

public record Insertion<T extends City>(int index, T city, double deltaDistance) {}
