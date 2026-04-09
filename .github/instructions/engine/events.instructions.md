---
description: "Agent coding instructions — Events. Apply these rules when writing or reviewing Hytale plugin code."
applyTo: "**/*.java"
---

# Events

Apply these principles consistently when writing, reviewing, or suggesting code changes.

## Core Rules

### Hytale has two distinct event systems
- **Global EventBus** — for server-wide events not tied to ECS iteration (player connects, chat, etc.). Register via `plugin.getEventRegistry().registerGlobal(EventClass, handler)`.
- **ECS EntityEventSystem** — for events dispatched during ECS processing (block placed, recipe crafted, entity damaged). Extend `EntityEventSystem<StoreType, EventType>` and register as a system.
- Choose global for server/player lifecycle events. Choose ECS for events that need access to the `Store`, `CommandBuffer`, or happen within the ECS tick loop.

### Register global events with method references
- The handler is a static method or instance method reference: `ExampleEvent::onPlayerReady`.
- The method signature takes a single parameter — the event instance.
- Register during `setup()`: `this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, ExampleEvent::onPlayerReady)`.

### ECS event systems are registered as systems
- Extend `EntityEventSystem<StoreType, EventType>` and pass the event class to `super(EventClass.class)`.
- Override `handle(int index, ArchetypeChunk, Store, CommandBuffer, EventType event)` for the logic.
- Override `getQuery()` to filter which entities receive the event. Use `Archetype.empty()` or `Query.any()` if no filtering is needed.
- Register via `plugin.getEntityStoreRegistry().registerSystem(new MyEventSystem())` during `setup()`.

### Use Pre events for cancellation
- Many events have `.Pre` and `.Post` variants (e.g., `CraftRecipeEvent.Pre`, `CraftRecipeEvent.Post`).
- Only `.Pre` events support `event.setCancelled(true)` — this prevents the action from occurring.
- `.Post` events fire after the action is complete and cannot be cancelled.
- Use `.Pre` when you need to validate, block, or modify an action. Use `.Post` for reactions and side effects.

### Defer Store mutations with commandBuffer.run
- Inside event handlers, use `commandBuffer.run(cb -> { ... })` to schedule Store mutations that execute after the event dispatch completes.
- Direct Store modifications during event handling can corrupt the iteration state.

### Event priorities control execution order
- Priorities from lowest to highest: `LOWEST`, `LOW`, `NORMAL`, `HIGH`, `HIGHEST`.
- Lower-priority handlers run first; higher-priority handlers run last and can override earlier decisions.
- Default is `NORMAL`. Only override when your handler must run before or after others.

### Common events reference
- `PlayerReadyEvent` — player fully joined and spawned in world.
- `PlayerChatEvent` — player sends a chat message (cancellable).
- `PlaceBlockEvent` — player places a block (use for wiring grid connections).
- `BreakBlockEvent` — player breaks a block (use for cleanup).
- `CraftRecipeEvent.Pre` / `.Post` — recipe is about to be crafted / was crafted.
- `ChunkPreLoadProcessEvent` — chunk loaded from disk, before systems process it.
- `OnDeathSystem` events — entity death handling.

## Code Examples

### Global event handler

```java
public class WelcomeHandler {

    public static void onPlayerReady(PlayerReadyEvent event) {
        Player player = event.getPlayer();
        player.sendMessage(Message.raw("Welcome, " + player.getDisplayName()));
    }
}

// Registration in plugin setup():
this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, WelcomeHandler::onPlayerReady);
```

### ECS event system — cancelling a crafting recipe

```java
public class BlockFibreCrafting extends EntityEventSystem<EntityStore, CraftRecipeEvent.Pre> {

    public BlockFibreCrafting() {
        super(CraftRecipeEvent.Pre.class);
    }

    @Override
    public void handle(int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull CraftRecipeEvent.Pre event) {

        CraftingRecipe recipe = event.getCraftedRecipe();
        if (recipe.getInput() == null)
            return;

        for (MaterialQuantity mq : recipe.getInput()) {
            if (Objects.equals(mq.getItemId(), "Ingredient_Fibre")) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }
}
```

### ECS event system — reacting to block placement

```java
public class OnBlockPlaced extends EntityEventSystem<EntityStore, PlaceBlockEvent> {

    public OnBlockPlaced() {
        super(PlaceBlockEvent.class);
    }

    @Override
    public void handle(int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull PlaceBlockEvent event) {

        commandBuffer.run(cb -> {
            World world = cb.getExternalData().getWorld();
            Vector3i target = event.getTargetBlock();
            connectToGrid(world, target);
        });
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }
}
```

### Choosing the right event system

**Global — server lifecycle, no Store access needed:**
```java
this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, MyHandler::onReady);
```

**ECS — needs Store/CommandBuffer or happens during ECS tick:**
```java
this.getEntityStoreRegistry().registerSystem(new MyEventSystem());
```
