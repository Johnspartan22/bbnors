package org.bbnors.engine.timing;

import java.util.concurrent.atomic.AtomicBoolean;

/** Shared queue entry for both scheduler implementations. */
final class ScheduledEntry implements TaskScheduler.Task, Comparable<ScheduledEntry> {

    final Runnable action;
    final long period; // 0 = one-shot
    final long seq;
    long due;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final AtomicBoolean done = new AtomicBoolean();

    ScheduledEntry(Runnable action, long due, long period, long seq) {
        this.action = action;
        this.due = due;
        this.period = period;
        this.seq = seq;
    }

    @Override
    public boolean cancel() {
        return !done.get() && cancelled.compareAndSet(false, true);
    }

    @Override
    public boolean isCancelled() {
        return cancelled.get();
    }

    void markDone() {
        done.set(true);
    }

    @Override
    public int compareTo(ScheduledEntry o) {
        int c = Long.compare(due, o.due);
        return c != 0 ? c : Long.compare(seq, o.seq);
    }
}
