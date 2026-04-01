package dev.drav.glyphworks.grid.util;

import javax.annotation.Nullable;

import org.joml.Vector3i;

import com.hypixel.hytale.protocol.BlockFace;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;

import dev.drav.glyphworks.grid.component.FaceMode;
import dev.drav.glyphworks.grid.component.FacePlane;
import dev.drav.glyphworks.grid.component.GridTypeEntry;

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
     * Rotates a local-space filler-cell offset vector to world space using the
     * block's {@link RotationTuple}. For 1×1 blocks the position is always
     * {@code (0,0,0)} so this is a no-op, but for multi-block structures it
     * maps the JSON-space filler offset to the correct world-space offset.
     */
    public static Vector3i rotateFacePosition(Vector3i localPos, RotationTuple rotation) {
        if (rotation.yaw() == Rotation.None && rotation.pitch() == Rotation.None && rotation.roll() == Rotation.None)
            return localPos;
        Vector3i rotated = new Vector3i(localPos.x, localPos.y, localPos.z);
        Rotation.applyRotationTo(rotated, rotation.yaw(), rotation.pitch(), rotation.roll());
        return rotated;
    }

    /**
     * Rotates a local-space {@link BlockFace} to world-space using the block's
     * {@link RotationTuple}. Returns {@link BlockFace#None} for unrecognised
     * direction vectors after rotation.
     */
    public static BlockFace rotateBlockFace(BlockFace face, RotationTuple rotation) {
        if (face == BlockFace.None)
            return BlockFace.None;
        if (rotation.yaw() == Rotation.None && rotation.pitch() == Rotation.None && rotation.roll() == Rotation.None)
            return face;
        Vector3i dir = blockFaceToVec(face);
        Rotation.applyRotationTo(dir, rotation.yaw(), rotation.pitch(), rotation.roll());
        return vecToBlockFace(dir);
    }

    private static Vector3i blockFaceToVec(BlockFace face) {
        return switch (face) {
            case North -> new Vector3i( 0,  0, -1);
            case South -> new Vector3i( 0,  0,  1);
            case East  -> new Vector3i( 1,  0,  0);
            case West  -> new Vector3i(-1,  0,  0);
            case Up    -> new Vector3i( 0,  1,  0);
            case Down  -> new Vector3i( 0, -1,  0);
            default    -> new Vector3i( 0,  0,  0);
        };
    }

    private static BlockFace vecToBlockFace(Vector3i v) {
        if (v.x ==  0 && v.y ==  0 && v.z == -1) return BlockFace.North;
        if (v.x ==  0 && v.y ==  0 && v.z ==  1) return BlockFace.South;
        if (v.x ==  1 && v.y ==  0 && v.z ==  0) return BlockFace.East;
        if (v.x == -1 && v.y ==  0 && v.z ==  0) return BlockFace.West;
        if (v.x ==  0 && v.y ==  1 && v.z ==  0) return BlockFace.Up;
        if (v.x ==  0 && v.y == -1 && v.z ==  0) return BlockFace.Down;
        return BlockFace.None;
    }

    /**
     * Finds the face on {@code entry} whose world-space normal (after applying
     * {@code componentRotation}) matches {@code requiredNormal} AND whose world
     * position ({@code originPos + face.getPosition()}) equals {@code requiredWorldPos}.
     *
     * <p>The position check is essential for multi-block structures: a 2×2×2 block
     * may have multiple faces with the same normal on different cells; only the one
     * spatially adjacent to the caller's face should link.
     */
    @Nullable
    public static FacePlane findMatchingFace(
            GridTypeEntry entry,
            Vector3i originPos,
            RotationTuple componentRotation,
            BlockFace requiredNormal,
            Vector3i requiredWorldPos) {
        for (FacePlane face : entry.getFaces()) {
            if (rotateBlockFace(face.getNormal(), componentRotation) != requiredNormal)
                continue;
            Vector3i worldFacePos = rotateFacePosition(face.getPosition(), componentRotation);
            if (addOffset(originPos, worldFacePos).equals(requiredWorldPos))
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
