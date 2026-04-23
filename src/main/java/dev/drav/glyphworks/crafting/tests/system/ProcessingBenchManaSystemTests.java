package dev.drav.glyphworks.crafting.tests.system;

import javax.annotation.Nullable;

import org.joml.Vector3i;

import com.hypixel.hytale.builtin.crafting.component.ProcessingBenchBlock;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.World;

import dev.drav.glyphworks.fluid.FluidStack;
import dev.drav.glyphworks.fluid.component.FluidContainerComponent;
import dev.drav.glyphworks.grid.event.PlaceGridBlockEvent;
import dev.drav.glyphworks.grid.lookup.GridLookup;
import dev.drav.glyphworks.test.framework.Steps;
import dev.drav.glyphworks.test.framework.TestCase;
import dev.drav.glyphworks.test.framework.TestRegistry;
import dev.drav.glyphworks.test.framework.TestSuite;

/**
 * Suite {@code "processing_bench_mana_system"} — integration tests for
 * {@link dev.drav.glyphworks.crafting.system.ProcessingBenchManaSystem}.
 *
 * <h3>Coverage</h3>
 * <ol>
 * <li>Mana is drained from the fluid container while the bench is active.</li>
 * <li>The bench stops when mana runs out.</li>
 * <li>The bench does not start when no mana is present.</li>
 * </ol>
 */
public final class ProcessingBenchManaSystemTests {

    private static final String FURNACE_ID = "Glyphworks_Crafting_Machine_Furnace";
    private static final String MANA_FLUID_ID = "Mana_Source";
    private static final int MANA_CAPACITY = 4_000;

    /**
     * A vanilla ore that can be smelted in the furnace, seeded to trigger
     * active processing.
     */
    private static final String INPUT_ITEM = "Rock_Ore_Copper";

    /**
     * Fuel item with non-zero FuelQuality so the furnace starts burning.
     */
    private static final String FUEL_ITEM = "Wood_Sticks";

    private ProcessingBenchManaSystemTests() {
    }

    public static void register(String moduleId) {
        TestRegistry.register(moduleId, buildSuite());
    }

    private static TestSuite buildSuite() {
        return new TestSuite("processing_bench_mana_system")
                .test(manaDrawsWhileActive())
                .test(benchStopsWhenManaEmpty())
                .test(benchDoesNotStartWithoutMana());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void place(World w, int x, int y, int z) {
        w.setBlock(x, y, z, FURNACE_ID);
        PlaceGridBlockEvent.connectBlock(w, new Vector3i(x, y, z));
    }

    @Nullable
    private static ProcessingBenchBlock getPbb(World w, int x, int y, int z) {
        GridLookup lu = GridLookup.resolve(w.getChunkStore(), new Vector3i(x, y, z));
        if (lu == null)
            return null;
        return w.getChunkStore().getStore().getComponent(lu.blockRef(),
                ProcessingBenchBlock.getComponentType());
    }

    @Nullable
    private static FluidContainerComponent getManaContainer(World w, int x, int y, int z) {
        GridLookup lu = GridLookup.resolve(w.getChunkStore(), new Vector3i(x, y, z));
        if (lu == null)
            return null;
        return w.getChunkStore().getStore().getComponent(lu.blockRef(),
                FluidContainerComponent.getComponentType());
    }

    private static void seedContainers(World w, int x, int y, int z, int manaAmount) {
        ProcessingBenchBlock pbb = getPbb(w, x, y, z);
        if (pbb == null)
            return;

        ItemContainer input = pbb.getInputContainer();
        if (input != null)
            input.addItemStack(new ItemStack(INPUT_ITEM, 10), false, false, false);

        ItemContainer fuel = pbb.getFuelContainer();
        if (fuel != null)
            fuel.addItemStack(new ItemStack(FUEL_ITEM, 10), false, false, false);

        if (manaAmount > 0) {
            FluidContainerComponent mana = getManaContainer(w, x, y, z);
            if (mana != null)
                mana.fill(new FluidStack(MANA_FLUID_ID, manaAmount, mana.getCapacity()));
        }
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    /**
     * Test 1 — Mana is consumed from the fluid container while the bench is
     * actively processing. After 2 seconds of active processing the mana
     * level must be lower than the starting amount.
     */
    private static TestCase manaDrawsWhileActive() {
        return new TestCase("mana_drains_while_active", 5, 5, 5)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    place(w, x, y, z);
                }))
                .step(Steps.wait(2))
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    seedContainers(w, x, y, z, MANA_CAPACITY);
                }))
                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    FluidContainerComponent mana = getManaContainer(w, x, y, z);
                    return mana != null && mana.getAmount() < MANA_CAPACITY;
                }, "mana drained below starting capacity after 2 seconds of active processing"));
    }

    /**
     * Test 2 — The bench is deactivated by
     * {@link dev.drav.glyphworks.crafting.system.ProcessingBenchManaSystem} when
     * the mana container empties. Seeding only 3 mL ensures the supply is
     * exhausted well within the wait window.
     */
    private static TestCase benchStopsWhenManaEmpty() {
        return new TestCase("bench_stops_when_mana_empty", 5, 5, 5)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    place(w, x, y, z);
                }))
                .step(Steps.wait(2))
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    seedContainers(w, x, y, z, 3);
                }))
                .step(Steps.wait(ctx -> 3 * ctx.getWorld().getTps()))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    ProcessingBenchBlock pbb = getPbb(w, x, y, z);
                    FluidContainerComponent mana = getManaContainer(w, x, y, z);
                    return pbb != null && !pbb.isActive()
                            && mana != null && mana.isEmpty();
                }, "bench stopped and mana container empty after mana exhausted"));
    }

    /**
     * Test 3 — The bench must not start when items and fuel are present but
     * no mana is available. The bench should remain inactive after 3 seconds.
     */
    private static TestCase benchDoesNotStartWithoutMana() {
        return new TestCase("bench_does_not_start_without_mana", 5, 5, 5)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    place(w, x, y, z);
                }))
                .step(Steps.wait(2))
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    seedContainers(w, x, y, z, 0);
                }))
                .step(Steps.wait(ctx -> 3 * ctx.getWorld().getTps()))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    ProcessingBenchBlock pbb = getPbb(w, x, y, z);
                    return pbb != null && !pbb.isActive();
                }, "bench remains inactive without mana"));
    }
}
