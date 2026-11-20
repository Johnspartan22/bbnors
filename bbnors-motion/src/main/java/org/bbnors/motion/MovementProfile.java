package org.bbnors.motion;

import org.bbnors.motion.timing.BetaLaw;
import org.bbnors.motion.timing.TimingLaw;
import org.bbnors.motion.timing.TimingLaws;
import org.bbnors.motion.timing.TrapezoidalLaw;

import java.util.Locale;
import java.util.Objects;

/**
 * Complete, validated description of how a class of movements should look and feel.
 *
 * <p>A profile is pure data: immutable, comparable, serialisable through {@link ProfileCodec},
 * and independent of any game client. Scripts never hard-code movement behaviour; they select a
 * profile by name (or use the default) and the {@link MotionPlanner} does the rest.
 *
 * <p>Construct with {@link #builder(String)}; every field is range-checked by
 * {@link Builder#build()}.
 */
public final class MovementProfile {

    /** Path geometry family. */
    public enum PathKind { LINEAR, BEZIER, CATMULL_ROM, WIND }

    /** Timing law family. */
    public enum LawKind { LINEAR, SMOOTHSTEP, MINIMUM_JERK, BETA, TRAPEZOIDAL }

    private final String name;
    private final PathKind path;
    private final int bezierDegree;
    private final int waypoints;
    private final double curvature;
    private final double jitter;
    private final LawKind law;
    private final double peak;
    private final double sharpness;
    private final double fittsA;
    private final double fittsB;
    private final double durationJitter;
    private final double minDuration;
    private final double maxDuration;
    private final double endpointSpread;
    private final double overshootChance;
    private final double overshootFraction;
    private final double overshootMinDistance;
    private final double correctionPause;
    private final double sampleRateHz;
    private final TimingLaw timingLaw;

    private MovementProfile(Builder b) {
        this.name = b.name;
        this.path = b.path;
        this.bezierDegree = b.bezierDegree;
        this.waypoints = b.waypoints;
        this.curvature = b.curvature;
        this.jitter = b.jitter;
        this.law = b.law;
        this.peak = b.peak;
        this.sharpness = b.sharpness;
        this.fittsA = b.fittsA;
        this.fittsB = b.fittsB;
        this.durationJitter = b.durationJitter;
        this.minDuration = b.minDuration;
        this.maxDuration = b.maxDuration;
        this.endpointSpread = b.endpointSpread;
        this.overshootChance = b.overshootChance;
        this.overshootFraction = b.overshootFraction;
        this.overshootMinDistance = b.overshootMinDistance;
        this.correctionPause = b.correctionPause;
        this.sampleRateHz = b.sampleRateHz;
        this.timingLaw = switch (law) {
            case LINEAR -> TimingLaws.LINEAR;
            case SMOOTHSTEP -> TimingLaws.SMOOTHSTEP;
            case MINIMUM_JERK -> TimingLaws.MINIMUM_JERK;
            case BETA -> BetaLaw.fromPeak(peak, sharpness);
            case TRAPEZOIDAL -> {
                // 20% cruise; the ramps split the remaining 80% in the ratio peak : (1 - peak),
                // so 'peak' moves the plateau earlier or later just as it moves the beta peak.
                double accel = peak * 0.8;
                double decel = (1 - peak) * 0.8;
                yield new TrapezoidalLaw(accel, decel);
            }
        };
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public Builder toBuilder() {
        Builder b = new Builder(name);
        b.path = path;
        b.bezierDegree = bezierDegree;
        b.waypoints = waypoints;
        b.curvature = curvature;
        b.jitter = jitter;
        b.law = law;
        b.peak = peak;
        b.sharpness = sharpness;
        b.fittsA = fittsA;
        b.fittsB = fittsB;
        b.durationJitter = durationJitter;
        b.minDuration = minDuration;
        b.maxDuration = maxDuration;
        b.endpointSpread = endpointSpread;
        b.overshootChance = overshootChance;
        b.overshootFraction = overshootFraction;
        b.overshootMinDistance = overshootMinDistance;
        b.correctionPause = correctionPause;
        b.sampleRateHz = sampleRateHz;
        return b;
    }

    public String name() { return name; }
    public PathKind path() { return path; }
    public int bezierDegree() { return bezierDegree; }
    public int waypoints() { return waypoints; }
    public double curvature() { return curvature; }
    public double jitter() { return jitter; }
    public LawKind law() { return law; }
    public double peak() { return peak; }
    public double sharpness() { return sharpness; }
    public double fittsA() { return fittsA; }
    public double fittsB() { return fittsB; }
    public double durationJitter() { return durationJitter; }
    public double minDuration() { return minDuration; }
    public double maxDuration() { return maxDuration; }
    public double endpointSpread() { return endpointSpread; }
    public double overshootChance() { return overshootChance; }
    public double overshootFraction() { return overshootFraction; }
    public double overshootMinDistance() { return overshootMinDistance; }
    public double correctionPause() { return correctionPause; }
    public double sampleRateHz() { return sampleRateHz; }

    /** The resolved timing law instance (shared, immutable). */
    public TimingLaw timingLaw() { return timingLaw; }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%s[%s/%s, fitts=%.3f+%.3f·ID]", name,
                path.name().toLowerCase(Locale.ROOT), timingLaw.name(), fittsA, fittsB);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof MovementProfile p && ProfileCodec.toProperties(this).equals(ProfileCodec.toProperties(p));
    }

    @Override
    public int hashCode() {
        return ProfileCodec.toProperties(this).hashCode();
    }

    /** Mutable builder with validated defaults (the "standard" profile). */
    public static final class Builder {
        private String name;
        private PathKind path = PathKind.BEZIER;
        private int bezierDegree = 3;
        private int waypoints = 2;
        private double curvature = 0.12;
        private double jitter = 0.35;
        private LawKind law = LawKind.BETA;
        private double peak = 0.42;
        private double sharpness = 7.0;
        private double fittsA = 0.080;
        private double fittsB = 0.110;
        private double durationJitter = 0.12;
        private double minDuration = 0.060;
        private double maxDuration = 1.500;
        private double endpointSpread = 0.35;
        private double overshootChance = 0.15;
        private double overshootFraction = 0.06;
        private double overshootMinDistance = 150;
        private double correctionPause = 0.045;
        private double sampleRateHz = 200;

        private Builder(String name) {
            this.name = Objects.requireNonNull(name);
        }

        public Builder name(String v) { name = Objects.requireNonNull(v); return this; }
        public Builder path(PathKind v) { path = Objects.requireNonNull(v); return this; }
        public Builder bezierDegree(int v) { bezierDegree = v; return this; }
        public Builder waypoints(int v) { waypoints = v; return this; }
        public Builder curvature(double v) { curvature = v; return this; }
        public Builder jitter(double v) { jitter = v; return this; }
        public Builder law(LawKind v) { law = Objects.requireNonNull(v); return this; }
        public Builder peak(double v) { peak = v; return this; }
        public Builder sharpness(double v) { sharpness = v; return this; }
        public Builder fittsA(double v) { fittsA = v; return this; }
        public Builder fittsB(double v) { fittsB = v; return this; }
        public Builder durationJitter(double v) { durationJitter = v; return this; }
        public Builder minDuration(double v) { minDuration = v; return this; }
        public Builder maxDuration(double v) { maxDuration = v; return this; }
        public Builder endpointSpread(double v) { endpointSpread = v; return this; }
        public Builder overshootChance(double v) { overshootChance = v; return this; }
        public Builder overshootFraction(double v) { overshootFraction = v; return this; }
        public Builder overshootMinDistance(double v) { overshootMinDistance = v; return this; }
        public Builder correctionPause(double v) { correctionPause = v; return this; }
        public Builder sampleRateHz(double v) { sampleRateHz = v; return this; }

        public MovementProfile build() {
            check(!name.isBlank() && name.matches("[A-Za-z0-9_.-]+"), "name must be [A-Za-z0-9_.-]+");
            check(bezierDegree >= 2 && bezierDegree <= 10, "bezierDegree must be in [2, 10]");
            check(waypoints >= 0 && waypoints <= 12, "waypoints must be in [0, 12]");
            check(curvature >= 0 && curvature <= 1, "curvature must be in [0, 1]");
            check(jitter >= 0 && jitter <= 2, "jitter must be in [0, 2]");
            check(peak > 0.05 && peak < 0.95, "peak must be in (0.05, 0.95)");
            check(sharpness >= 4 && sharpness <= 40, "sharpness must be in [4, 40]");
            if (law == LawKind.BETA) {
                double a = 1 + peak * (sharpness - 2);
                double b = sharpness - a;
                check(a >= 2 && b >= 2, "peak/sharpness combination gives beta(a=" + a + ", b=" + b + "); need a,b >= 2 (raise sharpness or move peak toward 0.5)");
            }
            check(fittsA >= 0 && fittsA <= 2, "fittsA must be in [0, 2] s");
            check(fittsB >= 0 && fittsB <= 1, "fittsB must be in [0, 1] s/bit");
            check(durationJitter >= 0 && durationJitter <= 1, "durationJitter must be in [0, 1]");
            check(minDuration > 0 && minDuration <= maxDuration, "need 0 < minDuration <= maxDuration");
            check(maxDuration <= 10, "maxDuration must be <= 10 s");
            check(endpointSpread >= 0 && endpointSpread <= 1, "endpointSpread must be in [0, 1]");
            check(overshootChance >= 0 && overshootChance <= 1, "overshootChance must be in [0, 1]");
            check(overshootFraction >= 0 && overshootFraction <= 0.5, "overshootFraction must be in [0, 0.5]");
            check(overshootMinDistance >= 0, "overshootMinDistance must be >= 0");
            check(correctionPause >= 0 && correctionPause <= 1, "correctionPause must be in [0, 1] s");
            check(sampleRateHz >= 10 && sampleRateHz <= 2000, "sampleRateHz must be in [10, 2000]");
            return new MovementProfile(this);
        }

        private void check(boolean ok, String msg) {
            if (!ok) {
                throw new IllegalArgumentException("profile '" + name + "': " + msg);
            }
        }
    }
}
