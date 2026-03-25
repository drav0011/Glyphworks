package dev.drav.glyphworks.grid.event;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockFace;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import dev.drav.glyphworks.GlyphworksPlugin;
import dev.drav.glyphworks.grid.component.FaceMode;
import dev.drav.glyphworks.grid.component.FacePlane;
import dev.drav.glyphworks.grid.component.GridComponent;
import dev.drav.glyphworks.grid.graph.GridGraph;
import dev.drav.glyphworks.grid.lookup.GridLookup;
import dev.drav.glyphworks.grid.util.GridFaceUtil;

public final class PlaceGridBlockEvent extends EntityEventSystem<EntityStore, PlaceBlockEvent> {

    public PlaceGridBlockEvent() {
        super(PlaceBlockEvent.class);
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
            // Visual update for neighbours outside ±1 notification radius — safe here,
            // deferred outside Store.tick() by commandBuffer.run().
            GridLookup postLookup = GridLookup.resolve(world.getChunkStore(), target);
            if (postLookup != null) {
                for (Vector3i n : postLookup.component().getNeighbors()) {
                    GridFaceUtil.forceConnectedBlockUpdate(world, n);
                }
            }
        });
    }

    /**
     * Establishes grid connections for the block at {@code pos} and updates the
     * runtime {@link GridGraph}.
     *
     * <p>Safe to call directly (e.g. from tests) when {@code world.setBlock()} is
     * used instead of a player placement event, which may not dispatch
     * {@link PlaceBlockEvent}. Idempotent — calling it more than once for the same
     * position is harmless.
     *
     * <p>The ordering fix here — {@code graph.addNode(n)} before
     * {@code graph.addEdge(pos, n)} — ensures that edges are registered on both
     * sides even when several adjacent blocks are connected in the same tick.
     */
    public static void connectBlock(@Nonnull World world, @Nonnull Vector3i pos) {
        ChunkStore chunkStore = world.getChunkStore();

        GridLookup lookup = GridLookup.resolve(chunkStore, pos);
        if (lookup == null)
            return;

        GridComponent component = lookup.component();
        if (component.getGridType() == null)
            return;

        component.setOriginPosition(lookup.originPos());

        // Scan faces, find compatible neighbors of the same grid type, and link.
        for (FacePlane face : component.getFaces()) {
            if (face.getMode() == FaceMode.CLOSED || face.getNormal() == BlockFace.None)
                continue;

            // Rotate normal and filler-cell offset from local/JSON space to world space
            // using the block's placement rotation, so rotated blocks connect correctly.
            BlockFace worldNormal = GridFaceUtil.rotateBlockFace(face.getNormal(), lookup.rotation());
            if (worldNormal == BlockFace.None)
                continue;

            // The world cell this face touches: origin + rotated(face.position) + normal.offset.
            // For a 1x1 block, face.position is (0,0,0) so this reduces to pos + normal.
            // For a multi-block, face.position offsets to the correct filler cell first.
            Vector3i worldFacePos = GridFaceUtil.rotateFacePosition(face.getPosition(), lookup.rotation());
            Vector3i candidatePos = GridFaceUtil.addOffset(GridFaceUtil.addOffset(pos, worldFacePos), worldNormal);

            GridLookup neighborLookup = GridLookup.resolve(chunkStore, candidatePos);
            if (neighborLookup == null)
                continue;

            Vector3i resolvedNeighborPos = neighborLookup.originPos();

            GridComponent neighbor = neighborLookup.component();
            if (neighbor.getGridType() == null
                    || !neighbor.getGridType().id().equals(component.getGridType().id()))
                continue;

            // The neighbor's face must have the opposite world-space normal AND its world
            // position (neighborOrigin + faceB.position) must equal candidatePos — ensuring
            // the two faces are spatially adjacent, not just directionally compatible.
            FacePlane neighborFace = GridFaceUtil.findMatchingFace(neighbor, resolvedNeighborPos,
                    neighborLookup.rotation(),
                    GridFaceUtil.opposite(worldNormal), candidatePos);
            if (neighborFace == null || neighborFace.getMode() == FaceMode.CLOSED)
                continue;
            if (!GridFaceUtil.areLinkable(face.getMode(), neighborFace.getMode()))
                continue;

            component.addNeighbor(resolvedNeighborPos);
            neighbor.addNeighbor(pos);
        }

        GridGraph graph = GlyphworksPlugin.get().getOrCreateGridGraph(world, component.getGridType());
        graph.addNode(pos);
        for (Vector3i n : component.getNeighbors()) {
            // Ensure the neighbor node exists in the graph before adding the edge so that
            // the edge is registered on both sides even when blocks are placed in the same
            // tick (before the neighbor's own connectBlock call has run).
            graph.addNode(n);
            graph.addEdge(pos, n);
        }

    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }
}
