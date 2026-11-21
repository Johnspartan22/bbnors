# Module inventory

Six Gradle modules, Java 21, no third-party runtime dependencies. The complete, generated list of
every public type and member is in [API_INVENTORY.md](API_INVENTORY.md)
(`tools/api-inventory.sh`). This page is the curated map.

```
bbnors-core ◄── bbnors-motion ◄── bbnors-engine ◄── bbnors-api ◄── bbnors-lab
                                                         ▲
                                                         └──────── bbnors-examples
```

## bbnors-core

Source: `bbnors-core/src/main/java/org/bbnors/core` · depends on: nothing

| Package | Public types | Role |
|---|---|---|
| `geom` | `Vec2`, `Rect` | immutable screen-space geometry (double precision) |
| `time` | `Clock`, `SystemClock`, `ManualClock` | injectable time source; real or virtual |
| `random` | `Rng` | seedable RNG: uniform, gaussian, truncated gaussian, log-normal, `split()` |
| `event` | `EventBus`, `EventBus.Subscription` | synchronous type-keyed pub/sub |
| `config` | `Setting<T>`, `ConfigSchema`, `Config` | typed, validated, self-documenting settings |
| `log` | `Log`, `Log.Sink`, `Log.RingBufferSink`, `Log.Record`, `Log.Level` | logging facade |

## bbnors-motion

Source: `bbnors-motion/src/main/java/org/bbnors/motion` · depends on: core · no threads, no I/O

| Package | Public types | Role |
|---|---|---|
| `path` | `Path`, `LinearPath`, `BezierPath`, `CatmullRomPath`, `ArcLengthTable` | parametric geometry with analytic P′, P″; arc-length reparameterisation |
| `timing` | `TimingLaw`, `TimingLaws`, `BetaLaw`, `TrapezoidalLaw` | velocity profiles s(τ), s′, s″ |
| `gen` | `PathGenerators` | seeded stochastic path generators (bezier, catmull-rom, wind, linear) |
| (root) | `Motion`, `PathMotion`, `SequenceMotion`, `MotionState` | time-parameterised movements with exact kinematics |
| (root) | `MotionPlanner`, `Fitts`, `EndpointModel` | trajectory generation: aim point → duration → overshoot → geometry → timing |
| (root) | `MovementProfile` (+`Builder`, `PathKind`, `LawKind`), `MovementProfiles`, `ProfileCodec` | validated profile data, registry, properties codec |
| (root) | `MotionSampler`, `TrajectorySamples`, `MotionMetrics` | sampling and objective measurement |

## bbnors-engine

Source: `bbnors-engine/src/main/java/org/bbnors/engine` · depends on: motion · no game types

| Package | Public types | Role |
|---|---|---|
| (root) | `InteractionEngine` (+`Builder`), `EngineSettings` | facade wiring all controllers; configuration keys |
| `interact` | `InteractionManager`, `Operation<T>`, `OpContext`, `OpHandle<T>`, `Channel`, `ConflictPolicy`, `InteractionEvent`, `ChannelBusyException` | channel arbitration, lifecycle, cancellation |
| `mouse` | `MouseController`, `MouseEvents` | movement playback, clicks, drags, scroll, re-aim |
| `keyboard` | `KeyboardController` | typing, taps, holds, key-state tracking |
| `camera` | `CameraController`, `CameraModel`, `SimulatedCamera` | closed-loop camera rotation (keys / drag / direct) |
| `timing` | `TimingProfile`, `TimingProfiles`, `Waits`, `TaskScheduler`, `RealTimeScheduler`, `ManualScheduler` | delays, conditional waits, scheduling |
| `input` | `InputBackend`, `AwtComponentBackend`, `RobotBackend`, `RecordingBackend`, `MouseButton`, `KeyMap` | input delivery boundary |

## bbnors-api

Source: `bbnors-api/src/main/java/org/bbnors/api` · depends on: engine

| Package | Public types | Role |
|---|---|---|
| `model` | `GameClient`, `Entity`, `Actor`, `Npc`, `Player`, `GameObject`, `GroundItem`, `Item`, `Tile`, `Area`, `MenuModel`, `MenuEntry`, `Widget`, `InterfaceLayout`, `CollisionMap` | the backend SPI and game value types |
| `query` | `EntityQuery<T>` | lazy fluent entity queries |
| `path` | `GridPathFinder`, `ReferencePathFinder` | A* (optimized + reference oracle) |
| `game` | `GameState`, `Interactions`, `Walking`, `Inventory`, `Bank`, `Equipment`, `Widgets`, `CameraActions` | per-tick cache and high-level managers |
| `script` | `Script`, `TaskScript` (+`Task`), `TreeScript` (+`Node`, `Branch`, `Leaf`), `ScriptContext`, `ScriptRunner` (+`State`, `ScriptEvent`), `ScriptRegistry`, `ScriptManifest`, `ScriptSettings` | script model, runtime, registration |
| `sim` | `SimulatedGame`, `SimulationClock`, `Simulation` | deterministic reference backend |

## bbnors-lab

Source: `bbnors-lab/src/main/java/org/bbnors/lab` · depends on: motion, engine, api · main class `org.bbnors.lab.LabMain`

| Type | Role |
|---|---|
| `LabMain` | CLI: `gui`, `render`, `compare`, `evaluate`, `bench`, `config-docs` |
| `LabWindow` | interactive Swing lab |
| `ChartRenderer`, `TrajectoryRun`, `Exporter`, `Json` | single-trajectory comparison and export |
| `ProfileEvaluator`, `EvaluationRenderer` | batch evaluation over paired scenarios |
| `bench.Harness`, `bench.BenchmarkSuite` | micro/macro benchmarks |

## bbnors-examples

Source: `bbnors-examples/src/main/java/org/bbnors/examples` · depends on: api · main class `org.bbnors.examples.RunExample`

| Type | Role |
|---|---|
| `Woodcutter` | `TaskScript` example: chop, walk, bank |
| `ChickenFighter` | `TreeScript` example: moving targets, ground items |
| `RunExample` | runs any registered script against the simulator (`list` to enumerate) |
| `META-INF/services/org.bbnors.api.script.Script` | registration of both examples |

## Supporting directories

| Path | Content |
|---|---|
| `docs/` | documentation; `docs/benchmarks/` recorded runs; `docs/evaluation/` preset evaluation; `docs/images/` figures |
| `config/` | example configuration and profile files (tested to load cleanly) |
| `tools/` | `api-inventory.sh`, `commit-history.sh` doc generators |
| `gradle/`, `gradlew*` | Gradle wrapper 8.14.3 |
