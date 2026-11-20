package org.bbnors.motion.timing;

/**
 * Normalised progress-versus-time law {@code s(τ)} on {@code τ ∈ [0,1]} with {@code s(0)=0},
 * {@code s(1)=1}, monotone non-decreasing.
 *
 * <p>For a movement of arc length {@code L} and duration {@code T}, the distance travelled at
 * time {@code t} is {@code L·s(t/T)}, the speed is {@code L·s'(t/T)/T} and the tangential
 * acceleration is {@code L·s''(t/T)/T²}. All three are available analytically, which is what lets
 * the engine report exact velocity and acceleration instead of finite differences.
 *
 * <p>Implementations are immutable and thread-safe.
 */
public interface TimingLaw {

    /** Progress in [0,1]. */
    double s(double tau);

    /** ds/dτ. */
    double ds(double tau);

    /** d²s/dτ². */
    double dds(double tau);

    /** Short identifier, e.g. {@code "minimum-jerk"}. */
    String name();

    /** Location of peak speed in [0,1] (computed numerically if not overridden). */
    default double peakTau() {
        double best = 0;
        double bestV = -1;
        for (int i = 0; i <= 1000; i++) {
            double t = i / 1000.0;
            double v = ds(t);
            if (v > bestV) {
                bestV = v;
                best = t;
            }
        }
        return best;
    }

    static double clamp(double tau) {
        return tau < 0 ? 0 : (tau > 1 ? 1 : tau);
    }
}
