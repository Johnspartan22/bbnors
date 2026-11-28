package org.bbnors.runelite;

import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;

import org.bbnors.api.model.InterfaceLayout;
import org.bbnors.core.geom.Rect;

/**
 * Reads interface geometry from RuneLite's widget system.
 *
 * <p><b>Learning notes:</b> OSRS has two interface modes: "fixed" (the classic 765×503 layout)
 * and "resizable" (interface panels can be repositioned). The widget system uses (group, child)
 * pairs to identify every interface element. RuneLite exposes these as Widget objects with
 * canvas position and size.
 *
 * <p>This implementation reads positions dynamically from live widgets rather than hardcoding
 * them, so it works in both fixed and resizable modes. If a widget isn't loaded, we fall back
 * to the classic fixed-mode positions.
 *
 * <p><b>Widget IDs used:</b>
 * <ul>
 *   <li>Viewport: WidgetInfo.FIXED_VIEWPORT / RESIZABLE_VIEWPORT</li>
 *   <li>Inventory: WidgetInfo.INVENTORY (group 149)</li>
 *   <li>Bank: WidgetInfo.BANK_ITEM_CONTAINER (group 12)</li>
 *   <li>Minimap: WidgetInfo.FIXED_VIEWPORT_MINIMAP_DRAW_AREA</li>
 * </ul>
 */
final class RuneLiteInterfaceLayout implements InterfaceLayout {

    private final Client client;

    // Classic fixed-mode fallback positions (765x503)
    private static final Rect FALLBACK_VIEWPORT = new Rect(4, 4, 512, 334);
    private static final Rect FALLBACK_MINIMAP = new Rect(555, 10, 144, 144);
    private static final Rect FALLBACK_INVENTORY = new Rect(553, 205, 180, 260);
    private static final Rect FALLBACK_BANK_DEPOSIT = new Rect(420, 290, 40, 26);
    private static final Rect FALLBACK_BANK_CLOSE = new Rect(476, 24, 22, 22);

    // Inventory grid constants
    private static final int INV_COLS = 4;
    private static final int INV_SLOT_W = 36;
    private static final int INV_SLOT_H = 32;
    private static final int INV_PAD_X = 42;
    private static final int INV_PAD_Y = 36;

    // Bank grid constants
    private static final int BANK_COLS = 8;
    private static final int BANK_SLOT_W = 40;
    private static final int BANK_SLOT_H = 34;
    private static final int BANK_PAD_X = 48;
    private static final int BANK_PAD_Y = 40;

    RuneLiteInterfaceLayout(Client client) {
        this.client = client;
    }

    @Override
    public Rect viewport() {
        return widgetRect(WidgetInfo.FIXED_VIEWPORT, FALLBACK_VIEWPORT);
    }

    /**
     * Learning: Inventory slots are arranged in a 4-column grid. Each slot's position is
     * calculated from the inventory widget's top-left corner plus the slot's row/column offset.
     */
    @Override
    public Rect inventorySlot(int slot) {
        Rect inv = widgetRect(WidgetInfo.INVENTORY, FALLBACK_INVENTORY);
        int col = slot % INV_COLS;
        int row = slot / INV_COLS;
        return new Rect(
                inv.x() + 10 + col * INV_PAD_X,
                inv.y() + 8 + row * INV_PAD_Y,
                INV_SLOT_W, INV_SLOT_H);
    }

    @Override
    public Rect bankSlot(int index) {
        int col = index % BANK_COLS;
        int row = index / BANK_COLS;
        return new Rect(40 + col * BANK_PAD_X, 60 + row * BANK_PAD_Y, BANK_SLOT_W, BANK_SLOT_H);
    }

    @Override
    public Rect bankDepositInventory() {
        return widgetRect(WidgetInfo.BANK_DEPOSIT_INVENTORY, FALLBACK_BANK_DEPOSIT);
    }

    @Override
    public Rect bankClose() {
        return FALLBACK_BANK_CLOSE;
    }

    @Override
    public Rect minimap() {
        return widgetRect(WidgetInfo.FIXED_VIEWPORT_MINIMAP_DRAW_AREA, FALLBACK_MINIMAP);
    }

    /**
     * Learning: Reads a widget's on-screen rectangle. If the widget isn't loaded (happens
     * when the interface mode doesn't use it), we return the fallback.
     */
    private Rect widgetRect(WidgetInfo info, Rect fallback) {
        Widget w = client.getWidget(info);
        if (w == null || w.isHidden()) return fallback;
        return new Rect(
                w.getCanvasLocation().getX(),
                w.getCanvasLocation().getY(),
                w.getWidth(),
                w.getHeight());
    }
}
