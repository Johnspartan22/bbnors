package org.bbnors.engine.interact;

/** Lifecycle events published by the {@link InteractionManager} on its event bus. */
public sealed interface InteractionEvent {

    OpHandle<?> handle();

    record Queued(OpHandle<?> handle) implements InteractionEvent { }

    record Started(OpHandle<?> handle) implements InteractionEvent { }

    record Completed(OpHandle<?> handle, long durationNanos) implements InteractionEvent { }

    record Cancelled(OpHandle<?> handle, String reason) implements InteractionEvent { }

    record Failed(OpHandle<?> handle, Throwable error) implements InteractionEvent { }
}
