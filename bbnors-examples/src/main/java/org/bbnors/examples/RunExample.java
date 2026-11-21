package org.bbnors.examples;

import org.bbnors.api.script.Script;
import org.bbnors.api.script.ScriptRegistry;
import org.bbnors.api.script.ScriptRunner;
import org.bbnors.api.sim.Simulation;

/**
 * Runs a registered script against the deterministic simulated server and prints a summary.
 * Scripts are discovered through {@link ScriptRegistry} (META-INF/services).
 * <pre>
 *   RunExample list
 *   RunExample &lt;script-id&gt; [loops] [seed]
 * </pre>
 */
public final class RunExample {

    private RunExample() {
    }

    public static void main(String[] args) {
        ScriptRegistry registry = new ScriptRegistry();
        registry.discover(RunExample.class.getClassLoader());
        String which = args.length > 0 ? args[0] : "woodcutter";
        if (which.equals("list")) {
            registry.list().forEach(System.out::println);
            return;
        }
        int loops = args.length > 1 ? Integer.parseInt(args[1]) : 300;
        long seed = args.length > 2 ? Long.parseLong(args[2]) : 1;
        Script script = registry.create(which);
        Simulation sim = Simulation.create(seed);
        ScriptRunner runner = sim.runner(script);
        long t0 = System.nanoTime();
        ScriptRunner.State end = runner.runInline(loops);
        double wall = (System.nanoTime() - t0) / 1e9;
        System.out.printf("%s: %s after %d loops (%d errors)%n", script.name(), end, runner.loops(), runner.errors());
        System.out.printf("simulated %.1f s in %.3f s wall; %d input events%n", sim.clock().nanos() / 1e9, wall, sim.recorder().events().size());
        System.out.printf("logs chopped %d, items banked %d, inventory %s%n", sim.game().logsChopped(), sim.game().itemsBanked(),
                sim.context().inventory().items().stream().map(i -> i.name()).distinct().toList());
    }
}
