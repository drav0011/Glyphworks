package dev.drav.glyphworks.crafting.tests.system;

import javax.annotation.Nullable;

import org.joml.Vector3i;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.World;

import dev.drav.glyphworks.GlyphworksPlugin;
import dev.drav.glyphworks.crafting.component.AutoCraftingBenchBlock;
import dev.drav.glyphworks.crafting.component.AutoProcessingBenchBlock;
import dev.drav.glyphworks.fluid.FluidStack;
import dev.drav.glyphworks.fluid.component.FluidContainerComponent;
import dev.drav.glyphworks.fluid.container.FluidContainer;
import dev.drav.glyphworks.fluid.event.FluidItemRegistry;
import dev.drav.glyphworks.fluid.tests.FluidTestUtil;
import dev.drav.glyphworks.grid.event.PlaceGridBlockEvent;
import dev.drav.glyphworks.grid.graph.GridGraph;
import dev.drav.glyphworks.grid.lookup.GridLookup;
import dev.drav.glyphworks.grid.type.GridType;
import dev.drav.glyphworks.grid.type.GridTypeRegistry;
import dev.drav.glyphworks.item.tests.ItemTestUtil;
import dev.drav.glyphworks.test.framework.Steps;
import dev.drav.glyphworks.test.framework.TestCase;
import dev.drav.glyphworks.test.framework.TestRegistry;
import dev.drav.glyphworks.test.framework.TestSuite;

/**
 * Suite {@code "auto_processing_bench_grid"} — verifies grid connectivity and
 * resource flow through the {@code Glyphworks_Crafting_Machine_WorkBench}
 * multi-block structure.
 *
 * <h3>WorkBench face geometry (rotation 0, South-facing)</h3>
 * <ul>
 * <li><b>Fluid in</b> — face cell {@code (+0, +2, -1)}, normal Up →
 * grid neighbor at {@code (bx, by+3, bz-1)}.</li>
 * <li><b>Item in</b> — face cell {@code (+0, +0, +1)}, normal South →
 * grid neighbor at {@code (bx, by, bz+2)}.</li>
 * <li><b>Item out</b> — face cell {@code (+0, +0, -2)}, normal North →
 * grid neighbor at {@code (bx, by, bz-3)}.</li>
 * </ul>
 *
 * <p>
 * Bench is placed at {@code (ox, oy, oz+4)} within a {@code 3×10×9}
 * test area, leaving room for the fluid tank above/north and item
 * containers to the south/north.
 */
public final class AutoProcessingBenchGridTests {

    // ── Block IDs ─────────────────────────────────────────────────────────────

    private static final String BENCH_ID = "Glyphworks_Crafting_Machine_WorkBench";
    private static final String FLUID_PIPE_ID = "Glyphworks_Fluid_Pipe";
    private static final String FLUID_TANK_ID = "Glyphworks_Fluid_Tank";
    private static final String ITEM_PIPE_ID = "Glyphworks_Item_Pipe";
    private static final String ITEM_CONTAINER_ID = "Glyphworks_Item_Container";
    private static final String ITEM_EXTRACTOR_ID = "Glyphworks_Item_Extractor";
    private static final String ITEM_INSERTER_ID = "Glyphworks_Item_Inserter";

    // ── Recipe / ingredient constants ─────────────────────────────────────────

    private static final String RECIPE_ID = "Deco_Target_Recipe_Generated_0";
    private static final String RECIPE_INPUT = "Ingredient_Fibre";
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
                    // fluid neighbor: (bx, by+3, bz-1)
                    placeBlock(w, bx, by + 3, bz - 1, FLUID_PIPE_ID);
                }))
                .step(Steps.waitUntil(
                        ctx -> getProcessingBench(ctx.getWorld(),
                                ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ() + 4) != null,
                        ctx -> 5 * ctx.getWorld().getTps(),
                        "bench initialised"))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int bx = ctx.getOriginX(), by = ctx.getOriginY(), bz = ctx.getOriginZ() + 4;
                    Vector3i benchPos = new Vector3i(bx, by, bz);
                    Vector3i pipePos = new Vector3i(bx, by + 3, bz - 1);
                    return isConnected(w, "Fluid", benchPos, pipePos);
                }, "bench is connected to fluid pipe in Fluid grid"));
    }

    private static TestCase itemPipeConnectsToBenchInput() {
        return new TestCase("item_pipe_connects_to_bench_input", 3, 10, 9)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int bx = ctx.getOriginX(), by = ctx.getOriginY(), bz = ctx.getOriginZ() + 4;
                    placeBench(w, bx, by, bz);
                    // item input neighbor: (bx, by, bz+2)
                    placeBlock(w, bx, by, bz + 2, ITEM_PIPE_ID);
                }))
                .step(Steps.waitUntil(
                        ctx -> getProcessingBench(ctx.getWorld(),
                                ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ() + 4) != null,
                        ctx -> 5 * ctx.getWorld().getTps(),
                        "bench initialised"))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int bx = ctx.getOriginX(), by = ctx.getOriginY(), bz = ctx.getOriginZ() + 4;
                    Vector3i benchPos = new Vector3i(bx, by, bz);
                    Vector3i pipePos = new Vector3i(bx, by, bz + 2);
                    return isConnected(w, "Item", benchPos, pipePos);
                }, "bench is connected to item pipe at input face in Item grid"));
    }

    private static TestCase itemPipeConnectsToBenchOutput() {
        return new TestCase("item_pipe_connects_to_bench_output", 3, 10, 9)
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int bx = ctx.getOriginX(), by = ctx.getOriginY(), bz = ctx.getOriginZ() + 4;
                    placeBench(w, bx, by, bz);
                    // item output neighbor: (bx, by, bz-3)
                    placeBlock(w, bx, by, bz - 3, ITEM_PIPE_ID);
                }))
                .step(Steps.waitUntil(
                        ctx -> getProcessingBench(ctx.getWorld(),
                                ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ() + 4) != null,
                        ctx -> 5 * ctx.getWorld().getTps(),
                        "bench initialised"))
                .step(Steps.assertThat(ctx -> {
                    World w = ctx.getWorld();
                    int bx = ctx.getOriginX(), by = ctx.getOriginY(), bz = ctx.getOriginZ() + 4;
                    Vector3i benchPos = new Vector3i(bx, by, bz);
                    Vector3i pipePos = new Vector3i(bx, by, bz - 3);
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
                    // pipe connects to bench fluid face; seed the pipe as the fluid source
                    placeBlock(w, bx, by + 3, bz - 1, FLUID_PIPE_ID);
                }))
                .step(Steps.waitUntil(
                        ctx -> isFluidSourceFilled(ctx.getWorld(),
                                ctx.getOriginX(), ctx.getOriginY() + 3, ctx.getOriginZ() + 3),
                        ctx -> 5 * ctx.getWorld().getTps(),
                        "fluid pipe container initialised"))
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int bx = ctx.getOriginX(), by = ctx.getOriginY(), bz = ctx.getOriginZ() + 4;
                    seedFluidSource(w, bx, by + 3, bz - 1);
                }))
                .step(Steps.waitUntil(
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
                    // extractor (South-facing) at bz+2 pulls from container at bz+3 and outputs toward bench
                    FluidTestUtil.setBlockWithRotation(w, bx, by, bz + 2, ITEM_EXTRACTOR_ID, FluidTestUtil.ROTATION_SOUTH);
                    PlaceGridBlockEvent.connectBlock(w, new Vector3i(bx, by, bz + 2));
                    placeBlock(w, bx, by, bz + 3, ITEM_CONTAINER_ID);
                }))
                .step(Steps.waitUntil(
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
                    lockRecipe(w, bx, by, bz);
                }))
                .step(Steps.waitUntil(
                        ctx -> {
                            World w = ctx.getWorld();
                            int bx = ctx.getOriginX(), by = ctx.getOriginY(), bz = ctx.getOriginZ() + 4;
                            AutoProcessingBenchBlock apbb = getProcessingBench(w, bx, by, bz);
                            return apbb != null && apbb.getItemInputContainer() != null;
                        },
                        ctx -> 3 * ctx.getWorld().getTps(),
                        "bench item input container ready"))
                .step(Steps.run(ctx -> {
                    World w = ctx.getWorld();
                    int bx = ctx.getOriginX(), by = ctx.getOriginY(), bz = ctx.getOriginZ() + 4;
                    seedItemContainer(w, bx, by, bz + 3, RECIPE_INPUT, 3);
                }))
                .step(Steps.waitUntil(
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
                    // inserter (North-facing) at bz-3 receives from bench output and pushes to container at bz-4
                    FluidTestUtil.setBlockWithRotation(w, bx, by, bz - 3, ITEM_INSERTER_ID, FluidTestUtil.ROTATION_NORTH);
                    PlaceGridBlockEvent.connectBlock(w, new Vector3i(bx, by, bz - 3));
                    placeBlock(w, bx, by, bz - 4, ITEM_CONTAINER_ID);
                }))
                .step(Steps.waitUntil(
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
                            output.setItemStackForSlot((short) 0, new ItemStack(RECIPE_INPUT, 3), false);
                    }
                }))
                .step(Steps.waitUntil(
                        ctx -> {
                            World w = ctx.getWorld();
                            int bx = ctx.getOriginX(), by = ctx.getOriginY(), bz = ctx.getOriginZ() + 4;
                            ItemContainerBlock icb = ItemTestUtil.getItemContainerBlock(w,
                                    new Vector3i(bx, by, bz - 4));
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
                            new Vector3i(bx, by, bz - 4));
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

    private static void lockRecipe(World w, int x, int y, int z) {
        AutoCraftingBenchBlock acbb = getSelectorBench(w, x, y, z);
        if (acbb != null)
            acbb.setLockedRecipe(RECIPE_ID);
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
        fc.addFluidStack(new FluidStack(fluidId, FLUID_AMOUNT_MB, fc.getCapacityMbPerSlot()), false, false);
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
                total += s.getQuantity();
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
    private static AutoCraftingBenchBlock getSelectorBench(World w, int x, int y, int z) {
        GridLookup lu = GridLookup.resolve(w.getChunkStore(), new Vector3i(x, y, z));
        if (lu == null)
            return null;
        return w.getChunkStore().getStore().getComponent(lu.blockRef(),
                AutoCraftingBenchBlock.getComponentType());
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
