package solver;

import domain.City;

/**
 * Represents a candidate insertion of one city into a route at a specific
 * position.
 *
 * @param index         position in the route list where the city should be
 *                      inserted (before the element currently at that index)
 * @param city          the city to insert
 * @param deltaDistance additional distance in metres that the insertion adds
 *                      to the total route length
 * @param <T>           the concrete city subtype
 */
public record Insertion<T extends City>(int index, T city, double deltaDistance) {}
