package dev.drav.glyphworks.test.tests;

import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.util.thread.TickingThread;

import dev.drav.glyphworks.test.Steps;
import dev.drav.glyphworks.test.TestCase;
import dev.drav.glyphworks.test.TestRegistry;
import dev.drav.glyphworks.test.TestSuite;

/**
 * Basic sanity tests for block placement in the world.
 *
 * <p>All tests place blocks relative to the player's current feet position,
 * so they can be run in any world as long as the area around the player is clear.
 */
public final class BasicBlockTests {

    /** Block type used across all placement tests — a solid non-physics stone block. */
    private static final String TEST_BLOCK = "Rock_Stone";

    /** Number of ticks to wait between placing and asserting (1 second at native TPS). */
    private static final int WAIT_TICKS = TickingThread.TPS;

    private BasicBlockTests() {}

    /**
     * Builds and registers the {@code "blocks"} test suite.
     * Call once from {@code GlyphworksPlugin.setup()}.
     */
    public static void register(String moduleId) {
        TestRegistry.register(moduleId, buildSuite());
    }

    private static TestSuite buildSuite() {
        return new TestSuite("blocks")
                .test(rowOf3BlocksX())
                .test(colOf3BlocksY())
                .test(rowOf3BlocksZ());
    }

    // -------------------------------------------------------------------------
    // Test: row along X
    // -------------------------------------------------------------------------

    private static TestCase rowOf3BlocksX() {
        return new TestCase("row_of_3_blocks_x", 5, 3, 1)
                // Step 1 — place 3 stone blocks in a row at the test's assigned origin.
                .step(Steps.run(ctx -> {
                    World world = ctx.getWorld();
                    for (int i = 0; i < 3; i++) {
                        world.setBlock(ctx.getOriginX() + i, ctx.getOriginY(), ctx.getOriginZ(), TEST_BLOCK);
                    }
                }))

                // Step 2 — wait 1 second (native TPS ticks) before checking.
                .step(Steps.wait(WAIT_TICKS))

                // Step 3 — assert all 3 blocks are still Rock_Stone at the recorded positions.
                .step(Steps.assertThat(ctx -> {
                    World world = ctx.getWorld();
                    for (int i = 0; i < 3; i++) {
                        var blockType = world.getBlockType(ctx.getOriginX() + i, ctx.getOriginY(), ctx.getOriginZ());
                        if (blockType == null || !TEST_BLOCK.equals(blockType.getId())) {
                            return false;
                        }
                    }
                    return true;
                }, "all 3 Rock_Stone blocks persist along X after " + WAIT_TICKS + " ticks"));
    }

    // -------------------------------------------------------------------------
    // Test: column along Y (vertical stack)
    // -------------------------------------------------------------------------

    private static TestCase colOf3BlocksY() {
        return new TestCase("col_of_3_blocks_y", 3, 3, 3)
                .step(Steps.run(ctx -> {
                    World world = ctx.getWorld();
                    for (int i = 0; i < 3; i++) {
                        world.setBlock(ctx.getOriginX(), ctx.getOriginY() + i, ctx.getOriginZ(), TEST_BLOCK);
                    }
                }))
                .step(Steps.wait(WAIT_TICKS))
                .step(Steps.assertThat(ctx -> {
                    World world = ctx.getWorld();
                    for (int i = 0; i < 3; i++) {
                        var blockType = world.getBlockType(ctx.getOriginX(), ctx.getOriginY() + i, ctx.getOriginZ());
                        if (blockType == null || !TEST_BLOCK.equals(blockType.getId())) {
                            return false;
                        }
                    }
                    return true;
                }, "all 3 Rock_Stone blocks persist along Y after " + WAIT_TICKS + " ticks"));
    }

    // -------------------------------------------------------------------------
    // Test: row along Z
    // -------------------------------------------------------------------------

    private static TestCase rowOf3BlocksZ() {
        return new TestCase("row_of_3_blocks_z", 3, 5, 1)
                .step(Steps.run(ctx -> {
                    World world = ctx.getWorld();
                    for (int i = 0; i < 3; i++) {
                        world.setBlock(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ() + i, TEST_BLOCK);
                    }
                }))
                .step(Steps.wait(WAIT_TICKS))
                .step(Steps.assertThat(ctx -> {
                    World world = ctx.getWorld();
                    for (int i = 0; i < 3; i++) {
                        var blockType = world.getBlockType(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ() + i);
                        if (blockType == null || !TEST_BLOCK.equals(blockType.getId())) {
                            return false;
                        }
                    }
                    return true;
                }, "all 3 Rock_Stone blocks persist along Z after " + WAIT_TICKS + " ticks"));
    }
}
