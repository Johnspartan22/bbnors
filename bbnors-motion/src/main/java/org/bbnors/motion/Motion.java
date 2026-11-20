package org.bbnors.motion;

import org.bbnors.core.geom.Vec2;

/**
 * A fully planned cursor movement: a continuous function of time that yields position, velocity
 * and acceleration for {@code t ∈ [0, duration]}.
 *
 * <p>Motions are immutable and contain no randomness; all stochastic choices are made by the
 * {@link MotionPlanner} when the motion is created. That means a motion can be evaluated any
 * number of times (playback, visualisation, analysis) with identical results.
 */
public interface Motion {

    /** Total duration in seconds (including any internal pauses). */
    double duration();

    /** Writes the state at time {@code t} (clamped to [0, duration]) into {@code out}. */
    void state(double t, MotionState out);

    Vec2 start();

    Vec2 end();

    /** Human-readable description for diagnostics. */
    String describe();

    /** A zero-length, zero-duration motion at {@code p}. */
    static Motion stationary(Vec2 p) {
        return new Motion() {
            @Override public double duration() { return 0; }
            @Override public void state(double t, MotionState o) {
                o.t = Math.max(0, t); o.x = p.x(); o.y = p.y();
                o.vx = o.vy = o.speed = o.accTangential = o.accNormal = o.ax = o.ay = o.curvature = 0;
                o.segment = 0;
            }
            @Override public Vec2 start() { return p; }
            @Override public Vec2 end() { return p; }
            @Override public String describe() { return "stationary " + p; }
        };
    }
}
