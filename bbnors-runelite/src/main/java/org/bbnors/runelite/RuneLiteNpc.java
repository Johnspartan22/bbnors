package org.bbnors.runelite;

import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;

import org.bbnors.api.model.Npc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Adapts a RuneLite {@link NPC} to bbnors's {@link Npc}.
 *
 * <p><b>Learning notes:</b> NPC data in OSRS comes from two sources: the live NPC instance
 * (position, animation, health) and the NPC's "composition" (definition data: name, actions,
 * combat level). The composition is static data loaded from the game cache; the instance is
 * the live entity in the game world.
 */
final class RuneLiteNpc extends RuneLiteActor implements Npc {

    private final NPC npc;

    RuneLiteNpc(NPC npc, Client client) {
        super(npc, client);
        this.npc = npc;
    }

    @Override
    public int id() {
        return npc.getId();
    }

    /**
     * Learning: NPC names come from the composition (cache data). Some NPCs transform
     * (e.g., a tree becoming a stump) — RuneLite handles this via getTransformedComposition().
     */
    @Override
    public String name() {
        NPCComposition comp = npc.getTransformedComposition();
        if (comp != null) {
            String name = comp.getName();
            if (name != null) return name;
        }
        String name = npc.getName();
        return name != null ? name : "Unknown";
    }

    /**
     * Learning: NPC actions (Attack, Talk-to, Pickpocket, etc.) come from the composition.
     * The first non-null action is the left-click default. We filter out nulls because
     * OSRS stores actions in a fixed-size array with null gaps.
     */
    @Override
    public List<String> actions() {
        NPCComposition comp = npc.getTransformedComposition();
        if (comp == null) return Collections.emptyList();
        String[] raw = comp.getActions();
        if (raw == null) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        for (String a : raw) {
            if (a != null) out.add(a);
        }
        return out;
    }

    @Override
    public boolean valid() {
        // An NPC is valid if it's still in the loaded NPC list
        return npc.getIndex() >= 0;
    }
}
