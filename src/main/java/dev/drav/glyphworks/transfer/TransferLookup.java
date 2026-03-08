package dev.drav.glyphworks.transfer;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.drav.glyphworks.transfer.component.TransferComponent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Holds the result of resolving a {@link TransferComponent} and its entity {@link Ref}
 * from a world position. Use {@link #resolve(ChunkStore, Vector3i)} to look up a block;
 * returns {@code null} when the chunk is not loaded, the block entity doesn't exist, or
 * the block has no {@link TransferComponent}.
 */
public record TransferLookup(
        @Nonnull TransferComponent transfer,
        @Nonnull Ref<ChunkStore> blockRef) {

    /**
     * Resolves the {@link TransferComponent} and its entity reference for the block at
     * {@code pos}. Returns {@code null} on any failure (unloaded chunk, missing entity,
     * no {@link TransferComponent}).
     */
    @Nullable
    public static TransferLookup resolve(
            @Nonnull ChunkStore chunkStore,
            @Nonnull Vector3i pos) {
        Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(
                ChunkUtil.indexChunkFromBlock(pos.x, pos.z));
        if (chunkRef == null || !chunkRef.isValid()) return null;

        BlockComponentChunk bcc = chunkStore.getStore().getComponent(
                chunkRef, BlockComponentChunk.getComponentType());
        if (bcc == null) return null;

        Ref<ChunkStore> blockRef = bcc.getEntityReference(
                ChunkUtil.indexBlockInColumn(pos.x, pos.y, pos.z));
        if (blockRef == null) return null;

        TransferComponent transfer = chunkStore.getStore().getComponent(
                blockRef, TransferComponent.getComponentType());
        if (transfer == null) return null;

        return new TransferLookup(transfer, blockRef);
    }
}
