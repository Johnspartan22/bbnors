package org.bbnors.examples;

import org.bbnors.api.script.ScriptContext;
import org.bbnors.api.script.ScriptManifest;
import org.bbnors.api.script.ScriptRegistry;
import org.bbnors.api.script.ScriptRunner;
import org.bbnors.api.script.TaskScript;
import org.bbnors.api.script.TreeScript;
import org.bbnors.api.sim.Simulation;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The code samples in docs/SCRIPTING.md, compiled and run so the documentation cannot drift from
 * the API. Keep these in sync with the doc text.
 */
public class DocSnippetsTest {

    @ScriptManifest(id = "fisher", name = "Fisher", version = "1.0", category = "skilling",
            description = "Nets shrimp and drops them.")
    public static final class Fisher extends TaskScript {
        public Fisher() { add(new Drop(), new Fish()); }

        static final class Drop implements Task {
            public String name() { return "drop"; }
            public int priority() { return 10; }
            public boolean accept(ScriptContext c) { return c.inventory().isFull(); }
            public int execute(ScriptContext c) { c.inventory().dropAll("Raw shrimps"); return 300; }
        }

        static final class Fish implements Task {
            public String name() { return "fish"; }
            public boolean accept(ScriptContext c) { return c.player().idle(); }
            public int execute(ScriptContext c) {
                c.npcs().name("Fishing spot").action("Net").nearest()
                        .ifPresent(spot -> c.interact(spot, "Net"));
                return 600;
            }
        }
    }

    public static final class Banker extends TreeScript {
        private final Node root = branch("root", c -> true,
                branch("bank", c -> c.inventory().isFull(),
                        leaf("open", c -> !c.bank().isOpen(), c -> c.bank().open() ? 200 : 1000),
                        leaf("deposit", c -> true, c -> { c.bank().depositInventory(); return 300; })),
                leaf("work", c -> 600));

        @Override protected Node root() { return root; }
    }

    @Test
    public void scriptingGuideSamplesRunOnTheSimulator() {
        ScriptRegistry registry = new ScriptRegistry();
        registry.register(Fisher.class, Fisher::new);
        registry.register(Banker.class, Banker::new);
        assertEquals(java.util.List.of("banker", "fisher"), registry.ids());

        // The simulator has no fishing spots: the script must idle cleanly, not fail.
        Simulation sim = Simulation.create(1);
        ScriptRunner fisher = sim.runner(registry.create("fisher"));
        assertEquals(ScriptRunner.State.STOPPED, fisher.runInline(20));
        assertEquals(0, fisher.errors());

        Simulation sim2 = Simulation.create(2);
        TreeScript banker = (TreeScript) registry.create("banker");
        ScriptRunner r = sim2.runner(banker);
        assertEquals(ScriptRunner.State.STOPPED, r.runInline(5));
        assertTrue(banker.status(), banker.status().startsWith("root > work"));
    }
}
