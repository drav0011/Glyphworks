package dev.drav.glyphworks.grid.lookup;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3i;
import org.joml.Vector3ic;

import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;

import dev.drav.glyphworks.fluid.util.FluidUtil;
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
        Ref<ChunkStore> blockRef = BlockModule.getBlockEntity(
                chunkStore.getWorld(), pos.x(), pos.y(), pos.z());

        Vector3i originPos = new Vector3i(pos);

        if (blockRef == null) {
            // pos may be a filler cell of a multi-block — resolve back to the origin.
            BlockSection blockSection = FluidUtil.getBlockSection(
                    chunkStore, chunkStore.getStore(), pos.x(), pos.y(), pos.z());
            if (blockSection == null)
                return null;

            int filler = blockSection.getFiller(pos.x(), pos.y(), pos.z());
            if (filler == FillerBlockUtil.NO_FILLER)
                return null;

            originPos = new Vector3i(
                    pos.x() - FillerBlockUtil.unpackX(filler),
                    pos.y() - FillerBlockUtil.unpackY(filler),
                    pos.z() - FillerBlockUtil.unpackZ(filler));

            blockRef = BlockModule.getBlockEntity(
                    chunkStore.getWorld(), originPos.x, originPos.y, originPos.z);
            if (blockRef == null)
                return null;
        }

        GridComponent component = chunkStore.getStore().getComponent(
                blockRef, GridComponent.getComponentType());
        if (component == null)
            return null;

        BlockSection originSection = FluidUtil.getBlockSection(
                chunkStore, chunkStore.getStore(), originPos.x, originPos.y, originPos.z);
        RotationTuple rotation = originSection != null
                ? RotationTuple.get(originSection.getRotationIndex(originPos.x, originPos.y, originPos.z))
                : RotationTuple.NONE;

        return new GridLookup(component, blockRef, originPos, rotation);
    }
}
