package dev.drav.glyphworks.crafting.system;

import javax.annotation.Nullable;

import org.joml.Vector3i;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.crafting.component.AutoCraftingBenchBlock;
import dev.drav.glyphworks.crafting.component.AutoProcessingBenchBlock;
import dev.drav.glyphworks.fluid.FluidStack;
import dev.drav.glyphworks.fluid.container.FluidContainer;
import dev.drav.glyphworks.fluid.event.FluidItemRegistry;
import dev.drav.glyphworks.grid.event.PlaceGridBlockEvent;
import dev.drav.glyphworks.test.framework.Steps;
import dev.drav.glyphworks.test.framework.TestCase;
import dev.drav.glyphworks.test.framework.TestRegistry;
import dev.drav.glyphworks.test.framework.TestSuite;

/**
 * Suite {@code "auto_crafting_bench_recipe_lock"} — verifies the recipe-lock
 * feature of {@link AutoCraftingBenchBlock} and how it drives
 * {@link AutoProcessingBenchBlock}.
 *
 * <p>Recipe under test: {@code Deco_Target_Recipe_Generated_0} —
 * {@code Ingredient_Fibre} → {@code Deco_Target}.
 */
public final class AutoCraftingBenchRecipeLockTests {

    private static final String SELECTOR_BLOCK = "Glyphworks_Test_Crafting_Bench_Selector";

    private static final String RECIPE_ID = "Deco_Target_Recipe_Generated_0";
    private static final String RECIPE_INPUT = "Ingredient_Fibre";
    private static final String WRONG_INPUT = "Rock_Stone";

    private static final String MANA_FLUID_ITEM = "Glyphworks_Fluid_Mana";
    private static final int FLUID_AMOUNT_MB = 4000;

    private AutoCraftingBenchRecipeLockTests() {
    }

    public static void register(String moduleId) {
        TestRegistry.register(moduleId, buildSuite());
    }

    private static TestSuite buildSuite() {
        return new TestSuite("auto_crafting_bench_recipe_lock")
                .test(recipeLockResizesInputContainer())
                .test(recipeLockAppliesIngredientFilter())
                .test(lockedRecipeFullCraftCycle())
                .test(clearingRecipeClearsInputContainer())
                .test(recipeLockSyncsRecipeIdToProcessingBench());
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    private static TestCase recipeLockResizesInputContainer() {
        return new TestCase("recipe_lock_resizes_input_container", 3, 3, 3)
                .step(Steps.run(ctx -> {
                    placeBlock(ctx.getWorld(), ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ(), SELECTOR_BLOCK);
                }))
                .step(Steps.waitUntil(
                        ctx -> isFluidFuelInitialised(ctx.getWorld(),
                                ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()),
                        ctx -> 5 * ctx.getWorld().getTps(),
                        "bench initialised"))
                .step(Steps.run(ctx -> {
                    AutoCraftingBenchBlock acbb = getSelector(ctx.getWorld(),
                            ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    if (acbb != null)
                        acbb.setLockedRecipe(RECIPE_ID);
                }))
                .step(Steps.waitUntil(
                        ctx -> {
                            AutoProcessingBenchBlock apbb = getProcessingBench(ctx.getWorld(),
                                    ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                            if (apbb == null)
                                return false;
                            ItemContainer input = apbb.getItemInputContainer();
                            return input != null && input.getCapacity() == 1;
                        },
                        ctx -> 3 * ctx.getWorld().getTps(),
                        "input container resized to 1 slot for locked recipe"))
                .step(Steps.assertThat(ctx -> {
                    AutoProcessingBenchBlock apbb = getProcessingBench(ctx.getWorld(),
                            ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    if (apbb == null)
                        return false;
                    ItemContainer input = apbb.getItemInputContainer();
                    return input != null && input.getCapacity() == 1;
                }, "input container has 1 slot after recipe lock"));
    }

    private static TestCase recipeLockAppliesIngredientFilter() {
        return new TestCase("recipe_lock_applies_ingredient_filter", 3, 3, 3)
                .step(Steps.run(ctx -> {
                    placeBlock(ctx.getWorld(), ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ(), SELECTOR_BLOCK);
                }))
                .step(Steps.waitUntil(
                        ctx -> isFluidFuelInitialised(ctx.getWorld(),
                                ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()),
                        ctx -> 5 * ctx.getWorld().getTps(),
                        "bench initialised"))
                .step(Steps.run(ctx -> {
                    AutoCraftingBenchBlock acbb = getSelector(ctx.getWorld(),
                            ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    if (acbb != null)
                        acbb.setLockedRecipe(RECIPE_ID);
                }))
                .step(Steps.waitUntil(
                        ctx -> {
                            AutoProcessingBenchBlock apbb = getProcessingBench(ctx.getWorld(),
                                    ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                            return apbb != null && apbb.getItemInputContainer() != null
                                    && apbb.getItemInputContainer().getCapacity() == 1;
                        },
                        ctx -> 3 * ctx.getWorld().getTps(),
                        "input container ready"))
                .step(Steps.assertThat(ctx -> {
                    AutoProcessingBenchBlock apbb = getProcessingBench(ctx.getWorld(),
                            ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    if (apbb == null)
                        return false;
                    ItemContainer input = apbb.getItemInputContainer();
                    if (input == null)
                        return false;
                    boolean wrongAccepted = input.canAddItemStack(
                            new ItemStack(WRONG_INPUT, 1));
                    if (wrongAccepted)
                        return false;
                    boolean correctAccepted = input.canAddItemStack(
                            new ItemStack(RECIPE_INPUT, 1));
                    return correctAccepted;
                }, "filter rejects " + WRONG_INPUT + " and accepts " + RECIPE_INPUT));
    }

    private static TestCase lockedRecipeFullCraftCycle() {
        return new TestCase("locked_recipe_full_craft_cycle", 3, 3, 3)
                .step(Steps.run(ctx -> {
                    placeBlock(ctx.getWorld(), ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ(), SELECTOR_BLOCK);
                }))
                .step(Steps.waitUntil(
                        ctx -> isFluidFuelInitialised(ctx.getWorld(),
                                ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()),
                        ctx -> 5 * ctx.getWorld().getTps(),
                        "bench initialised"))
                .step(Steps.run(ctx -> {
                    AutoCraftingBenchBlock acbb = getSelector(ctx.getWorld(),
                            ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    if (acbb != null)
                        acbb.setLockedRecipe(RECIPE_ID);
                }))
                .step(Steps.waitUntil(
                        ctx -> {
                            AutoProcessingBenchBlock apbb = getProcessingBench(ctx.getWorld(),
                                    ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                            return apbb != null && apbb.getItemInputContainer() != null
                                    && apbb.getItemInputContainer().getCapacity() == 1;
                        },
                        ctx -> 3 * ctx.getWorld().getTps(),
                        "input container ready"))
                .step(Steps.run(ctx -> {
                    AutoProcessingBenchBlock apbb = getProcessingBench(ctx.getWorld(),
                            ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    if (apbb == null)
                        return;
                    ItemContainer input = apbb.getItemInputContainer();
                    if (input != null)
                        input.addItemStack(new ItemStack(RECIPE_INPUT, 1), false, false, false);
                    seedFluidFuel(apbb);
                }))
                .step(Steps.waitUntil(
                        ctx -> {
                            AutoProcessingBenchBlock apbb = getProcessingBench(ctx.getWorld(),
                                    ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                            return apbb != null && countOutput(apbb) > 0;
                        },
                        ctx -> 3 * ctx.getWorld().getTps(),
                        "output produced"))
                .step(Steps.assertThat(ctx -> {
                    AutoProcessingBenchBlock apbb = getProcessingBench(ctx.getWorld(),
                            ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    return apbb != null && countOutput(apbb) > 0;
                }, "locked recipe produced output"));
    }

    private static TestCase clearingRecipeClearsInputContainer() {
        return new TestCase("clearing_recipe_clears_input_container", 3, 3, 3)
                .step(Steps.run(ctx -> {
                    placeBlock(ctx.getWorld(), ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ(), SELECTOR_BLOCK);
                }))
                .step(Steps.waitUntil(
                        ctx -> isFluidFuelInitialised(ctx.getWorld(),
                                ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()),
                        ctx -> 5 * ctx.getWorld().getTps(),
                        "bench initialised"))
                .step(Steps.run(ctx -> {
                    AutoCraftingBenchBlock acbb = getSelector(ctx.getWorld(),
                            ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    if (acbb != null)
                        acbb.setLockedRecipe(RECIPE_ID);
                }))
                .step(Steps.waitUntil(
                        ctx -> {
                            AutoProcessingBenchBlock apbb = getProcessingBench(ctx.getWorld(),
                                    ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                            return apbb != null && apbb.getItemInputContainer() != null
                                    && apbb.getItemInputContainer().getCapacity() == 1;
                        },
                        ctx -> 3 * ctx.getWorld().getTps(),
                        "input container initialised with recipe"))
                .step(Steps.run(ctx -> {
                    AutoCraftingBenchBlock acbb = getSelector(ctx.getWorld(),
                            ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    if (acbb != null)
                        acbb.setLockedRecipe(null);
                }))
                .step(Steps.waitUntil(
                        ctx -> {
                            AutoProcessingBenchBlock apbb = getProcessingBench(ctx.getWorld(),
                                    ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                            if (apbb == null)
                                return false;
                            ItemContainer input = apbb.getItemInputContainer();
                            return input == null || input.getCapacity() == 0;
                        },
                        ctx -> 3 * ctx.getWorld().getTps(),
                        "input container cleared after recipe cleared"))
                .step(Steps.assertThat(ctx -> {
                    AutoProcessingBenchBlock apbb = getProcessingBench(ctx.getWorld(),
                            ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    if (apbb == null)
                        return false;
                    ItemContainer input = apbb.getItemInputContainer();
                    return input == null || input.getCapacity() == 0;
                }, "input container empty/absent after recipe cleared"));
    }

    private static TestCase recipeLockSyncsRecipeIdToProcessingBench() {
        return new TestCase("recipe_lock_syncs_recipe_id_to_processing_bench", 3, 3, 3)
                .step(Steps.run(ctx -> {
                    placeBlock(ctx.getWorld(), ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ(), SELECTOR_BLOCK);
                }))
                .step(Steps.waitUntil(
                        ctx -> isFluidFuelInitialised(ctx.getWorld(),
                                ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()),
                        ctx -> 5 * ctx.getWorld().getTps(),
                        "bench initialised"))
                .step(Steps.run(ctx -> {
                    AutoCraftingBenchBlock acbb = getSelector(ctx.getWorld(),
                            ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    if (acbb != null)
                        acbb.setLockedRecipe(RECIPE_ID);
                }))
                .step(Steps.waitUntil(
                        ctx -> {
                            AutoProcessingBenchBlock apbb = getProcessingBench(ctx.getWorld(),
                                    ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                            if (apbb == null)
                                return false;
                            ItemContainer input = apbb.getItemInputContainer();
                            return input != null && input.getCapacity() == 1;
                        },
                        ctx -> 3 * ctx.getWorld().getTps(),
                        "processing bench input container resized by locked recipe"))
                .step(Steps.assertThat(ctx -> {
                    AutoCraftingBenchBlock acbb = getSelector(ctx.getWorld(),
                            ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    return acbb != null && RECIPE_ID.equals(acbb.getLockedRecipeId());
                }, "selector holds locked recipe id == " + RECIPE_ID));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void placeBlock(World w, int x, int y, int z, String blockId) {
        w.setBlock(x, y, z, blockId);
        PlaceGridBlockEvent.connectBlock(w, new Vector3i(x, y, z));
    }

    private static boolean isFluidFuelInitialised(World w, int x, int y, int z) {
        AutoProcessingBenchBlock apbb = getProcessingBench(w, x, y, z);
        return apbb != null && apbb.getFluidFuelContainer() != null;
    }

    private static void seedFluidFuel(AutoProcessingBenchBlock apbb) {
        FluidContainer fc = apbb.getFluidFuelContainer();
        if (fc == null)
            return;
        String fluidId = FluidItemRegistry.resolveFluidId(MANA_FLUID_ITEM);
        if (fluidId == null)
            return;
        fc.addFluidStack(new FluidStack(fluidId, FLUID_AMOUNT_MB, fc.getCapacityMbPerSlot()), false, false);
    }

    private static int countOutput(AutoProcessingBenchBlock apbb) {
        ItemContainer output = apbb.getItemOutputContainer();
        if (output == null)
            return 0;
        int total = 0;
        for (short i = 0; i < output.getCapacity(); i++) {
            ItemStack s = output.getItemStack(i);
            if (s != null && !s.isEmpty())
                total += s.getQuantity();
        }
        return total;
    }

    @Nullable
    private static AutoCraftingBenchBlock getSelector(World w, int x, int y, int z) {
        Ref<ChunkStore> ref = getBlockEntityRef(w, x, y, z);
        if (ref == null)
            return null;
        return w.getChunkStore().getStore().getComponent(ref, AutoCraftingBenchBlock.getComponentType());
    }

    @Nullable
    private static AutoProcessingBenchBlock getProcessingBench(World w, int x, int y, int z) {
        Ref<ChunkStore> ref = getBlockEntityRef(w, x, y, z);
        if (ref == null)
            return null;
        return w.getChunkStore().getStore().getComponent(ref, AutoProcessingBenchBlock.getComponentType());
    }

    @Nullable
    private static Ref<ChunkStore> getBlockEntityRef(World w, int x, int y, int z) {
        ChunkStore chunkStore = w.getChunkStore();
        Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunkRef == null || !chunkRef.isValid())
            return null;

        Store<ChunkStore> store = chunkStore.getStore();
        BlockComponentChunk blockComponentChunk = store.getComponent(chunkRef, BlockComponentChunk.getComponentType());
        if (blockComponentChunk == null)
            return null;

        Ref<ChunkStore> blockEntityRef = blockComponentChunk.getEntityReference(
                ChunkUtil.indexBlockInColumn(x, y, z));
        if (blockEntityRef == null || !blockEntityRef.isValid())
            return null;

        return blockEntityRef;
    }
}
