package org.bbnors.engine.input;

/**
 * The only boundary between $bbnors and whatever actually receives input.
 *
 * <p>The interaction engine computes <em>what</em> to send and <em>when</em>; a backend only
 * delivers it. Implementations:
 * <ul>
 *   <li>{@link AwtComponentBackend}: dispatches synthetic AWT events to a {@link java.awt.Component}
 *       in the same JVM (the usual way to drive a Java game client you load yourself).</li>
 *   <li>{@link RobotBackend}: OS-level input through {@link java.awt.Robot}.</li>
 *   <li>{@link RecordingBackend}: records timestamped events; used by tests, the lab and the
 *       simulated game backend.</li>
 * </ul>
 * Coordinates are integer pixels in the target's coordinate space. Calls are made from the
 * engine's worker threads; implementations must be thread-safe.
 */
public interface InputBackend {

    void mouseMove(int x, int y);

    void mousePress(MouseButton button);

    void mouseRelease(MouseButton button);

    /** Positive = towards the user (scroll down / zoom out in most clients). */
    void mouseWheel(int notches);

    /**
     * @param keyCode {@link java.awt.event.KeyEvent} VK_ code
     * @param keyChar the character produced, or {@link java.awt.event.KeyEvent#CHAR_UNDEFINED}
     */
    void keyPress(int keyCode, char keyChar);

    void keyRelease(int keyCode, char keyChar);

    /** Short identifier for diagnostics. */
    default String name() {
        return getClass().getSimpleName();
    }
}
