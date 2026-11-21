# Testing

```bash
./gradlew test            # all modules
./gradlew :bbnors-motion:test
```

**71 tests, all passing** at the last commit. Engine concurrency tests were also run 5× in a row
to check for flakiness. 53 tests (core, motion, engine) run without any game backend.

## Inventory

| Module | Class | Tests | What it covers |
|---|---:|---:|---|
| core | `CoreTest` | 8 | config parsing/validation/fallback, schema docs, event bus (supertypes, failure isolation, unsubscribe), RNG reproducibility and distribution moments, manual clock, geometry, ring-buffer log |
| motion | `PathAndTimingTest` | 9 | Bezier vs de Casteljau (degrees 1-10, 1e-7 px); analytic vs finite-difference P′, P″ for all path types; Catmull-Rom interpolation and C1 joins; arc-length accuracy and inversion (≤0.01 px); timing-law normalisation, monotonicity and derivatives; boundary conditions; beta(3,3) ≡ minimum-jerk; beta peak placement; Fitts values |
| motion | `MotionTest` | 10 | kinematic consistency (v, a vs finite differences, ∫v = L); every preset × 300 random cases: in-target, zero endpoint error, start exactness, duration bounds, sample continuity, no NaN; determinism; overshoot continuity; Fitts duration; stationary case; sampler end time; profile codec round trip and error reporting; invalid beta rejection; beta peaks earlier than minimum-jerk |
| engine | `InputEngineTest` | 7 | playback rate and tick grid, no duplicate events, in-target end; move+click ordering and hold bounds; typing with SHIFT and full release; untypeable characters rejected; camera KEYS convergence under ±25% rate error with simultaneous keys; DRAG and DIRECT modes; config problem reporting |
| engine | `InteractionManagerTest` | 9 | same-channel serialisation; disjoint channels concurrent; preempt lower priority; no preempt of higher priority; reject; reservation (no overtaking); cancel queued; cancelled key hold releases keys; failure propagation and channel release |
| engine | `TimingEngineTest` | 8 | wait success/timeout; `stableFor`; `resetWhile`; event wait without polling; manual scheduler ordering and fixed rate; real-time scheduler punctuality; timing-profile bounds and properties overrides |
| engine | `AdverseConditionsTest` | 2 | every profile under 0/4/12 ms scheduling jitter: in-target, ordered events; slow machine skips frames instead of stretching movements |
| api | `PathFinderTest` | 3 | optimized A* equals reference cost on 200 random maps with legal steps; no corner cutting, adjacent goals; unreachable + reuse safety |
| api | `SimulationIntegrationTest` | 7 | bank open/close via menu or left click; walking through a fence gap; equip from inventory; tick cache hit/miss accounting; full woodcutter script deterministic across runs with every press released; runner FAILED after repeated errors; threaded runner stop |
| lab | `LabTest` | 5 | headless rendering with series colours present; palette cap enforced; export artefacts; JSON escaping; harness + full benchmark suite smoke run |
| examples | `ExamplesTest` | 3 | woodcutter banks across 3 seeds; chicken fighter (moving targets) attacks and loots across 2 seeds; shipped example config files load with zero problems |

## Scenario coverage (requirement: "different conditions")

| Condition | Where |
|---|---|
| Every movement profile, random start/target sizes | `MotionTest`, `AdverseConditionsTest` |
| Scheduling jitter / slow machine | `AdverseConditionsTest` (jittery clock) |
| Camera rate miscalibration (0.8×, 1.0×, 1.25×) | `InputEngineTest` |
| Moving targets | `ChickenFighter` example test (wandering NPCs) |
| Off-screen targets requiring camera + walking | woodcutter scenarios |
| Concurrency, preemption, cancellation | `InteractionManagerTest` |
| Script errors, stop from another thread | `SimulationIntegrationTest` |
| Multiple seeds | examples, integration tests |

## Notes

- Tests run in headless AWT mode; Swing UI interaction is verified manually (see
  MOVEMENT_VISUALIZATION.md); the renderer itself is tested.
- Timing-sensitive tests use virtual clocks; only `InteractionManagerTest`,
  `realTimeSchedulerFiresOnTime` and the threaded-runner test use real time, with generous
  bounds.
