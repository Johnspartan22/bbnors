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
| Visualization lab | ✅ interactive + headless; ⚠️ Swing UI verified by rendering, not by automated UI tests |
| Benchmarks | ✅ suite, three recorded runs |
| Server bridge backend | ⏳ planned (below) |
| Footage analysis | ⏳ blocked on recordings |
| Official-client adapter | ⛔ out of scope (ARCHITECTURE §2) |

## Next steps (priority order)

1. **Server bridge backend.** A `GameClient` that receives world state from a plugin on a
   server you operate over a local socket (newline-delimited JSON), plus a protocol spec and a
   reference plugin for your server codebase once it's known.
2. **Calibration tooling.** Import cursor recordings (CSV/frame extraction), fit Fitts a/b, peak
   time, curvature and endpoint spread, and produce a profile plus comparison report in the lab.
3. **Movement continuity across re-plans.** The planner starts every motion at rest; add
   initial-velocity support so a re-aim during motion can blend continuously instead of after
   arrival.
4. **Region/web pathfinding** for long distances (graph of transports and doors over the local A*).
5. **Additional `GameClient` extensions**: skills, chat, dialogues, prayers, as optional
   `extension()` capabilities.
6. **Lab**: overlay a recorded trajectory against generated ones; batch statistics view (N seeds →
   distributions of duration, peak time, deviation).
7. **CI**: GitHub Actions workflow running `./gradlew build` and publishing benchmark artefacts.

## Known limitations

- Fitts and timing defaults are assumptions, not calibrated (no data provided yet).
- `KeyMap` covers a US layout only.
- Catmull-Rom paths are C1 (curvature steps at joins); use Bezier profiles where C2+ matters.
- Benchmarks are not JMH; numbers are machine-specific and noisy on shared hardware.
- `SimulatedGame` is a fixture; its projection is a 2D approximation, not a 3D renderer.
