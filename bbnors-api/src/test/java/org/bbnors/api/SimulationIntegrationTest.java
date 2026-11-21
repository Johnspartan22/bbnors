package org.bbnors.api;

import org.bbnors.api.game.GameState;
import org.bbnors.api.model.GameObject;
import org.bbnors.api.model.Tile;
import org.bbnors.api.script.Script;
import org.bbnors.api.script.ScriptContext;
import org.bbnors.api.script.ScriptRunner;
import org.bbnors.api.script.TaskScript;
import org.bbnors.api.sim.SimulatedGame;
import org.bbnors.api.sim.Simulation;
import org.bbnors.engine.input.RecordingBackend;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** End-to-end: scripts drive the engine, which drives the simulated world. Fully deterministic. */
public class SimulationIntegrationTest {

    @Test
    public void interactOpensBankThroughMenuOrLeftClick() {
        Simulation sim = Simulation.create(3);
        ScriptContext ctx = sim.context();
        assertFalse(ctx.bank().isOpen());
        assertTrue(ctx.bank().open());
        assertTrue(ctx.bank().isOpen());
        assertTrue(ctx.bank().close());
        assertFalse(sim.engine().keyboard().pressedKeys().iterator().hasNext());
    }

    @Test
    public void walkingReachesDestinationThroughFenceGap() {
        Simulation sim = Simulation.create(5);
        ScriptContext ctx = sim.context();
        Tile dest = new Tile(SimulatedGame.BASE_X + 22, SimulatedGame.BASE_Y + 18); // beyond the fence
        assertTrue(ctx.walking().walkTo(dest, 1, Duration.ofMinutes(2)));
        assertTrue(ctx.game().playerTile().steps(dest) <= 1);
    }

    @Test
    public void inventoryEquipAndDrop() {
        Simulation sim = Simulation.create(9);
        ScriptContext ctx = sim.context();
        assertTrue(ctx.inventory().contains("Bronze axe"));
        assertTrue(ctx.equipment().equip("Bronze axe"));
        assertTrue(ctx.equipment().isEquipped("Bronze axe"));
        assertFalse(ctx.inventory().contains("Bronze axe"));
    }

    @Test
    public void tickCacheServesRepeatedQueriesWithinATick() {
        Simulation sim = Simulation.create(1);
        GameState gs = sim.context().game();
        long m0 = gs.cacheMisses();
        for (int i = 0; i < 50; i++) {
            gs.objects().name("Tree").nearest();
        }
        assertEquals("one fetch per tick", m0 + 1, gs.cacheMisses());
        sim.clock().scheduler().advance(SimulatedGame.TICK_NANOS);
        gs.objects().name("Tree").count();
        assertEquals(m0 + 2, gs.cacheMisses());
    }

    /** Minimal woodcutter used as the integration fixture (the full example lives in bbnors-examples). */
    static final class Woodcutter extends TaskScript {
        Woodcutter() {
            add(new Task() {
                public String name() { return "bank"; }
                public int priority() { return 10; }
                public boolean accept(ScriptContext c) { return c.inventory().isFull(); }
                public int execute(ScriptContext c) {
                    if (!c.bank().isOpen()) {
                        c.bank().open();
                        return 300;
                    }
                    c.bank().depositAll("Logs");
                    c.bank().close();
                    return 300;
                }
            }, new Task() {
                public String name() { return "chop"; }
                public boolean accept(ScriptContext c) { return c.player().animation() == -1 && !c.player().moving(); }
                public int execute(ScriptContext c) {
                    Optional<GameObject> tree = c.objects().name("Tree").action("Chop down").nearest();
                    if (tree.isEmpty()) return 600;
                    if (!tree.get().onScreen() && c.player().tile().distance(tree.get().tile()) > 6) {
                        c.walking().walkTo(tree.get().tile(), 4, Duration.ofSeconds(30));
                    }
                    if (c.interact(tree.get(), "Chop down")) {
                        c.waitUntil(() -> c.player().animation() != -1).timeout(Duration.ofSeconds(8))
                                .resetWhile(() -> c.player().moving()).await();
                    }
                    return 600;
                }
            });
        }
    }

    @Test
    public void woodcutterScriptChopsAndBanksDeterministically() {
        int[] banked = new int[2];
        List<Integer> events = new ArrayList<>();
        for (int run = 0; run < 2; run++) {
            Simulation sim = Simulation.create(2024);
            ScriptRunner r = sim.runner(new Woodcutter());
            ScriptRunner.State s = r.runInline(400);
            assertEquals(ScriptRunner.State.STOPPED, s);
            assertEquals(0, r.errors());
            banked[run] = sim.game().itemsBanked();
            events.add(sim.recorder().events().size());
            assertTrue("chopped some logs: " + sim.game().logsChopped(), sim.game().logsChopped() >= 27);
            assertTrue("banked logs: " + sim.game().itemsBanked(), sim.game().itemsBanked() >= 27);
            assertTrue(sim.engine().keyboard().pressedKeys().isEmpty());
            assertTrue(sim.engine().manager().isIdle());
            // every press has a matching release
            assertEquals(sim.recorder().events(RecordingBackend.Type.PRESS).size(), sim.recorder().events(RecordingBackend.Type.RELEASE).size());
        }
        assertEquals("same seed, same run", banked[0], banked[1]);
        assertEquals(events.get(0), events.get(1));
    }

    @Test
    public void runnerStopsAfterRepeatedErrorsAndReleasesInput() {
        Simulation sim = Simulation.create(4);
        ScriptRunner r = sim.runner(new Script() {
            @Override
            public int loop(ScriptContext ctx) {
                throw new IllegalStateException("broken script");
            }
        });
        assertEquals(ScriptRunner.State.FAILED, r.runInline(100));
        assertEquals(5, r.errors());
    }

    @Test
    public void threadedRunnerCanBeStoppedFromOutside() throws Exception {
        Simulation sim = Simulation.create(6);
        ScriptRunner r = sim.runner(ctx -> 50);
        r.start();
        long end = System.nanoTime() + 2_000_000_000L;
        while (r.loops() < 3 && System.nanoTime() < end) Thread.onSpinWait();
        r.stop();
        assertTrue(r.join(2000));
        assertEquals(ScriptRunner.State.STOPPED, r.state());
    }
}
