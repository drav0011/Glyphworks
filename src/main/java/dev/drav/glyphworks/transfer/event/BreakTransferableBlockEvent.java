package dev.drav.glyphworks.transfer.event;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.drav.glyphworks.transfer.component.TransferComponent;
import dev.drav.glyphworks.transfer.graph.GraphManager;
import dev.drav.glyphworks.transfer.graph.WorldGraph;

import javax.annotation.Nonnull;

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

        Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(ChunkUtil.indexChunkFromBlock(pos.x, pos.z));
        if (chunkRef == null || !chunkRef.isValid()) {
            return;
        }

        BlockComponentChunk bcc = chunkStore.getStore().getComponent(chunkRef, BlockComponentChunk.getComponentType());
        if (bcc == null) {
            return;
        }

        Ref<ChunkStore> blockRef = bcc.getEntityReference(ChunkUtil.indexBlockInColumn(pos.x, pos.y, pos.z));
        if (blockRef == null) {
            return;
        }

        TransferComponent transfer = chunkStore.getStore().getComponent(blockRef, TransferComponent.getComponentType());
        if (transfer == null) {
            return;
        }
        
        WorldGraph graph = GraphManager.get().getGraph(world.getName());
        if (graph != null) {
            graph.removeNode(transfer.getNodeId());
        }
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }
}