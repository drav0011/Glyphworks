package dev.drav.glyphworks.item.system;

import java.util.List;

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
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockBreakingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.SoftBlockDropType;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.fluid.util.FluidUtil;
import dev.drav.glyphworks.grid.util.GridFaceUtil;
import dev.drav.glyphworks.item.component.BlockMinerComponent;
import dev.drav.glyphworks.util.TriggerVolumeGuard;

/**
 * Ticking system for block miner machines.
 *
 * <p>
 * Each tick, for every block with both a {@link BlockMinerComponent} and an
 * {@link ItemContainerBlock}:
 * <ol>
 * <li>Resolves the adjacent target cell from the component's target offset and
 * normal, applying the block's placed rotation.</li>
 * <li>Reads the block at that cell. If empty or unbreakable, resets progress
 * and returns.</li>
 * <li>Checks that the container can accept the expected drop. If not
 * (back-pressure), holds progress without advancing.</li>
 * <li>Increments mining progress. When the required number of ticks is
 * reached, removes the block from the world and places the drop item in the
 * container.</li>
 * </ol>
 *
 * <p>
 * Mining speed is driven by {@link BlockBreakingDropType#getQuality()}:
 * quality&nbsp;0 → 1&nbsp;tick, 1 → 40&nbsp;ticks, 2 → 100&nbsp;ticks,
 * 3+ → 200&nbsp;ticks.
 */
public final class BlockMinerSystem extends EntityTickingSystem<ChunkStore> {

    private static final int EMPTY_BLOCK_ID = 0;

    @Override
    public Query<ChunkStore> getQuery() {
        return Query.and(
                BlockMinerComponent.getComponentType(),
                ItemContainerBlock.getComponentType());
    }

    @Override
    public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<ChunkStore> chunk,
            @Nonnull Store<ChunkStore> store,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer) {

        BlockMinerComponent miner = chunk.getComponent(index, BlockMinerComponent.getComponentType());
        if (miner == null) {
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

        Vector3i adjacent = resolveTargetCell(originPos, miner, rotation);
        if (adjacent == null) {
            return;
        }

        BlockSection adjSection = FluidUtil.getBlockSection(chunkStore, store, adjacent.x, adjacent.y, adjacent.z);
        if (adjSection == null) {
            return;
        }

        int adjLocalX = ChunkUtil.localCoordinate(adjacent.x);
        int adjLocalZ = ChunkUtil.localCoordinate(adjacent.z);
        int targetBlockId = adjSection.get(adjLocalX, adjacent.y, adjLocalZ);

        if (targetBlockId == EMPTY_BLOCK_ID) {
            miner.setMiningProgress(0);
            miner.setLastSeenBlockId(-1);
            return;
        }

        BlockType blockType = (BlockType) BlockType.getAssetMap().getAsset(targetBlockId);
        if (blockType == null) {
            return;
        }

        String targetBlockKey = blockType.getId();
        if (!TriggerVolumeGuard.canDestroy(chunkStore.getWorld(), adjacent, targetBlockKey)
                || !TriggerVolumeGuard.canHarvest(chunkStore.getWorld(), adjacent, targetBlockKey)) {
            // Protected region — drop progress like an unbreakable target.
            miner.setMiningProgress(0);
            miner.setLastSeenBlockId(targetBlockId);
            return;
        }

        BlockGathering gathering = blockType.getGathering();
        if (gathering == null) {
            // Unbreakable block — reset so stale progress does not carry forward.
            miner.setMiningProgress(0);
            miner.setLastSeenBlockId(targetBlockId);
            return;
        }

        // Reset progress when the target block type changes (e.g. a player placed
        // a different block in the target cell while we were mining).
        boolean freshTarget = targetBlockId != miner.getLastSeenBlockId();
        if (freshTarget) {
            miner.setMiningProgress(0);
            miner.setLastSeenBlockId(targetBlockId);
        }

        List<ItemStack> drops = computeDrops(blockType, gathering);
        if (drops.isEmpty()) {
            miner.setMiningProgress(0);
            miner.setLastSeenBlockId(targetBlockId);
            return;
        }

        int ticksRequired = computeTicksRequired(gathering.getBreaking());

        // Back-pressure: stall while any drop cannot fit.
        for (ItemStack drop : drops) {
            if (!container.canAddItemStack(drop)) {
                return;
            }
        }

        miner.setMiningProgress(miner.getMiningProgress() + 1);

        if (miner.getMiningProgress() < ticksRequired) {
            return;
        }

        // Mining complete — add all drops and remove the block.
        boolean anySucceeded = false;
        for (ItemStack drop : drops) {
            if (container.addItemStack(drop).succeeded()) {
                anySucceeded = true;
            }
        }
        if (anySucceeded) {
            final int ax = adjacent.x, ay = adjacent.y, az = adjacent.z;
            commandBuffer.run(_ -> commandBuffer.getExternalData().getWorld().setBlock(ax, ay, az, "Empty"));
        }
        miner.setMiningProgress(0);
        miner.setLastSeenBlockId(-1);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    @Nonnull
    private static List<ItemStack> computeDrops(@Nonnull BlockType blockType, @Nonnull BlockGathering gathering) {
        BlockBreakingDropType breaking = gathering.getBreaking();
        if (breaking != null) {
            int qty = Math.max(1, breaking.getQuantity());
            return BlockHarvestUtils.getDrops(blockType, qty, breaking.getItemId(), breaking.getDropListId());
        }
        SoftBlockDropType soft = gathering.getSoft();
        if (soft != null) {
            return BlockHarvestUtils.getDrops(blockType, 1, soft.getItemId(), soft.getDropListId());
        }
        return List.of();
    }

    /**
     * Maps a block's breaking quality tier to the number of ticks required to mine
     * it with this machine.
     *
     * <ul>
     * <li>Soft block (no {@link BlockBreakingDropType}) → 10 ticks</li>
     * <li>Quality 0 → 10 ticks (~0.5 s at 20 TPS)</li>
     * <li>Quality 1 → 400 ticks (~20 s)</li>
     * <li>Quality 2 → 1000 ticks (~50 s)</li>
     * <li>Quality 3+ → 2000 ticks (~100 s)</li>
     * </ul>
     */
    private static int computeTicksRequired(@Nullable BlockBreakingDropType breaking) {
        if (breaking == null) {
            return 10;
        }
        int quality = breaking.getQuality();
        if (quality <= 0) return 10;
        if (quality == 1) return 400;
        if (quality == 2) return 1000;
        return 2000;
    }

    /**
     * Resolves the world cell the miner should dig into.
     *
     * <p>
     * The target cell is determined identically to
     * {@link dev.drav.glyphworks.fluid.system.FluidRemoverSystem}: the
     * component's block-local {@code targetNormal} and {@code targetPosition}
     * are rotated into world-space by the block's placed rotation.
     */
    @Nullable
    private static Vector3i resolveTargetCell(
            @Nonnull Vector3i originPos,
            @Nonnull BlockMinerComponent miner,
            @Nonnull RotationTuple rotation) {
        BlockFace worldNormal = GridFaceUtil.rotateBlockFace(miner.getTargetNormal(), rotation);
        if (worldNormal == BlockFace.None) {
            return null;
        }
        Vector3i worldFaceCell = GridFaceUtil.addOffset(
                originPos, GridFaceUtil.rotateFacePosition(miner.getTargetPosition(), rotation));
        return GridFaceUtil.addOffset(worldFaceCell, worldNormal);
    }
}
