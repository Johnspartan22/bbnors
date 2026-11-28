# Architecture

$bbnors is a modular interaction-simulation and automation framework. Its core (movement
mathematics, timing, input, camera, interaction state, visualization) is independent of any game
client; game access goes through one documented adapter interface, `GameClient`.

## 1. Module graph

```
bbnors-core      geometry, Clock (system/manual), Rng, EventBus, typed Config, Log
   ▲
bbnors-motion    paths, arc length, timing laws, Fitts, planner, profiles, sampler, metrics   (pure math)
   ▲
bbnors-engine    InteractionManager, Mouse/Keyboard/Camera controllers, TimingProfiles, Waits,
   ▲             schedulers, InputBackend (AWT component / Robot / recording)                 (no game types)
bbnors-api       GameClient SPI, GameState cache, queries, managers, pathfinding,
   ▲             script runtime, SimulatedGame reference backend
   ├── bbnors-lab       visualizer, headless renderer/exporter, benchmark suite
   └── bbnors-examples  example scripts (woodcutter, chicken fighter)
```

Dependencies point one way. `core`, `motion` and `engine` never import anything game-related;
they are tested without any backend (55 of the 80 tests). Every module has zero third-party
runtime dependencies; tests use JUnit 4.

## 2. Scope boundary

**This is a learning experiment.** The project is being adapted to work with the official OSRS
client to learn how automation frameworks interface with real game clients.

| In scope | Out of scope |
|---|---|
| Movement mathematics, timing, input, camera, interaction state | Anti-cheat bypasses, detection evasion, mechanisms to conceal automation |
| `GameClient` adapter interface + simulated reference backend | "Anti-ban" features whose purpose is to make automation harder for operators to detect |
| Official client adapter (learning experiment) | |
| Input delivery to a component/window you control (`AwtComponentBackend`, `RobotBackend`) | |
| Optional server-side bridge for a server you operate (roadmap) | |

Game state reaches $bbnors through `GameClient` implementations: the included simulator for
deterministic tests and development, and an in-progress adapter for the official client. The
project is an educational exercise in understanding game-client integration, not a tool for
undetected live-game automation.

## 3. Key components

| Directive component | Implementation |
|---|---|
| Mouse Movement Engine | `engine.mouse.MouseController` (plan → real-time playback → re-aim corrections, clicks, drags, scroll) |
| Trajectory Generation Engine | `motion.MotionPlanner`, `motion.gen.PathGenerators` |
| Interpolation Engine | `motion.path.*` (Bezier any degree, centripetal Catmull-Rom, linear) + `ArcLengthTable` |
| Velocity & Acceleration Engine | `motion.timing.*` (analytic s, s′, s″) composed in `PathMotion` (analytic v, a_t, a_n, κ) |
| Timing & Scheduling Engine | `engine.timing.*` (TimingProfiles, Waits, Real-time/Manual schedulers), `core.time.Clock` |
| Keyboard Input Engine | `engine.keyboard.KeyboardController` |
| Camera Interaction Engine | `engine.camera.CameraController` (+ `CameraModel`, `SimulatedCamera`) |
| Interaction State Manager | `engine.interact.InteractionManager` |
| Movement Visualization Engine | `bbnors-lab` (`LabWindow`, `ChartRenderer`, `Exporter`) |
| Testing & Benchmarking Engine | JUnit suites per module; `lab.bench.*`; `lab.ProfileEvaluator` (batch profile evaluation) |

## 4. Architectural decisions

Each entry: decision, alternatives considered, reason.

**ADR-1: Separate geometry from timing.** A movement = `Path` (where) × `TimingLaw` (when), joined
by an arc-length table. *Alternative:* step-based generators (WindMouse, pixel splines with
per-step sleeps) where shape and speed are entangled. *Reason:* each half is independently
testable and swappable; velocity and acceleration become analytic, not finite differences; any
geometry can be combined with any velocity profile.

**ADR-2: All randomness at planning time, motions immutable.** `MotionPlanner` draws every random
choice from an injected `Rng`; a `Motion` is a pure function of time. *Reason:* the same motion
can be played, visualised, sampled and analysed repeatedly with identical results; every
trajectory is reproducible from a seed.

**ADR-3: Inject the clock everywhere.** No component calls `System.nanoTime()` or `Thread.sleep`
directly. *Reason:* `ManualClock`/`SimulationClock` make playback, waits, schedulers and whole
scripts deterministic and much faster than real time (the end-to-end benchmark simulates 215 s in
~42 ms).

**ADR-4: Channels, not a global input lock.** Input resources are `MOUSE`, `KEYBOARD`, `CAMERA`;
operations declare what they need. Disjoint operations run concurrently (keyboard camera rotation
while the mouse travels); conflicts queue, preempt or reject by policy; queued operations reserve
their channels so multi-channel work is not starved. *Alternative:* one input lock (simple, but
serialises everything) or none (races). 

**ADR-5: Cooperative cancellation + guaranteed release.** Operations sleep only through
`OpContext`, which converts interrupts into cancellation; every operation that presses a key or
button releases it in `finally`; `ScriptRunner` calls `stopAll()` on every exit path.
*Reason:* a stopped script can never leave a key or button held down.

**ADR-6: Real-time playback evaluates at actual elapsed time.** Ticks are scheduled on absolute
deadlines, but the position sent is the motion at the time the thread actually woke. *Reason:* a
late thread skips frames instead of stretching the movement; measured in
`AdverseConditionsTest`.

**ADR-7: Small adapter SPI + per-tick cache in the framework.** `GameClient` is minimal; caching
lives in `GameState`, keyed on the game tick, so adapters stay simple and can never serve stale
data. Backend-specific capabilities go through `GameClient.extension(Class)` instead of growing the
core interface.

**ADR-8: The simulator is a fixture, not the design centre.** `SimulatedGame` implements the same
public interfaces any backend would. Framework code has no `instanceof SimulatedGame` and no
simulator-specific hooks; behaviours the simulator happened to expose (e.g. blocked destination
tiles) were fixed in the framework generally (`Walking.path` targets an adjacent tile).

**ADR-9: One renderer for interactive and exported views.** `ChartRenderer` draws with Java2D and
is used by the Swing lab and the PNG exporter, so exports are exactly what was inspected.

**ADR-10: Measure before optimising; record negative results.** See `PERFORMANCE.md`. Examples:
the playback spin-wait default was removed after measurement; planning (~11 µs per movement) was
deliberately *not* optimised because it is ~0.02% of a movement's duration.

**ADR-11: Script registration through `ServiceLoader`.** Scripts are discovered from
`META-INF/services/org.bbnors.api.script.Script` and described by an optional `@ScriptManifest`.
*Alternatives:* classpath scanning (needs a library or fragile custom code), a hand-maintained
registry class (couples every script to one file). *Reason:* standard JDK mechanism, no
dependency, works for scripts in separate jars; discovery reads only class metadata and
`create(id)` builds a fresh instance per run.

**ADR-12: Evaluate profiles on paired scenarios.** `ProfileEvaluator` runs every profile on the
same seeded scenarios and validates itself (exact Fitts recovery with randomness disabled).
Computational cost is measured only by the benchmark harness, never inside analysis loops.

## 5. Threading model

| Thread | Runs |
|---|---|
| Script thread (`ScriptRunner.start`) or caller (`runInline`) | script loop, managers, waits |
| Operation workers (virtual threads by default) | one per running operation |
| `RealTimeScheduler` thread | timers (parks until the next due task) |
| Swing EDT | lab only |

Locks are never held while operation code runs or events are published. `EventBus` dispatch is
synchronous on the publisher's thread. For deterministic runs, `InteractionEngine.builder(...)
.executor(Runnable::run)` executes operations inline.

## 6. Configuration

Typed `Setting<T>` constants live next to the code that uses them and are collected into a
`ConfigSchema`; invalid values fall back to defaults and are reported, never thrown. Movement and
timing profiles load from `profile.*` / `timing.*` properties. Reference: `CONFIGURATION.md`
(generated by `bbnors-lab config-docs`).
