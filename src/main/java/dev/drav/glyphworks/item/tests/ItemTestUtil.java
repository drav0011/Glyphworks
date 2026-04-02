package dev.drav.glyphworks.item.tests;

import javax.annotation.Nullable;

import org.joml.Vector3i;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.World;

import dev.drav.glyphworks.grid.lookup.GridLookup;

final class ItemTestUtil {

    private ItemTestUtil() {
    }

    @Nullable
    static ItemContainerBlock getItemContainerBlock(World world, Vector3i pos) {
        GridLookup lu = GridLookup.resolve(world.getChunkStore(), pos);
        if (lu == null)
            return null;
        return world.getChunkStore().getStore()
                .getComponent(lu.blockRef(), ItemContainerBlock.getComponentType());
    }

    static int countItems(ItemContainer container) {
        if (container == null)
            return -1;
        int total = 0;
        for (short i = 0; i < container.getCapacity(); i++) {
            ItemStack stack = container.getItemStack(i);
            if (stack != null && !stack.isEmpty()) {
                total += stack.getQuantity();
            }
        }
        return total;
    }

    static void seedItems(ItemContainer container, String itemId, int amount) {
        if (container == null)
            return;
        container.addItemStack(new ItemStack(itemId, amount), false, false, false);
    }
}
