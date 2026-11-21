# Implementation log

Newest last. Times are America/New_York. Test counts are cumulative and were run, not estimated.

---

## 2026-11-20 · Phase 1: repository inspection

- **Finding:** the repository was empty (no commits, no files). No existing code to preserve, no
  build to keep working.
- **Decisions:** Java 21 (the OSRS/RSPS tooling ecosystem is Java, and `java.awt` gives input
  injection and rendering with no dependencies); Gradle multi-project with wrapper 8.14.3; zero
  third-party runtime dependencies; JUnit 4 for tests.
- **Environment constraint:** Maven Central was unreachable from the development sandbox. Tests
  were run against the JUnit/hamcrest jars bundled with the local Gradle installation through an
  init script kept *outside* the repository; the committed build uses `mavenCentral()` normally.

## 2026-11-20 · Phase 2: research

- Read DreamBot javadoc pages for `Sleep`, `TreeScript`, `Camera`, `Walking`, `MethodProvider`,
  `EntityDestination`, and OsrsBot's `MouseHandler`. Powerbot was searched but not reviewed; this
  is stated in RESEARCH.md rather than inferred.
- No gameplay footage was provided; none is analysed.

## 2026-11-20 · Commit 94c2b68: core + movement mathematics

- **Created:** `bbnors-core` (Vec2, Rect, Clock/SystemClock/ManualClock, Rng, EventBus, Setting/
  ConfigSchema/Config, Log); `bbnors-motion` (Path, LinearPath, BezierPath, CatmullRomPath,
  ArcLengthTable, TimingLaw(s), BetaLaw, TrapezoidalLaw, Motion, PathMotion, SequenceMotion,
  MotionState, Fitts, EndpointModel, PathGenerators, MovementProfile(s), ProfileCodec,
  MotionPlanner, MotionSampler, TrajectorySamples, MotionMetrics).
- **Reasoning:** ADR-1/2/3 (ARCHITECTURE.md).
- **Bugs found by tests and fixed before commit:**
  - Catmull-Rom with a uniform per-segment parameter was only G1, not C1. Fixed by allocating the
    global parameter by knot length.
  - Arc-length intervals straddling segment joins lost accuracy. Fixed by aligning intervals to
    `Path.breakpoints()`.
  - Measured inversion error by table size (1.0 / 0.068 / 0.003 px at 32 / 64 / 128 intervals) and
    picked 128 as the default.
- **Tests:** 27 (core 8, motion 19), passing.

## 2026-11-20 · Commit 8014f75: interaction engine

- **Created:** `bbnors-engine`: InputBackend (+ AWT component, Robot, recording), MouseButton,
  KeyMap; TimingProfile(s), Waits, TaskScheduler (real-time, manual); Channel, ConflictPolicy,
  Operation, OpContext, OpHandle, InteractionEvent, InteractionManager; MouseController,
  KeyboardController, CameraModel, CameraController, SimulatedCamera; EngineSettings,
  InteractionEngine.
- **Reasoning:** ADR-4/5/6.
- **Fixed during development:** a test wrongly assumed the first mouse event lands on the first
  tick. With bell-shaped velocity the first pixel change comes later. The test now asserts events
  lie on the tick grid.
- **Tests:** +24 → 51; engine tests run 5× consecutively with no flake.

## 2026-11-20 · Scope clarification

- The game client was clarified as the official OSRS client (modified to point at a private
  server). Adapters that hook, inject into, reflect over or read the memory of that client are
  **out of scope**, because they would work unchanged against the live game. Everything else
  continues; documented in ARCHITECTURE.md §2 and GAME_CLIENT.md.

## 2026-11-20 · Commit 2968aae: scripting API, pathfinding, simulator

- **Created:** `bbnors-api`: model (Tile, Area, Entity, Actor, Npc, Player, GameObject,
  GroundItem, Item, MenuEntry, MenuModel, Widget, InterfaceLayout, CollisionMap, GameClient),
  EntityQuery, GridPathFinder, ReferencePathFinder, GameState, Interactions, CameraActions,
  Walking, Inventory, Bank, Equipment, Widgets, Script, TaskScript, TreeScript, ScriptContext,
  ScriptRunner, ScriptSettings, SimulationClock, SimulatedGame, Simulation.
  `GameClient.extension(Class)` added for backend-specific capabilities.
  `AdverseConditionsTest` (jittery clock) added to the engine.
- **Framework bugs found by the integration test:**
  - `Walking` asked for a path *onto* a blocked tile (a tree), so no path existed. It now targets
    an adjacent tile for blocked destinations, which is correct for any backend.
  - `Bank.open()` failed when the bank was off screen. As a high-level manager it now walks there
    first. Low-level `interact()` stays walk-free.
- **Tests:** +12 → 63.

## 2026-11-20 · Commit df27935: lab, benchmarks, examples

- **Created:** `bbnors-lab` (TrajectoryRun, ChartRenderer, LabWindow, Exporter, Json, LabMain,
  bench/Harness, bench/BenchmarkSuite); `bbnors-examples` (Woodcutter, ChickenFighter,
  RunExample).
- **Palette:** first three categorical slots validated for all-pairs CVD separation with the
  dataviz validator; the aqua contrast warning is handled by direct labels and the metrics table.
- **Visual inspection fixes:** overlapping direct labels, x-tick/unit collision, invisible sample
  spacing (now a dot every 25 ms), cold-JIT planning time in the table (now warm).
- **Tuning from inspection:** `fast` overshoot fraction 0.08 → 0.05.
- **Benchmark-driven changes (PERFORMANCE.md):**
  - Run 1 showed the tick cache was slower and allocated more than direct access. Cause:
    per-entity streams in `EntityQuery.name` and `Entity.hasAction`, now replaced by loops.
  - Run 1 artefacts: boxed `Long` in the A* benchmark map, boxed `double` return in
    `state/evaluate`. Fixed in the harness inputs.
  - Clock spin study: default spin tail 300 µs → 0 (45.5 → 4.6 ms CPU per second of playback).
- **Tests:** +7 → 70.

## 2026-11-20 · Documentation and configuration

- **Created:** README.md, docs/ARCHITECTURE.md, GAME_CLIENT.md, RESEARCH.md, API_COMPARISON.md,
  MOVEMENT_ENGINE.md, MOVEMENT_VISUALIZATION.md, TIMING_ENGINE.md, PERFORMANCE.md, TESTING.md,
  ROADMAP.md, CONFIGURATION.md (generated by `bbnors-lab config-docs`), docs/benchmarks/ (three
  recorded runs), docs/images/ (rendered comparisons), config/*.example.properties.
- **Doc fix found by re-measuring:** the 32-interval arc-length error had been written from memory
  as 0.38 px. Re-measured at 1.0 px and corrected.
- **Test added:** shipped example configuration loads with zero problems → **71 tests**.
- **Known limitations / outstanding work:** see ROADMAP.md.

## 2026-11-20 · Consolidation: inventory, registration, evaluation, documentation

Requested: inventory, commit history, scripting and simulator documentation, maturity status,
review of movement defaults, independent profile evaluation, developer guide, no server bridge
until environment and authorization are confirmed, no rewrites of working components.

- **Created:**
  - `bbnors-api`: `script/ScriptManifest`, `script/ScriptRegistry`; test `ScriptRegistryTest`.
  - `bbnors-lab`: `ProfileEvaluator`, `EvaluationRenderer`; `evaluate` CLI command; **Evaluate**
    button in `LabWindow`.
  - `bbnors-engine` test: `AwtComponentBackendTest`.
  - `bbnors-examples`: `META-INF/services/org.bbnors.api.script.Script`; tests `DocSnippetsTest`,
    discovery test.
  - `tools/api-inventory.sh`, `tools/commit-history.sh`.
  - Docs: MODULES, API_INVENTORY (generated), COMMIT_HISTORY (generated), SCRIPTING, SIMULATOR,
    STATUS, MOVEMENT_DEFAULTS, DEVELOPER_GUIDE, `docs/evaluation/presets-report.{md,json}`, two
    evaluation figures.
- **Modified (additive only):**
  - `AwtComponentBackend`: explicit `KeyDelivery` (`DISPATCH` default, unchanged behaviour;
    `LISTENERS` new).
  - `Woodcutter`, `ChickenFighter`: `@ScriptManifest`.
  - `RunExample`: uses the registry; `list` command.
  - `LabMain`: `evaluate` command.
  - Docs: README, ARCHITECTURE (ADR-11, ADR-12), GAME_CLIENT, MOVEMENT_VISUALIZATION, ROADMAP
    (prioritized list; bridge on hold), TESTING.
- **Findings:**
  - Key events dispatched with `Component.dispatchEvent` are retargeted by AWT's
    `KeyboardFocusManager` to the focus owner and dropped when nothing has focus. Documented, and
    the selectable `LISTENERS` delivery was added.
  - `wind` preset is straighter than `standard` (straightness 0.996 vs 0.982). Recorded as open (P3);
    behaviour not changed.
  - Fitted Fitts slopes exceed configured slopes by 9-13% for profiles with overshoot (expected).
  - Per-call timing inside the evaluator was unreliable (12-35 µs for the same work), so it was
    removed; cost belongs to the benchmark harness.
- **Doc corrections found while reviewing against code:** simulator stacking rules; menu-aim
  geometry; evaluation trial counts in STATUS.
- **Tests:** 80 passing (core 8, motion 19, engine 28, api 13, lab 7, examples 5).
- **Not done (by request):** server bridge.
