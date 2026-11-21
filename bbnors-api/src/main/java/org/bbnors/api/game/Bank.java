package org.bbnors.api.game;

import org.bbnors.api.model.Entity;
import org.bbnors.api.model.Item;
import org.bbnors.api.model.Player;
import org.bbnors.core.geom.Rect;
import org.bbnors.engine.InteractionEngine;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** Banking: open the nearest bank, deposit, withdraw, close. All actions block until done. */
public final class Bank {

    private final GameState state;
    private final InteractionEngine engine;
    private final Interactions interactions;
    private final Walking walking;

    public Bank(GameState state, InteractionEngine engine, Interactions interactions, Walking walking) {
        this.state = state;
        this.engine = engine;
        this.interactions = interactions;
        this.walking = walking;
    }

    public boolean isOpen() {
        return state.client().bankOpen();
    }

    public List<Item> items() {
        return isOpen() ? state.client().bank() : List.of();
    }

    public int count(String name) {
        return items().stream().filter(i -> i.name().equalsIgnoreCase(name)).mapToInt(Item::quantity).sum();
    }

    /**
     * Opens the nearest bank booth / chest / banker and waits until the interface shows. If the
     * bank is not on screen (even after a camera turn), walks towards it first.
     */
    public boolean open() {
        if (isOpen()) {
            return true;
        }
        Optional<? extends Entity> bank = state.objects().action("Bank").nearest();
        if (bank.isEmpty()) {
            bank = state.npcs().action("Bank").nearest();
        }
        if (bank.isEmpty()) {
            return false;
        }
        if (!bank.get().onScreen()) {
            walking.walkTo(bank.get().tile(), 3, Duration.ofSeconds(60));
        }
        if (!interactions.interact(bank.get(), "Bank")) {
            return false;
        }
        return engine.waitUntil(this::isOpen)
                .timeout(Duration.ofSeconds(3))
                .resetWhile(() -> {
                    Player p = state.localPlayer();
                    return p != null && p.moving();
                })
                .poll(Duration.ofMillis(100)).test();
    }

    /** Deposits all of {@code name} (uses "Deposit-All" on the inventory stack). */
    public boolean depositAll(String name) {
        if (!isOpen()) return false;
        Optional<Item> item = state.client().inventory().stream().filter(i -> i.name().equalsIgnoreCase(name)).findFirst();
        if (item.isEmpty()) return true;
        Rect slot = state.client().layout().inventorySlot(item.get().slot()).inset(4);
        boolean ok = interactions.click(() -> slot, "Deposit-All", item.get().name());
        state.invalidate();
        return ok && engine.waitUntil(() -> state.client().inventory().stream().noneMatch(i -> i.name().equalsIgnoreCase(name)))
                .timeout(Duration.ofSeconds(2)).test();
    }

    /** Deposits the whole inventory with the deposit-inventory button. */
    public boolean depositInventory() {
        if (!isOpen()) return false;
        Rect b = state.client().layout().bankDepositInventory();
        interactions.leftClick(() -> b.inset(3));
        state.invalidate();
        return engine.waitUntil(() -> state.client().inventory().isEmpty()).timeout(Duration.ofSeconds(2)).test();
    }

    /** Withdraws {@code amount} (1 or {@link Integer#MAX_VALUE} for all) of {@code name}. */
    public boolean withdraw(String name, int amount) {
        if (!isOpen()) return false;
        List<Item> bank = state.client().bank();
        Optional<Item> item = bank.stream().filter(i -> i.name().equalsIgnoreCase(name)).findFirst();
        if (item.isEmpty()) return false;
        String action = amount == 1 ? "Withdraw-1" : "Withdraw-All";
        Rect slot = state.client().layout().bankSlot(item.get().slot()).inset(4);
        int before = state.client().inventory().size();
        boolean ok = interactions.click(() -> slot, action, item.get().name());
        state.invalidate();
        return ok && engine.waitUntil(() -> state.client().inventory().size() != before).timeout(Duration.ofSeconds(2)).test();
    }

    public boolean close() {
        if (!isOpen()) return true;
        Rect c = state.client().layout().bankClose();
        interactions.leftClick(() -> c.inset(2));
        return engine.waitUntil(() -> !isOpen()).timeout(Duration.ofSeconds(2)).test();
    }
}
