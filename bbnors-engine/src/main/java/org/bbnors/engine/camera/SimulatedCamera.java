package org.bbnors.engine.camera;

import org.bbnors.core.time.Clock;
import org.bbnors.engine.input.InputBackend;
import org.bbnors.engine.input.MouseButton;

/**
 * A camera that responds to input like a real client: holding a rotation key turns it at a
 * constant rate (optionally with a per-instance rate error to emulate imperfect calibration),
 * and middle-button drags rotate it proportionally to cursor travel. Used by tests, the lab and
 * the simulated game backend. Thread-safe.
 */
public final class SimulatedCamera implements CameraModel, InputBackend {

    private final Clock clock;
    private final double rateError;
    private double yaw;
    private double pitch;
    private int yawDir;    // -1, 0, +1 while a yaw key is held
    private int pitchDir;
    private long since;    // when the current key state began
    private boolean middleDown;
    private int lastX;
    private int lastY;

    /**
     * @param rateError multiplicative error between {@link #yawSpeed()} (what the controller
     *                  believes) and the true rotation rate, e.g. 1.1 = really 10% faster
     */
    public SimulatedCamera(Clock clock, int yaw, int pitch, double rateError) {
        this.clock = clock;
        this.yaw = yaw;
        this.pitch = pitch;
        this.rateError = rateError;
        this.since = clock.nanos();
    }

    private void integrate() {
        long now = clock.nanos();
        double dt = (now - since) / 1e9;
        since = now;
        yaw = mod(yaw + yawDir * yawSpeed() * rateError * dt, yawRange());
        pitch = clampPitch(pitch + pitchDir * pitchSpeed() * rateError * dt);
    }

    private double clampPitch(double p) {
        return Math.max(minPitch(), Math.min(maxPitch(), p));
    }

    private static double mod(double v, double m) {
        double r = v % m;
        return r < 0 ? r + m : r;
    }

    @Override
    public synchronized int yaw() {
        integrate();
        return (int) Math.round(yaw) % yawRange();
    }

    @Override
    public synchronized int pitch() {
        integrate();
        return (int) Math.round(pitch);
    }

    @Override
    public boolean supportsDirect() {
        return true;
    }

    @Override
    public synchronized void setDirect(int y, int p) {
        integrate();
        yaw = mod(y, yawRange());
        pitch = clampPitch(p);
    }

    @Override
    public synchronized void keyPress(int keyCode, char keyChar) {
        integrate();
        if (keyCode == keyYawIncrease()) yawDir = 1;
        else if (keyCode == keyYawDecrease()) yawDir = -1;
        else if (keyCode == keyPitchIncrease()) pitchDir = 1;
        else if (keyCode == keyPitchDecrease()) pitchDir = -1;
    }

    @Override
    public synchronized void keyRelease(int keyCode, char keyChar) {
        integrate();
        if (keyCode == keyYawIncrease() && yawDir == 1 || keyCode == keyYawDecrease() && yawDir == -1) yawDir = 0;
        else if (keyCode == keyPitchIncrease() && pitchDir == 1 || keyCode == keyPitchDecrease() && pitchDir == -1) pitchDir = 0;
    }

    @Override
    public synchronized void mouseMove(int x, int y) {
        integrate();
        if (middleDown) {
            yaw = mod(yaw + (x - lastX) * dragYawPerPixel() * rateError, yawRange());
            pitch = clampPitch(pitch + (y - lastY) * dragPitchPerPixel() * rateError);
        }
        lastX = x;
        lastY = y;
    }

    @Override
    public synchronized void mousePress(MouseButton button) {
        if (button == MouseButton.MIDDLE) middleDown = true;
    }

    @Override
    public synchronized void mouseRelease(MouseButton button) {
        if (button == MouseButton.MIDDLE) middleDown = false;
    }

    @Override
    public void mouseWheel(int notches) {
    }
}
