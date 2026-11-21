package org.bbnors.examples;

import org.bbnors.api.model.GameObject;
import org.bbnors.api.script.ScriptContext;
import org.bbnors.api.script.TaskScript;
import org.bbnors.engine.timing.TimingProfiles;

import java.time.Duration;
import java.util.Optional;

/**
 * Example {@link TaskScript}: chop the nearest tree, bank the logs when the inventory is full.
 *
 * <p>Shows the intended style: small tasks with explicit {@code accept} conditions, high-level
 * managers ({@code bank()}, {@code walking()}), menu-aware {@code interact}, and conditional waits
 * instead of fixed sleeps. No delay literals: pauses come from named timing profiles.
 */
public final class Woodcutter extends TaskScript {

    private int trips;

    public Woodcutter() {
        add(new Bank(), new Chop());
    }

    @Override
    public void onStart(ScriptContext ctx) {
        ctx.log().info("Woodcutter starting at " + ctx.player().tile());
    }

    @Override
    public void onStop(ScriptContext ctx) {
        ctx.log().info("Woodcutter stopped after " + trips + " bank trips");
    }

    private final class Bank implements Task {
        public String name() { return "bank"; }

        public int priority() { return 10; }

        public boolean accept(ScriptContext c) {
            return c.inventory().isFull() || (c.bank().isOpen() && c.inventory().contains("Logs"));
        }

        public int execute(ScriptContext c) {
            if (!c.bank().isOpen()) {
                return c.bank().open() ? 200 : 1200;
            }
            if (c.bank().depositAll("Logs")) {
                trips++;
            }
            c.bank().close();
            c.sleepProfile(TimingProfiles.ACTION_REACTION);
            return 100;
        }
    }

    private static final class Chop implements Task {
        public String name() { return "chop"; }

        public boolean accept(ScriptContext c) {
            return !c.inventory().isFull() && c.player().animation() == -1 && !c.player().moving();
        }

        public int execute(ScriptContext c) {
            Optional<GameObject> tree = c.objects().name("Tree").action("Chop down").nearest();
            if (tree.isEmpty()) {
                return 600; // all depleted: wait for a respawn
            }
            if (!tree.get().onScreen() && c.player().tile().distance(tree.get().tile()) > 6) {
                c.walking().walkTo(tree.get().tile(), 4, Duration.ofSeconds(30));
            }
            if (c.interact(tree.get(), "Chop down")) {
                c.waitUntil(() -> c.player().animation() != -1)
                        .timeout(Duration.ofSeconds(4))
                        .resetWhile(() -> c.player().moving())
                        .await();
            }
            c.sleepProfile(TimingProfiles.ACTION_REACTION);
            return 100;
        }
    }
}
