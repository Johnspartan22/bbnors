package org.bbnors.engine.timing;

import org.bbnors.core.time.Clock;
import org.bbnors.core.time.ManualClock;

import java.util.PriorityQueue;

/**
 * Deterministic scheduler over a {@link ManualClock}. Nothing runs until {@link #advance(long)}
 * or {@link #runUntilIdle(long)} is called; tasks then run on the calling thread in due-time
 * order, with the clock set to each task's due time while it runs.
 */
public final class ManualScheduler implements TaskScheduler {

    private final ManualClock clock;
    private final PriorityQueue<ScheduledEntry> queue = new PriorityQueue<>();
    private long seq;

    public ManualScheduler(ManualClock clock) {
        this.clock = clock;
    }

    @Override
    public Clock clock() {
        return clock;
    }

    @Override
    public synchronized Task schedule(long delayNanos, Runnable action) {
        ScheduledEntry e = new ScheduledEntry(action, clock.nanos() + Math.max(0, delayNanos), 0, seq++);
        queue.add(e);
        return e;
    }

    @Override
    public synchronized Task scheduleAtFixedRate(long initialDelayNanos, long periodNanos, Runnable action) {
        if (periodNanos <= 0) throw new IllegalArgumentException("period must be > 0");
        ScheduledEntry e = new ScheduledEntry(action, clock.nanos() + Math.max(0, initialDelayNanos), periodNanos, seq++);
        queue.add(e);
        return e;
    }

    /** Runs every task due within the next {@code nanos}, then sets the clock to now+nanos. */
    public void advance(long nanos) {
        long target = clock.nanos() + nanos;
        while (true) {
            ScheduledEntry e;
            synchronized (this) {
                e = queue.peek();
                if (e == null || e.due > target) {
                    break;
                }
                queue.poll();
            }
            if (e.isCancelled()) {
                continue;
            }
            long delta = e.due - clock.nanos();
            if (delta > 0) {
                clock.advanceNanos(delta);
            }
            e.action.run();
            if (e.period > 0 && !e.isCancelled()) {
                e.due += e.period;
                synchronized (this) {
                    queue.add(e);
                }
            } else {
                e.markDone();
            }
        }
        long rest = target - clock.nanos();
        if (rest > 0) {
            clock.advanceNanos(rest);
        }
    }

    /** Advances until the queue is empty or {@code maxNanos} has elapsed. */
    public void runUntilIdle(long maxNanos) {
        long deadline = clock.nanos() + maxNanos;
        while (clock.nanos() < deadline) {
            ScheduledEntry next;
            synchronized (this) {
                next = queue.peek();
            }
            if (next == null) {
                return;
            }
            advance(Math.max(0, Math.min(next.due, deadline) - clock.nanos()));
        }
    }

    public synchronized int pending() {
        return (int) queue.stream().filter(e -> !e.isCancelled()).count();
    }

    @Override
    public synchronized void close() {
        queue.clear();
    }
}
