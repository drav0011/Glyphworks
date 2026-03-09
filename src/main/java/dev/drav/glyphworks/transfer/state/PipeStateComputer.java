package dev.drav.glyphworks.transfer.state;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.universe.world.World;

import dev.drav.glyphworks.transfer.component.FaceMode;
import dev.drav.glyphworks.transfer.component.FacePlane;
import dev.drav.glyphworks.transfer.component.TransferComponent;

/**
 * Computes the visual state name for a pipe block based on which faces are linked.
 *
 * <p>Face relMin/relMax are stored in north-facing (authored) coordinates.
 * The block's placed yaw is applied when computing the world direction of each face,
 * so the state label (N/S/E/W/U/D) always reflects the actual world axis.
 */
public final class PipeStateComputer {

    private PipeStateComputer() {}

    @Nonnull
    public static String compute(
            @Nonnull TransferComponent pipe,
            @Nonnull Vector3i pos,
            @Nonnull World world) {
        Rotation yaw = pipe.getYaw();
        StringBuilder sb = new StringBuilder();

        for (FacePlane face : pipe.getFaces().values()) {
            if (face.getMode() == FaceMode.CLOSED) continue;
            if (face.getNeighborNodeId() == null) continue;

            String label = worldDirectionLabel(face.getRelMin(), face.getRelMax(), yaw);
            if (label != null) sb.append(label);
        }

        return sb.length() == 0 ? "Single" : sb.toString();
    }

    /**
     * Determines the world-direction label (N/S/E/W/U/D) of a face by rotating
     * its north-facing relMin/relMax by the block's yaw.
     */
    @Nullable
    private static String worldDirectionLabel(Vector3i relMin, Vector3i relMax, Rotation yaw) {
        Vector3i ra = rotateYaw(relMin, yaw);
        Vector3i rb = rotateYaw(relMax, yaw);
        int minX = Math.min(ra.x, rb.x), maxX = Math.max(ra.x, rb.x);
        int minY = Math.min(ra.y, rb.y), maxY = Math.max(ra.y, rb.y);
        int minZ = Math.min(ra.z, rb.z), maxZ = Math.max(ra.z, rb.z);

        if (minX == maxX) return minX == 1 ? "E" : "W";
        if (minY == maxY) return minY == 1 ? "U" : "D";
        if (minZ == maxZ) return minZ == 1 ? "N" : "S";
        return null; // malformed face
    }

    private static Vector3i rotateYaw(Vector3i v, Rotation yaw) {
        switch (yaw) {
            case Ninety:     return new Vector3i(v.z,     v.y, 1 - v.x);
            case OneEighty:  return new Vector3i(1 - v.x, v.y, 1 - v.z);
            case TwoSeventy: return new Vector3i(1 - v.z, v.y, v.x);
            default:         return new Vector3i(v.x,     v.y, v.z);
        }
    }
}
