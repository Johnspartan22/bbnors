package org.bbnors.engine.interact;

import org.bbnors.core.event.EventBus;
import org.bbnors.core.log.Log;
import org.bbnors.core.random.Rng;
import org.bbnors.core.time.Clock;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The interaction state manager: owns the input {@link Channel}s and decides which
 * {@link Operation}s run, wait, preempt or are rejected.
 *
 * <p><b>Scheduling rules</b>
 * <ul>
 *   <li>An operation starts only when <em>all</em> its channels are free.</li>
 *   <li>Pending operations are ordered by priority (high first), then submission order. While
 *       scanning the queue, a waiting operation <em>reserves</em> its channels, so a later,
 *       smaller operation cannot overtake it on a shared channel (no starvation of multi-channel
 *       operations), while operations on unrelated channels still start immediately.</li>
 *   <li>{@link ConflictPolicy#PREEMPT} cancels running holders of the needed channels whose
 *       priority is not higher than the newcomer's.</li>
 * </ul>
 *
 * <p><b>Threading</b>: each running operation executes on an executor thread (virtual threads by
 * default). The internal lock is never held while operation code runs or events are published.
 * Lifecycle events go to the {@link EventBus} for diagnostics and visualisation.
 */
public final class InteractionManager implements AutoCloseable {

    private static final Log LOG = Log.get(InteractionManager.class);

    private final Clock clock;
    private final Rng rng;
    private final Executor executor;
    private final boolean ownsExecutor;
    private final EventBus bus;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition idle = lock.newCondition();
    private final Map<Channel, OpHandle<?>> owners = new EnumMap<>(Channel.class);
    private final List<OpHandle<?>> pending = new ArrayList<>();
    private final Set<OpHandle<?>> running = new LinkedHashSet<>();
    private long nextId;
    private volatile boolean closed;

    /** Manager running operations on virtual threads. */
    public InteractionManager(Clock clock, Rng rng, EventBus bus) {
        this(clock, rng, bus, Executors.newVirtualThreadPerTaskExecutor(), true);
    }

    /**
     * Manager with a caller-supplied executor. {@code Runnable::run} gives fully synchronous,
     * deterministic execution for tests and offline simulation.
     */
    public InteractionManager(Clock clock, Rng rng, EventBus bus, Executor executor) {
        this(clock, rng, bus, executor, false);
    }

    private InteractionManager(Clock clock, Rng rng, EventBus bus, Executor executor, boolean owns) {
        this.clock = clock;
        this.rng = rng;
        this.bus = bus;
        this.executor = executor;
        this.ownsExecutor = owns;
    }

    public Clock clock() {
        return clock;
    }

    public EventBus bus() {
        return bus;
    }

    public <T> OpHandle<T> submit(Operation<T> op) {
        if (closed) {
            throw new IllegalStateException("interaction manager closed");
        }
        OpHandle<T> h;
        List<OpHandle<?>> preempt = List.of();
        Set<Channel> busy;
        lock.lock();
        try {
            h = new OpHandle<>(op, new OpContext(clock, rng.split()), nextId++, this);
            busy = EnumSet.noneOf(Channel.class);
            for (Channel c : op.channels()) {
                if (owners.containsKey(c)) {
                    busy.add(c);
                }
            }
            if (busy.isEmpty() || op.policy() != ConflictPolicy.REJECT) {
                if (!busy.isEmpty() && op.policy() == ConflictPolicy.PREEMPT) {
                    List<OpHandle<?>> victims = new ArrayList<>();
                    for (Channel c : busy) {
                        OpHandle<?> o = owners.get(c);
                        if (o.operation().priority() <= op.priority() && !victims.contains(o)) {
                            victims.add(o);
                        }
                    }
                    preempt = victims;
                }
                insertPending(h);
                busy = Set.of();
            }
        } finally {
            lock.unlock();
        }
        if (!busy.isEmpty()) {
            h.status(OpHandle.Status.FAILED);
            ChannelBusyException ex = new ChannelBusyException(op.name(), busy);
            h.future().completeExceptionally(ex);
            bus.publish(new InteractionEvent.Failed(h, ex));
            return h;
        }
        bus.publish(new InteractionEvent.Queued(h));
        for (OpHandle<?> v : preempt) {
            LOG.debug(() -> "preempting " + v + " for " + op.name());
            v.ctx.cancel();
        }
        dispatch();
        return h;
    }

    private void insertPending(OpHandle<?> h) {
        int i = pending.size();
        while (i > 0 && pending.get(i - 1).operation().priority() < h.operation().priority()) {
            i--;
        }
        pending.add(i, h);
    }

    private void dispatch() {
        List<OpHandle<?>> start = new ArrayList<>();
        lock.lock();
        try {
            EnumSet<Channel> reserved = EnumSet.noneOf(Channel.class);
            for (Iterator<OpHandle<?>> it = pending.iterator(); it.hasNext(); ) {
                OpHandle<?> h = it.next();
                Set<Channel> need = h.operation().channels();
                boolean free = true;
                for (Channel c : need) {
                    if (owners.containsKey(c) || reserved.contains(c)) {
                        free = false;
                        break;
                    }
                }
                if (free) {
                    it.remove();
                    for (Channel c : need) {
                        owners.put(c, h);
                    }
                    running.add(h);
                    h.status(OpHandle.Status.RUNNING);
                    start.add(h);
                } else {
                    reserved.addAll(need);
                }
            }
        } finally {
            lock.unlock();
        }
        for (OpHandle<?> h : start) {
            executor.execute(() -> run(h));
        }
    }

    private <T> void run(OpHandle<T> h) {
        OpContext ctx = h.ctx;
        ctx.bind(Thread.currentThread());
        long t0 = clock.nanos();
        T result = null;
        Throwable error = null;
        boolean cancelled = false;
        if (ctx.cancelled()) {
            cancelled = true;
        } else {
            bus.publish(new InteractionEvent.Started(h));
            try {
                result = h.operation().execute(ctx);
            } catch (CancellationException e) {
                cancelled = true;
            } catch (InterruptedException e) {
                cancelled = true;
            } catch (Throwable t) {
                if (ctx.cancelled()) {
                    cancelled = true;
                } else {
                    error = t;
                }
            }
        }
        ctx.bind(null);
        Thread.interrupted(); // never leak an interrupt into a pooled worker
        release(h);
        if (cancelled) {
            h.status(OpHandle.Status.CANCELLED);
            h.future().completeExceptionally(new CancellationException(h.operation().name() + " cancelled"));
            bus.publish(new InteractionEvent.Cancelled(h, "cancelled"));
        } else if (error != null) {
            h.status(OpHandle.Status.FAILED);
            h.future().completeExceptionally(error);
            bus.publish(new InteractionEvent.Failed(h, error));
            LOG.warn("operation " + h + " failed", error);
        } else {
            h.status(OpHandle.Status.COMPLETED);
            h.future().complete(result);
            bus.publish(new InteractionEvent.Completed(h, clock.nanos() - t0));
        }
        dispatch();
    }

    private void release(OpHandle<?> h) {
        lock.lock();
        try {
            for (Channel c : h.operation().channels()) {
                if (owners.get(c) == h) {
                    owners.remove(c);
                }
            }
            running.remove(h);
            if (running.isEmpty() && pending.isEmpty()) {
                idle.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    void cancel(OpHandle<?> h) {
        boolean wasPending;
        lock.lock();
        try {
            wasPending = pending.remove(h);
            if (wasPending && running.isEmpty() && pending.isEmpty()) {
                idle.signalAll();
            }
        } finally {
            lock.unlock();
        }
        if (wasPending) {
            h.status(OpHandle.Status.CANCELLED);
            h.future().completeExceptionally(new CancellationException(h.operation().name() + " cancelled while queued"));
            bus.publish(new InteractionEvent.Cancelled(h, "cancelled while queued"));
            dispatch();
        } else {
            h.ctx.cancel();
        }
    }

    /** Cancels everything queued and running (e.g. when a script stops). */
    public void cancelAll() {
        List<OpHandle<?>> all;
        lock.lock();
        try {
            all = new ArrayList<>(pending);
            all.addAll(running);
        } finally {
            lock.unlock();
        }
        for (OpHandle<?> h : all) {
            cancel(h);
        }
    }

    /** Current owner of each busy channel. */
    public Map<Channel, String> channelOwners() {
        lock.lock();
        try {
            Map<Channel, String> m = new EnumMap<>(Channel.class);
            owners.forEach((c, h) -> m.put(c, h.toString()));
            return Collections.unmodifiableMap(m);
        } finally {
            lock.unlock();
        }
    }

    public List<String> pendingOperations() {
        lock.lock();
        try {
            return pending.stream().map(OpHandle::toString).toList();
        } finally {
            lock.unlock();
        }
    }

    public boolean isIdle() {
        lock.lock();
        try {
            return running.isEmpty() && pending.isEmpty();
        } finally {
            lock.unlock();
        }
    }

    /** Blocks (real time) until no operation is running or queued. */
    public boolean awaitIdle(Duration timeout) throws InterruptedException {
        long remaining = timeout.toNanos();
        lock.lock();
        try {
            while (!(running.isEmpty() && pending.isEmpty())) {
                if (remaining <= 0) {
                    return false;
                }
                remaining = idle.awaitNanos(remaining);
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() {
        closed = true;
        cancelAll();
        if (ownsExecutor && executor instanceof ExecutorService es) {
            es.shutdown();
            try {
                es.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
