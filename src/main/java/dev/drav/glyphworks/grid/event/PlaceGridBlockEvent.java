package dev.drav.glyphworks.grid.event;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

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
import dev.drav.glyphworks.grid.state.GridStateRegistry;

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
                Vector3i candidatePos = addOffset(addOffset(pos, face.getPosition()), face.getNormal());

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
                FacePlane neighborFace = findMatchingFace(neighbor, resolvedNeighborPos,
                        opposite(face.getNormal()), candidatePos);
                if (neighborFace == null || neighborFace.getMode() == FaceMode.CLOSED)
                    continue;
                if (!areLinkable(face.getMode(), neighborFace.getMode()))
                    continue;

                component.addNeighbor(resolvedNeighborPos);
                neighbor.addNeighbor(pos);
            }

            GridGraph graph = GlyphworksPlugin.get().getOrCreateGridGraph(world, component.getGridType());
            graph.addNode(pos);
            for (Vector3i n : component.getNeighbors()) {
                graph.addEdge(pos, n);
            }

            // @Deprecated remove
            // Update visual state for this block and every newly linked neighbor.
            GridStateRegistry.applyState(world, pos);
            for (Vector3i n : component.getNeighbors()) {
                GridStateRegistry.applyState(world, n);
            }
        });
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    /** Translates {@code pos} by one step in {@code face} direction. */
    private static Vector3i addOffset(Vector3i pos, BlockFace face) {
        return switch (face) {
            case North -> new Vector3i(pos.x,     pos.y,     pos.z - 1);
            case South -> new Vector3i(pos.x,     pos.y,     pos.z + 1);
            case East  -> new Vector3i(pos.x + 1, pos.y,     pos.z);
            case West  -> new Vector3i(pos.x - 1, pos.y,     pos.z);
            case Up    -> new Vector3i(pos.x,     pos.y + 1, pos.z);
            case Down  -> new Vector3i(pos.x,     pos.y - 1, pos.z);
            default    -> pos;
        };
    }

    /** Adds a relative offset vector to a world position. */
    private static Vector3i addOffset(Vector3i pos, Vector3i offset) {
        return new Vector3i(pos.x + offset.x, pos.y + offset.y, pos.z + offset.z);
    }

    private static BlockFace opposite(BlockFace face) {
        return switch (face) {
            case North -> BlockFace.South;
            case South -> BlockFace.North;
            case East -> BlockFace.West;
            case West -> BlockFace.East;
            case Up -> BlockFace.Down;
            case Down -> BlockFace.Up;
            default -> BlockFace.None;
        };
    }

    /**
     * Finds the face on {@code component} whose normal matches {@code requiredNormal}
     * AND whose world position ({@code originPos + face.getPosition()}) equals
     * {@code requiredWorldPos}.
     *
     * <p>The position check is essential for multi-block structures: a 2×2×2 block
     * may have multiple faces with the same normal on different cells; only the one
     * spatially adjacent to the caller's face should link.
     */
    @Nullable
    private static FacePlane findMatchingFace(
            GridComponent component,
            Vector3i originPos,
            BlockFace requiredNormal,
            Vector3i requiredWorldPos) {
        for (FacePlane face : component.getFaces()) {
            if (face.getNormal() != requiredNormal)
                continue;
            Vector3i faceWorldPos = addOffset(originPos, face.getPosition());
            if (faceWorldPos.equals(requiredWorldPos))
                return face;
        }
        return null;
    }

    private static boolean areLinkable(FaceMode a, FaceMode b) {
        if (a == FaceMode.CLOSED || b == FaceMode.CLOSED)
            return false;
        if (a == FaceMode.INPUT && b == FaceMode.INPUT)
            return false;
        if (a == FaceMode.OUTPUT && b == FaceMode.OUTPUT)
            return false;
        return true;
    }
}
