package org.bbnors.core.time;

/**
 * Time source used by every timing-sensitive component.
 *
 * <p>Injecting the clock (instead of calling {@link System#nanoTime()} and {@link Thread#sleep})
 * is what makes the scheduler, waits and trajectory playback deterministic under test:
 * {@link ManualClock} advances instantly on {@link #sleepNanos(long)}.
 */
public interface Clock {

    /** Monotonic time in nanoseconds. Only differences are meaningful. */
    long nanos();

    /** Blocks (or, for virtual clocks, advances time) for the given duration. */
    void sleepNanos(long nanos) throws InterruptedException;

    default long millis() {
        return nanos() / 1_000_000L;
    }

    default void sleepMillis(long millis) throws InterruptedException {
        sleepNanos(millis * 1_000_000L);
    }

    /** Sleeps until {@code deadlineNanos} (no-op if already past). */
    default void sleepUntil(long deadlineNanos) throws InterruptedException {
        long remaining = deadlineNanos - nanos();
        if (remaining > 0) {
            sleepNanos(remaining);
        }
    }

    static Clock system() {
        return SystemClock.INSTANCE;
    }
}
