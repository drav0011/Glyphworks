package dev.drav.glyphworks.grid.tests.system;

import com.hypixel.hytale.math.vector.Vector3i;

import com.hypixel.hytale.server.core.universe.world.World;

import dev.drav.glyphworks.GlyphworksPlugin;
import dev.drav.glyphworks.grid.event.BreakGridBlockEvent;
import dev.drav.glyphworks.grid.event.PlaceGridBlockEvent;
import dev.drav.glyphworks.grid.graph.GridGraph;
import dev.drav.glyphworks.grid.component.GridTypeEntry;
import dev.drav.glyphworks.grid.lookup.GridLookup;
import dev.drav.glyphworks.grid.type.GridType;
import dev.drav.glyphworks.grid.tests.GridTestUtil;
import dev.drav.glyphworks.test.framework.Steps;
import dev.drav.glyphworks.test.framework.TestCase;
import dev.drav.glyphworks.test.framework.TestRegistry;
import dev.drav.glyphworks.test.framework.TestSuite;

/**
 * Suite {@code "grid_connections"} — tests the event-driven block place/break
 * pipeline.
 *
 * <p>
 * Each test uses {@code world.setBlock()} to place the block in the chunk
 * store,
 * then calls {@link PlaceGridBlockEvent#connectBlock} or
 * {@link BreakGridBlockEvent#disconnectBlock} explicitly (the same path taken
 * by
 * {@link dev.drav.glyphworks.grid.system.BlockChangeGridSystem} for
 * {@code world.setBlock()} calls). Tests then wait 2 seconds for any
 * deferred processing before asserting graph state.
 */
public final class GridConnectionTests {

    private static final String PIPE_ID = "Glyphworks_Item_Pipe";
    private static final GridType ITEM_GRID = GridType.of("Item");

    private GridConnectionTests() {
    }

    public static void register(String moduleId) {
        TestRegistry.register(moduleId, buildSuite());
    }

    private static TestSuite buildSuite() {
        return new TestSuite("grid_connections")
                .test(placeAddsNode())
                .test(placeNoEdgeIsolated())
                .test(placeConnectsAdjacent())
                .test(breakRemovesNode())
                .test(breakUpdatesNeighbor())
                .test(breakSplitsChain())
                .test(reconnectAfterBreak())
                .test(crossTypeNoConnection());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static Vector3i v(int x, int y, int z) {
        return new Vector3i(x, y, z);
    }

    /** Place a pipe and register its grid connections. */
    private static void place(World world, Vector3i pos) {
        world.setBlock(pos.x, pos.y, pos.z, PIPE_ID);
        PlaceGridBlockEvent.connectBlock(world, pos);
    }

    /** Remove a block and disconnect its grid connections. */
    private static void breakAt(World world, Vector3i pos) {
        BreakGridBlockEvent.disconnectBlock(world, pos);
        world.setBlock(pos.x, pos.y, pos.z, "Empty");
    }

    private static GridGraph graph(World world) {
        return GlyphworksPlugin.get().getGridModule().getOrCreateGridGraph(world, ITEM_GRID);
    }

    private static boolean connected(World world, Vector3i a, Vector3i b) {
        return GridTestUtil.connected(world, a, b, ITEM_GRID);
    }

    // -------------------------------------------------------------------------
    // Test 1: placing a single pipe registers it as a node in the graph
    // -------------------------------------------------------------------------

    private static TestCase placeAddsNode() {
        return new TestCase("place_adds_node", 4, 3, 1)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    place(w, v(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()));
                }))
                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))
                .step(Steps.assertThat(
                        ctx -> graph(ctx.getWorld()).contains(v(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ())),
                        "placing a pipe registers it as a node in the Item grid graph"));
    }

    // -------------------------------------------------------------------------
    // Test 2: an isolated pipe has no neighbors
    // -------------------------------------------------------------------------

    private static TestCase placeNoEdgeIsolated() {
        return new TestCase("place_no_edge_isolated", 4, 3, 1)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    place(w, v(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()));
                }))
                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))
                .step(Steps.assertThat(ctx -> {
                    Vector3i pos = v(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    return graph(ctx.getWorld()).getNeighbors(pos).isEmpty();
                }, "isolated pipe has no neighbors in the graph"));
    }

    // -------------------------------------------------------------------------
    // Test 3: two adjacent pipes are bidirectionally connected
    // -------------------------------------------------------------------------

    private static TestCase placeConnectsAdjacent() {
        return new TestCase("place_connects_adjacent", 5, 3, 1)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    place(w, v(ox, oy, oz));
                    place(w, v(ox + 1, oy, oz));
                }))
                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    Vector3i pa = v(ox, oy, oz);
                    Vector3i pb = v(ox + 1, oy, oz);
                    return connected(w, pa, pb) && connected(w, pb, pa);
                }, "two adjacent pipes are bidirectionally connected in the Item grid graph"));
    }

    // -------------------------------------------------------------------------
    // Test 4: breaking a pipe removes it from the graph
    // -------------------------------------------------------------------------

    private static TestCase breakRemovesNode() {
        return new TestCase("break_removes_node", 4, 3, 1)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    place(w, v(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()));
                }))
                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    breakAt(w, v(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()));
                }))
                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))
                .step(Steps.assertThat(
                        ctx -> !graph(ctx.getWorld()).contains(v(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ())),
                        "breaking a pipe removes it from the grid graph"));
    }

    // -------------------------------------------------------------------------
    // Test 5: breaking one of two connected pipes empties the survivor's neighbor
    // set
    // -------------------------------------------------------------------------

    private static TestCase breakUpdatesNeighbor() {
        return new TestCase("break_updates_neighbor", 5, 3, 1)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    place(w, v(ox, oy, oz));
                    place(w, v(ox + 1, oy, oz));
                }))
                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    // Break the second pipe; first pipe's neighbor set should become empty.
                    breakAt(w, v(ox + 1, oy, oz));
                }))
                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    Vector3i remaining = v(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    return graph(w).contains(remaining) && graph(w).getNeighbors(remaining).isEmpty();
                }, "surviving pipe has empty neighbor set after its partner is broken"));
    }

    // -------------------------------------------------------------------------
    // Test 6: breaking the middle pipe in a chain splits the two ends
    // -------------------------------------------------------------------------

    private static TestCase breakSplitsChain() {
        return new TestCase("break_splits_chain", 7, 3, 1)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    place(w, v(ox, oy, oz)); // A
                    place(w, v(ox + 1, oy, oz)); // B (middle)
                    place(w, v(ox + 2, oy, oz)); // C
                }))
                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    breakAt(w, v(ox + 1, oy, oz)); // Break B
                }))
                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))
                .step(Steps.assertThat(ctx -> {
                    GridGraph g = graph(ctx.getWorld());
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    Vector3i pa = v(ox, oy, oz);
                    Vector3i pc = v(ox + 2, oy, oz);
                    // A and C should exist but be in separate components.
                    return g.contains(pa) && g.contains(pc)
                            && !g.getComponent(pa).contains(pc);
                }, "breaking the middle pipe disconnects A and C into separate components"));
    }

    // -------------------------------------------------------------------------
    // Test 7: breaking a pipe then re-placing it reconnects correctly
    // -------------------------------------------------------------------------

    private static TestCase reconnectAfterBreak() {
        return new TestCase("reconnect_after_break", 5, 3, 1)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    place(w, v(ox, oy, oz));
                    place(w, v(ox + 1, oy, oz));
                }))
                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    breakAt(w, v(ox, oy, oz));
                }))
                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    place(w, v(ox, oy, oz)); // re-place the broken pipe
                }))
                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    Vector3i pa = v(ox, oy, oz);
                    Vector3i pb = v(ox + 1, oy, oz);
                    return connected(w, pa, pb) && connected(w, pb, pa);
                }, "re-placed pipe reconnects bidirectionally with its neighbor"));
    }

    // -------------------------------------------------------------------------
    // Test 8: blocks of different grid types must NOT connect to each other
    // -------------------------------------------------------------------------

    private static TestCase crossTypeNoConnection() {
        return new TestCase("cross_type_no_connection", 5, 3, 1)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    Vector3i pa = v(ox, oy, oz);
                    Vector3i pb = v(ox + 1, oy, oz);
                    // Place two adjacent pipes.
                    w.setBlock(pa.x, pa.y, pa.z, PIPE_ID);
                    w.setBlock(pb.x, pb.y, pb.z, PIPE_ID);
                    // Change B's grid type to a different type before connecting.
                    GridLookup luB = GridLookup.resolve(w.getChunkStore(), pb);
                    if (luB != null) {
                        GridTypeEntry entryB = luB.component().getEntry("Item");
                        if (entryB != null) {
                            entryB.setGridType(GridType.of("__other_type__"));
                        }
                    }
                    // Connect both: A is \"Item\", B is now \"__other_type__\".
                    PlaceGridBlockEvent.connectBlock(w, pa);
                    PlaceGridBlockEvent.connectBlock(w, pb);
                }))
                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    return !connected(w, v(ox, oy, oz), v(ox + 1, oy, oz))
                            && !connected(w, v(ox + 1, oy, oz), v(ox, oy, oz));
                }, "blocks of different grid types are not connected to each other in either direction"));
    }
}

