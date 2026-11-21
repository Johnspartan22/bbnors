package org.bbnors.api.model;

/** A moving character (player or NPC). */
public interface Actor extends Entity {

    /** Current animation id, or -1 when idle. */
    int animation();

    boolean moving();

    /** Health as a fraction in [0,1], or -1 if unknown. */
    default double health() {
        return -1;
    }

    default boolean idle() {
        return animation() == -1 && !moving();
    }
}
