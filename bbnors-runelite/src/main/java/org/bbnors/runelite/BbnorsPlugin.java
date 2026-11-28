package org.bbnors.runelite;

import net.runelite.api.Client;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import org.bbnors.api.model.GameClient;
import org.bbnors.core.time.Clock;
import org.bbnors.core.time.SystemClock;
import org.bbnors.engine.input.InputBackend;
import org.bbnors.engine.input.RobotBackend;

import javax.inject.Inject;

/**
 * RuneLite plugin entry point for bbnors.
 *
 * <p><b>Learning notes:</b> This is where everything comes together. RuneLite loads this class
 * as a plugin and injects the game {@link Client} via Guice. We create the bbnors
 * {@link GameClient} adapter and can then wire up scripts, the interaction engine, and input
 * delivery.
 *
 * <h3>How RuneLite plugins work:</h3>
 * <ol>
 *   <li>RuneLite scans for classes annotated with {@code @PluginDescriptor}</li>
 *   <li>It creates an instance and injects dependencies (Client, ItemManager, etc.)</li>
 *   <li>{@link #startUp()} is called when the plugin is enabled</li>
 *   <li>{@link #shutDown()} is called when it's disabled</li>
 *   <li>Methods annotated with {@code @Subscribe} receive game events</li>
 * </ol>
 *
 * <h3>Architecture — how bbnors layers connect here:</h3>
 * <pre>
 *   RuneLite Client (game state)
 *       ↓ read via
 *   RuneLiteGameClient (this adapter, implements GameClient)
 *       ↓ used by
 *   bbnors-api: GameState, queries, managers, scripts
 *       ↓ commands
 *   bbnors-engine: InteractionManager, MouseController
 *       ↓ delivers input via
 *   RobotBackend (java.awt.Robot sends OS-level mouse/keyboard events)
 *       ↓ received by
 *   RuneLite Client window
 * </pre>
 *
 * <p><b>Input delivery:</b> The {@link RobotBackend} sends real OS mouse/keyboard events to
 * the RuneLite window. This is different from the simulator, where input went directly to the
 * SimulatedGame. With a real client, the input has to travel through the OS event system so
 * the client processes it normally.
 *
 * <p><b>Status:</b> This is a scaffold. To use it:
 * <ol>
 *   <li>Build the module and place the JAR in RuneLite's plugin directory</li>
 *   <li>Enable the plugin in RuneLite</li>
 *   <li>Write a script using the bbnors scripting API</li>
 *   <li>Register and run the script through ScriptRunner</li>
 * </ol>
 */
@PluginDescriptor(
        name = "bbnors",
        description = "bbnors automation framework adapter (learning experiment)",
        tags = {"automation", "learning"}
)
public class BbnorsPlugin extends Plugin {

    @Inject
    private Client client;

    @Inject
    private ItemManager itemManager;

    private RuneLiteGameClient gameClient;
    private int lastTick;

    @Override
    protected void startUp() {
        gameClient = new RuneLiteGameClient(client, itemManager);
        lastTick = -1;

        // At this point the adapter is live. To run a script you would:
        //
        //   Clock clock = new SystemClock();
        //   InputBackend input = new RobotBackend();  // sends real OS events
        //   // ... wire up InteractionManager, ScriptContext, ScriptRunner ...
        //
        // See bbnors-examples for the scripting pattern. The key difference from the
        // simulator is that input goes through RobotBackend (real OS events) instead of
        // SimulatedGame (direct state mutation).

        System.out.println("[bbnors] Plugin started — GameClient adapter is live.");
    }

    @Override
    protected void shutDown() {
        System.out.println("[bbnors] Plugin stopped.");
        gameClient = null;
    }

    /**
     * Learning: GameTick fires every 600ms game tick. This is where you'd advance any
     * bbnors-side bookkeeping that's tick-synchronized. The adapter itself doesn't need
     * this — it reads state on demand — but scripts that use tick-based waits benefit
     * from knowing when ticks happen.
     */
    @Subscribe
    public void onGameTick(GameTick event) {
        int currentTick = client.getTickCount();
        if (currentTick != lastTick) {
            lastTick = currentTick;
            // Future: notify tick-based waits, update statistics, etc.
        }
    }

    /**
     * Returns the bbnors GameClient adapter for use by scripts and the interaction engine.
     * This is the bridge between RuneLite's world and bbnors's scripting API.
     */
    public GameClient gameClient() {
        return gameClient;
    }
}
