---
description: "Agent coding instructions — Plugin and Module Lifecycle. Apply these rules when writing or reviewing Hytale plugin code."
applyTo: "**/*.java"
---

# Plugin and Module Lifecycle

Apply these principles consistently when writing, reviewing, or suggesting code changes.

## Core Rules

### Understand the JavaPlugin lifecycle
- The engine calls methods in strict order: constructor → `setup()` → `start()` → tick loop → shutdown.
- Breaking this order causes `NullPointerException` or silent registration failures.

### setup() is for registration, start() is for systems
- In `setup()`: register components, events, commands. Components must exist before any system references them.
- In `start()`: register systems. Systems require component types to be already registered for their queries.
- Never register components in `start()` or at runtime — queries that depend on them will fail.

### Use a static instance accessor for the plugin
- Store `instance = this` in the constructor or at the top of `setup()`.
- Expose via `public static MyPlugin get() { return instance; }`.
- Components and systems access `ComponentType` handles through this accessor.

### Store ComponentType handles on the module, not the plugin
- `registerComponent()` returns a `ComponentType<StoreType, T>` handle — store it as a field on the owning module.
- Expose each handle via a getter on the module: `fluidModule.getFluidContainerComponentType()`.
- The plugin exposes modules via getters (`getFluidModule()`, `getItemModule()`, etc.) — never re-expose every component type on the plugin root.
- Systems and event handlers retrieve handles through the plugin’s module accessor: `MyPlugin.get().getFluidModule().getTankType()`.

### Use the module pattern for domain separation
- Define a base module class with `setup(plugin)`, `start(plugin)`, and `setupTests()` hooks.
- Each domain (grid, fluid, item, crafting) gets its own module subclass.
- The plugin creates and orchestrates modules — iterating `setup`, then `start`, then `setupTests` in order.
- Modules own their `ComponentType` fields and expose them via getters.

### Declare manifest dependencies for load ordering
- In `manifest.json`, list the Hytale modules your plugin depends on:
  ```json
  "Dependencies": {
    "Hytale:EntityModule": "*",
    "Hytale:BlockModule": "*"
  }
  ```
- Missing dependencies cause `NullPointerException` when queries reference built-in component types.

### Prefer ChunkStoreRegistry for block components, EntityStoreRegistry for entity components
- `plugin.getChunkStoreRegistry()` — registers components and systems that operate on the block/chunk world.
- `plugin.getEntityStoreRegistry()` — registers components and systems that operate on spawned entities, players, NPCs.
- Using the wrong registry causes components to silently never appear on the entities you expect.

### Keep the plugin class thin
- The plugin class should only orchestrate module lifecycle and expose module accessors.
- Do not add per-component delegation getters on the plugin — that turns the plugin into a god object.
- Move registration logic into modules. Move command implementations into command classes. Move event handlers into their own classes.
- The plugin is the entry point, not the dumping ground.

## Code Examples

### Plugin orchestrating modules

```java
public class MyPlugin extends JavaPlugin {

    private static MyPlugin instance;
    private FluidModule fluidModule;
    private ItemModule itemModule;
    private List<MyModule> modules;

    public static MyPlugin get() {
        return instance;
    }

    public FluidModule getFluidModule() {
        return fluidModule;
    }

    public ItemModule getItemModule() {
        return itemModule;
    }

    public MyPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        instance = this;

        fluidModule = new FluidModule();
        itemModule = new ItemModule();
        modules = List.of(fluidModule, itemModule);

        for (MyModule module : modules) {
            module.setup(this);
        }

        this.getCommandRegistry().registerCommand(new MyCommand());
    }

    @Override
    protected void start() {
        for (MyModule module : modules) {
            module.start(this);
        }
    }
}
```

### Module with component and system registration

```java
public class FluidModule extends MyModule {

    private ComponentType<ChunkStore, FluidTankComponent> tankType;

    public ComponentType<ChunkStore, FluidTankComponent> getTankType() {
        return tankType;
    }

    @Override
    public void setup(@Nonnull MyPlugin plugin) {
        this.tankType = plugin.getChunkStoreRegistry().registerComponent(
                FluidTankComponent.class,
                "MyMod_FluidTankComponent",
                FluidTankComponent.CODEC);
    }

    @Override
    public void start(@Nonnull MyPlugin plugin) {
        plugin.getChunkStoreRegistry().registerSystem(new FluidTransferSystem());
    }
}
```

### Accessing a component type from another module

```java
// Correct — go through the module
ComponentType<ChunkStore, FluidTankComponent> tankType =
        MyPlugin.get().getFluidModule().getTankType();
```

### Wrong — plugin-level delegation getters

**Avoid:**
```java
// Every new component adds another method to the plugin — god object
public class MyPlugin extends JavaPlugin {
    public ComponentType<ChunkStore, FluidTankComponent> getTankType() {
        return fluidModule.getTankType();
    }
    public ComponentType<ChunkStore, FluidPipeComponent> getPipeType() {
        return fluidModule.getPipeType();
    }
    // ... grows forever
}
```

**Prefer:**
```java
// Plugin exposes modules only
public class MyPlugin extends JavaPlugin {
    public FluidModule getFluidModule() { return fluidModule; }
    public ItemModule getItemModule() { return itemModule; }
}
// Callers access components through the module
MyPlugin.get().getFluidModule().getTankType();
```

### Registration order matters

**Avoid:**
```java
@Override
protected void setup() {
    // System registered too early — tankType is null
    this.getChunkStoreRegistry().registerSystem(new FluidTransferSystem());
    this.tankType = this.getChunkStoreRegistry().registerComponent(...);
}
```

**Prefer:**
```java
@Override
protected void setup() {
    this.tankType = this.getChunkStoreRegistry().registerComponent(...);
}

@Override
protected void start() {
    this.getChunkStoreRegistry().registerSystem(new FluidTransferSystem());
}
```

### Wrong registry — silent failure

**Avoid:**
```java
// Block component registered on EntityStore — will never appear on block entities
plugin.getEntityStoreRegistry().registerComponent(
        MyBlockComponent.class, "MyBlockComponent", MyBlockComponent.CODEC);
```

**Prefer:**
```java
// Block components use ChunkStoreRegistry
plugin.getChunkStoreRegistry().registerComponent(
        MyBlockComponent.class, "MyBlockComponent", MyBlockComponent.CODEC);
```
