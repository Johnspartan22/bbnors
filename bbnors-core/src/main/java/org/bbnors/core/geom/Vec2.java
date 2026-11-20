package org.bbnors.core.geom;

/**
 * Immutable 2D vector / point in screen space (pixels, double precision).
 *
 * <p>Movement math works in doubles throughout and only quantizes to integer pixels at the
 * input-backend boundary, so rounding error never accumulates along a trajectory.
 */
public record Vec2(double x, double y) {

    public static final Vec2 ZERO = new Vec2(0, 0);

    public static Vec2 of(double x, double y) {
        return new Vec2(x, y);
    }

    public Vec2 add(Vec2 o) {
        return new Vec2(x + o.x, y + o.y);
    }

    public Vec2 sub(Vec2 o) {
        return new Vec2(x - o.x, y - o.y);
    }

    public Vec2 scale(double s) {
        return new Vec2(x * s, y * s);
    }

    public double dot(Vec2 o) {
        return x * o.x + y * o.y;
    }

    /** z-component of the 3D cross product; positive when {@code o} is counter-clockwise of this. */
    public double cross(Vec2 o) {
        return x * o.y - y * o.x;
    }

    public double length() {
        return Math.hypot(x, y);
    }

    public double lengthSq() {
        return x * x + y * y;
    }

    public double distance(Vec2 o) {
        return Math.hypot(x - o.x, y - o.y);
    }

    /** Unit vector in the same direction, or {@link #ZERO} for a zero-length vector. */
    public Vec2 normalized() {
        double len = length();
        return len < 1e-12 ? ZERO : new Vec2(x / len, y / len);
    }

    /** This vector rotated +90 degrees (counter-clockwise in a y-up frame). */
    public Vec2 perpendicular() {
        return new Vec2(-y, x);
    }

    public Vec2 lerp(Vec2 o, double t) {
        return new Vec2(x + (o.x - x) * t, y + (o.y - y) * t);
    }

    public int ix() {
        return (int) Math.round(x);
    }

    public int iy() {
        return (int) Math.round(y);
    }

    @Override
    public String toString() {
        return String.format("(%.2f, %.2f)", x, y);
    }
}
