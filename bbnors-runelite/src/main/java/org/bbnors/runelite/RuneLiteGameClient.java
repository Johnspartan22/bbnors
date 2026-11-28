package org.bbnors.runelite;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemID;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.game.ItemManager;

import org.bbnors.api.model.CollisionMap;
import org.bbnors.api.model.GameClient;
import org.bbnors.api.model.GameObject;
import org.bbnors.api.model.GroundItem;
import org.bbnors.api.model.InterfaceLayout;
import org.bbnors.api.model.Item;
import org.bbnors.api.model.MenuModel;
import org.bbnors.api.model.Npc;
import org.bbnors.api.model.Player;
import org.bbnors.core.geom.Vec2;
import org.bbnors.engine.camera.CameraModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Adapts RuneLite's {@link Client} to bbnors's {@link GameClient} interface.
 *
 * <p><b>Learning notes:</b> This is the central piece of the official-client adapter. Every
 * method maps one RuneLite API call (or a small group of them) to the contract that bbnors
 * scripts expect. The key insight is that the {@code GameClient} interface was designed to be
 * thin — it asks for snapshots of game state and doesn't care how they're obtained.
 *
 * <p><b>Thread safety:</b> RuneLite's client methods are generally safe to call from any thread
 * for reads, but values can change between calls. The bbnors {@code GameState} wrapper caches
 * per tick, so we don't need our own caching here — just null-safe reads.
 *
 * <p><b>What's here vs what's missing:</b> This covers the core contract (entities, containers,
 * basic interface, collision, camera). Extensions like skills, prayer, and chat are future work
 * via {@link #extension(Class)}.
 */
public final class RuneLiteGameClient implements GameClient {

    private final Client client;
    private final ItemManager itemManager;
    private final RuneLiteMenuModel menuModel;
    private final RuneLiteCollisionMap collisionMap;
    private final RuneLiteInterfaceLayout interfaceLayout;
    private final CameraModel cameraModel;

    public RuneLiteGameClient(Client client, ItemManager itemManager) {
        this.client = client;
        this.itemManager = itemManager;
        this.menuModel = new RuneLiteMenuModel(client);
        this.collisionMap = new RuneLiteCollisionMap(client);
        this.interfaceLayout = new RuneLiteInterfaceLayout(client);
        // OSRS camera: 2048 units per yaw revolution, pitch range 128-383
        this.cameraModel = new CameraModel(2048, 128, 383, 1.0);
    }

    // ------------------------------------------------------------------ session

    /**
     * Learning: RuneLite's GameState enum has LOGGED_IN, LOADING, LOGIN_SCREEN, etc.
     * We only report true when the player is fully in the game world.
     */
    @Override
    public boolean loggedIn() {
        return client.getGameState() == GameState.LOGGED_IN;
    }

    /**
     * Learning: The tick counter increases by 1 every 600ms game tick. RuneLite exposes it
     * directly. bbnors uses this for per-tick caching in GameState and for tick-based waits.
     */
    @Override
    public int tick() {
        return client.getTickCount();
    }

    // ------------------------------------------------------------------ entities

    /**
     * Learning: RuneLite's getLocalPlayer() returns the player character we control.
     * We wrap it in RuneLitePlayer which adapts RuneLite's Player interface to bbnors's.
     */
    @Override
    public Player localPlayer() {
        net.runelite.api.Player p = client.getLocalPlayer();
        return p != null ? new RuneLitePlayer(p, client) : null;
    }

    /**
     * Learning: RuneLite gives us all loaded NPCs. We wrap each one. The list is a snapshot —
     * NPCs can despawn between when we read this and when a script acts on one, which is why
     * Entity.valid() exists.
     */
    @Override
    public List<Npc> npcs() {
        List<net.runelite.api.NPC> rlNpcs = client.getNpcs();
        if (rlNpcs == null) return Collections.emptyList();
        List<Npc> out = new ArrayList<>(rlNpcs.size());
        for (net.runelite.api.NPC n : rlNpcs) {
            if (n != null) {
                out.add(new RuneLiteNpc(n, client));
            }
        }
        return out;
    }

    @Override
    public List<Player> players() {
        List<net.runelite.api.Player> rlPlayers = client.getPlayers();
        if (rlPlayers == null) return Collections.emptyList();
        List<Player> out = new ArrayList<>(rlPlayers.size());
        for (net.runelite.api.Player p : rlPlayers) {
            if (p != null) {
                out.add(new RuneLitePlayer(p, client));
            }
        }
        return out;
    }

    /**
     * Learning: Game objects in OSRS are static scene elements (trees, rocks, doors, etc.).
     * RuneLite stores them per-tile in the scene. We iterate all loaded tiles and collect
     * the objects. This is more expensive than the NPC list but objects rarely change.
     */
    @Override
    public List<GameObject> objects() {
        List<GameObject> out = new ArrayList<>();
        Scene scene = client.getScene();
        if (scene == null) return out;
        Tile[][][] tiles = scene.getTiles();
        if (tiles == null) return out;
        int plane = client.getPlane();
        if (plane < 0 || plane >= tiles.length || tiles[plane] == null) return out;
        for (Tile[] row : tiles[plane]) {
            if (row == null) continue;
            for (Tile tile : row) {
                if (tile == null) continue;
                net.runelite.api.GameObject[] gameObjects = tile.getGameObjects();
                if (gameObjects != null) {
                    for (net.runelite.api.GameObject go : gameObjects) {
                        if (go != null) {
                            out.add(new RuneLiteGameObject(go, client));
                        }
                    }
                }
            }
        }
        return out;
    }

    /**
     * Learning: Ground items sit on tiles. RuneLite tracks them per-tile in the scene.
     * Each TileItem has an ID and quantity. We walk the scene tiles to collect them.
     */
    @Override
    public List<GroundItem> groundItems() {
        List<GroundItem> out = new ArrayList<>();
        Scene scene = client.getScene();
        if (scene == null) return out;
        Tile[][][] tiles = scene.getTiles();
        if (tiles == null) return out;
        int plane = client.getPlane();
        if (plane < 0 || plane >= tiles.length || tiles[plane] == null) return out;
        for (Tile[] row : tiles[plane]) {
            if (row == null) continue;
            for (Tile tile : row) {
                if (tile == null) continue;
                List<TileItem> items = tile.getGroundItems();
                if (items != null) {
                    for (TileItem ti : items) {
                        if (ti != null) {
                            out.add(new RuneLiteGroundItem(ti, tile, client, itemManager));
                        }
                    }
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ containers

    /**
     * Learning: OSRS containers (inventory, bank, equipment) are identified by InventoryID.
     * RuneLite gives us the raw item arrays. We convert to bbnors Items with names looked up
     * through ItemManager.
     */
    @Override
    public List<Item> inventory() {
        return containerToItems(InventoryID.INVENTORY);
    }

    @Override
    public int inventoryCapacity() {
        return 28;
    }

    @Override
    public List<Item> equipment() {
        return containerToItems(InventoryID.EQUIPMENT);
    }

    /**
     * Learning: The bank is open when its top-level widget is visible. Widget group 12 is
     * the bank interface in OSRS's widget system.
     */
    @Override
    public boolean bankOpen() {
        Widget bankWidget = client.getWidget(WidgetInfo.BANK_CONTAINER);
        return bankWidget != null && !bankWidget.isHidden();
    }

    @Override
    public List<Item> bank() {
        return containerToItems(InventoryID.BANK);
    }

    private List<Item> containerToItems(InventoryID id) {
        ItemContainer container = client.getItemContainer(id);
        if (container == null) return Collections.emptyList();
        net.runelite.api.Item[] raw = container.getItems();
        if (raw == null) return Collections.emptyList();
        List<Item> out = new ArrayList<>();
        for (int slot = 0; slot < raw.length; slot++) {
            net.runelite.api.Item ri = raw[slot];
            if (ri != null && ri.getId() != -1 && ri.getId() != 0) {
                String name = itemManager.getItemComposition(ri.getId()).getName();
                // Item actions depend on context; for now use a basic set
                List<String> actions = List.of("Use", "Drop", "Examine");
                out.add(new Item(ri.getId(), name != null ? name : "Unknown", ri.getQuantity(), slot, actions));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ interface

    /**
     * Learning: OSRS's interface system uses (group, child) pairs to identify widgets.
     * RuneLite exposes them directly. We wrap the RuneLite Widget into bbnors's Widget record.
     */
    @Override
    public Optional<org.bbnors.api.model.Widget> widget(int group, int child) {
        Widget w = client.getWidget(group, child);
        if (w == null || w.isHidden()) {
            return Optional.empty();
        }
        return Optional.of(new org.bbnors.api.model.Widget(
                group, child,
                w.getText() != null ? w.getText() : "",
                new org.bbnors.core.geom.Rect(
                        w.getCanvasLocation().getX(),
                        w.getCanvasLocation().getY(),
                        w.getWidth(),
                        w.getHeight()),
                !w.isHidden(),
                w.getActions() != null ? List.of(w.getActions()) : List.of()
        ));
    }

    @Override
    public MenuModel menu() {
        return menuModel;
    }

    @Override
    public InterfaceLayout layout() {
        return interfaceLayout;
    }

    // ------------------------------------------------------------------ world

    @Override
    public CollisionMap collision() {
        return collisionMap;
    }

    @Override
    public CameraModel camera() {
        return cameraModel;
    }

    /**
     * Learning: Converting a world tile to a screen pixel requires two steps in RuneLite:
     * 1. WorldPoint → LocalPoint (scene-relative coordinates)
     * 2. LocalPoint → canvas point via Perspective.localToCanvas()
     * This is one of the trickiest parts of the adapter because OSRS uses a 3D projection.
     */
    @Override
    public Vec2 tileToScreen(org.bbnors.api.model.Tile tile) {
        WorldPoint wp = new WorldPoint(tile.x(), tile.y(), tile.plane());
        LocalPoint lp = LocalPoint.fromWorld(client, wp);
        if (lp == null) return null;
        net.runelite.api.Point canvas = net.runelite.api.Perspective.localToCanvas(
                client, lp, client.getPlane());
        return canvas != null ? new Vec2(canvas.getX(), canvas.getY()) : null;
    }

    /**
     * Learning: Minimap projection is simpler than the viewport — it's a top-down rotated view.
     * RuneLite has Perspective.localToMinimap() for this.
     */
    @Override
    public Vec2 tileToMinimap(org.bbnors.api.model.Tile tile) {
        WorldPoint wp = new WorldPoint(tile.x(), tile.y(), tile.plane());
        LocalPoint lp = LocalPoint.fromWorld(client, wp);
        if (lp == null) return null;
        net.runelite.api.Point mm = net.runelite.api.Perspective.localToMinimap(
                client, lp);
        return mm != null ? new Vec2(mm.getX(), mm.getY()) : null;
    }
}
