package dev.drav.glyphworks.crafting.system;

import javax.annotation.Nullable;

import org.joml.Vector3i;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import dev.drav.glyphworks.crafting.component.AutoProcessingBenchBlock;
import dev.drav.glyphworks.fluid.container.FluidContainer;
import dev.drav.glyphworks.grid.component.GridComponent;
import dev.drav.glyphworks.grid.event.PlaceGridBlockEvent;
import dev.drav.glyphworks.grid.lookup.GridLookup;
import dev.drav.glyphworks.test.framework.Steps;
import dev.drav.glyphworks.test.framework.TestCase;
import dev.drav.glyphworks.test.framework.TestRegistry;
import dev.drav.glyphworks.test.framework.TestSuite;

/**
 * Suite {@code "auto_processing_bench_setup"} — verifies that
 * {@link AutoProcessingBenchBlock} containers are correctly initialised when a
 * bench block entity is added.
 */
public final class AutoProcessingBenchSetupTests {

    private static final String FLUID_FUEL_ANY_BLOCK = "Glyphworks_Test_Crafting_Bench_FluidFuel_Any";
    private static final String NO_FUEL_BLOCK = "Glyphworks_Test_Crafting_Bench_NoFuel";
    private static final String FURNACE_ID = "Glyphworks_Crafting_Machine_Furnace";

    private static final int EXPECTED_FLUID_FUEL_CAPACITY_MB = 4000;

    private AutoProcessingBenchSetupTests() {
    }

    public static void register(String moduleId) {
        TestRegistry.register(moduleId, buildSuite());
    }

    private static TestSuite buildSuite() {
        return new TestSuite("auto_processing_bench_setup")
                .test(fluidFuelContainerInitialisedWithCorrectCapacity())
                .test(itemOutputContainerIsOutputOnly())
                .test(benchHasFluidGridEntry())
                .test(benchHasItemGridEntry());
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    private static TestCase fluidFuelContainerInitialisedWithCorrectCapacity() {
        return new TestCase("fluid_fuel_container_initialised_with_correct_capacity", 3, 3, 3)
                .step(Steps.run(ctx -> placeBlock(ctx.getWorld(),
                        ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ(), FLUID_FUEL_ANY_BLOCK)))
                .step(Steps.succeedWhen(
                        ctx -> isFluidFuelInitialised(ctx.getWorld(),
                                ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()),
                        ctx -> 5 * ctx.getWorld().getTps(),
                        "fluid fuel container initialised"))
                .step(Steps.assertThat(ctx -> {
                    AutoProcessingBenchBlock apbb = getBench(ctx.getWorld(),
                            ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    if (apbb == null)
                        return false;
                    FluidContainer fc = apbb.getFluidFuelContainer();
                    if (fc == null)
                        return false;
                    return (int) fc.getCapacity() * fc.getCapacityMbPerSlot() == EXPECTED_FLUID_FUEL_CAPACITY_MB;
                }, "fluid fuel container capacity == " + EXPECTED_FLUID_FUEL_CAPACITY_MB + " MB"));
    }

    private static TestCase itemOutputContainerIsOutputOnly() {
        return new TestCase("item_output_container_is_output_only", 3, 3, 3)
                .step(Steps.run(ctx -> placeBlock(ctx.getWorld(),
                        ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ(), NO_FUEL_BLOCK)))
                .step(Steps.succeedWhen(
                        ctx -> isItemOutputInitialised(ctx.getWorld(),
                                ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()),
                        ctx -> 5 * ctx.getWorld().getTps(),
                        "item output container initialised"))
                .step(Steps.assertThat(ctx -> {
                    AutoProcessingBenchBlock apbb = getBench(ctx.getWorld(),
                            ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    if (apbb == null)
                        return false;
                    var output = apbb.getItemOutputContainer();
                    if (output == null)
                        return false;
                    boolean added = output.addItemStack(new ItemStack("Rock_Stone", 1), true, false, true).succeeded();
                    return !added;
                }, "output-only filter rejects direct addItemStack"));
    }

    private static TestCase benchHasFluidGridEntry() {
        return new TestCase("bench_has_fluid_grid_entry", 3, 3, 3)
                .step(Steps.run(ctx -> {       
                    World w = ctx.getWorld();
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    w.setBlock(x, y, z, FURNACE_ID);
                    PlaceGridBlockEvent.connectBlock(w, new Vector3i(x, y, z));
                }))
                .step(Steps.succeedWhen(
                        ctx -> getGridComponent(ctx.getWorld(),
                                ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()) != null,
                        ctx -> 5 * ctx.getWorld().getTps(),
                        "GridComponent initialised"))
                .step(Steps.assertThat(ctx -> {
                    GridComponent gc = getGridComponent(ctx.getWorld(),
                            ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    return gc != null && gc.getEntry("Fluid") != null;
                }, "bench GridComponent has a Fluid grid type entry"));
    }

    private static TestCase benchHasItemGridEntry() {
        return new TestCase("bench_has_item_grid_entry", 3, 3, 3)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    w.setBlock(x, y, z, FURNACE_ID);
                    PlaceGridBlockEvent.connectBlock(w, new Vector3i(x, y, z));
                }))
                .step(Steps.succeedWhen(
                        ctx -> getGridComponent(ctx.getWorld(),
                                ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()) != null,
                        ctx -> 5 * ctx.getWorld().getTps(),
                        "GridComponent initialised"))
                .step(Steps.assertThat(ctx -> {
                    GridComponent gc = getGridComponent(ctx.getWorld(),
                            ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    return gc != null && gc.getEntry("Item") != null;
                }, "bench GridComponent has an Item grid type entry"));
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

    private static boolean isItemOutputInitialised(World w, int x, int y, int z) {
        AutoProcessingBenchBlock apbb = getBench(w, x, y, z);
        return apbb != null && apbb.getItemOutputContainer() != null;
    }

    @Nullable
    private static AutoProcessingBenchBlock getBench(World w, int x, int y, int z) {
        Ref<ChunkStore> ref = getBlockEntityRef(w, x, y, z);
        if (ref == null)
            return null;
        return w.getChunkStore().getStore().getComponent(ref, AutoProcessingBenchBlock.getComponentType());
    }

    @Nullable
    private static GridComponent getGridComponent(World w, int x, int y, int z) {
        GridLookup lu = GridLookup.resolve(w.getChunkStore(), new Vector3i(x, y, z));
        if (lu == null)
            return null;
        return w.getChunkStore().getStore().getComponent(lu.blockRef(), GridComponent.getComponentType());
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
