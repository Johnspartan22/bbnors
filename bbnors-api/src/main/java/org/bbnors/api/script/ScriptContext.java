package org.bbnors.api.script;

import org.bbnors.api.game.Bank;
import org.bbnors.api.game.CameraActions;
import org.bbnors.api.game.Equipment;
import org.bbnors.api.game.GameState;
import org.bbnors.api.game.Interactions;
import org.bbnors.api.game.Inventory;
import org.bbnors.api.game.Walking;
import org.bbnors.api.game.Widgets;
import org.bbnors.api.model.Entity;
import org.bbnors.api.model.GameClient;
import org.bbnors.api.model.GameObject;
import org.bbnors.api.model.GroundItem;
import org.bbnors.api.model.Npc;
import org.bbnors.api.model.Player;
import org.bbnors.api.query.EntityQuery;
import org.bbnors.core.log.Log;
import org.bbnors.core.random.Rng;
import org.bbnors.engine.InteractionEngine;
import org.bbnors.engine.timing.Waits;

import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/**
 * Everything a script uses, in one object. Scripts receive it in every callback:
 * <pre>{@code
 *   ctx.objects().name("Tree").nearest().ifPresent(t -> ctx.interact(t, "Chop down"));
 *   ctx.waitUntil(() -> ctx.player().animation() != -1).timeout(Duration.ofSeconds(3)).await();
 * }</pre>
 * Subsystems with internal state (engine, managers) are shared; the {@link Rng} is private to
 * the script thread.
 */
public final class ScriptContext {

    private final GameState state;
    private final InteractionEngine engine;
    private final Rng rng;
    private final Log log;
    private final CameraActions camera;
    private final Interactions interactions;
    private final Walking walking;
    private final Inventory inventory;
    private final Bank bank;
    private final Equipment equipment;
    private final Widgets widgets;
    private volatile ScriptRunner runner;

    public ScriptContext(GameClient client, InteractionEngine engine, Rng rng, String scriptName) {
        this.state = new GameState(client);
        this.engine = engine;
        this.rng = rng;
        this.log = Log.get(scriptName);
        this.camera = new CameraActions(state, engine);
        this.interactions = new Interactions(state, engine, camera, rng.split());
        this.walking = new Walking(state, engine, interactions);
        this.inventory = new Inventory(state, interactions);
        this.bank = new Bank(state, engine, interactions, walking);
        this.equipment = new Equipment(state, engine, inventory);
        this.widgets = new Widgets(state, interactions);
    }

    void bind(ScriptRunner r) {
        runner = r;
    }

    public GameState game() { return state; }
    public GameClient client() { return state.client(); }
    public InteractionEngine engine() { return engine; }
    public Rng rng() { return rng; }
    public Log log() { return log; }
    public CameraActions camera() { return camera; }
    public Interactions interactions() { return interactions; }
    public Walking walking() { return walking; }
    public Inventory inventory() { return inventory; }
    public Bank bank() { return bank; }
    public Equipment equipment() { return equipment; }
    public Widgets widgets() { return widgets; }

    public Player player() { return state.localPlayer(); }
    public EntityQuery<Npc> npcs() { return state.npcs(); }
    public EntityQuery<GameObject> objects() { return state.objects(); }
    public EntityQuery<Player> players() { return state.players(); }
    public EntityQuery<GroundItem> groundItems() { return state.groundItems(); }

    /** Shortcut for {@link Interactions#interact}. */
    public boolean interact(Entity e, String action) {
        return interactions.interact(e, action);
    }

    /** Conditional wait on the engine clock (see {@link Waits}). */
    public Waits.Builder waitUntil(BooleanSupplier condition) {
        return engine.waitUntil(() -> {
            checkStop();
            return condition.getAsBoolean();
        });
    }

    /** Sleeps for {@code ms}, honouring pause and stop. */
    public void sleep(long ms) {
        checkStop();
        try {
            engine.clock().sleepNanos(ms * 1_000_000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CancellationException("script interrupted");
        }
        checkStop();
    }

    /** Sleeps for a duration drawn from a named timing profile. */
    public void sleepProfile(String timingProfile) {
        sleep(Math.round(engine.timing().get(timingProfile).sample(rng) * 1000));
    }

    /** Throws {@link CancellationException} if the runner has been asked to stop. */
    public void checkStop() {
        ScriptRunner r = runner;
        if (r != null && r.stopRequested()) {
            throw new CancellationException("script stop requested");
        }
    }
}
