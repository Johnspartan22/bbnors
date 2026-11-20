package org.bbnors.motion;

import org.bbnors.core.geom.Rect;
import org.bbnors.core.geom.Vec2;
import org.bbnors.core.random.Rng;

/**
 * Chooses the exact pixel a movement aims at inside a target rectangle.
 *
 * <p>Points are drawn from an independent 2D Gaussian centred on the target, with standard
 * deviation {@code spread × side / 2} per axis, truncated to the target shrunk by a 1 px margin.
 * {@code spread = 0} always aims at the centre; {@code spread ≈ 0.35} puts ~95% of points in the
 * middle 70% of each side. The point is guaranteed to lie inside the target, which is the
 * endpoint-precision contract every other component relies on.
 */
public final class EndpointModel {

    private EndpointModel() {
    }

    public static Vec2 pick(Rect target, double spread, Rng rng) {
        Rect safe = target.width() > 2 && target.height() > 2 ? target.inset(1) : target;
        Vec2 c = safe.center();
        if (spread <= 0 || safe.width() == 0 && safe.height() == 0) {
            return c;
        }
        double sx = spread * safe.width() / 2;
        double sy = spread * safe.height() / 2;
        double x = sx > 0 ? rng.truncatedGaussian(c.x(), sx, safe.x(), safe.x() + safe.width()) : c.x();
        double y = sy > 0 ? rng.truncatedGaussian(c.y(), sy, safe.y(), safe.y() + safe.height()) : c.y();
        return new Vec2(x, y);
    }
}
