package org.bbnors.motion;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry of named {@link MovementProfile}s, pre-populated with the built-in presets.
 *
 * <p>Presets (all parameters are tunable; these are starting points):
 * <ul>
 *   <li><b>standard</b>: cubic Bezier, beta(peak 0.42) timing, occasional overshoot.</li>
 *   <li><b>precise</b>: flatter arcs, longer homing phase, tight endpoint spread; for small targets
 *       such as inventory slots.</li>
 *   <li><b>fast</b>: shorter durations, sharper velocity peak; for large or repeated targets.</li>
 *   <li><b>smooth-spline</b>: centripetal Catmull-Rom through 2 waypoints, minimum-jerk timing.</li>
 *   <li><b>wind</b>: WindMouse-derived geometry with beta timing.</li>
 *   <li><b>linear-baseline</b>: straight line, constant speed. Reference only.</li>
 * </ul>
 */
public final class MovementProfiles {

    public static final String DEFAULT = "standard";

    private final Map<String, MovementProfile> profiles = new ConcurrentHashMap<>();

    public MovementProfiles() {
        for (MovementProfile p : presets()) {
            profiles.put(p.name(), p);
        }
    }

    public static List<MovementProfile> presets() {
        List<MovementProfile> out = new ArrayList<>();
        out.add(MovementProfile.builder("standard").build());
        out.add(MovementProfile.builder("precise")
                .curvature(0.07).jitter(0.25).peak(0.36).sharpness(8)
                .fittsA(0.100).fittsB(0.130).endpointSpread(0.22).overshootChance(0.08).build());
        out.add(MovementProfile.builder("fast")
                .curvature(0.10).jitter(0.4).peak(0.45).sharpness(9)
                .fittsA(0.050).fittsB(0.085).endpointSpread(0.45).overshootChance(0.22).overshootFraction(0.05).build());
        out.add(MovementProfile.builder("smooth-spline")
                .path(MovementProfile.PathKind.CATMULL_ROM).waypoints(2).curvature(0.14)
                .law(MovementProfile.LawKind.MINIMUM_JERK).overshootChance(0).build());
        out.add(MovementProfile.builder("wind")
                .path(MovementProfile.PathKind.WIND).curvature(0.35).overshootChance(0).build());
        out.add(MovementProfile.builder("linear-baseline")
                .path(MovementProfile.PathKind.LINEAR).law(MovementProfile.LawKind.LINEAR)
                .curvature(0).jitter(0).durationJitter(0).endpointSpread(0).overshootChance(0).build());
        return out;
    }

    public MovementProfile get(String name) {
        MovementProfile p = profiles.get(name);
        if (p == null) {
            throw new IllegalArgumentException("unknown movement profile '" + name + "'; known: " + profiles.keySet());
        }
        return p;
    }

    public MovementProfile getOrDefault(String name) {
        return profiles.getOrDefault(name, profiles.get(DEFAULT));
    }

    public void put(MovementProfile p) {
        profiles.put(p.name(), p);
    }

    public Collection<MovementProfile> all() {
        Map<String, MovementProfile> sorted = new LinkedHashMap<>();
        profiles.keySet().stream().sorted().forEach(k -> sorted.put(k, profiles.get(k)));
        return Collections.unmodifiableCollection(sorted.values());
    }

    /**
     * Loads profiles from a properties file, overriding/adding to the registry.
     *
     * @return problems found (empty if the file was fully valid)
     */
    public List<String> load(Path file) throws IOException {
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            p.load(r);
        }
        return load(p);
    }

    public List<String> load(Properties props) {
        ProfileCodec.Result r = ProfileCodec.parse(props, profiles);
        profiles.putAll(r.profiles());
        return r.problems();
    }
}
