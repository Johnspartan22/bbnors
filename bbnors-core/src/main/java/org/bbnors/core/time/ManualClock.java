package org.bbnors.core.time;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Virtual clock for deterministic tests and offline simulation.
 *
 * <p>{@link #sleepNanos(long)} advances time immediately instead of blocking, so a 2-second
 * trajectory plays back in microseconds with exactly the same event timestamps it would have
 * produced in real time.
 */
public final class ManualClock implements Clock {

    private final AtomicLong now;

    public ManualClock() {
        this(0L);
    }

    public ManualClock(long startNanos) {
        this.now = new AtomicLong(startNanos);
    }

    @Override
    public long nanos() {
        return now.get();
    }

    @Override
    public void sleepNanos(long nanos) throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        if (nanos > 0) {
            now.addAndGet(nanos);
        }
    }

    public void advanceNanos(long nanos) {
        now.addAndGet(nanos);
    }

    public void advanceMillis(long millis) {
        advanceNanos(millis * 1_000_000L);
    }
}
