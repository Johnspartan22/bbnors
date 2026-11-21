package org.bbnors.lab;

import org.bbnors.core.geom.Rect;
import org.bbnors.core.geom.Vec2;
import org.bbnors.core.random.Rng;
import org.bbnors.motion.Motion;
import org.bbnors.motion.MotionMetrics;
import org.bbnors.motion.MotionPlanner;
import org.bbnors.motion.MotionSampler;
import org.bbnors.motion.MovementProfile;
import org.bbnors.motion.TrajectorySamples;

/**
 * One planned + sampled + measured trajectory: the unit the lab displays, compares and exports.
 * Fully determined by (profile, from, target, seed).
 */
public record TrajectoryRun(MovementProfile profile, Vec2 from, Rect target, long seed,
                            Motion motion, TrajectorySamples samples, MotionMetrics metrics, long planNanos) {

    public static TrajectoryRun of(MovementProfile p, Vec2 from, Rect target, long seed) {
        MotionPlanner planner = new MotionPlanner();
        Motion m = planner.plan(p, from, target, new Rng(seed));
        // Report warm planning cost: re-plan the identical movement after a JIT warm-up.
        for (int i = 0; i < 200; i++) {
            planner.plan(p, from, target, new Rng(seed));
        }
        int reps = 200;
        long t0 = System.nanoTime();
        for (int i = 0; i < reps; i++) {
            planner.plan(p, from, target, new Rng(seed));
        }
        long planNanos = (System.nanoTime() - t0) / reps;
        TrajectorySamples s = MotionSampler.sample(m, p.sampleRateHz());
        MotionMetrics mm = MotionMetrics.of(s, m.end(), target);
        return new TrajectoryRun(p, from, target, seed, m, s, mm, planNanos);
    }

    public String label() {
        return profile.name();
    }
}
