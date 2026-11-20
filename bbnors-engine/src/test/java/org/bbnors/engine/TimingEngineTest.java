package org.bbnors.engine;

import org.bbnors.core.event.EventBus;
import org.bbnors.core.random.Rng;
import org.bbnors.core.time.Clock;
import org.bbnors.core.time.ManualClock;
import org.bbnors.engine.timing.ManualScheduler;
import org.bbnors.engine.timing.RealTimeScheduler;
import org.bbnors.engine.timing.TaskScheduler;
import org.bbnors.engine.timing.TimingProfile;
import org.bbnors.engine.timing.TimingProfiles;
import org.bbnors.engine.timing.Waits;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TimingEngineTest {

    @Test
    public void waitSucceedsWhenConditionBecomesTrue() {
        ManualClock c = new ManualClock();
        long readyAt = c.nanos() + Duration.ofMillis(300).toNanos();
        Waits.Result r = Waits.until(c, () -> c.nanos() >= readyAt).timeout(Duration.ofSeconds(1)).poll(Duration.ofMillis(50)).await();
        assertTrue(r.satisfied());
        assertEquals(300, r.elapsedNanos() / 1_000_000);
    }

    @Test
    public void waitTimesOut() {
        ManualClock c = new ManualClock();
        Waits.Result r = Waits.until(c, () -> false).timeout(Duration.ofMillis(500)).await();
        assertTrue(r.timedOut());
        assertEquals(500, r.elapsedNanos() / 1_000_000);
    }

    @Test
    public void stableForRequiresConsecutivePolls() {
        ManualClock c = new ManualClock();
        AtomicInteger n = new AtomicInteger();
        // pattern: true, false, true, true, true ...
        Waits.Result r = Waits.until(c, () -> {
            int i = n.getAndIncrement();
            return i != 1;
        }).stableFor(3).await();
        assertTrue(r.satisfied());
        assertEquals(5, r.polls());
    }

    @Test
    public void resetWhileExtendsTimeoutDuringProgress() {
        ManualClock c = new ManualClock();
        long progressUntil = Duration.ofMillis(1500).toNanos();
        long doneAt = Duration.ofMillis(1700).toNanos();
        Waits.Result r = Waits.until(c, () -> c.nanos() >= doneAt)
                .timeout(Duration.ofMillis(500))
                .resetWhile(() -> c.nanos() < progressUntil)
                .await();
        assertTrue("would have timed out at 500ms without reset", r.satisfied());
    }

    @Test
    public void eventWaitNeedsNoPolling() throws Exception {
        EventBus bus = new EventBus();
        Thread t = Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(30);
            } catch (InterruptedException ignored) {
                return;
            }
            bus.publish("noise");
            bus.publish("tick-7");
        });
        String got = Waits.forEvent(bus, String.class, s -> s.startsWith("tick"), Duration.ofSeconds(2));
        assertEquals("tick-7", got);
        t.join();
        assertNull(Waits.forEvent(bus, String.class, s -> true, Duration.ofMillis(20)));
    }

    @Test
    public void manualSchedulerRunsInDueOrderWithFixedRate() {
        ManualClock c = new ManualClock();
        ManualScheduler s = new ManualScheduler(c);
        List<String> log = new ArrayList<>();
        s.schedule(ms(30), () -> log.add("b@" + c.millis()));
        s.schedule(ms(10), () -> log.add("a@" + c.millis()));
        TaskScheduler.Task rep = s.scheduleAtFixedRate(ms(0), ms(25), () -> log.add("r@" + c.millis()));
        TaskScheduler.Task cancelled = s.schedule(ms(20), () -> log.add("never"));
        assertTrue(cancelled.cancel());
        s.advance(ms(60));
        assertEquals(List.of("r@0", "a@10", "r@25", "b@30", "r@50"), log);
        assertEquals(60, c.millis());
        rep.cancel();
        s.runUntilIdle(ms(1000));
        assertEquals(5, log.size());
    }

    @Test
    public void realTimeSchedulerFiresOnTime() throws Exception {
        try (RealTimeScheduler s = new RealTimeScheduler(Clock.system(), "test-sched")) {
            CountDownLatch l = new CountDownLatch(3);
            long t0 = System.nanoTime();
            long[] fired = new long[3];
            AtomicInteger i = new AtomicInteger();
            s.scheduleAtFixedRate(ms(20), ms(20), () -> {
                int k = i.getAndIncrement();
                if (k < 3) {
                    fired[k] = System.nanoTime() - t0;
                    l.countDown();
                }
            });
            assertTrue(l.await(2, TimeUnit.SECONDS));
            for (int k = 0; k < 3; k++) {
                long expected = ms(20) * (k + 1);
                assertTrue("fired at " + fired[k] / 1e6 + "ms, expected >= " + expected / 1e6, fired[k] >= expected);
                assertTrue("late by < 50ms", fired[k] - expected < ms(50));
            }
        }
    }

    @Test
    public void timingProfilesSampleWithinBoundsAndLoadOverrides() {
        TimingProfiles tp = new TimingProfiles();
        Rng r = new Rng(3);
        for (TimingProfile p : tp.all()) {
            for (int i = 0; i < 2000; i++) {
                double v = p.sample(r);
                assertTrue(p + " -> " + v, v >= p.min() && v <= p.max());
            }
        }
        Properties props = new Properties();
        props.setProperty("timing.key.gap.center", "0.2");
        props.setProperty("timing.key.gap.max", "0.9");
        props.setProperty("timing.custom.wait.kind", "uniform");
        props.setProperty("timing.custom.wait.center", "1");
        props.setProperty("timing.custom.wait.spread", "0.5");
        props.setProperty("timing.custom.wait.max", "1.5");
        props.setProperty("timing.bad.center", "-1");
        props.setProperty("timing.bad.max", "1");
        List<String> problems = tp.load(props);
        assertEquals(problems.toString(), 1, problems.size());
        assertEquals(0.2, tp.get("key.gap").center(), 0);
        assertEquals(0.9, tp.get("key.gap").max(), 0);
        assertEquals(TimingProfile.Kind.UNIFORM, tp.get("custom.wait").kind());
    }

    private static long ms(long v) {
        return TimeUnit.MILLISECONDS.toNanos(v);
    }
}
