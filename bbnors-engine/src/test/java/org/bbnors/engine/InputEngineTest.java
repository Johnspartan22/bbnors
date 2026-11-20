package org.bbnors.engine;

import org.bbnors.core.config.Config;
import org.bbnors.core.geom.Rect;
import org.bbnors.core.geom.Vec2;
import org.bbnors.core.time.ManualClock;
import org.bbnors.engine.camera.CameraController;
import org.bbnors.engine.camera.SimulatedCamera;
import org.bbnors.engine.input.MouseButton;
import org.bbnors.engine.input.RecordingBackend;
import org.bbnors.engine.input.RecordingBackend.Event;
import org.bbnors.engine.input.RecordingBackend.Type;
import org.bbnors.engine.interact.OpHandle;
import org.bbnors.engine.mouse.MouseEvents;
import org.bbnors.engine.timing.TimingProfile;
import org.bbnors.engine.timing.TimingProfiles;
import org.junit.Test;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Mouse / keyboard / camera behaviour, run deterministically on a virtual clock. */
public class InputEngineTest {

    private final ManualClock clock = new ManualClock();

    private InteractionEngine engine(RecordingBackend backend, SimulatedCamera cam) {
        Config cfg = Config.defaults(InteractionEngine.schema()).with(EngineSettings.SEED, 1234L);
        return InteractionEngine.builder(clock, backend).camera(cam).executor(Runnable::run)
                .config(cfg).initialMouse(new Vec2(100, 100)).build();
    }

    @Test
    public void moveIsPlayedAtProfileRateAndEndsInsideTarget() {
        RecordingBackend rec = new RecordingBackend(clock);
        InteractionEngine e = engine(rec, null);
        List<MouseEvents.MotionPlanned> planned = new ArrayList<>();
        e.bus().subscribe(MouseEvents.MotionPlanned.class, planned::add);
        Rect target = Rect.centered(new Vec2(600, 400), 24, 24);
        long t0 = clock.nanos();
        OpHandle<Vec2> h = e.mouse().moveTo(target);
        assertEquals(OpHandle.Status.COMPLETED, h.status());
        Vec2 end = h.await();
        assertTrue(target.contains(end));
        List<Event> moves = rec.events(Type.MOVE);
        Event last = moves.get(moves.size() - 1);
        assertTrue(target.contains(new Vec2(last.a(), last.b())));
        double duration = planned.stream().mapToDouble(p -> p.motion().duration()).sum();
        double rate = e.profiles().get("standard").sampleRateHz();
        // at most one event per tick plus the final one
        assertTrue(moves.size() <= Math.ceil(duration * rate) + planned.size());
        assertTrue("long move sends many updates", moves.size() > duration * rate * 0.5);
        long period = (long) (1e9 / rate);
        for (int i = 1; i < moves.size(); i++) {
            Event a = moves.get(i - 1);
            Event b = moves.get(i);
            assertTrue("no duplicate positions", a.a() != b.a() || a.b() != b.b());
            assertTrue("time monotone", b.nanos() >= a.nanos());
        }
        // Updates land exactly on the playback grid (virtual clock), and ticks whose rounded pixel
        // did not change are skipped (the slow start of a bell-shaped velocity profile).
        for (Event ev : moves.subList(0, moves.size() - 1)) {
            if (planned.size() == 1) {
                assertEquals("on tick grid", 0, (ev.nanos() - t0) % period);
            }
        }
    }

    @Test
    public void moveAndClickClicksInsideTargetAfterArriving() {
        RecordingBackend rec = new RecordingBackend(clock);
        InteractionEngine e = engine(rec, null);
        Rect target = Rect.centered(new Vec2(300, 250), 30, 18);
        e.mouse().moveAndClick(target, MouseButton.LEFT).await();
        List<Event> ev = rec.events();
        int pressIdx = -1;
        int releaseIdx = -1;
        for (int i = 0; i < ev.size(); i++) {
            if (ev.get(i).type() == Type.PRESS) pressIdx = i;
            if (ev.get(i).type() == Type.RELEASE) releaseIdx = i;
        }
        assertTrue(pressIdx > 0 && releaseIdx == pressIdx + 1);
        assertEquals(Type.MOVE, ev.get(pressIdx - 1).type());
        assertTrue(target.contains(e.mouse().position()));
        TimingProfile hold = e.timing().get(TimingProfiles.MOUSE_CLICK_HOLD);
        double held = (ev.get(releaseIdx).nanos() - ev.get(pressIdx).nanos()) / 1e9;
        assertTrue(held >= hold.min() - 1e-9 && held <= hold.max() + 1e-9);
        assertFalse(e.mouse().isPressed(MouseButton.LEFT));
    }

    @Test
    public void typingUsesShiftForCapitalsAndReleasesEverything() {
        RecordingBackend rec = new RecordingBackend(clock);
        InteractionEngine e = engine(rec, null);
        e.keyboard().type("Hi!").await();
        List<Event> presses = rec.events(Type.KEY_PRESS);
        List<Event> releases = rec.events(Type.KEY_RELEASE);
        assertEquals(presses.size(), releases.size());
        List<Integer> codes = presses.stream().map(Event::a).toList();
        assertEquals(List.of(KeyEvent.VK_SHIFT, KeyEvent.VK_H, KeyEvent.VK_I, KeyEvent.VK_SHIFT, KeyEvent.VK_1), codes);
        assertEquals("H", String.valueOf(presses.get(1).ch()));
        assertTrue(e.keyboard().pressedKeys().isEmpty());
        // inter-key gap bounds
        TimingProfile gap = e.timing().get(TimingProfiles.KEY_GAP);
        double g = (presses.get(2).nanos() - releases.get(0).nanos()) / 1e9; // H released ... i pressed (shift released in between)
        assertTrue(g >= 0 && g <= gap.max() + 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void typingRejectsUntypeableCharactersUpFront() {
        engine(new RecordingBackend(clock), null).keyboard().type("naïve");
    }

    @Test
    public void keyCameraRotationConvergesDespiteRateError() {
        for (double err : new double[]{0.8, 1.0, 1.25}) {
            SimulatedCamera cam = new SimulatedCamera(clock, 100, 200, err);
            RecordingBackend rec = new RecordingBackend(clock, cam);
            InteractionEngine e = engine(rec, cam);
            CameraController.Result r = e.camera().rotateTo(1500, 300, CameraController.Mode.KEYS).await();
            assertTrue("rateError " + err + " -> " + r, r.withinTolerance());
            assertTrue(Math.abs(CameraModelDelta.yaw(cam.yaw(), 1500)) <= 24);
            assertTrue(Math.abs(cam.pitch() - 300) <= 12);
            assertTrue(e.keyboard().pressedKeys().isEmpty());
            // yaw and pitch keys were held at the same time at least once
            assertTrue(overlap(rec.events(), KeyEvent.VK_RIGHT, KeyEvent.VK_LEFT, KeyEvent.VK_UP));
        }
    }

    @Test
    public void dragAndDirectCameraModesReachTarget() {
        SimulatedCamera cam = new SimulatedCamera(clock, 2000, 250, 1.1);
        RecordingBackend rec = new RecordingBackend(clock, cam);
        InteractionEngine e = engine(rec, cam);
        CameraController.Result r = e.camera().rotateTo(300, 200, CameraController.Mode.DRAG).await();
        assertTrue(r.toString(), r.withinTolerance());
        assertFalse(e.mouse().isPressed(MouseButton.MIDDLE));
        r = e.camera().rotateTo(1024, 350, CameraController.Mode.DIRECT).await();
        assertEquals(1024, cam.yaw());
        assertEquals(350, cam.pitch());
    }

    @Test
    public void invalidConfiguredProfileIsReportedNotFatal() {
        Config cfg = Config.defaults(InteractionEngine.schema()).with(EngineSettings.DEFAULT_PROFILE, "nope");
        InteractionEngine e = InteractionEngine.builder(clock, new RecordingBackend(clock)).executor(Runnable::run).config(cfg).build();
        assertEquals("standard", e.mouse().defaultProfile());
        assertEquals(1, e.configProblems().size());
    }

    private static boolean overlap(List<Event> ev, int... keys) {
        java.util.Set<Integer> down = new java.util.HashSet<>();
        for (Event x : ev) {
            if (x.type() == Type.KEY_PRESS) down.add(x.a());
            if (x.type() == Type.KEY_RELEASE) down.remove(x.a());
            int count = 0;
            for (int k : keys) if (down.contains(k)) count++;
            if (count >= 2) return true;
        }
        return false;
    }

    /** Small helper so the test reads clearly. */
    private static final class CameraModelDelta {
        static int yaw(int from, int to) {
            return org.bbnors.engine.camera.CameraModel.yawDelta(from, to, 2048);
        }
    }
}
