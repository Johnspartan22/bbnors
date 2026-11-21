# Benchmark results

Environment: `{java=OpenJDK 64-Bit Server VM 21.0.13.0.3, os=Linux amd64, cpus=2, max_heap_mb=247, gc=[Copy, MarkSweepCompact]}`

| Group | Benchmark | median | p10 | p90 | alloc/op | Note |
|---|---|---:|---:|---:|---:|---|
| motion | `plan/fast` | 10.92 µs | 10.62 µs | 11.45 µs | 4.1 KB | planner.plan(): aim point, Fitts duration, geometry, arc-length table |
| motion | `plan/linear-baseline` | 5.25 µs | 5.16 µs | 5.57 µs | 3.2 KB | planner.plan(): aim point, Fitts duration, geometry, arc-length table |
| motion | `plan/precise` | 9.63 µs | 9.53 µs | 9.76 µs | 3.8 KB | planner.plan(): aim point, Fitts duration, geometry, arc-length table |
| motion | `plan/smooth-spline` | 12.77 µs | 12.36 µs | 13.12 µs | 3.9 KB | planner.plan(): aim point, Fitts duration, geometry, arc-length table |
| motion | `plan/standard` | 11.08 µs | 10.86 µs | 11.68 µs | 4.1 KB | planner.plan(): aim point, Fitts duration, geometry, arc-length table |
| motion | `plan/wind` | 16.85 µs | 16.53 µs | 17.10 µs | 6.2 KB | planner.plan(): aim point, Fitts duration, geometry, arc-length table |
| motion | `arc-length/build-32` | 2.47 µs | 2.42 µs | 2.49 µs | 992 B | ArcLengthTable for a cubic Bezier, 32 intervals |
| motion | `arc-length/build-64` | 4.82 µs | 4.70 µs | 4.89 µs | 1.7 KB | ArcLengthTable for a cubic Bezier, 64 intervals |
| motion | `arc-length/build-128` | 9.49 µs | 9.33 µs | 9.58 µs | 3.2 KB | ArcLengthTable for a cubic Bezier, 128 intervals |
| motion | `state/evaluate` | 179.3 ns | 178.3 ns | 183.8 ns | 24 B | one Motion.state(t): position, velocity, acceleration, curvature |
| motion | `sample/baseline-new-buffer` | 26.79 µs | 26.25 µs | 27.06 µs | 10.8 KB | MotionSampler.sample at 200 Hz into a fresh buffer |
| motion | `sample/optimized-reused-buffer` | 25.58 µs | 24.94 µs | 25.98 µs | 120 B | same, reusing one TrajectorySamples |
| path | `astar/baseline-reference` | 1.03 ms | 795.22 µs | 3.12 ms | 739.0 KB | textbook A* (HashMap/PriorityQueue), ~45-tile diagonal, 22% obstacles |
| path | `astar/optimized-grid` | 313.11 µs | 297.81 µs | 321.84 µs | 356.3 KB | GridPathFinder (primitive arrays, generation stamps, indexed heap), same maps |
| path | `astar/optimized-grid-open` | 3.26 µs | 3.20 µs | 3.34 µs | 1.6 KB | GridPathFinder, open field, 40 tiles |
| state | `query/baseline-uncached` | 1.68 µs | 1.64 µs | 1.72 µs | 1.7 KB | 5 object queries per op straight from the client (list copied each call) |
| state | `query/optimized-tick-cached` | 1.75 µs | 1.71 µs | 1.79 µs | 5.4 KB | same 5 queries through GameState (one fetch per game tick) |
| engine | `manager/submit-sync` | 199.1 ns | 195.8 ns | 211.4 ns | 352 B | submit+run+complete a no-op operation (synchronous executor) |
| engine | `bus/publish-no-subscribers` | 2.2 ns | 2.1 ns | 2.5 ns | 0 B | EventBus.publish with no subscribers |
| engine | `bus/publish-3-subscribers` | 27.2 ns | 26.7 ns | 27.8 ns | 16 B | EventBus.publish with 3 subscribers |

## End-to-end simulation

- scenario: woodcutter, 400 script loops, seed 2024 (deterministic)
- simulated_seconds: 215.4
- wall_ms: 40.6
- speedup_vs_real_time: 5306
- input_events: 2691
- logs_chopped: 61
- items_banked: 54
- mean_loop_ms_wall: 0.097
