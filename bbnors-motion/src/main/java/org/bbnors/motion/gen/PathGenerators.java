package org.bbnors.motion.gen;

import org.bbnors.core.geom.Vec2;
import org.bbnors.core.random.Rng;
import org.bbnors.motion.path.BezierPath;
import org.bbnors.motion.path.CatmullRomPath;
import org.bbnors.motion.path.LinearPath;
import org.bbnors.motion.path.Path;

import java.util.ArrayList;
import java.util.List;

/**
 * Stochastic path-geometry generators. Each takes explicit shape parameters plus an {@link Rng};
 * the same inputs and seed always yield the same path.
 *
 * <p>Shared vocabulary:
 * <ul>
 *   <li><b>curvature</b>: peak perpendicular offset of the interior control points as a fraction
 *       of the start-end distance (0 = straight).</li>
 *   <li><b>jitter</b>: relative random variation of each control point (0 = perfectly regular
 *       symmetric arc).</li>
 * </ul>
 * The arc bends to one side, chosen at random per movement; control-point offsets follow a
 * {@code 4f(1-f)} bump along the chord so the path leaves and arrives roughly along the chord.
 */
public final class PathGenerators {

    private PathGenerators() {
    }

    public static Path linear(Vec2 from, Vec2 to) {
        return new LinearPath(from, to);
    }

    /**
     * Bezier of the given degree (2..10). Degree 3 is the classic cubic; higher degrees allow
     * gentle S-shapes when jitter is high while remaining C∞ smooth.
     */
    public static Path bezier(Vec2 from, Vec2 to, int degree, double curvature, double jitter, Rng rng) {
        if (degree < 2 || degree > 10) {
            throw new IllegalArgumentException("degree must be in [2, 10]");
        }
        List<Vec2> pts = interior(from, to, degree - 1, curvature, jitter, rng);
        return new BezierPath(pts);
    }

    /** Centripetal Catmull-Rom through {@code waypoints} randomly displaced interior points. */
    public static Path catmullRom(Vec2 from, Vec2 to, int waypoints, double curvature, double jitter, Rng rng) {
        if (waypoints < 0 || waypoints > 12) {
            throw new IllegalArgumentException("waypoints must be in [0, 12]");
        }
        // Interpolating splines pass through their points, so the same offset bends them more than
        // a Bezier; scale down so 'curvature' means roughly the same visible deviation.
        return new CatmullRomPath(interior(from, to, waypoints, curvature * 0.5, jitter, rng));
    }

    private static List<Vec2> interior(Vec2 from, Vec2 to, int count, double curvature, double jitter, Rng rng) {
        Vec2 chord = to.sub(from);
        double dist = chord.length();
        Vec2 dir = chord.normalized();
        Vec2 normal = dir.perpendicular();
        double side = rng.chance(0.5) ? 1 : -1;
        List<Vec2> pts = new ArrayList<>(count + 2);
        pts.add(from);
        for (int k = 1; k <= count; k++) {
            double f = (double) k / (count + 1);
            f += rng.gaussian() * jitter * 0.08;
            f = Math.max(0.02, Math.min(0.98, f));
            double bump = 4 * f * (1 - f);
            double offset = dist * curvature * side * bump * (1 + jitter * rng.gaussian());
            offset += dist * curvature * jitter * 0.3 * rng.gaussian();
            pts.add(from.add(chord.scale(f)).add(normal.scale(offset)));
        }
        pts.add(to);
        return pts;
    }

    /**
     * WindMouse-style geometry (after B. Land's algorithm): a damped particle pulled toward the
     * target by "gravity" and pushed by a random "wind". Only the <em>shape</em> is kept: the
     * simulated points are decimated and fitted with a centripetal Catmull-Rom spline, and timing
     * is then applied by the usual timing law. This decouples WindMouse's characteristic wobble
     * from its (non-configurable, step-based) timing so it can be compared fairly with the
     * analytic generators.
     *
     * @param wind relative wind strength (0 = straight line, 1 = strong wobble)
     */
    public static Path wind(Vec2 from, Vec2 to, double wind, Rng rng) {
        double dist = from.distance(to);
        if (dist < 2) {
            return new LinearPath(from, to);
        }
        double gravity = 9;
        double windMag = 1 + 8 * Math.max(0, wind);
        double maxStep = Math.max(3, dist / 25);
        double targetArea = Math.max(4, dist / 12);
        double x = from.x();
        double y = from.y();
        double vx = 0;
        double vy = 0;
        double wx = 0;
        double wy = 0;
        List<Vec2> raw = new ArrayList<>();
        raw.add(from);
        int guard = 0;
        while (Math.hypot(to.x() - x, to.y() - y) > 1 && guard++ < 5000) {
            double d = Math.hypot(to.x() - x, to.y() - y);
            double w = Math.min(windMag, d);
            if (d >= targetArea) {
                wx = wx / Math.sqrt(3) + (2 * rng.nextDouble() - 1) * w / Math.sqrt(5);
                wy = wy / Math.sqrt(3) + (2 * rng.nextDouble() - 1) * w / Math.sqrt(5);
            } else {
                wx /= Math.sqrt(3);
                wy /= Math.sqrt(3);
                maxStep = Math.max(2, maxStep * 0.75);
            }
            vx += wx + gravity * (to.x() - x) / d;
            vy += wy + gravity * (to.y() - y) / d;
            double v = Math.hypot(vx, vy);
            if (v > maxStep) {
                double clip = maxStep / 2 + rng.nextDouble() * maxStep / 2;
                vx = vx / v * clip;
                vy = vy / v * clip;
            }
            x += vx;
            y += vy;
            raw.add(new Vec2(x, y));
        }
        // Decimate to points at least ~dist/10 apart, always keeping the exact target.
        List<Vec2> pts = new ArrayList<>();
        pts.add(from);
        double minGap = Math.max(4, dist / 10);
        Vec2 last = from;
        for (int i = 1; i < raw.size() - 1; i++) {
            Vec2 p = raw.get(i);
            if (p.distance(last) >= minGap && p.distance(to) >= minGap / 2) {
                pts.add(p);
                last = p;
            }
        }
        pts.add(to);
        return new CatmullRomPath(pts);
    }
}
