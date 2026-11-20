package org.bbnors.motion.path;

/**
 * Arc-length reparameterisation of a {@link Path}: maps travelled distance {@code s} to curve
 * parameter {@code u}.
 *
 * <p><b>Construction</b> divides {@code [0,1]} into about {@code n} parameter intervals, aligned
 * to the path's {@link Path#breakpoints() breakpoints} so no interval straddles a segment join, and
 * integrates the speed {@code |P'(u)|} over each with 5-point Gauss-Legendre quadrature (exact for
 * polynomials up to degree 9; the integrand is smooth, so the error is far below a pixel even for
 * strongly curved paths). The cumulative lengths and the speed at every knot are stored.
 *
 * <p><b>Lookup</b> finds the interval by binary search and interpolates {@code u(s)} with a cubic
 * Hermite using the exact derivative {@code du/ds = 1/|P'(u)|} at both knots. This is 4th-order
 * accurate, versus 2nd-order for the usual linear interpolation, at the same table size. If the
 * speed at a knot is (near) zero, i.e. a cusp or coincident control points, that end falls back
 * to the secant slope so the interpolant stays monotone.
 *
 * <p>Immutable and thread-safe; {@link #paramAt(double)} does not allocate.
 */
public final class ArcLengthTable {

    private static final double[] GL_X = {
            -0.9061798459386640, -0.5384693101056831, 0.0, 0.5384693101056831, 0.9061798459386640};
    private static final double[] GL_W = {
            0.2369268850561891, 0.4786286704993665, 0.5688888888888889, 0.4786286704993665, 0.2369268850561891};

    public static final int DEFAULT_INTERVALS = 128;

    private final int n;
    private final double[] knots; // parameter value of knot i (n+1 entries)
    private final double[] cum;   // cumulative length at knot i (n+1 entries)
    private final double[] speed; // |P'(u_i)| at knot i (n+1 entries)

    public ArcLengthTable(Path path) {
        this(path, DEFAULT_INTERVALS);
    }

    public ArcLengthTable(Path path, int intervals) {
        if (intervals < 1) {
            throw new IllegalArgumentException("intervals must be >= 1");
        }
        this.knots = buildKnots(path.breakpoints(), intervals);
        this.n = knots.length - 1;
        this.cum = new double[n + 1];
        this.speed = new double[n + 1];
        double[] e = new double[6];
        for (int i = 0; i <= n; i++) {
            path.eval(knots[i], e);
            speed[i] = Math.hypot(e[2], e[3]);
        }
        double total = 0;
        for (int i = 0; i < n; i++) {
            double h = knots[i + 1] - knots[i];
            double mid = (knots[i] + knots[i + 1]) / 2;
            double seg = 0;
            for (int k = 0; k < 5; k++) {
                path.eval(mid + GL_X[k] * h / 2, e);
                seg += GL_W[k] * Math.hypot(e[2], e[3]);
            }
            total += seg * h / 2;
            cum[i + 1] = total;
        }
    }

    private static double[] buildKnots(double[] breaks, int intervals) {
        int pieces = breaks.length - 1;
        int[] counts = new int[pieces];
        int total = 0;
        for (int p = 0; p < pieces; p++) {
            counts[p] = Math.max(2, (int) Math.ceil(intervals * (breaks[p + 1] - breaks[p])));
            total += counts[p];
        }
        double[] k = new double[total + 1];
        int idx = 0;
        for (int p = 0; p < pieces; p++) {
            double a = breaks[p];
            double w = breaks[p + 1] - a;
            for (int j = 0; j < counts[p]; j++) {
                k[idx++] = a + w * j / counts[p];
            }
        }
        k[total] = 1.0;
        return k;
    }

    /** Total arc length in pixels. */
    public double length() {
        return cum[n];
    }

    /** Curve parameter at distance {@code s} from the start ({@code s} clamped to [0, length]). */
    public double paramAt(double s) {
        double len = cum[n];
        if (s <= 0 || len <= 0) {
            return 0;
        }
        if (s >= len) {
            return 1;
        }
        int lo = 0;
        int hi = n;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (cum[mid] <= s) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        double s0 = cum[lo];
        double ds = cum[hi] - s0;
        double u0 = knots[lo];
        double h = knots[hi] - u0;
        if (ds <= 1e-12) {
            return u0;
        }
        double t = (s - s0) / ds;
        double secant = h / ds;
        double m0 = slope(speed[lo], secant) * ds;
        double m1 = slope(speed[hi], secant) * ds;
        double t2 = t * t;
        double t3 = t2 * t;
        double u = u0 * (2 * t3 - 3 * t2 + 1) + m0 * (t3 - 2 * t2 + t) + (u0 + h) * (-2 * t3 + 3 * t2) + m1 * (t3 - t2);
        return Math.max(u0, Math.min(u0 + h, u));
    }

    private static double slope(double speedAtKnot, double secant) {
        if (speedAtKnot < 1e-9) {
            return secant;
        }
        double m = 1.0 / speedAtKnot;
        // Limit to 3x the secant (Fritsch-Carlson style) to preserve monotonicity.
        return Math.min(m, 3 * secant);
    }

    public int intervals() {
        return n;
    }
}
