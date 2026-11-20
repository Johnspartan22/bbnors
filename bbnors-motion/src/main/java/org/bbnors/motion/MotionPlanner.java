package org.bbnors.motion;

import org.bbnors.core.geom.Rect;
import org.bbnors.core.geom.Vec2;
import org.bbnors.core.random.Rng;
import org.bbnors.motion.gen.PathGenerators;
import org.bbnors.motion.path.Path;

import java.util.List;

/**
 * The trajectory generation engine: turns (current position, target, profile) into a
 * deterministic {@link Motion}. All randomness is drawn here from the supplied {@link Rng}.
 *
 * <p>Pipeline:
 * <ol>
 *   <li><b>Aim point</b>: {@link EndpointModel} picks a pixel inside the target.</li>
 *   <li><b>Duration</b>: Fitts' law on (distance, target min side), multiplied by a log-normal
 *       factor ({@code durationJitter}), clamped to the profile's [min, max].</li>
 *   <li><b>Overshoot</b> (optional, distance ≥ {@code overshootMinDistance}): the primary movement
 *       aims past the target along the approach direction; after {@code correctionPause} a short
 *       corrective sub-movement (its own Fitts duration) lands on the aim point.</li>
 *   <li><b>Geometry</b>: the profile's path generator.</li>
 *   <li><b>Timing</b>: the profile's timing law.</li>
 * </ol>
 * The final position of every planned motion is exactly the aim point, which always lies inside
 * the target.
 */
public final class MotionPlanner {

    /** Below this distance (px) a movement is considered a no-op. */
    public static final double MIN_DISTANCE = 0.5;

    public Motion plan(MovementProfile p, Vec2 from, Rect target, Rng rng) {
        Vec2 aim = EndpointModel.pick(target, p.endpointSpread(), rng);
        return planTo(p, from, aim, Math.max(1, target.minSide()), rng);
    }

    /** Plans to an exact point, using {@code targetWidth} for the Fitts difficulty. */
    public Motion planTo(MovementProfile p, Vec2 from, Vec2 aim, double targetWidth, Rng rng) {
        double dist = from.distance(aim);
        if (dist < MIN_DISTANCE) {
            return Motion.stationary(aim);
        }
        double duration = duration(p, dist, targetWidth, rng);
        boolean overshoot = p.overshootChance() > 0 && dist >= p.overshootMinDistance()
                && p.overshootFraction() > 0 && rng.chance(p.overshootChance());
        if (!overshoot) {
            return new PathMotion(geometry(p, from, aim, rng), p.timingLaw(), duration);
        }
        Vec2 dir = aim.sub(from).normalized();
        double over = dist * p.overshootFraction() * rng.uniform(0.6, 1.4);
        Vec2 lateral = dir.perpendicular().scale(rng.gaussian() * over * 0.35);
        Vec2 overshootPoint = aim.add(dir.scale(over)).add(lateral);
        double primaryDist = from.distance(overshootPoint);
        double primaryDuration = duration(p, primaryDist, targetWidth * 2, rng);
        Motion primary = new PathMotion(geometry(p, from, overshootPoint, rng), p.timingLaw(), primaryDuration);
        double corrDist = overshootPoint.distance(aim);
        double corrDuration = clamp(Fitts.movementTime(p.fittsA() * 0.6, p.fittsB(), corrDist, targetWidth), p.minDuration(), p.maxDuration());
        // Corrections are short and nearly straight: low curvature, same timing law.
        Path corrPath = PathGenerators.bezier(overshootPoint, aim, 3, p.curvature() * 0.3, p.jitter() * 0.5, rng);
        Motion correction = new PathMotion(corrPath, p.timingLaw(), corrDuration);
        double pause = p.correctionPause() * rng.logNormal(1, 0.25);
        return new SequenceMotion(List.of(primary, correction), List.of(pause));
    }

    private static double duration(MovementProfile p, double dist, double width, Rng rng) {
        double base = Fitts.movementTime(p.fittsA(), p.fittsB(), dist, width);
        double jittered = p.durationJitter() > 0 ? base * rng.logNormal(1, p.durationJitter()) : base;
        return clamp(jittered, p.minDuration(), p.maxDuration());
    }

    private static Path geometry(MovementProfile p, Vec2 from, Vec2 to, Rng rng) {
        return switch (p.path()) {
            case LINEAR -> PathGenerators.linear(from, to);
            case BEZIER -> PathGenerators.bezier(from, to, p.bezierDegree(), p.curvature(), p.jitter(), rng);
            case CATMULL_ROM -> PathGenerators.catmullRom(from, to, p.waypoints(), p.curvature(), p.jitter(), rng);
            case WIND -> PathGenerators.wind(from, to, p.curvature(), rng);
        };
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
