# Glyphworks – Copilot Instructions

Glyphworks is a **Hytale server plugin** that implements a typed, extensible grid network system. Any block can become a node in a named grid (Item, Fluid, …), and multiple independent grid types can coexist in the same world.

## Build & Run

```bash
./gradlew build        # compile + jar
./gradlew runServer    # start local dev server (exit 1 = server crash, check logs)
./gradlew jar          # artifact only
```

`build.gradle.kts` is intentionally empty — all deps and repo config are injected by the `dev.scaffoldit` Gradle plugin. Do **not** add raw Maven dependencies without checking scaffoldit compatibility first.

## Source Layout

```
src/main/java/dev/drav/glyphworks/
  GlyphworksPlugin.java          — entry point; owns module lifecycle, exposes stable public API
  GlyphworksModule.java          — base class for all domain modules (setup / start / setupTests)
  grid/     — core grid infrastructure (component, graph, events, lookup, systems, type registry)
  fluid/    — FluidGridTypeHandler + FluidContainerComponent / FluidPipeComponent + systems
  crafting/ — AutoCraftingBench + ItemContainerBlock ECS components + systems
  transfer/ — GridTypeHandler implementations (ItemGridTypeHandler, FluidGridTypeHandler)
  test/     — in-game test framework; /glyphworks:test command; no JUnit
src/main/resources/   — Hytale asset pack (manifest.json, Common/, Server/)
generate-pipe-template.js  — pre-generates the 63 pipe .blockymodel files; re-run when adding pipe states
```

## Architecture

### ECS + Module pattern
All block/entity state lives in `Component<ChunkStore>` or `Component<EntityStore>` — never in plain maps or singletons. Each domain (`grid`, `fluid`, `crafting`, `transfer`, `test`) is a `GlyphworksModule` subclass that registers its own component types, systems, and event handlers. `GlyphworksPlugin` delegates to modules and re-exposes a stable public API.

### Grid network pipeline
```
Block placed/broken/chunk loaded
  → BlockChangeGridSystem / PlaceGridBlockEvent / BreakGridBlockEvent
      → GridLookup.resolve(pos)  [handles multi-block filler cells transparently]
      → GridGraph.addNode / addEdge / removeNode / removeEdge
      → GridComponent.neighbors updated (persisted)
Every tick: GridSystem (EntityTickingSystem)
  → GridTypeHandlerRegistry.get(gridType.id()) → handler.tick(...)
  → BFS from source faces → sink faces, respecting transferRate accumulator
```

### Adding a new grid type (e.g. Energy)
1. `GridTypeRegistry.register(GridType.of("Energy"))` in a new module
2. Implement `GridTypeHandler` → register in `GridTypeHandlerRegistry`
3. Add block JSONs with `"GridComponent_Type": "Energy"` and configure `FacePlane`s

## Serialization Convention: `BuilderCodec`

Every serializable component defines:
```java
public static final BuilderCodec<MyComponent> CODEC = BuilderCodec
    .append("Glyphworks_MyComponent_Field1", Codec.STRING, MyComponent::getField1)
    .add("Glyphworks_MyComponent_Field2", Codec.INTEGER, MyComponent::getField2)
    .build(...);
```
**JSON key format is always `Glyphworks_ClassName_FieldName`** — no camelCase, no abbreviations. Follow this exact pattern for any new component.

> **Warning — `KeyedCodec` identifier uniqueness**: Every identifier string passed to `BuilderCodec` must start with an **uppercase letter** and be **globally unique across the entire mod** — not just within the component. Identifiers from different components must never clash. Collisions cause silent serialization corruption. Always use the full `Glyphworks_ClassName_FieldName` pattern to stay safe. Existing identifiers use the bare `ClassName_FieldName` form and are being migrated incrementally — do not rename them unless explicitly asked.

## Block & Item Naming

All **new** blocks and items must be namespaced with the prefix `Glyphworks_<Module>` to avoid collisions with other mods. Use `PascalCase` for each segment:

```
Glyphworks_Fluid_Tank
Glyphworks_Fluid_Pipe
Glyphworks_Grid_Inserter
```

This applies to item JSON filenames, block type keys, and display names. Existing blocks are being migrated incrementally — do not rename them unless explicitly asked.

## Resource JSON Schema

Block entity JSON structure (see [src/main/resources/Server/Item/Items/Glyphworks/Fluid/Tank.json](../src/main/resources/Server/Item/Items/Glyphworks/Fluid/Glyphworks_Fluid_Tank.json) as a reference):
```json
{
  "BlockType": {
    "Material": "Solid",
    "DrawType": "Model",
    "VariantRotation": "...",
    "HitboxType": "...",
    "CustomModel": "...",
    "BlockEntity": {
      "Components": {
        "GridComponent": {
          "GridComponent_Type": "Fluid",
          "GridComponent_TransferRate": 25,
          "GridComponent_Faces": [{
            "FacePlane_Position": {"X":0,"Y":0,"Z":0},
            "FacePlane_Normal": "Up",
            "FacePlane_Mode": "Bidirectional",
            "FacePlane_ContainerKey": "tank"
          }]
        }
      }
    }
  }
}
```
- All component keys: `Glyphworks_ClassName_FieldName`
- `FacePlane_ContainerKey`: `null` for Extractor/Inserter (external-block reference); a named container key for storage blocks
- Face modes: `Input`, `Output`, `Bidirectional`, `Closed`

## Testing

There is **no JUnit**. All tests are in-game under `src/main/java/.../tests/` and run via
`/glyphworks:test` (alias `gw:test`) — see `TestCommand` for the full arg list.
`TestRunnerSystem` ticks test steps one per tick. Steps can use `Steps.wait(n)` or `Steps.waitUntil(predicate, max, desc)`. Each `TestCase` declares a bounding box that is cleared to `Empty` before the test runs. Register new suites in the module's `setupTests()` method.

## Commands

**Naming convention** — primary name is always `glyphworks:<name>`; a short alias `gw:<name>` is
registered via `addAliases()` in the constructor.

**Arg types** (Hytale command framework):

| Factory | Java type | In-game syntax | Use for |
|---|---|---|---|
| `withRequiredArg(name, ...)` | `RequiredArg<T>` | `<name>` | Mandatory positional args |
| `withOptionalArg(name, ...)` | `OptionalArg<T>` | `--name <value>` | Optional named args with a value |
| `withFlagArg(name, ...)` | `FlagArg` | `--name` | Boolean toggles |

Declare args in **most-specific → most-general** order so usage text reads naturally.
Example for a command with no positional args:

```java
public MyCommand() {
    super("glyphworks:foo", "Description");
    addAliases("gw:foo");
    // most-specific first
    this.itemArg    = withOptionalArg("item",   "Item ID to target", ArgTypes.STRING);
    this.suiteArg   = withOptionalArg("suite",  "Suite to run",      ArgTypes.STRING);
    this.moduleArg  = withOptionalArg("module", "Module to run",     ArgTypes.STRING);
    this.verboseArg = withFlagArg("verbose",    "Enable verbose output");
}
```

Registered in the owning module's `setup()` via `plugin.getCommandRegistry().registerCommand(new MyCommand())`.

## Known Pitfalls

- **`originPosition` is transient** — lost on restart, repopulated by `ChunkLoadGridGraphEvent`. A bug in that handler causes extractors/inserters to silently skip their tick.
- **Static registries are not thread-safe** — `GridTypeRegistry`, `GridTypeHandlerRegistry`, `TestRegistry` all use plain `HashMap`. Setup must remain single-threaded.
- **`GridGraph` is not thread-safe** — held in a `ConcurrentHashMap<UUID, ConcurrentHashMap<String, GridGraph>>`, but the `GridGraph` itself has no synchronization. Never touch it off the world thread.
- **Pipe models are pre-generated** — if `PipeConnectedBlockRuleSet.buildAllStateNames()` drifts from `generate-pipe-template.js`, the shape lookup silently fails (logs a warning). Re-run the JS script when changing pipe states.
- **`BlockChangeGridSystem` ordering** — must run `BEFORE ChunkSystems.ReplicateChanges`; if the ordering breaks, block changes are silently dropped from replication.

## Logging

Use `java.util.logging` throughout — `Logger.getLogger(Foo.class.getName())`. No SLF4J.

## Other Conventions

- Return defensive views from collections: `Collections.unmodifiableSet(...)`.
- Use copy constructors instead of `clone()`.
- Mark runtime-only fields `transient` and exclude them from the codec.
- Annotate all public API with `@Nonnull` / `@Nullable` (`javax.annotation`).
