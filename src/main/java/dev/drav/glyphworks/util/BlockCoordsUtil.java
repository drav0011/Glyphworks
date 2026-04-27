package dev.drav.glyphworks.util;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.modules.block.BlockModule.BlockStateInfo;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

public final class BlockCoordsUtil {

    private BlockCoordsUtil() {
    }

    @Nullable
    public static int[] resolveCoords(
            @Nonnull Store<ChunkStore> store,
            @Nonnull BlockStateInfo blockStateInfo) {
        Ref<ChunkStore> chunkRef = blockStateInfo.getChunkRef();
        if (!chunkRef.isValid()) {
            return null;
        }

        BlockChunk blockChunk = store.getComponent(chunkRef, BlockChunk.getComponentType());
        if (blockChunk == null) {
            return null;
        }

        int idx = blockStateInfo.getIndex();
        int localX = ChunkUtil.xFromBlockInColumn(idx);
        int localY = ChunkUtil.yFromBlockInColumn(idx);
        int localZ = ChunkUtil.zFromBlockInColumn(idx);
        return new int[] {
                ChunkUtil.worldCoordFromLocalCoord(blockChunk.getX(), localX),
                localY,
                ChunkUtil.worldCoordFromLocalCoord(blockChunk.getZ(), localZ)
        };
    }
}