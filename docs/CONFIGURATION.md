# Configuration reference

Generated from the code by `bbnors-lab config-docs`; regenerate after changing any setting.

Settings load from a `.properties` file (`Config.load(schema, path)`). Invalid values fall back to the default and are reported in `Config.problems()` / `InteractionEngine.configProblems()`; unknown keys are reported as likely typos. Movement and timing profiles are read from the file named by `engine.profiles-file` (`profile.*` and `timing.*` keys).

## Engine and script settings

| Key | Type | Default | Allowed | Description |
|---|---|---|---|---|
| `engine.mouse.default-profile` | String | `standard` | text | Movement profile used when a script does not name one. |
| `engine.mouse.max-corrections` | Integer | `2` | [0, 10] | Corrective re-aims after a movement if the (moving) target no longer contains the cursor. |
| `engine.seed` | Long | `0` | [-9223372036854775808, 9223372036854775807] | Random seed; 0 = derive from time (non-reproducible). |
| `engine.camera.mode` | Mode | `keys` | keys | drag | direct | Default camera rotation mode. |
| `engine.camera.yaw-tolerance` | Integer | `24` | [1, 512] | Acceptable final yaw error, client units. |
| `engine.camera.pitch-tolerance` | Integer | `12` | [1, 256] | Acceptable final pitch error, client units. |
| `engine.camera.max-passes` | Integer | `4` | [1, 20] | Maximum rotate/re-measure passes. |
| `engine.camera.hold-factor` | Double | `0.92` | [0.31, 1.2] | Fraction of the computed key-hold / drag distance used per pass (<1 approaches from below). |
| `engine.profiles-file` | String | `` | text | Optional properties file with profile.* and timing.* overrides. |
| `script.max-consecutive-errors` | Integer | `5` | [1, 1000] | Stop the script (state FAILED) after this many loop() exceptions in a row. |
| `script.error-backoff-ms` | Integer | `1000` | [0, 60000] | Delay after a loop() exception before retrying. |
| `script.min-loop-delay-ms` | Integer | `20` | [0, 10000] | Lower bound applied to loop() return values; prevents accidental busy loops. |

## Movement profile fields (`profile.<name>.<field>`)

| Key | Type | Default | Allowed | Description |
|---|---|---|---|---|
| `path` | PathKind | `bezier` | linear | bezier | catmull_rom | wind | Path geometry family. |
| `bezier-degree` | Integer | `3` | [2, 10] | Bezier degree (bezier only). |
| `waypoints` | Integer | `2` | [0, 12] | Interior waypoints (catmull_rom only). |
| `curvature` | Double | `0.12` | [0, 1] | Peak sideways deviation as a fraction of distance (wind: wind strength). |
| `jitter` | Double | `0.35` | [0, 2] | Random variation of control points. |
| `law` | LawKind | `beta` | linear | smoothstep | minimum_jerk | beta | trapezoidal | Timing law (velocity profile). |
| `peak` | Double | `0.42` | [0.06, 0.94] | Normalised time of peak speed (beta, trapezoidal). |
| `sharpness` | Double | `7.0` | [4, 40] | Beta a+b; larger = narrower velocity peak. |
| `fitts-a` | Double | `0.08` | [0, 2] | Fitts intercept, seconds. |
| `fitts-b` | Double | `0.11` | [0, 1] | Fitts slope, seconds per bit. |
| `duration-jitter` | Double | `0.12` | [0, 1] | Log-normal sigma applied to the Fitts duration. |
| `min-duration` | Double | `0.06` | [0.001, 10] | Lower duration clamp, seconds. |
| `max-duration` | Double | `1.5` | [0.001, 10] | Upper duration clamp, seconds. |
| `endpoint-spread` | Double | `0.35` | [0, 1] | Gaussian spread of the aim point inside the target (0 = centre). |
| `overshoot-chance` | Double | `0.15` | [0, 1] | Probability of overshoot + corrective sub-movement. |
| `overshoot-fraction` | Double | `0.06` | [0, 0.5] | Overshoot distance as a fraction of movement distance. |
| `overshoot-min-distance` | Double | `150.0` | [0, 10000] | Movements shorter than this (px) never overshoot. |
| `correction-pause` | Double | `0.045` | [0, 1] | Pause before the corrective sub-movement, seconds. |
| `sample-rate-hz` | Double | `200.0` | [10, 2000] | Playback / sampling rate of cursor updates. |

## Built-in movement profiles

```properties
# fast[bezier/beta(4.15,4.85), fitts=0.050+0.085·ID]
profile.fast.bezier-degree = 3
profile.fast.correction-pause = 0.045
profile.fast.curvature = 0.1
profile.fast.duration-jitter = 0.12
profile.fast.endpoint-spread = 0.45
profile.fast.fitts-a = 0.05
profile.fast.fitts-b = 0.085
profile.fast.jitter = 0.4
profile.fast.law = beta
profile.fast.max-duration = 1.5
profile.fast.min-duration = 0.06
profile.fast.overshoot-chance = 0.22
profile.fast.overshoot-fraction = 0.05
profile.fast.overshoot-min-distance = 150
profile.fast.path = bezier
profile.fast.peak = 0.45
profile.fast.sample-rate-hz = 200
profile.fast.sharpness = 9
profile.fast.waypoints = 2

# linear-baseline[linear/linear, fitts=0.080+0.110·ID]
profile.linear-baseline.bezier-degree = 3
profile.linear-baseline.correction-pause = 0.045
profile.linear-baseline.curvature = 0
profile.linear-baseline.duration-jitter = 0
profile.linear-baseline.endpoint-spread = 0
profile.linear-baseline.fitts-a = 0.08
profile.linear-baseline.fitts-b = 0.11
profile.linear-baseline.jitter = 0
profile.linear-baseline.law = linear
profile.linear-baseline.max-duration = 1.5
profile.linear-baseline.min-duration = 0.06
profile.linear-baseline.overshoot-chance = 0
profile.linear-baseline.overshoot-fraction = 0.06
profile.linear-baseline.overshoot-min-distance = 150
profile.linear-baseline.path = linear
profile.linear-baseline.peak = 0.42
profile.linear-baseline.sample-rate-hz = 200
profile.linear-baseline.sharpness = 7
profile.linear-baseline.waypoints = 2

# precise[bezier/beta(3.16,4.84), fitts=0.100+0.130·ID]
profile.precise.bezier-degree = 3
profile.precise.correction-pause = 0.045
profile.precise.curvature = 0.07
profile.precise.duration-jitter = 0.12
profile.precise.endpoint-spread = 0.22
profile.precise.fitts-a = 0.1
profile.precise.fitts-b = 0.13
profile.precise.jitter = 0.25
profile.precise.law = beta
profile.precise.max-duration = 1.5
profile.precise.min-duration = 0.06
profile.precise.overshoot-chance = 0.08
profile.precise.overshoot-fraction = 0.06
profile.precise.overshoot-min-distance = 150
profile.precise.path = bezier
profile.precise.peak = 0.36
profile.precise.sample-rate-hz = 200
profile.precise.sharpness = 8
profile.precise.waypoints = 2

# smooth-spline[catmull_rom/minimum-jerk, fitts=0.080+0.110·ID]
profile.smooth-spline.bezier-degree = 3
profile.smooth-spline.correction-pause = 0.045
profile.smooth-spline.curvature = 0.14
profile.smooth-spline.duration-jitter = 0.12
profile.smooth-spline.endpoint-spread = 0.35
profile.smooth-spline.fitts-a = 0.08
profile.smooth-spline.fitts-b = 0.11
profile.smooth-spline.jitter = 0.35
profile.smooth-spline.law = minimum_jerk
profile.smooth-spline.max-duration = 1.5
profile.smooth-spline.min-duration = 0.06
profile.smooth-spline.overshoot-chance = 0
profile.smooth-spline.overshoot-fraction = 0.06
profile.smooth-spline.overshoot-min-distance = 150
profile.smooth-spline.path = catmull_rom
profile.smooth-spline.peak = 0.42
profile.smooth-spline.sample-rate-hz = 200
profile.smooth-spline.sharpness = 7
profile.smooth-spline.waypoints = 2

# standard[bezier/beta(3.10,3.90), fitts=0.080+0.110·ID]
profile.standard.bezier-degree = 3
profile.standard.correction-pause = 0.045
profile.standard.curvature = 0.12
profile.standard.duration-jitter = 0.12
profile.standard.endpoint-spread = 0.35
profile.standard.fitts-a = 0.08
profile.standard.fitts-b = 0.11
profile.standard.jitter = 0.35
profile.standard.law = beta
profile.standard.max-duration = 1.5
profile.standard.min-duration = 0.06
profile.standard.overshoot-chance = 0.15
profile.standard.overshoot-fraction = 0.06
profile.standard.overshoot-min-distance = 150
profile.standard.path = bezier
profile.standard.peak = 0.42
profile.standard.sample-rate-hz = 200
profile.standard.sharpness = 7
profile.standard.waypoints = 2

# wind[wind/beta(3.10,3.90), fitts=0.080+0.110·ID]
profile.wind.bezier-degree = 3
profile.wind.correction-pause = 0.045
profile.wind.curvature = 0.35
profile.wind.duration-jitter = 0.12
profile.wind.endpoint-spread = 0.35
profile.wind.fitts-a = 0.08
profile.wind.fitts-b = 0.11
profile.wind.jitter = 0.35
profile.wind.law = beta
profile.wind.max-duration = 1.5
profile.wind.min-duration = 0.06
profile.wind.overshoot-chance = 0
profile.wind.overshoot-fraction = 0.06
profile.wind.overshoot-min-distance = 150
profile.wind.path = wind
profile.wind.peak = 0.42
profile.wind.sample-rate-hz = 200
profile.wind.sharpness = 7
profile.wind.waypoints = 2

```

## Timing profiles (`timing.<name>.<field>`, fields: kind, center, spread, min, max)

| Profile | Kind | Center (s) | Spread | Min (s) | Max (s) |
|---|---|---|---|---|---|
| `action.reaction` | lognormal | 0.250 | 0.350 | 0.120 | 1.200 |
| `camera.correction-gap` | lognormal | 0.060 | 0.300 | 0.020 | 0.250 |
| `key.gap` | lognormal | 0.090 | 0.450 | 0.020 | 0.500 |
| `key.hold` | lognormal | 0.075 | 0.300 | 0.030 | 0.200 |
| `key.modifier-lead` | lognormal | 0.040 | 0.300 | 0.015 | 0.150 |
| `mouse.click-hold` | lognormal | 0.085 | 0.250 | 0.040 | 0.250 |
| `mouse.double-click-gap` | lognormal | 0.110 | 0.200 | 0.060 | 0.250 |
| `mouse.menu-read` | lognormal | 0.180 | 0.350 | 0.080 | 0.700 |
| `mouse.pre-click` | lognormal | 0.030 | 0.500 | 0.005 | 0.200 |
| `mouse.scroll-gap` | lognormal | 0.045 | 0.300 | 0.015 | 0.200 |
