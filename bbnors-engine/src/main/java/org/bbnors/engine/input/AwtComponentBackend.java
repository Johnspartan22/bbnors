package org.bbnors.engine.input;

import java.awt.Component;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Delivers input as synthetic AWT events dispatched directly to a component (typically the game
 * canvas of a Java client loaded in the same JVM). No OS cursor is involved, so several clients can
 * be driven side by side.
 *
 * <p>The backend tracks the button/modifier masks itself so generated events carry consistent
 * {@code getModifiersEx()} values (e.g. a move while the middle button is held is delivered as a
 * {@code MOUSE_DRAGGED}). A click event is synthesised on release when the cursor has not moved
 * since the press, matching AWT's own behaviour.
 */
public final class AwtComponentBackend implements InputBackend {

    private final Component target;
    private final AtomicInteger modifiers = new AtomicInteger();
    private volatile int x;
    private volatile int y;
    private volatile boolean movedSincePress;
    private volatile boolean inside;

    public AwtComponentBackend(Component target) {
        this.target = target;
    }

    private void dispatch(java.awt.AWTEvent e) {
        target.dispatchEvent(e);
    }

    private long now() {
        return System.currentTimeMillis();
    }

    @Override
    public synchronized void mouseMove(int nx, int ny) {
        boolean nowInside = nx >= 0 && ny >= 0 && nx < target.getWidth() && ny < target.getHeight();
        if (nowInside && !inside) {
            dispatch(new MouseEvent(target, MouseEvent.MOUSE_ENTERED, now(), modifiers.get(), nx, ny, 0, false));
        }
        boolean dragging = (modifiers.get() & (InputEvent.BUTTON1_DOWN_MASK | InputEvent.BUTTON2_DOWN_MASK | InputEvent.BUTTON3_DOWN_MASK)) != 0;
        int id = dragging ? MouseEvent.MOUSE_DRAGGED : MouseEvent.MOUSE_MOVED;
        dispatch(new MouseEvent(target, id, now(), modifiers.get(), nx, ny, 0, false));
        if (!nowInside && inside) {
            dispatch(new MouseEvent(target, MouseEvent.MOUSE_EXITED, now(), modifiers.get(), nx, ny, 0, false));
        }
        inside = nowInside;
        x = nx;
        y = ny;
        movedSincePress = true;
    }

    @Override
    public synchronized void mousePress(MouseButton b) {
        int mods = modifiers.updateAndGet(m -> m | b.downMask());
        movedSincePress = false;
        dispatch(new MouseEvent(target, MouseEvent.MOUSE_PRESSED, now(), mods, x, y, 1, b == MouseButton.RIGHT, b.awtButton()));
    }

    @Override
    public synchronized void mouseRelease(MouseButton b) {
        int mods = modifiers.updateAndGet(m -> m & ~b.downMask());
        dispatch(new MouseEvent(target, MouseEvent.MOUSE_RELEASED, now(), mods, x, y, 1, false, b.awtButton()));
        if (!movedSincePress) {
            dispatch(new MouseEvent(target, MouseEvent.MOUSE_CLICKED, now(), mods, x, y, 1, false, b.awtButton()));
        }
    }

    @Override
    public synchronized void mouseWheel(int notches) {
        dispatch(new MouseWheelEvent(target, MouseEvent.MOUSE_WHEEL, now(), modifiers.get(), x, y, 0, false,
                MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, notches));
    }

    @Override
    public synchronized void keyPress(int keyCode, char keyChar) {
        int mask = modifierMask(keyCode);
        int mods = mask != 0 ? modifiers.updateAndGet(m -> m | mask) : modifiers.get();
        dispatch(new KeyEvent(target, KeyEvent.KEY_PRESSED, now(), mods, keyCode, keyChar));
        if (keyChar != KeyEvent.CHAR_UNDEFINED) {
            dispatch(new KeyEvent(target, KeyEvent.KEY_TYPED, now(), mods, KeyEvent.VK_UNDEFINED, keyChar));
        }
    }

    @Override
    public synchronized void keyRelease(int keyCode, char keyChar) {
        int mask = modifierMask(keyCode);
        int mods = mask != 0 ? modifiers.updateAndGet(m -> m & ~mask) : modifiers.get();
        dispatch(new KeyEvent(target, KeyEvent.KEY_RELEASED, now(), mods, keyCode, keyChar));
    }

    private static int modifierMask(int keyCode) {
        return switch (keyCode) {
            case KeyEvent.VK_SHIFT -> InputEvent.SHIFT_DOWN_MASK;
            case KeyEvent.VK_CONTROL -> InputEvent.CTRL_DOWN_MASK;
            case KeyEvent.VK_ALT -> InputEvent.ALT_DOWN_MASK;
            default -> 0;
        };
    }
}
