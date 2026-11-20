package org.bbnors.engine.timing;

import org.bbnors.core.log.Log;
import org.bbnors.core.time.Clock;

import java.util.PriorityQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Single daemon thread executing tasks at their due time on the injected clock.
 *
 * <p>The thread parks until the earliest due time (or until a new earlier task arrives), so an
 * idle scheduler consumes no CPU, unlike the fixed-interval polling loops common in script
 * frameworks. Tasks should be short; long work belongs on its own thread. A throwing task is
 * logged and, if periodic, keeps its schedule.
 */
public final class RealTimeScheduler implements TaskScheduler {

    private static final Log LOG = Log.get(RealTimeScheduler.class);

    private final Clock clock;
    private final PriorityQueue<ScheduledEntry> queue = new PriorityQueue<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition changed = lock.newCondition();
    private final Thread thread;
    private long seq;
    private volatile boolean closed;

    public RealTimeScheduler(Clock clock, String name) {
        this.clock = clock;
        this.thread = new Thread(this::loop, name);
        this.thread.setDaemon(true);
        this.thread.start();
    }

    @Override
    public Clock clock() {
        return clock;
    }

    @Override
    public Task schedule(long delayNanos, Runnable action) {
        return add(new ScheduledEntry(action, clock.nanos() + Math.max(0, delayNanos), 0, 0));
    }

    @Override
    public Task scheduleAtFixedRate(long initialDelayNanos, long periodNanos, Runnable action) {
        if (periodNanos <= 0) throw new IllegalArgumentException("period must be > 0");
        return add(new ScheduledEntry(action, clock.nanos() + Math.max(0, initialDelayNanos), periodNanos, 0));
    }

    private Task add(ScheduledEntry proto) {
        if (closed) throw new IllegalStateException("scheduler closed");
        lock.lock();
        try {
            ScheduledEntry e = new ScheduledEntry(proto.action, proto.due, proto.period, seq++);
            queue.add(e);
            changed.signal();
            return e;
        } finally {
            lock.unlock();
        }
    }

    private void loop() {
        while (!closed) {
            ScheduledEntry e;
            lock.lock();
            try {
                e = queue.peek();
                if (e == null) {
                    changed.await();
                    continue;
                }
                long wait = e.due - clock.nanos();
                if (wait > 0) {
                    changed.awaitNanos(wait);
                    continue; // re-evaluate: an earlier task may have arrived
                }
                queue.poll();
            } catch (InterruptedException ie) {
                return;
            } finally {
                lock.unlock();
            }
            if (e.isCancelled()) {
                continue;
            }
            try {
                e.action.run();
            } catch (RuntimeException ex) {
                LOG.warn("scheduled task failed", ex);
            }
            if (e.period > 0 && !e.isCancelled()) {
                e.due += e.period;
                lock.lock();
                try {
                    queue.add(e);
                } finally {
                    lock.unlock();
                }
            } else {
                e.markDone();
            }
        }
    }

    @Override
    public void close() {
        closed = true;
        thread.interrupt();
    }
}
