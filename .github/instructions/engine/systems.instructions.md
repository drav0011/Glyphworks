---
description: "Agent coding instructions — ECS Systems. Apply these rules when writing or reviewing Hytale plugin code."
applyTo: "**/*.java"
---

# ECS Systems

Apply these principles consistently when writing, reviewing, or suggesting code changes.

## Core Rules

### Pick the right system base class
- `EntityTickingSystem<T>` — runs every tick, iterates each entity matching the query. Use for per-entity game logic (damage, transfer, movement). This is the most common system type.
- `TickingSystem<T>` — runs once per tick globally, no per-entity iteration. Use for world-wide updates or logic not tied to individual entities.
- `DelayedEntitySystem<T>` — like `EntityTickingSystem` but with a built-in interval. Pass the delay in seconds to the constructor. Use for periodic checks (regeneration, decay).
- `RefSystem<T>` — reacts to entity add/remove lifecycle events. Override `onEntityAdded` and `onEntityRemove`. Use for initialization when an entity first appears.
- `RefChangeSystem<T, C>` — reacts to a specific component being added, updated, or removed. Override `onComponentAdded`, `onComponentSet`, `onComponentRemoved`. Use for caching, permission sync, or side effects triggered by state changes.
- `EntityEventSystem<T, E>` — handles a specific ECS event type. Use for block placement, crafting, and other discrete game events.

### Define precise queries
- Override `getQuery()` to return the narrowest filter that matches your system's needs.
- `Query.and(componentType1, componentType2)` — entities must have all listed components.
- `Query.not(componentType)` — exclude entities with this component.
- `Query.or(componentType1, componentType2)` — entities with any of the listed components.
- `Query.any()` — matches all entities (use sparingly, typically only in event systems).
- A single `ComponentType` is itself a valid query (implicit `and` with one element).

### Never modify the Store directly from a system
- Use the `CommandBuffer` parameter to add, remove, or replace components. Changes are applied at the tick boundary.
- For world mutations (setting blocks, spawning entities), use `world.execute(() -> { ... })` to schedule work on the world thread.
- Direct Store modifications during iteration cause concurrent modification and undefined behavior.

### Declare system ordering explicitly
- Override `getDependencies()` to return ordering constraints when your system interacts with another.
- `new SystemDependency<>(Order.BEFORE, OtherSystem.class)` — this system runs before `OtherSystem`.
- `new SystemDependency<>(Order.AFTER, OtherSystem.class)` — this system runs after `OtherSystem`.
- `new SystemGroupDependency<>(Order.AFTER, someGroup)` — run after an entire group finishes.
- Override `getGroup()` to assign the system to a `SystemGroup` for logical grouping.
- If ordering is wrong, bugs are silent — block changes get dropped, damage applies in the wrong order.

### Register systems during start, not setup
- Systems are registered via `plugin.getChunkStoreRegistry().registerSystem(new MySystem())` or the `EntityStore` equivalent.
- Registration must happen in the plugin's `start()` method, after all components have been registered in `setup()`.
- Registering a system that queries an unregistered component causes a `NullPointerException`.

### Block ticking requires a specific pattern
- Block systems use `EntityTickingSystem<ChunkStore>` with a query on `BlockSection` and `ChunkSection`.
- Use `blockSection.forEachTicking(blockComponentChunk, commandBuffer, sectionY, callback)` to iterate only blocks marked as ticking.
- Return `BlockTickStrategy.CONTINUE` to keep the block ticking next tick, `BlockTickStrategy.IGNORED` to skip.
- Mark blocks as ticking via `worldChunk.setTicking(x, y, z, true)` in a `RefSystem` when the block entity is first created.
- Convert local chunk coordinates to world coordinates: `globalX = localX + (worldChunk.getX() * 32)`.

### Keep tick methods focused
- The `tick` method should orchestrate, not implement. Delegate complex logic to helper methods or handler objects.
- Access components via `archetypeChunk.getComponent(index, componentType)` — null-check the result.
- Get the entity reference via `archetypeChunk.getReferenceTo(index)` when you need to issue `CommandBuffer` operations.

## Code Examples

### EntityTickingSystem — per-entity processing

```java
public class PoisonSystem extends EntityTickingSystem<EntityStore> {

    private final ComponentType<EntityStore, PoisonComponent> poisonType;

    public PoisonSystem(ComponentType<EntityStore, PoisonComponent> poisonType) {
        this.poisonType = poisonType;
    }

    @Override
    public void tick(float dt, int index,
            @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        PoisonComponent poison = chunk.getComponent(index, poisonType);
        Ref<EntityStore> ref = chunk.getReferenceTo(index);

        poison.addElapsedTime(dt);
        if (poison.getElapsedTime() >= poison.getTickInterval()) {
            poison.resetElapsedTime();
            Damage damage = new Damage(Damage.NULL_SOURCE, DamageCause.OUT_OF_WORLD, poison.getDamagePerTick());
            DamageSystems.executeDamage(ref, commandBuffer, damage);
            poison.decrementRemainingTicks();
        }

        if (poison.isExpired()) {
            commandBuffer.removeComponent(ref, poisonType);
        }
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(poisonType);
    }

    @Nullable
    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getGatherDamageGroup();
    }
}
```

### RefChangeSystem — reacting to component lifecycle

```java
public class PermissionSync extends RefChangeSystem<EntityStore, PermissionAttachment> {

    @Nonnull
    @Override
    public ComponentType<EntityStore, PermissionAttachment> componentType() {
        return EntityStoreRegistry.get().getPermissionAttachmentComponentType();
    }

    @Override
    public void onComponentAdded(@Nonnull Ref<EntityStore> ref,
            @Nonnull PermissionAttachment attachment,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        UUIDComponent uuid = store.getComponent(ref, UUIDComponent.getComponentType());
        cachePermissions(uuid.getUuid(), attachment);
    }

    @Override
    public void onComponentRemoved(@Nonnull Ref<EntityStore> ref,
            @Nonnull PermissionAttachment attachment,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        UUIDComponent uuid = store.getComponent(ref, UUIDComponent.getComponentType());
        evictPermissions(uuid.getUuid());
    }
}
```

### System ordering via dependencies

```java
@Override
public Set<Dependency<ChunkStore>> getDependencies() {
    return Set.of(
        new SystemDependency<>(Order.BEFORE, ChunkSystems.ReplicateChanges.class)
    );
}
```

### Block ticking system pattern

```java
public class MyBlockSystem extends EntityTickingSystem<ChunkStore> {

    @Override
    public void tick(float dt, int index,
            @Nonnull ArchetypeChunk<ChunkStore> chunk,
            @Nonnull Store<ChunkStore> store,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer) {

        BlockSection blocks = chunk.getComponent(index, BlockSection.getComponentType());
        if (blocks == null || blocks.getTickingBlocksCountCopy() == 0)
            return;

        ChunkSection section = chunk.getComponent(index, ChunkSection.getComponentType());
        BlockComponentChunk bcc = commandBuffer.getComponent(
                section.getChunkColumnReference(), BlockComponentChunk.getComponentType());

        blocks.forEachTicking(bcc, commandBuffer, section.getY(),
                (blockCompChunk, cb, localX, localY, localZ, blockId) -> {
                    Ref<ChunkStore> blockRef = blockCompChunk.getEntityReference(
                            ChunkUtil.indexBlockInColumn(localX, localY, localZ));
                    if (blockRef == null)
                        return BlockTickStrategy.IGNORED;

                    // Process the block...
                    return BlockTickStrategy.CONTINUE;
                });
    }

    @Nonnull
    @Override
    public Query<ChunkStore> getQuery() {
        return Query.and(BlockSection.getComponentType(), ChunkSection.getComponentType());
    }
}
```

### Scheduling world mutations from a system

**Avoid:**
```java
// Direct world mutation from inside tick — unsafe
world.setBlock(x, y, z, "Rock_Ice");
```

**Prefer:**
```java
// Schedule on the world thread
world.execute(() -> {
    world.setBlock(x, y, z, "Rock_Ice");
});
```
