package org.bbnors.motion;

import org.bbnors.core.geom.Rect;
import org.bbnors.core.geom.Vec2;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Objective measurements of a sampled trajectory, used by tests, the lab and benchmarks.
 *
 * @param duration           seconds
 * @param pathLength         polyline length of the samples, px
 * @param chord              straight-line start-end distance, px
 * @param straightness       chord / pathLength (1 = straight)
 * @param maxDeviation       max perpendicular distance from the chord, px
 * @param peakSpeed          px/s
 * @param meanSpeed          px/s
 * @param peakSpeedTime      normalised time of the peak speed (0..1)
 * @param peakAcceleration   max |a|, px/s²
 * @param rmsJerk            RMS of the finite-difference derivative of tangential acceleration, px/s³
 * @param maxStep            largest position jump between consecutive samples, px (continuity)
 * @param maxSpeedJump       largest |Δspeed| between consecutive samples, px/s (continuity)
 * @param endpointError      distance between the last sample and the intended end, px
 * @param endInTarget        whether the last sample lies inside the target (true if no target given)
 * @param segments           number of sub-movements (1 + corrections)
 */
public record MotionMetrics(
        double duration, double pathLength, double chord, double straightness, double maxDeviation,
        double peakSpeed, double meanSpeed, double peakSpeedTime, double peakAcceleration, double rmsJerk,
        double maxStep, double maxSpeedJump, double endpointError, boolean endInTarget, int segments) {

    public static MotionMetrics of(TrajectorySamples s, Vec2 intendedEnd, Rect target) {
        int n = s.size();
        if (n == 0) {
            throw new IllegalArgumentException("no samples");
        }
        double x0 = s.x[0];
        double y0 = s.y[0];
        double xe = s.x[n - 1];
        double ye = s.y[n - 1];
        double chord = Math.hypot(xe - x0, ye - y0);
        double cx = chord > 0 ? (xe - x0) / chord : 0;
        double cy = chord > 0 ? (ye - y0) / chord : 0;
        double len = 0;
        double maxDev = 0;
        double peak = 0;
        double peakT = 0;
        double peakA = 0;
        double maxStep = 0;
        double maxDv = 0;
        double jerkSq = 0;
        int jerkN = 0;
        int segs = 1;
        for (int i = 0; i < n; i++) {
            double dev = Math.abs((s.x[i] - x0) * cy - (s.y[i] - y0) * cx);
            maxDev = Math.max(maxDev, dev);
            if (s.speed[i] > peak) {
                peak = s.speed[i];
                peakT = s.t[i];
            }
            peakA = Math.max(peakA, s.acc[i]);
            if (i > 0) {
                double step = Math.hypot(s.x[i] - s.x[i - 1], s.y[i] - s.y[i - 1]);
                len += step;
                maxStep = Math.max(maxStep, step);
                maxDv = Math.max(maxDv, Math.abs(s.speed[i] - s.speed[i - 1]));
                double dt = s.t[i] - s.t[i - 1];
                if (dt > 1e-9 && s.segment[i] == s.segment[i - 1]) {
                    double j = (s.accTangential[i] - s.accTangential[i - 1]) / dt;
                    jerkSq += j * j;
                    jerkN++;
                }
                segs = Math.max(segs, s.segment[i] + 1);
            }
        }
        double dur = s.t[n - 1] - s.t[0];
        Vec2 last = new Vec2(xe, ye);
        return new MotionMetrics(
                dur, len, chord, len > 0 ? chord / len : 1, maxDev,
                peak, dur > 0 ? len / dur : 0, dur > 0 ? peakT / dur : 0, peakA,
                jerkN > 0 ? Math.sqrt(jerkSq / jerkN) : 0,
                maxStep, maxDv,
                intendedEnd == null ? 0 : last.distance(intendedEnd),
                target == null || target.contains(last), segs);
    }

    public Map<String, Object> asMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("duration_ms", round(duration * 1000));
        m.put("path_length_px", round(pathLength));
        m.put("chord_px", round(chord));
        m.put("straightness", round(straightness));
        m.put("max_deviation_px", round(maxDeviation));
        m.put("peak_speed_px_s", round(peakSpeed));
        m.put("mean_speed_px_s", round(meanSpeed));
        m.put("peak_speed_time", round(peakSpeedTime));
        m.put("peak_acc_px_s2", round(peakAcceleration));
        m.put("rms_jerk_px_s3", round(rmsJerk));
        m.put("max_step_px", round(maxStep));
        m.put("max_speed_jump_px_s", round(maxSpeedJump));
        m.put("endpoint_error_px", round(endpointError));
        m.put("end_in_target", endInTarget);
        m.put("segments", segments);
        return m;
    }

    private static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT,
                "dur=%.0fms len=%.1fpx straight=%.3f dev=%.1fpx vpk=%.0fpx/s@%.2f apk=%.0f jerk=%.0f step=%.2f err=%.3f seg=%d",
                duration * 1000, pathLength, straightness, maxDeviation, peakSpeed, peakSpeedTime, peakAcceleration,
                rmsJerk, maxStep, endpointError, segments);
    }
}
