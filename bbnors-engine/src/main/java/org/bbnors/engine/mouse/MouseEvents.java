package org.bbnors.engine.mouse;

import org.bbnors.core.geom.Rect;
import org.bbnors.engine.input.MouseButton;
import org.bbnors.motion.Motion;

/** Events published by the {@link MouseController} for diagnostics and visualisation. */
public final class MouseEvents {

    private MouseEvents() {
    }

    /** A motion was planned and is about to play. {@code target} may be null for raw point moves. */
    public record MotionPlanned(Motion motion, Rect target, String profile, long startNanos) { }

    /** A motion finished (or was cut short). {@code completed=false} means cancelled mid-flight. */
    public record MotionFinished(Motion motion, boolean completed, int eventsSent, long endNanos) { }

    /** A button press/release was sent. */
    public record Button(MouseButton button, boolean pressed, int x, int y, long nanos) { }
}
