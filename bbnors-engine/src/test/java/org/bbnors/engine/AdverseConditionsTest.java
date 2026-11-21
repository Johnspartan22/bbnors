package org.bbnors.engine;

import org.bbnors.core.config.Config;
import org.bbnors.core.geom.Rect;
import org.bbnors.core.geom.Vec2;
import org.bbnors.core.random.Rng;
import org.bbnors.core.time.Clock;
import org.bbnors.core.time.ManualClock;
import org.bbnors.engine.input.RecordingBackend;
import org.bbnors.motion.MovementProfile;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertTrue;

/**
 * Scenario tests: the engine under conditions a real machine produces. A "jittery" clock makes
 * every sleep overshoot by a random 0-12 ms (GC pauses, a busy scheduler), which must not break
 * endpoint precision, ordering, or make the cursor travel backwards in time.
 */
public class AdverseConditionsTest {

    /** Virtual clock whose sleeps overshoot randomly, like an overloaded OS scheduler. */
    static final class JitteryClock implements Clock {
        private final ManualClock base = new ManualClock();
        private final Rng rng;
        private final double maxJitterMs;

        JitteryClock(long seed, double maxJitterMs) {
            this.rng = new Rng(seed);
            this.maxJitterMs = maxJitterMs;
        }

        @Override public long nanos() { return base.nanos(); }

        @Override
        public void sleepNanos(long nanos) throws InterruptedException {
            base.sleepNanos(nanos + (long) (rng.uniform(0, maxJitterMs) * 1e6));
        }
    }

    @Test
    public void movesStayPreciseUnderSchedulingJitterForEveryProfile() {
        for (double jitter : new double[]{0, 4, 12}) {
            JitteryClock clock = new JitteryClock(17, jitter);
            RecordingBackend rec = new RecordingBackend(clock);
            InteractionEngine e = InteractionEngine.builder(clock, rec).executor(Runnable::run)
                    .config(Config.defaults(InteractionEngine.schema()).with(EngineSettings.SEED, 99L))
                    .initialMouse(new Vec2(10, 10)).build();
            Rng r = new Rng(5);
            for (MovementProfile p : e.profiles().all()) {
                for (int i = 0; i < 25; i++) {
                    Rect target = Rect.centered(new Vec2(r.uniform(30, 730), r.uniform(30, 470)), r.uniform(6, 40), r.uniform(6, 40));
                    rec.clear();
                    Vec2 end = e.mouse().moveTo(() -> target, p.name()).await();
                    String ctx = p.name() + " jitter=" + jitter + " #" + i;
                    assertTrue(ctx, target.contains(end));
                    List<RecordingBackend.Event> ev = rec.events(RecordingBackend.Type.MOVE);
                    if (!ev.isEmpty()) {
                        RecordingBackend.Event last = ev.get(ev.size() - 1);
                        assertTrue(ctx + " last pixel in target", target.inset(-0.5).contains(new Vec2(last.a(), last.b())));
                    }
                    for (int k = 1; k < ev.size(); k++) {
                        assertTrue(ctx + " time ordering", ev.get(k).nanos() >= ev.get(k - 1).nanos());
                    }
                }
            }
        }
    }

    @Test
    public void slowMachineSkipsFramesInsteadOfStretchingTheMovement() {
        JitteryClock clock = new JitteryClock(3, 30); // every frame ~15 ms late on average
        RecordingBackend rec = new RecordingBackend(clock);
        InteractionEngine e = InteractionEngine.builder(clock, rec).executor(Runnable::run)
                .config(Config.defaults(InteractionEngine.schema()).with(EngineSettings.SEED, 7L)).build();
        MovementProfile p = e.profiles().get("standard").toBuilder().name("fixed").durationJitter(0).overshootChance(0).build();
        e.profiles().put(p);
        long t0 = clock.nanos();
        e.mouse().moveTo(() -> Rect.centered(new Vec2(700, 450), 10, 10), "fixed").await();
        double took = (clock.nanos() - t0) / 1e9;
        double planned = org.bbnors.motion.Fitts.movementTime(p.fittsA(), p.fittsB(), new Vec2(0, 0).distance(new Vec2(700, 450)), 10);
        // Real-time playback: total time ≈ planned + at most one late frame, not planned × lateness.
        assertTrue("took " + took + " planned " + planned, took < planned + 0.035);
    }
}
