package org.bbnors.engine.keyboard;

import org.bbnors.engine.input.InputBackend;
import org.bbnors.engine.input.KeyMap;
import org.bbnors.engine.interact.Channel;
import org.bbnors.engine.interact.InteractionManager;
import org.bbnors.engine.interact.OpContext;
import org.bbnors.engine.interact.OpHandle;
import org.bbnors.engine.interact.Operation;
import org.bbnors.engine.timing.TimingProfiles;

import java.awt.event.KeyEvent;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Centralised keyboard input: key-state tracking, typing with per-key timing profiles, holds and
 * chords, all as {@link Channel#KEYBOARD} operations.
 *
 * <p>Every operation releases the keys it pressed in a {@code finally} block, so cancelling a
 * script mid-word can never leave SHIFT (or an arrow key) stuck down. {@link #pressedKeys()}
 * exposes the tracked state for diagnostics and tests.
 */
public final class KeyboardController {

    private final InteractionManager manager;
    private final InputBackend backend;
    private final TimingProfiles timing;
    private final Set<Integer> down = new HashSet<>();

    public KeyboardController(InteractionManager manager, InputBackend backend, TimingProfiles timing) {
        this.manager = manager;
        this.backend = backend;
        this.timing = timing;
    }

    /** Types {@code text} character by character. Unsupported characters fail the operation up front. */
    public OpHandle<Void> type(String text) {
        for (char c : text.toCharArray()) {
            if (KeyMap.lookup(c) == null) {
                throw new IllegalArgumentException("cannot type character U+" + Integer.toHexString(c));
            }
        }
        return manager.submit(Operation.of("type(" + text.length() + " chars)", EnumSet.of(Channel.KEYBOARD), ctx -> {
            doType(ctx, text);
            return null;
        }));
    }

    /** Presses and releases one key (e.g. {@link KeyEvent#VK_ESCAPE}, {@link KeyEvent#VK_F1}). */
    public OpHandle<Void> tap(int keyCode) {
        return manager.submit(Operation.of("tap(" + KeyEvent.getKeyText(keyCode) + ")", EnumSet.of(Channel.KEYBOARD), ctx -> {
            doTap(ctx, keyCode, KeyEvent.CHAR_UNDEFINED);
            return null;
        }));
    }

    /** Holds all {@code keyCodes} (pressed in order) for {@code seconds}, then releases in reverse. */
    public OpHandle<Void> hold(List<Integer> keyCodes, double seconds) {
        return manager.submit(Operation.of("hold" + keyCodes, EnumSet.of(Channel.KEYBOARD), ctx -> {
            doHold(ctx, keyCodes, seconds);
            return null;
        }));
    }

    // ---------------------------------------------------------------- building blocks

    public void doType(OpContext ctx, String text) {
        boolean first = true;
        for (char c : text.toCharArray()) {
            KeyMap.Stroke s = KeyMap.lookup(c);
            if (!first) {
                ctx.sleepSeconds(timing.get(TimingProfiles.KEY_GAP).sample(ctx.rng()));
            }
            first = false;
            if (s.shift()) {
                press(KeyEvent.VK_SHIFT, KeyEvent.CHAR_UNDEFINED);
                try {
                    ctx.sleepSeconds(timing.get(TimingProfiles.KEY_MODIFIER_LEAD).sample(ctx.rng()));
                    doTap(ctx, s.keyCode(), c);
                    ctx.sleepSeconds(timing.get(TimingProfiles.KEY_MODIFIER_LEAD).sample(ctx.rng()) * 0.5);
                } finally {
                    release(KeyEvent.VK_SHIFT, KeyEvent.CHAR_UNDEFINED);
                }
            } else {
                doTap(ctx, s.keyCode(), c);
            }
        }
    }

    public void doTap(OpContext ctx, int keyCode, char ch) {
        press(keyCode, ch);
        try {
            ctx.sleepSeconds(timing.get(TimingProfiles.KEY_HOLD).sample(ctx.rng()));
        } finally {
            release(keyCode, ch);
        }
    }

    public void doHold(OpContext ctx, List<Integer> keyCodes, double seconds) {
        int pressedCount = 0;
        try {
            for (int k : keyCodes) {
                press(k, KeyEvent.CHAR_UNDEFINED);
                pressedCount++;
            }
            ctx.sleepSeconds(seconds);
        } finally {
            for (int i = pressedCount - 1; i >= 0; i--) {
                release(keyCodes.get(i), KeyEvent.CHAR_UNDEFINED);
            }
        }
    }

    /** Low-level press; tracked so it can be released by {@link #releaseAll()}. */
    public void press(int keyCode, char ch) {
        synchronized (down) {
            if (!down.add(keyCode)) {
                return; // already down: do not generate auto-repeat-like duplicates
            }
        }
        backend.keyPress(keyCode, ch);
    }

    public void release(int keyCode, char ch) {
        synchronized (down) {
            if (!down.remove(keyCode)) {
                return;
            }
        }
        backend.keyRelease(keyCode, ch);
    }

    public boolean isDown(int keyCode) {
        synchronized (down) {
            return down.contains(keyCode);
        }
    }

    public Set<Integer> pressedKeys() {
        synchronized (down) {
            return Set.copyOf(down);
        }
    }

    /** Releases every tracked key (safety net used on script stop). */
    public void releaseAll() {
        for (int k : pressedKeys()) {
            release(k, KeyEvent.CHAR_UNDEFINED);
        }
    }
}
