# OSRS product direction: assessment and roadmap

**Status: proposal, awaiting approval.** No code has been changed for this document. Once
approved, its phases replace the "Prioritized remaining work" table in ROADMAP.md and its
proposed ADRs move into ARCHITECTURE.md.

## 1. Product definition

$bbnors is an **Old School RuneScape automation framework** with its own OSRS development and
simulation environment. Three layers:

| Layer | Role | Game knowledge |
|---|---|---|
| **OSRS Automation Framework** (the product) | scripting API, world model, entities, items, skills, interfaces, navigation, interactions, reusable activities, script organisation | OSRS-specific |
| **Interaction Simulation Core** | movement mathematics, mouse, keyboard, camera, timing, interaction state | game-agnostic by design |
| **OSRS Simulation Environment** | deterministic OSRS-like world for developing and testing scripts | OSRS-specific |

"Backend-agnostic" in earlier documents means **independent of how game state is obtained**
(simulator, an authorized server bridge), not independent of OSRS. The framework layer should
be unapologetically OSRS-shaped.

Unchanged boundary: game state only through `GameClient` implementations for environments we own
or are authorized to test; no memory reading, injection, reflection or hooking of the official
client; no detection-evasion functionality. In practice that also excludes "anti-ban" features
whose purpose is to make automation harder for operators to detect (break schedulers, fake idling,
deliberate misclick generators). The existing movement variability stays what it is: a
configurable simulation feature.

## 2. Where the project stands (measured on commit `06f192d`)

### Code distribution (main sources, 10,179 lines)

| Module / package | Lines | OSRS-specific? |
|---|---:|---|
| `bbnors-core` | 911 | no (by design) |
| `bbnors-motion` | 2,094 | no (by design) |
| `bbnors-engine` | 2,828 | no, except OSRS camera defaults in `CameraModel` (yaw range 2048, pitch 128-383) |
| `bbnors-lab` | 2,123 | no (movement lab) |
| `bbnors-api` `script` | 690 | mostly generic (runner, registry, Task/Tree scripts) |
| `bbnors-api` `model` + `query` + `game` + `path` | 1,474 | **yes**: tiles and planes, 28-slot inventory, bank, equipment slots, left-click/right-click menu model, minimap walking, widgets by group/child |
| `bbnors-api` `sim` | 872 | **yes**, but minimal |
| `bbnors-examples` | 187 | yes (woodcutter, chicken fighter) |

**Roughly 25% of the code is OSRS-specific.** The interaction core (about 60%) is the most mature
part. That's the reverse of the product's priorities.

### OSRS framework surface: coverage

Coverage is measured against the areas an OSRS framework of DreamBot/Powerbot scope needs.

| Area | State |
|---|---|
| Tiles, areas, planes | partial: `Tile` has a plane, but pathfinding and the simulator use one plane |
| Entities: NPCs, objects, ground items, players | partial: id, name, tile, actions, bounds, animation, moving, health |
| Entity queries | ✅ good |
| Menu-aware interaction | ✅ good (hover check, right-click, choose, cancel) |
| Inventory | partial: query, interact, drop; no use-item-on, no shift-drop, no stack/noted semantics |
| Bank | partial: open, deposit-all, deposit-inventory, withdraw 1/all, close; no withdraw-X, noted mode, tabs, placeholders |
| Equipment | partial: slot constants, equip |
| Widgets | minimal: lookup and click by (group, child) |
| Local walking (A*) | ✅ good, one plane |
| Camera | ✅ good (engine) |
| Scripts: lifecycle, Task/Tree, registry | ✅ good |
| Item / NPC / object definitions | ❌ |
| Skills, XP, levels, boosted levels | ❌ |
| Player state: run energy, HP, prayer, special attack, weight | ❌ |
| Game tabs and orbs | ❌ |
| Dialogues, level-up, make-X / production interfaces | ❌ |
| Chat messages | ❌ |
| Game events (XP drop, inventory change, NPC spawn, animation) | ❌ |
| Tick-based waits (`sleepTicks`) | ❌ (ticks exist; there's no API for them) |
| Combat: attack, styles, eating, looting, target selection | ❌ (the simulator has a toy attack) |
| Prayer, magic | ❌ |
| Doors, stairs, transports, multi-plane and long-distance walking | ❌ |
| Directional collision (walls), multi-tile objects | ❌ |
| Shops, deposit box | ❌ |
| Script paint/overlay, parameters, statistics (XP/h) | ❌ |
| Reusable OSRS activities (gather, bank, drop, fight, loot) | ❌ (logic lives inside the example scripts) |

**About 4 of 27 areas are solid, 7 partial, 16 missing.** The framework layer is a working
vertical slice that proves the architecture end to end. It's far from DreamBot/Powerbot breadth.

### Simulator: coverage

It has one plane, a fence, 6 trees, 1 bank booth and 4 chickens, with chopping, banking, a toy
attack, dropping, wielding and picking up. Success chances are flat (30% log per tick, 60% hit
chance). There are no skills, definitions, combat formulas, dialogues, doors, latency or other
players. It's a test fixture, not yet a development environment.

### Honest expectations

- **"Comparable to DreamBot/Powerbot"** is achievable in **API breadth, architecture and
  development tooling**. It isn't achievable in **live-game reach**. Those frameworks' main
  asset is maintained integration with the official client, which is permanently out of scope
  here. $bbnors scripts will run on the simulator and on backends you own or are authorized to
  use.
- Simulator mechanics will be **modelled approximations** built from publicly documented
  behaviour (the level/XP table, published formula shapes), written by us. They won't be
  extracted from the game client or its cache. Sprites, models, maps and bulk definition data
  belong to Jagex; bulk copying from community wikis also raises licensing issues (check the
  licence before importing anything). Definitions should be small, self-authored data files.

## 3. Gap analysis

### Framework API (missing OSRS concepts)

| Concept | Needed for |
|---|---|
| `ItemDefinition` (stackable, noted pair, equip slot, actions), `NpcDefinition` (combat level, size, actions), `ObjectDefinition` (size, orientation, actions, type) | everything: stacks, noted banking, multi-tile interaction |
| `Skill` enum (23), XP table, real vs boosted level, `Skills` manager | gathering, combat, requirement checks |
| `PlayerState`: HP, prayer points, run energy and run toggle, special attack, weight, poisoned, in-combat | resource management, combat, walking |
| `Tabs` (inventory, equipment, skills, prayer, magic, combat options), F-key or click switching | any tab-based action |
| `Dialogues`: NPC/player text, continue, options; level-up | NPC interactions, gathering interruptions |
| `Production` (make-X) interface | crafting, cooking, fletching |
| `Chat` messages, game events (`XpGained`, `InventoryChanged`, `ChatMessage`, `NpcSpawned`/`Despawned`, `AnimationChanged`, `LevelUp`, `Tick`) | event-driven scripts, statistics |
| Tick API: `ctx.ticks().sleep(n)`, `waitUntilNextTick`, tick-based timeouts | tick-accurate scripts |
| `Combat`: attack style, auto-retaliate, target selection, eat/drink thresholds, loot rules | combat scripts |
| `Prayer`, `Magic` (cast spell on target) | combat, utility |
| Bank: withdraw-X/all-but-one, noted mode, tabs, placeholders, deposit worn items; `DepositBox`; `Shop` | banking, resource management |
| Inventory: use-item-on-item/entity, combine, shift-drop | processing, dropping |
| Navigation: directional collision flags, doors (open/closed state), plane transitions, transports (stairs, ladders, shortcuts), region graph for long distances, run-energy management | real navigation |
| Activities: `Gather`, `Bank`, `Drop`, `Fight`, `Loot`, `Restock` building blocks | the reference scripts, and reuse |
| Script DX: parameters (typed `Setting`s per script), paint/overlay API, statistics (XP/h, items/h), runtime limits (stop conditions) | DreamBot-level developer experience |

### Simulation environment

| Concept | Approach |
|---|---|
| Definitions-driven world | load items, NPCs, objects, spawns, collision from self-authored data files |
| World authoring | a scenario/world builder (Java DSL plus data files); several reference worlds |
| Planes, walls, doors, stairs | directional collision flags; door objects with state; transports |
| Skills and XP | standard level/XP curve; XP drops; level-ups trigger a dialogue |
| Gathering | level- and tool-dependent success per roll interval; resource depletion and respawn per type; moving fishing spots; rocks; trees |
| Combat | simplified accuracy and max-hit formulas from public shapes; attack speed in ticks; retaliation, aggression, death, respawn, loot tables; eating |
| Interfaces | tabs, dialogues, make-X, bank tabs and modes, deposit box, shop; widget tree |
| Latency and adversity | configurable input-to-server delay (in ticks), dropped clicks, delayed state updates, other simulated players competing for resources |
| Inspection | top-down world viewer in the lab: entities, paths, menus, script status, event log; replayable runs |
| Determinism | everything seeded and virtual-clocked, as now |

## 4. Module plan

Extend before adding; split only where the dependency direction requires it.

| Module | Action | Contents |
|---|---|---|
| `bbnors-core`, `bbnors-motion`, `bbnors-engine` | **keep** (Interaction Simulation Core); small additions only | e.g. tick-aware waits are added in the framework, not here |
| `bbnors-lab` | **extend** | world/scenario viewer, script run inspector, evaluation of scripts (not only movement) |
| **`bbnors-osrs-data`** | **new** | definitions model (`ItemDefinition`, `NpcDefinition`, `ObjectDefinition`), `Skill`, XP table, equipment slots, data-file loader; no engine dependency |
| `bbnors-api` | **extend into the OSRS framework** (keep the module and package names to avoid churn) | new managers (skills, player state, tabs, dialogues, chat, combat, prayer, magic, production, deposit box, shop), event model and tick API, navigation extensions, `activity` package, script DX (parameters, paint, statistics) |
| **`bbnors-osrs-sim`** | **new, by moving** `org.bbnors.api.sim` out of `bbnors-api`, then expanding it | the simulation environment; framework code must not depend on it |
| **`bbnors-scripts`** | **new** (successor to `bbnors-examples`) | reference OSRS scripts: woodcutting, fishing, mining, combat, banking, generic gathering, resource management, each with scenario tests |
| **`bbnors-testkit`** | **new** | backend contract tests every `GameClient` must pass; scenario assertions and fixtures for script tests |
| `bbnors-examples` | merge into `bbnors-scripts` once that exists | |

Dependency direction after the plan:

```
core ◄ motion ◄ engine ◄ api ◄ osrs-sim ◄ scripts
                         ▲  ▲      ▲         ▲
             osrs-data ──┘  └ testkit ───────┘
                                      lab (uses api, osrs-sim)
```

## 5. Proposed architecture decisions

**ADR-13 (proposed): Grow `GameClient` with OSRS facets, not with flat methods.**
`GameClient` gains facet accessors such as `skills()`, `playerState()`, `tabs()`,
`dialogues()`, `chat()` and `varbits()`, each a small interface. Default implementations return
an "unsupported" facet, so existing backends keep compiling and generic code can check
`isSupported()`. Facets that every OSRS backend must offer get promoted to required. `extension()`
stays for truly backend-specific features.

**ADR-14 (proposed): Game events are tick-stamped and published on the engine bus.** Backends
emit `GameEvent`s (tick, type, payload). The framework derives some events itself by diffing
per-tick snapshots (inventory, XP), so a minimal backend still produces them.

**ADR-15 (proposed): Definitions are data, not code.** Self-authored data files, loaded by
`bbnors-osrs-data`. No extraction from the official client or cache. Bulk third-party data is
imported only after a licence check.

**ADR-16 (proposed): Activities over inheritance.** Reusable behaviours (`Gather`, `Bank`,
`Drop`, `Fight`, `Loot`) are composable objects that plug into `TaskScript`/`TreeScript`, not
base classes. A woodcutting script becomes configuration plus composition.

**ADR-17 (proposed): The simulator is a separate module and stays a fixture to the framework.**
Moving it out of `bbnors-api` enforces ADR-8 at compile time.

## 6. Prioritized roadmap

Each phase leaves the build green and ships tests and docs. Sizes: S ≈ days, M ≈ 1-2 weeks,
L ≈ several weeks (rough).

| Phase | Goal | Main work | Exit criteria | Size |
|---|---|---|---|---|
| **0. Reposition** | product identity and safety net | CI workflow; rewrite README/ARCHITECTURE around the three layers; move `sim` → `bbnors-osrs-sim` (mechanical, tests unchanged); adopt ADR-13 to ADR-17 | CI green on every push; no behaviour change; 80 tests pass | S |
| **1. OSRS foundations** | the data and world model everything else needs | `bbnors-osrs-data` (definitions, `Skill`, XP table, equipment slots); stack/noted semantics in `Item`; directional collision, multi-tile objects, doors, planes in `CollisionMap`/pathfinding; facet scaffolding (ADR-13) | definitions load from data files; pathfinding through doors and across planes in tests | M |
| **2. Ticks and events** | tick-accurate, event-driven scripting | tick API (`ctx.ticks()`), `GameEvent` model, snapshot-diff events, chat messages; simulator emits them | scripts can `sleepTicks` and subscribe to XP, inventory and chat events; tests use tick-precise assertions | S-M |
| **3. Core OSRS managers** | the everyday API | `Skills`, `PlayerState` (HP, run, prayer points), `Tabs`, `Dialogues`, `Production`, `Chat`; inventory use-on and combine; bank modes (X, noted, tabs, placeholders), `DepositBox` | each manager has simulator-backed tests and docs | M |
| **4. Simulation environment v2** | useful for real script development | definitions-driven worlds and a world builder; skills and XP; level-dependent gathering (trees, rocks, fishing spots) with depletion and respawn; dialogues and level-ups; doors, stairs, planes; latency, dropped clicks, competing players; 3 reference worlds (woodcutting/bank, mining/fishing, combat area) | scenario tests run in all three worlds under normal, latent and contended conditions | L |
| **5. Activities and reference scripts** | prove the framework with real OSRS scripts | `activity` package; `bbnors-scripts` with woodcutting, fishing, mining, banking, generic gathering, resource management (tool and food checks, restocking) | every script passes multi-seed, multi-condition scenario tests | M |
| **6. Combat** | the hardest common script class | simulator combat (attack speed, simplified accuracy and max hit, retaliation, aggression, death, loot tables, eating); `Combat`, `Prayer`, basic `Magic` managers; combat and loot activities; combat reference script | combat script survives and loots across seeds; eating thresholds respected | M-L |
| **7. Developer experience** | DreamBot-level ergonomics | script parameters (typed settings per script), paint/overlay API rendered in the simulator viewer, run statistics (XP/h, items/h), stop conditions; lab world viewer and run inspector; backend contract test kit | a new gathering script can be written, run, watched and tested from the docs alone | M |
| **8. Navigation at scale** | long-distance walking | region graph with transports over local A*, run-energy management | cross-world routes in the simulator | M |
| **9. Authorized backends** (gated) | run outside the simulator | server bridge only after the environment is named and authorization to integrate is confirmed; must pass the contract test kit | contract kit green against the bridge | M-L |

**Recommended order:** 0 → 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8, with 9 independent and gated.
Phases 1-3 are API work that the simulator then exercises; phase 4 is where the environment
becomes genuinely useful; phase 5 is the first point at which the product reads as an "OSRS
automation framework" rather than a slice of one.

## 7. What is preserved

Nothing that works is rewritten. Interaction core, movement math, timing, interaction manager,
camera, script runtime, registry, queries, interactions, walking, managers and the lab stay as
they are and get extended. Planned mechanical changes: moving `org.bbnors.api.sim` to its own
module (phase 0) and merging `bbnors-examples` into `bbnors-scripts` (phase 5), each guarded by
the existing tests.

## 8. Open decisions for the owner

1. Approve the three-layer framing and ADR-13 to ADR-17 (or amend them).
2. Module names: `bbnors-osrs-data`, `bbnors-osrs-sim`, `bbnors-scripts`, `bbnors-testkit`, and
   whether to keep `bbnors-api` as the framework module's name (recommended: keep it, to avoid churn).
3. Data sourcing policy for definitions (self-authored only, or licensed sources).
4. How far combat fidelity should go (simplified formulas vs closer models).
