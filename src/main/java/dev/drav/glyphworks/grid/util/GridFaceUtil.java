package dev.drav.glyphworks.grid.util;

import javax.annotation.Nullable;

import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockFace;

import dev.drav.glyphworks.grid.component.FaceMode;
import dev.drav.glyphworks.grid.component.FacePlane;
import dev.drav.glyphworks.grid.component.GridComponent;

/** Shared spatial helpers for grid face calculations. */
public final class GridFaceUtil {

    private GridFaceUtil() {}

    /** Translates {@code pos} by one step in {@code face} direction. */
    public static Vector3i addOffset(Vector3i pos, BlockFace face) {
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
    public static Vector3i addOffset(Vector3i pos, Vector3i offset) {
        return new Vector3i(pos.x + offset.x, pos.y + offset.y, pos.z + offset.z);
    }

    /** Returns the face directly opposite to {@code face}. */
    public static BlockFace opposite(BlockFace face) {
        return switch (face) {
            case North -> BlockFace.South;
            case South -> BlockFace.North;
            case East  -> BlockFace.West;
            case West  -> BlockFace.East;
            case Up    -> BlockFace.Down;
            case Down  -> BlockFace.Up;
            default    -> BlockFace.None;
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
    public static FacePlane findMatchingFace(
            GridComponent component,
            Vector3i originPos,
            BlockFace requiredNormal,
            Vector3i requiredWorldPos) {
        for (FacePlane face : component.getFaces()) {
            if (face.getNormal() != requiredNormal)
                continue;
            if (addOffset(originPos, face.getPosition()).equals(requiredWorldPos))
                return face;
        }
        return null;
    }

    /**
     * Returns {@code true} when two face modes can form a grid connection.
     * Two inputs or two outputs cannot link to each other.
     */
    public static boolean areLinkable(FaceMode a, FaceMode b) {
        if (a == FaceMode.CLOSED || b == FaceMode.CLOSED)
            return false;
        if (a == FaceMode.INPUT  && b == FaceMode.INPUT)
            return false;
        if (a == FaceMode.OUTPUT && b == FaceMode.OUTPUT)
            return false;
        return true;
    }
}
