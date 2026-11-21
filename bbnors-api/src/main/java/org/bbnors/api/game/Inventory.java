package org.bbnors.api.game;

import org.bbnors.api.model.Item;
import org.bbnors.core.geom.Rect;

import java.util.List;
import java.util.Optional;

/** Inventory queries and actions. Item clicks are menu-aware via {@link Interactions}. */
public final class Inventory {

    private final GameState state;
    private final Interactions interactions;

    public Inventory(GameState state, Interactions interactions) {
        this.state = state;
        this.interactions = interactions;
    }

    public List<Item> items() {
        return state.inventoryList();
    }

    public Optional<Item> first(String name) {
        return items().stream().filter(i -> i.name().equalsIgnoreCase(name)).findFirst();
    }

    public boolean contains(String name) {
        return first(name).isPresent();
    }

    /** Total quantity of {@code name} across stacks. */
    public int count(String name) {
        return items().stream().filter(i -> i.name().equalsIgnoreCase(name)).mapToInt(Item::quantity).sum();
    }

    public int usedSlots() {
        return items().size();
    }

    public int emptySlots() {
        return state.client().inventoryCapacity() - usedSlots();
    }

    public boolean isFull() {
        return emptySlots() <= 0;
    }

    public boolean isEmpty() {
        return items().isEmpty();
    }

    /** Clicks {@code action} on the first {@code name} stack. */
    public boolean interact(String name, String action) {
        Optional<Item> item = first(name);
        if (item.isEmpty() || !item.get().hasAction(action)) {
            return false;
        }
        Item it = item.get();
        Rect slot = state.client().layout().inventorySlot(it.slot()).inset(4);
        boolean ok = interactions.click(() -> slot, action, it.name());
        state.invalidate();
        return ok;
    }

    public boolean drop(String name) {
        return interact(name, "Drop");
    }

    /** Drops every {@code name} stack; returns the number of drop actions performed. */
    public int dropAll(String name) {
        int n = 0;
        while (contains(name) && drop(name)) {
            n++;
            if (n > 64) break; // safety: never loop forever if the client ignores drops
        }
        return n;
    }
}
