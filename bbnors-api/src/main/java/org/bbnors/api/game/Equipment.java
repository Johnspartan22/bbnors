package org.bbnors.api.game;

import org.bbnors.api.model.Item;
import org.bbnors.engine.InteractionEngine;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** Equipped items and equipping from the inventory. */
public final class Equipment {

    /** Conventional OSRS equipment slot indices. */
    public static final int HEAD = 0, CAPE = 1, NECK = 2, WEAPON = 3, BODY = 4, SHIELD = 5,
            LEGS = 7, HANDS = 9, FEET = 10, RING = 12, AMMO = 13;

    private final GameState state;
    private final InteractionEngine engine;
    private final Inventory inventory;

    public Equipment(GameState state, InteractionEngine engine, Inventory inventory) {
        this.state = state;
        this.engine = engine;
        this.inventory = inventory;
    }

    public List<Item> items() {
        return state.client().equipment();
    }

    public Optional<Item> inSlot(int slot) {
        return items().stream().filter(i -> i.slot() == slot).findFirst();
    }

    public boolean isEquipped(String name) {
        return items().stream().anyMatch(i -> i.name().equalsIgnoreCase(name));
    }

    /** Equips {@code name} from the inventory using whichever of Wield / Wear it offers. */
    public boolean equip(String name) {
        if (isEquipped(name)) return true;
        Optional<Item> it = inventory.first(name);
        if (it.isEmpty()) return false;
        String action = it.get().hasAction("Wield") ? "Wield" : it.get().hasAction("Wear") ? "Wear" : null;
        if (action == null || !inventory.interact(name, action)) return false;
        return engine.waitUntil(() -> isEquipped(name)).timeout(Duration.ofSeconds(2)).test();
    }
}
