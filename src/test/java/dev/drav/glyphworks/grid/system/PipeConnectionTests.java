package dev.drav.glyphworks.grid.system;

import org.joml.Vector3i;

import com.hypixel.hytale.server.core.universe.world.World;

import dev.drav.glyphworks.GlyphworksPlugin;
import dev.drav.glyphworks.grid.event.PlaceGridBlockEvent;
import dev.drav.glyphworks.grid.graph.GridGraph;
import dev.drav.glyphworks.grid.type.GridType;
import dev.drav.glyphworks.test.framework.Steps;
import dev.drav.glyphworks.test.framework.TestCase;
import dev.drav.glyphworks.test.framework.TestRegistry;
import dev.drav.glyphworks.test.framework.TestSuite;

/**
 * Tests all possible item-pipe grid connections in the world.
 *
 * <p>
 * Each test places item pipes adjacent to each other and verifies that the
 * runtime {@link GridGraph} records the expected bidirectional edges. The six
 * cardinal directions (East/West, Up/Down, North/South) are each covered
 * individually, then chain, L-turn, and 4-way cross configurations test
 * compound connection patterns.
 */
public final class PipeConnectionTests {

    /** Block type ID for the item pipe (from Pipe.json). */
    private static final String PIPE_ID = "Glyphworks_Item_Pipe";

    /** Grid type used by item pipes (matches GridComponent_Type in Pipe.json). */
    private static final GridType ITEM_GRID = GridType.of("Item");

    private PipeConnectionTests() {
    }

    /**
     * Builds and registers the {@code "pipe_connections"} test suite.
     * Called from {@link dev.drav.glyphworks.grid.GridModule#setupTests()}.
     */
    public static void register(String moduleId) {
        TestRegistry.register(moduleId, buildSuite());
    }

    private static TestSuite buildSuite() {
        return new TestSuite("pipe_connections")
                .test(connectX())
                .test(connectY())
                .test(connectZ())
                .test(chainOf3X())
                .test(lTurnXZ())
                .test(crossXZ());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Shorthand constructor to reduce noise in coordinate expressions. */
    private static Vector3i v(int x, int y, int z) {
        return new Vector3i(x, y, z);
    }

    /**
     * Returns {@code true} if the runtime {@link GridGraph} for the Item grid
     * has a direct edge from {@code a} to {@code b}.
     */
    private static boolean connected(World world, Vector3i a, Vector3i b) {
        GridGraph graph = GlyphworksPlugin.get().getGridModule().getGridGraph(world, ITEM_GRID);
        if (graph == null)
            return false;
        return graph.getNeighbors(a).contains(b);
    }

    // -------------------------------------------------------------------------
    // Test 1: two pipes along X (East – West faces)
    // -------------------------------------------------------------------------

    private static TestCase connectX() {
        return new TestCase("connect_x", 4, 3, 1)
                .step(Steps.run(ctx -> {
                    World world = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    world.setBlock(ox, oy, oz, PIPE_ID);
                    world.setBlock(ox + 1, oy, oz, PIPE_ID);
                    PlaceGridBlockEvent.connectBlock(world, v(ox, oy, oz));
                    PlaceGridBlockEvent.connectBlock(world, v(ox + 1, oy, oz));
                }))

                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))

                .step(Steps.assertThat(ctx -> {
                    World world = ctx.getWorld();
                    Vector3i a = v(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    Vector3i b = v(ctx.getOriginX() + 1, ctx.getOriginY(), ctx.getOriginZ());
                    return connected(world, a, b) && connected(world, b, a);
                }, "two pipes along X are bidirectionally connected in the GridGraph"));
    }

    // -------------------------------------------------------------------------
    // Test 2: two pipes along Y (Up – Down faces)
    // -------------------------------------------------------------------------

    private static TestCase connectY() {
        return new TestCase("connect_y", 3, 3, 3)
                .step(Steps.run(ctx -> {
                    World world = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    world.setBlock(ox, oy, oz, PIPE_ID);
                    world.setBlock(ox, oy + 1, oz, PIPE_ID);
                    PlaceGridBlockEvent.connectBlock(world, v(ox, oy, oz));
                    PlaceGridBlockEvent.connectBlock(world, v(ox, oy + 1, oz));
                }))

                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))

                .step(Steps.assertThat(ctx -> {
                    World world = ctx.getWorld();
                    Vector3i a = v(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    Vector3i b = v(ctx.getOriginX(), ctx.getOriginY() + 1, ctx.getOriginZ());
                    return connected(world, a, b) && connected(world, b, a);
                }, "two pipes along Y are bidirectionally connected in the GridGraph"));
    }

    // -------------------------------------------------------------------------
    // Test 3: two pipes along Z (North – South faces)
    // -------------------------------------------------------------------------

    private static TestCase connectZ() {
        return new TestCase("connect_z", 3, 4, 1)
                .step(Steps.run(ctx -> {
                    World world = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    world.setBlock(ox, oy, oz, PIPE_ID);
                    world.setBlock(ox, oy, oz + 1, PIPE_ID);
                    PlaceGridBlockEvent.connectBlock(world, v(ox, oy, oz));
                    PlaceGridBlockEvent.connectBlock(world, v(ox, oy, oz + 1));
                }))

                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))

                .step(Steps.assertThat(ctx -> {
                    World world = ctx.getWorld();
                    Vector3i a = v(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    Vector3i b = v(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ() + 1);
                    return connected(world, a, b) && connected(world, b, a);
                }, "two pipes along Z are bidirectionally connected in the GridGraph"));
    }

    // -------------------------------------------------------------------------
    // Test 4: chain of 3 along X
    //
    // [A] — [B] — [C]
    //
    // A–B and B–C must be connected; A–C must NOT be directly connected.
    // -------------------------------------------------------------------------

    private static TestCase chainOf3X() {
        return new TestCase("chain_of_3_x", 5, 3, 1)
                .step(Steps.run(ctx -> {
                    World world = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    world.setBlock(ox, oy, oz, PIPE_ID);
                    world.setBlock(ox + 1, oy, oz, PIPE_ID);
                    world.setBlock(ox + 2, oy, oz, PIPE_ID);
                    PlaceGridBlockEvent.connectBlock(world, v(ox, oy, oz));
                    PlaceGridBlockEvent.connectBlock(world, v(ox + 1, oy, oz));
                    PlaceGridBlockEvent.connectBlock(world, v(ox + 2, oy, oz));
                }))

                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))

                .step(Steps.assertThat(ctx -> {
                    World world = ctx.getWorld();
                    Vector3i left = v(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    Vector3i center = v(ctx.getOriginX() + 1, ctx.getOriginY(), ctx.getOriginZ());
                    Vector3i right = v(ctx.getOriginX() + 2, ctx.getOriginY(), ctx.getOriginZ());
                    return connected(world, left, center)
                            && connected(world, center, left)
                            && connected(world, center, right)
                            && connected(world, right, center)
                            && !connected(world, left, right);
                }, "chain of 3 along X: left-center and center-right connected; endpoints not directly linked"));
    }

    // -------------------------------------------------------------------------
    // Test 5: L-turn (one arm along X, one arm along Z at the corner)
    //
    // [A] — [B]
    // |
    // [C]
    //
    // A–B and B–C connected; A–C NOT directly connected.
    // -------------------------------------------------------------------------

    private static TestCase lTurnXZ() {
        return new TestCase("l_turn_xz", 4, 4, 1)
                .step(Steps.run(ctx -> {
                    World world = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    world.setBlock(ox, oy, oz, PIPE_ID); // A
                    world.setBlock(ox + 1, oy, oz, PIPE_ID); // B (corner)
                    world.setBlock(ox + 1, oy, oz + 1, PIPE_ID); // C
                    PlaceGridBlockEvent.connectBlock(world, v(ox, oy, oz));
                    PlaceGridBlockEvent.connectBlock(world, v(ox + 1, oy, oz));
                    PlaceGridBlockEvent.connectBlock(world, v(ox + 1, oy, oz + 1));
                }))

                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))

                .step(Steps.assertThat(ctx -> {
                    World world = ctx.getWorld();
                    Vector3i a = v(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    Vector3i b = v(ctx.getOriginX() + 1, ctx.getOriginY(), ctx.getOriginZ());
                    Vector3i c = v(ctx.getOriginX() + 1, ctx.getOriginY(), ctx.getOriginZ() + 1);
                    return connected(world, a, b)
                            && connected(world, b, a)
                            && connected(world, b, c)
                            && connected(world, c, b)
                            && !connected(world, a, c);
                }, "L-turn: A-B and B-C connected; A not directly connected to C"));
    }

    // -------------------------------------------------------------------------
    // Test 6: 4-way cross on the XZ plane
    //
    // [N]
    // |
    // [W] — [O] — [E]
    // |
    // [S]
    //
    // Center must connect to all four cardinal arms.
    // -------------------------------------------------------------------------

    private static TestCase crossXZ() {
        return new TestCase("cross_xz", 5, 5, 1)
                .step(Steps.run(ctx -> {
                    World world = ctx.getWorld();
                    int cx = ctx.getOriginX() + 1; // center — offset by 1 so West arm fits
                    int cy = ctx.getOriginY();
                    int cz = ctx.getOriginZ() + 1; // center — offset by 1 so North arm fits
                    world.setBlock(cx, cy, cz, PIPE_ID); // center
                    world.setBlock(cx + 1, cy, cz, PIPE_ID); // East arm
                    world.setBlock(cx - 1, cy, cz, PIPE_ID); // West arm
                    world.setBlock(cx, cy, cz + 1, PIPE_ID); // South arm
                    world.setBlock(cx, cy, cz - 1, PIPE_ID); // North arm
                    PlaceGridBlockEvent.connectBlock(world, v(cx, cy, cz));
                    PlaceGridBlockEvent.connectBlock(world, v(cx + 1, cy, cz));
                    PlaceGridBlockEvent.connectBlock(world, v(cx - 1, cy, cz));
                    PlaceGridBlockEvent.connectBlock(world, v(cx, cy, cz + 1));
                    PlaceGridBlockEvent.connectBlock(world, v(cx, cy, cz - 1));
                }))

                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))

                .step(Steps.assertThat(ctx -> {
                    World world = ctx.getWorld();
                    int cx = ctx.getOriginX() + 1;
                    int cy = ctx.getOriginY();
                    int cz = ctx.getOriginZ() + 1;
                    Vector3i center = v(cx, cy, cz);
                    Vector3i east = v(cx + 1, cy, cz);
                    Vector3i west = v(cx - 1, cy, cz);
                    Vector3i south = v(cx, cy, cz + 1);
                    Vector3i north = v(cx, cy, cz - 1);
                    return connected(world, center, east)
                            && connected(world, center, west)
                            && connected(world, center, south)
                            && connected(world, center, north);
                }, "cross: center pipe connects to all 4 cardinal arms (E/W/S/N)"));
    }
}
