package org.bbnors.core.time;

import java.util.concurrent.locks.LockSupport;

/**
 * Wall-clock implementation.
 *
 * <p><b>Default: no spinning.</b> Measured on Linux (benchmark {@code clock_spin_study_200hz},
 * docs/PERFORMANCE.md): pure parking wakes a median 89 µs late at a cost of 4.6 ms CPU per second
 * of 200 Hz playback; a 300 µs spin tail removes the lateness but costs 45.5 ms/s. Playback
 * evaluates motions at the actual elapsed time, so 89 µs of lateness shifts the cursor by at most
 * ~0.2 px; the 10× CPU cost is not worth it. On platforms with coarse timers (e.g. 1 ms+ wake-up
 * slack) use {@link #withSpinTail(long)}.
 *
 * <p>Sleeps park with {@link LockSupport#parkNanos} (re-parking on early wake-ups) and, if a spin
 * tail is configured, busy-wait with {@link Thread#onSpinWait()} for the final
 * {@link #spinTailNanos()} ns.
 */
public final class SystemClock implements Clock {

    /** Default spin tail; see docs/PERFORMANCE.md for the CPU-vs-precision measurements behind it. */
    public static final long DEFAULT_SPIN_NANOS = 0L;

    static final SystemClock INSTANCE = new SystemClock(DEFAULT_SPIN_NANOS);

    private final long spinThresholdNanos;

    private SystemClock(long spinThresholdNanos) {
        this.spinThresholdNanos = spinThresholdNanos;
    }

    /**
     * A system clock with a custom spin tail. {@code 0} = never spin (lowest CPU, wake-up
     * lateness up to the OS timer slack); larger values trade CPU for punctuality.
     */
    public static SystemClock withSpinTail(long nanos) {
        if (nanos < 0 || nanos > 5_000_000L) throw new IllegalArgumentException("spin tail must be in [0, 5 ms]");
        return new SystemClock(nanos);
    }

    public long spinTailNanos() {
        return spinThresholdNanos;
    }

    @Override
    public long nanos() {
        return System.nanoTime();
    }

    @Override
    public void sleepNanos(long nanos) throws InterruptedException {
        if (nanos <= 0) {
            return;
        }
        final long deadline = System.nanoTime() + nanos;
        long remaining = nanos;
        while (remaining > spinThresholdNanos) {
            LockSupport.parkNanos(remaining - spinThresholdNanos);
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            remaining = deadline - System.nanoTime();
        }
        while (System.nanoTime() < deadline) {
            Thread.onSpinWait();
            if (Thread.currentThread().isInterrupted()) {
                Thread.interrupted();
                throw new InterruptedException();
            }
        }
    }
}
