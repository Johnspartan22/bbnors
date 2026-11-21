# Simulated server (`org.bbnors.api.sim`)

The simulator is a **test fixture and reference backend**. It shows how a `GameClient` is
implemented and lets every layer above it run deterministically. It doesn't model any real
game client or server protocol, and the framework doesn't depend on it (ARCHITECTURE ADR-8).

## 1. Architecture

```
Simulation (wiring helper)
 ├── SimulationClock ── ManualClock + ManualScheduler: sleeping advances virtual time and runs due ticks
 ├── SimulatedGame   ── implements GameClient (what scripts read) AND InputBackend (what the engine writes)
 │     ├── world: tiles, collision, objects, NPCs, ground items, local player
 │     ├── interfaces: inventory (28), bank, equipment, right-click menu, minimap, two widgets
 │     ├── camera: SimulatedCamera (responds to arrow keys / middle-drag; supports direct set)
 │     └── advanceTick(): every 600 ms of virtual time
 ├── RecordingBackend ─ wraps the game; timestamps every input event
 ├── InteractionEngine ─ synchronous executor (Runnable::run) → single-threaded, deterministic
 └── ScriptContext
```

One thread does everything. When a script waits or the mouse plays a movement, the virtual clock
advances and the world ticks inside that sleep. Same seed means the same run, event for event
(asserted in `SimulationIntegrationTest`). Speed: about 5,000× real time (PERFORMANCE.md §4).

## 2. World

| Element | Details |
|---|---|
| Region | 40×30 tiles from (3200, 3200), single plane |
| Obstacles | fence along y = 3214 for x = 3200-3229, 3-tile gap at x = 3218-3220; objects block their tile |
| Bank booth | (3205, 3205), action `Bank` (walks adjacent, then opens) |
| Trees | 6, action `Chop down`; each tick 30% chance of a log; after a log, 25% chance to deplete into a stump for 12 ticks; needs a `Bronze axe` (inventory or equipped) |
| Chickens | 4, wander (20%/tick, within 3 tiles of home); `Attack`: 60%/tick hit, 3 HP, drop 5 `Feather`s, respawn after 15 ticks |
| Ground items | `Take`; despawn after 200 ticks |
| Player | starts at (3208, 3208) with a `Bronze axe`; walks 1 tile per tick along A* paths (radius 64) |

## 3. Rendering and input model

| Aspect | Model |
|---|---|
| Viewport | 512×334 at (4, 4); top-down projection rotated by camera yaw; 34 px/tile across, 20 px/tile ahead, so turning towards a target brings more of it on screen |
| Entity bounds | rectangles above the tile centre; `null` when off the viewport or while the bank is open |
| Minimap | 144 px circle at (555, 10), 4 px/tile, rotated with yaw; clicking walks there |
| Inventory | 4×7 slots from (563, 213), 42×36 pitch |
| Bank | panel (20, 20, 480, 300); 8-column slots; deposit-inventory and close buttons (also widgets 12:1, 12:2) |
| Menu | right-click opens at the cursor: actions under the cursor, `Walk here`, `Cancel`; 140 px wide, 19 px header, 15 px rows; closes when the cursor moves more than 10 px outside it |
| Left click | top-most option under the cursor (menu → bank → inventory → minimap → nearest-to-camera entity → walk) |
| Camera | yaw changes with LEFT/RIGHT held (1024 units/s), pitch with UP/DOWN (512/s), or middle-drag; direct set supported |

## 4. Limitations (by design or not yet built)

| Limitation | Consequence |
|---|---|
| 2D projection; pitch and zoom do not affect what is visible | camera pitch logic is exercised for convergence only |
| Single plane, no doors, stairs or transports | no multi-floor or long-distance pathing scenarios |
| No run energy, skills, XP, levels, combat stats, prayers | managers for those don't exist yet; add via `GameClient.extension` |
| No general stacking rules: chopped logs and withdrawn items take one slot each, picked-up ground items keep their quantity in one slot; no noted items, shops, trading, dialogue or chat | bank/inventory logic is tested on simple cases only |
| Only two widgets | `Widgets` manager is lightly exercised |
| No network latency, lost clicks or frame drops | `AdverseConditionsTest` covers scheduling jitter in the engine, not server lag |
| No other players | `players()` returns only the local player |
| Deterministic and single-threaded in `Simulation` | concurrency is tested separately (`InteractionManagerTest`), not end to end |
| Menu and click semantics are a simplification | real backends will differ in details (menu sizes, option order); `Interactions` only relies on `MenuModel` |

## 5. Extending it

Add behaviour inside `SimulatedGame` (new object types in `buildWorld`, new action cases in
`entityAction`/`itemAction`). Keep it a fixture: if a scenario needs framework support, implement
that support against `GameClient`, never with simulator-specific branches in framework code.
