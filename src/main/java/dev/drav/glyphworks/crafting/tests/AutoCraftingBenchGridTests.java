package dev.drav.glyphworks.crafting.tests;

import javax.annotation.Nullable;

import org.joml.Vector3i;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.World;

import dev.drav.glyphworks.GlyphworksPlugin;
import dev.drav.glyphworks.crafting.component.AutoCraftingBenchBlock;
import dev.drav.glyphworks.fluid.component.FluidContainerComponent;
import dev.drav.glyphworks.fluid.tests.FluidTestUtil;
import dev.drav.glyphworks.grid.event.PlaceGridBlockEvent;
import dev.drav.glyphworks.grid.graph.GridGraph;
import dev.drav.glyphworks.grid.lookup.GridLookup;
import dev.drav.glyphworks.grid.type.GridType;
import dev.drav.glyphworks.grid.type.GridTypeRegistry;
import dev.drav.glyphworks.test.framework.Steps;
import dev.drav.glyphworks.test.framework.TestCase;
import dev.drav.glyphworks.test.framework.TestRegistry;
import dev.drav.glyphworks.test.framework.TestSuite;

/**
 * Suite "auto_crafting_bench_grid" — verifies that fluid pipes and item pipes
 * connect to bench faces at the expected positions, and that items/fluid
 * actually flow across those connections during grid ticks.
 *
 * <p>Bench geometry (rotation=0, South-facing, placed at origin (ox, oy, oz)):
 * <pre>
 *   Multi-block spans: X[ox-1..ox+1]  Y[oy..oy+2]  Z[oz-2..oz+1]
 *   Fluid face  at cell (ox, oy+2, oz-1), Normal Up    → neighbor at (ox, oy+3, oz-1)
 *   Item input  at cell (ox, oy,   oz+1), Normal South → neighbor at (ox, oy,   oz+2)
 *   Item output at cell (ox, oy,   oz-2), Normal North → neighbor at (ox, oy,   oz-3)
 * </pre>
 *
 * <p>Fluid flow network (test 4):
 * <pre>
 *   Fluid source (ox, oy+2, oz+2) [Up-Output, outside bench]
 *     → Fluid pipe A (ox, oy+3, oz+2) [Down connects to source]
 *     → Fluid pipe B (ox, oy+3, oz+1) [relay]
 *     → Fluid pipe C (ox, oy+3, oz+0) [relay]
 *     → Fluid pipe D (ox, oy+3, oz-1) [Down connects to bench fluid face]
 *     → Bench fluid container
 * </pre>
 *
 * <p>Item-in network (test 5):
 * <pre>
 *   Item container (ox, oy, oz+3) [rotated 180° yaw → North-Output]
 *     → Item pipe (ox, oy, oz+2) [connects to container; South connects to bench input]
 *     → Bench item input container
 * </pre>
 *
 * <p>Item-out network (test 6):
 * <pre>
 *   Bench item output container
 *     → Item pipe (ox, oy, oz-3) [South connects to bench output cell]
 *     → Item container (ox, oy, oz-4) [rotated 180° yaw → South-Input]
 * </pre>
 */
public final class AutoCraftingBenchGridTests {

    private static final String BENCH_ID         = "Glyphworks_Crafting_Bench_WorkBench";
    private static final String RECIPE_ID        = "Deco_Target_Recipe_Generated_0";
    private static final String RECIPE_INPUT     = "Ingredient_Fibre";
    private static final String MANA_FLUID_ID    = "Glyphworks_Fluid_Mana";
    private static final int    MANA_CAPACITY    = 4_000;
    private static final String FLUID_PIPE_ID    = "Glyphworks_Fluid_Pipe";
    private static final String FLUID_SOURCE_ID  = "Glyphworks_Fluid_Source";
    private static final String ITEM_PIPE_ID     = "Glyphworks_Item_Pipe";
    private static final String ITEM_CONTAINER_ID = "Glyphworks_Item_Container";
    private static final int    SETUP_WAIT       = 2;

    private static final int ROTATION_YAW_180 = RotationTuple.index(
            Rotation.OneEighty, Rotation.None, Rotation.None);

    private AutoCraftingBenchGridTests() {}

    public static void register(String moduleId) {
        TestRegistry.register(moduleId, buildSuite());
    }

    private static TestSuite buildSuite() {
        return new TestSuite("auto_crafting_bench_grid")
                .test(fluidPipeConnectsToBench())
                .test(itemPipeConnectsToBenchInput())
                .test(itemPipeConnectsToBenchOutput())
                .test(fluidFlowsFromNetworkIntoBench())
                .test(itemsFlowIntoBenchViaItemPipe())
                .test(itemsFlowOutOfBenchViaItemPipe());
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static void placeBench(World w, int x, int y, int z) {
        w.setBlock(x, y, z, BENCH_ID);
        PlaceGridBlockEvent.connectBlock(w, new Vector3i(x, y, z));
    }

    private static void placeBlock(World w, int x, int y, int z, String id) {
        w.setBlock(x, y, z, id);
        PlaceGridBlockEvent.connectBlock(w, new Vector3i(x, y, z));
    }

    private static void placeBlockRotated(World w, int x, int y, int z, String id, int rotationIndex) {
        FluidTestUtil.setBlockWithRotation(w, x, y, z, id, rotationIndex);
        PlaceGridBlockEvent.connectBlock(w, new Vector3i(x, y, z));
    }

    @Nullable
    private static AutoCraftingBenchBlock getBench(World w, int x, int y, int z) {
        GridLookup lu = GridLookup.resolve(w.getChunkStore(), new Vector3i(x, y, z));
        if (lu == null) return null;
        return w.getChunkStore().getStore().getComponent(lu.blockRef(),
                AutoCraftingBenchBlock.getComponentType());
    }

    @Nullable
    private static BlockModule.BlockStateInfo getBsi(World w, int x, int y, int z) {
        GridLookup lu = GridLookup.resolve(w.getChunkStore(), new Vector3i(x, y, z));
        if (lu == null) return null;
        return w.getChunkStore().getStore().getComponent(lu.blockRef(),
                BlockModule.BlockStateInfo.getComponentType());
    }

    @Nullable
    private static FluidContainerComponent getFluid(World w, int x, int y, int z) {
        GridLookup lu = GridLookup.resolve(w.getChunkStore(), new Vector3i(x, y, z));
        if (lu == null) return null;
        return w.getChunkStore().getStore().getComponent(lu.blockRef(),
                FluidContainerComponent.getComponentType());
    }

    @Nullable
    private static ItemContainerBlock getItemContainerBlock(World w, int x, int y, int z) {
        GridLookup lu = GridLookup.resolve(w.getChunkStore(), new Vector3i(x, y, z));
        if (lu == null) return null;
        return w.getChunkStore().getStore().getComponent(lu.blockRef(),
                ItemContainerBlock.getComponentType());
    }

    /** Returns true if a and b are directly adjacent in the named grid graph. */
    private static boolean isConnected(World w, String gridTypeId, Vector3i a, Vector3i b) {
        GridType type = GridTypeRegistry.get(gridTypeId);
        if (type == null) return false;
        GridGraph graph = GlyphworksPlugin.get().getGridModule().getGridGraph(w, type);
        if (graph == null) return false;
        return graph.getNeighbors(a).contains(b);
    }

    private static void lockRecipe(World w, int x, int y, int z) {
        AutoCraftingBenchBlock bench = getBench(w, x, y, z);
        if (bench == null) return;
        BlockModule.BlockStateInfo bsi = getBsi(w, x, y, z);
        if (bsi == null) return;
        BlockType bt = w.getBlockType(x, y, z);
        if (bt == null) return;
        bench.setLockedRecipe(RECIPE_ID, bsi, w, x, y, z, bt, 0);
    }

    private static int countInput(World w, int x, int y, int z) {
        AutoCraftingBenchBlock bench = getBench(w, x, y, z);
        if (bench == null) return -1;
        ItemContainer input = bench.getInputContainer();
        if (input == null) return -1;
        int total = 0;
        for (short i = 0; i < input.getCapacity(); i++) {
            ItemStack s = input.getItemStack(i);
            if (s != null && !s.isEmpty()) total += s.getQuantity();
        }
        return total;
    }

    /** Counts total items in the {@code ItemContainerBlock} at the given world position. */
    private static int countItemsAt(World w, int x, int y, int z) {
        ItemContainerBlock icb = getItemContainerBlock(w, x, y, z);
        if (icb == null) return -1;
        ItemContainer c = icb.getItemContainer();
        if (c == null) return -1;
        int total = 0;
        for (short i = 0; i < c.getCapacity(); i++) {
            ItemStack s = c.getItemStack(i);
            if (s != null && !s.isEmpty()) total += s.getQuantity();
        }
        return total;
    }

    // ── Tests ──────────────────────────────────────────────────────────────────

    /**
     * Fluid pipe placed exactly one block above the bench fluid face cell
     * (ox, oy+3, oz-1) must be directly connected to the bench in the Fluid grid graph.
     */
    private static TestCase fluidPipeConnectsToBench() {
        return new TestCase("fluid_pipe_connects_to_bench", 5, 5, 5)
                .step(Steps.run(ctx -> {
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    placeBench(ctx.getWorld(), x, y, z);
                    // Bench fluid face: local {0,2,-1} Normal Up → connection point at (ox, oy+3, oz-1)
                    placeBlock(ctx.getWorld(), x, y + 3, z - 1, FLUID_PIPE_ID);
                }))
                .step(Steps.wait(SETUP_WAIT))
                .step(Steps.assertThat(ctx -> {
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    return isConnected(ctx.getWorld(), "Fluid",
                            new Vector3i(x, y, z), new Vector3i(x, y + 3, z - 1));
                }, "fluid pipe at (ox, oy+3, oz-1) must be connected to bench in the Fluid graph"));
    }

    /**
     * Item pipe placed one step behind the bench South input face
     * (ox, oy, oz+2) must be directly connected to the bench in the Item grid graph.
     */
    private static TestCase itemPipeConnectsToBenchInput() {
        return new TestCase("item_pipe_connects_to_bench_input", 5, 5, 5)
                .step(Steps.run(ctx -> {
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    placeBench(ctx.getWorld(), x, y, z);
                    // Item input face: local {0,0,1} Normal South → connection at (ox, oy, oz+2)
                    placeBlock(ctx.getWorld(), x, y, z + 2, ITEM_PIPE_ID);
                }))
                .step(Steps.wait(SETUP_WAIT))
                .step(Steps.assertThat(ctx -> {
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    return isConnected(ctx.getWorld(), "Item",
                            new Vector3i(x, y, z), new Vector3i(x, y, z + 2));
                }, "item pipe at (ox, oy, oz+2) must be connected to bench in the Item graph (input face)"));
    }

    /**
     * Item pipe placed one step in front of the bench North output face
     * (ox, oy, oz-3) must be directly connected to the bench in the Item grid graph.
     */
    private static TestCase itemPipeConnectsToBenchOutput() {
        return new TestCase("item_pipe_connects_to_bench_output", 5, 5, 5)
                .step(Steps.run(ctx -> {
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    placeBench(ctx.getWorld(), x, y, z);
                    // Item output face: local {0,0,-2} Normal North → connection at (ox, oy, oz-3)
                    placeBlock(ctx.getWorld(), x, y, z - 3, ITEM_PIPE_ID);
                }))
                .step(Steps.wait(SETUP_WAIT))
                .step(Steps.assertThat(ctx -> {
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    return isConnected(ctx.getWorld(), "Item",
                            new Vector3i(x, y, z), new Vector3i(x, y, z - 3));
                }, "item pipe at (ox, oy, oz-3) must be connected to bench in the Item graph (output face)"));
    }

    /**
     * Fluid flows from a seeded fluid source through two relay pipes into the bench mana container.
     *
     * <p>Layout:
     * <pre>
     *   source (ox, oy+2, oz+2)  [Up-Output, outside bench footprint]
     *   pipe A (ox, oy+3, oz+2)  [Down connects to source]
     *   pipe B (ox, oy+3, oz+1)  [relay]
     *   pipe C (ox, oy+3, oz+0)  [relay]
     *   pipe D (ox, oy+3, oz-1)  [Down connects to bench fluid face]
     *   bench  (ox, oy,   oz)
     * </pre>
     */
    private static TestCase fluidFlowsFromNetworkIntoBench() {
        return new TestCase("fluid_flows_from_network_into_bench", 5, 5, 5)
                .step(Steps.run(ctx -> {
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    placeBench(ctx.getWorld(), x, y, z);
                    // Pipe D: directly above bench fluid face (Down→bench)
                    placeBlock(ctx.getWorld(), x, y + 3, z - 1, FLUID_PIPE_ID);
                    // Pipe C: relay
                    placeBlock(ctx.getWorld(), x, y + 3, z + 0, FLUID_PIPE_ID);
                    // Pipe B: relay
                    placeBlock(ctx.getWorld(), x, y + 3, z + 1, FLUID_PIPE_ID);
                    // Pipe A: above source, outside bench footprint (Down→source)
                    placeBlock(ctx.getWorld(), x, y + 3, z + 2, FLUID_PIPE_ID);
                    // Fluid source: beneath pipe A, outside bench (Up→pipe A)
                    placeBlock(ctx.getWorld(), x, y + 2, z + 2, FLUID_SOURCE_ID);
                    // Seed source with mana
                    FluidContainerComponent fcc = getFluid(ctx.getWorld(), x, y + 2, z + 2);
                    if (fcc != null) fcc.fill(MANA_FLUID_ID, MANA_CAPACITY);
                }))
                .step(Steps.wait(ctx -> 3 * ctx.getWorld().getTps()))
                .step(Steps.assertThat(ctx -> {
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    FluidContainerComponent benchFluid = getFluid(ctx.getWorld(), x, y, z);
                    return benchFluid != null && benchFluid.getAmount() > 0;
                }, "mana must flow: fluid source → pipe A → pipe B → pipe C → pipe D → bench mana container"));
    }

    /**
     * Items flow from a seeded item container through an item pipe into the bench input container.
     *
     * <p>Layout:
     * <pre>
     *   item container (ox, oy, oz+3)  [rotated 180° yaw → North-Output → pipe]
     *   item pipe      (ox, oy, oz+2)  [North connects to bench input]
     *   bench input    (ox, oy, oz+1)  [South-Input face cell]
     * </pre>
     */
    private static TestCase itemsFlowIntoBenchViaItemPipe() {
        return new TestCase("items_flow_into_bench_via_item_pipe", 5, 5, 6)
                .step(Steps.run(ctx -> {
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    placeBench(ctx.getWorld(), x, y, z);
                    lockRecipe(ctx.getWorld(), x, y, z);
                    placeBlock(ctx.getWorld(), x, y, z + 2, ITEM_PIPE_ID);
                    placeBlockRotated(ctx.getWorld(), x, y, z + 3, ITEM_CONTAINER_ID, ROTATION_YAW_180);
                    ItemContainerBlock icb = getItemContainerBlock(ctx.getWorld(), x, y, z + 3);
                    if (icb != null) {
                        icb.getItemContainer().addItemStack(
                                new ItemStack(RECIPE_INPUT, 5), false, false, false);
                    }
                }))
                .step(Steps.wait(ctx -> 3 * ctx.getWorld().getTps()))
                .step(Steps.assertThat(ctx -> {
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    return countInput(ctx.getWorld(), x, y, z) > 0;
                }, "items must flow: item container (North-Output via 180° yaw) → item pipe → bench input container"));
    }

    /**
     * The bench crafts one cycle (ingredient + mana present), producing an item in
     * the output container.  An item pipe at (ox, oy, oz-3) connects bench output
     * to an item container at (ox, oy, oz-4) rotated 180° yaw so that its South-Input
     * face connects to the pipe.
     *
     * <p>Layout:
     * <pre>
     *   bench output   (ox, oy, oz-2)  [North-Output face cell]
     *   item pipe      (ox, oy, oz-3)  [South→bench; North→container]
     *   item container (ox, oy, oz-4)  [rotated 180° yaw → South-Input receives from pipe]
     * </pre>
     */
    private static TestCase itemsFlowOutOfBenchViaItemPipe() {
        return new TestCase("items_flow_out_of_bench_via_item_pipe", 5, 5, 7)
                .step(Steps.run(ctx -> {
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    placeBench(ctx.getWorld(), x, y, z);
                    placeBlock(ctx.getWorld(), x, y, z - 3, ITEM_PIPE_ID);
                    placeBlockRotated(ctx.getWorld(), x, y, z - 4, ITEM_CONTAINER_ID, ROTATION_YAW_180);
                }))
                .step(Steps.wait(SETUP_WAIT))
                .step(Steps.run(ctx -> {
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    lockRecipe(ctx.getWorld(), x, y, z);
                    FluidContainerComponent fcc = getFluid(ctx.getWorld(), x, y, z);
                    if (fcc != null) fcc.fill(MANA_FLUID_ID, MANA_CAPACITY);
                    AutoCraftingBenchBlock bench = getBench(ctx.getWorld(), x, y, z);
                    if (bench != null) {
                        ItemContainer input = bench.getInputContainer();
                        if (input != null) {
                            input.addItemStack(
                                    new ItemStack(RECIPE_INPUT, 1), false, false, false);
                        }
                    }
                }))
                .step(Steps.wait(ctx -> 5 * ctx.getWorld().getTps()))
                .step(Steps.assertThat(ctx -> {
                    int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                    return countItemsAt(ctx.getWorld(), x, y, z - 4) > 0;
                }, "crafted item must drain from bench output → item pipe → item container (South-Input via 180° yaw)"));
    }
}

