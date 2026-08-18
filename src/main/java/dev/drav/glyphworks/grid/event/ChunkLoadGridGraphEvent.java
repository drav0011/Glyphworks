package dev.drav.glyphworks.grid.event;

import javax.annotation.Nonnull;

import org.joml.Vector3i;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.ChunkColumn;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockComponentSection;
import com.hypixel.hytale.server.core.universe.world.events.ChunkPreLoadProcessEvent;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.GlyphworksPlugin;
import dev.drav.glyphworks.grid.component.GridComponent;
import dev.drav.glyphworks.grid.component.GridTypeEntry;
import dev.drav.glyphworks.grid.graph.GridGraph;
import dev.drav.glyphworks.grid.type.GridType;
import it.unimi.dsi.fastutil.shorts.Short2ObjectMap;

/**
 * Rebuilds the per-world, per-type {@link GridGraph} entries for every
 * {@link GridComponent} found in a freshly loaded chunk.
 *
 * <p>
 * Cross-chunk edges are handled naturally: each chunk load adds the nodes
 * and edges it can see; when a neighbor's chunk loads it completes the other
 * half.
 *
 * <p>
 * Registered as a global event listener in {@link GlyphworksPlugin#setup()}.
 */
public final class ChunkLoadGridGraphEvent {

    private ChunkLoadGridGraphEvent() {
    }

    public static void handle(@Nonnull ChunkPreLoadProcessEvent event) {
        BlockChunk blockChunk = event.getChunk().getBlockChunk();
        if (blockChunk == null)
            return;

        ChunkColumn column = (ChunkColumn) event.getHolder().getComponent(ChunkColumn.getComponentType());
        if (column == null)
            return;

        Holder<ChunkStore>[] sections = column.getSectionHolders();
        if (sections == null)
            return;

        int chunkOriginX = blockChunk.getX() << 5;
        int chunkOriginZ = blockChunk.getZ() << 5;

        for (int sectionY = 0; sectionY < sections.length; sectionY++) {
            Holder<ChunkStore> sectionHolder = sections[sectionY];
            if (sectionHolder == null)
                continue;

            BlockComponentSection bcs = (BlockComponentSection) sectionHolder.getComponent(
                    BlockComponentSection.getComponentType());
            if (bcs == null || bcs.getBlockHolders().isEmpty())
                continue;

            int sectionOriginY = sectionY << 5;

            for (Short2ObjectMap.Entry<Holder<ChunkStore>> bcsEntry : bcs.getBlockHolders().short2ObjectEntrySet()) {
                int blockIndex = bcsEntry.getShortKey();
                GridComponent component = (GridComponent) bcsEntry.getValue().getComponent(
                        GridComponent.getComponentType());
                if (component == null)
                    continue;

                Vector3i pos = new Vector3i(
                        chunkOriginX + ChunkUtil.xFromIndex(blockIndex),
                        sectionOriginY + ChunkUtil.yFromIndex(blockIndex),
                        chunkOriginZ + ChunkUtil.zFromIndex(blockIndex));

                component.setOriginPosition(pos);

                for (GridTypeEntry entry : component.getEntries()) {
                    GridType type = entry.getGridType();
                    if (type == null)
                        continue;

                    GridGraph graph = GlyphworksPlugin.get().getGridModule().getOrCreateGridGraph(event.getChunk().getWorld(), type);
                    graph.addNode(pos);

                    for (Vector3i neighborPos : entry.getNeighbors()) {
                        graph.addNode(neighborPos);
                        graph.addEdge(pos, neighborPos);
                    }
                }
            }
        }
    }
}
