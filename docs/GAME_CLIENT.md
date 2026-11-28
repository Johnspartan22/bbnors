# The `GameClient` adapter interface

`org.bbnors.api.model.GameClient` is the only way game state enters $bbnors. Every manager,
query and script is written against it, so a new backend needs one class (plus entity views).
`org.bbnors.api.sim.SimulatedGame` is the complete reference implementation.

## Contract

| Area | Methods | Notes |
|---|---|---|
| Session | `loggedIn()`, `tick()` | `tick()` must increase monotonically; it keys the per-tick cache |
| Entities | `localPlayer()`, `npcs()`, `players()`, `objects()`, `groundItems()` | Lists may be snapshots; entities may be live views. `Entity.valid()` reports existence |
| Containers | `inventory()`, `inventoryCapacity()`, `equipment()`, `bankOpen()`, `bank()` | `Item.slot()` is the container slot |
| Interface | `widget(group, child)`, `menu()`, `layout()` | `MenuModel.hovered()` = the left-click action at the cursor |
| World | `collision()`, `tileToScreen()`, `tileToMinimap()`, `yawFacing()` | `null` = not visible |
| Camera | `camera()` | returns an engine `CameraModel`; rates/keys must be calibrated per backend |
| Extensions | `extension(Class<T>)` | optional capabilities without changing the core interface |

Thread safety: methods are called from script and operation threads; implementations must be
thread-safe. Caching is not needed: `GameState` fetches each list at most once per tick.

## Entities

Implement `Entity` (`id`, `name`, `tile`, `actions`, `screenBounds`, `valid`) and the marker
sub-interfaces (`Npc`, `Player`, `GameObject`, `GroundItem`). `actions()` is in menu order; the
first is the left-click default. `screenBounds()` is the clickable area or `null` off-screen. For
moving entities return live bounds: the mouse engine re-reads them after each movement and
corrects.

## Extending

```java
public interface Skills { int level(String skill); }

final class MyClient implements GameClient {
    @Override public <T> Optional<T> extension(Class<T> type) {
        return type == Skills.class ? Optional.of(type.cast(skills)) : Optional.empty();
    }
}

ctx.client().extension(Skills.class).ifPresent(s -> ...);
```

## Input side

Game state and input are independent. Input leaves through an `engine.input.InputBackend`:
`AwtComponentBackend` (events into a Java component you load), `RobotBackend` (OS input into a
window), or a backend of your own. The simulator implements both sides itself.

## Supported backends

| Backend | Status |
|---|---|
| `SimulatedGame` | complete; used by tests, examples, benchmarks |
| Official OSRS client adapter | in progress (learning experiment — see ARCHITECTURE §2) |
| Server bridge (state pushed by a plugin on a server you operate) | on hold until the server environment is identified and authorization to integrate is confirmed (ROADMAP P8) |
