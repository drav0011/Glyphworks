package dev.drav.glyphworks.item.tests.system;

import org.joml.Vector3i;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.World;

import dev.drav.glyphworks.grid.lookup.GridLookup;
import dev.drav.glyphworks.item.component.ItemSourceComponent;
import dev.drav.glyphworks.item.tests.ItemTestUtil;
import dev.drav.glyphworks.test.framework.Steps;
import dev.drav.glyphworks.test.framework.TestCase;
import dev.drav.glyphworks.test.framework.TestRegistry;
import dev.drav.glyphworks.test.framework.TestSuite;

/**
 * Suite {@code "item_source_system"} — integration tests for
 * {@link dev.drav.glyphworks.item.system.ItemSourceSystem}.
 *
 * <p>
 * Each tick the system fills every empty slot in the container on every
 * {@code Glyphworks_Item_Source} block with the configured item. Tests verify
 * that the system both fills from empty and overrides any stale empty state.
 */
public final class ItemSourceSystemTests {

    private static final String SOURCE_ID  = "Glyphworks_Item_Source";
    private static final String ITEM_ID     = "Rock_Stone";

    private ItemSourceSystemTests() {
    }

    public static void register(String moduleId) {
        TestRegistry.register(moduleId, buildSuite());
    }

    private static TestSuite buildSuite() {
        return new TestSuite("item_source_system")
                .test(sourceFillsContainer())
                .test(sourceOverridesStaleState());
    }

    // -------------------------------------------------------------------------
    // Test 1: source refills its container with items each tick
    // -------------------------------------------------------------------------

    private static TestCase sourceFillsContainer() {
        return new TestCase("source_fills_container", 3, 3, 3)
                .step(Steps.run(ctx -> {
                    ctx.getWorld().setBlock(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ(), SOURCE_ID);
                }))
                .step(Steps.waitUntil(ctx -> getSource(ctx.getWorld(), ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()) != null,
                        ctx -> 5 * ctx.getWorld().getTps(), "source block entity initialised"))
                .step(Steps.run(ctx -> {
                    ItemSourceComponent isc = getSource(ctx.getWorld(), ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    if (isc != null)
                        isc.getSelectorContainer().setItemStackForSlot((short) 0, new ItemStack(ITEM_ID, 1), false);
                }))
                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))
                .step(Steps.assertThat(ctx -> {
                    ItemContainerBlock icb = ItemTestUtil.getItemContainerBlock(ctx.getWorld(),
                            new Vector3i(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()));
                    return icb != null && ItemTestUtil.countItems(icb.getItemContainer()) > 0;
                }, "ItemSourceSystem fills the container with items each tick"));
    }

    // -------------------------------------------------------------------------
    // Test 2: source overwrites stale empty state on the next tick
    // -------------------------------------------------------------------------

    private static TestCase sourceOverridesStaleState() {
        return new TestCase("source_overrides_stale_state", 3, 3, 3)
                .step(Steps.run(ctx -> {
                    ctx.getWorld().setBlock(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ(), SOURCE_ID);
                }))
                .step(Steps.waitUntil(ctx -> getSource(ctx.getWorld(), ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()) != null,
                        ctx -> 5 * ctx.getWorld().getTps(), "source block entity initialised"))
                .step(Steps.run(ctx -> {
                    ItemSourceComponent isc = getSource(ctx.getWorld(), ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    if (isc != null)
                        isc.getSelectorContainer().setItemStackForSlot((short) 0, new ItemStack(ITEM_ID, 1), false);
                }))
                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))
                .step(Steps.run(ctx -> {
                    ItemContainerBlock icb = ItemTestUtil.getItemContainerBlock(ctx.getWorld(),
                            new Vector3i(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()));
                    if (icb != null) {
                        for (short i = 0; i < icb.getItemContainer().getCapacity(); i++) {
                            icb.getItemContainer().setItemStackForSlot(i, null, false);
                        }
                    }
                }))
                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))
                .step(Steps.assertThat(ctx -> {
                    ItemContainerBlock icb = ItemTestUtil.getItemContainerBlock(ctx.getWorld(),
                            new Vector3i(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()));
                    return icb != null && ItemTestUtil.countItems(icb.getItemContainer()) > 0;
                }, "ItemSourceSystem overrides stale empty state and refills the container on the next tick"));
    }

    private static ItemSourceComponent getSource(World world, int x, int y, int z) {
        GridLookup lu = GridLookup.resolve(world.getChunkStore(), new Vector3i(x, y, z));
        if (lu == null)
            return null;
        return world.getChunkStore().getStore().getComponent(lu.blockRef(), ItemSourceComponent.getComponentType());
    }
}
