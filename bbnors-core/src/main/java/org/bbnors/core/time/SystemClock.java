package org.bbnors.core.time;

import java.util.concurrent.locks.LockSupport;

/**
 * Wall-clock implementation.
 *
 * <p>Sleeps use a hybrid strategy: {@link LockSupport#parkNanos} for the bulk of the wait, then a
 * short spin with {@link Thread#onSpinWait()} for the final {@value #SPIN_THRESHOLD_NANOS} ns.
 * {@code Thread.sleep(1)} on many platforms overshoots by 1-2 ms, which would visibly distort
 * 125-250 Hz trajectory playback; the spin tail bounds the overshoot to microseconds while keeping
 * CPU use negligible (the spin never exceeds 0.3 ms per wait).
 */
public final class SystemClock implements Clock {

    static final SystemClock INSTANCE = new SystemClock();

    private static final long SPIN_THRESHOLD_NANOS = 300_000L;

    private SystemClock() {
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
        while (remaining > SPIN_THRESHOLD_NANOS) {
            LockSupport.parkNanos(remaining - SPIN_THRESHOLD_NANOS);
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
