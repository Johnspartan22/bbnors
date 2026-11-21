package org.bbnors.api.game;

import org.bbnors.api.model.Entity;
import org.bbnors.api.model.Tile;
import org.bbnors.engine.InteractionEngine;
import org.bbnors.engine.camera.CameraController;

/** Game-aware camera helpers on top of the engine's {@link CameraController}. */
public final class CameraActions {

    private final GameState state;
    private final InteractionEngine engine;

    public CameraActions(GameState state, InteractionEngine engine) {
        this.state = state;
        this.engine = engine;
    }

    public int yaw() {
        return state.client().camera().yaw();
    }

    public int pitch() {
        return state.client().camera().pitch();
    }

    /** Rotates so {@code tile} is straight ahead of the local player. */
    public CameraController.Result turnTo(Tile tile) {
        Tile me = state.playerTile();
        if (me == null || engine.camera() == null) {
            return null;
        }
        int yaw = state.client().yawFacing(me, tile);
        return engine.camera().rotateToYaw(yaw, engine.defaultCameraMode()).await();
    }

    public CameraController.Result turnTo(Entity e) {
        return turnTo(e.tile());
    }

    public CameraController.Result rotateTo(int yaw, int pitch) {
        return engine.camera().rotateTo(yaw, pitch, engine.defaultCameraMode()).await();
    }
}
