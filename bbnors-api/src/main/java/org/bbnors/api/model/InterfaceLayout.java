package org.bbnors.api.model;

import org.bbnors.core.geom.Rect;

/** Screen geometry of the fixed interface regions the managers click on. */
public interface InterfaceLayout {

    Rect viewport();

    Rect inventorySlot(int slot);

    Rect bankSlot(int index);

    Rect bankDepositInventory();

    Rect bankClose();

    Rect minimap();
}
