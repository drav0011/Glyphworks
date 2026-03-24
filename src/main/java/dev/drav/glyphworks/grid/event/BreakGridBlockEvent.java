package dev.drav.glyphworks.grid.event;

import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import dev.drav.glyphworks.GlyphworksPlugin;
import dev.drav.glyphworks.grid.component.GridComponent;
import dev.drav.glyphworks.grid.graph.GridGraph;
import dev.drav.glyphworks.grid.lookup.GridLookup;
import dev.drav.glyphworks.grid.util.GridFaceUtil;

public final class BreakGridBlockEvent extends EntityEventSystem<EntityStore, BreakBlockEvent> {

    public BreakGridBlockEvent() {
        super(BreakBlockEvent.class);
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
        disconnectBlock(world, pos);
        // Snapshot neighbour positions for deferred visual update — at commandBuffer.run()
        // time the block entity is already gone, so GridLookup returns null there and
        // PipeConnectedBlockRuleSet computes the correct disconnected visual state.
        ChunkStore chunkStore = world.getChunkStore();
        GridLookup lookup = GridLookup.resolve(chunkStore, pos);
        // lookup is now null (block removed), so we read neighbors from component
        // before calling disconnectBlock removed them; capture via the graph instead.
        commandBuffer.run(_ -> {
            // Visual updates already triggered inside disconnectBlock for neighbours.
        });
    }

    /**
     * Removes all grid connections for the block at {@code pos} and updates the
     * runtime {@link GridGraph}.
     *
     * <p>Safe to call directly (e.g. from the block-change polling system) when
     * {@code world.setBlock(x, y, z, "Empty")} is used instead of a player breaking
     * event, which may not dispatch {@link BreakBlockEvent}. Idempotent — calling it
     * for a position that has no grid block is a no-op.
     */
    public static void disconnectBlock(@Nonnull World world, @Nonnull Vector3i pos) {
        ChunkStore chunkStore = world.getChunkStore();

        GridLookup lookup = GridLookup.resolve(chunkStore, pos);
        if (lookup == null)
            return;

        GridComponent component = lookup.component();
        if (component.getGridType() == null)
            return;

        // Snapshot neighbors before removing node (removeNode wipes edges).
        Set<Vector3i> neighbors = new HashSet<>(component.getNeighbors());

        GridGraph graph = GlyphworksPlugin.get().getGridGraph(world, component.getGridType());
        if (graph != null) {
            graph.removeNode(pos);
        }

        // Remove this position from each surviving neighbor's persisted neighbor set
        // and trigger their visual update.
        for (Vector3i neighborPos : neighbors) {
            GridLookup neighborLookup = GridLookup.resolve(chunkStore, neighborPos);
            if (neighborLookup == null)
                continue;
            neighborLookup.component().removeNeighbor(pos);
            GridFaceUtil.forceConnectedBlockUpdate(world, neighborPos);
        }
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }
}
