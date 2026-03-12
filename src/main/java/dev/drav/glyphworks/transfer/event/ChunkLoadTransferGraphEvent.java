package dev.drav.glyphworks.transfer.event;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.events.ChunkPreLoadProcessEvent;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.GlyphworksPlugin;
import dev.drav.glyphworks.transfer.TransferGraph;
import dev.drav.glyphworks.transfer.component.FacePlane;
import dev.drav.glyphworks.transfer.component.TransferComponent;
import dev.drav.glyphworks.transfer.wiring.InventoryWiringRegistry;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

/**
 * Rebuilds the per-world {@link TransferGraph} entries for every
 * {@link TransferComponent} found in a freshly loaded chunk.
 *
 * <p>Registered as a global event listener in {@link GlyphworksPlugin#setup()}.
 * Works for both newly generated chunks and chunks loaded from disk.
 * Cross-chunk edges are handled naturally: each call adds the half-edges it
 * can see; when the neighbour's chunk loads its call completes the other half.
 */
public final class ChunkLoadTransferGraphEvent {

    private ChunkLoadTransferGraphEvent() {
    }

    public static void handle(@Nonnull ChunkPreLoadProcessEvent event) {
        BlockChunk blockChunk = event.getChunk().getBlockChunk();
        if (blockChunk == null) return;

        BlockComponentChunk bcc = (BlockComponentChunk) event.getHolder().getComponent(
                BlockComponentChunk.getComponentType());
        if (bcc == null) return;

        if (bcc.getEntityHolders().isEmpty()) return;

        TransferGraph graph = GlyphworksPlugin.get().getOrCreateGraph(event.getChunk().getWorld());
        ChunkStore chunkStore = event.getChunk().getWorld().getChunkStore();

        int chunkOriginX = blockChunk.getX() << 5;
        int chunkOriginZ = blockChunk.getZ() << 5;

        Set<UUID> addedNodes = new HashSet<>();

        for (Int2ObjectMap.Entry<Holder<ChunkStore>> entry : bcc.getEntityHolders().int2ObjectEntrySet()) {
            int blockIndex = entry.getIntKey();
            TransferComponent transfer = (TransferComponent) entry.getValue().getComponent(
                    TransferComponent.getComponentType());
            if (transfer == null) continue;

            Vector3i pos = new Vector3i(
                    chunkOriginX + ChunkUtil.xFromBlockInColumn(blockIndex),
                    ChunkUtil.yFromBlockInColumn(blockIndex),
                    chunkOriginZ + ChunkUtil.zFromBlockInColumn(blockIndex));

            graph.addNode(transfer.getNodeId(), pos);
            addedNodes.add(transfer.getNodeId());

            for (FacePlane face : transfer.getFaces()) {
                UUID neighborId = face.getNeighborNodeId();
                if (neighborId != null) {
                    graph.addEdge(transfer.getNodeId(), neighborId);
                }
            }

            InventoryWiringRegistry.wire(chunkStore, transfer, pos);
        }

        graph.rebuildRoutesForNodes(addedNodes, chunkStore);
    }
}

