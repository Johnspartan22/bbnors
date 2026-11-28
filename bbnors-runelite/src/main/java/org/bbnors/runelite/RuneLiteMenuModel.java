package org.bbnors.runelite;

import net.runelite.api.Client;

import org.bbnors.api.model.MenuEntry;
import org.bbnors.api.model.MenuModel;
import org.bbnors.core.geom.Rect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Adapts RuneLite's menu system to bbnors's {@link MenuModel}.
 *
 * <p><b>Learning notes:</b> OSRS has two menu states: closed (left-click performs the top
 * action, shown as hover text) and open (right-click menu showing all options). RuneLite
 * exposes the menu entries array in both states. When the menu is closed, the first entry
 * is what a left-click would do — that's the "hovered" entry in bbnors terms.
 *
 * <p><b>Menu entry order:</b> RuneLite returns entries in reverse priority order (last entry
 * is the top/default action). We reverse them to match bbnors's convention (first = default).
 */
final class RuneLiteMenuModel implements MenuModel {

    private final Client client;

    RuneLiteMenuModel(Client client) {
        this.client = client;
    }

    @Override
    public boolean isOpen() {
        return client.isMenuOpen();
    }

    @Override
    public List<MenuEntry> entries() {
        if (!client.isMenuOpen()) return Collections.emptyList();
        net.runelite.api.MenuEntry[] raw = client.getMenuEntries();
        if (raw == null || raw.length == 0) return Collections.emptyList();
        List<MenuEntry> out = new ArrayList<>(raw.length);
        // RuneLite stores entries bottom-to-top; reverse for bbnors (top-to-bottom)
        for (int i = raw.length - 1; i >= 0; i--) {
            net.runelite.api.MenuEntry me = raw[i];
            String action = me.getOption() != null ? me.getOption() : "";
            String target = me.getTarget() != null ? stripTags(me.getTarget()) : "";
            // Menu entry bounds aren't directly available from RuneLite;
            // we approximate based on menu position and row index
            out.add(new MenuEntry(action, target, null));
        }
        return out;
    }

    @Override
    public Optional<MenuEntry> hovered() {
        if (client.isMenuOpen()) return Optional.empty();
        net.runelite.api.MenuEntry[] raw = client.getMenuEntries();
        if (raw == null || raw.length == 0) return Optional.empty();
        // The last entry in RuneLite's array is the top-priority (left-click) action
        net.runelite.api.MenuEntry top = raw[raw.length - 1];
        String action = top.getOption() != null ? top.getOption() : "";
        String target = top.getTarget() != null ? stripTags(top.getTarget()) : "";
        return Optional.of(new MenuEntry(action, target, null));
    }

    /**
     * Learning: RuneLite menu targets contain color tags like {@code <col=ff9040>Tree</col>}.
     * We strip these to get the plain name that bbnors scripts match against.
     */
    private static String stripTags(String s) {
        return s.replaceAll("<[^>]+>", "").trim();
    }
}
