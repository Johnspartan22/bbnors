package org.bbnors.motion.timing;

/**
 * Timing law whose velocity profile is a Beta density: {@code v(τ) = τ^(a-1)(1-τ)^(b-1)/B(a,b)}.
 *
 * <p>Why Beta: aimed hand movements have a single-peaked, usually right-skewed velocity profile
 * (fast acceleration, longer homing-in deceleration). The Beta family captures exactly that with
 * two intuitive parameters, contains minimum-jerk as the special case {@code a=b=3}, and has
 * analytic {@code v} and {@code v'}.
 *
 * <p>{@code s(τ)} is the regularised incomplete beta function, which has no closed form for
 * non-integer parameters. It is tabulated once at construction (1024 intervals, Simpson) and
 * evaluated with cubic Hermite interpolation using the exact derivative {@code v}, giving errors
 * around 1e-12. {@link #ds} and {@link #dds} are exact.
 */
public final class BetaLaw implements TimingLaw {

    private static final int N = 1024;

    private final double a;
    private final double b;
    private final double norm;   // 1 / B(a,b)
    private final double[] table; // s at τ = i/N

    public BetaLaw(double a, double b) {
        if (!(a >= 2) || !(b >= 2)) {
            throw new IllegalArgumentException("Beta law needs a >= 2 and b >= 2 (zero end velocity, finite acceleration); got a=" + a + " b=" + b);
        }
        this.a = a;
        this.b = b;
        double h = 1.0 / N;
        double[] raw = new double[N + 1];
        for (int i = 0; i < N; i++) {
            double t0 = i * h;
            double tm = t0 + h / 2;
            double t1 = t0 + h;
            raw[i + 1] = raw[i] + h / 6 * (rawV(t0) + 4 * rawV(tm) + rawV(t1));
        }
        double total = raw[N];
        this.norm = 1 / total;
        this.table = new double[N + 1];
        for (int i = 0; i <= N; i++) {
            table[i] = raw[i] / total;
        }
        table[N] = 1;
    }

    /**
     * @param peak      τ at which speed peaks, in (0,1)
     * @param sharpness a + b; larger = narrower, taller velocity peak. 6 with peak 0.5 is minimum-jerk.
     */
    public static BetaLaw fromPeak(double peak, double sharpness) {
        if (!(peak > 0 && peak < 1)) {
            throw new IllegalArgumentException("peak must be in (0,1)");
        }
        double a = 1 + peak * (sharpness - 2);
        double b = sharpness - a;
        return new BetaLaw(a, b);
    }

    private double rawV(double t) {
        return Math.pow(t, a - 1) * Math.pow(1 - t, b - 1);
    }

    @Override
    public double s(double tau) {
        tau = TimingLaw.clamp(tau);
        if (tau >= 1) {
            return 1;
        }
        double x = tau * N;
        int i = (int) x;
        double t = x - i;
        double h = 1.0 / N;
        double p0 = table[i];
        double p1 = table[i + 1];
        double m0 = ds(i * h) * h;
        double m1 = ds((i + 1) * h) * h;
        double t2 = t * t;
        double t3 = t2 * t;
        return p0 * (2 * t3 - 3 * t2 + 1) + m0 * (t3 - 2 * t2 + t) + p1 * (-2 * t3 + 3 * t2) + m1 * (t3 - t2);
    }

    @Override
    public double ds(double tau) {
        tau = TimingLaw.clamp(tau);
        return norm * rawV(tau);
    }

    @Override
    public double dds(double tau) {
        tau = TimingLaw.clamp(tau);
        double p = Math.pow(tau, a - 2) * Math.pow(1 - tau, b - 2);
        return norm * p * ((a - 1) * (1 - tau) - (b - 1) * tau);
    }

    @Override
    public double peakTau() {
        return (a - 1) / (a + b - 2);
    }

    @Override
    public String name() {
        return String.format("beta(%.2f,%.2f)", a, b);
    }

    public double alpha() {
        return a;
    }

    public double beta() {
        return b;
    }
}
