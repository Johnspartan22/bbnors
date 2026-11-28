package org.bbnors.runelite;

import net.runelite.api.Client;

import org.bbnors.api.model.Player;

import java.util.List;

/**
 * Adapts a RuneLite {@link net.runelite.api.Player} to bbnors's {@link Player}.
 *
 * <p><b>Learning notes:</b> Players are simpler than NPCs — they don't have composition data.
 * Their name comes directly from the player object. Actions on other players are context-
 * dependent (Trade, Follow, Report in most cases).
 */
final class RuneLitePlayer extends RuneLiteActor implements Player {

    private final net.runelite.api.Player player;

    RuneLitePlayer(net.runelite.api.Player player, Client client) {
        super(player, client);
        this.player = player;
    }

    @Override
    public int id() {
        // Players don't have a game-cache ID like NPCs; use the player index in the list
        return player.getPlayerId();
    }

    @Override
    public String name() {
        String name = player.getName();
        return name != null ? name : "Unknown";
    }

    /**
     * Learning: Player interactions are limited compared to NPCs. For the local player,
     * there are no right-click actions. For other players: Trade, Follow, Report.
     */
    @Override
    public List<String> actions() {
        boolean isLocal = client.getLocalPlayer() == player;
        return isLocal ? List.of() : List.of("Trade with", "Follow", "Report");
    }
}
