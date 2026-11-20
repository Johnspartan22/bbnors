package org.bbnors.engine.camera;

import org.bbnors.core.geom.Rect;

import java.awt.event.KeyEvent;

/**
 * Read access to (and the input semantics of) a game camera. Provided by the game adapter; the
 * {@link CameraController} is written purely against this interface.
 *
 * <p>Angles are in client units: yaw wraps at {@link #yawRange()} (2048 for OSRS-style clients),
 * pitch is clamped to [{@link #minPitch()}, {@link #maxPitch()}]. The rates and key bindings
 * default to plausible values but <b>must be verified per client</b>: measure them with the
 * camera calibration routine in the lab or by holding a key for a known time.
 */
public interface CameraModel {

    int yaw();

    int pitch();

    default int zoom() {
        return 0;
    }

    default int yawRange() {
        return 2048;
    }

    default int minPitch() {
        return 128;
    }

    default int maxPitch() {
        return 383;
    }

    /** Yaw units per second while a yaw key is held. */
    default double yawSpeed() {
        return 1024;
    }

    /** Pitch units per second while a pitch key is held. */
    default double pitchSpeed() {
        return 512;
    }

    /** Key that <em>increases</em> yaw. */
    default int keyYawIncrease() {
        return KeyEvent.VK_LEFT;
    }

    default int keyYawDecrease() {
        return KeyEvent.VK_RIGHT;
    }

    default int keyPitchIncrease() {
        return KeyEvent.VK_UP;
    }

    default int keyPitchDecrease() {
        return KeyEvent.VK_DOWN;
    }

    /** Yaw units per pixel of horizontal middle-button drag (sign included). */
    default double dragYawPerPixel() {
        return -4.0;
    }

    /** Pitch units per pixel of vertical middle-button drag (sign included). */
    default double dragPitchPerPixel() {
        return 2.0;
    }

    /** Screen area in which a camera drag may start and end. */
    default Rect dragArea() {
        return new Rect(40, 40, 430, 260);
    }

    /** Whether {@link #setDirect} is supported (e.g. a developer hook on your own client). */
    default boolean supportsDirect() {
        return false;
    }

    /** Sets the camera instantly; only called if {@link #supportsDirect()}. */
    default void setDirect(int yaw, int pitch) {
        throw new UnsupportedOperationException("direct camera control not supported");
    }

    /** Shortest signed yaw difference {@code to - from}, in (-range/2, range/2]. */
    static int yawDelta(int from, int to, int range) {
        int d = Math.floorMod(to - from, range);
        return d > range / 2 ? d - range : d;
    }
}
