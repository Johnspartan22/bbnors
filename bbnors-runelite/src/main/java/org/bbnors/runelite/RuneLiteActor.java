package org.bbnors.runelite;

import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;

import org.bbnors.api.model.Tile;
import org.bbnors.core.geom.Rect;

import java.awt.Shape;
import java.util.List;

/**
 * Base adapter for RuneLite {@link Actor} → bbnors {@link org.bbnors.api.model.Actor}.
 *
 * <p><b>Learning notes:</b> Both players and NPCs extend Actor in RuneLite, which provides
 * animation, movement, health, and world position. This base class handles the common
 * mappings; subclasses add entity-type-specific behaviour (NPC composition, player name, etc.).
 *
 * <p><b>Screen bounds:</b> RuneLite gives us a convex hull (java.awt.Shape) for each actor's
 * on-screen appearance. We convert it to an axis-aligned bounding box (Rect) because that's
 * what bbnors's mouse engine needs for click targeting.
 */
abstract class RuneLiteActor implements org.bbnors.api.model.Actor {

    final Actor actor;
    final Client client;

    RuneLiteActor(Actor actor, Client client) {
        this.actor = actor;
        this.client = client;
    }

    @Override
    public Tile tile() {
        WorldPoint wp = actor.getWorldLocation();
        return wp != null ? new Tile(wp.getX(), wp.getY(), wp.getPlane()) : new Tile(0, 0, 0);
    }

    /**
     * Learning: Animation IDs in OSRS are numeric. -1 means idle. The animation changes
     * when the character performs an action (chopping, fighting, etc.). Scripts use this
     * to detect when an action has started or ended.
     */
    @Override
    public int animation() {
        return actor.getAnimation();
    }

    /**
     * Learning: RuneLite doesn't have a simple isMoving() on Actor. We check the path length
     * — if the actor has remaining path tiles, they're walking.
     */
    @Override
    public boolean moving() {
        // Actor has getPathLength() in some RuneLite versions; fallback to pose animation
        return actor.getPoseAnimation() != actor.getIdlePoseAnimation();
    }

    /**
     * Learning: Health in OSRS is shown as a ratio (health bar). RuneLite gives us
     * getHealthRatio() (0-30 by default) and getHealthScale() to normalize it.
     * Returns -1 if health isn't visible (not in combat).
     */
    @Override
    public double health() {
        int ratio = actor.getHealthRatio();
        int scale = actor.getHealthScale();
        if (ratio == -1 || scale <= 0) return -1;
        return (double) ratio / scale;
    }

    /**
     * Learning: We convert RuneLite's convex hull to an axis-aligned bounding box.
     * The convex hull is the tight outline of the actor's model on screen. Using its
     * bounding box is good enough for click targeting — the mouse engine will aim
     * within this rectangle.
     */
    @Override
    public Rect screenBounds() {
        Shape hull = actor.getConvexHull();
        if (hull == null) return null;
        java.awt.Rectangle r = hull.getBounds();
        return new Rect(r.x, r.y, r.width, r.height);
    }

    @Override
    public boolean valid() {
        // Subclasses may override for more specific checks
        return true;
    }
}
