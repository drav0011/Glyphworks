package dev.drav.glyphworks.item.tests;

import java.util.List;

import javax.annotation.Nullable;

import org.joml.Vector3d;
import org.joml.Vector3i;

import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import dev.drav.glyphworks.fluid.util.FluidUtil;
import dev.drav.glyphworks.grid.lookup.GridLookup;

public final class ItemTestUtil {

    private ItemTestUtil() {
    }

    @Nullable
    public static ItemContainerBlock getItemContainerBlock(World world, Vector3i pos) {
        GridLookup lu = GridLookup.resolve(world.getChunkStore(), pos);
        if (lu == null)
            return null;
        return world.getChunkStore().getStore()
                .getComponent(lu.blockRef(), ItemContainerBlock.getComponentType());
    }

    public static int countItems(ItemContainer container) {
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

    public static void seedItems(ItemContainer container, String itemId, int amount) {
        if (container == null)
            return;
        container.addItemStack(new ItemStack(itemId, amount), false, false, false);
    }

    /**
     * Fills every slot in {@code container} to the item's registered max-stack
     * size, bypassing filters. Used to simulate a full container (back-pressure).
     */
    public static void fillContainer(ItemContainer container, String itemId) {
        if (container == null)
            return;
        DefaultAssetMap<String, Item> assetMap = Item.getAssetMap();
        Item item = (assetMap != null) ? assetMap.getAsset(itemId) : null;
        int maxStack = (item != null && item != Item.UNKNOWN) ? item.getMaxStack() : 1;
        for (short i = 0; i < container.getCapacity(); i++) {
            container.setItemStackForSlot(i, new ItemStack(itemId, maxStack), false);
        }
    }

    /**
     * Returns the numeric block-type ID at the given world position, or {@code 0}
     * if the chunk section is not loaded or the cell is empty.
     */
    public static int getBlockId(World world, int x, int y, int z) {
        BlockSection bs = FluidUtil.getBlockSection(
                world.getChunkStore(), world.getChunkStore().getStore(), x, y, z);
        if (bs == null)
            return 0;
        int lx = ChunkUtil.localCoordinate(x);
        int lz = ChunkUtil.localCoordinate(z);
        return bs.get(lx, y, lz);
    }

    /**
     * Schedules the spawn of a single item entity at {@code (x, y, z)} via
     * {@link World#execute}. The entity will be present at the start of the next
     * server tick.
     */
    public static void spawnItemEntity(
            World world,
            Store<EntityStore> entityStore,
            double x, double y, double z,
            String itemId, int qty) {
        List<ItemStack> items = List.of(new ItemStack(itemId, qty));
        Vector3d pos = new Vector3d(x, y, z);
        Holder<EntityStore>[] holders = ItemComponent.generateItemDrops(entityStore, items, pos, Rotation3f.ZERO);
        if (holders.length > 0) {
            world.execute(() -> entityStore.addEntities(holders, AddReason.SPAWN));
        }
    }
}
