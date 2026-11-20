package org.bbnors.engine.interact;

import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Caller-side view of a submitted operation: status, result, cancellation. Scripts that want
 * blocking semantics call {@link #await()}; others compose on {@link #future()}.
 */
public final class OpHandle<T> {

    public enum Status { QUEUED, RUNNING, COMPLETED, CANCELLED, FAILED }

    private final Operation<T> op;
    private final CompletableFuture<T> future = new CompletableFuture<>();
    final OpContext ctx;
    final long id;
    private volatile Status status = Status.QUEUED;
    private final InteractionManager owner;

    OpHandle(Operation<T> op, OpContext ctx, long id, InteractionManager owner) {
        this.op = op;
        this.ctx = ctx;
        this.id = id;
        this.owner = owner;
    }

    public Operation<T> operation() {
        return op;
    }

    public Status status() {
        return status;
    }

    void status(Status s) {
        status = s;
    }

    public CompletableFuture<T> future() {
        return future;
    }

    public boolean isDone() {
        return future.isDone();
    }

    /** Requests cancellation. Queued operations are removed; running ones are interrupted. */
    public void cancel() {
        owner.cancel(this);
    }

    /**
     * Blocks until the operation finishes.
     *
     * @return the result
     * @throws CancellationException if cancelled
     * @throws OperationFailedException if the operation threw
     */
    public T await() {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CancellationException("interrupted while awaiting " + op.name());
        } catch (ExecutionException e) {
            throw unwrap(e);
        }
    }

    public T await(Duration timeout) throws TimeoutException {
        try {
            return future.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CancellationException("interrupted while awaiting " + op.name());
        } catch (ExecutionException e) {
            throw unwrap(e);
        }
    }

    private RuntimeException unwrap(ExecutionException e) {
        Throwable c = e.getCause();
        if (c instanceof CancellationException ce) {
            return ce;
        }
        return new OperationFailedException(op.name(), c);
    }

    @Override
    public String toString() {
        return op.name() + "#" + id + "[" + status + "]";
    }

    /** Wraps an exception thrown by an operation body. */
    public static final class OperationFailedException extends RuntimeException {
        public OperationFailedException(String op, Throwable cause) {
            super("operation '" + op + "' failed: " + cause, cause);
        }
    }
}
