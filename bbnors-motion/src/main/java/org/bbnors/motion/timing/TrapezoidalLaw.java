package org.bbnors.motion.timing;

/**
 * Accelerate / cruise / decelerate profile with constant acceleration in each ramp.
 * Velocity is continuous; acceleration is not. Included mainly as a comparison point because it is
 * what simple "eased" movement implementations tend to produce.
 */
public final class TrapezoidalLaw implements TimingLaw {

    private final double ta;
    private final double td;
    private final double vmax;

    public TrapezoidalLaw(double accelFraction, double decelFraction) {
        if (!(accelFraction > 0) || !(decelFraction > 0) || accelFraction + decelFraction > 1 + 1e-12) {
            throw new IllegalArgumentException("need accel > 0, decel > 0 and accel + decel <= 1");
        }
        this.ta = accelFraction;
        this.td = decelFraction;
        this.vmax = 1 / (1 - ta / 2 - td / 2);
    }

    @Override
    public double s(double t) {
        t = TimingLaw.clamp(t);
        if (t < ta) {
            return 0.5 * vmax * t * t / ta;
        }
        if (t <= 1 - td) {
            return vmax * (ta / 2 + (t - ta));
        }
        double r = 1 - t;
        return 1 - 0.5 * vmax * r * r / td;
    }

    @Override
    public double ds(double t) {
        t = TimingLaw.clamp(t);
        if (t < ta) {
            return vmax * t / ta;
        }
        if (t <= 1 - td) {
            return vmax;
        }
        return vmax * (1 - t) / td;
    }

    @Override
    public double dds(double t) {
        t = TimingLaw.clamp(t);
        if (t < ta) {
            return vmax / ta;
        }
        if (t <= 1 - td) {
            return 0;
        }
        return -vmax / td;
    }

    @Override
    public double peakTau() {
        return (ta + 1 - td) / 2;
    }

    @Override
    public String name() {
        return String.format("trapezoidal(%.2f,%.2f)", ta, td);
    }
}
