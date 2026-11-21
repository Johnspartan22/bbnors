package org.bbnors.api.model;

/**
 * Walkability information for pathfinding. {@link #canMove} defaults to the usual tile-grid rule:
 * the destination must be walkable and, for a diagonal step, both orthogonal neighbours too (no
 * corner cutting). Adapters with wall flags override it.
 */
public interface CollisionMap {

    boolean walkable(int x, int y, int plane);

    default boolean canMove(int x, int y, int plane, int dx, int dy) {
        if (!walkable(x + dx, y + dy, plane)) {
            return false;
        }
        if (dx != 0 && dy != 0) {
            return walkable(x + dx, y, plane) && walkable(x, y + dy, plane);
        }
        return true;
    }
}
