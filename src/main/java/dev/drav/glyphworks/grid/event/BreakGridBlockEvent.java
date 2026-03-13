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
        ChunkStore chunkStore = world.getChunkStore();

        GridLookup lookup = GridLookup.resolve(chunkStore, pos);
        if (lookup == null)
            return;

        GridComponent component = lookup.component();
        if (component.getGridType() == null)
            return;

        // Snapshot neighbors before we clear anything.
        Set<Vector3i> neighbors = component.getNeighbors();

        // Remove the node (and all its graph edges) first.
        GridGraph graph = GlyphworksPlugin.get().getGridGraph(world, component.getGridType());
        if (graph != null) {
            graph.removeNode(pos);
        }

        // Remove this position from each neighbor's persisted neighbor set.
        for (Vector3i neighborPos : neighbors) {
            GridLookup neighborLookup = GridLookup.resolve(chunkStore, neighborPos);
            if (neighborLookup == null)
                continue;
            neighborLookup.component().removeNeighbor(pos);
        }

        // Snapshot the neighbour positions for the deferred visual update.
        // We copy the set here (on the WorldThread) before the furnace entity is
        // destroyed, then apply the update in commandBuffer.run() which executes
        // after naturallyRemoveBlock() has removed the entity.  At that point,
        // GridLookup.resolve at the furnace position returns null, so
        // PipeConnectedBlockRuleSet correctly computes a disconnected state.
        Set<Vector3i> neighborSnapshot = new HashSet<>(neighbors);
        commandBuffer.run(_ -> {
            for (Vector3i n : neighborSnapshot) {
                GridFaceUtil.forceConnectedBlockUpdate(world, n);
            }
        });

    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }
}
