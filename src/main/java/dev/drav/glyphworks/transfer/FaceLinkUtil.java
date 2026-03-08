package dev.drav.glyphworks.transfer;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.drav.glyphworks.transfer.component.FaceKey;
import dev.drav.glyphworks.transfer.component.FacePlane;
import dev.drav.glyphworks.transfer.component.TransferComponent;

/**
 * Utility for linking and unlinking transfer network edges directly in FacePlane data.
 *
 * <p>Edges are stored as {@code FacePlane.neighborNodeId} — a nullable UUID persisted
 * on each face. Two matching face keys on adjacent blocks means the boundary is shared.
 * No external graph object is needed: the components themselves are the graph.
 *
 * <h3>Face boundary geometry</h3>
 * Each 1×1 face cell has exactly one "constant axis" (min.axis == max.axis = K).
 * The two blocks that share this boundary are at:
 * <pre>
 *   position  (min.x, min.y, K)     — block whose surface IS at K (e.g. SOUTH face of block at z=K)
 *   position  (min.x, min.y, K - 1) — block whose far wall   IS at K (e.g. NORTH face of block at z=K-d)
 * </pre>
 * We try both, skip the one that is our own block, and use the other as the candidate neighbor.
 */
public final class FaceLinkUtil {

    private FaceLinkUtil() {}

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Links all faces of {@code transfer} to any matching neighbor TransferComponents.
     * Safe to call on placement or on mode-change (already-linked faces are skipped).
     */
    public static void linkAll(
            TransferComponent transfer,
            Ref<ChunkStore> blockRef,
            ChunkStore chunkStore) {

        for (FacePlane face : transfer.getFaces().values()) {
            if (face.getNeighborNodeId() != null) continue; // already linked
            tryLink(transfer, blockRef, face, chunkStore);
        }
    }

    /**
     * Unlinks all neighbor faces that point to {@code transfer}, then clears
     * neighborNodeId on this block's own faces.
     * Call before removing the block from the world.
     */
    public static void unlinkAll(
            TransferComponent transfer,
            Ref<ChunkStore> blockRef,
            ChunkStore chunkStore) {

        for (FacePlane face : transfer.getFaces().values()) {
            if (face.getNeighborNodeId() == null) continue;
            clearNeighborSide(face, blockRef, chunkStore);
            face.setNeighborNodeId(null);
        }
    }

    /**
     * Re-evaluates a single face after its mode has changed.
     * Clears any existing link, then re-links if modes are now compatible.
     */
    public static void relinkFace(
            TransferComponent transfer,
            Ref<ChunkStore> blockRef,
            FacePlane face,
            ChunkStore chunkStore) {

        // Always clear the old link on both sides first
        if (face.getNeighborNodeId() != null) {
            clearNeighborSide(face, blockRef, chunkStore);
            face.setNeighborNodeId(null);
        }
        // Attempt fresh link
        tryLink(transfer, blockRef, face, chunkStore);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Tries both sides of the face boundary; links the first valid neighbor found. */
    private static void tryLink(
            TransferComponent transfer,
            Ref<ChunkStore> blockRef,
            FacePlane face,
            ChunkStore chunkStore) {

        Vector3i min = face.getPlaneMin();

        // Determine the two candidate positions based on constant axis
        Vector3i posA, posB;
        if (min.x == face.getPlaneMax().x) {
            int K = min.x;
            posA = new Vector3i(K,     min.y, min.z);
            posB = new Vector3i(K - 1, min.y, min.z);
        } else if (min.y == face.getPlaneMax().y) {
            int K = min.y;
            posA = new Vector3i(min.x, K,     min.z);
            posB = new Vector3i(min.x, K - 1, min.z);
        } else {
            int K = min.z;
            posA = new Vector3i(min.x, min.y, K);
            posB = new Vector3i(min.x, min.y, K - 1);
        }

        if (!tryLinkAt(transfer, blockRef, face, posA, chunkStore)) {
            tryLinkAt(transfer, blockRef, face, posB, chunkStore);
        }
    }

    /**
     * Tries to look up a TransferComponent at {@code neighborPos} and link if compatible.
     *
     * @return true if a link was established
     */
    private static boolean tryLinkAt(
            TransferComponent transfer,
            Ref<ChunkStore> blockRef,
            FacePlane face,
            Vector3i neighborPos,
            ChunkStore chunkStore) {

        Ref<ChunkStore> nChunkRef = chunkStore.getChunkReference(
                ChunkUtil.indexChunkFromBlock(neighborPos.x, neighborPos.z));
        if (nChunkRef == null || !nChunkRef.isValid()) return false;

        BlockComponentChunk bcc = chunkStore.getStore().getComponent(
                nChunkRef, BlockComponentChunk.getComponentType());
        if (bcc == null) return false;

        Ref<ChunkStore> nBlockRef = bcc.getEntityReference(
                ChunkUtil.indexBlockInColumn(neighborPos.x, neighborPos.y, neighborPos.z));
        if (nBlockRef == null || nBlockRef.equals(blockRef)) return false; // skip self

        TransferComponent neighbor = chunkStore.getStore().getComponent(
                nBlockRef, TransferComponent.getComponentType());
        if (neighbor == null) return false;

        FacePlane neighborFace = neighbor.getFaces().get(face.getFaceKey());
        if (neighborFace == null) return false;

        // Link both sides
        face.setNeighborNodeId(neighbor.getNodeId());
        neighborFace.setNeighborNodeId(transfer.getNodeId());
        return true;
    }

    /**
     * Finds the neighbor block for {@code face} and clears its neighborNodeId.
     * Does NOT touch {@code face} itself — caller is responsible for that.
     */
    private static void clearNeighborSide(
            FacePlane face,
            Ref<ChunkStore> blockRef,
            ChunkStore chunkStore) {

        Vector3i min = face.getPlaneMin();
        Vector3i posA, posB;
        if (min.x == face.getPlaneMax().x) {
            int K = min.x;
            posA = new Vector3i(K,     min.y, min.z);
            posB = new Vector3i(K - 1, min.y, min.z);
        } else if (min.y == face.getPlaneMax().y) {
            int K = min.y;
            posA = new Vector3i(min.x, K,     min.z);
            posB = new Vector3i(min.x, K - 1, min.z);
        } else {
            int K = min.z;
            posA = new Vector3i(min.x, min.y, K);
            posB = new Vector3i(min.x, min.y, K - 1);
        }

        FaceKey key = face.getFaceKey();
        clearNeighborAt(posA, blockRef, key, chunkStore);
        clearNeighborAt(posB, blockRef, key, chunkStore);
    }

    private static void clearNeighborAt(
            Vector3i neighborPos,
            Ref<ChunkStore> blockRef,
            FaceKey key,
            ChunkStore chunkStore) {

        Ref<ChunkStore> nChunkRef = chunkStore.getChunkReference(
                ChunkUtil.indexChunkFromBlock(neighborPos.x, neighborPos.z));
        if (nChunkRef == null || !nChunkRef.isValid()) return;

        BlockComponentChunk bcc = chunkStore.getStore().getComponent(
                nChunkRef, BlockComponentChunk.getComponentType());
        if (bcc == null) return;

        Ref<ChunkStore> nBlockRef = bcc.getEntityReference(
                ChunkUtil.indexBlockInColumn(neighborPos.x, neighborPos.y, neighborPos.z));
        if (nBlockRef == null || nBlockRef.equals(blockRef)) return;

        TransferComponent neighbor = chunkStore.getStore().getComponent(
                nBlockRef, TransferComponent.getComponentType());
        if (neighbor == null) return;

        FacePlane neighborFace = neighbor.getFaces().get(key);
        if (neighborFace != null) {
            neighborFace.setNeighborNodeId(null);
        }
    }
}
