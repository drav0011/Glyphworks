package dev.drav.glyphworks.grid.event;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.events.ChunkPreLoadProcessEvent;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.GlyphworksPlugin;
import dev.drav.glyphworks.grid.component.GridComponent;
import dev.drav.glyphworks.grid.graph.GridGraph;
import dev.drav.glyphworks.grid.type.GridType;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

/**
 * Rebuilds the per-world, per-type {@link GridGraph} entries for every
 * {@link GridComponent} found in a freshly loaded chunk.
 *
 * <p>Cross-chunk edges are handled naturally: each chunk load adds the nodes
 * and edges it can see; when a neighbor's chunk loads it completes the other half.
 *
 * <p>Registered as a global event listener in {@link GlyphworksPlugin#setup()}.
 */
public final class ChunkLoadGridGraphEvent {

    private ChunkLoadGridGraphEvent() {}

    public static void handle(@Nonnull ChunkPreLoadProcessEvent event) {
        BlockChunk blockChunk = event.getChunk().getBlockChunk();
        if (blockChunk == null) return;

        BlockComponentChunk bcc = (BlockComponentChunk) event.getHolder().getComponent(
                BlockComponentChunk.getComponentType());
        if (bcc == null || bcc.getEntityHolders().isEmpty()) return;

        int chunkOriginX = blockChunk.getX() << 5;
        int chunkOriginZ = blockChunk.getZ() << 5;

        for (Int2ObjectMap.Entry<Holder<ChunkStore>> entry : bcc.getEntityHolders().int2ObjectEntrySet()) {
            int blockIndex = entry.getIntKey();
            GridComponent component = (GridComponent) entry.getValue().getComponent(
                    GridComponent.getComponentType());
            if (component == null) continue;

            GridType type = component.getGridType();
            if (type == null) continue;

            Vector3i pos = new Vector3i(
                    chunkOriginX + ChunkUtil.xFromBlockInColumn(blockIndex),
                    ChunkUtil.yFromBlockInColumn(blockIndex),
                    chunkOriginZ + ChunkUtil.zFromBlockInColumn(blockIndex));

            component.setOriginPosition(pos);

            GridGraph graph = GlyphworksPlugin.get().getOrCreateGridGraph(event.getChunk().getWorld(), type);
            graph.addNode(pos);

            for (Vector3i neighborPos : component.getNeighbors()) {
                // Ensure the neighbor node exists before adding the edge so that
                // addEdge can populate both sides immediately, regardless of the
                // order in which blocks are iterated within (or across) chunks.
                graph.addNode(neighborPos);
                graph.addEdge(pos, neighborPos);
            }
        }
    }
}
