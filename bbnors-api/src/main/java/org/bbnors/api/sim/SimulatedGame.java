package org.bbnors.api.sim;

import org.bbnors.api.model.CollisionMap;
import org.bbnors.api.model.GameClient;
import org.bbnors.api.model.GameObject;
import org.bbnors.api.model.GroundItem;
import org.bbnors.api.model.InterfaceLayout;
import org.bbnors.api.model.Item;
import org.bbnors.api.model.MenuEntry;
import org.bbnors.api.model.MenuModel;
import org.bbnors.api.model.Npc;
import org.bbnors.api.model.Player;
import org.bbnors.api.model.Tile;
import org.bbnors.api.model.Widget;
import org.bbnors.api.path.GridPathFinder;
import org.bbnors.core.geom.Rect;
import org.bbnors.core.geom.Vec2;
import org.bbnors.core.random.Rng;
import org.bbnors.core.time.Clock;
import org.bbnors.engine.camera.CameraModel;
import org.bbnors.engine.camera.SimulatedCamera;
import org.bbnors.engine.input.InputBackend;
import org.bbnors.engine.input.MouseButton;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A small, self-contained game world implementing both {@link GameClient} (what scripts read)
 * and {@link InputBackend} (what the engine writes). It is the reference adapter, the
 * integration-test fixture and the demo target, and it has nothing to do with any real client.
 *
 * <p>World: a 40×30 tile region with a fence, a bank booth, a cluster of trees that deplete and
 * respawn, and wandering chickens. The local player walks one tile per 600 ms tick along A*
 * paths. Rendering is a rotated, anisotropic top-down projection (34 px/tile across, 20 px/tile
 * ahead), so the camera yaw genuinely affects what is on screen. There is a minimap, a 28-slot
 * inventory, a bank interface and an OSRS-style right-click menu (opens at the cursor, closes
 * when the cursor leaves it).
 *
 * <p>All state is guarded by {@code this}. Entities are live views.
 */
public final class SimulatedGame implements GameClient, InputBackend {

    public static final int BASE_X = 3200;
    public static final int BASE_Y = 3200;
    public static final int WIDTH = 40;
    public static final int HEIGHT = 30;
    public static final long TICK_NANOS = 600_000_000L;

    public static final int LOGS = 1511;
    public static final int AXE = 1351;
    public static final int FEATHER = 314;

    private static final Rect VIEWPORT = new Rect(4, 4, 512, 334);
    private static final Rect MINIMAP = new Rect(555, 10, 144, 144);
    private static final Rect INVENTORY = new Rect(553, 205, 180, 260);
    private static final Rect BANK_UI = new Rect(20, 20, 480, 300);
    private static final double SCALE_X = 34;
    private static final double SCALE_Y = 20;
    private static final double MM_SCALE = 4;
    private static final int MENU_WIDTH = 140;
    private static final int MENU_HEADER = 19;
    private static final int MENU_ROW = 15;

    private final Rng rng;
    private final SimulatedCamera camera;
    private final GridPathFinder pathFinder = new GridPathFinder(64);
    private final Layout layout = new Layout();
    private final Menu menu = new Menu();
    private final Set<Long> blocked = new HashSet<>();

    private int tick;
    private final SimPlayer player;
    private final List<SimNpc> npcs = new ArrayList<>();
    private final List<SimObject> objects = new ArrayList<>();
    private final List<SimGroundItem> ground = new ArrayList<>();
    private final Item[] inventory = new Item[28];
    private final List<Item> bank = new ArrayList<>();
    private final List<Item> equipment = new ArrayList<>();
    private boolean bankOpen;

    private int mouseX;
    private int mouseY;
    private boolean menuOpen;
    private Rect menuRect;
    private List<MenuEntry> menuEntries = List.of();
    private List<Runnable> menuActions = List.of();

    // statistics for tests / demos
    private int logsChopped;
    private int itemsBanked;
    private int misclicks;
    private Consumer<String> messages = m -> { };

    public SimulatedGame(Clock clock, long seed) {
        this.rng = new Rng(seed);
        this.camera = new SimulatedCamera(clock, 0, 300, 1.0);
        this.player = new SimPlayer(new Tile(BASE_X + 8, BASE_Y + 8));
        buildWorld();
    }

    /** Registers the 600 ms game tick on a scheduler (typically {@link SimulationClock#scheduler()}). */
    public void attach(org.bbnors.engine.timing.TaskScheduler scheduler) {
        scheduler.scheduleAtFixedRate(TICK_NANOS, TICK_NANOS, this::advanceTick);
    }

    private void buildWorld() {
        // fence along y = BASE_Y + 14 with a 3-tile gap at x = 18..20
        for (int x = 0; x < 30; x++) {
            if (x < 18 || x > 20) {
                blocked.add(key(BASE_X + x, BASE_Y + 14));
            }
        }
        objects.add(new SimObject(10_355, "Bank booth", new Tile(BASE_X + 5, BASE_Y + 5), List.of("Bank", "Examine"), 34, 30, 10));
        int[][] trees = {{14, 20}, {17, 22}, {21, 19}, {24, 23}, {12, 24}, {19, 26}};
        for (int[] t : trees) {
            objects.add(new SimObject(1276, "Tree", new Tile(BASE_X + t[0], BASE_Y + t[1]), List.of("Chop down", "Examine"), 36, 54, 22));
        }
        for (SimObject o : objects) {
            blocked.add(key(o.tile.x(), o.tile.y()));
        }
        for (int i = 0; i < 4; i++) {
            npcs.add(new SimNpc(i, new Tile(BASE_X + 9 + i * 2, BASE_Y + 10)));
        }
        inventory[0] = item(AXE, "Bronze axe", 1, 0);
    }

    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xffffffffL);
    }

    private static Item item(int id, String name, int qty, int slot) {
        List<String> actions = id == AXE ? List.of("Wield", "Use", "Drop", "Examine") : List.of("Use", "Drop", "Examine");
        return new Item(id, name, qty, slot, actions);
    }

    public void setMessageListener(Consumer<String> l) {
        messages = l;
    }

    // ------------------------------------------------------------------ tick

    /** Advances the world by one game tick. */
    public synchronized void advanceTick() {
        tick++;
        player.step();
        for (SimNpc n : npcs) {
            n.step();
        }
        for (SimObject o : objects) {
            if (o.depletedUntil > 0 && tick >= o.depletedUntil) {
                o.depletedUntil = 0;
            }
        }
        ground.removeIf(g -> tick - g.spawnTick > 200);
    }

    // ------------------------------------------------------------------ projection

    private double theta() {
        return camera.yaw() * 2 * Math.PI / camera.yawRange();
    }

    private Vec2 project(double dx, double dy, Vec2 center, double sx, double sy) {
        double t = theta();
        double c = Math.cos(t);
        double s = Math.sin(t);
        double rx = dx * c - dy * s;
        double ry = dx * s + dy * c;
        return new Vec2(center.x() + rx * sx, center.y() - ry * sy);
    }

    private Tile unproject(double px, double py, Vec2 center, double sx, double sy) {
        double rx = (px - center.x()) / sx;
        double ry = (center.y() - py) / sy;
        double t = theta();
        double c = Math.cos(t);
        double s = Math.sin(t);
        double dx = c * rx + s * ry;
        double dy = -s * rx + c * ry;
        Tile me = player.tile;
        return new Tile(me.x() + (int) Math.round(dx), me.y() + (int) Math.round(dy));
    }

    @Override
    public synchronized Vec2 tileToScreen(Tile t) {
        Vec2 p = project(t.x() - player.tile.x(), t.y() - player.tile.y(), VIEWPORT.center(), SCALE_X, SCALE_Y);
        return VIEWPORT.contains(p) ? p : null;
    }

    @Override
    public synchronized Vec2 tileToMinimap(Tile t) {
        Vec2 p = project(t.x() - player.tile.x(), t.y() - player.tile.y(), MINIMAP.center(), MM_SCALE, MM_SCALE);
        return p.distance(MINIMAP.center()) <= MINIMAP.width() / 2 - 2 ? p : null;
    }

    private Rect boundsAt(Tile t, double w, double h, double lift) {
        if (bankOpen) {
            return null;
        }
        Vec2 p = tileToScreen(t);
        if (p == null) {
            return null;
        }
        Rect r = Rect.centered(p.add(new Vec2(0, -lift)), w, h);
        return VIEWPORT.contains(new Vec2(r.x(), r.y())) && VIEWPORT.contains(new Vec2(r.x() + r.width(), r.y() + r.height())) ? r : null;
    }

    // ------------------------------------------------------------------ GameClient

    @Override public boolean loggedIn() { return true; }
    @Override public synchronized int tick() { return tick; }
    @Override public Player localPlayer() { return player; }
    @Override public synchronized List<Npc> npcs() { return List.copyOf(npcs); }
    @Override public List<Player> players() { return List.of(player); }
    @Override public synchronized List<GameObject> objects() { return List.copyOf(objects); }
    @Override public synchronized List<GroundItem> groundItems() { return List.copyOf(ground); }
    @Override public int inventoryCapacity() { return 28; }
    @Override public synchronized List<Item> equipment() { return List.copyOf(equipment); }
    @Override public synchronized boolean bankOpen() { return bankOpen; }
    @Override public synchronized List<Item> bank() { return List.copyOf(bank); }
    @Override public MenuModel menu() { return menu; }
    @Override public InterfaceLayout layout() { return layout; }
    @Override public CameraModel camera() { return camera; }

    @Override
    public synchronized List<Item> inventory() {
        List<Item> out = new ArrayList<>();
        for (Item i : inventory) {
            if (i != null) {
                out.add(bankOpen ? new Item(i.id(), i.name(), i.quantity(), i.slot(), List.of("Deposit-1", "Deposit-All")) : i);
            }
        }
        return out;
    }

    @Override
    public synchronized Optional<Widget> widget(int group, int child) {
        if (group == 12 && bankOpen) {
            if (child == 1) return Optional.of(new Widget(12, 1, "Deposit inventory", layout.bankDepositInventory(), true, List.of()));
            if (child == 2) return Optional.of(new Widget(12, 2, "Close", layout.bankClose(), true, List.of()));
        }
        return Optional.empty();
    }

    @Override
    public CollisionMap collision() {
        return (x, y, plane) -> {
            if (x < BASE_X || y < BASE_Y || x >= BASE_X + WIDTH || y >= BASE_Y + HEIGHT || plane != 0) {
                return false;
            }
            synchronized (SimulatedGame.this) {
                return !blocked.contains(key(x, y));
            }
        };
    }

    // ------------------------------------------------------------------ input

    @Override
    public synchronized void mouseMove(int x, int y) {
        mouseX = x;
        mouseY = y;
        camera.mouseMove(x, y);
        if (menuOpen && !menuRect.inset(-10).contains(new Vec2(x, y))) {
            closeMenu();
        }
    }

    @Override
    public synchronized void mousePress(MouseButton button) {
        camera.mousePress(button);
        Vec2 p = new Vec2(mouseX, mouseY);
        if (button == MouseButton.LEFT) {
            if (menuOpen) {
                for (int i = 0; i < menuEntries.size(); i++) {
                    if (menuEntries.get(i).bounds().contains(p)) {
                        Runnable r = menuActions.get(i);
                        closeMenu();
                        r.run();
                        return;
                    }
                }
                closeMenu();
                return;
            }
            List<Option> opts = optionsAt(p);
            if (opts.isEmpty()) {
                misclicks++;
            } else {
                opts.get(0).run().run();
            }
        } else if (button == MouseButton.RIGHT) {
            openMenu(p);
        }
    }

    @Override
    public synchronized void mouseRelease(MouseButton button) {
        camera.mouseRelease(button);
    }

    @Override
    public void mouseWheel(int notches) {
    }

    @Override
    public synchronized void keyPress(int keyCode, char keyChar) {
        camera.keyPress(keyCode, keyChar);
    }

    @Override
    public synchronized void keyRelease(int keyCode, char keyChar) {
        camera.keyRelease(keyCode, keyChar);
    }

    // ------------------------------------------------------------------ menu / click resolution

    private record Option(String name, String target, Runnable run) { }

    private List<Option> optionsAt(Vec2 p) {
        List<Option> out = new ArrayList<>();
        if (bankOpen && BANK_UI.contains(p)) {
            if (layout.bankDepositInventory().contains(p)) {
                out.add(new Option("Deposit inventory", "", this::depositInventory));
            } else if (layout.bankClose().contains(p)) {
                out.add(new Option("Close", "", () -> bankOpen = false));
            } else {
                for (int i = 0; i < bank.size(); i++) {
                    if (layout.bankSlot(i).contains(p)) {
                        Item it = bank.get(i);
                        out.add(new Option("Withdraw-1", it.name(), () -> withdraw(it.id(), 1)));
                        out.add(new Option("Withdraw-All", it.name(), () -> withdraw(it.id(), Integer.MAX_VALUE)));
                    }
                }
            }
            return out;
        }
        if (INVENTORY.contains(p)) {
            for (int s = 0; s < 28; s++) {
                if (inventory[s] != null && layout.inventorySlot(s).contains(p)) {
                    Item it = inventory[s];
                    int slot = s;
                    if (bankOpen) {
                        out.add(new Option("Deposit-1", it.name(), () -> deposit(it.id(), 1)));
                        out.add(new Option("Deposit-All", it.name(), () -> deposit(it.id(), Integer.MAX_VALUE)));
                    } else {
                        for (String a : it.actions()) {
                            out.add(new Option(a, it.name(), () -> itemAction(slot, a)));
                        }
                    }
                }
            }
            return out;
        }
        if (MINIMAP.contains(p)) {
            if (p.distance(MINIMAP.center()) <= MINIMAP.width() / 2) {
                Tile t = unproject(p.x(), p.y(), MINIMAP.center(), MM_SCALE, MM_SCALE);
                out.add(new Option("Walk here", "", () -> player.walkTo(t, null)));
            }
            return out;
        }
        if (!VIEWPORT.contains(p) || bankOpen) {
            return out;
        }
        // entities: nearer to the camera (larger screen y) are on top
        List<SimEntity> hits = new ArrayList<>();
        for (SimNpc n : npcs) if (n.boundsUnlocked() != null && n.boundsUnlocked().contains(p)) hits.add(n);
        for (SimObject o : objects) if (o.boundsUnlocked() != null && o.boundsUnlocked().contains(p)) hits.add(o);
        for (SimGroundItem g : ground) if (g.boundsUnlocked() != null && g.boundsUnlocked().contains(p)) hits.add(g);
        hits.sort((a, b) -> Double.compare(b.boundsUnlocked().center().y(), a.boundsUnlocked().center().y()));
        for (SimEntity e : hits) {
            for (String a : e.actionsUnlocked()) {
                if (!a.equals("Examine")) {
                    out.add(new Option(a, e.name, () -> entityAction(e, a)));
                }
            }
        }
        Tile t = unproject(p.x(), p.y(), VIEWPORT.center(), SCALE_X, SCALE_Y);
        out.add(new Option("Walk here", "", () -> player.walkTo(t, null)));
        return out;
    }

    private void openMenu(Vec2 p) {
        List<Option> opts = new ArrayList<>(optionsAt(p));
        opts.add(new Option("Cancel", "", () -> { }));
        double x = Math.max(0, Math.min(765 - MENU_WIDTH, p.x() - MENU_WIDTH / 2.0));
        double y = Math.max(0, p.y() - 2);
        menuRect = new Rect(x, y, MENU_WIDTH, MENU_HEADER + MENU_ROW * opts.size());
        List<MenuEntry> named = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        for (int i = 0; i < opts.size(); i++) {
            named.add(new MenuEntry(opts.get(i).name(), opts.get(i).target(), new Rect(x, y + MENU_HEADER + i * MENU_ROW, MENU_WIDTH, MENU_ROW)));
            actions.add(opts.get(i).run());
        }
        menuEntries = named;
        menuActions = actions;
        menuOpen = true;
    }

    private void closeMenu() {
        menuOpen = false;
        menuEntries = List.of();
        menuActions = List.of();
        menuRect = null;
    }

    private final class Menu implements MenuModel {
        @Override
        public boolean isOpen() {
            synchronized (SimulatedGame.this) {
                return menuOpen;
            }
        }

        @Override
        public List<MenuEntry> entries() {
            synchronized (SimulatedGame.this) {
                return List.copyOf(menuEntries);
            }
        }

        @Override
        public Optional<MenuEntry> hovered() {
            synchronized (SimulatedGame.this) {
                if (menuOpen) return Optional.empty();
                List<Option> o = optionsAt(new Vec2(mouseX, mouseY));
                return o.isEmpty() ? Optional.empty() : Optional.of(new MenuEntry(o.get(0).name(), o.get(0).target(), null));
            }
        }
    }

    // ------------------------------------------------------------------ actions

    private void entityAction(SimEntity e, String action) {
        switch (action) {
            case "Chop down" -> player.walkTo(e.tile, () -> player.startChopping((SimObject) e));
            case "Bank" -> player.walkTo(e.tile, () -> bankOpen = true);
            case "Attack" -> player.walkTo(e.tile, () -> player.startAttacking((SimNpc) e));
            case "Take" -> player.walkTo(e.tile, () -> take((SimGroundItem) e));
            default -> messages.accept("Nothing interesting happens.");
        }
    }

    private void itemAction(int slot, String action) {
        Item it = inventory[slot];
        if (it == null) return;
        switch (action) {
            case "Drop" -> {
                inventory[slot] = null;
                ground.add(new SimGroundItem(it.id(), it.name(), it.quantity(), player.tile, tick));
            }
            case "Wield" -> {
                inventory[slot] = null;
                equipment.removeIf(e -> e.slot() == 3);
                equipment.add(new Item(it.id(), it.name(), 1, 3, List.of("Remove")));
            }
            default -> messages.accept("Nothing interesting happens.");
        }
    }

    private boolean addItem(int id, String name, int qty) {
        for (int s = 0; s < 28; s++) {
            if (inventory[s] == null) {
                inventory[s] = item(id, name, qty, s);
                return true;
            }
        }
        return false;
    }

    private int freeSlots() {
        int n = 0;
        for (Item i : inventory) if (i == null) n++;
        return n;
    }

    private void deposit(int id, int amount) {
        int left = amount;
        for (int s = 0; s < 28 && left > 0; s++) {
            if (inventory[s] != null && inventory[s].id() == id) {
                Item it = inventory[s];
                inventory[s] = null;
                left -= it.quantity();
                bankAdd(it);
            }
        }
    }

    private void depositInventory() {
        for (int s = 0; s < 28; s++) {
            if (inventory[s] != null) {
                bankAdd(inventory[s]);
                inventory[s] = null;
            }
        }
    }

    private void bankAdd(Item it) {
        itemsBanked += it.quantity();
        for (int i = 0; i < bank.size(); i++) {
            Item b = bank.get(i);
            if (b.id() == it.id()) {
                bank.set(i, new Item(b.id(), b.name(), b.quantity() + it.quantity(), i, List.of("Withdraw-1", "Withdraw-All")));
                return;
            }
        }
        bank.add(new Item(it.id(), it.name(), it.quantity(), bank.size(), List.of("Withdraw-1", "Withdraw-All")));
    }

    private void withdraw(int id, int amount) {
        for (int i = 0; i < bank.size(); i++) {
            Item b = bank.get(i);
            if (b.id() != id) continue;
            int n = Math.min(Math.min(amount, b.quantity()), freeSlots());
            for (int k = 0; k < n; k++) addItem(b.id(), b.name(), 1);
            int rest = b.quantity() - n;
            if (rest > 0) {
                bank.set(i, new Item(b.id(), b.name(), rest, i, b.actions()));
            } else {
                bank.remove(i);
                for (int j = i; j < bank.size(); j++) {
                    Item x = bank.get(j);
                    bank.set(j, new Item(x.id(), x.name(), x.quantity(), j, x.actions()));
                }
            }
            return;
        }
    }

    private void take(SimGroundItem g) {
        if (ground.contains(g) && addItem(g.id, g.name, g.quantity)) {
            ground.remove(g);
        }
    }

    private boolean hasAxe() {
        for (Item i : inventory) if (i != null && i.id() == AXE) return true;
        return equipment.stream().anyMatch(e -> e.id() == AXE);
    }

    // ------------------------------------------------------------------ statistics

    public synchronized int logsChopped() { return logsChopped; }
    public synchronized int itemsBanked() { return itemsBanked; }
    public synchronized int misclicks() { return misclicks; }
    public synchronized Vec2 mousePosition() { return new Vec2(mouseX, mouseY); }

    /** Teleports the player (tests). */
    public synchronized void setPlayerTile(Tile t) {
        player.tile = t;
        player.path.clear();
    }

    // ------------------------------------------------------------------ entities

    private abstract class SimEntity implements org.bbnors.api.model.Entity {
        final int id;
        final String name;
        Tile tile;

        SimEntity(int id, String name, Tile tile) {
            this.id = id;
            this.name = name;
            this.tile = tile;
        }

        abstract Rect boundsUnlocked();

        abstract List<String> actionsUnlocked();

        @Override public int id() { return id; }
        @Override public String name() { synchronized (SimulatedGame.this) { return displayName(); } }
        String displayName() { return name; }
        @Override public Tile tile() { synchronized (SimulatedGame.this) { return tile; } }
        @Override public List<String> actions() { synchronized (SimulatedGame.this) { return actionsUnlocked(); } }
        @Override public Rect screenBounds() { synchronized (SimulatedGame.this) { return boundsUnlocked(); } }
        @Override public boolean valid() { return true; }
    }

    private final class SimObject extends SimEntity implements GameObject {
        final List<String> actions;
        final double w;
        final double h;
        final double lift;
        int depletedUntil;

        SimObject(int id, String name, Tile tile, List<String> actions, double w, double h, double lift) {
            super(id, name, tile);
            this.actions = actions;
            this.w = w;
            this.h = h;
            this.lift = lift;
        }

        boolean depleted() {
            return depletedUntil > 0;
        }

        @Override String displayName() { return depleted() ? "Tree stump" : name; }
        @Override List<String> actionsUnlocked() { return depleted() ? List.of("Examine") : actions; }
        @Override Rect boundsUnlocked() { return boundsAt(tile, depleted() ? w * 0.7 : w, depleted() ? h * 0.4 : h, depleted() ? lift * 0.3 : lift); }

        @Override
        public String name() {
            synchronized (SimulatedGame.this) {
                return displayName();
            }
        }
    }

    private final class SimGroundItem extends SimEntity implements GroundItem {
        final int quantity;
        final int spawnTick;

        SimGroundItem(int id, String name, int quantity, Tile tile, int spawnTick) {
            super(id, name, tile);
            this.quantity = quantity;
            this.spawnTick = spawnTick;
        }

        @Override List<String> actionsUnlocked() { return List.of("Take", "Examine"); }
        @Override Rect boundsUnlocked() { return boundsAt(tile, 16, 12, 0); }
        @Override public int quantity() { return quantity; }
        @Override public boolean valid() { synchronized (SimulatedGame.this) { return ground.contains(this); } }
    }

    private final class SimNpc extends SimEntity implements Npc {
        final Tile home;
        int deadUntil;
        int hp = 3;

        SimNpc(int index, Tile home) {
            super(2692, "Chicken", home);
            this.home = home;
        }

        void step() {
            if (deadUntil > 0) {
                if (tick >= deadUntil) {
                    deadUntil = 0;
                    hp = 3;
                    tile = home;
                }
                return;
            }
            if (rng.chance(0.2)) {
                int dx = rng.nextInt(3) - 1;
                int dy = rng.nextInt(3) - 1;
                Tile n = tile.translate(dx, dy);
                if (n.steps(home) <= 3 && collision().canMove(tile.x(), tile.y(), 0, dx, dy) && !n.equals(player.tile)) {
                    tile = n;
                }
            }
        }

        @Override List<String> actionsUnlocked() { return deadUntil > 0 ? List.of() : List.of("Attack", "Examine"); }
        @Override Rect boundsUnlocked() { return deadUntil > 0 ? null : boundsAt(tile, 24, 30, 12); }
        @Override public int animation() { return -1; }
        @Override public boolean moving() { return false; }
        @Override public double health() { synchronized (SimulatedGame.this) { return hp / 3.0; } }
        @Override public boolean valid() { synchronized (SimulatedGame.this) { return deadUntil == 0; } }
    }

    private final class SimPlayer extends SimEntity implements Player {
        final java.util.ArrayDeque<Tile> path = new java.util.ArrayDeque<>();
        Runnable onArrive;
        SimObject chopping;
        SimNpc fighting;
        int animation = -1;

        SimPlayer(Tile t) {
            super(-1, "You", t);
        }

        void walkTo(Tile target, Runnable arrive) {
            chopping = null;
            fighting = null;
            animation = -1;
            path.clear();
            List<Tile> p = arrive != null ? pathFinder.findAdjacent(collision(), tile, target) : pathFinder.find(collision(), tile, target);
            if (p.isEmpty() && !(arrive != null && Math.abs(tile.x() - target.x()) + Math.abs(tile.y() - target.y()) <= 1)) {
                onArrive = null;
                return;
            }
            path.addAll(p);
            onArrive = arrive;
            if (path.isEmpty() && arrive != null) {
                onArrive = null;
                arrive.run();
            }
        }

        void step() {
            if (!path.isEmpty()) {
                tile = path.poll();
                if (path.isEmpty() && onArrive != null) {
                    Runnable r = onArrive;
                    onArrive = null;
                    r.run();
                }
                return;
            }
            if (chopping != null) {
                if (chopping.depleted() || freeSlots() == 0 || !hasAxe()) {
                    chopping = null;
                    animation = -1;
                } else if (rng.chance(0.3)) {
                    addItem(LOGS, "Logs", 1);
                    logsChopped++;
                    if (rng.chance(0.25)) {
                        chopping.depletedUntil = tick + 12;
                    }
                }
            }
            if (fighting != null) {
                if (fighting.deadUntil > 0) {
                    fighting = null;
                    animation = -1;
                } else if (rng.chance(0.6) && --fighting.hp <= 0) {
                    fighting.deadUntil = tick + 15;
                    ground.add(new SimGroundItem(FEATHER, "Feather", 5, fighting.tile, tick));
                    fighting = null;
                    animation = -1;
                }
            }
        }

        void startChopping(SimObject tree) {
            if (!hasAxe()) {
                messages.accept("You need an axe to chop down this tree.");
                return;
            }
            if (!tree.depleted() && freeSlots() > 0) {
                chopping = tree;
                animation = 879;
            }
        }

        void startAttacking(SimNpc n) {
            if (n.deadUntil == 0) {
                fighting = n;
                animation = 422;
            }
        }

        @Override List<String> actionsUnlocked() { return List.of(); }
        @Override Rect boundsUnlocked() { return boundsAt(tile, 24, 40, 16); }
        @Override public int animation() { synchronized (SimulatedGame.this) { return animation; } }
        @Override public boolean moving() { synchronized (SimulatedGame.this) { return !path.isEmpty(); } }
    }

    private final class Layout implements InterfaceLayout {
        @Override public Rect viewport() { return VIEWPORT; }
        @Override public Rect inventorySlot(int slot) { return new Rect(563 + (slot % 4) * 42, 213 + (slot / 4) * 36, 36, 32); }
        @Override public Rect bankSlot(int index) { return new Rect(40 + (index % 8) * 48, 60 + (index / 8) * 40, 40, 34); }
        @Override public Rect bankDepositInventory() { return new Rect(420, 290, 40, 26); }
        @Override public Rect bankClose() { return new Rect(476, 24, 22, 22); }
        @Override public Rect minimap() { return MINIMAP; }
    }
}
