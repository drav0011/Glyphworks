package dev.drav.glyphworks.crafting.tests.system;

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
 * Suite {@code "auto_processing_bench_flow"} — end-to-end integration tests
 * for the {@link AutoProcessingBenchBlock} crafting pipeline.
 *
 * <p>Uses {@code Glyphworks_Test_Crafting_Bench_FluidFuel_Any} (Tannery bench,
 * auto-detected recipe). Recipe under test:
 * {@code Ingredient_Hide_Light} → {@code Ingredient_Leather_Light}.
 */
public final class AutoProcessingBenchFlowTests {

    private static final String TEST_BLOCK = "Glyphworks_Test_Crafting_Bench_FluidFuel_Any";

    private static final String INPUT_ITEM = "Ingredient_Hide_Light";
    private static final String OUTPUT_ITEM = "Ingredient_Leather_Light";
    private static final String FILLER_ITEM = "Rock_Stone";
    private static final String MANA_FLUID_ITEM = "Glyphworks_Fluid_Mana";
    private static final int FLUID_AMOUNT_MB = 4000;

    private AutoProcessingBenchFlowTests() {
    }

    public static void register(String moduleId) {
        TestRegistry.register(moduleId, buildSuite());
    }

    private static TestSuite buildSuite() {
        return new TestSuite("auto_processing_bench_flow")
                .test(benchCraftsWhenIngredientAndManaPresent())
                .test(benchResetsProgressWhenInputRemoved())
                .test(benchHoldsWhenOutputFull())
                .test(multipleCyclesProduceMultipleOutputs());
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    private static TestCase benchCraftsWhenIngredientAndManaPresent() {
        return new TestCase("bench_crafts_when_ingredient_and_mana_present", 3, 3, 3)
                .step(Steps.run(ctx -> placeBlock(ctx.getWorld(),
                        ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ(), TEST_BLOCK)))
                .step(Steps.waitUntil(
                        ctx -> isFluidFuelInitialised(ctx.getWorld(),
                                ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()),
                        ctx -> 5 * ctx.getWorld().getTps(),
                        "bench initialised"))
                .step(Steps.run(ctx -> {
                    AutoProcessingBenchBlock apbb = getBench(ctx.getWorld(),
                            ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    if (apbb == null)
                        return;
                    seedInput(apbb, INPUT_ITEM, 1);
                    seedFluidFuel(apbb);
                }))
                .step(Steps.waitUntil(
                        ctx -> countOutput(ctx.getWorld(),
                                ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()) > 0,
                        ctx -> 25 * ctx.getWorld().getTps(),
                        "output produced"))
                .step(Steps.assertThat(ctx -> {
                    AutoProcessingBenchBlock apbb = getBench(ctx.getWorld(),
                            ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    if (apbb == null)
                        return false;
                    ItemContainer output = apbb.getItemOutputContainer();
                    if (output == null)
                        return false;
                    for (short i = 0; i < output.getCapacity(); i++) {
                        ItemStack s = output.getItemStack(i);
                        if (s != null && OUTPUT_ITEM.equals(s.getItemId()))
                            return true;
                    }
                    return false;
                }, "output container holds " + OUTPUT_ITEM));
    }

    private static TestCase benchResetsProgressWhenInputRemoved() {
        return new TestCase("bench_resets_progress_when_input_removed", 3, 3, 3)
                .step(Steps.run(ctx -> placeBlock(ctx.getWorld(),
                        ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ(), TEST_BLOCK)))
                .step(Steps.waitUntil(
                        ctx -> isFluidFuelInitialised(ctx.getWorld(),
                                ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()),
                        ctx -> 5 * ctx.getWorld().getTps(),
                        "bench initialised"))
                .step(Steps.run(ctx -> {
                    AutoProcessingBenchBlock apbb = getBench(ctx.getWorld(),
                            ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    if (apbb == null)
                        return;
                    seedInput(apbb, INPUT_ITEM, 1);
                    seedFluidFuel(apbb);
                }))
                .step(Steps.wait(5))
                .step(Steps.run(ctx -> {
                    AutoProcessingBenchBlock apbb = getBench(ctx.getWorld(),
                            ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    if (apbb == null)
                        return;
                    ItemContainer input = apbb.getItemInputContainer();
                    if (input != null)
                        input.setItemStackForSlot((short) 0, null, false);
                }))
                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))
                .step(Steps.assertThat(ctx -> {
                    AutoProcessingBenchBlock apbb = getBench(ctx.getWorld(),
                            ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    if (apbb == null)
                        return false;
                    return apbb.getInputProgress() == 0.0f && countOutput(ctx.getWorld(),
                            ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()) == 0;
                }, "progress reset to zero and output empty after input removed"));
    }

    private static TestCase benchHoldsWhenOutputFull() {
        return new TestCase("bench_holds_when_output_full", 3, 3, 3)
                .step(Steps.run(ctx -> placeBlock(ctx.getWorld(),
                        ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ(), TEST_BLOCK)))
                .step(Steps.waitUntil(
                        ctx -> isFluidFuelInitialised(ctx.getWorld(),
                                ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()),
                        ctx -> 5 * ctx.getWorld().getTps(),
                        "bench initialised"))
                .step(Steps.run(ctx -> {
                    AutoProcessingBenchBlock apbb = getBench(ctx.getWorld(),
                            ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    if (apbb == null)
                        return;
                    fillOutput(apbb);
                    seedInput(apbb, INPUT_ITEM, 1);
                    seedFluidFuel(apbb);
                }))
                .step(Steps.wait(ctx -> 3 * ctx.getWorld().getTps()))
                .step(Steps.assertThat(ctx -> {
                    AutoProcessingBenchBlock apbb = getBench(ctx.getWorld(),
                            ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    if (apbb == null)
                        return false;
                    int inputCount = countInput(apbb);
                    if (inputCount != 1)
                        return false;
                    ItemContainer output = apbb.getItemOutputContainer();
                    if (output == null)
                        return false;
                    for (short i = 0; i < output.getCapacity(); i++) {
                        ItemStack s = output.getItemStack(i);
                        if (s == null || !FILLER_ITEM.equals(s.getItemId()))
                            return false;
                    }
                    return true;
                }, "input not consumed and output unchanged when output full"));
    }

    private static TestCase multipleCyclesProduceMultipleOutputs() {
        return new TestCase("multiple_cycles_produce_multiple_outputs", 3, 3, 3)
                .step(Steps.run(ctx -> placeBlock(ctx.getWorld(),
                        ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ(), TEST_BLOCK)))
                .step(Steps.waitUntil(
                        ctx -> isFluidFuelInitialised(ctx.getWorld(),
                                ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()),
                        ctx -> 5 * ctx.getWorld().getTps(),
                        "bench initialised"))
                .step(Steps.run(ctx -> {
                    AutoProcessingBenchBlock apbb = getBench(ctx.getWorld(),
                            ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    if (apbb == null)
                        return;
                    seedInput(apbb, INPUT_ITEM, 3);
                    seedFluidFuel(apbb);
                }))
                .step(Steps.waitUntil(
                        ctx -> countOutput(ctx.getWorld(),
                                ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()) >= 3,
                        ctx -> 70 * ctx.getWorld().getTps(),
                        "3 outputs produced"))
                .step(Steps.assertThat(ctx -> {
                    int output = countOutput(ctx.getWorld(),
                            ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    return output == 3;
                }, "output container holds exactly 3 " + OUTPUT_ITEM));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void placeBlock(World w, int x, int y, int z, String blockId) {
        w.setBlock(x, y, z, blockId);
        PlaceGridBlockEvent.connectBlock(w, new Vector3i(x, y, z));
    }

    private static boolean isFluidFuelInitialised(World w, int x, int y, int z) {
        AutoProcessingBenchBlock apbb = getBench(w, x, y, z);
        return apbb != null && apbb.getFluidFuelContainer() != null;
    }

    private static void seedInput(AutoProcessingBenchBlock apbb, String itemId, int qty) {
        ItemContainer input = apbb.getItemInputContainer();
        if (input == null)
            return;
        input.addItemStack(new ItemStack(itemId, qty), false, false, false);
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

    private static void fillOutput(AutoProcessingBenchBlock apbb) {
        ItemContainer output = apbb.getItemOutputContainer();
        if (output == null)
            return;
        for (short i = 0; i < output.getCapacity(); i++) {
            output.setItemStackForSlot(i, new ItemStack(FILLER_ITEM, 1), false);
        }
    }

    private static int countInput(AutoProcessingBenchBlock apbb) {
        ItemContainer input = apbb.getItemInputContainer();
        if (input == null)
            return 0;
        int total = 0;
        for (short i = 0; i < input.getCapacity(); i++) {
            ItemStack s = input.getItemStack(i);
            if (s != null && !s.isEmpty())
                total += s.getQuantity();
        }
        return total;
    }

    private static int countOutput(World w, int x, int y, int z) {
        AutoProcessingBenchBlock apbb = getBench(w, x, y, z);
        if (apbb == null)
            return 0;
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
    private static AutoProcessingBenchBlock getBench(World w, int x, int y, int z) {
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
