package org.bbnors.examples;

import org.bbnors.api.script.ScriptRunner;
import org.bbnors.api.sim.Simulation;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** The examples are also integration scenarios: they must work across several seeds. */
public class ExamplesTest {

    @Test
    public void woodcutterBanksAcrossSeeds() {
        for (long seed : new long[]{1, 2, 3}) {
            Simulation sim = Simulation.create(seed);
            ScriptRunner r = sim.runner(new Woodcutter());
            assertEquals(ScriptRunner.State.STOPPED, r.runInline(500));
            assertEquals("seed " + seed, 0, r.errors());
            assertTrue("seed " + seed + " banked " + sim.game().itemsBanked(), sim.game().itemsBanked() >= 27);
        }
    }

    @Test
    public void shippedExampleConfigurationLoadsWithoutProblems() throws Exception {
        java.nio.file.Path cfgFile = java.nio.file.Path.of("../config/bbnors.example.properties");
        org.bbnors.core.config.Config cfg = org.bbnors.core.config.Config.load(Simulation.schema(), cfgFile);
        assertTrue(cfg.problems().toString(), cfg.problems().isEmpty());
        java.util.Properties p = new java.util.Properties();
        try (var r = java.nio.file.Files.newBufferedReader(java.nio.file.Path.of("../config/profiles.example.properties"))) {
            p.load(r);
        }
        org.bbnors.motion.MovementProfiles profiles = new org.bbnors.motion.MovementProfiles();
        assertTrue(profiles.load(p).isEmpty());
        assertEquals(5, profiles.get("interface").bezierDegree());
        assertTrue(new org.bbnors.engine.timing.TimingProfiles().load(p).isEmpty());
    }

    @Test
    public void chickenFighterHitsMovingTargetsAndLoots() {
        for (long seed : new long[]{1, 2}) {
            Simulation sim = Simulation.create(seed);
            ScriptRunner r = sim.runner(new ChickenFighter());
            assertEquals(ScriptRunner.State.STOPPED, r.runInline(150));
            assertEquals(0, r.errors());
            assertTrue("seed " + seed + " looted feathers", sim.context().inventory().count("Feather") > 0);
        }
    }
}
