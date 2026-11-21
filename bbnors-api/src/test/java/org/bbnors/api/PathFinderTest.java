package org.bbnors.api;

import org.bbnors.api.model.CollisionMap;
import org.bbnors.api.model.Tile;
import org.bbnors.api.path.GridPathFinder;
import org.bbnors.api.path.ReferencePathFinder;
import org.bbnors.core.random.Rng;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PathFinderTest {

    /** Random obstacle field; ~25% blocked, start/goal kept free. */
    static CollisionMap randomMap(Rng r, int size, double density) {
        Set<Long> blocked = new HashSet<>();
        for (int x = -size; x <= size; x++) {
            for (int y = -size; y <= size; y++) {
                if (r.chance(density)) blocked.add(((long) x << 32) | (y & 0xffffffffL));
            }
        }
        return (x, y, p) -> Math.abs(x) <= size && Math.abs(y) <= size && !blocked.contains(((long) x << 32) | (y & 0xffffffffL));
    }

    @Test
    public void optimizedFinderMatchesReferenceCostOnRandomMaps() {
        Rng r = new Rng(77);
        GridPathFinder fast = new GridPathFinder(40);
        ReferencePathFinder ref = new ReferencePathFinder(40);
        int found = 0;
        for (int trial = 0; trial < 200; trial++) {
            CollisionMap m = randomMap(r, 40, 0.25);
            Tile s = new Tile(r.nextInt(30) - 15, r.nextInt(30) - 15);
            Tile g = new Tile(r.nextInt(60) - 30, r.nextInt(60) - 30);
            if (!m.walkable(s.x(), s.y(), 0) || !m.walkable(g.x(), g.y(), 0) || s.equals(g)) continue;
            List<Tile> a = fast.find(m, s, g);
            List<Tile> b = ref.find(m, s, g);
            assertEquals("reachability trial " + trial, b.isEmpty(), a.isEmpty());
            if (!a.isEmpty()) {
                found++;
                assertEquals("optimal cost trial " + trial, GridPathFinder.cost(s, b), GridPathFinder.cost(s, a));
                assertEquals(g, a.get(a.size() - 1));
                Tile prev = s;
                for (Tile t : a) {
                    assertTrue("legal step", m.canMove(prev.x(), prev.y(), 0, t.x() - prev.x(), t.y() - prev.y()));
                    prev = t;
                }
            }
        }
        assertTrue("enough solvable trials", found > 100);
    }

    @Test
    public void noCornerCuttingAndAdjacentGoal() {
        // wall at x=1 except a gap at y=5
        CollisionMap m = (x, y, p) -> !(x == 1 && y != 5) && Math.abs(x) < 20 && Math.abs(y) < 20;
        GridPathFinder f = new GridPathFinder(20);
        List<Tile> path = f.find(m, new Tile(0, 0), new Tile(2, 0));
        assertTrue(path.contains(new Tile(1, 5)));
        List<Tile> adj = f.findAdjacent(m, new Tile(0, 0), new Tile(1, 0)); // target itself blocked
        Tile end = adj.isEmpty() ? new Tile(0, 0) : adj.get(adj.size() - 1);
        assertEquals(1, Math.abs(end.x() - 1) + Math.abs(end.y()));
    }

    @Test
    public void unreachableReturnsEmptyAndReuseIsSafe() {
        CollisionMap boxed = (x, y, p) -> Math.abs(x) <= 1 && Math.abs(y) <= 1;
        GridPathFinder f = new GridPathFinder(10);
        assertTrue(f.find(boxed, new Tile(0, 0), new Tile(5, 5)).isEmpty());
        // generation stamps: a second search on a different map must not see stale state
        CollisionMap open = (x, y, p) -> true;
        assertEquals(5, f.find(open, new Tile(0, 0), new Tile(5, 5)).size());
    }
}
