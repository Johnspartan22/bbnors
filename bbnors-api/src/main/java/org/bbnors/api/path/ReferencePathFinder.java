package org.bbnors.api.path;

import org.bbnors.api.model.CollisionMap;
import org.bbnors.api.model.Tile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Straightforward textbook A* (HashMap state, PriorityQueue with lazy deletion, Tile objects
 * everywhere). Same cost model and window as {@link GridPathFinder}. Kept as the correctness
 * oracle for tests and as the "before" baseline in benchmarks; scripts should use
 * {@link GridPathFinder}.
 */
public final class ReferencePathFinder {

    private static final int[] DX = {1, -1, 0, 0, 1, 1, -1, -1};
    private static final int[] DY = {0, 0, 1, -1, 1, -1, 1, -1};
    private static final int[] COST = {10, 10, 10, 10, 14, 14, 14, 14};

    private final int radius;

    public ReferencePathFinder(int radius) {
        this.radius = radius;
    }

    private record Node(Tile tile, int f, int g) { }

    public List<Tile> find(CollisionMap map, Tile start, Tile goal) {
        Map<Tile, Integer> gScore = new HashMap<>();
        Map<Tile, Tile> parent = new HashMap<>();
        Set<Tile> closed = new HashSet<>();
        PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> a.f != b.f ? Integer.compare(a.f, b.f) : Integer.compare(b.g, a.g));
        gScore.put(start, 0);
        open.add(new Node(start, h(start, goal), 0));
        while (!open.isEmpty()) {
            Node cur = open.poll();
            if (!closed.add(cur.tile)) {
                continue; // stale duplicate
            }
            if (cur.tile.equals(goal)) {
                List<Tile> out = new ArrayList<>();
                for (Tile t = goal; !t.equals(start); t = parent.get(t)) {
                    out.add(t);
                }
                Collections.reverse(out);
                return out;
            }
            for (int d = 0; d < 8; d++) {
                Tile n = cur.tile.translate(DX[d], DY[d]);
                if (Math.abs(n.x() - start.x()) > radius || Math.abs(n.y() - start.y()) > radius || closed.contains(n)) {
                    continue;
                }
                if (!map.canMove(cur.tile.x(), cur.tile.y(), cur.tile.plane(), DX[d], DY[d])) {
                    continue;
                }
                int ng = cur.g + COST[d];
                Integer old = gScore.get(n);
                if (old == null || ng < old) {
                    gScore.put(n, ng);
                    parent.put(n, cur.tile);
                    open.add(new Node(n, ng + h(n, goal), ng));
                }
            }
        }
        return List.of();
    }

    private static int h(Tile a, Tile b) {
        int dx = Math.abs(a.x() - b.x());
        int dy = Math.abs(a.y() - b.y());
        return 10 * (dx + dy) - 6 * Math.min(dx, dy);
    }
}
