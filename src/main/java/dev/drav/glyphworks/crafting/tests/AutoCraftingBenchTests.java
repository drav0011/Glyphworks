package dev.drav.glyphworks.crafting.tests;

import javax.annotation.Nullable;

import org.joml.Vector3i;

import com.hypixel.hytale.server.core.universe.world.World;

import dev.drav.glyphworks.crafting.component.AutoCraftingBenchBlock;
import dev.drav.glyphworks.fluid.component.FluidContainerComponent;
import dev.drav.glyphworks.grid.component.GridComponent;
import dev.drav.glyphworks.grid.event.PlaceGridBlockEvent;
import dev.drav.glyphworks.grid.lookup.GridLookup;
import dev.drav.glyphworks.test.framework.Steps;
import dev.drav.glyphworks.test.framework.TestCase;
import dev.drav.glyphworks.test.framework.TestRegistry;
import dev.drav.glyphworks.test.framework.TestSuite;

/**
 * Suite {@code "auto_crafting_bench"} — integration tests for the
 * auto crafting bench block entity components and mana infrastructure.
 *
 * <h3>Scope</h3>
 * <ul>
 * <li>Verifies that placing a crafting bench initialises a
 * {@link FluidContainerComponent} with the correct capacity from JSON.</li>
 * <li>Verifies the mana consumption rate loaded from JSON.</li>
 * <li>Verifies that the {@link GridComponent} contains a {@code Fluid} grid
 * type entry, enabling mana delivery via the fluid grid network.</li>
 * <li>Verifies that the mana container correctly accepts and holds mana
 * injected directly (simulating grid delivery).</li>
 * </ul>
 */
public final class AutoCraftingBenchTests {

    // ── Block ID ──────────────────────────────────────────────────────────────

    /** WorkBench carries ALL the component types under test. */
    private static final String BENCH_ID = "Glyphworks_Crafting_Machine_WorkBench";

    // ── Expected constants (from the block JSON) ───────────────────────────────

    private static final int EXPECTED_MANA_CAPACITY = 4_000;
    private static final float EXPECTED_MANA_RATE = 25.0f;
    private static final String MANA_FLUID_ID = "Glyphworks_Fluid_Mana";

    private AutoCraftingBenchTests() {
    }

    // ── Registration ──────────────────────────────────────────────────────────

    public static void register(String moduleId) {
        TestRegistry.register(moduleId, buildSuite());
    }

    private static TestSuite buildSuite() {
        return new TestSuite("auto_crafting_bench")
                .test(benchHasFluidContainer())
                .test(benchManaRateFromJson())
                .test(benchHasFluidGridEntry())
                .test(benchFluidContainerAcceptsMana());
    }

    // ── Component helpers ─────────────────────────────────────────────────────

    @Nullable
    private static AutoCraftingBenchBlock getBench(World w, int x, int y, int z) {
        GridLookup lu = GridLookup.resolve(w.getChunkStore(), new Vector3i(x, y, z));
        if (lu == null)
            return null;
        return w.getChunkStore().getStore().getComponent(lu.blockRef(),
                AutoCraftingBenchBlock.getComponentType());
    }

    @Nullable
    private static FluidContainerComponent getFluidContainer(World w, int x, int y, int z) {
        GridLookup lu = GridLookup.resolve(w.getChunkStore(), new Vector3i(x, y, z));
        if (lu == null)
            return null;
        return w.getChunkStore().getStore().getComponent(lu.blockRef(),
                FluidContainerComponent.getComponentType());
    }

    @Nullable
    private static GridComponent getGridComponent(World w, int x, int y, int z) {
        GridLookup lu = GridLookup.resolve(w.getChunkStore(), new Vector3i(x, y, z));
        if (lu == null)
            return null;
        return w.getChunkStore().getStore().getComponent(lu.blockRef(),
                GridComponent.getComponentType());
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    /**
     * Test 1 — A freshly placed crafting bench must have a
     * {@link FluidContainerComponent} with capacity {@link #EXPECTED_MANA_CAPACITY}.
     */
    private static TestCase benchHasFluidContainer() {
        return new TestCase("bench_has_fluid_container", 3, 3, 3)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    w.setBlock(x, y, z, BENCH_ID);
                    PlaceGridBlockEvent.connectBlock(w, new Vector3i(x, y, z));
                }))
                .step(Steps.wait(2))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    FluidContainerComponent fcc = getFluidContainer(w, x, y, z);
                    return fcc != null && fcc.getCapacity() == EXPECTED_MANA_CAPACITY;
                }, "bench has FluidContainerComponent with capacity " + EXPECTED_MANA_CAPACITY));
    }

    /**
     * Test 2 — The mana consumption rate read from JSON must equal
     * {@link #EXPECTED_MANA_RATE} liters per tick.
     */
    private static TestCase benchManaRateFromJson() {
        return new TestCase("bench_mana_rate_from_json", 3, 3, 3)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    w.setBlock(x, y, z, BENCH_ID);
                    PlaceGridBlockEvent.connectBlock(w, new Vector3i(x, y, z));
                }))
                .step(Steps.wait(2))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    AutoCraftingBenchBlock bench = getBench(w, x, y, z);
                    return bench != null && bench.getManaConsumptionRate() == EXPECTED_MANA_RATE;
                }, "bench manaConsumptionRate == " + EXPECTED_MANA_RATE + " L/tick from JSON"));
    }

    /**
     * Test 3 — The block's {@link GridComponent} must contain a {@code "Fluid"}
     * grid type entry so the bench can participate in the mana pipe network.
     */
    private static TestCase benchHasFluidGridEntry() {
        return new TestCase("bench_has_fluid_grid_entry", 3, 3, 3)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    w.setBlock(x, y, z, BENCH_ID);
                    PlaceGridBlockEvent.connectBlock(w, new Vector3i(x, y, z));
                }))
                .step(Steps.wait(2))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    GridComponent gc = getGridComponent(w, x, y, z);
                    return gc != null && gc.getEntry("Fluid") != null;
                }, "bench GridComponent has a Fluid grid type entry"));
    }

    /**
     * Test 4 — The mana container accepts fluid injected directly, confirming
     * that a pipe network can deliver mana to a running bench.
     *
     * <p>After placing the bench, {@value #EXPECTED_MANA_CAPACITY} liters of mana
     * are injected directly into the {@link FluidContainerComponent}. The
     * container must report the correct amount and fluid ID on the next tick.
     */
    private static TestCase benchFluidContainerAcceptsMana() {
        return new TestCase("bench_fluid_container_accepts_mana", 3, 3, 3)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    w.setBlock(x, y, z, BENCH_ID);
                    PlaceGridBlockEvent.connectBlock(w, new Vector3i(x, y, z));
                    FluidContainerComponent fcc = getFluidContainer(w, x, y, z);
                    if (fcc != null)
                        fcc.fill(MANA_FLUID_ID, EXPECTED_MANA_CAPACITY);
                }))
                .step(Steps.wait(2))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    FluidContainerComponent fcc = getFluidContainer(w, x, y, z);
                    return fcc != null
                            && fcc.getAmount() == EXPECTED_MANA_CAPACITY
                            && MANA_FLUID_ID.equals(fcc.getFluidId());
                }, "bench fluid container holds " + EXPECTED_MANA_CAPACITY + "L of " + MANA_FLUID_ID));
    }
}
