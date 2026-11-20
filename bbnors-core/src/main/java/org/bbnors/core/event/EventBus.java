package org.bbnors.core.event;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Minimal synchronous, type-keyed publish/subscribe bus.
 *
 * <p>Design choices:
 * <ul>
 *   <li>Dispatch is synchronous on the publishing thread: no hidden executor, no ordering
 *       surprises, and zero allocation per publish when nobody is subscribed.</li>
 *   <li>Subscribers are stored in {@link CopyOnWriteArrayList}s, which favours the common case
 *       of many publishes and rare (un)subscribes.</li>
 *   <li>Matching is by exact class and by every supertype/interface, resolved once per event
 *       class and cached.</li>
 *   <li>A throwing subscriber never prevents delivery to the others; the error goes to the
 *       configured error handler.</li>
 * </ul>
 */
public final class EventBus {

    private final Map<Class<?>, CopyOnWriteArrayList<Consumer<Object>>> subscribers = new ConcurrentHashMap<>();
    private final Map<Class<?>, List<Class<?>>> hierarchyCache = new ConcurrentHashMap<>();
    private volatile Consumer<Throwable> errorHandler = t -> t.printStackTrace(System.err);

    /** Registers {@code handler} for events of type {@code type} (and subtypes). */
    @SuppressWarnings("unchecked")
    public <E> Subscription subscribe(Class<E> type, Consumer<? super E> handler) {
        Consumer<Object> erased = (Consumer<Object>) handler;
        CopyOnWriteArrayList<Consumer<Object>> list = subscribers.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>());
        list.add(erased);
        return () -> list.remove(erased);
    }

    /** Delivers {@code event} to every subscriber of its class or any supertype. */
    public void publish(Object event) {
        if (subscribers.isEmpty()) {
            return;
        }
        for (Class<?> c : hierarchy(event.getClass())) {
            CopyOnWriteArrayList<Consumer<Object>> list = subscribers.get(c);
            if (list == null) {
                continue;
            }
            for (Consumer<Object> h : list) {
                try {
                    h.accept(event);
                } catch (RuntimeException e) {
                    errorHandler.accept(e);
                }
            }
        }
    }

    public boolean hasSubscribers(Class<?> type) {
        CopyOnWriteArrayList<Consumer<Object>> list = subscribers.get(type);
        return list != null && !list.isEmpty();
    }

    public void setErrorHandler(Consumer<Throwable> errorHandler) {
        this.errorHandler = errorHandler;
    }

    private List<Class<?>> hierarchy(Class<?> type) {
        return hierarchyCache.computeIfAbsent(type, EventBus::computeHierarchy);
    }

    private static List<Class<?>> computeHierarchy(Class<?> type) {
        java.util.LinkedHashSet<Class<?>> out = new java.util.LinkedHashSet<>();
        java.util.ArrayDeque<Class<?>> work = new java.util.ArrayDeque<>();
        work.add(type);
        while (!work.isEmpty()) {
            Class<?> c = work.poll();
            if (c == null || !out.add(c)) {
                continue;
            }
            if (c.getSuperclass() != null) {
                work.add(c.getSuperclass());
            }
            work.addAll(List.of(c.getInterfaces()));
        }
        return List.copyOf(out);
    }

    /** Handle returned by {@link #subscribe}; call {@link #cancel()} to unsubscribe. */
    @FunctionalInterface
    public interface Subscription extends AutoCloseable {
        void cancel();

        @Override
        default void close() {
            cancel();
        }
    }
}
