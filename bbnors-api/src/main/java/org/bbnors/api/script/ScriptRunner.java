package org.bbnors.api.script;

import org.bbnors.core.config.Config;
import org.bbnors.core.event.EventBus;
import org.bbnors.core.log.Log;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Script lifecycle state machine:
 * <pre>
 *   CREATED ─start→ RUNNING ⇄ PAUSED
 *                     │ stop / loop()&lt;0 / onStart failure / too many errors
 *                     ▼
 *                  STOPPED | FAILED
 * </pre>
 * The runner owns the loop: it applies the minimum loop delay, backs off after exceptions, stops
 * after {@link ScriptSettings#MAX_CONSECUTIVE_ERRORS} consecutive failures, publishes
 * {@link ScriptEvent}s, and on exit cancels all input operations and releases every held key and
 * button, whatever the reason for stopping.
 *
 * <p>{@link #start()} runs the script on a dedicated thread; {@link #runInline(int)} runs it on the
 * caller's thread (deterministic simulations and tests).
 */
public final class ScriptRunner {

    public enum State { CREATED, RUNNING, PAUSED, STOPPED, FAILED }

    /** Lifecycle events published on the engine's event bus. */
    public sealed interface ScriptEvent {
        record StateChanged(String script, State from, State to) implements ScriptEvent { }

        record LoopCompleted(String script, long loop, long durationNanos, int requestedDelayMs) implements ScriptEvent { }

        record LoopFailed(String script, long loop, Throwable error) implements ScriptEvent { }
    }

    private static final Log LOG = Log.get(ScriptRunner.class);

    private final Script script;
    private final ScriptContext ctx;
    private final EventBus bus;
    private final int maxErrors;
    private final int backoffMs;
    private final int minDelayMs;
    private final AtomicReference<State> state = new AtomicReference<>(State.CREATED);
    private final Object pauseLock = new Object();
    private volatile boolean stopRequested;
    private volatile boolean paused;
    private volatile Thread thread;
    private volatile Throwable failure;
    private long loops;
    private long errors;
    private long totalLoopNanos;

    public ScriptRunner(Script script, ScriptContext ctx, Config config) {
        this.script = Objects.requireNonNull(script);
        this.ctx = Objects.requireNonNull(ctx);
        this.bus = ctx.engine().bus();
        this.maxErrors = config.get(ScriptSettings.MAX_CONSECUTIVE_ERRORS);
        this.backoffMs = config.get(ScriptSettings.ERROR_BACKOFF_MS);
        this.minDelayMs = config.get(ScriptSettings.MIN_LOOP_DELAY_MS);
        ctx.bind(this);
    }

    public State state() {
        return state.get();
    }

    public Throwable failure() {
        return failure;
    }

    public long loops() {
        return loops;
    }

    public long errors() {
        return errors;
    }

    /** Mean wall time spent inside {@code loop()} (excludes the inter-loop sleep). */
    public double meanLoopMillis() {
        return loops == 0 ? 0 : totalLoopNanos / 1e6 / loops;
    }

    boolean stopRequested() {
        return stopRequested;
    }

    /** Starts the script on its own thread. */
    public synchronized void start() {
        if (state.get() != State.CREATED) throw new IllegalStateException("already started");
        thread = Thread.ofPlatform().name("script-" + script.name()).daemon(true).start(() -> runInline(Integer.MAX_VALUE));
    }

    /** Requests stop; in-flight input operations are cancelled immediately. */
    public void stop() {
        stopRequested = true;
        resume();
        ctx.engine().manager().cancelAll();
        Thread t = thread;
        if (t != null && t != Thread.currentThread()) {
            t.interrupt();
        }
    }

    public void pause() {
        if (state.get() == State.RUNNING) {
            paused = true;
        }
    }

    public void resume() {
        synchronized (pauseLock) {
            paused = false;
            pauseLock.notifyAll();
        }
    }

    public boolean join(long millis) throws InterruptedException {
        Thread t = thread;
        if (t != null) {
            t.join(millis);
        }
        State s = state.get();
        return s == State.STOPPED || s == State.FAILED;
    }

    /**
     * Runs the script on the calling thread for at most {@code maxLoops} loops (or until it stops).
     * With a virtual clock this is fully deterministic.
     */
    public State runInline(int maxLoops) {
        transition(State.CREATED, State.RUNNING);
        try {
            script.onStart(ctx);
        } catch (CancellationException e) {
            return finish(State.STOPPED);
        } catch (RuntimeException e) {
            failure = e;
            LOG.error(script.name() + ".onStart failed", e);
            return finish(State.FAILED);
        }
        int consecutive = 0;
        for (int i = 0; i < maxLoops && !stopRequested; i++) {
            if (paused) {
                waitWhilePaused();
                if (stopRequested) break;
            }
            long t0 = System.nanoTime();
            int delay;
            try {
                delay = script.loop(ctx);
                consecutive = 0;
            } catch (CancellationException e) {
                break;
            } catch (RuntimeException e) {
                errors++;
                consecutive++;
                bus.publish(new ScriptEvent.LoopFailed(script.name(), loops, e));
                LOG.warn(script.name() + " loop failed (" + consecutive + "/" + maxErrors + ")", e);
                if (consecutive >= maxErrors) {
                    failure = e;
                    return finish(State.FAILED);
                }
                delay = backoffMs;
            }
            long dt = System.nanoTime() - t0;
            loops++;
            totalLoopNanos += dt;
            bus.publish(new ScriptEvent.LoopCompleted(script.name(), loops, dt, delay));
            if (delay < 0) {
                break;
            }
            try {
                ctx.sleep(Math.max(minDelayMs, delay));
            } catch (CancellationException e) {
                break;
            }
        }
        return finish(State.STOPPED);
    }

    private void waitWhilePaused() {
        State prev = state.get();
        transition(prev, State.PAUSED);
        synchronized (pauseLock) {
            while (paused && !stopRequested) {
                try {
                    pauseLock.wait(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        if (!stopRequested) {
            transition(State.PAUSED, State.RUNNING);
        }
    }

    private State finish(State terminal) {
        try {
            script.onStop(ctx);
        } catch (RuntimeException e) {
            LOG.warn(script.name() + ".onStop failed", e);
        }
        ctx.engine().stopAll(); // cancel operations, release keys and buttons
        Thread.interrupted();
        State prev = state.getAndSet(terminal);
        bus.publish(new ScriptEvent.StateChanged(script.name(), prev, terminal));
        return terminal;
    }

    private void transition(State from, State to) {
        if (state.compareAndSet(from, to)) {
            bus.publish(new ScriptEvent.StateChanged(script.name(), from, to));
        } else if (from == State.CREATED) {
            throw new IllegalStateException("script already started");
        }
    }
}
