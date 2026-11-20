package org.bbnors.motion;

import org.bbnors.core.geom.Vec2;
import org.bbnors.motion.path.ArcLengthTable;
import org.bbnors.motion.path.Path;
import org.bbnors.motion.timing.TimingLaw;

/**
 * A single movement along a {@link Path}, timed by a {@link TimingLaw}.
 *
 * <p>At time {@code t}, with {@code τ = t/T} and arc length {@code L}:
 * <pre>
 *   distance  d = L·s(τ)            u = arcLength⁻¹(d)        position = P(u)
 *   speed     v = L·s'(τ)/T         direction = P'(u)/|P'(u)|
 *   a_tan       = L·s''(τ)/T²       κ = (P'×P'')/|P'|³         a_norm = κ·v²
 * </pre>
 * Everything is analytic except the arc-length inversion (4th-order accurate table lookup), so
 * reported velocities and accelerations are exact for the trajectory actually produced.
 */
public final class PathMotion implements Motion {

    private final Path path;
    private final ArcLengthTable arc;
    private final TimingLaw law;
    private final double duration;
    private final double length;

    public PathMotion(Path path, TimingLaw law, double durationSeconds) {
        this(path, new ArcLengthTable(path), law, durationSeconds);
    }

    public PathMotion(Path path, ArcLengthTable arc, TimingLaw law, double durationSeconds) {
        if (!(durationSeconds > 0)) {
            throw new IllegalArgumentException("duration must be > 0");
        }
        this.path = path;
        this.arc = arc;
        this.law = law;
        this.duration = durationSeconds;
        this.length = arc.length();
    }

    @Override
    public double duration() {
        return duration;
    }

    /** Thread-local scratch for {@link Path#eval}; avoids allocating per call. */
    private static final ThreadLocal<double[]> SCRATCH = ThreadLocal.withInitial(() -> new double[6]);

    @Override
    public void state(double t, MotionState o) {
        double tc = t < 0 ? 0 : Math.min(t, duration);
        double tau = tc / duration;
        double u = arc.paramAt(length * law.s(tau));
        double[] e = SCRATCH.get();
        path.eval(u, e);
        double dx = e[2];
        double dy = e[3];
        double dl = Math.hypot(dx, dy);
        double speed = length * law.ds(tau) / duration;
        double at = length * law.dds(tau) / (duration * duration);
        double tx;
        double ty;
        double kappa;
        if (dl > 1e-9) {
            tx = dx / dl;
            ty = dy / dl;
            kappa = (dx * e[5] - dy * e[4]) / (dl * dl * dl);
        } else {
            // Degenerate derivative (cusp / coincident control points): use chord direction.
            Vec2 c = path.end().sub(path.start()).normalized();
            tx = c.x();
            ty = c.y();
            kappa = 0;
        }
        double an = kappa * speed * speed;
        o.t = tc;
        o.x = e[0];
        o.y = e[1];
        o.vx = tx * speed;
        o.vy = ty * speed;
        o.speed = speed;
        o.accTangential = at;
        o.accNormal = Math.abs(an);
        // normal unit vector = tangent rotated +90°, signed by curvature
        o.ax = tx * at + (-ty) * an;
        o.ay = ty * at + tx * an;
        o.curvature = kappa;
        o.segment = 0;
    }

    @Override
    public Vec2 start() {
        return path.start();
    }

    @Override
    public Vec2 end() {
        return path.end();
    }

    public Path path() {
        return path;
    }

    public TimingLaw law() {
        return law;
    }

    public double length() {
        return length;
    }

    @Override
    public String describe() {
        return String.format("%s over %.1fpx in %.0fms (%s)", path.getClass().getSimpleName(), length, duration * 1000, law.name());
    }
}
