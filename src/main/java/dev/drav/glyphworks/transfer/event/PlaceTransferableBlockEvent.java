package dev.drav.glyphworks.transfer.event;

import java.util.UUID;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import dev.drav.glyphworks.GlyphworksPlugin;
import dev.drav.glyphworks.transfer.TransferGraph;
import dev.drav.glyphworks.transfer.component.FacePlane;
import dev.drav.glyphworks.transfer.lookups.TransferLookup;
import dev.drav.glyphworks.transfer.state.TransferStateRegistry;
import dev.drav.glyphworks.transfer.util.FaceLinkUtil;

public final class PlaceTransferableBlockEvent extends EntityEventSystem<EntityStore, PlaceBlockEvent> {

    public PlaceTransferableBlockEvent() {
        super(PlaceBlockEvent.class);
    }

    @Override
    public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull PlaceBlockEvent event
    ) {
        commandBuffer.run(_ -> {
            Vector3i pos = event.getTargetBlock();
            World world = commandBuffer.getExternalData().getWorld();
            ChunkStore chunkStore = world.getChunkStore();

            TransferLookup lookup = TransferLookup.resolve(chunkStore, pos);
            if (lookup == null) return;

            // Store the yaw so getWorldMin/Max can apply it dynamically.
            // Face coords in JSON are always authored for the north-facing (None) orientation.
            RotationTuple rotation = event.getRotation();
            lookup.transfer().setRotation(rotation);

            // Faces come pre-defined from the block type JSON via clone().
            // neighborNodeIds are all null (unlinked) on first placement — correct.
            FaceLinkUtil.linkAll(lookup.transfer(), lookup.blockRef(), pos, chunkStore,
                    p -> TransferStateRegistry.applyState(world, p));

            TransferGraph graph = GlyphworksPlugin.get().getOrCreateGraph(world);
            graph.addNode(lookup.transfer().getNodeId(), pos);

            for (FacePlane face : lookup.transfer().getFaces()) {
                UUID neighborId = face.getNeighborNodeId();
                
                if (neighborId != null) {
                    graph.addEdge(lookup.transfer().getNodeId(), neighborId);
                }
            }
        });
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }
}
