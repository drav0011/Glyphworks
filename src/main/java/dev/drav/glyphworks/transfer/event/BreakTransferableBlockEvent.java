package dev.drav.glyphworks.transfer.event;

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
import dev.drav.glyphworks.transfer.lookups.TransferLookup;
import dev.drav.glyphworks.transfer.state.TransferStateRegistry;
import dev.drav.glyphworks.util.FaceLinkUtil;

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
        if (lookup == null) return;

        // Clear neighborNodeId on all neighbor faces that point to this node.
        // Neighbor state updates are deferred so they run after the block is removed.
        FaceLinkUtil.unlinkAll(lookup.transfer(), lookup.blockRef(), pos, chunkStore,
                p -> commandBuffer.run(_ -> TransferStateRegistry.applyState(
                        commandBuffer.getExternalData().getWorld(), p)));
        GlyphworksPlugin.get().unregisterNode(lookup.transfer().getNodeId());
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }
}