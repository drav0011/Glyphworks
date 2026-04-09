package dev.drav.glyphworks.item.system;

import java.util.List;

import javax.annotation.Nonnull;

import org.joml.Vector3d;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import dev.drav.glyphworks.fluid.util.FluidUtil;
import dev.drav.glyphworks.item.component.ItemDropperComponent;

/**
 * Ticking system for item dropper machines.
 *
 * <p>
 * Each tick, for every block with both an {@link ItemDropperComponent} and an
 * {@link ItemContainerBlock}, all items are drained from the container and
 * spawned as item entities directly below the machine block.
 */
public final class ItemDropperSystem extends EntityTickingSystem<ChunkStore> {

    @Override
    public Query<ChunkStore> getQuery() {
        return Query.and(
                ItemDropperComponent.getComponentType(),
                ItemContainerBlock.getComponentType());
    }

    @Override
    public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<ChunkStore> chunk,
            @Nonnull Store<ChunkStore> store,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer) {

        ItemDropperComponent dropper = chunk.getComponent(index, ItemDropperComponent.getComponentType());
        if (dropper == null) {
            return;
        }

        if (!dropper.incrementAndShouldDrop()) {
            return;
        }

        ItemContainerBlock icb = chunk.getComponent(index, ItemContainerBlock.getComponentType());
        if (icb == null) {
            return;
        }

        BlockModule.BlockStateInfo bsi = chunk.getComponent(index, BlockModule.BlockStateInfo.getComponentType());
        if (bsi == null || !bsi.getChunkRef().isValid()) {
            return;
        }

        BlockChunk blockChunk = store.getComponent(bsi.getChunkRef(), BlockChunk.getComponentType());
        if (blockChunk == null) {
            return;
        }

        int bi = bsi.getIndex();
        int localX = ChunkUtil.xFromBlockInColumn(bi);
        int localY = ChunkUtil.yFromBlockInColumn(bi);
        int localZ = ChunkUtil.zFromBlockInColumn(bi);
        int blockX = ChunkUtil.worldCoordFromLocalCoord(blockChunk.getX(), localX);
        int blockZ = ChunkUtil.worldCoordFromLocalCoord(blockChunk.getZ(), localZ);

        // Only drop if the block below is empty or a fluid (blockId == 0 covers both).
        ChunkStore chunkStore = commandBuffer.getExternalData();
        BlockSection belowSection = FluidUtil.getBlockSection(chunkStore, store, blockX, localY - 1, blockZ);
        if (belowSection == null) {
            return;
        }
        int belowId = belowSection.get(ChunkUtil.localCoordinate(blockX), localY - 1, ChunkUtil.localCoordinate(blockZ));
        if (belowId != 0) {
            return;
        }

        ItemContainer container = icb.getItemContainer();
        if (container == null) {
            return;
        }

        List<ItemStack> items = container.dropAllItemStacks();
        if (items.isEmpty()) {
            return;
        }

        Vector3d dropPos = new Vector3d(blockX + 0.5d, localY - 0.5d, blockZ + 0.5d);

        World world = store.getExternalData().getWorld();
        Store<EntityStore> entityStore = world.getEntityStore().getStore();

        Holder<EntityStore>[] holders = ItemComponent.generateItemDrops(
                entityStore, items, dropPos, Rotation3f.ZERO);
        if (holders.length > 0) {
            world.execute(() -> entityStore.addEntities(holders, AddReason.SPAWN));
        }
    }
}
