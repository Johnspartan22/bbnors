# Developer guide

How to build, extend and keep $bbnors consistent. Read ARCHITECTURE.md first for the module
graph and design decisions.

## 1. Setup

Requirements: JDK 21. Everything else comes through the Gradle wrapper.

```bash
./gradlew build                     # compile with -Werror, run all tests
./gradlew :bbnors-motion:test       # one module
./gradlew :bbnors-lab:run           # interactive lab (needs a display)
./gradlew :bbnors-examples:run --args="list"
```

Compiler settings (root `build.gradle`): `--release 21`, `-Xlint:all` with warnings as errors
(except `serial`, `processing`, `this-escape`). Fix warnings; don't suppress them without a
comment saying why.

## 2. Ground rules

| Rule | Why |
|---|---|
| Never call `System.nanoTime()`, `Thread.sleep` or `Math.random()` in framework or script code; take a `Clock` and an `Rng` | determinism, testability, pause/stop (ADR-3) |
| Operations sleep only through `OpContext`; scripts only through `ctx.sleep` / `ctx.waitUntil` | cancellation works everywhere |
| Release what you press in `finally` | no stuck keys or buttons (ADR-5) |
| No delay literals; add a named `TimingProfile` | timing is configurable and documented in one place |
| `core`, `motion`, `engine` must not import `org.bbnors.api` or anything game-related | backend-agnostic core |
| No simulator-specific code in framework classes | the simulator is a fixture (ADR-8) |
| No new third-party runtime dependencies without an ADR | small, auditable footprint |
| Measure before optimising; record results in PERFORMANCE.md, including negative ones | ADR-10 |
| Update docs in the same commit as the code (see §9) | docs stay true |

Out of scope for contributions: detection-evasion features and "anti-ban" mechanisms
(ARCHITECTURE §2). Official client integration is in progress as a learning experiment.

## 3. Writing a script

Full guide: SCRIPTING.md. Checklist:

1. Extend `TaskScript` (independent prioritised behaviours) or `TreeScript` (nested decisions),
   or implement `Script` directly.
2. Annotate with `@ScriptManifest(id = "...", ...)`.
3. Register: add the class to `META-INF/services/org.bbnors.api.script.Script` in your module, or
   call `ScriptRegistry.register(type, factory)`.
4. Use queries + `ctx.interact` + conditional waits; return short delays.
5. Test it on the simulator across several seeds (copy `ExamplesTest`):

```java
for (long seed : new long[]{1, 2, 3}) {
    Simulation sim = Simulation.create(seed);
    ScriptRunner r = sim.runner(new MyScript());
    assertEquals(ScriptRunner.State.STOPPED, r.runInline(300));
    assertEquals(0, r.errors());
    // assert on sim.game() / sim.context() state
}
```

## 4. Adding a game backend

Implement `GameClient` (GAME_CLIENT.md has the contract). Steps:

1. Entity views implementing `Npc`, `Player`, `GameObject`, `GroundItem`, with live
   `screenBounds()` and `valid()`.
2. `MenuModel` (open state, entries with bounds, hover entry), `InterfaceLayout`, `CollisionMap`.
3. A `CameraModel` with **measured** rates and key bindings.
4. An `InputBackend` for your client (often `AwtComponentBackend` or `RobotBackend`).
5. Optional capabilities via `extension(Class)`.
6. A contract test: run the example scripts against your backend in a controlled environment.

Only implement backends for systems you're authorized to integrate with. The server bridge is
on hold until the server environment and authorization are confirmed (ROADMAP).

## 5. Extending movement

| Want | Do |
|---|---|
| New preset | add to `MovementProfiles.presets()` **or** ship a `profile.<name>.*` properties file (no code) |
| New path family | implement `Path` (analytic `eval`, override `breakpoints()` if piecewise), add a generator in `PathGenerators`, a `PathKind` value, and a case in `MotionPlanner.geometry` |
| New velocity profile | implement `TimingLaw` (s, s′, s″ exact; s monotone, s(0)=0, s(1)=1), add a `LawKind` and a case in `MovementProfile` |
| New profile field | builder field + validation, `ProfileCodec.Fields` entry + `apply` mapping, `toProperties`; the codec round-trip test catches omissions |

Every new path or law needs the property tests in `PathAndTimingTest` (analytic vs finite
differences, normalisation, monotonicity) and a lab comparison and evaluation against existing
profiles.

## 6. Extending the engine

- **New input action:** build it as an `Operation` (or `Operation.of(...)`) declaring its channels;
  put reusable steps in the controller as `doX(OpContext, ...)` so other operations can compose
  them (as `moveAndClick` composes `doMove` + `doClick`).
- **New timing profile:** add a constant and default in `TimingProfiles`; document it in
  TIMING_ENGINE.md.
- **New setting:** add a `Setting` constant to the relevant `*Settings` class; it appears in
  `config-docs` automatically. Regenerate CONFIGURATION.md.

## 7. Adding a module

1. Create `bbnors-<name>/build.gradle` with a `description` and only the dependencies it needs.
   Respect the direction core → motion → engine → api → (lab, examples).
2. Add it to `settings.gradle`.
3. Package `org.bbnors.<name>`.
4. Tests under `src/test/java`. Use virtual clocks; use real time only when testing real time.
5. Add it to MODULES.md, `tools/api-inventory.sh` (module list) and the README table.

## 8. Testing and benchmarking

- Virtual time: `ManualClock` (unit), `SimulationClock`/`Simulation` (integration),
  `executor(Runnable::run)` for synchronous engine execution.
- Scenario tests for "different conditions": see the patterns in `AdverseConditionsTest` (jittery
  clock), camera rate-error tests, and multi-seed example tests.
- Benchmarks: add cases to `BenchmarkSuite` with fixed seeds; for an optimization, add a
  `baseline` / `optimized` pair; run `./gradlew :bbnors-lab:bench` and record the result.
- Movement changes: run `bbnors-lab evaluate` before and after, and keep the report.

## 9. Documentation duties

| Change | Update |
|---|---|
| any public API | `tools/api-inventory.sh > docs/API_INVENTORY.md` |
| any commit | IMPLEMENTATION_LOG.md entry; after committing, `tools/commit-history.sh > docs/COMMIT_HISTORY.md` |
| settings / profiles / timing defaults | `bbnors-lab config-docs` → CONFIGURATION.md; MOVEMENT_DEFAULTS.md |
| performance work | PERFORMANCE.md + `docs/benchmarks/` |
| component maturity | STATUS.md |
| scripting API | SCRIPTING.md **and** `DocSnippetsTest` (the guide's samples are compiled and run) |
