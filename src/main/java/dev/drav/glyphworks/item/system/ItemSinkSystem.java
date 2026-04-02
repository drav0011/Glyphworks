package dev.drav.glyphworks.item.system;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.item.component.ItemSinkComponent;

/**
 * Ticking system for creative item sink blocks.
 *
 * <p>
 * Each tick, for every block that has both an {@link ItemSinkComponent} and an
 * {@link ItemContainerBlock}, the system clears every slot in the container,
 * destroying whatever items the grid transferred into it. This simulates an
 * infinite drain with no back-pressure.
 */
public final class ItemSinkSystem extends EntityTickingSystem<ChunkStore> {

    @Override
    public Query<ChunkStore> getQuery() {
        return Query.and(
                ItemSinkComponent.getComponentType(),
                ItemContainerBlock.getComponentType());
    }

    @Override
    public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<ChunkStore> chunk,
            @Nonnull Store<ChunkStore> store,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer) {

        ItemContainerBlock icb = chunk.getComponent(index, ItemContainerBlock.getComponentType());
        if (icb == null) {
            return;
        }

        ItemContainer container = icb.getItemContainer();
        if (container == null) {
            return;
        }

        for (short i = 0; i < container.getCapacity(); i++) {
            container.setItemStackForSlot(i, null, false);
        }
    }
}
