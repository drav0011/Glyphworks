package dev.drav.glyphworks.item.system;

import org.joml.Vector3i;
import javax.annotation.Nonnull;

import org.joml.Vector3d;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import dev.drav.glyphworks.item.component.ItemPickerComponent;

/**
 * Ticking system for item picker machines.
 *
 * <p>
 * Each tick, for every block with both an {@link ItemPickerComponent} and an
 * {@link ItemContainerBlock}, iterates all item entities within the configured
 * cubic radius and attempts to pick up each one into the block's container.
 *
 * <p>
 * An item entity is only collected if the container has room for it. The
 * entity is removed from the world once the transfer succeeds.
 */
public final class ItemPickerSystem extends EntityTickingSystem<ChunkStore> {

    @Override
    public Query<ChunkStore> getQuery() {
        return Query.and(
                ItemPickerComponent.getComponentType(),
                ItemContainerBlock.getComponentType());
    }

    @Override
    public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<ChunkStore> chunk,
            @Nonnull Store<ChunkStore> store,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer) {

        ItemPickerComponent picker = chunk.getComponent(index, ItemPickerComponent.getComponentType());
        if (picker == null) {
            return;
        }

        ItemContainerBlock icb = chunk.getComponent(index, ItemContainerBlock.getComponentType());
        if (icb == null) {
            return;
        }

        BlockModule.BlockStateInfo bsi = chunk.getComponent(index, BlockModule.BlockStateInfo.getComponentType());
        if (bsi == null) {
            return;
        }

        Vector3i blockPos = new Vector3i();
        if (!bsi.fillWorldPos(store, blockPos)) {
            return;
        }
        double cx = blockPos.x;
        double cy = blockPos.y;
        double cz = blockPos.z;

        ItemContainer container = icb.getItemContainer();
        if (container == null) {
            return;
        }

        int radius = picker.getRadius();
        double halfRange = radius + 0.5;

        World world = store.getExternalData().getWorld();
        Store<EntityStore> entityStore = world.getEntityStore().getStore();

        world.execute(() -> {
            entityStore.forEachChunk(ItemComponent.getComponentType(),
                    (ArchetypeChunk<EntityStore> itemChunk, CommandBuffer<EntityStore> itemBuf) -> {
                        for (int i = 0; i < itemChunk.size(); i++) {
                            TransformComponent tc = itemChunk.getComponent(i, TransformComponent.getComponentType());
                            if (tc == null) {
                                continue;
                            }

                            Vector3d pos = tc.getPosition();
                            if (Math.abs(pos.x - cx) > halfRange
                                    || Math.abs(pos.y - cy) > halfRange
                                    || Math.abs(pos.z - cz) > halfRange) {
                                continue;
                            }

                            ItemComponent ic = itemChunk.getComponent(i, ItemComponent.getComponentType());
                            if (ic == null) {
                                continue;
                            }
                            ItemStack stack = ic.getItemStack();
                            if (ItemStack.isEmpty(stack)) {
                                continue;
                            }

                            ItemStackTransaction tx = container.addItemStack(stack);
                            if (!tx.succeeded()) {
                                continue;
                            }
                            ItemStack remainder = tx.getRemainder();
                            if (ItemStack.isEmpty(remainder)) {
                                itemBuf.removeEntity(itemChunk.getReferenceTo(i), RemoveReason.REMOVE);
                            } else {
                                ic.setItemStack(remainder);
                            }
                        }
                    });
        });
    }
}