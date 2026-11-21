package org.bbnors.api.model;

import java.util.List;
import java.util.Optional;

/** The game's context menu. */
public interface MenuModel {

    boolean isOpen();

    /** Entries of the open menu, top to bottom (empty when closed). */
    List<MenuEntry> entries();

    /** The entry a left click would perform at the current cursor position (menu closed). */
    Optional<MenuEntry> hovered();
}
