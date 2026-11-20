package org.bbnors.engine.timing;

import org.bbnors.core.random.Rng;

import java.util.Locale;

/**
 * A named delay distribution (seconds), always clamped to {@code [min, max]}.
 *
 * <p>Every delay in the platform (button hold times, key gaps, settle times, reaction times)
 * is drawn from a named profile in {@link TimingProfiles} instead of being a literal in code, so
 * all timing behaviour can be inspected, configured and tested in one place.
 *
 * @param kind   distribution family
 * @param center mean (fixed/uniform/gaussian) or median (lognormal)
 * @param spread half-width (uniform), std-dev (gaussian) or sigma (lognormal); ignored for fixed
 */
public record TimingProfile(String name, Kind kind, double center, double spread, double min, double max) {

    public enum Kind { FIXED, UNIFORM, GAUSSIAN, LOGNORMAL }

    public TimingProfile {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name required");
        if (!(min >= 0) || !(max >= min)) throw new IllegalArgumentException(name + ": need 0 <= min <= max");
        if (!(center >= 0)) throw new IllegalArgumentException(name + ": center must be >= 0");
        if (!(spread >= 0)) throw new IllegalArgumentException(name + ": spread must be >= 0");
        if (kind == Kind.LOGNORMAL && center <= 0) throw new IllegalArgumentException(name + ": lognormal median must be > 0");
    }

    public static TimingProfile fixed(String name, double seconds) {
        return new TimingProfile(name, Kind.FIXED, seconds, 0, seconds, seconds);
    }

    public static TimingProfile logNormal(String name, double median, double sigma, double min, double max) {
        return new TimingProfile(name, Kind.LOGNORMAL, median, sigma, min, max);
    }

    public static TimingProfile gaussian(String name, double mean, double sd, double min, double max) {
        return new TimingProfile(name, Kind.GAUSSIAN, mean, sd, min, max);
    }

    public static TimingProfile uniform(String name, double min, double max) {
        return new TimingProfile(name, Kind.UNIFORM, (min + max) / 2, (max - min) / 2, min, max);
    }

    /** Draws a delay in seconds. */
    public double sample(Rng rng) {
        double v = switch (kind) {
            case FIXED -> center;
            case UNIFORM -> rng.uniform(center - spread, center + spread);
            case GAUSSIAN -> rng.gaussian(center, spread);
            case LOGNORMAL -> rng.logNormal(center, spread);
        };
        return Math.max(min, Math.min(max, v));
    }

    public long sampleNanos(Rng rng) {
        return (long) (sample(rng) * 1e9);
    }

    public TimingProfile scaled(double factor) {
        return new TimingProfile(name, kind, center * factor, kind == Kind.LOGNORMAL ? spread : spread * factor, min * factor, max * factor);
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%s{%s %.3fs ±%.3f [%.3f, %.3f]}", name, kind.name().toLowerCase(Locale.ROOT), center, spread, min, max);
    }
}
