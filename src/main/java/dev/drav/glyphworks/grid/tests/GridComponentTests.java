package dev.drav.glyphworks.grid.tests;

import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.util.thread.TickingThread;

import dev.drav.glyphworks.grid.component.GridComponent;
import dev.drav.glyphworks.grid.event.BreakGridBlockEvent;
import dev.drav.glyphworks.grid.event.PlaceGridBlockEvent;
import dev.drav.glyphworks.grid.lookup.GridLookup;
import dev.drav.glyphworks.test.Steps;
import dev.drav.glyphworks.test.TestCase;
import dev.drav.glyphworks.test.TestRegistry;
import dev.drav.glyphworks.test.TestSuite;

/**
 * Suite {@code "grid_component"} — tests {@link GridComponent} state correctness.
 *
 * <p>Connection-related tests place pipe blocks and verify that the component's
 * persisted state (origin position, neighbor set) is updated correctly by the
 * connect/disconnect pipeline. Accumulator tests instantiate {@link GridComponent}
 * directly to avoid ECS interaction.
 */
public final class GridComponentTests {

    private static final String PIPE_ID = "Pipe";
    private static final int WAIT_TICKS = 2 * TickingThread.TPS;

    private GridComponentTests() {}

    public static void register(String moduleId) {
        TestRegistry.register(moduleId, buildSuite());
    }

    private static TestSuite buildSuite() {
        return new TestSuite("grid_component")
                .test(placeSetsOriginPosition())
                .test(placeUpdatesNeighborSet())
                .test(breakRemovesFromNeighborSet())
                .test(transferAccumulatorWhole())
                .test(transferAccumulatorSubTick());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static Vector3i v(int x, int y, int z) { return new Vector3i(x, y, z); }

    private static void place(World world, Vector3i pos) {
        world.setBlock(pos.x, pos.y, pos.z, PIPE_ID);
        PlaceGridBlockEvent.connectBlock(world, pos);
    }

    private static void breakAt(World world, Vector3i pos) {
        BreakGridBlockEvent.disconnectBlock(world, pos);
        world.setBlock(pos.x, pos.y, pos.z, "Empty");
    }

    /** Returns the GridComponent at {@code pos}, or {@code null}. */
    private static GridComponent component(World world, Vector3i pos) {
        GridLookup lu = GridLookup.resolve(world.getChunkStore(), pos);
        return lu != null ? lu.component() : null;
    }

    // -------------------------------------------------------------------------
    // Test 1: connectBlock sets the component's origin position
    // -------------------------------------------------------------------------

    private static TestCase placeSetsOriginPosition() {
        return new TestCase("place_sets_origin_position", 4, 3, 1)
                .step(Steps.run(ctx -> {
                    Vector3i pos = v(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    place(ctx.getWorld(), pos);
                }))
                .step(Steps.wait(WAIT_TICKS))
                .step(Steps.assertThat(ctx -> {
                    Vector3i pos = v(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    GridComponent comp = component(ctx.getWorld(), pos);
                    return comp != null && pos.equals(comp.getOriginPosition());
                }, "connectBlock sets component.originPosition to the placed block's world position"));
    }

    // -------------------------------------------------------------------------
    // Test 2: two adjacent pipes each contain the other in their neighbor set
    // -------------------------------------------------------------------------

    private static TestCase placeUpdatesNeighborSet() {
        return new TestCase("place_updates_neighbor_set", 5, 3, 1)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    Vector3i pa = v(ox, oy, oz);
                    Vector3i pb = v(ox + 1, oy, oz);
                    place(w, pa);
                    place(w, pb);
                }))
                .step(Steps.wait(WAIT_TICKS))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    Vector3i pa = v(ox, oy, oz);
                    Vector3i pb = v(ox + 1, oy, oz);
                    GridComponent compA = component(w, pa);
                    GridComponent compB = component(w, pb);
                    if (compA == null || compB == null) return false;
                    return compA.getNeighbors().contains(pb) && compB.getNeighbors().contains(pa);
                }, "each pipe's component.neighbors contains the other pipe's position"));
    }

    // -------------------------------------------------------------------------
    // Test 3: breaking one pipe removes it from the surviving neighbor's neighbor set
    // -------------------------------------------------------------------------

    private static TestCase breakRemovesFromNeighborSet() {
        return new TestCase("break_removes_from_neighbor_set", 5, 3, 1)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    place(w, v(ox, oy, oz));
                    place(w, v(ox + 1, oy, oz));
                }))
                .step(Steps.wait(WAIT_TICKS))
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    breakAt(w, v(ox + 1, oy, oz));
                }))
                .step(Steps.wait(WAIT_TICKS))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    Vector3i remaining = v(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    GridComponent comp = component(w, remaining);
                    return comp != null && comp.getNeighbors().isEmpty();
                }, "surviving pipe's component.neighbors is empty after its partner is broken"));
    }

    // -------------------------------------------------------------------------
    // Test 4: drainAccumulator correctly tracks fractional carry-over (whole units)
    //
    // Instantiates GridComponent directly to test pure accumulator logic
    // without any ECS ticking interference.
    // -------------------------------------------------------------------------

    private static TestCase transferAccumulatorWhole() {
        return new TestCase("transfer_accumulator_whole", 1, 1, 1)
                .step(Steps.assertThat(ctx -> {
                    GridComponent comp = new GridComponent();
                    // 1.5 added → whole=1, remainder=0.5
                    int r1 = comp.drainAccumulator(1.5f);
                    // 0.0 added → whole=0, remainder=0.5
                    int r2 = comp.drainAccumulator(0.0f);
                    // 0.5 added → total=1.0, whole=1, remainder=0.0
                    int r3 = comp.drainAccumulator(0.5f);
                    return r1 == 1 && r2 == 0 && r3 == 1;
                }, "drainAccumulator(1.5) → 1; drainAccumulator(0.0) → 0; drainAccumulator(0.5) → 1"));
    }

    // -------------------------------------------------------------------------
    // Test 5: drainAccumulator sub-tick rate crosses 1.0 on the 4th call of 0.3
    // -------------------------------------------------------------------------

    private static TestCase transferAccumulatorSubTick() {
        return new TestCase("transfer_accumulator_sub_tick", 1, 1, 1)
                .step(Steps.assertThat(ctx -> {
                    GridComponent comp = new GridComponent();
                    // Each call adds 0.3; after 3 calls accumulator=0.9 (< 1.0 → 0 each time).
                    int r1 = comp.drainAccumulator(0.3f);
                    int r2 = comp.drainAccumulator(0.3f);
                    int r3 = comp.drainAccumulator(0.3f);
                    // 4th call: 0.9 + 0.3 = 1.2 → whole=1, remainder=0.2
                    int r4 = comp.drainAccumulator(0.3f);
                    return r1 == 0 && r2 == 0 && r3 == 0 && r4 == 1;
                }, "drainAccumulator(0.3) x 3 → 0 each; 4th call crosses 1.0 and returns 1"));
    }
}
