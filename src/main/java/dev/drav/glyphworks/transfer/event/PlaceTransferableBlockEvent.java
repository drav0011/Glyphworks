package dev.drav.glyphworks.transfer.event;

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
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.drav.glyphworks.transfer.component.FacePlane;
import dev.drav.glyphworks.transfer.component.TransferComponent;
import dev.drav.glyphworks.transfer.graph.GraphManager;

import javax.annotation.Nonnull;
import java.util.logging.Logger;

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

            // Log detailed face information
            LOGGER.info("=================================================");
            LOGGER.info("[PlaceTransferableBlock] Block placed at " + pos);
            LOGGER.info("[PlaceTransferableBlock] Node ID: " + transfer.getNodeId());
            LOGGER.info("[PlaceTransferableBlock] Block Size (from bounds): " + blockSize.x + "x" + blockSize.y + "x" + blockSize.z);
            LOGGER.info("[PlaceTransferableBlock] Default Face Mode: " + transfer.getDefaultFaceMode());
            LOGGER.info("[PlaceTransferableBlock] Number of configured faces: " + transfer.getFaces().size());

            // Calculate and log all possible face planes
            logAllPossibleFaces(pos, blockSize);

            if (transfer.getFaces().isEmpty()) {
                LOGGER.info("[PlaceTransferableBlock] No faces configured - will use defaultFaceMode for all connections");
            } else {
                LOGGER.info("[PlaceTransferableBlock] Configured faces:");
                int faceIndex = 1;
                for (FacePlane face : transfer.getFaces().values()) {
                    LOGGER.info("[PlaceTransferableBlock]   Face #" + faceIndex + ":");
                    LOGGER.info("[PlaceTransferableBlock]     Min: " + face.getPlaneMin());
                    LOGGER.info("[PlaceTransferableBlock]     Max: " + face.getPlaneMax());
                    LOGGER.info("[PlaceTransferableBlock]     Mode: " + face.getMode());
                    LOGGER.info("[PlaceTransferableBlock]     Can Send: " + face.canSend());
                    LOGGER.info("[PlaceTransferableBlock]     Can Receive: " + face.canReceive());
                    LOGGER.info("[PlaceTransferableBlock]     FaceKey: " + face.getFaceKey());
                    faceIndex++;
                }
            }
            LOGGER.info("=================================================");

            GraphManager.get()
                    .getOrCreateGraph(world.getName())
                    .addNode(transfer.getNodeId(), blockRef, pos);
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

            // Get the bounding box
            Box boundingBox = rotatedHitbox.getBoundingBox();

            // Calculate dimensions from the bounding box
            // Box coordinates are in block-relative space
            double width = boundingBox.width();
            double height = boundingBox.height();
            double depth = boundingBox.depth();

            // Calculate block-space coordinates
            int benchMinBlockX = (int) Math.floor(boundingBox.min.x);
            int benchMinBlockY = (int) Math.floor(boundingBox.min.y);
            int benchMinBlockZ = (int) Math.floor(boundingBox.min.z);
            int benchMaxBlockX = ((int) Math.ceil(boundingBox.max.x)) - 1;
            int benchMaxBlockY = ((int) Math.ceil(boundingBox.max.y)) - 1;
            int benchMaxBlockZ = ((int) Math.ceil(boundingBox.max.z)) - 1;

            // Calculate actual block dimensions
            int w = benchMaxBlockX - benchMinBlockX + 1;
            int h = benchMaxBlockY - benchMinBlockY + 1;
            int d = benchMaxBlockZ - benchMinBlockZ + 1;

            LOGGER.info("[PlaceTransferableBlock] Bounding Box: min=" + boundingBox.min + " max=" + boundingBox.max);
            LOGGER.info("[PlaceTransferableBlock] Dimensions: width=" + width + " height=" + height + " depth=" + depth);
            LOGGER.info("[PlaceTransferableBlock] Block space: X[" + benchMinBlockX + " to " + benchMaxBlockX + "] Y[" +
                    benchMinBlockY + " to " + benchMaxBlockY + "] Z[" + benchMinBlockZ + " to " + benchMaxBlockZ + "]");
            LOGGER.info("[PlaceTransferableBlock] Calculated size: " + w + "x" + h + "x" + d + " blocks");

            return new Vector3i(w, h, d);

        } catch (Exception e) {
            LOGGER.warning("[PlaceTransferableBlock] Failed to get block bounds: " + e.getMessage());
            e.printStackTrace();
            return new Vector3i(1, 1, 1);
        }
    }

    /**
     * Logs all possible face planes for a block of given size.
     * <p>
     * For a WxHxD block, this calculates:
     * - North/South faces: H*W face positions each
     * - East/West faces: H*D face positions each
     * - Up/Down faces: W*D face positions each
     */
    private void logAllPossibleFaces(Vector3i blockPos, Vector3i blockSize) {
        int width = blockSize.x;   // X dimension
        int height = blockSize.y;  // Y dimension
        int depth = blockSize.z;   // Z dimension

        int totalFaces = 2 * (width * height) + 2 * (width * depth) + 2 * (height * depth);

        LOGGER.info("[PlaceTransferableBlock] All possible face planes (" + totalFaces + " total):");

        int faceNum = 1;

        // NORTH faces (at Z+depth, facing +Z)
        LOGGER.info("[PlaceTransferableBlock]   NORTH side (" + (width * height) + " faces):");
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Vector3i facePos = new Vector3i(blockPos.x + x, blockPos.y + y, blockPos.z + depth);
                LOGGER.info("[PlaceTransferableBlock]     Face #" + faceNum + ": " + facePos + " (1x1 at north edge)");
                faceNum++;
            }
        }

        // SOUTH faces (at Z, facing -Z)
        LOGGER.info("[PlaceTransferableBlock]   SOUTH side (" + (width * height) + " faces):");
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Vector3i facePos = new Vector3i(blockPos.x + x, blockPos.y + y, blockPos.z - 1);
                LOGGER.info("[PlaceTransferableBlock]     Face #" + faceNum + ": " + facePos + " (1x1 at south edge)");
                faceNum++;
            }
        }

        // EAST faces (at X+width, facing +X)
        LOGGER.info("[PlaceTransferableBlock]   EAST side (" + (depth * height) + " faces):");
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < depth; z++) {
                Vector3i facePos = new Vector3i(blockPos.x + width, blockPos.y + y, blockPos.z + z);
                LOGGER.info("[PlaceTransferableBlock]     Face #" + faceNum + ": " + facePos + " (1x1 at east edge)");
                faceNum++;
            }
        }

        // WEST faces (at X, facing -X)
        LOGGER.info("[PlaceTransferableBlock]   WEST side (" + (depth * height) + " faces):");
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < depth; z++) {
                Vector3i facePos = new Vector3i(blockPos.x - 1, blockPos.y + y, blockPos.z + z);
                LOGGER.info("[PlaceTransferableBlock]     Face #" + faceNum + ": " + facePos + " (1x1 at west edge)");
                faceNum++;
            }
        }

        // UP faces (at Y+height, facing +Y)
        LOGGER.info("[PlaceTransferableBlock]   UP side (" + (width * depth) + " faces):");
        for (int z = 0; z < depth; z++) {
            for (int x = 0; x < width; x++) {
                Vector3i facePos = new Vector3i(blockPos.x + x, blockPos.y + height, blockPos.z + z);
                LOGGER.info("[PlaceTransferableBlock]     Face #" + faceNum + ": " + facePos + " (1x1 at top edge)");
                faceNum++;
            }
        }

        // DOWN faces (at Y, facing -Y)
        LOGGER.info("[PlaceTransferableBlock]   DOWN side (" + (width * depth) + " faces):");
        for (int z = 0; z < depth; z++) {
            for (int x = 0; x < width; x++) {
                Vector3i facePos = new Vector3i(blockPos.x + x, blockPos.y - 1, blockPos.z + z);
                LOGGER.info("[PlaceTransferableBlock]     Face #" + faceNum + ": " + facePos + " (1x1 at bottom edge)");
                faceNum++;
            }
        }
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }
}
