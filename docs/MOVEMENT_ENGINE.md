# Movement engine

Module: `bbnors-motion` (pure math, no threads, no I/O) + `engine.mouse.MouseController`
(real-time playback).

## 1. Model

A movement is a parametric path `P(u)`, `u ∈ [0,1]`, traversed according to a timing law
`s(τ)`, `τ = t/T`, through an arc-length reparameterisation:

```
d(t) = L·s(t/T)          u = arcLength⁻¹(d)          position = P(u)
v(t) = L·s'(t/T)/T       direction = P'(u)/|P'(u)|
a_t  = L·s''(t/T)/T²     κ = (P'×P'')/|P'|³           a_n = κ·v²
```

The timing law therefore controls *distance travelled*, independent of how the curve is
parameterised. Velocity and acceleration are analytic; tests check them against finite
differences of the position (`MotionTest.pathMotionKinematicsAreConsistent`) and check
`∫ v dt = L`.

## 2. Interpolation methods evaluated

| Method | Continuity | Strengths | Weaknesses | Used for |
|---|---|---|---|---|
| Linear | C∞ (straight) | trivial, exact | no curvature; constant-speed version has infinite end acceleration | `linear-baseline` reference only |
| Cubic Bezier | C∞ | smooth, 2 shape controls, cheap | single bend | `standard`, `precise`, `fast` |
| Higher-order Bezier (degree 2-10) | C∞ | S-curves, multiple bends | global control (moving one control point affects all) | configurable `bezier-degree` |
| Catmull-Rom (centripetal, α=0.5) | C1 | interpolates waypoints; centripetal form has no cusps/self-intersections within a segment | curvature jumps at joins (visible as steps in normal acceleration) | `smooth-spline` |
| Catmull-Rom uniform/chordal | C1 | — | uniform can cusp with uneven spacing | available via `alpha` |
| WindMouse-derived | C1 (after spline fit) | organic wobble | shape from a stochastic simulation; original couples shape and speed | `wind` (shape only; timing via law) |

Implementation notes:
- **Bezier** is converted once to power-basis coefficients; position, P′ and P″ come from one
  Horner pass (O(n), no allocation). Verified against de Casteljau for degrees 1-10 to 1e-7 px.
- **Catmull-Rom** segments are converted to Hermite then power basis; the global parameter is the
  normalised knot parameter, which makes `dP/du` continuous across joins (tested).

## 3. Arc length

`ArcLengthTable`: ~128 intervals aligned to path breakpoints, 5-point Gauss-Legendre per
interval, cubic-Hermite inversion using the exact `du/ds = 1/|P′|` at knots (4th order).
Measured worst inversion error on deliberately extreme random control polygons:
1.0 px at 32 intervals, 0.068 px at 64, **0.003 px at 128 (default)**, 0.0003 px at 256. Cost: 9.3 µs per build at 128 (PERFORMANCE.md).

## 4. Timing laws (velocity profiles)

| Law | v(0), v(1) | a(0), a(1) | Peak | Notes |
|---|---|---|---|---|
| linear | 1, 1 | ∞ jump | — | constant speed; baseline only |
| smoothstep | 0, 0 | ±6 (jump) | 0.5 | unbounded jerk at the ends |
| minimum-jerk | 0, 0 | 0, 0 | 0.5 | Flash & Hogan quintic `10τ³−15τ⁴+6τ⁵` |
| beta(a,b) | 0, 0 | 0 if a,b ≥ 3 | (a−1)/(a+b−2) | velocity ∝ τ^(a−1)(1−τ)^(b−1); **beta(3,3) ≡ minimum-jerk** (tested to 1e-10) |
| trapezoidal | 0, 0 | ±a_max (jump) | plateau | comparison point |

The default (`beta`, peak 0.42) gives the asymmetric single-peaked profile: faster acceleration,
longer deceleration. Profiles specify it as `peak` + `sharpness` (= a+b); the builder rejects
combinations with a or b < 2.

## 5. Planning pipeline (`MotionPlanner`)

1. **Aim point** (`EndpointModel`): truncated 2D Gaussian inside the target shrunk by 1 px. The
   final position of every motion is exactly this point, so it always lies inside the target
   (asserted for every preset over 300 random cases each).
2. **Duration**: Fitts' law, Shannon form `MT = a + b·log2(D/W + 1)`, `W` = target min side;
   multiplied by a log-normal factor (`duration-jitter`), clamped to [min, max].
3. **Overshoot** (optional): primary movement aims past the target along the approach; after a
   pause, a short corrective sub-movement (own Fitts duration) lands on the aim point. Composed with
   `SequenceMotion`; continuity across the pause is tested.
4. **Geometry**: generator chosen by `path`.
5. **Timing**: the profile's law.

## 6. Profiles

`MovementProfile` is immutable, validated data (19 fields; see CONFIGURATION.md). Presets:
`standard`, `precise`, `fast`, `smooth-spline`, `wind`, `linear-baseline`. Load or override any
field from properties (`profile.<name>.<field> = value`); unknown fields and invalid values are
reported and skipped.

The Fitts constants and timing defaults are **starting assumptions, not measurements**. Calibrate
them with the lab against recordings of your own input (none were provided in this phase; see
RESEARCH.md §5).

## 7. Playback (`MouseController`)

- Ticks on absolute deadlines (`start + k/rate`), so there is no cumulative drift.
- At each tick the motion is evaluated at the *actual* elapsed time; a late thread skips frames
  rather than stretching the movement (`AdverseConditionsTest`).
- Positions are kept in double precision; integer pixels are sent only when they change.
- After the movement, the target supplier is re-read; up to `max-corrections` re-aims if a moving
  target left the cursor.

## 8. API

```java
OpHandle<Vec2> h = engine.mouse().moveTo(() -> npc.screenBounds(), "precise");
engine.mouse().moveAndClick(target, MouseButton.LEFT).await();

// pure math, no engine:
Motion m = new MotionPlanner().plan(profile, from, target, new Rng(42));
TrajectorySamples s = MotionSampler.sample(m, 200);
MotionMetrics metrics = MotionMetrics.of(s, m.end(), target);
```
