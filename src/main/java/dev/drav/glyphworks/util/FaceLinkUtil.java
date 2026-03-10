package dev.drav.glyphworks.util;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;

import dev.drav.glyphworks.transfer.component.FacePlane;
import dev.drav.glyphworks.transfer.component.TransferComponent;
import dev.drav.glyphworks.transfer.state.BlockStateNotifier;

/**
 * Utility for linking and unlinking transfer network edges directly in
 * FacePlane data.
 *
 * <h3>Face boundary geometry</h3>
 * Each 1×1 face cell has exactly one "constant axis" (min.axis == max.axis =
 * K).
 * The two blocks that share this boundary are at:
 * 
 * <pre>
 *   position  (min.x, min.y, K)     — block whose surface IS at K (e.g. SOUTH face of block at z=K)
 *   position  (min.x, min.y, K - 1) — block whose far wall   IS at K (e.g. NORTH face of block at z=K-d)
 * </pre>
 * 
 * We try both, skip the one that is our own block, and use the other as the
 * candidate neighbor.
 */
public final class FaceLinkUtil {

    private static final Logger LOGGER = Logger.getLogger(FaceLinkUtil.class.getName());

    private FaceLinkUtil() {
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Links all faces of {@code transfer} to any matching compatible neighbor
     * TransferComponents.
     * Safe to call on placement or on mode-change (already-linked faces are
     * skipped).
     *
     * <p>
     * If {@code notifier} is non-null, it is fired for {@code blockPos} and each
     * neighbor
     * position where a link was established.
     */
    public static void linkAll(
            TransferComponent transfer,
            Ref<ChunkStore> blockRef,
            Vector3i blockPos,
            ChunkStore chunkStore,
            @Nullable BlockStateNotifier notifier) {

        Set<Vector3i> changed = (notifier != null) ? new HashSet<>() : null;

        for (FacePlane face : transfer.getFaces()) {
            if (face.getNeighborNodeId() != null) {
                continue;
            }

            tryLink(transfer, blockRef, face, blockPos, chunkStore, changed);
        }

        if (notifier != null) {
            changed.add(blockPos); // always update self, even with no neighbors
            changed.forEach(notifier::onChanged);
        }
    }

    /**
     * Unlinks all neighbor faces that point to {@code transfer}, then clears
     * neighborNodeId on this block's own faces.
     * Call before removing the block from the world.
     *
     * <p>
     * If {@code notifier} is non-null, it is fired for each neighbor position that
     * was
     * unlinked. {@code blockPos} itself is NOT notified — the block is already
     * gone.
     */
    public static void unlinkAll(
            TransferComponent transfer,
            Ref<ChunkStore> blockRef,
            Vector3i blockPos,
            ChunkStore chunkStore,
            @Nullable BlockStateNotifier notifier) {

        Set<Vector3i> changed = (notifier != null) ? new HashSet<>() : null;

        for (FacePlane face : transfer.getFaces()) {
            if (face.getNeighborNodeId() == null) {
                continue;
            }

            Vector3i neighborPos = clearNeighborSide(face, blockRef, blockPos, chunkStore, transfer.getYaw());
            face.setNeighborNodeId(null);

            if (changed != null && neighborPos != null) {
                changed.add(neighborPos);
            }
        }

        markBccDirty(blockPos, chunkStore);

        if (notifier != null) {
            changed.forEach(notifier::onChanged);
        }
    }

    /**
     * Re-evaluates a single face after its mode has changed.
     * Clears any existing link, then re-links if modes are now compatible.
     *
     * <p>
     * If {@code notifier} is non-null, it is always fired for {@code blockPos}
     * (mode
     * changed regardless of link outcome) and for any neighbor positions affected.
     */
    public static void relinkFace(
            TransferComponent transfer,
            Ref<ChunkStore> blockRef,
            FacePlane face,
            Vector3i blockPos,
            ChunkStore chunkStore,
            @Nullable BlockStateNotifier notifier) {

        Set<Vector3i> changed = (notifier != null) ? new HashSet<>() : null;

        // Always clear the old link on both sides first
        if (face.getNeighborNodeId() != null) {
            Vector3i oldNeighborPos = clearNeighborSide(face, blockRef, blockPos, chunkStore, transfer.getYaw());

            face.setNeighborNodeId(null);
            markBccDirty(blockPos, chunkStore);

            if (changed != null && oldNeighborPos != null) {
                changed.add(oldNeighborPos);
            }
        }

        // Attempt fresh link
        tryLink(transfer, blockRef, face, blockPos, chunkStore, changed);
        
        if (notifier != null) {
            changed.add(blockPos); // always notify self — mode changed regardless of link outcome
            changed.forEach(notifier::onChanged);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Tries both sides of the face boundary; links the first valid compatible
     * neighbor found.
     * Returns true if a link was established.
     */
    private static boolean tryLink(
            TransferComponent transfer,
            Ref<ChunkStore> blockRef,
            FacePlane face,
            Vector3i blockPos,
            ChunkStore chunkStore,
            @Nullable Set<Vector3i> changed) {

        Vector3i min = face.getWorldMin(blockPos, transfer.getYaw());
        Vector3i max = face.getWorldMax(blockPos, transfer.getYaw());

        // Determine the two candidate positions based on constant axis
        Vector3i posA, posB;
        if (min.x == max.x) {
            int K = min.x;
            posA = new Vector3i(K, min.y, min.z);
            posB = new Vector3i(K - 1, min.y, min.z);
        } else if (min.y == max.y) {
            int K = min.y;
            posA = new Vector3i(min.x, K, min.z);
            posB = new Vector3i(min.x, K - 1, min.z);
        } else {
            int K = min.z;
            posA = new Vector3i(min.x, min.y, K);
            posB = new Vector3i(min.x, min.y, K - 1);
        }

        if (!tryLinkAt(transfer, blockRef, face, posA, blockPos, chunkStore, changed)) {
            return tryLinkAt(transfer, blockRef, face, posB, blockPos, chunkStore, changed);
        }
        return true;
    }

    /**
     * Tries to look up a TransferComponent at {@code neighborPos} and link if modes
     * are compatible.
     * Adds {@code neighborPos} to {@code changed} when a link is established.
     *
     * @return true if a link was established
     */
    private static boolean tryLinkAt(
            TransferComponent transfer,
            Ref<ChunkStore> blockRef,
            FacePlane face,
            Vector3i neighborPos,
            Vector3i blockPos,
            ChunkStore chunkStore,
            @Nullable Set<Vector3i> changed) {

        long neighborChunkIndex = ChunkUtil.indexChunkFromBlock(neighborPos.x, neighborPos.z);

        Ref<ChunkStore> nChunkRef = chunkStore.getChunkReference(neighborChunkIndex);
        if (nChunkRef == null || !nChunkRef.isValid()) {
            LOGGER.info("[tryLinkAt] No chunk ref at " + neighborPos);
            return false;
        }

        BlockComponentChunk bcc = chunkStore.getStore().getComponent(
                nChunkRef, BlockComponentChunk.getComponentType());
        if (bcc == null) {
            LOGGER.info("[tryLinkAt] No BCC at " + neighborPos);
            return false;
        }

        Ref<ChunkStore> nBlockRef = bcc.getEntityReference(
                ChunkUtil.indexBlockInColumn(neighborPos.x, neighborPos.y, neighborPos.z));

        // Track the resolved position (may change if neighborPos is a filler cell)
        Vector3i actualNeighborPos = neighborPos;

        if (nBlockRef == null) {
            // Check if this position is a filler cell of a multi-block structure
            WorldChunk worldChunk = chunkStore.getWorld().getChunkIfLoaded(neighborChunkIndex);
            if (worldChunk == null) {
                LOGGER.info("[tryLinkAt] No block entity and no WorldChunk at " + neighborPos);
                return false;
            }

            int filler = worldChunk.getFiller(neighborPos.x, neighborPos.y, neighborPos.z);
            if (filler == 0) {
                LOGGER.info("[tryLinkAt] No block entity and no filler at " + neighborPos);
                return false;
            }

            // Resolve filler offset back to origin block
            int originX = neighborPos.x - FillerBlockUtil.unpackX(filler);
            int originY = neighborPos.y - FillerBlockUtil.unpackY(filler);
            int originZ = neighborPos.z - FillerBlockUtil.unpackZ(filler);
            actualNeighborPos = new Vector3i(originX, originY, originZ);
            LOGGER.info("[tryLinkAt] Filler at " + neighborPos + " -> origin at " + actualNeighborPos);

            long originChunkIndex = ChunkUtil.indexChunkFromBlock(originX, originZ);
            Ref<ChunkStore> originChunkRef = chunkStore.getChunkReference(originChunkIndex);
            if (originChunkRef == null || !originChunkRef.isValid())
                return false;

            BlockComponentChunk originBcc = chunkStore.getStore().getComponent(
                    originChunkRef, BlockComponentChunk.getComponentType());
            if (originBcc == null)
                return false;

            nBlockRef = originBcc.getEntityReference(
                    ChunkUtil.indexBlockInColumn(originX, originY, originZ));
            if (nBlockRef == null) {
                LOGGER.info("[tryLinkAt] No block entity at filler origin " + actualNeighborPos);
                return false;
            }

            bcc = originBcc;
        }

        if (nBlockRef.equals(blockRef))
            return false; // skip self

        TransferComponent neighbor = chunkStore.getStore().getComponent(
                nBlockRef, TransferComponent.getComponentType());
        if (neighbor == null) {
            LOGGER.info("[tryLinkAt] No TransferComponent at " + actualNeighborPos);
            return false;
        }

        LOGGER.info("[tryLinkAt] Found neighbor at " + actualNeighborPos + " (nodeId=" + neighbor.getNodeId() + ")");

        // Scan neighbor faces for one whose world boundary matches ours (shared plane)
        Vector3i worldMin = face.getWorldMin(blockPos, transfer.getYaw());
        Vector3i worldMax = face.getWorldMax(blockPos, transfer.getYaw());
        LOGGER.info("[tryLinkAt] Self face: relMin=" + face.getRelMin() + ", relMax=" + face.getRelMax() + ", mode=" + face.getMode() + " -> worldMin=" + worldMin + ", worldMax=" + worldMax);

        FacePlane neighborFace = null;

        for (FacePlane nf : neighbor.getFaces()) {
            Vector3i nfMin = nf.getWorldMin(actualNeighborPos, neighbor.getYaw());
            Vector3i nfMax = nf.getWorldMax(actualNeighborPos, neighbor.getYaw());
            LOGGER.info("[tryLinkAt]   Neighbor face: worldMin=" + nfMin + ", worldMax=" + nfMax + ", mode=" + nf.getMode());
            if (nfMin.equals(worldMin) && nfMax.equals(worldMax)) {
                neighborFace = nf;
                break;
            }
        }
        
        if (neighborFace == null) {
            LOGGER.info("[tryLinkAt] No matching face found on neighbor");
            return false;
        }

        // Mode compatibility check — no link if data cannot flow in either direction
        FacePlane.EdgeType edge = FacePlane.checkModeCompatibility(face.getMode(), neighborFace.getMode());
        if (edge == null)
            return false;

        // Link both sides
        face.setNeighborNodeId(neighbor.getNodeId());
        neighborFace.setNeighborNodeId(transfer.getNodeId());

        // Persist changes: mark both chunks dirty so neighborNodeId is saved to disk.
        markBccDirty(blockPos, chunkStore);
        bcc.markNeedsSaving();

        if (changed != null)
            changed.add(neighborPos);
        
        return true;
    }

    /**
     * Finds the neighbor block for {@code face} and clears its neighborNodeId.
     * Does NOT touch {@code face} itself — caller is responsible for that.
     *
     * @return the neighbor position that was cleared, or {@code null} if none found
     */
    @Nullable
    private static Vector3i clearNeighborSide(
            FacePlane face,
            Ref<ChunkStore> blockRef,
            Vector3i blockPos,
            ChunkStore chunkStore,
            Rotation yaw) {

        Vector3i min = face.getWorldMin(blockPos, yaw);
        Vector3i max = face.getWorldMax(blockPos, yaw);
        Vector3i posA, posB;

        if (min.x == max.x) {
            int K = min.x;
            posA = new Vector3i(K, min.y, min.z);
            posB = new Vector3i(K - 1, min.y, min.z);
        } else if (min.y == max.y) {
            int K = min.y;
            posA = new Vector3i(min.x, K, min.z);
            posB = new Vector3i(min.x, K - 1, min.z);
        } else {
            int K = min.z;
            posA = new Vector3i(min.x, min.y, K);
            posB = new Vector3i(min.x, min.y, K - 1);
        }

        Vector3i worldMin = face.getWorldMin(blockPos, yaw);
        Vector3i worldMax = face.getWorldMax(blockPos, yaw);

        if (clearNeighborAt(posA, blockRef, worldMin, worldMax, chunkStore))
            return posA;
        if (clearNeighborAt(posB, blockRef, worldMin, worldMax, chunkStore))
            return posB;

        return null;
    }

    private static boolean clearNeighborAt(
            Vector3i neighborPos,
            Ref<ChunkStore> blockRef,
            Vector3i worldMin,
            Vector3i worldMax,
            ChunkStore chunkStore) {

        Ref<ChunkStore> nChunkRef = chunkStore.getChunkReference(
                ChunkUtil.indexChunkFromBlock(neighborPos.x, neighborPos.z));
        if (nChunkRef == null || !nChunkRef.isValid())
            return false;

        BlockComponentChunk bcc = chunkStore.getStore().getComponent(
                nChunkRef, BlockComponentChunk.getComponentType());
        if (bcc == null)
            return false;

        Ref<ChunkStore> nBlockRef = bcc.getEntityReference(
                ChunkUtil.indexBlockInColumn(neighborPos.x, neighborPos.y, neighborPos.z));
        if (nBlockRef == null || nBlockRef.equals(blockRef))
            return false;

        TransferComponent neighbor = chunkStore.getStore().getComponent(
                nBlockRef, TransferComponent.getComponentType());
        if (neighbor == null)
            return false;

        Rotation neighborYaw = neighbor.getYaw();
        for (FacePlane nf : neighbor.getFaces()) {
            if (nf.getWorldMin(neighborPos, neighborYaw).equals(worldMin) && nf.getWorldMax(neighborPos, neighborYaw).equals(worldMax)) {
                nf.setNeighborNodeId(null);
                bcc.markNeedsSaving();
                return true;
            }
        }
        
        return false;
    }

    private static void markBccDirty(Vector3i pos, ChunkStore chunkStore) {
        Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(
                ChunkUtil.indexChunkFromBlock(pos.x, pos.z));
        if (chunkRef == null) return;
        BlockComponentChunk bcc = chunkStore.getStore().getComponent(
                chunkRef, BlockComponentChunk.getComponentType());
        if (bcc != null) bcc.markNeedsSaving();
    }
}
