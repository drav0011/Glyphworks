package dev.drav.glyphworks.transfer.event;

import java.util.logging.Logger;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blockhitbox.BlockBoundingBoxes;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import dev.drav.glyphworks.GlyphworksPlugin;
import dev.drav.glyphworks.transfer.FaceLinkUtil;
import dev.drav.glyphworks.transfer.PipeStateUtil;
import dev.drav.glyphworks.transfer.component.FaceMode;
import dev.drav.glyphworks.transfer.component.FacePlane;
import dev.drav.glyphworks.transfer.component.TransferComponent;

public final class PlaceTransferableBlockEvent extends EntityEventSystem<EntityStore, PlaceBlockEvent> {

    private static final Logger LOGGER = Logger.getLogger(PlaceTransferableBlockEvent.class.getName());

    public PlaceTransferableBlockEvent() {
        super(PlaceBlockEvent.class);
    }

    @Override
    public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull PlaceBlockEvent event
    ) {
        commandBuffer.run(_ -> {
            Vector3i pos = event.getTargetBlock();
            World world = commandBuffer.getExternalData().getWorld();
            ChunkStore chunkStore = world.getChunkStore();

            Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(ChunkUtil.indexChunkFromBlock(pos.x, pos.z));
            if (chunkRef == null || !chunkRef.isValid()) {
                return;
            }

            BlockComponentChunk bcc = chunkStore.getStore().getComponent(chunkRef, BlockComponentChunk.getComponentType());
            if (bcc == null) {
                return;
            }

            Ref<ChunkStore> blockRef = bcc.getEntityReference(ChunkUtil.indexBlockInColumn(pos.x, pos.y, pos.z));
            if (blockRef == null) {
                return;
            }

            TransferComponent transfer = chunkStore.getStore().getComponent(blockRef, TransferComponent.getComponentType());
            if (transfer == null) {
                return;
            }


            BlockType blockType = world.getBlockType(pos.x, pos.y, pos.z);

            WorldChunk worldChunk = world.getChunk(ChunkUtil.indexChunkFromBlock(pos.x, pos.z));
            if (worldChunk == null) {
                return;
            }

            int rotationIndex = worldChunk.getRotationIndex(pos.x, pos.y, pos.z);

            // Get block bounding box from BlockBoundingBoxes
            Vector3i blockSize = getBlockBoundsSize(blockType, rotationIndex);

            // Initialize faces on first placement
            if (transfer.getFaces().isEmpty()) {
                initAndLogAllFaces(transfer, pos, blockSize);
            }

            // Link faces to any already-placed neighbors
            FaceLinkUtil.linkAll(transfer, blockRef, chunkStore);
            // Register in the live node index so gwtransfer and TransferSystem can see it
            GlyphworksPlugin.get().registerNode(transfer);

            // Update pipe visual state for this block and all adjacent pipe blocks
            PipeStateUtil.updatePipeState(world, pos);
            PipeStateUtil.updateNeighborPipeStates(world, pos);
        });
    }

    /**
     * Gets the size of a block from its bounding box using BlockBoundingBoxes.
     * This properly handles multi-block structures like 2x2x1 furnaces.
     *
     * @param blockType     The block type
     * @param rotationIndex The rotation index of the placed block
     * @return Vector3i with dimensions (width, height, depth) in blocks
     */
    private Vector3i getBlockBoundsSize(BlockType blockType, int rotationIndex) {
        try {
            // Get the hitbox asset for this block type
            BlockBoundingBoxes hitboxAsset = BlockBoundingBoxes.getAssetMap().getAsset(blockType.getHitboxTypeIndex());

            if (hitboxAsset == null) {
                LOGGER.warning("[PlaceTransferableBlock] No hitbox asset found for block type, defaulting to 1x1x1");
                return new Vector3i(1, 1, 1);
            }

            // Get the rotated variant for this specific rotation
            BlockBoundingBoxes.RotatedVariantBoxes rotatedHitbox = hitboxAsset.get(rotationIndex);
            if (rotatedHitbox == null) {
                LOGGER.warning("[PlaceTransferableBlock] No rotated hitbox found for rotation " + rotationIndex + ", defaulting to 1x1x1");
                return new Vector3i(1, 1, 1);
            }

            Box boundingBox = rotatedHitbox.getBoundingBox();

            int benchMinBlockX = (int) Math.floor(boundingBox.min.x);
            int benchMinBlockY = (int) Math.floor(boundingBox.min.y);
            int benchMinBlockZ = (int) Math.floor(boundingBox.min.z);
            int benchMaxBlockX = ((int) Math.ceil(boundingBox.max.x)) - 1;
            int benchMaxBlockY = ((int) Math.ceil(boundingBox.max.y)) - 1;
            int benchMaxBlockZ = ((int) Math.ceil(boundingBox.max.z)) - 1;

            int w = benchMaxBlockX - benchMinBlockX + 1;
            int h = benchMaxBlockY - benchMinBlockY + 1;
            int d = benchMaxBlockZ - benchMinBlockZ + 1;

            return new Vector3i(w, h, d);

        } catch (Exception e) {
            LOGGER.warning("[PlaceTransferableBlock] Failed to get block bounds: " + e.getMessage());
            e.printStackTrace();
            return new Vector3i(1, 1, 1);
        }
    }

    /**
     * Creates a FacePlane for each 1×1 face cell on the block surface and registers
     * it in the TransferComponent, then logs all the created faces.
     *
     * <h3>Coordinate convention</h3>
     * Each face cell is stored as an axis-aligned rectangle. The axis perpendicular
     * to the face has the same value in planeMin and planeMax (the "constant axis").
     * The other two axes span exactly 1 unit so the raycast can intersect it.
     *
     * <p>The constant-axis value is always the shared boundary between the two blocks
     * that touch on that face, so two adjacent blocks produce identical min/max and
     * their faces collide by key equality.
     *
     * <pre>
     * NORTH (+Z wall of this block):  z_const = blockPos.z + depth
     * SOUTH (-Z wall of this block):  z_const = blockPos.z          ← same z as neighbor's NORTH
     * EAST  (+X wall):                x_const = blockPos.x + width
     * WEST  (-X wall):                x_const = blockPos.x          ← same x as neighbor's EAST
     * UP    (+Y wall):                y_const = blockPos.y + height
     * DOWN  (-Y wall):                y_const = blockPos.y          ← same y as neighbor's UP
     * </pre>
     *
     * <p>Initial mode is the component's defaultFaceMode.
     */
    private void initAndLogAllFaces(TransferComponent transfer, Vector3i blockPos, Vector3i blockSize) {
        int w  = blockSize.x;
        int h = blockSize.y;
        int d  = blockSize.z;
        FaceMode mode = transfer.getDefaultFaceMode();

        // NORTH (+Z wall)
        int zN = blockPos.z + d;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                FacePlane face = new FacePlane(
                        new Vector3i(blockPos.x + x,     blockPos.y + y,     zN),
                        new Vector3i(blockPos.x + x + 1, blockPos.y + y + 1, zN), mode);
                transfer.setFace(face.getFaceKey(), face);
            }

        // SOUTH (-Z wall)
        int zS = blockPos.z;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                FacePlane face = new FacePlane(
                        new Vector3i(blockPos.x + x,     blockPos.y + y,     zS),
                        new Vector3i(blockPos.x + x + 1, blockPos.y + y + 1, zS), mode);
                transfer.setFace(face.getFaceKey(), face);
            }

        // EAST (+X wall)
        int xE = blockPos.x + w;
        for (int y = 0; y < h; y++)
            for (int z = 0; z < d; z++) {
                FacePlane face = new FacePlane(
                        new Vector3i(xE, blockPos.y + y,     blockPos.z + z),
                        new Vector3i(xE, blockPos.y + y + 1, blockPos.z + z + 1), mode);
                transfer.setFace(face.getFaceKey(), face);
            }

        // WEST (-X wall)
        int xW = blockPos.x;
        for (int y = 0; y < h; y++)
            for (int z = 0; z < d; z++) {
                FacePlane face = new FacePlane(
                        new Vector3i(xW, blockPos.y + y,     blockPos.z + z),
                        new Vector3i(xW, blockPos.y + y + 1, blockPos.z + z + 1), mode);
                transfer.setFace(face.getFaceKey(), face);
            }

        // UP (+Y wall)
        int yU = blockPos.y + h;
        for (int z = 0; z < d; z++)
            for (int x = 0; x < w; x++) {
                FacePlane face = new FacePlane(
                        new Vector3i(blockPos.x + x,     yU, blockPos.z + z),
                        new Vector3i(blockPos.x + x + 1, yU, blockPos.z + z + 1), mode);
                transfer.setFace(face.getFaceKey(), face);
            }

        // DOWN (-Y wall)
        int yD = blockPos.y;
        for (int z = 0; z < d; z++)
            for (int x = 0; x < w; x++) {
                FacePlane face = new FacePlane(
                        new Vector3i(blockPos.x + x,     yD, blockPos.z + z),
                        new Vector3i(blockPos.x + x + 1, yD, blockPos.z + z + 1), mode);
                transfer.setFace(face.getFaceKey(), face);
            }
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }
}
