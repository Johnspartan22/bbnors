# Benchmark results

Environment: `{java=OpenJDK 64-Bit Server VM 21.0.13.0.3, os=Linux amd64, cpus=2, max_heap_mb=247, gc=[Copy, MarkSweepCompact]}`

| Group | Benchmark | median | p10 | p90 | alloc/op | Note |
|---|---|---:|---:|---:|---:|---|
| motion | `plan/fast` | 10.96 µs | 10.63 µs | 16.01 µs | 4.1 KB | planner.plan(): aim point, Fitts duration, geometry, arc-length table |
| motion | `plan/linear-baseline` | 5.13 µs | 4.99 µs | 5.31 µs | 3.2 KB | planner.plan(): aim point, Fitts duration, geometry, arc-length table |
| motion | `plan/precise` | 10.25 µs | 9.48 µs | 11.88 µs | 3.8 KB | planner.plan(): aim point, Fitts duration, geometry, arc-length table |
| motion | `plan/smooth-spline` | 12.81 µs | 12.45 µs | 14.46 µs | 3.9 KB | planner.plan(): aim point, Fitts duration, geometry, arc-length table |
| motion | `plan/standard` | 11.44 µs | 11.38 µs | 11.72 µs | 4.1 KB | planner.plan(): aim point, Fitts duration, geometry, arc-length table |
| motion | `plan/wind` | 16.61 µs | 15.89 µs | 17.43 µs | 6.2 KB | planner.plan(): aim point, Fitts duration, geometry, arc-length table |
| motion | `arc-length/build-32` | 2.51 µs | 2.46 µs | 3.60 µs | 992 B | ArcLengthTable for a cubic Bezier, 32 intervals |
| motion | `arc-length/build-64` | 4.76 µs | 4.69 µs | 4.84 µs | 1.7 KB | ArcLengthTable for a cubic Bezier, 64 intervals |
| motion | `arc-length/build-128` | 9.41 µs | 9.25 µs | 9.58 µs | 3.2 KB | ArcLengthTable for a cubic Bezier, 128 intervals |
| motion | `state/evaluate` | 176.8 ns | 173.9 ns | 182.6 ns | 0 B | one Motion.state(t): position, velocity, acceleration, curvature |
| motion | `sample/baseline-new-buffer` | 27.44 µs | 26.77 µs | 27.92 µs | 10.8 KB | MotionSampler.sample at 200 Hz into a fresh buffer |
| motion | `sample/optimized-reused-buffer` | 25.65 µs | 24.90 µs | 25.94 µs | 120 B | same, reusing one TrajectorySamples |
| path | `astar/baseline-reference` | 233.37 µs | 230.46 µs | 238.15 µs | 226.1 KB | textbook A* (HashMap/PriorityQueue), ~45-tile diagonal, 22% obstacles |
| path | `astar/optimized-grid` | 80.18 µs | 78.85 µs | 80.57 µs | 2.1 KB | GridPathFinder (primitive arrays, generation stamps, indexed heap), same maps |
| path | `astar/optimized-grid-open` | 4.23 µs | 4.05 µs | 4.70 µs | 1.6 KB | GridPathFinder, open field, 40 tiles |
| state | `scene400/baseline-uncached` | 16.05 µs | 15.22 µs | 16.91 µs | 117.4 KB | 5 queries/op; adapter builds 400 wrappers per objects() call |
| state | `scene400/optimized-tick-cached` | 8.88 µs | 8.76 µs | 9.20 µs | 600 B | same 5 queries through GameState (adapter called once per tick) |
| state | `query/baseline-uncached` | 1.63 µs | 1.61 µs | 1.66 µs | 1.7 KB | 5 object queries per op straight from the client (list copied each call) |
| state | `query/optimized-tick-cached` | 1.39 µs | 1.36 µs | 1.41 µs | 760 B | same 5 queries through GameState (one fetch per game tick) |
| engine | `manager/submit-sync` | 193.0 ns | 189.2 ns | 197.9 ns | 328 B | submit+run+complete a no-op operation (synchronous executor) |
| engine | `bus/publish-no-subscribers` | 2.2 ns | 2.1 ns | 2.3 ns | 0 B | EventBus.publish with no subscribers |
| engine | `bus/publish-3-subscribers` | 26.8 ns | 26.2 ns | 31.9 ns | 16 B | EventBus.publish with 3 subscribers |

## End-to-end simulation

- scenario: woodcutter, 400 script loops, seed 2024 (deterministic)
- simulated_seconds: 215.4
- wall_ms: 42.9
- speedup_vs_real_time: 5016
- input_events: 2691
- logs_chopped: 61
- items_banked: 54
- mean_loop_ms_wall: 0.103
- clock_spin_study_200hz: [{spin_tail_us=0, cpu_ms_per_s=4.6, late_p50_us=89, late_p99_us=172, late_max_us=338}, {spin_tail_us=50, cpu_ms_per_s=5.3, late_p50_us=46, late_p99_us=131, late_max_us=1154}, {spin_tail_us=300, cpu_ms_per_s=45.5, late_p50_us=0, late_p99_us=4, late_max_us=27}, {spin_tail_us=1000, cpu_ms_per_s=186.0, late_p50_us=0, late_p99_us=4, late_max_us=1798}]
