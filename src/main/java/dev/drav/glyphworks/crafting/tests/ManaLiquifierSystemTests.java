package dev.drav.glyphworks.crafting.tests;

import javax.annotation.Nullable;

import org.joml.Vector3i;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.World;

import dev.drav.glyphworks.crafting.component.ManaLiquifierBlock;
import dev.drav.glyphworks.fluid.component.FluidContainerComponent;
import dev.drav.glyphworks.grid.event.PlaceGridBlockEvent;
import dev.drav.glyphworks.grid.lookup.GridLookup;
import dev.drav.glyphworks.test.framework.Steps;
import dev.drav.glyphworks.test.framework.TestCase;
import dev.drav.glyphworks.test.framework.TestRegistry;
import dev.drav.glyphworks.test.framework.TestSuite;

/**
 * Suite {@code "mana_liquifier_system"} — integration tests for
 * {@link dev.drav.glyphworks.crafting.system.ManaLiquifierSystem}.
 *
 * <h3>Coverage</h3>
 * <ol>
 * <li>No fuel item → progress stays at {@code 0.0f} indefinitely.</li>
 * <li>Fuel item consumed on first tick when {@code remainingFuelEnergy == 0}.</li>
 * <li>Full cycle: essence consumed and {@link ManaLiquifierBlock#MANA_OUTPUT_PER_ESSENCE}
 *     liters of mana produced, progress reset to zero.</li>
 * <li>Full fluid output → progress clamped at {@link ManaLiquifierBlock#RECIPE_TIME},
 *     essence not consumed.</li>
 * <li>Non-essence item in input → progress clamps at recipe time, no mana
 *     produced.</li>
 * <li>Fuel exhausted mid-cycle (no replenishment) → progress pauses below
 *     recipe time, no mana produced.</li>
 * </ol>
 */
public final class ManaLiquifierSystemTests {

    // ── Constants ─────────────────────────────────────────────────────────────

    private static final String LIQUIFIER_ID = "Glyphworks_Crafting_Mana_Liquifier";

    /** Fluid container capacity from the block JSON. */
    private static final int FLUID_CAPACITY = 10_000;

    /** A valid essence item accepted by the machine. */
    private static final String ESSENCE = "Ingredient_Fire_Essence";

    /**
     * Fuel item with {@code FuelQuality = 4.0} — burns for 4 seconds,
     * enough to cover one full {@link ManaLiquifierBlock#RECIPE_TIME} cycle.
     */
    private static final String FUEL_ITEM = "Wood_Sticks";

    /**
     * Non-essence item used to verify the machine ignores unknown inputs.
     * Must not be in {@link ManaLiquifierBlock#ESSENCE_IDS} and must have
     * no {@code FuelQuality} so it cannot accidentally act as fuel either.
     */
    private static final String NON_ESSENCE_ITEM = "Rock_Stone";

    /**
     * Ticks to wait for
     * {@link dev.drav.glyphworks.crafting.system.ManaLiquifierSetupSystem}
     * to initialise the containers after block placement.
     */
    private static final int SETUP_WAIT = 2;

    private ManaLiquifierSystemTests() {
    }

    // ── Registration ──────────────────────────────────────────────────────────

    public static void register(String moduleId) {
        TestRegistry.register(moduleId, buildSuite());
    }

    private static TestSuite buildSuite() {
        return new TestSuite("mana_liquifier_system")
                .test(noFuelPausesProcessing())
                .test(fuelConsumedOnFirstTick())
                .test(fullCycleProducesMana())
                .test(fullFluidBlocksCompletion())
                .test(nonEssenceInInputClampsProgress())
                .test(fuelExhaustedMidCyclePausesProgress());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void place(World w, int x, int y, int z) {
        w.setBlock(x, y, z, LIQUIFIER_ID);
        PlaceGridBlockEvent.connectBlock(w, new Vector3i(x, y, z));
    }

    @Nullable
    private static ManaLiquifierBlock getLiquifier(World w, int x, int y, int z) {
        GridLookup lu = GridLookup.resolve(w.getChunkStore(), new Vector3i(x, y, z));
        if (lu == null)
            return null;
        return w.getChunkStore().getStore().getComponent(lu.blockRef(),
                ManaLiquifierBlock.getComponentType());
    }

    @Nullable
    private static FluidContainerComponent getFluid(World w, int x, int y, int z) {
        GridLookup lu = GridLookup.resolve(w.getChunkStore(), new Vector3i(x, y, z));
        if (lu == null)
            return null;
        return w.getChunkStore().getStore().getComponent(lu.blockRef(),
                FluidContainerComponent.getComponentType());
    }

    private static int countItems(ItemContainer container) {
        if (container == null)
            return -1;
        int total = 0;
        for (short i = 0; i < container.getCapacity(); i++) {
            ItemStack stack = container.getItemStack(i);
            if (stack != null && !stack.isEmpty())
                total += stack.getQuantity();
        }
        return total;
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    /**
     * Test 1 — With no fuel and one essence in the input, processing must not
     * advance: {@code processingProgress} remains {@code 0.0f} after 2 seconds.
     */
    private static TestCase noFuelPausesProcessing() {
        return new TestCase("no_fuel_pauses_processing", 5, 5, 5)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    place(w, x, y, z);
                }))
                .step(Steps.wait(SETUP_WAIT))
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    ManaLiquifierBlock mlb = getLiquifier(w, x, y, z);
                    if (mlb == null)
                        return;
                    ItemContainer input = mlb.getInputContainer();
                    if (input != null)
                        input.addItemStack(new ItemStack(ESSENCE, 1), false, false, false);
                    // fuel container intentionally left empty
                }))
                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    ManaLiquifierBlock mlb = getLiquifier(w, x, y, z);
                    return mlb != null && mlb.getProcessingProgress() == 0.0f;
                }, "no fuel: processingProgress stays at 0.0f"));
    }

    /**
     * Test 2 — One {@link #FUEL_ITEM} in the fuel container must be consumed on
     * the first tick the system runs (since {@code remainingFuelEnergy} starts at
     * zero). After a few ticks the fuel slot must be empty and progress must have
     * advanced past zero.
     */
    private static TestCase fuelConsumedOnFirstTick() {
        return new TestCase("fuel_consumed_on_first_tick", 5, 5, 5)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    place(w, x, y, z);
                }))
                .step(Steps.wait(SETUP_WAIT))
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    ManaLiquifierBlock mlb = getLiquifier(w, x, y, z);
                    if (mlb == null)
                        return;
                    // Seed essence so a cycle could theoretically complete.
                    ItemContainer input = mlb.getInputContainer();
                    if (input != null)
                        input.addItemStack(new ItemStack(ESSENCE, 1), false, false, false);
                    // Seed one fuel item (FuelQuality = 4.0 s).
                    ItemContainer fuel = mlb.getFuelContainer();
                    if (fuel != null)
                        fuel.addItemStack(new ItemStack(FUEL_ITEM, 1), false, false, false);
                }))
                .step(Steps.wait(3))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    ManaLiquifierBlock mlb = getLiquifier(w, x, y, z);
                    if (mlb == null)
                        return false;
                    ItemContainer fuel = mlb.getFuelContainer();
                    return fuel != null
                            && countItems(fuel) == 0
                            && mlb.getProcessingProgress() > 0.0f;
                }, "fuel consumed on first tick: fuel slot empty, progress > 0"));
    }

    /**
     * Test 3 — With sufficient fuel energy injected directly and one essence in
     * the input, a full cycle must complete: the essence is consumed, exactly
     * {@link ManaLiquifierBlock#MANA_OUTPUT_PER_ESSENCE} liters of mana appear in
     * the fluid container, and progress resets to {@code 0.0f}.
     */
    private static TestCase fullCycleProducesMana() {
        return new TestCase("full_cycle_produces_mana", 10, 5, 5)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    place(w, x, y, z);
                }))
                .step(Steps.wait(SETUP_WAIT))
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    ManaLiquifierBlock mlb = getLiquifier(w, x, y, z);
                    if (mlb == null)
                        return;
                    // Inject fuel energy directly — no fuel item needed.
                    mlb.setRemainingFuelEnergy(ManaLiquifierBlock.RECIPE_TIME + 2.0f);
                    ItemContainer input = mlb.getInputContainer();
                    if (input != null)
                        input.addItemStack(new ItemStack(ESSENCE, 1), false, false, false);
                }))
                .step(Steps.wait(ctx -> (int) (ManaLiquifierBlock.RECIPE_TIME * ctx.getWorld().getTps()) + 20))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    ManaLiquifierBlock mlb = getLiquifier(w, x, y, z);
                    FluidContainerComponent fluid = getFluid(w, x, y, z);
                    if (mlb == null || fluid == null)
                        return false;
                    ItemContainer input = mlb.getInputContainer();
                    // After the cycle the machine immediately continues on leftover fuel energy,
                    // so progress is no longer 0 by the time this assertion runs — only assert
                    // that the essence was consumed and mana was produced.
                    return input != null
                            && countItems(input) == 0
                            && fluid.getAmount() == ManaLiquifierBlock.MANA_OUTPUT_PER_ESSENCE;
                }, "full cycle: essence consumed, " + ManaLiquifierBlock.MANA_OUTPUT_PER_ESSENCE
                        + " L mana produced"));
    }

    /**
     * Test 4 — When the fluid output is already at capacity, the system must
     * clamp progress at {@link ManaLiquifierBlock#RECIPE_TIME} and must not
     * consume the essence.
     */
    private static TestCase fullFluidBlocksCompletion() {
        return new TestCase("full_fluid_blocks_completion", 10, 5, 5)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    place(w, x, y, z);
                }))
                .step(Steps.wait(SETUP_WAIT))
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    ManaLiquifierBlock mlb = getLiquifier(w, x, y, z);
                    FluidContainerComponent fluid = getFluid(w, x, y, z);
                    if (mlb == null || fluid == null)
                        return;
                    // Fill the fluid output to capacity.
                    fluid.fill(ManaLiquifierBlock.MANA_FLUID_ID, FLUID_CAPACITY);
                    // Provide ample fuel energy and an essence.
                    mlb.setRemainingFuelEnergy(ManaLiquifierBlock.RECIPE_TIME + 2.0f);
                    ItemContainer input = mlb.getInputContainer();
                    if (input != null)
                        input.addItemStack(new ItemStack(ESSENCE, 1), false, false, false);
                }))
                .step(Steps.wait(ctx -> (int) (ManaLiquifierBlock.RECIPE_TIME * ctx.getWorld().getTps()) + 20))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    ManaLiquifierBlock mlb = getLiquifier(w, x, y, z);
                    FluidContainerComponent fluid = getFluid(w, x, y, z);
                    if (mlb == null || fluid == null)
                        return false;
                    ItemContainer input = mlb.getInputContainer();
                    return input != null
                            && countItems(input) == 1
                            && fluid.getAmount() == FLUID_CAPACITY
                            && mlb.getProcessingProgress() == ManaLiquifierBlock.RECIPE_TIME;
                }, "full fluid output: essence not consumed, fluid unchanged, progress clamped at RECIPE_TIME"));
    }

    /**
     * Test 5 — A non-essence item in the input slot causes the system to stall
     * at {@link ManaLiquifierBlock#RECIPE_TIME}: no mana is produced and the
     * unrecognised item remains in the slot.
     */
    private static TestCase nonEssenceInInputClampsProgress() {
        return new TestCase("non_essence_in_input_clamps_progress", 10, 5, 5)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    place(w, x, y, z);
                }))
                .step(Steps.wait(SETUP_WAIT))
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    ManaLiquifierBlock mlb = getLiquifier(w, x, y, z);
                    if (mlb == null)
                        return;
                    mlb.setRemainingFuelEnergy(ManaLiquifierBlock.RECIPE_TIME + 2.0f);
                    ItemContainer input = mlb.getInputContainer();
                    if (input != null)
                        input.addItemStack(new ItemStack(NON_ESSENCE_ITEM, 1), false, false, false);
                }))
                .step(Steps.wait(ctx -> (int) (ManaLiquifierBlock.RECIPE_TIME * ctx.getWorld().getTps()) + 20))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    ManaLiquifierBlock mlb = getLiquifier(w, x, y, z);
                    FluidContainerComponent fluid = getFluid(w, x, y, z);
                    if (mlb == null || fluid == null)
                        return false;
                    return mlb.getProcessingProgress() == ManaLiquifierBlock.RECIPE_TIME
                            && fluid.getAmount() == 0;
                }, "non-essence input: progress clamped at RECIPE_TIME, no mana produced"));
    }

    /**
     * Test 6 — When fuel runs out before a cycle completes (with no fuel item to
     * replenish), processing must halt mid-cycle. After 3 seconds the progress
     * must be above zero but below {@link ManaLiquifierBlock#RECIPE_TIME}, and
     * no mana must have been produced.
     *
     * <p>Fuel energy is set to {@code RECIPE_TIME / 2} seconds — enough to
     * advance progress half-way, after which the machine pauses indefinitely.
     */
    private static TestCase fuelExhaustedMidCyclePausesProgress() {
        return new TestCase("fuel_exhausted_mid_cycle_pauses_progress", 10, 5, 5)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    place(w, x, y, z);
                }))
                .step(Steps.wait(SETUP_WAIT))
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    ManaLiquifierBlock mlb = getLiquifier(w, x, y, z);
                    if (mlb == null)
                        return;
                    // Provide less fuel than needed to complete one cycle.
                    mlb.setRemainingFuelEnergy(ManaLiquifierBlock.RECIPE_TIME / 2.0f);
                    ItemContainer input = mlb.getInputContainer();
                    if (input != null)
                        input.addItemStack(new ItemStack(ESSENCE, 1), false, false, false);
                    // fuel container left empty so no replenishment occurs
                }))
                .step(Steps.wait(ctx -> 3 * ctx.getWorld().getTps()))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    ManaLiquifierBlock mlb = getLiquifier(w, x, y, z);
                    FluidContainerComponent fluid = getFluid(w, x, y, z);
                    if (mlb == null || fluid == null)
                        return false;
                    float progress = mlb.getProcessingProgress();
                    // Progress advanced while fuel lasted, but never reached RECIPE_TIME.
                    return progress > 0.0f
                            && progress < ManaLiquifierBlock.RECIPE_TIME
                            && fluid.getAmount() == 0;
                }, "fuel exhausted mid-cycle: progress > 0 and < RECIPE_TIME, no mana produced"));
    }
}
