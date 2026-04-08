package dev.drav.glyphworks.item.tests;

import org.joml.Vector3i;

import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;

import dev.drav.glyphworks.test.framework.Steps;
import dev.drav.glyphworks.test.framework.TestCase;
import dev.drav.glyphworks.test.framework.TestRegistry;
import dev.drav.glyphworks.test.framework.TestSuite;

/**
 * Suite {@code "block_miner_system"} — integration tests for
 * {@link dev.drav.glyphworks.item.system.BlockMinerSystem}.
 *
 * <p>
 * The miner block defaults to facing Down (rotation 0), so the target cell is
 * always directly below it at {@code (ox+1, oy, oz+1)}. The miner is placed at
 * the centre of a 3×3×3 area: {@code (ox+1, oy+1, oz+1)}.
 *
 * <p>
 * {@code Rock_Stone} has a quality-0 {@link com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockBreakingDropType},
 * so the miner completes in a single tick and drops {@code Rock_Stone_Cobble}.
 */
public final class BlockMinerSystemTests {

    private static final String MINER_ID = "Glyphworks_Block_Miner";
    private static final String TARGET_BLOCK_ID = "Rock_Stone";
    /** Drop item for Rock_Stone — quality 0, mines in one tick. */
    private static final String DROP_ITEM_ID = "Rock_Stone_Cobble";

    private BlockMinerSystemTests() {
    }

    public static void register(String moduleId) {
        TestRegistry.register(moduleId, buildSuite());
    }

    private static TestSuite buildSuite() {
        return new TestSuite("block_miner_system")
                .test(minerMinesBlockIntoContainer())
                .test(minerStallsOnBackPressure())
                .test(minerSkipsEmptyTarget());
    }

    // -------------------------------------------------------------------------
    // Test 1: miner extracts a breakable block and stores the drop
    // -------------------------------------------------------------------------

    private static TestCase minerMinesBlockIntoContainer() {
        return new TestCase("miner_mines_block_into_container", 3, 3, 3)
                .step(Steps.run(ctx -> {
                    int bx = ctx.getOriginX() + 1, by = ctx.getOriginY() + 1, bz = ctx.getOriginZ() + 1;
                    int tx = bx, ty = by - 1, tz = bz;
                    ctx.getWorld().setBlock(bx, by, bz, MINER_ID);
                    ctx.getWorld().setBlock(tx, ty, tz, TARGET_BLOCK_ID);
                }))
                // Wait enough ticks for block-entity init + one mining cycle.
                .step(Steps.wait(ctx -> 3 * ctx.getWorld().getTps()))
                .step(Steps.assertThat(ctx -> {
                    int bx = ctx.getOriginX() + 1, by = ctx.getOriginY() + 1, bz = ctx.getOriginZ() + 1;
                    int tx = bx, ty = by - 1, tz = bz;
                    // World block at target must be cleared.
                    if (ItemTestUtil.getBlockId(ctx.getWorld(), tx, ty, tz) != 0)
                        return false;
                    // Miner container must hold at least one drop item.
                    ItemContainerBlock icb = ItemTestUtil.getItemContainerBlock(
                            ctx.getWorld(), new Vector3i(bx, by, bz));
                    return icb != null && ItemTestUtil.countItems(icb.getItemContainer()) > 0;
                }, "BlockMinerSystem mines Rock_Stone, clears the world block, and stores the drop"));
    }

    // -------------------------------------------------------------------------
    // Test 2: miner stalls when the container cannot accept the drop
    // -------------------------------------------------------------------------

    private static TestCase minerStallsOnBackPressure() {
        return new TestCase("miner_stalls_on_back_pressure", 3, 3, 3)
                .step(Steps.run(ctx -> {
                    int bx = ctx.getOriginX() + 1, by = ctx.getOriginY() + 1, bz = ctx.getOriginZ() + 1;
                    ctx.getWorld().setBlock(bx, by, bz, MINER_ID);
                }))
                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))
                .step(Steps.run(ctx -> {
                    int bx = ctx.getOriginX() + 1, by = ctx.getOriginY() + 1, bz = ctx.getOriginZ() + 1;
                    int tx = bx, ty = by - 1, tz = bz;
                    // Fill the miner's container — drop can no longer be accepted.
                    ItemContainerBlock icb = ItemTestUtil.getItemContainerBlock(
                            ctx.getWorld(), new Vector3i(bx, by, bz));
                    if (icb != null)
                        ItemTestUtil.fillContainer(icb.getItemContainer(), DROP_ITEM_ID);
                    // Place the target block after filling so progress cannot accumulate.
                    ctx.getWorld().setBlock(tx, ty, tz, TARGET_BLOCK_ID);
                }))
                .step(Steps.wait(ctx -> 3 * ctx.getWorld().getTps()))
                .step(Steps.assertThat(ctx -> {
                    int bx = ctx.getOriginX() + 1, by = ctx.getOriginY() + 1, bz = ctx.getOriginZ() + 1;
                    int tx = bx, ty = by - 1, tz = bz;
                    // Block must still be present — back-pressure held the miner.
                    return ItemTestUtil.getBlockId(ctx.getWorld(), tx, ty, tz) != 0;
                }, "BlockMinerSystem leaves the target block intact when the container is full"));
    }

    // -------------------------------------------------------------------------
    // Test 3: miner is a no-op when the target cell is empty
    // -------------------------------------------------------------------------

    private static TestCase minerSkipsEmptyTarget() {
        return new TestCase("miner_skips_empty_target", 3, 3, 3)
                .step(Steps.run(ctx -> {
                    int bx = ctx.getOriginX() + 1, by = ctx.getOriginY() + 1, bz = ctx.getOriginZ() + 1;
                    // Target cell below is already empty — miner has nothing to do.
                    ctx.getWorld().setBlock(bx, by, bz, MINER_ID);
                }))
                .step(Steps.wait(ctx -> 3 * ctx.getWorld().getTps()))
                .step(Steps.assertThat(ctx -> {
                    int bx = ctx.getOriginX() + 1, by = ctx.getOriginY() + 1, bz = ctx.getOriginZ() + 1;
                    ItemContainerBlock icb = ItemTestUtil.getItemContainerBlock(
                            ctx.getWorld(), new Vector3i(bx, by, bz));
                    return icb != null && ItemTestUtil.countItems(icb.getItemContainer()) == 0;
                }, "BlockMinerSystem leaves the container empty when the target cell has no block"));
    }
}
