package org.bbnors.engine.input;

import java.awt.event.KeyEvent;

/**
 * Maps printable characters to (VK code, needs-shift) for a US keyboard layout. Characters that
 * cannot be produced return {@code null} from {@link #lookup(char)}.
 */
public final class KeyMap {

    /** A key stroke: virtual key code plus whether SHIFT must be held. */
    public record Stroke(int keyCode, boolean shift) {
    }

    private static final String SHIFTED_DIGITS = ")!@#$%^&*(";
    private static final String UNSHIFTED_PUNCT = "`-=[]\\;',./";
    private static final String SHIFTED_PUNCT = "~_+{}|:\"<>?";
    private static final int[] PUNCT_CODES = {
            KeyEvent.VK_BACK_QUOTE, KeyEvent.VK_MINUS, KeyEvent.VK_EQUALS, KeyEvent.VK_OPEN_BRACKET,
            KeyEvent.VK_CLOSE_BRACKET, KeyEvent.VK_BACK_SLASH, KeyEvent.VK_SEMICOLON, KeyEvent.VK_QUOTE,
            KeyEvent.VK_COMMA, KeyEvent.VK_PERIOD, KeyEvent.VK_SLASH};

    private KeyMap() {
    }

    public static Stroke lookup(char c) {
        if (c >= 'a' && c <= 'z') return new Stroke(KeyEvent.VK_A + (c - 'a'), false);
        if (c >= 'A' && c <= 'Z') return new Stroke(KeyEvent.VK_A + (c - 'A'), true);
        if (c >= '0' && c <= '9') return new Stroke(KeyEvent.VK_0 + (c - '0'), false);
        int i = SHIFTED_DIGITS.indexOf(c);
        if (i >= 0) return new Stroke(KeyEvent.VK_0 + i, true);
        i = UNSHIFTED_PUNCT.indexOf(c);
        if (i >= 0) return new Stroke(PUNCT_CODES[i], false);
        i = SHIFTED_PUNCT.indexOf(c);
        if (i >= 0) return new Stroke(PUNCT_CODES[i], true);
        return switch (c) {
            case ' ' -> new Stroke(KeyEvent.VK_SPACE, false);
            case '\n' -> new Stroke(KeyEvent.VK_ENTER, false);
            case '\t' -> new Stroke(KeyEvent.VK_TAB, false);
            case '\b' -> new Stroke(KeyEvent.VK_BACK_SPACE, false);
            default -> null;
        };
    }
}
