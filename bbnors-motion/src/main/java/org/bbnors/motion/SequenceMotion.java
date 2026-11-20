package org.bbnors.motion;

import org.bbnors.core.geom.Vec2;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Concatenation of motions with optional pauses between them, e.g. a primary ballistic movement
 * followed by a short corrective sub-movement. Each part must start where the previous ended.
 * During a pause the cursor is stationary at the previous part's end point.
 */
public final class SequenceMotion implements Motion {

    private final Motion[] parts;
    private final double[] pauses;  // pause AFTER part i (last ignored)
    private final double[] starts;  // start time of part i
    private final double duration;

    public SequenceMotion(List<Motion> parts, List<Double> pausesAfter) {
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("no parts");
        }
        if (pausesAfter.size() != parts.size() - 1) {
            throw new IllegalArgumentException("need exactly parts-1 pauses");
        }
        this.parts = parts.toArray(new Motion[0]);
        this.pauses = new double[this.parts.length];
        this.starts = new double[this.parts.length];
        double t = 0;
        for (int i = 0; i < this.parts.length; i++) {
            if (i > 0 && this.parts[i - 1].end().distance(this.parts[i].start()) > 1e-6) {
                throw new IllegalArgumentException("part " + i + " does not start where part " + (i - 1) + " ends");
            }
            starts[i] = t;
            t += this.parts[i].duration();
            if (i < this.parts.length - 1) {
                double p = pausesAfter.get(i);
                if (p < 0) {
                    throw new IllegalArgumentException("negative pause");
                }
                pauses[i] = p;
                t += p;
            }
        }
        this.duration = t;
    }

    @Override
    public double duration() {
        return duration;
    }

    @Override
    public void state(double t, MotionState out) {
        double tc = t < 0 ? 0 : Math.min(t, duration);
        int i = parts.length - 1;
        while (i > 0 && tc < starts[i]) {
            i--;
        }
        double local = tc - starts[i];
        Motion m = parts[i];
        if (local > m.duration()) {
            // inside the pause after part i: stationary at its end
            Vec2 e = m.end();
            out.x = e.x();
            out.y = e.y();
            out.vx = out.vy = out.speed = out.accTangential = out.accNormal = out.ax = out.ay = out.curvature = 0;
        } else {
            m.state(local, out);
        }
        out.t = tc;
        out.segment = i;
    }

    public List<Motion> parts() {
        return List.of(parts);
    }

    @Override
    public Vec2 start() {
        return parts[0].start();
    }

    @Override
    public Vec2 end() {
        return parts[parts.length - 1].end();
    }

    @Override
    public String describe() {
        return List.of(parts).stream().map(Motion::describe).collect(Collectors.joining(" -> ", "[", "]"));
    }
}
