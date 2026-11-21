package org.bbnors.api.path;

import org.bbnors.api.model.CollisionMap;
import org.bbnors.api.model.Tile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * A* over the 8-connected tile grid inside a square window around the start tile, using flat
 * primitive arrays instead of object graphs.
 *
 * <p><b>Costs</b>: 10 per orthogonal step, 14 per diagonal (integer octile metric), with the
 * matching admissible, consistent octile heuristic, so returned paths are optimal under that
 * metric and look natural (no zig-zags that a pure step-count metric permits).
 *
 * <p><b>Performance design</b> (measured against {@link ReferencePathFinder} in the benchmark
 * suite):
 * <ul>
 *   <li>Node state lives in {@code int[]} arrays indexed by window cell; nothing is allocated per
 *       expanded node.</li>
 *   <li>Arrays are reused across searches; a generation stamp marks cells as "touched in this
 *       search", so nothing is cleared between searches (O(1) reset instead of O(window²)).</li>
 *   <li>The open set is an indexed binary min-heap with true decrease-key, so it never holds
 *       stale duplicates.</li>
 * </ul>
 * Instances are <b>not</b> thread-safe (they own the scratch arrays); use one per thread.
 */
public final class GridPathFinder {

    private static final int[] DX = {1, -1, 0, 0, 1, 1, -1, -1};
    private static final int[] DY = {0, 0, 1, -1, 1, -1, 1, -1};
    private static final int[] COST = {10, 10, 10, 10, 14, 14, 14, 14};

    private final int radius;
    private final int width;
    private final int[] g;
    private final int[] f;
    private final int[] parent;
    private final int[] seen;     // generation in which the cell was first touched
    private final int[] closed;   // generation in which the cell was closed
    private final int[] heapPos;  // position in heap (valid if seen == gen and not closed)
    private final int[] heap;
    private int heapSize;
    private int gen;
    private int expanded;

    /** @param radius search window half-size in tiles (window is (2r+1)²) */
    public GridPathFinder(int radius) {
        if (radius < 1 || radius > 1024) throw new IllegalArgumentException("radius must be in [1, 1024]");
        this.radius = radius;
        this.width = 2 * radius + 1;
        int n = width * width;
        g = new int[n];
        f = new int[n];
        parent = new int[n];
        seen = new int[n];
        closed = new int[n];
        heapPos = new int[n];
        heap = new int[n];
    }

    public GridPathFinder() {
        this(104);
    }

    /** Shortest path from {@code start} to {@code goal}, excluding start; empty if unreachable. */
    public List<Tile> find(CollisionMap map, Tile start, Tile goal) {
        return search(map, start, goal, GOAL_EXACT, null);
    }

    /**
     * Shortest path to the nearest tile orthogonally adjacent to {@code target} (for interacting
     * with objects that occupy their tile), or to {@code target} itself if it is walkable.
     */
    public List<Tile> findAdjacent(CollisionMap map, Tile start, Tile target) {
        return search(map, start, target, GOAL_ADJACENT, null);
    }

    /**
     * General form: shortest path to any tile satisfying {@code isGoal}; {@code heuristicTarget}
     * guides the search (must be near the goal set for good performance).
     */
    public List<Tile> find(CollisionMap map, Tile start, Tile heuristicTarget, Predicate<Tile> isGoal) {
        return search(map, start, heuristicTarget, GOAL_PREDICATE, isGoal);
    }

    private static final int GOAL_EXACT = 0;
    private static final int GOAL_ADJACENT = 1;
    private static final int GOAL_PREDICATE = 2;

    private List<Tile> search(CollisionMap map, Tile start, Tile heuristicTarget, int goalMode, Predicate<Tile> isGoal) {
        if (++gen == Integer.MAX_VALUE) {
            java.util.Arrays.fill(seen, 0);
            java.util.Arrays.fill(closed, 0);
            gen = 1;
        }
        expanded = 0;
        heapSize = 0;
        int ox = start.x() - radius;
        int oy = start.y() - radius;
        int plane = start.plane();
        int tx = heuristicTarget.x();
        int ty = heuristicTarget.y();
        int s = idx(radius, radius);
        seen[s] = gen;
        g[s] = 0;
        f[s] = h(start.x(), start.y(), tx, ty);
        parent[s] = -1;
        push(s);
        while (heapSize > 0) {
            int cur = pop();
            closed[cur] = gen;
            expanded++;
            int cx = cur % width;
            int cy = cur / width;
            int wx = ox + cx;
            int wy = oy + cy;
            boolean goal = switch (goalMode) {
                case GOAL_EXACT -> wx == tx && wy == ty;
                case GOAL_ADJACENT -> Math.abs(wx - tx) + Math.abs(wy - ty) <= 1;
                default -> isGoal.test(new Tile(wx, wy, plane));
            };
            if (goal) {
                return reconstruct(cur, ox, oy, plane);
            }
            for (int d = 0; d < 8; d++) {
                int nx = cx + DX[d];
                int ny = cy + DY[d];
                if (nx < 0 || ny < 0 || nx >= width || ny >= width) {
                    continue;
                }
                int n = idx(nx, ny);
                if (closed[n] == gen) {
                    continue;
                }
                if (!map.canMove(wx, wy, plane, DX[d], DY[d])) {
                    continue;
                }
                int ng = g[cur] + COST[d];
                if (seen[n] != gen) {
                    seen[n] = gen;
                    g[n] = ng;
                    f[n] = ng + h(ox + nx, oy + ny, tx, ty);
                    parent[n] = cur;
                    push(n);
                } else if (ng < g[n]) {
                    g[n] = ng;
                    f[n] = ng + h(ox + nx, oy + ny, tx, ty);
                    parent[n] = cur;
                    siftUp(heapPos[n]);
                }
            }
        }
        return List.of();
    }

    /** Nodes expanded by the last search (for diagnostics and benchmarks). */
    public int lastExpanded() {
        return expanded;
    }

    private int idx(int x, int y) {
        return y * width + x;
    }

    private static int h(int x, int y, int tx, int ty) {
        int dx = Math.abs(x - tx);
        int dy = Math.abs(y - ty);
        return 10 * (dx + dy) - 6 * Math.min(dx, dy);
    }

    private List<Tile> reconstruct(int end, int ox, int oy, int plane) {
        List<Tile> out = new ArrayList<>();
        for (int c = end; parent[c] != -1; c = parent[c]) {
            out.add(new Tile(ox + c % width, oy + c / width, plane));
        }
        Collections.reverse(out);
        return out;
    }

    // ---- indexed binary heap keyed on f (ties: larger g first, i.e. deeper nodes) ----

    private boolean less(int a, int b) {
        return f[a] < f[b] || (f[a] == f[b] && g[a] > g[b]);
    }

    private void push(int n) {
        heap[heapSize] = n;
        heapPos[n] = heapSize;
        siftUp(heapSize++);
    }

    private int pop() {
        int top = heap[0];
        int last = heap[--heapSize];
        if (heapSize > 0) {
            heap[0] = last;
            heapPos[last] = 0;
            siftDown(0);
        }
        return top;
    }

    private void siftUp(int i) {
        int n = heap[i];
        while (i > 0) {
            int p = (i - 1) >>> 1;
            int pn = heap[p];
            if (!less(n, pn)) break;
            heap[i] = pn;
            heapPos[pn] = i;
            i = p;
        }
        heap[i] = n;
        heapPos[n] = i;
    }

    private void siftDown(int i) {
        int n = heap[i];
        int half = heapSize >>> 1;
        while (i < half) {
            int c = 2 * i + 1;
            int r = c + 1;
            if (r < heapSize && less(heap[r], heap[c])) c = r;
            if (!less(heap[c], n)) break;
            heap[i] = heap[c];
            heapPos[heap[i]] = i;
            i = c;
        }
        heap[i] = n;
        heapPos[n] = i;
    }

    /** Octile cost of a path (10 per orthogonal step, 14 per diagonal), counting from {@code start}. */
    public static int cost(Tile start, List<Tile> path) {
        int c = 0;
        Tile prev = start;
        for (Tile t : path) {
            boolean diag = t.x() != prev.x() && t.y() != prev.y();
            c += diag ? 14 : 10;
            prev = t;
        }
        return c;
    }
}
