# Performance

All numbers come from `bbnors-lab bench` (`./gradlew :bbnors-lab:bench`), recorded in this
repository under `docs/benchmarks/`. Environment of the recorded runs: OpenJDK 21.0.13, Linux
x86-64, **2 CPUs** (shared cloud sandbox), `-Xms256m -Xmx256m -XX:+UseSerialGC`.

## 1. Method and limitations

- Harness (`lab.bench.Harness`): batch size calibrated to ~60 ms, 5 warm-up + 15 measured
  batches; reports median, p10, p90 per op and allocated bytes per op
  (`ThreadMXBean.getThreadAllocatedBytes`). Results are folded into a sink to prevent
  dead-code elimination.
- Fixed seeds and pre-generated inputs; `baseline`/`optimized` pairs do the same work two ways.
- Not JMH: no fork isolation or CPU pinning. Compare numbers within one run on one machine.
  The sandbox is shared, so absolute numbers can move between runs; see the A* note below.

Three recorded runs:
- `run1-baseline`: first suite run.
- `run2`: after fixing benchmark artifacts and the query allocations (§2), before the clock default change.
- `run3-final`: final code.

## 2. Results (run3-final unless stated)

### Movement

| Benchmark | median | alloc/op |
|---|---:|---:|
| `plan/standard` (full plan: aim, Fitts, Bezier, arc-length) | 11.2 µs | 4.1 KB |
| `plan/linear-baseline` | 5.4 µs | 3.2 KB |
| `plan/wind` (simulation + spline fit) | 17.0 µs | 6.2 KB |
| `arc-length/build-128` | 9.3 µs | 3.2 KB |
| `state/evaluate` (one `Motion.state`) | 178 ns | 0 B |
| `sample` 200 Hz, new buffer → reused buffer | 28.0 → 26.3 µs | 10.8 KB → 120 B |

**Decision: planning is not optimised.** A plan costs ~11 µs, while the shortest movement lasts
60 ms (~0.02%). The arc-length build is ~80% of planning and could be cut with fewer intervals,
but the accuracy table (MOVEMENT_ENGINE.md §3) shows 64 intervals already give 0.07 px worst-case
error on extreme paths. The current cost does not justify the change.

Playback cost: 200 updates/s × 178 ns ≈ 36 µs of computation per second of movement.

**Buffer reuse** removes 99% of sampling allocation but only ~6% of time. It matters only for
bulk analysis (lab, benchmarks), not playback, which does not sample into buffers.

### Pathfinding (A*, ~45-tile diagonal, 22% obstacles, 16 maps)

| | median | alloc/op |
|---|---:|---:|
| `astar/baseline-reference` (HashMap/PriorityQueue/Tile objects) | 233 µs (run2) - 1.45 ms (run3) | 226-291 KB |
| `astar/optimized-grid` (`GridPathFinder`) | **80-85 µs** | **2.1 KB** |
| `astar/optimized-grid-open` (40 tiles, no obstacles) | 4.3 µs | 1.6 KB |

The optimized finder is **2.9× faster in the most favourable run for the baseline** and allocates
~99% less. The reference's median varies 6× between runs because it allocates ~0.25 MB per search
and is GC-bound on a 256 MB serial heap; the grid finder is stable. Both return equal-cost paths
on 200 random maps (`PathFinderTest`).

Run 1 reported 356 KB/op for the *optimized* finder. That was a benchmark artifact: the test map
boxed a `Long` per lookup. Run 2 uses a `boolean[][]` map.

### Game-state cache (`GameState`)

| | median | alloc/op |
|---|---:|---:|
| 400-object scene, adapter builds wrappers per call: uncached | 21.0 µs | 117 KB |
| same, through the per-tick cache | **8.6 µs** | **600 B** |
| simulator (7 objects, cheap adapter): uncached | 1.76 µs | 1.7 KB |
| simulator through the cache | 1.43 µs | 760 B |

Negative result kept on record: in run 1 the cache was **slower** than direct access on the
simulator (1.75 vs 1.68 µs) and allocated 3× more (5.4 KB vs 1.7 KB). The overhead came from
`EntityQuery.name()` and `Entity.hasAction()` creating a stream per entity. Replacing those with
plain loops brought the cached path to 760 B. The cache's real benefit depends on adapter cost;
it is large for adapters that build objects or cross a process boundary per call.

### Engine overhead

| Benchmark | median | alloc/op |
|---|---:|---:|
| `manager/submit-sync` (submit + run + complete a no-op operation) | 209 ns | 352 B |
| `bus/publish`, no subscribers | 2.2 ns | 0 B |
| `bus/publish`, 3 subscribers | 27.5 ns | 16 B |

## 3. Playback clock: spin tail vs CPU (macro, 400 waits on a 200 Hz grid)

| spin tail | CPU ms per s (run2 / run3) | lateness p50 | p99 | max |
|---:|---:|---:|---:|---:|
| **0 (default)** | 4.6 / 7.6 | 89 / 121 µs | 172 / 193 µs | 338 / 237 µs |
| 50 µs | 5.3 / 6.2 | 46 / 39 µs | 131 / 186 µs | 1154 / 910 µs |
| 300 µs (previous default) | 45.5 / 45.7 | 0 / 1 µs | 4 / 48 µs | 27 / 787 µs |
| 1 ms | 186 / 186 | 0 µs | 4-5 µs | 1798 / 15 µs |

**Decision: default spin tail 300 µs → 0.** Playback evaluates the motion at the actual elapsed
time, so ~0.1 ms of lateness moves the cursor by at most ~0.2 px at 2000 px/s, while the 300 µs
tail costs ~6× the CPU. Platforms with coarse timer resolution can opt in with
`SystemClock.withSpinTail(...)`. These figures are Linux; re-run the study on your target OS.

## 4. End-to-end

Deterministic woodcutter scenario (400 script loops, seed 2024): **215.4 simulated seconds in
~42 ms** of wall time (~5,000× real time), 2,691 input events, 61 logs chopped, 54 banked, mean
script loop 0.1 ms. This is what makes whole-script tests cheap enough to run on every build.

## 5. Areas reviewed, no change needed

- `RealTimeScheduler` parks until the next due task: an idle scheduler uses no CPU.
- `EventBus` takes a fast path with no subscribers (2.2 ns, 0 B) and caches type hierarchies.
- `InteractionManager` never holds its lock while operation code runs.
- Mouse playback allocates one small `Vec2` per tick (200/s); not worth removing.

## 6. Reproducing

```bash
./gradlew :bbnors-lab:bench                 # full suite → bench-results/results.{md,json}
./gradlew :bbnors-lab:installDist
bbnors-lab/build/install/bbnors-lab/bin/bbnors-lab bench --quick --out /tmp/b
```
