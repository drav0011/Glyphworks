package dev.drav.glyphworks.item.tests.system;

import org.joml.Vector3i;

import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;

import dev.drav.glyphworks.item.tests.ItemTestUtil;
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
 *
 * <p>
 * {@code Rock_Basalt} has quality-1 (40 ticks).
 * {@code Rock_Basalt_Stalactite_Large} has quality-2 (100 ticks).
 * The timing tests verify that these blocks are NOT mined prematurely and ARE
 * mined after the correct number of ticks has elapsed.
 */
public final class BlockMinerSystemTests {

    private static final String MINER_ID = "Glyphworks_Block_Miner";
    private static final String TARGET_BLOCK_ID = "Rock_Stone";
    /** Drop item for Rock_Stone — quality 0, mines in one tick. */
    private static final String DROP_ITEM_ID = "Rock_Stone_Cobble";
    /** Quality 1 block — requires 40 ticks to mine. */
    private static final String QUALITY1_BLOCK_ID = "Rock_Basalt";
    private static final String QUALITY1_DROP_ID = "Rock_Basalt_Cobble";
    /**
     * A Glyphworks custom-model block that defines no {@code Gathering} field and
     * is therefore unbreakable by the miner (gathering == null). Using a mod-owned
     * block avoids brittleness against engine asset changes.
     */
    private static final String UNBREAKABLE_CUSTOM_MODEL_BLOCK_ID = "Glyphworks_Fluid_Tank";

    private BlockMinerSystemTests() {
    }

    public static void register(String moduleId) {
        TestRegistry.register(moduleId, buildSuite());
    }

    private static TestSuite buildSuite() {
        return new TestSuite("block_miner_system")
                .test(minerMinesBlockIntoContainer())
                .test(minerStallsOnBackPressure())
                .test(minerSkipsEmptyTarget())
                .test(minerRespectsQuality1Speed())
                .test(minerSkipsUnbreakableCustomModelBlock());
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

    // -------------------------------------------------------------------------
    // Test 4: quality-1 block (Rock_Basalt) requires 40 ticks to mine
    // -------------------------------------------------------------------------

    private static TestCase minerRespectsQuality1Speed() {
        return new TestCase("miner_respects_quality1_speed", 3, 3, 3)
                .step(Steps.run(ctx -> {
                    int bx = ctx.getOriginX() + 1, by = ctx.getOriginY() + 1, bz = ctx.getOriginZ() + 1;
                    ctx.getWorld().setBlock(bx, by, bz, MINER_ID);
                    ctx.getWorld().setBlock(bx, by - 1, bz, QUALITY1_BLOCK_ID);
                }))
                // After 200 ticks the block must still be present (quality 1 needs 400 ticks).
                .step(Steps.wait(200))
                .step(Steps.assertThat(ctx -> {
                    int bx = ctx.getOriginX() + 1, by = ctx.getOriginY() + 1, bz = ctx.getOriginZ() + 1;
                    return ItemTestUtil.getBlockId(ctx.getWorld(), bx, by - 1, bz) != 0;
                }, QUALITY1_BLOCK_ID + " must NOT be mined before 400 ticks (checked at tick 200)"))
                // Wait another 210 ticks (total ≥ 410) — mining must now be complete.
                .step(Steps.wait(210))
                .step(Steps.assertThat(ctx -> {
                    int bx = ctx.getOriginX() + 1, by = ctx.getOriginY() + 1, bz = ctx.getOriginZ() + 1;
                    if (ItemTestUtil.getBlockId(ctx.getWorld(), bx, by - 1, bz) != 0)
                        return false;
                    ItemContainerBlock icb = ItemTestUtil.getItemContainerBlock(
                            ctx.getWorld(), new Vector3i(bx, by, bz));
                    return icb != null && ItemTestUtil.countItems(icb.getItemContainer()) > 0;
                }, QUALITY1_BLOCK_ID + " must be fully mined after 400+ ticks with drop " + QUALITY1_DROP_ID + " in container"));
    }

    // -------------------------------------------------------------------------
    // Test 5: custom-model block with gathering==null is treated as unbreakable
    // -------------------------------------------------------------------------

    private static TestCase minerSkipsUnbreakableCustomModelBlock() {
        return new TestCase("miner_skips_unbreakable_custom_model_block", 3, 3, 3)
                .step(Steps.run(ctx -> {
                    int bx = ctx.getOriginX() + 1, by = ctx.getOriginY() + 1, bz = ctx.getOriginZ() + 1;
                    ctx.getWorld().setBlock(bx, by, bz, MINER_ID);
                    ctx.getWorld().setBlock(bx, by - 1, bz, UNBREAKABLE_CUSTOM_MODEL_BLOCK_ID);
                }))
                // Wait well past any mining threshold — 60 ticks is more than enough.
                .step(Steps.wait(60))
                .step(Steps.assertThat(ctx -> {
                    int bx = ctx.getOriginX() + 1, by = ctx.getOriginY() + 1, bz = ctx.getOriginZ() + 1;
                    // Block must still be present — gathering==null means unbreakable.
                    if (ItemTestUtil.getBlockId(ctx.getWorld(), bx, by - 1, bz) == 0)
                        return false;
                    // Container must remain empty.
                    ItemContainerBlock icb = ItemTestUtil.getItemContainerBlock(
                            ctx.getWorld(), new Vector3i(bx, by, bz));
                    return icb != null && ItemTestUtil.countItems(icb.getItemContainer()) == 0;
                }, UNBREAKABLE_CUSTOM_MODEL_BLOCK_ID + " defines no gathering and must not be mined"));
    }
}
