package org.bbnors.api.model;

/** Inclusive rectangular tile area on one plane. */
public record Area(int minX, int minY, int maxX, int maxY, int plane) {

    public Area {
        if (maxX < minX || maxY < minY) throw new IllegalArgumentException("empty area");
    }

    public static Area around(Tile c, int radius) {
        return new Area(c.x() - radius, c.y() - radius, c.x() + radius, c.y() + radius, c.plane());
    }

    public boolean contains(Tile t) {
        return t.plane() == plane && t.x() >= minX && t.x() <= maxX && t.y() >= minY && t.y() <= maxY;
    }

    public Tile center() {
        return new Tile((minX + maxX) / 2, (minY + maxY) / 2, plane);
    }
}
