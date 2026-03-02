//package dev.drav.glyphworks.transfer.system;
//
//import com.hypixel.hytale.component.ArchetypeChunk;
//import com.hypixel.hytale.component.CommandBuffer;
//import com.hypixel.hytale.component.Ref;
//import com.hypixel.hytale.component.Store;
//import com.hypixel.hytale.component.query.Query;
//import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
//import com.hypixel.hytale.math.util.ChunkUtil;
//import com.hypixel.hytale.server.core.asset.type.blocktick.BlockTickStrategy;
//import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
//import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
//import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
//import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
//
//import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
//import dev.drav.glyphworks.transfer.component.TransferComponent;
//import dev.drav.glyphworks.transfer.graph.GraphCache;
//import dev.drav.glyphworks.transfer.graph.GraphEdge;
//
//import javax.annotation.Nonnull;
//import javax.annotation.Nullable;
//import java.util.List;
//import java.util.UUID;
//
///**
// * Per-tick system that moves items through the transfer network.
// *
// * <p>For each ticking block whose {@link TransferComponent} has {@code autoPush}
// * set and {@link TransferComponent#canSend()} returns {@code true}, iterates its
// * outgoing edges and calls
// * {@link TransferComponent#push(com.hypixel.hytale.server.core.inventory.container.ItemContainer)}
// * on every reachable sink that {@link TransferComponent#canReceive()}.
// *
// * <p>The actual item movement is delegated to Hytale's native
// * {@code ItemContainer.moveItemStackFromSlot} — the system only orchestrates
// * which source talks to which sink.
// */
//public class TransferSystem extends EntityTickingSystem<EntityStore> {
//
//    @Override
//    public void tick(
//            float dt,
//            int index,
//            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
//            @Nonnull Store<EntityStore> store,
//            @Nonnull CommandBuffer<EntityStore> commandBuffer
//    ) {
//        BlockSection blocks = archetypeChunk.getComponent(index, BlockSection.getComponentType());
//        if (blocks == null || blocks.getTickingBlocksCountCopy() == 0) return;
//
//        ChunkSection section = archetypeChunk.getComponent(index, ChunkSection.getComponentType());
//        if (section == null) return;
//
//        BlockComponentChunk bcc =
//            commandBuffer.getComponent(section.getChunkColumnReference(), BlockComponentChunk.getComponentType());
//        if (bcc == null) return;
//
//        GraphCache graph = GraphCache.get();
//
//        blocks.forEachTicking(bcc, commandBuffer, section.getY(),
//            (blockComponentChunk, cb, localX, localY, localZ, blockId) -> {
//
//                Ref<EntityStore> blockRef =
//                    blockComponentChunk.getEntityReference(ChunkUtil.indexBlockInColumn(localX, localY, localZ));
//                if (blockRef == null) return BlockTickStrategy.IGNORED;
//
//                TransferComponent source =
//                    commandBuffer.getComponent(blockRef, TransferComponent.getComponentType());
//                if (source == null) return BlockTickStrategy.IGNORED;
//
//                // Only auto-push nodes drive outward transfers each tick.
//                if (!source.isAutoPush() || !source.canSend()) return BlockTickStrategy.CONTINUE;
//
//                UUID sourceId = source.getNodeId();
//                List<GraphEdge> edges = graph.getEdgesFrom(sourceId);
//                if (edges.isEmpty()) return BlockTickStrategy.CONTINUE;
//
//                for (GraphEdge edge : edges) {
//                    if (!edge.isEnabled()) continue;
//
//                    Ref<EntityStore> sinkRef = graph.getRef(edge.getTo());
//                    if (sinkRef == null) continue;
//
//                    TransferComponent sink =
//                        commandBuffer.getComponent(sinkRef, TransferComponent.getComponentType());
//                    if (sink == null || !sink.canReceive()) continue;
//
//                    // Delegate the actual item movement to TransferComponent#push,
//                    // which respects both source maxOutputRate and sink maxInputRate.
//                    source.push(sink.getInputInventory());
//                }
//
//                return BlockTickStrategy.CONTINUE;
//            }
//        );
//    }
//
//    @Nullable
//    @Override
//    public Query<EntityStore> getQuery() {
//        return Query.and(BlockSection.getComponentType(), ChunkSection.getComponentType());
//    }
//}
