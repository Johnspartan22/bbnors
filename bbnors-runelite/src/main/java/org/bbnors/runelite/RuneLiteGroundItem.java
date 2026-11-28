package org.bbnors.runelite;

import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.TileItem;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.game.ItemManager;

import org.bbnors.api.model.GroundItem;
import org.bbnors.api.model.Tile;
import org.bbnors.core.geom.Rect;

import java.util.List;

/**
 * Adapts a RuneLite {@link TileItem} to bbnors's {@link GroundItem}.
 *
 * <p><b>Learning notes:</b> Ground items in OSRS are items lying on tiles. They can be
 * visible to everyone or only to the player who dropped them, depending on how long ago
 * they were dropped. RuneLite gives us the TileItem (ID, quantity) and the Tile it sits on.
 * We look up the item name from the cache via ItemManager.
 */
final class RuneLiteGroundItem implements GroundItem {

    private final TileItem item;
    private final net.runelite.api.Tile tile;
    private final Client client;
    private final ItemManager itemManager;

    RuneLiteGroundItem(TileItem item, net.runelite.api.Tile tile, Client client, ItemManager itemManager) {
        this.item = item;
        this.tile = tile;
        this.client = client;
        this.itemManager = itemManager;
    }

    @Override
    public int id() {
        return item.getId();
    }

    @Override
    public String name() {
        ItemComposition comp = itemManager.getItemComposition(item.getId());
        return comp != null && comp.getName() != null ? comp.getName() : "Unknown";
    }

    @Override
    public Tile tile() {
        WorldPoint wp = tile.getWorldLocation();
        return wp != null ? new Tile(wp.getX(), wp.getY(), wp.getPlane()) : new Tile(0, 0, 0);
    }

    @Override
    public List<String> actions() {
        return List.of("Take", "Examine");
    }

    /**
     * Learning: Ground items don't have a clickbox like game objects. Their screen position
     * is derived from the tile they sit on. We use the tile's canvas position and a small
     * fixed rectangle. In practice, the click area is small because items render as small
     * sprites on the ground.
     */
    @Override
    public Rect screenBounds() {
        net.runelite.api.coords.LocalPoint lp = tile.getLocalLocation();
        if (lp == null) return null;
        net.runelite.api.Point canvas = net.runelite.api.Perspective.localToCanvas(
                client, lp, client.getPlane());
        if (canvas == null) return null;
        // Ground items render as small sprites; approximate with a 16x12 rectangle
        return new Rect(canvas.getX() - 8, canvas.getY() - 6, 16, 12);
    }

    @Override
    public int quantity() {
        return item.getQuantity();
    }

    @Override
    public boolean valid() {
        // Check if the item is still on this tile
        List<TileItem> items = tile.getGroundItems();
        return items != null && items.contains(item);
    }
}
