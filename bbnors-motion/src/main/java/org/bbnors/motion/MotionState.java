package org.bbnors.motion;

/**
 * Mutable kinematic state at one instant. Deliberately mutable and reusable so the per-frame
 * playback loop and the sampler run without allocating.
 *
 * <p>Units: pixels, seconds. Acceleration is decomposed into tangential (along the direction of
 * travel; positive = speeding up) and normal (centripetal, {@code κ·v²}) components.
 */
public final class MotionState {
    /** Time since the motion started, seconds. */
    public double t;
    public double x;
    public double y;
    public double vx;
    public double vy;
    /** |v|, px/s. */
    public double speed;
    /** Tangential acceleration, px/s². */
    public double accTangential;
    /** Normal (centripetal) acceleration magnitude, px/s². */
    public double accNormal;
    public double ax;
    public double ay;
    /** Signed curvature of the path at this point, 1/px. */
    public double curvature;
    /** Index of the active segment for composite motions. */
    public int segment;

    public double accMagnitude() {
        return Math.hypot(ax, ay);
    }

    public void set(MotionState o) {
        t = o.t;
        x = o.x;
        y = o.y;
        vx = o.vx;
        vy = o.vy;
        speed = o.speed;
        accTangential = o.accTangential;
        accNormal = o.accNormal;
        ax = o.ax;
        ay = o.ay;
        curvature = o.curvature;
        segment = o.segment;
    }

    @Override
    public String toString() {
        return String.format("t=%.4f pos=(%.2f,%.2f) v=%.1f at=%.1f an=%.1f", t, x, y, speed, accTangential, accNormal);
    }
}
