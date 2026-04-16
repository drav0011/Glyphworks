package dev.drav.glyphworks.grid.event;

import javax.annotation.Nonnull;

import org.joml.Vector3i;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.protocol.BlockFace;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import com.hypixel.hytale.server.core.inventory.container.filter.FilterType;

import dev.drav.glyphworks.GlyphworksPlugin;
import dev.drav.glyphworks.grid.component.FacePlane;
import dev.drav.glyphworks.grid.component.GridComponent;
import dev.drav.glyphworks.grid.component.GridTypeEntry;
import dev.drav.glyphworks.grid.graph.GridGraph;
import dev.drav.glyphworks.grid.lookup.GridLookup;
import dev.drav.glyphworks.grid.util.GridFaceUtil;

public final class PlaceGridBlockEvent extends EntityEventSystem<EntityStore, PlaceBlockEvent> {

    public PlaceGridBlockEvent() {
        super(PlaceBlockEvent.class);
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull PlaceBlockEvent event) {
        commandBuffer.run(_ -> {
            World world = commandBuffer.getExternalData().getWorld();
            Vector3i target = event.getTargetBlock();
            connectBlock(world, target);
        });
    }

    /**
     * Establishes grid connections for the block at {@code pos} and updates the
     * runtime {@link GridGraph}.
     *
     * <p>
     * Safe to call directly (e.g. from tests) when {@code world.setBlock()} is
     * used instead of a player placement event, which may not dispatch
     * {@link PlaceBlockEvent}. Idempotent — calling it more than once for the same
     * position is harmless.
     *
     * <p>
     * The ordering fix here — {@code graph.addNode(n)} before
     * {@code graph.addEdge(pos, n)} — ensures that edges are registered on both
     * sides even when several adjacent blocks are connected in the same tick.
     */
    public static void connectBlock(@Nonnull World world, @Nonnull Vector3i pos) {
        ChunkStore chunkStore = world.getChunkStore();

        GridLookup lookup = GridLookup.resolve(chunkStore, pos);
        if (lookup == null)
            return;

        // Filler cells of multi-block structures resolve to the origin block.
        // Face positions in the GridComponent are relative to the origin, so
        // processing a filler cell would compute wrong candidate positions and
        // create spurious graph edges. Only process at the true origin.
        if (!pos.equals(lookup.originPos()))
            return;

        GridComponent component = lookup.component();
        if (component.getEntries().isEmpty())
            return;

        component.setOriginPosition(lookup.originPos());

        // Process each grid type entry independently.
        for (GridTypeEntry entry : component.getEntries()) {
            if (entry.getGridType() == null)
                continue;

            // Scan faces, find compatible neighbors of the same grid type, and link.
            for (FacePlane face : entry.getFaces()) {
                if (face.getMode() == FilterType.DENY_ALL || face.getNormal() == BlockFace.None)
                    continue;

                BlockFace worldNormal = GridFaceUtil.rotateBlockFace(face.getNormal(), lookup.rotation());
                if (worldNormal == BlockFace.None)
                    continue;

                Vector3i worldFacePos = GridFaceUtil.rotateFacePosition(face.getPosition(), lookup.rotation());
                Vector3i candidatePos = GridFaceUtil.addOffset(
                        GridFaceUtil.addOffset(pos, worldFacePos), worldNormal);

                GridLookup neighborLookup = GridLookup.resolve(chunkStore, candidatePos);
                if (neighborLookup == null)
                    continue;

                Vector3i resolvedNeighborPos = neighborLookup.originPos();

                GridComponent neighbor = neighborLookup.component();
                // The neighbor must have an entry for the same grid type.
                GridTypeEntry neighborEntry = neighbor.getEntry(entry.getGridType().id());
                if (neighborEntry == null)
                    continue;

                FacePlane neighborFace = GridFaceUtil.findMatchingFace(neighborEntry, resolvedNeighborPos,
                        neighborLookup.rotation(),
                        GridFaceUtil.opposite(worldNormal), candidatePos);
                if (neighborFace == null || neighborFace.getMode() == FilterType.DENY_ALL)
                    continue;
                if (!GridFaceUtil.areLinkable(face.getMode(), neighborFace.getMode()))
                    continue;

                entry.addNeighbor(resolvedNeighborPos);
                neighborEntry.addNeighbor(pos);
            }

            GridGraph graph = GlyphworksPlugin.get().getGridModule().getOrCreateGridGraph(world, entry.getGridType());
            graph.addNode(pos);
            for (Vector3i n : entry.getNeighbors()) {
                graph.addNode(n);
                graph.addEdge(pos, n);
            }
        }
    }
}

