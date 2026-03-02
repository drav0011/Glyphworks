package dev.drav.glyphworks.transfer.event;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.packets.buildertools.BuilderToolLaserPointer;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.world.PlayerUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.drav.glyphworks.transfer.FaceLinkUtil;
import dev.drav.glyphworks.transfer.component.FaceMode;
import dev.drav.glyphworks.transfer.component.FacePlane;
import dev.drav.glyphworks.transfer.component.TransferComponent;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import javax.annotation.Nonnull;
import java.util.logging.Logger;

/**
 * Handles face-mode toggling when a player right-clicks a TransferComponent
 * block
 * while holding a face wrench item.
 *
 * <p>
 * Raycast from the player's camera finds the closest FacePlane on the block.
 * Each click cycles the mode: BIDIRECTIONAL → INPUT → OUTPUT → CLOSED →
 * BIDIRECTIONAL.
 *
 * <p>
 * The default block interaction (e.g. opening UI) is cancelled when the wrench
 * is held.
 */
public final class UseTransferableBlockEvent extends EntityEventSystem<EntityStore, UseBlockEvent.Pre> {

    private static final Logger LOGGER = Logger.getLogger(UseTransferableBlockEvent.class.getName());

    /**
     * Item ID of the face wrench that triggers this interaction.
     */
    public static final String FACE_WRENCH_ITEM_ID = "glyphworks:face_wrench";

    /** Maximum raycast reach distance in blocks. */
    private static final double MAX_REACH = 10.0;

    /** Laser duration in milliseconds. */
    private static final int LASER_DURATION_MS = 2000;

    /** Laser colour per face mode (0xRRGGBB). */
    private static int laserColor(FaceMode mode) {
        return switch (mode) {
            case BIDIRECTIONAL -> 0x00FF88; // green
            case INPUT         -> 0x4488FF; // blue
            case OUTPUT        -> 0xFF4400; // orange-red
            case CLOSED        -> 0x555555; // dark grey
        };
    }

    public UseTransferableBlockEvent() {
        super(UseBlockEvent.Pre.class);
    }

    @Override
    public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull UseBlockEvent.Pre event) {

        // Only trigger when the player is holding the face wrench
//        ItemStack heldItem = event.getContext().getHeldItem();
//        if (heldItem == null || heldItem.isEmpty() || !FACE_WRENCH_ITEM_ID.equals(heldItem.getItemId())) {
//            return;
//        }

        // Read player transform, model (for real eye height), and head rotation (actual camera look)
        TransformComponent transform = archetypeChunk.getComponent(index, TransformComponent.getComponentType());
        if (transform == null)
            return;

        ModelComponent modelComponent = archetypeChunk.getComponent(index, ModelComponent.getComponentType());
        if (modelComponent == null)
            return;

        HeadRotation headRotation = archetypeChunk.getComponent(index, HeadRotation.getComponentType());
        if (headRotation == null)
            return;

        NetworkId networkId = archetypeChunk.getComponent(index, NetworkId.getComponentType());
        if (networkId == null)
            return;
        final int playerNetworkId = networkId.getId();

        // Eye position: feet pos + actual eye height from the model definition
        final double eyeX = transform.getPosition().getX();
        final double eyeY = transform.getPosition().getY() + modelComponent.getModel().getEyeHeight();
        final double eyeZ = transform.getPosition().getZ();

        // Camera direction from head rotation — getDirection() computes the unit vector
        // from yaw/pitch using the engine's own TrigMath formula
        final Vector3d dir = headRotation.getDirection();
        final double dirX = dir.x;
        final double dirY = dir.y;
        final double dirZ = dir.z;

        // Target block position comes from the event, not an undefined variable
        final Vector3i pos = event.getTargetBlock();

        // Cancel the default block interaction since the wrench is handling it
        event.setCancelled(true);

        commandBuffer.run(_ -> {
            World world = commandBuffer.getExternalData().getWorld();
            ChunkStore chunkStore = world.getChunkStore();

            Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(ChunkUtil.indexChunkFromBlock(pos.x, pos.z));
            if (chunkRef == null || !chunkRef.isValid())
                return;

            BlockComponentChunk bcc = chunkStore.getStore().getComponent(chunkRef,
                    BlockComponentChunk.getComponentType());
            if (bcc == null)
                return;

            Ref<ChunkStore> blockRef = bcc.getEntityReference(ChunkUtil.indexBlockInColumn(pos.x, pos.y, pos.z));
            if (blockRef == null)
                return;

            TransferComponent transfer = chunkStore.getStore().getComponent(blockRef,
                    TransferComponent.getComponentType());
            if (transfer == null)
                return;

            LOGGER.info("[UseTransferableBlock] Target block: " + pos + "  faces=" + transfer.getFaces().size());
            LOGGER.info("[UseTransferableBlock] Eye: (" + eyeX + ", " + eyeY + ", " + eyeZ + ")  dir: (" +
                    String.format("%.3f", dirX) + ", " + String.format("%.3f", dirY) + ", "
                    + String.format("%.3f", dirZ) + ")");

            Vector3d eye = new Vector3d(eyeX, eyeY, eyeZ);
            FacePlane hitFace = null;
            double minT = Double.MAX_VALUE;

            StringBuilder missLog = new StringBuilder();
            for (FacePlane face : transfer.getFaces().values()) {
                double t = raycastFacePlane(eye, dirX, dirY, dirZ, face);
                if (t > 0 && t < minT && t <= MAX_REACH) {
                    minT = t;
                    hitFace = face;
                } else {
                    missLog.append("\n  MISS face min=").append(face.getPlaneMin())
                           .append(" max=").append(face.getPlaneMax())
                           .append(" t=").append(String.format("%.3f", t));
                }
            }

            if (hitFace != null) {
                FaceMode oldMode = hitFace.getMode();
                FaceMode newMode = cycleFaceMode(oldMode);
                hitFace.setMode(newMode);

                LOGGER.info("=================================================");
                LOGGER.info("[UseTransferableBlock] Raycast HIT face:");
                LOGGER.info("[UseTransferableBlock]   Min:  " + hitFace.getPlaneMin());
                LOGGER.info("[UseTransferableBlock]   Max:  " + hitFace.getPlaneMax());
                LOGGER.info("[UseTransferableBlock]   Mode: " + oldMode + " → " + newMode);
                LOGGER.info("[UseTransferableBlock]   t=" + String.format("%.4f", minT) + " blocks away");
                LOGGER.info("=================================================");

                FaceLinkUtil.relinkFace(transfer, blockRef, hitFace, chunkStore);

                // Laser from eye to hit point, coloured by new mode
                BuilderToolLaserPointer laser = new BuilderToolLaserPointer();
                laser.playerNetworkId = playerNetworkId;
                laser.startX = (float) eyeX;
                laser.startY = (float) eyeY;
                laser.startZ = (float) eyeZ;
                laser.endX   = (float) (eyeX + dirX * minT);
                laser.endY   = (float) (eyeY + dirY * minT);
                laser.endZ   = (float) (eyeZ + dirZ * minT);
                laser.color  = laserColor(newMode);
                laser.durationMs = LASER_DURATION_MS;
                PlayerUtil.broadcastPacketToPlayers(store, laser);
            } else {
                LOGGER.info("[UseTransferableBlock] Raycast hit no face (" + transfer.getFaces().size()
                        + " faces checked)" + missLog);
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Ray–FacePlane intersection.
     *
     * <p>
     * A FacePlane is an axis-aligned rectangle. Exactly one pair of min/max
     * coordinates will be equal (the constant axis that defines the plane).
     * We compute the ray parameter {@code t} for that plane, then verify the
     * hit point lies inside the rectangle's bounds on the other two axes.
     *
     * <p>
     * A small epsilon is added to the 2D bounds check so that a player standing
     * just outside a face cell's integer boundary (e.g. slightly south of the
     * block's south edge) can still interact with visible faces.
     *
     * @return {@code t > 0} if the ray hits, {@code -1} otherwise
     */
    private static final double FACE_HIT_EPSILON = 1;

    private static double raycastFacePlane(
            Vector3d origin,
            double dirX, double dirY, double dirZ,
            FacePlane face) {
        Vector3i min = face.getPlaneMin();
        Vector3i max = face.getPlaneMax();
        double e = FACE_HIT_EPSILON;

        if (min.x == max.x) {
            // YZ-plane at x = min.x
            if (Math.abs(dirX) < 1e-10)
                return -1;
            double t = (min.x - origin.x) / dirX;
            if (t < 0)
                return -1;
            double hy = origin.y + t * dirY;
            double hz = origin.z + t * dirZ;
            if (hy >= min.y - e && hy <= max.y + e && hz >= min.z - e && hz <= max.z + e)
                return t;

        } else if (min.y == max.y) {
            // XZ-plane at y = min.y
            if (Math.abs(dirY) < 1e-10)
                return -1;
            double t = (min.y - origin.y) / dirY;
            if (t < 0)
                return -1;
            double hx = origin.x + t * dirX;
            double hz = origin.z + t * dirZ;
            if (hx >= min.x - e && hx <= max.x + e && hz >= min.z - e && hz <= max.z + e)
                return t;

        } else if (min.z == max.z) {
            // XY-plane at z = min.z
            if (Math.abs(dirZ) < 1e-10)
                return -1;
            double t = (min.z - origin.z) / dirZ;
            if (t < 0)
                return -1;
            double hx = origin.x + t * dirX;
            double hy = origin.y + t * dirY;
            if (hx >= min.x - e && hx <= max.x + e && hy >= min.y - e && hy <= max.y + e)
                return t;
        }

        return -1;
    }

    /**
     * Cycles the face mode in order:
     * {@code BIDIRECTIONAL → INPUT → OUTPUT → CLOSED → BIDIRECTIONAL}
     */
    private static FaceMode cycleFaceMode(FaceMode current) {
        return switch (current) {
            case BIDIRECTIONAL -> FaceMode.INPUT;
            case INPUT -> FaceMode.OUTPUT;
            case OUTPUT -> FaceMode.CLOSED;
            case CLOSED -> FaceMode.BIDIRECTIONAL;
        };
    }

    @NullableDecl
    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }
}
