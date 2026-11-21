package org.bbnors.api.sim;

import org.bbnors.core.time.Clock;
import org.bbnors.core.time.ManualClock;
import org.bbnors.engine.timing.ManualScheduler;

/**
 * Virtual clock that drives a {@link ManualScheduler} as it advances: whenever anything sleeps,
 * the simulated world runs every game tick that falls inside the sleep. This is what makes a
 * whole script (mouse playback, waits, game ticks) run deterministically, single-threaded, and
 * much faster than real time.
 */
public final class SimulationClock implements Clock {

    private final ManualClock clock = new ManualClock();
    private final ManualScheduler scheduler = new ManualScheduler(clock);

    public ManualScheduler scheduler() {
        return scheduler;
    }

    @Override
    public long nanos() {
        return clock.nanos();
    }

    @Override
    public void sleepNanos(long nanos) throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        if (nanos > 0) {
            scheduler.advance(nanos);
        }
    }
}
