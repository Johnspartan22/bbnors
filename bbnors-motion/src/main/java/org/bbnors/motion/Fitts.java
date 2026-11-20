package org.bbnors.motion;

/**
 * Fitts' law, Shannon formulation (MacKenzie 1992): {@code MT = a + b·log2(D/W + 1)}.
 *
 * <p>{@code a} (seconds) and {@code b} (seconds per bit) are device- and person-specific. The
 * defaults used by the built-in profiles are configurable starting points, not measurements;
 * calibrate them with the lab against your own recordings.
 */
public final class Fitts {

    private Fitts() {
    }

    /** Index of difficulty in bits. {@code width} is clamped to at least 1 px. */
    public static double indexOfDifficulty(double distance, double width) {
        double w = Math.max(1.0, width);
        return Math.log(Math.max(0, distance) / w + 1) / Math.log(2);
    }

    public static double movementTime(double a, double b, double distance, double width) {
        return a + b * indexOfDifficulty(distance, width);
    }
}
