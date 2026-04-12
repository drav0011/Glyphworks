package dev.drav.glyphworks.item.tests.system;

import org.joml.Vector3i;

import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;

import dev.drav.glyphworks.item.tests.ItemTestUtil;
import dev.drav.glyphworks.test.framework.Steps;
import dev.drav.glyphworks.test.framework.TestCase;
import dev.drav.glyphworks.test.framework.TestRegistry;
import dev.drav.glyphworks.test.framework.TestSuite;

/**
 * Suite {@code "item_dropper_system"} — integration tests for
 * {@link dev.drav.glyphworks.item.system.ItemDropperSystem}.
 *
 * <p>
 * The dropper block is placed at the centre of a 3×3×3 area:
 * {@code (ox+1, oy+1, oz+1)}. Every tick the system drains all items from the
 * container and spawns them as entities at the block's position.
 */
public final class ItemDropperSystemTests {

    private static final String DROPPER_ID = "Glyphworks_Item_Dropper";
    private static final String ITEM_ID = "Rock_Stone_Cobble";

    private ItemDropperSystemTests() {
    }

    public static void register(String moduleId) {
        TestRegistry.register(moduleId, buildSuite());
    }

    private static TestSuite buildSuite() {
        return new TestSuite("item_dropper_system")
                .test(dropperEmptiesContainer())
                .test(dropperNoopWhenEmpty());
    }

    // -------------------------------------------------------------------------
    // Test 1: dropper drains all items from its container each tick
    // -------------------------------------------------------------------------

    private static TestCase dropperEmptiesContainer() {
        return new TestCase("dropper_empties_container", 3, 3, 3)
                .step(Steps.run(ctx -> {
                    int bx = ctx.getOriginX() + 1, by = ctx.getOriginY() + 1, bz = ctx.getOriginZ() + 1;
                    ctx.getWorld().setBlock(bx, by, bz, DROPPER_ID);
                }))
                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))
                .step(Steps.run(ctx -> {
                    int bx = ctx.getOriginX() + 1, by = ctx.getOriginY() + 1, bz = ctx.getOriginZ() + 1;
                    ItemContainerBlock icb = ItemTestUtil.getItemContainerBlock(
                            ctx.getWorld(), new Vector3i(bx, by, bz));
                    if (icb != null)
                        ItemTestUtil.seedItems(icb.getItemContainer(), ITEM_ID, 10);
                }))
                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))
                .step(Steps.assertThat(ctx -> {
                    int bx = ctx.getOriginX() + 1, by = ctx.getOriginY() + 1, bz = ctx.getOriginZ() + 1;
                    ItemContainerBlock icb = ItemTestUtil.getItemContainerBlock(
                            ctx.getWorld(), new Vector3i(bx, by, bz));
                    return icb != null && ItemTestUtil.countItems(icb.getItemContainer()) == 0;
                }, "ItemDropperSystem drains all items from the container each tick"));
    }

    // -------------------------------------------------------------------------
    // Test 2: dropper is a no-op when the container is already empty
    // -------------------------------------------------------------------------

    private static TestCase dropperNoopWhenEmpty() {
        return new TestCase("dropper_noop_when_empty", 3, 3, 3)
                .step(Steps.run(ctx -> {
                    int bx = ctx.getOriginX() + 1, by = ctx.getOriginY() + 1, bz = ctx.getOriginZ() + 1;
                    ctx.getWorld().setBlock(bx, by, bz, DROPPER_ID);
                }))
                .step(Steps.wait(ctx -> 3 * ctx.getWorld().getTps()))
                .step(Steps.assertThat(ctx -> {
                    int bx = ctx.getOriginX() + 1, by = ctx.getOriginY() + 1, bz = ctx.getOriginZ() + 1;
                    ItemContainerBlock icb = ItemTestUtil.getItemContainerBlock(
                            ctx.getWorld(), new Vector3i(bx, by, bz));
                    return icb != null && ItemTestUtil.countItems(icb.getItemContainer()) == 0;
                }, "ItemDropperSystem is a no-op when the container is already empty"));
    }
}
