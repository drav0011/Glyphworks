package dev.drav.glyphworks.item.system;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterActionType;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

/**
 * Lifecycle system that applies a max-stack-size-1 {@link FilterActionType#ADD}
 * filter to slot 0 of a block's {@link ItemContainerBlock} on fresh placement
 * and on every chunk load.
 */
public class ItemSingleSlotSetupSystem extends RefSystem<ChunkStore> {

    private final Query<ChunkStore> query;

    public ItemSingleSlotSetupSystem(@Nonnull Query<ChunkStore> query) {
        this.query = query;
    }

    @Override
    public Query<ChunkStore> getQuery() {
        return query;
    }

    @Override
    public void onEntityAdded(
            @Nonnull Ref<ChunkStore> ref,
            @Nonnull AddReason reason,
            @Nonnull Store<ChunkStore> store,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer) {

        ItemContainerBlock icb = commandBuffer.getComponent(ref, ItemContainerBlock.getComponentType());
        if (icb == null)
            return;

        ItemContainer container = icb.getItemContainer();
        if (container == null)
            return;

        container.setSlotFilter(FilterActionType.ADD, (short) 0,
                (actionType, cont, slot, itemStack) -> {
                    if (ItemStack.isEmpty(itemStack))
                        return true;
                    return itemStack.getQuantity() == 1 && ItemStack.isEmpty(cont.getItemStack(slot));
                });
    }

    @Override
    public void onEntityRemove(
            @Nonnull Ref<ChunkStore> ref,
            @Nonnull RemoveReason reason,
            @Nonnull Store<ChunkStore> store,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer) {
        // nothing to do — slot filters are transient and will not leak on unload or destroy
    }
}
