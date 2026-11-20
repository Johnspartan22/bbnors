package org.bbnors.engine.interact;

/**
 * Exclusive input resources. An operation declares the channels it needs; operations on disjoint
 * channels run concurrently (e.g. a keyboard camera rotation while the mouse travels, which is how
 * people actually play), while operations sharing a channel are serialised, queued or preempted.
 */
public enum Channel {
    MOUSE,
    KEYBOARD,
    CAMERA
}
