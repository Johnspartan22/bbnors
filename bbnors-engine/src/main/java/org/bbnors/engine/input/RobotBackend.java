package org.bbnors.engine.input;

import java.awt.AWTException;
import java.awt.Point;
import java.awt.Robot;

/**
 * OS-level input through {@link Robot}. Coordinates are translated by {@code origin} so the
 * engine can work in client-relative pixels while the robot moves the real cursor on screen.
 * Requires a non-headless environment.
 */
public final class RobotBackend implements InputBackend {

    private final Robot robot;
    private volatile Point origin;

    public RobotBackend(Point screenOrigin) throws AWTException {
        this.robot = new Robot();
        this.robot.setAutoDelay(0);
        this.origin = new Point(screenOrigin);
    }

    /** Updates the screen position of the client area (e.g. after the window moved). */
    public void setOrigin(Point screenOrigin) {
        this.origin = new Point(screenOrigin);
    }

    @Override
    public void mouseMove(int x, int y) {
        Point o = origin;
        robot.mouseMove(o.x + x, o.y + y);
    }

    @Override
    public void mousePress(MouseButton button) {
        robot.mousePress(button.downMask());
    }

    @Override
    public void mouseRelease(MouseButton button) {
        robot.mouseRelease(button.downMask());
    }

    @Override
    public void mouseWheel(int notches) {
        robot.mouseWheel(notches);
    }

    @Override
    public void keyPress(int keyCode, char keyChar) {
        robot.keyPress(keyCode);
    }

    @Override
    public void keyRelease(int keyCode, char keyChar) {
        robot.keyRelease(keyCode);
    }
}
