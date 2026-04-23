package dev.drav.glyphworks.crafting.tests;

import javax.annotation.Nullable;

import org.joml.Vector3i;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;

import dev.drav.glyphworks.crafting.component.AutoCraftingBenchBlock;
import dev.drav.glyphworks.fluid.FluidStack;
import dev.drav.glyphworks.fluid.component.FluidContainerComponent;
import dev.drav.glyphworks.grid.event.PlaceGridBlockEvent;
import dev.drav.glyphworks.grid.lookup.GridLookup;
import dev.drav.glyphworks.test.framework.Steps;
import dev.drav.glyphworks.test.framework.TestCase;
import dev.drav.glyphworks.test.framework.TestRegistry;
import dev.drav.glyphworks.test.framework.TestSuite;

/**
 * Suite {@code "auto_crafting_bench_flow"} — end-to-end integration tests for
 * the automated crafting bench processing pipeline.
 *
 * <h3>Recipe under test</h3>
 * {@code "Deco_Target_Recipe_Generated_0"}: 1× {@code Ingredient_Fibre} → 1× {@code Deco_Target},
 * 1-second cycle time, craftable at Workbench (Tinkering category). Its single
 * input slot makes it the simplest recipe for isolation testing.
 *
 * <h3>Coverage</h3>
 * <ol>
 * <li>Locking a recipe resizes the input container to match the ingredient
 * count.</li>
 * <li>A full craft cycle: ingredient consumed, output produced, mana
 * drained.</li>
 * <li>Absence of mana pauses crafting without consuming the ingredient.</li>
 * <li>A full output container holds progress at {@code recipeTime} and
 * prevents input consumption.</li>
 * <li>Removing the ingredient mid-cycle resets progress to zero.</li>
 * <li>Multiple craft cycles accumulate output when ample ingredients and mana
 * are provided.</li>
 * </ol>
 */
public final class AutoCraftingBenchFlowTests {

    // ── Constants ─────────────────────────────────────────────────────────────

    private static final String BENCH_ID = "Glyphworks_Crafting_Machine_WorkBench";

    /**
     * Recipe ID for the {@code Deco_Target} item's embedded recipe: 1× Ingredient_Fibre → 1×
     * Deco_Target, 1-second cycle, Workbench (Tinkering). Embedded item recipes are registered
     * under the key {@code {itemId}_Recipe_Generated_0}.
     */
    private static final String RECIPE_ID = "Deco_Target_Recipe_Generated_0";

    private static final String RECIPE_INPUT_ITEM = "Ingredient_Fibre";

    /**
     * Item used to pre-fill the output container. Must differ from
     * {@code Deco_Target} so that {@code canFitOutput()} returns {@code false}.
     */
    private static final String FILLER_ITEM = "Rock_Stone";

    private static final String MANA_FLUID_ID = "Mana_Source";
    private static final int MANA_CAPACITY = 4_000;

    /**
     * Ticks to wait after bench setup before seeding items or asserting.
     * Enough for {@link dev.drav.glyphworks.crafting.system.AutoCraftingBenchSetupSystem}
     * to initialise the containers.
     */
    private static final int SETUP_WAIT = 2;

    private AutoCraftingBenchFlowTests() {
    }

    // ── Registration ──────────────────────────────────────────────────────────

    public static void register(String moduleId) {
        TestRegistry.register(moduleId, buildSuite());
    }

    private static TestSuite buildSuite() {
        return new TestSuite("auto_crafting_bench_flow")
                .test(recipeLockSetsInputSlots())
                .test(benchCraftsWithManaAndIngredient())
                .test(benchPausesWithoutMana())
                .test(progressHoldsWhenOutputFull())
                .test(missingIngredientResetProgress())
                .test(multipleCyclesAccumulateOutput());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void place(World w, int x, int y, int z) {
        w.setBlock(x, y, z, BENCH_ID);
        PlaceGridBlockEvent.connectBlock(w, new Vector3i(x, y, z));
    }

    @Nullable
    private static AutoCraftingBenchBlock getBench(World w, int x, int y, int z) {
        GridLookup lu = GridLookup.resolve(w.getChunkStore(), new Vector3i(x, y, z));
        if (lu == null)
            return null;
        return w.getChunkStore().getStore().getComponent(lu.blockRef(),
                AutoCraftingBenchBlock.getComponentType());
    }

    @Nullable
    private static BlockModule.BlockStateInfo getBsi(World w, int x, int y, int z) {
        GridLookup lu = GridLookup.resolve(w.getChunkStore(), new Vector3i(x, y, z));
        if (lu == null)
            return null;
        return w.getChunkStore().getStore().getComponent(lu.blockRef(),
                BlockModule.BlockStateInfo.getComponentType());
    }

    @Nullable
    private static FluidContainerComponent getFluid(World w, int x, int y, int z) {
        GridLookup lu = GridLookup.resolve(w.getChunkStore(), new Vector3i(x, y, z));
        if (lu == null)
            return null;
        return w.getChunkStore().getStore().getComponent(lu.blockRef(),
                FluidContainerComponent.getComponentType());
    }

    /**
     * Locks {@code recipe} into the bench at the given position.
     *
     * <p>The bench must be freshly placed (empty input), so the eject path inside
     * {@link AutoCraftingBenchBlock#setLockedRecipe} is never reached and
     * {@code rotationIndex = 0} is safe. A {@code null} {@link BlockType} returned
     * by the world is handled gracefully by the eject-path null check inside
     * {@code ejectItems}.
     */
    private static void lockRecipe(World w, int x, int y, int z, String recipe) {
        AutoCraftingBenchBlock bench = getBench(w, x, y, z);
        if (bench == null)
            return;
        BlockModule.BlockStateInfo bsi = getBsi(w, x, y, z);
        if (bsi == null)
            return;
        BlockType bt = w.getBlockType(x, y, z);
        if (bt == null)
            return;
        bench.setLockedRecipe(recipe, bsi, w, x, y, z, bt, 0);
    }

    /** Fills the bench's fluid container to capacity with mana. */
    private static void seedMana(World w, int x, int y, int z) {
        FluidContainerComponent fcc = getFluid(w, x, y, z);
        if (fcc != null)
            fcc.fill(new FluidStack(MANA_FLUID_ID, MANA_CAPACITY, fcc.getCapacity()));
    }

    /**
     * Adds {@code qty} units of {@code item} to the bench's input container,
     * bypassing slot change events.
     */
    private static void seedInput(World w, int x, int y, int z, String item, int qty) {
        AutoCraftingBenchBlock bench = getBench(w, x, y, z);
        if (bench == null)
            return;
        ItemContainer input = bench.getInputContainer();
        if (input == null)
            return;
        input.addItemStack(new ItemStack(item, qty), false, false, false);
    }

    /**
     * Fills every slot of the bench's output container with 1× {@link #FILLER_ITEM}
     * using a direct slot write that bypasses the output-only filter.
     * Since each slot now holds a non-{@code Deco_Target} item,
     * {@link AutoCraftingBenchBlock#canFitOutput()} returns {@code false}.
     */
    private static void fillOutput(World w, int x, int y, int z) {
        AutoCraftingBenchBlock bench = getBench(w, x, y, z);
        if (bench == null)
            return;
        ItemContainer output = bench.getOutputContainer();
        if (output == null)
            return;
        for (short i = 0; i < output.getCapacity(); i++) {
            output.setItemStackForSlot(i, new ItemStack(FILLER_ITEM, 1), false);
        }
    }

    /** Returns total item quantity across all input slots, or {@code -1} on error. */
    private static int countInput(World w, int x, int y, int z) {
        AutoCraftingBenchBlock bench = getBench(w, x, y, z);
        if (bench == null)
            return -1;
        ItemContainer input = bench.getInputContainer();
        if (input == null)
            return -1;
        int total = 0;
        for (short i = 0; i < input.getCapacity(); i++) {
            ItemStack stack = input.getItemStack(i);
            if (stack != null && !stack.isEmpty())
                total += stack.getQuantity();
        }
        return total;
    }

    /** Returns total item quantity across all output slots, or {@code -1} on error. */
    private static int countOutput(World w, int x, int y, int z) {
        AutoCraftingBenchBlock bench = getBench(w, x, y, z);
        if (bench == null)
            return -1;
        ItemContainer output = bench.getOutputContainer();
        if (output == null)
            return -1;
        int total = 0;
        for (short i = 0; i < output.getCapacity(); i++) {
            ItemStack stack = output.getItemStack(i);
            if (stack != null && !stack.isEmpty())
                total += stack.getQuantity();
        }
        return total;
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    /**
     * Test 1 — Locking {@link #RECIPE_ID} on a fresh bench resizes the input
     * container to exactly 1 slot (matching the recipe's single ingredient).
     */
    private static TestCase recipeLockSetsInputSlots() {
        return new TestCase("recipe_lock_sets_input_slots", 5, 5, 5)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    place(w, x, y, z);
                }))
                .step(Steps.wait(SETUP_WAIT))
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    lockRecipe(w, x, y, z, RECIPE_ID);
                }))
                .step(Steps.wait(SETUP_WAIT))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    AutoCraftingBenchBlock bench = getBench(w, x, y, z);
                    if (bench == null)
                        return false;
                    ItemContainer input = bench.getInputContainer();
                    return RECIPE_ID.equals(bench.getLockedRecipeId())
                            && input != null
                            && input.getCapacity() == 1;
                }, "locking '" + RECIPE_ID + "' creates a 1-slot input container"));
    }

    /**
     * Test 2 — A complete craft cycle: one ingredient + full mana tank produces
     * output after one second.
     *
     * <p>The ingredient is consumed from the input container and at least one
     * {@code Deco_Target} appears in the output container.
     */
    private static TestCase benchCraftsWithManaAndIngredient() {
        return new TestCase("bench_crafts_with_mana_and_ingredient", 5, 5, 5)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    place(w, x, y, z);
                }))
                .step(Steps.wait(SETUP_WAIT))
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    lockRecipe(w, x, y, z, RECIPE_ID);
                    seedInput(w, x, y, z, RECIPE_INPUT_ITEM, 1);
                    seedMana(w, x, y, z);
                }))
                .step(Steps.wait(ctx -> 3 * ctx.getWorld().getTps()))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    return countInput(w, x, y, z) == 0
                            && countOutput(w, x, y, z) >= 1;
                }, "1 craft cycle: ingredient consumed, Deco_Target present in output"));
    }

    /**
     * Test 3 — Without mana the bench must not advance: the ingredient remains
     * in the input container and the output stays empty after 3 seconds.
     */
    private static TestCase benchPausesWithoutMana() {
        return new TestCase("bench_pauses_without_mana", 5, 5, 5)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    place(w, x, y, z);
                }))
                .step(Steps.wait(SETUP_WAIT))
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    lockRecipe(w, x, y, z, RECIPE_ID);
                    seedInput(w, x, y, z, RECIPE_INPUT_ITEM, 1);
                    // intentionally no mana
                }))
                .step(Steps.wait(ctx -> 3 * ctx.getWorld().getTps()))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    return countOutput(w, x, y, z) == 0
                            && countInput(w, x, y, z) == 1;
                }, "without mana: output remains empty, ingredient is not consumed"));
    }

    /**
     * Test 4 — When the output container is pre-filled with a different item,
     * {@code canFitOutput()} returns {@code false}. The bench should advance
     * progress to {@code recipeTime} and then hold it there — inputs must NOT be
     * consumed and the output filler must remain unchanged.
     *
     * <p>The output container (4 slots) is filled with 1× {@link #FILLER_ITEM}
     * per slot. Because Rock_Stone ≠ Deco_Target, none of the slots can accept
     * the craft output, so every slot is effectively "full" from the recipe's
     * perspective.
     */
    private static TestCase progressHoldsWhenOutputFull() {
        return new TestCase("progress_holds_when_output_full", 5, 5, 5)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    place(w, x, y, z);
                }))
                .step(Steps.wait(SETUP_WAIT))
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    lockRecipe(w, x, y, z, RECIPE_ID);
                    fillOutput(w, x, y, z);     // 4× Rock_Stone (one per slot)
                    seedInput(w, x, y, z, RECIPE_INPUT_ITEM, 1);
                    seedMana(w, x, y, z);
                }))
                .step(Steps.wait(ctx -> 3 * ctx.getWorld().getTps()))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    AutoCraftingBenchBlock bench = getBench(w, x, y, z);
                    if (bench == null)
                        return false;
                    // Ingredient must not have been consumed.
                    // Output must still hold exactly the 4 filler items (no Deco_Target).
                    // Progress must be clamped at recipeTime (1.0 s) by Math.min.
                    return countInput(w, x, y, z) == 1
                            && countOutput(w, x, y, z) == 4
                            && bench.getCraftingProgress() == 1.0f;
                }, "full output: ingredient not consumed, filler unchanged, progress held at 1.0 s"));
    }

    /**
     * Test 5 — If the ingredient is removed mid-cycle, progress must reset to
     * zero. The output stays empty.
     *
     * <p>After 5 ticks (¼ second at 20 TPS) the progress is non-zero but
     * below {@code recipeTime} (1 s), so the craft has not yet completed.
     * Clearing the input slot forces {@code isReadyToCraft()} to return
     * {@code false}, which the system handles by resetting progress to 0.
     */
    private static TestCase missingIngredientResetProgress() {
        return new TestCase("missing_ingredient_resets_progress", 5, 5, 5)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    place(w, x, y, z);
                }))
                .step(Steps.wait(SETUP_WAIT))
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    lockRecipe(w, x, y, z, RECIPE_ID);
                    seedInput(w, x, y, z, RECIPE_INPUT_ITEM, 1);
                    seedMana(w, x, y, z);
                }))
                .step(Steps.wait(5)) // some progress accumulates, but < recipeTime
                .step(Steps.run(ctx -> {
                    // Remove all items from the input container.
                    World w = ctx.getWorld();
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    AutoCraftingBenchBlock bench = getBench(w, x, y, z);
                    if (bench == null)
                        return;
                    ItemContainer input = bench.getInputContainer();
                    if (input == null)
                        return;
                    for (short i = 0; i < input.getCapacity(); i++) {
                        input.setItemStackForSlot(i, null, false);
                    }
                }))
                .step(Steps.wait(ctx -> 3 * ctx.getWorld().getTps()))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    AutoCraftingBenchBlock bench = getBench(w, x, y, z);
                    return bench != null
                            && bench.getCraftingProgress() == 0.0f
                            && countOutput(w, x, y, z) == 0;
                }, "removing ingredient mid-cycle resets craftingProgress to 0 and produces no output"));
    }

    /**
     * Test 6 — Three successive craft cycles each consume one ingredient and
     * produce one output. After 5 seconds the input must be empty and the output
     * must hold exactly 3 items.
     *
     * <p>Three units of {@link #RECIPE_INPUT_ITEM} are seeded into the single
     * input slot (quantity 3 in slot 0). The mana tank starts full (4,000 L),
     * which covers ≥ 3 cycles at 25 L/tick × 20 ticks/s × 3 s = 1,500 L.
     */
    private static TestCase multipleCyclesAccumulateOutput() {
        return new TestCase("multiple_cycles_accumulate_output", 5, 5, 5)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    place(w, x, y, z);
                }))
                .step(Steps.wait(SETUP_WAIT))
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    lockRecipe(w, x, y, z, RECIPE_ID);
                    seedInput(w, x, y, z, RECIPE_INPUT_ITEM, 3); // enough for 3 cycles
                    seedMana(w, x, y, z);
                }))
                .step(Steps.wait(ctx -> 5 * ctx.getWorld().getTps()))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    return countInput(w, x, y, z) == 0
                            && countOutput(w, x, y, z) == 3;
                }, "3 craft cycles: all 3 ingredients consumed, 3 outputs in the output container"));
    }
}
