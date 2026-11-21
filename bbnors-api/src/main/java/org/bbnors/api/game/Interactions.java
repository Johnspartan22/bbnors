package org.bbnors.api.game;

import org.bbnors.api.model.Entity;
import org.bbnors.api.model.MenuEntry;
import org.bbnors.api.model.MenuModel;
import org.bbnors.core.geom.Rect;
import org.bbnors.core.log.Log;
import org.bbnors.core.random.Rng;
import org.bbnors.engine.InteractionEngine;
import org.bbnors.engine.input.MouseButton;
import org.bbnors.engine.timing.TimingProfiles;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Menu-aware clicking: the single place that knows how to perform "action X on target Y".
 *
 * <p>Algorithm for {@link #click(Supplier, String, String)}:
 * <ol>
 *   <li>Move into the (live) target area with the configured movement profile.</li>
 *   <li>If the left-click (hover) entry already is the wanted action, left-click.</li>
 *   <li>Otherwise right-click, wait for the menu, move to the wanted row and left-click it. If the
 *       row is missing, choose "Cancel" so the menu is not left open.</li>
 * </ol>
 * Every step is a separate engine operation, so other channels (keyboard, camera) stay usable and
 * a script stop cancels cleanly between steps.
 */
public final class Interactions {

    private static final Log LOG = Log.get(Interactions.class);

    private final GameState state;
    private final InteractionEngine engine;
    private final CameraActions camera;
    private final Rng rng;
    private volatile String profile;

    /** @param rng random stream for script-side delays (menu reading); not shared with other threads */
    public Interactions(GameState state, InteractionEngine engine, CameraActions camera, Rng rng) {
        this.state = state;
        this.engine = engine;
        this.camera = camera;
        this.rng = rng;
        this.profile = engine.mouse().defaultProfile();
    }

    /** Movement profile used for interactions (e.g. "precise" for small targets). */
    public void setProfile(String name) {
        engine.profiles().get(name);
        profile = name;
    }

    public String profile() {
        return profile;
    }

    /**
     * Performs {@code action} on an entity. If it is off screen, the camera is turned towards it
     * first. Returns false if the entity is invalid, does not offer the action, cannot be brought
     * on screen, or the menu did not contain the action.
     */
    public boolean interact(Entity e, String action) {
        if (e == null || !e.valid() || !e.hasAction(action)) {
            return false;
        }
        if (!e.onScreen()) {
            camera.turnTo(e.tile());
            if (!e.onScreen()) {
                LOG.debug(() -> e.name() + " still off screen after camera turn");
                return false;
            }
        }
        AtomicReference<Rect> last = new AtomicReference<>(e.screenBounds());
        Supplier<Rect> live = () -> {
            Rect r = e.valid() ? e.screenBounds() : null;
            if (r != null) {
                last.set(r);
            }
            return last.get();
        };
        return click(live, action, e.name());
    }

    /** Menu-aware click on an arbitrary screen area (inventory slot, widget, ...). */
    public boolean click(Supplier<Rect> area, String action, String target) {
        engine.mouse().moveTo(area, profile).await();
        MenuModel menu = state.client().menu();
        Optional<MenuEntry> hover = menu.hovered();
        if (hover.isPresent() && hover.get().matches(action, target)) {
            engine.mouse().click(MouseButton.LEFT).await();
            return true;
        }
        engine.mouse().click(MouseButton.RIGHT).await();
        boolean open = engine.waitUntil(menu::isOpen).timeout(Duration.ofMillis(1200)).poll(Duration.ofMillis(20)).test();
        if (!open) {
            return false;
        }
        sleepProfile(TimingProfiles.MOUSE_MENU_READ); // a menu has to be read before choosing
        Optional<MenuEntry> entry = menu.entries().stream().filter(m -> m.matches(action, target)).findFirst();
        if (entry.isEmpty()) {
            entry = menu.entries().stream().filter(m -> m.action().equalsIgnoreCase("Cancel")).findFirst();
            entry.ifPresent(c -> engine.mouse().moveAndClick(() -> c.bounds().inset(2), MouseButton.LEFT, profile).await());
            return false;
        }
        Rect row = entry.get().bounds();
        // Menu rows are short and wide: aim inside the text area, away from the row edges.
        Rect aim = new Rect(row.x() + 4, row.y() + 3, Math.max(1, row.width() * 0.6), Math.max(1, row.height() - 6));
        engine.mouse().moveAndClick(() -> aim, MouseButton.LEFT, profile).await();
        return true;
    }

    /** Plain left click in an area (no menu check), e.g. a button. */
    public void leftClick(Supplier<Rect> area) {
        engine.mouse().moveAndClick(area, MouseButton.LEFT, profile).await();
    }

    private void sleepProfile(String name) {
        try {
            engine.clock().sleepNanos(engine.timing().get(name).sampleNanos(rng));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new java.util.concurrent.CancellationException("interrupted");
        }
    }
}
