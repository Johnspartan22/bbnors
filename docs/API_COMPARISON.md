# API comparison

DreamBot columns are limited to the pages listed in RESEARCH.md §1. "—" means *not reviewed*,
not "absent".

| Concern | DreamBot (reviewed pages) | RSBot lineage (OsrsBot `MouseHandler`) | $bbnors |
|---|---|---|---|
| Script entry | `AbstractScript` / `TreeScript`; `onLoop()` returns delay, negative stops | — | `Script.loop(ctx)` same contract; `TaskScript`, `TreeScript`; `ScriptRunner` state machine (pause/resume/stop, error budget, always releases input) |
| Access to subsystems | static classes (`Camera`, `Walking`, `Sleep`); `MethodProvider` base | static helpers | instance `ScriptContext` (`ctx.bank()`, `ctx.walking()`...), all injectable |
| Conditional wait | `Sleep.sleepUntil(cond, timeout[, poll])`, `resetCondition`, `successfulPolls`, tick sleeps | — | `ctx.waitUntil(cond).timeout().poll().resetWhile().stableFor().await()` → `Result`; `Waits.forEvent` (no polling) |
| Movement duration | — | `fittsLaw(dist, size)`, `msPerBit` | `Fitts` (Shannon form), per-profile a/b, log-normal jitter, clamps |
| Path shape | — | random control points → pixel spline | Bezier (any degree), centripetal Catmull-Rom, WindMouse-derived, linear |
| Velocity profile | — | implicit (point thinning) | explicit laws: minimum-jerk, beta(a,b), smoothstep, trapezoidal, linear; analytic v/a |
| Aim point | `getSuitablePoint()`: Gaussian offset in bounding box | — | `EndpointModel`: truncated Gaussian, guaranteed inside target |
| Moving targets | destination object + `verifyPostInteract` | — | `Supplier<Rect>` re-read after movement, up to N corrective re-aims |
| Camera | static; `rotateTo*`, `keyboardRotate*`, `mouseRotate*`, `CameraEvent`s; random mode by default | — | `CameraController` KEYS / DRAG / DIRECT, simultaneous yaw+pitch keys, closed-loop correction, tolerance and passes configurable |
| Walking | `walk(Tile)` web + local pathfinding, minimap, run energy | — | `Walking` (A* + minimap stepping, re-click when close/stopped); no web walker yet |
| Input arbitration | — | — | `InteractionManager`: channels, queue/preempt/reject, priorities, reservation, cancellation, events |
| Determinism / testing | — | — | injected clock + RNG; whole scripts run on a virtual clock (~5,000× real time) |
| Visualization | — | — | first-class lab: trajectories, velocity, acceleration, metrics, replay, export |
| Game access | built into the client | built into the client | `GameClient` SPI; simulator included; official-client hooks deliberately not provided |

## Where DreamBot's design was kept

- `onLoop` returning the next delay: simple and well understood.
- Tree/branch/leaf scripts with named current nodes, for status overlays.
- `resetCondition` and `successfulPolls` on waits: both solve real flakiness.
- Gaussian aim points and destination objects that know how to verify themselves.
- Separate keyboard and mouse camera rotation.

## Where $bbnors differs, and why

- **Instances instead of statics**: testability, multiple engines per JVM, no hidden global state.
- **Explicit velocity profiles**: inspectable, testable, comparable.
- **Channel arbitration**: concurrent camera + mouse without races.
- **Result objects**: callers can tell a timeout from an interruption.
- **Backend SPI**: the framework is reusable and testable without a client.
