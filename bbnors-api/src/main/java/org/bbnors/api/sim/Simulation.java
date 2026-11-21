package org.bbnors.api.sim;

import org.bbnors.api.script.Script;
import org.bbnors.api.script.ScriptContext;
import org.bbnors.api.script.ScriptRunner;
import org.bbnors.core.config.Config;
import org.bbnors.core.config.ConfigSchema;
import org.bbnors.core.geom.Vec2;
import org.bbnors.core.random.Rng;
import org.bbnors.engine.EngineSettings;
import org.bbnors.engine.InteractionEngine;
import org.bbnors.engine.input.RecordingBackend;

/**
 * One-call wiring of a deterministic simulation: virtual clock + simulated world + interaction
 * engine (synchronous executor) + script context. Every input event is recorded.
 *
 * <pre>{@code
 *   Simulation sim = Simulation.create(42);
 *   ScriptRunner r = sim.runner(new MyScript());
 *   r.runInline(500);
 *   sim.game().logsChopped();
 * }</pre>
 */
public final class Simulation {

    private final SimulationClock clock;
    private final SimulatedGame game;
    private final RecordingBackend recorder;
    private final InteractionEngine engine;
    private final ScriptContext context;
    private final Config config;

    private Simulation(long seed, Config config) {
        this.config = config;
        this.clock = new SimulationClock();
        this.game = new SimulatedGame(clock, seed);
        game.attach(clock.scheduler());
        this.recorder = new RecordingBackend(clock, game);
        this.engine = InteractionEngine.builder(clock, recorder)
                .camera(game.camera())
                .executor(Runnable::run)
                .config(config.with(EngineSettings.SEED, seed == 0 ? 1 : seed))
                .initialMouse(new Vec2(380, 250))
                .build();
        this.context = new ScriptContext(game, engine, new Rng(seed ^ 0x5DEECE66DL), "sim");
    }

    public static Simulation create(long seed) {
        return new Simulation(seed, Config.defaults(schema()));
    }

    public static Simulation create(long seed, Config config) {
        return new Simulation(seed, config);
    }

    /** Engine + script settings. */
    public static ConfigSchema schema() {
        return InteractionEngine.schema().registerAll(org.bbnors.api.script.ScriptSettings.class);
    }

    public ScriptRunner runner(Script script) {
        return new ScriptRunner(script, context, config);
    }

    public SimulationClock clock() { return clock; }
    public SimulatedGame game() { return game; }
    public RecordingBackend recorder() { return recorder; }
    public InteractionEngine engine() { return engine; }
    public ScriptContext context() { return context; }
}
