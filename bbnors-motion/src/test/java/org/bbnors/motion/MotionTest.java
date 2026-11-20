package org.bbnors.motion;

import org.bbnors.core.geom.Rect;
import org.bbnors.core.geom.Vec2;
import org.bbnors.core.random.Rng;
import org.bbnors.motion.path.BezierPath;
import org.bbnors.motion.timing.TimingLaws;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Kinematics, planner contracts, determinism and profile configuration. */
public class MotionTest {

    private static final MotionPlanner PLANNER = new MotionPlanner();

    @Test
    public void pathMotionKinematicsAreConsistent() {
        PathMotion m = new PathMotion(new BezierPath(Vec2.of(10, 10), Vec2.of(200, -80), Vec2.of(400, 300), Vec2.of(600, 220)),
                TimingLaws.beta(0.4, 7), 0.45);
        MotionState s0 = new MotionState();
        MotionState a = new MotionState();
        MotionState b = new MotionState();
        m.state(0, s0);
        assertEquals(10, s0.x, 1e-9);
        assertEquals(10, s0.y, 1e-9);
        assertEquals(0, s0.speed, 1e-9);
        m.state(m.duration(), s0);
        assertEquals(600, s0.x, 1e-6);
        assertEquals(220, s0.y, 1e-6);
        assertEquals(0, s0.speed, 1e-9);
        double h = 1e-6;
        double integrated = 0;
        double dtInt = m.duration() / 20_000;
        for (int i = 0; i < 20_000; i++) {
            m.state((i + 0.5) * dtInt, s0);
            integrated += s0.speed * dtInt;
        }
        assertEquals("∫speed dt = arc length", m.length(), integrated, m.length() * 1e-4);
        for (int i = 1; i < 50; i++) {
            double t = m.duration() * i / 50;
            m.state(t, s0);
            m.state(t - h, a);
            m.state(t + h, b);
            double fdVx = (b.x - a.x) / (2 * h);
            double fdVy = (b.y - a.y) / (2 * h);
            double tol = 0.01 * Math.max(50, s0.speed);
            assertEquals("vx at t=" + t, fdVx, s0.vx, tol);
            assertEquals("vy at t=" + t, fdVy, s0.vy, tol);
            double fdAx = (b.vx - a.vx) / (2 * h);
            double fdAy = (b.vy - a.vy) / (2 * h);
            double atol = 0.02 * Math.max(500, s0.accMagnitude());
            assertEquals("ax at t=" + t, fdAx, s0.ax, atol);
            assertEquals("ay at t=" + t, fdAy, s0.ay, atol);
        }
    }

    @Test
    public void everyPresetLandsInsideTargetContinuouslyAndWithinDurationBounds() {
        MovementProfiles profiles = new MovementProfiles();
        Rng rng = new Rng(2024);
        TrajectorySamples buf = new TrajectorySamples();
        for (MovementProfile p : profiles.all()) {
            for (int i = 0; i < 300; i++) {
                Vec2 from = new Vec2(rng.uniform(0, 765), rng.uniform(0, 503));
                Rect target = Rect.centered(new Vec2(rng.uniform(20, 745), rng.uniform(20, 483)), rng.uniform(4, 60), rng.uniform(4, 60));
                Motion m = PLANNER.plan(p, from, target, rng);
                TrajectorySamples s = MotionSampler.sample(m, p.sampleRateHz(), buf);
                MotionMetrics mm = MotionMetrics.of(s, m.end(), target);
                String ctx = p.name() + " #" + i + " " + mm;
                assertTrue(ctx, mm.endInTarget());
                assertTrue(ctx, target.contains(m.end()));
                assertEquals(ctx, 0, mm.endpointError(), 1e-6);
                assertEquals(ctx, from.x(), s.x[0], 1e-9);
                assertEquals(ctx, from.y(), s.y[0], 1e-9);
                if (m.duration() > 0) {
                    double maxDur = p.maxDuration() * 2 + 1; // two sub-movements + pause at most
                    assertTrue(ctx, m.duration() >= p.minDuration() - 1e-12 && m.duration() <= maxDur);
                    // continuity: no sample-to-sample jump larger than peak speed * dt (+5%)
                    assertTrue(ctx, mm.maxStep() <= mm.peakSpeed() / p.sampleRateHz() * 1.05 + 1e-6);
                }
                for (int k = 0; k < s.size(); k++) {
                    assertFalse(ctx, Double.isNaN(s.x[k]) || Double.isNaN(s.speed[k]) || Double.isNaN(s.acc[k]));
                }
            }
        }
    }

    @Test
    public void planningIsDeterministicForAGivenSeed() {
        MovementProfile p = new MovementProfiles().get("standard");
        Rect target = Rect.centered(Vec2.of(500, 300), 30, 30);
        TrajectorySamples a = MotionSampler.sample(PLANNER.plan(p, Vec2.of(20, 20), target, new Rng(42)), 200);
        TrajectorySamples b = MotionSampler.sample(PLANNER.plan(p, Vec2.of(20, 20), target, new Rng(42)), 200);
        assertEquals(a.size(), b.size());
        assertArrayEquals(java.util.Arrays.copyOf(a.x, a.size()), java.util.Arrays.copyOf(b.x, b.size()), 0);
        assertArrayEquals(java.util.Arrays.copyOf(a.y, a.size()), java.util.Arrays.copyOf(b.y, b.size()), 0);
    }

    @Test
    public void overshootProducesContinuousTwoPartMotion() {
        MovementProfile p = MovementProfile.builder("os").overshootChance(1).overshootMinDistance(0).build();
        Rect target = Rect.centered(Vec2.of(600, 400), 20, 20);
        Motion m = PLANNER.plan(p, Vec2.of(50, 50), target, new Rng(1));
        assertTrue(m instanceof SequenceMotion);
        TrajectorySamples s = MotionSampler.sample(m, 500);
        MotionMetrics mm = MotionMetrics.of(s, m.end(), target);
        assertEquals(2, mm.segments());
        assertTrue(mm.endInTarget());
        // primary movement overshoots past the aim point
        SequenceMotion seq = (SequenceMotion) m;
        Vec2 over = seq.parts().get(0).end();
        assertTrue(over.distance(Vec2.of(50, 50)) > m.end().distance(Vec2.of(50, 50)));
        assertTrue(mm.maxStep() <= mm.peakSpeed() / 500 * 1.05 + 1e-6);
    }

    @Test
    public void durationFollowsFittsLawWithoutJitter() {
        MovementProfile p = MovementProfile.builder("nojit").durationJitter(0).overshootChance(0).endpointSpread(0).build();
        Rect target = Rect.centered(Vec2.of(410, 10), 10, 10);
        Motion m = PLANNER.plan(p, Vec2.of(10, 10), target, new Rng(3));
        assertEquals(Fitts.movementTime(p.fittsA(), p.fittsB(), 400, 10), m.duration(), 1e-12);
    }

    @Test
    public void shortMovementIsStationary() {
        Motion m = PLANNER.planTo(new MovementProfiles().get("standard"), Vec2.of(5, 5), Vec2.of(5.1, 5.1), 10, new Rng(1));
        assertEquals(0, m.duration(), 0);
        assertEquals(1, MotionSampler.sample(m, 200).size());
    }

    @Test
    public void samplerEndsExactlyAtDuration() {
        Motion m = new PathMotion(new BezierPath(Vec2.of(0, 0), Vec2.of(50, 80), Vec2.of(100, 0)), TimingLaws.MINIMUM_JERK, 0.1234);
        TrajectorySamples s = MotionSampler.sample(m, 100);
        assertEquals(0.1234, s.t[s.size() - 1], 0);
        assertEquals(14, s.size());
    }

    @Test
    public void profileCodecRoundTripsAndReportsProblems() {
        MovementProfiles reg = new MovementProfiles();
        for (MovementProfile p : reg.all()) {
            ProfileCodec.Result r = ProfileCodec.parse(ProfileCodec.toProperties(p), Map.of());
            assertTrue(r.problems().isEmpty());
            assertEquals(p, r.profiles().get(p.name()));
        }
        Properties props = new Properties();
        props.setProperty("profile.mine.curvature", "0.2");
        props.setProperty("profile.mine.law", "minimum_jerk");
        props.setProperty("profile.mine.fitts-b", "banana");
        props.setProperty("profile.mine.colour", "red");
        props.setProperty("profile.precise.fitts-a", "0.2"); // override one field of a preset
        List<String> problems = reg.load(props);
        assertEquals(problems.toString(), 2, problems.size());
        assertEquals(0.2, reg.get("mine").curvature(), 0);
        assertEquals(MovementProfile.LawKind.MINIMUM_JERK, reg.get("mine").law());
        assertEquals(0.110, reg.get("mine").fittsB(), 0);
        assertEquals(0.2, reg.get("precise").fittsA(), 0);
        assertEquals(0.36, reg.get("precise").peak(), 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidBetaCombinationIsRejected() {
        MovementProfile.builder("bad").peak(0.1).sharpness(5).build();
    }

    @Test
    public void betaTimingPeaksEarlierThanMinimumJerk() {
        MovementProfile beta = MovementProfile.builder("b").durationJitter(0).overshootChance(0).build();
        MovementProfile mj = beta.toBuilder().name("mj").law(MovementProfile.LawKind.MINIMUM_JERK).build();
        Rect target = Rect.centered(Vec2.of(700, 400), 20, 20);
        MotionMetrics a = MotionMetrics.of(MotionSampler.sample(PLANNER.plan(beta, Vec2.of(10, 10), target, new Rng(8)), 1000), null, null);
        MotionMetrics b = MotionMetrics.of(MotionSampler.sample(PLANNER.plan(mj, Vec2.of(10, 10), target, new Rng(8)), 1000), null, null);
        assertEquals(0.42, a.peakSpeedTime(), 0.03);
        assertEquals(0.5, b.peakSpeedTime(), 0.03);
    }
}
