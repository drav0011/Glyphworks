package dev.drav.glyphworks.fluid.tests;

import org.joml.Vector3i;


import dev.drav.glyphworks.fluid.component.FluidContainerComponent;
import dev.drav.glyphworks.test.framework.Steps;
import dev.drav.glyphworks.test.framework.TestCase;
import dev.drav.glyphworks.test.framework.TestRegistry;
import dev.drav.glyphworks.test.framework.TestSuite;

/**
 * Suite {@code "fluid_source_system"} — integration tests for
 * {@link dev.drav.glyphworks.fluid.system.FluidSourceSystem}.
 *
 * <p>
 * Each tick the system forces the container on every {@code Test_Fluid_Source}
 * block to be completely full with the configured fluid type. Tests verify that
 * the system both maintains the full state and is authoritative over any stale
 * in-memory state.
 */
public final class FluidSourceSystemTests {

    private static final String SOURCE_ID = "Glyphworks_Fluid_Source";
    private static final String FLUID_ID = "Water_Source";
    

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
                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))
                .step(Steps.assertThat(ctx -> {
                    FluidContainerComponent fcc = FluidTestUtil.getContainer(ctx.getWorld(),
                            new Vector3i(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()));
                    return fcc != null
                            && fcc.getAmount() == fcc.getCapacity()
                            && FLUID_ID.equals(fcc.getFluidId());
                }, "FluidSourceSystem fills container to capacity with the correct fluid each tick"));
    }

    // -------------------------------------------------------------------------
    // Test 2: source overwrites stale fluid ID and partial amount on the next tick
    // -------------------------------------------------------------------------

    private static TestCase sourceOverridesStaleState() {
        return new TestCase("source_overrides_stale_state", 3, 3, 3)
                .step(Steps.run(ctx -> {
                    ctx.getWorld().setBlock(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ(), SOURCE_ID);
                }))
                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))
                .step(Steps.run(ctx -> {
                    // Corrupt the container — wrong fluid, partial amount.
                    FluidContainerComponent fcc = FluidTestUtil.getContainer(ctx.getWorld(),
                            new Vector3i(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()));
                    if (fcc != null) {
                        fcc.setFluidId("Lava_Source");
                        fcc.setAmount(1);
                    }
                }))
                .step(Steps.wait(ctx -> 2 * ctx.getWorld().getTps()))
                .step(Steps.assertThat(ctx -> {
                    FluidContainerComponent fcc = FluidTestUtil.getContainer(ctx.getWorld(),
                            new Vector3i(ctx.getOriginX(), ctx.getOriginY(), ctx.getOriginZ()));
                    return fcc != null
                            && fcc.getAmount() == fcc.getCapacity()
                            && FLUID_ID.equals(fcc.getFluidId());
                }, "FluidSourceSystem overrides stale fluid ID and partial amount on the next tick"));
    }
}
