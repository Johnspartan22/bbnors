# Scripting API

## 1. The pieces

```
ScriptRegistry ──create(id)──► Script ◄── TaskScript / TreeScript (bases you extend)
                                   │ loop(ctx), onStart(ctx), onStop(ctx)
                                   ▼
ScriptRunner ── owns the loop ──► ScriptContext ── one object a script uses for everything
   state machine, error budget,       ├── game()        GameState (per-tick cached GameClient)
   pause/stop, events,                ├── npcs() objects() players() groundItems()   EntityQuery
   releases input on exit             ├── interact(e, action)  → Interactions (menu-aware)
                                      ├── inventory() bank() equipment() widgets() walking() camera()
                                      ├── waitUntil(cond) sleep(ms) sleepProfile(name)
                                      ├── engine()      InteractionEngine (mouse/keyboard/camera)
                                      └── rng() log()
```

A `Script` is plain Java. It holds no reference to the engine or the game. Everything it needs
arrives as the `ScriptContext` argument, which makes scripts easy to test and to reuse with
another backend.

## 2. The loop contract

```java
public interface Script {
    default void onStart(ScriptContext ctx) {}
    int loop(ScriptContext ctx);            // return delay in ms; negative = stop
    default void onStop(ScriptContext ctx) {}
    default String status() { return ""; }
}
```

`ScriptRunner` drives it:

```
CREATED ─► RUNNING ─(pause)─► PAUSED ─(resume)─► RUNNING
              │ loop() < 0, stop(), onStart throws, or N consecutive loop() exceptions
              ▼
        onStop(ctx) → engine.stopAll() (cancel operations, release keys/buttons) → STOPPED | FAILED
```

- Return values below `script.min-loop-delay-ms` are raised to it (no accidental busy loops).
- A throwing `loop()` is logged, published as `ScriptEvent.LoopFailed`, followed by
  `script.error-backoff-ms`; `script.max-consecutive-errors` failures in a row → `FAILED`.
- `stop()` cancels in-flight input immediately; `ctx.sleep`/`ctx.waitUntil` notice the stop
  request and unwind the loop with a `CancellationException` that the runner handles.
- `start()` runs on a dedicated thread; `runInline(maxLoops)` runs on the caller's thread
  (deterministic with the simulator's virtual clock).

## 3. TaskScript

A list of small behaviours. Each loop runs the **highest-priority task whose `accept` is true**;
ties keep registration order. `status()` reports the running task's name.

```java
public final class Fisher extends TaskScript {
    public Fisher() { add(new Drop(), new Fish()); }

    static final class Drop implements Task {
        public String name() { return "drop"; }
        public int priority() { return 10; }                       // checked first
        public boolean accept(ScriptContext c) { return c.inventory().isFull(); }
        public int execute(ScriptContext c) { c.inventory().dropAll("Raw shrimps"); return 300; }
    }

    static final class Fish implements Task {
        public String name() { return "fish"; }
        public boolean accept(ScriptContext c) { return c.player().idle(); }
        public int execute(ScriptContext c) {
            c.npcs().name("Fishing spot").action("Net").nearest()
             .ifPresent(spot -> c.interact(spot, "Net"));
            return 600;
        }
    }
}
```

Use it when behaviours are independent and a priority order captures the logic.

## 4. TreeScript

A decision tree. `Branch` = condition + children; `Leaf` = condition + action returning the next
delay. Each loop descends from `root()` into the first accepting child until a leaf runs.
`status()` reports the path taken, e.g. `root > bank > deposit`.

```java
public final class Banker extends TreeScript {
    private final Node root = branch("root", c -> true,
        branch("bank", c -> c.inventory().isFull(),
            leaf("open",    c -> !c.bank().isOpen(), c -> c.bank().open() ? 200 : 1000),
            leaf("deposit", c -> true,               c -> { c.bank().depositInventory(); return 300; })),
        leaf("work", c -> { /* ... */ return 600; }));

    @Override protected Node root() { return root; }
}
```

Use it when decisions are nested ("if bank open → … else if near bank → …").

## 5. ScriptContext in practice

| Need | Call |
|---|---|
| find things | `ctx.objects().name("Tree").action("Chop down").within(10).nearest()` |
| act on them | `ctx.interact(entity, "Chop down")` (moves, checks the hover action, right-clicks and picks from the menu if needed) |
| wait for an effect | `ctx.waitUntil(() -> c.player().animation() != -1).timeout(Duration.ofSeconds(4)).resetWhile(() -> c.player().moving()).await()` |
| pause like a person | `ctx.sleepProfile(TimingProfiles.ACTION_REACTION)` |
| containers | `ctx.inventory().count("Logs")`, `ctx.bank().withdraw("Logs", 1)`, `ctx.equipment().equip("Bronze axe")` |
| movement | `ctx.walking().walkTo(tile)`, `ctx.camera().turnTo(entity)` |
| raw input | `ctx.engine().keyboard().type("hello")`, `ctx.engine().mouse().moveTo(rect)` |
| backend extras | `ctx.client().extension(MyFeature.class)` |

Rules that keep scripts robust:
- Never `Thread.sleep`; use `ctx.sleep` / `ctx.waitUntil` (pause/stop-aware, clock-injected).
- Prefer conditions over fixed delays: return a short delay and let `accept`/conditions decide.
- Queries are cheap within a tick (cached), so re-query rather than holding entity references
  across long waits; check `entity.valid()` if you do hold one.

## 6. Registering and running scripts

**Annotate** (optional, recommended):

```java
@ScriptManifest(id = "fisher", name = "Fisher", version = "1.0", category = "skilling",
                description = "Nets shrimp and drops them.")
public final class Fisher extends TaskScript { ... }
```

**Register via ServiceLoader** (scripts need a public no-argument constructor). Create
`src/main/resources/META-INF/services/org.bbnors.api.script.Script` listing one class per line:

```
com.example.scripts.Fisher
```

**Or register in code** (constructor arguments, tests):

```java
ScriptRegistry registry = new ScriptRegistry();
registry.discover(MyApp.class.getClassLoader());              // ServiceLoader entries
registry.register(Fisher.class, () -> new Fisher(config));   // explicit factory
registry.list().forEach(System.out::println);
Script s = registry.create("fisher");                         // a fresh instance every time
```

Duplicate ids are rejected at registration; ids must match `[a-z0-9][a-z0-9-]*`.

**Run:**

```java
Simulation sim = Simulation.create(42);                 // or your own GameClient + InteractionEngine
ScriptRunner runner = sim.runner(registry.create("fisher"));
runner.runInline(500);                                  // deterministic
// or: runner.start(); ... runner.pause(); runner.resume(); runner.stop(); runner.join(2000);
```

From the command line, `RunExample list` and `RunExample <id> [loops] [seed]` run any registered
script on the simulator.

With a different backend, build the pieces yourself:

```java
InteractionEngine engine = InteractionEngine.builder(Clock.system(), inputBackend)
        .camera(client.camera()).config(config).build();
ScriptContext ctx = new ScriptContext(client, engine, new Rng(seed), "fisher");
new ScriptRunner(registry.create("fisher"), ctx, config).start();
```

## 7. Observability

`ScriptRunner.ScriptEvent` (state changes, loop completed with duration, loop failed) and
`InteractionEvent` (operation queued/started/completed/cancelled/failed) are published on
`engine.bus()`. `MouseEvents.MotionPlanned` carries each planned `Motion`, which the lab's
renderer can draw directly.
