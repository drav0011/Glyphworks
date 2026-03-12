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
            Vector3i pos = event.getTargetBlock();
            World world = commandBuffer.getExternalData().getWorld();
            ChunkStore chunkStore = world.getChunkStore();

            GridLookup lookup = GridLookup.resolve(chunkStore, pos);
            if (lookup == null)
                return;

            GridComponent component = lookup.component();
            if (component.getGridType() == null)
                return;

            // Scan faces, find compatible neighbors of the same grid type, and link.
            for (FacePlane face : component.getFaces()) {
                if (face.getMode() == FaceMode.CLOSED || face.getNormal() == BlockFace.None)
                    continue;

                // The world cell this face touches: origin + face.position + normal.offset.
                // For a 1x1 block, face.position is (0,0,0) so this reduces to pos + normal.
                // For a multi-block, face.position offsets to the correct filler cell first.
                Vector3i candidatePos = GridFaceUtil.addOffset(GridFaceUtil.addOffset(pos, face.getPosition()), face.getNormal());

                GridLookup neighborLookup = GridLookup.resolve(chunkStore, candidatePos);
                if (neighborLookup == null)
                    continue;

                Vector3i resolvedNeighborPos = neighborLookup.originPos();

                GridComponent neighbor = neighborLookup.component();
                if (neighbor.getGridType() == null
                        || !neighbor.getGridType().id().equals(component.getGridType().id()))
                    continue;

                // The neighbor's face must have the opposite normal AND its world position
                // (neighborOrigin + faceB.position) must equal candidatePos — ensuring the
                // two faces are spatially adjacent, not just directionally compatible.
                FacePlane neighborFace = GridFaceUtil.findMatchingFace(neighbor, resolvedNeighborPos,
                        GridFaceUtil.opposite(face.getNormal()), candidatePos);
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
                graph.addEdge(pos, n);
            }


        });
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }
}
