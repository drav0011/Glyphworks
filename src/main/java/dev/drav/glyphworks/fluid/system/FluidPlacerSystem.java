package dev.drav.glyphworks.fluid.system;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3i;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.BlockFace;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.asset.type.fluid.Fluid;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.fluid.FluidStack;
import dev.drav.glyphworks.fluid.util.FluidUtil;
import dev.drav.glyphworks.fluid.component.FluidContainerComponent;
import dev.drav.glyphworks.fluid.component.FluidPlacerComponent;
import dev.drav.glyphworks.fluid.container.FluidContainer;
import dev.drav.glyphworks.fluid.transaction.FluidStackSlotTransaction;
import dev.drav.glyphworks.grid.util.GridFaceUtil;

/**
 * Ticking system for fluid placer blocks.
 *
 * <p>
 * Each tick, for every block that has both a {@link FluidPlacerComponent}
 * and a {@link FluidContainerComponent}, the system:
 * <ol>
 * <li>Skips if the container holds less than 1 000 L or has no locked
 * fluid.</li>
 * <li>Rotates the component's {@code targetNormal} and {@code targetPosition}
 * by the block's placed rotation to find the world cell to fill.</li>
 * <li>Checks if that world cell is empty (no fluid).</li>
 * <li>If so, drains 1 000 L from the container and places a fluid source block
 * there.</li>
 * </ol>
 * The fluid grid fills the container from connected tanks.
 */
public final class FluidPlacerSystem extends EntityTickingSystem<ChunkStore> {

    private static final short SLOT_INDEX = 0;

    @Override
    public Query<ChunkStore> getQuery() {
        return Query.and(
                FluidPlacerComponent.getComponentType(),
                FluidContainerComponent.getComponentType());
    }

    @Override
    public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<ChunkStore> chunk,
            @Nonnull Store<ChunkStore> store,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer) {

        FluidContainerComponent fcc = chunk.getComponent(index, FluidContainerComponent.getComponentType());
        if (fcc == null) {
            return;
        }
        FluidContainer fc = fcc.getFluidContainer();
        FluidStack slotStack = fc.getFluidStack(SLOT_INDEX);
        if (slotStack == null || slotStack.getAmount() < FluidUtil.MB_PER_BLOCK) {
            return;
        }

        String placerFluidId = slotStack.getFluidId();
        if (placerFluidId == null) {
            return;
        }

        FluidPlacerComponent placer = chunk.getComponent(index, FluidPlacerComponent.getComponentType());
        if (placer == null) {
            return;
        }

        BlockModule.BlockStateInfo bsi = chunk.getComponent(index, BlockModule.BlockStateInfo.getComponentType());
        if (bsi == null) {
            return;
        }
        if (!bsi.getChunkRef().isValid()) {
            return;
        }

        ChunkStore chunkStore = commandBuffer.getExternalData();
        BlockChunk blockChunk = store.getComponent(bsi.getChunkRef(), BlockChunk.getComponentType());
        if (blockChunk == null) {
            return;
        }

        int bi = bsi.getIndex();
        int localX = ChunkUtil.xFromBlockInColumn(bi);
        int localY = ChunkUtil.yFromBlockInColumn(bi);
        int localZ = ChunkUtil.zFromBlockInColumn(bi);
        Vector3i originPos = new Vector3i(
                ChunkUtil.worldCoordFromLocalCoord(blockChunk.getX(), localX),
                localY,
                ChunkUtil.worldCoordFromLocalCoord(blockChunk.getZ(), localZ));

        BlockSection blockSection = FluidUtil.getBlockSection(chunkStore, store, originPos.x, originPos.y, originPos.z);
        if (blockSection == null) {
            return;
        }
        RotationTuple rotation = RotationTuple.get(blockSection.getRotationIndex(localX, localY, localZ));

        Vector3i adjacent = resolveTargetCell(originPos, placer, rotation);
        if (adjacent == null) {
            return;
        }

        FluidSection fs = FluidUtil.getFluidSection(chunkStore, store, adjacent);
        if (fs == null) {
            return;
        }

        BlockSection adjBlockSection = FluidUtil.getBlockSection(chunkStore, store, adjacent.x, adjacent.y, adjacent.z);
        if (adjBlockSection == null) {
            return;
        }
        int adjLocalX = ChunkUtil.localCoordinate(adjacent.x);
        int adjLocalZ = ChunkUtil.localCoordinate(adjacent.z);
        if (adjBlockSection.get(adjLocalX, adjacent.y, adjLocalZ) != FluidUtil.EMPTY_BLOCK_ID) {
            return;
        }

        int existingFluidId = fs.getFluidId(adjacent.x, adjacent.y, adjacent.z);
        if (existingFluidId != FluidUtil.EMPTY_FLUID_ID) {
            return;
        }

        int indexedId = Fluid.getAssetMap().getIndex(placerFluidId);
        if (indexedId <= FluidUtil.EMPTY_FLUID_ID) {
            return;
        }

        Fluid fluid = Fluid.getAssetMap().getAsset(indexedId);
        if (fluid == null) {
            return;
        }

        FluidStackSlotTransaction drainTx = fc.removeFluidStackFromSlot(SLOT_INDEX, FluidUtil.MB_PER_BLOCK, true,
                false);
        if (drainTx.succeeded()) {
            fs.setFluid(adjacent.x, adjacent.y, adjacent.z, indexedId, (byte) fluid.getMaxFluidLevel());
            Ref<ChunkStore> adjChunkRef = chunkStore
                    .getChunkReference(ChunkUtil.indexChunkFromBlock(adjacent.x, adjacent.z));
            if (adjChunkRef != null && adjChunkRef.isValid()) {
                BlockChunk adjBlockChunk = store.getComponent(adjChunkRef, BlockChunk.getComponentType());
                if (adjBlockChunk != null)
                    adjBlockChunk.setTicking(adjacent.x, adjacent.y, adjacent.z, true);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Resolves the world cell the placer should fill.
     *
     * <p>
     * The target is always the cell on the opposite side of the block from
     * its grid connection face. Deriving the direction from the rotated grid
     * face means the target automatically tracks any
     * {@link com.hypixel.hytale.protocol.VariantRotation} — a normally-placed
     * block targets the cell below, a sideways block targets the correct
     * horizontal neighbour, etc.
     */
    @Nullable
    private static Vector3i resolveTargetCell(
            @Nonnull Vector3i originPos,
            @Nonnull FluidPlacerComponent placer,
            @Nonnull RotationTuple rotation) {
        BlockFace worldNormal = GridFaceUtil.rotateBlockFace(placer.getTargetNormal(), rotation);
        if (worldNormal == BlockFace.None)
            return null;
        Vector3i worldFaceCell = GridFaceUtil.addOffset(
                originPos, GridFaceUtil.rotateFacePosition(placer.getTargetPosition(), rotation));
        return GridFaceUtil.addOffset(worldFaceCell, worldNormal);
    }

}
