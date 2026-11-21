# Benchmark results

Environment: `{java=OpenJDK 64-Bit Server VM 21.0.13.0.3, os=Linux amd64, cpus=2, max_heap_mb=247, gc=[Copy, MarkSweepCompact]}`

| Group | Benchmark | median | p10 | p90 | alloc/op | Note |
|---|---|---:|---:|---:|---:|---|
| motion | `plan/fast` | 11.74 µs | 11.08 µs | 13.48 µs | 4.1 KB | planner.plan(): aim point, Fitts duration, geometry, arc-length table |
| motion | `plan/linear-baseline` | 5.44 µs | 5.21 µs | 6.53 µs | 3.2 KB | planner.plan(): aim point, Fitts duration, geometry, arc-length table |
| motion | `plan/precise` | 10.00 µs | 9.71 µs | 11.43 µs | 3.8 KB | planner.plan(): aim point, Fitts duration, geometry, arc-length table |
| motion | `plan/smooth-spline` | 13.54 µs | 13.15 µs | 15.29 µs | 3.9 KB | planner.plan(): aim point, Fitts duration, geometry, arc-length table |
| motion | `plan/standard` | 11.20 µs | 11.13 µs | 11.52 µs | 4.1 KB | planner.plan(): aim point, Fitts duration, geometry, arc-length table |
| motion | `plan/wind` | 16.95 µs | 16.71 µs | 20.94 µs | 6.2 KB | planner.plan(): aim point, Fitts duration, geometry, arc-length table |
| motion | `arc-length/build-32` | 2.44 µs | 2.29 µs | 2.55 µs | 992 B | ArcLengthTable for a cubic Bezier, 32 intervals |
| motion | `arc-length/build-64` | 4.84 µs | 4.62 µs | 5.02 µs | 1.7 KB | ArcLengthTable for a cubic Bezier, 64 intervals |
| motion | `arc-length/build-128` | 9.25 µs | 9.01 µs | 10.86 µs | 3.2 KB | ArcLengthTable for a cubic Bezier, 128 intervals |
| motion | `state/evaluate` | 177.7 ns | 173.1 ns | 186.0 ns | 0 B | one Motion.state(t): position, velocity, acceleration, curvature |
| motion | `sample/baseline-new-buffer` | 28.02 µs | 27.57 µs | 33.29 µs | 10.8 KB | MotionSampler.sample at 200 Hz into a fresh buffer |
| motion | `sample/optimized-reused-buffer` | 26.31 µs | 26.08 µs | 27.50 µs | 120 B | same, reusing one TrajectorySamples |
| path | `astar/baseline-reference` | 1.45 ms | 917.87 µs | 2.03 ms | 290.8 KB | textbook A* (HashMap/PriorityQueue), ~45-tile diagonal, 22% obstacles |
| path | `astar/optimized-grid` | 85.09 µs | 83.96 µs | 86.01 µs | 2.1 KB | GridPathFinder (primitive arrays, generation stamps, indexed heap), same maps |
| path | `astar/optimized-grid-open` | 4.33 µs | 4.12 µs | 4.96 µs | 1.6 KB | GridPathFinder, open field, 40 tiles |
| state | `scene400/baseline-uncached` | 20.96 µs | 19.56 µs | 21.88 µs | 117.4 KB | 5 queries/op; adapter builds 400 wrappers per objects() call |
| state | `scene400/optimized-tick-cached` | 8.57 µs | 8.40 µs | 8.80 µs | 600 B | same 5 queries through GameState (adapter called once per tick) |
| state | `query/baseline-uncached` | 1.76 µs | 1.67 µs | 1.99 µs | 1.7 KB | 5 object queries per op straight from the client (list copied each call) |
| state | `query/optimized-tick-cached` | 1.43 µs | 1.43 µs | 1.52 µs | 760 B | same 5 queries through GameState (one fetch per game tick) |
| engine | `manager/submit-sync` | 208.6 ns | 204.9 ns | 222.8 ns | 352 B | submit+run+complete a no-op operation (synchronous executor) |
| engine | `bus/publish-no-subscribers` | 2.2 ns | 2.1 ns | 2.9 ns | 0 B | EventBus.publish with no subscribers |
| engine | `bus/publish-3-subscribers` | 27.5 ns | 27.1 ns | 28.0 ns | 16 B | EventBus.publish with 3 subscribers |

## End-to-end simulation

- scenario: woodcutter, 400 script loops, seed 2024 (deterministic)
- simulated_seconds: 215.4
- wall_ms: 42.2
- speedup_vs_real_time: 5106
- input_events: 2691
- logs_chopped: 61
- items_banked: 54
- mean_loop_ms_wall: 0.097
- clock_spin_study_200hz: [{spin_tail_us=0, cpu_ms_per_s=7.6, late_p50_us=121, late_p99_us=193, late_max_us=237}, {spin_tail_us=50, cpu_ms_per_s=6.2, late_p50_us=39, late_p99_us=186, late_max_us=910}, {spin_tail_us=300, cpu_ms_per_s=45.7, late_p50_us=1, late_p99_us=48, late_max_us=787}, {spin_tail_us=1000, cpu_ms_per_s=186.3, late_p50_us=0, late_p99_us=5, late_max_us=15}]
