package dev.drav.glyphworks.item.tests.system;

import org.joml.Vector3i;

import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;

import dev.drav.glyphworks.item.tests.ItemTestUtil;
import dev.drav.glyphworks.test.framework.Steps;
import dev.drav.glyphworks.test.framework.TestCase;
import dev.drav.glyphworks.test.framework.TestRegistry;
import dev.drav.glyphworks.test.framework.TestSuite;

/**
 * Suite {@code "block_placer_system"} — integration tests for
 * {@link dev.drav.glyphworks.item.system.BlockPlacerSystem}.
 *
 * <p>
 * The placer block defaults to facing Down (rotation 0), so the target cell is
 * always directly below it at {@code (ox+1, oy, oz+1)}. The placer is placed at
 * the centre of a 3×3×3 area: {@code (ox+1, oy+1, oz+1)}.
 *
 * <p>
 * {@code Rock_Stone} is used as the block-item payload because it has a
 * {@code BlockType} definition and can be placed into the world.
 * {@code Glyphworks_Rune_Empty_Fire} is a non-block item used to verify that
 * the system skips non-placeable inventory contents.
 */
public final class BlockPlacerSystemTests {

    private static final String PLACER_ID = "Glyphworks_Block_Placer";
    /** A block item — placing it will produce a Rock_Stone world block. */
    private static final String BLOCK_ITEM_ID = "Rock_Stone";
    /** A non-block item — must be ignored by the placer. */
    private static final String NONBLOCK_ITEM_ID = "Glyphworks_Rune_Empty_Fire";

    private BlockPlacerSystemTests() {
    }

    public static void register(String moduleId) {
        TestRegistry.register(moduleId, buildSuite());
    }

    private static TestSuite buildSuite() {
        return new TestSuite("block_placer_system")
                .test(placerPlacesBlockBelow())
                .test(placerSkipsOccupiedTarget())
                .test(placerSkipsNonBlockItems());
    }

    // -------------------------------------------------------------------------
    // Test 1: placer places a block-item from the container into the world
    // -------------------------------------------------------------------------

    private static TestCase placerPlacesBlockBelow() {
        return new TestCase("placer_places_block_below", 3, 3, 3)
                .step(Steps.run(ctx -> {
                    int bx = ctx.getOriginX() + 1, by = ctx.getOriginY() + 1, bz = ctx.getOriginZ() + 1;
                    ctx.getWorld().setBlock(bx, by, bz, PLACER_ID);
                }))
                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))
                .step(Steps.run(ctx -> {
                    int bx = ctx.getOriginX() + 1, by = ctx.getOriginY() + 1, bz = ctx.getOriginZ() + 1;
                    ItemContainerBlock icb = ItemTestUtil.getItemContainerBlock(
                            ctx.getWorld(), new Vector3i(bx, by, bz));
                    if (icb != null)
                        ItemTestUtil.seedItems(icb.getItemContainer(), BLOCK_ITEM_ID, 1);
                }))
                .step(Steps.wait(ctx -> 3 * ctx.getWorld().getTps()))
                .step(Steps.assertThat(ctx -> {
                    int bx = ctx.getOriginX() + 1, by = ctx.getOriginY() + 1, bz = ctx.getOriginZ() + 1;
                    int tx = bx, ty = by - 1, tz = bz;
                    // Target cell must now hold a block.
                    if (ItemTestUtil.getBlockId(ctx.getWorld(), tx, ty, tz) == 0)
                        return false;
                    // Container must be empty — the item was consumed.
                    ItemContainerBlock icb = ItemTestUtil.getItemContainerBlock(
                            ctx.getWorld(), new Vector3i(bx, by, bz));
                    return icb != null && ItemTestUtil.countItems(icb.getItemContainer()) == 0;
                }, "BlockPlacerSystem places a Rock_Stone block and removes the item from the container"));
    }

    // -------------------------------------------------------------------------
    // Test 2: placer does not consume items when the target cell is occupied
    // -------------------------------------------------------------------------

    private static TestCase placerSkipsOccupiedTarget() {
        return new TestCase("placer_skips_occupied_target", 3, 3, 3)
                .step(Steps.run(ctx -> {
                    int bx = ctx.getOriginX() + 1, by = ctx.getOriginY() + 1, bz = ctx.getOriginZ() + 1;
                    int tx = bx, ty = by - 1, tz = bz;
                    ctx.getWorld().setBlock(bx, by, bz, PLACER_ID);
                    // Pre-occupy the target cell before the placer can act.
                    ctx.getWorld().setBlock(tx, ty, tz, BLOCK_ITEM_ID);
                }))
                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))
                .step(Steps.run(ctx -> {
                    int bx = ctx.getOriginX() + 1, by = ctx.getOriginY() + 1, bz = ctx.getOriginZ() + 1;
                    ItemContainerBlock icb = ItemTestUtil.getItemContainerBlock(
                            ctx.getWorld(), new Vector3i(bx, by, bz));
                    if (icb != null)
                        ItemTestUtil.seedItems(icb.getItemContainer(), BLOCK_ITEM_ID, 1);
                }))
                .step(Steps.wait(ctx -> 3 * ctx.getWorld().getTps()))
                .step(Steps.assertThat(ctx -> {
                    int bx = ctx.getOriginX() + 1, by = ctx.getOriginY() + 1, bz = ctx.getOriginZ() + 1;
                    // Container must still hold the item — placer was blocked.
                    ItemContainerBlock icb = ItemTestUtil.getItemContainerBlock(
                            ctx.getWorld(), new Vector3i(bx, by, bz));
                    return icb != null && ItemTestUtil.countItems(icb.getItemContainer()) == 1;
                }, "BlockPlacerSystem retains the item when the target cell is already occupied"));
    }

    // -------------------------------------------------------------------------
    // Test 3: placer ignores non-block items in the container
    // -------------------------------------------------------------------------

    private static TestCase placerSkipsNonBlockItems() {
        return new TestCase("placer_skips_non_block_items", 3, 3, 3)
                .step(Steps.run(ctx -> {
                    int bx = ctx.getOriginX() + 1, by = ctx.getOriginY() + 1, bz = ctx.getOriginZ() + 1;
                    ctx.getWorld().setBlock(bx, by, bz, PLACER_ID);
                }))
                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))
                .step(Steps.run(ctx -> {
                    int bx = ctx.getOriginX() + 1, by = ctx.getOriginY() + 1, bz = ctx.getOriginZ() + 1;
                    ItemContainerBlock icb = ItemTestUtil.getItemContainerBlock(
                            ctx.getWorld(), new Vector3i(bx, by, bz));
                    if (icb != null)
                        ItemTestUtil.seedItems(icb.getItemContainer(), NONBLOCK_ITEM_ID, 5);
                }))
                .step(Steps.wait(ctx -> 3 * ctx.getWorld().getTps()))
                .step(Steps.assertThat(ctx -> {
                    int bx = ctx.getOriginX() + 1, by = ctx.getOriginY() + 1, bz = ctx.getOriginZ() + 1;
                    int tx = bx, ty = by - 1, tz = bz;
                    // Target cell must remain empty — no block was placed.
                    if (ItemTestUtil.getBlockId(ctx.getWorld(), tx, ty, tz) != 0)
                        return false;
                    // Non-block items must remain in the container.
                    ItemContainerBlock icb = ItemTestUtil.getItemContainerBlock(
                            ctx.getWorld(), new Vector3i(bx, by, bz));
                    return icb != null && ItemTestUtil.countItems(icb.getItemContainer()) == 5;
                }, "BlockPlacerSystem skips non-block items and leaves the target cell empty"));
    }
}
