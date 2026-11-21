# $bbnors

A modular interaction-simulation and automation framework in Java 21: movement mathematics,
timing and scheduling, mouse/keyboard/camera input, interaction state management, a
DreamBot-inspired scripting API, and a first-class visualization and benchmarking lab.

The core is backend-agnostic. Game state comes in through one interface, `GameClient`, with a
complete simulated backend for tests and development. The project deliberately does **not**
include hooks into the official OSRS client or any detection-evasion functionality; see
[docs/ARCHITECTURE.md §2](docs/ARCHITECTURE.md).

![Movement comparison](docs/images/compare-laws.png)

## Modules

| Module | Purpose | Game-independent |
|---|---|---|
| `bbnors-core` | clock, RNG, event bus, typed config, logging | ✅ |
| `bbnors-motion` | Bezier / Catmull-Rom / wind paths, arc length, timing laws, Fitts, planner, profiles | ✅ |
| `bbnors-engine` | interaction manager, mouse, keyboard, camera, timing profiles, waits, schedulers | ✅ |
| `bbnors-api` | `GameClient` SPI, queries, managers, pathfinding, script runtime, simulator | uses `GameClient` |
| `bbnors-lab` | Swing visualizer, headless export, benchmark suite | ✅ (plus a simulator benchmark) |
| `bbnors-examples` | example scripts against the simulator | — |

## Quick start

```bash
./gradlew build                                   # compile + 71 tests
./gradlew :bbnors-lab:run                         # interactive movement lab
./gradlew :bbnors-examples:run --args="woodcutter 300 7"
./gradlew :bbnors-lab:bench                       # benchmarks → bench-results/
```

## A script

```java
public final class Woodcutter extends TaskScript {
    public Woodcutter() {
        add(new Task() {                       // bank when full
            public String name() { return "bank"; }
            public int priority() { return 10; }
            public boolean accept(ScriptContext c) { return c.inventory().isFull(); }
            public int execute(ScriptContext c) {
                if (!c.bank().open()) return 1200;
                c.bank().depositAll("Logs");
                c.bank().close();
                return 300;
            }
        }, new Task() {                        // otherwise chop
            public String name() { return "chop"; }
            public boolean accept(ScriptContext c) { return c.player().idle(); }
            public int execute(ScriptContext c) {
                c.objects().name("Tree").action("Chop down").nearest().ifPresent(t -> {
                    if (c.interact(t, "Chop down")) {
                        c.waitUntil(() -> c.player().animation() != -1)
                         .timeout(Duration.ofSeconds(4)).resetWhile(() -> c.player().moving()).await();
                    }
                });
                return 600;
            }
        });
    }
}
```

Run it deterministically: `Simulation.create(42).runner(new Woodcutter()).runInline(500)`.

## Using the movement engine on its own

```java
MovementProfile p = new MovementProfiles().get("precise");
Motion m = new MotionPlanner().plan(p, new Vec2(80, 400), Rect.centered(new Vec2(600, 120), 30, 30), new Rng(7));
TrajectorySamples s = MotionSampler.sample(m, 200);
System.out.println(MotionMetrics.of(s, m.end(), null));   // duration, peak speed, jerk, ...
```

## Documentation

| | |
|---|---|
| [ARCHITECTURE](docs/ARCHITECTURE.md) | modules, scope boundary, decisions (ADRs), threading |
| [GAME_CLIENT](docs/GAME_CLIENT.md) | the adapter interface |
| [MOVEMENT_ENGINE](docs/MOVEMENT_ENGINE.md) | paths, arc length, timing laws, planning, playback |
| [TIMING_ENGINE](docs/TIMING_ENGINE.md) | timing profiles, waits, schedulers |
| [MOVEMENT_VISUALIZATION](docs/MOVEMENT_VISUALIZATION.md) | the lab |
| [PERFORMANCE](docs/PERFORMANCE.md) | benchmarks and the decisions they drove |
| [TESTING](docs/TESTING.md) | test inventory and scenarios |
| [RESEARCH](docs/RESEARCH.md) / [API_COMPARISON](docs/API_COMPARISON.md) | DreamBot / RSBot-lineage review |
| [CONFIGURATION](docs/CONFIGURATION.md) | every setting (generated) |
| [IMPLEMENTATION_LOG](docs/IMPLEMENTATION_LOG.md) / [ROADMAP](docs/ROADMAP.md) | history and next steps |
