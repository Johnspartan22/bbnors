package org.bbnors.motion.path;

import org.bbnors.core.geom.Vec2;

import java.util.List;

/**
 * Catmull-Rom spline through a list of waypoints, with configurable knot parameterisation
 * {@code alpha}: 0 = uniform, 0.5 = centripetal (default), 1 = chordal.
 *
 * <p>Centripetal parameterisation is the default because it is guaranteed free of cusps and
 * self-intersections within a segment (Yuksel, Schaefer &amp; Keyser 2011), which uniform
 * Catmull-Rom is not when waypoints are unevenly spaced.
 *
 * <p>Each segment is converted at construction into a cubic Hermite segment using the
 * non-uniform tangent formula, then into power-basis coefficients, so evaluation is a single
 * cubic Horner pass. The phantom end points are reflections of the second / penultimate waypoint.
 * The global parameter {@code u} is split evenly across segments; uneven segment lengths are
 * irrelevant downstream because motion is driven through the arc-length table.
 */
public final class CatmullRomPath implements Path {

    private final Vec2[] points;
    private final int segments;
    /** Start of segment i in u, plus a final 1.0 entry. */
    private final double[] segStart;
    /** Per segment: x0..x3, y0..y3 power-basis coefficients (8 doubles). */
    private final double[] coeff;

    public CatmullRomPath(List<Vec2> waypoints) {
        this(waypoints, 0.5);
    }

    public CatmullRomPath(List<Vec2> waypoints, double alpha) {
        if (waypoints.size() < 2) {
            throw new IllegalArgumentException("need at least 2 waypoints");
        }
        if (alpha < 0 || alpha > 1) {
            throw new IllegalArgumentException("alpha must be in [0, 1]");
        }
        points = waypoints.toArray(new Vec2[0]);
        segments = points.length - 1;
        coeff = new double[segments * 8];
        segStart = new double[segments + 1];
        double[] knotLen = new double[segments];
        double knotTotal = 0;
        for (int s = 0; s < segments; s++) {
            knotLen[s] = knot(points[s], points[s + 1], alpha);
            knotTotal += knotLen[s];
        }
        for (int s = 0; s < segments; s++) {
            segStart[s + 1] = segStart[s] + knotLen[s] / knotTotal;
        }
        segStart[segments] = 1.0;
        for (int s = 0; s < segments; s++) {
            Vec2 p1 = points[s];
            Vec2 p2 = points[s + 1];
            Vec2 p0 = s > 0 ? points[s - 1] : p1.scale(2).sub(p2);
            Vec2 p3 = s + 2 < points.length ? points[s + 2] : p2.scale(2).sub(p1);
            double t01 = knot(p0, p1, alpha);
            double t12 = knot(p1, p2, alpha);
            double t23 = knot(p2, p3, alpha);
            // Non-uniform Catmull-Rom tangents (scaled to the [0,1] segment parameter).
            Vec2 m1 = p1.sub(p0).scale(1 / t01).sub(p2.sub(p0).scale(1 / (t01 + t12))).add(p2.sub(p1).scale(1 / t12)).scale(t12);
            Vec2 m2 = p2.sub(p1).scale(1 / t12).sub(p3.sub(p1).scale(1 / (t12 + t23))).add(p3.sub(p2).scale(1 / t23)).scale(t12);
            int o = s * 8;
            hermite(p1.x(), m1.x(), p2.x(), m2.x(), coeff, o);
            hermite(p1.y(), m1.y(), p2.y(), m2.y(), coeff, o + 4);
        }
    }

    private static double knot(Vec2 a, Vec2 b, double alpha) {
        double d = Math.pow(a.distance(b), alpha);
        return d < 1e-6 ? 1e-6 : d; // coincident points: avoid division by zero
    }

    /** Hermite basis -> power basis: p(t) = c0 + c1 t + c2 t^2 + c3 t^3. */
    private static void hermite(double p1, double m1, double p2, double m2, double[] out, int o) {
        out[o] = p1;
        out[o + 1] = m1;
        out[o + 2] = -3 * p1 - 2 * m1 + 3 * p2 - m2;
        out[o + 3] = 2 * p1 + m1 - 2 * p2 + m2;
    }

    /** Parameter value at which waypoint {@code i} is reached. */
    public double paramAtWaypoint(int i) {
        return segStart[i];
    }

    @Override
    public double[] breakpoints() {
        return segStart.clone();
    }

    public List<Vec2> waypoints() {
        return List.of(points);
    }

    @Override
    public void eval(double u, double[] out) {
        u = Path.clamp01(u);
        int s = 0;
        while (s < segments - 1 && u >= segStart[s + 1]) {
            s++;
        }
        double width = segStart[s + 1] - segStart[s];
        double t = Math.min(1, (u - segStart[s]) / width);
        int o = s * 8;
        double k = 1 / width; // chain rule: d/du = (1/width) * d/dt
        out[0] = ((coeff[o + 3] * t + coeff[o + 2]) * t + coeff[o + 1]) * t + coeff[o];
        out[1] = ((coeff[o + 7] * t + coeff[o + 6]) * t + coeff[o + 5]) * t + coeff[o + 4];
        out[2] = k * ((3 * coeff[o + 3] * t + 2 * coeff[o + 2]) * t + coeff[o + 1]);
        out[3] = k * ((3 * coeff[o + 7] * t + 2 * coeff[o + 6]) * t + coeff[o + 5]);
        out[4] = k * k * (6 * coeff[o + 3] * t + 2 * coeff[o + 2]);
        out[5] = k * k * (6 * coeff[o + 7] * t + 2 * coeff[o + 6]);
    }

    @Override
    public Vec2 start() {
        return points[0];
    }

    @Override
    public Vec2 end() {
        return points[points.length - 1];
    }
}
