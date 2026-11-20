package org.bbnors.engine.timing;

import org.bbnors.core.time.Clock;

/**
 * Clock-driven task scheduler used for timers, periodic jobs and deferred actions.
 * Two implementations share the same ordering semantics (by due time, then submission order):
 * {@link RealTimeScheduler} (one daemon thread) and {@link ManualScheduler} (deterministic,
 * advanced explicitly by tests and simulations).
 */
public interface TaskScheduler extends AutoCloseable {

    /** Handle to a scheduled task. */
    interface Task {
        /** Prevents future executions; returns false if it already ran (one-shot) or was cancelled. */
        boolean cancel();

        boolean isCancelled();
    }

    Clock clock();

    Task schedule(long delayNanos, Runnable action);

    /** Fixed-rate repetition: executions are anchored to the first due time, so they never drift. */
    Task scheduleAtFixedRate(long initialDelayNanos, long periodNanos, Runnable action);

    @Override
    void close();
}
