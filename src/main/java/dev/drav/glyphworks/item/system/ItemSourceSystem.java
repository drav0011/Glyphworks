package dev.drav.glyphworks.item.system;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.item.component.ItemSourceComponent;

/**
 * Ticking system for creative item source blocks.
 *
 * <p>
 * Each tick, for every block that has both an {@link ItemSourceComponent} and
 * an {@link ItemContainerBlock}, the system fills every empty slot in the
 * container with a stack of the item type specified by the source component.
 * This simulates an infinite item supply.
 */
public final class ItemSourceSystem extends EntityTickingSystem<ChunkStore> {

    @Override
    public Query<ChunkStore> getQuery() {
        return Query.and(
                ItemSourceComponent.getComponentType(),
                ItemContainerBlock.getComponentType());
    }

    @Override
    public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<ChunkStore> chunk,
            @Nonnull Store<ChunkStore> store,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer) {

        ItemSourceComponent source = chunk.getComponent(index, ItemSourceComponent.getComponentType());
        if (source == null) {
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

        for (short i = 0; i < container.getCapacity(); i++) {
            ItemStack stack = container.getItemStack(i);
            if (stack == null || stack.isEmpty()) {
                container.setItemStackForSlot(i, new ItemStack(source.getItemId(), 64), false);
            }
        }
    }
}
