package tspSolution;

public record Insertion<T extends City>(int index, T city, double deltaDistance) {}
