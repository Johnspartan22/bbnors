package org.bbnors.motion.timing;

/**
 * The built-in timing laws.
 *
 * <table>
 *   <caption>Boundary behaviour</caption>
 *   <tr><th>Law</th><th>v(0), v(1)</th><th>a(0), a(1)</th><th>Peak</th><th>Notes</th></tr>
 *   <tr><td>linear</td><td>1, 1</td><td>0</td><td>flat</td><td>Constant speed; infinite acceleration at start/stop. Baseline only.</td></tr>
 *   <tr><td>smoothstep</td><td>0, 0</td><td>±6</td><td>0.5</td><td>Cubic; acceleration jumps at the ends (unbounded jerk).</td></tr>
 *   <tr><td>minimum-jerk</td><td>0, 0</td><td>0, 0</td><td>0.5</td><td>Quintic of Flash &amp; Hogan (1985); minimises integrated squared jerk.</td></tr>
 *   <tr><td>beta(a,b)</td><td>0, 0</td><td>0 if a,b≥3</td><td>(a-1)/(a+b-2)</td><td>Velocity ∝ τ^(a-1)(1-τ)^(b-1). a=b=3 is exactly minimum-jerk; b&gt;a gives the early peak and long deceleration tail typical of aimed movements.</td></tr>
 *   <tr><td>trapezoidal</td><td>0, 0</td><td>±a_max</td><td>plateau</td><td>Accelerate / cruise / decelerate; acceleration is discontinuous.</td></tr>
 * </table>
 */
public final class TimingLaws {

    private TimingLaws() {
    }

    public static final TimingLaw LINEAR = new TimingLaw() {
        @Override public double s(double t) { return TimingLaw.clamp(t); }
        @Override public double ds(double t) { return 1; }
        @Override public double dds(double t) { return 0; }
        @Override public String name() { return "linear"; }
        @Override public double peakTau() { return 0.5; }
    };

    public static final TimingLaw SMOOTHSTEP = new TimingLaw() {
        @Override public double s(double t) { t = TimingLaw.clamp(t); return t * t * (3 - 2 * t); }
        @Override public double ds(double t) { t = TimingLaw.clamp(t); return 6 * t * (1 - t); }
        @Override public double dds(double t) { t = TimingLaw.clamp(t); return 6 - 12 * t; }
        @Override public String name() { return "smoothstep"; }
        @Override public double peakTau() { return 0.5; }
    };

    public static final TimingLaw MINIMUM_JERK = new TimingLaw() {
        @Override public double s(double t) { t = TimingLaw.clamp(t); return t * t * t * (10 + t * (-15 + 6 * t)); }
        @Override public double ds(double t) { t = TimingLaw.clamp(t); double u = t * (1 - t); return 30 * u * u; }
        @Override public double dds(double t) { t = TimingLaw.clamp(t); return 60 * t * (1 - t) * (1 - 2 * t); }
        @Override public String name() { return "minimum-jerk"; }
        @Override public double peakTau() { return 0.5; }
    };

    /** Beta-shaped velocity law peaking at {@code peak} with total shape {@code sharpness = a + b}. */
    public static TimingLaw beta(double peak, double sharpness) {
        return BetaLaw.fromPeak(peak, sharpness);
    }

    public static TimingLaw trapezoidal(double accelFraction, double decelFraction) {
        return new TrapezoidalLaw(accelFraction, decelFraction);
    }
}
