package dev.drav.glyphworks.crafting.system;

import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.joml.Vector3i;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
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
 * System integration tests for {@link AutoProcessingBenchBlock} fuel slot
 * behaviour.
 *
 * <p>
 * Each test case uses a dedicated test block whose fuel slot configuration is
 * baked
 * into its JSON, making each test independent of the live production machine
 * configs.
 *
 * <p>
 * Recipe under test: {@code Ingredient_Hide_Light} →
 * {@code Ingredient_Leather_Light},
 * Tannery bench.
 */
public final class AutoProcessingBenchFuelTests {

    private static final String NO_FUEL_BLOCK = "Glyphworks_Test_Crafting_Bench_NoFuel";
    private static final String ITEM_FUEL_ANY_BLOCK = "Glyphworks_Test_Crafting_Bench_ItemFuel_Any";
    private static final String ITEM_FUEL_ALL_BLOCK = "Glyphworks_Test_Crafting_Bench_ItemFuel_All";
    private static final String FLUID_FUEL_ANY_BLOCK = "Glyphworks_Test_Crafting_Bench_FluidFuel_Any";
    private static final String FLUID_FUEL_ALL_BLOCK = "Glyphworks_Test_Crafting_Bench_FluidFuel_All";
    private static final String BOTH_FUEL_ANY_BLOCK = "Glyphworks_Test_Crafting_Bench_BothFuel_Any";
    private static final String BOTH_FUEL_ALL_BLOCK = "Glyphworks_Test_Crafting_Bench_BothFuel_All";
    private static final String TWO_ITEM_ANY_BLOCK = "Glyphworks_Test_Crafting_Bench_TwoItemFuel_Any";
    private static final String TWO_ITEM_ALL_BLOCK = "Glyphworks_Test_Crafting_Bench_TwoItemFuel_All";

    private static final String HIDE_LIGHT = "Ingredient_Hide_Light";
    private static final String CHARCOAL = "Ingredient_Charcoal";
    private static final String MANA_FLUID_ITEM = "Glyphworks_Fluid_Mana";
    private static final int FLUID_AMOUNT_MB = 4000;

    private AutoProcessingBenchFuelTests() {
    }

    public static void register(String moduleId) {
        TestRegistry.register(moduleId, buildSuite());
    }

    private static TestSuite buildSuite() {
        return new TestSuite("auto_processing_bench_fuel")
                .test(noFuelAlwaysProcesses())
                .test(itemFuelAnyWithFuelProcesses())
                .test(itemFuelAnyWithoutFuelBlocks())
                .test(itemFuelAllWithFuelProcesses())
                .test(itemFuelAllWithoutFuelBlocks())
                .test(fluidFuelAnyWithFuelProcesses())
                .test(fluidFuelAnyWithoutFuelBlocks())
                .test(fluidFuelAllWithFuelProcesses())
                .test(fluidFuelAllWithoutFuelBlocks())
                .test(bothFuelAnyItemOnlyProcesses())
                .test(bothFuelAnyFluidOnlyProcesses())
                .test(bothFuelAnyNoFuelBlocks())
                .test(bothFuelAllItemOnlyBlocks())
                .test(bothFuelAllFluidOnlyBlocks())
                .test(bothFuelAllBothProcesses())
                .test(twoItemAnySlot0Processes())
                .test(twoItemAnyNoFuelBlocks())
                .test(twoItemAllRequiresBothSlots());
    }

    // ── No-fuel block ─────────────────────────────────────────────────────────

    private static TestCase noFuelAlwaysProcesses() {
        return buildProgressTest("no_fuel_always_processes", NO_FUEL_BLOCK,
                apbb -> seedInput(apbb, HIDE_LIGHT, 5),
                true,
                "no fuel slots configured: processing must advance without any fuel");
    }

    // ── Single item-fuel slot ─────────────────────────────────────────────────

    private static TestCase itemFuelAnyWithFuelProcesses() {
        return buildProgressTest("item_fuel_any_with_fuel_processes", ITEM_FUEL_ANY_BLOCK,
                apbb -> {
                    seedItemFuelSlot(apbb, CHARCOAL, 64);
                    seedInput(apbb, HIDE_LIGHT, 5);
                },
                true,
                "ANY policy, item fuel slot filled with charcoal: progress must advance");
    }

    private static TestCase itemFuelAnyWithoutFuelBlocks() {
        return buildProgressTest("item_fuel_any_without_fuel_blocks", ITEM_FUEL_ANY_BLOCK,
                apbb -> seedInput(apbb, HIDE_LIGHT, 5),
                false,
                "ANY policy, item fuel slot empty: progress must stay at zero");
    }

    private static TestCase itemFuelAllWithFuelProcesses() {
        return buildProgressTest("item_fuel_all_with_fuel_processes", ITEM_FUEL_ALL_BLOCK,
                apbb -> {
                    seedItemFuelSlot(apbb, CHARCOAL, 64);
                    seedInput(apbb, HIDE_LIGHT, 5);
                },
                true,
                "ALL policy, single item fuel slot filled: progress must advance");
    }

    private static TestCase itemFuelAllWithoutFuelBlocks() {
        return buildProgressTest("item_fuel_all_without_fuel_blocks", ITEM_FUEL_ALL_BLOCK,
                apbb -> seedInput(apbb, HIDE_LIGHT, 5),
                false,
                "ALL policy, item fuel slot empty: progress must stay at zero");
    }

    // ── Single fluid-fuel slot ────────────────────────────────────────────────

    private static TestCase fluidFuelAnyWithFuelProcesses() {
        return buildProgressTest("fluid_fuel_any_with_fuel_processes", FLUID_FUEL_ANY_BLOCK,
                apbb -> {
                    seedFluidFuelSlot(apbb, MANA_FLUID_ITEM);
                    seedInput(apbb, HIDE_LIGHT, 5);
                },
                true,
                "ANY policy, fluid fuel slot filled with mana: progress must advance");
    }

    private static TestCase fluidFuelAnyWithoutFuelBlocks() {
        return buildProgressTest("fluid_fuel_any_without_fuel_blocks", FLUID_FUEL_ANY_BLOCK,
                apbb -> seedInput(apbb, HIDE_LIGHT, 5),
                false,
                "ANY policy, fluid fuel slot empty: progress must stay at zero");
    }

    private static TestCase fluidFuelAllWithFuelProcesses() {
        return buildProgressTest("fluid_fuel_all_with_fuel_processes", FLUID_FUEL_ALL_BLOCK,
                apbb -> {
                    seedFluidFuelSlot(apbb, MANA_FLUID_ITEM);
                    seedInput(apbb, HIDE_LIGHT, 5);
                },
                true,
                "ALL policy, single fluid slot filled: progress must advance");
    }

    private static TestCase fluidFuelAllWithoutFuelBlocks() {
        return buildProgressTest("fluid_fuel_all_without_fuel_blocks", FLUID_FUEL_ALL_BLOCK,
                apbb -> seedInput(apbb, HIDE_LIGHT, 5),
                false,
                "ALL policy, fluid slot empty: progress must stay at zero");
    }

    // ── Item-and-fluid fuel slot (Any) ────────────────────────────────────────

    private static TestCase bothFuelAnyItemOnlyProcesses() {
        return buildProgressTest("both_fuel_any_item_only_processes", BOTH_FUEL_ANY_BLOCK,
                apbb -> {
                    seedItemFuelSlot(apbb, CHARCOAL, 64);
                    seedInput(apbb, HIDE_LIGHT, 5);
                },
                true,
                "ANY policy, item+fluid slots: charcoal only must be sufficient");
    }

    private static TestCase bothFuelAnyFluidOnlyProcesses() {
        return buildProgressTest("both_fuel_any_fluid_only_processes", BOTH_FUEL_ANY_BLOCK,
                apbb -> {
                    seedFluidFuelSlot(apbb, MANA_FLUID_ITEM);
                    seedInput(apbb, HIDE_LIGHT, 5);
                },
                true,
                "ANY policy, item+fluid slots: mana only must be sufficient");
    }

    private static TestCase bothFuelAnyNoFuelBlocks() {
        return buildProgressTest("both_fuel_any_no_fuel_blocks", BOTH_FUEL_ANY_BLOCK,
                apbb -> seedInput(apbb, HIDE_LIGHT, 5),
                false,
                "ANY policy, item+fluid slots, both empty: progress must stay at zero");
    }

    // ── Item-and-fluid fuel slot (All) ────────────────────────────────────────

    private static TestCase bothFuelAllItemOnlyBlocks() {
        return buildProgressTest("both_fuel_all_item_only_blocks", BOTH_FUEL_ALL_BLOCK,
                apbb -> {
                    seedItemFuelSlot(apbb, CHARCOAL, 64);
                    seedInput(apbb, HIDE_LIGHT, 5);
                },
                false,
                "ALL policy, item+fluid slots: charcoal without mana must block");
    }

    private static TestCase bothFuelAllFluidOnlyBlocks() {
        return buildProgressTest("both_fuel_all_fluid_only_blocks", BOTH_FUEL_ALL_BLOCK,
                apbb -> {
                    seedFluidFuelSlot(apbb, MANA_FLUID_ITEM);
                    seedInput(apbb, HIDE_LIGHT, 5);
                },
                false,
                "ALL policy, item+fluid slots: mana without charcoal must block");
    }

    private static TestCase bothFuelAllBothProcesses() {
        return buildProgressTest("both_fuel_all_both_processes", BOTH_FUEL_ALL_BLOCK,
                apbb -> {
                    seedItemFuelSlot(apbb, CHARCOAL, 64);
                    seedFluidFuelSlot(apbb, MANA_FLUID_ITEM);
                    seedInput(apbb, HIDE_LIGHT, 5);
                },
                true,
                "ALL policy, item+fluid slots, both filled: progress must advance");
    }

    // ── Two item-fuel slots ───────────────────────────────────────────────────

    private static TestCase twoItemAnySlot0Processes() {
        return buildProgressTest("two_item_any_slot0_processes", TWO_ITEM_ANY_BLOCK,
                apbb -> {
                    seedItemFuelSlot(apbb, CHARCOAL, 64);
                    seedInput(apbb, HIDE_LIGHT, 5);
                },
                true,
                "ANY policy, 2 item slots: charcoal in slot 0 (Fuel) must be sufficient");
    }

    private static TestCase twoItemAnyNoFuelBlocks() {
        return buildProgressTest("two_item_any_no_fuel_blocks", TWO_ITEM_ANY_BLOCK,
                apbb -> seedInput(apbb, HIDE_LIGHT, 5),
                false,
                "ANY policy, 2 item slots, both empty: progress must stay at zero");
    }

    private static TestCase twoItemAllRequiresBothSlots() {
        return buildProgressTest("two_item_all_requires_both_slots", TWO_ITEM_ALL_BLOCK,
                apbb -> {
                    seedItemFuelSlot(apbb, CHARCOAL, 64);
                    seedInput(apbb, HIDE_LIGHT, 5);
                },
                false,
                "ALL policy, 2 item slots: processing must block when only one required slot is filled");
    }

    // ── Template builder ──────────────────────────────────────────────────────

    private static TestCase buildProgressTest(
            String id,
            String blockId,
            Consumer<AutoProcessingBenchBlock> seed,
            boolean expectProgress,
            String description) {

        boolean needsFluidFuelContainer = blockId.equals(FLUID_FUEL_ANY_BLOCK)
                || blockId.equals(FLUID_FUEL_ALL_BLOCK)
                || blockId.equals(BOTH_FUEL_ANY_BLOCK)
                || blockId.equals(BOTH_FUEL_ALL_BLOCK);

        return new TestCase(id, 3, 3, 3)
                .step(Steps.run(ctx -> placeBlock(ctx.getWorld(), ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ(),
                        blockId)))
                .step(Steps.succeedWhen(
                        ctx -> {
                            int x = ctx.getOriginX(), y = ctx.getOriginY(), z = ctx.getOriginZ();
                            return needsFluidFuelContainer
                                    ? isFluidFuelInitialised(ctx.getWorld(), x, y, z)
                                    : isItemInputInitialised(ctx.getWorld(), x, y, z);
                        },
                        ctx -> 5 * ctx.getWorld().getTps(),
                        "bench block entity initialised"))
                .step(Steps.run(ctx -> {
                    AutoProcessingBenchBlock apbb = getBench(ctx.getWorld(), ctx.getOriginX(), ctx.getOriginY(),
                            ctx.getOriginZ());
                    if (apbb != null)
                        seed.accept(apbb);
                }))
                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))
                .step(Steps.assertThat(ctx -> {
                    AutoProcessingBenchBlock apbb = getBench(ctx.getWorld(),
                            ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    if (apbb == null)
                        return false;

                    boolean passed = expectProgress
                            ? apbb.getInputProgress() > 0.0f
                            : apbb.getInputProgress() == 0.0f;
                    return passed;
                }, description));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void placeBlock(World w, int x, int y, int z, String blockId) {
        w.setBlock(x, y, z, blockId);
        PlaceGridBlockEvent.connectBlock(w, new Vector3i(x, y, z));
    }

    private static boolean isItemInputInitialised(World w, int x, int y, int z) {
        AutoProcessingBenchBlock apbb = getBench(w, x, y, z);
        return apbb != null && apbb.getItemInputContainer() != null;
    }

    private static boolean isFluidFuelInitialised(World w, int x, int y, int z) {
        AutoProcessingBenchBlock apbb = getBench(w, x, y, z);
        return apbb != null && apbb.getFluidFuelContainer() != null;
    }

    private static void seedItemFuelSlot(AutoProcessingBenchBlock apbb, String itemId, int qty) {
        ItemContainer fc = apbb.getItemFuelContainer();
        if (fc == null)
            return;
        fc.addItemStack(new ItemStack(itemId, qty), false, false, false);
    }

    private static void seedFluidFuelSlot(AutoProcessingBenchBlock apbb, String fluidItemId) {
        FluidContainer fc = apbb.getFluidFuelContainer();
        if (fc == null) {
            return;
        }

        String fluidId = FluidItemRegistry.resolveFluidId(fluidItemId);
        if (fluidId == null) {
            return;
        }

        fc.addFluidStack(new FluidStack(fluidId, FLUID_AMOUNT_MB));
    }

    private static void seedInput(AutoProcessingBenchBlock apbb, String itemId, int qty) {
        ItemContainer input = apbb.getItemInputContainer();
        if (input == null)
            return;
        input.addItemStack(new ItemStack(itemId, qty), false, false, false);
    }

    @Nullable
    private static AutoProcessingBenchBlock getBench(World w, int x, int y, int z) {
        Ref<ChunkStore> blockEntityRef = BlockModule.getBlockEntity(w, x, y, z);
        if (blockEntityRef == null)
            return null;
        return w.getChunkStore().getStore().getComponent(blockEntityRef,
                AutoProcessingBenchBlock.getComponentType());
    }
}
