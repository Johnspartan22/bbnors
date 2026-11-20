package org.bbnors.engine.interact;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * A unit of input work (move, click, type, rotate camera, ...) executed by the
 * {@link InteractionManager} while holding its {@link #channels()}.
 *
 * <p>Implementations put their logic in {@link #execute(OpContext)}, must poll
 * {@link OpContext#checkCancelled()} (or sleep through the context) regularly, and must leave
 * input in a neutral state when they end for any reason, e.g. release pressed keys in a
 * {@code finally} block.
 *
 * @param <T> result type
 */
public abstract class Operation<T> {

    private final String name;
    private final Set<Channel> channels;
    private final int priority;
    private final ConflictPolicy policy;

    protected Operation(String name, Set<Channel> channels, int priority, ConflictPolicy policy) {
        if (channels.isEmpty()) {
            throw new IllegalArgumentException("operation must use at least one channel");
        }
        this.name = Objects.requireNonNull(name);
        this.channels = EnumSet.copyOf(channels);
        this.priority = priority;
        this.policy = Objects.requireNonNull(policy);
    }

    protected Operation(String name, Set<Channel> channels) {
        this(name, channels, 0, ConflictPolicy.QUEUE);
    }

    protected abstract T execute(OpContext ctx) throws Exception;

    public final String name() {
        return name;
    }

    public final Set<Channel> channels() {
        return channels;
    }

    public final int priority() {
        return priority;
    }

    public final ConflictPolicy policy() {
        return policy;
    }

    /** Wraps a lambda as an operation. */
    public static <T> Operation<T> of(String name, Set<Channel> channels, int priority, ConflictPolicy policy, Body<T> body) {
        return new Operation<>(name, channels, priority, policy) {
            @Override
            protected T execute(OpContext ctx) throws Exception {
                return body.run(ctx);
            }
        };
    }

    public static <T> Operation<T> of(String name, Set<Channel> channels, Body<T> body) {
        return of(name, channels, 0, ConflictPolicy.QUEUE, body);
    }

    /** Functional body for {@link #of}. */
    @FunctionalInterface
    public interface Body<T> {
        T run(OpContext ctx) throws Exception;
    }

    @Override
    public String toString() {
        return name + channels;
    }
}
