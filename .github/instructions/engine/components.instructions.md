---
description: "Agent coding instructions — ECS Components. Apply these rules when writing or reviewing Hytale plugin code."
applyTo: "**/*.java"
---

# ECS Components

Apply these principles consistently when writing, reviewing, or suggesting code changes.

## Core Rules

### Choose the correct store type
- Use `Component<ChunkStore>` for block-attached state — data tied to a position in the world (block entities, block metadata, grid nodes).
- Use `Component<EntityStore>` for entity-attached state — data tied to a living entity, player, NPC, or spawned object.
- Never mix store types. A single component class implements exactly one of the two.

### Components are pure data containers
- Components hold state but contain no business logic. No tick methods, no side effects, no event dispatch.
- Behavior belongs in systems. If a component method does more than get/set/derive a value from its own fields, move that logic to a system.
- Utility methods that compute a value from the component's own fields (e.g., `isEmpty()`, `isFull()`) are acceptable.

### Every serialized component needs a complete structure
- Provide a no-arg constructor — the codec factory calls it during deserialization.
- Provide a copy constructor that deep-copies all mutable fields (collections, nested objects).
- Implement `clone()` by delegating to the copy constructor.
- Define a `public static final BuilderCodec<MyComponent> CODEC` field for serialization.
- Provide a `public static ComponentType<StoreType, MyComponent> getComponentType()` that delegates to the plugin instance.

### Mark runtime-only fields as transient
- Fields that are reconstructed at load time (caches, resolved references, computed state) must be `transient`.
- Transient fields are excluded from codec serialization. Repopulate them in event handlers (e.g., chunk load events).

### Never store direct entity references
- Always use `Ref<ChunkStore>` or `Ref<EntityStore>` to reference other entities.
- Direct object references become dangling pointers when the ECS relocates or removes entities.
- Validate a `Ref` before use — entities can be removed between ticks.

### Registration happens during setup
- Register components in the plugin's `setup()` method via `plugin.getChunkStoreRegistry().registerComponent(Class, name, CODEC)` or the `EntityStore` equivalent.
- Store the returned `ComponentType` handle as a field on the plugin or module — systems and other code need it to access components.
- Never register components during `start()` or at runtime.

### Understand persistence semantics
- `store.putComponent(ref, componentType, instance)` persists the component — it survives world save/load and player reconnect.
- `store.addComponent(ref, componentType, instance)` is transient — the component is removed when the entity leaves the world.
- Use `putComponent` for player data and block entity state that must survive restarts.
- Use `addComponent` for temporary runtime markers (e.g., "currently being processed").

### Return defensive copies from collections
- If a component exposes a `Set`, `List`, or `Map`, return an unmodifiable view via `Collections.unmodifiableSet(...)` or similar.
- Callers that need to mutate the collection should go through explicit setter methods on the component.

## Code Examples

### Correct ChunkStore component structure

```java
public class FluidContainerComponent implements Component<ChunkStore> {

    public static final BuilderCodec<FluidContainerComponent> CODEC = BuilderCodec
            .builder(FluidContainerComponent.class, FluidContainerComponent::new)
            .append(
                    new KeyedCodec<>("MyMod_FluidContainer_Capacity", Codec.INTEGER),
                    (c, v) -> c.capacity = v,
                    c -> c.capacity)
            .add()
            .append(
                    new KeyedCodec<>("MyMod_FluidContainer_Amount", Codec.INTEGER),
                    (c, v) -> c.amount = v,
                    c -> c.amount)
            .add()
            .build();

    public static ComponentType<ChunkStore, FluidContainerComponent> getComponentType() {
        return MyPlugin.get().getFluidModule().getFluidContainerComponentType();
    }

    private int capacity;
    private int amount;

    public FluidContainerComponent() {
        this.capacity = 0;
        this.amount = 0;
    }

    public FluidContainerComponent(FluidContainerComponent other) {
        this.capacity = other.capacity;
        this.amount = other.amount;
    }

    @Nullable
    @Override
    public Component<ChunkStore> clone() {
        return new FluidContainerComponent(this);
    }

    public boolean isFull() {
        return amount >= capacity;
    }
}
```

### Correct EntityStore component structure

```java
public class PoisonComponent implements Component<EntityStore> {

    public static final BuilderCodec<PoisonComponent> CODEC = BuilderCodec
            .builder(PoisonComponent.class, PoisonComponent::new)
            .append(
                    new KeyedCodec<>("MyMod_Poison_DamagePerTick", Codec.FLOAT),
                    (c, v) -> c.damagePerTick = v,
                    c -> c.damagePerTick)
            .add()
            .append(
                    new KeyedCodec<>("MyMod_Poison_RemainingTicks", Codec.INTEGER),
                    (c, v) -> c.remainingTicks = v,
                    c -> c.remainingTicks)
            .add()
            .build();

    private float damagePerTick;
    private int remainingTicks;

    public PoisonComponent() {
        this(5f, 10);
    }

    public PoisonComponent(float damagePerTick, int remainingTicks) {
        this.damagePerTick = damagePerTick;
        this.remainingTicks = remainingTicks;
    }

    public PoisonComponent(PoisonComponent other) {
        this.damagePerTick = other.damagePerTick;
        this.remainingTicks = other.remainingTicks;
    }

    @Nullable
    @Override
    public Component<EntityStore> clone() {
        return new PoisonComponent(this);
    }
}
```

### Using transient fields for runtime-only state

```java
public class GridComponent implements Component<ChunkStore> {

    // Serialized — survives save/load
    private Set<GridTypeEntry> entries;

    // Runtime-only — repopulated on chunk load
    @Nullable
    private transient Vector3i originPosition;

    public void setOriginPosition(@Nonnull Vector3i pos) {
        this.originPosition = pos;
    }
}
```

### Accessing components via Ref

**Avoid:**
```java
// Direct reference — will break when entity is relocated
private Player cachedPlayer;
```

**Prefer:**
```java
// Safe handle — survives entity relocation
private Ref<EntityStore> playerRef;

// Access via store when needed
Player player = store.getComponent(playerRef, Player.getComponentType());
```

### Persistence: putComponent vs addComponent

**Transient marker (removed on world unload):**
```java
store.addComponent(ref, myComponentType, new ProcessingMarker());
```

**Persistent data (survives restart):**
```java
store.putComponent(ref, playerDataType, new PlayerData());
```
