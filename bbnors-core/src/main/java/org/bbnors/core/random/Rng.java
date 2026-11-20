package org.bbnors.core.random;

import java.util.SplittableRandom;

/**
 * Seedable random source with the distributions the interaction engine needs.
 *
 * <p>Every stochastic component takes an {@code Rng} rather than calling {@code Math.random()},
 * so any trajectory, timing sequence or benchmark can be reproduced exactly from its seed.
 * Instances are <b>not</b> thread-safe; use {@link #split()} to hand an independent stream to
 * another thread.
 */
public final class Rng {

    private final SplittableRandom random;
    private double spareGaussian;
    private boolean hasSpare;

    public Rng(long seed) {
        this.random = new SplittableRandom(seed);
    }

    private Rng(SplittableRandom random) {
        this.random = random;
    }

    /** A new generator with an independent stream derived from this one. */
    public Rng split() {
        return new Rng(random.split());
    }

    public double nextDouble() {
        return random.nextDouble();
    }

    public int nextInt(int boundExclusive) {
        return random.nextInt(boundExclusive);
    }

    public long nextLong() {
        return random.nextLong();
    }

    public boolean chance(double probability) {
        return random.nextDouble() < probability;
    }

    /** Uniform in {@code [min, max)}. */
    public double uniform(double min, double max) {
        return min + (max - min) * random.nextDouble();
    }

    /** Standard normal via the polar Box-Muller method (caches the second variate). */
    public double gaussian() {
        if (hasSpare) {
            hasSpare = false;
            return spareGaussian;
        }
        double u;
        double v;
        double s;
        do {
            u = random.nextDouble() * 2 - 1;
            v = random.nextDouble() * 2 - 1;
            s = u * u + v * v;
        } while (s >= 1 || s == 0);
        double mul = Math.sqrt(-2.0 * Math.log(s) / s);
        spareGaussian = v * mul;
        hasSpare = true;
        return u * mul;
    }

    public double gaussian(double mean, double stdDev) {
        return mean + stdDev * gaussian();
    }

    /**
     * Normal distribution truncated to {@code [min, max]} by rejection (falls back to clamping
     * after 32 rejections so pathological bounds cannot loop forever).
     */
    public double truncatedGaussian(double mean, double stdDev, double min, double max) {
        if (min > max) {
            throw new IllegalArgumentException("min > max");
        }
        for (int i = 0; i < 32; i++) {
            double v = gaussian(mean, stdDev);
            if (v >= min && v <= max) {
                return v;
            }
        }
        return Math.max(min, Math.min(max, mean));
    }

    /**
     * Log-normal parameterised by the desired <em>median</em> and the shape {@code sigma}.
     * Reaction and inter-action delays are right-skewed, which a symmetric Gaussian cannot model.
     */
    public double logNormal(double median, double sigma) {
        return median * Math.exp(sigma * gaussian());
    }
}
