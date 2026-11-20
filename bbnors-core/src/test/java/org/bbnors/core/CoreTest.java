package org.bbnors.core;

import org.bbnors.core.config.Config;
import org.bbnors.core.config.ConfigSchema;
import org.bbnors.core.config.Setting;
import org.bbnors.core.event.EventBus;
import org.bbnors.core.geom.Rect;
import org.bbnors.core.geom.Vec2;
import org.bbnors.core.log.Log;
import org.bbnors.core.random.Rng;
import org.bbnors.core.time.ManualClock;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CoreTest {

    enum Mode { FAST, SLOW }

    public static final class Holder {
        public static final Setting<Double> SPEED = Setting.ofDouble("t.speed", 1.5, 0.1, 10, "speed");
        public static final Setting<Integer> COUNT = Setting.ofInt("t.count", 3, 1, 9, "count");
        public static final Setting<Mode> MODE = Setting.ofEnum("t.mode", Mode.class, Mode.FAST, "mode");
        public static final Setting<Boolean> FLAG = Setting.ofBoolean("t.flag", false, "flag");
    }

    @Test
    public void configParsesValidValuesAndFallsBackOnInvalid() {
        ConfigSchema schema = new ConfigSchema().registerAll(Holder.class);
        Properties p = new Properties();
        p.setProperty("t.speed", "2.5");
        p.setProperty("t.count", "42");          // out of range
        p.setProperty("t.mode", "slow");
        p.setProperty("t.flag", "yes");
        p.setProperty("t.typo", "1");            // unknown
        Config c = Config.load(schema, p);
        assertEquals(2.5, c.get(Holder.SPEED), 0);
        assertEquals(3, (int) c.get(Holder.COUNT));
        assertEquals(Mode.SLOW, c.get(Holder.MODE));
        assertTrue(c.get(Holder.FLAG));
        assertEquals(2, c.problems().size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void configWithRejectsInvalid() {
        Config.defaults(new ConfigSchema().registerAll(Holder.class)).with(Holder.SPEED, 100.0);
    }

    @Test
    public void schemaMarkdownListsAllKeys() {
        String md = new ConfigSchema().registerAll(Holder.class).toMarkdown();
        assertTrue(md.contains("`t.speed`"));
        assertTrue(md.contains("fast | slow"));
    }

    @Test
    public void eventBusDeliversToSupertypesAndIsolatesFailures() {
        EventBus bus = new EventBus();
        List<Object> got = new ArrayList<>();
        bus.setErrorHandler(t -> got.add("error"));
        bus.subscribe(CharSequence.class, e -> { throw new IllegalStateException(); });
        EventBus.Subscription sub = bus.subscribe(String.class, got::add);
        bus.subscribe(Object.class, e -> got.add("obj"));
        bus.publish("hi");
        assertEquals(java.util.Set.of("hi", "error", "obj"), new java.util.HashSet<>(got));
        assertEquals("exact type first", "hi", got.get(0));
        sub.cancel();
        got.clear();
        bus.publish("again");
        assertFalse(got.contains("again"));
    }

    @Test
    public void rngIsReproducibleAndDistributionsAreSane() {
        Rng a = new Rng(7);
        Rng b = new Rng(7);
        for (int i = 0; i < 100; i++) {
            assertEquals(a.gaussian(), b.gaussian(), 0);
        }
        Rng r = new Rng(1);
        int n = 200_000;
        double sum = 0;
        double sumSq = 0;
        for (int i = 0; i < n; i++) {
            double g = r.gaussian();
            sum += g;
            sumSq += g * g;
        }
        double mean = sum / n;
        double var = sumSq / n - mean * mean;
        assertEquals(0, mean, 0.01);
        assertEquals(1, var, 0.02);
        for (int i = 0; i < 1000; i++) {
            double t = r.truncatedGaussian(0, 5, -1, 1);
            assertTrue(t >= -1 && t <= 1);
        }
        double[] ln = new double[20001];
        for (int i = 0; i < ln.length; i++) ln[i] = r.logNormal(200, 0.3);
        java.util.Arrays.sort(ln);
        assertEquals(200, ln[ln.length / 2], 6);
    }

    @Test
    public void manualClockAdvancesOnSleep() throws InterruptedException {
        ManualClock c = new ManualClock();
        c.sleepMillis(250);
        assertEquals(250, c.millis());
        c.sleepUntil(c.nanos() - 5);
        assertEquals(250, c.millis());
    }

    @Test
    public void geometryBasics() {
        Vec2 a = Vec2.of(3, 4);
        assertEquals(5, a.length(), 1e-12);
        assertEquals(0, a.dot(a.perpendicular()), 1e-12);
        Rect r = Rect.centered(Vec2.of(10, 10), 4, 6);
        assertTrue(r.contains(Vec2.of(11, 12)));
        assertFalse(r.contains(Vec2.of(13, 10)));
        assertEquals(4, r.minSide(), 0);
    }

    @Test
    public void ringBufferKeepsNewest() {
        Log.RingBufferSink sink = new Log.RingBufferSink(2);
        Log.addSink(sink);
        try {
            Log log = Log.get("t");
            log.info("a");
            log.info("b");
            log.info("c");
            assertEquals(List.of("b", "c"), sink.snapshot().stream().map(Log.Record::message).toList());
        } finally {
            Log.removeSink(sink);
        }
    }
}
