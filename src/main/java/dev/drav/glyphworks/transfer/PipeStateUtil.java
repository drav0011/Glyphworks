package dev.drav.glyphworks.transfer;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.transfer.component.FaceKey;
import dev.drav.glyphworks.transfer.component.FaceMode;
import dev.drav.glyphworks.transfer.component.FacePlane;
import dev.drav.glyphworks.transfer.component.TransferComponent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.logging.Logger;

/**
 * Computes and applies the correct visual pipe state (e.g. "NS", "NEU",
 * "Single")
 * based on which adjacent blocks have a {@link TransferComponent} and which
 * faces
 * are not {@link FaceMode#CLOSED}.
 *
 * <h3>Direction conventions (Minecraft/Hytale standard)</h3>
 * 
 * <pre>
 * N = NORTH (-Z)   offset (0,  0, -1)
 * S = SOUTH (+Z)   offset (0,  0, +1)
 * E = EAST  (+X)   offset (+1, 0,  0)
 * W = WEST  (-X)   offset (-1, 0,  0)
 * U = UP    (+Y)   offset (0, +1,  0)
 * D = DOWN  (-Y)   offset (0, -1,  0)
 * </pre>
 */
public final class PipeStateUtil {

    private static final Logger LOGGER = Logger.getLogger(PipeStateUtil.class.getName());

    private static final int[][] OFFSETS = {
            { 0, 0, -1 }, // 0: N  (North = -Z, Minecraft/Hytale standard)
            { 0, 0, 1 },  // 1: S  (South = +Z)
            { 1, 0, 0 },  // 2: E
            { -1, 0, 0 }, // 3: W
            { 0, 1, 0 },  // 4: U
            { 0, -1, 0 }, // 5: D
    };
    private static final String[] LABELS = { "N", "S", "E", "W", "U", "D" };

    private PipeStateUtil() {
    }

    /**
     * Computes the pipe state name for the block at {@code pos}.
     * An arm is active when the neighbour has a {@link TransferComponent} AND
     * the face on THIS pipe toward that neighbour is not {@link FaceMode#CLOSED}.
     * Returns {@code "Single"} when no arms are active.
     */
    @Nonnull
    public static String computeStateName(
            @Nonnull TransferComponent pipe,
            @Nonnull World world,
            @Nonnull Vector3i pos) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            int dx = OFFSETS[i][0], dy = OFFSETS[i][1], dz = OFFSETS[i][2];
            String label = LABELS[i];

            Vector3i neighborPos = new Vector3i(pos.x + dx, pos.y + dy, pos.z + dz);
            TransferComponent neighbor = getTransferComponent(world, neighborPos);
            if (neighbor == null) {
                LOGGER.fine("[PipeState] " + pos + " dir=" + label + " → no transfer neighbour");
                continue;
            }

            // Hide arm if THIS pipe's face toward the neighbour is CLOSED
            FacePlane thisFace = getFaceByDirection(pipe, pos, dx, dy, dz);
            if (thisFace != null && thisFace.getMode() == FaceMode.CLOSED) {
                LOGGER.info("[PipeState] " + pos + " dir=" + label + " → THIS face CLOSED → arm hidden");
                continue;
            }

            // Also hide arm if the NEIGHBOUR's face back toward this pipe is CLOSED
            FacePlane neighborFace = getFaceByDirection(neighbor, neighborPos, -dx, -dy, -dz);
            if (neighborFace != null && neighborFace.getMode() == FaceMode.CLOSED) {
                LOGGER.info("[PipeState] " + pos + " dir=" + label + " → NEIGHBOUR face at " + neighborPos + " CLOSED → arm hidden");
                continue;
            }

            LOGGER.info("[PipeState] " + pos + " dir=" + label + " → arm ACTIVE"
                + " (thisFace=" + (thisFace == null ? "null" : thisFace.getMode())
                + " neighborFace=" + (neighborFace == null ? "null" : neighborFace.getMode()) + ")");
            sb.append(label);
        }
        return sb.length() == 0 ? "Single" : sb.toString();
    }

    /**
     * Updates the visual block state of the pipe at {@code pos}.
     * Does nothing if the block has no pipe state machine or no
     * {@link TransferComponent}.
     *
     * <p>{@code world.getBlockType} may return a state <em>variant</em> after a
     * previous {@code setBlockInteractionState} call (because the variant is now
     * the stored block type at that position). Variants have {@code this.state ==
     * null}, so {@code variant.getBlockForState(x)} always returns {@code null}.
     * We therefore call {@code getDefaultStateKey()} to navigate from any variant
     * back to the root block type that holds the state machine.
     */
    public static void updatePipeState(@Nonnull World world, @Nonnull Vector3i pos) {
        BlockType blockType = world.getBlockType(pos.x, pos.y, pos.z);
        if (blockType == null) {
            LOGGER.fine("[PipeState] updatePipeState " + pos + " → no blockType, skip");
            return;
        }

        // Navigate from a potential state variant to the root block type.
        // getDefaultStateKey() is null on the root (no parent container) and
        // non-null on variants (returns the root's asset key).
        String baseKey = blockType.getDefaultStateKey();
        BlockType rootBlockType = (baseKey != null)
                ? BlockType.getAssetMap().getAsset(baseKey)
                : blockType;

        if (rootBlockType == null || rootBlockType.getData() == null) {
            LOGGER.fine("[PipeState] updatePipeState " + pos + " → root block type not found or has no data, skip");
            return;
        }

        // Confirm this block has the pipe state machine by checking for "Single" state
        if (rootBlockType.getBlockForState("Single") == null) {
            LOGGER.fine("[PipeState] updatePipeState " + pos + " → blockType '" + blockType.getId() + "' has no 'Single' state, skip");
            return;
        }

        TransferComponent pipe = getTransferComponent(world, pos);
        if (pipe == null) {
            LOGGER.fine("[PipeState] updatePipeState " + pos + " → no TransferComponent, skip");
            return;
        }

        String stateName = computeStateName(pipe, world, pos);
        LOGGER.info("[PipeState] updatePipeState " + pos
                + " variant='" + blockType.getId() + "'"
                + " root='" + rootBlockType.getId() + "'"
                + " → state='" + stateName + "'");

        WorldChunk chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(pos.x, pos.z));
        if (chunk != null) {
            chunk.setBlockInteractionState(pos.x, pos.y, pos.z, rootBlockType, stateName, true);
        } else {
            LOGGER.warning("[PipeState] updatePipeState " + pos + " → chunk not loaded, state not applied");
        }
    }

    /**
     * Calls {@link #updatePipeState} for all 6 neighbours of {@code origin}.
     * Used after a block at {@code origin} is placed or broken so adjacent pipes
     * gain or lose their arm toward it.
     */
    public static void updateNeighborPipeStates(@Nonnull World world, @Nonnull Vector3i origin) {
        for (int[] off : OFFSETS) {
            updatePipeState(world, new Vector3i(origin.x + off[0], origin.y + off[1], origin.z + off[2]));
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    @Nullable
    private static TransferComponent getTransferComponent(@Nonnull World world, @Nonnull Vector3i pos) {
        ChunkStore chunkStore = world.getChunkStore();
        Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(
                ChunkUtil.indexChunkFromBlock(pos.x, pos.z));
        if (chunkRef == null || !chunkRef.isValid())
            return null;

        BlockComponentChunk bcc = chunkStore.getStore().getComponent(
                chunkRef, BlockComponentChunk.getComponentType());
        if (bcc == null)
            return null;

        Ref<ChunkStore> blockRef = bcc.getEntityReference(
                ChunkUtil.indexBlockInColumn(pos.x, pos.y, pos.z));
        if (blockRef == null)
            return null;

        return chunkStore.getStore().getComponent(blockRef, TransferComponent.getComponentType());
    }

    /**
     * Returns the {@link FacePlane} on {@code pipe} whose plane key matches the
     * wall of the 1×1×1 block at {@code pos} facing direction (dx, dy, dz), using
     * the same {@link FaceKey} convention as {@code initAndLogAllFaces}:
     *
     * <pre>
     * N (-Z):  min=(x,   y,   z  )  max=(x+1, y+1, z  )
     * S (+Z):  min=(x,   y,   z+1)  max=(x+1, y+1, z+1)
     * E (+X):  min=(x+1, y,   z  )  max=(x+1, y+1, z+1)
     * W (-X):  min=(x,   y,   z  )  max=(x,   y+1, z+1)
     * U (+Y):  min=(x,   y+1, z  )  max=(x+1, y+1, z+1)
     * D (-Y):  min=(x,   y,   z  )  max=(x+1, y,   z+1)
     * </pre>
     */
    @Nullable
    private static FacePlane getFaceByDirection(
            @Nonnull TransferComponent pipe,
            @Nonnull Vector3i pos,
            int dx, int dy, int dz) {
        int bx = pos.x, by = pos.y, bz = pos.z;
        Vector3i min, max;

        if (dz == 1) {
            min = new Vector3i(bx, by, bz + 1);
            max = new Vector3i(bx + 1, by + 1, bz + 1);
        } else if (dz == -1) {
            min = new Vector3i(bx, by, bz);
            max = new Vector3i(bx + 1, by + 1, bz);
        } else if (dx == 1) {
            min = new Vector3i(bx + 1, by, bz);
            max = new Vector3i(bx + 1, by + 1, bz + 1);
        } else if (dx == -1) {
            min = new Vector3i(bx, by, bz);
            max = new Vector3i(bx, by + 1, bz + 1);
        } else if (dy == 1) {
            min = new Vector3i(bx, by + 1, bz);
            max = new Vector3i(bx + 1, by + 1, bz + 1);
        } else {
            min = new Vector3i(bx, by, bz);
            max = new Vector3i(bx + 1, by, bz + 1);
        }
        return pipe.getFaces().get(new FaceKey(min, max));
    }
}
