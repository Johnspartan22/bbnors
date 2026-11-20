package org.bbnors.engine.interact;

import java.util.Set;

/** Raised for {@link ConflictPolicy#REJECT} operations whose channels are busy. */
public final class ChannelBusyException extends RuntimeException {

    public ChannelBusyException(String op, Set<Channel> busy) {
        super("operation '" + op + "' rejected: channels busy " + busy);
    }
}
