package org.bbnors.api.model;

import java.util.List;

/**
 * An item stack in a container (inventory, bank, equipment).
 *
 * @param slot container slot index
 */
public record Item(int id, String name, int quantity, int slot, List<String> actions) {

    public Item {
        actions = List.copyOf(actions);
    }

    public boolean hasAction(String action) {
        return actions.stream().anyMatch(a -> a.equalsIgnoreCase(action));
    }
}
