# Research

## 1. Sources and what was actually reviewed

This document separates what was read directly during development from background knowledge.

**Read directly (DreamBot javadocs, dreambot.org/javadocs):**
- `org.dreambot.api.utilities.Sleep`
- `org.dreambot.api.script.frameworks.treebranch.TreeScript`
- `org.dreambot.api.methods.input.Camera`
- `org.dreambot.api.methods.walking.impl.Walking`
- `org.dreambot.api.methods.MethodProvider`
- `org.dreambot.api.input.mouse.destination.impl.EntityDestination`

**Read directly (RSBot-lineage framework):** OsrsBot javadoc, `rsb.internal.MouseHandler`
(osrsb.github.io/OsrsBot).

**Not reviewed directly:** Powerbot/RSBot. Searches were made, but no API pages were read, so
this document makes no claims about Powerbot's API beyond what the OsrsBot (RSBot-derived) page
shows. The full DreamBot package index was not read either; only the classes listed above.

**Background knowledge, not fetched in this session** (cited by name so it can be checked):
Fitts (1954) and MacKenzie's Shannon formulation (1992); Flash & Hogan (1985), minimum-jerk
trajectories; Yuksel, Schaefer & Keyser (2011), centripetal Catmull-Rom parameterisation;
B. Land's WindMouse algorithm.

**Gameplay footage:** none was provided, so no observations are recorded (see §5).

## 2. DreamBot: what the pages show

| Area | Observed API | Assessment |
|---|---|---|
| Waiting | `Sleep.sleepUntil(Condition, long timeout)`, overloads with `polling`, `resetCondition` (timeout restarts while true), and `successfulPolls` (condition must hold N polls in a row); `sleepTick`, `sleepTicks`; static `setDefaultPoll` | Strong ideas (reset condition, consecutive polls, tick-based waits). Returns `boolean` only; static global poll default. **Adopted** as `Waits` (`resetWhile`, `stableFor`) returning a `Result`, on an injected clock |
| Script structure | `TreeScript extends AbstractScript`; owns a `Root` holding `Branch`es that hold `Leaf`s; `onLoop()` returns the root's result: negative stops, otherwise the delay; exposes current branch/leaf names | Good: decision structure and observability. **Adopted** as `TreeScript` (branch/leaf records, status path) and `TaskScript`; same loop-delay contract |
| Camera | All static. Getters for yaw/pitch/zoom/position; `getYawForTile/Entity`; `rotateTo*` plus explicit `keyboardRotate*` and `mouseRotate*` families; `rotateTo*Event` returning a `CameraEvent`; `CameraMode` whose default `MOUSE_OR_KEYBOARD` chooses randomly; lowest pitch 128; zoom roughly 330-3000 depending on position | Rich surface. Static global state makes it hard to test or run more than one instance. **Adopted** mode selection (KEYS / DRAG / DIRECT) and yaw-for-tile; **changed** to an instance controller with closed-loop correction and explicit channels |
| Walking | All static. `walk(Tile)` combines web (global) and local pathfinding with obstacle handling; `walkOnScreen`, `clickTileOnMinimap`, `shouldWalk(distance)`, `canWalk`, run energy/threshold, `getDestination` | Pragmatic high-level API. **Adopted** "click again when close / stopped" loop and minimap stepping; global web walking is out of scope for now (ROADMAP) |
| Interaction | `EntityDestination extends AbstractMouseDestination<Entity>`: `getDestinationShape`, `getSuitablePoint` (Gaussian offset inside the bounding box), `handleCamera`, `handleWalk`, `isVisible`, `verifyPostInteract`, `canInteract(action)` | The destination abstraction, Gaussian aim point and post-interaction verification are good design. **Adopted** as `Supplier<Rect>` live targets, `EndpointModel` (truncated Gaussian), and post-move re-aim corrections |
| Base class | `MethodProvider`: sleep, logging (`log`, `info`, `debug`, `error`) and membership checks | **Changed**: $bbnors passes a `ScriptContext` instead of inheriting a provider, so everything is injectable and mockable |

## 3. OsrsBot (RSBot lineage): `MouseHandler`

The page documents: `fittsLaw(targetDist, targetSize)` for movement time and a `msPerBit`
constant; `reactionTime`; `generateControls` (random spline control points, credited to BenLand100);
`generateSpline` producing points at most one pixel apart; `applyDynamism(spline, msForMove,
msPerMove)`, which thins the spline into timed steps; and a deprecated `moveMouse` overload with
per-axis randomness.

Assessment:
- Fitts-based duration is the right foundation. **Adopted** (`Fitts`, Shannon form, configurable a/b).
- Shape and timing are coupled. Timing comes from dropping spline points, so velocity and
  acceleration are implicit and can't be inspected. **Changed** (ADR-1: path × timing law with
  analytic derivatives).
- The page itself advises varying target size instead of adding random values to Fitts' result.
  $bbnors does both deliberately and configurably (`duration-jitter` is a log-normal factor that can
  be set to 0).

## 4. Interpolation research summary

See MOVEMENT_ENGINE.md §2 and §4 for the evaluated methods and trade-offs: linear, cubic and
higher-order Bezier, Catmull-Rom (uniform/centripetal/chordal), WindMouse-derived geometry, and the
linear, smoothstep, minimum-jerk, beta and trapezoidal timing laws. No single method was assumed
best; all are selectable per profile and comparable side by side in the lab.

## 5. Gameplay footage

No recordings were provided, so there are no footage-based observations. When recordings are
available, the planned workflow is:
1. Extract cursor positions per frame (the cursor is visible in recordings) → CSV.
2. Segment into movements (stationary gaps), then fit duration vs. Fitts ID (→ `fitts-a`,
   `fitts-b`), peak-velocity time (→ `peak`), straightness and deviation (→ `curvature`),
   endpoint scatter (→ `endpoint-spread`).
3. Store each clip as a reproducible test case: start, target, measured metrics, tolerance.
4. Compare in the lab against the fitted profile.

Step 1 needs a frame-extraction tool that isn't built yet (ROADMAP).

## 6. Recommendations implemented

Separate geometry from timing; inject clock and RNG everywhere; channel-based arbitration
instead of static global input helpers; instance-based context instead of an inherited method
provider; result objects from waits; menu-aware interactions with verification; per-tick state
cache in the framework, not adapters.
