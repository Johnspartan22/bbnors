package org.bbnors.api.model;

import org.bbnors.core.geom.Rect;

import java.util.List;

/** An interface component addressed by (group, child). */
public record Widget(int group, int child, String text, Rect bounds, boolean visible, List<String> actions) {

    public Widget {
        actions = List.copyOf(actions);
    }
}
