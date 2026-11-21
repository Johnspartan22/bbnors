package org.bbnors.api.model;

import org.bbnors.core.geom.Vec2;
import org.bbnors.engine.camera.CameraModel;

import java.util.List;
import java.util.Optional;

/**
 * The adapter SPI: everything $bbnors needs to read from a game client. Implement this once per
 * client/server base (see {@code org.bbnors.api.sim.SimulatedGame} for a complete reference
 * implementation) and every manager, query and script works unchanged.
 *
 * <p>Methods are called from script and engine threads; implementations must be thread-safe.
 * Lists may be snapshots. The {@link org.bbnors.api.game.GameState} wrapper caches entity lists
 * per game tick, so adapters do not need their own caching.
 */
public interface GameClient {

    boolean loggedIn();

    /** Monotonic game tick counter (used for per-tick caching and tick waits). */
    int tick();

    Player localPlayer();

    List<Npc> npcs();

    List<Player> players();

    List<GameObject> objects();

    List<GroundItem> groundItems();

    /** Inventory items (only occupied slots). */
    List<Item> inventory();

    int inventoryCapacity();

    /** Equipped items; {@link Item#slot()} is the equipment slot index. */
    List<Item> equipment();

    boolean bankOpen();

    /** Bank contents (only meaningful while open). */
    List<Item> bank();

    Optional<Widget> widget(int group, int child);

    MenuModel menu();

    InterfaceLayout layout();

    CollisionMap collision();

    CameraModel camera();

    /** Screen position of a tile's centre, or {@code null} if not in the viewport. */
    Vec2 tileToScreen(Tile tile);

    /** Minimap position of a tile, or {@code null} if outside the minimap. */
    Vec2 tileToMinimap(Tile tile);

    /**
     * Extension point for backend-specific capabilities (skills, varbits, chat, prayers, ...)
     * that are not part of the core contract. Backends return an implementation of
     * {@code type} if they support it; generic code checks before use:
     * <pre>{@code
     *   client.extension(Skills.class).ifPresent(s -> log.info("WC level " + s.level("woodcutting")));
     * }</pre>
     * This keeps the core interface small and stable while letting each backend grow.
     */
    default <T> Optional<T> extension(Class<T> type) {
        return Optional.empty();
    }

    /**
     * Camera yaw at which {@code to} is straight ahead when standing on {@code from}. The default
     * uses the OSRS-style convention (0 = north, 2048 units per revolution, increasing
     * counter-clockwise); override if your client differs.
     */
    default int yawFacing(Tile from, Tile to) {
        double theta = Math.atan2(to.x() - from.x(), to.y() - from.y());
        return Math.floorMod((int) Math.round(theta * camera().yawRange() / (2 * Math.PI)), camera().yawRange());
    }
}
