package dev.drav.glyphworks.grid.event;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3i;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.ChunkColumn;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockComponentSection;
import com.hypixel.hytale.server.core.universe.world.events.ecs.ChunkUnloadEvent;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.GlyphworksPlugin;
import dev.drav.glyphworks.grid.component.GridComponent;
import dev.drav.glyphworks.grid.component.GridTypeEntry;
import dev.drav.glyphworks.grid.graph.GridGraph;
import dev.drav.glyphworks.grid.type.GridType;
import it.unimi.dsi.fastutil.shorts.Short2ObjectMap;
import it.unimi.dsi.fastutil.shorts.Short2ReferenceMap;

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

        ChunkColumn column = archetypeChunk.getComponent(index, ChunkColumn.getComponentType());
        if (column == null)
            return;

        Ref<ChunkStore>[] sections = column.getSections();
        if (sections == null)
            return;

        int chunkOriginX = blockChunk.getX() << 5;
        int chunkOriginZ = blockChunk.getZ() << 5;

        for (int sectionY = 0; sectionY < sections.length; sectionY++) {
            Ref<ChunkStore> sectionRef = sections[sectionY];
            if (sectionRef == null || !sectionRef.isValid())
                continue;

            BlockComponentSection bcs = store.getComponent(sectionRef, BlockComponentSection.getComponentType());
            if (bcs == null)
                continue;

            int sectionOriginY = sectionY << 5;

            // Non-ticking sections keep their block entities as raw holders...
            for (Short2ObjectMap.Entry<Holder<ChunkStore>> entry : bcs.getBlockHolders().short2ObjectEntrySet()) {
                GridComponent component = (GridComponent) entry.getValue().getComponent(
                        GridComponent.getComponentType());
                removeNodes(event, component, chunkOriginX, sectionOriginY, chunkOriginZ, entry.getShortKey());
            }

            // ...while ticking sections promote them to live store entities.
            for (Short2ReferenceMap.Entry<Ref<ChunkStore>> entry : bcs.getBlockReferences().short2ReferenceEntrySet()) {
                Ref<ChunkStore> blockRef = entry.getValue();
                if (blockRef == null || !blockRef.isValid())
                    continue;
                GridComponent component = store.getComponent(blockRef, GridComponent.getComponentType());
                removeNodes(event, component, chunkOriginX, sectionOriginY, chunkOriginZ, entry.getShortKey());
            }
        }
    }

    private static void removeNodes(
            @Nonnull ChunkUnloadEvent event,
            @Nullable GridComponent component,
            int chunkOriginX,
            int sectionOriginY,
            int chunkOriginZ,
            int blockIndex) {
        if (component == null)
            return;

        Vector3i pos = new Vector3i(
                chunkOriginX + ChunkUtil.xFromIndex(blockIndex),
                sectionOriginY + ChunkUtil.yFromIndex(blockIndex),
                chunkOriginZ + ChunkUtil.zFromIndex(blockIndex));

        for (GridTypeEntry gteEntry : component.getEntries()) {
            GridType type = gteEntry.getGridType();
            if (type == null)
                continue;

            GridGraph graph = GlyphworksPlugin.get().getGridModule().getGridGraph(event.getChunk().getWorld(), type);
            if (graph != null) {
                graph.removeNode(pos);
            }
        }
    }
}
