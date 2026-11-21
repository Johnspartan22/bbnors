# Movement defaults review

Every default in the movement and timing stack, in one of four categories:

- **Derived:** follows from mathematics or a correctness requirement. Changing it breaks a
  stated property.
- **Measured:** chosen from measurements in this repository (accuracy or cost).
- **Engineering choice:** a reasonable setting with no data behind its exact value. Tests
  confirm it works, not that it's optimal.
- **Assumption:** a modelling parameter meant to describe human-like input. **Not calibrated**,
  since no recordings were provided. Every assumption is configurable.

Evaluation evidence is from `docs/evaluation/presets-report.md` (600 paired trials per preset).

## 1. Derived

| Value | Where | Derivation |
|---|---|---|
| Minimum-jerk `s = 10τ³ − 15τ⁴ + 6τ⁵` | `TimingLaws.MINIMUM_JERK` | unique quintic with s(0)=0, s(1)=1 and zero velocity and acceleration at both ends (Flash & Hogan) |
| Smoothstep `3τ² − 2τ³` | `TimingLaws.SMOOTHSTEP` | unique cubic with zero end velocities |
| Beta law normalisation `1/B(a,b)` | `BetaLaw` | progress must reach exactly 1 |
| Beta constraint `a, b ≥ 2` | `BetaLaw`, profile validation | zero end velocity and finite end acceleration; `a, b ≥ 3` additionally gives zero end acceleration |
| Peak position `(a−1)/(a+b−2)`, mapping `a = 1 + peak·(sharpness−2)` | `BetaLaw.fromPeak` | mode of the Beta density; mapping solves for a given peak and a+b |
| Beta(3,3) ≡ minimum-jerk | test | identity verified to 1e-10 |
| Trapezoid `v_max = 1/(1 − t_a/2 − t_d/2)` | `TrapezoidalLaw` | area under velocity must equal 1 |
| 5-point Gauss-Legendre nodes and weights | `ArcLengthTable` | exact for polynomials up to degree 9 |
| Hermite inversion slope `du/ds = 1/|P′(u)|` | `ArcLengthTable` | derivative of the inverse function |
| Catmull-Rom `α = 0.5` (centripetal) | `CatmullRomPath` | the parameterisation proven free of cusps and self-intersections within a segment (Yuksel et al. 2011) |
| Non-uniform Catmull-Rom tangent formula | `CatmullRomPath` | standard derivation for knot spacing t_i |
| Fitts' law **form** `a + b·log2(D/W + 1)` | `Fitts` | Shannon formulation (MacKenzie 1992); the constants are assumptions (§4) |
| Aim point inside target shrunk by 1 px, truncated Gaussian | `EndpointModel` | guarantees the final position is in the target, whatever the spread |
| End of every motion = aim point exactly | `MotionPlanner`, sampler | endpoint precision contract (endpoint error ≈ 1e-13 px in evaluation) |
| Yaw delta wrapped to (−range/2, range/2] | `CameraModel.yawDelta` | shortest rotation |
| Lowest pitch 128 | `CameraModel.minPitch` default | value documented for OSRS-style cameras in DreamBot's `Camera` page |

## 2. Measured

| Value | Evidence |
|---|---|
| Arc-length table: 128 intervals | worst inversion error on extreme random paths: 1.0 px (32), 0.068 px (64), **0.003 px (128)**, 0.0003 px (256); build cost 9.3 µs |
| Intervals aligned to path breakpoints | accuracy test failed before alignment (MOVEMENT_ENGINE §3) |
| `SystemClock` spin tail 0 | 300 µs tail cost ~46 ms CPU per second of playback for ≤0.2 px benefit (PERFORMANCE §3) |
| Playback evaluates the motion at actual elapsed time | `AdverseConditionsTest`: total time ≤ planned + one frame under 15 ms average lateness |

## 3. Engineering choices

| Value | Default | Notes |
|---|---|---|
| Playback / sampling rate | 200 Hz | high enough for smooth cursor updates without wasted work; configurable 10-2000 Hz |
| Duration clamps | 60 ms - 1.5 s | protects against degenerate Fitts values; rarely binds with default constants |
| Overshoot only for distances ≥ 150 px | `overshoot-min-distance` | avoids corrections on tiny moves |
| Correction movement: Fitts a × 0.6, curvature × 0.3, jitter × 0.5 | `MotionPlanner` | short, nearly straight corrective sub-movements (hard-coded; candidate for profile fields) |
| Re-aim corrections after a moving target | 2 | `engine.mouse.max-corrections` |
| Camera tolerance 24 yaw / 12 pitch units, hold factor 0.92, 4 passes | `EngineSettings` | converge under ±25% rate error in tests; approach from below to avoid overshoot |
| Bezier control-point bump `4f(1−f)` | `PathGenerators` | zero offset at the ends, maximum mid-way; keeps departure and arrival near the chord |
| Catmull-Rom curvature scaled × 0.5 | `PathGenerators.catmullRom` | interpolating splines bend more than Bezier for the same offsets; equalises visible deviation (evaluation: median deviation 24.5 px vs 24.9 px for `standard`) |
| WindMouse constants: gravity 9, wind 1 + 8·curvature, step dist/25, target area dist/12, decimation dist/10 | `PathGenerators.wind` | heuristic; see finding §5 |
| Menu row aim: 4 px from the left edge, 3 px from top and bottom, 60% of the row width | `Interactions` | keeps clicks clear of row borders |

## 4. Assumptions (uncalibrated, configurable)

### Movement profiles

| Field | standard | precise | fast | smooth-spline | wind | linear-baseline |
|---|---|---|---|---|---|---|
| `fitts-a` (s) | 0.080 | 0.100 | 0.050 | 0.080 | 0.080 | 0.080 |
| `fitts-b` (s/bit) | 0.110 | 0.130 | 0.085 | 0.110 | 0.110 | 0.110 |
| `duration-jitter` (log-normal σ) | 0.12 | 0.12 | 0.12 | 0.12 | 0.12 | 0 |
| `curvature` | 0.12 | 0.07 | 0.10 | 0.14 | 0.35 (wind strength) | 0 |
| `jitter` | 0.35 | 0.25 | 0.40 | 0.35 | 0.35 | 0 |
| `peak` | 0.42 | 0.36 | 0.45 | (min-jerk: 0.5) | 0.42 | (linear) |
| `sharpness` (a+b) | 7 | 8 | 9 | — | 7 | — |
| `endpoint-spread` | 0.35 | 0.22 | 0.45 | 0.35 | 0.35 | 0 |
| `overshoot-chance` | 0.15 | 0.08 | 0.22 | 0 | 0 | 0 |
| `overshoot-fraction` | 0.06 | 0.06 | 0.05 | 0.06 | 0.06 | 0.06 |
| `correction-pause` (s) | 0.045 | 0.045 | 0.045 | 0.045 | 0.045 | 0.045 |

Rationale, not evidence: the Fitts constants sit in the range commonly reported for mouse
pointing, and peaks before 0.5 model the long deceleration phase usually described for aimed
movements. Both come from background literature, not measurements made here, so treat them as
starting points.

Other built-in assumptions: overshoot distance × U(0.6, 1.4) with lateral spread
0.35 × overshoot; correction pause × LogNormal(1, 0.25); Bezier control points shift along the
chord by `jitter × 0.08` standard deviations.

### Timing profiles

All ten defaults in `TimingProfiles` (click hold 85 ms median, key gap 90 ms, reaction 250 ms,
…; table in TIMING_ENGINE.md) are assumptions.

### Camera model

`yawSpeed` 1024 units/s, `pitchSpeed` 512 units/s, `maxPitch` 383, drag factors −4 / 2 units per
pixel, arrow-key bindings: assumptions that **must be measured per client** (the simulator
encodes the same values, so simulator tests can't detect a mismatch).

## 5. Findings from the evaluation

| Finding | Evidence | Status |
|---|---|---|
| Fitted Fitts slopes are 9-13% above configured for profiles with overshoot | standard 0.124 vs 0.110; precise 0.141 vs 0.130; fast 0.096 vs 0.085 | expected: corrections and jitter add time; documented |
| Without randomness the evaluator recovers configured constants exactly | `linear-baseline` 0.080 / 0.110, R² = 1.00; unit test to 1e-9 | validates the evaluator |
| `wind` is straighter than `standard` | median straightness 0.996 vs 0.982; median max deviation 7.5 px vs 24.9 px | **open**: decimation (dist/10) and spline fitting remove most of the wobble; either retune or rename the preset (ROADMAP) |
| Peak-speed time tracks `peak` | standard 0.419 (configured 0.42), precise 0.360 (0.36), fast 0.448 (0.45) | the beta mapping behaves as derived |
| `smooth-spline` peaks at exactly 0.5 | 0.500 [0.494-0.505] | minimum-jerk as expected |
| Aim offset scales with `endpoint-spread` | precise 0.233, standard 0.370, fast 0.458 half-widths (median) | as intended |

## 6. Calibrating the assumptions

1. Record your own cursor data (CSV of time, x, y and the clicked target).
2. Fit `fitts-a`/`fitts-b` by regressing duration on ID; set `peak` from the median time of peak
   speed; `curvature` from the median max deviation / distance; `endpoint-spread` from click
   scatter.
3. Write a profile file, then compare with `bbnors-lab evaluate --profiles-file my.properties
   --profiles mine,standard`.
4. The evaluator's fitted constants for the new profile should match the recording's.

Tooling for step 1-2 is on the roadmap.
