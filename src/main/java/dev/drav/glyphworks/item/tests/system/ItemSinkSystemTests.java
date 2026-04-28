package dev.drav.glyphworks.item.tests.system;

import com.hypixel.hytale.math.vector.Vector3i;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;

import dev.drav.glyphworks.item.tests.ItemTestUtil;
import dev.drav.glyphworks.test.framework.Steps;
import dev.drav.glyphworks.test.framework.TestCase;
import dev.drav.glyphworks.test.framework.TestRegistry;
import dev.drav.glyphworks.test.framework.TestSuite;

/**
 * Suite {@code "item_sink_system"} — integration tests for
 * {@link dev.drav.glyphworks.item.system.ItemSinkSystem}.
 *
 * <p>
 * Each tick the system clears every slot in the container on every
 * {@code Glyphworks_Item_Sink} block, simulating an infinite drain.
 */
public final class ItemSinkSystemTests {

    private static final String SINK_ID = "Glyphworks_Item_Sink";
    private static final String ITEM_ID = "Rock_Stone";

    private ItemSinkSystemTests() {
    }

    public static void register(String moduleId) {
        TestRegistry.register(moduleId, buildSuite());
    }

    private static TestSuite buildSuite() {
        return new TestSuite("item_sink_system")
                .test(sinkDrainsContainer())
                .test(sinkIsIdempotentWhenEmpty());
    }

    // -------------------------------------------------------------------------
    // Test 1: sink clears all items from its container each tick
    // -------------------------------------------------------------------------

    private static TestCase sinkDrainsContainer() {
        return new TestCase("sink_drains_container", 3, 3, 3)
                .step(Steps.run(ctx -> {
                    ctx.getWorld().setBlock(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ(), SINK_ID);
                }))
                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))
                .step(Steps.run(ctx -> {
                    ItemContainerBlock icb = ItemTestUtil.getItemContainerBlock(ctx.getWorld(),
                            new Vector3i(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()));
                    if (icb != null)
                        icb.getItemContainer().setItemStackForSlot((short) 0, new ItemStack(ITEM_ID, 5), false);
                }))
                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))
                .step(Steps.assertThat(ctx -> {
                    ItemContainerBlock icb = ItemTestUtil.getItemContainerBlock(ctx.getWorld(),
                            new Vector3i(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()));
                    return icb != null && ItemTestUtil.countItems(icb.getItemContainer()) == 0;
                }, "ItemSinkSystem drains all items from the container each tick"));
    }

    // -------------------------------------------------------------------------
    // Test 2: sink is a no-op when container is already empty
    // -------------------------------------------------------------------------

    private static TestCase sinkIsIdempotentWhenEmpty() {
        return new TestCase("sink_is_idempotent_when_empty", 3, 3, 3)
                .step(Steps.run(ctx -> {
                    ctx.getWorld().setBlock(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ(), SINK_ID);
                }))
                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))
                .step(Steps.assertThat(ctx -> {
                    ItemContainerBlock icb = ItemTestUtil.getItemContainerBlock(ctx.getWorld(),
                            new Vector3i(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()));
                    return icb != null && ItemTestUtil.countItems(icb.getItemContainer()) == 0;
                }, "ItemSinkSystem is a no-op when the container is already empty"));
    }
}
