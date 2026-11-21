package org.bbnors.lab;

import org.bbnors.core.geom.Rect;
import org.bbnors.core.geom.Vec2;
import org.bbnors.core.random.Rng;
import org.bbnors.motion.Fitts;
import org.bbnors.motion.Motion;
import org.bbnors.motion.MotionMetrics;
import org.bbnors.motion.MotionPlanner;
import org.bbnors.motion.MotionSampler;
import org.bbnors.motion.MovementProfile;
import org.bbnors.motion.TrajectorySamples;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Batch evaluation of movement profiles over a fixed, seeded set of scenarios.
 *
 * <p>Every profile is run on <em>the same</em> scenarios (start point, target rectangle, trial
 * seed), so differences between profiles are paired, not sampling noise. For each profile the
 * evaluator reports distributions of the {@link MotionMetrics}, correctness rates (in target,
 * continuity), the overshoot rate, and an ordinary least-squares fit of duration against the
 * Fitts index of difficulty, which can be compared with the profile's configured {@code fitts-a}
 * and {@code fitts-b}.
 */
public final class ProfileEvaluator {

    /** One test case shared by all profiles. */
    public record Scenario(int index, Vec2 from, Rect target, long seed) {
        public double distance() {
            return from.distance(target.center());
        }

        public double indexOfDifficulty() {
            return Fitts.indexOfDifficulty(distance(), target.minSide());
        }
    }

    /**
     * Per-trial result.
     *
     * @param aimOffset end point's distance from the target centre in half-widths (0 = centre, 1 = edge)
     */
    public record Trial(Scenario scenario, MotionMetrics metrics, double aimOffset) { }

    /** Summary statistics of one metric. */
    public record Stat(double mean, double sd, double p5, double p50, double p95) {
        static Stat of(double[] v) {
            double[] s = v.clone();
            Arrays.sort(s);
            double mean = Arrays.stream(s).average().orElse(0);
            double var = Arrays.stream(s).map(x -> (x - mean) * (x - mean)).sum() / Math.max(1, s.length - 1);
            return new Stat(mean, Math.sqrt(var), pct(s, 0.05), pct(s, 0.5), pct(s, 0.95));
        }

        Map<String, Object> asMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("mean", mean);
            m.put("sd", sd);
            m.put("p5", p5);
            m.put("p50", p50);
            m.put("p95", p95);
            return m;
        }
    }

    /** Aggregated result for one profile. */
    public record ProfileReport(MovementProfile profile, List<Trial> trials, Map<String, Stat> stats,
                                double inTargetRate, double continuityRate, double overshootRate,
                                double fittedA, double fittedB, double fitR2) {

        public Map<String, Object> asMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("profile", profile.name());
            m.put("trials", trials.size());
            m.put("in_target_rate", inTargetRate);
            m.put("continuity_rate", continuityRate);
            m.put("overshoot_rate", overshootRate);
            Map<String, Object> fit = new LinkedHashMap<>();
            fit.put("configured_a_s", profile.fittsA());
            fit.put("configured_b_s_per_bit", profile.fittsB());
            fit.put("fitted_a_s", fittedA);
            fit.put("fitted_b_s_per_bit", fittedB);
            fit.put("r2", fitR2);
            m.put("fitts", fit);
            Map<String, Object> st = new LinkedHashMap<>();
            stats.forEach((k, v) -> st.put(k, v.asMap()));
            m.put("metrics", st);
            return m;
        }
    }

    /** Metric names in report order, with extractors. */
    static final String[] METRICS = {"duration_ms", "peak_speed_time", "straightness", "max_deviation_px",
            "peak_speed_px_s", "rms_jerk_px_s3", "max_step_px", "endpoint_error_px", "aim_offset"};

    private ProfileEvaluator() {
    }

    /**
     * Generates {@code n} scenarios: start anywhere on a 765×503 surface, targets in three size
     * classes (small 8-16 px like inventory slots, medium 20-40 px, large 50-90 px), distances
     * from ~20 px up to the full diagonal.
     */
    public static List<Scenario> scenarios(int n, long seed) {
        Rng r = new Rng(seed);
        List<Scenario> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            double w;
            double h;
            switch (i % 3) {
                case 0 -> { w = r.uniform(8, 16); h = r.uniform(8, 16); }
                case 1 -> { w = r.uniform(20, 40); h = r.uniform(20, 40); }
                default -> { w = r.uniform(50, 90); h = r.uniform(50, 90); }
            }
            Vec2 c;
            Vec2 from;
            do {
                c = new Vec2(r.uniform(w / 2 + 2, 765 - w / 2 - 2), r.uniform(h / 2 + 2, 503 - h / 2 - 2));
                from = new Vec2(r.uniform(0, 765), r.uniform(0, 503));
            } while (from.distance(c) < 20);
            out.add(new Scenario(i, from, Rect.centered(c, w, h), seed * 1_000_003L + i));
        }
        return out;
    }

    public static ProfileReport evaluate(MovementProfile p, List<Scenario> scenarios) {
        MotionPlanner planner = new MotionPlanner();
        TrajectorySamples buf = new TrajectorySamples();
        List<Trial> trials = new ArrayList<>(scenarios.size());
        int n = scenarios.size();
        double[][] cols = new double[METRICS.length][n];
        int inTarget = 0;
        int continuous = 0;
        int overshoot = 0;
        double[] id = new double[n];
        double[] dur = new double[n];
        for (int i = 0; i < n; i++) {
            Scenario s = scenarios.get(i);
            Motion m = planner.plan(p, s.from(), s.target(), new Rng(s.seed()));
            TrajectorySamples smp = MotionSampler.sample(m, p.sampleRateHz(), buf);
            MotionMetrics mm = MotionMetrics.of(smp, m.end(), s.target());
            Vec2 c = s.target().center();
            double aim = Math.hypot((m.end().x() - c.x()) / (s.target().width() / 2), (m.end().y() - c.y()) / (s.target().height() / 2));
            trials.add(new Trial(s, mm, aim));
            double[] vals = {mm.duration() * 1000, mm.peakSpeedTime(), mm.straightness(), mm.maxDeviation(),
                    mm.peakSpeed(), mm.rmsJerk(), mm.maxStep(), mm.endpointError(), aim};
            for (int k = 0; k < METRICS.length; k++) {
                cols[k][i] = vals[k];
            }
            if (mm.endInTarget()) inTarget++;
            if (mm.duration() == 0 || mm.maxStep() <= mm.peakSpeed() / p.sampleRateHz() * 1.05 + 1e-6) continuous++;
            if (mm.segments() > 1) overshoot++;
            id[i] = s.indexOfDifficulty();
            dur[i] = mm.duration();
        }
        Map<String, Stat> stats = new LinkedHashMap<>();
        for (int k = 0; k < METRICS.length; k++) {
            stats.put(METRICS[k], Stat.of(cols[k]));
        }
        double[] fit = ols(id, dur);
        return new ProfileReport(p, trials, stats, (double) inTarget / n, (double) continuous / n, (double) overshoot / n,
                fit[0], fit[1], fit[2]);
    }

    public static List<ProfileReport> evaluateAll(List<MovementProfile> profiles, int trials, long seed) {
        List<Scenario> sc = scenarios(trials, seed);
        List<ProfileReport> out = new ArrayList<>();
        for (MovementProfile p : profiles) {
            out.add(evaluate(p, sc));
        }
        return out;
    }

    /** Ordinary least squares y = a + b·x; returns {a, b, R²}. */
    static double[] ols(double[] x, double[] y) {
        int n = x.length;
        double mx = Arrays.stream(x).average().orElse(0);
        double my = Arrays.stream(y).average().orElse(0);
        double sxy = 0;
        double sxx = 0;
        double syy = 0;
        for (int i = 0; i < n; i++) {
            sxy += (x[i] - mx) * (y[i] - my);
            sxx += (x[i] - mx) * (x[i] - mx);
            syy += (y[i] - my) * (y[i] - my);
        }
        double b = sxx > 0 ? sxy / sxx : 0;
        double a = my - b * mx;
        double r2 = sxx > 0 && syy > 0 ? (sxy * sxy) / (sxx * syy) : 0;
        return new double[]{a, b, r2};
    }

    private static double pct(double[] sorted, double p) {
        if (sorted.length == 0) return 0;
        double idx = p * (sorted.length - 1);
        int lo = (int) Math.floor(idx);
        int hi = (int) Math.ceil(idx);
        return sorted[lo] + (sorted[hi] - sorted[lo]) * (idx - lo);
    }

    /** Markdown report: correctness, Fitts fit, and metric distributions per profile. */
    public static String markdown(List<ProfileReport> reports, int trials, long seed) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "# Profile evaluation%n%n%d paired scenarios per profile (seed %d): targets 8-90 px in three size classes, starts anywhere on a 765×503 surface.%n%n", trials, seed));
        sb.append("## Correctness and Fitts fit\n\n| Profile | in target | continuity | overshoot rate | configured a / b | fitted a / b | R² |\n|---|---:|---:|---:|---|---|---:|\n");
        for (ProfileReport r : reports) {
            sb.append(String.format(Locale.ROOT, "| %s | %.1f%% | %.1f%% | %.1f%% | %.3f / %.3f | %.3f / %.3f | %.2f |%n",
                    r.profile().name(), r.inTargetRate() * 100, r.continuityRate() * 100, r.overshootRate() * 100,
                    r.profile().fittsA(), r.profile().fittsB(), r.fittedA(), r.fittedB(), r.fitR2()));
        }
        sb.append("\n## Metric distributions (median [p5 – p95])\n\n| Profile |");
        for (String m : METRICS) sb.append(' ').append(m).append(" |");
        sb.append("\n|---|");
        for (int i = 0; i < METRICS.length; i++) sb.append("---|");
        sb.append('\n');
        for (ProfileReport r : reports) {
            sb.append("| ").append(r.profile().name()).append(" |");
            for (String m : METRICS) {
                Stat s = r.stats().get(m);
                sb.append(' ').append(fmt(s.p50())).append(" [").append(fmt(s.p5())).append(" – ").append(fmt(s.p95())).append("] |");
            }
            sb.append('\n');
        }
        sb.append("\nNotes: `aim_offset` is the end point's distance from the target centre in half-widths (0 = centre, 1 = edge). ")
                .append("Fitted Fitts constants include duration jitter and overshoot corrections, so they differ from the configured ones by design; ")
                .append("a large gap or low R² flags a profile whose durations do not scale with difficulty. Computational cost is measured separately by the benchmark suite (docs/PERFORMANCE.md).\n");
        return sb.toString();
    }

    /** Per-trial CSV for external analysis. */
    public static String csv(List<ProfileReport> reports) {
        StringBuilder sb = new StringBuilder("profile,trial,distance_px,target_w,target_h,fitts_id");
        for (String m : METRICS) sb.append(',').append(m);
        sb.append(",in_target,segments\n");
        for (ProfileReport r : reports) {
            for (Trial t : r.trials()) {
                Scenario s = t.scenario();
                MotionMetrics mm = t.metrics();
                sb.append(String.format(Locale.ROOT, "%s,%d,%.2f,%.2f,%.2f,%.4f,%.3f,%.4f,%.5f,%.3f,%.2f,%.2f,%.4f,%.3g,",
                        r.profile().name(), s.index(), s.distance(), s.target().width(), s.target().height(), s.indexOfDifficulty(),
                        mm.duration() * 1000, mm.peakSpeedTime(), mm.straightness(), mm.maxDeviation(), mm.peakSpeed(), mm.rmsJerk(), mm.maxStep(), mm.endpointError()));
                sb.append(String.format(Locale.ROOT, "%.4f,%s,%d%n", t.aimOffset(), mm.endInTarget(), mm.segments()));
            }
        }
        return sb.toString();
    }

    static String fmt(double v) {
        double a = Math.abs(v);
        if (a == 0) return "0";
        if (a >= 1000) return String.format(Locale.ROOT, "%.0f", v);
        if (a >= 10) return String.format(Locale.ROOT, "%.1f", v);
        if (a >= 0.01) return String.format(Locale.ROOT, "%.3f", v);
        return String.format(Locale.ROOT, "%.2g", v);
    }
}
