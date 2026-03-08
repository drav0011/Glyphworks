package dev.drav.glyphworks.transfer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.transfer.component.FaceMode;
import dev.drav.glyphworks.transfer.component.FacePlane;
import dev.drav.glyphworks.transfer.component.TransferComponent;

/**
 * Computes the visual state name for a pipe block based on which adjacent faces are
 * linked and not {@link FaceMode#CLOSED}.
 *
 * <p>Registered in {@link TransferStateRegistry} at plugin init:
 * <pre>
 *     TransferStateRegistry.register("Glyphworks/Pipe/Transfer_PipeNode", PipeStateComputer::compute);
 * </pre>
 *
 * <h3>Direction conventions</h3>
 * <pre>
 * N = NORTH (+Z)   offset (0,  0, +1)
 * S = SOUTH (−Z)   offset (0,  0, −1)
 * E = EAST  (+X)   offset (+1, 0,  0)
 * W = WEST  (−X)   offset (−1, 0,  0)
 * U = UP    (+Y)   offset (0, +1,  0)
 * D = DOWN  (−Y)   offset (0, −1,  0)
 * </pre>
 */
public final class PipeStateComputer {

    private static final int[][] OFFSETS = {
            { 0, 0,  1 }, // 0: N  (North = +Z)
            { 0, 0, -1 }, // 1: S  (South = -Z)
            { 1, 0,  0 }, // 2: E
            { -1, 0, 0 }, // 3: W
            { 0, 1,  0 }, // 4: U
            { 0, -1, 0 }, // 5: D
    };
    private static final String[] LABELS = { "N", "S", "E", "W", "U", "D" };

    private PipeStateComputer() {}

    /**
     * Computes the pipe state name for the block at {@code pos}.
     * An arm is active when the neighbour has a {@link TransferComponent} AND
     * the face on THIS pipe toward that neighbour is not {@link FaceMode#CLOSED}.
     * Returns {@code "Single"} when no arms are active.
     *
     * <p>Implements {@link TransferStateComputer} — use {@code PipeStateComputer::compute}
     * when registering with {@link TransferStateRegistry}.
     */
    @Nonnull
    public static String compute(
            @Nonnull TransferComponent pipe,
            @Nonnull Vector3i pos,
            @Nonnull World world) {
        ChunkStore chunkStore = world.getChunkStore();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            int dx = OFFSETS[i][0], dy = OFFSETS[i][1], dz = OFFSETS[i][2];
            String label = LABELS[i];

            Vector3i neighborPos = new Vector3i(pos.x + dx, pos.y + dy, pos.z + dz);
            TransferLookup neighborLookup = TransferLookup.resolve(chunkStore, neighborPos);
            if (neighborLookup == null) continue;
            TransferComponent neighbor = neighborLookup.transfer();

            // Hide arm if THIS pipe's face toward the neighbour is CLOSED
            FacePlane thisFace = getFaceByDirection(pipe, pos, dx, dy, dz);
            if (thisFace != null && thisFace.getMode() == FaceMode.CLOSED) continue;

            // Also hide arm if the NEIGHBOUR's face back toward this pipe is CLOSED
            FacePlane neighborFace = getFaceByDirection(neighbor, neighborPos, -dx, -dy, -dz);
            if (neighborFace != null && neighborFace.getMode() == FaceMode.CLOSED) continue;

            sb.append(label);
        }
        return sb.length() == 0 ? "Single" : sb.toString();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the {@link FacePlane} on {@code pipe} whose world-absolute plane matches
     * the wall of the 1×1×1 block at {@code pos} facing direction {@code (dx, dy, dz)}.
     *
     * <pre>
     * N (+Z):  boundary z = bz+1  →  min=(bx,   by,   bz+1)  max=(bx+1, by+1, bz+1)
     * S (-Z):  boundary z = bz    →  min=(bx,   by,   bz  )  max=(bx+1, by+1, bz  )
     * E (+X):  boundary x = bx+1  →  min=(bx+1, by,   bz  )  max=(bx+1, by+1, bz+1)
     * W (-X):  boundary x = bx    →  min=(bx,   by,   bz  )  max=(bx,   by+1, bz+1)
     * U (+Y):  boundary y = by+1  →  min=(bx,   by+1, bz  )  max=(bx+1, by+1, bz+1)
     * D (-Y):  boundary y = by    →  min=(bx,   by,   bz  )  max=(bx+1, by,   bz+1)
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
        } else { // dy == -1
            min = new Vector3i(bx, by, bz);
            max = new Vector3i(bx + 1, by, bz + 1);
        }

        for (FacePlane face : pipe.getFaces().values()) {
            if (face.getWorldMin(pos).equals(min) && face.getWorldMax(pos).equals(max)) return face;
        }
        return null;
    }
}
