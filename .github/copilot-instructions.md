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

- **Plugin root** — `src/main/java/dev/drav/glyphworks/` contains `GlyphworksPlugin.java` (entry point) and `GlyphworksModule.java` (base class for all domain modules).
- **One package per domain** — each module gets its own package (e.g., `grid/`, `fluid/`, `item/`, `crafting/`). Components, systems, events, and tests for that domain all live inside its package.
- **`test/`** — in-game test framework and runner. No JUnit.
- **`src/main/resources/`** — Hytale asset pack (`manifest.json`, `Common/`, `Server/`).
- **`generate-pipe-template.js`** — pre-generates pipe `.blockymodel` files. Re-run when adding pipe states.

## Asset Layout

All assets are **namespaced by module**. The two asset roots follow the same `Glyphworks/<Module>/<BlockName>/` convention:

- **Block models** — `Common/Blocks/Glyphworks/<Module>/<BlockName>/` containing `Default.blockymodel` and `Default.png`.
- **Item JSONs** — `Server/Item/Items/Glyphworks/<Module>/` containing one `.json` per block or item.

**Rules:**
- Every block must have its **own** model folder — never share a folder between two blocks.
- When creating a new block, create the model folder with placeholder `Default.blockymodel` + `Default.png`, then reference them in the item JSON.

## Architecture

### ECS + Module pattern
All block/entity state lives in `Component<ChunkStore>` or `Component<EntityStore>` — never in plain maps or singletons. Each domain (`grid`, `fluid`, `crafting`, `transfer`, `test`) is a `GlyphworksModule` subclass that registers its own component types, systems, and event handlers. `GlyphworksPlugin` delegates to modules and exposes module accessors — component types are accessed through their owning module, not the plugin root:
```java
GlyphworksPlugin.get().getFluidModule().getFluidContainerComponentType();
```

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

## Block & Item Naming

All **new** blocks and items must be namespaced with the prefix `Glyphworks_<Module>` to avoid collisions with other mods. Use `PascalCase` for each segment:

```
Glyphworks_Fluid_Tank
Glyphworks_Fluid_Pipe
Glyphworks_Grid_Inserter
```

This applies to item JSON filenames, block type keys, and display names. Existing blocks are being migrated incrementally — do not rename them unless explicitly asked.

## Known Pitfalls

- **`originPosition` is transient** — lost on restart, repopulated by `ChunkLoadGridGraphEvent`. A bug in that handler causes extractors/inserters to silently skip their tick.
- **Static registries are not thread-safe** — `GridTypeRegistry`, `GridTypeHandlerRegistry`, `TestRegistry` all use plain `HashMap`. Setup must remain single-threaded.
- **`GridGraph` is not thread-safe** — held in a `ConcurrentHashMap<UUID, ConcurrentHashMap<String, GridGraph>>`, but the `GridGraph` itself has no synchronization. Never touch it off the world thread.
- **Pipe models are pre-generated** — if `PipeConnectedBlockRuleSet.buildAllStateNames()` drifts from `generate-pipe-template.js`, the shape lookup silently fails (logs a warning). Re-run the JS script when changing pipe states.
- **`BlockChangeGridSystem` ordering** — must run `BEFORE ChunkSystems.ReplicateChanges`; if the ordering breaks, block changes are silently dropped from replication.

## Logging

Use `java.util.logging` throughout — `Logger.getLogger(Foo.class.getName())`. No SLF4J.
