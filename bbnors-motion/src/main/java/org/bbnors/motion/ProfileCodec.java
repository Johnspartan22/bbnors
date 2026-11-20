package org.bbnors.motion;

import org.bbnors.core.config.Setting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Reads and writes {@link MovementProfile}s as flat properties:
 * <pre>
 *   profile.precise.path = bezier
 *   profile.precise.curvature = 0.08
 *   ...
 * </pre>
 * Keys that are missing take the builder default; unknown keys and invalid values are reported in
 * {@link Result#problems()} rather than thrown, so a hand-edited file degrades gracefully. Each
 * field is described by a {@link Setting}, which also produces the documentation table.
 */
public final class ProfileCodec {

    public static final String PREFIX = "profile.";

    /** Field definitions (keys are relative to {@code profile.<name>.}). */
    public static final class Fields {
        public static final Setting<MovementProfile.PathKind> PATH = Setting.ofEnum("path", MovementProfile.PathKind.class, MovementProfile.PathKind.BEZIER, "Path geometry family.");
        public static final Setting<Integer> BEZIER_DEGREE = Setting.ofInt("bezier-degree", 3, 2, 10, "Bezier degree (bezier only).");
        public static final Setting<Integer> WAYPOINTS = Setting.ofInt("waypoints", 2, 0, 12, "Interior waypoints (catmull_rom only).");
        public static final Setting<Double> CURVATURE = Setting.ofDouble("curvature", 0.12, 0, 1, "Peak sideways deviation as a fraction of distance (wind: wind strength).");
        public static final Setting<Double> JITTER = Setting.ofDouble("jitter", 0.35, 0, 2, "Random variation of control points.");
        public static final Setting<MovementProfile.LawKind> LAW = Setting.ofEnum("law", MovementProfile.LawKind.class, MovementProfile.LawKind.BETA, "Timing law (velocity profile).");
        public static final Setting<Double> PEAK = Setting.ofDouble("peak", 0.42, 0.06, 0.94, "Normalised time of peak speed (beta, trapezoidal).");
        public static final Setting<Double> SHARPNESS = Setting.ofDouble("sharpness", 7, 4, 40, "Beta a+b; larger = narrower velocity peak.");
        public static final Setting<Double> FITTS_A = Setting.ofDouble("fitts-a", 0.080, 0, 2, "Fitts intercept, seconds.");
        public static final Setting<Double> FITTS_B = Setting.ofDouble("fitts-b", 0.110, 0, 1, "Fitts slope, seconds per bit.");
        public static final Setting<Double> DURATION_JITTER = Setting.ofDouble("duration-jitter", 0.12, 0, 1, "Log-normal sigma applied to the Fitts duration.");
        public static final Setting<Double> MIN_DURATION = Setting.ofDouble("min-duration", 0.060, 0.001, 10, "Lower duration clamp, seconds.");
        public static final Setting<Double> MAX_DURATION = Setting.ofDouble("max-duration", 1.5, 0.001, 10, "Upper duration clamp, seconds.");
        public static final Setting<Double> ENDPOINT_SPREAD = Setting.ofDouble("endpoint-spread", 0.35, 0, 1, "Gaussian spread of the aim point inside the target (0 = centre).");
        public static final Setting<Double> OVERSHOOT_CHANCE = Setting.ofDouble("overshoot-chance", 0.15, 0, 1, "Probability of overshoot + corrective sub-movement.");
        public static final Setting<Double> OVERSHOOT_FRACTION = Setting.ofDouble("overshoot-fraction", 0.06, 0, 0.5, "Overshoot distance as a fraction of movement distance.");
        public static final Setting<Double> OVERSHOOT_MIN_DISTANCE = Setting.ofDouble("overshoot-min-distance", 150, 0, 10000, "Movements shorter than this (px) never overshoot.");
        public static final Setting<Double> CORRECTION_PAUSE = Setting.ofDouble("correction-pause", 0.045, 0, 1, "Pause before the corrective sub-movement, seconds.");
        public static final Setting<Double> SAMPLE_RATE = Setting.ofDouble("sample-rate-hz", 200, 10, 2000, "Playback / sampling rate of cursor updates.");

        private Fields() {
        }

        static List<Setting<?>> all() {
            return List.of(PATH, BEZIER_DEGREE, WAYPOINTS, CURVATURE, JITTER, LAW, PEAK, SHARPNESS, FITTS_A, FITTS_B,
                    DURATION_JITTER, MIN_DURATION, MAX_DURATION, ENDPOINT_SPREAD, OVERSHOOT_CHANCE, OVERSHOOT_FRACTION,
                    OVERSHOOT_MIN_DISTANCE, CORRECTION_PAUSE, SAMPLE_RATE);
        }
    }

    /** Parsed profiles plus every problem encountered. */
    public record Result(Map<String, MovementProfile> profiles, List<String> problems) {
    }

    private ProfileCodec() {
    }

    public static Properties toProperties(MovementProfile p) {
        Properties out = new Properties();
        String base = PREFIX + p.name() + ".";
        out.setProperty(base + Fields.PATH.key(), p.path().name().toLowerCase(Locale.ROOT));
        out.setProperty(base + Fields.BEZIER_DEGREE.key(), Integer.toString(p.bezierDegree()));
        out.setProperty(base + Fields.WAYPOINTS.key(), Integer.toString(p.waypoints()));
        out.setProperty(base + Fields.CURVATURE.key(), num(p.curvature()));
        out.setProperty(base + Fields.JITTER.key(), num(p.jitter()));
        out.setProperty(base + Fields.LAW.key(), p.law().name().toLowerCase(Locale.ROOT));
        out.setProperty(base + Fields.PEAK.key(), num(p.peak()));
        out.setProperty(base + Fields.SHARPNESS.key(), num(p.sharpness()));
        out.setProperty(base + Fields.FITTS_A.key(), num(p.fittsA()));
        out.setProperty(base + Fields.FITTS_B.key(), num(p.fittsB()));
        out.setProperty(base + Fields.DURATION_JITTER.key(), num(p.durationJitter()));
        out.setProperty(base + Fields.MIN_DURATION.key(), num(p.minDuration()));
        out.setProperty(base + Fields.MAX_DURATION.key(), num(p.maxDuration()));
        out.setProperty(base + Fields.ENDPOINT_SPREAD.key(), num(p.endpointSpread()));
        out.setProperty(base + Fields.OVERSHOOT_CHANCE.key(), num(p.overshootChance()));
        out.setProperty(base + Fields.OVERSHOOT_FRACTION.key(), num(p.overshootFraction()));
        out.setProperty(base + Fields.OVERSHOOT_MIN_DISTANCE.key(), num(p.overshootMinDistance()));
        out.setProperty(base + Fields.CORRECTION_PAUSE.key(), num(p.correctionPause()));
        out.setProperty(base + Fields.SAMPLE_RATE.key(), num(p.sampleRateHz()));
        return out;
    }

    /** Sorted, human-friendly properties text for one or more profiles. */
    public static String format(Iterable<MovementProfile> profiles) {
        StringBuilder sb = new StringBuilder();
        for (MovementProfile p : profiles) {
            sb.append("# ").append(p).append('\n');
            Map<String, String> sorted = new TreeMap<>();
            toProperties(p).forEach((k, v) -> sorted.put((String) k, (String) v));
            sorted.forEach((k, v) -> sb.append(k).append(" = ").append(v).append('\n'));
            sb.append('\n');
        }
        return sb.toString();
    }

    /**
     * Parses every {@code profile.<name>.*} group in {@code props}. Profiles named in
     * {@code base} start from that profile's values (so a file can override a single field of a
     * preset); others start from builder defaults.
     */
    public static Result parse(Properties props, Map<String, MovementProfile> base) {
        Map<String, Map<String, String>> groups = new LinkedHashMap<>();
        List<String> problems = new ArrayList<>();
        for (String key : new TreeSet<>(props.stringPropertyNames())) {
            if (!key.startsWith(PREFIX)) {
                continue;
            }
            String rest = key.substring(PREFIX.length());
            int dot = rest.indexOf('.');
            if (dot <= 0) {
                problems.add("malformed profile key '" + key + "'");
                continue;
            }
            groups.computeIfAbsent(rest.substring(0, dot), k -> new LinkedHashMap<>()).put(rest.substring(dot + 1), props.getProperty(key));
        }
        Map<String, MovementProfile> out = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, String>> g : groups.entrySet()) {
            String name = g.getKey();
            MovementProfile seed = base.get(name);
            MovementProfile.Builder b = seed != null ? seed.toBuilder() : MovementProfile.builder(name);
            for (Map.Entry<String, String> f : g.getValue().entrySet()) {
                Setting<?> s = Fields.all().stream().filter(x -> x.key().equals(f.getKey())).findFirst().orElse(null);
                if (s == null) {
                    problems.add("profile '" + name + "': unknown field '" + f.getKey() + "'");
                    continue;
                }
                try {
                    apply(b, s, s.parse(f.getValue()));
                } catch (IllegalArgumentException e) {
                    problems.add("profile '" + name + "': " + e.getMessage());
                }
            }
            try {
                out.put(name, b.build());
            } catch (IllegalArgumentException e) {
                problems.add(e.getMessage() + " (profile skipped)");
            }
        }
        return new Result(out, problems);
    }

    private static void apply(MovementProfile.Builder b, Setting<?> s, Object v) {
        if (s == Fields.PATH) b.path((MovementProfile.PathKind) v);
        else if (s == Fields.BEZIER_DEGREE) b.bezierDegree((Integer) v);
        else if (s == Fields.WAYPOINTS) b.waypoints((Integer) v);
        else if (s == Fields.CURVATURE) b.curvature((Double) v);
        else if (s == Fields.JITTER) b.jitter((Double) v);
        else if (s == Fields.LAW) b.law((MovementProfile.LawKind) v);
        else if (s == Fields.PEAK) b.peak((Double) v);
        else if (s == Fields.SHARPNESS) b.sharpness((Double) v);
        else if (s == Fields.FITTS_A) b.fittsA((Double) v);
        else if (s == Fields.FITTS_B) b.fittsB((Double) v);
        else if (s == Fields.DURATION_JITTER) b.durationJitter((Double) v);
        else if (s == Fields.MIN_DURATION) b.minDuration((Double) v);
        else if (s == Fields.MAX_DURATION) b.maxDuration((Double) v);
        else if (s == Fields.ENDPOINT_SPREAD) b.endpointSpread((Double) v);
        else if (s == Fields.OVERSHOOT_CHANCE) b.overshootChance((Double) v);
        else if (s == Fields.OVERSHOOT_FRACTION) b.overshootFraction((Double) v);
        else if (s == Fields.OVERSHOOT_MIN_DISTANCE) b.overshootMinDistance((Double) v);
        else if (s == Fields.CORRECTION_PAUSE) b.correctionPause((Double) v);
        else if (s == Fields.SAMPLE_RATE) b.sampleRateHz((Double) v);
        else throw new IllegalStateException("unmapped field " + s.key());
    }

    /** Markdown reference of profile fields. */
    public static String fieldsMarkdown() {
        org.bbnors.core.config.ConfigSchema schema = new org.bbnors.core.config.ConfigSchema();
        Fields.all().forEach(schema::register);
        return schema.toMarkdown();
    }

    private static String num(double d) {
        return d == Math.rint(d) ? Long.toString((long) d) : Double.toString(d);
    }
}
