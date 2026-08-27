package dev.drav.glyphworks.item.system;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3i;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.BlockFace;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.fluid.util.FluidUtil;
import dev.drav.glyphworks.grid.util.GridFaceUtil;
import dev.drav.glyphworks.item.component.BlockPlacerComponent;
import dev.drav.glyphworks.util.TriggerVolumeGuard;

/**
 * Ticking system for block placer machines.
 *
 * <p>
 * Each tick, for every block with both a {@link BlockPlacerComponent} and an
 * {@link ItemContainerBlock}:
 * <ol>
 * <li>Resolves the adjacent target cell from the component's target offset and
 * normal (same pattern as
 * {@link dev.drav.glyphworks.fluid.system.FluidPlacerSystem}).</li>
 * <li>Checks if the target cell is already occupied. If occupied, skips.</li>
 * <li>Scans the container for the first slot containing a block-item
 * ({@link Item#hasBlockType()} is true). Non-block items are skipped.</li>
 * <li>Places the block into the world and removes exactly one item from the
 * container slot. One block is placed per tick.</li>
 * </ol>
 *
 * <p>
 * The block's INPUT grid face receives block-items from the network.
 */
public final class BlockPlacerSystem extends EntityTickingSystem<ChunkStore> {

    private static final int EMPTY_BLOCK_ID = 0;

    @Override
    public Query<ChunkStore> getQuery() {
        return Query.and(
                BlockPlacerComponent.getComponentType(),
                ItemContainerBlock.getComponentType());
    }

    @Override
    public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<ChunkStore> chunk,
            @Nonnull Store<ChunkStore> store,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer) {

        BlockPlacerComponent placer = chunk.getComponent(index, BlockPlacerComponent.getComponentType());
        if (placer == null) {
            return;
        }

        ItemContainerBlock icb = chunk.getComponent(index, ItemContainerBlock.getComponentType());
        if (icb == null) {
            return;
        }
        ItemContainer container = icb.getItemContainer();
        if (container == null) {
            return;
        }

        BlockModule.BlockStateInfo bsi = chunk.getComponent(index, BlockModule.BlockStateInfo.getComponentType());
        if (bsi == null) {
            return;
        }

        ChunkStore chunkStore = commandBuffer.getExternalData();
        Vector3i originPos = new Vector3i();
        if (!bsi.fillWorldPos(store, originPos)) {
            return;
        }

        BlockSection blockSection = FluidUtil.getBlockSection(chunkStore, store, originPos.x, originPos.y, originPos.z);
        if (blockSection == null) {
            return;
        }
        RotationTuple rotation = RotationTuple.get(blockSection.getRotationIndex(originPos.x, originPos.y, originPos.z));

        Vector3i adjacent = resolveTargetCell(originPos, placer, rotation);
        if (adjacent == null) {
            return;
        }

        BlockSection adjSection = FluidUtil.getBlockSection(chunkStore, store, adjacent.x, adjacent.y, adjacent.z);
        if (adjSection == null) {
            return;
        }

        int adjLocalX = ChunkUtil.localCoordinate(adjacent.x);
        int adjLocalZ = ChunkUtil.localCoordinate(adjacent.z);
        int existingBlockId = adjSection.get(adjLocalX, adjacent.y, adjLocalZ);

        if (existingBlockId != EMPTY_BLOCK_ID) {
            return; // Target cell is occupied — nothing to place.
        }

        // Find the first block-item in the container.
        short cap = container.getCapacity();
        for (short slot = 0; slot < cap; slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (ItemStack.isEmpty(stack)) {
                continue;
            }
            Item item = stack.getItem();
            if (item == null || !item.hasBlockType()) {
                continue; // Not a placeable block item — skip.
            }

            // Place the block and consume exactly one item from the slot.
            final String blockId = item.getBlockId();
            if (!TriggerVolumeGuard.canBuild(chunkStore.getWorld(), adjacent, blockId)) {
                continue; // Protected region — another slot may hold an excepted block.
            }
            final int ax = adjacent.x, ay = adjacent.y, az = adjacent.z;
            commandBuffer.run(_ -> commandBuffer.getExternalData().getWorld().setBlock(ax, ay, az, blockId));
            container.removeItemStackFromSlot(slot, 1);
            return; // One block per tick.
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Resolves the world cell the placer should fill, mirroring the logic in
     * {@link dev.drav.glyphworks.fluid.system.FluidPlacerSystem}.
     */
    @Nullable
    private static Vector3i resolveTargetCell(
            @Nonnull Vector3i originPos,
            @Nonnull BlockPlacerComponent placer,
            @Nonnull RotationTuple rotation) {
        BlockFace worldNormal = GridFaceUtil.rotateBlockFace(placer.getTargetNormal(), rotation);
        if (worldNormal == BlockFace.None) {
            return null;
        }
        Vector3i worldFaceCell = GridFaceUtil.addOffset(
                originPos, GridFaceUtil.rotateFacePosition(placer.getTargetPosition(), rotation));
        return GridFaceUtil.addOffset(worldFaceCell, worldNormal);
    }
}
