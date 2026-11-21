package org.bbnors.api.model;

import org.bbnors.core.geom.Rect;

/**
 * One right-click menu row.
 *
 * @param bounds screen area of the row when the menu is open; {@code null} for the hover entry
 */
public record MenuEntry(String action, String target, Rect bounds) {

    public boolean matches(String action, String target) {
        return this.action.equalsIgnoreCase(action) && (target == null || this.target.equalsIgnoreCase(target));
    }
}
