---
description: "Agent coding instructions — Threading and World Thread Safety. Apply these rules when writing or reviewing code."
applyTo: "**/*.java"
---

# Threading and World Thread Safety

Apply these principles consistently when writing, reviewing, or suggesting code changes.

## Core Rules

### The world thread owns all Store, Ref, and CommandBuffer access
- `ChunkStore`, `EntityStore`, `Ref`, `Holder`, and `CommandBuffer` are bound to a specific world thread.
- Never read or write a `Ref` from a thread other than the world thread that owns it.
- Systems (`EntityTickingSystem`, `TickingSystem`, etc.) always run on the world thread — their tick methods are safe by default.

### Use CommandBuffer for deferred mutations inside event handlers
- Event handlers often fire in the middle of an iteration that the ECS is managing.
- Mutating the store directly (adding/removing components, destroying entities) inside an event handler can corrupt iteration state.
- Use `commandBuffer.run(() -> ...)` to schedule the mutation for after the current event dispatch completes.

### Use world.execute() to schedule work from off-thread contexts
- `AbstractAsyncCommand` runs its `executeAsync` method on a background thread for long-running work.
- From that background thread, call `world.execute(() -> ...)` to push store mutations back to the world thread.
- Never touch any `Ref`, `Store`, or `CommandBuffer` directly inside `executeAsync`.

### AbstractPlayerCommand and system ticks are already on the world thread
- `AbstractPlayerCommand.execute(...)` runs on the world thread — direct `Ref` and store access is safe.
- All system `tick(...)` methods run on the world thread — no extra scheduling needed.
- Do not wrap world-thread code in `world.execute()` — it adds unnecessary indirection.

### Static registries must be populated single-threaded during setup
- `GridTypeRegistry`, `GridTypeHandlerRegistry`, `TestRegistry`, and similar static `HashMap`-based registries have no internal synchronization.
- Populate them exclusively inside `setup()` or `start()`, which run on the main thread before any world ticks.
- Never register into a static registry from a system tick or event handler at runtime.

### Do not add your own locks around Store operations
- `Store` implementations use internal `StampedLock` for read/write access.
- Adding external `synchronized` blocks or `ReentrantLock`s around store calls risks deadlock and provides no benefit.
- Trust the engine's internal locking — if you need coordination, use `CommandBuffer` or `world.execute()`.

### Keep async command work purely computational
- `executeAsync` is for CPU-bound or I/O-bound work that should not block the world thread (file reads, HTTP calls, heavy computation).
- Collect results in local variables, then push a single `world.execute()` call to apply them.
- Never hold references to mutable world state across the async boundary.

## Code Examples

### Async command with world.execute() callback

```java
public class ImportCommand extends AbstractAsyncCommand {

    @Override
    protected void executeAsync(CommandContext context) {
        String data = fetchFromExternalService();
        World world = context.getPlayer().getWorld();

        world.execute(() -> {
            applyImportedData(world, data);
            context.getPlayer().sendMessage("Import complete.");
        });
    }
}
```

### Deferred mutation in an event handler

```java
public class BreakGridBlockEvent extends EntityEventSystem<ChunkStore, BlockBreakEvent> {

    @Override
    protected void onEvent(CommandBuffer<ChunkStore> commandBuffer, Ref<ChunkStore> ref,
                           BlockBreakEvent event) {
        GridComponent grid = ref.get(gridComponentType);
        if (grid == null) return;

        commandBuffer.run(() -> {
            graph.removeNode(grid.getGridId(), ref);
        });
    }
}
```

### Wrong — accessing Ref from a background thread

```java
// BROKEN — ref is bound to the world thread
protected void executeAsync(CommandContext context) {
    Ref<EntityStore> ref = context.getPlayer().getRef();
    InventoryComponent inv = ref.get(inventoryType);  // unsafe!
    inv.addItem(item);                                  // unsafe!
}
```

### Correct — read on world thread, compute off-thread, apply on world thread

```java
protected void execute(World world, Player player) {
    InventoryComponent snapshot = player.getRef().get(inventoryType).copy();

    CompletableFuture.supplyAsync(() -> computeRewards(snapshot))
        .thenAccept(rewards -> {
            world.execute(() -> applyRewards(player.getRef(), rewards));
        });
}
```

### Wrong — registering into a static registry at runtime

```java
// BROKEN — called from a system tick, races with other threads
@Override
public void tick(World world, Ref<EntityStore> ref, MyComponent comp) {
    if (!registered) {
        GridTypeRegistry.register(GridType.of("Dynamic"));  // unsafe!
        registered = true;
    }
}
```

### Correct — register during setup

```java
@Override
public void setup(GlyphworksPlugin plugin) {
    GridTypeRegistry.register(GridType.of("Energy"));
}
```
