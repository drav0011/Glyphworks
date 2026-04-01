package dev.drav.glyphworks.grid.type;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.grid.component.GridComponent;
import dev.drav.glyphworks.grid.component.GridTypeEntry;

/**
 * Handles per-tick behaviour for a specific {@link GridType}.
 *
 * <p>
 * Implementations are registered via {@link GridTypeHandlerRegistry} at plugin
 * init. Each tick {@link dev.drav.glyphworks.grid.system.GridSystem} resolves
 * the handler for the node's grid type and delegates to it.
 *
 * <p>
 * The {@code blockRef} argument is the entity {@link Ref} for the current
 * archetype chunk entry — equivalent to
 * {@code archetypeChunk.getReferenceTo(index)} — pre-computed by the system so
 * handlers do not have to extract it themselves.
 */
public interface GridTypeHandler {

    /** Stable ID must match the corresponding {@link GridType#id()}. */
    String typeId();

    /**
     * Called once per tick for every {@link GridComponent} node whose
     * {@link GridType} matches {@link #typeId()}.
     *
     * @param dt             tick delta time in seconds
     * @param index          archetype chunk slot index for this entity
     * @param archetypeChunk the chunk containing this entity
     * @param store          the owning {@link ChunkStore} store
     * @param commandBuffer  deferred command buffer for this tick
     * @param chunkStore     world-level chunk storage; required for position-based
     *                       lookups via
     *                       {@link dev.drav.glyphworks.grid.lookup.GridLookup}
     * @param component      the {@link GridComponent} attached to this block
     * @param entry          the {@link GridTypeEntry} for {@link #typeId()} on this
     *                       block
     * @param blockRef       entity reference for this block
     */
    void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<ChunkStore> archetypeChunk,
            @Nonnull Store<ChunkStore> store,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer,
            @Nonnull ChunkStore chunkStore,
            @Nonnull GridComponent component,
            @Nonnull GridTypeEntry entry,
            @Nonnull Ref<ChunkStore> blockRef);
}
