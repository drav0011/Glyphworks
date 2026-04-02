package dev.drav.glyphworks.grid.tests;

import org.joml.Vector3i;

import com.hypixel.hytale.protocol.BlockFace;
import com.hypixel.hytale.server.core.universe.world.World;

import dev.drav.glyphworks.grid.component.FaceMode;
import dev.drav.glyphworks.grid.component.FacePlane;
import dev.drav.glyphworks.grid.component.GridComponent;
import dev.drav.glyphworks.grid.component.GridTypeEntry;
import dev.drav.glyphworks.grid.event.BreakGridBlockEvent;
import dev.drav.glyphworks.grid.event.PlaceGridBlockEvent;
import dev.drav.glyphworks.grid.lookup.GridLookup;
import dev.drav.glyphworks.test.framework.Steps;
import dev.drav.glyphworks.test.framework.TestCase;
import dev.drav.glyphworks.test.framework.TestRegistry;
import dev.drav.glyphworks.test.framework.TestSuite;

/**
 * Suite {@code "grid_component"} — tests {@link GridComponent} state
 * correctness.
 *
 * <p>
 * Connection-related tests place pipe blocks and verify that the component's
 * persisted state (origin position, neighbor set) is updated correctly by the
 * connect/disconnect pipeline. Accumulator tests instantiate
 * {@link GridComponent}
 * directly to avoid ECS interaction.
 */
public final class GridComponentTests {

    private static final String PIPE_ID = "Glyphworks_Item_Pipe";

    private GridComponentTests() {
    }

    public static void register(String moduleId) {
        TestRegistry.register(moduleId, buildSuite());
    }

    private static TestSuite buildSuite() {
        return new TestSuite("grid_component")
                .test(placeSetsOriginPosition())
                .test(placeUpdatesNeighborSet())
                .test(breakRemovesFromNeighborSet())
                .test(transferAccumulatorWhole())
                .test(transferAccumulatorSubTick())
                .test(cloneDeepCopyFaces())
                .test(cloneDeepCopyNeighbors())
                .test(facePlaneEqualsIgnoresContainerKey());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static Vector3i v(int x, int y, int z) {
        return new Vector3i(x, y, z);
    }

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
                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))
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
                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int ox = ctx.getOriginX(), oy = ctx.getOriginY(), oz = ctx.getOriginZ();
                    Vector3i pa = v(ox, oy, oz);
                    Vector3i pb = v(ox + 1, oy, oz);
                    GridComponent compA = component(w, pa);
                    GridComponent compB = component(w, pb);
                    if (compA == null || compB == null)
                        return false;
                    GridTypeEntry entryA = compA.getEntry("Item");
                    GridTypeEntry entryB = compB.getEntry("Item");
                    if (entryA == null || entryB == null)
                        return false;
                    return entryA.getNeighbors().contains(pb) && entryB.getNeighbors().contains(pa);
                }, "each pipe's component.neighbors contains the other pipe's position"));
    }

    // -------------------------------------------------------------------------
    // Test 3: breaking one pipe removes it from the surviving neighbor's neighbor
    // set
    // -------------------------------------------------------------------------

    private static TestCase breakRemovesFromNeighborSet() {
        return new TestCase("break_removes_from_neighbor_set", 5, 3, 1)
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
                    breakAt(w, v(ox + 1, oy, oz));
                }))
                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    Vector3i remaining = v(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    GridComponent comp = component(w, remaining);
                    if (comp == null)
                        return false;
                    GridTypeEntry entry = comp.getEntry("Item");
                    return entry != null && entry.getNeighbors().isEmpty();
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
                    GridTypeEntry gte = new GridTypeEntry();
                    // 1.5 added → whole=1, remainder=0.5
                    int r1 = gte.drainAccumulator(1.5f);
                    // 0.0 added → whole=0, remainder=0.5
                    int r2 = gte.drainAccumulator(0.0f);
                    // 0.5 added → total=1.0, whole=1, remainder=0.0
                    int r3 = gte.drainAccumulator(0.5f);
                    return r1 == 1 && r2 == 0 && r3 == 1;
                }, "drainAccumulator(1.5) → 1; drainAccumulator(0.0) → 0; drainAccumulator(0.5) → 1"));
    }

    // -------------------------------------------------------------------------
    // Test 5: drainAccumulator sub-tick rate crosses 1.0 on the 4th call of 0.3
    // -------------------------------------------------------------------------

    private static TestCase transferAccumulatorSubTick() {
        return new TestCase("transfer_accumulator_sub_tick", 1, 1, 1)
                .step(Steps.assertThat(ctx -> {
                    GridTypeEntry gte = new GridTypeEntry();
                    // Each call adds 0.3; after 3 calls accumulator=0.9 (< 1.0 → 0 each time).
                    int r1 = gte.drainAccumulator(0.3f);
                    int r2 = gte.drainAccumulator(0.3f);
                    int r3 = gte.drainAccumulator(0.3f);
                    // 4th call: 0.9 + 0.3 = 1.2 → whole=1, remainder=0.2
                    int r4 = gte.drainAccumulator(0.3f);
                    return r1 == 0 && r2 == 0 && r3 == 0 && r4 == 1;
                }, "drainAccumulator(0.3) x 3 → 0 each; 4th call crosses 1.0 and returns 1"));
    }
    // -------------------------------------------------------------------------
    // Test 6: clone() creates a deep copy of the faces set
    //
    // Mutating a FacePlane obtained from the clone must not affect the original
    // because each FacePlane is cloned individually in the copy constructor.
    // -------------------------------------------------------------------------

    private static TestCase cloneDeepCopyFaces() {
        return new TestCase("clone_deep_copy_faces", 1, 1, 1)
                .step(Steps.assertThat(ctx -> {
                    GridTypeEntry original = new GridTypeEntry();
                    original.addFace(new FacePlane(new Vector3i(0, 0, 0), BlockFace.East, FaceMode.BIDIRECTIONAL));
                    GridTypeEntry clone = new GridTypeEntry(original);
                    // Mutate the face in the clone — should not affect the original.
                    for (FacePlane f : clone.getFaces()) {
                        f.setMode(FaceMode.CLOSED);
                    }
                    return original.getFaces().stream().allMatch(f -> f.getMode() == FaceMode.BIDIRECTIONAL)
                            && clone.getFaces().stream().allMatch(f -> f.getMode() == FaceMode.CLOSED);
                }, "clone() deep-copies FacePlane instances: mutating a face in the clone does not affect the original"));
    }

    // -------------------------------------------------------------------------
    // Test 7: clone() creates an independent neighbor set
    //
    // Adding a neighbor to the clone must not appear in the original's set
    // because the copy constructor builds a new HashSet.
    // -------------------------------------------------------------------------

    private static TestCase cloneDeepCopyNeighbors() {
        return new TestCase("clone_deep_copy_neighbors", 1, 1, 1)
                .step(Steps.assertThat(ctx -> {
                    GridTypeEntry original = new GridTypeEntry();
                    original.addNeighbor(new Vector3i(1, 0, 0));
                    GridTypeEntry clone = new GridTypeEntry(original);
                    clone.addNeighbor(new Vector3i(2, 0, 0));
                    return original.getNeighbors().size() == 1 && clone.getNeighbors().size() == 2;
                }, "clone() creates an independent neighbor set: adding to clone does not affect the original"));
    }

    // -------------------------------------------------------------------------
    // Test 8: FacePlane.equals ignores containerKey
    //
    // Two FacePlanes with the same position, normal, and mode must be equal
    // regardless of containerKey, and must share the same hashCode.
    // -------------------------------------------------------------------------

    private static TestCase facePlaneEqualsIgnoresContainerKey() {
        return new TestCase("face_plane_equals_ignores_container_key", 1, 1, 1)
                .step(Steps.assertThat(ctx -> {
                    FacePlane withKeyA = new FacePlane(
                            new Vector3i(0, 0, 0), BlockFace.East, FaceMode.BIDIRECTIONAL, "key_a");
                    FacePlane withKeyB = new FacePlane(
                            new Vector3i(0, 0, 0), BlockFace.East, FaceMode.BIDIRECTIONAL, "key_b");
                    FacePlane withNull = new FacePlane(
                            new Vector3i(0, 0, 0), BlockFace.East, FaceMode.BIDIRECTIONAL, null);
                    return withKeyA.equals(withKeyB)
                            && withKeyA.equals(withNull)
                            && withKeyA.hashCode() == withKeyB.hashCode()
                            && withKeyA.hashCode() == withNull.hashCode();
                }, "FacePlane.equals and hashCode ignore containerKey — only position, normal, and mode matter"));
    }
}
