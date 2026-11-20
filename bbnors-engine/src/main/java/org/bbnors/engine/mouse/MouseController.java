package org.bbnors.engine.mouse;

import org.bbnors.core.event.EventBus;
import org.bbnors.core.geom.Rect;
import org.bbnors.core.geom.Vec2;
import org.bbnors.engine.input.InputBackend;
import org.bbnors.engine.input.MouseButton;
import org.bbnors.engine.interact.Channel;
import org.bbnors.engine.interact.ConflictPolicy;
import org.bbnors.engine.interact.InteractionManager;
import org.bbnors.engine.interact.OpContext;
import org.bbnors.engine.interact.OpHandle;
import org.bbnors.engine.interact.Operation;
import org.bbnors.engine.timing.TimingProfiles;
import org.bbnors.motion.Motion;
import org.bbnors.motion.MotionPlanner;
import org.bbnors.motion.MotionState;
import org.bbnors.motion.MovementProfile;
import org.bbnors.motion.MovementProfiles;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * The mouse movement engine: plans movements with the {@link MotionPlanner}, plays them back in
 * real time through the {@link InputBackend}, and performs clicks, drags and scrolls, all as
 * {@link Operation}s on the {@link Channel#MOUSE} channel.
 *
 * <p><b>Playback.</b> Updates are scheduled on absolute deadlines {@code start + k/rate}, so
 * timing error never accumulates. At each tick the motion is evaluated at the <em>actual</em>
 * elapsed time (if the thread was late, the cursor jumps to where it should be rather than
 * slowing the whole movement down), quantised to whole pixels, and sent only if the pixel
 * changed. The exact end point is always delivered.
 *
 * <p><b>Moving targets.</b> Targets are passed as {@code Supplier<Rect>}. After a movement ends
 * the target is re-read; if the cursor is no longer inside (the NPC walked, the interface
 * scrolled), up to {@code maxCorrections} short corrective movements are made.
 */
public final class MouseController {

    private final InteractionManager manager;
    private final InputBackend backend;
    private final MotionPlanner planner;
    private final MovementProfiles profiles;
    private final TimingProfiles timing;
    private final EventBus bus;
    private final AtomicReference<Vec2> position;
    private final Set<MouseButton> pressed = EnumSet.noneOf(MouseButton.class);
    private volatile String defaultProfile = MovementProfiles.DEFAULT;
    private volatile int maxCorrections = 2;

    public MouseController(InteractionManager manager, InputBackend backend, MotionPlanner planner,
                           MovementProfiles profiles, TimingProfiles timing, Vec2 initialPosition) {
        this.manager = manager;
        this.backend = backend;
        this.planner = planner;
        this.profiles = profiles;
        this.timing = timing;
        this.bus = manager.bus();
        this.position = new AtomicReference<>(initialPosition);
    }

    /** Last position sent to the backend (sub-pixel precision preserved). */
    public Vec2 position() {
        return position.get();
    }

    /** Re-synchronises the believed cursor position (e.g. after reading the real one). */
    public void syncPosition(Vec2 p) {
        position.set(Objects.requireNonNull(p));
    }

    public boolean isPressed(MouseButton b) {
        synchronized (pressed) {
            return pressed.contains(b);
        }
    }

    public void setDefaultProfile(String name) {
        profiles.get(name); // validate
        defaultProfile = name;
    }

    public String defaultProfile() {
        return defaultProfile;
    }

    public void setMaxCorrections(int n) {
        if (n < 0 || n > 10) throw new IllegalArgumentException("maxCorrections must be in [0, 10]");
        maxCorrections = n;
    }

    // ------------------------------------------------------------------ public operations

    /** Moves into {@code target} with the default profile. */
    public OpHandle<Vec2> moveTo(Rect target) {
        return moveTo(() -> target, defaultProfile);
    }

    public OpHandle<Vec2> moveTo(Supplier<Rect> target, String profile) {
        MovementProfile p = profiles.get(profile);
        return manager.submit(Operation.of("move", EnumSet.of(Channel.MOUSE), ctx -> doMove(ctx, target, p)));
    }

    /** Moves into the target and clicks once it is reached (single operation; nothing can interleave). */
    public OpHandle<Vec2> moveAndClick(Supplier<Rect> target, MouseButton button, String profile) {
        MovementProfile p = profiles.get(profile);
        return manager.submit(Operation.of("move+click(" + button + ")", EnumSet.of(Channel.MOUSE), ctx -> {
            Vec2 at = doMove(ctx, target, p);
            ctx.sleepSeconds(timing.get(TimingProfiles.MOUSE_PRE_CLICK).sample(ctx.rng()));
            doClick(ctx, button);
            return at;
        }));
    }

    public OpHandle<Vec2> moveAndClick(Rect target, MouseButton button) {
        return moveAndClick(() -> target, button, defaultProfile);
    }

    public OpHandle<Void> click(MouseButton button) {
        return manager.submit(Operation.of("click(" + button + ")", EnumSet.of(Channel.MOUSE), ctx -> {
            doClick(ctx, button);
            return null;
        }));
    }

    public OpHandle<Void> doubleClick(MouseButton button) {
        return manager.submit(Operation.of("double-click(" + button + ")", EnumSet.of(Channel.MOUSE), ctx -> {
            doClick(ctx, button);
            ctx.sleepSeconds(timing.get(TimingProfiles.MOUSE_DOUBLE_CLICK_GAP).sample(ctx.rng()));
            doClick(ctx, button);
            return null;
        }));
    }

    /** Presses in {@code from}, moves to {@code to} with the button held, releases. */
    public OpHandle<Vec2> drag(Rect from, Rect to, MouseButton button, String profile) {
        MovementProfile p = profiles.get(profile);
        return manager.submit(Operation.of("drag(" + button + ")", EnumSet.of(Channel.MOUSE), ctx -> {
            doMove(ctx, () -> from, p);
            ctx.sleepSeconds(timing.get(TimingProfiles.MOUSE_PRE_CLICK).sample(ctx.rng()));
            press(button);
            try {
                ctx.sleepSeconds(timing.get(TimingProfiles.MOUSE_CLICK_HOLD).sample(ctx.rng()));
                return doMove(ctx, () -> to, p);
            } finally {
                release(button); // never leave a button stuck, even on cancellation
            }
        }));
    }

    /** Scrolls {@code notches} wheel steps (sign = direction) with natural gaps. */
    public OpHandle<Void> scroll(int notches) {
        return manager.submit(Operation.of("scroll(" + notches + ")", EnumSet.of(Channel.MOUSE), ctx -> {
            int dir = Integer.signum(notches);
            for (int i = 0; i < Math.abs(notches); i++) {
                if (i > 0) {
                    ctx.sleepSeconds(timing.get(TimingProfiles.MOUSE_SCROLL_GAP).sample(ctx.rng()));
                }
                backend.mouseWheel(dir);
            }
            return null;
        }));
    }

    /**
     * Builds (but does not submit) a raw movement operation along an explicit motion, e.g. for the
     * camera controller's middle-button drags or for replaying a recorded trajectory.
     */
    public Operation<Vec2> playOperation(String name, Motion motion, double rateHz, int priority, ConflictPolicy policy) {
        return Operation.of(name, EnumSet.of(Channel.MOUSE), priority, policy, ctx -> {
            play(ctx, motion, rateHz, null, "explicit");
            return position();
        });
    }

    // ------------------------------------------------------------------ building blocks (run inside an operation)

    /** Plans and plays a movement into the target, with up to {@code maxCorrections} re-aims. */
    public Vec2 doMove(OpContext ctx, Supplier<Rect> target, MovementProfile p) {
        Rect t = target.get();
        Motion m = planner.plan(p, position(), t, ctx.rng());
        play(ctx, m, p.sampleRateHz(), t, p.name());
        for (int i = 0; i < maxCorrections; i++) {
            Rect now = target.get();
            if (now == null || now.contains(position())) {
                break;
            }
            MovementProfile corr = p.toBuilder().overshootChance(0).build();
            Motion c = planner.plan(corr, position(), now, ctx.rng());
            play(ctx, c, p.sampleRateHz(), now, p.name() + "/correction");
        }
        return position();
    }

    /** Plays {@code m} in real time on the operation's clock. */
    public void play(OpContext ctx, Motion m, double rateHz, Rect target, String profileName) {
        long start = ctx.clock().nanos();
        bus.publish(new MouseEvents.MotionPlanned(m, target, profileName, start));
        double duration = m.duration();
        long periodNanos = (long) (1e9 / rateHz);
        MotionState s = new MotionState();
        int lastX = (int) Math.round(position().x());
        int lastY = (int) Math.round(position().y());
        int sent = 0;
        boolean completed = false;
        try {
            for (long k = 1; ; k++) {
                long deadline = start + k * periodNanos;
                double tPlanned = (deadline - start) / 1e9;
                if (tPlanned >= duration) {
                    break;
                }
                ctx.sleepUntil(deadline);
                double t = Math.min(duration, (ctx.clock().nanos() - start) / 1e9);
                m.state(t, s);
                sent += emit(s.x, s.y, lastX, lastY);
                lastX = (int) Math.round(s.x);
                lastY = (int) Math.round(s.y);
            }
            ctx.sleepUntil(start + (long) (duration * 1e9));
            Vec2 end = m.end();
            sent += emit(end.x(), end.y(), lastX, lastY);
            completed = true;
        } finally {
            bus.publish(new MouseEvents.MotionFinished(m, completed, sent, ctx.clock().nanos()));
        }
    }

    private int emit(double x, double y, int lastX, int lastY) {
        int ix = (int) Math.round(x);
        int iy = (int) Math.round(y);
        position.set(new Vec2(x, y));
        if (ix != lastX || iy != lastY) {
            backend.mouseMove(ix, iy);
            return 1;
        }
        return 0;
    }

    public void doClick(OpContext ctx, MouseButton button) {
        press(button);
        try {
            ctx.sleepSeconds(timing.get(TimingProfiles.MOUSE_CLICK_HOLD).sample(ctx.rng()));
        } finally {
            release(button);
        }
    }

    private void press(MouseButton b) {
        synchronized (pressed) {
            pressed.add(b);
        }
        backend.mousePress(b);
        Vec2 p = position();
        bus.publish(new MouseEvents.Button(b, true, p.ix(), p.iy(), manager.clock().nanos()));
    }

    private void release(MouseButton b) {
        boolean was;
        synchronized (pressed) {
            was = pressed.remove(b);
        }
        if (was) {
            backend.mouseRelease(b);
            Vec2 p = position();
            bus.publish(new MouseEvents.Button(b, false, p.ix(), p.iy(), manager.clock().nanos()));
        }
    }

    /** Releases every held button (safety net used on script stop). */
    public void releaseAll() {
        for (MouseButton b : MouseButton.values()) {
            release(b);
        }
    }

    /** Presses a button inside an operation without releasing (caller must release). */
    public void pressRaw(MouseButton b) {
        press(b);
    }

    public void releaseRaw(MouseButton b) {
        release(b);
    }
}
