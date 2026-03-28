package dev.drav.glyphworks.grid.tests;

import org.joml.Vector3i;

import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.util.thread.TickingThread;

import dev.drav.glyphworks.GlyphworksPlugin;
import dev.drav.glyphworks.grid.event.PlaceGridBlockEvent;
import dev.drav.glyphworks.grid.graph.GridGraph;
import dev.drav.glyphworks.grid.lookup.GridLookup;
import dev.drav.glyphworks.grid.type.GridType;
import dev.drav.glyphworks.test.Steps;
import dev.drav.glyphworks.test.TestCase;
import dev.drav.glyphworks.test.TestRegistry;
import dev.drav.glyphworks.test.TestSuite;

/**
 * Suite {@code "grid_block_change"} — tests
 * {@link dev.drav.glyphworks.grid.system.BlockChangeGridSystem}
 * auto-detection of block placements and removals via {@code world.setBlock()}.
 *
 * <p>
 * Unlike {@code grid_connections}, these tests call <em>only</em>
 * {@code world.setBlock()} — no manual {@link PlaceGridBlockEvent#connectBlock}
 * or {@link dev.drav.glyphworks.grid.event.BreakGridBlockEvent#disconnectBlock}
 * calls. The system must detect the change on its next tick and wire/unwire the
 * graph automatically.
 *
 * <p>
 * {@link Steps#waitUntil} is used so tests pass as soon as the system has
 * processed the block change, rather than spinning for a fixed tick count.
 */
public final class GridBlockChangeTests {

    private static final String PIPE_ID = "Pipe";
    private static final GridType ITEM_GRID = GridType.of("Item");

    /**
     * Maximum ticks to wait for the block-change system to process a single change.
     */
    private static final int TIMEOUT_TICKS = 5 * TickingThread.TPS;

    private GridBlockChangeTests() {
    }

    public static void register(String moduleId) {
        TestRegistry.register(moduleId, buildSuite());
    }

    private static TestSuite buildSuite() {
        return new TestSuite("grid_block_change")
                .test(autoConnectOnSetBlock())
                .test(autoDisconnectOnSetBlockEmpty())
                .test(autoReconnectAfterSetBlockCycle())
                .test(survivorNeighborCleanedOnAutoDisconnect())
                .test(chainSplitAutoDisconnect());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static Vector3i v(int x, int y, int z) {
        return new Vector3i(x, y, z);
    }

    private static GridGraph graph(World world) {
        return GlyphworksPlugin.get().getOrCreateGridGraph(world, ITEM_GRID);
    }

    private static boolean connected(World world, Vector3i a, Vector3i b) {
        return GridTestUtil.connected(world, a, b, ITEM_GRID);
    }

    // -------------------------------------------------------------------------
    // Test 1: BlockChangeGridSystem auto-connects two adjacent pipes placed via
    // setBlock
    // -------------------------------------------------------------------------

    private static TestCase autoConnectOnSetBlock() {
        return new TestCase("auto_connect_on_setblock", 5, 3, 1)
                // Place both pipes using only world.setBlock — no manual connectBlock.
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    w.setBlock(ox, oy, oz, PIPE_ID);
                    w.setBlock(ox + 1, oy, oz, PIPE_ID);
                }))
                // Wait until the system auto-detects and connects them.
                .step(Steps.waitUntil(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    Vector3i pa = v(ox, oy, oz);
                    Vector3i pb = v(ox + 1, oy, oz);
                    return connected(w, pa, pb) && connected(w, pb, pa);
                }, TIMEOUT_TICKS, "BlockChangeGridSystem auto-connects two adjacent pipes placed via setBlock"));
    }

    // -------------------------------------------------------------------------
    // Test 2: BlockChangeGridSystem auto-disconnects when a pipe is replaced with
    // Empty
    // -------------------------------------------------------------------------

    private static TestCase autoDisconnectOnSetBlockEmpty() {
        return new TestCase("auto_disconnect_on_setblock_empty", 5, 3, 1)
                // Establish a known-good connected state using explicit connectBlock.
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    w.setBlock(ox, oy, oz, PIPE_ID);
                    w.setBlock(ox + 1, oy, oz, PIPE_ID);
                    PlaceGridBlockEvent.connectBlock(w, v(ox, oy, oz));
                    PlaceGridBlockEvent.connectBlock(w, v(ox + 1, oy, oz));
                }))
                // Wait until connected.
                .step(Steps.waitUntil(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    return connected(w, v(ox, oy, oz), v(ox + 1, oy, oz));
                }, TIMEOUT_TICKS, "pipes are connected before testing disconnect"))
                // Remove one pipe using only world.setBlock — no manual disconnectBlock.
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    w.setBlock(ox + 1, oy, oz, "Empty");
                }))
                // Wait until the system auto-detects the removal and removes the node.
                .step(Steps.waitUntil(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    return !graph(w).contains(v(ox + 1, oy, oz));
                }, TIMEOUT_TICKS, "BlockChangeGridSystem auto-removes a pipe replaced with Empty"));
    }

    // -------------------------------------------------------------------------
    // Test 3: full break-and-replace cycle driven entirely by setBlock
    // -------------------------------------------------------------------------

    private static TestCase autoReconnectAfterSetBlockCycle() {
        return new TestCase("auto_reconnect_after_setblock_cycle", 5, 3, 1)
                // Phase 1: auto-connect A and B via setBlock only.
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    w.setBlock(ox, oy, oz, PIPE_ID);
                    w.setBlock(ox + 1, oy, oz, PIPE_ID);
                }))
                .step(Steps.waitUntil(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    return connected(w, v(ox, oy, oz), v(ox + 1, oy, oz));
                }, TIMEOUT_TICKS, "pipes auto-connected before cycle test"))
                // Phase 2: auto-remove B.
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    w.setBlock(ox + 1, oy, oz, "Empty");
                }))
                .step(Steps.waitUntil(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    return !graph(w).contains(v(ox + 1, oy, oz));
                }, TIMEOUT_TICKS, "B auto-removed before re-placement"))
                // Phase 3: re-place B via setBlock and verify reconnect.
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    w.setBlock(ox + 1, oy, oz, PIPE_ID);
                }))
                .step(Steps.waitUntil(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    Vector3i pa = v(ox, oy, oz);
                    Vector3i pb = v(ox + 1, oy, oz);
                    return connected(w, pa, pb) && connected(w, pb, pa);
                }, TIMEOUT_TICKS,
                        "BlockChangeGridSystem reconnects two pipes after a full break-and-replace cycle via setBlock only"));
    }

    // -------------------------------------------------------------------------
    // Test 4: surviving pipe's component.neighbors is clean after auto-disconnect
    //
    // The existing test 2 only checks whether the dead node is gone from the
    // graph. This test also verifies the surviving node's persisted neighbor
    // set is updated by the system.
    // -------------------------------------------------------------------------

    private static TestCase survivorNeighborCleanedOnAutoDisconnect() {
        return new TestCase("survivor_neighbor_cleaned_on_auto_disconnect", 5, 3, 1)
                // Establish a known-good connected pair using explicit connectBlock.
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    w.setBlock(ox, oy, oz, PIPE_ID);
                    w.setBlock(ox + 1, oy, oz, PIPE_ID);
                    PlaceGridBlockEvent.connectBlock(w, v(ox, oy, oz));
                    PlaceGridBlockEvent.connectBlock(w, v(ox + 1, oy, oz));
                }))
                .step(Steps.waitUntil(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    return connected(w, v(ox, oy, oz), v(ox + 1, oy, oz));
                }, TIMEOUT_TICKS, "pipes connected before testing auto-disconnect survivor state"))
                // Auto-remove the second pipe via setBlock.
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    w.setBlock(ox + 1, oy, oz, "Empty");
                }))
                // Wait until the survivor's component.neighbors is empty.
                .step(Steps.waitUntil(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    GridLookup lu = GridLookup.resolve(w.getChunkStore(), v(ox, oy, oz));
                    return lu != null && lu.component().getNeighbors().isEmpty();
                }, TIMEOUT_TICKS, "surviving pipe's component.neighbors is empty after auto-disconnect via setBlock"));
    }

    // -------------------------------------------------------------------------
    // Test 5: breaking the middle of a chain via setBlock splits A and C
    // -------------------------------------------------------------------------

    private static TestCase chainSplitAutoDisconnect() {
        return new TestCase("chain_split_auto_disconnect", 7, 3, 1)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    w.setBlock(ox, oy, oz, PIPE_ID); // A
                    w.setBlock(ox + 1, oy, oz, PIPE_ID); // B (middle)
                    w.setBlock(ox + 2, oy, oz, PIPE_ID); // C
                    PlaceGridBlockEvent.connectBlock(w, v(ox, oy, oz));
                    PlaceGridBlockEvent.connectBlock(w, v(ox + 1, oy, oz));
                    PlaceGridBlockEvent.connectBlock(w, v(ox + 2, oy, oz));
                }))
                .step(Steps.waitUntil(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    return connected(w, v(ox, oy, oz), v(ox + 1, oy, oz))
                            && connected(w, v(ox + 1, oy, oz), v(ox + 2, oy, oz));
                }, TIMEOUT_TICKS, "chain A-B-C connected before testing split"))
                // Remove B via setBlock only.
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    w.setBlock(ox + 1, oy, oz, "Empty");
                }))
                .step(Steps.waitUntil(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    GridGraph g = graph(w);
                    Vector3i pa = v(ox, oy, oz);
                    Vector3i pc = v(ox + 2, oy, oz);
                    return g.contains(pa) && g.contains(pc)
                            && !g.contains(v(ox + 1, oy, oz))
                            && !g.getComponent(pa).contains(pc);
                }, TIMEOUT_TICKS, "auto-removing the middle pipe splits A and C into separate components"));
    }
}
