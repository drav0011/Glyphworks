package dev.drav.glyphworks.fluid.system;

import org.joml.Vector3i;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.World;

import dev.drav.glyphworks.fluid.FluidStack;
import dev.drav.glyphworks.fluid.component.FluidContainerComponent;
import dev.drav.glyphworks.fluid.component.FluidSourceComponent;
import dev.drav.glyphworks.fluid.event.FluidItemRegistry;
import dev.drav.glyphworks.fluid.FluidTestUtil;
import dev.drav.glyphworks.grid.lookup.GridLookup;
import dev.drav.glyphworks.test.framework.Steps;
import dev.drav.glyphworks.test.framework.TestCase;
import dev.drav.glyphworks.test.framework.TestRegistry;
import dev.drav.glyphworks.test.framework.TestSuite;

/**
 * Suite {@code "fluid_source_system"} — integration tests for
 * {@link dev.drav.glyphworks.fluid.system.FluidSourceSystem}.
 *
 * <p>
 * The source block only produces fluid when a fluid item is placed in the
 * selector slot of its {@link FluidSourceComponent}. Tests first populate
 * that slot, then verify the paired container fills to capacity.
 */
public final class FluidSourceSystemTests {

    private static final String SOURCE_ID = "Glyphworks_Fluid_Source";
    private static final String FLUID_ID = "Mana_Source";

    private FluidSourceSystemTests() {
    }

    public static void register(String moduleId) {
        TestRegistry.register(moduleId, buildSuite());
    }

    private static TestSuite buildSuite() {
        return new TestSuite("fluid_source_system")
                .test(sourceFillsContainer())
                .test(sourceOverridesStaleState());
    }

    // -------------------------------------------------------------------------
    // Test 1: source keeps its container full with the correct fluid
    // -------------------------------------------------------------------------

    private static TestCase sourceFillsContainer() {
        return new TestCase("source_fills_container", 3, 3, 3)
                .step(Steps.run(ctx -> {
                    ctx.getWorld().setBlock(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ(), SOURCE_ID);
                }))
                .step(Steps.succeedWhen(ctx -> getSource(ctx.getWorld(), ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()) != null,
                        ctx -> 5 * ctx.getWorld().getTps(), "source block entity initialised"))
                .step(Steps.run(ctx -> {
                    FluidSourceComponent fsc = getSource(ctx.getWorld(), ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    if (fsc != null) {
                        String itemId = FluidItemRegistry.resolveItemId(FLUID_ID);
                        if (itemId != null) {
                            fsc.getSelectorContainer().setItemStackForSlot((short) 0, new ItemStack(itemId, 1, 1000, 1000, null), false);
                        }
                    }
                }))
                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))
                .step(Steps.assertThat(ctx -> {
                    FluidContainerComponent fcc = FluidTestUtil.getContainer(ctx.getWorld(),
                            new Vector3i(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()));
                    if (fcc == null)
                        return false;
                    int total = 0;
                    int matching = 0;
                    for (short slot = 0; slot < fcc.getFluidContainer().getCapacity(); slot++) {
                        FluidStack stack = fcc.getFluidContainer().getFluidStack(slot);
                        if (stack != null) {
                            total += stack.getQuantity();
                            if (FLUID_ID.equals(stack.getFluidId())) {
                                matching += stack.getQuantity();
                            }
                        }
                    }
                    int expectedTotal = fcc.getFluidContainer().getCapacityMbPerSlot()
                            * fcc.getFluidContainer().getCapacity();
                    return total == expectedTotal && matching == expectedTotal;
                }, "FluidSourceSystem fills container to capacity with the correct fluid each tick"));
    }

    // -------------------------------------------------------------------------
    // Test 2: source overwrites stale fluid and partial amount on the next tick
    // -------------------------------------------------------------------------

    private static TestCase sourceOverridesStaleState() {
        return new TestCase("source_overrides_stale_state", 3, 3, 3)
                .step(Steps.run(ctx -> {
                    ctx.getWorld().setBlock(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ(), SOURCE_ID);
                }))
                .step(Steps.succeedWhen(ctx -> getSource(ctx.getWorld(), ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()) != null,
                        ctx -> 5 * ctx.getWorld().getTps(), "source block entity initialised"))
                .step(Steps.run(ctx -> {
                    FluidSourceComponent fsc = getSource(ctx.getWorld(), ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ());
                    if (fsc != null) {
                        String itemId = FluidItemRegistry.resolveItemId(FLUID_ID);
                        if (itemId != null) {
                            fsc.getSelectorContainer().setItemStackForSlot((short) 0, new ItemStack(itemId, 1, 1000, 1000, null), false);
                        }
                    }
                }))
                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))
                .step(Steps.run(ctx -> {
                    FluidContainerComponent fcc = FluidTestUtil.getContainer(ctx.getWorld(),
                            new Vector3i(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()));
                    if (fcc != null) {
                        fcc.getFluidContainer().clear();
                        FluidStack stale = new FluidStack(FLUID_ID, 1, fcc.getFluidContainer().getCapacityMbPerSlot());
                        fcc.getFluidContainer().addFluidStackToSlot((short) 0, stale, true, false);
                    }
                }))
                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))
                .step(Steps.assertThat(ctx -> {
                    FluidContainerComponent fcc = FluidTestUtil.getContainer(ctx.getWorld(),
                            new Vector3i(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()));
                    if (fcc == null)
                        return false;
                    int total = 0;
                    int matching = 0;
                    for (short slot = 0; slot < fcc.getFluidContainer().getCapacity(); slot++) {
                        FluidStack stack = fcc.getFluidContainer().getFluidStack(slot);
                        if (stack != null) {
                            total += stack.getQuantity();
                            if (FLUID_ID.equals(stack.getFluidId())) {
                                matching += stack.getQuantity();
                            }
                        }
                    }
                    int expectedTotal = fcc.getFluidContainer().getCapacityMbPerSlot()
                            * fcc.getFluidContainer().getCapacity();
                    return total == expectedTotal && matching == expectedTotal;
                }, "FluidSourceSystem tops up a partially-filled container to capacity on the next tick"));
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private static FluidSourceComponent getSource(World world, int x, int y, int z) {
        GridLookup lu = GridLookup.resolve(world.getChunkStore(), new Vector3i(x, y, z));
        if (lu == null)
            return null;
        return world.getChunkStore().getStore().getComponent(lu.blockRef(), FluidSourceComponent.getComponentType());
    }
}

