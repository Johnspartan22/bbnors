package org.bbnors.engine;

import org.bbnors.core.config.Config;
import org.bbnors.core.config.ConfigSchema;
import org.bbnors.core.event.EventBus;
import org.bbnors.core.geom.Vec2;
import org.bbnors.core.log.Log;
import org.bbnors.core.random.Rng;
import org.bbnors.core.time.Clock;
import org.bbnors.engine.camera.CameraController;
import org.bbnors.engine.camera.CameraModel;
import org.bbnors.engine.input.InputBackend;
import org.bbnors.engine.interact.InteractionManager;
import org.bbnors.engine.keyboard.KeyboardController;
import org.bbnors.engine.mouse.MouseController;
import org.bbnors.engine.timing.TimingProfiles;
import org.bbnors.engine.timing.Waits;
import org.bbnors.motion.MotionPlanner;
import org.bbnors.motion.MovementProfiles;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;

/**
 * Facade that wires the interaction subsystems together:
 * <pre>
 *   InteractionManager (channels, queueing, cancellation, events)
 *     ├── MouseController    ── MotionPlanner + MovementProfiles
 *     ├── KeyboardController
 *     └── CameraController   ── CameraModel (from the game adapter)
 *   TimingProfiles, Waits, Clock, Rng, EventBus are shared by all of them.
 * </pre>
 * Nothing here knows about a specific game client: input leaves through the
 * {@link InputBackend}, camera state comes in through the {@link CameraModel}.
 */
public final class InteractionEngine implements AutoCloseable {

    private static final Log LOG = Log.get(InteractionEngine.class);

    private final Clock clock;
    private final EventBus bus;
    private final MovementProfiles profiles;
    private final TimingProfiles timing;
    private final MotionPlanner planner;
    private final InteractionManager manager;
    private final MouseController mouse;
    private final KeyboardController keyboard;
    private final CameraController camera;
    private final Config config;
    private final List<String> configProblems;

    private InteractionEngine(Builder b) {
        this.clock = b.clock;
        this.bus = b.bus;
        this.config = b.config;
        this.profiles = new MovementProfiles();
        this.timing = new TimingProfiles();
        this.planner = new MotionPlanner();
        List<String> problems = new ArrayList<>(config.problems());
        String file = config.get(EngineSettings.PROFILES_FILE);
        if (!file.isBlank()) {
            problems.addAll(loadOverrides(Path.of(file)));
        }
        long seed = config.get(EngineSettings.SEED);
        Rng rng = new Rng(seed != 0 ? seed : System.nanoTime() ^ 0x9E3779B97F4A7C15L);
        this.manager = b.executor != null
                ? new InteractionManager(clock, rng, bus, b.executor)
                : new InteractionManager(clock, rng, bus);
        this.mouse = new MouseController(manager, b.backend, planner, profiles, timing, b.initialMouse);
        this.keyboard = new KeyboardController(manager, b.backend, timing);
        String defProfile = config.get(EngineSettings.DEFAULT_PROFILE);
        try {
            mouse.setDefaultProfile(defProfile);
        } catch (IllegalArgumentException e) {
            problems.add(e.getMessage() + " -> using '" + MovementProfiles.DEFAULT + "'");
        }
        mouse.setMaxCorrections(config.get(EngineSettings.MAX_CORRECTIONS));
        if (b.cameraModel != null) {
            camera = new CameraController(manager, b.cameraModel, keyboard, mouse, planner, profiles, timing);
            camera.setTolerance(config.get(EngineSettings.CAMERA_YAW_TOLERANCE), config.get(EngineSettings.CAMERA_PITCH_TOLERANCE));
            camera.setMaxPasses(config.get(EngineSettings.CAMERA_MAX_PASSES));
            camera.setHoldFactor(config.get(EngineSettings.CAMERA_HOLD_FACTOR));
        } else {
            camera = null;
        }
        this.configProblems = List.copyOf(problems);
        for (String p : configProblems) {
            LOG.warn("config: " + p);
        }
    }

    private List<String> loadOverrides(Path file) {
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            p.load(r);
        } catch (IOException e) {
            return List.of("cannot read profiles file " + file + ": " + e.getMessage());
        }
        List<String> out = new ArrayList<>(profiles.load(p));
        out.addAll(timing.load(p));
        return out;
    }

    public static Builder builder(Clock clock, InputBackend backend) {
        return new Builder(clock, backend);
    }

    /** Schema containing every engine setting (for validation and docs). */
    public static ConfigSchema schema() {
        return new ConfigSchema().registerAll(EngineSettings.class);
    }

    public Clock clock() { return clock; }
    public EventBus bus() { return bus; }
    public MovementProfiles profiles() { return profiles; }
    public TimingProfiles timing() { return timing; }
    public MotionPlanner planner() { return planner; }
    public InteractionManager manager() { return manager; }
    public MouseController mouse() { return mouse; }
    public KeyboardController keyboard() { return keyboard; }
    public Config config() { return config; }

    /** Camera controller, or {@code null} if no {@link CameraModel} was supplied. */
    public CameraController camera() { return camera; }

    public CameraController.Mode defaultCameraMode() {
        return config.get(EngineSettings.CAMERA_MODE);
    }

    /** Problems found while applying configuration (also logged at WARN). */
    public List<String> configProblems() { return configProblems; }

    /** Conditional wait on this engine's clock. */
    public Waits.Builder waitUntil(BooleanSupplier condition) {
        return Waits.until(clock, condition);
    }

    /** Cancels all operations and releases every held key and button. */
    public void stopAll() {
        manager.cancelAll();
        mouse.releaseAll();
        keyboard.releaseAll();
    }

    @Override
    public void close() {
        stopAll();
        manager.close();
    }

    /** Builder; only clock and backend are required. */
    public static final class Builder {
        private final Clock clock;
        private final InputBackend backend;
        private EventBus bus = new EventBus();
        private Config config = Config.defaults(schema());
        private CameraModel cameraModel;
        private Executor executor;
        private Vec2 initialMouse = new Vec2(0, 0);

        private Builder(Clock clock, InputBackend backend) {
            this.clock = Objects.requireNonNull(clock);
            this.backend = Objects.requireNonNull(backend);
        }

        public Builder bus(EventBus v) { bus = Objects.requireNonNull(v); return this; }
        public Builder config(Config v) { config = Objects.requireNonNull(v); return this; }
        public Builder camera(CameraModel v) { cameraModel = v; return this; }
        /** {@code Runnable::run} = synchronous deterministic execution (tests, simulation). */
        public Builder executor(Executor v) { executor = v; return this; }
        public Builder initialMouse(Vec2 v) { initialMouse = Objects.requireNonNull(v); return this; }

        public InteractionEngine build() {
            return new InteractionEngine(this);
        }
    }
}
