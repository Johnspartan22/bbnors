# Component status

**Production-ready** means: complete for its documented contract, covered by automated tests
including edge cases, documented, exercised in integration scenarios, no known defects.
**Beta** means it works and is tested, but only against the simulator or a narrow environment, or
has known gaps. **Experimental** means it works but its behaviour or defaults still need
validation. "Production-ready" refers to engineering maturity within this framework, not to any
particular deployment.

## Production-ready

| Component | Evidence |
|---|---|
| core: `Clock`s, `Rng`, `EventBus`, `Config`/`Setting`/`ConfigSchema`, `Log` | `CoreTest`; used by every module |
| motion: `BezierPath`, `LinearPath`, `ArcLengthTable`, timing laws (`MINIMUM_JERK`, `BetaLaw`, `SMOOTHSTEP`, `TrapezoidalLaw`, `LINEAR`) | analytic vs numeric checks, reference implementations (de Casteljau), measured accuracy |
| motion: `PathMotion`, `SequenceMotion`, `MotionPlanner`, `EndpointModel`, `Fitts`, `MotionSampler`, `MotionMetrics`, `ProfileCodec` | kinematic consistency; 300 random cases per preset in `MotionTest` and 600 paired trials per preset in docs/evaluation: 100% in target, 100% continuous |
| engine: `InteractionManager` (channels, policies, cancellation) | 9 concurrency tests, repeated runs without flakes |
| engine: `MouseController`, `KeyboardController` | playback grid, ordering, hold bounds, release on cancel, scheduling-jitter scenarios |
| engine: `TimingProfiles`, `Waits`, `ManualScheduler`, `RealTimeScheduler` | `TimingEngineTest` |
| engine: `RecordingBackend`, `AwtComponentBackend` (mouse; keys in `LISTENERS` mode) | event-stream tests on a real component |
| api: `GridPathFinder` | equal cost to reference A* on 200 random maps; benchmarked |
| api: `GameState`, `EntityQuery` | cache accounting test; benchmarked |
| api: `ScriptRunner`, `TaskScript`, `TreeScript`, `ScriptRegistry`, `ScriptContext` | lifecycle, error budget, threaded stop, registry tests, examples across seeds |
| lab: `ChartRenderer`, `Exporter`, `ProfileEvaluator`, `EvaluationRenderer`, `bench.*`, CLI | rendering/export/evaluator tests (exact Fitts recovery), suite smoke test |

## Beta

| Component | Why not production-ready |
|---|---|
| `CameraController` | converges under ±25% rate error, but only against `SimulatedCamera`; real clients need calibrated rates, key bindings and drag factors |
| `Interactions`, `Walking`, `Bank`, `Inventory`, `Equipment`, `Widgets`, `CameraActions` | correct against the simulator's menu and interface model; a real backend may differ in menu layout and interface behaviour |
| `AwtComponentBackend` key delivery in `DISPATCH` mode | depends on the target component having keyboard focus (documented); not testable headless |
| `RobotBackend` | needs a display; not covered by automated tests |
| `CatmullRomPath` | correct and tested, but only C1: curvature (normal acceleration) steps at joins |
| `SimulatedGame` | complete as a fixture; limitations in SIMULATOR.md |
| `LabWindow` (Swing UI) | renderer and evaluator are tested; the UI interactions themselves are verified manually, not automatically |

## Experimental

| Component | Open question |
|---|---|
| All movement and timing **defaults** | uncalibrated assumptions; see MOVEMENT_DEFAULTS.md |
| `PathGenerators.wind` / `wind` preset | evaluation shows it is *straighter* than `standard` (median straightness 0.996); the decimation and spline fit remove most of the wobble |
| Overshoot model | plausible structure (overshoot, pause, correction), parameters not validated against data |
| `KeyMap` | US layout only |

## Not implemented

Real game backends; the server bridge (blocked until the server environment and authorization are
confirmed; see ROADMAP); calibration from recordings; footage analysis; long-distance/web
pathfinding; skills, chat and dialogue managers.

## Out of scope

Hooks, injection, reflection or memory reading of the official OSRS client, and anti-cheat or
detection-evasion functionality (ARCHITECTURE §2).
