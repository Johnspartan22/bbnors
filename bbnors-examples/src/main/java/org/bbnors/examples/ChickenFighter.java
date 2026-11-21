package org.bbnors.examples;

import org.bbnors.api.model.GroundItem;
import org.bbnors.api.model.Npc;
import org.bbnors.api.script.ScriptContext;
import org.bbnors.api.script.TreeScript;
import org.bbnors.engine.timing.TimingProfiles;

import java.time.Duration;
import java.util.Optional;

/**
 * Example {@link TreeScript}: fight wandering chickens (a moving-target interaction) and pick up
 * the feathers they drop.
 * <pre>
 *   root
 *    ├── loot      feathers on the ground nearby  → Take
 *    ├── fight     idle and a chicken is nearby   → Attack nearest chicken
 *    └── wait      otherwise                      → short reaction pause
 * </pre>
 */
public final class ChickenFighter extends TreeScript {

    private final Node root = branch("root", c -> true,
            leaf("loot", c -> feathers(c).isPresent() && !c.inventory().isFull(), ChickenFighter::loot),
            leaf("fight", c -> c.player().idle() && c.npcs().name("Chicken").within(8).exists(), ChickenFighter::fight),
            leaf("wait", c -> {
                c.sleepProfile(TimingProfiles.ACTION_REACTION);
                return 300;
            }));

    @Override
    protected Node root() {
        return root;
    }

    private static Optional<GroundItem> feathers(ScriptContext c) {
        return c.groundItems().name("Feather").within(6).nearest();
    }

    private static int loot(ScriptContext c) {
        GroundItem f = feathers(c).orElseThrow();
        if (c.interact(f, "Take")) {
            c.waitUntil(() -> !f.valid()).timeout(Duration.ofSeconds(6)).await();
        }
        return 200;
    }

    private static int fight(ScriptContext c) {
        Npc chicken = c.npcs().name("Chicken").action("Attack").within(8).nearest().orElse(null);
        if (chicken != null && c.interact(chicken, "Attack")) {
            c.waitUntil(() -> !chicken.valid()).timeout(Duration.ofSeconds(15)).await();
        }
        return 300;
    }
}
