package org.bbnors.runelite;

import net.runelite.api.Client;
import net.runelite.api.ObjectComposition;
import net.runelite.api.coords.WorldPoint;

import org.bbnors.api.model.GameObject;
import org.bbnors.api.model.Tile;
import org.bbnors.core.geom.Rect;

import java.awt.Shape;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Adapts a RuneLite {@link net.runelite.api.GameObject} to bbnors's {@link GameObject}.
 *
 * <p><b>Learning notes:</b> Game objects are static scene elements: trees, rocks, doors,
 * bank booths, furnaces, etc. Like NPCs, their definition data (name, actions) comes from
 * an object "composition" loaded from the game cache. Some objects transform based on game
 * state (e.g., a tree becoming a stump), handled by getImpostor().
 *
 * <p><b>Screen bounds:</b> Game objects provide a "clickbox" — the shape on screen where
 * clicks register. We convert it to a bounding Rect.
 */
final class RuneLiteGameObject implements GameObject {

    private final net.runelite.api.GameObject obj;
    private final Client client;

    RuneLiteGameObject(net.runelite.api.GameObject obj, Client client) {
        this.obj = obj;
        this.client = client;
    }

    @Override
    public int id() {
        return obj.getId();
    }

    /**
     * Learning: Object names come from ObjectComposition. Some objects are "impostors" —
     * their appearance changes based on varbits (e.g., a farming patch showing different
     * crops). getImpostorIds() and getImpostor() handle these transformations.
     */
    @Override
    public String name() {
        ObjectComposition comp = client.getObjectDefinition(obj.getId());
        if (comp != null) {
            ObjectComposition impostor = comp.getImpostor();
            if (impostor != null) {
                String name = impostor.getName();
                if (name != null && !name.equals("null")) return name;
            }
            String name = comp.getName();
            if (name != null && !name.equals("null")) return name;
        }
        return "Unknown";
    }

    @Override
    public Tile tile() {
        WorldPoint wp = obj.getWorldLocation();
        return wp != null ? new Tile(wp.getX(), wp.getY(), wp.getPlane()) : new Tile(0, 0, 0);
    }

    /**
     * Learning: Object actions (Chop down, Mine, Open, etc.) come from the composition,
     * same pattern as NPC actions — a fixed-size array with null gaps for unused slots.
     */
    @Override
    public List<String> actions() {
        ObjectComposition comp = client.getObjectDefinition(obj.getId());
        if (comp == null) return Collections.emptyList();
        ObjectComposition impostor = comp.getImpostor();
        String[] raw = impostor != null ? impostor.getActions() : comp.getActions();
        if (raw == null) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        for (String a : raw) {
            if (a != null) out.add(a);
        }
        return out;
    }

    /**
     * Learning: The clickbox is the precise on-screen area where clicks on this object
     * register. It follows the object's 3D model projected onto the screen. We use its
     * bounding rectangle for the mouse engine.
     */
    @Override
    public Rect screenBounds() {
        Shape clickbox = obj.getClickbox();
        if (clickbox == null) return null;
        java.awt.Rectangle r = clickbox.getBounds();
        return new Rect(r.x, r.y, r.width, r.height);
    }

    @Override
    public boolean valid() {
        return true; // Scene objects persist while loaded
    }
}
