package org.bbnors.motion.path;

import org.bbnors.core.geom.Vec2;

/** Straight segment. Baseline for comparisons; real profiles rarely use it alone. */
public final class LinearPath implements Path {

    private final Vec2 a;
    private final Vec2 b;
    private final double dx;
    private final double dy;

    public LinearPath(Vec2 a, Vec2 b) {
        this.a = a;
        this.b = b;
        this.dx = b.x() - a.x();
        this.dy = b.y() - a.y();
    }

    @Override
    public void eval(double u, double[] out) {
        u = Path.clamp01(u);
        out[0] = a.x() + dx * u;
        out[1] = a.y() + dy * u;
        out[2] = dx;
        out[3] = dy;
        out[4] = 0;
        out[5] = 0;
    }

    @Override
    public Vec2 start() {
        return a;
    }

    @Override
    public Vec2 end() {
        return b;
    }
}
