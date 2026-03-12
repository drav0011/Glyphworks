package dev.drav.glyphworks.transfer.event;

import java.util.Set;
import java.util.UUID;

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
import dev.drav.glyphworks.transfer.TransferGraph;
import dev.drav.glyphworks.transfer.lookups.TransferLookup;
import dev.drav.glyphworks.transfer.state.TransferStateRegistry;
import dev.drav.glyphworks.transfer.util.FaceLinkUtil;
import dev.drav.glyphworks.transfer.wiring.InventoryWiringRegistry;

public class BreakTransferableBlockEvent extends EntityEventSystem<EntityStore, BreakBlockEvent> {

    public BreakTransferableBlockEvent() {
        super(BreakBlockEvent.class);
    }

    @Override
    public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull BreakBlockEvent event
    ) {
        Vector3i pos = event.getTargetBlock();
        World world = commandBuffer.getExternalData().getWorld();
        ChunkStore chunkStore = world.getChunkStore();

        TransferLookup lookup = TransferLookup.resolve(chunkStore, pos);
        if (lookup == null) {
            // A non-transfer block (e.g. a chest) was broken. Collect its occupied cells
            // now (before the block is removed) then defer the re-wire so neighbouring IO
            // blocks lose their stale inventory reference once the block is gone.
            Set<Vector3i> occupied = InventoryWiringRegistry.collectOccupiedCells(pos, chunkStore);
            commandBuffer.run(_ -> InventoryWiringRegistry.rewireOccupied(occupied, chunkStore));
            return;
        }

        // Remove the node from the graph first (while neighborNodeIds are still set),
        // so edges are cleaned up on neighbor entries before unlinkAll clears face data.
        TransferGraph graph = GlyphworksPlugin.get().getGraph(world);
        if (graph != null) {
            // Capture the component before the node is removed so we can rebuild
            // routes for the remaining nodes after the topology changes.
            Set<UUID> oldComponent = graph.getComponent(lookup.transfer().getNodeId());
            graph.removeNode(lookup.transfer().getNodeId());

            FaceLinkUtil.unlinkAll(lookup.transfer(), lookup.blockRef(), pos, chunkStore,
                    p -> commandBuffer.run(_ -> TransferStateRegistry.applyState(
                            commandBuffer.getExternalData().getWorld(), p)));

            graph.rebuildRoutesForNodes(oldComponent, chunkStore);
        } else {
            FaceLinkUtil.unlinkAll(lookup.transfer(), lookup.blockRef(), pos, chunkStore,
                    p -> commandBuffer.run(_ -> TransferStateRegistry.applyState(
                            commandBuffer.getExternalData().getWorld(), p)));
        }
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }
}