package org.bbnors.api.model;

import org.bbnors.core.geom.Rect;

import java.util.List;

/**
 * Anything in the game world a script can find and interact with. Implementations are provided by
 * the game adapter and may be live views or snapshots; {@link #valid()} reports whether the
 * underlying object still exists.
 */
public interface Entity {

    int id();

    String name();

    Tile tile();

    /** Interaction actions in menu order; the first is the left-click default. */
    List<String> actions();

    /** Clickable screen area, or {@code null} if not currently on screen. */
    Rect screenBounds();

    boolean valid();

    default boolean hasAction(String action) {
        return actions().stream().anyMatch(a -> a.equalsIgnoreCase(action));
    }

    default boolean onScreen() {
        return screenBounds() != null;
    }
}
