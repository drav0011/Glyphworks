package dev.drav.glyphworks.transfer.wiring;

import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;

import dev.drav.glyphworks.transfer.component.FaceMode;
import dev.drav.glyphworks.transfer.component.FacePlane;
import dev.drav.glyphworks.transfer.component.TransferComponent;

/**
 * Wires the nozzle-side inventory into the pipe face of an IO block (Inserter or Extractor).
 *
 * <h3>Nozzle geometry</h3>
 * Each IO block has exactly one non-CLOSED face — the <em>pipe face</em> — which connects to
 * the transfer network. The <em>nozzle</em> neighbor is the block on the <strong>opposite</strong>
 * side: it holds the real inventory that items flow into or out of.
 *
 * <p>The nozzle position is derived purely from the pipe face's world-space boundary:
 * <ol>
 *   <li>Identify the constant axis of the 1-unit-thick face plane.</li>
 *   <li>Compute the two candidate block positions that touch the boundary.</li>
 *   <li>Whichever candidate is <em>not</em> {@code blockPos} (self) is the pipe neighbor.</li>
 *   <li>Nozzle = {@code blockPos + (blockPos - pipeNeighbor)} — one step in the opposite dir.</li>
 * </ol>
 */
public final class IOInventoryWirer {

    private IOInventoryWirer() {}

    /** Wires the INPUT face of an Inserter to the nozzle-side inventory. */
    public static void wireInserter(TransferComponent transfer, Vector3i pos, ChunkStore chunkStore) {
        wire(transfer, pos, chunkStore);
    }

    /** Wires the OUTPUT face of an Extractor to the nozzle-side inventory. */
    public static void wireExtractor(TransferComponent transfer, Vector3i pos, ChunkStore chunkStore) {
        wire(transfer, pos, chunkStore);
    }

    // -------------------------------------------------------------------------

    private static void wire(TransferComponent transfer, Vector3i pos, ChunkStore chunkStore) {
        RotationTuple rotation = transfer.getRotation();

        for (FacePlane face : transfer.getFaces()) {
            if (face.getMode() == FaceMode.CLOSED) continue;

            Vector3i nozzle = computeNozzlePos(face, pos, rotation);
            if (nozzle == null) continue;

            ItemContainerBlock icb = resolveItemContainer(nozzle, chunkStore);
            // Always update — even to null — so stale refs are cleared when a nozzle block is broken.
            switch (face.getMode()) {
                case INPUT:
                    face.setInputInventory(icb != null ? icb.getItemContainer() : null);
                    break;
                case OUTPUT:
                    face.setOutputInventory(icb != null ? icb.getItemContainer() : null);
                    break;
                case BIDIRECTIONAL:
                    face.setInventory(icb != null ? icb.getItemContainer() : null);
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * Computes the nozzle position given a pipe face and block pos.
     * Returns null only for degenerate (non-flat) faces, which should never occur.
     */
    @Nullable
    private static Vector3i computeNozzlePos(FacePlane face, Vector3i blockPos, RotationTuple rotation) {
        Vector3i worldMin = face.getWorldMin(blockPos, rotation);
        Vector3i worldMax = face.getWorldMax(blockPos, rotation);

        // Identify the constant axis and the two blocks sharing that boundary plane.
        Vector3i posA, posB;
        if (worldMin.x == worldMax.x) {
            int K = worldMin.x;
            posA = new Vector3i(K,     worldMin.y, worldMin.z);
            posB = new Vector3i(K - 1, worldMin.y, worldMin.z);
        } else if (worldMin.y == worldMax.y) {
            int K = worldMin.y;
            posA = new Vector3i(worldMin.x, K,     worldMin.z);
            posB = new Vector3i(worldMin.x, K - 1, worldMin.z);
        } else if (worldMin.z == worldMax.z) {
            int K = worldMin.z;
            posA = new Vector3i(worldMin.x, worldMin.y, K);
            posB = new Vector3i(worldMin.x, worldMin.y, K - 1);
        } else {
            return null; // degenerate — should never happen for authored faces
        }

        // Whichever candidate IS blockPos is self; the other is the pipe neighbor.
        Vector3i pipeNeighbor;
        if (posA.x == blockPos.x && posA.y == blockPos.y && posA.z == blockPos.z) {
            pipeNeighbor = posB;
        } else if (posB.x == blockPos.x && posB.y == blockPos.y && posB.z == blockPos.z) {
            pipeNeighbor = posA;
        } else {
            return null; // neither candidate is self — invalid face config
        }

        // Nozzle is one step past self in the direction opposite the pipe.
        return new Vector3i(
                2 * blockPos.x - pipeNeighbor.x,
                2 * blockPos.y - pipeNeighbor.y,
                2 * blockPos.z - pipeNeighbor.z);
    }

    /**
     * Resolves an {@link ItemContainerBlock} at {@code pos}, following filler-cell offsets
     * for multi-block structures (e.g. large chests).
     */
    @Nullable
    private static ItemContainerBlock resolveItemContainer(Vector3i pos, ChunkStore chunkStore) {
        long chunkIndex = ChunkUtil.indexChunkFromBlock(pos.x, pos.z);

        Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(chunkIndex);
        if (chunkRef == null || !chunkRef.isValid()) return null;

        BlockComponentChunk bcc = chunkStore.getStore().getComponent(chunkRef, BlockComponentChunk.getComponentType());
        if (bcc == null) return null;

        Ref<ChunkStore> blockRef = bcc.getEntityReference(ChunkUtil.indexBlockInColumn(pos.x, pos.y, pos.z));

        if (blockRef == null) {
            // May be a filler cell — resolve to the multi-block origin.
            WorldChunk worldChunk = chunkStore.getWorld().getChunkIfLoaded(chunkIndex);
            if (worldChunk == null) return null;

            int filler = worldChunk.getFiller(pos.x, pos.y, pos.z);
            if (filler == 0) return null;

            Vector3i origin = new Vector3i(
                    pos.x - FillerBlockUtil.unpackX(filler),
                    pos.y - FillerBlockUtil.unpackY(filler),
                    pos.z - FillerBlockUtil.unpackZ(filler));

            long originChunkIndex = ChunkUtil.indexChunkFromBlock(origin.x, origin.z);
            Ref<ChunkStore> originChunkRef = chunkStore.getChunkReference(originChunkIndex);
            if (originChunkRef == null || !originChunkRef.isValid()) return null;

            BlockComponentChunk originBcc = chunkStore.getStore().getComponent(originChunkRef, BlockComponentChunk.getComponentType());
            if (originBcc == null) return null;

            blockRef = originBcc.getEntityReference(ChunkUtil.indexBlockInColumn(origin.x, origin.y, origin.z));
            if (blockRef == null) return null;
        }

        return chunkStore.getStore().getComponent(blockRef, ItemContainerBlock.getComponentType());
    }
}
