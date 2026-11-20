package org.bbnors.motion.path;

import org.bbnors.core.geom.Vec2;

import java.util.List;

/**
 * Bezier curve of arbitrary degree {@code n = controlPoints - 1}.
 *
 * <p><b>Evaluation strategy.</b> The Bernstein form is converted once, at construction, into
 * power-basis coefficients {@code P(u) = Σ a_k u^k} with
 * {@code a_k = C(n,k) Σ_{i=0..k} (-1)^(k-i) C(k,i) P_i}. Position, first and second derivative are
 * then evaluated together with a single Horner pass: O(n) per call, no allocation and no scratch
 * state, so instances are freely shareable between threads. De Casteljau is more robust for very
 * high degrees, but for the degrees used for cursor paths (2-7) on {@code u ∈ [0,1]} the power
 * basis is accurate to well below 1e-9 px and roughly 3-4x cheaper.
 */
public final class BezierPath implements Path {

    private final Vec2[] control;
    private final double[] ax;
    private final double[] ay;

    public BezierPath(List<Vec2> controlPoints) {
        this(controlPoints.toArray(new Vec2[0]));
    }

    public BezierPath(Vec2... controlPoints) {
        if (controlPoints.length < 2) {
            throw new IllegalArgumentException("need at least 2 control points");
        }
        if (controlPoints.length > 16) {
            throw new IllegalArgumentException("degree > 15 is not supported (numerically unstable in power basis)");
        }
        this.control = controlPoints.clone();
        int n = control.length - 1;
        ax = new double[n + 1];
        ay = new double[n + 1];
        for (int k = 0; k <= n; k++) {
            double sx = 0;
            double sy = 0;
            for (int i = 0; i <= k; i++) {
                double c = binomial(k, i) * (((k - i) & 1) == 0 ? 1 : -1);
                sx += c * control[i].x();
                sy += c * control[i].y();
            }
            double ck = binomial(n, k);
            ax[k] = ck * sx;
            ay[k] = ck * sy;
        }
    }

    public int degree() {
        return control.length - 1;
    }

    public List<Vec2> controlPoints() {
        return List.of(control);
    }

    @Override
    public void eval(double u, double[] out) {
        u = Path.clamp01(u);
        int n = ax.length - 1;
        // Horner for p, p', p'' simultaneously.
        double px = ax[n];
        double py = ay[n];
        double dx = 0;
        double dy = 0;
        double ddx = 0;
        double ddy = 0;
        for (int k = n - 1; k >= 0; k--) {
            ddx = ddx * u + 2 * dx;
            ddy = ddy * u + 2 * dy;
            dx = dx * u + px;
            dy = dy * u + py;
            px = px * u + ax[k];
            py = py * u + ay[k];
        }
        out[0] = px;
        out[1] = py;
        out[2] = dx;
        out[3] = dy;
        out[4] = ddx;
        out[5] = ddy;
    }

    @Override
    public Vec2 start() {
        return control[0];
    }

    @Override
    public Vec2 end() {
        return control[control.length - 1];
    }

    static double binomial(int n, int k) {
        double r = 1;
        for (int i = 1; i <= k; i++) {
            r = r * (n - k + i) / i;
        }
        return r;
    }
}
