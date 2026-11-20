package org.bbnors.motion;

/**
 * Samples a {@link Motion} at a fixed rate. The first sample is at t=0 and the last at exactly
 * t=duration, so the final sample is always the true endpoint.
 */
public final class MotionSampler {

    private MotionSampler() {
    }

    public static TrajectorySamples sample(Motion m, double rateHz) {
        return sample(m, rateHz, new TrajectorySamples());
    }

    /** Samples into {@code into} (reused; grown if needed) and returns it. */
    public static TrajectorySamples sample(Motion m, double rateHz, TrajectorySamples into) {
        if (!(rateHz > 0)) {
            throw new IllegalArgumentException("rate must be > 0");
        }
        double dur = m.duration();
        double dt = 1.0 / rateHz;
        int steps = (int) Math.ceil(dur / dt - 1e-9);
        int n = steps + 1;
        into.ensureCapacity(n);
        MotionState s = new MotionState();
        for (int i = 0; i < n; i++) {
            double t = i == n - 1 ? dur : i * dt;
            m.state(t, s);
            into.put(i, s);
        }
        into.setSize(n);
        return into;
    }
}
