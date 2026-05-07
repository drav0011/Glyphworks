package dev.drav.glyphworks.crafting.system;

import javax.annotation.Nullable;

import org.joml.Vector3i;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.World;

import dev.drav.glyphworks.GlyphworksPlugin;
import dev.drav.glyphworks.crafting.component.AutoProcessingBenchBlock;
import dev.drav.glyphworks.fluid.FluidStack;
import dev.drav.glyphworks.fluid.component.FluidContainerComponent;
import dev.drav.glyphworks.fluid.container.FluidContainer;
import dev.drav.glyphworks.fluid.event.FluidItemRegistry;
import dev.drav.glyphworks.fluid.FluidTestUtil;
import dev.drav.glyphworks.grid.event.PlaceGridBlockEvent;
import dev.drav.glyphworks.grid.graph.GridGraph;
import dev.drav.glyphworks.grid.lookup.GridLookup;
import dev.drav.glyphworks.grid.type.GridType;
import dev.drav.glyphworks.grid.type.GridTypeRegistry;
import dev.drav.glyphworks.item.ItemTestUtil;
import dev.drav.glyphworks.test.framework.Steps;
import dev.drav.glyphworks.test.framework.TestCase;
import dev.drav.glyphworks.test.framework.TestRegistry;
import dev.drav.glyphworks.test.framework.TestSuite;

/**
 * Suite {@code "auto_processing_bench_grid"} — verifies grid connectivity and
 * resource flow through a dedicated single-block test bench.
 *
 * <h3>Grid test bench face geometry</h3>
 * <ul>
 * <li><b>Fluid in</b> — top face → grid neighbor at {@code (bx, by+1, bz)}.</li>
 * <li><b>Item in</b> — south face → grid neighbor at {@code (bx, by, bz+1)}.</li>
 * <li><b>Item out</b> — north face → grid neighbor at {@code (bx, by, bz-1)}.</li>
 * </ul>
 */
public final class AutoProcessingBenchGridTests {

    // ── Block IDs ─────────────────────────────────────────────────────────────

    private static final String BENCH_ID = "Glyphworks_Test_Crafting_Bench_Grid";
    private static final String FLUID_PIPE_ID = "Glyphworks_Fluid_Pipe";
    private static final String ITEM_PIPE_ID = "Glyphworks_Item_Pipe";
    private static final String ITEM_CONTAINER_ID = "Glyphworks_Item_Container";
    private static final String ITEM_EXTRACTOR_ID = "Glyphworks_Item_Extractor";
    private static final String ITEM_INSERTER_ID = "Glyphworks_Item_Inserter";

    // ── Recipe / ingredient constants ─────────────────────────────────────────

    private static final String INPUT_ITEM = "Ingredient_Hide_Light";
    private static final String MANA_FLUID_ITEM = "Glyphworks_Fluid_Mana";
    private static final int FLUID_AMOUNT_MB = 4000;

    private AutoProcessingBenchGridTests() {
    }

    public static void register(String moduleId) {
        TestRegistry.register(moduleId, buildSuite());
    }

    private static TestSuite buildSuite() {
        return new TestSuite("auto_processing_bench_grid")
                .test(fluidPipeConnectsToBench())
                .test(itemPipeConnectsToBenchInput())
                .test(itemPipeConnectsToBenchOutput())
                .test(fluidFlowsFromNetworkIntoBench())
                .test(itemsFlowIntoBenchViaItemPipe())
                .test(itemsFlowOutOfBenchViaItemPipe());
    }

    // ── Connectivity tests ────────────────────────────────────────────────────

    private static TestCase fluidPipeConnectsToBench() {
        return new TestCase("fluid_pipe_connects_to_bench", 3, 10, 9)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int bx = ctx.getOriginX(), by = ctx.getOriginY(), bz = ctx.getOriginZ() + 4;
                    placeBench(w, bx, by, bz);
                    placeBlock(w, bx, by + 1, bz, FLUID_PIPE_ID);
                }))
                .step(Steps.succeedWhen(
                        ctx -> getProcessingBench(ctx.getWorld(),
                                ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ() + 4) != null,
                        ctx -> 5 * ctx.getWorld().getTps(),
                        "bench initialised"))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int bx = ctx.getOriginX(), by = ctx.getOriginY(), bz = ctx.getOriginZ() + 4;
                    Vector3i benchPos = new Vector3i(bx, by, bz);
                    Vector3i pipePos = new Vector3i(bx, by + 1, bz);
                    return isConnected(w, "Fluid", benchPos, pipePos);
                }, "bench is connected to fluid pipe in Fluid grid"));
    }

    private static TestCase itemPipeConnectsToBenchInput() {
        return new TestCase("item_pipe_connects_to_bench_input", 3, 10, 9)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int bx = ctx.getOriginX(), by = ctx.getOriginY(), bz = ctx.getOriginZ() + 4;
                    placeBench(w, bx, by, bz);
                    placeBlock(w, bx, by, bz + 1, ITEM_PIPE_ID);
                }))
                .step(Steps.succeedWhen(
                        ctx -> getProcessingBench(ctx.getWorld(),
                                ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ() + 4) != null,
                        ctx -> 5 * ctx.getWorld().getTps(),
                        "bench initialised"))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int bx = ctx.getOriginX(), by = ctx.getOriginY(), bz = ctx.getOriginZ() + 4;
                    Vector3i benchPos = new Vector3i(bx, by, bz);
                    Vector3i pipePos = new Vector3i(bx, by, bz + 1);
                    return isConnected(w, "Item", benchPos, pipePos);
                }, "bench is connected to item pipe at input face in Item grid"));
    }

    private static TestCase itemPipeConnectsToBenchOutput() {
        return new TestCase("item_pipe_connects_to_bench_output", 3, 10, 9)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int bx = ctx.getOriginX(), by = ctx.getOriginY(), bz = ctx.getOriginZ() + 4;
                    placeBench(w, bx, by, bz);
                    placeBlock(w, bx, by, bz - 1, ITEM_PIPE_ID);
                }))
                .step(Steps.succeedWhen(
                        ctx -> getProcessingBench(ctx.getWorld(),
                                ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ() + 4) != null,
                        ctx -> 5 * ctx.getWorld().getTps(),
                        "bench initialised"))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int bx = ctx.getOriginX(), by = ctx.getOriginY(), bz = ctx.getOriginZ() + 4;
                    Vector3i benchPos = new Vector3i(bx, by, bz);
                    Vector3i pipePos = new Vector3i(bx, by, bz - 1);
                    return isConnected(w, "Item", benchPos, pipePos);
                }, "bench is connected to item pipe at output face in Item grid"));
    }

    // ── Flow tests ────────────────────────────────────────────────────────────

    private static TestCase fluidFlowsFromNetworkIntoBench() {
        return new TestCase("fluid_flows_from_network_into_bench", 3, 10, 9)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int bx = ctx.getOriginX(), by = ctx.getOriginY(), bz = ctx.getOriginZ() + 4;
                    placeBench(w, bx, by, bz);
                        placeBlock(w, bx, by + 1, bz, FLUID_PIPE_ID);
                }))
                .step(Steps.succeedWhen(
                        ctx -> isFluidSourceFilled(ctx.getWorld(),
                            ctx.getOriginX(), ctx.getOriginY() + 1, ctx.getOriginZ() + 4),
                        ctx -> 5 * ctx.getWorld().getTps(),
                        "fluid pipe container initialised"))
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int bx = ctx.getOriginX(), by = ctx.getOriginY(), bz = ctx.getOriginZ() + 4;
                        seedFluidSource(w, bx, by + 1, bz);
                }))
                .step(Steps.succeedWhen(
                        ctx -> {
                            World w = ctx.getWorld();
                            int bx = ctx.getOriginX(), by = ctx.getOriginY(), bz = ctx.getOriginZ() + 4;
                            AutoProcessingBenchBlock apbb = getProcessingBench(w, bx, by, bz);
                            if (apbb == null)
                                return false;
                            FluidContainer fc = apbb.getFluidFuelContainer();
                            return fc != null && getFluidAmount(fc) > 0;
                        },
                        ctx -> 5 * ctx.getWorld().getTps(),
                        "fluid transferred into bench fuel container"))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int bx = ctx.getOriginX(), by = ctx.getOriginY(), bz = ctx.getOriginZ() + 4;
                    AutoProcessingBenchBlock apbb = getProcessingBench(w, bx, by, bz);
                    if (apbb == null)
                        return false;
                    FluidContainer fc = apbb.getFluidFuelContainer();
                    return fc != null && getFluidAmount(fc) > 0;
                }, "bench fluid fuel container received fluid from grid"));
    }

    private static TestCase itemsFlowIntoBenchViaItemPipe() {
        return new TestCase("items_flow_into_bench_via_item_pipe", 3, 10, 9)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int bx = ctx.getOriginX(), by = ctx.getOriginY(), bz = ctx.getOriginZ() + 4;
                    placeBench(w, bx, by, bz);
                    FluidTestUtil.setBlockWithRotation(w, bx, by, bz + 1, ITEM_EXTRACTOR_ID, FluidTestUtil.ROTATION_SOUTH);
                    PlaceGridBlockEvent.connectBlock(w, new Vector3i(bx, by, bz + 1));
                    placeBlock(w, bx, by, bz + 2, ITEM_CONTAINER_ID);
                }))
                .step(Steps.succeedWhen(
                        ctx -> {
                            World w = ctx.getWorld();
                            int bx = ctx.getOriginX(), by = ctx.getOriginY(), bz = ctx.getOriginZ() + 4;
                            AutoProcessingBenchBlock apbb = getProcessingBench(w, bx, by, bz);
                            return apbb != null;
                        },
                        ctx -> 5 * ctx.getWorld().getTps(),
                        "bench initialised"))
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int bx = ctx.getOriginX(), by = ctx.getOriginY(), bz = ctx.getOriginZ() + 4;
                    seedItemContainer(w, bx, by, bz + 2, INPUT_ITEM, 3);
                }))
                .step(Steps.succeedWhen(
                        ctx -> {
                            World w = ctx.getWorld();
                            int bx = ctx.getOriginX(), by = ctx.getOriginY(), bz = ctx.getOriginZ() + 4;
                            AutoProcessingBenchBlock apbb = getProcessingBench(w, bx, by, bz);
                            if (apbb == null)
                                return false;
                            ItemContainer input = apbb.getItemInputContainer();
                            return input != null && ItemTestUtil.countItems(input) > 0;
                        },
                        ctx -> 5 * ctx.getWorld().getTps(),
                        "items transferred into bench input"))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int bx = ctx.getOriginX(), by = ctx.getOriginY(), bz = ctx.getOriginZ() + 4;
                    AutoProcessingBenchBlock apbb = getProcessingBench(w, bx, by, bz);
                    if (apbb == null)
                        return false;
                    ItemContainer input = apbb.getItemInputContainer();
                    return input != null && ItemTestUtil.countItems(input) > 0;
                }, "bench item input container received items from grid"));
    }

    private static TestCase itemsFlowOutOfBenchViaItemPipe() {
        return new TestCase("items_flow_out_of_bench_via_item_pipe", 3, 10, 9)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int bx = ctx.getOriginX(), by = ctx.getOriginY(), bz = ctx.getOriginZ() + 4;
                    placeBench(w, bx, by, bz);
                    FluidTestUtil.setBlockWithRotation(w, bx, by, bz - 1, ITEM_INSERTER_ID, FluidTestUtil.ROTATION_NORTH);
                    PlaceGridBlockEvent.connectBlock(w, new Vector3i(bx, by, bz - 1));
                    placeBlock(w, bx, by, bz - 2, ITEM_CONTAINER_ID);
                }))
                .step(Steps.succeedWhen(
                        ctx -> {
                            World w = ctx.getWorld();
                            int bx = ctx.getOriginX(), by = ctx.getOriginY(), bz = ctx.getOriginZ() + 4;
                            AutoProcessingBenchBlock apbb = getProcessingBench(w, bx, by, bz);
                            return apbb != null && apbb.getItemOutputContainer() != null;
                        },
                        ctx -> 5 * ctx.getWorld().getTps(),
                        "bench initialised"))
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int bx = ctx.getOriginX(), by = ctx.getOriginY(), bz = ctx.getOriginZ() + 4;
                    AutoProcessingBenchBlock apbb = getProcessingBench(w, bx, by, bz);
                    if (apbb != null) {
                        ItemContainer output = apbb.getItemOutputContainer();
                        if (output != null)
                            output.setItemStackForSlot((short) 0, new ItemStack(INPUT_ITEM, 3), false);
                    }
                }))
                .step(Steps.succeedWhen(
                        ctx -> {
                            World w = ctx.getWorld();
                            int bx = ctx.getOriginX(), by = ctx.getOriginY(), bz = ctx.getOriginZ() + 4;
                            ItemContainerBlock icb = ItemTestUtil.getItemContainerBlock(w,
                                    new Vector3i(bx, by, bz - 2));
                            if (icb == null)
                                return false;
                            return ItemTestUtil.countItems(icb.getItemContainer()) > 0;
                        },
                        ctx -> 5 * ctx.getWorld().getTps(),
                        "items extracted from bench output into container"))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int bx = ctx.getOriginX(), by = ctx.getOriginY(), bz = ctx.getOriginZ() + 4;
                    ItemContainerBlock icb = ItemTestUtil.getItemContainerBlock(w,
                            new Vector3i(bx, by, bz - 2));
                    if (icb == null)
                        return false;
                    return ItemTestUtil.countItems(icb.getItemContainer()) > 0;
                }, "item container received items from bench output via grid"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void placeBench(World w, int x, int y, int z) {
        w.setBlock(x, y, z, BENCH_ID);
        PlaceGridBlockEvent.connectBlock(w, new Vector3i(x, y, z));
    }

    private static void placeBlock(World w, int x, int y, int z, String blockId) {
        w.setBlock(x, y, z, blockId);
        PlaceGridBlockEvent.connectBlock(w, new Vector3i(x, y, z));
    }

    private static boolean isFluidSourceFilled(World w, int x, int y, int z) {
        FluidContainerComponent fcc = FluidTestUtil.getContainer(w, new Vector3i(x, y, z));
        if (fcc == null)
            return false;
        FluidContainer fc = fcc.getFluidContainer();
        return fc != null && fc.getCapacity() > 0;
    }

    private static void seedFluidSource(World w, int x, int y, int z) {
        FluidContainerComponent fcc = FluidTestUtil.getContainer(w, new Vector3i(x, y, z));
        if (fcc == null)
            return;
        String fluidId = FluidItemRegistry.resolveFluidId(MANA_FLUID_ITEM);
        if (fluidId == null)
            return;
        FluidContainer fc = fcc.getFluidContainer();
        if (fc == null)
            return;
        fc.addFluidStack(new FluidStack(fluidId, FLUID_AMOUNT_MB));
    }

    private static void seedItemContainer(World w, int x, int y, int z, String itemId, int qty) {
        ItemContainerBlock icb = ItemTestUtil.getItemContainerBlock(w, new Vector3i(x, y, z));
        if (icb == null)
            return;
        ItemContainer c = icb.getItemContainer();
        if (c == null)
            return;
        c.addItemStack(new ItemStack(itemId, qty), false, false, false);
    }

    private static int getFluidAmount(FluidContainer fc) {
        int total = 0;
        for (short i = 0; i < fc.getCapacity(); i++) {
            FluidStack s = fc.getFluidStack(i);
            if (s != null)
                total += s.getAmount();
        }
        return total;
    }

    private static boolean isConnected(World w, String gridTypeId, Vector3i a, Vector3i b) {
        GridType type = GridTypeRegistry.get(gridTypeId);
        if (type == null)
            return false;
        GridGraph graph = GlyphworksPlugin.get().getGridModule().getGridGraph(w, type);
        if (graph == null)
            return false;
        return graph.getNeighbors(a).contains(b);
    }

    @Nullable
    private static AutoProcessingBenchBlock getProcessingBench(World w, int x, int y, int z) {
        GridLookup lu = GridLookup.resolve(w.getChunkStore(), new Vector3i(x, y, z));
        if (lu == null)
            return null;
        return w.getChunkStore().getStore().getComponent(lu.blockRef(),
                AutoProcessingBenchBlock.getComponentType());
    }
}
