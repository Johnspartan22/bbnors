package org.bbnors.api.model;

/** World tile coordinate. Immutable value type. */
public record Tile(int x, int y, int plane) {

    public Tile(int x, int y) {
        this(x, y, 0);
    }

    public Tile translate(int dx, int dy) {
        return new Tile(x + dx, y + dy, plane);
    }

    /** Euclidean distance (planes ignored). */
    public double distance(Tile o) {
        return Math.hypot(x - o.x, y - o.y);
    }

    /** Chebyshev distance: the number of 8-directional steps between two tiles on an open grid. */
    public int steps(Tile o) {
        return Math.max(Math.abs(x - o.x), Math.abs(y - o.y));
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + (plane != 0 ? ", " + plane : "") + ")";
    }
}
