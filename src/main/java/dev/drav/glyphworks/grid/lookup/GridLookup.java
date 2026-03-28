package dev.drav.glyphworks.grid.lookup;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3i;
import org.joml.Vector3ic;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;

import dev.drav.glyphworks.grid.component.GridComponent;

/**
 * Holds the result of resolving a {@link GridComponent} and its entity
 * {@link Ref} from a world position. Returns {@code null} when the chunk is not
 * loaded, the block entity doesn't exist, or the block has no
 * {@link GridComponent}.
 *
 * <p>
 * Filler cells of multi-block structures are transparently redirected to their
 * origin block before the component lookup, so callers never need to handle
 * fillers.
 */
public record GridLookup(
        @Nonnull GridComponent component,
        @Nonnull Ref<ChunkStore> blockRef,
        @Nonnull Vector3i originPos,
        @Nonnull RotationTuple rotation) {

    @Nullable
    public static GridLookup resolve(@Nonnull ChunkStore chunkStore, @Nonnull Vector3ic pos) {
        long chunkIndex = ChunkUtil.indexChunkFromBlock(pos.x(), pos.z());

        Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(chunkIndex);
        if (chunkRef == null || !chunkRef.isValid())
            return null;

        BlockComponentChunk bcc = chunkStore.getStore().getComponent(
                chunkRef, BlockComponentChunk.getComponentType());
        if (bcc == null)
            return null;

        Ref<ChunkStore> blockRef = bcc.getEntityReference(
                ChunkUtil.indexBlockInColumn(pos.x(), pos.y(), pos.z()));

        Vector3i originPos = new Vector3i(pos);

        if (blockRef == null) {
            // pos may be a filler cell of a multi-block — resolve back to the origin.
            WorldChunk worldChunk = chunkStore.getWorld().getChunkIfLoaded(chunkIndex);
            if (worldChunk == null)
                return null;

            int filler = worldChunk.getFiller(pos.x(), pos.y(), pos.z());
            if (filler == FillerBlockUtil.NO_FILLER)
                return null;

            originPos = new Vector3i(
                    pos.x() - FillerBlockUtil.unpackX(filler),
                    pos.y() - FillerBlockUtil.unpackY(filler),
                    pos.z() - FillerBlockUtil.unpackZ(filler));

            long originChunkIndex = ChunkUtil.indexChunkFromBlock(originPos.x, originPos.z);
            Ref<ChunkStore> originChunkRef = chunkStore.getChunkReference(originChunkIndex);
            if (originChunkRef == null || !originChunkRef.isValid())
                return null;

            BlockComponentChunk originBcc = chunkStore.getStore().getComponent(
                    originChunkRef, BlockComponentChunk.getComponentType());
            if (originBcc == null)
                return null;

            blockRef = originBcc.getEntityReference(
                    ChunkUtil.indexBlockInColumn(originPos.x, originPos.y, originPos.z));
            if (blockRef == null)
                return null;
        }

        GridComponent component = chunkStore.getStore().getComponent(
                blockRef, GridComponent.getComponentType());
        if (component == null)
            return null;

        long originChunkIdx = ChunkUtil.indexChunkFromBlock(originPos.x, originPos.z);
        WorldChunk originWorldChunk = chunkStore.getWorld().getChunkIfLoaded(originChunkIdx);
        RotationTuple rotation = RotationTuple.NONE;
        if (originWorldChunk != null) {
            rotation = RotationTuple.get(originWorldChunk.getRotationIndex(originPos.x, originPos.y, originPos.z));
        }

        return new GridLookup(component, blockRef, originPos, rotation);
    }
}
