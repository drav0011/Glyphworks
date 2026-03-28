package dev.drav.glyphworks.grid.event;

import javax.annotation.Nonnull;

import org.joml.Vector3i;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.events.ecs.ChunkUnloadEvent;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.GlyphworksPlugin;
import dev.drav.glyphworks.grid.component.GridComponent;
import dev.drav.glyphworks.grid.graph.GridGraph;
import dev.drav.glyphworks.grid.type.GridType;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

/**
 * Removes all {@link GridComponent} nodes from their per-type {@link GridGraph}
 * when a chunk unloads.
 *
 * <p>
 * Persisted neighbor data in {@link GridComponent#getNeighbors()} is left
 * intact
 * so the graph can be fully reconstructed when the chunk loads again.
 *
 * <p>
 * Registered on the chunk store registry in
 * {@link dev.drav.glyphworks.GlyphworksPlugin#start()}.
 */
public final class ChunkUnloadGridGraphEvent extends EntityEventSystem<ChunkStore, ChunkUnloadEvent> {

    public ChunkUnloadGridGraphEvent() {
        super(ChunkUnloadEvent.class);
    }

    @Nonnull
    @Override
    public Query<ChunkStore> getQuery() {
        return Query.any();
    }

    @Override
    public void handle(
            int index,
            @Nonnull ArchetypeChunk<ChunkStore> archetypeChunk,
            @Nonnull Store<ChunkStore> store,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer,
            @Nonnull ChunkUnloadEvent event) {

        BlockChunk blockChunk = event.getChunk().getBlockChunk();
        if (blockChunk == null)
            return;

        BlockComponentChunk bcc = archetypeChunk.getComponent(index, BlockComponentChunk.getComponentType());
        if (bcc == null || bcc.getEntityHolders().isEmpty())
            return;

        int chunkOriginX = blockChunk.getX() << 5;
        int chunkOriginZ = blockChunk.getZ() << 5;

        for (Int2ObjectMap.Entry<Holder<ChunkStore>> entry : bcc.getEntityHolders().int2ObjectEntrySet()) {
            int blockIndex = entry.getIntKey();
            GridComponent component = (GridComponent) entry.getValue().getComponent(
                    GridComponent.getComponentType());
            if (component == null)
                continue;

            GridType type = component.getGridType();
            if (type == null)
                continue;

            Vector3i pos = new Vector3i(
                    chunkOriginX + ChunkUtil.xFromBlockInColumn(blockIndex),
                    ChunkUtil.yFromBlockInColumn(blockIndex),
                    chunkOriginZ + ChunkUtil.zFromBlockInColumn(blockIndex));

            GridGraph graph = GlyphworksPlugin.get().getGridGraph(event.getChunk().getWorld(), type);
            if (graph != null) {
                graph.removeNode(pos);
            }
        }
    }
}
