package org.bbnors.engine.camera;

import org.bbnors.core.geom.Rect;
import org.bbnors.core.geom.Vec2;
import org.bbnors.engine.input.MouseButton;
import org.bbnors.engine.interact.Channel;
import org.bbnors.engine.interact.InteractionManager;
import org.bbnors.engine.interact.OpContext;
import org.bbnors.engine.interact.OpHandle;
import org.bbnors.engine.interact.Operation;
import org.bbnors.engine.keyboard.KeyboardController;
import org.bbnors.engine.mouse.MouseController;
import org.bbnors.engine.timing.TimingProfiles;
import org.bbnors.motion.MotionPlanner;
import org.bbnors.motion.MovementProfiles;
import org.bbnors.motion.timing.TimingLaws;

import java.util.EnumSet;

/**
 * Camera interaction engine. Rotations are closed-loop: the controller computes how long to hold
 * the rotation keys (or how far to drag) from the model's measured rates, executes, re-reads the
 * actual camera state, and corrects with shorter actions until within tolerance or out of
 * attempts. This makes it robust to unknown frame timing and to rate calibration error.
 *
 * <p>Modes:
 * <ul>
 *   <li>{@link Mode#KEYS}: yaw and pitch keys held <em>simultaneously</em>, each released at its own
 *       computed time ({@link Channel#CAMERA} + {@link Channel#KEYBOARD}; the mouse stays free).</li>
 *   <li>{@link Mode#DRAG}: middle-button drag planned by the movement engine
 *       ({@link Channel#CAMERA} + {@link Channel#MOUSE}).</li>
 *   <li>{@link Mode#DIRECT}: minimum-jerk interpolation through {@link CameraModel#setDirect}, for
 *       clients that expose direct control (only {@link Channel#CAMERA}).</li>
 * </ul>
 */
public final class CameraController {

    public enum Mode { KEYS, DRAG, DIRECT }

    /** Result of a rotation: final state and how many corrective passes were needed. */
    public record Result(int yaw, int pitch, int passes, boolean withinTolerance) { }

    private final InteractionManager manager;
    private final CameraModel model;
    private final KeyboardController keyboard;
    private final MouseController mouse;
    private final MotionPlanner planner;
    private final MovementProfiles profiles;
    private final TimingProfiles timing;
    private volatile int yawTolerance = 24;   // ≈ 4.2° at 2048 units/rev
    private volatile int pitchTolerance = 12;
    private volatile int maxPasses = 4;
    /** Fraction of the computed hold time actually used; <1 approaches from below, avoiding overshoot. */
    private volatile double holdFactor = 0.92;

    public CameraController(InteractionManager manager, CameraModel model, KeyboardController keyboard, MouseController mouse,
                            MotionPlanner planner, MovementProfiles profiles, TimingProfiles timing) {
        this.manager = manager;
        this.model = model;
        this.keyboard = keyboard;
        this.mouse = mouse;
        this.planner = planner;
        this.profiles = profiles;
        this.timing = timing;
    }

    public CameraModel model() {
        return model;
    }

    public void setTolerance(int yawUnits, int pitchUnits) {
        if (yawUnits < 1 || pitchUnits < 1) throw new IllegalArgumentException("tolerances must be >= 1");
        yawTolerance = yawUnits;
        pitchTolerance = pitchUnits;
    }

    public void setMaxPasses(int n) {
        if (n < 1 || n > 20) throw new IllegalArgumentException("maxPasses must be in [1, 20]");
        maxPasses = n;
    }

    public void setHoldFactor(double f) {
        if (!(f > 0.3 && f <= 1.2)) throw new IllegalArgumentException("holdFactor must be in (0.3, 1.2]");
        holdFactor = f;
    }

    /** Rotates yaw only (pitch unchanged). */
    public OpHandle<Result> rotateToYaw(int yaw, Mode mode) {
        return rotateTo(yaw, -1, mode);
    }

    /**
     * @param yaw   target yaw (wrapped into range)
     * @param pitch target pitch, or -1 to leave unchanged
     */
    public OpHandle<Result> rotateTo(int yaw, int pitch, Mode mode) {
        int ty = Math.floorMod(yaw, model.yawRange());
        int tp = pitch < 0 ? -1 : Math.max(model.minPitch(), Math.min(model.maxPitch(), pitch));
        EnumSet<Channel> ch = switch (mode) {
            case KEYS -> EnumSet.of(Channel.CAMERA, Channel.KEYBOARD);
            case DRAG -> EnumSet.of(Channel.CAMERA, Channel.MOUSE);
            case DIRECT -> EnumSet.of(Channel.CAMERA);
        };
        if (mode == Mode.DIRECT && !model.supportsDirect()) {
            throw new UnsupportedOperationException("camera model does not support direct control");
        }
        return manager.submit(Operation.of("camera(" + mode + " yaw=" + ty + (tp >= 0 ? " pitch=" + tp : "") + ")", ch, ctx -> switch (mode) {
            case KEYS -> rotateKeys(ctx, ty, tp);
            case DRAG -> rotateDrag(ctx, ty, tp);
            case DIRECT -> rotateDirect(ctx, ty, tp, 0.35);
        }));
    }

    private Result rotateKeys(OpContext ctx, int ty, int tp) {
        int pass = 0;
        while (true) {
            int dy = CameraModel.yawDelta(model.yaw(), ty, model.yawRange());
            int dp = tp < 0 ? 0 : tp - model.pitch();
            boolean yawOk = Math.abs(dy) <= yawTolerance;
            boolean pitchOk = Math.abs(dp) <= pitchTolerance;
            if ((yawOk && pitchOk) || pass >= maxPasses) {
                return new Result(model.yaw(), model.pitch(), pass, yawOk && pitchOk);
            }
            pass++;
            if (pass > 1) {
                ctx.sleepSeconds(timing.get(TimingProfiles.CAMERA_CORRECTION_GAP).sample(ctx.rng()));
            }
            double holdYaw = yawOk ? 0 : Math.abs(dy) / model.yawSpeed() * holdFactor;
            double holdPitch = pitchOk ? 0 : Math.abs(dp) / model.pitchSpeed() * holdFactor;
            int yawKey = dy > 0 ? model.keyYawIncrease() : model.keyYawDecrease();
            int pitchKey = dp > 0 ? model.keyPitchIncrease() : model.keyPitchDecrease();
            holdTwo(ctx, holdYaw > 0 ? yawKey : -1, holdYaw, holdPitch > 0 ? pitchKey : -1, holdPitch);
        }
    }

    /** Holds up to two keys at once, releasing each after its own duration. */
    private void holdTwo(OpContext ctx, int keyA, double secA, int keyB, double secB) {
        long start = ctx.clock().nanos();
        boolean aDown = false;
        boolean bDown = false;
        try {
            // small natural stagger: the second key goes down a few ms after the first
            if (keyA >= 0) {
                keyboard.press(keyA, java.awt.event.KeyEvent.CHAR_UNDEFINED);
                aDown = true;
            }
            if (keyB >= 0) {
                if (aDown) {
                    ctx.sleepSeconds(ctx.rng().uniform(0.008, 0.030));
                }
                keyboard.press(keyB, java.awt.event.KeyEvent.CHAR_UNDEFINED);
                bDown = true;
            }
            long endA = start + (long) (secA * 1e9);
            long endB = start + (long) (secB * 1e9);
            if (aDown && (!bDown || endA <= endB)) {
                ctx.sleepUntil(endA);
                keyboard.release(keyA, java.awt.event.KeyEvent.CHAR_UNDEFINED);
                aDown = false;
                if (bDown) {
                    ctx.sleepUntil(endB);
                }
            } else if (bDown) {
                ctx.sleepUntil(endB);
                keyboard.release(keyB, java.awt.event.KeyEvent.CHAR_UNDEFINED);
                bDown = false;
                if (aDown) {
                    ctx.sleepUntil(endA);
                }
            }
        } finally {
            if (aDown) keyboard.release(keyA, java.awt.event.KeyEvent.CHAR_UNDEFINED);
            if (bDown) keyboard.release(keyB, java.awt.event.KeyEvent.CHAR_UNDEFINED);
        }
    }

    private Result rotateDrag(OpContext ctx, int ty, int tp) {
        int pass = 0;
        Rect area = model.dragArea();
        while (true) {
            int dy = CameraModel.yawDelta(model.yaw(), ty, model.yawRange());
            int dp = tp < 0 ? 0 : tp - model.pitch();
            boolean ok = Math.abs(dy) <= yawTolerance && Math.abs(dp) <= pitchTolerance;
            if (ok || pass >= maxPasses) {
                return new Result(model.yaw(), model.pitch(), pass, ok);
            }
            pass++;
            double px = dy / model.dragYawPerPixel() * holdFactor;
            double py = dp / model.dragPitchPerPixel() * holdFactor;
            // Choose a start point that leaves room for the drag inside the drag area.
            double sx = px >= 0 ? area.x() + area.width() * 0.25 : area.x() + area.width() * 0.75;
            double sy = py >= 0 ? area.y() + area.height() * 0.3 : area.y() + area.height() * 0.7;
            px = Math.max(area.x() - sx, Math.min(area.x() + area.width() - sx, px));
            py = Math.max(area.y() - sy, Math.min(area.y() + area.height() - sy, py));
            Rect startBox = Rect.centered(new Vec2(sx, sy), 30, 30);
            mouse.doMove(ctx, () -> startBox, profiles.get(MovementProfiles.DEFAULT));
            Vec2 from = mouse.position();
            Vec2 to = from.add(new Vec2(px, py));
            mouse.pressRaw(MouseButton.MIDDLE);
            try {
                ctx.sleepSeconds(ctx.rng().uniform(0.03, 0.07));
                var p = profiles.get(MovementProfiles.DEFAULT).toBuilder().overshootChance(0).endpointSpread(0).build();
                var motion = planner.planTo(p, from, to, 40, ctx.rng());
                mouse.play(ctx, motion, p.sampleRateHz(), null, "camera-drag");
            } finally {
                mouse.releaseRaw(MouseButton.MIDDLE);
            }
            ctx.sleepSeconds(timing.get(TimingProfiles.CAMERA_CORRECTION_GAP).sample(ctx.rng()));
        }
    }

    /** Smooth minimum-jerk interpolation along the shortest yaw arc, updated at 60 Hz. */
    private Result rotateDirect(OpContext ctx, int ty, int tp, double seconds) {
        int y0 = model.yaw();
        int p0 = model.pitch();
        int dy = CameraModel.yawDelta(y0, ty, model.yawRange());
        int dp = tp < 0 ? 0 : tp - p0;
        long start = ctx.clock().nanos();
        long period = 1_000_000_000L / 60;
        for (long k = 1; ; k++) {
            long deadline = start + k * period;
            double tau = Math.min(1, (deadline - start) / 1e9 / seconds);
            ctx.sleepUntil(deadline);
            double s = TimingLaws.MINIMUM_JERK.s(tau);
            model.setDirect(Math.floorMod(y0 + (int) Math.round(dy * s), model.yawRange()), p0 + (int) Math.round(dp * s));
            if (tau >= 1) {
                break;
            }
        }
        return new Result(model.yaw(), model.pitch(), 1, true);
    }
}
