package org.bbnors.api.game;

import org.bbnors.api.model.Widget;

import java.util.Optional;

/** Interface components by (group, child). */
public final class Widgets {

    private final GameState state;
    private final Interactions interactions;

    public Widgets(GameState state, Interactions interactions) {
        this.state = state;
        this.interactions = interactions;
    }

    public Optional<Widget> get(int group, int child) {
        return state.client().widget(group, child);
    }

    public boolean isVisible(int group, int child) {
        return get(group, child).map(Widget::visible).orElse(false);
    }

    /** Menu-aware click of {@code action} on a visible widget. */
    public boolean interact(int group, int child, String action) {
        Optional<Widget> w = get(group, child).filter(Widget::visible);
        if (w.isEmpty()) return false;
        Widget widget = w.get();
        if (widget.actions().isEmpty()) {
            interactions.leftClick(() -> widget.bounds().inset(2));
            return true;
        }
        return interactions.click(() -> widget.bounds().inset(2), action, widget.text());
    }
}
