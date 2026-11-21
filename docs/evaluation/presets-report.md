# Profile evaluation

600 paired scenarios per profile (seed 1): targets 8-90 px in three size classes, starts anywhere on a 765×503 surface.

## Correctness and Fitts fit

| Profile | in target | continuity | overshoot rate | configured a / b | fitted a / b | R² |
|---|---:|---:|---:|---|---|---:|
| standard | 100.0% | 100.0% | 14.8% | 0.080 / 0.110 | 0.051 / 0.124 | 0.80 |
| precise | 100.0% | 100.0% | 8.0% | 0.100 / 0.130 | 0.079 / 0.141 | 0.81 |
| fast | 100.0% | 100.0% | 20.7% | 0.050 / 0.085 | 0.032 / 0.096 | 0.79 |
| smooth-spline | 100.0% | 100.0% | 0.0% | 0.080 / 0.110 | 0.072 / 0.114 | 0.83 |
| wind | 100.0% | 100.0% | 0.0% | 0.080 / 0.110 | 0.072 / 0.114 | 0.83 |
| linear-baseline | 100.0% | 100.0% | 0.0% | 0.080 / 0.110 | 0.080 / 0.110 | 1.00 |

## Metric distributions (median [p5 – p95])

| Profile | duration_ms | peak_speed_time | straightness | max_deviation_px | peak_speed_px_s | rms_jerk_px_s3 | max_step_px | endpoint_error_px | aim_offset |
|---|---|---|---|---|---|---|---|---|---|
| standard | 490.3 [257.4 – 811.7] | 0.419 [0.276 – 0.425] | 0.982 [0.861 – 0.994] | 24.9 [6.860 – 56.3] | 1436 [549.9 – 2859] | 98696 [27293 – 436852] | 7.181 [2.749 – 14.3] | 1.0e-13 [0 – 2.3e-13] | 0.370 [0.088 – 0.761] |
| precise | 581.9 [313.4 – 917.5] | 0.360 [0.249 – 0.364] | 0.994 [0.901 – 0.997] | 14.7 [4.153 – 30.7] | 1291 [498.7 – 2508] | 70875 [21153 – 305387] | 6.455 [2.494 – 12.5] | 9.2e-14 [0 – 2.3e-13] | 0.233 [0.056 – 0.484] |
| fast | 369.8 [192.8 – 629.5] | 0.448 [0.290 – 0.457] | 0.986 [0.881 – 0.997] | 20.2 [4.765 – 48.0] | 2213 [827.6 – 4438] | 287371 [78045 – 1460386] | 11.1 [4.138 – 22.2] | 8.5e-14 [0 – 2.3e-13] | 0.458 [0.112 – 0.906] |
| smooth-spline | 485.8 [257.4 – 747.8] | 0.500 [0.494 – 0.505] | 0.985 [0.970 – 0.994] | 24.5 [6.422 – 51.0] | 1271 [501.3 – 2365] | 74494 [22325 – 301409] | 6.354 [2.506 – 11.8] | 8.5e-14 [0 – 2.3e-13] | 0.370 [0.088 – 0.761] |
| wind | 485.8 [257.4 – 747.8] | 0.420 [0.415 – 0.426] | 0.996 [0.990 – 0.998] | 7.548 [2.119 – 19.9] | 1380 [540.1 – 2530] | 87927 [26684 – 352462] | 6.900 [2.701 – 12.6] | 8.0e-14 [7.1e-15 – 2.3e-13] | 0.370 [0.088 – 0.761] |
| linear-baseline | 482.2 [258.8 – 708.8] | 0 [0 – 0] | 1.000 [1.000 – 1.000] | 4.3e-14 [1.4e-14 – 7.1e-14] | 673.9 [256.0 – 1172] | 0 [0 – 0] | 3.370 [1.280 – 5.862] | 0 [0 – 2.8e-14] | 0 [0 – 0] |

Notes: `aim_offset` is the end point's distance from the target centre in half-widths (0 = centre, 1 = edge). Fitted Fitts constants include duration jitter and overshoot corrections, so they differ from the configured ones by design; a large gap or low R² flags a profile whose durations do not scale with difficulty. Computational cost is measured separately by the benchmark suite (docs/PERFORMANCE.md).
