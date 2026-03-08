package dev.drav.glyphworks.util;

import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.asset.type.blockhitbox.BlockBoundingBoxes;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Pure static utilities for raycasting against a block's physical hitbox
 * detail boxes.
 *
 * <p>Separation rationale: these operations are independent of any
 * Glyphworks-specific concept (pipes, faces, modes) and can be reused by any
 * block interaction that needs sub-shape precision.
 */
public final class BlockHitboxRaycast {

    /** Sentinel return value for {@link #rayAABB} when the ray misses. */
    public static final double MISS = -1.0;

    private BlockHitboxRaycast() {}

    // ─────────────────────────────────────────────────────────────────────────
    // Value type
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Immutable result from a successful block hitbox raycast.
     */
    public static final class BlockHitResult {

        /** Index into {@link BlockBoundingBoxes.RotatedVariantBoxes#getDetailBoxes()}. */
        public final int detailBoxIndex;

        /**
         * The detail box that was hit, expressed in block-local 0–1 space
         * (i.e. the raw value from the asset — NOT shifted to world coords).
         */
        public final Box detailBox;

        /**
         * Ray parameter {@code t}: distance from the ray origin along the ray
         * direction at which the intersection occurs.
         * Use {@code origin + direction * t} to recover the world-space hit point.
         */
        public final double t;

        /** World-space position of the intersection point. */
        public final Vector3d hitPoint;

        private BlockHitResult(int detailBoxIndex, @Nonnull Box detailBox,
                               double t, @Nonnull Vector3d hitPoint) {
            this.detailBoxIndex = detailBoxIndex;
            this.detailBox      = detailBox;
            this.t              = t;
            this.hitPoint       = hitPoint;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Primitive
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Slab-method ray vs. axis-aligned bounding box test.
     *
     * @param ox  ray origin X (world space)
     * @param oy  ray origin Y
     * @param oz  ray origin Z
     * @param dx  ray direction X
     * @param dy  ray direction Y
     * @param dz  ray direction Z
     * @param minX world-space AABB minimum X
     * @param minY world-space AABB minimum Y
     * @param minZ world-space AABB minimum Z
     * @param maxX world-space AABB maximum X
     * @param maxY world-space AABB maximum Y
     * @param maxZ world-space AABB maximum Z
     * @return entry {@code t ≥ 0} on a hit, or {@link #MISS} if the ray misses
     *         or the box is behind the origin
     */
    public static double rayAABB(
            double ox, double oy, double oz,
            double dx, double dy, double dz,
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ) {

        double tmin = 0.0;
        double tmax = Double.MAX_VALUE;

        // X slab
        if (Math.abs(dx) < 1e-10) {
            if (ox < minX || ox > maxX) return MISS;
        } else {
            double t1 = (minX - ox) / dx;
            double t2 = (maxX - ox) / dx;
            if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
            tmin = Math.max(tmin, t1);
            tmax = Math.min(tmax, t2);
            if (tmin > tmax) return MISS;
        }

        // Y slab
        if (Math.abs(dy) < 1e-10) {
            if (oy < minY || oy > maxY) return MISS;
        } else {
            double t1 = (minY - oy) / dy;
            double t2 = (maxY - oy) / dy;
            if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
            tmin = Math.max(tmin, t1);
            tmax = Math.min(tmax, t2);
            if (tmin > tmax) return MISS;
        }

        // Z slab
        if (Math.abs(dz) < 1e-10) {
            if (oz < minZ || oz > maxZ) return MISS;
        } else {
            double t1 = (minZ - oz) / dz;
            double t2 = (maxZ - oz) / dz;
            if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
            tmin = Math.max(tmin, t1);
            tmax = Math.min(tmax, t2);
            if (tmin > tmax) return MISS;
        }

        return tmin >= 0.0 ? tmin : MISS;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Hitbox resolution
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Looks up the rotation-correct {@link BlockBoundingBoxes.RotatedVariantBoxes}
     * for the block at {@code (bx, by, bz)}.
     *
     * @return the variant boxes, or {@code null} if the chunk is not loaded,
     *         the block type is unknown, or it has no registered hitbox
     */
    @Nullable
    public static BlockBoundingBoxes.RotatedVariantBoxes resolveVariantBoxes(
            @Nonnull World world, int bx, int by, int bz) {

        WorldChunk chunk = world.getChunk(ChunkUtil.indexChunkFromBlock(bx, bz));
        if (chunk == null) return null;

        BlockType blockType = world.getBlockType(bx, by, bz);
        if (blockType == null) return null;

        BlockBoundingBoxes hitboxes =
                BlockBoundingBoxes.getAssetMap().getAsset(blockType.getHitboxTypeIndex());
        if (hitboxes == null) return null;

        int rotationIndex = chunk.getRotationIndex(bx, by, bz);
        return hitboxes.get(rotationIndex);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Detail-box picker
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Casts a ray against every detail box of {@code rotated} and returns the
     * closest hit to the ray origin.
     *
     * <p>The detail boxes are stored in block-local 0–1 space; they are shifted
     * to world space using {@code (bx, by, bz)} before the intersection test.
     *
     * @param ox       ray origin X (world space)
     * @param oy       ray origin Y
     * @param oz       ray origin Z
     * @param dx       ray direction X
     * @param dy       ray direction Y
     * @param dz       ray direction Z
     * @param bx       block grid X
     * @param by       block grid Y
     * @param bz       block grid Z
     * @param rotated  pre-resolved variant boxes for this block (see
     *                 {@link #resolveVariantBoxes})
     * @param maxReach maximum allowable {@code t}; hits beyond this are ignored
     * @return the closest {@link BlockHitResult}, or {@code null} if no detail
     *         box was hit within {@code maxReach}
     */
    @Nullable
    public static BlockHitResult raycastBlock(
            double ox, double oy, double oz,
            double dx, double dy, double dz,
            int bx, int by, int bz,
            @Nonnull BlockBoundingBoxes.RotatedVariantBoxes rotated,
            double maxReach) {

        Box[]  details  = rotated.getDetailBoxes();
        int    bestIdx  = -1;
        double bestT    = Double.MAX_VALUE;

        for (int i = 0; i < details.length; i++) {
            Box d = details[i];
            double t = rayAABB(
                    ox, oy, oz, dx, dy, dz,
                    bx + d.min.x, by + d.min.y, bz + d.min.z,
                    bx + d.max.x, by + d.max.y, bz + d.max.z);
            if (t != MISS && t < bestT && t <= maxReach) {
                bestT   = t;
                bestIdx = i;
            }
        }

        if (bestIdx < 0) return null;

        Vector3d hitPoint = new Vector3d(ox + dx * bestT,
                                         oy + dy * bestT,
                                         oz + dz * bestT);
        return new BlockHitResult(bestIdx, details[bestIdx], bestT, hitPoint);
    }

    /**
     * Convenience overload: resolves the block hitboxes from the world and then
     * performs the raycast in one call.
     *
     * @return the closest {@link BlockHitResult}, or {@code null} if the chunk
     *         is not loaded, the block has no hitbox, or the ray misses
     */
    @Nullable
    public static BlockHitResult raycastBlock(
            @Nonnull World world,
            double ox, double oy, double oz,
            double dx, double dy, double dz,
            int bx, int by, int bz,
            double maxReach) {

        BlockBoundingBoxes.RotatedVariantBoxes rotated =
                resolveVariantBoxes(world, bx, by, bz);
        if (rotated == null) return null;

        return raycastBlock(ox, oy, oz, dx, dy, dz, bx, by, bz, rotated, maxReach);
    }
}
