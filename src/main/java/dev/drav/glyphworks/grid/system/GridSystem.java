package dev.drav.glyphworks.grid.system;

import java.util.logging.Logger;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.grid.component.GridComponent;
import dev.drav.glyphworks.grid.component.GridTypeEntry;
import dev.drav.glyphworks.grid.type.GridType;
import dev.drav.glyphworks.grid.type.GridTypeHandler;
import dev.drav.glyphworks.grid.type.GridTypeHandlerRegistry;

/**
 * Per-tick system for grid nodes.
 *
 * <p>
 * Reads each node's {@link GridType} and delegates to the matching
 * {@link GridTypeHandler} registered in {@link GridTypeHandlerRegistry}.
 * Nodes whose type has no registered handler are skipped with a warning.
 */
public final class GridSystem extends EntityTickingSystem<ChunkStore> {

    private static final Logger LOGGER = Logger.getLogger(GridSystem.class.getName());

    @Override
    public Query<ChunkStore> getQuery() {
        return GridComponent.getComponentType();
    }

    @Override
    public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<ChunkStore> archetypeChunk,
            @Nonnull Store<ChunkStore> store,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer) {

        GridComponent component = archetypeChunk.getComponent(index, GridComponent.getComponentType());
        if (component == null)
            return;

        if (component.getEntries().isEmpty())
            return;

        Ref<ChunkStore> blockRef = archetypeChunk.getReferenceTo(index);
        ChunkStore chunkStore = commandBuffer.getExternalData();

        for (GridTypeEntry entry : component.getEntries()) {
            GridType gridType = entry.getGridType();
            if (gridType == null)
                continue;

            GridTypeHandler handler = GridTypeHandlerRegistry.get(gridType.id());
            if (handler == null) {
                LOGGER.warning("[GridSystem] No handler registered for grid type: \"" + gridType.id() + "\"");
                continue;
            }

            handler.tick(dt, index, archetypeChunk, store, commandBuffer, chunkStore, component, entry, blockRef);
        }
    }
}
