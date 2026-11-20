package org.bbnors.engine.interact;

import org.bbnors.core.random.Rng;
import org.bbnors.core.time.Clock;

import java.util.concurrent.CancellationException;

/**
 * Execution context handed to a running {@link Operation}: the clock, a private random stream,
 * and cooperative cancellation. Operations must use {@link #sleepNanos}/{@link #sleepUntil}
 * (never {@code Thread.sleep}) so that cancellation and virtual clocks work.
 */
public final class OpContext {

    private final Clock clock;
    private final Rng rng;
    private volatile boolean cancelled;
    private volatile Thread worker;

    OpContext(Clock clock, Rng rng) {
        this.clock = clock;
        this.rng = rng;
    }

    public Clock clock() {
        return clock;
    }

    public Rng rng() {
        return rng;
    }

    public boolean cancelled() {
        return cancelled;
    }

    /** Throws {@link CancellationException} if cancellation was requested. */
    public void checkCancelled() {
        if (cancelled) {
            throw new CancellationException();
        }
    }

    public void sleepNanos(long nanos) {
        checkCancelled();
        try {
            clock.sleepNanos(nanos);
        } catch (InterruptedException e) {
            throw new CancellationException();
        }
        checkCancelled();
    }

    public void sleepSeconds(double seconds) {
        sleepNanos((long) (seconds * 1e9));
    }

    public void sleepUntil(long deadlineNanos) {
        long d = deadlineNanos - clock.nanos();
        if (d > 0) {
            sleepNanos(d);
        } else {
            checkCancelled();
        }
    }

    void bind(Thread t) {
        worker = t;
    }

    void cancel() {
        cancelled = true;
        Thread t = worker;
        if (t != null && t != Thread.currentThread()) {
            t.interrupt();
        }
    }
}
