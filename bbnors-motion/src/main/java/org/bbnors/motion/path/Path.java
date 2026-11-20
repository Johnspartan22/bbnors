package org.bbnors.motion.path;

import org.bbnors.core.geom.Vec2;

/**
 * A parametric plane curve {@code P(u)}, {@code u ∈ [0, 1]}, with analytic first and second
 * derivatives.
 *
 * <p>The path describes <em>geometry only</em>. How fast the cursor travels along it is the job of
 * a {@link org.bbnors.motion.timing.TimingLaw}; the two are joined through an
 * {@link ArcLengthTable} so the timing law controls distance travelled, not the raw parameter
 * (Bezier and spline parameters are not proportional to distance).
 *
 * <p>Implementations are immutable and thread-safe, and {@link #eval} does not allocate.
 */
public interface Path {

    /**
     * Writes {@code x, y, dx/du, dy/du, d²x/du², d²y/du²} at parameter {@code u} into
     * {@code out[0..5]}. {@code u} is clamped to {@code [0, 1]}.
     */
    void eval(double u, double[] out);

    Vec2 start();

    Vec2 end();

    /**
     * Parameter values where the path's smoothness may drop (segment joins), including 0 and 1.
     * Numerical integrators align their intervals to these. Smooth single-piece paths return
     * {@code {0, 1}}.
     */
    default double[] breakpoints() {
        return new double[]{0, 1};
    }

    /** Convenience (allocating) point lookup. */
    default Vec2 point(double u) {
        double[] o = new double[6];
        eval(u, o);
        return new Vec2(o[0], o[1]);
    }

    /** Convenience (allocating) first-derivative lookup. */
    default Vec2 derivative(double u) {
        double[] o = new double[6];
        eval(u, o);
        return new Vec2(o[2], o[3]);
    }

    static double clamp01(double u) {
        return u < 0 ? 0 : (u > 1 ? 1 : u);
    }
}
