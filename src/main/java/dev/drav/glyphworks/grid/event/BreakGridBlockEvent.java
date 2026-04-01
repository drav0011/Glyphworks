package dev.drav.glyphworks.grid.event;

import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nonnull;

import org.joml.Vector3i;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import dev.drav.glyphworks.GlyphworksPlugin;
import dev.drav.glyphworks.grid.component.GridComponent;
import dev.drav.glyphworks.grid.component.GridTypeEntry;
import dev.drav.glyphworks.grid.graph.GridGraph;
import dev.drav.glyphworks.grid.lookup.GridLookup;

public final class BreakGridBlockEvent extends EntityEventSystem<EntityStore, BreakBlockEvent> {

    public BreakGridBlockEvent() {
        super(BreakBlockEvent.class);
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull BreakBlockEvent event) {
        Vector3i pos = event.getTargetBlock();
        World world = commandBuffer.getExternalData().getWorld();
        // Snapshot neighbours (union across all entries) before disconnectBlock wipes.
        ChunkStore chunkStore = world.getChunkStore();
        GridLookup lookup = GridLookup.resolve(chunkStore, pos);
        Set<Vector3i> survivors = new HashSet<>();
        if (lookup != null) {
            for (GridTypeEntry entry : lookup.component().getEntries()) {
                survivors.addAll(entry.getNeighbors());
            }
        }
        disconnectBlock(world, pos);
        // Deferred rebuild: by the time commandBuffer.run() fires the broken block's
        // entity is gone. Clearing each survivor's stale neighbor sets then re-running
        // connectBlock rebuilds them correctly from the live world.
        if (!survivors.isEmpty()) {
            commandBuffer.run(_ -> {
                for (Vector3i n : survivors) {
                    GridLookup nl = GridLookup.resolve(world.getChunkStore(), n);
                    if (nl == null)
                        continue;
                    for (GridTypeEntry e : nl.component().getEntries()) {
                        e.setNeighbors(new HashSet<>());
                    }
                    PlaceGridBlockEvent.connectBlock(world, n);
                }
            });
        }
    }

    /**
     * Removes all grid connections for the block at {@code pos} and updates the
     * runtime {@link GridGraph}.
     *
     * <p>
     * Safe to call directly (e.g. from the block-change polling system) when
     * {@code world.setBlock(x, y, z, "Empty")} is used instead of a player breaking
     * event, which may not dispatch {@link BreakBlockEvent}. Idempotent — calling
     * it
     * for a position that has no grid block is a no-op.
     */
    public static void disconnectBlock(@Nonnull World world, @Nonnull Vector3i pos) {
        ChunkStore chunkStore = world.getChunkStore();

        GridLookup lookup = GridLookup.resolve(chunkStore, pos);
        if (lookup == null)
            return;

        GridComponent component = lookup.component();
        if (component.getEntries().isEmpty())
            return;

        // For each grid type entry, remove this node from its graph and clean neighbors.
        for (GridTypeEntry entry : component.getEntries()) {
            if (entry.getGridType() == null)
                continue;

            // Snapshot neighbors before removing node (removeNode wipes edges).
            Set<Vector3i> neighbors = new HashSet<>(entry.getNeighbors());

            GridGraph graph = GlyphworksPlugin.get().getGridGraph(world, entry.getGridType());
            if (graph != null) {
                graph.removeNode(pos);
            }

            String typeId = entry.getGridType().id();
            // Remove this position from each surviving neighbor's persisted neighbor set.
            for (Vector3i neighborPos : neighbors) {
                GridLookup neighborLookup = GridLookup.resolve(chunkStore, neighborPos);
                if (neighborLookup == null)
                    continue;
                GridTypeEntry neighborEntry = neighborLookup.component().getEntry(typeId);
                if (neighborEntry != null) {
                    neighborEntry.removeNeighbor(pos);
                }
            }
        }
    }
}
