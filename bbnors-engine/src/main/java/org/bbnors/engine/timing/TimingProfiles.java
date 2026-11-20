package org.bbnors.engine.timing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of named {@link TimingProfile}s with built-in defaults and properties loading:
 * <pre>
 *   timing.key.gap.kind   = lognormal
 *   timing.key.gap.center = 0.09
 *   timing.key.gap.spread = 0.45
 *   timing.key.gap.min    = 0.02
 *   timing.key.gap.max    = 0.5
 * </pre>
 * Missing fields keep the existing (default) value; invalid entries are reported, not thrown.
 *
 * <p>The defaults are reasonable starting assumptions, not measurements. Calibrate them
 * against recordings of your own input (see docs/TIMING_ENGINE.md).
 */
public final class TimingProfiles {

    public static final String MOUSE_CLICK_HOLD = "mouse.click-hold";
    public static final String MOUSE_DOUBLE_CLICK_GAP = "mouse.double-click-gap";
    public static final String MOUSE_PRE_CLICK = "mouse.pre-click";
    public static final String MOUSE_SCROLL_GAP = "mouse.scroll-gap";
    public static final String MOUSE_MENU_READ = "mouse.menu-read";
    public static final String KEY_HOLD = "key.hold";
    public static final String KEY_GAP = "key.gap";
    public static final String KEY_MODIFIER_LEAD = "key.modifier-lead";
    public static final String CAMERA_CORRECTION_GAP = "camera.correction-gap";
    public static final String ACTION_REACTION = "action.reaction";

    private static final String PREFIX = "timing.";

    private final Map<String, TimingProfile> profiles = new ConcurrentHashMap<>();

    public TimingProfiles() {
        for (TimingProfile p : defaults()) {
            profiles.put(p.name(), p);
        }
    }

    public static List<TimingProfile> defaults() {
        return List.of(
                TimingProfile.logNormal(MOUSE_CLICK_HOLD, 0.085, 0.25, 0.040, 0.250),
                TimingProfile.logNormal(MOUSE_DOUBLE_CLICK_GAP, 0.110, 0.20, 0.060, 0.250),
                TimingProfile.logNormal(MOUSE_PRE_CLICK, 0.030, 0.50, 0.005, 0.200),
                TimingProfile.logNormal(MOUSE_SCROLL_GAP, 0.045, 0.30, 0.015, 0.200),
                TimingProfile.logNormal(MOUSE_MENU_READ, 0.180, 0.35, 0.080, 0.700),
                TimingProfile.logNormal(KEY_HOLD, 0.075, 0.30, 0.030, 0.200),
                TimingProfile.logNormal(KEY_GAP, 0.090, 0.45, 0.020, 0.500),
                TimingProfile.logNormal(KEY_MODIFIER_LEAD, 0.040, 0.30, 0.015, 0.150),
                TimingProfile.logNormal(CAMERA_CORRECTION_GAP, 0.060, 0.30, 0.020, 0.250),
                TimingProfile.logNormal(ACTION_REACTION, 0.250, 0.35, 0.120, 1.200));
    }

    public TimingProfile get(String name) {
        TimingProfile p = profiles.get(name);
        if (p == null) {
            throw new IllegalArgumentException("unknown timing profile '" + name + "'; known: " + new TreeSet<>(profiles.keySet()));
        }
        return p;
    }

    public void put(TimingProfile p) {
        profiles.put(p.name(), p);
    }

    public Collection<TimingProfile> all() {
        return new TreeMap<>(profiles).values();
    }

    /** Applies {@code timing.<name>.<field>} entries; returns problems (empty if all valid). */
    public List<String> load(Properties props) {
        Map<String, Map<String, String>> groups = new TreeMap<>();
        List<String> problems = new ArrayList<>();
        for (String key : props.stringPropertyNames()) {
            if (!key.startsWith(PREFIX)) {
                continue;
            }
            String rest = key.substring(PREFIX.length());
            int dot = rest.lastIndexOf('.');
            if (dot <= 0) {
                problems.add("malformed timing key '" + key + "'");
                continue;
            }
            groups.computeIfAbsent(rest.substring(0, dot), k -> new TreeMap<>()).put(rest.substring(dot + 1), props.getProperty(key).trim());
        }
        for (Map.Entry<String, Map<String, String>> g : groups.entrySet()) {
            TimingProfile base = profiles.get(g.getKey());
            TimingProfile.Kind kind = base != null ? base.kind() : TimingProfile.Kind.LOGNORMAL;
            double center = base != null ? base.center() : Double.NaN;
            double spread = base != null ? base.spread() : 0;
            double min = base != null ? base.min() : 0;
            double max = base != null ? base.max() : Double.NaN;
            try {
                for (Map.Entry<String, String> f : g.getValue().entrySet()) {
                    switch (f.getKey()) {
                        case "kind" -> kind = TimingProfile.Kind.valueOf(f.getValue().toUpperCase(Locale.ROOT));
                        case "center" -> center = Double.parseDouble(f.getValue());
                        case "spread" -> spread = Double.parseDouble(f.getValue());
                        case "min" -> min = Double.parseDouble(f.getValue());
                        case "max" -> max = Double.parseDouble(f.getValue());
                        default -> throw new IllegalArgumentException("unknown field '" + f.getKey() + "'");
                    }
                }
                if (Double.isNaN(center) || Double.isNaN(max)) {
                    throw new IllegalArgumentException("new profiles need at least center and max");
                }
                profiles.put(g.getKey(), new TimingProfile(g.getKey(), kind, center, spread, min, max));
            } catch (IllegalArgumentException e) {
                problems.add("timing '" + g.getKey() + "': " + e.getMessage() + " (kept previous value)");
            }
        }
        return problems;
    }

    public String toMarkdown() {
        StringBuilder sb = new StringBuilder("| Profile | Kind | Center (s) | Spread | Min (s) | Max (s) |\n|---|---|---|---|---|---|\n");
        for (TimingProfile p : all()) {
            sb.append(String.format(Locale.ROOT, "| `%s` | %s | %.3f | %.3f | %.3f | %.3f |%n",
                    p.name(), p.kind().name().toLowerCase(Locale.ROOT), p.center(), p.spread(), p.min(), p.max()));
        }
        return sb.toString();
    }
}
