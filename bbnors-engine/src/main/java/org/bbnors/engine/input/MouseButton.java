package org.bbnors.engine.input;

import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

/** Mouse buttons with their AWT mappings. */
public enum MouseButton {
    LEFT(MouseEvent.BUTTON1, InputEvent.BUTTON1_DOWN_MASK),
    MIDDLE(MouseEvent.BUTTON2, InputEvent.BUTTON2_DOWN_MASK),
    RIGHT(MouseEvent.BUTTON3, InputEvent.BUTTON3_DOWN_MASK);

    private final int awtButton;
    private final int downMask;

    MouseButton(int awtButton, int downMask) {
        this.awtButton = awtButton;
        this.downMask = downMask;
    }

    public int awtButton() {
        return awtButton;
    }

    public int downMask() {
        return downMask;
    }
}
