package org.bbnors.runelite;

import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;

import org.bbnors.api.model.CollisionMap;

/**
 * Adapts RuneLite's collision data to bbnors's {@link CollisionMap}.
 *
 * <p><b>Learning notes:</b> OSRS collision works with flag bitmasks per tile. Each tile has
 * flags indicating what blocks movement: full blocks, walls on specific sides, floors, roofs.
 * RuneLite exposes these as a 2D array of flag integers per plane.
 *
 * <p>The collision data uses <b>scene coordinates</b> (0-103 for a standard scene), not
 * world coordinates. We convert between the two using the scene's base position.
 *
 * <p><b>Key flags:</b>
 * <ul>
 *   <li>{@code BLOCK_MOVEMENT_FULL} — tile is completely unwalkable</li>
 *   <li>{@code BLOCK_MOVEMENT_OBJECT} — blocked by a game object</li>
 *   <li>{@code BLOCK_MOVEMENT_FLOOR_DECORATION} — blocked by floor decoration</li>
 *   <li>Wall flags — directional blocks (N, S, E, W) for walls and doors</li>
 * </ul>
 */
final class RuneLiteCollisionMap implements CollisionMap {

    private final Client client;

    RuneLiteCollisionMap(Client client) {
        this.client = client;
    }

    /**
     * Learning: We check whether a tile is walkable by reading its collision flags.
     * A tile is walkable if it has none of the full-block flags set. We convert from
     * world coordinates to scene coordinates using the client's base X/Y.
     */
    @Override
    public boolean walkable(int x, int y, int plane) {
        CollisionData[] data = client.getCollisionMaps();
        if (data == null || plane < 0 || plane >= data.length || data[plane] == null) {
            return false;
        }
        // Convert world coords to scene coords
        int sceneX = x - client.getBaseX();
        int sceneY = y - client.getBaseY();
        int[][] flags = data[plane].getFlags();
        if (sceneX < 0 || sceneY < 0 || sceneX >= flags.length || sceneY >= flags[sceneX].length) {
            return false; // Outside the loaded scene
        }
        int flag = flags[sceneX][sceneY];
        // A tile is unwalkable if any of these blocking flags are set
        return (flag & (CollisionDataFlag.BLOCK_MOVEMENT_FULL
                | CollisionDataFlag.BLOCK_MOVEMENT_OBJECT
                | CollisionDataFlag.BLOCK_MOVEMENT_FLOOR_DECORATION)) == 0;
    }

    /**
     * Learning: Directional movement checks are more nuanced than simple walkability.
     * OSRS has wall flags that block movement in specific directions (a wall on the north
     * side of a tile blocks northward movement). The default implementation in CollisionMap
     * handles the basic diagonal-corner-cutting rule, but a full implementation would also
     * check wall flags. This is an area for future improvement.
     */
    // canMove() uses the default implementation from CollisionMap for now.
    // A complete implementation would check directional wall flags (BLOCK_MOVEMENT_NORTH, etc.)
    // to properly handle walls and doors.
}
