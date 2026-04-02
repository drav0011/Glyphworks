package dev.drav.glyphworks.item.tests;

import org.joml.Vector3i;

import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;

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

    private static final String SOURCE_ID = "Glyphworks_Item_Source";

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
                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))
                .step(Steps.run(ctx -> {
                    // Clear all slots to simulate stale drained state.
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
}
