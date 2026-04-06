package dev.drav.glyphworks.crafting.system;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import org.joml.Vector3d;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.BlockModule.BlockStateInfo;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import dev.drav.glyphworks.crafting.component.ManaLiquifierBlock;

/**
 * Lifecycle system for {@link ManaLiquifierBlock}.
 *
 * <ul>
 * <li><b>onEntityAdded</b> — creates / restores input and fuel containers.</li>
 * <li><b>onEntityRemove (UNLOAD)</b> — marks the block as needing saving.</li>
 * <li><b>onEntityRemove (other)</b> — drops inventoried items at the block
 * position (block destroyed).</li>
 * </ul>
 */
public final class ManaLiquifierSetupSystem extends RefSystem<ChunkStore> {

    private final com.hypixel.hytale.component.ComponentType<ChunkStore, BlockStateInfo>
            blockStateInfoComponentType = BlockModule.BlockStateInfo.getComponentType();

    @Override
    public Query<ChunkStore> getQuery() {
        return ManaLiquifierBlock.getComponentType();
    }

    @Override
    public void onEntityAdded(
            @Nonnull Ref<ChunkStore> ref,
            @Nonnull AddReason reason,
            @Nonnull Store<ChunkStore> store,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer) {

        ManaLiquifierBlock mlb = commandBuffer.getComponent(ref, ManaLiquifierBlock.getComponentType());
        BlockStateInfo blockStateInfo = commandBuffer.getComponent(ref, blockStateInfoComponentType);
        if (mlb == null || blockStateInfo == null)
            return;

        List<ItemStack> ejected = new ArrayList<>();
        mlb.setupContainers(blockStateInfo, ejected);

        if (ejected.isEmpty())
            return;

        Ref<ChunkStore> chunkRef = blockStateInfo.getChunkRef();
        if (!chunkRef.isValid())
            return;
        BlockChunk blockChunk = commandBuffer.getComponent(chunkRef, BlockChunk.getComponentType());
        if (blockChunk == null)
            return;

        int blockIndex = blockStateInfo.getIndex();
        int localX = ChunkUtil.xFromBlockInColumn(blockIndex);
        int localY = ChunkUtil.yFromBlockInColumn(blockIndex);
        int localZ = ChunkUtil.zFromBlockInColumn(blockIndex);
        int blockX = ChunkUtil.worldCoordFromLocalCoord(blockChunk.getX(), localX);
        int blockZ = ChunkUtil.worldCoordFromLocalCoord(blockChunk.getZ(), localZ);

        World world = commandBuffer.getExternalData().getWorld();
        Store<EntityStore> entityStore = world.getEntityStore().getStore();
        Vector3d dropPos = new Vector3d(blockX + 0.5d, localY, blockZ + 0.5d);

        Holder<EntityStore>[] holders = ItemComponent.generateItemDrops(entityStore, ejected, dropPos, Rotation3f.ZERO);
        if (holders.length > 0) {
            world.execute(() -> entityStore.addEntities(holders, AddReason.SPAWN));
        }
    }

    @Override
    public void onEntityRemove(
            @Nonnull Ref<ChunkStore> ref,
            @Nonnull RemoveReason reason,
            @Nonnull Store<ChunkStore> store,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer) {

        BlockStateInfo blockStateInfo = commandBuffer.getComponent(ref, blockStateInfoComponentType);

        if (reason == RemoveReason.UNLOAD) {
            if (blockStateInfo != null)
                blockStateInfo.markNeedsSaving();
            return;
        }

        // Block destroyed — drop all items at block centre.
        ManaLiquifierBlock mlb = commandBuffer.getComponent(ref, ManaLiquifierBlock.getComponentType());
        if (mlb == null || blockStateInfo == null)
            return;

        List<ItemStack> items = new ArrayList<>();
        ItemContainer inputContainer = mlb.getInputContainer();
        if (inputContainer != null)
            items.addAll(inputContainer.dropAllItemStacks());
        ItemContainer fuelContainer = mlb.getFuelContainer();
        if (fuelContainer != null)
            items.addAll(fuelContainer.dropAllItemStacks());

        if (items.isEmpty())
            return;

        Ref<ChunkStore> chunkRef = blockStateInfo.getChunkRef();
        if (!chunkRef.isValid())
            return;
        BlockChunk blockChunk = commandBuffer.getComponent(chunkRef, BlockChunk.getComponentType());
        if (blockChunk == null)
            return;

        int blockIndex = blockStateInfo.getIndex();
        int localX = ChunkUtil.xFromBlockInColumn(blockIndex);
        int localY = ChunkUtil.yFromBlockInColumn(blockIndex);
        int localZ = ChunkUtil.zFromBlockInColumn(blockIndex);
        int blockX = ChunkUtil.worldCoordFromLocalCoord(blockChunk.getX(), localX);
        int blockZ = ChunkUtil.worldCoordFromLocalCoord(blockChunk.getZ(), localZ);

        World world = commandBuffer.getExternalData().getWorld();
        Store<EntityStore> entityStore = world.getEntityStore().getStore();
        Vector3d dropPos = new Vector3d(blockX + 0.5d, localY, blockZ + 0.5d);

        Holder<EntityStore>[] holders = ItemComponent.generateItemDrops(entityStore, items, dropPos, Rotation3f.ZERO);
        if (holders.length > 0) {
            world.execute(() -> entityStore.addEntities(holders, AddReason.SPAWN));
        }
    }
}
