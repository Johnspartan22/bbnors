# Roadmap

## Status

| Area | Status |
|---|---|
| Core (clock, RNG, events, config, logging) | ✅ complete |
| Movement mathematics (paths, arc length, timing laws, Fitts, planner, profiles) | ✅ complete, tested |
| Interaction engine (manager, mouse, keyboard, camera, timing, schedulers, waits) | ✅ complete, tested |
| Scripting API (context, queries, managers, Task/Tree scripts, runner) | ✅ complete for the current `GameClient` surface |
| Pathfinding | ✅ local A*; ❌ no global/web walker |
| Simulated backend | ✅ movement, NPCs, objects, ground items, inventory, bank, equipment, menus, minimap, camera |
| Visualization lab | ✅ interactive + headless + batch evaluation; ⚠️ Swing UI interactions verified manually |
| Script registration | ✅ `ScriptRegistry` (ServiceLoader + explicit) |
| Benchmarks | ✅ suite, three recorded runs |
| Server bridge backend | ⏸ on hold: needs the target server environment identified and authorization confirmed |
| Footage analysis | ⏳ blocked on recordings |
| Official-client adapter | ⛔ out of scope (ARCHITECTURE §2) |

## Prioritized remaining work

Ordered by value to a reusable framework. None of it requires external systems except P8.

| # | Task | Why | Size |
|---|---|---|---|
| P1 | **CI workflow** (GitHub Actions: `./gradlew build`, upload benchmark/evaluation artefacts) | keeps the 80-test suite and `-Werror` enforced on every push | S |
| P2 | **Calibration tooling**: import cursor recordings (CSV), segment movements, fit Fitts a/b, peak, curvature, spread → profile file + lab comparison | turns every assumption in MOVEMENT_DEFAULTS §4 into a measured value | M |
| P3 | **Resolve the `wind` finding**: retune decimation/wind mapping or rename the preset; re-evaluate | the preset doesn't do what its name says | S |
| P4 | **Promote hard-coded overshoot/correction constants to profile fields** (correction a-factor, curvature, pause σ, overshoot range) | removes the last hidden assumptions in the planner | S |
| P5 | **Velocity-continuous re-planning** (planner accepts an initial velocity) | smooth re-aims at moving targets during, not after, a movement | M |
| P6 | **Backend contract test kit**: reusable tests any `GameClient` implementation must pass (menu semantics, bounds, tick monotonicity, collision) | makes new backends verifiable without the simulator | M |
| P7 | **Simulator scenario breadth**: doors/obstacles, a second plane, stacking rules, dialogue widget, simulated server lag and lost clicks | tests framework behaviour under harder conditions | M |
| P8 | **Server bridge**, only after the server environment is named and authorization to integrate is confirmed; then protocol spec + client adapter + reference plugin | a real backend through the agreed architecture | M-L |
| P9 | **Long-distance pathfinding** (region graph with transports over the local A*) | walking beyond the local window | M |
| P10 | **Optional managers via `extension()`**: skills, chat, dialogue | broader scripting coverage | S each |
| P11 | **Lab**: overlay recorded trajectories, distribution plots (histograms) per metric | richer comparison once recordings exist | S-M |
| P12 | **Automated Swing UI smoke test** (needs a virtual display in CI) | moves `LabWindow` from beta to ready | S |

## Known limitations

- Fitts and timing defaults are assumptions, not calibrated (no data provided yet).
- `KeyMap` covers a US layout only.
- `wind` preset is nearly straight (MOVEMENT_DEFAULTS §5).
- Camera rates, bindings and drag factors are defaults that must be measured per client.
- Catmull-Rom paths are C1 (curvature steps at joins); use Bezier profiles where C2+ matters.
- Benchmarks are not JMH; numbers are machine-specific and noisy on shared hardware.
- `SimulatedGame` is a fixture; its projection is a 2D approximation, not a 3D renderer.
