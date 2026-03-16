package dev.drav.glyphworks.crafting.system;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.BlockModule.BlockStateInfo;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.entity.entities.player.windows.WindowManager;

import dev.drav.glyphworks.crafting.component.AutoCraftingBenchBlock;

import java.util.List;

/**
 * Lifecycle system for {@link AutoCraftingBenchBlock}.
 *
 * <ul>
 *   <li><b>onEntityAdded</b> — calls {@link AutoCraftingBenchBlock#setupContainers}
 *       to create / resize the input and output containers and restore the locked
 *       recipe reference after a chunk load or fresh placement.</li>
 *   <li><b>onEntityRemove (UNLOAD)</b> — marks the block as needing saving.</li>
 *   <li><b>onEntityRemove (other)</b> — drops all inventoried items at the block
 *       position (block destroyed).</li>
 * </ul>
 */
public final class AutoCraftingBenchSetupSystem extends RefSystem<ChunkStore> {

    private final ComponentType<ChunkStore, BlockStateInfo> blockStateInfoComponentType =
            BlockModule.BlockStateInfo.getComponentType();

    @Override
    public Query<ChunkStore> getQuery() {
        return AutoCraftingBenchBlock.getComponentType();
    }

    @Override
    public void onEntityAdded(
            @Nonnull Ref<ChunkStore> ref,
            @Nonnull AddReason reason,
            @Nonnull Store<ChunkStore> store,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer) {

        AutoCraftingBenchBlock acbb =
                commandBuffer.getComponent(ref, AutoCraftingBenchBlock.getComponentType());
        BlockStateInfo blockStateInfo =
                commandBuffer.getComponent(ref, blockStateInfoComponentType);
        if (acbb == null || blockStateInfo == null) return;

        Ref<ChunkStore> chunkRef = blockStateInfo.getChunkRef();
        if (!chunkRef.isValid()) return;

        BlockChunk blockChunk = commandBuffer.getComponent(chunkRef, BlockChunk.getComponentType());
        if (blockChunk == null) return;

        int blockIndex = blockStateInfo.getIndex();
        int localX = ChunkUtil.xFromBlockInColumn(blockIndex);
        int localY = ChunkUtil.yFromBlockInColumn(blockIndex);
        int localZ = ChunkUtil.zFromBlockInColumn(blockIndex);
        int blockX = ChunkUtil.worldCoordFromLocalCoord(blockChunk.getX(), localX);
        int blockZ = ChunkUtil.worldCoordFromLocalCoord(blockChunk.getZ(), localZ);

        BlockSection blockSection = blockChunk.getSectionAtBlockY(localY);
        int blockId = blockSection.get(localX, localY, localZ);
        BlockType blockType = (BlockType) BlockType.getAssetMap().getAsset(blockId);
        if (blockType == null) return;

        int rotationIndex = blockSection.getRotationIndex(localX, localY, localZ);
        World world = commandBuffer.getExternalData().getWorld();

        acbb.setupContainers(blockStateInfo, world, blockX, localY, blockZ, blockType, rotationIndex);
    }

    @Override
    public void onEntityRemove(
            @Nonnull Ref<ChunkStore> ref,
            @Nonnull RemoveReason reason,
            @Nonnull Store<ChunkStore> store,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer) {

        BlockStateInfo blockStateInfo =
                commandBuffer.getComponent(ref, blockStateInfoComponentType);

        if (reason == RemoveReason.UNLOAD) {
            if (blockStateInfo != null) blockStateInfo.markNeedsSaving();
            return;
        }

        // Block destroyed — drop all items at block centre.
        AutoCraftingBenchBlock acbb =
                commandBuffer.getComponent(ref, AutoCraftingBenchBlock.getComponentType());
        if (acbb == null || blockStateInfo == null) return;

        CombinedItemContainer combined = acbb.getItemContainer();
        if (combined == null) return;

        WindowManager.closeAndRemoveAll(acbb.getWindows());

        List<ItemStack> items = combined.dropAllItemStacks();
        if (items.isEmpty()) return;

        Ref<ChunkStore> chunkRef = blockStateInfo.getChunkRef();
        if (!chunkRef.isValid()) return;

        BlockChunk blockChunk = commandBuffer.getComponent(chunkRef, BlockChunk.getComponentType());
        if (blockChunk == null) return;

        int blockIndex = blockStateInfo.getIndex();
        int localX = ChunkUtil.xFromBlockInColumn(blockIndex);
        int localY = ChunkUtil.yFromBlockInColumn(blockIndex);
        int localZ = ChunkUtil.zFromBlockInColumn(blockIndex);
        int blockX = ChunkUtil.worldCoordFromLocalCoord(blockChunk.getX(), localX);
        int blockZ = ChunkUtil.worldCoordFromLocalCoord(blockChunk.getZ(), localZ);

        World world = commandBuffer.getExternalData().getWorld();
        Store<EntityStore> entityStore = world.getEntityStore().getStore();
        Vector3d dropPos = new Vector3d(blockX + 0.5d, localY, blockZ + 0.5d);

        Holder<EntityStore>[] holders = ItemComponent.generateItemDrops(
                entityStore, items, dropPos, Vector3f.ZERO);
        if (holders.length > 0) {
            world.execute(() -> entityStore.addEntities(holders, AddReason.SPAWN));
        }
    }
}
