# Timing and scheduling engine

## Principles

1. **No delay literals in scripts or controllers.** Every pause is drawn from a named
   `TimingProfile` (`mouse.click-hold`, `key.gap`, `action.reaction`, ...).
2. **One injected `Clock`.** `SystemClock` (real), `ManualClock` (virtual, instant sleeps),
   `SimulationClock` (virtual; runs due game ticks while advancing).
3. **Waits are conditions, not sleeps.**

## Components

| Class | Purpose |
|---|---|
| `TimingProfile` | named distribution: fixed / uniform / truncated gaussian / log-normal, always clamped to [min, max] |
| `TimingProfiles` | registry with defaults; `timing.<name>.<field>` properties override; problems reported, not thrown |
| `Waits` | `until(clock, cond).timeout(..).poll(..).stableFor(n).resetWhile(progress).await()` → `Result(satisfied, interrupted, elapsed, polls)`; `forEvent(bus, type, filter, timeout)` with no polling |
| `TaskScheduler` | `schedule`, `scheduleAtFixedRate` (anchored, no drift), cancellable tasks |
| `RealTimeScheduler` | one daemon thread that parks until the earliest due task |
| `ManualScheduler` | deterministic; runs due tasks on `advance()` with the clock set to each due time |
| `OpContext.sleep*` | the only way operations sleep; converts interrupts into cancellation |

`stableFor` (condition must hold for N consecutive polls) and `resetWhile` (timeout restarts while
progress is made) correspond to DreamBot's `successfulPolls` and `resetCondition` parameters on
`Sleep.sleepUntil` (see RESEARCH.md). $bbnors returns a `Result` instead of a boolean so callers can
distinguish timeout from interruption.

## Default timing profiles

These are assumptions to be calibrated, not measurements:

| Profile | Kind | Median/mean (s) | Spread | Range (s) |
|---|---|---|---|---|
| `mouse.click-hold` | log-normal | 0.085 | 0.25 | 0.040-0.250 |
| `mouse.double-click-gap` | log-normal | 0.110 | 0.20 | 0.060-0.250 |
| `mouse.pre-click` | log-normal | 0.030 | 0.50 | 0.005-0.200 |
| `mouse.scroll-gap` | log-normal | 0.045 | 0.30 | 0.015-0.200 |
| `mouse.menu-read` | log-normal | 0.180 | 0.35 | 0.080-0.700 |
| `key.hold` | log-normal | 0.075 | 0.30 | 0.030-0.200 |
| `key.gap` | log-normal | 0.090 | 0.45 | 0.020-0.500 |
| `key.modifier-lead` | log-normal | 0.040 | 0.30 | 0.015-0.150 |
| `camera.correction-gap` | log-normal | 0.060 | 0.30 | 0.020-0.250 |
| `action.reaction` | log-normal | 0.250 | 0.35 | 0.120-1.200 |

Log-normal is the default family because delays are positive and right-skewed; a symmetric
Gaussian would need heavy clamping.

## Integration with movement and interaction

- Mouse playback uses the operation's clock for its tick deadlines.
- Clicks, typing and camera corrections sample their profiles through the operation's private
  `Rng` stream (split from the engine seed), so a seeded engine is fully reproducible.
- Scripts use `ctx.sleep`, `ctx.sleepProfile(name)` and `ctx.waitUntil(...)`, all of which honour
  pause/stop.

## Playback clock precision vs CPU

Measured in PERFORMANCE.md §3: pure parking (the default) costs ~5-8 ms CPU per second of 200 Hz
playback with ~0.1 ms median wake-up lateness; a 300 µs spin tail removes lateness at ~46 ms/s.
Configurable with `SystemClock.withSpinTail(nanos)`.
