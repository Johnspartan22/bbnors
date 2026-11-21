package org.bbnors.api.game;

import org.bbnors.api.model.Player;
import org.bbnors.api.model.Tile;
import org.bbnors.api.path.GridPathFinder;
import org.bbnors.core.geom.Rect;
import org.bbnors.core.geom.Vec2;
import org.bbnors.engine.InteractionEngine;

import java.time.Duration;
import java.util.List;

/**
 * Minimap walking along A* paths.
 *
 * <p>{@link #step(Tile)} plans a path, picks the furthest path tile that is still comfortably
 * inside the minimap, and clicks it. {@link #walkTo(Tile, Duration)} repeats steps, re-clicking
 * when the player is close to the last clicked tile or has stopped, until arrival or timeout.
 * Paths are recomputed every step, so doors, moving obstacles and misclicks self-correct.
 */
public final class Walking {

    private final GameState state;
    private final InteractionEngine engine;
    private final Interactions interactions;
    private final GridPathFinder finder = new GridPathFinder();
    private volatile Tile lastClicked;

    public Walking(GameState state, InteractionEngine engine, Interactions interactions) {
        this.state = state;
        this.engine = engine;
        this.interactions = interactions;
    }

    /**
     * Path from the player to {@code dest} (excluding the player tile); empty if unreachable.
     * If {@code dest} itself is not walkable (an object such as a tree or booth occupies it),
     * the path ends on the nearest orthogonally adjacent walkable tile instead.
     */
    public synchronized List<Tile> path(Tile dest) {
        Tile me = state.playerTile();
        if (me == null) return List.of();
        var map = state.client().collision();
        return map.walkable(dest.x(), dest.y(), dest.plane())
                ? finder.find(map, me, dest)
                : finder.findAdjacent(map, me, dest);
    }

    /** One walking click towards {@code dest}. Returns false if no path / nothing clickable. */
    public boolean step(Tile dest) {
        List<Tile> path = path(dest);
        if (path.isEmpty()) {
            return false;
        }
        Rect mm = state.client().layout().minimap();
        Vec2 c = mm.center();
        double safeRadius = Math.min(mm.width(), mm.height()) / 2 - 10;
        Tile chosen = null;
        Vec2 chosenPt = null;
        for (int i = path.size() - 1; i >= 0; i--) {
            Vec2 p = state.client().tileToMinimap(path.get(i));
            if (p != null && p.distance(c) <= safeRadius) {
                chosen = path.get(i);
                chosenPt = p;
                break;
            }
        }
        if (chosen == null) {
            return false;
        }
        Vec2 pt = chosenPt;
        lastClicked = chosen;
        interactions.leftClick(() -> Rect.centered(pt, 4, 4));
        return true;
    }

    /** Walks until within {@code tolerance} steps of {@code dest} or the timeout passes. */
    public boolean walkTo(Tile dest, int tolerance, Duration timeout) {
        long deadline = engine.clock().nanos() + timeout.toNanos();
        while (engine.clock().nanos() < deadline) {
            Tile me = state.playerTile();
            if (me != null && me.steps(dest) <= tolerance) {
                return true;
            }
            if (!step(dest)) {
                return false;
            }
            Tile target = lastClicked;
            // Wait until we are near the clicked tile (time to click again) or we stopped moving.
            engine.waitUntil(() -> {
                Tile t = state.playerTile();
                Player p = state.localPlayer();
                return t != null && (t.steps(target) <= 3 || t.steps(dest) <= tolerance || (p != null && !p.moving()));
            }).timeout(Duration.ofSeconds(20)).poll(Duration.ofMillis(100)).stableFor(1).await();
        }
        return false;
    }

    public boolean walkTo(Tile dest) {
        return walkTo(dest, 1, Duration.ofMinutes(2));
    }
}
