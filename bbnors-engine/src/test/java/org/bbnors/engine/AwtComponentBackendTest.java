package org.bbnors.engine;

import org.bbnors.engine.input.AwtComponentBackend;
import org.bbnors.engine.input.MouseButton;
import org.junit.Test;

import javax.swing.JPanel;
import java.awt.AWTEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Verifies the synthetic AWT event stream on a real (headless, lightweight) component. */
public class AwtComponentBackendTest {

    static final class Recorder extends JPanel {
        final List<AWTEvent> events = new ArrayList<>();

        Recorder() {
            setSize(200, 100);
            enableEvents(AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.MOUSE_WHEEL_EVENT_MASK | AWTEvent.KEY_EVENT_MASK);
        }

        @Override
        protected void processEvent(AWTEvent e) {
            events.add(e);
        }
    }

    @Test
    public void mouseSequenceHasConsistentIdsModifiersAndClicks() {
        Recorder c = new Recorder();
        AwtComponentBackend b = new AwtComponentBackend(c);
        b.mouseMove(10, 10);                 // enter + move
        b.mousePress(MouseButton.LEFT);
        b.mouseRelease(MouseButton.LEFT);    // no movement in between -> click
        b.mousePress(MouseButton.MIDDLE);
        b.mouseMove(40, 12);                 // drag while middle held
        b.mouseRelease(MouseButton.MIDDLE);  // moved -> no click
        b.mouseMove(500, 12);                // leave the component
        List<Integer> ids = c.events.stream().map(AWTEvent::getID).toList();
        assertEquals(List.of(
                MouseEvent.MOUSE_ENTERED, MouseEvent.MOUSE_MOVED,
                MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED,
                MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_DRAGGED, MouseEvent.MOUSE_RELEASED,
                MouseEvent.MOUSE_MOVED, MouseEvent.MOUSE_EXITED), ids);
        MouseEvent drag = (MouseEvent) c.events.get(6);
        assertTrue((drag.getModifiersEx() & InputEvent.BUTTON2_DOWN_MASK) != 0);
        assertEquals(40, drag.getX());
        MouseEvent press = (MouseEvent) c.events.get(2);
        assertEquals(MouseEvent.BUTTON1, press.getButton());
        assertEquals(10, press.getX());
    }

    @Test
    public void keysCarryShiftModifierAndTypedCharacters() {
        // No component has focus in a headless test, so keys go to the listeners directly.
        Recorder c = new Recorder();
        c.addKeyListener(new java.awt.event.KeyListener() {
            public void keyTyped(KeyEvent e) { c.events.add(e); }
            public void keyPressed(KeyEvent e) { c.events.add(e); }
            public void keyReleased(KeyEvent e) { c.events.add(e); }
        });
        AwtComponentBackend b = new AwtComponentBackend(c, AwtComponentBackend.KeyDelivery.LISTENERS);
        b.keyPress(KeyEvent.VK_SHIFT, KeyEvent.CHAR_UNDEFINED);
        b.keyPress(KeyEvent.VK_A, 'A');
        b.keyRelease(KeyEvent.VK_A, 'A');
        b.keyRelease(KeyEvent.VK_SHIFT, KeyEvent.CHAR_UNDEFINED);
        List<Integer> ids = c.events.stream().map(AWTEvent::getID).toList();
        assertEquals(List.of(KeyEvent.KEY_PRESSED, KeyEvent.KEY_PRESSED, KeyEvent.KEY_TYPED, KeyEvent.KEY_RELEASED, KeyEvent.KEY_RELEASED), ids);
        KeyEvent typed = (KeyEvent) c.events.get(2);
        assertEquals('A', typed.getKeyChar());
        assertTrue((typed.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) != 0);
        KeyEvent lastRelease = (KeyEvent) c.events.get(4);
        assertEquals(0, lastRelease.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK);
    }
}
