package org.bbnors.api.game;

import org.bbnors.api.model.GameClient;
import org.bbnors.api.model.GameObject;
import org.bbnors.api.model.GroundItem;
import org.bbnors.api.model.Item;
import org.bbnors.api.model.Npc;
import org.bbnors.api.model.Player;
import org.bbnors.api.model.Tile;
import org.bbnors.api.query.EntityQuery;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Per-tick cached view of a {@link GameClient}.
 *
 * <p>Game state only changes on game ticks (600 ms in OSRS), but a script loop typically asks for
 * the same lists many times per tick ("is there a tree?", "nearest tree", "count trees"...).
 * Each list here is fetched from the adapter at most once per tick and then shared. The cache is
 * keyed on {@link GameClient#tick()}, so it can never serve data from a previous tick.
 * Hit/miss counters are exposed for diagnostics and the benchmark suite.
 */
public final class GameState {

    private final GameClient client;
    private final TickCached<List<Npc>> npcs;
    private final TickCached<List<Player>> players;
    private final TickCached<List<GameObject>> objects;
    private final TickCached<List<GroundItem>> groundItems;
    private final TickCached<List<Item>> inventory;
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();

    public GameState(GameClient client) {
        this.client = client;
        this.npcs = new TickCached<>(client::npcs);
        this.players = new TickCached<>(client::players);
        this.objects = new TickCached<>(client::objects);
        this.groundItems = new TickCached<>(client::groundItems);
        this.inventory = new TickCached<>(client::inventory);
    }

    public GameClient client() {
        return client;
    }

    public int tick() {
        return client.tick();
    }

    public Player localPlayer() {
        return client.localPlayer();
    }

    public Tile playerTile() {
        Player p = client.localPlayer();
        return p == null ? null : p.tile();
    }

    public List<Npc> npcList() {
        return npcs.get();
    }

    public List<Player> playerList() {
        return players.get();
    }

    public List<GameObject> objectList() {
        return objects.get();
    }

    public List<GroundItem> groundItemList() {
        return groundItems.get();
    }

    public List<Item> inventoryList() {
        return inventory.get();
    }

    public EntityQuery<Npc> npcs() {
        return new EntityQuery<>(npcs, this::playerTile);
    }

    public EntityQuery<Player> players() {
        return new EntityQuery<>(players, this::playerTile);
    }

    public EntityQuery<GameObject> objects() {
        return new EntityQuery<>(objects, this::playerTile);
    }

    public EntityQuery<GroundItem> groundItems() {
        return new EntityQuery<>(groundItems, this::playerTile);
    }

    public long cacheHits() {
        return hits.get();
    }

    public long cacheMisses() {
        return misses.get();
    }

    /** Drops cached data (e.g. after an action known to change state within the same tick). */
    public void invalidate() {
        npcs.invalidate();
        players.invalidate();
        objects.invalidate();
        groundItems.invalidate();
        inventory.invalidate();
    }

    private final class TickCached<T> implements Supplier<T> {
        private final Supplier<T> source;
        private volatile Entry<T> entry;

        TickCached(Supplier<T> source) {
            this.source = source;
        }

        @Override
        public T get() {
            int tick = client.tick();
            Entry<T> e = entry;
            if (e != null && e.tick == tick) {
                hits.incrementAndGet();
                return e.value;
            }
            misses.incrementAndGet();
            T v = source.get();
            entry = new Entry<>(tick, v);
            return v;
        }

        void invalidate() {
            entry = null;
        }
    }

    private record Entry<T>(int tick, T value) { }
}
