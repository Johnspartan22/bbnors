package org.bbnors.core.geom;

/**
 * Axis-aligned rectangle in screen space. {@code (x, y)} is the top-left corner.
 */
public record Rect(double x, double y, double width, double height) {

    public Rect {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("negative size: " + width + "x" + height);
        }
    }

    public static Rect centered(Vec2 center, double width, double height) {
        return new Rect(center.x() - width / 2, center.y() - height / 2, width, height);
    }

    public Vec2 center() {
        return new Vec2(x + width / 2, y + height / 2);
    }

    public boolean contains(Vec2 p) {
        return p.x() >= x && p.x() <= x + width && p.y() >= y && p.y() <= y + height;
    }

    /** Smallest side; the "effective width" used by Fitts' law for a rectangular target. */
    public double minSide() {
        return Math.min(width, height);
    }

    public Vec2 clamp(Vec2 p) {
        return new Vec2(Math.max(x, Math.min(x + width, p.x())), Math.max(y, Math.min(y + height, p.y())));
    }

    public Rect inset(double d) {
        double w = Math.max(0, width - 2 * d);
        double h = Math.max(0, height - 2 * d);
        return Rect.centered(center(), w, h);
    }
}
