package org.bbnors.lab.bench;

import org.bbnors.api.game.GameState;
import org.bbnors.api.model.CollisionMap;
import org.bbnors.api.model.GameObject;
import org.bbnors.api.model.Tile;
import org.bbnors.api.path.GridPathFinder;
import org.bbnors.api.path.ReferencePathFinder;
import org.bbnors.api.sim.SimulatedGame;
import org.bbnors.api.sim.Simulation;
import org.bbnors.core.event.EventBus;
import org.bbnors.core.geom.Rect;
import org.bbnors.core.geom.Vec2;
import org.bbnors.core.log.Log;
import org.bbnors.core.random.Rng;
import org.bbnors.core.time.Clock;
import org.bbnors.engine.interact.Channel;
import org.bbnors.engine.interact.InteractionManager;
import org.bbnors.engine.interact.Operation;
import org.bbnors.lab.Json;
import org.bbnors.motion.Motion;
import org.bbnors.motion.MotionPlanner;
import org.bbnors.motion.MotionSampler;
import org.bbnors.motion.MotionState;
import org.bbnors.motion.MovementProfile;
import org.bbnors.motion.MovementProfiles;
import org.bbnors.motion.PathMotion;
import org.bbnors.motion.TrajectorySamples;
import org.bbnors.motion.path.ArcLengthTable;
import org.bbnors.motion.path.BezierPath;
import org.bbnors.motion.timing.TimingLaws;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The repeatable benchmark suite. Every case uses fixed seeds and pre-generated inputs, so runs
 * differ only by machine noise. Pairs named {@code baseline} / {@code optimized} measure the same
 * work done two ways and back the claims in docs/PERFORMANCE.md.
 */
public final class BenchmarkSuite {

    private final Harness h;
    private final List<Harness.Result> results = new ArrayList<>();
    private final Map<String, Object> macro = new LinkedHashMap<>();

    public BenchmarkSuite(Harness h) {
        this.h = h;
    }

    public List<Harness.Result> results() {
        return results;
    }

    public Map<String, Object> macro() {
        return macro;
    }

    public void runAll() {
        List<Log.Sink> quiet = List.of();
        Log.setSinks(quiet); // keep logging out of the measurements
        motion();
        paths();
        gameState();
        engine();
        endToEnd();
        clockStudy();
    }

    // ---------------------------------------------------------------- motion

    private void motion() {
        MotionPlanner planner = new MotionPlanner();
        MovementProfiles profiles = new MovementProfiles();
        Rng r = new Rng(1);
        int n = 1024;
        Vec2[] from = new Vec2[n];
        Rect[] to = new Rect[n];
        for (int i = 0; i < n; i++) {
            from[i] = new Vec2(r.uniform(0, 765), r.uniform(0, 503));
            to[i] = Rect.centered(new Vec2(r.uniform(20, 745), r.uniform(20, 483)), r.uniform(8, 48), r.uniform(8, 48));
        }
        for (MovementProfile p : profiles.all()) {
            Rng pr = new Rng(2);
            results.add(h.run("motion", "plan/" + p.name(), "planner.plan(): aim point, Fitts duration, geometry, arc-length table",
                    i -> planner.plan(p, from[i & (n - 1)], to[i & (n - 1)], pr)));
        }
        BezierPath cubic = new BezierPath(new Vec2(10, 400), new Vec2(200, 300), new Vec2(450, 80), new Vec2(700, 120));
        for (int iv : new int[]{32, 64, 128}) {
            results.add(h.run("motion", "arc-length/build-" + iv, "ArcLengthTable for a cubic Bezier, " + iv + " intervals",
                    i -> new ArcLengthTable(cubic, iv)));
        }
        PathMotion pm = new PathMotion(cubic, TimingLaws.beta(0.42, 7), 0.45);
        MotionState st = new MotionState();
        double[] ts = new double[n];
        for (int i = 0; i < n; i++) ts[i] = r.uniform(0, 0.45);
        results.add(h.run("motion", "state/evaluate", "one Motion.state(t): position, velocity, acceleration, curvature",
                i -> {
                    pm.state(ts[i & (n - 1)], st);
                    return null; // st escapes, so the work cannot be eliminated; avoids boxing a double
                }));
        Motion m = planner.plan(profiles.get("standard"), new Vec2(10, 10), Rect.centered(new Vec2(700, 450), 20, 20), new Rng(3));
        results.add(h.run("motion", "sample/baseline-new-buffer", "MotionSampler.sample at 200 Hz into a fresh buffer",
                i -> MotionSampler.sample(m, 200).size()));
        TrajectorySamples reuse = new TrajectorySamples();
        results.add(h.run("motion", "sample/optimized-reused-buffer", "same, reusing one TrajectorySamples",
                i -> MotionSampler.sample(m, 200, reuse).size()));
    }

    // ---------------------------------------------------------------- pathfinding

    private void paths() {
        Rng r = new Rng(9);
        int maps = 16;
        CollisionMap[] cm = new CollisionMap[maps];
        Tile[] goals = new Tile[maps];
        Tile start = new Tile(0, 0);
        GridPathFinder grid = new GridPathFinder(50);
        for (int k = 0; k < maps; k++) {
            while (true) {
                // boolean[][] map: lookups allocate nothing, so allocation figures belong to the finders
                boolean[][] free = new boolean[101][101];
                for (int x = 0; x <= 100; x++) {
                    for (int y = 0; y <= 100; y++) {
                        free[x][y] = !r.chance(0.22) || (Math.abs(x - 50) <= 1 && Math.abs(y - 50) <= 1);
                    }
                }
                Tile g = new Tile(30 + r.nextInt(15), 30 + r.nextInt(15));
                free[g.x() + 50][g.y() + 50] = true;
                CollisionMap map = (x, y, p) -> x >= -50 && y >= -50 && x <= 50 && y <= 50 && free[x + 50][y + 50];
                if (!grid.find(map, start, g).isEmpty()) {
                    cm[k] = map;
                    goals[k] = g;
                    break;
                }
            }
        }
        ReferencePathFinder ref = new ReferencePathFinder(50);
        results.add(h.run("path", "astar/baseline-reference", "textbook A* (HashMap/PriorityQueue), ~45-tile diagonal, 22% obstacles",
                i -> ref.find(cm[i & (maps - 1)], start, goals[i & (maps - 1)]).size()));
        results.add(h.run("path", "astar/optimized-grid", "GridPathFinder (primitive arrays, generation stamps, indexed heap), same maps",
                i -> grid.find(cm[i & (maps - 1)], start, goals[i & (maps - 1)]).size()));
        CollisionMap open = (x, y, p) -> true;
        results.add(h.run("path", "astar/optimized-grid-open", "GridPathFinder, open field, 40 tiles",
                i -> grid.find(open, start, new Tile(40, 25)).size()));
    }

    // ---------------------------------------------------------------- game state cache

    private void gameState() {
        realisticAdapterCache();
        Simulation sim = Simulation.create(5);
        SimulatedGame game = sim.game();
        GameState cached = sim.context().game();
        Tile me = game.localPlayer().tile();
        // a typical loop asks ~5 questions about the same entity list per tick
        results.add(h.run("state", "query/baseline-uncached", "5 object queries per op straight from the client (list copied each call)",
                i -> {
                    int c = 0;
                    for (int q = 0; q < 5; q++) {
                        Optional<GameObject> t = game.objects().stream().filter(o -> o.name().equals("Tree")).min((a, b) -> Double.compare(a.tile().distance(me), b.tile().distance(me)));
                        c += t.isPresent() ? 1 : 0;
                    }
                    return c;
                }));
        results.add(h.run("state", "query/optimized-tick-cached", "same 5 queries through GameState (one fetch per game tick)",
                i -> {
                    int c = 0;
                    for (int q = 0; q < 5; q++) {
                        c += cached.objects().name("Tree").nearest().isPresent() ? 1 : 0;
                    }
                    return c;
                }));
    }

    /**
     * Real adapters usually build wrapper objects for every entity on every call (and a networked
     * backend pays a round trip). This client exposes a 400-object scene that way.
     */
    private void realisticAdapterCache() {
        Simulation sim = Simulation.create(5);
        SimulatedGame game = sim.game();
        Rng r = new Rng(4);
        int n = 400;
        int[] xs = new int[n];
        int[] ys = new int[n];
        String[] names = {"Tree", "Rock", "Door", "Fence", "Bush", "Oak", "Willow", "Crate"};
        int[] kinds = new int[n];
        for (int i = 0; i < n; i++) {
            xs[i] = SimulatedGame.BASE_X + r.nextInt(40);
            ys[i] = SimulatedGame.BASE_Y + r.nextInt(30);
            kinds[i] = r.nextInt(names.length);
        }
        record Obj(int id, String name, Tile tile, List<String> actions) implements GameObject {
            public Rect screenBounds() { return null; }
            public boolean valid() { return true; }
        }
        List<String> acts = List.of("Chop down", "Examine");
        org.bbnors.api.model.GameClient wrapping = (org.bbnors.api.model.GameClient) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{org.bbnors.api.model.GameClient.class}, (proxy, m, args) -> {
                    if (m.getName().equals("objects")) {
                        List<GameObject> out = new ArrayList<>(n);
                        for (int i = 0; i < n; i++) out.add(new Obj(i, names[kinds[i]], new Tile(xs[i], ys[i]), acts));
                        return out;
                    }
                    return m.invoke(game, args);
                });
        GameState cached = new GameState(wrapping);
        Tile me = game.localPlayer().tile();
        results.add(h.run("state", "scene400/baseline-uncached", "5 queries/op; adapter builds 400 wrappers per objects() call",
                i -> {
                    int c = 0;
                    for (int q = 0; q < 5; q++) {
                        GameObject best = null;
                        double bd = Double.MAX_VALUE;
                        for (GameObject o : wrapping.objects()) {
                            if (o.name().equalsIgnoreCase("Tree") && o.tile().distance(me) < bd) {
                                bd = o.tile().distance(me);
                                best = o;
                            }
                        }
                        c += best != null ? 1 : 0;
                    }
                    return c;
                }));
        results.add(h.run("state", "scene400/optimized-tick-cached", "same 5 queries through GameState (adapter called once per tick)",
                i -> {
                    int c = 0;
                    for (int q = 0; q < 5; q++) {
                        c += cached.objects().name("Tree").nearest().isPresent() ? 1 : 0;
                    }
                    return c;
                }));
    }

    // ---------------------------------------------------------------- playback clock

    /**
     * Spin-tail study: 400 waits on a 200 Hz deadline grid (2 s of playback) per setting. Reports
     * thread CPU time per second of playback and wake-up lateness. This is a macro measurement
     * (real sleeps), so it is reported separately from the micro table.
     */
    public void clockStudy() {
        com.sun.management.ThreadMXBean tb = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        List<Object> rows = new ArrayList<>();
        for (long spin : new long[]{0, 50_000, 300_000, 1_000_000}) {
            org.bbnors.core.time.SystemClock c = org.bbnors.core.time.SystemClock.withSpinTail(spin);
            int ticks = 400;
            long period = 5_000_000L;
            long[] late = new long[ticks];
            long cpu0 = tb.getCurrentThreadCpuTime();
            long start = c.nanos();
            try {
                for (int k = 1; k <= ticks; k++) {
                    long deadline = start + k * period;
                    c.sleepUntil(deadline);
                    late[k - 1] = c.nanos() - deadline;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            long cpu = tb.getCurrentThreadCpuTime() - cpu0;
            double wall = (c.nanos() - start) / 1e9;
            java.util.Arrays.sort(late);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("spin_tail_us", spin / 1000);
            row.put("cpu_ms_per_s", Math.round(cpu / 1e6 / wall * 10) / 10.0);
            row.put("late_p50_us", late[ticks / 2] / 1000);
            row.put("late_p99_us", late[(int) (ticks * 0.99)] / 1000);
            row.put("late_max_us", late[ticks - 1] / 1000);
            rows.add(row);
        }
        macro.put("clock_spin_study_200hz", rows);
    }

    // ---------------------------------------------------------------- engine overhead

    private void engine() {
        EventBus quietBus = new EventBus();
        InteractionManager sync = new InteractionManager(Clock.system(), new Rng(1), quietBus, Runnable::run);
        Operation<Integer> noop = Operation.of("noop", EnumSet.of(Channel.MOUSE), ctx -> 1);
        results.add(h.run("engine", "manager/submit-sync", "submit+run+complete a no-op operation (synchronous executor)",
                i -> sync.submit(noop).future().join()));
        EventBus bus = new EventBus();
        results.add(h.run("engine", "bus/publish-no-subscribers", "EventBus.publish with no subscribers", i -> {
            bus.publish(Integer.valueOf(i & 127));
            return null;
        }));
        EventBus bus3 = new EventBus();
        int[] counter = new int[1];
        for (int k = 0; k < 3; k++) bus3.subscribe(Integer.class, x -> counter[0]++);
        results.add(h.run("engine", "bus/publish-3-subscribers", "EventBus.publish with 3 subscribers", i -> {
            bus3.publish(Integer.valueOf(i & 127));
            return counter[0];
        }));
        sync.close();
    }

    // ---------------------------------------------------------------- end to end

    private void endToEnd() {
        Simulation sim = Simulation.create(2024);
        org.bbnors.api.script.ScriptRunner runner = sim.runner(new BenchWoodcutter());
        long t0 = System.nanoTime();
        runner.runInline(400);
        long wall = System.nanoTime() - t0;
        double simSeconds = sim.clock().nanos() / 1e9;
        macro.put("scenario", "woodcutter, 400 script loops, seed 2024 (deterministic)");
        macro.put("simulated_seconds", Math.round(simSeconds * 10) / 10.0);
        macro.put("wall_ms", Math.round(wall / 1e5) / 10.0);
        macro.put("speedup_vs_real_time", Math.round(simSeconds / (wall / 1e9)));
        macro.put("input_events", sim.recorder().events().size());
        macro.put("logs_chopped", sim.game().logsChopped());
        macro.put("items_banked", sim.game().itemsBanked());
        macro.put("mean_loop_ms_wall", Math.round(runner.meanLoopMillis() * 1000) / 1000.0);
    }

    /** Same behaviour as the example woodcutter; duplicated so the suite has no examples dependency. */
    static final class BenchWoodcutter extends org.bbnors.api.script.TaskScript {
        BenchWoodcutter() {
            add(new Task() {
                public String name() { return "bank"; }
                public int priority() { return 10; }
                public boolean accept(org.bbnors.api.script.ScriptContext c) { return c.inventory().isFull(); }
                public int execute(org.bbnors.api.script.ScriptContext c) {
                    if (!c.bank().isOpen()) { c.bank().open(); return 300; }
                    c.bank().depositAll("Logs");
                    c.bank().close();
                    return 300;
                }
            }, new Task() {
                public String name() { return "chop"; }
                public boolean accept(org.bbnors.api.script.ScriptContext c) { return c.player().animation() == -1 && !c.player().moving(); }
                public int execute(org.bbnors.api.script.ScriptContext c) {
                    var tree = c.objects().name("Tree").action("Chop down").nearest();
                    if (tree.isEmpty()) return 600;
                    if (!tree.get().onScreen() && c.player().tile().distance(tree.get().tile()) > 6) {
                        c.walking().walkTo(tree.get().tile(), 4, java.time.Duration.ofSeconds(30));
                    }
                    if (c.interact(tree.get(), "Chop down")) {
                        c.waitUntil(() -> c.player().animation() != -1).timeout(java.time.Duration.ofSeconds(8)).resetWhile(() -> c.player().moving()).await();
                    }
                    return 600;
                }
            });
        }
    }

    public void write(Path dir) throws IOException {
        Files.createDirectories(dir);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("timestamp", Instant.now().toString());
        out.put("environment", Harness.environment());
        out.put("micro", results.stream().map(Harness.Result::asMap).toList());
        out.put("macro", macro);
        Files.writeString(dir.resolve("results.json"), Json.write(out) + "\n", StandardCharsets.UTF_8);
        StringBuilder md = new StringBuilder("# Benchmark results\n\n");
        md.append("Environment: `").append(Harness.environment()).append("`\n\n");
        md.append(Harness.markdown(results)).append("\n## End-to-end simulation\n\n");
        macro.forEach((k, v) -> md.append("- ").append(k).append(": ").append(v).append('\n'));
        Files.writeString(dir.resolve("results.md"), md.toString(), StandardCharsets.UTF_8);
    }
}
