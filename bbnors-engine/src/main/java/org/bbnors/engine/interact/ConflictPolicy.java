package org.bbnors.engine.interact;

/** What to do when a submitted operation needs a channel that is busy. */
public enum ConflictPolicy {
    /** Wait for the channel(s); ordered by priority, then submission order. */
    QUEUE,
    /** Cancel running operations holding the channel(s) if their priority is not higher. */
    PREEMPT,
    /** Fail immediately with {@link ChannelBusyException}. */
    REJECT
}
